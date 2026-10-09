# Final Feature Inventory — Phase 5

Legend: **IMPLEMENTED_UNVERIFIED** = source complete, runtime not verified by agent. **PARTIAL** = present with documented limits. **UNSUPPORTED** = intentionally disabled/documented.

| Feature | Status | Primary APIs |
|---------|--------|--------------|
| Civilization orchestrator | IMPLEMENTED_UNVERIFIED | `CivilizationOrchestrator.onServerTick` |
| Config master switch + budgets | IMPLEMENTED_UNVERIFIED | `MillenaireServerConfig` `civilization*` |
| Membership reconcile cadence | IMPLEMENTED_UNVERIFIED | orchestrator + `FactionManager.reconcileSettlements` |
| Population aggregates | IMPLEMENTED_UNVERIFIED | `PopulationAggregator` |
| Migration transactions | IMPLEMENTED_UNVERIFIED | `MigrationTransactionService` |
| Conserved physical trade | IMPLEMENTED_UNVERIFIED | `SettlementTradeExecutor` |
| Needs→trade bridge | IMPLEMENTED_UNVERIFIED | `NeedsTradeBridge` |
| Politics chronicle bridge | IMPLEMENTED_UNVERIFIED | `CivilizationEventBridge.publishPoliticsChanges` |
| Diplomacy chronicle bridge | IMPLEMENTED_UNVERIFIED | `publishDiplomacyChanges` |
| War/peace chronicle bridge | IMPLEMENTED_UNVERIFIED | `publishWarChanges` |
| Settlement development labels | IMPLEMENTED_UNVERIFIED | `SettlementDevelopmentClassifier` |
| Offscreen economy catch-up | IMPLEMENTED_UNVERIFIED | `OffscreenReconciler` |
| Living event types (civ) | IMPLEMENTED_UNVERIFIED | `LivingEventType` LEADERSHIP/TREATY/WAR/PEACE/TRADE/SETTLEMENT_DEVELOPMENT |
| Worldgen founded event | IMPLEMENTED_UNVERIFIED | `SettlementActivator.emitOptionalPhase3Event` |
| NPC civ dialogue context | IMPLEMENTED_UNVERIFIED | `CivilizationContextProvider` |
| Village panel civ lines | IMPLEMENTED_UNVERIFIED | `VillageOverviewPanelGenerator.appendCivilizationLines` |
| Dev command | IMPLEMENTED_UNVERIFIED | `/millenaire dev civilization` |
| en_us / nl_nl strings | IMPLEMENTED_UNVERIFIED | lang JSON |
| Existing politics/diplomacy/war managers | Pre-existing + integrated | faction packages |
| Autonomous founding | UNSUPPORTED | config default false |
| Abstract→physical free items | UNSUPPORTED | forbidden by design |
| Force-loaded offscreen combat | UNSUPPORTED | forbidden by design |
| LLM-driven faction decisions | UNSUPPORTED | forbidden by design |

## Pre-existing (baseline) still authoritative

Universal NPC dialogue, Living Villages 2.0, Phase 4 deterministic worldgen, VillageManager, quests, reputation, TradeManager abstract economy, DiplomaticManager, PoliticsManager, MilitaryManager, WarManager.
