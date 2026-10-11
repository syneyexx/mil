package org.millenaire.world;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.millenaire.config.MillenaireServerConfig;
import org.slf4j.Logger;

public final class StructureAvoidance {
   private static final Logger LOGGER = LogUtils.getLogger();
   static final int SAFETY_MARGIN = 3;
   private static final int SCAN_STEP = 8;
   private static final List<ResourceKey<Structure>> AVOIDED_STRUCTURE_KEYS = List.of(
      BuiltinStructures.PILLAGER_OUTPOST,
      BuiltinStructures.DESERT_PYRAMID,
      BuiltinStructures.JUNGLE_TEMPLE,
      BuiltinStructures.SWAMP_HUT,
      BuiltinStructures.WOODLAND_MANSION,
      BuiltinStructures.IGLOO
   );
   public static final TagKey<Structure> AVOID_SETTLEMENT = TagKey.create(
      Registries.STRUCTURE,
      ResourceLocation.fromNamespaceAndPath("millenaire", "avoid_settlement")
   );

   private StructureAvoidance() {
   }

   public enum LoadedVerdict {
      CONFLICT,
      CLEAR,
      WAITING
   }

   public static boolean hasConflict(ServerLevel level, BlockPos center, int villageRadius) {
      LoadedVerdict verdict = hasConflictLoaded(level, center, villageRadius);
      return verdict == LoadedVerdict.CONFLICT;
   }

   /**
    * Cheap 5-point probe for aerial world-boss spawn (center + four diagonals at radius).
    * Worldgen placement should keep using {@link #hasConflictLoaded}.
    */
   public static boolean hasConflictSparse(ServerLevel level, BlockPos center, int villageRadius) {
      LoadedVerdict verdict = hasConflictSparseLoaded(level, center, villageRadius);
      return verdict == LoadedVerdict.CONFLICT;
   }

   /**
    * Checks protected structures only on loaded columns. Unloaded sample points are skipped;
    * WAITING only if the centre column itself is missing.
    */
   public static LoadedVerdict hasConflictLoaded(ServerLevel level, BlockPos center, int villageRadius) {
      AvoidContext ctx = prepareAvoidContext(level, center, villageRadius);
      if (ctx.early != null) {
         return ctx.early;
      }

      for (int dx = -ctx.scanRadius; dx <= ctx.scanRadius; dx += SCAN_STEP) {
         for (int dz = -ctx.scanRadius; dz <= ctx.scanRadius; dz += SCAN_STEP) {
            if (conflictsAt(level, center, center.offset(dx, 0, dz), ctx)) {
               return LoadedVerdict.CONFLICT;
            }
         }
      }

      return LoadedVerdict.CLEAR;
   }

   /**
    * Sparse loaded check: center + four diagonals at {@code villageRadius + safety margin}.
    */
   public static LoadedVerdict hasConflictSparseLoaded(ServerLevel level, BlockPos center, int villageRadius) {
      AvoidContext ctx = prepareAvoidContext(level, center, villageRadius);
      if (ctx.early != null) {
         return ctx.early;
      }

      int r = ctx.scanRadius;
      BlockPos[] samples = new BlockPos[]{
         center,
         center.offset(r, 0, r),
         center.offset(r, 0, -r),
         center.offset(-r, 0, r),
         center.offset(-r, 0, -r)
      };
      for (BlockPos check : samples) {
         if (conflictsAt(level, center, check, ctx)) {
            return LoadedVerdict.CONFLICT;
         }
      }
      return LoadedVerdict.CLEAR;
   }

   private static AvoidContext prepareAvoidContext(ServerLevel level, BlockPos center, int villageRadius) {
      if (!org.millenaire.worldgen.terrain.WorldgenChunkAccess.isColumnLoaded(level, center.getX(), center.getZ())) {
         return AvoidContext.early(LoadedVerdict.WAITING);
      }
      MillenaireServerConfig cfg = MillenaireServerConfig.SERVER;
      boolean preserveVanilla = cfg == null || Boolean.TRUE.equals(cfg.worldgenPreserveVanillaStructures.get());
      boolean preserveModded = cfg == null || Boolean.TRUE.equals(cfg.worldgenPreserveModdedStructures.get());
      if (!preserveVanilla && !preserveModded) {
         return AvoidContext.early(LoadedVerdict.CLEAR);
      }

      int margin = cfg != null ? cfg.worldgenStructureSafetyMargin.get() : SAFETY_MARGIN;
      int scanRadius = villageRadius + margin;
      StructureManager sm = level.structureManager();
      RegistryLookup<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);

      List<Structure> avoidedVanilla = List.of();
      if (preserveVanilla) {
         avoidedVanilla = AVOIDED_STRUCTURE_KEYS.stream()
            .map(key -> registry.get(key).map(Holder::value).orElse(null))
            .filter(s -> s != null)
            .toList();
      }

      List<Structure> avoidedModded = List.of();
      if (preserveModded) {
         Optional<HolderSet.Named<Structure>> tagged = registry.get(AVOID_SETTLEMENT);
         if (tagged.isPresent()) {
            List<Structure> modded = new ArrayList<>();
            tagged.get().forEach(holder -> modded.add(holder.value()));
            avoidedModded = List.copyOf(modded);
         }
      }

      return new AvoidContext(null, scanRadius, sm, preserveVanilla, avoidedVanilla, avoidedModded);
   }

   private static boolean conflictsAt(ServerLevel level, BlockPos center, BlockPos check, AvoidContext ctx) {
      if (!org.millenaire.worldgen.terrain.WorldgenChunkAccess.isColumnLoaded(level, check.getX(), check.getZ())) {
         return false;
      }
      if (ctx.preserveVanilla) {
         StructureStart villageStart = ctx.sm.getStructureWithPieceAt(check, StructureTags.VILLAGE);
         if (villageStart.isValid()) {
            LOGGER.debug("[Millenaire] Spawn rejected at {} : vanilla village detected at {}", center.toShortString(), check.toShortString());
            return true;
         }

         for (int i = 0; i < ctx.avoidedVanilla.size(); i++) {
            if (ctx.sm.getStructureAt(check, ctx.avoidedVanilla.get(i)).isValid()) {
               LOGGER.debug(
                  "[Millenaire] Spawn rejected at {} : structure '{}' detected at {}",
                  center.toShortString(),
                  AVOIDED_STRUCTURE_KEYS.get(i).location(),
                  check.toShortString()
               );
               return true;
            }
         }
      }

      for (Structure structure : ctx.avoidedModded) {
         if (ctx.sm.getStructureAt(check, structure).isValid()) {
            LOGGER.debug(
               "[Millenaire] Spawn rejected at {} : modded structure detected at {}",
               center.toShortString(),
               check.toShortString()
            );
            return true;
         }
      }
      return false;
   }

   private record AvoidContext(
      LoadedVerdict early,
      int scanRadius,
      StructureManager sm,
      boolean preserveVanilla,
      List<Structure> avoidedVanilla,
      List<Structure> avoidedModded
   ) {
      static AvoidContext early(LoadedVerdict verdict) {
         return new AvoidContext(verdict, 0, null, false, List.of(), List.of());
      }
   }
}
