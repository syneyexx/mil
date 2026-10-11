package org.millenaire.faction;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.Millenaire;

/**
 * Factories for stable, well-known faction identifiers.
 * <p>
 * Default culture-faction ids are deterministic from the culture id so worlds initialize
 * consistently. They do <strong>not</strong> imply a one-culture-one-faction rule: callers may
 * register additional factions that reuse the same culture with different {@link FactionId}s.
 */
public final class FactionIds {
   private static final String CULTURE_FACTION_PREFIX = "culture_faction/";
   private static final String PLAYER_FACTION_PREFIX = "player_faction/";
   private static final String BREAKAWAY_FACTION_PREFIX = "breakaway_faction/";

   private FactionIds() {
   }

   /**
    * Deterministic default culture faction for automatic settlement association.
    * Example: culture {@code millenaire:norman} → {@code millenaire:culture_faction/norman}.
    */
   public static FactionId defaultCultureFaction(ResourceLocation cultureId) {
      Objects.requireNonNull(cultureId, "cultureId");
      String path = CULTURE_FACTION_PREFIX + cultureId.getPath();
      return FactionId.of(ResourceLocation.fromNamespaceAndPath(Millenaire.MODID, path));
   }

   /**
    * Stable id for a player faction. The UUID is the faction's own identity, not necessarily a
    * player UUID (future phases may create multiple player factions).
    */
   public static FactionId playerFaction(UUID factionUuid) {
      Objects.requireNonNull(factionUuid, "factionUuid");
      String path = PLAYER_FACTION_PREFIX + factionUuid;
      return FactionId.of(ResourceLocation.fromNamespaceAndPath(Millenaire.MODID, path));
   }

   /**
    * Unique breakaway faction id for Phase 9 secession / revolutionary splits.
    */
   public static FactionId breakawayFaction(UUID factionUuid) {
      Objects.requireNonNull(factionUuid, "factionUuid");
      String path = BREAKAWAY_FACTION_PREFIX + factionUuid;
      return FactionId.of(ResourceLocation.fromNamespaceAndPath(Millenaire.MODID, path));
   }

   public static boolean isDefaultCultureFactionId(FactionId factionId, ResourceLocation cultureId) {
      return factionId != null && cultureId != null && factionId.equals(defaultCultureFaction(cultureId));
   }

   /** True when the id path uses the player-faction prefix (even if the faction record is missing). */
   public static boolean isPlayerFactionId(@Nullable FactionId factionId) {
      return factionId != null && factionId.id().getPath().startsWith(PLAYER_FACTION_PREFIX);
   }
}
