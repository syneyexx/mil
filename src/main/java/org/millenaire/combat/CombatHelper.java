package org.millenaire.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers.Entry;
import net.minecraft.world.phys.AABB;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.VillageId;

public final class CombatHelper {
   private CombatHelper() {
   }

   public static void callForHelp(MillVillager victim, LivingEntity attacker) {
      VillageId victimVillage = victim.getVillageId();
      if (victimVillage != null) {
         double attackRangeSq = 6400.0;
         AABB box = victim.getBoundingBox().inflate(80.0);

         for (MillVillager other : victim.level().getEntitiesOfClass(MillVillager.class, box)) {
            if (other != victim && other.isAlive() && victimVillage.equals(other.getVillageId()) && other.getAttackTarget() == null && !other.isRaiderEntity()) {
               VillagerType type = ModCultures.getVillagerType(other.getVillagerTypeId());
               if (type != null && type.isHelpInAttacks() && !(other.distanceToSqr(attacker) >= attackRangeSq)) {
                  other.setAttackTarget(attacker);
                  other.speakCombatCall("calltoarms");
               }
            }
         }
      }
   }

   public static int entityAttackStrength(int baseAttackStrength, double weaponDamage) {
      return baseAttackStrength + (int)Math.ceil(weaponDamage / 2.0);
   }

   public static double weaponDamage(ItemStack stack) {
      for (Entry entry : stack.getAttributeModifiers().modifiers()) {
         if (entry.attribute().equals(Attributes.ATTACK_DAMAGE) && entry.slot().test(EquipmentSlot.MAINHAND)) {
            return entry.modifier().amount();
         }
      }

      return 0.0;
   }

   /**
    * Whether Millenaire NPC/crime systems should treat this player as a normal survival target.
    * Spectator is always ignored. Creative is ignored unless {@code treatCreativeAsSurvival} is true.
    */
   public static boolean isPlayerTargetable(Player player) {
      if (player == null || player.isSpectator()) {
         return false;
      }
      if (!player.isCreative()) {
         return true;
      }
      return treatCreativeAsSurvival();
   }

   /** Prefer {@link #isPlayerTargetable(Player)} so the config is applied. */
   @Deprecated
   public static boolean isPlayerTargetable(boolean creative, boolean spectator) {
      if (spectator) {
         return false;
      }
      if (!creative) {
         return true;
      }
      return treatCreativeAsSurvival();
   }

   public static boolean isPlayerTargetable(ServerPlayer player) {
      return isPlayerTargetable((Player) player);
   }

   private static boolean treatCreativeAsSurvival() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.treatCreativeAsSurvival.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   public static boolean isVillagerHostile(boolean selfIsRaiderEntity, boolean attackerIsRaiderEntity, boolean sameVillage) {
      return selfIsRaiderEntity != attackerIsRaiderEntity || !sameVillage;
   }

   public static boolean canTeleport(boolean hasNoTeleportTag, boolean isRaider) {
      return !hasNoTeleportTag || isRaider;
   }

   public static CombatHelper.AttackType decideAttack(double distance, boolean isArcher, boolean hasBow, int attackCooldown, boolean verticalOverlap) {
      boolean archerBranch = isArcher && hasBow && distance > 5.0;
      if (archerBranch) {
         return distance < 20.0 && attackCooldown <= 0 ? CombatHelper.AttackType.RANGED : CombatHelper.AttackType.NONE;
      } else {
         return attackCooldown <= 0 && distance < 2.0 && verticalOverlap ? CombatHelper.AttackType.MELEE : CombatHelper.AttackType.NONE;
      }
   }

   public enum AttackType {
      NONE,
      MELEE,
      RANGED;
   }
}
