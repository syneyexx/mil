package org.millenaire.client.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.millenaire.map.sync.SettlementMapEntry;

class SettlementMapClientCacheTest {
   private SettlementMapClientCache cache;

   @BeforeEach
   void setUp() {
      cache = SettlementMapClientCache.get();
      cache.clear();
   }

   @Test
   void snapshotAndDeltaIdempotent() {
      UUID a = UUID.randomUUID();
      UUID b = UUID.randomUUID();
      SettlementMapEntry ea = entry(a, "Alpha");
      SettlementMapEntry eb = entry(b, "Beta");
      assertTrue(cache.applySnapshotPart("world1", 1, 0, 1, List.of(ea, eb)));
      assertEquals(2, cache.entries().size());

      SettlementMapEntry ea2 = entry(a, "AlphaRenamed");
      assertTrue(cache.applyDelta("world1", 2, List.of(ea2), List.of(b)));
      assertEquals(1, cache.entries().size());
      assertEquals("AlphaRenamed", cache.entries().iterator().next().name());
   }

   @Test
   void worldTokenMismatchClearsOnSnapshot() {
      UUID a = UUID.randomUUID();
      cache.applySnapshotPart("world1", 1, 0, 1, List.of(entry(a, "A")));
      assertTrue(cache.applySnapshotPart("world2", 1, 0, 1, List.of(entry(UUID.randomUUID(), "B"))));
      assertEquals("world2", cache.worldToken());
      assertEquals(1, cache.entries().size());
   }

   @Test
   void deltaWithoutMatchingTokenIgnored() {
      assertFalse(cache.applyDelta("other", 1, List.of(entry(UUID.randomUUID(), "X")), List.of()));
      assertTrue(cache.entries().isEmpty());
   }

   private static SettlementMapEntry entry(UUID id, String name) {
      return new SettlementMapEntry(
         id,
         name,
         ResourceLocation.fromNamespaceAndPath("millenaire", "norman"),
         ResourceLocation.fromNamespaceAndPath("millenaire", "agricultural"),
         "village",
         10,
         64,
         20,
         ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
         SettlementMapEntry.FLAG_CONTROLLED,
         null,
         1,
         null
      );
   }
}
