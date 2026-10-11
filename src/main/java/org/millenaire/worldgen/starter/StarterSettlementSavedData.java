package org.millenaire.worldgen.starter;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

/**
 * One-shot gate for the guaranteed starter settlement at world spawn.
 */
public final class StarterSettlementSavedData extends SavedData {
   private static final String DATA_NAME = "millenaire_starter_settlement";

   private boolean placed;
   private boolean gaveUp;
   private int attempts;
   @Nullable
   private UUID villageId;
   @Nullable
   private BlockPos centre;

   private StarterSettlementSavedData() {
   }

   public static Factory<StarterSettlementSavedData> factory() {
      return new Factory<>(StarterSettlementSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static StarterSettlementSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      ServerLevel storage = overworld != null ? overworld : level;
      return storage.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public boolean isPlaced() {
      return this.placed;
   }

   public boolean hasGaveUp() {
      return this.gaveUp;
   }

   public int attempts() {
      return this.attempts;
   }

   public void incrementAttempts() {
      this.attempts++;
      this.setDirty();
   }

   public void markGaveUp() {
      this.gaveUp = true;
      this.setDirty();
   }

   public void markPlaced(UUID villageId, BlockPos centre) {
      this.placed = true;
      this.villageId = villageId;
      this.centre = centre.immutable();
      this.setDirty();
   }

   /** Existing-world skip: culture already present, treat as satisfied without spawning. */
   public void markSatisfiedExisting() {
      this.placed = true;
      this.setDirty();
   }

   /**
    * Clears sticky failure / incomplete state so {@link StarterSettlementService} can try again.
    * Does not remove an already-spawned village.
    */
   public void prepareRetry() {
      this.gaveUp = false;
      this.attempts = 0;
      this.placed = false;
      this.villageId = null;
      this.centre = null;
      this.setDirty();
   }

   @Nullable
   public UUID villageId() {
      return this.villageId;
   }

   @Nullable
   public BlockPos centre() {
      return this.centre;
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putBoolean("placed", this.placed);
      tag.putBoolean("gaveUp", this.gaveUp);
      tag.putInt("attempts", this.attempts);
      if (this.villageId != null) {
         tag.putUUID("villageId", this.villageId);
      }
      if (this.centre != null) {
         tag.putLong("centre", this.centre.asLong());
      }
      return tag;
   }

   private static StarterSettlementSavedData load(CompoundTag tag) {
      StarterSettlementSavedData data = new StarterSettlementSavedData();
      data.placed = tag.getBoolean("placed");
      data.gaveUp = tag.getBoolean("gaveUp");
      data.attempts = tag.getInt("attempts");
      if (tag.hasUUID("villageId")) {
         data.villageId = tag.getUUID("villageId");
      }
      if (tag.contains("centre")) {
         data.centre = BlockPos.of(tag.getLong("centre"));
      }
      return data;
   }
}
