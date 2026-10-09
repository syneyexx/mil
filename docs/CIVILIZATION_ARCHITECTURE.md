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

1. Eligibility (not killed, not hired/away, not raider/visitor).
2. Destination housing selected from residential plan capacity.
3. Single mutation path: `Village.transferVillagerPermanently` (home, entity villageId, teleport/discard).
4. `putMigration` does not duplicate queue entries; retention skips active/recovery ops.
5. Events publish only on COMPLETE.

## Trade / logistics

- Importers chosen by **local** Living Villages shortages (faction balances only seed candidates).
- Exporters chosen by **local** exportable stock (reserve protected) + diplomacy/war filters before ranking.
- Failed candidates do not burn the day; success dedupe is written **after** conserved commit.
- Same-faction moves are logistics (`civilizationIntraFactionLogistics`), not diplomatic trade records.

## Offscreen

Separates last **attempt**, last **success**, and per-faction last economy/offscreen time. Elapsed is read **before** overwriting stage clocks. Work is profile refresh only — `civilizationCatchupDays` caps that refresh window. No force-load, no free goods, no combat simulation.

## Autonomous founding

Default **off**. When enabled: housing/population pressure + real inventory costs + nearby loaded-chunk region via `SettlementPlanner` + later migration of real residents. Never invents free villagers. Block reasons are stored in civilization markers for panels/NPC context.

## Crash consistency

Mutations may span civilization SD, village SD, colonization SD, and chunk block data. Operations keep explicit status / recovery flags; there is no cross-file atomic commit.
