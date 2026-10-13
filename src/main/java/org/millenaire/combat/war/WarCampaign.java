package org.millenaire.combat.war;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;

/** Mutable runtime + persisted war campaign between two cultures. */
public final class WarCampaign {
   public enum Phase {
      WARNING,
      ACTIVE,
      WAVE_FIGHT,
      ARMISTICE,
      ENDED
   }

   private final UUID id;
   private ResourceLocation sideA;
   private ResourceLocation sideB;
   private Phase phase;
   @Nullable
   private UUID frontVillageId;
   @Nullable
   private UUID attackerVillageId;
   @Nullable
   private UUID defenderVillageId;
   private int waveIndex;
   private int wavesTotal;
   private int warScoreA;
   private int warScoreB;
   private long nextWaveGameTime;
   @Nullable
   private UUID activeWaveId;
   private final List<UUID> sabotageTargets = new ArrayList<>();
   private boolean playerWar;
   private long declaredGameTime;

   public WarCampaign(UUID id, ResourceLocation sideA, ResourceLocation sideB, int wavesTotal, boolean playerWar, long declaredGameTime) {
      this.id = id;
      this.sideA = sideA;
      this.sideB = sideB;
      this.phase = Phase.WARNING;
      this.wavesTotal = Math.max(1, wavesTotal);
      this.waveIndex = 0;
      this.playerWar = playerWar;
      this.declaredGameTime = declaredGameTime;
      this.nextWaveGameTime = declaredGameTime;
   }

   public UUID id() {
      return this.id;
   }

   public ResourceLocation sideA() {
      return this.sideA;
   }

   public ResourceLocation sideB() {
      return this.sideB;
   }

   public Phase phase() {
      return this.phase;
   }

   public void setPhase(Phase phase) {
      this.phase = phase == null ? Phase.ENDED : phase;
   }

   @Nullable
   public UUID frontVillageId() {
      return this.frontVillageId;
   }

   public void setFrontVillageId(@Nullable UUID frontVillageId) {
      this.frontVillageId = frontVillageId;
   }

   @Nullable
   public UUID attackerVillageId() {
      return this.attackerVillageId;
   }

   public void setAttackerVillageId(@Nullable UUID attackerVillageId) {
      this.attackerVillageId = attackerVillageId;
   }

   @Nullable
   public UUID defenderVillageId() {
      return this.defenderVillageId;
   }

   public void setDefenderVillageId(@Nullable UUID defenderVillageId) {
      this.defenderVillageId = defenderVillageId;
   }

   public int waveIndex() {
      return this.waveIndex;
   }

   public void setWaveIndex(int waveIndex) {
      this.waveIndex = Math.max(0, waveIndex);
   }

   public int wavesTotal() {
      return this.wavesTotal;
   }

   public void setWavesTotal(int wavesTotal) {
      this.wavesTotal = Math.max(1, wavesTotal);
   }

   public int warScoreA() {
      return this.warScoreA;
   }

   public int warScoreB() {
      return this.warScoreB;
   }

   public void addScoreA(int delta) {
      this.warScoreA += delta;
   }

   public void addScoreB(int delta) {
      this.warScoreB += delta;
   }

   public long nextWaveGameTime() {
      return this.nextWaveGameTime;
   }

   public void setNextWaveGameTime(long nextWaveGameTime) {
      this.nextWaveGameTime = nextWaveGameTime;
   }

   @Nullable
   public UUID activeWaveId() {
      return this.activeWaveId;
   }

   public void setActiveWaveId(@Nullable UUID activeWaveId) {
      this.activeWaveId = activeWaveId;
   }

   public List<UUID> sabotageTargets() {
      return this.sabotageTargets;
   }

   public void setSabotageTargets(List<UUID> targets) {
      this.sabotageTargets.clear();
      if (targets != null) {
         this.sabotageTargets.addAll(targets);
      }
   }

   public boolean playerWar() {
      return this.playerWar;
   }

   public long declaredGameTime() {
      return this.declaredGameTime;
   }

   public boolean isOpen() {
      return this.phase != Phase.ENDED;
   }

   public boolean involvesCulture(ResourceLocation culture) {
      return culture != null && (culture.equals(this.sideA) || culture.equals(this.sideB));
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.id);
      tag.putString("side_a", this.sideA.toString());
      tag.putString("side_b", this.sideB.toString());
      tag.putString("phase", this.phase.name());
      if (this.frontVillageId != null) {
         tag.putUUID("front_village", this.frontVillageId);
      }
      if (this.attackerVillageId != null) {
         tag.putUUID("attacker_village", this.attackerVillageId);
      }
      if (this.defenderVillageId != null) {
         tag.putUUID("defender_village", this.defenderVillageId);
      }
      tag.putInt("wave_index", this.waveIndex);
      tag.putInt("waves_total", this.wavesTotal);
      tag.putInt("score_a", this.warScoreA);
      tag.putInt("score_b", this.warScoreB);
      tag.putLong("next_wave", this.nextWaveGameTime);
      if (this.activeWaveId != null) {
         tag.putUUID("active_wave", this.activeWaveId);
      }
      tag.putBoolean("player_war", this.playerWar);
      tag.putLong("declared", this.declaredGameTime);
      ListTag targets = new ListTag();
      for (UUID u : this.sabotageTargets) {
         targets.add(StringTag.valueOf(u.toString()));
      }
      tag.put("sabotage_targets", targets);
      return tag;
   }

   public static WarCampaign load(CompoundTag tag) {
      UUID id = tag.getUUID("id");
      ResourceLocation sideA = ResourceLocation.parse(tag.getString("side_a"));
      ResourceLocation sideB = ResourceLocation.parse(tag.getString("side_b"));
      int wavesTotal = tag.contains("waves_total") ? tag.getInt("waves_total") : 3;
      boolean playerWar = tag.getBoolean("player_war");
      long declared = tag.getLong("declared");
      WarCampaign c = new WarCampaign(id, sideA, sideB, wavesTotal, playerWar, declared);
      try {
         c.phase = Phase.valueOf(tag.getString("phase"));
      } catch (IllegalArgumentException e) {
         c.phase = Phase.ENDED;
      }
      if (tag.hasUUID("front_village")) {
         c.frontVillageId = tag.getUUID("front_village");
      }
      if (tag.hasUUID("attacker_village")) {
         c.attackerVillageId = tag.getUUID("attacker_village");
      }
      if (tag.hasUUID("defender_village")) {
         c.defenderVillageId = tag.getUUID("defender_village");
      }
      c.waveIndex = tag.getInt("wave_index");
      c.warScoreA = tag.getInt("score_a");
      c.warScoreB = tag.getInt("score_b");
      c.nextWaveGameTime = tag.getLong("next_wave");
      if (tag.hasUUID("active_wave")) {
         c.activeWaveId = tag.getUUID("active_wave");
      }
      if (tag.contains("sabotage_targets", 9)) {
         ListTag targets = tag.getList("sabotage_targets", 8);
         for (int i = 0; i < targets.size(); i++) {
            try {
               c.sabotageTargets.add(UUID.fromString(targets.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
         }
      }
      return c;
   }
}
