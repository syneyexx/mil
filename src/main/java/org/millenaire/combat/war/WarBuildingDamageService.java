package org.millenaire.combat.war;

import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.perf.TickPerfCounters;
import org.millenaire.village.Village;
import org.millenaire.village.Village.PendingProject;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Budgeted war sabotage: setBlock air on non-TH buildings; TH soft-cap only; enqueue repair.
 * Never forgets/deletes the town hall.
 */
public final class WarBuildingDamageService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Queue<DamageRequest> QUEUE = new ArrayDeque<>();
   private static final int MAX_QUEUE = 128;

   private WarBuildingDamageService() {
   }

   public record DamageRequest(UUID villageId, UUID buildingId, UUID campaignId, @Nullable UUID waveId, int preferBlocks) {
   }

   public static void enqueue(DamageRequest request) {
      if (request == null || QUEUE.size() >= MAX_QUEUE) {
         return;
      }
      QUEUE.offer(request);
   }

   public static void requestDamage(Village village, BuildingInstance building, UUID campaignId, @Nullable UUID waveId, int preferBlocks) {
      if (village == null || building == null) {
         return;
      }
      enqueue(new DamageRequest(village.getId().uuid(), building.getId().uuid(), campaignId, waveId, Math.max(1, preferBlocks)));
   }

   public static int tick(ServerLevel level) {
      if (!MillenaireServerConfig.SERVER.warBuildingDamageEnabled.getAsBoolean()) {
         QUEUE.clear();
         return 0;
      }
      int budget = MillenaireServerConfig.SERVER.warDamageBlocksPerTick.getAsInt();
      int used = 0;
      while (used < budget && !QUEUE.isEmpty()) {
         DamageRequest req = QUEUE.poll();
         if (req == null) {
            break;
         }
         int applied = applyOne(level, req, budget - used);
         used += applied;
         if (applied > 0) {
            TickPerfCounters.WAR_DAMAGE_BLOCKS.addAndGet(applied);
         }
      }
      return used;
   }

   private static int applyOne(ServerLevel level, DamageRequest req, int remainingBudget) {
      Village village = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(req.villageId()));
      if (village == null) {
         return 0;
      }
      BuildingInstance building = village.getBuilding(new BuildingId(req.buildingId()));
      if (building == null || !building.isOperational()) {
         return 0;
      }

      boolean isTh = isTownHall(village, building);
      int thCap = MillenaireServerConfig.SERVER.warThDamageCap.getAsInt();
      if (isTh && building.getWarDamageLevel() >= thCap) {
         return 0;
      }

      int waveCap = MillenaireServerConfig.SERVER.warDamageBlocksPerWave.getAsInt();
      WarCampaignSavedData data = WarCampaignSavedData.get(level);
      WarWaveInstance wave = req.waveId() != null ? data.getWave(req.waveId()) : null;
      if (wave != null && wave.blocksDamaged() >= waveCap) {
         return 0;
      }

      int toBreak = Math.min(remainingBudget, Math.min(req.preferBlocks(), isTh ? 2 : 4));
      if (toBreak <= 0) {
         return 0;
      }

      List<BlockPos> candidates = collectBreakableBlocks(level, building, isTh);
      if (candidates.isEmpty()) {
         bumpDamageLevel(village, building, isTh, thCap, 1);
         maybeEnqueueRepair(village, building, isTh);
         return 0;
      }

      ThreadLocalRandom rng = ThreadLocalRandom.current();
      int broken = 0;
      for (int i = 0; i < toBreak && !candidates.isEmpty(); i++) {
         int idx = rng.nextInt(candidates.size());
         BlockPos pos = candidates.remove(idx);
         BlockState state = level.getBlockState(pos);
         if (state.isAir() || state.getDestroySpeed(level, pos) < 0) {
            continue;
         }
         level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
         broken++;
      }

      if (broken > 0) {
         bumpDamageLevel(village, building, isTh, thCap, Math.max(1, broken));
         if (wave != null) {
            wave.addBlocksDamaged(broken);
            data.setDirty();
         }
         maybeEnqueueRepair(village, building, isTh);
         village.markDirty();
         VillageSavedData.get(level).setDirty();
      }
      return broken;
   }

   private static void bumpDamageLevel(Village village, BuildingInstance building, boolean isTh, int thCap, int amount) {
      int next = building.getWarDamageLevel() + amount;
      if (isTh) {
         next = Math.min(next, thCap);
      }
      building.setWarDamageLevel(next);
   }

   private static boolean isTownHall(Village village, BuildingInstance building) {
      BuildingInstance th = village.getTownhall();
      return th != null && th.getId().equals(building.getId());
   }

   private static List<BlockPos> collectBreakableBlocks(ServerLevel level, BuildingInstance building, boolean isTh) {
      List<BlockPos> out = new ArrayList<>();
      BlockPos origin = building.getOrigin();
      if (origin == null) {
         return out;
      }
      // Sample a modest cube around origin; containsPos filters membership.
      int radius = isTh ? 4 : 6;
      int ySpan = isTh ? 3 : 5;
      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            for (int dy = 0; dy <= ySpan; dy++) {
               BlockPos pos = origin.offset(dx, dy, dz);
               if (!building.containsPos(pos)) {
                  continue;
               }
               BlockState state = level.getBlockState(pos);
               if (state.isAir() || state.getDestroySpeed(level, pos) < 0) {
                  continue;
               }
               // Soft-cap TH: never break the origin column floor.
               if (isTh && dx == 0 && dz == 0 && dy == 0) {
                  continue;
               }
               out.add(pos);
               if (out.size() >= 48) {
                  return out;
               }
            }
         }
      }
      return out;
   }

   /** Enqueue repair via PendingProject (upgrade re-apply) when damage is meaningful. Never forgets TH. */
   public static void maybeEnqueueRepair(Village village, BuildingInstance building, boolean isTh) {
      if (building.getWarDamageLevel() < 10) {
         return;
      }
      if (building.getPlanSetId() == null || building.getVariant() == null) {
         return;
      }
      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(building.getPlanSetId());
      if (planSet == null) {
         return;
      }
      // Already queued as pending repair/upgrade of this building.
      PendingProject pending = village.getPendingProject();
      if (pending != null && pending.buildingId() != null && pending.buildingId().equals(building.getId())) {
         return;
      }
      if (pending != null) {
         return;
      }
      // Controlled queue: only if empty slot and not TH-delete; upgrades use pending path.
      village.setPendingProject(
         new PendingProject(building.getPlanSetId(), building.getVariant(), building.getLevel(), true, building.getId())
      );
      LOGGER.debug("[War] Enqueued repair for {} in {}", building.getPlanSetId(), village.getVillageName());
   }

   public static void clearQueue() {
      QUEUE.clear();
   }
}
