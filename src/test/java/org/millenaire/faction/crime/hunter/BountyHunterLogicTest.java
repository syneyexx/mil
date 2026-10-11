package org.millenaire.faction.crime.hunter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.millenaire.entity.BountyHunterEntity;
import org.millenaire.faction.crime.WantedTier;

class BountyHunterLogicTest {
   @Test
   void attributeDefaultsMatchSpec() {
      var attrs = BountyHunterEntity.createAttributes().build();
      assertEquals(44.0, attrs.getValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH), 0.001);
      assertEquals(6.0, attrs.getValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE), 0.001);
      assertEquals(0.29, attrs.getValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED), 0.001);
      assertEquals(40.0, attrs.getValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE), 0.001);
      assertEquals(0.35, attrs.getValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE), 0.001);
      assertEquals(8.0, attrs.getValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR), 0.001);
   }

   @Test
   void phasePursuitFlags() {
      assertTrue(BountyHunterPhase.PURSUING.isActivePursuit());
      assertTrue(BountyHunterPhase.RESTRAINING.isActivePursuit());
      assertFalse(BountyHunterPhase.COMPLETE.isActivePursuit());
      assertTrue(BountyHunterPhase.COMPLETE.shouldDespawnWhenIdle());
      assertFalse(BountyHunterPhase.TRAVELLING.shouldDespawnWhenIdle());
   }

   @Test
   void bountyKeyStable() {
      UUID player = UUID.fromString("11111111-1111-1111-1111-111111111111");
      ResourceLocation culture = ResourceLocation.fromNamespaceAndPath("millenaire", "norman");
      assertEquals(player + "|" + culture, BountyEligibilityService.bountyKey(player, culture));
   }

   @Test
   void wantedTierGateMatchesDispatchDefault() {
      assertTrue(WantedTier.WANTED.isAtLeast(WantedTier.WANTED));
      assertFalse(WantedTier.SUSPECT.isAtLeast(WantedTier.WANTED));
      assertTrue(WantedTier.SCOURGE.isAtLeast(WantedTier.WANTED));
   }

   @Test
   void contractStoresIdentity() {
      UUID contractId = UUID.randomUUID();
      UUID player = UUID.randomUUID();
      ResourceLocation culture = ResourceLocation.fromNamespaceAndPath("millenaire", "indian");
      BountyHunterContract contract = new BountyHunterContract(
         contractId, player, culture, BountyEligibilityService.bountyKey(player, culture), 100L, 200L
      );
      assertEquals(BountyHunterPhase.DISPATCH_PENDING, contract.phase());
      assertEquals(player, contract.targetPlayerId());
      assertEquals(culture, contract.cultureId());
      assertFalse(contract.settled());
      contract.setSettled(true);
      assertTrue(contract.settled());
   }

   @Test
   void arrestRangesMatchSpec() {
      assertEquals(2.15, BountyHunterEntity.ARREST_RANGE, 0.001);
      assertEquals(5.0, BountyHunterEntity.BOLA_MIN_RANGE, 0.001);
      assertEquals(12.0, BountyHunterEntity.BOLA_MAX_RANGE, 0.001);
   }
}
