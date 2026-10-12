package org.millenaire.worldgen.highway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;

/**
 * Immutable highway graph inputs for off-thread link planning.
 */
public final class HighwayPlanSnapshot {
   public record NodeView(
      String key,
      BlockPos center,
      int radiusHint,
      boolean factionHub,
      int maxNeighbors,
      @Nullable UUID planId
   ) {
   }

   public record RouteView(String fromKey, String toKey, HighwayRoute.Status status) {
   }

   private final long worldSeed;
   private final int maxDist;
   private final int baseNeighbors;
   private final List<NodeView> nodes;
   private final List<RouteView> routes;
   private final Map<UUID, HighwayAttachmentData> attachmentsByPlanId;
   private final long topologyRevision;

   public HighwayPlanSnapshot(
      long worldSeed,
      int maxDist,
      int baseNeighbors,
      List<NodeView> nodes,
      List<RouteView> routes,
      Map<UUID, HighwayAttachmentData> attachmentsByPlanId,
      long topologyRevision
   ) {
      this.worldSeed = worldSeed;
      this.maxDist = maxDist;
      this.baseNeighbors = baseNeighbors;
      this.nodes = List.copyOf(nodes);
      this.routes = List.copyOf(routes);
      this.attachmentsByPlanId = Map.copyOf(attachmentsByPlanId);
      this.topologyRevision = topologyRevision;
   }

   public static HighwayPlanSnapshot capture(ServerLevel level, HighwayNodeSavedData nodes, HighwayRouteSavedData routes) {
      int maxDist = MillenaireServerConfig.SERVER.highwayMaxLinkDistance.getAsInt();
      int baseNeighbors = MillenaireServerConfig.SERVER.highwayMaxNeighbors.getAsInt();
      List<NodeView> nodeViews = new ArrayList<>();
      Map<UUID, HighwayAttachmentData> attachments = new HashMap<>();
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      for (HighwayNode n : nodes.allNodes()) {
         nodeViews.add(new NodeView(
            n.key(),
            n.center(),
            n.radiusHint(),
            n.factionHub(),
            n.maxNeighbors(baseNeighbors),
            n.planId()
         ));
         if (n.planId() != null) {
            SettlementPlan plan = attachments.containsKey(n.planId()) ? null : store.getPlan(n.planId());
            if (plan != null) {
               attachments.put(n.planId(), HighwayAttachmentData.capture(plan));
            }
         }
      }
      List<RouteView> routeViews = new ArrayList<>();
      for (HighwayRoute r : routes.allRoutes()) {
         routeViews.add(new RouteView(r.fromNodeKey(), r.toNodeKey(), r.status()));
      }
      return new HighwayPlanSnapshot(level.getSeed(), maxDist, baseNeighbors, nodeViews, routeViews, attachments, revisionOf(nodes, routes));
   }

   public long worldSeed() {
      return this.worldSeed;
   }

   public int maxDist() {
      return this.maxDist;
   }

   public int baseNeighbors() {
      return this.baseNeighbors;
   }

   public List<NodeView> nodes() {
      return this.nodes;
   }

   public List<RouteView> routes() {
      return this.routes;
   }

   public long topologyRevision() {
      return this.topologyRevision;
   }

   public long configKey() {
      return configKey(this.maxDist, this.baseNeighbors);
   }

   @Nullable
   public HighwayAttachmentData attachment(@Nullable UUID planId) {
      return planId == null ? null : this.attachmentsByPlanId.get(planId);
   }

   public static long revisionOf(HighwayNodeSavedData nodes, HighwayRouteSavedData routes) {
      return nodes.topologyRevision() + routes.topologyRevision();
   }

   public static long configKey(int maxDist, int baseNeighbors) {
      return ((long)maxDist << 32) ^ (baseNeighbors & 0xFFFFFFFFL);
   }

   public Set<String> linkedKeys() {
      Set<String> linked = new HashSet<>();
      for (RouteView r : this.routes) {
         if (r.status() != HighwayRoute.Status.FAILED) {
            linked.add(linkKey(r.fromKey(), r.toKey()));
         }
      }
      return linked;
   }

   public static String linkKey(String a, String b) {
      return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
   }
}
