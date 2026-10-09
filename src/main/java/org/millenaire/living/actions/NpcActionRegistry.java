package org.millenaire.living.actions;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.living.needs.VillageNeed;
import org.millenaire.living.needs.VillageNeedsSnapshot;
import org.millenaire.living.LivingVillagesService;
import org.millenaire.living.requests.VillageRequest;
import org.millenaire.living.requests.VillageRequestSavedData;
import org.millenaire.living.requests.VillageRequestState;
import org.slf4j.Logger;

/**
 * Server-side action proposal registry. AI never creates proposals.
 */
public final class NpcActionRegistry {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final long PROPOSAL_TTL = 20L * 60L * 5L; // 5 minutes
   private static final Map<String, NpcActionProposal> PROPOSALS = new ConcurrentHashMap<>();
   private static final Map<String, Long> RECENT_EXECUTIONS = new ConcurrentHashMap<>();

   private NpcActionRegistry() {
   }

   public static List<NpcActionType> availableActions(NpcActionContext ctx) {
      List<NpcActionType> out = new ArrayList<>();
      if (ctx == null || !suggestionsEnabled()) {
         return out;
      }
      if (ctx.millVillager() == null || ctx.village() == null) {
         return out;
      }
      out.add(NpcActionType.VIEW_VILLAGE_STATUS);
      out.add(NpcActionType.VIEW_VILLAGE_NEEDS);
      VillageNeedsSnapshot snap = LivingVillagesService.get().needsEngine().getCached(ctx.villageId());
      if (snap == null) {
         snap = LivingVillagesService.get().needsEngine().evaluate(ctx.level(), ctx.village(), false);
      }
      if (snap != null && snap.mostSevere().filter(n -> !n.shortage().isEmpty()).isPresent()) {
         out.add(NpcActionType.OFFER_ASSISTANCE);
      }
      VillageRequest active = findActiveRequest(ctx);
      if (active != null) {
         out.add(NpcActionType.REVIEW_REQUEST_PROGRESS);
         if (active.state() == VillageRequestState.OFFERED) {
            out.add(NpcActionType.ACCEPT_REQUEST);
            out.add(NpcActionType.DECLINE_REQUEST);
         }
         if (active.state() == VillageRequestState.ACCEPTED || active.state() == VillageRequestState.IN_PROGRESS) {
            out.add(NpcActionType.DELIVER_RESOURCES);
         }
         if (active.state() == VillageRequestState.READY_TO_COMPLETE) {
            out.add(NpcActionType.COMPLETE_REQUEST);
         }
      }
      out.add(NpcActionType.DISCUSS_EXISTING_QUEST);
      out.add(NpcActionType.OPEN_EXISTING_QUEST_INTERACTION);
      out.add(NpcActionType.OPEN_EXISTING_TRADE_INTERACTION);
      return out;
   }

   public static NpcActionProposal createProposal(NpcActionContext ctx, NpcActionType type, @Nullable String targetId) {
      CompoundTag params = new CompoundTag();
      if (targetId != null) {
         params.putString("target", targetId);
      }
      if (type == NpcActionType.OFFER_ASSISTANCE && (targetId == null || targetId.isBlank()) && ctx.village() != null) {
         VillageNeedsSnapshot snap = LivingVillagesService.get().needsEngine().evaluate(ctx.level(), ctx.village(), true);
         VillageNeed need = snap.mostSevere().orElse(null);
         if (need != null) {
            targetId = need.needId();
            params.putString("target", targetId);
         }
      }
      if ((type == NpcActionType.ACCEPT_REQUEST
         || type == NpcActionType.DECLINE_REQUEST
         || type == NpcActionType.DELIVER_RESOURCES
         || type == NpcActionType.REVIEW_REQUEST_PROGRESS
         || type == NpcActionType.COMPLETE_REQUEST)
         && (targetId == null || targetId.isBlank())) {
         VillageRequest active = findActiveRequest(ctx);
         if (active != null) {
            targetId = active.requestId();
            params.putString("target", targetId);
         }
      }
      long now = ctx.level().getGameTime();
      NpcActionProposal proposal = new NpcActionProposal(
         UUID.randomUUID().toString(),
         ctx.sessionId(),
         ctx.player().getUUID(),
         ctx.npcId(),
         ctx.villageId() != null ? ctx.villageId() : new UUID(0L, 0L),
         type,
         targetId,
         params,
         now,
         now + PROPOSAL_TTL,
         1
      );
      PROPOSALS.put(proposal.proposalId(), proposal);
      prune(now);
      return proposal;
   }

   public static NpcActionResult executeProposal(ServerLevel level, ServerPlayer player, int entityId, String proposalId) {
      NpcActionContext ctx = NpcActionValidator.validateSession(level, player, entityId, null);
      if (ctx == null) {
         return NpcActionResult.fail("living.millenaire.action.error.session");
      }
      NpcActionProposal proposal = PROPOSALS.get(proposalId);
      if (proposal == null) {
         return NpcActionResult.fail("living.millenaire.action.error.missing_proposal");
      }
      String idempotencyKey = proposalId + ":" + proposal.stateVersion();
      Long recent = RECENT_EXECUTIONS.get(idempotencyKey);
      long now = level.getGameTime();
      if (recent != null && now - recent < 40L) {
         return NpcActionResult.fail("living.millenaire.action.error.duplicate");
      }
      if (proposal.isExpired(now)) {
         proposal.setState(NpcActionProposal.State.EXPIRED);
         return NpcActionResult.fail("living.millenaire.action.error.expired");
      }
      NpcActionResult result = NpcActionExecutor.execute(ctx, proposal);
      RECENT_EXECUTIONS.put(idempotencyKey, now);
      proposal.setState(result.success() ? NpcActionProposal.State.EXECUTED : NpcActionProposal.State.REJECTED);
      if (MillenaireServerConfig.SERVER.livingVillagesDebugLogging.get()) {
         LOGGER.debug("[Living] Action {} success={} key={}", proposal.actionType(), result.success(), result.messageKey());
      }
      return result;
   }

   public static NpcActionResult executeDirect(ServerLevel level, ServerPlayer player, int entityId, NpcActionType type, @Nullable String targetId) {
      NpcActionContext ctx = NpcActionValidator.validateSession(level, player, entityId, null);
      if (ctx == null) {
         return NpcActionResult.fail("living.millenaire.action.error.session");
      }
      if (!availableActions(ctx).contains(type) && type.mutatesWorld()) {
         // Still allow read-only fallbacks.
         if (type != NpcActionType.VIEW_VILLAGE_NEEDS && type != NpcActionType.VIEW_VILLAGE_STATUS && type != NpcActionType.REVIEW_REQUEST_PROGRESS) {
            return NpcActionResult.fail("living.millenaire.action.error.not_available");
         }
      }
      NpcActionProposal proposal = createProposal(ctx, type, targetId);
      return executeProposal(level, player, entityId, proposal.proposalId());
   }

   @Nullable
   public static NpcActionProposal get(String id) {
      return PROPOSALS.get(id);
   }

   public static void clearPlayer(UUID playerId) {
      PROPOSALS.entrySet().removeIf(e -> e.getValue().playerId().equals(playerId));
   }

   private static VillageRequest findActiveRequest(NpcActionContext ctx) {
      for (VillageRequest r : VillageRequestSavedData.get(ctx.level()).forPlayer(ctx.player().getUUID())) {
         if (VillageRequestState.activeStates().contains(r.state())
            && (ctx.villageId() == null || r.villageId().equals(ctx.villageId()))) {
            return r;
         }
      }
      return null;
   }

   private static void prune(long now) {
      Iterator<Map.Entry<String, NpcActionProposal>> it = PROPOSALS.entrySet().iterator();
      while (it.hasNext()) {
         NpcActionProposal p = it.next().getValue();
         if (p.isExpired(now) || p.state() == NpcActionProposal.State.EXECUTED || p.state() == NpcActionProposal.State.REJECTED) {
            if (now - p.createdGameTime() > PROPOSAL_TTL) {
               it.remove();
            }
         }
      }
      RECENT_EXECUTIONS.entrySet().removeIf(e -> now - e.getValue() > PROPOSAL_TTL);
      if (PROPOSALS.size() > 500) {
         PROPOSALS.clear();
      }
   }

   private static boolean suggestionsEnabled() {
      try {
         return MillenaireServerConfig.SERVER.livingVillagesEnabled.get()
            && MillenaireServerConfig.SERVER.npcDialogueActionSuggestionsEnabled.get();
      } catch (IllegalStateException e) {
         return true;
      }
   }
}
