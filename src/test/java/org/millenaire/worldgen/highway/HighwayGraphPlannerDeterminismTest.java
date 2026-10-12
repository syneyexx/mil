package org.millenaire.worldgen.highway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.millenaire.worldgen.highway.HighwayPlanSnapshot.NodeView;
import org.millenaire.worldgen.highway.HighwayPlanSnapshot.RouteView;

class HighwayGraphPlannerDeterminismTest {
   @Test
   void planFromSnapshotIsDeterministic() {
      List<NodeView> nodes = new ArrayList<>();
      nodes.add(new NodeView("a", new BlockPos(0, 64, 0), 32, true, 3, null));
      nodes.add(new NodeView("b", new BlockPos(200, 64, 0), 32, false, 3, null));
      nodes.add(new NodeView("c", new BlockPos(0, 64, 200), 32, false, 3, null));
      List<RouteView> routes = List.of();
      Map<UUID, HighwayAttachmentData> attachments = new HashMap<>();
      HighwayPlanSnapshot snap = new HighwayPlanSnapshot(12345L, 800, 3, nodes, routes, attachments, 7L);

      List<HighwayRoute> first = HighwayGraphPlanner.planFromSnapshot(snap);
      List<HighwayRoute> second = HighwayGraphPlanner.planFromSnapshot(snap);
      assertEquals(first.size(), second.size());
      for (int i = 0; i < first.size(); i++) {
         assertEquals(first.get(i).routeId(), second.get(i).routeId());
         assertEquals(first.get(i).fromNodeKey(), second.get(i).fromNodeKey());
         assertEquals(first.get(i).toNodeKey(), second.get(i).toNodeKey());
         assertEquals(first.get(i).waypoints(), second.get(i).waypoints());
      }
   }

   @Test
   void attachmentDataDrivesAttachmentPoint() {
      UUID planId = new UUID(1L, 2L);
      BlockPos center = new BlockPos(0, 64, 0);
      BlockPos rim = new BlockPos(24, 64, 0);
      HighwayAttachmentData data = new HighwayAttachmentData(planId, center, 32, List.of(rim, new BlockPos(-24, 64, 0)));
      List<NodeView> nodes = List.of(
         new NodeView("a", center, 32, true, 3, planId),
         new NodeView("b", new BlockPos(200, 64, 0), 32, false, 3, null)
      );
      HighwayPlanSnapshot snap = new HighwayPlanSnapshot(1L, 800, 3, nodes, List.of(), Map.of(planId, data), 3L);
      assertEquals(3L, snap.topologyRevision());
      assertEquals(rim, HighwayGraphPlanner.attachmentPoint(nodes.get(0), new BlockPos(200, 64, 0), snap));
   }

   @Test
   void nodeUpsertBumpsRevisionOnlyOnMeaningfulChange() {
      HighwayNodeSavedData store = new HighwayNodeSavedData();
      long r0 = store.topologyRevision();
      HighwayNode node = new HighwayNode("a", HighwayNodeKind.VANILLA_VILLAGE, new BlockPos(0, 64, 0), 32);
      assertTrue(store.upsert(node));
      long r1 = store.topologyRevision();
      assertTrue(r1 > r0);
      assertFalse(store.upsert(new HighwayNode("a", HighwayNodeKind.VANILLA_VILLAGE, new BlockPos(0, 64, 0), 32)));
      assertEquals(r1, store.topologyRevision());
      assertFalse(store.upsert(new HighwayNode("a", HighwayNodeKind.VANILLA_VILLAGE, new BlockPos(10, 64, 0), 32)));
      assertTrue(store.topologyRevision() > r1);
   }

   @Test
   void activeAndConnectedRouteCachesFollowStatusRevision() {
      HighwayRouteSavedData routes = new HighwayRouteSavedData();
      List<BlockPos> wps = List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0));
      HighwayRoute a = new HighwayRoute(new UUID(0L, 1L), "a", "b", wps);
      HighwayRoute b = new HighwayRoute(new UUID(0L, 2L), "a", "c", wps);
      routes.put(a);
      routes.put(b);
      assertEquals(2, routes.activeRoutes().size());
      assertTrue(routes.connectedRoutes().isEmpty());
      long topology = routes.topologyRevision();

      a.setStatus(HighwayRoute.Status.CONNECTED);
      routes.notifyStatusChanged();
      assertEquals(topology, routes.topologyRevision());
      assertEquals(List.of(b), routes.activeRoutes());
      assertEquals(List.of(a), routes.connectedRoutes());
   }
}
