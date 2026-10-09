package org.millenaire.living.requests;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.living.events.LivingEvent;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.events.LivingEventSource;
import org.millenaire.living.events.LivingEventType;
import org.millenaire.living.needs.VillageNeed;
import org.millenaire.living.needs.VillageNeedType;
import org.millenaire.living.needs.VillageNeedsEngine;
import org.millenaire.living.needs.VillageNeedsSnapshot;
import org.millenaire.living.social.NpcSocialService;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.slf4j.Logger;

public final class VillageRequestService {
   private static final Logger LOGGER = LogUtils.getLogger();

   private VillageRequestService() {
   }

   @Nullable
   public static VillageRequest offerFromNeed(
      ServerLevel level,
      ServerPlayer player,
      Village village,
      UUID issuingNpcId,
      VillageNeed need,
      VillageNeedsEngine needsEngine
   ) {
      if (!enabled() || player == null || village == null || need == null) {
         return null;
      }
      if (need.shortage().isEmpty()) {
         return null;
      }
      if (need.type() != VillageNeedType.CONSTRUCTION_MATERIAL_SHORTAGE
         && need.type() != VillageNeedType.WORK_RESOURCE_SHORTAGE
         && need.type() != VillageNeedType.VILLAGE_FOOD_SHORTAGE) {
         // Non-shortage needs do not produce delivery requests.
         return null;
      }

      VillageRequestSavedData data = VillageRequestSavedData.get(level);
      int maxPlayer = MillenaireServerConfig.SERVER.villageRequestMaxPerPlayer.getAsInt();
      int maxVillage = MillenaireServerConfig.SERVER.villageRequestMaxPerVillage.getAsInt();
      long activePlayer = data.forPlayer(player.getUUID()).stream().filter(r -> VillageRequestState.activeStates().contains(r.state())).count();
      long activeVillage = data.forVillage(village.getId().uuid()).stream().filter(r -> VillageRequestState.activeStates().contains(r.state())).count();
      if (activePlayer >= maxPlayer || activeVillage >= maxVillage) {
         return null;
      }

      // Prevent duplicate offers multiplying the same shortage.
      List<VillageRequest> existing = data.activeForNeed(village.getId().uuid(), need.projectRef() != null ? need.projectRef() : need.needId());
      for (VillageRequest r : existing) {
         if (r.playerId().equals(player.getUUID())) {
            return r;
         }
      }

      Map<ResourceLocation, Integer> required = boundQuantities(need.shortage(), existing);
      if (required.isEmpty()) {
         return null;
      }

      VillageRequestType type = switch (need.type()) {
         case VILLAGE_FOOD_SHORTAGE -> VillageRequestType.FOOD_OR_PRODUCTION_SUPPLY;
         case WORK_RESOURCE_SHORTAGE -> VillageRequestType.WORK_RESOURCE_DELIVERY;
         default -> VillageRequestType.CONSTRUCTION_MATERIAL_DELIVERY;
      };

      long now = level.getGameTime();
      long expiryDays = MillenaireServerConfig.SERVER.villageRequestExpiryDays.getAsInt();
      VillageRequest request = new VillageRequest(
         UUID.randomUUID().toString(),
         player.getUUID(),
         issuingNpcId,
         village.getId().uuid(),
         type,
         need.projectRef() != null ? need.projectRef() : need.needId(),
         required,
         now,
         now + expiryDays * 24000L,
         VillageRequestState.OFFERED,
         "default",
         1
      );
      data.put(request);
      LivingEventService.emit(
         level,
         LivingEventType.ASSISTANCE_REQUEST_OFFERED,
         LivingEventSource.REQUEST_SERVICE,
         village,
         issuingNpcId,
         player.getUUID(),
         request.requestId(),
         "public",
         "offer:" + request.requestId()
      );
      return request;
   }

   private static Map<ResourceLocation, Integer> boundQuantities(Map<ResourceLocation, Integer> shortage, List<VillageRequest> existingActive) {
      Map<ResourceLocation, Integer> alreadyCommitted = new LinkedHashMap<>();
      for (VillageRequest r : existingActive) {
         for (Entry<ResourceLocation, Integer> e : r.remaining().entrySet()) {
            alreadyCommitted.merge(e.getKey(), e.getValue(), Integer::sum);
         }
      }
      int maxPer = MillenaireServerConfig.SERVER.villageRequestMaximumDeliveryQuantity.getAsInt();
      Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
      for (Entry<ResourceLocation, Integer> e : shortage.entrySet()) {
         int remainingShortage = Math.max(0, e.getValue() - alreadyCommitted.getOrDefault(e.getKey(), 0));
         int qty = Math.min(remainingShortage, maxPer);
         if (qty > 0) {
            out.put(e.getKey(), qty);
         }
      }
      return out;
   }

   public static boolean accept(ServerLevel level, ServerPlayer player, String requestId) {
      VillageRequest request = VillageRequestSavedData.get(level).get(requestId);
      if (request == null || !request.playerId().equals(player.getUUID())) {
         return false;
      }
      if (!request.transitionTo(VillageRequestState.ACCEPTED, level.getGameTime())) {
         return false;
      }
      Village village = Village.resolve(level, new VillageId(request.villageId()));
      if (village == null) {
         request.forceState(VillageRequestState.CANCELLED, level.getGameTime());
         VillageRequestSavedData.get(level).setDirty();
         return false;
      }
      // Revalidate need still exists.
      if (!revalidateAgainstNeed(level, village, request)) {
         request.forceState(VillageRequestState.CANCELLED, level.getGameTime());
         VillageRequestSavedData.get(level).setDirty();
         return false;
      }
      VillageRequestSavedData.get(level).setDirty();
      LivingEventService.emit(
         level,
         LivingEventType.ASSISTANCE_REQUEST_ACCEPTED,
         LivingEventSource.REQUEST_SERVICE,
         village,
         request.issuingNpcId(),
         player.getUUID(),
         request.requestId(),
         "public",
         "accept:" + request.requestId()
      );
      return true;
   }

   public static boolean decline(ServerLevel level, ServerPlayer player, String requestId) {
      VillageRequest request = VillageRequestSavedData.get(level).get(requestId);
      if (request == null || !request.playerId().equals(player.getUUID())) {
         return false;
      }
      if (!request.transitionTo(VillageRequestState.DECLINED, level.getGameTime())) {
         return false;
      }
      VillageRequestSavedData.get(level).setDirty();
      Village village = Village.resolve(level, new VillageId(request.villageId()));
      if (village != null) {
         LivingEventService.emit(
            level,
            LivingEventType.ASSISTANCE_REQUEST_DECLINED,
            LivingEventSource.REQUEST_SERVICE,
            village,
            request.issuingNpcId(),
            player.getUUID(),
            request.requestId(),
            "public",
            "decline:" + request.requestId()
         );
      }
      return true;
   }

   public static VillageDeliveryService.DeliveryResult deliver(ServerLevel level, ServerPlayer player, String requestId, VillageNeedsEngine needsEngine) {
      VillageRequest request = VillageRequestSavedData.get(level).get(requestId);
      if (request == null) {
         return VillageDeliveryService.DeliveryResult.fail("living.millenaire.request.error.missing");
      }
      Village village = Village.resolve(level, new VillageId(request.villageId()));
      if (village == null) {
         request.forceState(VillageRequestState.CANCELLED, level.getGameTime());
         VillageRequestSavedData.get(level).setDirty();
         return VillageDeliveryService.DeliveryResult.fail("living.millenaire.request.error.village_gone");
      }
      if (!revalidateAgainstNeed(level, village, request)) {
         // Need resolved elsewhere — cancel remaining without confiscating.
         request.forceState(VillageRequestState.CANCELLED, level.getGameTime());
         VillageRequestSavedData.get(level).setDirty();
         return VillageDeliveryService.DeliveryResult.fail("living.millenaire.request.error.no_longer_needed");
      }

      VillageDeliveryService.DeliveryResult result = VillageDeliveryService.deliver(level, player, village, request);
      if (result.success()) {
         needsEngine.invalidate(village.getId().uuid());
         LivingEventService.emit(
            level,
            LivingEventType.PLAYER_DELIVERY_VERIFIED,
            LivingEventSource.REQUEST_SERVICE,
            village,
            player.getUUID(),
            request.issuingNpcId(),
            "delivered=" + result.transferred(),
            "public",
            "delivery:" + request.requestId() + ":" + request.version()
         );
         if (request.state() == VillageRequestState.READY_TO_COMPLETE) {
            complete(level, player, request, village);
         }
      }
      return result;
   }

   public static boolean complete(ServerLevel level, ServerPlayer player, VillageRequest request, Village village) {
      if (request.rewardClaimed()) {
         return false;
      }
      if (request.state() != VillageRequestState.READY_TO_COMPLETE && request.remaining().isEmpty()) {
         request.forceState(VillageRequestState.READY_TO_COMPLETE, level.getGameTime());
      }
      if (request.state() != VillageRequestState.READY_TO_COMPLETE) {
         return false;
      }
      if (!request.transitionTo(VillageRequestState.COMPLETED, level.getGameTime())) {
         return false;
      }

      int delivered = request.totalDelivered();
      double mult = MillenaireServerConfig.SERVER.villageRequestReputationMultiplier.get();
      int rep = Math.max(1, (int)Math.round(Math.min(50, 2 + delivered / 8.0) * mult));
      village.adjustReputation(level, player.getUUID(), rep);

      LivingEvent event = LivingEventService.emit(
         level,
         LivingEventType.ASSISTANCE_REQUEST_COMPLETED,
         LivingEventSource.REQUEST_SERVICE,
         village,
         player.getUUID(),
         request.issuingNpcId(),
         "rep+" + rep + " items=" + delivered,
         "public",
         "complete:" + request.requestId()
      );
      request.markRewardClaimed(event != null ? event.eventId() : request.requestId(), level.getGameTime());
      VillageRequestSavedData.get(level).setDirty();

      NpcSocialService.learnFromEvent(level, village, event, request.issuingNpcId());
      if (MillenaireServerConfig.SERVER.livingVillagesDebugLogging.get()) {
         LOGGER.debug("[Living] Request {} completed rep+{}", request.requestId(), rep);
      }
      return true;
   }

   private static boolean revalidateAgainstNeed(ServerLevel level, Village village, VillageRequest request) {
      // Shared shortage accounting: remaining request must not exceed fresh shortage for same project.
      VillageNeedsSnapshot snap = org.millenaire.living.LivingVillagesService.get().needsEngine().requireFresh(level, village.getId());
      VillageNeed need = null;
      for (VillageNeed n : snap.needs()) {
         String ref = n.projectRef() != null ? n.projectRef() : n.needId();
         if (ref.equals(request.linkedNeedOrProjectId()) && !n.shortage().isEmpty()) {
            need = n;
            break;
         }
      }
      if (need == null) {
         // Also accept if request still has undelivered items but townhall still shows shortage of those items.
         return !request.remaining().isEmpty() && stillShortInInventory(level, village, request.remaining());
      }
      // Cap remaining against live shortage across all active requests.
      Map<ResourceLocation, Integer> live = need.shortage();
      Map<ResourceLocation, Integer> committedOthers = new LinkedHashMap<>();
      for (VillageRequest other : VillageRequestSavedData.get(level).activeForNeed(village.getId().uuid(), request.linkedNeedOrProjectId())) {
         if (other.requestId().equals(request.requestId())) {
            continue;
         }
         for (Entry<ResourceLocation, Integer> e : other.remaining().entrySet()) {
            committedOthers.merge(e.getKey(), e.getValue(), Integer::sum);
         }
      }
      boolean any = false;
      for (Entry<ResourceLocation, Integer> e : request.remaining().entrySet()) {
         int liveNeed = live.getOrDefault(e.getKey(), 0) - committedOthers.getOrDefault(e.getKey(), 0);
         if (liveNeed > 0) {
            any = true;
            break;
         }
      }
      return any || request.totalDelivered() > 0 && request.remaining().isEmpty();
   }

   private static boolean stillShortInInventory(ServerLevel level, Village village, Map<ResourceLocation, Integer> remaining) {
      var th = village.getTownhall();
      if (th == null || th.getInventory() == null) {
         return false;
      }
      // Soft check: if player still has a request remaining, allow delivery attempt; growth engine will use items.
      return !remaining.isEmpty();
   }

   public static void tickMaintenance(ServerLevel level) {
      if (!enabled()) {
         return;
      }
      long now = level.getGameTime();
      VillageRequestSavedData data = VillageRequestSavedData.get(level);
      boolean dirty = false;
      for (VillageRequest request : List.copyOf(data.all().values())) {
         if (VillageRequestState.activeStates().contains(request.state()) && now > request.expiryGameTime()) {
            request.forceState(VillageRequestState.EXPIRED, now);
            dirty = true;
         }
         Village village = Village.resolve(level, new VillageId(request.villageId()));
         if (VillageRequestState.activeStates().contains(request.state()) && village == null) {
            request.forceState(VillageRequestState.CANCELLED, now);
            dirty = true;
         }
      }
      if (now % 24000L == 0L) {
         data.pruneTerminalOlderThan(now - 24000L * 40L, 500);
         dirty = true;
      }
      if (dirty) {
         data.setDirty();
      }
   }

   private static boolean enabled() {
      try {
         return MillenaireServerConfig.SERVER.livingVillagesEnabled.get()
            && MillenaireServerConfig.SERVER.villageRequestsEnabled.get();
      } catch (IllegalStateException e) {
         return true;
      }
   }
}
