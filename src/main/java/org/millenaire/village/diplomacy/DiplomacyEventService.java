package org.millenaire.village.diplomacy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.combat.raid.RaidManager;
import org.millenaire.culture.ModCultures;
import org.millenaire.item.MoneyHelper;
import org.millenaire.network.DiplomacyEventPopupPayload;
import org.millenaire.village.CityStatsService;
import org.millenaire.village.CultureDiplomacySimulator;
import org.millenaire.village.CultureStandingSavedData;
import org.millenaire.village.CultureTradeSpillover;
import org.millenaire.village.CultureWarService;
import org.millenaire.village.ImmigrantService;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.Village;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;

public final class DiplomacyEventService {
   private static final int COOLDOWN_MIN_DAYS = 2;
   private static final int COOLDOWN_MAX_DAYS = 4;
   private static final DiplomacyEventType[] WEIGHTED = DiplomacyEventType.values();

   private DiplomacyEventService() {
   }

   public static void onSimulatedDay(ServerLevel level, long day, ThreadLocalRandom rng) {
      DiplomacyEventSavedData data = DiplomacyEventSavedData.get(level);
      data.expireBefore(day);
      VillageManager vm = VillageSavedData.get(level).getVillageManager();

      for (Village village : vm.getAllVillages()) {
         if (!village.isPlayerControlled() || village.getOwnerUUID() == null) {
            continue;
         }
         UUID owner = village.getOwnerUUID();
         long last = data.lastEventDay(owner);
         int cooldown = COOLDOWN_MIN_DAYS + rng.nextInt(COOLDOWN_MAX_DAYS - COOLDOWN_MIN_DAYS + 1);
         if (last >= 0 && day - last < cooldown) {
            continue;
         }
         if (rng.nextInt(100) >= 45) {
            continue;
         }

         ResourceLocation source = pickOtherCulture(vm, village.getCultureId(), rng);
         if (source == null) {
            continue;
         }

         DiplomacyEventType type = WEIGHTED[rng.nextInt(WEIGHTED.length)];
         int amount = 32 + rng.nextInt(96);
         ResourceLocation secondary = null;
         if (type == DiplomacyEventType.JOINT_RAID_INVITE || type == DiplomacyEventType.WAR_DECLARATION) {
            secondary = pickOtherCulture(vm, source, rng);
         }

         DiplomacyEvent event = new DiplomacyEvent(
            UUID.randomUUID(),
            owner,
            village.getId().uuid(),
            type,
            source,
            secondary,
            day,
            day + 3L,
            amount,
            "denier",
            true
         );
         data.add(event);
         pushPopup(level, event);
      }
   }

   public static void offerWarDeclarationPopup(ServerLevel level, ResourceLocation cultureA, ResourceLocation cultureB) {
      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      long day = level.getDayTime() / 24000L;
      DiplomacyEventSavedData data = DiplomacyEventSavedData.get(level);

      for (Village village : vm.getAllVillages()) {
         if (!village.isPlayerControlled() || village.getOwnerUUID() == null) {
            continue;
         }
         ResourceLocation own = village.getCultureId();
         if (!own.equals(cultureA) && !own.equals(cultureB)) {
            continue;
         }
         ResourceLocation enemy = own.equals(cultureA) ? cultureB : cultureA;
         DiplomacyEvent event = new DiplomacyEvent(
            UUID.randomUUID(),
            village.getOwnerUUID(),
            village.getId().uuid(),
            DiplomacyEventType.WAR_DECLARATION,
            enemy,
            own,
            day,
            day + 2L,
            0,
            "",
            true
         );
         data.add(event);
         pushPopup(level, event);
      }
   }

   public static void pushPendingToPlayer(ServerPlayer player, ServerLevel level) {
      for (DiplomacyEvent event : DiplomacyEventSavedData.get(level).pendingForPlayer(player.getUUID())) {
         pushPopup(level, event);
         break;
      }
   }

   private static void pushPopup(ServerLevel level, DiplomacyEvent event) {
      ServerPlayer player = level.getServer().getPlayerList().getPlayer(event.playerId());
      if (player == null) {
         return;
      }
      String cultureName = cultureName(event.sourceCulture());
      String secondary = event.secondaryCulture() != null ? cultureName(event.secondaryCulture()) : "";
      PacketDistributor.sendToPlayer(
         player,
         new DiplomacyEventPopupPayload(
            event.eventId().toString(),
            event.type().name(),
            cultureName,
            secondary,
            event.amount(),
            event.itemKey() != null ? event.itemKey() : ""
         ),
         new CustomPacketPayload[0]
      );
   }

   public static void respond(ServerPlayer player, ServerLevel level, UUID eventId, boolean accept) {
      CultureDiplomacySimulator.ensureUpToDate(level);
      DiplomacyEventSavedData data = DiplomacyEventSavedData.get(level);
      DiplomacyEvent event = data.find(eventId);
      if (event == null || !event.pending() || !event.playerId().equals(player.getUUID())) {
         return;
      }

      Village village = VillageSavedData.get(level).getVillageManager().getVillage(new org.millenaire.village.VillageId(event.villageId()));
      applyResponse(player, level, village, event, accept);
      data.replace(event.withPending(false));
   }

   private static void applyResponse(
      ServerPlayer player, ServerLevel level, @Nullable Village village, DiplomacyEvent event, boolean accept
   ) {
      PlayerCultureReputation rep = PlayerCultureReputation.get(level);
      CultureStandingSavedData standing = CultureStandingSavedData.get(level);
      ResourceLocation source = event.sourceCulture();
      int amount = event.amount();

      switch (event.type()) {
         case TRADE_BUY_OFFER -> {
            if (accept) {
               MoneyHelper.addDeniers(player.getInventory(), amount, player);
               rep.add(player.getUUID(), source, 40);
               CultureTradeSpillover.onPlayerTrade(player, level, source, amount);
            } else {
               rep.add(player.getUUID(), source, -15);
            }
         }
         case WAR_DECLARATION -> {
            if (village != null) {
               CultureWarService.playerDeclareWar(level, village.getCultureId(), source);
            }
            if (accept && village != null) {
               Village target = findVillageOfCulture(level, source);
               if (target != null) {
                  RaidManager.planRaid(village, target, level);
               }
            }
            rep.add(player.getUUID(), source, -80);
         }
         case RAID_WARNING -> {
            Village attacker = findVillageOfCulture(level, source);
            if (attacker != null && village != null) {
               RaidManager.planRaid(attacker, village, level);
            }
            rep.add(player.getUUID(), source, -40);
         }
         case PEACE_OFFER -> {
            if (accept && village != null) {
               CultureWarService.playerEndWar(level, village.getCultureId(), source);
               rep.add(player.getUUID(), source, 50);
            } else {
               rep.add(player.getUUID(), source, -25);
               if (village != null) {
                  standing.adjustStanding(village.getCultureId(), source, -10);
               }
            }
         }
         case ALLIANCE_PROPOSAL -> {
            if (accept && village != null) {
               standing.setStanding(village.getCultureId(), source, CultureStandingSavedData.ALLIED_THRESHOLD);
               CultureWarService.propagatePeaceToVillages(level, village.getCultureId(), source);
               rep.add(player.getUUID(), source, 80);
               CultureTradeSpillover.onPlayerTrade(player, level, source, amount);
            } else {
               rep.add(player.getUUID(), source, -30);
            }
         }
         case TRIBUTE_DEMAND -> {
            if (accept) {
               if (MoneyHelper.removeDeniers(player.getInventory(), amount)) {
                  rep.add(player.getUUID(), source, 20);
               } else {
                  rep.add(player.getUUID(), source, -50);
                  standing.adjustStanding(village != null ? village.getCultureId() : source, source, -15);
               }
            } else {
               rep.add(player.getUUID(), source, -60);
               if (village != null) {
                  standing.adjustStanding(village.getCultureId(), source, -20);
               }
            }
         }
         case DIPLOMATIC_GIFT -> {
            if (accept) {
               MoneyHelper.addDeniers(player.getInventory(), amount, player);
               rep.add(player.getUUID(), source, 35);
            } else {
               rep.add(player.getUUID(), source, -10);
            }
         }
         case TRADE_SELL_OFFER -> {
            if (accept) {
               if (MoneyHelper.removeDeniers(player.getInventory(), amount)) {
                  rep.add(player.getUUID(), source, 45);
                  CultureTradeSpillover.onPlayerTrade(player, level, source, amount);
               } else {
                  player.sendSystemMessage(Component.translatable("message.millenaire.diplomacy.cannot_pay"));
                  rep.add(player.getUUID(), source, -10);
               }
            } else {
               rep.add(player.getUUID(), source, -12);
            }
         }
         case BORDER_DISPUTE -> {
            if (village != null) {
               standing.adjustStanding(village.getCultureId(), source, accept ? -10 : -25);
            }
            rep.add(player.getUUID(), source, accept ? -20 : -40);
         }
         case TRADE_EMBARGO -> {
            if (village != null) {
               standing.adjustStanding(village.getCultureId(), source, accept ? -30 : -10);
            }
            rep.add(player.getUUID(), source, accept ? -25 : -5);
         }
         case FESTIVAL_INVITE -> {
            if (accept) {
               if (MoneyHelper.removeDeniers(player.getInventory(), Math.max(16, amount / 2))) {
                  rep.add(player.getUUID(), source, 55);
                  if (village != null) {
                     standing.adjustStanding(village.getCultureId(), source, 15);
                  }
               } else {
                  rep.add(player.getUUID(), source, -15);
               }
            } else {
               rep.add(player.getUUID(), source, -20);
            }
         }
         case SPY_ACCUSATION -> {
            if (accept) {
               if (MoneyHelper.removeDeniers(player.getInventory(), amount)) {
                  rep.add(player.getUUID(), source, 15);
               } else {
                  rep.add(player.getUUID(), source, -35);
               }
            } else {
               rep.add(player.getUUID(), source, -45);
               if (village != null) {
                  standing.adjustStanding(village.getCultureId(), source, -15);
               }
            }
         }
         case REFUGEE_REQUEST -> {
            if (accept && village != null) {
               ImmigrantService.callImmigrants(level, village, player);
               rep.add(player.getUUID(), source, 40);
            } else {
               rep.add(player.getUUID(), source, -25);
            }
         }
         case MERCENARY_OFFER -> {
            if (accept) {
               if (MoneyHelper.removeDeniers(player.getInventory(), amount) && village != null) {
                  village.markCityStatsDirty();
                  CityStatsService.refresh(level, village);
                  rep.add(player.getUUID(), source, 25);
               } else {
                  rep.add(player.getUUID(), source, -10);
               }
            } else {
               rep.add(player.getUUID(), source, -5);
            }
         }
         case GRAIN_LOAN_REQUEST -> {
            if (accept) {
               rep.add(player.getUUID(), source, 30);
               if (village != null) {
                  standing.adjustStanding(village.getCultureId(), source, 10);
               }
            } else {
               rep.add(player.getUUID(), source, -35);
            }
         }
         case CARAVAN_BONUS -> {
            if (accept) {
               rep.add(player.getUUID(), source, 50);
               CultureTradeSpillover.onPlayerTrade(player, level, source, amount);
            } else {
               rep.add(player.getUUID(), source, -8);
            }
         }
         case JUSTICE_DEMAND -> {
            if (accept) {
               rep.add(player.getUUID(), source, 45);
            } else {
               rep.add(player.getUUID(), source, -55);
               if (village != null) {
                  standing.adjustStanding(village.getCultureId(), source, -20);
               }
            }
         }
         case JOINT_RAID_INVITE -> {
            if (accept && village != null && event.secondaryCulture() != null) {
               Village target = findVillageOfCulture(level, event.secondaryCulture());
               if (target != null) {
                  RaidManager.planRaid(village, target, level);
               }
               standing.adjustStanding(village.getCultureId(), source, 20);
               rep.add(player.getUUID(), source, 40);
            } else {
               rep.add(player.getUUID(), source, -20);
            }
         }
         case TERRITORY_PROTEST -> {
            if (accept) {
               if (MoneyHelper.removeDeniers(player.getInventory(), amount)) {
                  rep.add(player.getUUID(), source, 10);
               } else if (village != null) {
                  CultureWarService.playerDeclareWar(level, village.getCultureId(), source);
                  rep.add(player.getUUID(), source, -70);
               }
            } else if (village != null) {
               standing.adjustStanding(village.getCultureId(), source, -30);
               rep.add(player.getUUID(), source, -40);
            }
         }
         case MERCHANT_RANSOM -> {
            if (accept) {
               if (MoneyHelper.removeDeniers(player.getInventory(), amount)) {
                  rep.add(player.getUUID(), source, 35);
               } else {
                  rep.add(player.getUUID(), source, -40);
               }
            } else {
               rep.add(player.getUUID(), source, -50);
            }
         }
         case CULTURE_PRAISE -> {
            if (accept) {
               rep.add(player.getUUID(), source, 25);
               if (MoneyHelper.removeDeniers(player.getInventory(), Math.min(32, amount))) {
                  rep.add(player.getUUID(), source, 15);
               }
            }
         }
         case SABOTAGE_THREAT -> {
            if (accept) {
               if (MoneyHelper.removeDeniers(player.getInventory(), amount)) {
                  rep.add(player.getUUID(), source, 5);
               } else if (village != null) {
                  village.markCityStatsDirty();
                  rep.add(player.getUUID(), source, -30);
               }
            } else if (village != null) {
               standing.adjustStanding(village.getCultureId(), source, -25);
               rep.add(player.getUUID(), source, -35);
            }
         }
      }

      player.sendSystemMessage(
         Component.translatable(
            accept ? "message.millenaire.diplomacy.accepted" : "message.millenaire.diplomacy.refused",
            cultureName(source)
         )
      );
   }

   @Nullable
   private static ResourceLocation pickOtherCulture(VillageManager vm, ResourceLocation exclude, ThreadLocalRandom rng) {
      List<ResourceLocation> list = new ArrayList<>(CultureDiplomacySimulator.collectNonLoneCultures(vm));
      list.remove(exclude);
      if (list.isEmpty()) {
         return null;
      }
      return list.get(rng.nextInt(list.size()));
   }

   @Nullable
   private static Village findVillageOfCulture(ServerLevel level, ResourceLocation culture) {
      List<Village> candidates = new ArrayList<>();
      for (Village v : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (!v.isLoneBuilding() && culture.equals(v.getCultureId())) {
            candidates.add(v);
         }
      }
      if (candidates.isEmpty()) {
         return null;
      }
      return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
   }

   private static String cultureName(ResourceLocation id) {
      var c = ModCultures.getCulture(id);
      return c != null ? c.displayName() : id.getPath();
   }
}
