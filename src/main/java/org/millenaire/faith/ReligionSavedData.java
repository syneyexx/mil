package org.millenaire.faith;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.village.VillageId;
import org.slf4j.Logger;

/**
 * Central religion persistence: faith instances, orders, chapters, memberships, rituals, travel.
 */
public class ReligionSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final int SCHEMA_VERSION = 1;
   private static final String DATA_NAME = "millenaire_religion";
   private static final int MAX_RITUAL_HISTORY = 64;
   private static final int MAX_TRAVEL_RECORDS = 128;
   private static final int MAX_CHAPTERS = 512;
   private static final int MAX_MEMBERSHIPS = 4096;

   private final Map<UUID, FaithInstance> faithInstances = new HashMap<>();
   private final Map<UUID, OrderInstance> orderInstances = new HashMap<>();
   private final Map<UUID, Chapter> chapters = new HashMap<>();
   /** villageId -> chapterId (at most one active chapter per village in v1). */
   private final Map<UUID, UUID> chapterByVillage = new HashMap<>();
   private final Map<String, Membership> memberships = new HashMap<>();
   private final Map<UUID, RitualInstance> activeRituals = new HashMap<>();
   private final List<CompoundTag> ritualHistory = new ArrayList<>();
   private final Map<UUID, TravelRecord> travelRecords = new HashMap<>();
   private long worldSessionId = UUID.randomUUID().getMostSignificantBits();
   private int contentGenerationSeen;

   private ReligionSavedData() {
   }

   public static Factory<ReligionSavedData> factory() {
      return new Factory<>(ReligionSavedData::new, (tag, regs) -> load(tag), null);
   }

   public static ReligionSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         overworld = level;
      }
      return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public long worldSessionId() {
      return this.worldSessionId;
   }

   public void bumpSession() {
      this.worldSessionId = UUID.randomUUID().getMostSignificantBits();
      this.setDirty();
   }

   public int contentGenerationSeen() {
      return this.contentGenerationSeen;
   }

   public void setContentGenerationSeen(int gen) {
      this.contentGenerationSeen = gen;
      this.setDirty();
   }

   public FaithInstance putFaith(FaithInstance inst) {
      this.faithInstances.put(inst.id(), inst);
      this.setDirty();
      return inst;
   }

   @Nullable
   public FaithInstance getFaith(UUID id) {
      return id == null ? null : this.faithInstances.get(id);
   }

   public Collection<FaithInstance> allFaiths() {
      return this.faithInstances.values();
   }

   public OrderInstance putOrder(OrderInstance inst) {
      this.orderInstances.put(inst.id(), inst);
      this.setDirty();
      return inst;
   }

   @Nullable
   public OrderInstance getOrder(UUID id) {
      return id == null ? null : this.orderInstances.get(id);
   }

   public Collection<OrderInstance> allOrders() {
      return this.orderInstances.values();
   }

   public boolean putChapter(Chapter chapter) {
      if (this.chapters.size() >= MAX_CHAPTERS && !this.chapters.containsKey(chapter.id())) {
         LOGGER.warn("[Religion] Chapter cap reached ({})", MAX_CHAPTERS);
         return false;
      }
      this.chapters.put(chapter.id(), chapter);
      this.chapterByVillage.put(chapter.villageId().uuid(), chapter.id());
      this.setDirty();
      return true;
   }

   @Nullable
   public Chapter getChapter(UUID id) {
      return id == null ? null : this.chapters.get(id);
   }

   @Nullable
   public Chapter getChapterForVillage(VillageId villageId) {
      if (villageId == null) {
         return null;
      }
      UUID chapterId = this.chapterByVillage.get(villageId.uuid());
      return chapterId == null ? null : this.chapters.get(chapterId);
   }

   public Collection<Chapter> allChapters() {
      return this.chapters.values();
   }

   public void removeChapter(UUID chapterId) {
      Chapter ch = this.chapters.remove(chapterId);
      if (ch != null) {
         this.chapterByVillage.remove(ch.villageId().uuid(), chapterId);
         this.memberships.entrySet().removeIf(e -> e.getValue().chapterId().equals(chapterId));
         this.activeRituals.entrySet().removeIf(e -> e.getValue().chapterId().equals(chapterId));
         this.setDirty();
      }
   }

   public boolean putMembership(Membership membership) {
      if (this.memberships.size() >= MAX_MEMBERSHIPS && !this.memberships.containsKey(membership.storageKey())) {
         LOGGER.warn("[Religion] Membership cap reached ({})", MAX_MEMBERSHIPS);
         return false;
      }
      this.memberships.put(membership.storageKey(), membership);
      this.setDirty();
      return true;
   }

   @Nullable
   public Membership getMembership(Membership.SubjectType type, UUID subjectId) {
      return this.memberships.get(Membership.keyFor(type, subjectId));
   }

   public void removeMembership(Membership.SubjectType type, UUID subjectId) {
      if (this.memberships.remove(Membership.keyFor(type, subjectId)) != null) {
         this.setDirty();
      }
   }

   public Collection<Membership> allMemberships() {
      return this.memberships.values();
   }

   public List<Membership> membershipsForChapter(UUID chapterId) {
      List<Membership> out = new ArrayList<>();
      for (Membership m : this.memberships.values()) {
         if (m.chapterId().equals(chapterId)) {
            out.add(m);
         }
      }
      return out;
   }

   public void putRitual(RitualInstance ritual) {
      this.activeRituals.put(ritual.id(), ritual);
      this.setDirty();
   }

   @Nullable
   public RitualInstance getRitual(UUID id) {
      return id == null ? null : this.activeRituals.get(id);
   }

   @Nullable
   public RitualInstance findActiveRitualForChapter(UUID chapterId) {
      for (RitualInstance r : this.activeRituals.values()) {
         if (r.chapterId().equals(chapterId) && !r.state().isTerminal()) {
            return r;
         }
      }
      return null;
   }

   public Collection<RitualInstance> allActiveRituals() {
      return this.activeRituals.values();
   }

   public void archiveRitual(RitualInstance ritual) {
      this.activeRituals.remove(ritual.id());
      this.ritualHistory.add(ritual.save());
      while (this.ritualHistory.size() > MAX_RITUAL_HISTORY) {
         this.ritualHistory.removeFirst();
      }
      this.setDirty();
   }

   public void putTravel(TravelRecord record) {
      if (this.travelRecords.size() >= MAX_TRAVEL_RECORDS && !this.travelRecords.containsKey(record.id())) {
         pruneOldestTravel();
      }
      this.travelRecords.put(record.id(), record);
      this.setDirty();
   }

   @Nullable
   public TravelRecord getTravel(UUID id) {
      return this.travelRecords.get(id);
   }

   public Collection<TravelRecord> allTravel() {
      return this.travelRecords.values();
   }

   public void pruneTravelOlderThan(long currentDay, int maxAgeDays) {
      Iterator<Map.Entry<UUID, TravelRecord>> it = this.travelRecords.entrySet().iterator();
      boolean dirty = false;
      while (it.hasNext()) {
         TravelRecord r = it.next().getValue();
         if (r.status() == TravelRecord.Status.COMPLETED
            || r.status() == TravelRecord.Status.FAILED
            || r.status() == TravelRecord.Status.EXPIRED) {
            if (currentDay - r.plannedDay() > maxAgeDays) {
               it.remove();
               dirty = true;
            }
         } else if (currentDay > r.completeByDay()) {
            r.setStatus(TravelRecord.Status.EXPIRED);
            dirty = true;
         }
      }
      if (dirty) {
         this.setDirty();
      }
   }

   private void pruneOldestTravel() {
      UUID oldest = null;
      long oldestDay = Long.MAX_VALUE;
      for (TravelRecord r : this.travelRecords.values()) {
         if (r.plannedDay() < oldestDay) {
            oldestDay = r.plannedDay();
            oldest = r.id();
         }
      }
      if (oldest != null) {
         this.travelRecords.remove(oldest);
      }
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      tag.putInt("version", SCHEMA_VERSION);
      tag.putLong("session", this.worldSessionId);
      tag.putInt("content_gen", this.contentGenerationSeen);

      ListTag faiths = new ListTag();
      for (FaithInstance f : this.faithInstances.values()) {
         faiths.add(f.save());
      }
      tag.put("faiths", faiths);

      ListTag orders = new ListTag();
      for (OrderInstance o : this.orderInstances.values()) {
         orders.add(o.save());
      }
      tag.put("orders", orders);

      ListTag chapterList = new ListTag();
      for (Chapter c : this.chapters.values()) {
         chapterList.add(c.save());
      }
      tag.put("chapters", chapterList);

      ListTag members = new ListTag();
      for (Membership m : this.memberships.values()) {
         members.add(m.save());
      }
      tag.put("memberships", members);

      ListTag rituals = new ListTag();
      for (RitualInstance r : this.activeRituals.values()) {
         rituals.add(r.save());
      }
      tag.put("rituals", rituals);

      ListTag history = new ListTag();
      history.addAll(this.ritualHistory);
      tag.put("ritual_history", history);

      ListTag travels = new ListTag();
      for (TravelRecord t : this.travelRecords.values()) {
         travels.add(t.save());
      }
      tag.put("travels", travels);
      return tag;
   }

   private static ReligionSavedData load(CompoundTag tag) {
      ReligionSavedData data = new ReligionSavedData();
      int version = tag.contains("version") ? tag.getInt("version") : 0;
      if (version > SCHEMA_VERSION) {
         LOGGER.warn(
            "[Religion] Save version {} > supported {}; unknown fields may be dropped",
            version,
            SCHEMA_VERSION
         );
      }
      data.worldSessionId = tag.contains("session") ? tag.getLong("session") : UUID.randomUUID().getMostSignificantBits();
      data.contentGenerationSeen = tag.getInt("content_gen");

      ListTag faiths = tag.getList("faiths", 10);
      for (int i = 0; i < faiths.size(); i++) {
         FaithInstance f = FaithInstance.load(faiths.getCompound(i));
         data.faithInstances.put(f.id(), f);
      }
      ListTag orders = tag.getList("orders", 10);
      for (int i = 0; i < orders.size(); i++) {
         OrderInstance o = OrderInstance.load(orders.getCompound(i));
         data.orderInstances.put(o.id(), o);
      }
      ListTag chapters = tag.getList("chapters", 10);
      for (int i = 0; i < chapters.size(); i++) {
         Chapter c = Chapter.load(chapters.getCompound(i));
         data.chapters.put(c.id(), c);
         data.chapterByVillage.put(c.villageId().uuid(), c.id());
      }
      ListTag members = tag.getList("memberships", 10);
      for (int i = 0; i < members.size(); i++) {
         Membership m = Membership.load(members.getCompound(i));
         if (m != null) {
            data.memberships.put(m.storageKey(), m);
         }
      }
      ListTag rituals = tag.getList("rituals", 10);
      for (int i = 0; i < rituals.size(); i++) {
         RitualInstance r = RitualInstance.load(rituals.getCompound(i));
         data.activeRituals.put(r.id(), r);
      }
      ListTag history = tag.getList("ritual_history", 10);
      for (int i = 0; i < history.size(); i++) {
         data.ritualHistory.add(history.getCompound(i));
      }
      ListTag travels = tag.getList("travels", 10);
      for (int i = 0; i < travels.size(); i++) {
         TravelRecord t = TravelRecord.load(travels.getCompound(i));
         data.travelRecords.put(t.id(), t);
      }
      LOGGER.info(
         "[Religion] Loaded {} faiths, {} orders, {} chapters, {} memberships, {} rituals",
         data.faithInstances.size(),
         data.orderInstances.size(),
         data.chapters.size(),
         data.memberships.size(),
         data.activeRituals.size()
      );
      return data;
   }
}
