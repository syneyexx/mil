package org.millenaire.civilization;

import java.util.concurrent.atomic.AtomicLong;

public final class CivilizationMetrics {
   public static final AtomicLong ORCHESTRATOR_TICKS = new AtomicLong();
   public static final AtomicLong STAGES_RUN = new AtomicLong();
   public static final AtomicLong EVENTS_PUBLISHED = new AtomicLong();
   public static final AtomicLong TRADE_OPS = new AtomicLong();
   public static final AtomicLong MIGRATIONS = new AtomicLong();
   public static final AtomicLong OFFSCREEN_CATCHUPS = new AtomicLong();
   public static final AtomicLong STAGE_FAILURES = new AtomicLong();

   private CivilizationMetrics() {
   }

   public static String snapshot() {
      return "orch="
         + ORCHESTRATOR_TICKS.get()
         + " stages="
         + STAGES_RUN.get()
         + " events="
         + EVENTS_PUBLISHED.get()
         + " trades="
         + TRADE_OPS.get()
         + " migrations="
         + MIGRATIONS.get()
         + " offscreen="
         + OFFSCREEN_CATCHUPS.get()
         + " fails="
         + STAGE_FAILURES.get();
   }
}
