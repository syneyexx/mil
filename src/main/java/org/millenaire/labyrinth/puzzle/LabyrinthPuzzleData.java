package org.millenaire.labyrinth.puzzle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.labyrinth.LabyrinthKeys;

/**
 * Persists rotary rotation indices and opened relic gates per Labyrinth cell.
 */
public final class LabyrinthPuzzleData extends SavedData {
   public static final String DATA_NAME = "millenaire_labyrinth_puzzles";

   private final Map<Long, Integer> rotaryRotation = new HashMap<>();
   private final Set<String> openGates = new HashSet<>();
   private final Set<Long> relicSpawned = new HashSet<>();

   private LabyrinthPuzzleData() {
   }

   public static Factory<LabyrinthPuzzleData> factory() {
      return new Factory<>(LabyrinthPuzzleData::new, (tag, registries) -> load(tag), null);
   }

   public static LabyrinthPuzzleData get(ServerLevel level) {
      ServerLevel storageLevel = level.dimension().equals(LabyrinthKeys.LABYRINTH_LEVEL)
         ? level
         : level.getServer().getLevel(LabyrinthKeys.LABYRINTH_LEVEL);
      if (storageLevel == null) {
         storageLevel = level.getServer().overworld();
      }
      return storageLevel.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   private static LabyrinthPuzzleData load(CompoundTag tag) {
      LabyrinthPuzzleData data = new LabyrinthPuzzleData();
      CompoundTag rotaries = tag.getCompound("rotaries");
      for (String key : rotaries.getAllKeys()) {
         try {
            data.rotaryRotation.put(Long.parseLong(key), rotaries.getInt(key));
         } catch (NumberFormatException ignored) {
         }
      }
      ListTag gates = tag.getList("gates", Tag.TAG_STRING);
      for (int i = 0; i < gates.size(); i++) {
         data.openGates.add(gates.getString(i));
      }
      ListTag relics = tag.getList("relics", Tag.TAG_STRING);
      for (int i = 0; i < relics.size(); i++) {
         try {
            data.relicSpawned.add(Long.parseLong(relics.getString(i)));
         } catch (NumberFormatException ignored) {
         }
      }
      return data;
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
      CompoundTag rotaries = new CompoundTag();
      for (Map.Entry<Long, Integer> entry : this.rotaryRotation.entrySet()) {
         rotaries.putInt(Long.toString(entry.getKey()), entry.getValue());
      }
      tag.put("rotaries", rotaries);
      ListTag gates = new ListTag();
      for (String gate : this.openGates) {
         gates.add(StringTag.valueOf(gate));
      }
      tag.put("gates", gates);
      ListTag relics = new ListTag();
      for (Long key : this.relicSpawned) {
         relics.add(StringTag.valueOf(Long.toString(key)));
      }
      tag.put("relics", relics);
      return tag;
   }

   public int rotateRotary(int cellX, int cellZ) {
      long key = cellKey(cellX, cellZ);
      int next = (this.rotaryRotation.getOrDefault(key, 0) + 1) & 1;
      this.rotaryRotation.put(key, next);
      this.setDirty();
      return next;
   }

   public int getRotary(int cellX, int cellZ) {
      return this.rotaryRotation.getOrDefault(cellKey(cellX, cellZ), 0);
   }

   public void openGate(int cellX, int cellZ, GatePuzzle.GateSlot slot) {
      this.openGates.add(gateKey(cellX, cellZ, slot));
      this.setDirty();
   }

   public boolean isGateOpen(int cellX, int cellZ, GatePuzzle.GateSlot slot) {
      return this.openGates.contains(gateKey(cellX, cellZ, slot));
   }

   public boolean hasRelicSpawned(int cellX, int cellZ) {
      return this.relicSpawned.contains(cellKey(cellX, cellZ));
   }

   public void markRelicSpawned(int cellX, int cellZ) {
      this.relicSpawned.add(cellKey(cellX, cellZ));
      this.setDirty();
   }

   private static long cellKey(int cellX, int cellZ) {
      return (((long) cellX) << 32) | (cellZ & 0xffffffffL);
   }

   private static String gateKey(int cellX, int cellZ, GatePuzzle.GateSlot slot) {
      return cellX + "," + cellZ + ":" + slot.name();
   }
}
