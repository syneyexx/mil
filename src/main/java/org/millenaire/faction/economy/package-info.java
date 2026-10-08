/**
 * Phase 3 faction economy and trade layer.
 *
 * <p>Aggregates Millénaire settlement stock/demand without duplicating chest inventories.
 * Records directional faction trade (routes, activity, history, disruption state) and can
 * gently influence Phase 2 political relationships through {@code RelationshipChangeReason.TRADE}.
 *
 * <p>Does not implement diplomacy, embargoes, wars, or alliances. Route disruption states exist
 * so later phases can interact safely.
 */
package org.millenaire.faction.economy;
