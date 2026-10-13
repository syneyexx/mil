package org.millenaire.crime;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.combat.CombatHelper;
import org.millenaire.combat.raid.RaidManager;
import org.millenaire.village.VillageTreasuryService;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.item.ModItems;
import org.millenaire.item.MoneyHelper;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerInventory;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.impl.InvestigateChestGoal;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

public final class CrimeService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Map<UUID, Long> pickpocketCooldown = new HashMap<>();
   private static final Map<String, Long> investigateDebounce = new HashMap<>();

   private CrimeService() {
   }

   public static boolean tryPickpocket(ServerPlayer player, MillVillager villager) {
      if (!PlayerBountySavedData.crimeEnabled() || player.isCreative() || player.isSpectator()) {
         return false;
      }

      if (!player.isShiftKeyDown() || !isBehind(player, villager)) {
         return false;
      }

      if (player.distanceTo(villager) > CrimeConstants.PICKPOCKET_RANGE) {
         return false;
      }

      if (villager.isSleeping() || villager.isVillagerSleeping()) {
         return false;
      }

      VillagerType type = ModCultures.getVillagerType(villager.getVillagerTypeId());
      if (type != null && type.isHostile()) {
         return false;
      }

      if (!(villager.level() instanceof ServerLevel level)) {
         return false;
      }

      long now = level.getGameTime();
      Long last = pickpocketCooldown.get(villager.getUUID());
      if (last != null && now - last < CrimeConstants.PICKPOCKET_COOLDOWN_TICKS) {
         player.sendSystemMessage(Component.translatable("message.millenaire.crime.pickpocket_cooldown"));
         return true;
      }

      pickpocketCooldown.put(villager.getUUID(), now);
      Village village = resolveVillage(villager);
      List<MillVillager> witnesses = findWitnesses(villager, player, villager);
      ItemStack loot = stealRandomItem(villager);
      if (loot.isEmpty()) {
         player.sendSystemMessage(Component.translatable("message.millenaire.crime.pickpocket_empty"));
         if (!witnesses.isEmpty()) {
            reportCaught(player, village, villager.getCultureId(), CrimeConstants.PICKPOCKET_BOUNTY, CrimeConstants.PICKPOCKET_REP, villager, witnesses);
            player.sendSystemMessage(Component.translatable("message.millenaire.crime.pickpocket_caught"));
         }

         return true;
      }

      if (!player.getInventory().add(loot)) {
         player.drop(loot, false);
      }

      if (witnesses.isEmpty()) {
         player.sendSystemMessage(Component.translatable("message.millenaire.crime.pickpocket_success", loot.getHoverName()));
      } else {
         reportCaught(player, village, villager.getCultureId(), CrimeConstants.PICKPOCKET_BOUNTY, CrimeConstants.PICKPOCKET_REP, villager, witnesses);
         player.sendSystemMessage(Component.translatable("message.millenaire.crime.pickpocket_caught"));
      }

      return true;
   }

   public static void onLockpickFailed(ServerPlayer player, Village village, ResourceLocation cultureId, BlockPos chestPos) {
      if (!PlayerBountySavedData.crimeEnabled() || player.isCreative() || player.isSpectator()) {
         return;
      }

      List<MillVillager> witnesses = findWitnessesNear(player.serverLevel(), chestPos, player, village != null ? village.getId() : null);
      if (!witnesses.isEmpty()) {
         reportCaught(player, village, cultureId, CrimeConstants.LOCKPICK_FAIL_BOUNTY, CrimeConstants.LOCKPICK_FAIL_REP, witnesses.get(0), witnesses);
         player.sendSystemMessage(Component.translatable("message.millenaire.crime.lockpick_noticed"));
      }
   }

   public static void onChestItemTaken(ServerPlayer player, BuildingId buildingId, BlockPos chestPos) {
      if (!PlayerBountySavedData.crimeEnabled() || player.isCreative() || player.isSpectator()) {
         return;
      }

      if (!(player.level() instanceof ServerLevel level)) {
         return;
      }

      Village village = VillageSavedData.get(level).getVillageManager().findVillageContaining(buildingId);
      if (village == null || village.isControlledBy(player.getUUID())) {
         return;
      }

      String key = player.getUUID() + "|" + buildingId.uuid();
      long now = level.getGameTime();
      Long last = investigateDebounce.get(key);
      if (last != null && now - last < CrimeConstants.INVESTIGATE_DEBOUNCE_TICKS) {
         return;
      }

      investigateDebounce.put(key, now);
      MillVillager inspector = findInspector(level, village, buildingId);
      if (inspector == null) {
         LOGGER.debug("[Crime] No inspector for building {} in {}", buildingId.uuid(), village.getVillageName());
         return;
      }

      GoalScheduler scheduler = inspector.getGoalScheduler();
      GoalContext ctx = inspector.buildGoalContext();
      if (scheduler == null || ctx == null) {
         return;
      }

      scheduler.forceTask(new InvestigateChestGoal.InvestigateChestTask(chestPos, player.getUUID()), ctx);
      player.sendSystemMessage(Component.translatable("message.millenaire.crime.owner_coming"));
      LOGGER.debug("[Crime] {} investigating chest at {} (thief={})", inspector.getVillagerDisplayName(), chestPos, player.getGameProfile().getName());
   }

   public static void onInspectorArrived(MillVillager inspector, BlockPos chestPos, UUID thiefId) {
      if (!(inspector.level() instanceof ServerLevel level)) {
         return;
      }

      ServerPlayer thief = level.getServer().getPlayerList().getPlayer(thiefId);
      if (thief == null || !thief.isAlive() || thief.isCreative() || thief.isSpectator()) {
         return;
      }

      if (thief.distanceToSqr(chestPos.getX() + 0.5, chestPos.getY() + 0.5, chestPos.getZ() + 0.5)
         > CrimeConstants.CHEST_CATCH_RANGE * CrimeConstants.CHEST_CATCH_RANGE) {
         return;
      }

      Village village = resolveVillage(inspector);
      List<MillVillager> helpers = List.of(inspector);
      reportCaught(thief, village, inspector.getCultureId(), CrimeConstants.CHEST_CAUGHT_BOUNTY, CrimeConstants.CHEST_CAUGHT_REP, inspector, helpers);
      thief.sendSystemMessage(Component.translatable("message.millenaire.crime.chest_caught"));
   }

   public static void onVillagerAssault(ServerPlayer player, MillVillager villager, @Nullable Village village) {
      if (!PlayerBountySavedData.crimeEnabled() || player.isCreative() || player.isSpectator()) {
         return;
      }
      List<MillVillager> witnesses = findWitnesses(villager, player, villager);
      if (witnesses.isEmpty()) {
         witnesses = List.of(villager);
      }
      reportCaught(
         player,
         village,
         villager.getCultureId(),
         CrimeConstants.ASSAULT_BOUNTY,
         0, // rep already applied in VillagerCombat.onHurt
         villager,
         witnesses
      );
   }

   public static void onVillagerKilled(ServerPlayer player, MillVillager villager) {
      if (!PlayerBountySavedData.crimeEnabled() || player.isCreative() || player.isSpectator()) {
         return;
      }
      Village village = resolveVillage(villager);
      reportCaught(
         player,
         village,
         villager.getCultureId(),
         CrimeConstants.KILL_BOUNTY,
         CrimeConstants.KILL_REP,
         null,
         List.of()
      );
   }

   public static void onCultureBlockBroken(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState brokenState) {
      if (!PlayerBountySavedData.crimeEnabled() || player.isCreative() || player.isSpectator()) {
         return;
      }

      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      Village village = vm.findNearestVillage(pos, 128.0);
      if (village == null) {
         return;
      }
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      int radius = vt != null ? vt.radius() : 80;
      if (village.getCenter().distSqr(pos) > (double)radius * (double)radius) {
         return;
      }
      if (village.getBuildingAt(pos) == null) {
         return;
      }
      if (village.isControlledBy(player.getUUID())) {
         return;
      }

      int damage = estimateDamageDeniers(brokenState);
      if (MoneyHelper.removeDeniers(player.getInventory(), damage)) {
         VillageTreasuryService.deposit(level, village, damage, VillageTreasuryService.REASON_CRIME_FINE);
         village.adjustReputation(level, player.getUUID(), CrimeConstants.VANDALISM_REP_PAID);
         VillageSavedData.get(level).setDirty();
         player.sendSystemMessage(Component.translatable("message.millenaire.crime.vandalism_paid", damage));
         return;
      }

      int bounty = Math.min(
         CrimeConstants.VANDALISM_BOUNTY_MAX,
         CrimeConstants.VANDALISM_BOUNTY_BASE + damage / 2
      );
      List<MillVillager> witnesses = findWitnessesNear(level, pos, player, village.getId());
      reportCaught(player, village, village.getCultureId(), bounty, CrimeConstants.VANDALISM_REP_UNPAID, null, witnesses);
      player.sendSystemMessage(Component.translatable("message.millenaire.crime.vandalism_bounty", bounty));
   }

   private static int estimateDamageDeniers(BlockState state) {
      ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
      if (id == null) {
         return CrimeConstants.VANDALISM_FALLBACK_DENIERS;
      }
      return org.millenaire.building.BlockCostRegistry.get(id.toString())
         .map(cost -> Math.max(1, cost.quantity() * CrimeConstants.VANDALISM_DENIERS_PER_COST_UNIT))
         .orElse(CrimeConstants.VANDALISM_FALLBACK_DENIERS);
   }

   public static void reportCaught(
      ServerPlayer player,
      @Nullable Village village,
      @Nullable ResourceLocation cultureId,
      int bountyAmount,
      int repAmount,
      @Nullable MillVillager primary,
      List<MillVillager> witnesses
   ) {
      if (!(player.level() instanceof ServerLevel level)) {
         return;
      }

      ResourceLocation culture = cultureId;
      if (culture == null && village != null) {
         culture = village.getCultureId();
      }

      if (culture == null && primary != null) {
         culture = primary.getCultureId();
      }

      if (culture == null) {
         return;
      }

      int total = PlayerBountySavedData.get(level).add(player.getUUID(), culture, bountyAmount);
      if (bountyAmount != 0) {
         String cultureName = cultureDisplayName(culture);
         player.sendSystemMessage(
            Component.translatable(
               "message.millenaire.crime.bounty_added",
               bountyAmount,
               cultureName,
               total
            )
         );
         BountyHudSync.syncToPlayer(player);
      }
      if (village != null && repAmount != 0) {
         village.adjustReputation(level, player.getUUID(), repAmount);
         VillageSavedData.get(level).setDirty();
      }

      if (primary != null && CombatHelper.isPlayerTargetable(player.isCreative(), player.isSpectator())) {
         primary.setAttackTarget(player);
         CombatHelper.callForHelp(primary, player);
         primary.speakCombatCall("hitbyplayer");
      } else {
         for (MillVillager witness : witnesses) {
            if (CombatHelper.isPlayerTargetable(player.isCreative(), player.isSpectator())) {
               witness.setAttackTarget(player);
               CombatHelper.callForHelp(witness, player);
               break;
            }
         }
      }
   }

   public static void tickNightlyCrimeRaids(ServerLevel level) {
      if (!PlayerBountySavedData.crimeEnabled()) {
         return;
      }

      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      PlayerBountySavedData bountyData = PlayerBountySavedData.get(level);
      long gameTime = level.getGameTime();

      for (Village playerCity : vm.getAllVillages()) {
         if (!playerCity.isPlayerControlled() || playerCity.getOwnerUUID() == null) {
            continue;
         }

         UUID owner = playerCity.getOwnerUUID();

         for (Village attacker : vm.getAllVillages()) {
            if (attacker == playerCity || attacker.isLoneBuilding() || attacker.getRaidTarget() != null || attacker.isUnderAttack()) {
               continue;
            }

            if (!bountyData.shouldRaidCity(owner, attacker.getCultureId())) {
               continue;
            }

            if (!bountyData.tryMarkCrimeRaid(owner, attacker.getCultureId(), gameTime)) {
               continue;
            }

            double distSq = playerCity.getCenter().distSqr(attacker.getCenter());
            int bg = 2000;

            try {
               bg = org.millenaire.config.MillenaireServerConfig.SERVER.backgroundRadius.getAsInt();
            } catch (IllegalStateException ignored) {
            }

            if (bg <= 0 || distSq > (double)bg * (double)bg) {
               continue;
            }

            Integer rel = attacker.getRelations().get(playerCity.getId());
            if (rel == null || rel > -90) {
               attacker.adjustRelationSymmetric(level, playerCity.getId(), -100, true);
            }

            RaidManager.startRaidForced(attacker, playerCity, level);
            ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
            if (ownerPlayer != null) {
               ownerPlayer.sendSystemMessage(
                  Component.translatable("message.millenaire.crime.city_raid", attacker.getVillageName() != null ? attacker.getVillageName() : "?")
               );
            }

            LOGGER.info("[Crime] Culture bounty raid: {} -> player city {}", attacker.getVillageName(), playerCity.getVillageName());
            return;
         }
      }
   }

   public static boolean isBehind(LivingEntity observer, LivingEntity target) {
      Vec3 forward = Vec3.directionFromRotation(0.0F, target.getYRot());
      Vec3 toObserver = observer.position().subtract(target.position());
      if (toObserver.lengthSqr() < 1.0E-4) {
         return false;
      }

      return forward.normalize().dot(toObserver.normalize()) <= CrimeConstants.BEHIND_DOT_MAX;
   }

   public static boolean isFacing(LivingEntity observer, LivingEntity subject) {
      Vec3 forward = Vec3.directionFromRotation(0.0F, observer.getYRot());
      Vec3 toSubject = subject.position().subtract(observer.position());
      if (toSubject.lengthSqr() < 1.0E-4) {
         return false;
      }

      return forward.normalize().dot(toSubject.normalize()) >= CrimeConstants.WITNESS_FACING_DOT_MIN;
   }

   private static ItemStack stealRandomItem(MillVillager villager) {
      VillagerInventory inv = villager.getInventory();
      Map<Item, Integer> all = inv.getAll();
      if (all.isEmpty()) {
         return ItemStack.EMPTY;
      }

      List<Item> items = new ArrayList<>(all.keySet());
      Item chosen = items.get(ThreadLocalRandom.current().nextInt(items.size()));
      int removed = inv.remove(chosen, 1);
      return removed > 0 ? new ItemStack(chosen, removed) : ItemStack.EMPTY;
   }

   private static List<MillVillager> findWitnesses(MillVillager crimeScene, ServerPlayer thief, MillVillager excludeOrTarget) {
      return findWitnessesNear(
         (ServerLevel)crimeScene.level(), crimeScene.blockPosition(), thief, crimeScene.getVillageId(), excludeOrTarget.getUUID()
      );
   }

   private static List<MillVillager> findWitnessesNear(ServerLevel level, BlockPos pos, ServerPlayer thief, @Nullable VillageId villageId) {
      return findWitnessesNear(level, pos, thief, villageId, null);
   }

   private static List<MillVillager> findWitnessesNear(
      ServerLevel level, BlockPos pos, ServerPlayer thief, @Nullable VillageId villageId, @Nullable UUID exclude
   ) {
      List<MillVillager> witnesses = new ArrayList<>();
      AABB box = new AABB(pos).inflate(CrimeConstants.WITNESS_RANGE);

      for (MillVillager other : level.getEntitiesOfClass(MillVillager.class, box)) {
         if (!other.isAlive() || exclude != null && other.getUUID().equals(exclude)) {
            continue;
         }

         if (villageId != null && !villageId.equals(other.getVillageId())) {
            continue;
         }

         VillagerType type = ModCultures.getVillagerType(other.getVillagerTypeId());
         if (type != null && type.isHostile()) {
            continue;
         }

         if (!other.hasLineOfSight(thief)) {
            continue;
         }

         if (!isFacing(other, thief)) {
            continue;
         }

         witnesses.add(other);
      }

      return witnesses;
   }

   @Nullable
   private static MillVillager findInspector(ServerLevel level, Village village, BuildingId buildingId) {
      MillVillager best = null;
      double bestDist = Double.MAX_VALUE;

      for (Entry<UUID, VillagerRecord> entry : village.getVillagerRecords().entrySet()) {
         VillagerRecord record = entry.getValue();
         if (record.isKilled() || record.getHomeBuilding() == null || !record.getHomeBuilding().equals(buildingId)) {
            continue;
         }

         if (level.getEntity(entry.getKey()) instanceof MillVillager mv && mv.isAlive()) {
            double d = mv.distanceToSqr(village.getCenter().getX(), village.getCenter().getY(), village.getCenter().getZ());
            if (d < bestDist) {
               bestDist = d;
               best = mv;
            }
         }
      }

      if (best != null) {
         return best;
      }

      for (Entry<UUID, VillagerRecord> entry : village.getVillagerRecords().entrySet()) {
         if (entry.getValue().isKilled()) {
            continue;
         }

         if (level.getEntity(entry.getKey()) instanceof MillVillager mv && mv.isAlive()) {
            VillagerType type = ModCultures.getVillagerType(mv.getVillagerTypeId());
            if (type != null && (type.hasTag("seller") || type.hasTag("chief") || type.isHelpInAttacks())) {
               return mv;
            }
         }
      }

      for (UUID uuid : village.getVillagerRecords().keySet()) {
         if (level.getEntity(uuid) instanceof MillVillager mv && mv.isAlive()) {
            return mv;
         }
      }

      return null;
   }

   @Nullable
   private static Village resolveVillage(MillVillager villager) {
      VillageId id = villager.getVillageId();
      if (id == null || !(villager.level() instanceof ServerLevel level)) {
         return null;
      }

      return Village.resolve(level, id);
   }

   private static String cultureDisplayName(ResourceLocation cultureId) {
      Culture culture = ModCultures.getCulture(cultureId);
      if (culture != null && culture.displayName() != null && !culture.displayName().isBlank()) {
         return culture.displayName();
      }
      return cultureId.getPath();
   }
}
