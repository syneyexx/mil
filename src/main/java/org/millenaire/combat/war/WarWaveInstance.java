package org.millenaire.combat.war;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/** One scheduled/active wave within a {@link WarCampaign}. */
public final class WarWaveInstance {
   private final UUID waveId;
   private final UUID campaignId;
   private final UUID attackerVillageId;
   private final UUID defenderVillageId;
   private final int waveIndex;
   private final long startedGameTime;
   private int blocksDamaged;
   private boolean resolved;
   private boolean attackersWon;

   public WarWaveInstance(
      UUID waveId, UUID campaignId, UUID attackerVillageId, UUID defenderVillageId, int waveIndex, long startedGameTime
   ) {
      this.waveId = waveId;
      this.campaignId = campaignId;
      this.attackerVillageId = attackerVillageId;
      this.defenderVillageId = defenderVillageId;
      this.waveIndex = waveIndex;
      this.startedGameTime = startedGameTime;
   }

   public UUID waveId() {
      return this.waveId;
   }

   public UUID campaignId() {
      return this.campaignId;
   }

   public UUID attackerVillageId() {
      return this.attackerVillageId;
   }

   public UUID defenderVillageId() {
      return this.defenderVillageId;
   }

   public int waveIndex() {
      return this.waveIndex;
   }

   public long startedGameTime() {
      return this.startedGameTime;
   }

   public int blocksDamaged() {
      return this.blocksDamaged;
   }

   public void addBlocksDamaged(int n) {
      this.blocksDamaged += Math.max(0, n);
   }

   public boolean resolved() {
      return this.resolved;
   }

   public boolean attackersWon() {
      return this.attackersWon;
   }

   public void resolve(boolean attackersWon) {
      this.resolved = true;
      this.attackersWon = attackersWon;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("wave_id", this.waveId);
      tag.putUUID("campaign_id", this.campaignId);
      tag.putUUID("attacker", this.attackerVillageId);
      tag.putUUID("defender", this.defenderVillageId);
      tag.putInt("wave_index", this.waveIndex);
      tag.putLong("started", this.startedGameTime);
      tag.putInt("blocks_damaged", this.blocksDamaged);
      tag.putBoolean("resolved", this.resolved);
      tag.putBoolean("attackers_won", this.attackersWon);
      return tag;
   }

   public static WarWaveInstance load(CompoundTag tag) {
      WarWaveInstance w = new WarWaveInstance(
         tag.getUUID("wave_id"),
         tag.getUUID("campaign_id"),
         tag.getUUID("attacker"),
         tag.getUUID("defender"),
         tag.getInt("wave_index"),
         tag.getLong("started")
      );
      w.blocksDamaged = tag.getInt("blocks_damaged");
      w.resolved = tag.getBoolean("resolved");
      w.attackersWon = tag.getBoolean("attackers_won");
      return w;
   }

   @Nullable
   public static WarWaveInstance tryLoad(CompoundTag tag) {
      if (!tag.hasUUID("wave_id") || !tag.hasUUID("campaign_id")) {
         return null;
      }
      return load(tag);
   }
}
