package org.millenaire.village;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.village.diplomacy.DiplomacyEventService;

/**
 * Day-based culture diplomacy batch simulator. Not driven by the hot tick path —
 * call {@link #ensureUpToDate(ServerLevel)} from lazy triggers (hub, login, trade, night fallback).
 */
public final class CultureDiplomacySimulator {
   private static final int MAX_CATCH_UP_DAYS = 7;
   private static final int MAX_LOG = 64;

   private CultureDiplomacySimulator() {
   }

   public static void ensureUpToDate(ServerLevel level) {
      CultureStandingSavedData data = CultureStandingSavedData.get(level);
      long currentDay = level.getDayTime() / 24000L;
      long last = data.getLastProcessedDay();
      if (currentDay <= last) {
         return;
      }

      long start = Math.max(last + 1L, currentDay - MAX_CATCH_UP_DAYS + 1L);
      for (long day = start; day <= currentDay; day++) {
         simulateOneDay(level, data, day);
      }
      data.setLastProcessedDay(currentDay);
      data.setDirty();
   }

   static void simulateOneDay(ServerLevel level, CultureStandingSavedData data, long day) {
      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      List<ResourceLocation> cultures = new ArrayList<>(collectNonLoneCultures(vm));
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      int warThreshold = data.warAutoThreshold();

      for (int i = 0; i < cultures.size(); i++) {
         for (int j = i + 1; j < cultures.size(); j++) {
            ResourceLocation a = cultures.get(i);
            ResourceLocation b = cultures.get(j);
            applyPairAction(level, data, vm, a, b, day, warThreshold, rng);
         }
      }

      CultureWarService.tryScheduleWarRaids(level, data);
      DiplomacyEventService.onSimulatedDay(level, day, rng);
   }

   private static void applyPairAction(
      ServerLevel level,
      CultureStandingSavedData data,
      VillageManager vm,
      ResourceLocation a,
      ResourceLocation b,
      long day,
      int warThreshold,
      ThreadLocalRandom rng
   ) {
      boolean atWar = data.isAtWar(a, b);
      int standing = data.getStanding(a, b);
      int roll = rng.nextInt(100);

      if (atWar) {
         if (roll < 15) {
            int next = data.adjustStanding(a, b, 5 + rng.nextInt(11));
            log(data, day, "ceasefire_feelers", a, b, next, null);
            notifyOwnersOfPair(level, vm, a, b, "message.millenaire.diplomacy.ceasefire_feelers", a, b);
         } else if (roll < 40) {
            int next = data.adjustStanding(a, b, -(5 + rng.nextInt(11)));
            log(data, day, "war_escalation", a, b, next, null);
         } else if (roll < 55) {
            log(data, day, "embargo", a, b, standing, "trade_blocked");
         } else if (roll < 70) {
            log(data, day, "war_raid_plan", a, b, standing, null);
         }
         return;
      }

      if (standing <= warThreshold || (standing < -50 && roll < 12)) {
         data.declareWar(a, b);
         CultureWarService.propagateWarToVillages(level, a, b);
         CultureWarService.ensureWarCampaign(level, a, b, false);
         log(data, day, "declare_war", a, b, -100, null);
         notifyOwnersOfPair(level, vm, a, b, "message.millenaire.diplomacy.war_declared", a, b);
         DiplomacyEventService.offerWarDeclarationPopup(level, a, b);
         return;
      }

      if (standing >= CultureStandingSavedData.ALLIED_THRESHOLD && roll < 20) {
         int next = Math.max(standing, data.adjustStanding(a, b, 5));
         if (next < CultureStandingSavedData.ALLIED_THRESHOLD) {
            data.setStanding(a, b, CultureStandingSavedData.ALLIED_THRESHOLD);
            next = CultureStandingSavedData.ALLIED_THRESHOLD;
         }
         log(data, day, "alliance_pact", a, b, next, null);
         return;
      }

      if (standing >= 0 && roll < 18) {
         int next = data.adjustStanding(a, b, 5 + rng.nextInt(11));
         log(data, day, "trade_mission", a, b, next, null);
         return;
      }

      if (standing < 10 && roll < 22) {
         int next = data.adjustStanding(a, b, -(5 + rng.nextInt(11)));
         log(data, day, "border_incident", a, b, next, null);
         if (next <= warThreshold) {
            data.declareWar(a, b);
            CultureWarService.propagateWarToVillages(level, a, b);
            CultureWarService.ensureWarCampaign(level, a, b, false);
            log(data, day, "declare_war", a, b, -100, "from_border");
            notifyOwnersOfPair(level, vm, a, b, "message.millenaire.diplomacy.war_declared", a, b);
            DiplomacyEventService.offerWarDeclarationPopup(level, a, b);
         }
         return;
      }

      if (roll < 12) {
         int next = data.adjustStanding(a, b, -(5 + rng.nextInt(8)));
         log(data, day, "insult_rivalry", a, b, next, null);
         return;
      }

      int driftChance = MillenaireServerConfig.SERVER.cultureStandingDriftChance.get();
      if (driftChance > 0 && rng.nextInt(100) < driftChance) {
         boolean improve = pickDriftDirection(rng, standing, false);
         int change = 5 + rng.nextInt(11);
         int next = improve ? data.adjustStanding(a, b, change) : data.adjustStanding(a, b, -change);
         log(data, day, "standing_drift", a, b, next, improve ? "improve" : "worsen");
         if (next <= warThreshold) {
            data.declareWar(a, b);
            CultureWarService.propagateWarToVillages(level, a, b);
            CultureWarService.ensureWarCampaign(level, a, b, false);
            log(data, day, "declare_war", a, b, -100, "from_drift");
            notifyOwnersOfPair(level, vm, a, b, "message.millenaire.diplomacy.war_declared", a, b);
            DiplomacyEventService.offerWarDeclarationPopup(level, a, b);
         }
      }
   }

   private static boolean pickDriftDirection(ThreadLocalRandom rng, int standing, boolean atWar) {
      if (atWar) {
         return rng.nextInt(100) < 25;
      }
      if (standing < -50) {
         return rng.nextInt(100) < 30;
      }
      if (standing < 0) {
         return rng.nextInt(100) < 40;
      }
      if (standing > 90) {
         return true;
      }
      if (standing > 50) {
         return rng.nextInt(100) < 70;
      }
      return rng.nextInt(100) < 60;
   }

   private static void log(
      CultureStandingSavedData data, long day, String action, ResourceLocation a, ResourceLocation b, int standing, String detail
   ) {
      data.addDiplomacyLog(new CultureDiplomacyLogEntry(day, action, a, b, standing, detail), MAX_LOG);
   }

   private static void notifyOwnersOfPair(
      ServerLevel level, VillageManager vm, ResourceLocation a, ResourceLocation b, String key, ResourceLocation argA, ResourceLocation argB
   ) {
      Set<UUID> notified = new HashSet<>();
      String nameA = cultureDisplay(argA);
      String nameB = cultureDisplay(argB);
      for (Village v : vm.getAllVillages()) {
         if (!v.isPlayerControlled() || v.getOwnerUUID() == null) {
            continue;
         }
         ResourceLocation c = v.getCultureId();
         if (!c.equals(a) && !c.equals(b)) {
            continue;
         }
         if (!notified.add(v.getOwnerUUID())) {
            continue;
         }
         ServerPlayer player = level.getServer().getPlayerList().getPlayer(v.getOwnerUUID());
         if (player != null) {
            player.sendSystemMessage(Component.translatable(key, nameA, nameB));
         }
      }
   }

   private static String cultureDisplay(ResourceLocation id) {
      var culture = ModCultures.getCulture(id);
      return culture != null ? culture.displayName() : id.getPath();
   }

   public static Set<ResourceLocation> collectNonLoneCultures(VillageManager vm) {
      Set<ResourceLocation> cultures = new HashSet<>();
      for (Village v : vm.getAllVillages()) {
         if (!v.isLoneBuilding()) {
            cultures.add(v.getCultureId());
         }
      }
      return cultures;
   }
}
