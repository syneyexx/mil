# World Generation Overhaul — Implementation Ledger

**Status legend:** `IMPLEMENTED_UNVERIFIED` · `PARTIAL`  
Build / CI / Minecraft runtime / worldgen benchmarks were **not** executed by this agent.

## Architecture (combined with Living Villages + civilization)

1. **Planning** — `SettlementPlanner` / `SettlementLayoutPlanner` (deterministic, no village registration)
2. **Materialization** — `ChunkSettlementMaterializer` with per-chunk `ChunkMaterializationProgress`, shared `MaterializationTickBudget`, and `MaterializationResumeQueue`
3. **Activation** — `SettlementActivator` after true physical completion; attempts consume activation budget; emits `LivingEventService` `VILLAGE_FOUNDED` directly (no reflective Phase-3 hook)
4. **Regional connectors** — `RegionalRoadPlanner` + `RegionalRoadMaterializer` when `worldgenGenerateRegionalRoads` is true; bridges when `worldgenGenerateBridges` is true

## Resumable materialization

| State | Meaning |
|-------|---------|
| Chunk loaded | Available for work; never force-loaded to finish |
| Queued | Unique `(planId, chunk)` in resume queue |
| Partial | Progress units recorded (terrain vs placement vs roads/walls) |
| Fully materialized | All required units for that chunk done → only then `materializedChunks` |
| PHYSICAL_COMPLETE | All buildings/walls/roads done for the plan |
| ACTIVE | Gameplay village registered (idempotent) |

Budget exhaustion is a **deferral** (`BLOCKED` / `budget_exhausted`), not a terminal plan failure.

### Legacy worlds

Unfinished plans whose old `materializedChunks` may have been marked after mid-building aborts are migrated defensively: completion flags reopen, terrain is assumed prepared (`legacyAmbiguous`), placement resumes without re-clearing. Active settlements keep trusted completion.

## Regional roads / bridges

- Stable route IDs; duplicate planning avoided
- Placement waits for loaded chunks; progress persisted in `millenaire_regional_roads`
- Statuses: `PLANNED` / `PARTIAL` / `WAITING_CHUNKS` / `BLOCKED` / `CONNECTED` / `FAILED` / `RECOVERY_REQUIRED`
- Coarse waypoints (~32) are expanded to continuous adjacent cells before placement; `CONNECTED` requires every cell and finished bridges
- Schema v1 coarse-only `CONNECTED` routes migrate to `RECOVERY_REQUIRED` and resume forward without blind rebuild
- Bridge placement returns an explicit incomplete result; the route cursor does not advance on a half-built crossing
- Water crossings: deterministic stone supports + plank deck when bridges enabled (no invented template files)
- A partial route is never described as connected

## Sand highways

- `HighwayEngine` + discovery (`HighwayDiscoveryService`), graph (`HighwayGraphPlanner`), materializer (4-wide sand, bridges, tunnels), travelers (`RoadJourneyScheduler`)
- Master switch: `worldgenSandHighways` (default false). When on, `RegionalRoadMaterializer` is skipped
- Tag: `#millenaire:highway_endpoint`
- Commands: `/millenaire highway status|audit`

## Known limitations

1. Native Structure datapack registration remains optional (`worldgenUseNativeStructures`)
2. Bridge strategy is procedural, not content-pack templates
3. Regional roads default off
4. Sand highways default off until owner validation
5. Runtime visual quality unverified until owner-run validation
