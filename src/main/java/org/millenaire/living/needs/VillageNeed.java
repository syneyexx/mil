package org.millenaire.living.needs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable description of one verified village need derived from authoritative game state.
 */
public final class VillageNeed {
   private final String needId;
   private final UUID villageId;
   private final VillageNeedType type;
   private final VillageNeedPriority severity;
   @Nullable
   private final String projectRef;
   private final Map<ResourceLocation, Integer> required;
   private final Map<ResourceLocation, Integer> available;
   private final Map<ResourceLocation, Integer> shortage;
   private final String summaryKey;
   private final String summaryDetail;
   private final long observedGameTime;
   private final int stateVersion;

   public VillageNeed(
      String needId,
      UUID villageId,
      VillageNeedType type,
      VillageNeedPriority severity,
      @Nullable String projectRef,
      Map<ResourceLocation, Integer> required,
      Map<ResourceLocation, Integer> available,
      Map<ResourceLocation, Integer> shortage,
      String summaryKey,
      String summaryDetail,
      long observedGameTime,
      int stateVersion
   ) {
      this.needId = Objects.requireNonNull(needId);
      this.villageId = Objects.requireNonNull(villageId);
      this.type = Objects.requireNonNull(type);
      this.severity = Objects.requireNonNull(severity);
      this.projectRef = projectRef;
      this.required = Collections.unmodifiableMap(new LinkedHashMap<>(required));
      this.available = Collections.unmodifiableMap(new LinkedHashMap<>(available));
      this.shortage = Collections.unmodifiableMap(new LinkedHashMap<>(shortage));
      this.summaryKey = summaryKey == null ? "" : summaryKey;
      this.summaryDetail = summaryDetail == null ? "" : summaryDetail;
      this.observedGameTime = observedGameTime;
      this.stateVersion = stateVersion;
   }

   public String needId() {
      return this.needId;
   }

   public UUID villageId() {
      return this.villageId;
   }

   public VillageNeedType type() {
      return this.type;
   }

   public VillageNeedPriority severity() {
      return this.severity;
   }

   @Nullable
   public String projectRef() {
      return this.projectRef;
   }

   public Map<ResourceLocation, Integer> required() {
      return this.required;
   }

   public Map<ResourceLocation, Integer> available() {
      return this.available;
   }

   public Map<ResourceLocation, Integer> shortage() {
      return this.shortage;
   }

   public String summaryKey() {
      return this.summaryKey;
   }

   public String summaryDetail() {
      return this.summaryDetail;
   }

   public long observedGameTime() {
      return this.observedGameTime;
   }

   public int stateVersion() {
      return this.stateVersion;
   }

   public int totalShortage() {
      int total = 0;
      for (int v : this.shortage.values()) {
         total += Math.max(0, v);
      }
      return total;
   }

   public boolean isEmpty() {
      if (this.severity == VillageNeedPriority.NONE) {
         return true;
      }
      boolean statusOnly = this.type == VillageNeedType.VILLAGE_SECURITY_THREAT
         || this.type == VillageNeedType.RESIDENT_LOSS
         || this.type == VillageNeedType.CONSTRUCTION_STALLED
         || this.type == VillageNeedType.TRADE_DISRUPTION
         || this.type == VillageNeedType.MERCHANT_OR_SUPPLY_UNAVAILABLE;
      return !statusOnly && this.shortage.isEmpty();
   }
}
