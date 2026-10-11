package org.millenaire.faction.crime.hunter;

import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.slf4j.Logger;

/** World-scoped persistent hunter contracts (overworld storage). */
public final class BountyHunterContractSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String DATA_NAME = "millenaire_bounty_hunter_contracts";
   private final Map<UUID, BountyHunterContract> byTarget = new LinkedHashMap<>();
   private final Map<UUID, UUID> settledClaimIds = new LinkedHashMap<>();

   private BountyHunterContractSavedData() {
   }

   public static Factory<BountyHunterContractSavedData> factory() {
      return new Factory<>(BountyHunterContractSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static BountyHunterContractSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      ServerLevel storage = overworld != null ? overworld : level;
      return storage.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   @Nullable
   public BountyHunterContract getForTarget(UUID targetPlayerId) {
      return this.byTarget.get(targetPlayerId);
   }

   public Collection<BountyHunterContract> all() {
      return Collections.unmodifiableCollection(this.byTarget.values());
   }

   public void put(BountyHunterContract contract) {
      if (contract != null) {
         this.byTarget.put(contract.targetPlayerId(), contract);
         this.setDirty();
      }
   }

   public void remove(UUID targetPlayerId) {
      if (this.byTarget.remove(targetPlayerId) != null) {
         this.setDirty();
      }
   }

   public boolean hasSettledClaim(UUID claimId) {
      return claimId != null && this.settledClaimIds.containsKey(claimId);
   }

   public void markSettledClaim(UUID claimId, UUID targetPlayerId) {
      if (claimId != null) {
         this.settledClaimIds.put(claimId, targetPlayerId);
         this.setDirty();
      }
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      ListTag list = new ListTag();
      for (BountyHunterContract c : this.byTarget.values()) {
         CompoundTag entry = new CompoundTag();
         entry.putUUID("id", c.contractId());
         entry.putUUID("target", c.targetPlayerId());
         entry.putString("culture", c.cultureId().toString());
         entry.putString("bountyKey", c.bountyKey());
         entry.putString("phase", c.phase().name());
         entry.putLong("created", c.createdGameTime());
         entry.putLong("dispatchAt", c.dispatchAtGameTime());
         entry.putLong("redispatchAt", c.nextRedispatchGameTime());
         if (c.hunterEntityUuid() != null) {
            entry.putUUID("hunter", c.hunterEntityUuid());
         }
         if (c.lastKnownPos() != null) {
            entry.putLong("lastPos", c.lastKnownPos().asLong());
         }
         if (c.lastKnownDimension() != null) {
            entry.putString("lastDim", c.lastKnownDimension().toString());
         }
         entry.putLong("lastSight", c.lastSightingGameTime());
         entry.putBoolean("settled", c.settled());
         if (c.settlementId() != null) {
            entry.putUUID("settlementId", c.settlementId());
         }
         entry.putInt("failures", c.failureCount());
         list.add(entry);
      }
      tag.put("contracts", list);

      ListTag claims = new ListTag();
      for (Map.Entry<UUID, UUID> e : this.settledClaimIds.entrySet()) {
         CompoundTag claim = new CompoundTag();
         claim.putUUID("claim", e.getKey());
         claim.putUUID("target", e.getValue());
         claims.add(claim);
      }
      tag.put("settledClaims", claims);
      return tag;
   }

   private static BountyHunterContractSavedData load(CompoundTag tag) {
      BountyHunterContractSavedData data = new BountyHunterContractSavedData();
      ListTag list = tag.getList("contracts", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         CompoundTag entry = list.getCompound(i);
         try {
            UUID id = entry.getUUID("id");
            UUID target = entry.getUUID("target");
            ResourceLocation culture = ResourceLocation.parse(entry.getString("culture"));
            String bountyKey = entry.getString("bountyKey");
            BountyHunterContract c = new BountyHunterContract(
               id, target, culture, bountyKey, entry.getLong("created"), entry.getLong("dispatchAt")
            );
            try {
               c.setPhase(BountyHunterPhase.valueOf(entry.getString("phase")));
            } catch (IllegalArgumentException ignored) {
               c.setPhase(BountyHunterPhase.DISPATCH_PENDING);
            }
            c.setNextRedispatchGameTime(entry.getLong("redispatchAt"));
            if (entry.hasUUID("hunter")) {
               c.setHunterEntityUuid(entry.getUUID("hunter"));
            }
            if (entry.contains("lastPos")) {
               c.setLastKnownPos(BlockPos.of(entry.getLong("lastPos")));
            }
            if (entry.contains("lastDim")) {
               c.setLastKnownDimension(ResourceLocation.tryParse(entry.getString("lastDim")));
            }
            c.setLastSightingGameTime(entry.getLong("lastSight"));
            c.setSettled(entry.getBoolean("settled"));
            if (entry.hasUUID("settlementId")) {
               c.setSettlementId(entry.getUUID("settlementId"));
            }
            c.setFailureCount(entry.getInt("failures"));
            data.byTarget.put(target, c);
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to load bounty hunter contract index {}", i, e);
         }
      }
      ListTag claims = tag.getList("settledClaims", Tag.TAG_COMPOUND);
      for (int i = 0; i < claims.size(); i++) {
         CompoundTag claim = claims.getCompound(i);
         if (claim.hasUUID("claim") && claim.hasUUID("target")) {
            data.settledClaimIds.put(claim.getUUID("claim"), claim.getUUID("target"));
         }
      }
      return data;
   }
}
