package org.millenaire.entity;

import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import org.jetbrains.annotations.Nullable;

public final class MillDamageVeto {
   private static final Set<ResourceKey<DamageType>> VETOED_TYPES = Set.of(
      DamageTypes.FALL,
      DamageTypes.IN_FIRE,
      DamageTypes.ON_FIRE,
      DamageTypes.LAVA,
      DamageTypes.HOT_FLOOR,
      DamageTypes.DROWN,
      DamageTypes.IN_WALL,
      DamageTypes.CACTUS,
      DamageTypes.STARVE,
      DamageTypes.WITHER,
      DamageTypes.MAGIC,
      DamageTypes.FALLING_BLOCK,
      DamageTypes.FALLING_ANVIL,
      DamageTypes.FLY_INTO_WALL,
      DamageTypes.LIGHTNING_BOLT,
      DamageTypes.GENERIC,
      DamageTypes.CRAMMING,
      DamageTypes.FREEZE,
      DamageTypes.STALAGMITE,
      DamageTypes.FALLING_STALACTITE,
      DamageTypes.SWEET_BERRY_BUSH,
      DamageTypes.CAMPFIRE,
      DamageTypes.DRY_OUT
   );
   private static final Set<ResourceKey<DamageType>> VETOED_WHEN_UNATTRIBUTED = Set.of(DamageTypes.EXPLOSION);

   private MillDamageVeto() {
   }

   public static boolean isVetoed(DamageSource source) {
      return isVetoed(
         (ResourceKey<DamageType>)source.typeHolder().unwrapKey().orElse(null), source.is(DamageTypeTags.BYPASSES_INVULNERABILITY), source.getEntity() != null
      );
   }

   public static boolean isVetoed(@Nullable ResourceKey<DamageType> type, boolean bypassesInvulnerability, boolean attributed) {
      if (bypassesInvulnerability) {
         return false;
      } else if (type == null) {
         return false;
      } else {
         return VETOED_TYPES.contains(type) ? true : VETOED_WHEN_UNATTRIBUTED.contains(type) && !attributed;
      }
   }

   public static boolean isVetoedType(ResourceKey<DamageType> type) {
      return VETOED_TYPES.contains(type);
   }
}
