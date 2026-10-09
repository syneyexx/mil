package org.millenaire.living.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.living.LivingVillagesService;
import org.millenaire.living.needs.VillageNeed;
import org.millenaire.living.needs.VillageNeedsSnapshot;
import org.millenaire.living.requests.VillageDeliveryService;
import org.millenaire.living.requests.VillageRequest;
import org.millenaire.living.requests.VillageRequestSavedData;
import org.millenaire.living.requests.VillageRequestService;
import org.millenaire.living.requests.VillageRequestState;
import org.millenaire.entity.VillagerInteraction;
import org.millenaire.village.Village;

public final class NpcActionExecutor {
   private NpcActionExecutor() {
   }

   public static NpcActionResult execute(NpcActionContext ctx, NpcActionProposal proposal) {
      if (!NpcActionValidator.validateProposal(ctx, proposal)) {
         return NpcActionResult.fail("living.millenaire.action.error.invalid_proposal");
      }
      return switch (proposal.actionType()) {
         case VIEW_VILLAGE_NEEDS -> viewNeeds(ctx);
         case OFFER_ASSISTANCE -> offerAssistance(ctx, proposal);
         case ACCEPT_REQUEST -> accept(ctx, proposal);
         case DECLINE_REQUEST -> decline(ctx, proposal);
         case DELIVER_RESOURCES -> deliver(ctx, proposal);
         case REVIEW_REQUEST_PROGRESS -> review(ctx, proposal);
         case COMPLETE_REQUEST -> complete(ctx, proposal);
         case DISCUSS_EXISTING_QUEST -> discussQuest(ctx);
         case OPEN_EXISTING_QUEST_INTERACTION -> openQuest(ctx);
         case OPEN_EXISTING_TRADE_INTERACTION -> openTrade(ctx);
         case VIEW_VILLAGE_STATUS -> viewStatus(ctx);
         case REPORT_WORLD_EVENT -> NpcActionResult.fail("living.millenaire.action.error.report_not_from_dialogue");
         case SUPPORT_VILLAGE_NEED -> civilizationAction(
            ctx, proposal, org.millenaire.civilization.presentation.CivilizationActionId.SUPPORT_NEED
         );
         case PRIORITIZE_HOUSING_PROJECT -> civilizationAction(
            ctx, proposal, org.millenaire.civilization.presentation.CivilizationActionId.PRIORITIZE_HOUSING
         );
         case VIEW_COLONIZATION_PROPOSAL -> civilizationAction(
            ctx, proposal, org.millenaire.civilization.presentation.CivilizationActionId.VIEW_COLONIZATION
         );
         case SUPPORT_COLONIZATION_MATERIALS -> civilizationAction(
            ctx, proposal, org.millenaire.civilization.presentation.CivilizationActionId.SUPPORT_COLONIZATION_MATERIALS
         );
         case AUTHORIZE_COLONIZATION_PROPOSAL -> civilizationAction(
            ctx, proposal, org.millenaire.civilization.presentation.CivilizationActionId.AUTHORIZE_COLONIZATION
         );
         case VIEW_INTERRUPTED_OPERATION -> civilizationAction(
            ctx, proposal, org.millenaire.civilization.presentation.CivilizationActionId.VIEW_INTERRUPTED_OPERATION
         );
      };
   }

   private static NpcActionResult civilizationAction(
      NpcActionContext ctx,
      NpcActionProposal proposal,
      org.millenaire.civilization.presentation.CivilizationActionId actionId
   ) {
      if (ctx.village() == null) {
         return NpcActionResult.fail("living.millenaire.action.error.no_village");
      }
      org.millenaire.civilization.presentation.CivilizationActionService.ActionResult result =
         org.millenaire.civilization.presentation.CivilizationActionService.execute(
            ctx.level(),
            ctx.player(),
            ctx.village(),
            actionId,
            proposal.targetId(),
            ctx.npcId()
         );
      result.notify(ctx.player());
      return result.success()
         ? NpcActionResult.ok(result.messageKey(), result.detail() == null ? "" : result.detail())
         : NpcActionResult.fail(result.messageKey());
   }

   private static NpcActionResult viewNeeds(NpcActionContext ctx) {
      if (ctx.village() == null) {
         return NpcActionResult.fail("living.millenaire.action.error.no_village");
      }
      VillageNeedsSnapshot snap = LivingVillagesService.get().needsEngine().evaluate(ctx.level(), ctx.village(), true);
      if (snap.isEmpty()) {
         return NpcActionResult.ok("living.millenaire.action.needs.none");
      }
      List<String> lines = new ArrayList<>();
      for (VillageNeed need : snap.needs()) {
         lines.add(need.type().name() + ":" + need.severity().name() + ":" + need.summaryDetail() + " short=" + need.totalShortage());
      }
      String detail = String.join(" | ", lines);
      ctx.player().displayClientMessage(Component.translatable("living.millenaire.action.needs.header"), false);
      for (VillageNeed need : snap.needs()) {
         ctx.player().displayClientMessage(
            Component.translatable(need.summaryKey(), need.summaryDetail(), need.severity().name(), need.totalShortage()),
            false
         );
      }
      return NpcActionResult.ok("living.millenaire.action.needs.ok", detail);
   }

   private static NpcActionResult offerAssistance(NpcActionContext ctx, NpcActionProposal proposal) {
      if (ctx.village() == null || ctx.millVillager() == null) {
         return NpcActionResult.fail("living.millenaire.action.error.no_village");
      }
      VillageNeedsSnapshot snap = LivingVillagesService.get().needsEngine().evaluate(ctx.level(), ctx.village(), true);
      VillageNeed need = null;
      if (proposal.targetId() != null) {
         need = snap.findById(proposal.targetId()).orElse(null);
      }
      if (need == null) {
         need = snap.mostSevere().orElse(null);
      }
      if (need == null || need.shortage().isEmpty()) {
         return NpcActionResult.fail("living.millenaire.action.error.no_assistance");
      }
      VillageRequest request = VillageRequestService.offerFromNeed(
         ctx.level(), ctx.player(), ctx.village(), ctx.npcId(), need, LivingVillagesService.get().needsEngine()
      );
      if (request == null) {
         return NpcActionResult.fail("living.millenaire.action.error.offer_failed");
      }
      summarizeRequest(ctx.player(), request);
      return NpcActionResult.okWithFollowUp("living.millenaire.action.offer.ok", request.requestId());
   }

   private static NpcActionResult accept(NpcActionContext ctx, NpcActionProposal proposal) {
      String id = proposal.targetId();
      if (id == null || id.isBlank()) {
         return NpcActionResult.fail("living.millenaire.action.error.missing_request");
      }
      if (!VillageRequestService.accept(ctx.level(), ctx.player(), id)) {
         return NpcActionResult.fail("living.millenaire.action.error.accept_failed");
      }
      return NpcActionResult.ok("living.millenaire.action.accept.ok", id);
   }

   private static NpcActionResult decline(NpcActionContext ctx, NpcActionProposal proposal) {
      String id = proposal.targetId();
      if (id == null || id.isBlank()) {
         return NpcActionResult.fail("living.millenaire.action.error.missing_request");
      }
      if (!VillageRequestService.decline(ctx.level(), ctx.player(), id)) {
         return NpcActionResult.fail("living.millenaire.action.error.decline_failed");
      }
      return NpcActionResult.ok("living.millenaire.action.decline.ok");
   }

   private static NpcActionResult deliver(NpcActionContext ctx, NpcActionProposal proposal) {
      String id = proposal.targetId();
      if (id == null || id.isBlank()) {
         return NpcActionResult.fail("living.millenaire.action.error.missing_request");
      }
      VillageDeliveryService.DeliveryResult result = VillageRequestService.deliver(
         ctx.level(), ctx.player(), id, LivingVillagesService.get().needsEngine()
      );
      if (!result.success()) {
         return NpcActionResult.fail(result.messageKey());
      }
      StringBuilder sb = new StringBuilder();
      for (Entry<ResourceLocation, Integer> e : result.transferred().entrySet()) {
         if (!sb.isEmpty()) {
            sb.append(", ");
         }
         sb.append(e.getValue()).append("x ").append(e.getKey());
      }
      ctx.player().displayClientMessage(Component.translatable("living.millenaire.request.delivery.progress", sb.toString(), result.remaining().toString()), false);
      return NpcActionResult.ok(result.messageKey(), sb.toString());
   }

   private static NpcActionResult review(NpcActionContext ctx, NpcActionProposal proposal) {
      String id = proposal.targetId();
      VillageRequest request = id != null ? VillageRequestSavedData.get(ctx.level()).get(id) : findActive(ctx);
      if (request == null) {
         return NpcActionResult.fail("living.millenaire.action.error.missing_request");
      }
      summarizeRequest(ctx.player(), request);
      return NpcActionResult.ok("living.millenaire.action.review.ok", request.state().name());
   }

   private static NpcActionResult complete(NpcActionContext ctx, NpcActionProposal proposal) {
      String id = proposal.targetId();
      VillageRequest request = id != null ? VillageRequestSavedData.get(ctx.level()).get(id) : null;
      if (request == null) {
         return NpcActionResult.fail("living.millenaire.action.error.missing_request");
      }
      Village village = ctx.village();
      if (village == null) {
         return NpcActionResult.fail("living.millenaire.action.error.no_village");
      }
      if (!VillageRequestService.complete(ctx.level(), ctx.player(), request, village)) {
         return NpcActionResult.fail("living.millenaire.action.error.complete_failed");
      }
      return NpcActionResult.ok("living.millenaire.action.complete.ok");
   }

   private static NpcActionResult discussQuest(NpcActionContext ctx) {
      return NpcActionResult.ok("living.millenaire.action.quest.discuss");
   }

   private static NpcActionResult openQuest(NpcActionContext ctx) {
      if (ctx.millVillager() == null) {
         return NpcActionResult.fail("living.millenaire.action.error.not_millenaire");
      }
      // Existing interaction entrypoint handles quest UI when present.
      VillagerInteraction.openInfoScreen(ctx.player(), ctx.millVillager());
      return NpcActionResult.ok("living.millenaire.action.quest.opened");
   }

   private static NpcActionResult openTrade(NpcActionContext ctx) {
      if (ctx.millVillager() == null) {
         return NpcActionResult.fail("living.millenaire.action.error.not_millenaire");
      }
      VillagerInteraction.openInfoScreen(ctx.player(), ctx.millVillager());
      return NpcActionResult.ok("living.millenaire.action.trade.opened");
   }

   private static NpcActionResult viewStatus(NpcActionContext ctx) {
      Village village = ctx.village();
      if (village == null) {
         return NpcActionResult.fail("living.millenaire.action.error.no_village");
      }
      String status = village.getVillageName()
         + " attack="
         + village.isUnderAttack()
         + " pending="
         + (village.getPendingProject() != null)
         + " residents="
         + village.getVillagerRecords().size();
      ctx.player().displayClientMessage(Component.literal(status), false);
      return NpcActionResult.ok("living.millenaire.action.status.ok", status);
   }

   @Nullable
   private static VillageRequest findActive(NpcActionContext ctx) {
      for (VillageRequest r : VillageRequestSavedData.get(ctx.level()).forPlayer(ctx.player().getUUID())) {
         if (VillageRequestState.activeStates().contains(r.state())
            && (ctx.villageId() == null || r.villageId().equals(ctx.villageId()))) {
            return r;
         }
      }
      return null;
   }

   private static void summarizeRequest(ServerPlayer player, VillageRequest request) {
      player.displayClientMessage(
         Component.translatable(
            "living.millenaire.request.summary",
            request.type().name(),
            request.state().name(),
            request.totalDelivered(),
            request.totalRequired(),
            String.valueOf(request.remaining())
         ),
         false
      );
   }
}
