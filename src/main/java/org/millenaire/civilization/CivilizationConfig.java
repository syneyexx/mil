package org.millenaire.civilization;

import org.millenaire.TickConstants;

/**
 * Hard-coded cadence / budget defaults for civilization orchestration.
 * Runtime toggles live in MillenaireServerConfig; these are stable algorithm constants.
 */
public final class CivilizationConfig {
   /** Schema 4: trade ops carry unresolved stack evidence + finalization-aware recovery. */
   public static final int SCHEMA_VERSION = 4;
   public static final int TRADE_OPERATION_RETENTION = 256;
   public static final long STAGE_MIGRATION_INTERVAL = 2400L;
   public static final long STAGE_COLONIZATION_INTERVAL = 6000L;
   public static final int MAX_COLONIZATION_CANDIDATES_PER_CYCLE = 4;
   public static final int MAX_COLONIZATION_OPS_PER_CYCLE = 1;
   public static final int MAX_MIGRATION_SCANS_PER_CYCLE = 32;

   /** Stagger offsets so stages do not all land on the same tick. */
   public static final long STAGE_EVENT_INGEST_INTERVAL = 20L;
   public static final long STAGE_MEMBERSHIP_INTERVAL = 100L;
   public static final long STAGE_ECONOMY_INTERVAL = 200L;
   public static final long STAGE_POLITICS_INTERVAL = 400L;
   public static final long STAGE_DIPLOMACY_INTERVAL = 200L;
   public static final long STAGE_MILITARY_INTERVAL = 200L;
   public static final long STAGE_WAR_INTERVAL = 100L;
   public static final long STAGE_DEMOGRAPHICS_INTERVAL = 1200L;
   public static final long STAGE_DEVELOPMENT_INTERVAL = 1200L;
   public static final long STAGE_HISTORY_INTERVAL = 100L;
   public static final long STAGE_OFFSCREEN_INTERVAL = 600L;

   public static final int MAX_FACTIONS_PER_STAGE = 8;
   public static final int MAX_EVENTS_PUBLISHED_PER_TICK = 12;
   public static final int MAX_TRADE_OPS_PER_CYCLE = 4;
   public static final int MAX_MIGRATIONS_PER_CYCLE = 2;
   public static final int MAX_OFFSCREEN_CATCHUP_DAYS = 7;
   public static final long MAX_OFFSCREEN_CATCHUP_TICKS = MAX_OFFSCREEN_CATCHUP_DAYS * TickConstants.DAY_LENGTH;
   public static final int MAX_EVENT_PROPAGATION_DEPTH = 3;
   public static final int MIGRATION_OPERATION_RETENTION = 256;

   private CivilizationConfig() {
   }
}
