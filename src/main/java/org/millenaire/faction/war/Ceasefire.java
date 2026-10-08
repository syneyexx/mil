package org.millenaire.faction.war;

import java.util.Objects;

public final class Ceasefire {
   private final long startGameTime;
   private final long expirationGameTime;
   private boolean active = true;
   private long endedGameTime;

   public Ceasefire(long startGameTime, long expirationGameTime) {
      this.startGameTime = startGameTime;
      this.expirationGameTime = expirationGameTime;
   }

   public long getStartGameTime() {
      return this.startGameTime;
   }

   public long getExpirationGameTime() {
      return this.expirationGameTime;
   }

   public boolean isActive() {
      return this.active;
   }

   public long getEndedGameTime() {
      return this.endedGameTime;
   }

   public boolean isExpired(long gameTime) {
      return this.expirationGameTime > 0L && gameTime >= this.expirationGameTime;
   }

   void end(long gameTime) {
      this.active = false;
      this.endedGameTime = gameTime;
   }

   void loadState(boolean active, long endedGameTime) {
      this.active = active;
      this.endedGameTime = Math.max(0L, endedGameTime);
   }

   @Override
   public String toString() {
      return "Ceasefire{start=" + this.startGameTime + ", expiration=" + this.expirationGameTime + ", active=" + this.active + "}";
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }

      if (!(obj instanceof Ceasefire other)) {
         return false;
      }

      return this.startGameTime == other.startGameTime && this.expirationGameTime == other.expirationGameTime;
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.startGameTime, this.expirationGameTime);
   }
}
