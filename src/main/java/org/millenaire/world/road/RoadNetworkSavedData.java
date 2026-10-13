package org.millenaire.world.road;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class RoadNetworkSavedData extends SavedData {
   public static final String DATA_NAME = "millenaire_road_network";
   public static final int CURRENT_VERSION = 1;

   private final VillageRegistry registry = new VillageRegistry();
   private final RoadGraph graph = new RoadGraph();
   private final ChunkRouteIndex chunkIndex = new ChunkRouteIndex();

   private RoadNetworkSavedData() {
   }

   public static Factory<RoadNetworkSavedData> factory() {
      return new Factory<>(RoadNetworkSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static RoadNetworkSavedData get(ServerLevel level) {
      return level.getServer().overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public VillageRegistry getRegistry() {
      return this.registry;
   }

   public RoadGraph getGraph() {
      return this.graph;
   }

   public ChunkRouteIndex getChunkIndex() {
      return this.chunkIndex;
   }

   public void rebuildChunkIndex() {
      this.chunkIndex.clear();
      for (RoadEdge edge : this.graph.getEdges()) {
         if (edge.polyline() != null) {
            this.chunkIndex.indexEdgeFromPolyline(edge.id(), edge.polyline());
         }
      }
   }

   private static RoadNetworkSavedData load(CompoundTag tag) {
      RoadNetworkSavedData data = new RoadNetworkSavedData();
      int version = tag.getInt("version");
      if (tag.contains("registry")) {
         data.registry.load(tag.getCompound("registry"));
      }
      if (tag.contains("graph")) {
         data.graph.load(tag.getCompound("graph"));
      }
      data.rebuildChunkIndex();
      if (version != CURRENT_VERSION) {
         data.setDirty();
      }
      return data;
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putInt("version", CURRENT_VERSION);
      tag.put("registry", this.registry.save());
      tag.put("graph", this.graph.save());
      return tag;
   }
}
