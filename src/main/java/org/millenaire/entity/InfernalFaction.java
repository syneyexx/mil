package org.millenaire.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Shared ally checks for Antichrist Priest / Infernal Cultist combat and projectiles.
 */
public final class InfernalFaction {
   private InfernalFaction() {
   }

   public static boolean isInfernalCultMember(Entity entity) {
      return entity instanceof AntichristPriestEntity || entity instanceof InfernalCultistEntity;
   }

   public static boolean areAllies(Entity a, Entity b) {
      if (a == null || b == null || a == b) {
         return a == b;
      }
      return isInfernalCultMember(a) && isInfernalCultMember(b);
   }

   public static boolean shouldSkipHostileTarget(LivingEntity attacker, LivingEntity candidate) {
      return areAllies(attacker, candidate);
   }
}
