package org.millenaire.faction;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.village.VillageId;

/**
 * Persistent political entity.
 * <p>
 * Culture ({@link #cultureId}) describes cultural/NPC characteristics used by this faction.
 * Faction identity ({@link #id}) is political and may be shared by multiple factions using the
 * same culture in future content. Phase 2 relationship systems should key off {@link FactionId}
 * without altering this model.
 */
public final class Faction {
   private final FactionId id;
   private final FactionType type;
   private final long createdAtGameTime;
   private String displayName;
   @Nullable
   private final ResourceLocation cultureId;
   private final Set<VillageId> settlementIds = new LinkedHashSet<>();

   public Faction(FactionId id, FactionType type, String displayName, @Nullable ResourceLocation cultureId, long createdAtGameTime) {
      this.id = Objects.requireNonNull(id, "id");
      this.type = Objects.requireNonNull(type, "type");
      this.displayName = requireDisplayName(displayName);
      this.cultureId = cultureId;
      this.createdAtGameTime = createdAtGameTime;
   }

   public FactionId getId() {
      return this.id;
   }

   public FactionType getType() {
      return this.type;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public void setDisplayName(String displayName) {
      this.displayName = requireDisplayName(displayName);
   }

   /**
    * Millénaire culture this faction uses, or {@code null} when the faction has no culture binding.
    * Multiple factions may share the same culture id.
    */
   @Nullable
   public ResourceLocation getCultureId() {
      return this.cultureId;
   }

   public long getCreatedAtGameTime() {
      return this.createdAtGameTime;
   }

   public Set<VillageId> getSettlementIds() {
      return Collections.unmodifiableSet(this.settlementIds);
   }

   public boolean hasSettlement(VillageId villageId) {
      return villageId != null && this.settlementIds.contains(villageId);
   }

   public int getSettlementCount() {
      return this.settlementIds.size();
   }

   boolean addSettlement(VillageId villageId) {
      Objects.requireNonNull(villageId, "villageId");
      return this.settlementIds.add(villageId);
   }

   boolean removeSettlement(VillageId villageId) {
      return villageId != null && this.settlementIds.remove(villageId);
   }

   void clearSettlements() {
      this.settlementIds.clear();
   }

   void replaceSettlements(Set<VillageId> villageIds) {
      this.settlementIds.clear();
      if (villageIds != null) {
         this.settlementIds.addAll(villageIds);
      }
   }

   private static String requireDisplayName(String displayName) {
      Objects.requireNonNull(displayName, "displayName");
      String trimmed = displayName.trim();
      if (trimmed.isEmpty()) {
         throw new IllegalArgumentException("displayName must not be blank");
      }

      return trimmed;
   }

   @Override
   public String toString() {
      return "Faction{id=" + this.id + ", type=" + this.type + ", culture=" + this.cultureId + ", settlements=" + this.settlementIds.size() + "}";
   }
}
