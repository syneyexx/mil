package org.millenaire.map.sync;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Network/client DTO for one Millénaire settlement marker. Not an authoritative store.
 */
public record SettlementMapEntry(
   UUID villageId,
   String name,
   ResourceLocation cultureId,
   ResourceLocation villageTypeId,
   String developmentLabel,
   int x,
   int y,
   int z,
   ResourceLocation dimension,
   int flags,
   @Nullable String factionId,
   int revision,
   /** Approximate building AABB; only populated when server config enables bounds sync. */
   @Nullable ApproxBounds approxBounds
) {
   public static final int FLAG_CONTROLLED = 1;
   public static final int FLAG_SOVEREIGN_PLAYER_CITY = 1 << 1;
   public static final int FLAG_LONE_BUILDING = 1 << 2;
   public static final int FLAG_HOSTILE = 1 << 3;
   public static final int FLAG_HAS_TOWNHALL = 1 << 4;

   public SettlementMapEntry {
      Objects.requireNonNull(villageId, "villageId");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(cultureId, "cultureId");
      Objects.requireNonNull(villageTypeId, "villageTypeId");
      Objects.requireNonNull(developmentLabel, "developmentLabel");
      Objects.requireNonNull(dimension, "dimension");
   }

   public boolean isControlled() {
      return (flags & FLAG_CONTROLLED) != 0;
   }

   public boolean isSovereignPlayerCity() {
      return (flags & FLAG_SOVEREIGN_PLAYER_CITY) != 0;
   }

   public boolean isLoneBuilding() {
      return (flags & FLAG_LONE_BUILDING) != 0;
   }

   public boolean isHostile() {
      return (flags & FLAG_HOSTILE) != 0;
   }

   public boolean hasTownhall() {
      return (flags & FLAG_HAS_TOWNHALL) != 0;
   }

   public record ApproxBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
   }
}
