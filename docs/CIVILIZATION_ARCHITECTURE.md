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
| Colonization | `ColonizationService` + Phase 4 plans | `millenaire_colonization` + worldgen plans |
| Housing capacity | residential `BuildingPlanSet` occupant lists | derived |
| Orchestration cursors / offscreen clocks | `CivilizationSavedData` (schema ≥ 2) | `millenaire_civilization` |

## Fair budgets

Each stage has its **own** cursor (`membershipCursor`, `populationCursor`, `developmentCursor`, `offscreenCursor`, `tradeCursor`, `migrationCursor`, `colonizationCursor`). Round-robin processing bounds both **scans** and **mutations**. Empty lists and size changes use `floorMod`.

## Migration

1. Eligibility uses real fields (`isAwayHired`, `isAwayRaiding`, `isRaidingVillage`, hire state) plus type rules; children are not independent migrants.
2. Cooldown is filtered **before** migrant selection so one cooling UUID cannot block others; success cooldown is applied only after `MigrationResult.completed()`.
3. Destination housing matches gender/resident declarations when known; factionless villages only migrate to other factionless destinations.
4. `migrate` returns `MigrationResult` (`COMPLETE` / `ALREADY_COMPLETE` / `REJECTED` / `FAILED` / `RECOVERY_REQUIRED` / …). A non-null operation id is **not** success.
5. Single mutation path: `Village.transferVillagerPermanently`.
6. `putMigration` does not duplicate queue entries; retention skips active/recovery ops.
7. `recoverPending` consumes `needsRecovery` flags on the demographics stage.
8. Events and metrics publish only on completed migrations.

## Trade / logistics

- Importers chosen by **local** Living Villages shortages (faction balances only seed candidates).
- Exporters chosen by **local** exportable stock (reserve protected) + diplomacy/war filters before ranking.
- Failed candidates do not burn the day; success dedupe is written **after** conserved commit.
- Same-faction moves are logistics (`civilizationIntraFactionLogistics`), not diplomatic trade records.

## Offscreen

Separates last **attempt**, last **success**, and per-faction last economy/offscreen time. Elapsed is read **before** overwriting stage clocks. Work is profile refresh only — `civilizationCatchupDays` caps that refresh window. No force-load, no free goods, no combat simulation.

## Autonomous founding

Default **off**. Explicit lifecycle on `ColonizationOperation` + `SettlementPlan.FoundingMode.COLONIZATION`:

1. Parent pressure / faction / war / cooldown gates  
2. Site + `SettlementPlanner.ensureRegionPlan` (culture-compatible, non-lone)  
3. Immediate `SettlementLayoutPlanner.ensureLayout`  
4. Exact cost reservation from building `requiredResources` (fallback levy 32 any-log + 32 cobble when content has no costs)  
5. `WorldgenEngine.kickPlanExecution` queues already-loaded footprint chunks (no new chunk-event required)  
6. Colony activation skips free occupants and free starting goods; assigns `parentFactionId`  
7. Intended founders migrate via `MigrationResult`; `MIGRATED` only when the founder target is met  
8. `VILLAGE_FOUNDED` emits once after successful founding, not from the natural activator path  

Refunds restore exact reserved stacks (or keep `refundOutstanding`). Consumed physical founding does not invent a full refund. When worldgen is disabled, colonization records `blocked_worldgen_disabled`.

## Crash consistency

Mutations may span civilization SD, village SD, colonization SD, and chunk block data. Operations keep explicit status / recovery flags; there is no cross-file atomic commit.
