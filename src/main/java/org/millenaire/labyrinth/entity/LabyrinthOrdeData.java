package org.millenaire.labyrinth.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.labyrinth.LabyrinthKeys;

/**
 * Per-player Labyrinth order state: region reputation and hostility flags,
 * plus optional overworld return anchors used by the teleporter.
 */
public final class LabyrinthOrdeData extends SavedData {
   public static final String DATA_NAME = "millenaire_labyrinth_orde";
   public static final int CURRENT_VERSION = 1;

   private final Map<UUID, PlayerOrde> players = new HashMap<>();

   private LabyrinthOrdeData() {
   }

   public static Factory<LabyrinthOrdeData> factory() {
      return new Factory<>(LabyrinthOrdeData::new, (tag, registries) -> load(tag), null);
   }

   public static LabyrinthOrdeData get(ServerLevel level) {
      ServerLevel storageLevel = level.dimension().equals(LabyrinthKeys.LABYRINTH_LEVEL)
         ? level
         : level.getServer().getLevel(LabyrinthKeys.LABYRINTH_LEVEL);
      if (storageLevel == null) {
         storageLevel = level.getServer().overworld();
      }
      return storageLevel.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public PlayerOrde getOrCreate(UUID playerId) {
      return this.players.computeIfAbsent(playerId, id -> new PlayerOrde());
   }

   @Nullable
   public PlayerOrde get(UUID playerId) {
      return this.players.get(playerId);
   }

   public int getReputation(UUID playerId, int regionX, int regionZ) {
      PlayerOrde orde = this.players.get(playerId);
      if (orde == null) {
         return 0;
      }
      return orde.regionReputation.getOrDefault(regionKey(regionX, regionZ), 0);
   }

   public void addReputation(UUID playerId, int regionX, int regionZ, int delta) {
      PlayerOrde orde = getOrCreate(playerId);
      String key = regionKey(regionX, regionZ);
      orde.regionReputation.put(key, orde.regionReputation.getOrDefault(key, 0) + delta);
      setDirty();
   }

   public boolean isHostile(UUID playerId) {
      PlayerOrde orde = this.players.get(playerId);
      return orde != null && orde.hostile;
   }

   public void setHostile(UUID playerId, boolean hostile) {
      PlayerOrde orde = getOrCreate(playerId);
      orde.hostile = hostile;
      setDirty();
   }

   public void storeReturnPosition(UUID playerId, ResourceKey<Level> dimension, BlockPos pos, float yRot, float xRot) {
      PlayerOrde orde = getOrCreate(playerId);
      orde.returnDimension = dimension.location().toString();
      orde.returnX = pos.getX() + 0.5;
      orde.returnY = pos.getY();
      orde.returnZ = pos.getZ() + 0.5;
      orde.returnYRot = yRot;
      orde.returnXRot = xRot;
      orde.hasReturn = true;
      setDirty();
   }

   public void clearReturnPosition(UUID playerId) {
      PlayerOrde orde = this.players.get(playerId);
      if (orde != null && orde.hasReturn) {
         orde.hasReturn = false;
         setDirty();
      }
   }

   private static String regionKey(int regionX, int regionZ) {
      return regionX + "," + regionZ;
   }

   private static LabyrinthOrdeData load(CompoundTag tag) {
      LabyrinthOrdeData data = new LabyrinthOrdeData();
      ListTag list = tag.getList("Players", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         CompoundTag entry = list.getCompound(i);
         if (!entry.hasUUID("Id")) {
            continue;
         }
         UUID id = entry.getUUID("Id");
         PlayerOrde orde = new PlayerOrde();
         orde.hostile = entry.getBoolean("Hostile");
         orde.hasReturn = entry.getBoolean("HasReturn");
         orde.returnDimension = entry.getString("ReturnDim");
         orde.returnX = entry.getDouble("ReturnX");
         orde.returnY = entry.getDouble("ReturnY");
         orde.returnZ = entry.getDouble("ReturnZ");
         orde.returnYRot = entry.getFloat("ReturnYRot");
         orde.returnXRot = entry.getFloat("ReturnXRot");
         CompoundTag repTag = entry.getCompound("Reputation");
         for (String key : repTag.getAllKeys()) {
            orde.regionReputation.put(key, repTag.getInt(key));
         }
         data.players.put(id, orde);
      }
      if (tag.getInt("Version") != CURRENT_VERSION) {
         data.setDirty();
      }
      return data;
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putInt("Version", CURRENT_VERSION);
      ListTag list = new ListTag();
      for (Map.Entry<UUID, PlayerOrde> entry : this.players.entrySet()) {
         CompoundTag playerTag = new CompoundTag();
         playerTag.putUUID("Id", entry.getKey());
         PlayerOrde orde = entry.getValue();
         playerTag.putBoolean("Hostile", orde.hostile);
         playerTag.putBoolean("HasReturn", orde.hasReturn);
         if (orde.returnDimension != null) {
            playerTag.putString("ReturnDim", orde.returnDimension);
         }
         playerTag.putDouble("ReturnX", orde.returnX);
         playerTag.putDouble("ReturnY", orde.returnY);
         playerTag.putDouble("ReturnZ", orde.returnZ);
         playerTag.putFloat("ReturnYRot", orde.returnYRot);
         playerTag.putFloat("ReturnXRot", orde.returnXRot);
         CompoundTag repTag = new CompoundTag();
         for (Map.Entry<String, Integer> rep : orde.regionReputation.entrySet()) {
            repTag.putInt(rep.getKey(), rep.getValue());
         }
         playerTag.put("Reputation", repTag);
         list.add(playerTag);
      }
      tag.put("Players", list);
      return tag;
   }

   public static final class PlayerOrde {
      public boolean hostile;
      public boolean hasReturn;
      public String returnDimension = ResourceLocation.withDefaultNamespace("overworld").toString();
      public double returnX;
      public double returnY;
      public double returnZ;
      public float returnYRot;
      public float returnXRot;
      public final Map<String, Integer> regionReputation = new HashMap<>();

      @Nullable
      public ResourceKey<Level> returnDimensionKey() {
         if (this.returnDimension == null || this.returnDimension.isBlank()) {
            return Level.OVERWORLD;
         }
         ResourceLocation loc = ResourceLocation.tryParse(this.returnDimension);
         if (loc == null) {
            return Level.OVERWORLD;
         }
         return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, loc);
      }
   }
}
