package org.millenaire.village;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.combat.raid.RaidManager;
import org.millenaire.combat.war.WarCampaignService;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;

public final class CultureWarService {
   private CultureWarService() {
   }

   /** Fallback night edge — delegates to day catch-up simulator (no duplicate drift). */
   public static void tickNightly(ServerLevel level) {
      CultureDiplomacySimulator.ensureUpToDate(level);
   }

   public static void propagateWarToVillages(ServerLevel level, ResourceLocation cultureA, ResourceLocation cultureB) {
      applyCulturePairRelations(level, cultureA, cultureB, -100);
   }

   public static void propagatePeaceToVillages(ServerLevel level, ResourceLocation cultureA, ResourceLocation cultureB) {
      applyCulturePairRelations(level, cultureA, cultureB, 0);
   }

   private static void applyCulturePairRelations(ServerLevel level, ResourceLocation cultureA, ResourceLocation cultureB, int relationValue) {
      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      int bgRadius = MillenaireServerConfig.SERVER.backgroundRadius.get();
      long radiusSq = (long)bgRadius * bgRadius;
      List<Village> groupA = new ArrayList<>();
      List<Village> groupB = new ArrayList<>();

      for (Village v : vm.getAllVillages()) {
         if (v.isLoneBuilding()) {
            continue;
         }

         ResourceLocation culture = v.getCultureId();
         if (cultureA.equals(culture)) {
            groupA.add(v);
         } else if (cultureB.equals(culture)) {
            groupB.add(v);
         }
      }

      for (Village a : groupA) {
         BlockPos ac = a.getCenter();

         for (Village b : groupB) {
            if (bgRadius <= 0 || ac.distSqr(b.getCenter()) <= radiusSq) {
               // Culture ≠ empire: respect village-level non-aggression treaties.
               if (relationValue < 0
                  && org.millenaire.diplomacy.TreatyService.blocksWar(level, a.getId().uuid(), b.getId().uuid())) {
                  continue;
               }
               a.adjustRelationSymmetric(level, b.getId(), relationValue, true);
               if (relationValue < 0) {
                  org.millenaire.diplomacy.TreatyService.onWarDeclared(
                     level, a.getId().uuid(), b.getId().uuid(), "culture_war_propagation"
                  );
               }
            }
         }
      }

      VillageSavedData.get(level).setDirty();
   }

   public static void tryScheduleWarRaids(ServerLevel level, CultureStandingSavedData data) {
      int maxAttempts = MillenaireServerConfig.SERVER.cultureWarRaidAttemptsPerNight.get();
      if (maxAttempts <= 0) {
         return;
      }

      int maxConcurrent = MillenaireServerConfig.SERVER.maxConcurrentRaids.getAsInt();
      if (countActiveRaids(level) >= maxConcurrent) {
         return;
      }

      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      int bgRadius = MillenaireServerConfig.SERVER.backgroundRadius.get();
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      int attempts = 0;
      List<String> warKeys = new ArrayList<>(data.getWarPairKeys());
      java.util.Collections.shuffle(warKeys, rng);

      for (String warKey : warKeys) {
         if (attempts >= maxAttempts || countActiveRaids(level) >= maxConcurrent) {
            break;
         }

         String[] parts = warKey.split("\\|", 2);
         if (parts.length != 2) {
            continue;
         }

         ResourceLocation cultureA = ResourceLocation.parse(parts[0]);
         ResourceLocation cultureB = ResourceLocation.parse(parts[1]);
         Village attacker = pickWarRaidAttacker(level, vm, cultureA, cultureB, bgRadius, rng);
         if (attacker == null) {
            attacker = pickWarRaidAttacker(level, vm, cultureB, cultureA, bgRadius, rng);
         }

         if (attacker != null) {
            ResourceLocation enemyCulture = attacker.getCultureId().equals(cultureA) ? cultureB : cultureA;
            Village target = pickWarRaidTarget(attacker, vm, enemyCulture, rng);
            if (target != null) {
               if (MillenaireServerConfig.SERVER.warCampaignsEnabled.getAsBoolean()) {
                  WarCampaignService.declareOrGet(level, cultureA, cultureB, false);
                  if (!WarCampaignService.tryScheduleCampaignWave(level, attacker, target)) {
                     RaidManager.planRaid(attacker, target, level);
                  }
               } else {
                  RaidManager.planRaid(attacker, target, level);
               }
               attempts++;
            }
         }
      }
   }

   @Nullable
   private static Village pickWarRaidAttacker(
      ServerLevel level, VillageManager vm, ResourceLocation attackerCulture, ResourceLocation enemyCulture, int bgRadius, ThreadLocalRandom rng
   ) {
      List<Village> candidates = new ArrayList<>();

      for (Village v : vm.getAllVillages()) {
         if (v.isLoneBuilding() || !attackerCulture.equals(v.getCultureId()) || v.getRaidTarget() != null) {
            continue;
         }

         VillageType vt = ModCultures.getVillageType(v.getVillageTypeId());
         if (vt == null || !vt.carriesRaid()) {
            continue;
         }

         if (villageHasActiveRaiders(v)) {
            continue;
         }

         BlockPos c = v.getCenter();
         if (!level.hasNearbyAlivePlayer(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, bgRadius)) {
            continue;
         }

         candidates.add(v);
      }

      return candidates.isEmpty() ? null : candidates.get(rng.nextInt(candidates.size()));
   }

   @Nullable
   private static Village pickWarRaidTarget(Village attacker, VillageManager vm, ResourceLocation enemyCulture, ThreadLocalRandom rng) {
      int maxAcceptable = RaidManager.maxAcceptableTargetStrength(attacker.getVillageRaidingStrength());
      if (maxAcceptable <= 0) {
         return null;
      }

      List<Village> candidates = new ArrayList<>();

      for (Village v : vm.getAllVillages()) {
         if (v.isLoneBuilding() || !enemyCulture.equals(v.getCultureId()) || v == attacker) {
            continue;
         }

         if (RaidManager.isRegularViableTargetForCultureWar(v.getVillageDefendingStrength(), maxAcceptable, v.isUnderAttack())) {
            candidates.add(v);
         }
      }

      return candidates.isEmpty() ? null : candidates.get(rng.nextInt(candidates.size()));
   }

   private static boolean villageHasActiveRaiders(Village village) {
      for (VillagerRecord r : village.getVillagerRecords().values()) {
         if (r.isRaidingVillage()) {
            return true;
         }
      }

      return false;
   }

   private static int countActiveRaids(ServerLevel level) {
      int count = 0;

      for (Village v : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (v.getRaidStart() > 0L || v.isUnderAttack()) {
            count++;
         }
      }

      return count;
   }

   @Nullable
   public static ResourceLocation playerCultureId(ServerPlayer player, ServerLevel level) {
      VillageManager vm = VillageSavedData.get(level).getVillageManager();

      for (Village v : vm.getAllVillages()) {
         if (v.isPlayerControlled() && player.getUUID().equals(v.getOwnerUUID())) {
            return v.getCultureId();
         }
      }

      return null;
   }

   public static boolean canPlayerDeclareFor(ServerPlayer player, ResourceLocation culture, ServerLevel level) {
      VillageManager vm = VillageSavedData.get(level).getVillageManager();

      for (Village v : vm.getAllVillages()) {
         if (v.isPlayerControlled() && player.getUUID().equals(v.getOwnerUUID()) && culture.equals(v.getCultureId())) {
            return true;
         }
      }

      return false;
   }

   public static boolean allowsTrade(ResourceLocation cultureA, ResourceLocation cultureB, ServerLevel level) {
      return !CultureStandingSavedData.get(level).isAtWar(cultureA, cultureB);
   }

   public static double tradeStandingMultiplier(ResourceLocation cultureA, ResourceLocation cultureB, ServerLevel level) {
      CultureStandingSavedData data = CultureStandingSavedData.get(level);
      if (data.isAtWar(cultureA, cultureB)) {
         return 0.0;
      }

      int standing = data.getStanding(cultureA, cultureB);
      if (standing >= CultureStandingSavedData.ALLIED_THRESHOLD) {
         return 1.25;
      }

      if (standing >= 0) {
         return 1.0;
      }

      return 0.75;
   }

   public static void playerDeclareWar(ServerLevel level, ResourceLocation ownCulture, ResourceLocation otherCulture) {
      CultureStandingSavedData data = CultureStandingSavedData.get(level);
      if (!data.isAtWar(ownCulture, otherCulture)) {
         data.declareWar(ownCulture, otherCulture);
         propagateWarToVillages(level, ownCulture, otherCulture);
         data.setDirty();
      }
      ensureWarCampaign(level, ownCulture, otherCulture, true);
   }

   /** Start a wave campaign when culture war begins (no-op if campaigns disabled or already open). */
   public static void ensureWarCampaign(ServerLevel level, ResourceLocation cultureA, ResourceLocation cultureB, boolean playerWar) {
      if (MillenaireServerConfig.SERVER.warCampaignsEnabled.getAsBoolean()) {
         WarCampaignService.declareOrGet(level, cultureA, cultureB, playerWar);
      }
   }

   public static void playerEndWar(ServerLevel level, ResourceLocation ownCulture, ResourceLocation otherCulture) {
      CultureStandingSavedData data = CultureStandingSavedData.get(level);
      if (data.isAtWar(ownCulture, otherCulture)) {
         data.endWar(ownCulture, otherCulture);
         propagatePeaceToVillages(level, ownCulture, otherCulture);
         data.setDirty();
      }
   }
}
