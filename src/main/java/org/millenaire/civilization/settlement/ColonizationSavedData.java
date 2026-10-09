package org.millenaire.civilization.settlement;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.slf4j.Logger;

/**
 * Persistent colonization / autonomous founding operations.
 */
public class ColonizationSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String DATA_NAME = "millenaire_colonization";
   public static final int SCHEMA = 2;
   private static final int RETENTION = 128;

   private final Map<UUID, ColonizationOperation> operations = new HashMap<>();
   private int schema = SCHEMA;

   public static Factory<ColonizationSavedData> factory() {
      return new Factory<>(ColonizationSavedData::new, (tag, regs) -> load(tag), null);
   }

   public static ColonizationSavedData get(ServerLevel level) {
      return level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public Map<UUID, ColonizationOperation> operations() {
      return this.operations;
   }

   @Nullable
   public ColonizationOperation get(UUID id) {
      return id == null ? null : this.operations.get(id);
   }

   public void put(ColonizationOperation op) {
      if (op == null) {
         return;
      }
      this.operations.put(op.operationId(), op);
      if (this.operations.size() > RETENTION) {
         this.operations.entrySet().removeIf(e -> e.getValue().isTerminal() && this.operations.size() > RETENTION / 2);
      }
      this.setDirty();
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putInt("schema", this.schema);
      ListTag list = new ListTag();
      for (ColonizationOperation op : this.operations.values()) {
         list.add(op.save());
      }
      tag.put("ops", list);
      return tag;
   }

   private static ColonizationSavedData load(CompoundTag tag) {
      ColonizationSavedData data = new ColonizationSavedData();
      data.schema = tag.contains("schema") ? tag.getInt("schema") : SCHEMA;
      ListTag list = tag.getList("ops", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         ColonizationOperation op = ColonizationOperation.load(list.getCompound(i));
         if (op != null) {
            data.operations.put(op.operationId(), op);
         } else {
            LOGGER.warn("[Millenaire] Skipping corrupt colonization op #{}", i);
         }
      }
      return data;
   }
}
