package org.millenaire.living.requests;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

public final class VillageRequest {
   public static final int SCHEMA_VERSION = 2;

   private final String requestId;
   private final UUID playerId;
   private final UUID issuingNpcId;
   private final UUID villageId;
   private final VillageRequestType type;
   @Nullable
   private final String linkedNeedOrProjectId;
   private final Map<ResourceLocation, Integer> required;
   private final Map<ResourceLocation, Integer> delivered;
   private final long createdGameTime;
   private long expiryGameTime;
   private VillageRequestState state;
   private final String rewardPolicyRef;
   @Nullable
   private String completionEventId;
   private int version;
   private boolean rewardClaimed;
   private long lastUpdateGameTime;
   /** Goods that could not be pulled back after overdelivery — explicit recovery ledger. */
   private final Map<ResourceLocation, Integer> recoveryOutstanding = new LinkedHashMap<>();

   public VillageRequest(
      String requestId,
      UUID playerId,
      UUID issuingNpcId,
      UUID villageId,
      VillageRequestType type,
      @Nullable String linkedNeedOrProjectId,
      Map<ResourceLocation, Integer> required,
      long createdGameTime,
      long expiryGameTime,
      VillageRequestState state,
      String rewardPolicyRef,
      int version
   ) {
      this.requestId = requestId;
      this.playerId = playerId;
      this.issuingNpcId = issuingNpcId;
      this.villageId = villageId;
      this.type = type;
      this.linkedNeedOrProjectId = linkedNeedOrProjectId;
      this.required = new LinkedHashMap<>(required);
      this.delivered = new LinkedHashMap<>();
      for (ResourceLocation key : this.required.keySet()) {
         this.delivered.put(key, 0);
      }
      this.createdGameTime = createdGameTime;
      this.expiryGameTime = expiryGameTime;
      this.state = state;
      this.rewardPolicyRef = rewardPolicyRef == null ? "default" : rewardPolicyRef;
      this.version = version;
      this.lastUpdateGameTime = createdGameTime;
   }

   public String requestId() {
      return this.requestId;
   }

   public UUID playerId() {
      return this.playerId;
   }

   public UUID issuingNpcId() {
      return this.issuingNpcId;
   }

   public UUID villageId() {
      return this.villageId;
   }

   public VillageRequestType type() {
      return this.type;
   }

   @Nullable
   public String linkedNeedOrProjectId() {
      return this.linkedNeedOrProjectId;
   }

   public Map<ResourceLocation, Integer> required() {
      return Collections.unmodifiableMap(this.required);
   }

   public Map<ResourceLocation, Integer> delivered() {
      return Collections.unmodifiableMap(this.delivered);
   }

   public Map<ResourceLocation, Integer> remaining() {
      Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
      for (Entry<ResourceLocation, Integer> e : this.required.entrySet()) {
         int rem = Math.max(0, e.getValue() - this.delivered.getOrDefault(e.getKey(), 0));
         if (rem > 0) {
            out.put(e.getKey(), rem);
         }
      }
      return out;
   }

   public long createdGameTime() {
      return this.createdGameTime;
   }

   public long expiryGameTime() {
      return this.expiryGameTime;
   }

   public VillageRequestState state() {
      return this.state;
   }

   public String rewardPolicyRef() {
      return this.rewardPolicyRef;
   }

   @Nullable
   public String completionEventId() {
      return this.completionEventId;
   }

   public int version() {
      return this.version;
   }

   public boolean rewardClaimed() {
      return this.rewardClaimed;
   }

   public long lastUpdateGameTime() {
      return this.lastUpdateGameTime;
   }

   public boolean transitionTo(VillageRequestState next, long now) {
      if (!this.state.canTransitionTo(next) && !(this.state == VillageRequestState.IN_PROGRESS && next == VillageRequestState.IN_PROGRESS)) {
         return false;
      }
      this.state = next;
      this.version++;
      this.lastUpdateGameTime = now;
      return true;
   }

   public void forceState(VillageRequestState next, long now) {
      this.state = next;
      this.version++;
      this.lastUpdateGameTime = now;
   }

   public int applyDelivery(ResourceLocation itemId, int amount, long now) {
      if (amount <= 0) {
         return 0;
      }
      int need = this.required.getOrDefault(itemId, 0);
      int have = this.delivered.getOrDefault(itemId, 0);
      int room = Math.max(0, need - have);
      int applied = Math.min(room, amount);
      if (applied <= 0) {
         return 0;
      }
      this.delivered.put(itemId, have + applied);
      this.version++;
      this.lastUpdateGameTime = now;
      if (this.state == VillageRequestState.ACCEPTED) {
         this.state = VillageRequestState.IN_PROGRESS;
      }
      if (this.remaining().isEmpty()) {
         this.state = VillageRequestState.READY_TO_COMPLETE;
      }
      return applied;
   }

   /**
    * Caps required quantity for an item without dropping already-delivered credit.
    * Used when live need shrinks after a request was offered.
    */
   public void capRequired(ResourceLocation itemId, int newRequired) {
      if (itemId == null) {
         return;
      }
      int delivered = this.delivered.getOrDefault(itemId, 0);
      int capped = Math.max(delivered, Math.max(0, newRequired));
      if (capped <= 0 && delivered <= 0) {
         this.required.remove(itemId);
         this.delivered.remove(itemId);
      } else {
         this.required.put(itemId, capped);
         this.delivered.putIfAbsent(itemId, delivered);
      }
      this.version++;
   }

   public void markRewardClaimed(String eventId, long now) {
      this.rewardClaimed = true;
      this.completionEventId = eventId;
      this.lastUpdateGameTime = now;
      this.version++;
   }

   public void markRecoveryOutstanding(ResourceLocation itemId, int amount) {
      if (itemId == null || amount <= 0) {
         return;
      }
      this.recoveryOutstanding.merge(itemId, amount, Integer::sum);
      this.version++;
   }

   /**
    * Reduce the outstanding recovery ledger after goods were actually pulled back.
    * @return amount actually consumed from the ledger
    */
   public int consumeRecovery(ResourceLocation itemId, int amount) {
      if (itemId == null || amount <= 0) {
         return 0;
      }
      int current = this.recoveryOutstanding.getOrDefault(itemId, 0);
      if (current <= 0) {
         return 0;
      }
      int take = Math.min(current, amount);
      int left = current - take;
      if (left <= 0) {
         this.recoveryOutstanding.remove(itemId);
      } else {
         this.recoveryOutstanding.put(itemId, left);
      }
      this.version++;
      return take;
   }

   public Map<ResourceLocation, Integer> recoveryOutstanding() {
      return Collections.unmodifiableMap(this.recoveryOutstanding);
   }

   public boolean hasRecoveryOutstanding() {
      return this.recoveryOutstanding.values().stream().anyMatch(v -> v > 0);
   }

   public int totalRequired() {
      return this.required.values().stream().mapToInt(Integer::intValue).sum();
   }

   public int totalDelivered() {
      return this.delivered.values().stream().mapToInt(Integer::intValue).sum();
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putInt("schema", SCHEMA_VERSION);
      tag.putString("id", this.requestId);
      tag.putUUID("player", this.playerId);
      tag.putUUID("npc", this.issuingNpcId);
      tag.putUUID("village", this.villageId);
      tag.putString("type", this.type.name());
      if (this.linkedNeedOrProjectId != null) {
         tag.putString("linked", this.linkedNeedOrProjectId);
      }
      tag.putLong("created", this.createdGameTime);
      tag.putLong("expiry", this.expiryGameTime);
      tag.putString("state", this.state.name());
      tag.putString("rewardPolicy", this.rewardPolicyRef);
      if (this.completionEventId != null) {
         tag.putString("completionEvent", this.completionEventId);
      }
      tag.putInt("version", this.version);
      tag.putBoolean("rewardClaimed", this.rewardClaimed);
      tag.putLong("lastUpdate", this.lastUpdateGameTime);
      ListTag req = new ListTag();
      for (Entry<ResourceLocation, Integer> e : this.required.entrySet()) {
         CompoundTag item = new CompoundTag();
         item.putString("id", e.getKey().toString());
         item.putInt("required", e.getValue());
         item.putInt("delivered", this.delivered.getOrDefault(e.getKey(), 0));
         req.add(item);
      }
      tag.put("items", req);
      if (!this.recoveryOutstanding.isEmpty()) {
         ListTag recovery = new ListTag();
         for (Entry<ResourceLocation, Integer> e : this.recoveryOutstanding.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("id", e.getKey().toString());
            item.putInt("amount", e.getValue());
            recovery.add(item);
         }
         tag.put("recovery", recovery);
      }
      return tag;
   }

   @Nullable
   public static VillageRequest load(CompoundTag tag) {
      try {
         String id = tag.getString("id");
         UUID player = tag.getUUID("player");
         UUID npc = tag.getUUID("npc");
         UUID village = tag.getUUID("village");
         VillageRequestType type = VillageRequestType.valueOf(tag.getString("type"));
         VillageRequestState state = VillageRequestState.valueOf(tag.getString("state"));
         Map<ResourceLocation, Integer> required = new LinkedHashMap<>();
         Map<ResourceLocation, Integer> delivered = new LinkedHashMap<>();
         ListTag items = tag.getList("items", 10);
         for (int i = 0; i < items.size(); i++) {
            CompoundTag item = items.getCompound(i);
            ResourceLocation rl = ResourceLocation.tryParse(item.getString("id"));
            if (rl != null) {
               required.put(rl, item.getInt("required"));
               delivered.put(rl, item.getInt("delivered"));
            }
         }
         VillageRequest request = new VillageRequest(
            id,
            player,
            npc,
            village,
            type,
            tag.contains("linked") ? tag.getString("linked") : null,
            required,
            tag.getLong("created"),
            tag.getLong("expiry"),
            state,
            tag.getString("rewardPolicy"),
            tag.getInt("version")
         );
         request.delivered.clear();
         request.delivered.putAll(delivered);
         request.rewardClaimed = tag.getBoolean("rewardClaimed");
         request.completionEventId = tag.contains("completionEvent") ? tag.getString("completionEvent") : null;
         request.lastUpdateGameTime = tag.getLong("lastUpdate");
         if (tag.contains("recovery")) {
            ListTag recovery = tag.getList("recovery", 10);
            for (int i = 0; i < recovery.size(); i++) {
               CompoundTag item = recovery.getCompound(i);
               ResourceLocation rl = ResourceLocation.tryParse(item.getString("id"));
               if (rl != null) {
                  request.recoveryOutstanding.put(rl, item.getInt("amount"));
               }
            }
         }
         return request;
      } catch (Exception e) {
         return null;
      }
   }
}
