package org.millenaire.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.village.Village;

/**
 * Authoritative owner of village needs. Other systems request create/update; they do not reserve goods here.
 */
public final class NeedService {
   public static final int HISTORY_CAP = 32;

   private final Village village;
   private final Map<UUID, Need> needs = new LinkedHashMap<>();
   private final List<Need> history = new ArrayList<>();
   private String lastPlanSummary = "";
   private ProductionChoice lastChoice = ProductionChoice.WAIT;
   private long lastPlanGameTime = -1L;

   public NeedService(Village village) {
      this.village = village;
   }

   public Village village() {
      return this.village;
   }

   public List<Need> activeNeeds() {
      List<Need> out = new ArrayList<>();
      for (Need n : this.needs.values()) {
         if (n.acceptsDelivery()) {
            out.add(n);
         }
      }
      out.sort(Comparator.comparingInt(Need::priority).reversed());
      return out;
   }

   public List<Need> allNeeds() {
      return List.copyOf(this.needs.values());
   }

   @Nullable
   public Need get(UUID id) {
      return this.needs.get(id);
   }

   public String lastPlanSummary() {
      return this.lastPlanSummary;
   }

   public ProductionChoice lastChoice() {
      return this.lastChoice;
   }

   public void setLastPlan(ProductionChoice choice, String summary, long gameTime) {
      this.lastChoice = choice == null ? ProductionChoice.WAIT : choice;
      this.lastPlanSummary = summary == null ? "" : summary;
      this.lastPlanGameTime = gameTime;
   }

   public long lastPlanGameTime() {
      return this.lastPlanGameTime;
   }

   public Need upsert(
      NeedCategory category,
      Item item,
      int amount,
      int priority,
      long deadlineGameTime,
      String sourceRef,
      long gameTime
   ) {
      if (item == null || amount <= 0) {
         return null;
      }
      for (Need existing : this.needs.values()) {
         if (existing.acceptsDelivery()
            && existing.category() == category
            && existing.item() == item
            && existing.sourceRef().equals(sourceRef == null ? "" : sourceRef)) {
            // Hysterese: only bump when amount rises meaningfully
            if (amount > existing.amount() + Math.max(2, existing.amount() / 10)) {
               // replace with higher demand
               existing.setState(NeedState.CANCELLED);
               archive(existing);
               this.needs.remove(existing.id());
               break;
            }
            return existing;
         }
      }
      int max = MillenaireServerConfig.SERVER.economyMaxNeedsPerVillage.getAsInt();
      evictLowestIfNeeded(max);
      Need need = new Need(
         UUID.randomUUID(),
         this.village.getId().uuid(),
         category,
         item,
         amount,
         priority,
         deadlineGameTime,
         sourceRef,
         NeedState.ACTIVE,
         gameTime
      );
      this.needs.put(need.id(), need);
      this.village.markDirty();
      return need;
   }

   public void tickExpiry(long gameTime) {
      boolean dirty = false;
      Iterator<Map.Entry<UUID, Need>> it = this.needs.entrySet().iterator();
      while (it.hasNext()) {
         Need need = it.next().getValue();
         NeedState before = need.state();
         need.tickExpiry(gameTime);
         if (need.state() != before && !need.acceptsDelivery()) {
            archive(need);
            it.remove();
            dirty = true;
         }
      }
      if (dirty) {
         this.village.markDirty();
      }
   }

   public boolean applyDelivery(UUID needId, int qty) {
      Need need = this.needs.get(needId);
      if (need == null || !need.acceptsDelivery()) {
         return false;
      }
      need.applyDelivery(qty);
      if (!need.acceptsDelivery()) {
         archive(need);
         this.needs.remove(needId);
      }
      this.village.markDirty();
      return true;
   }

   public void cancelBySourcePrefix(String sourcePrefix, String reason) {
      if (sourcePrefix == null) {
         return;
      }
      Iterator<Map.Entry<UUID, Need>> it = this.needs.entrySet().iterator();
      while (it.hasNext()) {
         Need need = it.next().getValue();
         if (need.sourceRef().startsWith(sourcePrefix) && need.acceptsDelivery()) {
            need.setState(NeedState.CANCELLED);
            need.setBlockedReason(reason == null ? "cancelled" : reason);
            archive(need);
            it.remove();
         }
      }
      this.village.markDirty();
   }

   @Nullable
   public Need topBlocked() {
      Need best = null;
      for (Need n : this.needs.values()) {
         if (!n.acceptsDelivery()) {
            continue;
         }
         if (n.blockedReason() != null && !n.blockedReason().isEmpty()) {
            if (best == null || n.priority() > best.priority()) {
               best = n;
            }
         }
      }
      return best;
   }

   private void evictLowestIfNeeded(int max) {
      while (this.needs.size() >= max) {
         Need lowest = null;
         for (Need n : this.needs.values()) {
            if (lowest == null || n.priority() < lowest.priority()) {
               lowest = n;
            }
         }
         if (lowest == null) {
            break;
         }
         lowest.setState(NeedState.CANCELLED);
         lowest.setBlockedReason("evicted_cap");
         archive(lowest);
         this.needs.remove(lowest.id());
      }
   }

   private void archive(Need need) {
      this.history.add(need);
      while (this.history.size() > HISTORY_CAP) {
         this.history.removeFirst();
      }
   }

   public CompoundTag save(HolderLookup.Provider registries) {
      CompoundTag tag = new CompoundTag();
      ListTag list = new ListTag();
      for (Need n : this.needs.values()) {
         list.add(n.save(registries));
      }
      tag.put("needs", list);
      ListTag hist = new ListTag();
      for (Need n : this.history) {
         hist.add(n.save(registries));
      }
      tag.put("history", hist);
      tag.putString("lastPlan", this.lastPlanSummary);
      tag.putString("lastChoice", this.lastChoice.name());
      tag.putLong("lastPlanGameTime", this.lastPlanGameTime);
      return tag;
   }

   public void load(CompoundTag tag, HolderLookup.Provider registries) {
      this.needs.clear();
      this.history.clear();
      if (tag.contains("needs", Tag.TAG_LIST)) {
         ListTag list = tag.getList("needs", Tag.TAG_COMPOUND);
         for (int i = 0; i < list.size(); i++) {
            Need n = Need.load(list.getCompound(i), registries);
            this.needs.put(n.id(), n);
         }
      }
      if (tag.contains("history", Tag.TAG_LIST)) {
         ListTag hist = tag.getList("history", Tag.TAG_COMPOUND);
         for (int i = 0; i < hist.size(); i++) {
            this.history.add(Need.load(hist.getCompound(i), registries));
         }
      }
      this.lastPlanSummary = tag.contains("lastPlan") ? tag.getString("lastPlan") : "";
      if (tag.contains("lastChoice")) {
         try {
            this.lastChoice = ProductionChoice.valueOf(tag.getString("lastChoice"));
         } catch (Exception ignored) {
            this.lastChoice = ProductionChoice.WAIT;
         }
      }
      this.lastPlanGameTime = tag.contains("lastPlanGameTime") ? tag.getLong("lastPlanGameTime") : -1L;
   }
}
