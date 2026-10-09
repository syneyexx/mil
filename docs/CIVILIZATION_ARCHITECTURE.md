# Civilization Architecture (Phase 5)

**Status:** IMPLEMENTED_UNVERIFIED (source-level). Builds/tests/Minecraft were not run by the agent.

## Authority diagram

```
Millenaire server tick (overworld)
 └─ CivilizationOrchestrator.onServerTick   [when civilizationEvolutionEnabled]
     ├─ LivingEventService.beginTick
     ├─ EVENT_INGEST          → CivilizationEventBridge.ingestPending
     ├─ MEMBERSHIP_RECONCILE  → FactionManager.reconcileSettlements + PoliticsManager.ensurePolitics
     ├─ relationships         → RelationshipManager.tickDecay (every orchestrator pass)
     ├─ ECONOMY               → TradeManager.tick + NeedsTradeBridge
     ├─ POLITICS              → PoliticsManager.tick + publishPoliticsChanges
     ├─ DIPLOMACY             → DiplomaticManager.tick + publishDiplomacyChanges
     ├─ MILITARY              → MilitaryManager.tick
     ├─ WAR                   → WarManager + TerritoryManager + ConflictManager + publishWarChanges
     ├─ DEMOGRAPHICS          → PopulationAggregator
     ├─ DEVELOPMENT           → SettlementDevelopmentClassifier
     ├─ HISTORY_PUBLISH       → shortage chronicle + history flush
     └─ OFFSCREEN_RECONCILE   → OffscreenReconciler (no chunk force-load)
 └─ else FactionLifecycle.onServerTick      [legacy direct manager ticks]
```

When civilization is disabled, Phase 3 Living Villages, Phase 4 worldgen, villages, trade UI, quests and NPC chat remain active.

## Canonical owners (do not duplicate)

| Concern | Owner | Persistence |
|---------|-------|-------------|
| Faction membership | `FactionManager` | `millenaire_factions` |
| Village residents | `Village` / `VillagerRecord` | `millenaire_villages` |
| Building inventory | `BuildingInventory` | village buildings |
| Abstract economy | `TradeManager` / profiles | economy SavedData |
| Physical trade moves | `SettlementTradeExecutor` | via inventories + daily dedupe in civilization SD |
| Diplomacy | `DiplomaticManager` | diplomacy SavedData |
| Politics | `PoliticsManager` | politics SavedData |
| War / armies | `WarManager` / `MilitaryManager` | war / military SD |
| Living events | `LivingEventService` | `millenaire_living_events` |
| Settlement plans | Phase 4 worldgen | plan SavedData |
| Orchestration progress | `CivilizationSavedData` | `millenaire_civilization` |

## Scheduling

Stages use per-stage intervals in `CivilizationConfig` plus ordinal stagger. Per-tick budgets come from `MillenaireServerConfig` (`civilizationFactionBudget`, `civilizationTradeOpsBudget`, `civilizationEventPublishBudget`). Iteration order is deterministic (`FactionId.asString()` / `VillageId.uuid()` sort).

## Membership & territory

- Culture ≠ faction. Multiple factions may share a culture.
- Village affiliation is `Village.factionId` + `Faction.settlementIds`, reconciled by `FactionManager.reconcileSettlements`.
- Hamlets keep parent village IDs from existing village model.
- Territory claims remain in `TerritoryManager`; Phase 5 does not auto-claim Minecraft chunks or alter player protection.
- Autonomous live founding is **disabled** (`civilizationAutonomousFoundingEnabled=false`).

## Economy & conservation

- Abstract balances: `FactionEconomicProfile` / `ResourceBalance` (derived, not free items).
- Physical transfers: `SettlementTradeExecutor.tryTransferDeficitItem` removes from exporter inventories then adds to importer; undelivered remainder is compensated back.
- `BuildingInventory.addStack` copies stacks — placement count uses before/after `getCount`.
- Cross-faction trade requires `DiplomaticManager.canTrade` and not `WarManager.areAtWar`.
- Daily dedupe key prevents duplicate transfer effects after restart.

## Politics / diplomacy / war

Existing managers remain authoritative. Phase 5 publishes verified transitions into `LivingEventService` via state markers (first observation after reload seeds without re-emitting; recent creations may emit once).

## Demographics / migration

- Aggregates from real `VillagerRecord` maps only.
- `MigrationTransactionService` moves a record origin→destination with operation ID, capacity check, duplicate prevention, and LivingEvent `MIGRATION`. Does not force-load or teleport unloaded entities.

## Offscreen reconciliation

- Refreshes economic profiles for factions with inactive settlements.
- Caps catch-up to `civilizationCatchupDays` / `CivilizationConfig.MAX_OFFSCREEN_CATCHUP_TICKS`.
- Never force-loads chunks; physical combat/block placement stays deferred.

## Phase 3 / 4 integration

- Activation: `SettlementActivator` emits `LivingEventType.VILLAGE_FOUNDED` via `LivingEventService`.
- Needs→trade: `NeedsTradeBridge` uses Living Villages needs + economic deficits.
- Dialogue: `CivilizationContextProvider` appends verified faction/leader/economy/treaty/war facts for chiefs (via village context) and traders.
- Panel: village summary shows civilization block.

## Persistence & security

- Schema version in `CivilizationSavedData` (`schema`).
- Malformed optional keys are skipped with warnings.
- Server-thread mutation only; no LLM calls in civilization ticks.
- Player civilization mutations go through existing faction/politics/diplomacy/war APIs (permission-checked commands already present).
