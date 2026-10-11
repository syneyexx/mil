/**
 * Bounty Hunter law-enforcement system.
 *
 * <p>Integrates with the existing culture bounty ledger ({@code CultureBountyManager}),
 * Millénaire jail buildings ({@code BuildingPurpose.JAIL}), and arrest custody records.
 * Hunters are event-dispatched only when a player has an enforceable bounty and may deliver
 * the target to any valid jail of the issuing culture, or any culture jail as fallback.
 */
package org.millenaire.faction.crime.hunter;
