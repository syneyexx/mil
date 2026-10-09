# Final Release Audit (Phases 1–5) — Static Source

**Scope:** Static inspection only. No compilation, CI, GameTests, Minecraft launch, or live AI verification.

Severity: BLOCKER · CRITICAL · MAJOR · MINOR · INFORMATIONAL

## Fixed in Phase 5 (this branch)

| ID | Severity | Finding | Fix |
|----|----------|---------|-----|
| P5-01 | BLOCKER | `SettlementActivator` reflected missing `VillageEventService` | Direct `LivingEventService.emit(VILLAGE_FOUNDED, …)` |
| P5-02 | CRITICAL | No causal civilization orchestration / quotas | `CivilizationOrchestrator` stages + budgets |
| P5-03 | CRITICAL | Faction outcomes not published to Living events | `CivilizationEventBridge` + new `LivingEventType`s |
| P5-04 | CRITICAL | `BuildingInventory.addStack` copy semantics broke conserved trade accounting | Before/after `getCount` in `SettlementTradeExecutor` |
| P5-05 | CRITICAL | Code called nonexistent `FactionId.uuid()` | Use `FactionId.asString()`; population keys are strings |
| P5-06 | MAJOR | No physical conserved settlement trade | `SettlementTradeExecutor` + `NeedsTradeBridge` |
| P5-07 | MAJOR | No demographics / migration txn layer | `PopulationAggregator` + `MigrationTransactionService` |
| P5-08 | MAJOR | No offscreen catch-up | `OffscreenReconciler` |
| P5-09 | MAJOR | No civilization config / disable switch | `MillenaireServerConfig` civilization section |
| P5-10 | MAJOR | Leadership/treaty/war chronicle spam risk | State markers seed-on-load; emit on change |

## Remaining risks (not falsely claimed fixed)

| ID | Severity | Finding | Notes |
|----|----------|---------|-------|
| R-01 | MAJOR | Autonomous colonization unsupported | Config forced false; document only |
| R-02 | MAJOR | Offscreen does not simulate physical combat/block placement | By design; coarse economy refresh only |
| R-03 | MAJOR | Same-faction physical trade does not update `recordSettlementGoodsTransfer` | TradeManager skips same-faction; NeedsTradeBridge uses cross-faction exporters |
| R-04 | MINOR | First leadership appointment after new faction may not chronicle | Marker seeds without first emit |
| R-05 | MINOR | Migration API not exposed as player command | Service available for integration/tests |
| R-06 | INFORMATIONAL | FactionLifecycle still exists as fallback when civ disabled | Intentional |
| R-07 | INFORMATIONAL | Runtime performance under many factions unverified | User must profile |

## Phase 3 / 4 static notes

- Phase 3 Living Villages package present; event pipeline reused.
- Phase 4 worldgen activation now correctly hooks Living events.
- Prior Phase 4 SiteValidator/KEY_ONLY work assumed intact on baseline `cc7af9c`.

## Security (static)

- Civilization tick path does not call NPC model HTTP.
- Dialogue context is read-only verified facts; no command execution from model text in this layer.
- Config disable of Phase 5 does not disable chat/villages/worldgen.
