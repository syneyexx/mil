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
import net.minecraft.resources.ResourceLocation;
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
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.compat.TacZCompat;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;

/**
 * Shared wardrobe for player_custom villages: gear in armory / barracks chests is worn/wielded
 * by villagers. Guards prefer their home barracks chests; everyone can fall back to the armory.
 * Refresh is cached every {@link #REFRESH_INTERVAL_TICKS} to avoid per-tick chest scans.
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
      Map<EquipmentSlot, List<Item>> preferred = resolvePreferredPools(pools, villager);
      if (slot == EquipmentSlot.MAINHAND) {
         ResourceLocation typeId = villager.getVillagerTypeId();
         VillagerType vt = typeId != null ? ModCultures.getVillagerType(typeId) : null;
         ItemStack fromHome = pickMainhand(preferred, villager.getUUID(), vt != null && vt.isArcher());
         if (!fromHome.isEmpty()) {
            return fromHome;
         }
         return pickMainhand(pools.villageWide, villager.getUUID(), vt != null && vt.isArcher());
      }
      ItemStack fromHome = pickFromPool(preferred, villager.getUUID(), slot);
      if (!fromHome.isEmpty()) {
         return fromHome;
      }
      return pickFromPool(pools.villageWide, villager.getUUID(), slot);
   }

   /**
    * Combat weapon from the wardrobe, or {@code null} when custom outfit is not active.
    * When {@code preferRanged}, prefers bow/crossbow/TacZ guns; otherwise prefers melee, then any mainhand.
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
      Map<EquipmentSlot, List<Item>> preferred = resolvePreferredPools(pools, villager);
      ItemStack fromHome = pickMainhand(preferred, villager.getUUID(), preferRanged);
      if (!fromHome.isEmpty()) {
         return fromHome;
      }
      return pickMainhand(pools.villageWide, villager.getUUID(), preferRanged);
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

   private static Map<EquipmentSlot, List<Item>> resolvePreferredPools(CachedPools pools, MillVillager villager) {
      BuildingId homeId = villager.getHomeBuilding();
      if (homeId != null) {
         Map<EquipmentSlot, List<Item>> home = pools.byBuilding.get(homeId.uuid());
         if (home != null) {
            return home;
         }
      }
      return pools.villageWide;
   }

   private static ItemStack pickFromPool(Map<EquipmentSlot, List<Item>> pools, UUID villagerId, EquipmentSlot slot) {
      List<Item> options = pools.get(slot);
      if (options == null || options.isEmpty()) {
         return ItemStack.EMPTY;
      }
      int index = Math.floorMod(villagerId.hashCode() ^ slot.ordinal() * 31, options.size());
      return new ItemStack(options.get(index));
   }

   private static ItemStack pickMainhand(Map<EquipmentSlot, List<Item>> pools, UUID villagerId, boolean preferRanged) {
      List<Item> mainhand = pools.get(EquipmentSlot.MAINHAND);
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

      Map<UUID, Map<EquipmentSlot, List<Item>>> byBuilding = new ConcurrentHashMap<>();
      Map<EquipmentSlot, List<Item>> villageWide = emptySlotMap();

      for (BuildingInstance source : collectGearSources(village)) {
         Map<EquipmentSlot, List<Item>> buildingPools = emptySlotMap();
         for (BlockPos pos : source.getChestPositions()) {
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
               addUnique(buildingPools.get(slot), stack.getItem());
               addUnique(villageWide.get(slot), stack.getItem());
            }
         }
         byBuilding.put(source.getId().uuid(), buildingPools);
      }

      CachedPools fresh = new CachedPools(now, villageWide, byBuilding);
      CACHE.put(key, fresh);
      return fresh;
   }

   private static void addUnique(List<Item> list, Item item) {
      if (list != null && !list.contains(item)) {
         list.add(item);
      }
   }

   private static Map<EquipmentSlot, List<Item>> emptySlotMap() {
      Map<EquipmentSlot, List<Item>> bySlot = new EnumMap<>(EquipmentSlot.class);
      bySlot.put(EquipmentSlot.HEAD, new ArrayList<>());
      bySlot.put(EquipmentSlot.CHEST, new ArrayList<>());
      bySlot.put(EquipmentSlot.LEGS, new ArrayList<>());
      bySlot.put(EquipmentSlot.FEET, new ArrayList<>());
      bySlot.put(EquipmentSlot.MAINHAND, new ArrayList<>());
      bySlot.put(EquipmentSlot.OFFHAND, new ArrayList<>());
      return bySlot;
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
         || item instanceof TridentItem
         || TacZCompat.isGunItem(item);
   }

   private static boolean isRangedWeapon(Item item) {
      return item instanceof BowItem || item instanceof CrossbowItem || TacZCompat.isGunItem(item);
   }

   public static void clearCache() {
      CACHE.clear();
   }

   private record CachedPools(
      long refreshedAt, Map<EquipmentSlot, List<Item>> villageWide, Map<UUID, Map<EquipmentSlot, List<Item>>> byBuilding
   ) {
   }
}
