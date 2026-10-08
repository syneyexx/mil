package org.millenaire.chat;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.village.VillageId;
import org.slf4j.Logger;

public class NpcMindSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String DATA_NAME = "millenaire_npc_minds";

   private final Map<UUID, NpcMind> minds = new HashMap<>();
   private final Map<Long, NpcRumor> rumors = new HashMap<>();
   private long nextRumorId = 1L;
   private long lastGossipDay = -1L;

   private NpcMindSavedData() {
   }

   public static Factory<NpcMindSavedData> factory() {
      return new Factory<>(NpcMindSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static NpcMindSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      ServerLevel storage = overworld != null ? overworld : level;
      return storage.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public NpcMind getOrCreateMind(UUID villagerId) {
      return this.minds.computeIfAbsent(villagerId, id -> new NpcMind());
   }

   @Nullable
   public NpcMind getMind(UUID villagerId) {
      return this.minds.get(villagerId);
   }

   public Map<UUID, NpcMind> getMinds() {
      return this.minds;
   }

   public Map<Long, NpcRumor> getRumors() {
      return this.rumors;
   }

   @Nullable
   public NpcRumor getRumor(long id) {
      return this.rumors.get(id);
   }

   public long getLastGossipDay() {
      return this.lastGossipDay;
   }

   public void setLastGossipDay(long day) {
      this.lastGossipDay = day;
      this.setDirty();
   }

   public long peekNextRumorId() {
      return this.nextRumorId;
   }

   public NpcRumor addRumor(NpcRumor rumor) {
      this.rumors.put(rumor.id(), rumor);
      this.nextRumorId = Math.max(this.nextRumorId, rumor.id() + 1L);
      this.setDirty();
      return rumor;
   }

   public long nextRumorId() {
      return this.nextRumorId++;
   }

   public void replaceRumor(NpcRumor rumor) {
      this.rumors.put(rumor.id(), rumor);
      this.setDirty();
   }

   public void removeRumor(long id) {
      this.rumors.remove(id);
      this.setDirty();
   }

   public List<NpcRumor> activeRumorsForCulture(ResourceLocation cultureId) {
      List<NpcRumor> list = new ArrayList<>();
      for (NpcRumor rumor : this.rumors.values()) {
         if (rumor.isActive() && rumor.cultureId().equals(cultureId)) {
            list.add(rumor);
         }
      }
      return list;
   }

   public List<NpcRumorKnowledge> allKnowledge() {
      List<NpcRumorKnowledge> list = new ArrayList<>();
      for (NpcMind mind : this.minds.values()) {
         list.addAll(mind.getKnowledge());
      }
      return list;
   }

   public void markChanged() {
      this.setDirty();
   }

   public CompoundTag save(CompoundTag root, Provider registries) {
      ListTag mindsList = new ListTag();
      for (Map.Entry<UUID, NpcMind> entry : this.minds.entrySet()) {
         CompoundTag mindTag = saveMind(entry.getKey(), entry.getValue());
         mindsList.add(mindTag);
      }
      root.put("minds", mindsList);

      ListTag rumorsList = new ListTag();
      for (NpcRumor rumor : this.rumors.values()) {
         rumorsList.add(saveRumor(rumor));
      }
      root.put("rumors", rumorsList);
      root.putLong("nextRumorId", this.nextRumorId);
      root.putLong("lastGossipDay", this.lastGossipDay);
      return root;
   }

   static NpcMindSavedData load(CompoundTag root) {
      NpcMindSavedData data = new NpcMindSavedData();
      ListTag mindsList = root.getList("minds", 10);
      for (int i = 0; i < mindsList.size(); i++) {
         CompoundTag mindTag = mindsList.getCompound(i);
         try {
            UUID id = mindTag.getUUID("villager");
            data.minds.put(id, loadMind(mindTag));
         } catch (Exception e) {
            LOGGER.warn("Skipping corrupt NPC mind entry at index {}", i, e);
         }
      }
      ListTag rumorsList = root.getList("rumors", 10);
      for (int i = 0; i < rumorsList.size(); i++) {
         CompoundTag rumorTag = rumorsList.getCompound(i);
         try {
            NpcRumor rumor = loadRumor(rumorTag);
            data.rumors.put(rumor.id(), rumor);
         } catch (Exception e) {
            LOGGER.warn("Skipping corrupt NPC rumor entry at index {}", i, e);
         }
      }
      data.nextRumorId = Math.max(1L, root.getLong("nextRumorId"));
      data.lastGossipDay = root.contains("lastGossipDay") ? root.getLong("lastGossipDay") : -1L;
      return data;
   }

   private static CompoundTag saveMind(UUID villagerId, NpcMind mind) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("villager", villagerId);
      tag.putString("mood", mind.getMood());
      tag.putString("lastThought", mind.getLastThought());
      tag.putString("summary", mind.getSummary());
      tag.putLong("nextFactId", mind.getNextFactId());
      tag.putLong("nextQuoteId", mind.getNextQuoteId());

      ListTag facts = new ListTag();
      for (NpcFact fact : mind.getFacts()) {
         CompoundTag f = new CompoundTag();
         f.putLong("id", fact.id());
         f.putString("text", fact.text());
         f.putLong("gameTime", fact.gameTime());
         f.putInt("importance", fact.importance());
         if (fact.aboutPlayer() != null) {
            f.putUUID("aboutPlayer", fact.aboutPlayer());
         }
         facts.add(f);
      }
      tag.put("facts", facts);

      ListTag opinions = new ListTag();
      for (Map.Entry<UUID, NpcOpinion> e : mind.getOpinions().entrySet()) {
         CompoundTag o = new CompoundTag();
         o.putUUID("player", e.getKey());
         o.putFloat("trust", e.getValue().trust());
         o.putFloat("fear", e.getValue().fear());
         o.putFloat("affection", e.getValue().affection());
         opinions.add(o);
      }
      tag.put("opinions", opinions);

      ListTag turns = new ListTag();
      for (Map.Entry<UUID, List<NpcTurn>> e : mind.getTurns().entrySet()) {
         CompoundTag playerTag = new CompoundTag();
         playerTag.putUUID("player", e.getKey());
         ListTag list = new ListTag();
         for (NpcTurn turn : e.getValue()) {
            CompoundTag t = new CompoundTag();
            t.putString("role", turn.role());
            t.putString("text", turn.text());
            t.putLong("gameTime", turn.gameTime());
            list.add(t);
         }
         playerTag.put("entries", list);
         turns.add(playerTag);
      }
      tag.put("turns", turns);

      ListTag quotes = new ListTag();
      for (Map.Entry<UUID, List<NpcQuote>> e : mind.getQuotes().entrySet()) {
         CompoundTag playerTag = new CompoundTag();
         playerTag.putUUID("player", e.getKey());
         ListTag list = new ListTag();
         for (NpcQuote quote : e.getValue()) {
            CompoundTag q = new CompoundTag();
            q.putLong("id", quote.id());
            q.putString("text", quote.text());
            q.putLong("gameTime", quote.gameTime());
            list.add(q);
         }
         playerTag.put("entries", list);
         quotes.add(playerTag);
      }
      tag.put("quotes", quotes);

      ListTag talks = new ListTag();
      for (Map.Entry<UUID, Long> e : mind.getLastTalkGameTime().entrySet()) {
         CompoundTag t = new CompoundTag();
         t.putUUID("player", e.getKey());
         t.putLong("gameTime", e.getValue());
         talks.add(t);
      }
      tag.put("lastTalk", talks);

      ListTag knowledge = new ListTag();
      for (NpcRumorKnowledge k : mind.getKnowledge()) {
         CompoundTag kt = new CompoundTag();
         kt.putLong("rumorId", k.rumorId());
         kt.putUUID("villager", k.villager());
         if (k.toldBy() != null) {
            kt.putUUID("toldBy", k.toldBy());
         }
         kt.putInt("hop", k.hop());
         kt.putFloat("confidence", k.confidence());
         kt.putLong("learnedGameTime", k.learnedGameTime());
         kt.putLong("learnedDay", k.learnedDay());
         knowledge.add(kt);
      }
      tag.put("knowledge", knowledge);
      return tag;
   }

   private static NpcMind loadMind(CompoundTag tag) {
      NpcMind mind = new NpcMind();
      mind.setMood(tag.getString("mood"));
      mind.setLastThought(tag.getString("lastThought"));
      mind.setSummary(tag.getString("summary"));
      mind.setNextFactId(tag.contains("nextFactId") ? tag.getLong("nextFactId") : 1L);
      mind.setNextQuoteId(tag.contains("nextQuoteId") ? tag.getLong("nextQuoteId") : 1L);

      ListTag facts = tag.getList("facts", 10);
      for (int i = 0; i < facts.size(); i++) {
         CompoundTag f = facts.getCompound(i);
         UUID about = f.hasUUID("aboutPlayer") ? f.getUUID("aboutPlayer") : null;
         mind.getFacts().add(new NpcFact(f.getLong("id"), f.getString("text"), f.getLong("gameTime"), f.getInt("importance"), about));
      }

      ListTag opinions = tag.getList("opinions", 10);
      for (int i = 0; i < opinions.size(); i++) {
         CompoundTag o = opinions.getCompound(i);
         mind.getOpinions().put(o.getUUID("player"), new NpcOpinion(o.getFloat("trust"), o.getFloat("fear"), o.getFloat("affection")));
      }

      ListTag turns = tag.getList("turns", 10);
      for (int i = 0; i < turns.size(); i++) {
         CompoundTag playerTag = turns.getCompound(i);
         UUID player = playerTag.getUUID("player");
         List<NpcTurn> list = new ArrayList<>();
         ListTag entries = playerTag.getList("entries", 10);
         for (int j = 0; j < entries.size(); j++) {
            CompoundTag t = entries.getCompound(j);
            list.add(new NpcTurn(t.getString("role"), t.getString("text"), t.getLong("gameTime")));
         }
         mind.getTurns().put(player, list);
      }

      ListTag quotes = tag.getList("quotes", 10);
      for (int i = 0; i < quotes.size(); i++) {
         CompoundTag playerTag = quotes.getCompound(i);
         UUID player = playerTag.getUUID("player");
         List<NpcQuote> list = new ArrayList<>();
         ListTag entries = playerTag.getList("entries", 10);
         for (int j = 0; j < entries.size(); j++) {
            CompoundTag q = entries.getCompound(j);
            list.add(new NpcQuote(q.getLong("id"), q.getString("text"), q.getLong("gameTime")));
         }
         mind.getQuotes().put(player, list);
      }

      ListTag talks = tag.getList("lastTalk", 10);
      for (int i = 0; i < talks.size(); i++) {
         CompoundTag t = talks.getCompound(i);
         mind.getLastTalkGameTime().put(t.getUUID("player"), t.getLong("gameTime"));
      }

      ListTag knowledge = tag.getList("knowledge", 10);
      for (int i = 0; i < knowledge.size(); i++) {
         CompoundTag kt = knowledge.getCompound(i);
         UUID toldBy = kt.hasUUID("toldBy") ? kt.getUUID("toldBy") : null;
         mind.getKnowledge()
            .add(
               new NpcRumorKnowledge(
                  kt.getLong("rumorId"),
                  kt.getUUID("villager"),
                  toldBy,
                  kt.getInt("hop"),
                  kt.getFloat("confidence"),
                  kt.getLong("learnedGameTime"),
                  kt.getLong("learnedDay")
               )
            );
      }
      return mind;
   }

   private static CompoundTag saveRumor(NpcRumor rumor) {
      CompoundTag tag = new CompoundTag();
      tag.putLong("id", rumor.id());
      tag.putString("cultureId", rumor.cultureId().toString());
      tag.putUUID("originVillager", rumor.originVillager());
      tag.putUUID("originVillage", rumor.originVillage().uuid());
      tag.putUUID("aboutPlayer", rumor.aboutPlayer());
      tag.putString("playerName", rumor.playerName());
      tag.putString("canonical", rumor.canonical());
      tag.putLong("createdGameTime", rumor.createdGameTime());
      tag.putLong("createdDay", rumor.createdDay());
      tag.putInt("importance", rumor.importance());
      tag.putLong("supersededBy", rumor.supersededBy());
      tag.putBoolean("expired", rumor.expired());
      return tag;
   }

   private static NpcRumor loadRumor(CompoundTag tag) {
      return new NpcRumor(
         tag.getLong("id"),
         ResourceLocation.parse(tag.getString("cultureId")),
         tag.getUUID("originVillager"),
         new VillageId(tag.getUUID("originVillage")),
         tag.getUUID("aboutPlayer"),
         tag.getString("playerName"),
         tag.getString("canonical"),
         tag.getLong("createdGameTime"),
         tag.getLong("createdDay"),
         tag.getInt("importance"),
         tag.getLong("supersededBy"),
         tag.getBoolean("expired")
      );
   }
}
