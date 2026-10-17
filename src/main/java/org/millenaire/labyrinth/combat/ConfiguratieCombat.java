package org.millenaire.labyrinth.combat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.labyrinth.entity.GebondeneAttitude;
import org.millenaire.labyrinth.entity.GebondeneEntity;

/**
 * Labyrinth-only Configuratie KETTING pulse: roots nearby hunting Gebondenen briefly.
 */
public final class ConfiguratieCombat {
   private static final double RADIUS = 8.0;

   private ConfiguratieCombat() {
   }

   public static boolean tryPulse(ServerPlayer player, ServerLevel level) {
      if (!combatEnabled()) {
         player.displayClientMessage(Component.translatable("labyrinth.configuratie.combat_disabled"), true);
         return true;
      }

      AABB box = player.getBoundingBox().inflate(RADIUS);
      int hit = 0;
      for (GebondeneEntity entity : level.getEntitiesOfClass(GebondeneEntity.class, box)) {
         if (entity.getAttitude() == GebondeneAttitude.GASTVRIJ) {
            continue;
         }
         entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3));
         entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
         entity.hurt(level.damageSources().magic(), 4.0F);
         hit++;
      }

      level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.5F);
      level.sendParticles(
         ParticleTypes.SONIC_BOOM,
         player.getX(),
         player.getY() + 1.0,
         player.getZ(),
         1,
         0,
         0,
         0,
         0
      );
      if (hit == 0) {
         player.displayClientMessage(Component.translatable("labyrinth.configuratie.ketting_miss"), true);
      } else {
         player.displayClientMessage(Component.translatable("labyrinth.configuratie.ketting_hit", hit), true);
      }
      return true;
   }

   private static boolean combatEnabled() {
      try {
         return MillenaireServerConfig.SERVER.labyrinthConfigCombatEnabled.getAsBoolean();
      } catch (IllegalStateException ignored) {
         return true;
      }
   }
}
