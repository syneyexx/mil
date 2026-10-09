# Civilization Architecture (Phase 5)

**Status:** IMPLEMENTED_UNVERIFIED (source-level). Builds/tests/Minecraft were not run by the agent.

## Authority diagram

```
Millenaire server tick (overworld)
 └─ CivilizationOrchestrator.onServerTick   [when civilizationEvolutionEnabled]
     ├─ LivingEventService.beginTick
     ├─ EVENT_INGEST          → CivilizationEventBridge.ingestPending
     ├─ MEMBERSHIP_RECONCILE  → FactionManager.reconcileSettlements (+ independent membershipCursor)
     ├─ relationships         → RelationshipManager.tickDecay
     ├─ ECONOMY               → TradeManager.tick + NeedsTradeBridge (tradeCursor)
     ├─ POLITICS / DIPLOMACY / MILITARY / WAR → existing managers + chronicle bridges
     ├─ DEMOGRAPHICS          → PopulationAggregator (populationCursor) + MigrationEvaluator
     ├─ DEVELOPMENT           → SettlementDevelopmentClassifier (developmentCursor) + ColonizationService
     ├─ HISTORY_PUBLISH       → flush pending chronicle keys
     └─ OFFSCREEN_RECONCILE   → OffscreenReconciler profile refresh (reads previous timestamp first)
 └─ else FactionLifecycle.onServerTick
```

## Canonical owners

| Concern | Owner | Persistence |
|---------|-------|-------------|
| Faction membership | `FactionManager` | `millenaire_factions` |
| Village residents / homes | `Village` / `VillagerRecord` | `millenaire_villages` |
| Building inventory | `BuildingInventory` | village buildings |
| Abstract economy | `TradeManager` | economy SavedData |
| Physical transfers | `SettlementTradeExecutor` | inventories + success dedupe after commit |
| Migration | `MigrationTransactionService` → `transferVillagerPermanently` | civilization SD ops + village SD |
| Colonization | `ColonizationService` + Phase 4 plans | `millenaire_colonization` (schema ≥ 3) + worldgen plans |
| Founder reservations | `ColonizationSavedData.founderReservations` | exclusive villager→op index |
| Housing capacity | residential `BuildingPlanSet` occupant lists | derived |
| Presentation | `CivilizationPresentationService` | derived only (not persisted) |
| Player actions | `CivilizationActionService` via `NpcAction*` | server-validated intents |
| Orchestration cursors / offscreen clocks | `CivilizationSavedData` (schema ≥ 2) | `millenaire_civilization` |

## Fair budgets

Each stage has its **own** cursor (`membershipCursor`, `populationCursor`, `developmentCursor`, `offscreenCursor`, `tradeCursor`, `migrationCursor`, `colonizationCursor`). Round-robin processing bounds both **scans** and **mutations**. Empty lists and size changes use `floorMod`.

Colonization additionally persists `advanceCursor` and `refundCursor` in `ColonizationSavedData` so waiting ops cannot starve later ones, and refunds have an independent budget from active founding work.

## Migration

1. Eligibility uses real fields (`isAwayHired`, `isAwayRaiding`, `isRaidingVillage`, hire state) plus type rules; children are not independent migrants.
2. Cooldown is filtered **before** migrant selection so one cooling UUID cannot block others; success cooldown is applied only after `MigrationResult.completed()`.
3. Destination housing matches gender/resident declarations when known; factionless villages only migrate to other factionless destinations.
4. `migrate` returns `MigrationResult` (`COMPLETE` / `ALREADY_COMPLETE` / `REJECTED` / `FAILED` / `RECOVERY_REQUIRED` / …). A non-null operation id is **not** success.
5. Single mutation path: `Village.transferVillagerPermanently`.
6. Exclusive colonization founder reservations block ordinary migration destinations other than the matching colony.
7. `putMigration` does not duplicate queue entries; retention skips active/recovery ops.
8. `recoverPending` consumes `needsRecovery` flags on the demographics stage.
9. Events and metrics publish only on completed migrations.

## Trade / logistics

- Importers chosen by **local** Living Villages shortages (faction balances only seed candidates).
- Exporters chosen by **local** exportable stock (reserve protected) + diplomacy/war filters before ranking.
- Failed candidates do not burn the day; success dedupe is written **after** conserved commit.
- Same-faction moves are logistics (`civilizationIntraFactionLogistics`), not diplomatic trade records.

## Offscreen

Separates last **attempt**, last **success**, and per-faction last economy/offscreen time. Elapsed is read **before** overwriting stage clocks. Work is profile refresh only — `civilizationCatchupDays` caps that refresh window. No force-load, no free goods, no combat simulation.

## Autonomous founding

Default **off** (`civilizationAutonomousFoundingEnabled`). Explicit lifecycle on `ColonizationOperation` + `SettlementPlan.FoundingMode.COLONIZATION`:

1. Parent pressure / faction / war / cooldown gates  
2. Site + `SettlementPlanner.ensureRegionPlan` (culture-compatible, non-lone)  
3. Immediate `SettlementLayoutPlanner.ensureLayout`  
4. Exact stack reservation via `BuildingInventory.removeByItemExact` / `removeByTagExact` + `AnywoodHelper.LOGS_TAG` (fallback levy 32 any-log + 32 cobble when content has no costs)  
5. Exclusive founder slots (`INTENDED` / `RESERVED` / `TEMP_BLOCKED` / `UNSUITABLE` / `MIGRATED`) with replacement of permanently unsuitable candidates  
6. `WorldgenEngine.kickPlanExecution` queues already-loaded footprint chunks (no new chunk-event required)  
7. Colony activation skips free occupants and free starting goods; assigns `parentFactionId`  
8. Founders migrate via `MigrationResult`; `MIGRATED` only when the founder target is met  
9. `VILLAGE_FOUNDED` emits once after successful founding (only when `LivingEventService.emit` succeeds — budget exhaustion leaves the flag unset for retry)

**Consumption policy:** reserved stacks are held until physical materialization begins (`costsConsumed`). Full refund applies only when founding fails before that transition. Incomplete refunds persist as `refundOutstanding` and are drained on an independent refund cursor even when new proposals are disabled.

**Config off:** stops new proposals; continues safe advance of already-active ops and refunds. Does not silently enable autonomous founding from a player button.

## Safe road / bridge placement

`RoadPlacementGuard` is the shared permission helper for local and regional roads/bridges. Natural terrain may receive path surfaces; ordinary building materials, containers, village/planned footprints, and structure conflicts block overwrite. Loaded chunks and missing block entities are never sufficient proof. Slope continuity uses persisted `lastPlaced*` on regional routes. Detours are spliced as continuous segments. Bridges require clear passage above the deck before `COMPLETE`.

Protection limits: Minecraft does not always provide reliable provenance for every player-placed block. Guard uses available footprint/claim/structure rules and conservative natural-terrain overwrite — not a claim of perfect player-build detection.

## Presentation and player actions

`CivilizationPresentationService` builds bounded village/faction snapshots from authoritative services (no force-load; unknown inventory ≠ zero). The village book **Civilization** section, summary lines, NPC dialogue context, and `NpcActionType` civilization buttons all read the same snapshot derivation.

| Action | Visitor | Controller | Notes |
|--------|---------|------------|-------|
| Support need | yes | yes | Uses `VillageRequestService` + `VillageDeliveryService` |
| Prioritize housing | no | yes | Reorders existing controlled residential queue entry |
| View colonization | yes* | yes | Visibility when civilization on + proposal or founding enabled |
| Support colonization materials | yes | yes | Delivers into live needs — no parallel donation inventory |
| Authorize colonization | no | yes | Revalidates and kicks plan; never flips master switch |
| View interrupted op | yes | yes | Gameplay-safe inspection only — no overwrite repair |

\* Subject to existing village reach / visibility checks.

## NPC context

`CivilizationContextProvider` injects role-scoped facts from the same presentation snapshots (chief / merchant / builder / resident). Dialogue may describe facts and point at server-owned action buttons; LLMs never authorize costs, migration, founding, or rewards.

## Crash consistency

Mutations may span civilization SD, village SD, colonization SD, and chunk block data. Operations keep explicit status / recovery flags; there is no cross-file atomic commit. Prefer idempotent steps and bounded recovery.
