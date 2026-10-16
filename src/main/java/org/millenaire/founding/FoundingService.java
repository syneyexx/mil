package org.millenaire.founding;

import com.mojang.logging.LogUtils;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.perf.plan.CommitBudget;
import org.millenaire.village.Village;
import org.millenaire.village.VillageEventType;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillageTreasuryService;
import org.millenaire.world.VillageSpawner;
import org.slf4j.Logger;

/**
 * Player-consented founding with real costs and capped open ops.
 * Location planning uses known safe data only (offset from parent) — no chunk scans.
 */
public final class FoundingService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_OPEN_GLOBAL = 4;
   private static final int FOOD_COST = 32;
   private static final int TOOL_COST = 8;
   private static final int DENIER_COST = 128;
   private static final int MIN_DISTANCE = 400;

   private FoundingService() {
   }

   public static void tick(ServerLevel level) {
      FoundingSavedData data = FoundingSavedData.get(level);
      CommitBudget budget = CommitBudget.get();
      // Suggest at most one proposal from a prosperous controlled village (no auto-spawn).
      if (data.openOps().size() < MAX_OPEN_GLOBAL && budget.trySpendCandidate(1)) {
         VillageManager manager = VillageSavedData.get(level).getVillageManager();
         for (Village parent : manager.getAllVillages()) {
            if (!parent.isPlayerControlled() || parent.isLoneBuilding()) {
               continue;
            }
            if (data.openCountForParent(parent.getId().uuid()) > 0) {
               continue;
            }
            if (VillageTreasuryService.balance(level, parent) < DENIER_COST * 2) {
               continue;
            }
            if (!parent.getNeedService().activeNeeds().isEmpty()) {
               continue;
            }
            propose(level, parent, null, null);
            break;
         }
      }
      int advanced = 0;
      for (FoundingOperation op : data.openOps()) {
         if (advanced >= 2 || !budget.trySpendCommit(1)) {
            break;
         }
         if (advance(level, data, op)) {
            advanced++;
            data.setDirty();
         }
      }
   }

   /**
    * Propose a daughter settlement near the parent. Requires player consent before reserves.
    */
   @Nullable
   public static FoundingOperation propose(
      ServerLevel level,
      Village parent,
      @Nullable ServerPlayer player,
      BlockPos preferredCenter
   ) {
      if (parent == null || parent.isLoneBuilding()) {
         return null;
      }
      FoundingSavedData data = FoundingSavedData.get(level);
      if (data.openOps().size() >= MAX_OPEN_GLOBAL || data.openCountForParent(parent.getId().uuid()) >= 1) {
         return null;
      }
      BlockPos center = preferredCenter != null ? preferredCenter : parent.getCenter().offset(MIN_DISTANCE, 0, 0);
      // Reject if too close to an existing village (known registry only — no chunk gen).
      VillageManager manager = VillageSavedData.get(level).getVillageManager();
      for (Village other : manager.getAllVillages()) {
         if (other.getCenter().distSqr(center) < (long) MIN_DISTANCE * MIN_DISTANCE) {
            return null;
         }
      }
      ResourceLocation typeId = parent.getVillageTypeId();
      VillageType type = ModCultures.getVillageType(typeId);
      if (type == null) {
         typeId = ResourceLocation.fromNamespaceAndPath("millenaire", parent.getCultureId().getPath() + "/agricultural");
      }
      FoundingOperation op = new FoundingOperation(
         UUID.randomUUID(),
         parent.getId().uuid(),
         player != null ? player.getUUID() : parent.getOwnerUUID(),
         center,
         parent.getCultureId(),
         typeId,
         level.getGameTime()
      );
      op.setState(FoundingState.PROPOSED);
      op.setReason("awaiting_player_consent");
      data.put(op);
      parent.recordChronicleEvent(level, VillageEventType.FOUNDING, "Proposed settlement", center.toShortString());
      return op;
   }

   public static boolean consent(ServerLevel level, UUID opId, ServerPlayer player) {
      FoundingSavedData data = FoundingSavedData.get(level);
      FoundingOperation op = data.get(opId);
      if (op == null || op.state() != FoundingState.PROPOSED) {
         return false;
      }
      if (op.ownerPlayerId() != null && !op.ownerPlayerId().equals(player.getUUID())) {
         return false;
      }
      op.setState(FoundingState.PLAYER_CONSENT);
      op.setReason("consent_granted");
      data.setDirty();
      return true;
   }

   public static boolean cancel(ServerLevel level, UUID opId, String reason) {
      FoundingSavedData data = FoundingSavedData.get(level);
      FoundingOperation op = data.get(opId);
      if (op == null || !op.isOpen()) {
         return false;
      }
      refund(level, op);
      op.setState(FoundingState.CANCELLED);
      op.setReason(reason == null ? "cancelled" : reason);
      data.setDirty();
      return true;
   }

   private static boolean advance(ServerLevel level, FoundingSavedData data, FoundingOperation op) {
      VillageManager manager = VillageSavedData.get(level).getVillageManager();
      Village parent = null;
      for (Village v : manager.getAllVillages()) {
         if (v.getId().uuid().equals(op.parentVillageId())) {
            parent = v;
            break;
         }
      }
      if (parent == null) {
         op.setState(FoundingState.CANCELLED);
         op.setReason("parent_missing");
         return true;
      }
      return switch (op.state()) {
         case PLAYER_CONSENT -> reserve(level, parent, op);
         case RESERVED -> {
            op.setState(FoundingState.MATERIALIZING);
            op.setReason("materializing");
            yield true;
         }
         case MATERIALIZING -> materialize(level, parent, op);
         case ACTIVATING -> {
            op.setState(FoundingState.DONE);
            op.setReason("done");
            parent.recordChronicleEvent(level, VillageEventType.FOUNDING, "Settlement founded", op.targetCenter().toShortString());
            yield true;
         }
         default -> false;
      };
   }

   private static boolean reserve(ServerLevel level, Village parent, FoundingOperation op) {
      BuildingInstance th = parent.getTownhall();
      BuildingInventory inv = th != null ? th.getInventory() : null;
      if (inv == null) {
         op.setReason("no_townhall");
         return false;
      }
      int bread = inv.getCount(level, Items.BREAD);
      int iron = inv.getCount(level, Items.IRON_INGOT);
      if (bread < FOOD_COST || iron < TOOL_COST) {
         op.setReason("insufficient_goods");
         return false;
      }
      if (!VillageTreasuryService.canAfford(level, parent, DENIER_COST)) {
         op.setReason("insufficient_treasury");
         return false;
      }
      inv.remove(level, Items.BREAD, FOOD_COST);
      inv.remove(level, Items.IRON_INGOT, TOOL_COST);
      VillageTreasuryService.withdraw(level, parent, DENIER_COST, "founding_reserve");
      parent.getBudget().setReservedDeniers(parent.getBudget().reservedDeniers() + DENIER_COST);
      op.setReservations(FOOD_COST, TOOL_COST, DENIER_COST);
      op.setState(FoundingState.RESERVED);
      op.setReason("reserved");
      parent.markDirty();
      return true;
   }

   private static boolean materialize(ServerLevel level, Village parent, FoundingOperation op) {
      // Only spawn when the target chunk is already loaded — never force-load.
      if (!level.isLoaded(op.targetCenter())) {
         op.setReason("waiting_chunks");
         return false;
      }
      VillageType type = ModCultures.getVillageType(op.villageTypeId());
      if (type == null) {
         op.setState(FoundingState.REFUND);
         op.setReason("missing_village_type");
         refund(level, op);
         return true;
      }
      ServerPlayer owner = op.ownerPlayerId() != null
         ? level.getServer().getPlayerList().getPlayer(op.ownerPlayerId())
         : null;
      Component result = VillageSpawner.spawnVillage(
         level,
         op.targetCenter(),
         type,
         0,
         parent.getVillageName(),
         parent.getId(),
         owner
      );
      if (result == null) {
         op.setReason("spawn_failed");
         op.setState(FoundingState.REFUND);
         refund(level, op);
         return true;
      }
      Village spawned = null;
      long best = Long.MAX_VALUE;
      for (Village v : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         long d = (long) v.getCenter().distSqr(op.targetCenter());
         if (d < best) {
            best = d;
            spawned = v;
         }
      }
      parent.getBudget().setReservedDeniers(Math.max(0, parent.getBudget().reservedDeniers() - op.reservedDeniers()));
      op.setState(FoundingState.ACTIVATING);
      op.setReason("activating");
      LOGGER.info(
         "[Founding] Spawned {} from {}",
         spawned != null ? spawned.getVillageName() : "?",
         parent.getVillageName()
      );
      return true;
   }

   private static void refund(ServerLevel level, FoundingOperation op) {
      VillageManager manager = VillageSavedData.get(level).getVillageManager();
      for (Village parent : manager.getAllVillages()) {
         if (!parent.getId().uuid().equals(op.parentVillageId())) {
            continue;
         }
         BuildingInstance th = parent.getTownhall();
         if (th != null && th.getInventory() != null) {
            if (op.reservedFood() > 0) {
               th.getInventory().add(level, Items.BREAD, op.reservedFood());
            }
            if (op.reservedTools() > 0) {
               th.getInventory().add(level, Items.IRON_INGOT, op.reservedTools());
            }
         }
         if (op.reservedDeniers() > 0) {
            VillageTreasuryService.deposit(level, parent, op.reservedDeniers(), "founding_refund");
            parent.getBudget().setReservedDeniers(Math.max(0, parent.getBudget().reservedDeniers() - op.reservedDeniers()));
         }
         op.setReservations(0, 0, 0);
         parent.markDirty();
         return;
      }
   }
}
