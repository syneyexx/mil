# Civilization — Local Manual Validation

**Agent did not execute these scenarios.** All checkboxes start unchecked. Prerequisites: Minecraft 1.21.1, NeoForge 21.1.x, Java 21, build from this branch.

Diagnostics: `/millenaire dev civilization status|metrics|stages`, `/millenaire dev politics`, `/millenaire living events`.

---

## 1. Trade-driven development
- **Prerequisites:** Two faction-affiliated villages with inventories; importer deficit (iron/etc.); exporter surplus; trade permitted; `civilizationAutoTradeEnabled=true`.
- **Setup:** Seed exporter chests with surplus item; empty importer; wait economy stage (~200+ ticks).
- **Actions:** Observe; optional `/millenaire dev civilization metrics`.
- **Expected visible:** Importer gains item; exporter loses same count; Living event `TRADE_COMPLETED`.
- **Authoritative:** Inventory conservation; `CivilizationMetrics.TRADE_OPS` increments once/day/key.
- **Fail symptoms:** Items duplicated; trade with embargo/war; zero placement but exporter emptied.
- **Result:** ☐ PASS ☐ FAIL

## 2. Trade blockade
- **Prerequisites:** Working trade path from scenario 1.
- **Setup:** Embargo or declare war between factions.
- **Expected:** Further physical transfers blocked; after peace/embargo end, trade may resume next day key.
- **Result:** ☐ PASS ☐ FAIL

## 3. Political succession
- **Prerequisites:** Faction with government/leader.
- **Setup:** Trigger succession via existing politics APIs/dev tools.
- **Expected:** Leader changes in politics queries; one `LEADERSHIP_CHANGED` event; panel/NPC context shows new leader; no spam every tick.
- **Result:** ☐ PASS ☐ FAIL

## 4. Alliance / treaty
- **Prerequisites:** Two factions eligible for trade/alliance agreement.
- **Setup:** Create ACTIVE agreement via diplomacy system.
- **Expected:** `TREATY_SIGNED` once; `canTrade`/military cooperation reflects type; UI/context lists treaty.
- **Result:** ☐ PASS ☐ FAIL

## 5. War and peace
- **Prerequisites:** Valid war declaration path.
- **Setup:** Declare war → operations → ceasefire/peace.
- **Expected:** State machine transitions; `WAR_DECLARED`/`PEACE_SIGNED` on changes; trade blocked while at war; no infinite raid loop from orchestrator alone.
- **Result:** ☐ PASS ☐ FAIL

## 6. Loaded vs unloaded
- **Prerequisites:** Faction with at least one inactive settlement; `civilizationOffscreenEnabled=true`.
- **Setup:** Move away so village inactive; wait offscreen stage.
- **Expected:** Profile refresh without forced chunk tickets; on reactivation no duplicate trade/events for same keys.
- **Result:** ☐ PASS ☐ FAIL

## 7. Migration
- **Prerequisites:** Origin and destination villages; housing capacity on destination.
- **Setup:** Call `MigrationTransactionService.migrate` (dev/test hook) for one villager UUID.
- **Expected:** Record leaves origin, appears once at destination; restart does not duplicate; operation logged COMPLETE.
- **Result:** ☐ PASS ☐ FAIL

## 8. Player assistance (Phase 3)
- **Prerequisites:** Living Villages requests enabled.
- **Setup:** Complete assistance delivery.
- **Expected:** Existing request rewards once; living event recorded; no second civilization reward invented.
- **Result:** ☐ PASS ☐ FAIL

## 9. History after reload
- **Setup:** Produce major events; stop server; reload.
- **Expected:** Journals retain events; markers prevent re-emit of same leadership/treaty/war state.
- **Result:** ☐ PASS ☐ FAIL

## 10. Existing save
- **Setup:** Open pre-Phase-5 world (Phase 3/4 present).
- **Expected:** Villages, minds, plans, factions load; civilization SD creates schema 1; no wiped IDs.
- **Result:** ☐ PASS ☐ FAIL

## 11. AI offline
- **Setup:** Disable NPC chat / unreachable LM Studio.
- **Expected:** Orchestrator, trade, politics, war, demographics continue; no HTTP on server thread for civ ticks.
- **Result:** ☐ PASS ☐ FAIL

## 12. Many factions
- **Setup:** World with many villages/factions; watch TPS and `/millenaire dev civilization metrics`.
- **Expected:** Bounded budgets; no full-world scan every tick; stage failures logged not crash loops.
- **Result:** ☐ PASS ☐ FAIL

## 13. Phase 4 activation
- **Setup:** Generate new settlement via worldgen engine; wait activation.
- **Expected:** One `VILLAGE_FOUNDED`; faction membership once; enters economy/politics on reconcile.
- **Result:** ☐ PASS ☐ FAIL

## 14. Restart mid-transaction
- **Setup:** Interrupt during trade day after remove-before-add if possible; or migration RESERVED.
- **Expected:** Dedupe/operation IDs prevent double delivery; compensation or failed op recorded.
- **Result:** ☐ PASS ☐ FAIL

## 15. Multiple players
- **Setup:** Concurrent diplomacy/trade/admin actions.
- **Expected:** Server-authoritative; no client-granted treaties/items.
- **Result:** ☐ PASS ☐ FAIL

## 16. Time jump
- **Setup:** `/time add` large value; `/time set`.
- **Expected:** No multi-day catch-up explosion beyond configured days; no replay of completed daily trade keys incorrectly creating items.
- **Result:** ☐ PASS ☐ FAIL

## 17. Missing content
- **Setup:** Remove optional culture/content pack referenced by a faction.
- **Expected:** Safe skips; save not corrupted.
- **Result:** ☐ PASS ☐ FAIL

## 18. Security
- **Setup:** Attempt forged/repeated serverbound faction mutations if applicable.
- **Expected:** Rejected without state change; model speech cannot declare war/grant items.
- **Result:** ☐ PASS ☐ FAIL

## 19. Crisis recovery
- **Setup:** Economy UNDER_PRESSURE; restore surplus via trade/production.
- **Expected:** Shortage resolved chronicle when healthy; crisis not permanent.
- **Result:** ☐ PASS ☐ FAIL

## 20. Long-term civilization
- **Setup:** Play many Minecraft days.
- **Expected:** Development labels evolve from real buildings/pop; history bounded; wars/treaties/trade remain coherent.
- **Result:** ☐ PASS ☐ FAIL

## 21. Colonization exact-stack refund
- **Setup:** Enable founding; parent reserves spruce/birch logs for colony; force plan failure before materialization.
- **Expected:** Same species refunded (not oak wash); `resourcesReserved` stays true if chests cannot accept full refund (`refundOutstanding`).
- **Result:** ☐ PASS ☐ FAIL

## 22. Colonization without new chunk event
- **Setup:** Destination region chunks already loaded; start founding.
- **Expected:** Layout + materialization queue starts without reloading the chunk; colony activation skips free occupants; founders migrate to founding faction.
- **Result:** ☐ PASS ☐ FAIL

## 23. Migration cooldown selection
- **Setup:** Overcrowded village; lowest UUID on cooldown; another eligible resident present.
- **Expected:** Alternate resident migrates; failed attempt does not apply full success cooldown.
- **Result:** ☐ PASS ☐ FAIL

## 24. Safe road overwrite
- **Setup:** Regional or local road path crosses player-built planks/stone near a village footprint.
- **Expected:** Placement blocks with a concrete reason; route becomes blocked/detoured — not CONNECTED through the building.
- **Result:** ☐ PASS ☐ FAIL

## 25. Fair colonization advance
- **Setup:** Multiple active colonization ops; first op waiting on unloaded chunks.
- **Expected:** Later ops still advance within budget; refunds drain even when autonomous founding is toggled off.
- **Result:** ☐ PASS ☐ FAIL

## 26. Founder replacement
- **Setup:** Colony reserved with intended founders; kill/hire one before migration completes; another eligible adult exists.
- **Expected:** Unsuitable founder released; replacement reserved; already-migrated founders kept; ordinary migration cannot steal reserved founders.
- **Result:** ☐ PASS ☐ FAIL

## 27. Civilization UI actions
- **Setup:** Controlled village with shortage + queued residential project; optional active colonization proposal.
- **Expected:** Village book Civilization section shows localized status; NPC action buttons support need / prioritize housing / view colonization; visitor cannot authorize colonization or reorder projects.
- **Result:** ☐ PASS ☐ FAIL
