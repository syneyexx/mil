/**
 * Faction political identity (Phase 1), relationships (Phase 2), economy/trade (Phase 3),
 * formal diplomacy (Phase 4), military organization (Phase 5), warfare (Phase 6),
 * territory/conquest/occupation (Phase 7 under {@code org.millenaire.faction.territory}),
 * and internal politics/governance (Phase 8 under {@code org.millenaire.faction.politics}).
 *
 * <p>Keep these layers separate:
 * <ul>
 *   <li>Relationship score — opinion (-100/+100)</li>
 *   <li>Trade routes/activity — economic interaction</li>
 *   <li>Diplomatic agreements — formal political status (peace, NAP, alliance, embargo, …)</li>
 *   <li>Territory — permanent owner vs military controller vs occupier</li>
 *   <li>Internal politics — government, leadership, groups, stability (distinct from territorial stability)</li>
 * </ul>
 */
package org.millenaire.faction;
