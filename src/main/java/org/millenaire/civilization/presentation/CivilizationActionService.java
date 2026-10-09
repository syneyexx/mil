package org.millenaire.civilization.presentation;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;

/**
 * Server-validated civilization player actions. Client sends intent + stable ids only.
 * LLM / dialogue never authorizes costs, migration or founding.
 */
public final class CivilizationActionService {
   private CivilizationActionService() {
   }

   public record ActionResult(boolean success, String messageKey, @Nullable String detail) {
      public static ActionResult ok(String key) {
         return new ActionResult(true, key, null);
      }

      public static ActionResult ok(String key, String detail) {
         return new ActionResult(true, key, detail);
      }

      public static ActionResult fail(String key) {
         return new ActionResult(false, key, null);
      }

      public void notify(ServerPlayer player) {
         if (player == null) {
            return;
         }
         if (this.detail == null || this.detail.isBlank()) {
            player.displayClientMessage(Component.translatable(this.messageKey), false);
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
      if (level == null || player == null || village == null || action == null) {
         return ActionResult.fail("civilization.millenaire.action.error.invalid");
      }
      if (!ServerPayloadGuards.isWithinVillageReach(player, village)) {
         return ActionResult.fail("civilization.millenaire.action.error.too_far");
      }
      return switch (action) {
         case SUPPORT_NEED -> supportNeed(level, player, village, targetId, npcId);
         case PRIORITIZE_HOUSING -> prioritizeHousing(level, player, village, targetId);
         case VIEW_COLONIZATION -> viewColonization(level, player, village, targetId);
         case SUPPORT_COLONIZATION_MATERIALS -> supportColonizationMaterials(level, player, village, targetId, npcId);
         case AUTHORIZE_COLONIZATION -> authorizeColonization(level, player, village, targetId);
         case VIEW_INTERRUPTED_OPERATION -> viewInterrupted(level, player, village, targetId);
      };
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
      if (needId != null && !needId.isBlank()) {
         need = snap.findById(needId).orElse(null);
      }
      if (need == null) {
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
      for (int i = 0; i < queue.size(); i++) {
         ControlledQueuedProject p = queue.get(i);
         if (p.planSetId() == null) {
            continue;
         }
         if (planSetId != null && !planSetId.isBlank() && !p.planSetId().toString().equals(planSetId)) {
            continue;
         }
         BuildingPlanSet set = ModCultures.getBuildingPlanSet(p.planSetId());
         if (set != null && set.isResidential()) {
            from = i;
            break;
         }
      }
      if (from < 0) {
         return ActionResult.fail("civilization.millenaire.action.blocked.no_housing_project");
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
      ColonizationOperation op = resolveOperation(level, village, operationId);
      if (op == null) {
         boolean founding = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationAutonomousFoundingEnabled.get());
         if (!founding) {
            return ActionResult.fail("civilization.millenaire.action.blocked.colonization_off");
         }
         return ActionResult.fail("civilization.millenaire.action.blocked.no_colonization");
      }
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
      return ActionResult.ok("civilization.millenaire.action.view_colonization.ok", op.statusCode());
   }

   private static ActionResult supportColonizationMaterials(
      ServerLevel level,
      ServerPlayer player,
      Village village,
      @Nullable String needId,
      @Nullable UUID npcId
   ) {
      ColonizationOperation op = ColonizationService.findActiveForParent(level, village.getId().uuid());
      if (op == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.no_colonization");
      }
      // Materials support reuses the live need / request path — never invents a parallel donation inventory.
      return supportNeed(level, player, village, needId, npcId);
   }

   private static ActionResult authorizeColonization(
      ServerLevel level,
      ServerPlayer player,
      Village village,
      @Nullable String operationId
   ) {
      if (!village.isControlledBy(player.getUUID())) {
         return ActionResult.fail("civilization.millenaire.action.blocked.not_controller");
      }
      if (!Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationAutonomousFoundingEnabled.get())) {
         // Never silently flip the master switch.
         return ActionResult.fail("civilization.millenaire.action.blocked.colonization_off");
      }
      if (!WorldgenEngine.isEnabled()) {
         return ActionResult.fail("civilization.millenaire.action.blocked.worldgen_off");
      }
      ColonizationOperation op = resolveOperation(level, village, operationId);
      if (op == null || !op.isActive()) {
         return ActionResult.fail("civilization.millenaire.action.blocked.no_colonization");
      }
      // Revalidate authoritative state before kick — client coordinates are never trusted.
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
      return ActionResult.ok("civilization.millenaire.action.authorize_colonization.ok", op.statusCode());
   }

   private static ActionResult viewInterrupted(
      ServerLevel level,
      ServerPlayer player,
      Village village,
      @Nullable String operationId
   ) {
      ColonizationOperation op = resolveOperation(level, village, operationId);
      if (op == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.no_interrupted");
      }
      player.displayClientMessage(Component.translatable(CivilizationStatusCodes.statusLabelKey(op.statusCode())), false);
      player.displayClientMessage(Component.translatable(CivilizationStatusCodes.reasonLabelKey(op.reason())), false);
      if (!op.refundOutstanding().isEmpty()) {
         player.displayClientMessage(Component.translatable("civilization.millenaire.reason.refund_outstanding"), false);
      }
      // Gameplay-safe only — no overwrite repair of ambiguous worldgen.
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

   @Nullable
   private static ColonizationOperation resolveOperation(ServerLevel level, Village village, @Nullable String operationId) {
      if (operationId != null && !operationId.isBlank()) {
         try {
            UUID id = UUID.fromString(operationId);
            ColonizationOperation op = ColonizationSavedData.get(level).get(id);
            if (op != null) {
               return op;
            }
         } catch (IllegalArgumentException ignored) {
         }
      }
      return ColonizationService.findActiveForParent(level, village.getId().uuid());
   }
}
