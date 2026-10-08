package org.millenaire.faction.relationship;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.faction.FactionId;
import org.slf4j.Logger;

/**
 * World-scoped persistence for faction relationships.
 * Stored alongside other Millénaire {@link SavedData} on the overworld data storage.
 */
public final class RelationshipSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int CURRENT_VERSION = 1;
   public static final String DATA_NAME = "millenaire_faction_relationships";

   private final RelationshipManager relationshipManager = new RelationshipManager(this::setDirty);

   private RelationshipSavedData() {
   }

   public static Factory<RelationshipSavedData> factory() {
      return new Factory<>(RelationshipSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static RelationshipSavedData get(ServerLevel level) {
      return (RelationshipSavedData)level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public RelationshipManager getRelationshipManager() {
      return this.relationshipManager;
   }

   public CompoundTag save(CompoundTag root, Provider registries) {
      root.putInt("version", CURRENT_VERSION);
      root.putLong("last_decay_check", this.relationshipManager.getLastDecayCheckGameTime());
      ListTag relationshipsList = new ListTag();

      for (FactionRelationship relationship : this.relationshipManager.getAllRelationships()) {
         try {
            relationshipsList.add(saveRelationship(relationship));
         } catch (Exception e) {
            LOGGER.error(
               "[Millenaire] Failed to serialize relationship {} → {} — omitted from this save",
               relationship.getSourceFactionId(),
               relationship.getTargetFactionId(),
               e
            );
         }
      }

      root.put("relationships", relationshipsList);
      LOGGER.debug("[Millenaire] Saved {} faction relationship(s)", relationshipsList.size());
      return root;
   }

   private static CompoundTag saveRelationship(FactionRelationship relationship) {
      CompoundTag tag = new CompoundTag();
      tag.putString("source", relationship.getSourceFactionId().asString());
      tag.putString("target", relationship.getTargetFactionId().asString());
      tag.putInt("score", relationship.getScore());
      tag.putLong("last_meaningful_change", relationship.getLastMeaningfulChangeGameTime());
      tag.putLong("last_updated", relationship.getLastUpdatedGameTime());
      tag.putLong("last_decay", relationship.getLastDecayGameTime());
      ListTag historyList = new ListTag();

      for (RelationshipHistoryEntry entry : relationship.getHistory()) {
         CompoundTag historyTag = new CompoundTag();
         historyTag.putLong("game_time", entry.getGameTime());
         historyTag.putString("reason", entry.getReason().name());
         historyTag.putInt("delta", entry.getDelta());
         historyTag.putInt("previous", entry.getPreviousScore());
         historyTag.putInt("new", entry.getNewScore());
         historyList.add(historyTag);
      }

      tag.put("history", historyList);
      return tag;
   }

   private static RelationshipSavedData load(CompoundTag root) {
      RelationshipSavedData data = new RelationshipSavedData();
      int version = root.contains("version") ? root.getInt("version") : 0;
      if (version > CURRENT_VERSION) {
         LOGGER.warn(
            "[Millenaire] RelationshipSavedData version {} is newer than supported {} — loading best-effort",
            version,
            CURRENT_VERSION
         );
      } else {
         LOGGER.info("[Millenaire] Loading RelationshipSavedData version {}", version);
      }

      if (root.contains("last_decay_check")) {
         data.relationshipManager.setLastDecayCheckGameTime(root.getLong("last_decay_check"));
      }

      if (!root.contains("relationships")) {
         return data;
      }

      ListTag relationshipsList = root.getList("relationships", 10);
      int loaded = 0;

      for (int i = 0; i < relationshipsList.size(); i++) {
         CompoundTag relationshipTag = relationshipsList.getCompound(i);

         try {
            FactionRelationship relationship = readRelationship(relationshipTag);
            if (relationship != null) {
               data.relationshipManager.loadRelationship(relationship);
               loaded++;
            }
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Skipping malformed relationship at index {}", i, e);
         }
      }

      LOGGER.info("[Millenaire] Loaded {} faction relationship(s)", loaded);
      return data;
   }

   @Nullable
   private static FactionRelationship readRelationship(CompoundTag tag) {
      FactionId source = FactionId.tryParse(tag.contains("source") ? tag.getString("source") : null);
      FactionId target = FactionId.tryParse(tag.contains("target") ? tag.getString("target") : null);
      if (source == null || target == null) {
         LOGGER.error("[Millenaire] Persisted relationship missing source/target — skipped");
         return null;
      }

      if (source.equals(target)) {
         LOGGER.error("[Millenaire] Persisted self-relationship {} — skipped", source);
         return null;
      }

      if (!tag.contains("score")) {
         LOGGER.error("[Millenaire] Persisted relationship {} → {} missing score — skipped", source, target);
         return null;
      }

      int score = RelationshipConfig.clampScore(tag.getInt("score"));
      long nowBookmark = 0L;
      FactionRelationship relationship = new FactionRelationship(source, target, score, nowBookmark);
      long lastMeaningful = tag.contains("last_meaningful_change") ? tag.getLong("last_meaningful_change") : nowBookmark;
      long lastUpdated = tag.contains("last_updated") ? tag.getLong("last_updated") : lastMeaningful;
      long lastDecay = tag.contains("last_decay") ? tag.getLong("last_decay") : lastUpdated;
      relationship.loadTimestamps(lastMeaningful, lastUpdated, lastDecay);

      if (tag.contains("history")) {
         ListTag historyList = tag.getList("history", 10);
         List<RelationshipHistoryEntry> history = new ArrayList<>();

         for (int i = 0; i < historyList.size(); i++) {
            CompoundTag historyTag = historyList.getCompound(i);
            RelationshipHistoryEntry entry = readHistoryEntry(historyTag, source, target, i);
            if (entry != null) {
               history.add(entry);
            }
         }

         relationship.replaceHistory(history);
      }

      return relationship;
   }

   @Nullable
   private static RelationshipHistoryEntry readHistoryEntry(CompoundTag tag, FactionId source, FactionId target, int index) {
      if (!tag.contains("game_time") || !tag.contains("delta") || !tag.contains("previous") || !tag.contains("new")) {
         LOGGER.warn("[Millenaire] Relationship {} → {} history entry {} incomplete — ignored", source, target, index);
         return null;
      }

      RelationshipChangeReason reason = RelationshipChangeReason.tryParse(tag.contains("reason") ? tag.getString("reason") : null);
      if (reason == null) {
         LOGGER.warn(
            "[Millenaire] Relationship {} → {} history entry {} has unknown reason '{}' — using GENERIC",
            source,
            target,
            index,
            tag.contains("reason") ? tag.getString("reason") : ""
         );
         reason = RelationshipChangeReason.GENERIC;
      }

      int previous = RelationshipConfig.clampScore(tag.getInt("previous"));
      int next = RelationshipConfig.clampScore(tag.getInt("new"));
      int delta = tag.getInt("delta");
      int expectedDelta = next - previous;
      if (delta != expectedDelta) {
         LOGGER.warn(
            "[Millenaire] Relationship {} → {} history entry {} delta {} inconsistent with scores {}→{} — correcting delta",
            source,
            target,
            index,
            delta,
            previous,
            next
         );
         delta = expectedDelta;
      }

      return new RelationshipHistoryEntry(tag.getLong("game_time"), reason, delta, previous, next);
   }
}
