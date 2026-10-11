package org.millenaire.companion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class CompanionBondTest {
   @Test
   void roundTripsNbtAndCapsLists() {
      UUID player = UUID.randomUUID();
      CompanionBond bond = new CompanionBond(player);
      bond.setStance(CompanionStance.VILLAGE_DAY_JOB);
      bond.addPreference("prefers daytime travel");
      bond.addTravelLine("visited biome plains");
      bond.addSharedEvent("fought a creeper");
      bond.addActionResult("FOLLOW_PLAYER ok");
      bond.setDismissed(false);

      CompoundTag tag = bond.save();
      CompanionBond loaded = CompanionBond.load(tag);
      assertEquals(player, loaded.playerId());
      assertEquals(CompanionStance.VILLAGE_DAY_JOB, loaded.stance());
      assertTrue(loaded.preferences().contains("prefers daytime travel"));
      assertTrue(loaded.travelLines().contains("visited biome plains"));
      assertTrue(loaded.sharedEvents().contains("fought a creeper"));
      assertFalse(loaded.dismissed());
   }

   @Test
   void capsPreferenceList() {
      CompanionBond bond = new CompanionBond(UUID.randomUUID());
      for (int i = 0; i < CompanionBond.MAX_PREFERENCES + 5; i++) {
         bond.addPreference("pref " + i);
      }
      assertEquals(CompanionBond.MAX_PREFERENCES, bond.preferences().size());
   }
}
