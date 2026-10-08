package org.millenaire.faction.diplomacy;

import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionManager;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.relationship.RelationshipCategory;
import org.millenaire.faction.relationship.RelationshipChangeReason;
import org.millenaire.faction.relationship.RelationshipSavedData;
import org.slf4j.Logger;

/**
 * Authoritative server-side diplomatic agreement registry and transition API.
 * <p>
 * Distinct from Phase 2 relationship scores and Phase 3 trade statistics.
 * Does not declare war, spawn armies, or run autonomous diplomatic AI.
 */
public final class DiplomaticManager {
   private static final Logger LOGGER = LogUtils.getLogger();

   private final Map<AgreementId, DiplomaticAgreement> agreements = new LinkedHashMap<>();
   private final Map<FactionId, Set<AgreementId>> byFaction = new LinkedHashMap<>();
   private final Map<AgreementType, Map<DiplomaticPairKey, AgreementId>> activeByType = new EnumMap<>(AgreementType.class);
   private final Deque<DiplomaticHistoryEntry> globalHistory = new ArrayDeque<>();
   private final Runnable markDirty;
   private long lastExpirationCheckGameTime;

   DiplomaticManager(Runnable markDirty) {
      this.markDirty = Objects.requireNonNull(markDirty, "markDirty");

      for (AgreementType type : AgreementType.values()) {
         this.activeByType.put(type, new LinkedHashMap<>());
      }
   }

   @Nullable
   public DiplomaticAgreement getAgreement(AgreementId id) {
      return id == null ? null : this.agreements.get(id);
   }

   public Collection<DiplomaticAgreement> getAllAgreements() {
      return Collections.unmodifiableCollection(this.agreements.values());
   }

   public Collection<DiplomaticAgreement> getActiveAgreements() {
      List<DiplomaticAgreement> result = new ArrayList<>();

      for (DiplomaticAgreement agreement : this.agreements.values()) {
         if (agreement.isActive()) {
            result.add(agreement);
         }
      }

      return Collections.unmodifiableList(result);
   }

   public Collection<DiplomaticAgreement> getAgreementsInvolving(FactionId factionId) {
      Set<AgreementId> ids = this.byFaction.get(factionId);
      if (ids == null || ids.isEmpty()) {
         return List.of();
      }

      List<DiplomaticAgreement> result = new ArrayList<>();

      for (AgreementId id : ids) {
         DiplomaticAgreement agreement = this.agreements.get(id);
         if (agreement != null) {
            result.add(agreement);
         }
      }

      return Collections.unmodifiableList(result);
   }

   public Collection<DiplomaticAgreement> getActiveAgreementsInvolving(FactionId factionId) {
      List<DiplomaticAgreement> result = new ArrayList<>();

      for (DiplomaticAgreement agreement : this.getAgreementsInvolving(factionId)) {
         if (agreement.isActive()) {
            result.add(agreement);
         }
      }

      return Collections.unmodifiableList(result);
   }

   @Nullable
   public DiplomaticAgreement getActiveAgreement(AgreementType type, FactionId a, FactionId b) {
      if (type == null || a == null || b == null || a.equals(b)) {
         return null;
      }

      AgreementId id = this.activeByType.get(type).get(DiplomaticPairKey.forType(type, a, b));
      return id == null ? null : this.agreements.get(id);
   }

   public boolean hasActiveAgreement(AgreementType type, FactionId a, FactionId b) {
      return this.getActiveAgreement(type, a, b) != null;
   }

   public boolean isPeaceful(FactionId a, FactionId b) {
      return this.hasActiveAgreement(AgreementType.PEACE, a, b)
         || this.hasActiveAgreement(AgreementType.NON_AGGRESSION, a, b)
         || this.hasActiveAgreement(AgreementType.DEFENSIVE_PACT, a, b)
         || this.hasActiveAgreement(AgreementType.ALLIANCE, a, b);
   }

   public boolean hasNonAggressionPact(FactionId a, FactionId b) {
      return this.hasActiveAgreement(AgreementType.NON_AGGRESSION, a, b) || this.hasActiveAgreement(AgreementType.ALLIANCE, a, b) || this.hasActiveAgreement(AgreementType.DEFENSIVE_PACT, a, b);
   }

   public boolean hasTradeAgreement(FactionId a, FactionId b) {
      return this.hasActiveAgreement(AgreementType.TRADE_AGREEMENT, a, b);
   }

   public boolean hasDefensivePact(FactionId a, FactionId b) {
      return this.hasActiveAgreement(AgreementType.DEFENSIVE_PACT, a, b) || this.hasActiveAgreement(AgreementType.ALLIANCE, a, b);
   }

   public boolean isAllied(FactionId a, FactionId b) {
      return this.hasActiveAgreement(AgreementType.ALLIANCE, a, b);
   }

   /** True when {@code imposer} currently embargoes {@code target}. Directional. */
   public boolean isEmbargoed(FactionId imposer, FactionId target) {
      return this.hasActiveAgreement(AgreementType.EMBARGO, imposer, target);
   }

   /**
    * Trade between the pair is politically restricted when either side embargoes the other.
    */
   public boolean isTradeRestricted(FactionId a, FactionId b) {
      return this.isEmbargoed(a, b) || this.isEmbargoed(b, a);
   }

   public boolean canTrade(FactionId a, FactionId b) {
      return a != null && b != null && !a.equals(b) && !this.isTradeRestricted(a, b);
   }

   public boolean isTradeFormallyAgreed(FactionId a, FactionId b) {
      return this.hasTradeAgreement(a, b) && this.canTrade(a, b);
   }

   public Collection<FactionId> getAllies(FactionId factionId) {
      return this.collectPartners(factionId, AgreementType.ALLIANCE);
   }

   public Collection<FactionId> getTradeAgreementPartners(FactionId factionId) {
      return this.collectPartners(factionId, AgreementType.TRADE_AGREEMENT);
   }

   public Collection<FactionId> getNonAggressionPartners(FactionId factionId) {
      return this.collectPartners(factionId, AgreementType.NON_AGGRESSION);
   }

   public Collection<FactionId> getDefensivePactPartners(FactionId factionId) {
      Set<FactionId> result = new LinkedHashSet<>();
      result.addAll(this.collectPartners(factionId, AgreementType.DEFENSIVE_PACT));
      result.addAll(this.collectPartners(factionId, AgreementType.ALLIANCE));
      return Collections.unmodifiableSet(result);
   }

   public Collection<FactionId> getFactionsEmbargoing(FactionId target) {
      List<FactionId> result = new ArrayList<>();

      for (DiplomaticAgreement agreement : this.getActiveAgreements()) {
         if (agreement.getType() == AgreementType.EMBARGO && agreement.getSecondaryFactionId().equals(target)) {
            result.add(agreement.getPrimaryFactionId());
         }
      }

      return Collections.unmodifiableList(result);
   }

   public Collection<FactionId> getFactionsEmbargoedBy(FactionId imposer) {
      return this.collectPartners(imposer, AgreementType.EMBARGO);
   }

   public List<DiplomaticHistoryEntry> getDiplomaticHistory() {
      return Collections.unmodifiableList(new ArrayList<>(this.globalHistory));
   }

   /**
    * Derived formal standing between two factions. Soft FRIENDLY/HOSTILE only apply when no formal
    * agreement exists, using Phase 2 relationship categories without mutating them.
    */
   public DiplomaticStanding getDiplomaticStanding(ServerLevel level, FactionId a, FactionId b) {
      if (a == null || b == null || a.equals(b)) {
         return DiplomaticStanding.NEUTRAL;
      }

      DiplomaticStanding best = DiplomaticStanding.NEUTRAL;
      int bestPriority = DiplomaticStanding.priority(best);

      if (this.isEmbargoed(a, b) || this.isEmbargoed(b, a)) {
         return DiplomaticStanding.EMBARGO;
      }

      for (AgreementType type : AgreementType.values()) {
         if (type == AgreementType.EMBARGO) {
            continue;
         }

         if (this.hasActiveAgreement(type, a, b)) {
            DiplomaticStanding standing = standingFor(type);
            int priority = DiplomaticStanding.priority(standing);
            if (priority > bestPriority) {
               best = standing;
               bestPriority = priority;
            }
         }
      }

      if (best != DiplomaticStanding.NEUTRAL) {
         return best;
      }

      RelationshipCategory category = RelationshipSavedData.get(level).getRelationshipManager().getRelationshipCategory(a, b);
      if (category.isFriendly()) {
         return DiplomaticStanding.FRIENDLY;
      }

      if (category.isHostile()) {
         return DiplomaticStanding.HOSTILE;
      }

      return DiplomaticStanding.NEUTRAL;
   }

   @Nullable
   public DiplomaticAgreement establishPeace(ServerLevel level, FactionId a, FactionId b, long expirationGameTime) {
      return this.establish(level, AgreementType.PEACE, a, b, expirationGameTime, null);
   }

   @Nullable
   public DiplomaticAgreement establishNonAggressionPact(ServerLevel level, FactionId a, FactionId b, long expirationGameTime) {
      return this.establish(level, AgreementType.NON_AGGRESSION, a, b, expirationGameTime, null);
   }

   @Nullable
   public DiplomaticAgreement establishTradeAgreement(ServerLevel level, FactionId a, FactionId b, long expirationGameTime) {
      return this.establish(level, AgreementType.TRADE_AGREEMENT, a, b, expirationGameTime, null);
   }

   @Nullable
   public DiplomaticAgreement establishDefensivePact(ServerLevel level, FactionId a, FactionId b, long expirationGameTime) {
      return this.establish(level, AgreementType.DEFENSIVE_PACT, a, b, expirationGameTime, null);
   }

   @Nullable
   public DiplomaticAgreement establishAlliance(ServerLevel level, FactionId a, FactionId b, long expirationGameTime) {
      return this.establish(level, AgreementType.ALLIANCE, a, b, expirationGameTime, null);
   }

   @Nullable
   public DiplomaticAgreement imposeEmbargo(ServerLevel level, FactionId imposer, FactionId target, long expirationGameTime, @Nullable String reasonNote) {
      return this.establish(level, AgreementType.EMBARGO, imposer, target, expirationGameTime, reasonNote);
   }

   public boolean terminateAgreement(ServerLevel level, AgreementId agreementId, AgreementTerminationReason reason) {
      Objects.requireNonNull(level, "level");
      DiplomaticAgreement agreement = this.agreements.get(agreementId);
      if (agreement == null || !agreement.isActive()) {
         return false;
      }

      this.terminateInternal(level, agreement, reason == null ? AgreementTerminationReason.EXPLICIT : reason, level.getGameTime(), true);
      return true;
   }

   public boolean terminateActiveAgreement(ServerLevel level, AgreementType type, FactionId a, FactionId b, AgreementTerminationReason reason) {
      DiplomaticAgreement agreement = this.getActiveAgreement(type, a, b);
      return agreement != null && this.terminateAgreement(level, agreement.getId(), reason);
   }

   /**
    * Terminates all active agreements involving {@code factionId} (faction removal / cleanup).
    * Historical agreement records remain with terminated status.
    */
   public int terminateAllInvolving(ServerLevel level, FactionId factionId) {
      Objects.requireNonNull(level, "level");
      if (factionId == null) {
         return 0;
      }

      List<DiplomaticAgreement> active = new ArrayList<>();

      for (DiplomaticAgreement agreement : this.getAgreementsInvolving(factionId)) {
         if (agreement.isActive()) {
            active.add(agreement);
         }
      }

      long gameTime = level.getGameTime();

      for (DiplomaticAgreement agreement : active) {
         this.terminateInternal(level, agreement, AgreementTerminationReason.FACTION_REMOVED, gameTime, false);
      }

      if (!active.isEmpty()) {
         this.markDirty.run();
         LOGGER.info("[Millenaire] Terminated {} diplomatic agreement(s) involving removed faction {}", active.size(), factionId);
      }

      return active.size();
   }

   public void tick(ServerLevel level) {
      Objects.requireNonNull(level, "level");
      long gameTime = level.getGameTime();
      if (this.lastExpirationCheckGameTime > 0L && gameTime - this.lastExpirationCheckGameTime < DiplomaticConfig.EXPIRATION_CHECK_INTERVAL_TICKS) {
         return;
      }

      this.lastExpirationCheckGameTime = gameTime;
      List<DiplomaticAgreement> expired = new ArrayList<>();

      for (DiplomaticAgreement agreement : this.agreements.values()) {
         if (agreement.isActive() && agreement.isExpiredAt(gameTime)) {
            expired.add(agreement);
         }
      }

      for (DiplomaticAgreement agreement : expired) {
         this.terminateInternal(level, agreement, AgreementTerminationReason.EXPIRED, gameTime, false);
      }

      if (!expired.isEmpty()) {
         this.markDirty.run();
         LOGGER.debug("[Millenaire] Expired {} diplomatic agreement(s)", expired.size());
      }
   }

   public int purgeInvalid(FactionManager factionManager) {
      Objects.requireNonNull(factionManager, "factionManager");
      List<AgreementId> toTerminate = new ArrayList<>();

      for (DiplomaticAgreement agreement : this.agreements.values()) {
         if (!agreement.isActive()) {
            continue;
         }

         if (!factionManager.exists(agreement.getPrimaryFactionId()) || !factionManager.exists(agreement.getSecondaryFactionId())) {
            toTerminate.add(agreement.getId());
         }
      }

      for (AgreementId id : toTerminate) {
         DiplomaticAgreement agreement = this.agreements.get(id);
         if (agreement != null && agreement.isActive()) {
            agreement.terminate(0L, AgreementTerminationReason.FACTION_REMOVED);
            this.unindexActive(agreement);
            this.appendGlobalHistory(
               new DiplomaticHistoryEntry(
                  0L,
                  DiplomaticEventType.forTermination(agreement.getType()),
                  agreement.getPrimaryFactionId(),
                  agreement.getSecondaryFactionId(),
                  agreement.getId(),
                  agreement.getType(),
                  AgreementTerminationReason.FACTION_REMOVED
               )
            );
         }
      }

      if (!toTerminate.isEmpty()) {
         this.markDirty.run();
         LOGGER.warn("[Millenaire] Purged {} diplomatic agreement(s) with missing factions", toTerminate.size());
      }

      return toTerminate.size();
   }

   void loadAgreement(DiplomaticAgreement agreement) {
      Objects.requireNonNull(agreement, "agreement");
      if (this.agreements.containsKey(agreement.getId())) {
         LOGGER.error("[Millenaire] Ignoring duplicate diplomatic agreement {}", agreement.getId());
         return;
      }

      this.agreements.put(agreement.getId(), agreement);
      this.indexFaction(agreement.getPrimaryFactionId(), agreement.getId());
      this.indexFaction(agreement.getSecondaryFactionId(), agreement.getId());
      if (agreement.isActive()) {
         DiplomaticPairKey key = DiplomaticPairKey.forType(agreement.getType(), agreement.getPrimaryFactionId(), agreement.getSecondaryFactionId());
         Map<DiplomaticPairKey, AgreementId> map = this.activeByType.get(agreement.getType());
         if (map.containsKey(key)) {
            LOGGER.error(
               "[Millenaire] Duplicate active {} between {} and {} — keeping first, marking loaded duplicate terminated",
               agreement.getType(),
               agreement.getPrimaryFactionId(),
               agreement.getSecondaryFactionId()
            );
            agreement.terminate(agreement.getCreatedGameTime(), AgreementTerminationReason.CONFLICT);
         } else {
            map.put(key, agreement.getId());
         }
      }
   }

   void replaceGlobalHistory(List<DiplomaticHistoryEntry> entries) {
      this.globalHistory.clear();
      if (entries == null) {
         return;
      }

      for (DiplomaticHistoryEntry entry : entries) {
         if (entry != null) {
            this.globalHistory.addLast(entry);
         }
      }

      while (this.globalHistory.size() > DiplomaticConfig.MAX_DIPLOMATIC_HISTORY_ENTRIES) {
         this.globalHistory.removeFirst();
      }
   }

   long getLastExpirationCheckGameTime() {
      return this.lastExpirationCheckGameTime;
   }

   void setLastExpirationCheckGameTime(long gameTime) {
      this.lastExpirationCheckGameTime = gameTime;
   }

   void clear() {
      this.agreements.clear();
      this.byFaction.clear();

      for (Map<DiplomaticPairKey, AgreementId> map : this.activeByType.values()) {
         map.clear();
      }

      this.globalHistory.clear();
      this.lastExpirationCheckGameTime = 0L;
   }

   @Nullable
   private DiplomaticAgreement establish(
      ServerLevel level, AgreementType type, FactionId primary, FactionId secondary, long expirationGameTime, @Nullable String reasonNote
   ) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(type, "type");
      if (!this.validateParticipants(level, primary, secondary)) {
         return null;
      }

      if (expirationGameTime < 0L) {
         LOGGER.warn("[Millenaire] Rejecting {} with negative expiration", type);
         return null;
      }

      long gameTime = level.getGameTime();
      if (expirationGameTime > 0L && expirationGameTime <= gameTime) {
         LOGGER.warn("[Millenaire] Rejecting {} — expiration {} is not in the future (now={})", type, expirationGameTime, gameTime);
         return null;
      }

      DiplomaticPairKey key = DiplomaticPairKey.forType(type, primary, secondary);
      if (this.activeByType.get(type).containsKey(key)) {
         LOGGER.warn("[Millenaire] Rejecting duplicate active {} between {} and {}", type, primary, secondary);
         return null;
      }

      if (!this.validateConsistencyForEstablish(level, type, primary, secondary)) {
         return null;
      }

      this.resolveConflictsBeforeEstablish(level, type, primary, secondary, gameTime);
      DiplomaticAgreement agreement = new DiplomaticAgreement(AgreementId.random(), type, primary, secondary, gameTime, gameTime, expirationGameTime);
      agreement.setReasonNote(reasonNote);
      DiplomaticEventType eventType = DiplomaticEventType.forEstablishment(type);
      DiplomaticHistoryEntry historyEntry = new DiplomaticHistoryEntry(gameTime, eventType, primary, secondary, agreement.getId(), type, null);
      agreement.recordHistory(historyEntry);
      this.agreements.put(agreement.getId(), agreement);
      this.indexFaction(primary, agreement.getId());
      this.indexFaction(secondary, agreement.getId());
      this.activeByType.get(type).put(key, agreement.getId());
      this.appendGlobalHistory(historyEntry);
      this.applyRelationshipInfluence(level, type, primary, secondary, true, gameTime);
      this.markDirty.run();
      LOGGER.info("[Millenaire] Established diplomatic {} {} ↔ {} ({})", type, primary, secondary, agreement.getId());
      return agreement;
   }

   private boolean validateConsistencyForEstablish(ServerLevel level, AgreementType type, FactionId primary, FactionId secondary) {
      if (type == AgreementType.EMBARGO) {
         if (this.isAllied(primary, secondary) || this.hasActiveAgreement(AgreementType.DEFENSIVE_PACT, primary, secondary)) {
            LOGGER.warn("[Millenaire] Cannot impose embargo while alliance/defensive pact exists between {} and {}", primary, secondary);
            return false;
         }
      }

      if (type == AgreementType.ALLIANCE || type == AgreementType.DEFENSIVE_PACT || type == AgreementType.TRADE_AGREEMENT) {
         if (this.isTradeRestricted(primary, secondary) && type == AgreementType.TRADE_AGREEMENT) {
            LOGGER.warn("[Millenaire] Cannot create trade agreement while embargo exists between {} and {}", primary, secondary);
            return false;
         }

         if ((type == AgreementType.ALLIANCE || type == AgreementType.DEFENSIVE_PACT) && this.isTradeRestricted(primary, secondary)) {
            LOGGER.warn("[Millenaire] Cannot create {} while embargo exists between {} and {}", type, primary, secondary);
            return false;
         }
      }

      return true;
   }

   private void resolveConflictsBeforeEstablish(ServerLevel level, AgreementType type, FactionId primary, FactionId secondary, long gameTime) {
      if (type == AgreementType.EMBARGO) {
         DiplomaticAgreement trade = this.getActiveAgreement(AgreementType.TRADE_AGREEMENT, primary, secondary);
         if (trade != null) {
            this.terminateInternal(level, trade, AgreementTerminationReason.CONFLICT, gameTime, false);
         }
      }

      if (type == AgreementType.ALLIANCE) {
         DiplomaticAgreement nap = this.getActiveAgreement(AgreementType.NON_AGGRESSION, primary, secondary);
         if (nap != null) {
            this.terminateInternal(level, nap, AgreementTerminationReason.SUPERSEDED, gameTime, false);
         }
      }
   }

   private void terminateInternal(
      ServerLevel level, DiplomaticAgreement agreement, AgreementTerminationReason reason, long gameTime, boolean applyRelationshipPenalty
   ) {
      if (!agreement.isActive()) {
         return;
      }

      agreement.terminate(gameTime, reason);
      this.unindexActive(agreement);
      DiplomaticEventType eventType = reason == AgreementTerminationReason.EXPIRED
         ? DiplomaticEventType.forExpiration(agreement.getType())
         : DiplomaticEventType.forTermination(agreement.getType());
      DiplomaticHistoryEntry entry = new DiplomaticHistoryEntry(
         gameTime, eventType, agreement.getPrimaryFactionId(), agreement.getSecondaryFactionId(), agreement.getId(), agreement.getType(), reason
      );
      agreement.recordHistory(entry);
      this.appendGlobalHistory(entry);
      if (applyRelationshipPenalty && reason != AgreementTerminationReason.EXPIRED && reason != AgreementTerminationReason.SUPERSEDED && reason != AgreementTerminationReason.FACTION_REMOVED) {
         this.applyRelationshipInfluence(level, agreement.getType(), agreement.getPrimaryFactionId(), agreement.getSecondaryFactionId(), false, gameTime);
      }

      this.markDirty.run();
      LOGGER.info(
         "[Millenaire] Terminated diplomatic {} {} ↔ {} reason={} ({})",
         agreement.getType(),
         agreement.getPrimaryFactionId(),
         agreement.getSecondaryFactionId(),
         reason,
         agreement.getId()
      );
   }

   private void applyRelationshipInfluence(ServerLevel level, AgreementType type, FactionId a, FactionId b, boolean established, long gameTime) {
      int magnitude = switch (type) {
         case PEACE -> DiplomaticConfig.RELATIONSHIP_DELTA_PEACE;
         case NON_AGGRESSION -> DiplomaticConfig.RELATIONSHIP_DELTA_NON_AGGRESSION;
         case TRADE_AGREEMENT -> DiplomaticConfig.RELATIONSHIP_DELTA_TRADE_AGREEMENT;
         case DEFENSIVE_PACT -> DiplomaticConfig.RELATIONSHIP_DELTA_DEFENSIVE_PACT;
         case ALLIANCE -> DiplomaticConfig.RELATIONSHIP_DELTA_ALLIANCE;
         case EMBARGO -> Math.abs(DiplomaticConfig.RELATIONSHIP_DELTA_EMBARGO);
      };
      int delta = DiplomaticConfig.clampRelationshipDelta(established ? magnitude : -Math.min(magnitude, Math.abs(DiplomaticConfig.RELATIONSHIP_DELTA_TERMINATION)));
      if (type == AgreementType.EMBARGO) {
         delta = DiplomaticConfig.clampRelationshipDelta(established ? DiplomaticConfig.RELATIONSHIP_DELTA_EMBARGO : Math.abs(DiplomaticConfig.RELATIONSHIP_DELTA_EMBARGO));
      }

      if (delta == 0) {
         return;
      }

      RelationshipChangeReason reason = switch (type) {
         case ALLIANCE, DEFENSIVE_PACT -> RelationshipChangeReason.ALLIANCE;
         case PEACE, NON_AGGRESSION -> RelationshipChangeReason.PEACE;
         case TRADE_AGREEMENT -> RelationshipChangeReason.TRADE;
         case EMBARGO -> RelationshipChangeReason.INSULT;
      };
      RelationshipSavedData.get(level).getRelationshipManager().modifyRelationship(level, a, b, delta, reason, gameTime);
      RelationshipSavedData.get(level).getRelationshipManager().modifyRelationship(level, b, a, delta, reason, gameTime);
   }

   private boolean validateParticipants(ServerLevel level, FactionId a, FactionId b) {
      if (a == null || b == null) {
         LOGGER.warn("[Millenaire] Diplomatic operation rejected — null faction");
         return false;
      }

      if (a.equals(b)) {
         LOGGER.warn("[Millenaire] Diplomatic operation rejected — self diplomacy {}", a);
         return false;
      }

      FactionManager factions = FactionSavedData.get(level).getFactionManager();
      if (!factions.exists(a) || !factions.exists(b)) {
         LOGGER.warn("[Millenaire] Diplomatic operation rejected — missing faction {} / {}", a, b);
         return false;
      }

      return true;
   }

   private Collection<FactionId> collectPartners(FactionId factionId, AgreementType type) {
      if (factionId == null) {
         return List.of();
      }

      List<FactionId> result = new ArrayList<>();

      for (DiplomaticAgreement agreement : this.getActiveAgreementsInvolving(factionId)) {
         if (agreement.getType() == type) {
            result.add(agreement.otherParticipant(factionId));
         }
      }

      return Collections.unmodifiableList(result);
   }

   private void indexFaction(FactionId factionId, AgreementId agreementId) {
      this.byFaction.computeIfAbsent(factionId, ignored -> new LinkedHashSet<>()).add(agreementId);
   }

   private void unindexActive(DiplomaticAgreement agreement) {
      DiplomaticPairKey key = DiplomaticPairKey.forType(agreement.getType(), agreement.getPrimaryFactionId(), agreement.getSecondaryFactionId());
      Map<DiplomaticPairKey, AgreementId> map = this.activeByType.get(agreement.getType());
      AgreementId current = map.get(key);
      if (agreement.getId().equals(current)) {
         map.remove(key);
      }
   }

   private void appendGlobalHistory(DiplomaticHistoryEntry entry) {
      this.globalHistory.addLast(entry);

      while (this.globalHistory.size() > DiplomaticConfig.MAX_DIPLOMATIC_HISTORY_ENTRIES) {
         this.globalHistory.removeFirst();
      }
   }

   private static DiplomaticStanding standingFor(AgreementType type) {
      return switch (type) {
         case PEACE -> DiplomaticStanding.PEACE;
         case NON_AGGRESSION -> DiplomaticStanding.NON_AGGRESSION;
         case TRADE_AGREEMENT -> DiplomaticStanding.TRADE_AGREEMENT;
         case DEFENSIVE_PACT -> DiplomaticStanding.DEFENSIVE_PACT;
         case ALLIANCE -> DiplomaticStanding.ALLIANCE;
         case EMBARGO -> DiplomaticStanding.EMBARGO;
      };
   }
}
