package org.millenaire.faction.relationship;

import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.FactionId;

/**
 * Internal/debug accessors for faction relationships. Not a player-facing GUI.
 */
public final class RelationshipQueries {
   private RelationshipQueries() {
   }

   public static RelationshipManager manager(ServerLevel level) {
      return RelationshipSavedData.get(level).getRelationshipManager();
   }

   public static int getScore(ServerLevel level, FactionId source, FactionId target) {
      return manager(level).getRelationshipScore(source, target);
   }

   @Nullable
   public static FactionRelationship get(ServerLevel level, FactionId source, FactionId target) {
      return manager(level).getRelationship(source, target);
   }

   public static boolean has(ServerLevel level, FactionId source, FactionId target) {
      return manager(level).hasRelationship(source, target);
   }

   public static RelationshipCategory category(ServerLevel level, FactionId source, FactionId target) {
      return manager(level).getRelationshipCategory(source, target);
   }

   public static boolean isFriendly(ServerLevel level, FactionId source, FactionId target) {
      return manager(level).isFriendly(source, target);
   }

   public static boolean isHostile(ServerLevel level, FactionId source, FactionId target) {
      return manager(level).isHostile(source, target);
   }

   public static boolean isPositive(ServerLevel level, FactionId source, FactionId target) {
      return manager(level).isPositive(source, target);
   }

   public static boolean isNegative(ServerLevel level, FactionId source, FactionId target) {
      return manager(level).isNegative(source, target);
   }

   public static Collection<FactionRelationship> from(ServerLevel level, FactionId source) {
      return manager(level).getAllRelationships(source);
   }

   public static Collection<FactionRelationship> involving(ServerLevel level, FactionId faction) {
      return manager(level).getAllRelationshipsInvolving(faction);
   }

   @Nullable
   public static FactionRelationship mostPositive(ServerLevel level, FactionId source) {
      return manager(level).getMostPositiveRelationship(source);
   }

   @Nullable
   public static FactionRelationship mostNegative(ServerLevel level, FactionId source) {
      return manager(level).getMostNegativeRelationship(source);
   }

   public static Collection<FactionId> relatedTargets(ServerLevel level, FactionId source) {
      return manager(level).getRelatedTargets(source);
   }

   public static Collection<FactionId> relatedSources(ServerLevel level, FactionId target) {
      return manager(level).getRelatedSources(target);
   }
}
