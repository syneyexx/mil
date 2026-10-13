package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import org.millenaire.Millenaire;
import org.millenaire.building.BedManager;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.SpecialPoint;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerSpawnFactory;
import org.millenaire.goal.GoalRegistry;
import org.slf4j.Logger;

public final class PlayerBuildingDesignationService {
   private static final Logger LOGGER = LogUtils.getLogger();
   /** Role keys that may fall back to another culture when missing. */
   private static final List<ResourceLocation> FALLBACK_CULTURES = List.of(
      ResourceLocation.fromNamespaceAndPath("millenaire", "norman"),
      ResourceLocation.fromNamespaceAndPath("millenaire", "byzantines"),
      ResourceLocation.fromNamespaceAndPath("millenaire", "japanese"),
      ResourceLocation.fromNamespaceAndPath("millenaire", "inuits"),
      ResourceLocation.fromNamespaceAndPath("millenaire", "seljuk")
   );

   private PlayerBuildingDesignationService() {
   }

   public static void designate(ServerPlayer player, Village village, PlayerBuildingRole role) {
      if (!(player.level() instanceof ServerLevel level)) {
         return;
      }
      if (!village.isControlledBy(player.getUUID())) {
         player.sendSystemMessage(Component.translatable("message.millenaire.designate.not_owner"));
         return;
      }

      BlockPos feet = player.blockPosition();
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      int radius = vt != null ? vt.radius() : 80;
      if (village.getCenter().distSqr(feet) > (double) radius * radius) {
         player.sendSystemMessage(Component.translatable("message.millenaire.designate.too_far"));
         return;
      }

      PlayerBuildingScanner.ScanResult scan = PlayerBuildingScanner.scan(level, feet);
      Component requirementError = validateRequirements(role, scan);
      if (requirementError != null) {
         player.sendSystemMessage(requirementError);
         return;
      }

      BuildingInstance building = village.getOperationalBuildingAt(feet);
      boolean created = false;
      if (building != null && isProtectedBuilding(building, village)) {
         player.sendSystemMessage(Component.translatable("message.millenaire.designate.protected"));
         return;
      }
      if (building == null) {
         building = createDesignatedBuilding(village, feet, role);
         village.addBuilding(building);
         created = true;
      } else {
         // Use synthetic plan so shopId/tags match the chosen role
         building.setPlanId(role.planId());
      }

      applyRole(level, village, building, role, scan, feet);
      int spawned = ensureWorkers(level, village, building, role, feet);
      village.invalidateBuildingTagCache();
      village.markCityStatsDirty();
      village.markDirty();
      VillageSavedData.get(level).setDirty();

      player.sendSystemMessage(
         Component.translatable(
            "message.millenaire.designate.success",
            Component.translatable("gui.millenaire.designate.role." + role.id()),
            spawned,
            created ? Component.translatable("message.millenaire.designate.created") : Component.translatable("message.millenaire.designate.updated")
         )
      );
      if (role.reservesBedsForPlayer()) {
         player.sendSystemMessage(Component.translatable("message.millenaire.designate.kings_house_hint"));
      } else if (scan.bedCount() <= 0) {
         player.sendSystemMessage(Component.translatable("message.millenaire.designate.no_bed"));
      }
      LOGGER.info(
         "[Designate] {} set {} at {} in {} (spawned={})",
         player.getGameProfile().getName(),
         role.id(),
         feet,
         village.getVillageName(),
         spawned
      );
   }


   private static boolean isProtectedBuilding(BuildingInstance building, Village village) {
      if (village.getTownhall() != null && village.getTownhall().getId().equals(building.getId())) {
         return true;
      }
      BuildingPlan plan = ModCultures.getBuildingPlan(building.getPlanId());
      if (plan != null && ("townhall".equals(plan.shopId()) || plan.hasTag("townhall"))) {
         return true;
      }
      return false;
   }

   private static BuildingInstance createDesignatedBuilding(Village village, BlockPos center, PlayerBuildingRole role) {
      BlockPos origin = new BlockPos(center.getX() - PlayerBuildingRole.HALF, center.getY() - PlayerBuildingRole.SCAN_Y_DOWN, center.getZ() - PlayerBuildingRole.HALF);
      BuildingInstance building = new BuildingInstance(
         BuildingId.random(),
         role.planId(),
         origin,
         Rotation.NONE,
         BuildingInstance.Status.COMPLETE
      );
      building.setPlayerChosen(true);
      return building;
   }

   private static void applyRole(
      ServerLevel level,
      Village village,
      BuildingInstance building,
      PlayerBuildingRole role,
      PlayerBuildingScanner.ScanResult scan,
      BlockPos center
   ) {
      // Drop previous designation tags
      building.clearRuntimeTags();
      building.setPlayerBuildingRole(role.id());
      building.setPurpose(role.purpose());
      building.addRuntimeTags(role.tags());

      List<SpecialPoint> points = new ArrayList<>(scan.points());
      // Ensure sleeping/defending relative to building for navigation
      if (points.stream().noneMatch(p -> p.isType(SpecialPoint.SLEEPING_POS))) {
         points.add(new SpecialPoint(SpecialPoint.SLEEPING_POS, null, null, center));
      }
      building.setResolvedPoints(points);
      building.initInventory();
      building.linkChestsToBuilding(level);
      applyBeds(level, village, building, role, scan);
   }

   private static void applyBeds(
      ServerLevel level,
      Village village,
      BuildingInstance building,
      PlayerBuildingRole role,
      PlayerBuildingScanner.ScanResult scan
   ) {
      BedManager beds = building.getBedManager();
      beds.clearAll();
      building.setBedManagerInitialized(true);

      if (role.reservesBedsForPlayer()) {
         for (VillagerRecord record : village.getVillagerRecords().values()) {
            if (!record.isKilled() && building.getId().equals(record.getHomeBuilding())) {
               record.setHomeBuilding(null);
               MillVillager entity = findEntity(level, record.getUuid());
               if (entity != null) {
                  entity.setHomeBuilding(null);
               }
            }
         }
         return;
      }

      if (role.registersNpcBeds()) {
         for (BlockPos bed : scan.bedPositions()) {
            beds.add(bed);
         }
      }
   }

   private static int ensureWorkers(ServerLevel level, Village village, BuildingInstance building, PlayerBuildingRole role, BlockPos spawnPos) {
      Set<UUID> livingAtHome = new HashSet<>();
      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (!record.isKilled() && building.getId().equals(record.getHomeBuilding())) {
            livingAtHome.add(record.getUuid());
         }
      }

      // Count existing suitable workers at this home
      int suitable = 0;
      for (UUID uuid : livingAtHome) {
         VillagerRecord record = village.getVillagerRecord(uuid);
         if (record == null) {
            continue;
         }
         if (roleMatches(record.getVillagerTypeId(), role)) {
            suitable++;
            MillVillager entity = findEntity(level, uuid);
            if (entity != null) {
               reloadGoals(entity);
            }
         }
      }

      int needed = Math.max(0, role.workerCount() - suitable);
      int spawned = 0;
      List<ResourceLocation> types = resolveWorkerTypes(village.getCultureId(), role);
      if (types.isEmpty()) {
         return 0;
      }

      for (int i = 0; i < needed; i++) {
         ResourceLocation typeId = types.get(i % types.size());
         MillVillager villager = VillagerSpawnFactory.spawnInVillage(level, village, typeId, spawnPos, building.getId());
         if (villager != null) {
            spawned++;
         }
      }
      return spawned;
   }

   private static boolean roleMatches(ResourceLocation typeId, PlayerBuildingRole role) {
      if (typeId == null) {
         return false;
      }
      String path = typeId.getPath();
      String shortName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
      for (String male : role.maleRoles()) {
         if (shortName.equals(male) || shortName.endsWith("_" + male) || shortName.startsWith(male)) {
            return true;
         }
      }
      for (String female : role.femaleRoles()) {
         if (shortName.equals(female) || shortName.endsWith("_" + female) || shortName.startsWith(female)) {
            return true;
         }
      }
      // Bakery / shop: wife with seller / craft_bread
      if (role == PlayerBuildingRole.BAKERY || role == PlayerBuildingRole.SHOP) {
         VillagerType vt = ModCultures.getVillagerType(typeId);
         return vt != null && (vt.hasTag("seller") || shortName.contains("wife"));
      }
      if (role == PlayerBuildingRole.ARMORY) {
         return shortName.contains("smith");
      }
      if (role == PlayerBuildingRole.FISHERMAN) {
         return shortName.contains("fish");
      }
      return false;
   }

   private static List<ResourceLocation> resolveWorkerTypes(ResourceLocation cultureId, PlayerBuildingRole role) {
      List<ResourceLocation> result = new ArrayList<>();
      List<String> keys = new ArrayList<>();
      keys.addAll(role.maleRoles());
      keys.addAll(role.femaleRoles());
      for (String key : keys) {
         ResourceLocation resolved = resolveRoleType(cultureId, key);
         if (resolved != null) {
            result.add(resolved);
         }
      }
      return result;
   }

   @Nullable
   private static ResourceLocation resolveRoleType(ResourceLocation cultureId, String roleKey) {
      ResourceLocation primary = ResourceLocation.fromNamespaceAndPath(cultureId.getNamespace(), cultureId.getPath() + "/" + roleKey);
      if (ModCultures.getVillagerType(primary) != null) {
         return primary;
      }
      // Common path variants
      for (String folderHint : List.of(roleKey, roleKey + "_byzantine", "turk_" + roleKey, "inuit_" + roleKey, "japanese_" + roleKey)) {
         ResourceLocation local = ResourceLocation.fromNamespaceAndPath(cultureId.getNamespace(), cultureId.getPath() + "/" + folderHint);
         if (ModCultures.getVillagerType(local) != null) {
            return local;
         }
      }
      for (ResourceLocation fallbackCulture : FALLBACK_CULTURES) {
         if (fallbackCulture.equals(cultureId)) {
            continue;
         }
         ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath(
            fallbackCulture.getNamespace(),
            fallbackCulture.getPath() + "/" + roleKey
         );
         if (ModCultures.getVillagerType(candidate) != null) {
            return candidate;
         }
         for (VillagerType vt : ModCultures.getAllVillagerTypes().values()) {
            if (!vt.culture().equals(fallbackCulture)) {
               continue;
            }
            String path = vt.id().getPath();
            String shortName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            if (shortName.equals(roleKey) || shortName.endsWith("_" + roleKey) || shortName.contains(roleKey)) {
               return vt.id();
            }
         }
      }
      return null;
   }

   @Nullable
   private static MillVillager findEntity(ServerLevel level, UUID uuid) {
      return level.getEntity(uuid) instanceof MillVillager mv ? mv : null;
   }

   private static void reloadGoals(MillVillager villager) {
      VillagerType vType = ModCultures.getVillagerType(villager.getVillagerTypeId());
      GoalRegistry registry = Millenaire.getGoalRegistry();
      if (vType != null && registry != null) {
         villager.initGoals(registry, vType);
      }
   }

   @Nullable
   private static Component validateRequirements(PlayerBuildingRole role, PlayerBuildingScanner.ScanResult scan) {
      if (role.requireFarmland() && scan.farmlandCount() <= 0) {
         return Component.translatable("message.millenaire.designate.need_farmland");
      }
      if (role.requireTrees() && scan.treeCount() <= 0) {
         return Component.translatable("message.millenaire.designate.need_trees");
      }
      if (role.requireChest() && scan.chestCount() <= 0) {
         return Component.translatable("message.millenaire.designate.need_chest");
      }
      if (role.requireCrafting() && scan.craftingCount() <= 0) {
         return Component.translatable("message.millenaire.designate.need_crafting");
      }
      if (role.requireWater() && scan.waterCount() <= 0) {
         return Component.translatable("message.millenaire.designate.need_water");
      }
      if (role.requireBed() && scan.bedCount() <= 0) {
         return Component.translatable("message.millenaire.designate.need_bed");
      }
      return null;
   }

   /** Re-apply scanned points after world load for player-designated buildings. */
   public static void rehydrate(ServerLevel level, Village village, BuildingInstance building) {
      PlayerBuildingRole role = PlayerBuildingRole.fromId(building.getPlayerBuildingRole());
      if (role == null) {
         return;
      }
      BlockPos center = building.getOrigin().offset(PlayerBuildingRole.HALF, PlayerBuildingRole.SCAN_Y_DOWN, PlayerBuildingRole.HALF);
      PlayerBuildingScanner.ScanResult scan = PlayerBuildingScanner.scan(level, center);
      building.clearRuntimeTags();
      building.addRuntimeTags(role.tags());
      building.setResolvedPoints(scan.points());
      building.initInventory();
      building.linkChestsToBuilding(level);
      applyBeds(level, village, building, role, scan);
   }
}
