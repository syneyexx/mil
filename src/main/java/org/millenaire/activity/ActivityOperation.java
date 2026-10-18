package org.millenaire.activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Canonical ledger record for one regional activity operation.
 * AI and projection logic live in services; this class owns persisted state.
 */
public final class ActivityOperation {
   public static final int SCHEMA_VERSION = 1;

   private final UUID operationId;
   private final ResourceLocation dimension;
   private final ActivityKind kind;
   private int generation;
   private long creationGameTime;
   @Nullable
   private UUID sourceSettlementId;
   @Nullable
   private UUID sourceCampId;
   @Nullable
   private UUID targetSettlementId;
   @Nullable
   private UUID targetOperationId;
   private final List<UUID> groupIds = new ArrayList<>();
   private final List<UUID> participantIds = new ArrayList<>();
   private String hostilityReason = "";
   private ActivityState state = ActivityState.PROPOSED;
   private long nextDueGameTime;
   private long stateEnteredGameTime;
   private long deadlineGameTime;
   private final List<BlockPos> coarseRoute = new ArrayList<>();
   private int waypointIndex;
   @Nullable
   private UUID targetReservationId;
   @Nullable
   private BlockPos lastValidPos;
   private final List<ItemStack> cargo = new ArrayList<>();
   private boolean outcomeApplied;
   private String recoveryReason = "";
   private ActivityReasonCode deferReason = ActivityReasonCode.NONE;
   private String profileId = "";
   private String organizationId = "";
   private RaidParticipantMode raidMode = RaidParticipantMode.LEGACY_CLONES;
   private int preyTaken;
   private int initialMemberCount;
   private int lostMembers;
   private long seed;
   private String transferId = "";
   private boolean cargoAmbiguous;
   private boolean physicalActive;
   private long lastNavFailGameTime;
   private int consecutivePathFails;
   private long chaseDeadlineGameTime;

   public ActivityOperation(UUID operationId, ResourceLocation dimension, ActivityKind kind, long creationGameTime) {
      this.operationId = operationId;
      this.dimension = dimension;
      this.kind = kind;
      this.creationGameTime = creationGameTime;
      this.generation = 1;
      this.stateEnteredGameTime = creationGameTime;
      this.nextDueGameTime = creationGameTime;
      this.seed = operationId.getMostSignificantBits() ^ creationGameTime;
   }

   public UUID operationId() {
      return this.operationId;
   }

   public ResourceLocation dimension() {
      return this.dimension;
   }

   public ActivityKind kind() {
      return this.kind;
   }

   public int generation() {
      return this.generation;
   }

   public void bumpGeneration() {
      this.generation++;
   }

   public long creationGameTime() {
      return this.creationGameTime;
   }

   @Nullable
   public UUID sourceSettlementId() {
      return this.sourceSettlementId;
   }

   public void setSourceSettlementId(@Nullable UUID id) {
      this.sourceSettlementId = id;
   }

   @Nullable
   public UUID sourceCampId() {
      return this.sourceCampId;
   }

   public void setSourceCampId(@Nullable UUID id) {
      this.sourceCampId = id;
   }

   @Nullable
   public UUID sourceId() {
      return this.sourceSettlementId != null ? this.sourceSettlementId : this.sourceCampId;
   }

   @Nullable
   public UUID targetSettlementId() {
      return this.targetSettlementId;
   }

   public void setTargetSettlementId(@Nullable UUID id) {
      this.targetSettlementId = id;
   }

   @Nullable
   public UUID targetOperationId() {
      return this.targetOperationId;
   }

   public void setTargetOperationId(@Nullable UUID id) {
      this.targetOperationId = id;
   }

   public List<UUID> groupIds() {
      return Collections.unmodifiableList(this.groupIds);
   }

   public void addGroupId(UUID id) {
      if (!this.groupIds.contains(id)) {
         this.groupIds.add(id);
      }
   }

   public List<UUID> participantIds() {
      return Collections.unmodifiableList(this.participantIds);
   }

   public void setParticipantIds(List<UUID> ids) {
      this.participantIds.clear();
      this.participantIds.addAll(ids);
   }

   public void clearParticipants() {
      this.participantIds.clear();
   }

   public String hostilityReason() {
      return this.hostilityReason;
   }

   public void setHostilityReason(String reason) {
      this.hostilityReason = reason == null ? "" : reason;
   }

   public ActivityState state() {
      return this.state;
   }

   public void enterState(ActivityState next, long gameTime) {
      this.state = next;
      this.stateEnteredGameTime = gameTime;
      this.nextDueGameTime = gameTime;
   }

   public long nextDueGameTime() {
      return this.nextDueGameTime;
   }

   public void setNextDueGameTime(long t) {
      this.nextDueGameTime = t;
   }

   public long stateEnteredGameTime() {
      return this.stateEnteredGameTime;
   }

   public long deadlineGameTime() {
      return this.deadlineGameTime;
   }

   public void setDeadlineGameTime(long t) {
      this.deadlineGameTime = t;
   }

   public List<BlockPos> coarseRoute() {
      return Collections.unmodifiableList(this.coarseRoute);
   }

   public void setCoarseRoute(List<BlockPos> route) {
      this.coarseRoute.clear();
      this.coarseRoute.addAll(route);
      this.waypointIndex = 0;
   }

   public int waypointIndex() {
      return this.waypointIndex;
   }

   public void setWaypointIndex(int idx) {
      this.waypointIndex = Math.max(0, idx);
   }

   public void advanceWaypoint() {
      this.waypointIndex++;
   }

   @Nullable
   public BlockPos currentWaypoint() {
      if (this.waypointIndex < 0 || this.waypointIndex >= this.coarseRoute.size()) {
         return null;
      }
      return this.coarseRoute.get(this.waypointIndex);
   }

   @Nullable
   public UUID targetReservationId() {
      return this.targetReservationId;
   }

   public void setTargetReservationId(@Nullable UUID id) {
      this.targetReservationId = id;
   }

   @Nullable
   public BlockPos lastValidPos() {
      return this.lastValidPos;
   }

   public void setLastValidPos(@Nullable BlockPos pos) {
      this.lastValidPos = pos;
   }

   public List<ItemStack> cargo() {
      return this.cargo;
   }

   public boolean outcomeApplied() {
      return this.outcomeApplied;
   }

   public void setOutcomeApplied(boolean v) {
      this.outcomeApplied = v;
   }

   public String recoveryReason() {
      return this.recoveryReason;
   }

   public void setRecoveryReason(String reason) {
      this.recoveryReason = reason == null ? "" : reason;
   }

   public ActivityReasonCode deferReason() {
      return this.deferReason;
   }

   public void setDeferReason(ActivityReasonCode code) {
      this.deferReason = code == null ? ActivityReasonCode.NONE : code;
   }

   public String profileId() {
      return this.profileId;
   }

   public void setProfileId(String id) {
      this.profileId = id == null ? "" : id;
   }

   public String organizationId() {
      return this.organizationId;
   }

   public void setOrganizationId(String id) {
      this.organizationId = id == null ? "" : id;
   }

   public RaidParticipantMode raidMode() {
      return this.raidMode;
   }

   public void setRaidMode(RaidParticipantMode mode) {
      this.raidMode = mode == null ? RaidParticipantMode.LEGACY_CLONES : mode;
   }

   public int preyTaken() {
      return this.preyTaken;
   }

   public void setPreyTaken(int n) {
      this.preyTaken = n;
   }

   public int initialMemberCount() {
      return this.initialMemberCount;
   }

   public void setInitialMemberCount(int n) {
      this.initialMemberCount = n;
   }

   public int lostMembers() {
      return this.lostMembers;
   }

   public void incrementLostMembers() {
      this.lostMembers++;
   }

   public long seed() {
      return this.seed;
   }

   public String transferId() {
      return this.transferId;
   }

   public void setTransferId(String id) {
      this.transferId = id == null ? "" : id;
   }

   public boolean cargoAmbiguous() {
      return this.cargoAmbiguous;
   }

   public void setCargoAmbiguous(boolean v) {
      this.cargoAmbiguous = v;
   }

   public boolean physicalActive() {
      return this.physicalActive;
   }

   public void setPhysicalActive(boolean v) {
      this.physicalActive = v;
   }

   public long lastNavFailGameTime() {
      return this.lastNavFailGameTime;
   }

   public void setLastNavFailGameTime(long t) {
      this.lastNavFailGameTime = t;
   }

   public int consecutivePathFails() {
      return this.consecutivePathFails;
   }

   public void setConsecutivePathFails(int n) {
      this.consecutivePathFails = n;
   }

   public long chaseDeadlineGameTime() {
      return this.chaseDeadlineGameTime;
   }

   public void setChaseDeadlineGameTime(long t) {
      this.chaseDeadlineGameTime = t;
   }

   public CompoundTag save(HolderLookup.Provider registries) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.operationId);
      tag.putString("dim", this.dimension.toString());
      tag.putString("kind", this.kind.name());
      tag.putInt("schema", SCHEMA_VERSION);
      tag.putInt("gen", this.generation);
      tag.putLong("created", this.creationGameTime);
      if (this.sourceSettlementId != null) {
         tag.putUUID("sourceSettlement", this.sourceSettlementId);
      }
      if (this.sourceCampId != null) {
         tag.putUUID("sourceCamp", this.sourceCampId);
      }
      if (this.targetSettlementId != null) {
         tag.putUUID("targetSettlement", this.targetSettlementId);
      }
      if (this.targetOperationId != null) {
         tag.putUUID("targetOp", this.targetOperationId);
      }
      tag.put("groups", uuidList(this.groupIds));
      tag.put("participants", uuidList(this.participantIds));
      tag.putString("hostility", this.hostilityReason);
      tag.putString("state", this.state.name());
      tag.putLong("nextDue", this.nextDueGameTime);
      tag.putLong("stateEntered", this.stateEnteredGameTime);
      tag.putLong("deadline", this.deadlineGameTime);
      ListTag route = new ListTag();
      for (BlockPos p : this.coarseRoute) {
         CompoundTag pt = new CompoundTag();
         pt.putIntArray("p", new int[]{p.getX(), p.getY(), p.getZ()});
         route.add(pt);
      }
      tag.put("route", route);
      tag.putInt("wp", this.waypointIndex);
      if (this.targetReservationId != null) {
         tag.putUUID("targetRes", this.targetReservationId);
      }
      if (this.lastValidPos != null) {
         tag.putIntArray("lastPos", new int[]{this.lastValidPos.getX(), this.lastValidPos.getY(), this.lastValidPos.getZ()});
      }
      ListTag cargoTag = new ListTag();
      for (ItemStack stack : this.cargo) {
         if (!stack.isEmpty()) {
            Tag saved = stack.save(registries);
            if (saved instanceof CompoundTag ct) {
               cargoTag.add(ct);
            }
         }
      }
      tag.put("cargo", cargoTag);
      tag.putBoolean("outcomeApplied", this.outcomeApplied);
      tag.putString("recovery", this.recoveryReason);
      tag.putString("defer", this.deferReason.name());
      tag.putString("profile", this.profileId);
      tag.putString("org", this.organizationId);
      tag.putString("raidMode", this.raidMode.name());
      tag.putInt("prey", this.preyTaken);
      tag.putInt("initialMembers", this.initialMemberCount);
      tag.putInt("lost", this.lostMembers);
      tag.putLong("seed", this.seed);
      tag.putString("transferId", this.transferId);
      tag.putBoolean("cargoAmbiguous", this.cargoAmbiguous);
      tag.putBoolean("physical", this.physicalActive);
      tag.putLong("navFailAt", this.lastNavFailGameTime);
      tag.putInt("pathFails", this.consecutivePathFails);
      tag.putLong("chaseDeadline", this.chaseDeadlineGameTime);
      return tag;
   }

   public static ActivityOperation load(CompoundTag tag, HolderLookup.Provider registries) {
      UUID id = tag.getUUID("id");
      ResourceLocation dim = ResourceLocation.parse(tag.getString("dim"));
      ActivityKind kind = ActivityKind.fromId(tag.getString("kind"));
      ActivityOperation op = new ActivityOperation(id, dim, kind, tag.getLong("created"));
      op.generation = tag.getInt("gen");
      if (tag.hasUUID("sourceSettlement")) {
         op.sourceSettlementId = tag.getUUID("sourceSettlement");
      }
      if (tag.hasUUID("sourceCamp")) {
         op.sourceCampId = tag.getUUID("sourceCamp");
      }
      if (tag.hasUUID("targetSettlement")) {
         op.targetSettlementId = tag.getUUID("targetSettlement");
      }
      if (tag.hasUUID("targetOp")) {
         op.targetOperationId = tag.getUUID("targetOp");
      }
      readUuidList(tag.getList("groups", Tag.TAG_COMPOUND), op.groupIds);
      readUuidList(tag.getList("participants", Tag.TAG_COMPOUND), op.participantIds);
      op.hostilityReason = tag.getString("hostility");
      op.state = ActivityState.fromId(tag.getString("state"));
      op.nextDueGameTime = tag.getLong("nextDue");
      op.stateEnteredGameTime = tag.getLong("stateEntered");
      op.deadlineGameTime = tag.getLong("deadline");
      ListTag route = tag.getList("route", Tag.TAG_COMPOUND);
      for (int i = 0; i < route.size(); i++) {
         int[] p = route.getCompound(i).getIntArray("p");
         if (p.length == 3) {
            op.coarseRoute.add(new BlockPos(p[0], p[1], p[2]));
         }
      }
      op.waypointIndex = tag.getInt("wp");
      if (tag.hasUUID("targetRes")) {
         op.targetReservationId = tag.getUUID("targetRes");
      }
      if (tag.contains("lastPos")) {
         int[] p = tag.getIntArray("lastPos");
         if (p.length == 3) {
            op.lastValidPos = new BlockPos(p[0], p[1], p[2]);
         }
      }
      ListTag cargoTag = tag.getList("cargo", Tag.TAG_COMPOUND);
      for (int i = 0; i < cargoTag.size(); i++) {
         ItemStack stack = ItemStack.parseOptional(registries, cargoTag.getCompound(i));
         if (!stack.isEmpty()) {
            op.cargo.add(stack);
         }
      }
      op.outcomeApplied = tag.getBoolean("outcomeApplied");
      op.recoveryReason = tag.getString("recovery");
      op.deferReason = ActivityReasonCode.fromId(tag.getString("defer"));
      op.profileId = tag.getString("profile");
      op.organizationId = tag.getString("org");
      op.raidMode = RaidParticipantMode.fromId(tag.getString("raidMode"));
      op.preyTaken = tag.getInt("prey");
      op.initialMemberCount = tag.getInt("initialMembers");
      op.lostMembers = tag.getInt("lost");
      op.seed = tag.contains("seed") ? tag.getLong("seed") : id.getMostSignificantBits();
      op.transferId = tag.getString("transferId");
      op.cargoAmbiguous = tag.getBoolean("cargoAmbiguous");
      op.physicalActive = tag.getBoolean("physical");
      op.lastNavFailGameTime = tag.getLong("navFailAt");
      op.consecutivePathFails = tag.getInt("pathFails");
      op.chaseDeadlineGameTime = tag.getLong("chaseDeadline");
      return op;
   }

   private static ListTag uuidList(List<UUID> ids) {
      ListTag list = new ListTag();
      for (UUID u : ids) {
         CompoundTag t = new CompoundTag();
         t.putUUID("u", u);
         list.add(t);
      }
      return list;
   }

   private static void readUuidList(ListTag list, List<UUID> out) {
      out.clear();
      for (int i = 0; i < list.size(); i++) {
         out.add(list.getCompound(i).getUUID("u"));
      }
   }
}
