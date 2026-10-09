# World Generation Overhaul — Implementation Ledger

**Baseline HEAD (main):** `8e57289167c6ff39b4915ae911a99f2b384d043c`  
**Branch:** `cursor/worldgen-overhaul-7128`  
**Phase 3 present on main:** No (Living Villages remains on separate PR). Activator uses optional reflective hook only.  
**Status legend:** `PENDING` · `IN_PROGRESS` · `IMPLEMENTED_UNVERIFIED` · `BLOCKED`

Build / CI / Minecraft runtime / worldgen benchmarks were **not** executed by this agent.

---

## Phase checklist

| Phase | Status |
|-------|--------|
| 4.0 Baseline audit | IMPLEMENTED_UNVERIFIED |
| 4.1 Content snapshot | IMPLEMENTED_UNVERIFIED |
| 4.2 Spatial planner | IMPLEMENTED_UNVERIFIED |
| 4.3 Terrain + layout | IMPLEMENTED_UNVERIFIED |
| 4.4 Roads / walls / hamlets / LB | IMPLEMENTED_UNVERIFIED |
| 4.5 Chunk materialization | IMPLEMENTED_UNVERIFIED |
| 4.6 Activation | IMPLEMENTED_UNVERIFIED |
| 4.7 Legacy / manual compatibility | IMPLEMENTED_UNVERIFIED |
| 4.8 Performance | IMPLEMENTED_UNVERIFIED |
| 4.9 Static review + docs | IMPLEMENTED_UNVERIFIED |

---

## Task ledger

| Task ID | Phase | Description | Status | Files |
|---------|-------|-------------|--------|-------|
| WG-01 | 4.0 | Baseline audit + ledger | IMPLEMENTED_UNVERIFIED | `docs/WORLDGEN_OVERHAUL_IMPLEMENTATION.md` |
| WG-02 | 4.1 | Immutable content snapshot + validation | IMPLEMENTED_UNVERIFIED | `worldgen/content/*` |
| WG-03 | 4.2 | Region planner + seeds + SavedData | IMPLEMENTED_UNVERIFIED | `worldgen/plan/*` |
| WG-04 | 4.3 | Terrain sampler + layout planner | IMPLEMENTED_UNVERIFIED | `worldgen/terrain/*`, `SettlementLayoutPlanner` |
| WG-05 | 4.4 | Roads, walls, hamlets, regional foundation | IMPLEMENTED_UNVERIFIED | `PlannedRoad/Wall/Hamlet`, `RegionalRoadPlanner` |
| WG-06 | 4.5 | Chunk materializer + Millenaire hooks | IMPLEMENTED_UNVERIFIED | `ChunkSettlementMaterializer`, `WorldgenEngine`, `Millenaire` |
| WG-07 | 4.6 | Settlement activator | IMPLEMENTED_UNVERIFIED | `SettlementActivator` |
| WG-08 | 4.7 | Legacy queue gating + manual spawn preserved | IMPLEMENTED_UNVERIFIED | `LegacySpawnBridge`, `Millenaire` chunk/tick |
| WG-09 | 4.8 | Budgets, no force-load, bounded caches | IMPLEMENTED_UNVERIFIED | config budgets, engine tick budget |
| WG-10 | 4.9 | Docs + diag command + lang | IMPLEMENTED_UNVERIFIED | `docs/WORLDGEN_*`, `WorldgenDiagCommand`, `en_us.json` |
| WG-11 | 4.7 | SiteValidator + KEY_ONLY LB + lone registry | IMPLEMENTED_UNVERIFIED | `SettlementPlanner`, `SettlementActivator` |

---

## Chosen architecture

**Three stages** in package `org.millenaire.worldgen`:

1. **Planning** — `SettlementPlanner` / `SettlementLayoutPlanner` (deterministic, no village registration)
2. **Materialization** — `ChunkSettlementMaterializer` places `PlacementStep`s clipped to newly generated chunks via existing `BuildingPlacer.compilePlacementSteps`
3. **Activation** — `SettlementActivator` registers `Village` / buildings / residents once `PHYSICAL_COMPLETE`

When `worldgenEngineEnabled=true`, legacy natural `VillageSpawnQueue.enqueue` / `trySpawnNext` are suppressed. Manual `SpawnVillageCommand` / `VillageSpawner.spawnVillage` remain for live-world placement.

---

## Known limitations (source-level)

1. Native Structure datapack registration is prepared via config flag but not required; primary path is chunk-load materialization timed before first client observation of new chunks.
2. Bridge templates are not yet a separate content pack — water crossings prefer rejection/reroute; `worldgenGenerateBridges` reserves the policy switch.
3. `BuildingLocationFinder` is not used for natural layouts (ThreadLocalRandom); deterministic ring placement is used instead. Manual spawn still uses VillageSpawner/dryRun.
4. NameLists culture API still uses ThreadLocalRandom for gameplay naming elsewhere; worldgen naming indexes the list with a seeded RNG.
5. Phase 3 Living Villages event bus is optional/reflective only on this branch.
6. Regional roads default off; foundation planner exists without full inter-village terraforming.
7. Runtime visual quality and determinism across seeds are **unverified** until local gameplay tests in `WORLDGEN_OVERHAUL_LOCAL_VALIDATION.md`.
