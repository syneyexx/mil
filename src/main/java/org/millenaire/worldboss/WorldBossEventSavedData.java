package org.millenaire.worldboss;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class WorldBossEventSavedData extends SavedData {
   private static final String DATA_NAME = "millenaire_world_boss_events";
   public static final int CURRENT_VERSION = 1;
   private static final int RECENT_PLAYER_HISTORY = 8;

   private int elapsedActiveTicks;
   private int nextIntervalTicks;
   private boolean intervalChosen;
   @Nullable
   private ActiveWorldBossEvent active;
   @Nullable
   private ResourceLocation lastBossId;
   @Nullable
   private String lastOutcome;
   private long lastTerminalGameTime;
   private final Deque<UUID> recentAnchorPlayers = new ArrayDeque<>();

   private WorldBossEventSavedData() {
   }

   public static Factory<WorldBossEventSavedData> factory() {
      return new Factory<>(WorldBossEventSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static WorldBossEventSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      ServerLevel storage = overworld != null ? overworld : level;
      return storage.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public int elapsedActiveTicks() {
      return this.elapsedActiveTicks;
   }

   public void setElapsedActiveTicks(int elapsedActiveTicks) {
      this.elapsedActiveTicks = Math.max(0, elapsedActiveTicks);
      this.setDirty();
   }

   public int nextIntervalTicks() {
      return this.nextIntervalTicks;
   }

   public void setNextIntervalTicks(int nextIntervalTicks) {
      this.nextIntervalTicks = Math.max(1, nextIntervalTicks);
      this.intervalChosen = true;
      this.setDirty();
   }

   public boolean intervalChosen() {
      return this.intervalChosen;
   }

   @Nullable
   public ActiveWorldBossEvent active() {
      return this.active;
   }

   public void setActive(@Nullable ActiveWorldBossEvent active) {
      this.active = active;
      this.setDirty();
   }

   public void clearActive() {
      this.active = null;
      this.setDirty();
   }

   @Nullable
   public ResourceLocation lastBossId() {
      return this.lastBossId;
   }

   public void setLastBossId(@Nullable ResourceLocation lastBossId) {
      this.lastBossId = lastBossId;
      this.setDirty();
   }

   @Nullable
   public String lastOutcome() {
      return this.lastOutcome;
   }

   public void setLastOutcome(@Nullable String lastOutcome, long gameTime) {
      this.lastOutcome = lastOutcome;
      this.lastTerminalGameTime = gameTime;
      this.setDirty();
   }

   public long lastTerminalGameTime() {
      return this.lastTerminalGameTime;
   }

   public Deque<UUID> recentAnchorPlayers() {
      return this.recentAnchorPlayers;
   }

   public void rememberAnchor(UUID playerId) {
      if (playerId == null) {
         return;
      }
      this.recentAnchorPlayers.remove(playerId);
      this.recentAnchorPlayers.addFirst(playerId);
      while (this.recentAnchorPlayers.size() > RECENT_PLAYER_HISTORY) {
         this.recentAnchorPlayers.removeLast();
      }
      this.setDirty();
   }

   public int recentPenalty(UUID playerId) {
      int idx = 0;
      for (UUID id : this.recentAnchorPlayers) {
         if (id.equals(playerId)) {
            return Math.max(0, 70 - idx * 8);
         }
         idx++;
      }
      return 0;
   }

   public void markDirtyPublic() {
      this.setDirty();
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putInt("version", CURRENT_VERSION);
      tag.putInt("elapsedActiveTicks", this.elapsedActiveTicks);
      tag.putInt("nextIntervalTicks", this.nextIntervalTicks);
      tag.putBoolean("intervalChosen", this.intervalChosen);
      if (this.active != null) {
         tag.put("active", this.active.save(registries));
      }
      if (this.lastBossId != null) {
         tag.putString("lastBossId", this.lastBossId.toString());
      }
      if (this.lastOutcome != null) {
         tag.putString("lastOutcome", this.lastOutcome);
      }
      tag.putLong("lastTerminalGameTime", this.lastTerminalGameTime);
      ListTag recent = new ListTag();
      for (UUID id : this.recentAnchorPlayers) {
         recent.add(StringTag.valueOf(id.toString()));
      }
      tag.put("recentAnchorPlayers", recent);
      return tag;
   }

   private static WorldBossEventSavedData load(CompoundTag tag) {
      WorldBossEventSavedData data = new WorldBossEventSavedData();
      data.elapsedActiveTicks = tag.getInt("elapsedActiveTicks");
      data.nextIntervalTicks = Math.max(1, tag.getInt("nextIntervalTicks"));
      data.intervalChosen = tag.getBoolean("intervalChosen") || tag.contains("nextIntervalTicks");
      if (tag.contains("active")) {
         data.active = ActiveWorldBossEvent.load(tag.getCompound("active"));
      }
      if (tag.contains("lastBossId")) {
         data.lastBossId = ResourceLocation.parse(tag.getString("lastBossId"));
      }
      if (tag.contains("lastOutcome")) {
         data.lastOutcome = tag.getString("lastOutcome");
      }
      data.lastTerminalGameTime = tag.getLong("lastTerminalGameTime");
      ListTag recent = tag.getList("recentAnchorPlayers", Tag.TAG_STRING);
      for (int i = 0; i < recent.size(); i++) {
         try {
            data.recentAnchorPlayers.addLast(UUID.fromString(recent.getString(i)));
         } catch (IllegalArgumentException ignored) {
         }
      }
      return data;
   }
}
