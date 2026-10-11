package org.millenaire.religion.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPurpose;
import org.millenaire.item.ItemHelper;
import org.millenaire.religion.ReligionService;
import org.millenaire.religion.content.ReligionContentRegistry;
import org.millenaire.religion.content.RitualDefinition;
import org.millenaire.religion.model.CeremonyInstance;
import org.millenaire.religion.model.ReligionPolicy;
import org.millenaire.religion.model.VillageFaithCommunity;
import org.millenaire.religion.persist.ReligionSavedData;
import org.millenaire.religion.supply.RitualSupplyService;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

/**
 * Server-authoritative religion player actions. Client payloads never authorize outcomes.
 */
public final class ReligionActionService {
   private static final double MAX_DISTANCE_SQ = 4096.0;
   private final java.util.Map<UUID, Long> dedupe = new java.util.HashMap<>();

   public ReligionActionService() {
   }

   public record ActionResult(boolean success, String messageKey, @Nullable String detail) {
      public static ActionResult ok(String key) {
         return new ActionResult(true, key, null);
      }

      public static ActionResult fail(String key) {
         return new ActionResult(false, key, null);
      }

      public static ActionResult fail(String key, String detail) {
         return new ActionResult(false, key, detail);
      }
   }

   public List<ReligionActionType> availableActions(ServerLevel level, Village village, ServerPlayer player, @Nullable VillagerRecord npc) {
      List<ReligionActionType> out = new ArrayList<>();
      out.add(ReligionActionType.VIEW_RELIGION_STATUS);
      CeremonyInstance active = ReligionService.get().ceremonies().activeCeremony(level, village);
      if (active != null) {
         out.add(ReligionActionType.JOIN_CEREMONY);
         RitualDefinition ritual = ReligionContentRegistry.ritualOrNull(active.ritualId());
         if (ritual != null && !active.suppliesConsumed()) {
            if (!ReligionService.get().supplies().missingSupplies(level, village, ritual).isEmpty()) {
               out.add(ReligionActionType.HELP_PREPARE_CEREMONY);
               out.add(ReligionActionType.DELIVER_RITUAL_SUPPLIES);
            }
         }
      }
      if (village.isUnderAttack()) {
         out.add(ReligionActionType.SUPPORT_AFTER_ATTACK);
      }
      if (ReligionService.get().pilgrimages().isOnPilgrimage(npc != null ? npc.getUuid() : null)
         || (npc != null && ReligionService.get().clergy().isClergy(level, village, npc))) {
         out.add(ReligionActionType.ESCORT_PILGRIM);
      }
      if (village.getOwnerUUID() != null && village.getOwnerUUID().equals(player.getUUID())) {
         out.add(ReligionActionType.SET_RELIGION_POLICY_OPEN);
         out.add(ReligionActionType.SET_RELIGION_POLICY_HOST_PREFERRED);
         out.add(ReligionActionType.SET_RELIGION_POLICY_HOST_ONLY);
         out.add(ReligionActionType.DESIGNATE_SHRINE);
      }
      return out;
   }

   public ActionResult execute(
      ServerLevel level,
      Village village,
      ServerPlayer player,
      ReligionActionType type,
      @Nullable String arg
   ) {
      if (!withinRange(village, player)) {
         return ActionResult.fail("religion.millenaire.error.too_far");
      }
      long now = level.getGameTime();
      Long last = dedupe.get(player.getUUID());
      if (last != null && now - last < 20L) {
         return ActionResult.fail("religion.millenaire.error.too_fast");
      }
      dedupe.put(player.getUUID(), now);

      return switch (type) {
         case VIEW_RELIGION_STATUS -> ActionResult.ok("religion.millenaire.action.view_ok");
         case HELP_PREPARE_CEREMONY, DELIVER_RITUAL_SUPPLIES -> deliverSupplies(level, village, player);
         case JOIN_CEREMONY -> joinCeremony(level, village, player);
         case ESCORT_PILGRIM -> ActionResult.ok("religion.millenaire.action.escort_ack");
         case SUPPORT_AFTER_ATTACK -> supportAfterAttack(level, village, player);
         case SET_RELIGION_POLICY_OPEN -> setPolicy(level, village, player, ReligionPolicy.OPEN);
         case SET_RELIGION_POLICY_HOST_PREFERRED -> setPolicy(level, village, player, ReligionPolicy.HOST_PREFERRED);
         case SET_RELIGION_POLICY_HOST_ONLY -> setPolicy(level, village, player, ReligionPolicy.HOST_ONLY);
         case DESIGNATE_SHRINE -> designateNearbyShrine(level, village, player, arg);
      };
   }

   private ActionResult deliverSupplies(ServerLevel level, Village village, ServerPlayer player) {
      CeremonyInstance active = ReligionService.get().ceremonies().activeCeremony(level, village);
      if (active == null) {
         return ActionResult.fail("religion.millenaire.error.no_ceremony");
      }
      RitualDefinition ritual = ReligionContentRegistry.ritualOrNull(active.ritualId());
      if (ritual == null) {
         return ActionResult.fail("religion.millenaire.error.missing_ritual");
      }
      if (active.suppliesConsumed()) {
         return ActionResult.fail("religion.millenaire.error.already_supplied");
      }
      List<RitualSupplyService.Shortage> missing = ReligionService.get().supplies().missingSupplies(level, village, ritual);
      if (missing.isEmpty()) {
         return ActionResult.fail("religion.millenaire.error.no_shortage");
      }
      BuildingInstance th = village.getTownhall();
      if (th == null) {
         return ActionResult.fail("religion.millenaire.error.no_townhall");
      }
      BuildingInventory inv = th.getInventory();
      int delivered = 0;
      for (RitualSupplyService.Shortage shortage : missing) {
         Item item = ItemHelper.resolve(shortage.itemId());
         if (item == null) {
            continue;
         }
         int taken = takeFromPlayer(player, item, shortage.missing());
         if (taken > 0) {
            inv.add(level, item, taken);
            delivered += taken;
         }
      }
      if (delivered <= 0) {
         return ActionResult.fail("religion.millenaire.error.player_lacks_items");
      }
      ReligionSavedData.get(level).communityFor(village.getId().uuid())
         .addHistory("player_supply:" + player.getUUID() + ":" + delivered);
      ReligionSavedData.get(level).setDirty();
      return ActionResult.ok("religion.millenaire.action.delivered");
   }

   private ActionResult joinCeremony(ServerLevel level, Village village, ServerPlayer player) {
      CeremonyInstance active = ReligionService.get().ceremonies().activeCeremony(level, village);
      if (active == null || !active.state().isActivePhase()) {
         return ActionResult.fail("religion.millenaire.error.no_ceremony");
      }
      BuildingInstance building = active.buildingId() != null ? village.getBuilding(active.buildingId()) : null;
      if (building == null) {
         return ActionResult.fail("religion.millenaire.error.missing_location");
      }
      BlockPos pos = building.getOrigin();
      if (player.blockPosition().distSqr(pos) > 100.0) {
         return ActionResult.fail("religion.millenaire.error.too_far_from_site");
      }
      active.arrivedParticipants().add(player.getUUID());
      active.creditedParticipants().add(player.getUUID());
      ReligionSavedData.get(level).setDirty();
      return ActionResult.ok("religion.millenaire.action.joined");
   }

   private ActionResult supportAfterAttack(ServerLevel level, Village village, ServerPlayer player) {
      ReligionSavedData data = ReligionSavedData.get(level);
      VillageFaithCommunity community = data.communityFor(village.getId().uuid());
      community.addHistory("player_support_attack:" + player.getUUID());
      data.setDirty();
      village.adjustReputation(level, player.getUUID(), 5);
      return ActionResult.ok("religion.millenaire.action.support_ack");
   }

   private ActionResult setPolicy(ServerLevel level, Village village, ServerPlayer player, ReligionPolicy policy) {
      String key = ReligionService.get().policy().setPolicy(level, village, player, policy);
      boolean ok = key.endsWith("updated");
      return ok ? ActionResult.ok(key) : ActionResult.fail(key);
   }

   private ActionResult designateNearbyShrine(ServerLevel level, Village village, ServerPlayer player, @Nullable String arg) {
      // arg optional: existing building uuid to mark; otherwise uses look-targeted designated flow via purpose SHRINE.
      if (arg != null && !arg.isEmpty()) {
         try {
            BuildingId id = new BuildingId(UUID.fromString(arg));
            BuildingInstance building = village.getBuilding(id);
            if (building == null || !building.isOperational()) {
               return ActionResult.fail("religion.millenaire.error.invalid_building");
            }
            ReligionSavedData data = ReligionSavedData.get(level);
            data.communityFor(village.getId().uuid()).designatedShrines().add(id);
            data.setDirty();
            return ActionResult.ok("religion.millenaire.action.shrine_designated");
         } catch (Exception e) {
            return ActionResult.fail("religion.millenaire.error.invalid_building");
         }
      }
      return ActionResult.fail("religion.millenaire.error.need_building_id", BuildingPurpose.SHRINE.id());
   }

   private static boolean withinRange(Village village, ServerPlayer player) {
      return player.blockPosition().distSqr(village.getCenter()) <= MAX_DISTANCE_SQ;
   }

   private static int takeFromPlayer(ServerPlayer player, Item item, int amount) {
      int remaining = amount;
      var inv = player.getInventory();
      for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
         ItemStack stack = inv.getItem(i);
         if (stack.isEmpty() || stack.getItem() != item) {
            continue;
         }
         int take = Math.min(remaining, stack.getCount());
         stack.shrink(take);
         remaining -= take;
      }
      return amount - remaining;
   }
}
