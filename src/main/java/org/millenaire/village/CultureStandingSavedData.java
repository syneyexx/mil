package org.millenaire.village;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.config.MillenaireServerConfig;

public final class CultureStandingSavedData extends SavedData {
   public static final String DATA_NAME = "millenaire_culture_standing";
   public static final int DEFAULT_CROSS_CULTURE_STANDING = -20;
   public static final int ALLIED_THRESHOLD = 70;
   private static final int MIN_STANDING = -100;
   private static final int MAX_STANDING = 100;

   private final Map<String, Integer> standings = new HashMap<>();
   private final Set<String> warPairs = new HashSet<>();
   private final List<CultureDiplomacyLogEntry> diplomacyLog = new ArrayList<>();
   private long lastProcessedDay = -1L;

   private CultureStandingSavedData() {
   }

   public static Factory<CultureStandingSavedData> factory() {
      return new Factory<>(CultureStandingSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static CultureStandingSavedData get(ServerLevel level) {
      return (CultureStandingSavedData)level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public long getLastProcessedDay() {
      return this.lastProcessedDay;
   }

   public void setLastProcessedDay(long day) {
      if (this.lastProcessedDay != day) {
         this.lastProcessedDay = day;
         this.setDirty();
      }
   }

   public static String pairKey(ResourceLocation cultureA, ResourceLocation cultureB) {
      if (cultureA.equals(cultureB)) {
         return cultureA.toString();
      }

      String idA = cultureA.toString();
      String idB = cultureB.toString();
      return idA.compareTo(idB) <= 0 ? idA + "|" + idB : idB + "|" + idA;
   }

   public int getStanding(ResourceLocation cultureA, ResourceLocation cultureB) {
      if (cultureA.equals(cultureB)) {
         return MAX_STANDING;
      }

      return this.standings.getOrDefault(pairKey(cultureA, cultureB), DEFAULT_CROSS_CULTURE_STANDING);
   }

   public void setStanding(ResourceLocation cultureA, ResourceLocation cultureB, int value) {
      if (cultureA.equals(cultureB)) {
         return;
      }

      int clamped = Math.max(MIN_STANDING, Math.min(MAX_STANDING, value));
      this.standings.put(pairKey(cultureA, cultureB), clamped);
      this.setDirty();
   }

   public int adjustStanding(ResourceLocation cultureA, ResourceLocation cultureB, int delta) {
      if (cultureA.equals(cultureB)) {
         return MAX_STANDING;
      }

      int next = getStanding(cultureA, cultureB) + delta;
      next = Math.max(MIN_STANDING, Math.min(MAX_STANDING, next));
      this.standings.put(pairKey(cultureA, cultureB), next);
      this.setDirty();
      return next;
   }

   public boolean isAtWar(ResourceLocation cultureA, ResourceLocation cultureB) {
      if (cultureA.equals(cultureB)) {
         return false;
      }

      return this.warPairs.contains(pairKey(cultureA, cultureB));
   }

   public boolean isAllied(ResourceLocation cultureA, ResourceLocation cultureB) {
      return !cultureA.equals(cultureB) && getStanding(cultureA, cultureB) >= ALLIED_THRESHOLD;
   }

   public void declareWar(ResourceLocation cultureA, ResourceLocation cultureB) {
      if (cultureA.equals(cultureB)) {
         return;
      }

      String key = pairKey(cultureA, cultureB);
      this.standings.put(key, MIN_STANDING);
      this.warPairs.add(key);
      this.setDirty();
   }

   public void endWar(ResourceLocation cultureA, ResourceLocation cultureB) {
      if (cultureA.equals(cultureB)) {
         return;
      }

      String key = pairKey(cultureA, cultureB);
      this.warPairs.remove(key);
      this.standings.put(key, 0);
      this.setDirty();
   }

   public Set<String> getWarPairKeys() {
      return Set.copyOf(this.warPairs);
   }

   public List<CultureStandingEntry> getAllStandings() {
      List<CultureStandingEntry> result = new ArrayList<>();

      for (Entry<String, Integer> e : this.standings.entrySet()) {
         String[] parts = e.getKey().split("\\|", 2);
         if (parts.length == 2) {
            ResourceLocation a = ResourceLocation.parse(parts[0]);
            ResourceLocation b = ResourceLocation.parse(parts[1]);
            result.add(new CultureStandingEntry(a, b, e.getValue(), this.warPairs.contains(e.getKey())));
         }
      }

      for (String warKey : this.warPairs) {
         if (!this.standings.containsKey(warKey)) {
            String[] parts = warKey.split("\\|", 2);
            if (parts.length == 2) {
               result.add(
                  new CultureStandingEntry(ResourceLocation.parse(parts[0]), ResourceLocation.parse(parts[1]), MIN_STANDING, true)
               );
            }
         }
      }

      return result;
   }

   public int warAutoThreshold() {
      return MillenaireServerConfig.SERVER.cultureWarAutoThreshold.get();
   }

   public void addDiplomacyLog(CultureDiplomacyLogEntry entry, int maxEntries) {
      this.diplomacyLog.add(entry);
      while (this.diplomacyLog.size() > maxEntries) {
         this.diplomacyLog.remove(0);
      }
      this.setDirty();
   }

   public List<CultureDiplomacyLogEntry> getDiplomacyLog() {
      return List.copyOf(this.diplomacyLog);
   }

   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putLong("lastProcessedDay", this.lastProcessedDay);
      CompoundTag standingTag = new CompoundTag();

      for (Entry<String, Integer> e : this.standings.entrySet()) {
         standingTag.putInt(e.getKey(), e.getValue());
      }

      tag.put("standings", standingTag);
      ListTag wars = new ListTag();

      for (String key : this.warPairs) {
         wars.add(net.minecraft.nbt.StringTag.valueOf(key));
      }

      tag.put("wars", wars);
      ListTag logTag = new ListTag();
      for (CultureDiplomacyLogEntry entry : this.diplomacyLog) {
         logTag.add(entry.save());
      }
      tag.put("diplomacyLog", logTag);
      return tag;
   }

   private static CultureStandingSavedData load(CompoundTag tag) {
      CultureStandingSavedData data = new CultureStandingSavedData();
      data.lastProcessedDay = tag.getLong("lastProcessedDay");
      if (tag.contains("standings", Tag.TAG_COMPOUND)) {
         CompoundTag standingTag = tag.getCompound("standings");

         for (String key : standingTag.getAllKeys()) {
            data.standings.put(key, standingTag.getInt(key));
         }
      }

      if (tag.contains("wars", Tag.TAG_LIST)) {
         ListTag wars = tag.getList("wars", Tag.TAG_STRING);

         for (int i = 0; i < wars.size(); i++) {
            data.warPairs.add(wars.getString(i));
         }
      }

      if (tag.contains("diplomacyLog", Tag.TAG_LIST)) {
         ListTag logTag = tag.getList("diplomacyLog", Tag.TAG_COMPOUND);
         for (int i = 0; i < logTag.size(); i++) {
            data.diplomacyLog.add(CultureDiplomacyLogEntry.load(logTag.getCompound(i)));
         }
      }

      return data;
   }

   public record CultureStandingEntry(ResourceLocation cultureA, ResourceLocation cultureB, int standing, boolean atWar) {
   }
}
