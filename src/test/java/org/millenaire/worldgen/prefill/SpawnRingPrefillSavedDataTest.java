package org.millenaire.worldgen.prefill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class SpawnRingPrefillSavedDataTest {
   @Test
   void regionCellOffsetRoundTrips() {
      SpawnRingPrefillSavedData data = new SpawnRingPrefillSavedData();
      data.setRegions(List.of(1L, 2L, 3L));
      data.setRegionCellOffset(5);
      SpawnRingPrefillSavedData loaded = SpawnRingPrefillSavedData.load(data.save(new CompoundTag(), null));
      assertEquals(5, loaded.regionCellOffset());
      assertEquals(3, loaded.regionCount());
   }

   @Test
   void legacyTagDefaultsCellOffsetToZero() {
      CompoundTag legacy = new CompoundTag();
      legacy.putString("phase", "CHUNKING");
      legacy.putInt("regionIndex", 2);
      assertEquals(0, SpawnRingPrefillSavedData.load(legacy).regionCellOffset());
   }

   @Test
   void incrementRegionIndexResetsCellOffset() {
      SpawnRingPrefillSavedData data = new SpawnRingPrefillSavedData();
      data.setRegionCellOffset(7);
      data.incrementRegionIndex();
      assertEquals(0, data.regionCellOffset());
      assertEquals(1, data.regionIndex());
   }

   @Test
   void cellOffsetIsClamped() {
      SpawnRingPrefillSavedData data = new SpawnRingPrefillSavedData();
      data.setRegionCellOffset(99);
      assertEquals(SpawnRingPrefillSavedData.MAX_REGION_CELL_OFFSET, data.regionCellOffset());
      data.setRegionCellOffset(-3);
      assertEquals(0, data.regionCellOffset());
   }
}
