package org.millenaire.world.settlement;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Capped overlay promotion of vanilla settlements to Millenaire Villages.
 * Never promotes more than maxActivePromotions; never force-loads to promote.
 */
public final class SettlementTakeover {
   private static final Logger LOGGER = LogUtils.getLogger();

   private SettlementTakeover() {
   }

   public static boolean enabled() {
      try {
         return MillenaireServerConfig.SERVER.settlementsTakeoverEnabled.getAsBoolean();
      } catch (Throwable t) {
         return false;
      }
   }

   public static int maxActive() {
      try {
         return MillenaireServerConfig.SERVER.settlementsMaxActivePromotions.getAsInt();
      } catch (Throwable t) {
         return 4;
      }
   }

   public static int scanInterval() {
      try {
         return MillenaireServerConfig.SERVER.settlementsTakeoverScanInterval.getAsInt();
      } catch (Throwable t) {
         return 20;
      }
   }

   public static double keepRadius() {
      try {
         return MillenaireServerConfig.SERVER.keepActiveRadius.getAsInt();
      } catch (Throwable t) {
         return 200;
      }
   }

   public static void tick(ServerLevel level) {
      if (!enabled()) {
         return;
      }
      if (level.getGameTime() % scanInterval() != 0L) {
         return;
      }
      double mspt = level.getServer().getCurrentSmoothedTickTime();
      if (mspt > 45.0) {
         return;
      }

      SettlementSavedData data = SettlementSavedData.get(level);
      VillageManager manager = VillageSavedData.get(level).getVillageManager();
      double radiusSq = keepRadius() * keepRadius();
      int cap = maxActive();

      // Score vanilla settlements by nearest player distance
      List<Scored> candidates = new ArrayList<>();
      for (Settlement s : data.getRegistry().getAll()) {
         if (s.source() != SettlementSource.VANILLA) {
            continue;
         }
         double nearest = Double.MAX_VALUE;
         for (ServerPlayer player : level.players()) {
            nearest = Math.min(nearest, player.blockPosition().distSqr(s.center()));
         }
         if (nearest <= radiusSq && level.isLoaded(s.center())) {
            candidates.add(new Scored(s, nearest));
         }
      }
      candidates.sort(Comparator.comparingDouble(Scored::dist));

      // Demote promoted vanilla settlements that are far / over cap
      int activePromoted = 0;
      List<Settlement> promoted = new ArrayList<>();
      for (Settlement s : data.getRegistry().getAll()) {
         if (s.source() == SettlementSource.VANILLA && s.promoted() && s.linkedVillageId() != null) {
            promoted.add(s);
         }
      }
      promoted.sort((a, b) -> {
         double da = nearestPlayerDistSq(level, a);
         double db = nearestPlayerDistSq(level, b);
         return Double.compare(db, da); // farthest first for demotion
      });

      // Keep at most cap closest
      List<Settlement> keep = new ArrayList<>();
      for (Scored scored : candidates) {
         if (keep.size() >= cap) {
            break;
         }
         keep.add(scored.settlement);
      }

      for (Settlement s : promoted) {
         boolean shouldKeep = false;
         for (Settlement k : keep) {
            if (k.id().equals(s.id())) {
               shouldKeep = true;
               break;
            }
         }
         if (!shouldKeep) {
            demote(level, manager, s);
            data.markChanged();
         } else {
            activePromoted++;
         }
      }

      for (Settlement s : keep) {
         if (s.promoted() && s.linkedVillageId() != null) {
            Village v = manager.getVillage(new VillageId(s.linkedVillageId()));
            if (v != null) {
               // Ensure active if chunks loaded
               if (level.isLoaded(v.getCenter()) && !v.isActive()) {
                  v.setActive(true);
               }
               continue;
            }
         }
         if (activePromoted >= cap) {
            break;
         }
         if (promoteOverlay(level, manager, s)) {
            activePromoted++;
            data.markChanged();
         }
      }
   }

   private static double nearestPlayerDistSq(ServerLevel level, Settlement s) {
      double best = Double.MAX_VALUE;
      for (ServerPlayer player : level.players()) {
         best = Math.min(best, player.blockPosition().distSqr(s.center()));
      }
      return best;
   }

   private static boolean promoteOverlay(ServerLevel level, VillageManager manager, Settlement settlement) {
      if (!level.isLoaded(settlement.center())) {
         return false;
      }
      Holder<Biome> biome = level.getBiome(settlement.center());
      ResourceLocation culture = settlement.cultureId() != null ? settlement.cultureId() : SettlementCultureResolver.resolveCulture(biome);
      VillageType type = culture != null ? SettlementCultureResolver.pickVillageType(culture, biome) : null;
      if (type == null) {
         // Pick any non-lone type for culture, or first available
         for (VillageType t : ModCultures.getAllVillageTypes().values()) {
            if (!t.loneBuilding()) {
               type = t;
               culture = t.culture();
               break;
            }
         }
      }
      if (type == null || culture == null) {
         return false;
      }

      VillageId id = VillageId.random();
      Village village = new Village(id, culture, type.id(), settlement.center());
      if (settlement.displayName() != null) {
         village.setVillageName(settlement.displayName());
      }
      // Overlay: no buildings placed — dormant presence village, activated when in range
      village.setActive(true);
      manager.addVillage(village);
      VillageSavedData.get(level).setDirty();

      settlement.setLinkedVillageId(id.uuid());
      settlement.setPromoted(true);
      settlement.setCultureId(culture);
      LOGGER.info("[Settlements] Promoted {} -> Village {} ({})", settlement.id(), settlement.displayName(), culture);
      return true;
   }

   private static void demote(ServerLevel level, VillageManager manager, Settlement settlement) {
      if (settlement.linkedVillageId() == null) {
         settlement.setPromoted(false);
         return;
      }
      VillageId vid = new VillageId(settlement.linkedVillageId());
      Village v = manager.getVillage(vid);
      if (v != null) {
         v.setActive(false);
         // Keep dormant village in SavedData (state preserved) but mark settlement demoted for cap accounting
         // Soft demote: stay registered but inactive
         LOGGER.debug("[Settlements] Demoted {} (village kept dormant)", settlement.id());
      }
      settlement.setPromoted(false);
   }

   /** Manual OP promote of nearest vanilla settlement. */
   public static boolean promoteNearest(ServerLevel level, net.minecraft.core.BlockPos pos) {
      Settlement s = SettlementSavedData.get(level).getRegistry().getNearest(pos, keepRadius());
      if (s == null || s.source() != SettlementSource.VANILLA) {
         return false;
      }
      VillageManager manager = VillageSavedData.get(level).getVillageManager();
      boolean ok = promoteOverlay(level, manager, s);
      if (ok) {
         SettlementSavedData.get(level).markChanged();
      }
      return ok;
   }

   public static boolean demoteNearest(ServerLevel level, net.minecraft.core.BlockPos pos) {
      Settlement s = SettlementSavedData.get(level).getRegistry().getNearest(pos, keepRadius());
      if (s == null || !s.promoted()) {
         return false;
      }
      demote(level, VillageSavedData.get(level).getVillageManager(), s);
      SettlementSavedData.get(level).markChanged();
      return true;
   }

   private record Scored(Settlement settlement, double dist) {
   }
}
