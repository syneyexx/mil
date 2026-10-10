package org.millenaire.civilization.settlement;

import com.mojang.logging.LogUtils;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;
import org.slf4j.Logger;

/**
 * Server-owned gate shared by every physical colonization mutation entry:
 * {@code onNewChunk}, {@code onChunkAvailable}, resume queue, {@code kickPlanExecution},
 * materializer, and activator.
 *
 * <p>Natural settlement plans are unaffected. Colonization plans require a linked (or
 * unambiguously mapped) operation with full founding-cost coverage before any block write
 * or activation. Ambiguous legacy progress is blocked — goods/villagers are never invented.
 */
public final class ColonizationMaterializationGate {
   private static final Logger LOGGER = LogUtils.getLogger();

   private ColonizationMaterializationGate() {
   }

   public enum Decision {
      ALLOW_NATURAL,
      ALLOW_PAID,
      DENY_MISSING_OP,
      DENY_TERMINAL_OP,
      DENY_UNPAID,
      DENY_UNRESOLVED_STACKS,
      DENY_AMBIGUOUS
   }

   public static Decision evaluate(ServerLevel level, SettlementPlan plan) {
      if (level == null || plan == null || !plan.isColonizationFounding()) {
         return Decision.ALLOW_NATURAL;
      }
      ColonizationOperation op = resolveOperation(level, plan);
      if (op == null) {
         return Decision.DENY_MISSING_OP;
      }
      if (op.status() == ColonizationOperation.Status.FAILED
         || op.status() == ColonizationOperation.Status.CANCELLED) {
         return Decision.DENY_TERMINAL_OP;
      }
      if (op.decodeUnresolved()) {
         return Decision.DENY_UNRESOLVED_STACKS;
      }
      if (op.costsConsumed()) {
         // Build already started — continue physical work; refunds of unused levy are blocked elsewhere.
         return Decision.ALLOW_PAID;
      }
      if (op.status() == ColonizationOperation.Status.MIGRATED
         || op.status() == ColonizationOperation.Status.ACTIVATED
         || op.status() == ColonizationOperation.Status.MIGRATING) {
         return Decision.ALLOW_PAID;
      }
      if (!FoundingCostService.isOperationFullyCovered(level, op, plan)) {
         return Decision.DENY_UNPAID;
      }
      return Decision.ALLOW_PAID;
   }

   public static boolean allowPhysicalMutation(ServerLevel level, SettlementPlan plan) {
      Decision d = evaluate(level, plan);
      return d == Decision.ALLOW_NATURAL || d == Decision.ALLOW_PAID;
   }

   public static boolean allowActivation(ServerLevel level, SettlementPlan plan) {
      return allowPhysicalMutation(level, plan);
   }

   /**
    * Record that the first real physical mutation for this colonization plan occurred.
    * Marks escrow consumed — must not wait for a later civilization stage.
    */
   public static void notePhysicalMutation(ServerLevel level, SettlementPlan plan) {
      if (level == null || plan == null || !plan.isColonizationFounding()) {
         return;
      }
      ColonizationOperation op = resolveOperation(level, plan);
      if (op == null || op.costsConsumed()) {
         return;
      }
      if (!FoundingCostService.isOperationFullyCovered(level, op, plan) && !op.costsConsumed()) {
         // Should be unreachable when callers respect the gate; refuse silent consumption.
         LOGGER.warn(
            "[Millenaire] Refusing costsConsumed for unpaid colonization op {} plan {}",
            op.operationId(),
            plan.planId()
         );
         return;
      }
      op.setCostsConsumed(true);
      op.reservedStacks().clear();
      op.setResourcesReserved(false);
      if (op.status() == ColonizationOperation.Status.RESERVED
         || op.status() == ColonizationOperation.Status.LAYOUT
         || op.status() == ColonizationOperation.Status.AWAITING_CHUNKS
         || op.status() == ColonizationOperation.Status.PLANNED) {
         op.setStatus(ColonizationOperation.Status.MATERIALIZING);
      }
      ColonizationSavedData.get(level).put(op);
   }

   public static void bindPlan(SettlementPlan plan, ColonizationOperation op, ServerLevel level) {
      if (plan == null || op == null) {
         return;
      }
      plan.setColonizationOperationId(op.operationId());
      plan.setFoundingMode(SettlementPlan.FoundingMode.COLONIZATION);
      if (op.factionId() != null && !op.factionId().isBlank()) {
         plan.setFoundingFactionId(op.factionId());
      }
      SettlementPlanSavedData.get(level).markDirtyPlan();
   }

   @Nullable
   public static ColonizationOperation resolveOperation(ServerLevel level, SettlementPlan plan) {
      if (level == null || plan == null) {
         return null;
      }
      ColonizationSavedData store = ColonizationSavedData.get(level);
      UUID linked = plan.colonizationOperationId();
      if (linked != null) {
         ColonizationOperation op = store.get(linked);
         if (op != null && op.planId().equals(plan.planId())) {
            return op;
         }
         if (op != null) {
            // Linked id points at a different plan — ambiguous.
            return null;
         }
      }
      // Legacy unambiguous mapping: exactly one active op for this planId.
      ColonizationOperation found = null;
      for (ColonizationOperation candidate : store.operations().values()) {
         if (candidate != null && plan.planId().equals(candidate.planId())) {
            if (found != null) {
               return null; // ambiguous duplicates
            }
            found = candidate;
         }
      }
      if (found != null && plan.colonizationOperationId() == null) {
         plan.setColonizationOperationId(found.operationId());
         SettlementPlanSavedData.get(level).markDirtyPlan();
      }
      return found;
   }
}
