package org.millenaire.activity;

import net.minecraft.server.MinecraftServer;
import org.millenaire.config.MillenaireServerConfig;

/**
 * Whole-server MSPT pressure with EWMA + ring buffer. Percentiles computed at most once/sec.
 */
public final class ActivityPressureMonitor {
   private static final ActivityPressureMonitor INSTANCE = new ActivityPressureMonitor();

   private final double[] ring = new double[200];
   private int ringSize;
   private int ringIndex;
   private double ewma;
   private boolean ewmaInit;
   private ActivityPressureState state = ActivityPressureState.HEALTHY;
   private int recoveryStreak;
   private long lastSpikeBlockUntilTick;
   private long lastPercentileComputeMs;
   private double cachedP95;
   private int sampleCount;
   private long serverTickCounter;

   private ActivityPressureMonitor() {}

   public static ActivityPressureMonitor get() {
      return INSTANCE;
   }

   public void onServerTick(MinecraftServer server) {
      this.serverTickCounter++;
      double mspt = server.getCurrentSmoothedTickTime();
      // Ignore pause/start zeros as healthy proof; still record positive values.
      if (mspt > 0.01) {
         this.ring[this.ringIndex] = mspt;
         this.ringIndex = (this.ringIndex + 1) % this.ring.length;
         if (this.ringSize < this.ring.length) {
            this.ringSize++;
         }
         this.sampleCount++;
         double alpha = 0.08;
         if (!this.ewmaInit) {
            this.ewma = mspt;
            this.ewmaInit = true;
         } else {
            this.ewma = this.ewma * (1.0 - alpha) + mspt * alpha;
         }
         if (mspt > 50.0) {
            this.lastSpikeBlockUntilTick = this.serverTickCounter + MillenaireServerConfig.SERVER.regionalActivitySpikeBlockTicks.getAsInt();
         }
      }
      long nowMs = System.nanoTime() / 1_000_000L;
      if (nowMs - this.lastPercentileComputeMs >= 1000L) {
         this.cachedP95 = computeP95();
         this.lastPercentileComputeMs = nowMs;
      }
      updateState();
   }

   private void updateState() {
      double softMspt = MillenaireServerConfig.SERVER.regionalActivitySoftMspt.get();
      double softP95 = MillenaireServerConfig.SERVER.regionalActivitySoftP95.get();
      double hardMspt = MillenaireServerConfig.SERVER.regionalActivityHardMspt.get();
      double hardP95 = MillenaireServerConfig.SERVER.regionalActivityHardP95.get();
      ActivityPressureState desired;
      if (this.ewma >= hardMspt || this.cachedP95 >= hardP95) {
         desired = ActivityPressureState.HARD;
      } else if (this.ewma >= softMspt || this.cachedP95 >= softP95) {
         desired = ActivityPressureState.SOFT;
      } else {
         desired = ActivityPressureState.HEALTHY;
      }
      if (desired.ordinal() > this.state.ordinal()) {
         this.state = desired;
         this.recoveryStreak = 0;
         return;
      }
      if (desired.ordinal() < this.state.ordinal()) {
         double margin = 3.0;
         boolean under = switch (this.state) {
            case HARD -> this.ewma < hardMspt - margin && this.cachedP95 < hardP95 - margin;
            case SOFT -> this.ewma < softMspt - margin && this.cachedP95 < softP95 - margin;
            case HEALTHY -> true;
         };
         if (under) {
            this.recoveryStreak++;
            if (this.recoveryStreak >= MillenaireServerConfig.SERVER.regionalActivityRecoveryTicks.getAsInt()) {
               this.state = this.state == ActivityPressureState.HARD ? ActivityPressureState.SOFT : ActivityPressureState.HEALTHY;
               this.recoveryStreak = 0;
            }
         } else {
            this.recoveryStreak = 0;
         }
      }
   }

   private double computeP95() {
      if (this.ringSize <= 0) {
         return 0.0;
      }
      double[] copy = new double[this.ringSize];
      for (int i = 0; i < this.ringSize; i++) {
         copy[i] = this.ring[i];
      }
      java.util.Arrays.sort(copy);
      int idx = Math.min(copy.length - 1, (int) Math.ceil(copy.length * 0.95) - 1);
      return copy[Math.max(0, idx)];
   }

   public ActivityPressureState state() {
      return this.state;
   }

   public double ewma() {
      return this.ewma;
   }

   public double p95() {
      return this.cachedP95;
   }

   public int sampleCount() {
      return this.sampleCount;
   }

   public boolean spikeBlocksAdmission() {
      return this.serverTickCounter < this.lastSpikeBlockUntilTick;
   }

   public long serverTickCounter() {
      return this.serverTickCounter;
   }

   public void resetRuntime() {
      this.ringSize = 0;
      this.ringIndex = 0;
      this.ewma = 0;
      this.ewmaInit = false;
      this.state = ActivityPressureState.HEALTHY;
      this.recoveryStreak = 0;
      this.lastSpikeBlockUntilTick = 0;
      this.cachedP95 = 0;
      this.sampleCount = 0;
      this.serverTickCounter = 0;
   }
}
