package org.millenaire.worldboss;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ActiveWorldBossEvent {
   private UUID eventId;
   private ResourceLocation bossId;
   private WorldBossEventState state = WorldBossEventState.SELECTING;
   @Nullable
   private UUID bossEntityUuid;
   @Nullable
   private ResourceKey<Level> dimension;
   @Nullable
   private BlockPos spawnPos;
   @Nullable
   private UUID anchorPlayerId;
   @Nullable
   private UUID provokerPlayerId;
   private final Set<UUID> participants = new HashSet<>();
   private long startedGameTime;
   private long stateEnteredGameTime;
   private int spawnAttempts;
   private int spawnBackoffTicks;
   private boolean rewardsGranted;
   @Nullable
   private String lastOutcome;

   public ActiveWorldBossEvent(UUID eventId, ResourceLocation bossId, long gameTime) {
      this.eventId = eventId;
      this.bossId = bossId;
      this.startedGameTime = gameTime;
      this.stateEnteredGameTime = gameTime;
   }

   private ActiveWorldBossEvent() {
   }

   public UUID eventId() {
      return this.eventId;
   }

   public ResourceLocation bossId() {
      return this.bossId;
   }

   public WorldBossEventState state() {
      return this.state;
   }

   public void setState(WorldBossEventState state, long gameTime) {
      if (this.state != state) {
         this.state = state;
         this.stateEnteredGameTime = gameTime;
      }
   }

   @Nullable
   public UUID bossEntityUuid() {
      return this.bossEntityUuid;
   }

   public void setBossEntityUuid(@Nullable UUID bossEntityUuid) {
      this.bossEntityUuid = bossEntityUuid;
   }

   @Nullable
   public ResourceKey<Level> dimension() {
      return this.dimension;
   }

   public void setDimension(@Nullable ResourceKey<Level> dimension) {
      this.dimension = dimension;
   }

   @Nullable
   public BlockPos spawnPos() {
      return this.spawnPos;
   }

   public void setSpawnPos(@Nullable BlockPos spawnPos) {
      this.spawnPos = spawnPos;
   }

   @Nullable
   public UUID anchorPlayerId() {
      return this.anchorPlayerId;
   }

   public void setAnchorPlayerId(@Nullable UUID anchorPlayerId) {
      this.anchorPlayerId = anchorPlayerId;
   }

   @Nullable
   public UUID provokerPlayerId() {
      return this.provokerPlayerId;
   }

   public void setProvokerPlayerId(@Nullable UUID provokerPlayerId) {
      this.provokerPlayerId = provokerPlayerId;
   }

   public Set<UUID> participants() {
      return this.participants;
   }

   public void addParticipant(UUID id) {
      if (id != null) {
         this.participants.add(id);
      }
   }

   public long startedGameTime() {
      return this.startedGameTime;
   }

   public long stateEnteredGameTime() {
      return this.stateEnteredGameTime;
   }

   public int spawnAttempts() {
      return this.spawnAttempts;
   }

   public void incrementSpawnAttempts() {
      this.spawnAttempts++;
   }

   public int spawnBackoffTicks() {
      return this.spawnBackoffTicks;
   }

   public void setSpawnBackoffTicks(int spawnBackoffTicks) {
      this.spawnBackoffTicks = Math.max(0, spawnBackoffTicks);
   }

   public boolean rewardsGranted() {
      return this.rewardsGranted;
   }

   public void setRewardsGranted(boolean rewardsGranted) {
      this.rewardsGranted = rewardsGranted;
   }

   @Nullable
   public String lastOutcome() {
      return this.lastOutcome;
   }

   public void setLastOutcome(@Nullable String lastOutcome) {
      this.lastOutcome = lastOutcome;
   }

   public CompoundTag save(Provider registries) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("eventId", this.eventId);
      tag.putString("bossId", this.bossId.toString());
      tag.putString("state", this.state.name());
      if (this.bossEntityUuid != null) {
         tag.putUUID("bossEntityUuid", this.bossEntityUuid);
      }
      if (this.dimension != null) {
         tag.putString("dimension", this.dimension.location().toString());
      }
      if (this.spawnPos != null) {
         tag.put("spawnPos", new IntArrayTag(new int[]{this.spawnPos.getX(), this.spawnPos.getY(), this.spawnPos.getZ()}));
      }
      if (this.anchorPlayerId != null) {
         tag.putUUID("anchorPlayerId", this.anchorPlayerId);
      }
      if (this.provokerPlayerId != null) {
         tag.putUUID("provokerPlayerId", this.provokerPlayerId);
      }
      ListTag parts = new ListTag();
      for (UUID id : this.participants) {
         parts.add(StringTag.valueOf(id.toString()));
      }
      tag.put("participants", parts);
      tag.putLong("startedGameTime", this.startedGameTime);
      tag.putLong("stateEnteredGameTime", this.stateEnteredGameTime);
      tag.putInt("spawnAttempts", this.spawnAttempts);
      tag.putInt("spawnBackoffTicks", this.spawnBackoffTicks);
      tag.putBoolean("rewardsGranted", this.rewardsGranted);
      if (this.lastOutcome != null) {
         tag.putString("lastOutcome", this.lastOutcome);
      }
      return tag;
   }

   public static ActiveWorldBossEvent load(CompoundTag tag) {
      ActiveWorldBossEvent event = new ActiveWorldBossEvent();
      event.eventId = tag.getUUID("eventId");
      event.bossId = ResourceLocation.parse(tag.getString("bossId"));
      try {
         event.state = WorldBossEventState.valueOf(tag.getString("state"));
      } catch (IllegalArgumentException e) {
         event.state = WorldBossEventState.FAILED;
      }
      if (tag.hasUUID("bossEntityUuid")) {
         event.bossEntityUuid = tag.getUUID("bossEntityUuid");
      }
      if (tag.contains("dimension")) {
         event.dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dimension")));
      }
      if (tag.contains("spawnPos")) {
         int[] arr = tag.getIntArray("spawnPos");
         if (arr.length == 3) {
            event.spawnPos = new BlockPos(arr[0], arr[1], arr[2]);
         }
      }
      if (tag.hasUUID("anchorPlayerId")) {
         event.anchorPlayerId = tag.getUUID("anchorPlayerId");
      }
      if (tag.hasUUID("provokerPlayerId")) {
         event.provokerPlayerId = tag.getUUID("provokerPlayerId");
      }
      ListTag parts = tag.getList("participants", Tag.TAG_STRING);
      for (int i = 0; i < parts.size(); i++) {
         try {
            event.participants.add(UUID.fromString(parts.getString(i)));
         } catch (IllegalArgumentException ignored) {
         }
      }
      event.startedGameTime = tag.getLong("startedGameTime");
      event.stateEnteredGameTime = tag.getLong("stateEnteredGameTime");
      event.spawnAttempts = tag.getInt("spawnAttempts");
      event.spawnBackoffTicks = tag.getInt("spawnBackoffTicks");
      event.rewardsGranted = tag.getBoolean("rewardsGranted");
      if (tag.contains("lastOutcome")) {
         event.lastOutcome = tag.getString("lastOutcome");
      }
      return event;
   }
}
