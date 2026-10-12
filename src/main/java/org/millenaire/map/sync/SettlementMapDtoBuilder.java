package org.millenaire.map.sync;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.millenaire.building.BuildingInstance;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.civilization.settlement.PlayerSettlementFoundingService;
import org.millenaire.civilization.settlement.SettlementDevelopmentClassifier;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;

/**
 * Builds per-player settlement map DTOs from authoritative Millénaire SavedData.
 */
public final class SettlementMapDtoBuilder {
   private SettlementMapDtoBuilder() {
   }

   public static List<SettlementMapEntry> buildForPlayer(ServerLevel overworld, ServerPlayer player, int revision) {
      List<SettlementMapEntry> out = new ArrayList<>();
      if (overworld == null || player == null) {
         return out;
      }
      UUID playerId = player.getUUID();
      Set<VillageId> discovered = PlayerCultureReputation.get(overworld).getDiscoveredVillages(playerId);
      CivilizationSavedData civ = CivilizationSavedData.get(overworld);
      boolean showLone = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.mapSyncShowLoneBuildings.get());
      boolean includeBounds = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.mapSyncIncludeApproxBounds.get());
      ResourceLocation overworldDim = Level.OVERWORLD.location();

      for (Village village : VillageSavedData.get(overworld).getVillageManager().getAllVillages()) {
         boolean controlled = village.isControlledBy(playerId);
         boolean sovereign = PlayerSettlementFoundingService.isSovereignPlayerSettlement(overworld, village)
            && village.getOwnerUUID() != null
            && village.getOwnerUUID().equals(playerId);
         boolean lone = village.isLoneBuilding();
         boolean isDiscovered = discovered.contains(village.getId());
         if (!SettlementMapVisibility.shouldInclude(isDiscovered, controlled, sovereign, lone, showLone)) {
            continue;
         }
         SettlementMapEntry entry = toEntry(village, overworldDim, civ, revision, controlled, sovereign, lone, includeBounds);
         if (entry != null) {
            out.add(entry);
         }
      }
      return out;
   }

   /** Upserts plus ids the client should drop (missing or not visible to this player). */
   public record Delta(List<SettlementMapEntry> upserts, List<UUID> removed) {
   }

   /**
    * Builds DTOs for {@code dirtyIds} only; visibility flags are computed per dirty village.
    * Dirty ids that no longer exist or are not visible are returned as removals.
    */
   public static Delta buildDeltaForPlayer(ServerLevel overworld, ServerPlayer player, int revision, Collection<UUID> dirtyIds) {
      List<SettlementMapEntry> upserts = new ArrayList<>();
      Set<UUID> removed = new LinkedHashSet<>();
      if (overworld == null || player == null || dirtyIds == null || dirtyIds.isEmpty()) {
         return new Delta(upserts, new ArrayList<>(removed));
      }
      UUID playerId = player.getUUID();
      Set<VillageId> discovered = PlayerCultureReputation.get(overworld).getDiscoveredVillages(playerId);
      CivilizationSavedData civ = CivilizationSavedData.get(overworld);
      boolean showLone = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.mapSyncShowLoneBuildings.get());
      boolean includeBounds = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.mapSyncIncludeApproxBounds.get());
      ResourceLocation overworldDim = Level.OVERWORLD.location();
      var manager = VillageSavedData.get(overworld).getVillageManager();

      for (UUID id : dirtyIds) {
         Village village = manager.getVillage(new VillageId(id));
         if (village == null) {
            removed.add(id);
            continue;
         }
         boolean controlled = village.isControlledBy(playerId);
         boolean sovereign = PlayerSettlementFoundingService.isSovereignPlayerSettlement(overworld, village)
            && village.getOwnerUUID() != null
            && village.getOwnerUUID().equals(playerId);
         boolean lone = village.isLoneBuilding();
         boolean isDiscovered = discovered.contains(village.getId());
         if (!SettlementMapVisibility.shouldInclude(isDiscovered, controlled, sovereign, lone, showLone)) {
            removed.add(id);
            continue;
         }
         SettlementMapEntry entry = toEntry(village, overworldDim, civ, revision, controlled, sovereign, lone, includeBounds);
         if (entry != null) {
            upserts.add(entry);
         } else {
            removed.add(id);
         }
      }
      return new Delta(upserts, new ArrayList<>(removed));
   }

   @Nullable
   public static SettlementMapEntry toEntry(
      Village village,
      ResourceLocation dimension,
      CivilizationSavedData civ,
      int revision,
      boolean controlled,
      boolean sovereign,
      boolean lone,
      boolean includeBounds
   ) {
      if (village == null || village.getCenter() == null) {
         return null;
      }
      BlockPos center = village.getCenter();
      String label = civ != null ? civ.getDevelopmentLabel(village.getId().uuid()) : null;
      if (label == null || label.isBlank()) {
         label = SettlementDevelopmentClassifier.classify(village, 0L);
      }
      int flags = 0;
      if (controlled) {
         flags |= SettlementMapEntry.FLAG_CONTROLLED;
      }
      if (sovereign) {
         flags |= SettlementMapEntry.FLAG_SOVEREIGN_PLAYER_CITY;
      }
      if (lone) {
         flags |= SettlementMapEntry.FLAG_LONE_BUILDING;
      }
      BuildingInstance townhall = village.getTownhall();
      if (townhall != null) {
         flags |= SettlementMapEntry.FLAG_HAS_TOWNHALL;
      }
      String name = village.getVillageName();
      if (name == null || name.isBlank()) {
         name = village.getVillageTypeId() != null ? village.getVillageTypeId().getPath() : "settlement";
      }
      SettlementMapEntry.ApproxBounds bounds = null;
      if (includeBounds) {
         AABB aabb = village.computeBounds();
         bounds = new SettlementMapEntry.ApproxBounds(
            (int)Math.floor(aabb.minX),
            (int)Math.floor(aabb.minY),
            (int)Math.floor(aabb.minZ),
            (int)Math.ceil(aabb.maxX),
            (int)Math.ceil(aabb.maxY),
            (int)Math.ceil(aabb.maxZ)
         );
      }
      String factionId = village.getFactionId() != null ? village.getFactionId().asString() : null;
      ResourceLocation culture = village.getCultureId() != null
         ? village.getCultureId()
         : ResourceLocation.fromNamespaceAndPath("millenaire", "unknown");
      ResourceLocation type = village.getVillageTypeId() != null
         ? village.getVillageTypeId()
         : ResourceLocation.fromNamespaceAndPath("millenaire", "unknown");
      return new SettlementMapEntry(
         village.getId().uuid(),
         name,
         culture,
         type,
         label,
         center.getX(),
         center.getY(),
         center.getZ(),
         dimension,
         flags,
         factionId,
         revision,
         bounds
      );
   }
}
