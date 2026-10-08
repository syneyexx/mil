package org.millenaire.faction.politics;

import java.util.Objects;
import javax.annotation.Nullable;

public final class GovernmentPolicy {
   private final PolicyType type;
   private PolicyStance stance;
   private long lastChangedGameTime;
   @Nullable
   private String note;

   public GovernmentPolicy(PolicyType type, PolicyStance stance, long gameTime) {
      this.type = Objects.requireNonNull(type, "type");
      this.stance = stance == null ? PolicyStance.NORMAL : stance;
      this.lastChangedGameTime = gameTime;
   }

   public PolicyType getType() {
      return this.type;
   }

   public PolicyStance getStance() {
      return this.stance;
   }

   public long getLastChangedGameTime() {
      return this.lastChangedGameTime;
   }

   @Nullable
   public String getNote() {
      return this.note;
   }

   void setStance(PolicyStance stance, long gameTime) {
      this.stance = stance == null ? PolicyStance.NORMAL : stance;
      this.lastChangedGameTime = gameTime;
   }

   void setNote(@Nullable String note) {
      this.note = note;
   }

   void loadState(PolicyStance stance, long lastChanged, @Nullable String note) {
      this.stance = stance == null ? PolicyStance.NORMAL : stance;
      this.lastChangedGameTime = Math.max(0L, lastChanged);
      this.note = note;
   }
}
