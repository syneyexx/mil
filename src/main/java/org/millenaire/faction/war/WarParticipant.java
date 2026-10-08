package org.millenaire.faction.war;

import java.util.Objects;
import org.millenaire.faction.FactionId;

public final class WarParticipant {
   private final FactionId factionId;
   private final WarSideId side;
   private final JoinWarReason joinReason;
   private final long joinedGameTime;
   private boolean active = true;
   private long leftGameTime;
   private int casualtiesSuffered;
   private int casualtiesInflicted;
   private int warExhaustion;

   public WarParticipant(FactionId factionId, WarSideId side, JoinWarReason joinReason, long joinedGameTime) {
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.side = Objects.requireNonNull(side, "side");
      this.joinReason = joinReason == null ? JoinWarReason.VOLUNTARY : joinReason;
      this.joinedGameTime = joinedGameTime;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   public WarSideId getSide() {
      return this.side;
   }

   public JoinWarReason getJoinReason() {
      return this.joinReason;
   }

   public long getJoinedGameTime() {
      return this.joinedGameTime;
   }

   public boolean isActive() {
      return this.active;
   }

   public long getLeftGameTime() {
      return this.leftGameTime;
   }

   public int getCasualtiesSuffered() {
      return this.casualtiesSuffered;
   }

   public int getCasualtiesInflicted() {
      return this.casualtiesInflicted;
   }

   public int getWarExhaustion() {
      return this.warExhaustion;
   }

   void markLeft(long gameTime) {
      this.active = false;
      this.leftGameTime = gameTime;
   }

   void addCasualtiesSuffered(int amount) {
      if (amount > 0) {
         this.casualtiesSuffered = WarConfig.clampNonNegative(this.casualtiesSuffered + amount);
      }
   }

   void addCasualtiesInflicted(int amount) {
      if (amount > 0) {
         this.casualtiesInflicted = WarConfig.clampNonNegative(this.casualtiesInflicted + amount);
      }
   }

   void setWarExhaustion(int value) {
      this.warExhaustion = WarConfig.clampPercent(value);
   }

   void loadState(boolean active, long leftGameTime, int casualtiesSuffered, int casualtiesInflicted, int warExhaustion) {
      this.active = active;
      this.leftGameTime = Math.max(0L, leftGameTime);
      this.casualtiesSuffered = WarConfig.clampNonNegative(casualtiesSuffered);
      this.casualtiesInflicted = WarConfig.clampNonNegative(casualtiesInflicted);
      this.warExhaustion = WarConfig.clampPercent(warExhaustion);
   }
}
