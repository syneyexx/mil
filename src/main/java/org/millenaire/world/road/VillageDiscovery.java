package org.millenaire.world.road;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.village.Village;
import org.millenaire.village.VillageManager;
import org.millenaire.world.settlement.SettlementSavedData;
import org.slf4j.Logger;

/**
 * Discovers villages for the road/settlement registries without forcing chunk generation.
 */
public final class VillageDiscovery {
   private static final Logger LOGGER = LogUtils.getLogger();

   private VillageDiscovery() {
   }

   public static void onChunkLoad(ServerLevel level, ChunkAccess chunk) {
      if (!RoadNetworkConfig.enabled()) {
         return;
      }
      RoadNetworkSavedData data = RoadNetworkSavedData.get(level);
      SettlementSavedData settlements = SettlementSavedData.get(level);
      boolean changed = false;

      Map<Structure, StructureStart> starts = chunk.getAllStarts();
      Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
      Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);

      for (Map.Entry<Structure, StructureStart> entry : starts.entrySet()) {
         Structure structure = entry.getKey();
         StructureStart start = entry.getValue();
         if (!start.isValid()) {
            continue;
         }

         Optional<ResourceKey<Structure>> structureKey = structureRegistry.getResourceKey(structure);
         if (structureKey.isEmpty()) {
            continue;
         }
         Holder.Reference<Structure> holder = structureRegistry.getHolderOrThrow(structureKey.get());
         if (!holder.is(StructureTags.VILLAGE)) {
            continue;
         }

         BoundingBox box = start.getBoundingBox();
         int cx = (box.minX() + box.maxX()) / 2;
         int cy = (box.minY() + box.maxY()) / 2;
         int cz = (box.minZ() + box.maxZ()) / 2;
         ChunkPos centerChunk = new ChunkPos(cx >> 4, cz >> 4);
         if (!centerChunk.equals(chunk.getPos())) {
            continue;
         }

         ResourceLocation keyLoc = structureKey.get().location();
         String id = RoadNode.vanillaId(cx, cz, keyLoc.toString());
         BlockPos center = new BlockPos(cx, cy, cz);
         List<BlockPos> entrances = RoadNode.computeEntrances(box, 8);
         Holder<Biome> biome = level.getBiome(center);
         ResourceLocation biomeId = biomeRegistry.getKey(biome.value());

         RoadNode node = new RoadNode(id, RoadNodeSource.VANILLA, center, box, entrances, biomeId, null);
         if (data.getRegistry().register(node)) {
            changed = true;
            LOGGER.debug("[Roads] Discovered vanilla village {} at {}", id, center);
            org.millenaire.compat.WaystonePlacer.tryPlaceAtVanilla(level, center, "Village " + cx + "," + cz);
         }
         if (settlements.getRegistry().registerFromRoadNode(node, level)) {
            settlements.markChanged();
         }
      }

      if (changed) {
         data.setDirty();
         RoadNetworkService.get().onRegistryDirty(level);
      }
   }

   public static void registerMillenaireVillage(ServerLevel level, Village village) {
      if (!RoadNetworkConfig.enabled()) {
         return;
      }
      RoadNetworkSavedData data = RoadNetworkSavedData.get(level);
      SettlementSavedData settlements = SettlementSavedData.get(level);

      AABB aabb = village.computeBounds();
      BoundingBox box = new BoundingBox(
         (int)Math.floor(aabb.minX),
         (int)Math.floor(aabb.minY),
         (int)Math.floor(aabb.minZ),
         (int)Math.floor(aabb.maxX),
         (int)Math.floor(aabb.maxY),
         (int)Math.floor(aabb.maxZ)
      );
      VillageType type = ModCultures.getVillageType(village.getVillageTypeId());
      if (type != null && village.getBuildings().isEmpty()) {
         int r = type.radius();
         BlockPos c = village.getCenter();
         box = new BoundingBox(c.getX() - r, c.getY() - 16, c.getZ() - r, c.getX() + r, c.getY() + 64, c.getZ() + r);
      }

      String id = RoadNode.millenaireId(village.getId().uuid());
      List<BlockPos> entrances = RoadNode.computeEntrances(box, 8);
      Holder<Biome> biome = level.getBiome(village.getCenter());
      ResourceLocation biomeId = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome.value());

      RoadNode node = new RoadNode(
         id, RoadNodeSource.MILLENAIRE, village.getCenter(), box, entrances, biomeId, village.getId().uuid()
      );
      boolean added = data.getRegistry().register(node);
      settlements.getRegistry().registerMillenaire(village, node, level);
      settlements.markChanged();
      if (added) {
         data.setDirty();
         RoadNetworkService.get().onRegistryDirty(level);
         LOGGER.debug("[Roads] Registered Millenaire village {}", id);
      }
   }

   public static void syncAllMillenaire(ServerLevel level, VillageManager manager) {
      for (Village v : manager.getAllVillages()) {
         registerMillenaireVillage(level, v);
      }
   }
}
