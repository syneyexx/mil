package org.millenaire.worldgen.plan;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * Deterministic random streams. Natural generation must never use ThreadLocalRandom
 * or global mutable Random for settlement selection/layout.
 */
public final class WorldgenSeeds {
   public static final long SALT_POSITION = 0x4D494C4C504F5331L; // MILLPOS1
   public static final long SALT_TYPE = 0x4D494C4C54595045L; // MILLTYPE
   public static final long SALT_CULTURE = 0x4D494C4C43554C54L; // MILLCULT
   public static final long SALT_LAYOUT = 0x4D494C4C4C41594FL; // MILLLAYO
   public static final long SALT_VARIANT = 0x4D494C4C56415249L; // MILLVARI
   public static final long SALT_ROTATION = 0x4D494C4C524F5441L; // MILLROTA
   public static final long SALT_DECORATION = 0x4D494C4C4445434FL; // MILLDECO
   public static final long SALT_WALL = 0x4D494C4C57414C4CL; // MILLWALL
   public static final long SALT_ROAD = 0x4D494C4C524F4144L; // MILLROAD
   public static final long SALT_COMPLETION = 0x4D494C4C434F4D50L; // MILLCOMP
   public static final long SALT_NAME = 0x4D494C4C4E414D45L; // MILLNAME
   public static final long SALT_LONE = 0x4D494C4C4C4F4E45L; // MILLLONE
   public static final long SALT_HAMLET = 0x4D494C4C48414D4CL; // MILLHAML
   public static final long SALT_ARBITER = 0x4D494C4C41524249L; // MILLARBI
   public static final int PLANNER_VERSION = 1;

   private WorldgenSeeds() {
   }

   public static RandomSource stream(long worldSeed, long salt, int x, int z) {
      long mixed = worldSeed
         ^ salt
         ^ ((long)x * 341873128712L)
         ^ ((long)z * 132897987541L)
         ^ ((long)PLANNER_VERSION * 0x9E3779B97F4A7C15L);
      return new XoroshiroRandomSource(mixed);
   }

   public static RandomSource stream(long worldSeed, long salt, SettlementRegionKey region) {
      return stream(worldSeed, salt, region.regionX(), region.regionZ());
   }

   public static long mix(long worldSeed, long salt, long a, long b) {
      long x = worldSeed ^ salt ^ a * 0xBF58476D1CE4E5B9L ^ b * 0x94D049BB133111EBL;
      x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
      x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
      return x ^ (x >>> 31);
   }

   /**
    * Deterministic UUID bits for natural settlements. Manual spawns keep VillageId.random().
    */
   public static java.util.UUID settlementUuid(long worldSeed, SettlementRegionKey region, String villageTypePath) {
      long msb = mix(worldSeed, SALT_POSITION, region.packed(), villageTypePath.hashCode());
      long lsb = mix(worldSeed, SALT_TYPE, region.regionX(), region.regionZ());
      // Set version/variant bits loosely for uniqueness; not RFC-required for gameplay IDs.
      return new java.util.UUID(msb, lsb);
   }
}
