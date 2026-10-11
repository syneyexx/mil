package org.millenaire.worldgen.starter;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.millenaire.Millenaire;
import org.millenaire.compat.ClimateBiomeFallback;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.millenaire.world.VillageSpawner;
import org.millenaire.worldgen.content.WorldgenContentSnapshot;
import org.slf4j.Logger;

/**
 * Places one guaranteed starter settlement (default Gabbers Koninkrijk) on new overworlds and
 * moves the shared spawn to its centre. Uses {@link VillageSpawner} for sync physical+ACTIVE form.
 */
public final class StarterSettlementService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_ATTEMPTS = 240;
   private static final int SITES_PER_TICK = 10;
   private static final int RING_STEP = 16;

   private StarterSettlementService() {
   }

   public static void onWorldReady(ServerLevel level) {
      tick(level);
   }

   public static void tick(ServerLevel level) {
      if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
         return;
      }
      boolean enabled;
      try {
         enabled = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.starterSettlementEnabled.get());
      } catch (IllegalStateException e) {
         return;
      }
      if (!enabled) {
         return;
      }
      if (!WorldgenContentSnapshot.isReady() && ModCultures.getVillageType(starterTypeId()) == null) {
         return;
      }

      StarterSettlementSavedData data = StarterSettlementSavedData.get(level);
      if (data.isPlaced() || data.hasGaveUp()) {
         return;
      }

      ResourceLocation cultureId = starterCultureId();
      if (hasExistingCultureVillage(level, cultureId)) {
         data.markSatisfiedExisting();
         LOGGER.info("[Millenaire] Starter settlement skipped — {} village already present", cultureId);
         return;
      }

      VillageType villageType = ModCultures.getVillageType(starterTypeId());
      if (villageType == null) {
         data.incrementAttempts();
         if (data.attempts() >= MAX_ATTEMPTS) {
            data.markGaveUp();
            LOGGER.error("[Millenaire] Starter settlement gave up — village type {} not loaded", starterTypeId());
         }
         return;
      }

      BlockPos origin = level.getSharedSpawnPos();
      int maxRadius = MillenaireServerConfig.SERVER.starterSearchRadius.getAsInt();
      int attemptsBefore = data.attempts();
      int tried = 0;

      // Prefer the spawn column first, then expanding rings.
      if (attemptsBefore == 0) {
         if (tryPlaceAt(level, data, villageType, cultureId, origin, true)) {
            return;
         }
         tried++;
         data.incrementAttempts();
      }

      int ringStart = Math.max(RING_STEP, (attemptsBefore / 4) * RING_STEP);
      for (int radius = ringStart; radius <= maxRadius && tried < SITES_PER_TICK; radius += RING_STEP) {
         int samples = Math.max(8, (radius / RING_STEP) * 4);
         int angleOffset = attemptsBefore * 37;
         for (int i = 0; i < samples && tried < SITES_PER_TICK; i++) {
            double angle = (Math.PI * 2.0 * (i + angleOffset)) / samples;
            int x = origin.getX() + (int)Math.round(Math.cos(angle) * radius);
            int z = origin.getZ() + (int)Math.round(Math.sin(angle) * radius);
            int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos candidate = new BlockPos(x, y, z);
            tried++;
            data.incrementAttempts();
            if (tryPlaceAt(level, data, villageType, cultureId, candidate, true)) {
               return;
            }
         }
      }

      if (data.attempts() >= MAX_ATTEMPTS) {
         data.markGaveUp();
         LOGGER.error(
            "[Millenaire] Starter settlement gave up after {} attempts near {}",
            data.attempts(),
            origin.toShortString()
         );
      }
   }

   /**
    * Clears sticky give-up / incomplete state and immediately retries placement.
    *
    * @return true if a retry was armed (or placement succeeded this call)
    */
   public static boolean retry(ServerLevel level) {
      if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
         return false;
      }
      StarterSettlementSavedData data = StarterSettlementSavedData.get(level);
      ResourceLocation cultureId = starterCultureId();
      if (hasExistingCultureVillage(level, cultureId)) {
         data.markSatisfiedExisting();
         LOGGER.info("[Millenaire] Starter retry skipped — {} village already present", cultureId);
         return false;
      }
      data.prepareRetry();
      tick(level);
      return !data.hasGaveUp();
   }

   private static boolean tryPlaceAt(
      ServerLevel level,
      StarterSettlementSavedData data,
      VillageType villageType,
      ResourceLocation cultureId,
      BlockPos candidate,
      boolean ignoreCultureBiome
   ) {
      Holder<Biome> biome = level.getBiome(candidate);
      if (ClimateBiomeFallback.isExcluded(biome)) {
         LOGGER.debug("[Millenaire] Starter reject {} — excluded biome", candidate.toShortString());
         return false;
      }
      // Starter placement intentionally ignores culture biome tags so spawn is not stuck
      // when the shared spawn is outside plains/meadow. Natural Gabbers still use biome_tags.
      if (!ignoreCultureBiome && !villageType.matchesBiome(biome)) {
         return false;
      }
      Component siteError = VillageSpawner.validateSite(level, candidate, villageType);
      if (siteError != null) {
         LOGGER.debug("[Millenaire] Starter site reject {}: {}", candidate.toShortString(), siteError.getString());
         return false;
      }
      Component failure = VillageSpawner.spawnVillage(level, candidate, villageType, 0);
      if (failure != null) {
         LOGGER.debug("[Millenaire] Starter spawn failed at {}: {}", candidate.toShortString(), failure.getString());
         return false;
      }

      Village placed = findNearestCultureVillage(level, cultureId, candidate);
      BlockPos centre = placed != null ? placed.getCenter() : candidate;
      if (placed != null) {
         data.markPlaced(placed.getId().uuid(), centre);
      } else {
         data.markPlaced(java.util.UUID.randomUUID(), centre);
      }
      level.setDefaultSpawnPos(centre, 0.0F);
      LOGGER.info(
         "[Millenaire] Starter settlement {} placed at {} — world spawn updated",
         villageType.id(),
         centre.toShortString()
      );
      return true;
   }

   private static boolean hasExistingCultureVillage(ServerLevel level, ResourceLocation cultureId) {
      for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (cultureId.equals(village.getCultureId()) && !isLone(village)) {
            return true;
         }
      }
      return false;
   }

   private static Village findNearestCultureVillage(ServerLevel level, ResourceLocation cultureId, BlockPos near) {
      Village best = null;
      double bestDist = Double.MAX_VALUE;
      for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (!cultureId.equals(village.getCultureId())) {
            continue;
         }
         double d = village.getCenter().distSqr(near);
         if (d < bestDist) {
            bestDist = d;
            best = village;
         }
      }
      return best;
   }

   private static boolean isLone(Village village) {
      VillageType type = ModCultures.getVillageType(village.getVillageTypeId());
      return type != null && type.loneBuilding();
   }

   private static ResourceLocation starterCultureId() {
      String path = MillenaireServerConfig.SERVER.starterCulture.get();
      if (path == null || path.isBlank()) {
         path = "gabbers";
      }
      path = path.trim();
      if (path.contains(":")) {
         return ResourceLocation.parse(path);
      }
      return ResourceLocation.fromNamespaceAndPath(Millenaire.MODID, path);
   }

   private static ResourceLocation starterTypeId() {
      ResourceLocation culture = starterCultureId();
      String typePath = MillenaireServerConfig.SERVER.starterVillageType.get();
      if (typePath == null || typePath.isBlank()) {
         typePath = "koninkrijk";
      }
      typePath = typePath.trim();
      if (typePath.contains(":")) {
         return ResourceLocation.parse(typePath);
      }
      if (typePath.contains("/")) {
         return ResourceLocation.fromNamespaceAndPath(culture.getNamespace(), typePath);
      }
      return ResourceLocation.fromNamespaceAndPath(culture.getNamespace(), culture.getPath() + "/" + typePath);
   }
}
