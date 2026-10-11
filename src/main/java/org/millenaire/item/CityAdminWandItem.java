package org.millenaire.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.civilization.presentation.CivilizationPresentationService;
import org.millenaire.civilization.presentation.CivilizationVillageSnapshot;
import org.millenaire.civilization.settlement.PlayerSettlementFoundingService;
import org.millenaire.network.CityAdminPayload;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;

public class CityAdminWandItem extends Item {
   private static final double REACH = 120.0;

   public CityAdminWandItem(Properties properties) {
      super(properties);
   }

   @Override
   public InteractionResult useOn(UseOnContext context) {
      Level level = context.getLevel();
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      }
      if (!(context.getPlayer() instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) {
         return InteractionResult.FAIL;
      }
      return openAdmin(serverLevel, player, context.getClickedPos()) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (level.isClientSide()) {
         return InteractionResultHolder.success(stack);
      }
      if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
         return InteractionResultHolder.fail(stack);
      }
      boolean ok = openAdmin(serverLevel, serverPlayer, serverPlayer.blockPosition());
      return ok ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
   }

   public static boolean openAdmin(ServerLevel level, ServerPlayer player, BlockPos near) {
      Village village = VillageSavedData.get(level).getVillageManager().findNearestVillage(near, REACH);
      if (village == null) {
         player.sendSystemMessage(Component.translatable("ui.city_admin.no_village"));
         return false;
      }
      boolean owner = village.isControlledBy(player.getUUID())
         || PlayerSettlementFoundingService.isSovereignPlayerSettlement(level, village)
            && village.getOwnerUUID() != null
            && village.getOwnerUUID().equals(player.getUUID());
      if (!owner) {
         player.sendSystemMessage(Component.translatable("ui.city_admin.not_owner"));
         return false;
      }
      tryApplyOffhandBanner(level, player, village);
      CivilizationVillageSnapshot snap = CivilizationPresentationService.villageSnapshot(level, village, player);
      CityAdminPayload payload = CityAdminPayload.fromSnapshot(snap, 0);
      payload = enrichWantedStatus(level, player, village, payload);
      PacketDistributor.sendToPlayer(player, payload, new CustomPacketPayload[0]);
      return true;
   }

   /** Holding a banner in the offhand while opening City Admin sets the settlement/faction colours. */
   private static void tryApplyOffhandBanner(ServerLevel level, ServerPlayer player, Village village) {
      ItemStack offhand = player.getOffhandItem();
      if (offhand.isEmpty() || !(offhand.getItem() instanceof net.minecraft.world.item.BannerItem)) {
         return;
      }
      String stored = org.millenaire.village.VillageBannerService.serializeBannerStack(offhand, level.registryAccess());
      if (stored == null) {
         return;
      }
      village.setBannerNbt(stored);
      if (PlayerSettlementFoundingService.isSovereignPlayerSettlement(level, village) && village.getFactionId() != null) {
         var faction = org.millenaire.faction.FactionSavedData.get(level).getFactionManager().getFaction(village.getFactionId());
         if (faction != null) {
            faction.setBannerNbt(stored);
            org.millenaire.faction.FactionSavedData.get(level).setDirty();
         }
      }
      player.sendSystemMessage(Component.translatable("ui.city_admin.banner_set"));
   }

   private static CityAdminPayload enrichWantedStatus(
      ServerLevel level, ServerPlayer player, Village village, CityAdminPayload payload
   ) {
      if (village.getCultureId() == null) {
         return payload;
      }
      var record = org.millenaire.faction.crime.CultureBountySavedData.get(level)
         .getManager()
         .get(player.getUUID(), village.getCultureId());
      String faction = payload.factionDisplay();
      if (village.getCultureId() != null) {
         String culture = village.getCultureId().getPath();
         faction = (faction == null || faction.isBlank() ? culture : faction + " / " + culture);
      }
      String wantedLine = "";
      if (record != null && record.tier().isAtLeast(org.millenaire.faction.crime.WantedTier.SUSPECT)) {
         wantedLine = "wanted:" + record.tier().name() + ":" + record.bountyScore();
      }
      // Pack wanted into colonizationHint only when empty; otherwise keep colony status.
      String colony = payload.colonizationHint();
      if ((colony == null || colony.isBlank()) && !wantedLine.isBlank()) {
         colony = wantedLine;
      } else if (!wantedLine.isBlank()) {
         faction = faction + " [" + record.tier().name() + "]";
      }
      return new CityAdminPayload(
         payload.villageId(),
         payload.villageName(),
         faction,
         payload.developmentKey(),
         payload.residents(),
         payload.housingCapacity(),
         payload.overcrowded(),
         payload.inventoryCertaintyKey(),
         payload.projectLabel(),
         payload.immigrationHint(),
         payload.tradeHint(),
         colony,
         payload.needLines(),
         payload.actions(),
         payload.tabIndex(),
         payload.feedbackKey()
      );
   }
}
