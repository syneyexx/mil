package org.millenaire.map.sync;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettlementMapVisibilityTest {
   @Test
   void undiscoveredForeignHidden() {
      assertFalse(SettlementMapVisibility.shouldInclude(false, false, false, false, false));
   }

   @Test
   void discoveredIncluded() {
      assertTrue(SettlementMapVisibility.shouldInclude(true, false, false, false, false));
   }

   @Test
   void controlledAlwaysIncluded() {
      assertTrue(SettlementMapVisibility.shouldInclude(false, true, false, false, false));
   }

   @Test
   void sovereignCityAlwaysIncluded() {
      assertTrue(SettlementMapVisibility.shouldInclude(false, false, true, false, false));
   }

   @Test
   void loneExcludedUnlessConfigured() {
      assertFalse(SettlementMapVisibility.shouldInclude(true, false, false, true, false));
      assertTrue(SettlementMapVisibility.shouldInclude(true, false, false, true, true));
   }

   @Test
   void clientCategories() {
      assertTrue(SettlementMapVisibility.clientCategoryVisible("village", false, true, true, true, true, true));
      assertFalse(SettlementMapVisibility.clientCategoryVisible("hamlet", false, false, true, true, true, true));
      assertTrue(SettlementMapVisibility.clientCategoryVisible("regional_hub", false, true, true, true, true, true));
      assertFalse(SettlementMapVisibility.clientCategoryVisible("town", false, true, true, false, true, true));
      assertTrue(SettlementMapVisibility.clientCategoryVisible("village", true, false, false, false, false, true));
      assertFalse(SettlementMapVisibility.clientCategoryVisible("village", true, true, true, true, true, false));
   }
}
