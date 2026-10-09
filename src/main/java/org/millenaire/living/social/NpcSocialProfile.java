package org.millenaire.living.social;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.millenaire.living.knowledge.NpcKnowledgeItem;

/**
 * Persistent personality, transient mood, relationships and bounded knowledge for one NPC.
 */
public final class NpcSocialProfile {
   public static final int SCHEMA_VERSION = 1;

   private final UUID npcId;
   private int sociability;
   private int caution;
   private int generosity;
   private int ambition;
   private int patience;
   private int loyalty;
   private boolean personalityInitialized;
   private String mood = "calm";
   private long moodSetGameTime;
   private final Map<UUID, NpcRelationship> relationships = new HashMap<>();
   private final List<NpcKnowledgeItem> knowledge = new ArrayList<>();
   private long lastSocialInteractionGameTime;

   public NpcSocialProfile(UUID npcId) {
      this.npcId = npcId;
   }

   public UUID npcId() {
      return this.npcId;
   }

   public int sociability() {
      return this.sociability;
   }

   public int caution() {
      return this.caution;
   }

   public int generosity() {
      return this.generosity;
   }

   public int ambition() {
      return this.ambition;
   }

   public int patience() {
      return this.patience;
   }

   public int loyalty() {
      return this.loyalty;
   }

   public boolean personalityInitialized() {
      return this.personalityInitialized;
   }

   public String mood() {
      return this.mood;
   }

   public long moodSetGameTime() {
      return this.moodSetGameTime;
   }

   public long lastSocialInteractionGameTime() {
      return this.lastSocialInteractionGameTime;
   }

   public void setLastSocialInteractionGameTime(long t) {
      this.lastSocialInteractionGameTime = t;
   }

   public Map<UUID, NpcRelationship> relationships() {
      return this.relationships;
   }

   public List<NpcKnowledgeItem> knowledge() {
      return this.knowledge;
   }

   public void initializePersonality(int sociability, int caution, int generosity, int ambition, int patience, int loyalty) {
      if (this.personalityInitialized) {
         return;
      }
      this.sociability = clampTrait(sociability);
      this.caution = clampTrait(caution);
      this.generosity = clampTrait(generosity);
      this.ambition = clampTrait(ambition);
      this.patience = clampTrait(patience);
      this.loyalty = clampTrait(loyalty);
      this.personalityInitialized = true;
   }

   public void setMood(String mood, long gameTime) {
      this.mood = mood == null || mood.isBlank() ? "calm" : mood;
      this.moodSetGameTime = gameTime;
   }

   public void decayMood(long gameTime, long decayInterval) {
      if (!"calm".equals(this.mood) && gameTime - this.moodSetGameTime >= decayInterval) {
         this.mood = "calm";
         this.moodSetGameTime = gameTime;
      }
   }

   @Nullable
   public NpcRelationship getRelationship(UUID other) {
      return this.relationships.get(other);
   }

   public NpcRelationship getOrCreateRelationship(UUID other, NpcRelationshipType type) {
      return this.relationships.computeIfAbsent(other, id -> new NpcRelationship(this.npcId, id, type));
   }

   public void addKnowledge(NpcKnowledgeItem item, int maxItems) {
      if (item == null) {
         return;
      }
      this.knowledge.removeIf(k -> k.knowledgeId().equals(item.knowledgeId()));
      this.knowledge.add(item);
      while (this.knowledge.size() > Math.max(8, maxItems)) {
         this.knowledge.remove(0);
      }
   }

   public void pruneKnowledge(long now, long maxAgeTicks, int maxItems) {
      Iterator<NpcKnowledgeItem> it = this.knowledge.iterator();
      while (it.hasNext()) {
         NpcKnowledgeItem item = it.next();
         if (item.isExpired(now) || now - item.observationTime() > maxAgeTicks) {
            it.remove();
         }
      }
      while (this.knowledge.size() > Math.max(8, maxItems)) {
         this.knowledge.remove(0);
      }
   }

   public void pruneRelationships(int maxRelationships, long now, long maxIdleTicks) {
      if (this.relationships.size() <= maxRelationships) {
         this.relationships.entrySet().removeIf(e -> {
            NpcRelationship rel = e.getValue();
            return rel.type() == NpcRelationshipType.ACQUAINTANCE
               && rel.familiarity() <= 5
               && now - rel.lastInteractionGameTime() > maxIdleTicks;
         });
         return;
      }
      List<Map.Entry<UUID, NpcRelationship>> entries = new ArrayList<>(this.relationships.entrySet());
      entries.sort((a, b) -> Long.compare(a.getValue().lastInteractionGameTime(), b.getValue().lastInteractionGameTime()));
      int remove = this.relationships.size() - maxRelationships;
      for (int i = 0; i < remove && i < entries.size(); i++) {
         NpcRelationship rel = entries.get(i).getValue();
         if (rel.type() == NpcRelationshipType.FAMILY_SPOUSE
            || rel.type() == NpcRelationshipType.FAMILY_PARENT
            || rel.type() == NpcRelationshipType.FAMILY_CHILD) {
            continue;
         }
         this.relationships.remove(entries.get(i).getKey());
      }
   }

   public static int clampTrait(int v) {
      return Math.max(0, Math.min(100, v));
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("npc", this.npcId);
      tag.putInt("schema", SCHEMA_VERSION);
      tag.putBoolean("personalityInit", this.personalityInitialized);
      tag.putInt("sociability", this.sociability);
      tag.putInt("caution", this.caution);
      tag.putInt("generosity", this.generosity);
      tag.putInt("ambition", this.ambition);
      tag.putInt("patience", this.patience);
      tag.putInt("loyalty", this.loyalty);
      tag.putString("mood", this.mood);
      tag.putLong("moodSet", this.moodSetGameTime);
      tag.putLong("lastSocial", this.lastSocialInteractionGameTime);
      ListTag relList = new ListTag();
      for (NpcRelationship rel : this.relationships.values()) {
         relList.add(rel.save());
      }
      tag.put("relationships", relList);
      ListTag knowList = new ListTag();
      for (NpcKnowledgeItem item : this.knowledge) {
         knowList.add(item.save());
      }
      tag.put("knowledge", knowList);
      return tag;
   }

   public static NpcSocialProfile load(CompoundTag tag) {
      UUID npc = tag.getUUID("npc");
      NpcSocialProfile profile = new NpcSocialProfile(npc);
      profile.personalityInitialized = tag.getBoolean("personalityInit");
      profile.sociability = clampTrait(tag.getInt("sociability"));
      profile.caution = clampTrait(tag.getInt("caution"));
      profile.generosity = clampTrait(tag.getInt("generosity"));
      profile.ambition = clampTrait(tag.getInt("ambition"));
      profile.patience = clampTrait(tag.getInt("patience"));
      profile.loyalty = clampTrait(tag.getInt("loyalty"));
      profile.mood = tag.contains("mood") ? tag.getString("mood") : "calm";
      profile.moodSetGameTime = tag.getLong("moodSet");
      profile.lastSocialInteractionGameTime = tag.getLong("lastSocial");
      ListTag relList = tag.getList("relationships", 10);
      for (int i = 0; i < relList.size(); i++) {
         try {
            NpcRelationship rel = NpcRelationship.load(relList.getCompound(i));
            profile.relationships.put(rel.toId(), rel);
         } catch (Exception ignored) {
         }
      }
      ListTag knowList = tag.getList("knowledge", 10);
      for (int i = 0; i < knowList.size(); i++) {
         NpcKnowledgeItem item = NpcKnowledgeItem.load(knowList.getCompound(i));
         if (item != null) {
            profile.knowledge.add(item);
         }
      }
      return profile;
   }
}
