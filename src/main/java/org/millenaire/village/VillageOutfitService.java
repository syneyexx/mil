package org.millenaire.village;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.compat.TacZCompat;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;

/**
 * Player_custom village gear: armor/weapons in armory / barracks chests are physically claimed
 * onto villagers (full {@link ItemStack}s, components preserved for TacZ guns).
 * Guards prefer their home barracks chests; everyone can fall back to the armory.
 */
public final class VillageOutfitService {
   private static final List<EquipmentSlot> CLAIM_SLOTS = List.of(
      EquipmentSlot.HEAD,
      EquipmentSlot.CHEST,
      EquipmentSlot.LEGS,
      EquipmentSlot.FEET,
      EquipmentSlot.MAINHAND,
      EquipmentSlot.OFFHAND
   );

   private VillageOutfitService() {
   }

   /**
    * Returns claimed/synced equipment for a custom-outfit village, or {@code null} when custom
    * outfit is inactive. Empty stack means the slot has no claimed gear yet.
    */
   @Nullable
   public static ItemStack getOutfitPiece(MillVillager villager, EquipmentSlot slot) {
      if (!isCustomOutfitActive(villager)) {
         return null;
      }
      return villager.getSyncedEquipment(slot).copy();
   }

   /**
    * Combat weapon from claimed MAINHAND (or a fresh chest claim), or {@code null} when custom
    * outfit is not active.
    */
   @Nullable
   public static ItemStack getOutfitCombatWeapon(MillVillager villager, boolean preferRanged) {
      if (!isCustomOutfitActive(villager)) {
         return null;
      }

      ItemStack equipped = villager.getSyncedEquipment(EquipmentSlot.MAINHAND);
      if (!equipped.isEmpty() && isMainHandWeapon(equipped)) {
         return equipped.copy();
      }

      if (!(villager.level() instanceof ServerLevel level)) {
         return ItemStack.EMPTY;
      }
      Village village = resolveVillage(villager, level);
      if (village == null) {
         return ItemStack.EMPTY;
      }

      ItemStack claimed = claimMainhand(level, village, villager, preferRanged);
      return claimed.isEmpty() ? ItemStack.EMPTY : claimed.copy();
   }

   /**
    * Fills empty equipment slots by physically taking matching stacks from gear chests.
    * Existing claimed gear is left alone. Also tops up the TacZ ammo stash when needed.
    */
   public static void syncArmorSlots(MillVillager villager) {
      if (!(villager.level() instanceof ServerLevel level) || !isCustomOutfitActive(villager)) {
         return;
      }
      Village village = resolveVillage(villager, level);
      if (village == null) {
         return;
      }

      ResourceLocation typeId = villager.getVillagerTypeId();
      VillagerType vt = typeId != null ? ModCultures.getVillagerType(typeId) : null;
      boolean preferRanged = vt != null && vt.isArcher();

      for (EquipmentSlot slot : CLAIM_SLOTS) {
         ItemStack current = villager.getSyncedEquipment(slot);
         if (!current.isEmpty()) {
            continue;
         }
         ItemStack claimed = slot == EquipmentSlot.MAINHAND
            ? claimMainhand(level, village, villager, preferRanged)
            : claimSlot(level, village, villager, slot);
         if (!claimed.isEmpty()) {
            villager.setItemSlot(slot, claimed);
         }
      }

      ItemStack main = villager.getSyncedEquipment(EquipmentSlot.MAINHAND);
      if (TacZCompat.isGunStack(main) && villager.getGearAmmoStashCount() == 0) {
         claimAmmoIntoStash(level, village, villager);
      }
   }

   /** Whether goal held-item cycling must not overwrite claimed custom outfit gear. */
   public static boolean protectsEquipmentSlots(MillVillager villager) {
      return isCustomOutfitActive(villager);
   }

   public static void clearCache() {
      // No longer caches chest scans; kept for call-site compatibility.
   }

   private static boolean isCustomOutfitActive(MillVillager villager) {
      if (!(villager.level() instanceof ServerLevel level)) {
         return false;
      }
      Village village = resolveVillage(villager, level);
      return village != null && village.isCustomOutfitEnabled();
   }

   @Nullable
   private static Village resolveVillage(MillVillager villager, ServerLevel level) {
      VillageId villageId = villager.getVillageId();
      if (villageId == null) {
         return null;
      }
      return Village.resolve(level, villageId);
   }

   private static ItemStack claimSlot(ServerLevel level, Village village, MillVillager villager, EquipmentSlot slot) {
      for (BuildingInstance source : orderedGearSources(village, villager)) {
         ItemStack taken = extractMatching(level, source, stack -> slot.equals(classifySlot(stack)));
         if (!taken.isEmpty()) {
            return taken;
         }
      }
      return ItemStack.EMPTY;
   }

   private static ItemStack claimMainhand(ServerLevel level, Village village, MillVillager villager, boolean preferRanged) {
      for (BuildingInstance source : orderedGearSources(village, villager)) {
         ItemStack ranged = preferRanged
            ? extractMatching(level, source, stack -> isMainHandWeapon(stack) && isRangedWeapon(stack))
            : ItemStack.EMPTY;
         if (!ranged.isEmpty()) {
            maybeClaimAmmoWithGun(level, village, villager, ranged);
            return ranged;
         }
         ItemStack any = extractMatching(level, source, VillageOutfitService::isMainHandWeapon);
         if (!any.isEmpty()) {
            maybeClaimAmmoWithGun(level, village, villager, any);
            return any;
         }
      }
      return ItemStack.EMPTY;
   }

   private static void maybeClaimAmmoWithGun(ServerLevel level, Village village, MillVillager villager, ItemStack weapon) {
      if (TacZCompat.isGunStack(weapon) && villager.getGearAmmoStashCount() == 0) {
         claimAmmoIntoStash(level, village, villager);
      }
   }

   private static void claimAmmoIntoStash(ServerLevel level, Village village, MillVillager villager) {
      for (BuildingInstance source : orderedGearSources(village, villager)) {
         while (villager.getGearAmmoStashCount() < MillVillager.GEAR_AMMO_STASH_SIZE) {
            ItemStack ammo = extractMatching(level, source, TacZCompat::isAmmoStack, true);
            if (ammo.isEmpty()) {
               break;
            }
            if (!villager.offerGearAmmo(ammo)) {
               break;
            }
         }
         if (villager.getGearAmmoStashCount() > 0) {
            return;
         }
      }
   }

   private static List<BuildingInstance> orderedGearSources(Village village, MillVillager villager) {
      List<BuildingInstance> sources = collectGearSources(village);
      BuildingId homeId = villager.getHomeBuilding();
      if (homeId == null) {
         return sources;
      }
      List<BuildingInstance> ordered = new ArrayList<>(sources.size());
      for (BuildingInstance building : sources) {
         if (homeId.equals(building.getId())) {
            ordered.add(building);
         }
      }
      for (BuildingInstance building : sources) {
         if (!homeId.equals(building.getId())) {
            ordered.add(building);
         }
      }
      return ordered;
   }

   private static List<BuildingInstance> collectGearSources(Village village) {
      Set<BuildingInstance> found = new LinkedHashSet<>();
      found.addAll(village.getBuildingsWithTag("armoury"));
      found.addAll(village.getBuildingsWithTag("patrol"));
      for (BuildingInstance building : village.getBuildings()) {
         String role = building.getPlayerBuildingRole();
         if (PlayerBuildingRole.ARMORY.id().equals(role)
            || PlayerBuildingRole.GUARD_BARRACKS.id().equals(role)
            || PlayerBuildingRole.GUARD_STATION.id().equals(role)) {
            found.add(building);
         }
      }
      return new ArrayList<>(found);
   }

   private static ItemStack extractMatching(ServerLevel level, BuildingInstance building, Predicate<ItemStack> match) {
      return extractMatching(level, building, match, false);
   }

   private static ItemStack extractMatching(
      ServerLevel level, BuildingInstance building, Predicate<ItemStack> match, boolean wholeStack
   ) {
      for (BlockPos pos : building.getChestPositions()) {
         if (!level.isLoaded(pos)) {
            continue;
         }
         BlockEntity be = level.getBlockEntity(pos);
         if (!(be instanceof Container container)) {
            continue;
         }
         for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty() || !match.test(stack)) {
               continue;
            }
            ItemStack taken;
            if (wholeStack) {
               taken = stack.copy();
               container.setItem(i, ItemStack.EMPTY);
            } else {
               taken = stack.copyWithCount(1);
               stack.shrink(1);
               if (stack.isEmpty()) {
                  container.setItem(i, ItemStack.EMPTY);
               } else {
                  container.setItem(i, stack);
               }
            }
            container.setChanged();
            return taken;
         }
      }
      return ItemStack.EMPTY;
   }

   @Nullable
   private static EquipmentSlot classifySlot(ItemStack stack) {
      if (stack.isEmpty()) {
         return null;
      }
      if (stack.getItem() instanceof ArmorItem armor) {
         EquipmentSlot slot = armor.getEquipmentSlot();
         return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? slot : null;
      }
      if (stack.getItem() instanceof ShieldItem) {
         return EquipmentSlot.OFFHAND;
      }
      if (isMainHandWeapon(stack)) {
         return EquipmentSlot.MAINHAND;
      }
      return null;
   }

   private static boolean isMainHandWeapon(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      }
      return stack.getItem() instanceof SwordItem
         || stack.getItem() instanceof AxeItem
         || stack.getItem() instanceof BowItem
         || stack.getItem() instanceof CrossbowItem
         || stack.getItem() instanceof TridentItem
         || TacZCompat.isGunStack(stack);
   }

   private static boolean isRangedWeapon(ItemStack stack) {
      return stack.getItem() instanceof BowItem
         || stack.getItem() instanceof CrossbowItem
         || TacZCompat.isGunStack(stack);
   }
}
