package org.millenaire.village;

final class WaypointRebuildThrottle {
   static final long BACKOFF_WINDOW_TICKS = 600L;
   static final int BACKOFF_CAP = 6;
   static final long BACKOFF_MAX_TICKS = 1200L;
   static final int IDENTICAL_RESULT_CLAMP = 3;
   private long lastRebuildTick = Long.MIN_VALUE;
   private long lastRequestTick = Long.MIN_VALUE;
   private int backoff;
   private boolean dirty = true;
   private long lastResultSignature;
   private boolean hasResultSignature;
   private int identicalResultRun;
   private boolean hysteresisClamped;

   void markDirty() {
      this.dirty = true;
   }

   void markDirtyStructural() {
      this.dirty = true;
      this.identicalResultRun = 0;
      if (this.hysteresisClamped) {
         this.backoff = 0;
         this.hysteresisClamped = false;
      }
   }

   boolean isDirty() {
      return this.dirty;
   }

   void onRebuilt(long now, long resultSignature) {
      this.lastRebuildTick = now;
      this.dirty = false;
      if (this.hasResultSignature && resultSignature == this.lastResultSignature) {
         this.identicalResultRun++;
      } else {
         this.identicalResultRun = 1;
      }

      if (this.identicalResultRun >= 3) {
         this.backoff = 6;
         this.hysteresisClamped = true;
      }

      this.lastResultSignature = resultSignature;
      this.hasResultSignature = true;
   }

   void onRebuilt(long now) {
      this.onRebuilt(now, now);
   }

   boolean tryAcquire(long now, long minIntervalTicks) {
      if (!this.dirty) {
         this.lastRequestTick = now;
         return false;
      }

      long sinceLastRebuild = this.lastRebuildTick == Long.MIN_VALUE ? Long.MAX_VALUE : now - this.lastRebuildTick;
      long sinceLastRequest = this.lastRequestTick == Long.MIN_VALUE ? Long.MAX_VALUE : now - this.lastRequestTick;
      if (sinceLastRequest > 600L) {
         this.backoff = 0;
      }

      this.lastRequestTick = now;
      long effectiveInterval = Math.min(minIntervalTicks << Math.min(this.backoff, 6), 1200L);
      if (sinceLastRebuild < effectiveInterval) {
         return false;
      }

      if (this.backoff < 6) {
         this.backoff++;
      }

      return true;
   }
}
