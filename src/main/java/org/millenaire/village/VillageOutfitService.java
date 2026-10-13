package org.millenaire.village;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.millenaire.building.BuildingInstance;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;

/**
 * Shared wardrobe for player_custom villages: gear in designated armory chests is worn/wielded
 * by all villagers with stable per-NPC variation when multiple pieces exist.
 */
public final class VillageOutfitService {
   private static final Map<UUID, CachedPools> CACHE = new ConcurrentHashMap<>();
   private static final long REFRESH_INTERVAL_TICKS = 40L;
   private static final List<EquipmentSlot> SYNC_SLOTS = List.of(
      EquipmentSlot.HEAD,
      EquipmentSlot.CHEST,
      EquipmentSlot.LEGS,
      EquipmentSlot.FEET,
      EquipmentSlot.MAINHAND,
      EquipmentSlot.OFFHAND
   );

   private VillageOutfitService() {
   }

   @Nullable
   public static ItemStack getOutfitPiece(MillVillager villager, EquipmentSlot slot) {
      if (!(villager.level() instanceof ServerLevel level)) {
         return null;
      }
      VillageId villageId = villager.getVillageId();
      if (villageId == null) {
         return null;
      }
      Village village = Village.resolve(level, villageId);
      if (village == null || !village.isCustomOutfitEnabled()) {
         return null;
      }

      CachedPools pools = getOrRefresh(level, village);
      if (slot == EquipmentSlot.MAINHAND) {
         VillagerType vt = ModCultures.getVillagerType(villager.getVillagerTypeId());
         return pickMainhand(pools, villager.getUUID(), vt != null && vt.isArcher());
      }
      return pickFromPool(pools, villager.getUUID(), slot);
   }

   /**
    * Combat weapon from the armory wardrobe, or {@code null} when custom outfit is not active.
    * When {@code preferRanged}, prefers bow/crossbow; otherwise prefers melee, then any mainhand.
    */
   @Nullable
   public static ItemStack getOutfitCombatWeapon(MillVillager villager, boolean preferRanged) {
      if (!(villager.level() instanceof ServerLevel level)) {
         return null;
      }
      VillageId villageId = villager.getVillageId();
      if (villageId == null) {
         return null;
      }
      Village village = Village.resolve(level, villageId);
      if (village == null || !village.isCustomOutfitEnabled()) {
         return null;
      }

      CachedPools pools = getOrRefresh(level, village);
      return pickMainhand(pools, villager.getUUID(), preferRanged);
   }

   public static void syncArmorSlots(MillVillager villager) {
      if (!(villager.level() instanceof ServerLevel)) {
         return;
      }
      for (EquipmentSlot slot : SYNC_SLOTS) {
         ItemStack desired = getOutfitPiece(villager, slot);
         if (desired == null) {
            return;
         }
         ItemStack current = villager.getSyncedEquipment(slot);
         if (!ItemStack.isSameItemSameComponents(current, desired)) {
            villager.setItemSlot(slot, desired.isEmpty() ? ItemStack.EMPTY : desired.copy());
         }
      }
   }

   private static ItemStack pickFromPool(CachedPools pools, UUID villagerId, EquipmentSlot slot) {
      List<Item> options = pools.bySlot.get(slot);
      if (options == null || options.isEmpty()) {
         return ItemStack.EMPTY;
      }
      int index = Math.floorMod(villagerId.hashCode() ^ slot.ordinal() * 31, options.size());
      return new ItemStack(options.get(index));
   }

   private static ItemStack pickMainhand(CachedPools pools, UUID villagerId, boolean preferRanged) {
      List<Item> mainhand = pools.bySlot.get(EquipmentSlot.MAINHAND);
      if (mainhand == null || mainhand.isEmpty()) {
         return ItemStack.EMPTY;
      }

      List<Item> preferred = new ArrayList<>();
      for (Item item : mainhand) {
         if (preferRanged == isRangedWeapon(item)) {
            preferred.add(item);
         }
      }
      List<Item> options = preferred.isEmpty() ? mainhand : preferred;
      int index = Math.floorMod(villagerId.hashCode() ^ EquipmentSlot.MAINHAND.ordinal() * 31, options.size());
      return new ItemStack(options.get(index));
   }

   private static CachedPools getOrRefresh(ServerLevel level, Village village) {
      UUID key = village.getId().uuid();
      long now = level.getGameTime();
      CachedPools cached = CACHE.get(key);
      if (cached != null && now - cached.refreshedAt < REFRESH_INTERVAL_TICKS) {
         return cached;
      }

      Map<EquipmentSlot, List<Item>> bySlot = new EnumMap<>(EquipmentSlot.class);
      bySlot.put(EquipmentSlot.HEAD, new ArrayList<>());
      bySlot.put(EquipmentSlot.CHEST, new ArrayList<>());
      bySlot.put(EquipmentSlot.LEGS, new ArrayList<>());
      bySlot.put(EquipmentSlot.FEET, new ArrayList<>());
      bySlot.put(EquipmentSlot.MAINHAND, new ArrayList<>());
      bySlot.put(EquipmentSlot.OFFHAND, new ArrayList<>());

      for (BuildingInstance armory : collectArmories(village)) {
         for (BlockPos pos : armory.getChestPositions()) {
            if (!level.isLoaded(pos)) {
               continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof Container container)) {
               continue;
            }
            for (int i = 0; i < container.getContainerSize(); i++) {
               ItemStack stack = container.getItem(i);
               if (stack.isEmpty()) {
                  continue;
               }
               EquipmentSlot slot = classifySlot(stack.getItem());
               if (slot == null) {
                  continue;
               }
               List<Item> list = bySlot.get(slot);
               if (list != null && !list.contains(stack.getItem())) {
                  list.add(stack.getItem());
               }
            }
         }
      }

      CachedPools fresh = new CachedPools(now, bySlot);
      CACHE.put(key, fresh);
      return fresh;
   }

   private static List<BuildingInstance> collectArmories(Village village) {
      Set<BuildingInstance> found = new LinkedHashSet<>();
      found.addAll(village.getBuildingsWithTag("armoury"));
      for (BuildingInstance building : village.getBuildings()) {
         if (PlayerBuildingRole.ARMORY.id().equals(building.getPlayerBuildingRole())) {
            found.add(building);
         }
      }
      return new ArrayList<>(found);
   }

   @Nullable
   private static EquipmentSlot classifySlot(Item item) {
      if (item instanceof ArmorItem armor) {
         EquipmentSlot slot = armor.getEquipmentSlot();
         return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? slot : null;
      }
      if (item instanceof ShieldItem) {
         return EquipmentSlot.OFFHAND;
      }
      if (isMainHandWeapon(item)) {
         return EquipmentSlot.MAINHAND;
      }
      return null;
   }

   private static boolean isMainHandWeapon(Item item) {
      return item instanceof SwordItem
         || item instanceof AxeItem
         || item instanceof BowItem
         || item instanceof CrossbowItem
         || item instanceof TridentItem;
   }

   private static boolean isRangedWeapon(Item item) {
      return item instanceof BowItem || item instanceof CrossbowItem;
   }

   public static void clearCache() {
      CACHE.clear();
   }

   private record CachedPools(long refreshedAt, Map<EquipmentSlot, List<Item>> bySlot) {
   }
}
