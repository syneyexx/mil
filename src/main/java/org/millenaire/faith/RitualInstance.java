package org.millenaire.faith;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Persistent ritual state machine. Consume/reward flags make completion idempotent.
 */
public final class RitualInstance {
   private final UUID id;
   private final ResourceLocation definitionId;
   private final UUID chapterId;
   @Nullable
   private UUID leaderRecordUuid;
   private final List<UUID> participantRecordUuids = new ArrayList<>();
   private final List<UUID> playerParticipantUuids = new ArrayList<>();
   private RitualState state = RitualState.PREPARING;
   private final Map<String, Integer> reservedGoods = new LinkedHashMap<>();
   private long prepareGameTime;
   private long startDeadline = -1L;
   private long endDeadline = -1L;
   private boolean consumed;
   private boolean rewarded;
   private boolean goodsReserved;

   public RitualInstance(UUID id, ResourceLocation definitionId, UUID chapterId) {
      this.id = id;
      this.definitionId = definitionId;
      this.chapterId = chapterId;
   }

   public UUID id() {
      return this.id;
   }

   public ResourceLocation definitionId() {
      return this.definitionId;
   }

   public UUID chapterId() {
      return this.chapterId;
   }

   @Nullable
   public UUID leaderRecordUuid() {
      return this.leaderRecordUuid;
   }

   public void setLeaderRecordUuid(@Nullable UUID leaderRecordUuid) {
      this.leaderRecordUuid = leaderRecordUuid;
   }

   public List<UUID> participantRecordUuids() {
      return this.participantRecordUuids;
   }

   public List<UUID> playerParticipantUuids() {
      return this.playerParticipantUuids;
   }

   public RitualState state() {
      return this.state;
   }

   public void setState(RitualState state) {
      this.state = state;
   }

   public Map<String, Integer> reservedGoods() {
      return this.reservedGoods;
   }

   public long prepareGameTime() {
      return this.prepareGameTime;
   }

   public void setPrepareGameTime(long prepareGameTime) {
      this.prepareGameTime = prepareGameTime;
   }

   public long startDeadline() {
      return this.startDeadline;
   }

   public void setStartDeadline(long startDeadline) {
      this.startDeadline = startDeadline;
   }

   public long endDeadline() {
      return this.endDeadline;
   }

   public void setEndDeadline(long endDeadline) {
      this.endDeadline = endDeadline;
   }

   public boolean consumed() {
      return this.consumed;
   }

   public void setConsumed(boolean consumed) {
      this.consumed = consumed;
   }

   public boolean rewarded() {
      return this.rewarded;
   }

   public void setRewarded(boolean rewarded) {
      this.rewarded = rewarded;
   }

   public boolean goodsReserved() {
      return this.goodsReserved;
   }

   public void setGoodsReserved(boolean goodsReserved) {
      this.goodsReserved = goodsReserved;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.id);
      tag.putString("def", this.definitionId.toString());
      tag.putUUID("chapter", this.chapterId);
      if (this.leaderRecordUuid != null) {
         tag.putUUID("leader", this.leaderRecordUuid);
      }
      tag.putString("state", this.state.name());
      tag.putLong("prepare", this.prepareGameTime);
      tag.putLong("start_dl", this.startDeadline);
      tag.putLong("end_dl", this.endDeadline);
      tag.putBoolean("consumed", this.consumed);
      tag.putBoolean("rewarded", this.rewarded);
      tag.putBoolean("reserved", this.goodsReserved);
      ListTag parts = new ListTag();
      for (UUID u : this.participantRecordUuids) {
         parts.add(StringTag.valueOf(u.toString()));
      }
      tag.put("participants", parts);
      ListTag players = new ListTag();
      for (UUID u : this.playerParticipantUuids) {
         players.add(StringTag.valueOf(u.toString()));
      }
      tag.put("players", players);
      CompoundTag goods = new CompoundTag();
      for (Map.Entry<String, Integer> e : this.reservedGoods.entrySet()) {
         goods.putInt(e.getKey(), e.getValue());
      }
      tag.put("goods", goods);
      return tag;
   }

   public static RitualInstance load(CompoundTag tag) {
      RitualInstance r = new RitualInstance(
         tag.getUUID("id"),
         ResourceLocation.parse(tag.getString("def")),
         tag.getUUID("chapter")
      );
      if (tag.hasUUID("leader")) {
         r.leaderRecordUuid = tag.getUUID("leader");
      }
      RitualState st = RitualState.fromId(tag.getString("state"));
      r.state = st != null ? st : RitualState.CANCELLED;
      r.prepareGameTime = tag.getLong("prepare");
      r.startDeadline = tag.getLong("start_dl");
      r.endDeadline = tag.getLong("end_dl");
      r.consumed = tag.getBoolean("consumed");
      r.rewarded = tag.getBoolean("rewarded");
      r.goodsReserved = tag.getBoolean("reserved");
      ListTag parts = tag.getList("participants", 8);
      for (int i = 0; i < parts.size(); i++) {
         try {
            r.participantRecordUuids.add(UUID.fromString(parts.getString(i)));
         } catch (IllegalArgumentException ignored) {
         }
      }
      ListTag players = tag.getList("players", 8);
      for (int i = 0; i < players.size(); i++) {
         try {
            r.playerParticipantUuids.add(UUID.fromString(players.getString(i)));
         } catch (IllegalArgumentException ignored) {
         }
      }
      CompoundTag goods = tag.getCompound("goods");
      for (String key : goods.getAllKeys()) {
         r.reservedGoods.put(key, goods.getInt(key));
      }
      return r;
   }
}
