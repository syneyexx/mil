package org.millenaire.compat;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.millenaire.entity.MillVillager;
import org.slf4j.Logger;

/**
 * Soft optional TacZ ([UNOFFICIAL] NeoForge port) integration.
 * Uses reflection so Millénaire compiles and runs without TacZ present.
 * When loaded, LivingEntity already implements IGunOperator via TacZ mixins.
 */
public final class TacZCompat {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final String MOD_ID = "tacz";

   private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);
   private static boolean resolved;
   private static boolean usable;

   @Nullable
   private static Method getIGunOrNull;
   @Nullable
   private static Method fromLivingEntity;
   @Nullable
   private static Method shoot;
   @Nullable
   private static Method aim;
   @Nullable
   private static Method reload;
   @Nullable
   private static Method draw;
   @Nullable
   private static Method getCurrentAmmoCount;
   @Nullable
   private static Object shootSuccess;
   @Nullable
   private static Object shootNoAmmo;
   @Nullable
   private static Object shootNotDraw;
   @Nullable
   private static Object shootCooldown;

   private TacZCompat() {
   }

   public static boolean isLoaded() {
      return LOADED;
   }

   public static boolean isUsable() {
      ensureResolved();
      return usable;
   }

   public static boolean isGunItem(Item item) {
      if (!isUsable() || item == null) {
         return false;
      }
      return isGunStack(item.getDefaultInstance());
   }

   public static boolean isGunStack(ItemStack stack) {
      if (!isUsable() || stack == null || stack.isEmpty()) {
         return false;
      }
      try {
         return getIGunOrNull.invoke(null, stack) != null;
      } catch (ReflectiveOperationException e) {
         return false;
      }
   }

   public static boolean holdingGun(LivingEntity entity) {
      return entity != null && isGunStack(entity.getMainHandItem());
   }

   /**
    * Aim and fire at the current look direction of {@code shooter} toward {@code target}.
    * Returns true if a shot was fired this call.
    */
   public static boolean tryShootAt(LivingEntity shooter, LivingEntity target) {
      if (!isUsable() || shooter == null || target == null || !holdingGun(shooter)) {
         return false;
      }
      try {
         Object operator = fromLivingEntity.invoke(null, shooter);
         if (operator == null) {
            return false;
         }

         // Ensure gun is "drawn" (TacZ state) and aimed
         if (draw != null) {
            draw.invoke(operator, (Supplier<ItemStack>)shooter::getMainHandItem);
         }
         if (aim != null) {
            aim.invoke(operator, true);
         }

         float pitch = lookPitch(shooter, target);
         float yaw = lookYaw(shooter, target);
         shooter.setXRot(pitch);
         shooter.setYRot(yaw);
         shooter.yHeadRot = yaw;

         Object result = shoot.invoke(operator, (Supplier<Float>)() -> pitch, (Supplier<Float>)() -> yaw);
         if (result != null && result.equals(shootNoAmmo)) {
            if (reload != null) {
               reload.invoke(operator);
            }
            topUpAmmoFromHand(shooter);
            return false;
         }
         if (result != null && result.equals(shootNotDraw) && draw != null) {
            draw.invoke(operator, (Supplier<ItemStack>)shooter::getMainHandItem);
            return false;
         }
         return result != null && result.equals(shootSuccess);
      } catch (ReflectiveOperationException e) {
         LOGGER.debug("[Millenaire] TacZ shoot failed: {}", e.toString());
         return false;
      }
   }

   public static void stopAiming(LivingEntity shooter) {
      if (!isUsable() || shooter == null || aim == null) {
         return;
      }
      try {
         Object operator = fromLivingEntity.invoke(null, shooter);
         if (operator != null) {
            aim.invoke(operator, false);
         }
      } catch (ReflectiveOperationException ignored) {
      }
   }

   public static boolean supportsGunCombat(LivingEntity entity) {
      return isUsable() && (entity instanceof MillVillager || entity instanceof AbstractVillager);
   }

   private static void topUpAmmoFromHand(LivingEntity shooter) {
      // Mag-fed guns store ammo in the ItemStack NBT; reload() pulls from inventory.
      // For Mill villagers we rely on TacZ reload + whatever ammo is already in the gun stack
      // after the player loaded magazines into barracks chests.
   }

   private static float lookPitch(LivingEntity shooter, LivingEntity target) {
      double dx = target.getX() - shooter.getX();
      double dy = target.getEyeY() - shooter.getEyeY();
      double dz = target.getZ() - shooter.getZ();
      double horiz = Math.sqrt(dx * dx + dz * dz);
      return (float)(-(Math.atan2(dy, horiz) * (180.0 / Math.PI)));
   }

   private static float lookYaw(LivingEntity shooter, LivingEntity target) {
      double dx = target.getX() - shooter.getX();
      double dz = target.getZ() - shooter.getZ();
      return (float)(Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
   }

   private static void ensureResolved() {
      if (resolved) {
         return;
      }
      resolved = true;
      if (!LOADED) {
         usable = false;
         return;
      }
      try {
         Class<?> iGun = Class.forName("com.tacz.guns.api.item.IGun");
         getIGunOrNull = iGun.getMethod("getIGunOrNull", ItemStack.class);
         getCurrentAmmoCount = iGun.getMethod("getCurrentAmmoCount", ItemStack.class);

         Class<?> operator = Class.forName("com.tacz.guns.api.entity.IGunOperator");
         fromLivingEntity = operator.getMethod("fromLivingEntity", LivingEntity.class);
         shoot = operator.getMethod("shoot", Supplier.class, Supplier.class);
         aim = operator.getMethod("aim", boolean.class);
         reload = operator.getMethod("reload");
         draw = operator.getMethod("draw", Supplier.class);

         Class<?> shootResult = Class.forName("com.tacz.guns.api.entity.ShootResult");
         @SuppressWarnings({"unchecked", "rawtypes"})
         Class<? extends Enum> shootEnum = (Class<? extends Enum>)shootResult;
         shootSuccess = Enum.valueOf(shootEnum, "SUCCESS");
         shootNoAmmo = Enum.valueOf(shootEnum, "NO_AMMO");
         try {
            shootNotDraw = Enum.valueOf(shootEnum, "NOT_DRAW");
         } catch (IllegalArgumentException ignored) {
            shootNotDraw = null;
         }
         try {
            shootCooldown = Enum.valueOf(shootEnum, "COOL_DOWN");
         } catch (IllegalArgumentException ignored) {
            shootCooldown = null;
         }

         usable = true;
         LOGGER.info("[Millenaire] TacZ detected — gun combat enabled for villagers");
      } catch (ReflectiveOperationException | ClassCastException e) {
         usable = false;
         LOGGER.warn("[Millenaire] TacZ present but API unresolved: {}", e.toString());
      }
   }
}
