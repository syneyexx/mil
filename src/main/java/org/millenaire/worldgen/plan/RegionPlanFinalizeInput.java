package org.millenaire.worldgen.plan;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.NameLists;
import org.millenaire.worldgen.content.VillageTypeWorldgenEntry;

/**
 * Immutable inputs for building an accepted {@link SettlementPlan} off the server thread.
 */
public record RegionPlanFinalizeInput(
   long worldSeed,
   SettlementRegionKey region,
   BlockPos center,
   ResourceLocation cultureId,
   ResourceLocation typeId,
   int radius,
   boolean lone,
   String nameListKey,
   BlockPos spawn,
   boolean allowPartialCompletion,
   int completionMinDistance,
   int completionMaxDistance,
   int completionMaxPercentage
) {
   private static final Rotation[] ROTATIONS = {
      Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90
   };

   public static RegionPlanFinalizeInput fromSelected(
      long worldSeed,
      SettlementRegionKey region,
      BlockPos center,
      VillageTypeWorldgenEntry selected,
      boolean lone,
      BlockPos spawn
   ) {
      MillenaireServerConfig cfg = MillenaireServerConfig.SERVER;
      return new RegionPlanFinalizeInput(
         worldSeed,
         region,
         center,
         selected.cultureId(),
         selected.id(),
         selected.radius(),
         lone,
         selected.nameList(),
         spawn,
         Boolean.TRUE.equals(cfg.worldgenAllowPartialInitialCompletion.get()),
         cfg.completionMinDistance.getAsInt(),
         cfg.completionMaxDistance.getAsInt(),
         cfg.completionMaxPercentage.getAsInt()
      );
   }

   public SettlementPlan buildPlan() {
      UUID planId = WorldgenSeeds.settlementUuid(this.worldSeed, this.region, this.typeId.toString());
      RandomSource rotRng = WorldgenSeeds.stream(this.worldSeed, WorldgenSeeds.SALT_ROTATION, this.region);
      Rotation orientation = ROTATIONS[rotRng.nextInt(ROTATIONS.length)];
      RandomSource compRng = WorldgenSeeds.stream(this.worldSeed, WorldgenSeeds.SALT_COMPLETION, this.region);
      int completion = computeInitialCompletion(compRng);
      String name = pickName();
      SettlementPlan plan = new SettlementPlan(
         planId,
         planId,
         this.region,
         this.worldSeed,
         this.cultureId,
         this.typeId,
         this.center,
         orientation,
         this.radius,
         completion,
         this.lone,
         null,
         name
      );
      plan.reserveFootprintChunks();
      plan.trySetStatus(SettlementPlanStatus.PLANNED);
      return plan;
   }

   private int computeInitialCompletion(RandomSource rng) {
      if (!this.allowPartialCompletion) {
         return 0;
      }
      double dist = Math.sqrt(this.center.distToCenterSqr(this.spawn.getX(), this.center.getY(), this.spawn.getZ()));
      int minD = this.completionMinDistance;
      int maxD = Math.max(minD + 1, this.completionMaxDistance);
      int maxPct = this.completionMaxPercentage;
      if (dist <= minD) {
         return 0;
      }
      double t = Math.min(1.0, (dist - minD) / (double)(maxD - minD));
      int base = (int)Math.round(t * maxPct);
      int jitter = rng.nextInt(Math.max(1, maxPct / 5 + 1));
      return Math.max(0, Math.min(maxPct, base + jitter - maxPct / 10));
   }

   private String pickName() {
      RandomSource nameRng = WorldgenSeeds.stream(this.worldSeed, WorldgenSeeds.SALT_NAME, this.region);
      if (this.nameListKey != null) {
         NameLists lists = ModCultures.getNameLists(this.cultureId);
         if (lists != null) {
            List<String> names = lists.lists().get(this.nameListKey);
            if (names != null && !names.isEmpty()) {
               return names.get(nameRng.nextInt(names.size()));
            }
         }
      }
      return this.typeId.getPath();
   }
}
