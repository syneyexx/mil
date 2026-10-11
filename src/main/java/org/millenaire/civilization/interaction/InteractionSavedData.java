package org.millenaire.civilization.interaction;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.slf4j.Logger;

/**
 * World-scoped persistence for civilization interaction state.
 */
public final class InteractionSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int CURRENT_VERSION = 1;
   public static final String DATA_NAME = "millenaire_civ_interaction";

   private final Map<String, TradeCorridorRecord> corridors = new LinkedHashMap<>();
   private final List<TheaterStory> theaterStories = new ArrayList<>();
   private final List<CaravanCharter> caravanCharters = new ArrayList<>();
   private final List<FestivalWindow> festivals = new ArrayList<>();
   private final Map<String, ClaimHeatEntry> claimHeat = new LinkedHashMap<>();
   private final List<CallToArmsOffer> callToArms = new ArrayList<>();
   private final List<ProtectorateLink> protectorates = new ArrayList<>();
   private final List<MercenaryContract> mercenaries = new ArrayList<>();
   private final List<FosterageBond> fosterage = new ArrayList<>();
   private final List<JointWorkProject> jointProjects = new ArrayList<>();
   private final List<EnvoyMission> envoys = new ArrayList<>();
   private final List<RumorEntry> rumors = new ArrayList<>();
   private final Map<String, Integer> legitimacy = new LinkedHashMap<>();
   private final Map<String, Integer> diasporaAssimilation = new LinkedHashMap<>();
   private final Map<String, Integer> refugeeAccepted = new LinkedHashMap<>();
   private final Map<String, Boolean> marketDayFlags = new LinkedHashMap<>();
   private long lastFestivalAttemptGameTime;
   private long lastTickerPassGameTime;

   private InteractionSavedData() {
   }

   public static Factory<InteractionSavedData> factory() {
      return new Factory<>(InteractionSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static InteractionSavedData get(ServerLevel level) {
      return level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public Map<String, TradeCorridorRecord> corridors() {
      return Collections.unmodifiableMap(this.corridors);
   }

   public List<TheaterStory> theaterStories() {
      return Collections.unmodifiableList(this.theaterStories);
   }

   public List<CaravanCharter> caravanCharters() {
      return Collections.unmodifiableList(this.caravanCharters);
   }

   public List<FestivalWindow> festivals() {
      return Collections.unmodifiableList(this.festivals);
   }

   public Map<String, ClaimHeatEntry> claimHeat() {
      return Collections.unmodifiableMap(this.claimHeat);
   }

   public List<CallToArmsOffer> callToArms() {
      return Collections.unmodifiableList(this.callToArms);
   }

   public List<ProtectorateLink> protectorates() {
      return Collections.unmodifiableList(this.protectorates);
   }

   public List<MercenaryContract> mercenaries() {
      return Collections.unmodifiableList(this.mercenaries);
   }

   public List<FosterageBond> fosterage() {
      return Collections.unmodifiableList(this.fosterage);
   }

   public List<JointWorkProject> jointProjects() {
      return Collections.unmodifiableList(this.jointProjects);
   }

   public List<EnvoyMission> envoys() {
      return Collections.unmodifiableList(this.envoys);
   }

   public List<RumorEntry> rumors() {
      return Collections.unmodifiableList(this.rumors);
   }

   public Map<String, Integer> legitimacy() {
      return Collections.unmodifiableMap(this.legitimacy);
   }

   public Map<String, Integer> diasporaAssimilation() {
      return Collections.unmodifiableMap(this.diasporaAssimilation);
   }

   public Map<String, Integer> refugeeAccepted() {
      return Collections.unmodifiableMap(this.refugeeAccepted);
   }

   public Map<String, Boolean> marketDayFlags() {
      return Collections.unmodifiableMap(this.marketDayFlags);
   }

   public long lastFestivalAttemptGameTime() {
      return this.lastFestivalAttemptGameTime;
   }

   public void setLastFestivalAttemptGameTime(long gameTime) {
      this.lastFestivalAttemptGameTime = gameTime;
      this.setDirty();
   }

   public long lastTickerPassGameTime() {
      return this.lastTickerPassGameTime;
   }

   public void setLastTickerPassGameTime(long gameTime) {
      this.lastTickerPassGameTime = gameTime;
      this.setDirty();
   }

   public void putCorridor(TradeCorridorRecord record) {
      this.corridors.put(record.pairKey(), record);
      this.setDirty();
   }

   @Nullable
   public TradeCorridorRecord getCorridor(String pairKey) {
      return this.corridors.get(pairKey);
   }

   public void addTheaterStory(TheaterStory story) {
      this.theaterStories.add(0, story);
      while (this.theaterStories.size() > InteractionConfig.MAX_THEATER_STORIES) {
         this.theaterStories.remove(this.theaterStories.size() - 1);
      }
      this.setDirty();
   }

   public void addCaravanCharter(CaravanCharter charter) {
      this.caravanCharters.add(charter);
      this.setDirty();
   }

   public void setCaravanCharters(List<CaravanCharter> next) {
      this.caravanCharters.clear();
      this.caravanCharters.addAll(next);
      this.setDirty();
   }

   public void addFestival(FestivalWindow window) {
      this.festivals.add(window);
      this.setDirty();
   }

   public void setFestivals(List<FestivalWindow> next) {
      this.festivals.clear();
      this.festivals.addAll(next);
      this.setDirty();
   }

   public void putClaimHeat(ClaimHeatEntry entry) {
      this.claimHeat.put(entry.pairKey(), entry);
      this.setDirty();
   }

   public void addCallToArms(CallToArmsOffer offer) {
      this.callToArms.add(offer);
      this.setDirty();
   }

   public void setCallToArms(List<CallToArmsOffer> next) {
      this.callToArms.clear();
      this.callToArms.addAll(next);
      this.setDirty();
   }

   public void addProtectorate(ProtectorateLink link) {
      this.protectorates.add(link);
      this.setDirty();
   }

   public void setProtectorates(List<ProtectorateLink> next) {
      this.protectorates.clear();
      this.protectorates.addAll(next);
      this.setDirty();
   }

   public void addMercenary(MercenaryContract contract) {
      this.mercenaries.add(contract);
      this.setDirty();
   }

   public void setMercenaries(List<MercenaryContract> next) {
      this.mercenaries.clear();
      this.mercenaries.addAll(next);
      this.setDirty();
   }

   public void addFosterage(FosterageBond bond) {
      this.fosterage.add(bond);
      this.setDirty();
   }

   public void setFosterage(List<FosterageBond> next) {
      this.fosterage.clear();
      this.fosterage.addAll(next);
      this.setDirty();
   }

   public void addJointProject(JointWorkProject project) {
      this.jointProjects.add(project);
      this.setDirty();
   }

   public void setJointProjects(List<JointWorkProject> next) {
      this.jointProjects.clear();
      this.jointProjects.addAll(next);
      this.setDirty();
   }

   public void addEnvoy(EnvoyMission mission) {
      this.envoys.add(mission);
      this.setDirty();
   }

   public void setEnvoys(List<EnvoyMission> next) {
      this.envoys.clear();
      this.envoys.addAll(next);
      this.setDirty();
   }

   public void addRumor(RumorEntry rumor) {
      this.rumors.add(0, rumor);
      while (this.rumors.size() > InteractionConfig.MAX_RUMORS) {
         this.rumors.remove(this.rumors.size() - 1);
      }
      this.setDirty();
   }

   public void setRumors(List<RumorEntry> next) {
      this.rumors.clear();
      this.rumors.addAll(next);
      this.setDirty();
   }

   public int getLegitimacy(String factionId) {
      return this.legitimacy.getOrDefault(factionId, 0);
   }

   public void setLegitimacy(String factionId, int value) {
      int clamped = Math.max(InteractionConfig.LEGITIMACY_MIN, Math.min(InteractionConfig.LEGITIMACY_MAX, value));
      this.legitimacy.put(factionId, clamped);
      this.setDirty();
   }

   public int getDiasporaAssimilation(String key) {
      return this.diasporaAssimilation.getOrDefault(key, 0);
   }

   public void setDiasporaAssimilation(String key, int value) {
      this.diasporaAssimilation.put(key, Math.max(0, value));
      this.setDirty();
   }

   public int getRefugeeAccepted(String warKey) {
      return this.refugeeAccepted.getOrDefault(warKey, 0);
   }

   public void setRefugeeAccepted(String warKey, int value) {
      this.refugeeAccepted.put(warKey, Math.max(0, value));
      this.setDirty();
   }

   public boolean isMarketDay(String villageId) {
      return Boolean.TRUE.equals(this.marketDayFlags.get(villageId));
   }

   public void setMarketDay(String villageId, boolean value) {
      if (value) {
         this.marketDayFlags.put(villageId, true);
      } else {
         this.marketDayFlags.remove(villageId);
      }
      this.setDirty();
   }

   public void clearMarketDays() {
      if (!this.marketDayFlags.isEmpty()) {
         this.marketDayFlags.clear();
         this.setDirty();
      }
   }

   @Override
   public CompoundTag save(CompoundTag root, Provider registries) {
      root.putInt("version", CURRENT_VERSION);
      root.putLong("last_festival_attempt", this.lastFestivalAttemptGameTime);
      root.putLong("last_ticker_pass", this.lastTickerPassGameTime);

      ListTag corridorList = new ListTag();
      for (TradeCorridorRecord c : this.corridors.values()) {
         CompoundTag tag = new CompoundTag();
         tag.putString("source_faction", c.sourceFaction());
         tag.putString("target_faction", c.targetFaction());
         tag.putString("source_village", c.sourceVillage());
         tag.putString("target_village", c.targetVillage());
         tag.putInt("deliveries", c.successfulDeliveries());
         tag.putBoolean("recognized", c.recognized());
         tag.putBoolean("disrupted", c.disrupted());
         if (c.nameKey() != null) {
            tag.putString("name_key", c.nameKey());
         }
         corridorList.add(tag);
      }
      root.put("corridors", corridorList);

      ListTag theaterList = new ListTag();
      for (TheaterStory s : this.theaterStories) {
         CompoundTag tag = new CompoundTag();
         tag.putString("type", s.type());
         tag.putString("summary_key", s.summaryKey());
         tag.putString("faction_a", s.factionA());
         if (s.factionB() != null) {
            tag.putString("faction_b", s.factionB());
         }
         tag.putInt("severity", s.severity());
         tag.putLong("game_time", s.gameTime());
         theaterList.add(tag);
      }
      root.put("theater", theaterList);

      root.put("charters", saveCharters());
      root.put("festivals", saveFestivals());
      root.put("claim_heat", saveClaimHeat());
      root.put("call_to_arms", saveCallToArms());
      root.put("protectorates", saveProtectorates());
      root.put("mercenaries", saveMercenaries());
      root.put("fosterage", saveFosterage());
      root.put("joint_projects", saveJointProjects());
      root.put("envoys", saveEnvoys());
      root.put("rumors", saveRumors());
      root.put("legitimacy", saveStringIntMap(this.legitimacy));
      root.put("diaspora", saveStringIntMap(this.diasporaAssimilation));
      root.put("refugees", saveStringIntMap(this.refugeeAccepted));
      root.put("market_days", saveStringBoolMap(this.marketDayFlags));

      LOGGER.debug("[Millenaire] Saved interaction data ({} corridors)", this.corridors.size());
      return root;
   }

   private ListTag saveCharters() {
      ListTag list = new ListTag();
      for (CaravanCharter c : this.caravanCharters) {
         CompoundTag tag = new CompoundTag();
         tag.putString("player_faction", c.playerFaction());
         tag.putString("partner_faction", c.partnerFaction());
         if (c.exportItemId() != null) {
            tag.putString("export_item", c.exportItemId());
         }
         tag.putBoolean("active", c.active());
         tag.putLong("created", c.createdAt());
         list.add(tag);
      }
      return list;
   }

   private ListTag saveFestivals() {
      ListTag list = new ListTag();
      for (FestivalWindow f : this.festivals) {
         CompoundTag tag = new CompoundTag();
         tag.putString("culture", f.cultureId());
         tag.putString("faction", f.factionId());
         tag.putLong("start", f.startGameTime());
         tag.putLong("end", f.endGameTime());
         if (f.hostVillage() != null) {
            tag.putString("host_village", f.hostVillage());
         }
         list.add(tag);
      }
      return list;
   }

   private ListTag saveClaimHeat() {
      ListTag list = new ListTag();
      for (ClaimHeatEntry e : this.claimHeat.values()) {
         CompoundTag tag = new CompoundTag();
         tag.putString("faction_a", e.factionA());
         tag.putString("faction_b", e.factionB());
         tag.putInt("heat", e.heat());
         list.add(tag);
      }
      return list;
   }

   private ListTag saveCallToArms() {
      ListTag list = new ListTag();
      for (CallToArmsOffer o : this.callToArms) {
         CompoundTag tag = new CompoundTag();
         tag.putString("ally", o.allyFaction());
         tag.putString("enemy", o.enemyFaction());
         tag.putString("player", o.targetPlayerFaction());
         tag.putLong("expires", o.expiresAt());
         tag.putString("status", o.status().name());
         list.add(tag);
      }
      return list;
   }

   private ListTag saveProtectorates() {
      ListTag list = new ListTag();
      for (ProtectorateLink p : this.protectorates) {
         CompoundTag tag = new CompoundTag();
         tag.putString("suzerain", p.suzerain());
         tag.putString("vassal", p.vassal());
         tag.putLong("tribute_due", p.tributeDueGameTime());
         tag.putBoolean("active", p.active());
         list.add(tag);
      }
      return list;
   }

   private ListTag saveMercenaries() {
      ListTag list = new ListTag();
      for (MercenaryContract m : this.mercenaries) {
         CompoundTag tag = new CompoundTag();
         tag.putString("employer", m.employerFaction());
         tag.putString("provider", m.providerFaction());
         tag.putLong("expires", m.expiresAt());
         tag.putBoolean("active", m.active());
         list.add(tag);
      }
      return list;
   }

   private ListTag saveFosterage() {
      ListTag list = new ListTag();
      for (FosterageBond b : this.fosterage) {
         CompoundTag tag = new CompoundTag();
         tag.putString("host", b.hostFaction());
         tag.putString("guest", b.guestFaction());
         if (b.villagerUuid() != null) {
            tag.putString("villager", b.villagerUuid());
         }
         tag.putLong("expires", b.expiresAt());
         tag.putString("kind", b.kind().name());
         list.add(tag);
      }
      return list;
   }

   private ListTag saveJointProjects() {
      ListTag list = new ListTag();
      for (JointWorkProject p : this.jointProjects) {
         CompoundTag tag = new CompoundTag();
         tag.putString("id", p.id());
         tag.putString("faction_a", p.factionA());
         tag.putString("faction_b", p.factionB());
         tag.putString("kind", p.kind().name());
         tag.putInt("progress", p.progress());
         tag.putString("status", p.status().name());
         list.add(tag);
      }
      return list;
   }

   private ListTag saveEnvoys() {
      ListTag list = new ListTag();
      for (EnvoyMission e : this.envoys) {
         CompoundTag tag = new CompoundTag();
         tag.putString("culture", e.cultureFaction());
         tag.putString("player", e.playerFaction());
         tag.putString("agenda", e.agenda().name());
         tag.putLong("expires", e.expiresAt());
         tag.putBoolean("pending", e.pending());
         list.add(tag);
      }
      return list;
   }

   private ListTag saveRumors() {
      ListTag list = new ListTag();
      for (RumorEntry r : this.rumors) {
         CompoundTag tag = new CompoundTag();
         tag.putString("type", r.type());
         tag.putString("summary", r.summary());
         tag.putString("about", r.aboutFactionId());
         tag.putLong("expires", r.expiresAt());
         tag.putFloat("reliability", r.reliability());
         list.add(tag);
      }
      return list;
   }

   private static CompoundTag saveStringIntMap(Map<String, Integer> map) {
      CompoundTag tag = new CompoundTag();
      for (Map.Entry<String, Integer> e : map.entrySet()) {
         tag.putInt(e.getKey(), e.getValue());
      }
      return tag;
   }

   private static CompoundTag saveStringBoolMap(Map<String, Boolean> map) {
      CompoundTag tag = new CompoundTag();
      for (Map.Entry<String, Boolean> e : map.entrySet()) {
         tag.putBoolean(e.getKey(), Boolean.TRUE.equals(e.getValue()));
      }
      return tag;
   }

   private static InteractionSavedData load(CompoundTag root) {
      InteractionSavedData data = new InteractionSavedData();
      int version = root.contains("version") ? root.getInt("version") : 0;
      if (version > CURRENT_VERSION) {
         LOGGER.warn("[Millenaire] InteractionSavedData version {} newer than supported {}", version, CURRENT_VERSION);
      }
      data.lastFestivalAttemptGameTime = root.getLong("last_festival_attempt");
      data.lastTickerPassGameTime = root.getLong("last_ticker_pass");

      ListTag corridorList = root.getList("corridors", Tag.TAG_COMPOUND);
      for (int i = 0; i < corridorList.size(); i++) {
         CompoundTag tag = corridorList.getCompound(i);
         TradeCorridorRecord record = new TradeCorridorRecord(
            tag.getString("source_faction"),
            tag.getString("target_faction"),
            tag.getString("source_village"),
            tag.getString("target_village"),
            tag.getInt("deliveries"),
            tag.getBoolean("recognized"),
            tag.getBoolean("disrupted"),
            tag.contains("name_key") ? tag.getString("name_key") : null
         );
         data.corridors.put(record.pairKey(), record);
      }

      ListTag theaterList = root.getList("theater", Tag.TAG_COMPOUND);
      for (int i = 0; i < theaterList.size(); i++) {
         CompoundTag tag = theaterList.getCompound(i);
         data.theaterStories.add(new TheaterStory(
            tag.getString("type"),
            tag.getString("summary_key"),
            tag.getString("faction_a"),
            tag.contains("faction_b") ? tag.getString("faction_b") : null,
            tag.getInt("severity"),
            tag.getLong("game_time")
         ));
      }

      loadCharters(data, root.getList("charters", Tag.TAG_COMPOUND));
      loadFestivals(data, root.getList("festivals", Tag.TAG_COMPOUND));
      loadClaimHeat(data, root.getList("claim_heat", Tag.TAG_COMPOUND));
      loadCallToArms(data, root.getList("call_to_arms", Tag.TAG_COMPOUND));
      loadProtectorates(data, root.getList("protectorates", Tag.TAG_COMPOUND));
      loadMercenaries(data, root.getList("mercenaries", Tag.TAG_COMPOUND));
      loadFosterage(data, root.getList("fosterage", Tag.TAG_COMPOUND));
      loadJointProjects(data, root.getList("joint_projects", Tag.TAG_COMPOUND));
      loadEnvoys(data, root.getList("envoys", Tag.TAG_COMPOUND));
      loadRumors(data, root.getList("rumors", Tag.TAG_COMPOUND));
      loadStringIntMap(data.legitimacy, root.getCompound("legitimacy"));
      loadStringIntMap(data.diasporaAssimilation, root.getCompound("diaspora"));
      loadStringIntMap(data.refugeeAccepted, root.getCompound("refugees"));
      loadStringBoolMap(data.marketDayFlags, root.getCompound("market_days"));
      return data;
   }

   private static void loadCharters(InteractionSavedData data, ListTag list) {
      for (int i = 0; i < list.size(); i++) {
         CompoundTag tag = list.getCompound(i);
         data.caravanCharters.add(new CaravanCharter(
            tag.getString("player_faction"),
            tag.getString("partner_faction"),
            tag.contains("export_item") ? tag.getString("export_item") : null,
            tag.getBoolean("active"),
            tag.getLong("created")
         ));
      }
   }

   private static void loadFestivals(InteractionSavedData data, ListTag list) {
      for (int i = 0; i < list.size(); i++) {
         CompoundTag tag = list.getCompound(i);
         data.festivals.add(new FestivalWindow(
            tag.getString("culture"),
            tag.getString("faction"),
            tag.getLong("start"),
            tag.getLong("end"),
            tag.contains("host_village") ? tag.getString("host_village") : null
         ));
      }
   }

   private static void loadClaimHeat(InteractionSavedData data, ListTag list) {
      for (int i = 0; i < list.size(); i++) {
         CompoundTag tag = list.getCompound(i);
         ClaimHeatEntry entry = new ClaimHeatEntry(tag.getString("faction_a"), tag.getString("faction_b"), tag.getInt("heat"));
         data.claimHeat.put(entry.pairKey(), entry);
      }
   }

   private static void loadCallToArms(InteractionSavedData data, ListTag list) {
      for (int i = 0; i < list.size(); i++) {
         CompoundTag tag = list.getCompound(i);
         CallToArmsOffer.Status status;
         try {
            status = CallToArmsOffer.Status.valueOf(tag.getString("status"));
         } catch (IllegalArgumentException e) {
            status = CallToArmsOffer.Status.EXPIRED;
         }
         data.callToArms.add(new CallToArmsOffer(
            tag.getString("ally"),
            tag.getString("enemy"),
            tag.getString("player"),
            tag.getLong("expires"),
            status
         ));
      }
   }

   private static void loadProtectorates(InteractionSavedData data, ListTag list) {
      for (int i = 0; i < list.size(); i++) {
         CompoundTag tag = list.getCompound(i);
         data.protectorates.add(new ProtectorateLink(
            tag.getString("suzerain"),
            tag.getString("vassal"),
            tag.getLong("tribute_due"),
            tag.getBoolean("active")
         ));
      }
   }

   private static void loadMercenaries(InteractionSavedData data, ListTag list) {
      for (int i = 0; i < list.size(); i++) {
         CompoundTag tag = list.getCompound(i);
         data.mercenaries.add(new MercenaryContract(
            tag.getString("employer"),
            tag.getString("provider"),
            tag.getLong("expires"),
            tag.getBoolean("active")
         ));
      }
   }

   private static void loadFosterage(InteractionSavedData data, ListTag list) {
      for (int i = 0; i < list.size(); i++) {
         CompoundTag tag = list.getCompound(i);
         FosterageBond.Kind kind;
         try {
            kind = FosterageBond.Kind.valueOf(tag.getString("kind"));
         } catch (IllegalArgumentException e) {
            kind = FosterageBond.Kind.GUEST;
         }
         data.fosterage.add(new FosterageBond(
            tag.getString("host"),
            tag.getString("guest"),
            tag.contains("villager") ? tag.getString("villager") : null,
            tag.getLong("expires"),
            kind
         ));
      }
   }

   private static void loadJointProjects(InteractionSavedData data, ListTag list) {
      for (int i = 0; i < list.size(); i++) {
         CompoundTag tag = list.getCompound(i);
         JointWorkProject.Kind kind;
         JointWorkProject.Status status;
         try {
            kind = JointWorkProject.Kind.valueOf(tag.getString("kind"));
         } catch (IllegalArgumentException e) {
            kind = JointWorkProject.Kind.ROAD;
         }
         try {
            status = JointWorkProject.Status.valueOf(tag.getString("status"));
         } catch (IllegalArgumentException e) {
            status = JointWorkProject.Status.CANCELLED;
         }
         data.jointProjects.add(new JointWorkProject(
            tag.getString("id"),
            tag.getString("faction_a"),
            tag.getString("faction_b"),
            kind,
            tag.getInt("progress"),
            status
         ));
      }
   }

   private static void loadEnvoys(InteractionSavedData data, ListTag list) {
      for (int i = 0; i < list.size(); i++) {
         CompoundTag tag = list.getCompound(i);
         EnvoyMission.Agenda agenda;
         try {
            agenda = EnvoyMission.Agenda.valueOf(tag.getString("agenda"));
         } catch (IllegalArgumentException e) {
            agenda = EnvoyMission.Agenda.RUMOR;
         }
         data.envoys.add(new EnvoyMission(
            tag.getString("culture"),
            tag.getString("player"),
            agenda,
            tag.getLong("expires"),
            tag.getBoolean("pending")
         ));
      }
   }

   private static void loadRumors(InteractionSavedData data, ListTag list) {
      for (int i = 0; i < list.size(); i++) {
         CompoundTag tag = list.getCompound(i);
         data.rumors.add(new RumorEntry(
            tag.getString("type"),
            tag.getString("summary"),
            tag.getString("about"),
            tag.getLong("expires"),
            tag.getFloat("reliability")
         ));
      }
   }

   private static void loadStringIntMap(Map<String, Integer> target, CompoundTag tag) {
      for (String key : tag.getAllKeys()) {
         target.put(key, tag.getInt(key));
      }
   }

   private static void loadStringBoolMap(Map<String, Boolean> target, CompoundTag tag) {
      for (String key : tag.getAllKeys()) {
         target.put(key, tag.getBoolean(key));
      }
   }
}
