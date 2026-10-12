# Final Release Audit — Static Source

**Scope:** Static inspection only. No compilation, CI, GameTests, Minecraft launch, or benchmarks.

## Integrity pass (this change set)

| ID | Finding | Resolution |
|----|---------|------------|
| W-01 | Chunk marked materialized after budget abort mid-buildings | Confirmed; chunk completion only after all units done; resume queue + progress schema |
| W-02 | Request revalidation boolean / soft inventory check | Closed: live need/project required; `request.remaining()` is only a cap; soft self-proof fallback removed |
| W-03 | Migration map-only move bypassed `transferVillagerPermanently` | Confirmed; single path now uses permanent transfer + housing select |
| W-04 | `putMigration` duplicated queue ids | Confirmed; update-in-place without re-queue; retention skips active ops |
| W-05 | Unfair stage cursors / shared membershipCursor | Confirmed; independent cursors + scan/mutate budgets |
| W-06 | Offscreen timestamp written before reconcile → elapsed 0 | Confirmed; previous time passed in; success clocks separate |
| W-07 | Trade used first village + unreachable deficit branch | Confirmed; concrete local need/stock selection + candidate fallback |
| W-08 | Housing `max(residents, buildings*2)` | Confirmed; residential plan-set capacity; overcrowding visible |
| W-09 | Autonomous founding unsupported flag | Implemented as real gated flow; default still **false** |
| W-10 | Regional roads/bridges cosmetic | Implemented materializer + bridge strategy; config meaningful |
| W-11 | Missing README / .gitignore; caches tracked | Added; caches untracked |

## Civilization usability + integrity pass (post-#6)

| ID | Finding | Resolution |
|----|---------|------------|
| C-01 | `isSafeToOverwrite` accepted ordinary building blocks | Confirmed; `RoadPlacementGuard` intent-aware natural-terrain rules for path/bridge/passage; local+regional callers wired |
| C-02 | Colonization advance starved later ops; unbounded terminal refunds | Confirmed; persisted `advanceCursor` / `refundCursor` with independent budgets |
| C-03 | Sticky intended founders without exclusive reservation | Confirmed; founder slots + exclusive reservation index; replacement of permanently unsuitable candidates |
| C-04 | “Exact” cost reserve used Item+count reconstruction | Confirmed; `removeByItemExact` / `removeByTagExact` + persistent stack encode; incomplete rollback → `refundOutstanding` |
| C-05 | Civilization facts hard to act on in UI | Confirmed; shared snapshots → village book Civilization section + `NpcActionType` civilization actions |

## Integrity persistence milestone (source changes; owner-local runtime)

| ID | Finding | Resolution (source) |
|----|---------|---------------------|
| P-01 | `CivilizationSavedData.factory` dropped Provider → trade stacks vanished | Factory/load passes registries; `ExactStackCodec` + unresolved evidence |
| P-02 | Finalization-only trades skipped by `needsWork` | `DELIVERED` + empty pending still recoverable; stable `trade-fx` key |
| P-03 | `recoverPending` ignored `trySetStatus` / cleared `needsRecovery` early | `reconcileStatus` + physical postconditions; RESERVED included |
| P-04 | Partial/unpaid colonization could materialize | `FoundingCostService` + `ColonizationMaterializationGate` on all physical entries |
| P-05 | `costsConsumed` on plan MATERIALIZING status | Consumed on first real block mutation via gate |

**Not executed by agent:** `./gradlew build`, GameTests, GitHub CI test workflow, dedicated-server smoke (owner tests locally).

## Remaining limits (honest)

| ID | Notes |
|----|-------|
| L-01 | Offscreen is profile refresh, not full temporal simulation |
| L-02 | Autonomous founding requires already-loaded destination chunks |
| L-03 | Cross-SavedData crash consistency is best-effort with recovery flags — not exactly-once |
| L-04 | Bridge placement is procedural, not template-pack based |
| L-05 | Runtime perf mitigations landed (adaptive Post budget, entity swarm goal-pick cap, entity AI attribution, phase EMA/peak, pressure-scaled materialization). Formal SLO p95 still requires operator evidence via `docs/TICK_PERFORMANCE_LOCAL_VALIDATION.md` §5 — not auto-closed without Spark/S1–S4 paste |
| L-06 | Player-block provenance incomplete — road guard is conservative, not omniscient |
| L-07 | Authorize-colonization never enables the master switch; founding stays default-off |
| L-08 | Backlog: exporter scan budgets, VillageRequest recovery, book snapshot rate limits, published-key per-entry TTL |

## Security (static)

- Civilization / worldgen / migration / trade / colonization cost paths do not call LLM APIs to authorize mutations.
- NPC dialogue uses the same presentation facts as UI and only points at server-owned action proposals.
