package org.millenaire.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.millenaire.building.PlacementPhase;
import org.millenaire.building.PlacementStep;
import org.millenaire.worldgen.materialize.CompiledPlacementStepsCache;

class CompiledPlacementStepsStabilityTest {
   @AfterEach
   void reset() {
      CompiledPlacementStepsCache.invalidateAll();
   }

   @Test
   void cacheReturnsSameCompiledListAndStableIndexes() {
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "test_plan");
      CompiledPlacementStepsCache.Key key = new CompiledPlacementStepsCache.Key(planId, Rotation.NONE, 1);
      List<PlacementStep> first = List.of(
         new PlacementStep(new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState(), PlacementPhase.STRUCTURE),
         new PlacementStep(new BlockPos(1, 0, 0), Blocks.AIR.defaultBlockState(), PlacementPhase.DELETION),
         new PlacementStep(new BlockPos(17, 0, 0), Blocks.OAK_PLANKS.defaultBlockState(), PlacementPhase.STRUCTURE)
      );
      CompiledPlacementStepsCache.CompiledSteps a = CompiledPlacementStepsCache.getOrCompute(key, () -> first);
      CompiledPlacementStepsCache.CompiledSteps b = CompiledPlacementStepsCache.getOrCompute(key, () -> List.of());
      assertSame(a, b);
      assertEquals(3, a.steps().size());
      // Cursor semantics: index 1 always refers to the same relative deletion step.
      assertEquals(PlacementPhase.DELETION, a.steps().get(1).phase());
      assertEquals(new BlockPos(1, 0, 0), a.steps().get(1).relativePos());
   }
}
