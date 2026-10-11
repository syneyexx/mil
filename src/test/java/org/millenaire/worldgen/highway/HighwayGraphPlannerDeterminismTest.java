package org.millenaire.worldgen.highway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.millenaire.worldgen.highway.HighwayPlanSnapshot.NodeView;
import org.millenaire.worldgen.highway.HighwayPlanSnapshot.RouteView;
import org.millenaire.worldgen.plan.SettlementPlan;

class HighwayGraphPlannerDeterminismTest {
   @Test
   void planFromSnapshotIsDeterministic() {
      List<NodeView> nodes = new ArrayList<>();
      nodes.add(new NodeView("a", new BlockPos(0, 64, 0), 32, true, 3, null));
      nodes.add(new NodeView("b", new BlockPos(200, 64, 0), 32, false, 3, null));
      nodes.add(new NodeView("c", new BlockPos(0, 64, 200), 32, false, 3, null));
      List<RouteView> routes = List.of();
      Map<UUID, SettlementPlan> plans = new HashMap<>();
      HighwayPlanSnapshot snap = new HighwayPlanSnapshot(12345L, 800, 3, nodes, routes, plans);

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
}
