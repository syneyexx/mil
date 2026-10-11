package org.millenaire.religion.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Persistent per-NPC religious identity. Initialized once; never re-rolled on reload.
 */
public final class NpcFaithProfile {
   private final UUID villagerId;
   private final List<FaithAffiliation> affiliations = new ArrayList<>();
   private boolean initialized;
   private long lastDailyPracticeDay = -1L;
   private final List<String> recentParticipation = new ArrayList<>();
   private static final int MAX_HISTORY = 16;

   public NpcFaithProfile(UUID villagerId) {
      this.villagerId = villagerId;
   }

   public UUID villagerId() {
      return this.villagerId;
   }

   public boolean initialized() {
      return this.initialized;
   }

   public void markInitialized() {
      this.initialized = true;
   }

   public List<FaithAffiliation> affiliations() {
      return this.affiliations;
   }

   public boolean hasTradition(ResourceLocation traditionId) {
      if (traditionId == null) {
         return false;
      }
      for (FaithAffiliation a : this.affiliations) {
         if (traditionId.equals(a.traditionId())) {
            return true;
         }
      }
      return false;
   }

   @Nullable
   public FaithAffiliation primaryAffiliation() {
      if (this.affiliations.isEmpty()) {
         return null;
      }
      FaithAffiliation best = this.affiliations.get(0);
      for (FaithAffiliation a : this.affiliations) {
         if (a.devotion() > best.devotion()) {
            best = a;
         }
      }
      return best;
   }

   public void addAffiliation(FaithAffiliation affiliation) {
      if (affiliation == null) {
         return;
      }
      for (int i = 0; i < this.affiliations.size(); i++) {
         if (this.affiliations.get(i).traditionId().equals(affiliation.traditionId())) {
            this.affiliations.set(i, affiliation);
            return;
         }
      }
      this.affiliations.add(affiliation);
   }

   public long lastDailyPracticeDay() {
      return this.lastDailyPracticeDay;
   }

   public void setLastDailyPracticeDay(long day) {
      this.lastDailyPracticeDay = day;
   }

   public void recordParticipation(String ceremonyId) {
      if (ceremonyId == null || ceremonyId.isEmpty()) {
         return;
      }
      this.recentParticipation.remove(ceremonyId);
      this.recentParticipation.add(0, ceremonyId);
      while (this.recentParticipation.size() > MAX_HISTORY) {
         this.recentParticipation.remove(this.recentParticipation.size() - 1);
      }
   }

   public List<String> recentParticipation() {
      return List.copyOf(this.recentParticipation);
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.villagerId);
      tag.putBoolean("initialized", this.initialized);
      tag.putLong("lastDaily", this.lastDailyPracticeDay);
      ListTag aff = new ListTag();
      for (FaithAffiliation a : this.affiliations) {
         aff.add(a.save());
      }
      tag.put("affiliations", aff);
      ListTag hist = new ListTag();
      for (String c : this.recentParticipation) {
         CompoundTag h = new CompoundTag();
         h.putString("c", c);
         hist.add(h);
      }
      tag.put("participation", hist);
      return tag;
   }

   public static NpcFaithProfile load(CompoundTag tag) {
      UUID id = tag.getUUID("id");
      NpcFaithProfile profile = new NpcFaithProfile(id);
      profile.initialized = tag.getBoolean("initialized");
      profile.lastDailyPracticeDay = tag.contains("lastDaily") ? tag.getLong("lastDaily") : -1L;
      ListTag aff = tag.getList("affiliations", 10);
      for (int i = 0; i < aff.size(); i++) {
         try {
            profile.affiliations.add(FaithAffiliation.load(aff.getCompound(i)));
         } catch (Exception ignored) {
         }
      }
      ListTag hist = tag.getList("participation", 10);
      for (int i = 0; i < hist.size(); i++) {
         String c = hist.getCompound(i).getString("c");
         if (!c.isEmpty()) {
            profile.recentParticipation.add(c);
         }
      }
      return profile;
   }
}
