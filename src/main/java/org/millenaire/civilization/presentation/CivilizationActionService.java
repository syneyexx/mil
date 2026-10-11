package org.millenaire.civilization.presentation;

import java.util.ArrayList;
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
import org.millenaire.civilization.interaction.InteractionActions;
import org.millenaire.civilization.settlement.ColonizationMaterializationGate;
import org.millenaire.civilization.settlement.ColonizationOperation;
import org.millenaire.civilization.settlement.ColonizationSavedData;
import org.millenaire.civilization.settlement.ColonizationService;
import org.millenaire.civilization.settlement.FoundingCostService;
import org.millenaire.civilization.settlement.PlayerSettlementFoundingService;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.FactionType;
import org.millenaire.faction.ImmigrationPolicy;
import org.millenaire.faction.TradePolicy;
import org.millenaire.faction.diplomacy.AgreementTerminationReason;
import org.millenaire.faction.diplomacy.AgreementType;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.war.War;
import org.millenaire.faction.war.WarGoalType;
import org.millenaire.faction.war.WarSavedData;
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
      if (action.requiresVillageControl() && !village.isControlledBy(player.getUUID())) {
         boolean sovereignOwner = PlayerSettlementFoundingService.isSovereignPlayerSettlement(level, village)
            && village.getOwnerUUID() != null
            && village.getOwnerUUID().equals(player.getUUID());
         if (!sovereignOwner) {
            return ActionResult.fail("civilization.millenaire.action.blocked.not_controller");
         }
      }
      if (action.requiresSovereign()) {
         Faction sovereign = resolveSovereignFaction(level, player, village);
         if (sovereign == null) {
            return ActionResult.fail("civilization.millenaire.action.blocked.not_sovereign");
         }
      }
      ActionResult result = switch (action) {
         case SUPPORT_NEED -> supportNeed(level, player, village, targetId, npcId);
         case PRIORITIZE_HOUSING -> prioritizeHousing(level, player, village, targetId);
         case VIEW_COLONIZATION -> viewColonization(level, player, village, targetId);
         case SUPPORT_COLONIZATION_MATERIALS -> supportColonizationMaterials(level, player, village, targetId);
         case AUTHORIZE_COLONIZATION -> resumeColonization(level, player, village, targetId);
         case VIEW_INTERRUPTED_OPERATION -> viewInterrupted(level, player, village, targetId);
         case SET_IMMIGRATION_POLICY -> setImmigrationPolicy(level, player, village, targetId);
         case SET_TRADE_POLICY -> setTradePolicy(level, player, village, targetId);
         case DECLARE_WAR -> declareWar(level, player, village, targetId);
         case OFFER_PEACE -> offerPeace(level, player, village, targetId);
         case SET_EMBARGO -> setEmbargo(level, player, village, targetId, true);
         case CLEAR_EMBARGO -> setEmbargo(level, player, village, targetId, false);
         case OPEN_CONTROLLED_PROJECTS -> openControlledProjects(level, player, village);
         case ASSIGN_GUARD -> assignGuard(level, player, village, targetId);
         case RELIEVE_GUARD -> relieveGuard(level, player, village, targetId);
         case OFFER_CARAVAN_CHARTER,
            EMERGENCY_RELIEF,
            PAY_BLOOD_MONEY,
            RESPOND_CALL_TO_ARMS,
            REFUSE_CALL_TO_ARMS,
            OFFER_PROTECTORATE,
            ACCEPT_ENVOY,
            DECLINE_ENVOY,
            SUBSIDIZE_CORRIDOR,
            BROKER_PEACE,
            HIRE_MERCENARIES,
            PROPAGANDA_MARKET,
            INTERROGATE_PRISONER,
            PROPOSE_JOINT_WORK,
            OFFER_FOSTERAGE -> executeInteraction(level, player, village, action, targetId);
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
      ColonizationMaterializationGate.bindPlan(plan, op, level);
      FoundingCostService.Coverage coverage = FoundingCostService.assessCoverage(plan, op.reservedStacks());
      if (!coverage.ok() && coverage.blockReason() != null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.cost_content_missing");
      }
      if (coverage.fullyCovered()) {
         return ActionResult.fail("civilization.millenaire.action.blocked.materials_complete");
      }
      Map<ResourceLocation, Integer> missing = coverage.missing();

      List<ItemStack> contributed = new ArrayList<>();
      Inventory inv = player.getInventory();
      for (Entry<ResourceLocation, Integer> e : missing.entrySet()) {
         int want = e.getValue();
         if (AnywoodHelper.isAnywood(e.getKey())) {
            contributed.addAll(removeAnywoodFromPlayer(inv, want));
         } else {
            Item item = BuiltInRegistries.ITEM.get(e.getKey());
            if (item == null || item == Items.AIR) {
               return ActionResult.fail("civilization.millenaire.action.blocked.cost_content_missing");
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
      // Partial escrow is visible; full coverage is recomputed — never treat non-empty as paid.
      op.setResourcesReserved(true);
      ColonizationSavedData.get(level).put(op);

      int total = contributed.stream().mapToInt(ItemStack::getCount).sum();
      FoundingCostService.Coverage after = FoundingCostService.assessCoverage(plan, op.reservedStacks());
      String detail = after.fullyCovered()
         ? Component.translatable("civilization.millenaire.action.support_colonization_materials.complete").getString()
         : Component.translatable(
            "civilization.millenaire.action.support_colonization_materials.remaining",
            after.missing().size()
         ).getString();
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
      ColonizationMaterializationGate.bindPlan(plan, op, level);
      if (!op.costsConsumed() && !FoundingCostService.isOperationFullyCovered(level, op, plan)) {
         op.setReason("awaiting_materials");
         ColonizationSavedData.get(level).put(op);
         return ActionResult.fail("civilization.millenaire.action.blocked.materials_incomplete");
      }
      if (!level.hasChunk(plan.center().getX() >> 4, plan.center().getZ() >> 4)) {
         op.setReason("chunk_not_loaded");
         ColonizationSavedData.get(level).put(op);
         return ActionResult.fail("civilization.millenaire.reason.chunk_not_loaded");
      }
      if (!ColonizationMaterializationGate.allowPhysicalMutation(level, plan)) {
         return ActionResult.fail("civilization.millenaire.action.blocked.colonization_gated");
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

   private static ActionResult executeInteraction(
      ServerLevel level,
      ServerPlayer player,
      Village village,
      CivilizationActionId action,
      @Nullable String targetId
   ) {
      Faction sovereign = resolveSovereignFaction(level, player, village);
      if (sovereign == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.not_sovereign");
      }
      return switch (action) {
         case OFFER_CARAVAN_CHARTER -> InteractionActions.offerCaravanCharter(level, player, village, sovereign, targetId);
         case EMERGENCY_RELIEF -> InteractionActions.emergencyRelief(level, player, village, sovereign, targetId);
         case PAY_BLOOD_MONEY -> InteractionActions.payBloodMoney(level, player, village, sovereign, targetId);
         case RESPOND_CALL_TO_ARMS -> InteractionActions.respondCallToArms(level, player, village, sovereign, targetId, true);
         case REFUSE_CALL_TO_ARMS -> InteractionActions.respondCallToArms(level, player, village, sovereign, targetId, false);
         case OFFER_PROTECTORATE -> InteractionActions.offerProtectorate(level, player, village, sovereign, targetId);
         case ACCEPT_ENVOY -> InteractionActions.respondEnvoy(level, player, village, sovereign, targetId, true);
         case DECLINE_ENVOY -> InteractionActions.respondEnvoy(level, player, village, sovereign, targetId, false);
         case SUBSIDIZE_CORRIDOR -> InteractionActions.subsidizeCorridor(level, player, village, sovereign, targetId);
         case BROKER_PEACE -> InteractionActions.brokerPeace(level, player, village, sovereign, targetId);
         case HIRE_MERCENARIES -> InteractionActions.hireMercenaries(level, player, village, sovereign, targetId);
         case PROPAGANDA_MARKET -> InteractionActions.propagandaMarket(level, player, village, sovereign, targetId);
         case INTERROGATE_PRISONER -> InteractionActions.interrogatePrisoner(level, player, village, sovereign, targetId);
         case PROPOSE_JOINT_WORK -> InteractionActions.proposeJointWork(level, player, village, sovereign, targetId);
         case OFFER_FOSTERAGE -> InteractionActions.offerFosterage(level, player, village, sovereign, targetId);
         default -> ActionResult.fail("civilization.millenaire.action.blocked.unavailable");
      };
   }

   @Nullable
   private static Faction resolveSovereignFaction(ServerLevel level, ServerPlayer player, Village village) {
      if (!PlayerSettlementFoundingService.isSovereignPlayerSettlement(level, village)) {
         return null;
      }
      boolean owner = village.isControlledBy(player.getUUID())
         || (village.getOwnerUUID() != null && village.getOwnerUUID().equals(player.getUUID()));
      if (!owner) {
         return null;
      }
      Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(village.getFactionId());
      if (faction == null || faction.getType() != FactionType.PLAYER) {
         return null;
      }
      if (!faction.isSovereignOwnedBy(player.getUUID())) {
         return null;
      }
      return faction;
   }

   private static ActionResult openControlledProjects(ServerLevel level, ServerPlayer player, Village village) {
      var payload = org.millenaire.village.ControlledProjectsService.buildPayload(village, level);
      net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
         player,
         payload,
         new net.minecraft.network.protocol.common.custom.CustomPacketPayload[0]
      );
      return ActionResult.ok("gui.millenaire.city_admin.open_projects", village.getVillageName());
   }

   private static ActionResult setImmigrationPolicy(ServerLevel level, ServerPlayer player, Village village, @Nullable String targetId) {
      Faction faction = resolveSovereignFaction(level, player, village);
      if (faction == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.not_sovereign");
      }
      ImmigrationPolicy policy = ImmigrationPolicy.tryParse(targetId);
      faction.setImmigrationPolicy(policy);
      FactionSavedData.get(level).setDirty();
      return ActionResult.ok("civilization.millenaire.action.set_immigration_policy.ok", policy.name());
   }

   private static ActionResult setTradePolicy(ServerLevel level, ServerPlayer player, Village village, @Nullable String targetId) {
      Faction faction = resolveSovereignFaction(level, player, village);
      if (faction == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.not_sovereign");
      }
      TradePolicy policy = TradePolicy.tryParse(targetId);
      faction.setTradePolicy(policy);
      FactionSavedData.get(level).setDirty();
      return ActionResult.ok("civilization.millenaire.action.set_trade_policy.ok", policy.name());
   }

   private static ActionResult declareWar(ServerLevel level, ServerPlayer player, Village village, @Nullable String targetId) {
      Faction faction = resolveSovereignFaction(level, player, village);
      if (faction == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.not_sovereign");
      }
      FactionId targetFaction = resolveTargetFactionId(level, targetId);
      if (targetFaction == null || targetFaction.equals(faction.getId())) {
         return ActionResult.fail("civilization.millenaire.action.blocked.invalid_target");
      }
      WarGoalType goal = org.millenaire.civilization.interaction.CasusBelliEvaluator.suggestWarGoal(
         level, faction.getId(), targetFaction
      );
      War war = WarSavedData.get(level).getWarManager().declareWar(level, faction.getId(), targetFaction, goal);
      if (war == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.war_denied");
      }
      int casus = org.millenaire.civilization.interaction.CasusBelliEvaluator.scoreStrength(
         level, faction.getId(), targetFaction
      );
      org.millenaire.civilization.interaction.InteractionSavedData.get(level).addTheaterStory(
         new org.millenaire.civilization.interaction.TheaterStory(
            "casus",
            casus >= 30
               ? "civilization.millenaire.theater.casus_strong"
               : "civilization.millenaire.theater.casus_weak",
            faction.getId().asString(),
            targetFaction.asString(),
            casus >= 30 ? 3 : 4,
            level.getGameTime()
         )
      );
      return ActionResult.ok("civilization.millenaire.action.declare_war.ok", targetFaction.asString());
   }

   private static ActionResult offerPeace(ServerLevel level, ServerPlayer player, Village village, @Nullable String targetId) {
      Faction faction = resolveSovereignFaction(level, player, village);
      if (faction == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.not_sovereign");
      }
      FactionId targetFaction = resolveTargetFactionId(level, targetId);
      if (targetFaction == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.invalid_target");
      }
      if (!WarSavedData.get(level).getWarManager().areAtWar(faction.getId(), targetFaction)) {
         return ActionResult.fail("civilization.millenaire.action.blocked.no_war");
      }
      var wars = WarSavedData.get(level).getWarManager().getWarsInvolving(faction.getId());
      for (War war : wars) {
         if (!war.getState().isOngoing()) {
            continue;
         }
         if (war.getParticipant(targetFaction) == null) {
            continue;
         }
         if (WarSavedData.get(level).getWarManager().beginPeaceNegotiation(level, war.getId())) {
            Faction target = FactionSavedData.get(level).getFactionManager().getFaction(targetFaction);
            if (target != null && target.getCultureId() != null) {
               org.millenaire.faction.crime.CrimeService.onPeaceWithCulture(level, player.getUUID(), target.getCultureId());
            }
            // Soft fosterage bond as a living NAP after peace — no villager transfer required.
            org.millenaire.civilization.interaction.InteractionSavedData.get(level).addFosterage(
               new org.millenaire.civilization.interaction.FosterageBond(
                  faction.getId().asString(),
                  targetFaction.asString(),
                  null,
                  level.getGameTime() + org.millenaire.civilization.interaction.InteractionConfig.FOSTERAGE_DURATION_TICKS,
                  org.millenaire.civilization.interaction.FosterageBond.Kind.GUEST
               )
            );
            return ActionResult.ok("civilization.millenaire.action.offer_peace.ok", war.getId().asString());
         }
      }
      return ActionResult.fail("civilization.millenaire.action.blocked.no_war");
   }

   private static ActionResult setEmbargo(
      ServerLevel level, ServerPlayer player, Village village, @Nullable String targetId, boolean impose
   ) {
      Faction faction = resolveSovereignFaction(level, player, village);
      if (faction == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.not_sovereign");
      }
      FactionId targetFaction = resolveTargetFactionId(level, targetId);
      if (targetFaction == null || targetFaction.equals(faction.getId())) {
         return ActionResult.fail("civilization.millenaire.action.blocked.invalid_target");
      }
      var diplomacy = DiplomacySavedData.get(level).getDiplomaticManager();
      if (impose) {
         var agreement = diplomacy.imposeEmbargo(level, faction.getId(), targetFaction, 0L, "player_sovereign");
         if (agreement == null) {
            return ActionResult.fail("civilization.millenaire.action.blocked.embargo_denied");
         }
         return ActionResult.ok("civilization.millenaire.action.set_embargo.ok", targetFaction.asString());
      }
      boolean cleared = diplomacy.terminateActiveAgreement(
         level, AgreementType.EMBARGO, faction.getId(), targetFaction, AgreementTerminationReason.EXPLICIT
      );
      if (!cleared) {
         return ActionResult.fail("civilization.millenaire.action.blocked.no_embargo");
      }
      return ActionResult.ok("civilization.millenaire.action.clear_embargo.ok", targetFaction.asString());
   }

   private static ActionResult assignGuard(ServerLevel level, ServerPlayer player, Village village, @Nullable String targetId) {
      if (!org.millenaire.civilization.settlement.PlayerSettlementFoundingService.isSovereignPlayerSettlement(level, village)) {
         return ActionResult.fail("civilization.millenaire.action.blocked.not_sovereign");
      }
      UUID villagerId = parseUuid(targetId);
      if (villagerId == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.invalid_target");
      }
      if (!org.millenaire.village.GuardAssignmentService.assignGuard(level, village, villagerId)) {
         return ActionResult.fail("civilization.millenaire.action.blocked.guard_assign_failed");
      }
      return ActionResult.ok("civilization.millenaire.action.assign_guard.ok", targetId);
   }

   private static ActionResult relieveGuard(ServerLevel level, ServerPlayer player, Village village, @Nullable String targetId) {
      if (!org.millenaire.civilization.settlement.PlayerSettlementFoundingService.isSovereignPlayerSettlement(level, village)) {
         return ActionResult.fail("civilization.millenaire.action.blocked.not_sovereign");
      }
      UUID villagerId = parseUuid(targetId);
      if (villagerId == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.invalid_target");
      }
      if (!org.millenaire.village.GuardAssignmentService.relieveGuard(level, village, villagerId)) {
         return ActionResult.fail("civilization.millenaire.action.blocked.guard_relieve_failed");
      }
      return ActionResult.ok("civilization.millenaire.action.relieve_guard.ok", targetId);
   }

   @Nullable
   private static UUID parseUuid(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }
      try {
         return UUID.fromString(raw.trim());
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   @Nullable
   private static FactionId resolveTargetFactionId(ServerLevel level, @Nullable String targetId) {
      if (targetId == null || targetId.isBlank()) {
         return null;
      }
      FactionId direct = FactionId.tryParse(targetId);
      if (direct != null && FactionSavedData.get(level).getFactionManager().getFaction(direct) != null) {
         return direct;
      }
      try {
         UUID villageUuid = UUID.fromString(targetId);
         Village targetVillage = VillageSavedData.get(level).getVillageManager().getVillage(
            new org.millenaire.village.VillageId(villageUuid)
         );
         return targetVillage != null ? targetVillage.getFactionId() : null;
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
