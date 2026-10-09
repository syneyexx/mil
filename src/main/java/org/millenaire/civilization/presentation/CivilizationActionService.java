package org.millenaire.civilization.presentation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.millenaire.building.AnywoodHelper;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.civilization.settlement.ColonizationOperation;
import org.millenaire.civilization.settlement.ColonizationSavedData;
import org.millenaire.civilization.settlement.ColonizationService;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.living.LivingVillagesService;
import org.millenaire.living.needs.VillageNeed;
import org.millenaire.living.needs.VillageNeedsSnapshot;
import org.millenaire.living.requests.VillageDeliveryService;
import org.millenaire.living.requests.VillageRequest;
import org.millenaire.living.requests.VillageRequestService;
import org.millenaire.network.ServerPayloadGuards;
import org.millenaire.village.ControlledQueuedProject;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.millenaire.worldgen.WorldgenEngine;
import org.millenaire.worldgen.plan.PlannedBuilding;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;

/**
 * Server-validated civilization player actions. Client sends intent + stable ids only.
 * LLM / dialogue never authorizes costs, migration or founding.
 */
public final class CivilizationActionService {
   private CivilizationActionService() {
   }

   public enum ResolveMode {
      /** Mutating colonization actions — parent village must own the operation. */
      MUTATE_PARENT,
      /** Read-only — parent or linked child colony may view. */
      VIEW_RELATED
   }

   public record OperationResolve(
      @Nullable ColonizationOperation operation,
      @Nullable String errorKey,
      boolean explicitTarget
   ) {
      public static OperationResolve ok(ColonizationOperation op, boolean explicit) {
         return new OperationResolve(op, null, explicit);
      }

      public static OperationResolve fail(String key, boolean explicit) {
         return new OperationResolve(null, key, explicit);
      }

      public boolean ok() {
         return this.operation != null && this.errorKey == null;
      }
   }

   public record ActionResult(boolean success, String messageKey, @Nullable String detail, boolean notifyPlayer) {
      public static ActionResult ok(String key) {
         return new ActionResult(true, key, null, true);
      }

      public static ActionResult ok(String key, String detail) {
         return new ActionResult(true, key, detail, true);
      }

      public static ActionResult okSilent(String key, String detail) {
         return new ActionResult(true, key, detail, false);
      }

      public static ActionResult fail(String key) {
         return new ActionResult(false, key, null, true);
      }

      public void notify(ServerPlayer player) {
         if (player == null || !this.notifyPlayer) {
            return;
         }
         if (this.detail == null || this.detail.isBlank()) {
            player.displayClientMessage(Component.translatable(this.messageKey), false);
         } else if (this.detail.startsWith("civilization.millenaire.") || this.detail.startsWith("living.millenaire.")) {
            player.displayClientMessage(Component.translatable(this.messageKey, Component.translatable(this.detail)), false);
         } else {
            player.displayClientMessage(Component.translatable(this.messageKey, this.detail), false);
         }
      }
   }

   public static ActionResult execute(
      ServerLevel level,
      ServerPlayer player,
      Village village,
      CivilizationActionId action,
      @Nullable String targetId,
      @Nullable UUID npcId
   ) {
      return execute(level, player, village, action, targetId, npcId, true);
   }

   public static ActionResult execute(
      ServerLevel level,
      ServerPlayer player,
      Village village,
      CivilizationActionId action,
      @Nullable String targetId,
      @Nullable UUID npcId,
      boolean sendPlayerFeedback
   ) {
      if (level == null || player == null || village == null || action == null) {
         return ActionResult.fail("civilization.millenaire.action.error.invalid");
      }
      if (!ServerPayloadGuards.isWithinVillageReach(player, village)) {
         return ActionResult.fail("civilization.millenaire.action.error.too_far");
      }
      ActionResult result = switch (action) {
         case SUPPORT_NEED -> supportNeed(level, player, village, targetId, npcId);
         case PRIORITIZE_HOUSING -> prioritizeHousing(level, player, village, targetId);
         case VIEW_COLONIZATION -> viewColonization(level, player, village, targetId);
         case SUPPORT_COLONIZATION_MATERIALS -> supportColonizationMaterials(level, player, village, targetId);
         case AUTHORIZE_COLONIZATION -> resumeColonization(level, player, village, targetId);
         case VIEW_INTERRUPTED_OPERATION -> viewInterrupted(level, player, village, targetId);
      };
      if (!sendPlayerFeedback) {
         return new ActionResult(result.success(), result.messageKey(), result.detail(), false);
      }
      return result;
   }

   private static ActionResult supportNeed(
      ServerLevel level,
      ServerPlayer player,
      Village village,
      @Nullable String needId,
      @Nullable UUID npcId
   ) {
      VillageNeedsSnapshot snap = LivingVillagesService.get().needsEngine().evaluate(level, village, true);
      VillageNeed need = null;
      boolean explicit = needId != null && !needId.isBlank();
      if (explicit) {
         need = snap.findById(needId).orElse(null);
         if (need == null) {
            return ActionResult.fail("civilization.millenaire.action.blocked.need_not_found");
         }
      } else {
         need = snap.mostSevere().orElse(null);
      }
      if (need == null || need.shortage().isEmpty()) {
         return ActionResult.fail("civilization.millenaire.action.blocked.no_need");
      }
      UUID issuer = npcId != null ? npcId : player.getUUID();
      VillageRequest request = VillageRequestService.offerFromNeed(
         level, player, village, issuer, need, LivingVillagesService.get().needsEngine()
      );
      if (request == null) {
         return ActionResult.fail("living.millenaire.action.error.offer_failed");
      }
      if (!VillageRequestService.accept(level, player, request.requestId())) {
         return ActionResult.ok("civilization.millenaire.action.support_need.offered", request.requestId());
      }
      VillageDeliveryService.DeliveryResult delivered = VillageRequestService.deliver(
         level, player, request.requestId(), LivingVillagesService.get().needsEngine()
      );
      if (!delivered.success()) {
         return ActionResult.ok("civilization.millenaire.action.support_need.accepted_waiting", delivered.messageKey());
      }
      return ActionResult.ok("civilization.millenaire.action.support_need.delivered", delivered.messageKey());
   }

   private static ActionResult prioritizeHousing(
      ServerLevel level,
      ServerPlayer player,
      Village village,
      @Nullable String planSetId
   ) {
      if (!village.isControlledBy(player.getUUID())) {
         return ActionResult.fail("civilization.millenaire.action.blocked.not_controller");
      }
      List<ControlledQueuedProject> queue = village.getControlledQueue();
      int from = -1;
      boolean explicit = planSetId != null && !planSetId.isBlank();
      for (int i = 0; i < queue.size(); i++) {
         ControlledQueuedProject p = queue.get(i);
         if (p.planSetId() == null) {
            continue;
         }
         if (explicit && !p.planSetId().toString().equals(planSetId)) {
            continue;
         }
         BuildingPlanSet set = ModCultures.getBuildingPlanSet(p.planSetId());
         if (set != null && set.isResidential()) {
            from = i;
            break;
         }
      }
      if (from < 0) {
         return ActionResult.fail(
            explicit
               ? "civilization.millenaire.action.blocked.target_not_found"
               : "civilization.millenaire.action.blocked.no_housing_project"
         );
      }
      if (from == 0) {
         return ActionResult.ok("civilization.millenaire.action.prioritize_housing.already_first");
      }
      village.reorderControlledProject(from, 0);
      VillageSavedData.get(level).setDirty();
      return ActionResult.ok("civilization.millenaire.action.prioritize_housing.ok");
   }

   private static ActionResult viewColonization(
      ServerLevel level,
      ServerPlayer player,
      Village village,
      @Nullable String operationId
   ) {
      OperationResolve resolved = resolveOperation(level, village, operationId, ResolveMode.VIEW_RELATED);
      if (!resolved.ok()) {
         boolean founding = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationAutonomousFoundingEnabled.get());
         if (resolved.errorKey() != null) {
            return ActionResult.fail(resolved.errorKey());
         }
         if (!founding) {
            return ActionResult.fail("civilization.millenaire.action.blocked.colonization_off");
         }
         return ActionResult.fail("civilization.millenaire.action.blocked.no_colonization");
      }
      ColonizationOperation op = resolved.operation();
      String reasonKey = CivilizationStatusCodes.reasonLabelKey(op.reason());
      String statusKey = CivilizationStatusCodes.statusLabelKey(op.statusCode());
      player.displayClientMessage(Component.translatable(statusKey), false);
      player.displayClientMessage(Component.translatable(reasonKey), false);
      player.displayClientMessage(
         Component.translatable(
            "civilization.millenaire.action.view_colonization.founders",
            op.migratedCount(),
            op.founderTarget()
         ),
         false
      );
      player.displayClientMessage(Component.translatable(CivilizationStatusCodes.nextStepKey(op.reason())), false);
      return ActionResult.ok("civilization.millenaire.action.view_colonization.ok", statusKey);
   }

   /**
    * Contribute concrete stacks from the player inventory toward the selected colonization
    * operation's authoritative founding costs. Never falls back to an unrelated village need.
    */
   private static ActionResult supportColonizationMaterials(
      ServerLevel level,
      ServerPlayer player,
      Village village,
      @Nullable String operationId
   ) {
      OperationResolve resolved = resolveOperation(level, village, operationId, ResolveMode.MUTATE_PARENT);
      if (!resolved.ok()) {
         return ActionResult.fail(resolved.errorKey() != null ? resolved.errorKey() : "civilization.millenaire.action.blocked.no_colonization");
      }
      ColonizationOperation op = resolved.operation();
      if (!op.isActive()) {
         return ActionResult.fail("civilization.millenaire.action.blocked.operation_terminal");
      }
      if (op.costsConsumed()) {
         return ActionResult.fail("civilization.millenaire.action.blocked.materials_already_consumed");
      }
      if (op.status() == ColonizationOperation.Status.FAILED || op.status() == ColonizationOperation.Status.CANCELLED) {
         return ActionResult.fail("civilization.millenaire.action.blocked.operation_terminal");
      }

      SettlementPlan plan = SettlementPlanSavedData.get(level).getPlan(op.planId());
      if (plan == null) {
         return ActionResult.fail("civilization.millenaire.action.error.plan_invalid");
      }
      Map<ResourceLocation, Integer> costs = computeFoundingCosts(plan);
      Map<ResourceLocation, Integer> already = countStacks(op.reservedStacks());
      Map<ResourceLocation, Integer> missing = new LinkedHashMap<>();
      for (Entry<ResourceLocation, Integer> e : costs.entrySet()) {
         int have = already.getOrDefault(e.getKey(), 0);
         // Anywood reserved as concrete logs still count against anywood need.
         if (AnywoodHelper.isAnywood(e.getKey())) {
            have = countAnywood(op.reservedStacks());
         }
         int need = Math.max(0, e.getValue() - have);
         if (need > 0) {
            missing.put(e.getKey(), need);
         }
      }
      if (missing.isEmpty()) {
         return ActionResult.fail("civilization.millenaire.action.blocked.materials_complete");
      }

      List<ItemStack> contributed = new ArrayList<>();
      Inventory inv = player.getInventory();
      for (Entry<ResourceLocation, Integer> e : missing.entrySet()) {
         int want = e.getValue();
         if (AnywoodHelper.isAnywood(e.getKey()) || e.getKey().equals(BuiltInRegistries.ITEM.getKey(Items.OAK_LOG))) {
            contributed.addAll(removeAnywoodFromPlayer(inv, want));
         } else {
            Item item = BuiltInRegistries.ITEM.get(e.getKey());
            if (item == null || item == Items.AIR) {
               continue;
            }
            contributed.addAll(removeItemFromPlayer(inv, item, want));
         }
      }
      if (contributed.isEmpty()) {
         return ActionResult.fail("civilization.millenaire.action.blocked.no_matching_materials");
      }

      for (ItemStack stack : contributed) {
         if (stack != null && !stack.isEmpty()) {
            op.reservedStacks().add(stack.copy());
         }
      }
      op.setResourcesReserved(true);
      ColonizationSavedData.get(level).put(op);

      int total = contributed.stream().mapToInt(ItemStack::getCount).sum();
      Map<ResourceLocation, Integer> stillMissing = new LinkedHashMap<>();
      Map<ResourceLocation, Integer> after = countStacks(op.reservedStacks());
      for (Entry<ResourceLocation, Integer> e : costs.entrySet()) {
         int have = AnywoodHelper.isAnywood(e.getKey()) ? countAnywood(op.reservedStacks()) : after.getOrDefault(e.getKey(), 0);
         int need = Math.max(0, e.getValue() - have);
         if (need > 0) {
            stillMissing.put(e.getKey(), need);
         }
      }
      String detail = stillMissing.isEmpty()
         ? Component.translatable("civilization.millenaire.action.support_colonization_materials.complete").getString()
         : Component.translatable("civilization.millenaire.action.support_colonization_materials.remaining", stillMissing.size()).getString();
      return ActionResult.ok(
         "civilization.millenaire.action.support_colonization_materials.ok",
         total + " — " + detail
      );
   }

   /**
    * Resume an already-running colonization that needs another kick (loaded chunks / plan execution).
    * Does not store a separate approval gate — autonomous founding remains automatic.
    * Legacy AUTHORIZE_COLONIZATION id maps here for stored proposals / packets.
    */
   private static ActionResult resumeColonization(
      ServerLevel level,
      ServerPlayer player,
      Village village,
      @Nullable String operationId
   ) {
      if (!village.isControlledBy(player.getUUID())) {
         return ActionResult.fail("civilization.millenaire.action.blocked.not_controller");
      }
      OperationResolve resolved = resolveOperation(level, village, operationId, ResolveMode.MUTATE_PARENT);
      if (!resolved.ok()) {
         return ActionResult.fail(resolved.errorKey() != null ? resolved.errorKey() : "civilization.millenaire.action.blocked.no_colonization");
      }
      ColonizationOperation op = resolved.operation();
      if (!op.isActive()) {
         return ActionResult.fail("civilization.millenaire.action.blocked.operation_terminal");
      }
      if (!isResumable(op)) {
         return ActionResult.fail("civilization.millenaire.action.blocked.not_resumable");
      }
      if (!WorldgenEngine.isEnabled()) {
         return ActionResult.fail("civilization.millenaire.action.blocked.worldgen_off");
      }

      // Revalidate authoritative state immediately before mutation — snapshots are not proof.
      if (!village.isControlledBy(player.getUUID())) {
         return ActionResult.fail("civilization.millenaire.action.blocked.not_controller");
      }
      if (!op.parentVillageId().equals(village.getId().uuid())) {
         return ActionResult.fail("civilization.millenaire.action.blocked.operation_wrong_village");
      }
      SettlementPlan plan = SettlementPlanSavedData.get(level).getPlan(op.planId());
      if (plan == null || plan.status().isTerminalFailure()) {
         return ActionResult.fail("civilization.millenaire.action.error.plan_invalid");
      }
      if (!level.hasChunk(plan.center().getX() >> 4, plan.center().getZ() >> 4)) {
         op.setReason("chunk_not_loaded");
         ColonizationSavedData.get(level).put(op);
         return ActionResult.fail("civilization.millenaire.reason.chunk_not_loaded");
      }
      WorldgenEngine.kickPlanExecution(level, plan);
      ColonizationSavedData.get(level).put(op);
      return ActionResult.ok("civilization.millenaire.action.resume_colonization.ok", CivilizationStatusCodes.statusLabelKey(op.statusCode()));
   }

   private static boolean isResumable(ColonizationOperation op) {
      if (op == null || !op.isActive()) {
         return false;
      }
      return switch (op.status()) {
         case PLANNED, AWAITING_CHUNKS, LAYOUT, RESERVED, MATERIALIZING, ACTIVATED, MIGRATING -> true;
         default -> false;
      };
   }

   private static ActionResult viewInterrupted(
      ServerLevel level,
      ServerPlayer player,
      Village village,
      @Nullable String operationId
   ) {
      OperationResolve resolved = resolveOperation(level, village, operationId, ResolveMode.VIEW_RELATED);
      if (!resolved.ok()) {
         return ActionResult.fail(resolved.errorKey() != null ? resolved.errorKey() : "civilization.millenaire.action.blocked.no_interrupted");
      }
      ColonizationOperation op = resolved.operation();
      player.displayClientMessage(Component.translatable(CivilizationStatusCodes.statusLabelKey(op.statusCode())), false);
      player.displayClientMessage(Component.translatable(CivilizationStatusCodes.reasonLabelKey(op.reason())), false);
      if (!op.refundOutstanding().isEmpty()) {
         player.displayClientMessage(Component.translatable("civilization.millenaire.reason.refund_outstanding"), false);
      }
      boolean canRetry = op.isActive()
         || (!op.refundOutstanding().isEmpty())
         || "chunk_not_loaded".equals(CivilizationStatusCodes.normalizeReason(op.reason()));
      if (canRetry) {
         player.displayClientMessage(Component.translatable("civilization.millenaire.next.retry_when_loaded"), false);
      } else {
         player.displayClientMessage(Component.translatable("civilization.millenaire.next.no_safe_retry"), false);
      }
      return ActionResult.ok("civilization.millenaire.action.view_interrupted.ok");
   }

   /**
    * Resolve a colonization operation against the acting village.
    * Explicit invalid / foreign IDs never fall back to another active operation.
    */
   public static OperationResolve resolveOperation(
      ServerLevel level,
      Village village,
      @Nullable String operationId,
      ResolveMode mode
   ) {
      boolean explicit = operationId != null && !operationId.isBlank();
      if (explicit) {
         UUID id;
         try {
            id = UUID.fromString(operationId.trim());
         } catch (IllegalArgumentException e) {
            return OperationResolve.fail("civilization.millenaire.action.blocked.invalid_target", true);
         }
         ColonizationOperation op = ColonizationSavedData.get(level).get(id);
         if (op == null) {
            return OperationResolve.fail("civilization.millenaire.action.blocked.target_not_found", true);
         }
         UUID villageId = village.getId().uuid();
         boolean parent = villageId.equals(op.parentVillageId());
         boolean child = isChildColony(level, villageId, op);
         if (mode == ResolveMode.MUTATE_PARENT) {
            if (!parent) {
               return OperationResolve.fail("civilization.millenaire.action.blocked.operation_wrong_village", true);
            }
         } else if (!parent && !child) {
            return OperationResolve.fail("civilization.millenaire.action.blocked.operation_wrong_village", true);
         }
         return OperationResolve.ok(op, true);
      }
      ColonizationOperation active = ColonizationService.findActiveForParent(level, village.getId().uuid());
      if (active != null) {
         return OperationResolve.ok(active, false);
      }
      if (mode == ResolveMode.VIEW_RELATED) {
         for (ColonizationOperation candidate : ColonizationService.listForVillage(level, village.getId().uuid(), 4)) {
            return OperationResolve.ok(candidate, false);
         }
      }
      return OperationResolve.fail("civilization.millenaire.action.blocked.no_colonization", false);
   }

   private static boolean isChildColony(ServerLevel level, UUID villageId, ColonizationOperation op) {
      SettlementPlan plan = SettlementPlanSavedData.get(level).getPlan(op.planId());
      return plan != null && villageId.equals(plan.villageUuid());
   }

   private static Map<ResourceLocation, Integer> computeFoundingCosts(SettlementPlan plan) {
      Map<ResourceLocation, Integer> costs = new LinkedHashMap<>();
      for (PlannedBuilding building : plan.buildings()) {
         if (building.planSetId() == null) {
            continue;
         }
         BuildingPlanSet set = ModCultures.getBuildingPlanSet(building.planSetId());
         if (set == null) {
            continue;
         }
         BuildingPlanSet.LevelDef level0 = set.getLevel(building.variant(), Math.max(0, building.level()));
         if (level0 == null || level0.requiredResources() == null) {
            continue;
         }
         for (Entry<ResourceLocation, Integer> e : level0.requiredResources().entrySet()) {
            costs.merge(e.getKey(), e.getValue(), Integer::sum);
         }
      }
      if (costs.isEmpty()) {
         costs.put(AnywoodHelper.ANYWOOD_LOG, 32);
         costs.put(BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE), 32);
      }
      return costs;
   }

   private static Map<ResourceLocation, Integer> countStacks(List<ItemStack> stacks) {
      Map<ResourceLocation, Integer> map = new LinkedHashMap<>();
      for (ItemStack stack : stacks) {
         if (stack == null || stack.isEmpty()) {
            continue;
         }
         map.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), Integer::sum);
      }
      return map;
   }

   private static int countAnywood(List<ItemStack> stacks) {
      int n = 0;
      for (ItemStack stack : stacks) {
         if (stack != null && !stack.isEmpty() && stack.is(AnywoodHelper.LOGS_TAG)) {
            n += stack.getCount();
         }
      }
      return n;
   }

   private static List<ItemStack> removeItemFromPlayer(Inventory inv, Item item, int amount) {
      List<ItemStack> taken = new ArrayList<>();
      int left = amount;
      for (int slot = 0; slot < inv.getContainerSize() && left > 0; slot++) {
         ItemStack stack = inv.getItem(slot);
         if (stack.isEmpty() || !stack.is(item)) {
            continue;
         }
         int take = Math.min(left, stack.getCount());
         taken.add(stack.split(take).copy());
         left -= take;
      }
      return taken;
   }

   private static List<ItemStack> removeAnywoodFromPlayer(Inventory inv, int amount) {
      List<ItemStack> taken = new ArrayList<>();
      int left = amount;
      for (int slot = 0; slot < inv.getContainerSize() && left > 0; slot++) {
         ItemStack stack = inv.getItem(slot);
         if (stack.isEmpty() || !stack.is(AnywoodHelper.LOGS_TAG)) {
            continue;
         }
         int take = Math.min(left, stack.getCount());
         taken.add(stack.split(take).copy());
         left -= take;
      }
      return taken;
   }
}
