package org.millenaire.civilization.interaction;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.culture.CulturalTemperament;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.FactionType;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;

/**
 * Recomputes border claim heat from settlement proximity + expansive temperament.
 */
public final class ClaimHeatService {
   private ClaimHeatService() {
   }

   public static void recompute(ServerLevel level) {
      if (level == null) {
         return;
      }
      InteractionSavedData data = InteractionSavedData.get(level);
      List<Faction> factions = new ArrayList<>(FactionSavedData.get(level).getFactionManager().getAllFactions());
      for (int i = 0; i < factions.size(); i++) {
         Faction a = factions.get(i);
         if (a.getSettlementCount() <= 0) {
            continue;
         }
         for (int j = i + 1; j < factions.size(); j++) {
            Faction b = factions.get(j);
            if (b.getSettlementCount() <= 0) {
               continue;
            }
            int heat = computePairHeat(level, a, b);
            // Decay existing heat slightly if proximity drops.
            ClaimHeatEntry existing = data.claimHeat().get(pairKey(a.getId().asString(), b.getId().asString()));
            if (existing != null && heat < existing.heat()) {
               heat = Math.max(heat, existing.heat() - InteractionConfig.CLAIM_HEAT_DECAY_PER_TICK_PASS);
            }
            data.putClaimHeat(new ClaimHeatEntry(a.getId().asString(), b.getId().asString(), heat));
         }
      }
   }

   public static void decay(ServerLevel level) {
      if (level == null) {
         return;
      }
      InteractionSavedData data = InteractionSavedData.get(level);
      for (ClaimHeatEntry entry : new ArrayList<>(data.claimHeat().values())) {
         if (entry.heat() > 0) {
            data.putClaimHeat(new ClaimHeatEntry(
               entry.factionA(),
               entry.factionB(),
               entry.heat() - InteractionConfig.CLAIM_HEAT_DECAY_PER_TICK_PASS
            ));
         }
      }
   }

   private static int computePairHeat(ServerLevel level, Faction a, Faction b) {
      int closestBlocks = Integer.MAX_VALUE;
      VillageSavedData villages = VillageSavedData.get(level);
      for (VillageId aid : a.getSettlementIds()) {
         Village av = villages.getVillageManager().getVillage(aid);
         if (av == null) {
            continue;
         }
         for (VillageId bid : b.getSettlementIds()) {
            Village bv = villages.getVillageManager().getVillage(bid);
            if (bv == null) {
               continue;
            }
            long dx = (long) av.getCenter().getX() - bv.getCenter().getX();
            long dz = (long) av.getCenter().getZ() - bv.getCenter().getZ();
            int dist = (int) Math.min(Integer.MAX_VALUE, Math.sqrt(dx * dx + dz * dz));
            closestBlocks = Math.min(closestBlocks, dist);
         }
      }
      if (closestBlocks == Integer.MAX_VALUE || closestBlocks > InteractionConfig.CLAIM_HEAT_NEAR_BLOCKS) {
         return 0;
      }
      int base;
      if (closestBlocks <= InteractionConfig.CLAIM_HEAT_HOT_BLOCKS) {
         base = 70;
      } else {
         base = 40;
      }
      int expansiveBonus = (expansive(a) + expansive(b)) / 10;
      // Culture vs player borders heat a bit more.
      int typeBonus = (a.getType() != b.getType() && (a.getType() == FactionType.PLAYER || b.getType() == FactionType.PLAYER))
         ? 10
         : 0;
      return Math.min(InteractionConfig.CLAIM_HEAT_MAX, base + expansiveBonus + typeBonus);
   }

   private static int expansive(Faction faction) {
      if (faction.getCultureId() == null) {
         return 50;
      }
      Culture culture = ModCultures.getCulture(faction.getCultureId());
      CulturalTemperament t = culture == null ? CulturalTemperament.DEFAULT : culture.temperament();
      return t.expansive();
   }

   private static String pairKey(String a, String b) {
      return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
   }
}
