package org.millenaire.companion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.village.VillageId;

/**
 * Persistent bond between a player and their companion (entity may be dismissed).
 */
public final class CompanionBond {
   public static final int MAX_PREFERENCES = 24;
   public static final int MAX_ACTION_RESULTS = 12;
   public static final int MAX_TRAVEL = 40;
   public static final int MAX_EVENTS = 40;

   private final UUID playerId;
   @Nullable
   private UUID companionEntityId;
   @Nullable
   private ResourceLocation villagerTypeId;
   @Nullable
   private VillageId villageId;
   @Nullable
   private ResourceLocation cultureId;
   private CompanionStance stance = CompanionStance.FREE_ROAM;
   private long bondedGameDay;
   private boolean dismissed;
   private final List<String> preferences = new ArrayList<>();
   private final List<String> recentActionResults = new ArrayList<>();
   private final List<String> travelLines = new ArrayList<>();
   private final List<String> sharedEvents = new ArrayList<>();
   private String lastBiome = "";
   private String lastDimension = "";
   @Nullable
   private String lastVillageName;

   public CompanionBond(UUID playerId) {
      this.playerId = playerId;
   }

   public UUID playerId() {
      return this.playerId;
   }

   @Nullable
   public UUID companionEntityId() {
      return this.companionEntityId;
   }

   public void setCompanionEntityId(@Nullable UUID id) {
      this.companionEntityId = id;
   }

   @Nullable
   public ResourceLocation villagerTypeId() {
      return this.villagerTypeId;
   }

   public void setVillagerTypeId(@Nullable ResourceLocation id) {
      this.villagerTypeId = id;
   }

   @Nullable
   public VillageId villageId() {
      return this.villageId;
   }

   public void setVillageId(@Nullable VillageId id) {
      this.villageId = id;
   }

   @Nullable
   public ResourceLocation cultureId() {
      return this.cultureId;
   }

   public void setCultureId(@Nullable ResourceLocation id) {
      this.cultureId = id;
   }

   public CompanionStance stance() {
      return this.stance;
   }

   public void setStance(CompanionStance stance) {
      this.stance = stance == null ? CompanionStance.FREE_ROAM : stance;
   }

   public long bondedGameDay() {
      return this.bondedGameDay;
   }

   public void setBondedGameDay(long day) {
      this.bondedGameDay = day;
   }

   public boolean dismissed() {
      return this.dismissed;
   }

   public void setDismissed(boolean dismissed) {
      this.dismissed = dismissed;
   }

   public List<String> preferences() {
      return this.preferences;
   }

   public List<String> recentActionResults() {
      return this.recentActionResults;
   }

   public List<String> travelLines() {
      return this.travelLines;
   }

   public List<String> sharedEvents() {
      return this.sharedEvents;
   }

   public String lastBiome() {
      return this.lastBiome;
   }

   public void setLastBiome(String biome) {
      this.lastBiome = biome == null ? "" : biome;
   }

   public String lastDimension() {
      return this.lastDimension;
   }

   public void setLastDimension(String dim) {
      this.lastDimension = dim == null ? "" : dim;
   }

   @Nullable
   public String lastVillageName() {
      return this.lastVillageName;
   }

   public void setLastVillageName(@Nullable String name) {
      this.lastVillageName = name;
   }

   public void addPreference(String line) {
      pushCapped(this.preferences, sanitize(line, 160), MAX_PREFERENCES);
   }

   public void addActionResult(String line) {
      pushCapped(this.recentActionResults, sanitize(line, 180), MAX_ACTION_RESULTS);
   }

   public void addTravelLine(String line) {
      pushCapped(this.travelLines, sanitize(line, 160), MAX_TRAVEL);
   }

   public void addSharedEvent(String line) {
      pushCapped(this.sharedEvents, sanitize(line, 160), MAX_EVENTS);
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("player", this.playerId);
      if (this.companionEntityId != null) {
         tag.putUUID("entity", this.companionEntityId);
      }
      if (this.villagerTypeId != null) {
         tag.putString("villagerType", this.villagerTypeId.toString());
      }
      if (this.villageId != null) {
         tag.putUUID("village", this.villageId.uuid());
      }
      if (this.cultureId != null) {
         tag.putString("culture", this.cultureId.toString());
      }
      tag.putString("stance", this.stance.name());
      tag.putLong("bondedDay", this.bondedGameDay);
      tag.putBoolean("dismissed", this.dismissed);
      tag.putString("lastBiome", this.lastBiome);
      tag.putString("lastDimension", this.lastDimension);
      if (this.lastVillageName != null) {
         tag.putString("lastVillageName", this.lastVillageName);
      }
      tag.put("preferences", stringList(this.preferences));
      tag.put("actionResults", stringList(this.recentActionResults));
      tag.put("travel", stringList(this.travelLines));
      tag.put("events", stringList(this.sharedEvents));
      return tag;
   }

   public static CompanionBond load(CompoundTag tag) {
      UUID player = tag.getUUID("player");
      CompanionBond bond = new CompanionBond(player);
      if (tag.hasUUID("entity")) {
         bond.companionEntityId = tag.getUUID("entity");
      }
      if (tag.contains("villagerType", Tag.TAG_STRING)) {
         bond.villagerTypeId = ResourceLocation.tryParse(tag.getString("villagerType"));
      }
      if (tag.hasUUID("village")) {
         bond.villageId = new VillageId(tag.getUUID("village"));
      }
      if (tag.contains("culture", Tag.TAG_STRING)) {
         bond.cultureId = ResourceLocation.tryParse(tag.getString("culture"));
      }
      bond.stance = CompanionStance.parse(tag.getString("stance"), CompanionStance.FREE_ROAM);
      bond.bondedGameDay = tag.getLong("bondedDay");
      bond.dismissed = tag.getBoolean("dismissed");
      bond.lastBiome = tag.getString("lastBiome");
      bond.lastDimension = tag.getString("lastDimension");
      if (tag.contains("lastVillageName", Tag.TAG_STRING)) {
         bond.lastVillageName = tag.getString("lastVillageName");
      }
      readStringList(tag.getList("preferences", Tag.TAG_STRING), bond.preferences, MAX_PREFERENCES);
      readStringList(tag.getList("actionResults", Tag.TAG_STRING), bond.recentActionResults, MAX_ACTION_RESULTS);
      readStringList(tag.getList("travel", Tag.TAG_STRING), bond.travelLines, MAX_TRAVEL);
      readStringList(tag.getList("events", Tag.TAG_STRING), bond.sharedEvents, MAX_EVENTS);
      return bond;
   }

   private static void pushCapped(List<String> list, String line, int max) {
      if (line.isBlank()) {
         return;
      }
      list.add(line);
      while (list.size() > max) {
         list.remove(0);
      }
   }

   private static String sanitize(String line, int max) {
      if (line == null) {
         return "";
      }
      String cleaned = line.replace('\n', ' ').replace('\r', ' ').trim();
      if (cleaned.length() > max) {
         return cleaned.substring(0, max);
      }
      return cleaned;
   }

   private static ListTag stringList(List<String> lines) {
      ListTag list = new ListTag();
      for (String line : lines) {
         list.add(StringTag.valueOf(line));
      }
      return list;
   }

   private static void readStringList(ListTag tag, List<String> out, int max) {
      out.clear();
      for (int i = 0; i < tag.size() && out.size() < max; i++) {
         String s = tag.getString(i);
         if (s != null && !s.isBlank()) {
            out.add(s);
         }
      }
   }
}
