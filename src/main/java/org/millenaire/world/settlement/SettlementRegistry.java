package org.millenaire.world.settlement;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import org.millenaire.village.Village;
import org.millenaire.world.road.RoadNode;
import org.millenaire.world.road.RoadNodeSource;

public final class SettlementRegistry {
   private final Map<String, Settlement> settlements = new LinkedHashMap<>();

   public boolean registerFromRoadNode(RoadNode node, ServerLevel level) {
      SettlementId id = SettlementId.of(node.id());
      Settlement existing = this.settlements.get(id.value());
      if (existing != null) {
         existing.updateGeometry(node.center(), node.boundingBox());
         return false;
      }
      Holder<Biome> biome = level.getBiome(node.center());
      ResourceLocation culture = SettlementCultureResolver.resolveCulture(biome);
      String name = SettlementNames.generate(id, culture, level.getSeed());
      Settlement settlement = new Settlement(
         id,
         node.source() == RoadNodeSource.VANILLA ? SettlementSource.VANILLA : SettlementSource.MILLENAIRE,
         node.center(),
         node.boundingBox(),
         culture,
         name,
         node.biomeId(),
         node.millenaireVillageId()
      );
      this.settlements.put(id.value(), settlement);
      return true;
   }

   public boolean registerMillenaire(Village village, RoadNode node, ServerLevel level) {
      SettlementId id = SettlementId.millenaire(village.getId().uuid());
      Settlement existing = this.settlements.get(id.value());
      String name = village.getVillageName() != null ? village.getVillageName() : SettlementNames.generate(id, village.getCultureId(), level.getSeed());
      if (existing != null) {
         existing.updateGeometry(node.center(), node.boundingBox());
         existing.setDisplayName(name);
         existing.setCultureId(village.getCultureId());
         existing.setLinkedVillageId(village.getId().uuid());
         existing.setPromoted(true);
         return false;
      }
      Settlement settlement = new Settlement(
         id,
         SettlementSource.MILLENAIRE,
         node.center(),
         node.boundingBox(),
         village.getCultureId(),
         name,
         node.biomeId(),
         village.getId().uuid()
      );
      settlement.setPromoted(true);
      this.settlements.put(id.value(), settlement);
      return true;
   }

   @Nullable
   public Settlement get(String id) {
      return this.settlements.get(id);
   }

   @Nullable
   public Settlement getNearest(BlockPos pos, double maxDist) {
      Settlement best = null;
      double bestDist = maxDist * maxDist;
      for (Settlement s : this.settlements.values()) {
         double d = s.center().distSqr(pos);
         if (d <= bestDist) {
            bestDist = d;
            best = s;
         }
      }
      return best;
   }

   public Collection<Settlement> getAll() {
      return Collections.unmodifiableCollection(this.settlements.values());
   }

   public int size() {
      return this.settlements.size();
   }

   public int countBySource(SettlementSource source) {
      int n = 0;
      for (Settlement s : this.settlements.values()) {
         if (s.source() == source) {
            n++;
         }
      }
      return n;
   }

   public int promotedCount() {
      int n = 0;
      for (Settlement s : this.settlements.values()) {
         if (s.promoted() && s.source() == SettlementSource.VANILLA) {
            n++;
         }
      }
      return n;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      ListTag list = new ListTag();
      for (Settlement s : this.settlements.values()) {
         list.add(s.save());
      }
      tag.put("settlements", list);
      return tag;
   }

   public void load(CompoundTag tag) {
      this.settlements.clear();
      ListTag list = tag.getList("settlements", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         Settlement s = Settlement.load(list.getCompound(i));
         this.settlements.put(s.id().value(), s);
      }
   }
}
