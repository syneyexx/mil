/**
 * Phase 7 conquest, occupation, territory, and post-war settlement layer.
 *
 * <p>Authoritative ownership is {@link org.millenaire.faction.territory.Territory}: permanent owner,
 * military controller, and occupier are distinct. Capture creates occupation without transferring
 * settlement {@code factionId}; permanent transfer occurs only through validated peace treaties
 * via {@link org.millenaire.faction.territory.TerritoryManager#transferOwnership} /
 * {@link org.millenaire.faction.territory.TerritoryManager#finalizePeaceFromWar}.
 *
 * <p>Culture remains on the Millénaire settlement. Integration is administrative/political only.
 * Phase 8 may extend claims, disputes, borders, and rebellion pressure exposed here — do not add
 * parties, elections, governments, or civil-war systems in this package.
 */
package org.millenaire.faction.territory;
