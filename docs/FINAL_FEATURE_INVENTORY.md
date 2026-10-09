# Final Feature Inventory

Legend: **IMPLEMENTED_UNVERIFIED** = source complete, runtime not verified by agent. **PARTIAL** = present with documented limits. **UNSUPPORTED** = intentionally disabled/documented.

| Feature | Status | Primary APIs |
|---------|--------|--------------|
| Civilization orchestrator | IMPLEMENTED_UNVERIFIED | `CivilizationOrchestrator.onServerTick` |
| Config master switch + budgets | IMPLEMENTED_UNVERIFIED | `MillenaireServerConfig` `civilization*` |
| Membership reconcile cadence | IMPLEMENTED_UNVERIFIED | orchestrator + `FactionManager.reconcileSettlements` |
| Population aggregates (real housing) | IMPLEMENTED_UNVERIFIED | `PopulationAggregator` / residential plan slots |
| Migration transactions | IMPLEMENTED_UNVERIFIED | `MigrationTransactionService` → `MigrationResult` + `transferVillagerPermanently` |
| Migration evaluator (housing pressure) | IMPLEMENTED_UNVERIFIED | `MigrationEvaluator` (cooldown-before-select + recovery) |
| Conserved physical trade | IMPLEMENTED_UNVERIFIED | `SettlementTradeExecutor` (`addExactStack`; incomplete compensation ≠ success) |
| Needs→trade bridge (concrete villages) | IMPLEMENTED_UNVERIFIED | `NeedsTradeBridge` |
| Intra-faction logistics | IMPLEMENTED_UNVERIFIED | `civilizationIntraFactionLogistics` |
| Politics/diplomacy/war chronicle bridges | IMPLEMENTED_UNVERIFIED | `CivilizationEventBridge` |
| Settlement development labels | IMPLEMENTED_UNVERIFIED | `SettlementDevelopmentClassifier` (fair cursor) |
| Offscreen economy **profile refresh** | IMPLEMENTED_UNVERIFIED | `OffscreenReconciler` |
| Autonomous founding | IMPLEMENTED_UNVERIFIED | `ColonizationService` full lifecycle (default **off**); fair advance/refund cursors; exclusive founders |
| Exact founding cost stacks | IMPLEMENTED_UNVERIFIED | `BuildingInventory.removeBy*Exact` + `ColonizationOperation` stack persistence |
| Safe road/bridge overwrite | IMPLEMENTED_UNVERIFIED | `RoadPlacementGuard` + regional slope/detour persistence |
| Civilization presentation snapshots | IMPLEMENTED_UNVERIFIED | `CivilizationPresentationService` (derived, not persisted) |
| Village book Civilization section | IMPLEMENTED_UNVERIFIED | `VillageBookService` + `VillageOverviewPanelGenerator.generateCivilization` |
| Civilization player actions | IMPLEMENTED_UNVERIFIED | `CivilizationActionService` via `NpcActionType` (need / housing / colonization / interrupted) |
| Resumable worldgen materialization | IMPLEMENTED_UNVERIFIED | Per-unit cursors in `ChunkMaterializationProgress` (schema v3) |
| Regional roads + bridges | IMPLEMENTED_UNVERIFIED | Continuous routes + resumable bridges; `CONNECTED` only when finished |
| Living request live need caps | IMPLEMENTED_UNVERIFIED | Live need required; exact-stack delivery; unsafe overload disabled |
| Living event types (civ) | IMPLEMENTED_UNVERIFIED | `LivingEventType` |
| Worldgen founded event | IMPLEMENTED_UNVERIFIED | `SettlementActivator` → `LivingEventService` |
| NPC civ dialogue context | IMPLEMENTED_UNVERIFIED | `CivilizationContextProvider` (role-scoped shared snapshots) |
| Dev command | IMPLEMENTED_UNVERIFIED | `/millenaire dev civilization` |
| en_us / nl_nl strings | IMPLEMENTED_UNVERIFIED | lang JSON |
| Abstract→physical free items | UNSUPPORTED | forbidden by design |
| Force-loaded offscreen combat | UNSUPPORTED | forbidden by design |
| LLM-driven faction decisions | UNSUPPORTED | forbidden by design |

## Pre-existing (baseline) still authoritative

Universal NPC dialogue, Living Villages 2.0, Phase 4 deterministic worldgen, VillageManager, quests, reputation, TradeManager abstract economy, DiplomaticManager, PoliticsManager, MilitaryManager, WarManager.
