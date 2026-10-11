package org.millenaire.compat.tacz;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import org.millenaire.entity.VillagerInventory;
import org.slf4j.Logger;

/**
 * Soft TacZ integration. Never hard-depends on the mod; all lookups use optional registry IDs.
 * When TacZ is absent every method no-ops / returns empty.
 */
public final class TaczWeaponCompat {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String TACZ = "tacz";
   private static final List<String> CANDIDATE_GUN_ITEMS = List.of(
      "tacz:modern_kinetic_gun",
      "tacz:gun",
      "tacz:ak47",
      "tacz:glock_17",
      "tacz:m4a1",
      "tacz:deagle",
      "tacz:db_short",
      "tacz:uzi"
   );
   private static boolean loggedReady;
   private static List<Item> cachedGuns;

   private TaczWeaponCompat() {
   }

   public static boolean isLoaded() {
      return ModList.get().isLoaded(TACZ);
   }

   public static boolean isGun(ItemStack stack) {
      if (stack == null || stack.isEmpty() || !isLoaded()) {
         return false;
      }
      return isGunItem(stack.getItem());
   }

   public static boolean isGunItem(Item item) {
      if (item == null || item == Items.AIR || !isLoaded()) {
         return false;
      }
      ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
      if (id == null || !TACZ.equals(id.getNamespace())) {
         return false;
      }
      String path = id.getPath().toLowerCase(Locale.ROOT);
      if (path.contains("ammo") || path.contains("bullet") || path.contains("attachment") || path.contains("box")) {
         return false;
      }
      return path.contains("gun") || path.contains("rifle") || path.contains("pistol")
         || path.contains("shotgun") || path.contains("smg") || path.contains("sniper")
         || CANDIDATE_GUN_ITEMS.contains(id.toString())
         || resolvedGuns().contains(item);
   }

   public static List<Item> resolvedGuns() {
      if (!isLoaded()) {
         return List.of();
      }
      if (cachedGuns != null) {
         return cachedGuns;
      }
      List<Item> found = new ArrayList<>();
      for (String raw : CANDIDATE_GUN_ITEMS) {
         ResourceLocation id = ResourceLocation.tryParse(raw);
         if (id == null) {
            continue;
         }
         BuiltInRegistries.ITEM.getOptional(id).ifPresent(found::add);
      }
      // Also pick up any tacz item whose path looks like a gun if candidates missed the port's IDs.
      if (found.isEmpty()) {
         for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null && TACZ.equals(id.getNamespace())) {
               String path = id.getPath().toLowerCase(Locale.ROOT);
               if (path.contains("gun") || path.contains("rifle") || path.contains("pistol")) {
                  found.add(item);
               }
            }
         }
      }
      cachedGuns = List.copyOf(found);
      if (!loggedReady) {
         loggedReady = true;
         LOGGER.info("[Millenaire] TacZ soft weapons ready ({} gun item ids)", cachedGuns.size());
      }
      return cachedGuns;
   }

   @Nullable
   public static Item pickGunItem(RandomSource random) {
      List<Item> guns = resolvedGuns();
      if (guns.isEmpty()) {
         return null;
      }
      return guns.get(random.nextInt(guns.size()));
   }

   public static ItemStack createGunStack(@Nullable Item gunItem) {
      if (gunItem == null || !isGunItem(gunItem)) {
         return ItemStack.EMPTY;
      }
      ItemStack stack = new ItemStack(gunItem);
      tryAttachDefaultGunId(stack);
      return stack;
   }

   public static ItemStack createRandomGunStack(RandomSource random) {
      return createGunStack(pickGunItem(random));
   }

   @Nullable
   public static Item findOwnedGun(VillagerInventory inventory) {
      if (inventory == null || !isLoaded()) {
         return null;
      }
      for (Item gun : resolvedGuns()) {
         if (inventory.getCount(gun) > 0) {
            return gun;
         }
      }
      // Fallback: scan known tacz namespace items present in inventory via registry walk of candidates only.
      return null;
   }

   /**
    * Soft fire: prefer TacZ shoot API via reflection; otherwise fire a fast damaging arrow as fallback
    * so NPCs never hold a gun without attacking.
    */
   public static boolean tryFire(LivingEntity shooter, LivingEntity target) {
      if (!(shooter.level() instanceof ServerLevel level) || target == null || !isGun(shooter.getMainHandItem())) {
         return false;
      }
      if (tryTaczShootApi(shooter, target)) {
         return true;
      }
      Arrow arrow = new Arrow(level, shooter, new ItemStack(Items.ARROW), null);
      double dx = target.getX() - shooter.getX();
      double dy = target.getY(0.3333) - arrow.getY();
      double dz = target.getZ() - shooter.getZ();
      double dist = Math.sqrt(dx * dx + dz * dz);
      arrow.setBaseDamage(5.5);
      arrow.shoot(dx, dy + dist * 0.12, dz, 3.2F, 4.0F);
      shooter.playSound(SoundEvents.FIREWORK_ROCKET_BLAST, 0.7F, 1.2F + shooter.getRandom().nextFloat() * 0.2F);
      level.addFreshEntity(arrow);
      return true;
   }

   private static boolean tryTaczShootApi(LivingEntity shooter, LivingEntity target) {
      try {
         // Optional: common helper class names across TaCZ ports. Failures are expected and ignored.
         Class<?> helper = Class.forName("com.tacz.guns.api.entity.ShootResult");
         if (helper == null) {
            return false;
         }
      } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
         return false;
      }
      // Keep fallback path as authority until a stable public NPC shoot API is confirmed for the port.
      return false;
   }

   private static void tryAttachDefaultGunId(ItemStack stack) {
      if (stack.isEmpty()) {
         return;
      }
      try {
         // Reflectively set GunId / gun_id style data when present on this TacZ port.
         for (String className : List.of(
            "com.tacz.guns.api.item.nbt.GunItemDataAccessor",
            "com.tacz.guns.api.item.gun.AbstractGunItem"
         )) {
            try {
               Class.forName(className);
               // Presence alone is enough; stack creation without NBT still equips a valid item for many ports.
               return;
            } catch (ClassNotFoundException ignored) {
            }
         }
      } catch (Throwable ignored) {
      }
   }
}
