package org.millenaire.labyrinth.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.millenaire.entity.ModEntities;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.gen.LabyrinthShifter;

/**
 * Multi-phase Architect boss logic while attitude is JACHT.
 */
public final class ArchitectCombat {
   private ArchitectCombat() {
   }

   public static void tick(GebondeneEntity architect) {
      if (architect.getKind() != GebondeneKind.ARCHITECT) {
         return;
      }
      if (architect.getAttitude() != GebondeneAttitude.JACHT) {
         return;
      }
      if (!(architect.level() instanceof ServerLevel level)) {
         return;
      }
      LivingEntity target = architect.getTarget();
      if (target == null) {
         return;
      }

      float healthFrac = architect.getHealth() / architect.getMaxHealth();
      int phase = healthFrac > 0.66F ? 1 : healthFrac > 0.33F ? 2 : 3;
      architect.getPersistentData().putInt("ArchitectPhase", phase);

      if (architect.tickCount % 80 == 0) {
         pulse(level, architect, phase);
      }
      if (phase >= 2 && architect.tickCount % 160 == 0) {
         summonBound(level, architect);
      }
      if (phase >= 3 && architect.tickCount % 200 == 0) {
         LabyrinthShifter.shiftCoreArena(level, architect.blockPosition());
         level.playSound(null, architect.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.HOSTILE, 1.2F, 0.6F);
      }
      if (architect.tickCount % 40 == 0) {
         level.sendParticles(
            ParticleTypes.SOUL,
            architect.getX(),
            architect.getY() + 1.5,
            architect.getZ(),
            8,
            0.4,
            0.6,
            0.4,
            0.02
         );
      }
   }

   private static void pulse(ServerLevel level, GebondeneEntity architect, int phase) {
      LivingEntity target = architect.getTarget();
      if (target == null) {
         return;
      }
      double damage = 4.0 + phase * 2.0;
      if (target.distanceToSqr(architect) < (6.0 + phase) * (6.0 + phase)) {
         target.hurt(level.damageSources().mobAttack(architect), (float) damage);
         target.knockback(0.4 + 0.1 * phase, architect.getX() - target.getX(), architect.getZ() - target.getZ());
      }
      level.playSound(null, architect.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.5F, 1.4F);
      level.sendParticles(
         ParticleTypes.SONIC_BOOM,
         architect.getX(),
         architect.getY() + 1.0,
         architect.getZ(),
         1,
         0,
         0,
         0,
         0
      );
      if (architect.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
         architect.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.24 + 0.02 * phase);
      }
   }

   private static void summonBound(ServerLevel level, GebondeneEntity architect) {
      BlockPos base = architect.blockPosition();
      for (int i = 0; i < 2; i++) {
         GebondeneEntity minion = ModEntities.GEBONDENE.get().create(level);
         if (minion == null) {
            continue;
         }
         GebondeneKind kind = i == 0 ? GebondeneKind.RAMMELAAR : GebondeneKind.STILLE;
         minion.setKind(kind);
         minion.setAttitude(GebondeneAttitude.JACHT);
         minion.moveTo(
            base.getX() + 1.5 - i * 3.0,
            LabyrinthKeys.FLOOR_Y + 1.0,
            base.getZ() + 1.5,
            architect.getYRot(),
            0.0F
         );
         if (architect.getTarget() != null) {
            minion.setTarget(architect.getTarget());
         }
         level.addFreshEntity(minion);
      }
      level.playSound(null, base, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.0F, 0.8F);
   }
}
