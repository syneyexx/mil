package org.millenaire.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure-logic coverage for Priest/Cultist combat contracts that do not require a live Level.
 */
class InfernalCultCombatLogicTest {
   @Test
   void priestCombatConstantsMatchSpec() {
      assertEquals(8.0F, InfernalHexBoltEntity.DIRECT_DAMAGE, 0.001F);
      assertEquals(4, AntichristPriestEntity.MAX_OWNED_SUMMONS);
      assertEquals(8, AntichristPriestEntity.MAX_LOCAL_SUMMONS);
      assertEquals(24.0, AntichristPriestEntity.LOCAL_SUMMON_RADIUS, 0.001);
      assertEquals(28.0, AntichristPriestEntity.SUMMON_TARGET_RANGE, 0.001);
      assertEquals(600, AntichristPriestEntity.SUMMON_COOLDOWN_TICKS);
      assertEquals(30, AntichristPriestEntity.SUMMON_WINDUP_TICKS);
      assertEquals(5.0F, AntichristPriestEntity.HEAL_AMOUNT, 0.001F);
      assertEquals(10.0, AntichristPriestEntity.HEAL_RADIUS, 0.001);
      assertEquals(2, AntichristPriestEntity.HEAL_MAX_TARGETS);
      assertEquals(200, AntichristPriestEntity.HEAL_COOLDOWN_TICKS);
      assertEquals(60, AntichristPriestEntity.PROJECTILE_COOLDOWN_MIN);
      assertEquals(80, AntichristPriestEntity.PROJECTILE_COOLDOWN_MAX);
   }

   @Test
   void cultistCombatConstantsMatchSpec() {
      assertEquals(2400, InfernalCultistEntity.SUMMONED_LIFESPAN_TICKS);
      assertEquals(200, InfernalCultistEntity.OWNER_DEATH_DESPAWN_TICKS);
      assertEquals(200, InfernalCultistEntity.OWNER_MISSING_GRACE_TICKS);
      assertEquals(0, InfernalCultistEntity.VARIANT_RITUALIST);
      assertEquals(1, InfernalCultistEntity.VARIANT_ZEALOT);
   }

   @Test
   void priestAttributeSupplierMatchesSpec() {
      var attrs = AntichristPriestEntity.createAttributes().build();
      assertEquals(50.0, attrs.getValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH), 0.001);
      assertEquals(6.0, attrs.getValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE), 0.001);
      assertEquals(0.23, attrs.getValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED), 0.001);
      assertEquals(36.0, attrs.getValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE), 0.001);
      assertEquals(0.30, attrs.getValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE), 0.001);
   }

   @Test
   void cultistAttributeSupplierMatchesSpec() {
      var attrs = InfernalCultistEntity.createAttributes().build();
      assertEquals(26.0, attrs.getValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH), 0.001);
      assertEquals(7.0, attrs.getValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE), 0.001);
      assertEquals(0.29, attrs.getValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED), 0.001);
      assertEquals(30.0, attrs.getValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE), 0.001);
      assertEquals(0.10, attrs.getValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE), 0.001);
   }

   @Test
   void nightSpawnGateRejectsDayAndPeacefulNaturalSpawns() {
      assertFalse(InfernalNightSpawnRules.allowsNaturalSpawn(true, false, true));
      assertFalse(InfernalNightSpawnRules.allowsNaturalSpawn(false, false, false));
      assertTrue(InfernalNightSpawnRules.allowsNaturalSpawn(false, false, true));
      assertTrue(InfernalNightSpawnRules.allowsNaturalSpawn(false, true, false));
      assertFalse(InfernalNightSpawnRules.allowsNaturalSpawn(true, true, true));
   }

   @Test
   void factionHelperTreatsNullSafely() {
      assertTrue(InfernalFaction.areAllies(null, null));
      assertFalse(InfernalFaction.isInfernalCultMember(null));
   }

   @Test
   void cultistsDoNotRecursivelySummon() {
      assertTrue(hasMethod(AntichristPriestEntity.class, "trySummonCultists"));
      assertTrue(hasMethod(AntichristPriestEntity.class, "tryHealCultists"));
      assertFalse(hasMethod(InfernalCultistEntity.class, "trySummonCultists"));
      assertFalse(hasMethod(InfernalCultistEntity.class, "tryHealCultists"));
   }

   private static boolean hasMethod(Class<?> type, String name) {
      for (var method : type.getDeclaredMethods()) {
         if (method.getName().equals(name)) {
            return true;
         }
      }
      return false;
   }
}
