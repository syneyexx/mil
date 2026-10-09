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

## Remaining limits (honest)

| ID | Notes |
|----|-------|
| L-01 | Offscreen is profile refresh, not full temporal simulation |
| L-02 | Autonomous founding requires already-loaded destination chunks |
| L-03 | Cross-SavedData crash consistency is best-effort with recovery flags |
| L-04 | Bridge placement is procedural, not template-pack based |
| L-05 | Runtime performance and gameplay balance unverified |
| L-06 | Player-block provenance incomplete — road guard is conservative, not omniscient |
| L-07 | Authorize-colonization never enables the master switch; founding stays default-off |

## Security (static)

- Civilization / worldgen / migration / trade / colonization cost paths do not call LLM APIs to authorize mutations.
- NPC dialogue uses the same presentation facts as UI and only points at server-owned action proposals.
