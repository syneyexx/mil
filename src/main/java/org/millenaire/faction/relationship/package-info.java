/**
 * Phase 2 directional faction relationship system.
 *
 * <p>Relationships are political opinions {@code Faction A → Faction B} on a clamped -100/+100 scale,
 * independent of Millénaire village/player reputation. Self-relations are forbidden. Missing records
 * resolve to neutral (0) without creating persistent entries.
 *
 * <p>Future phases may call {@link org.millenaire.faction.relationship.RelationshipManager#modifyRelationship}
 * with a {@link org.millenaire.faction.relationship.RelationshipChangeReason}; they must not bypass the manager.
 * Scores alone never declare war or alliances.
 */
package org.millenaire.faction.relationship;
