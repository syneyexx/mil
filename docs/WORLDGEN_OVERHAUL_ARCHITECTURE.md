# World Generation Overhaul — Architecture

**Planner version:** 1  
**Package:** `org.millenaire.worldgen`  
**Minecraft / NeoForge:** 1.21.1 / 21.1.x

## Chosen integration

Primary path: **deterministic region plans + per-chunk materialization on `ChunkEvent.Load(isNewChunk)`**.

Native Structure JSON registration is reserved (`worldgenUseNativeStructures`) but not required for the first complete engine. Millénaire settlements are multi-building, expandable footprints; chunk-clipped `PlacementStep` application via existing `BuildingPlacer.compilePlacementSteps` matches that shape without force-loading neighbors.

**Generation stage:** after terrain heightmaps exist for a newly generated overworld chunk, on the server thread (`server.execute`).

## Three stages

| Stage | Owner | Thread | Mutates blocks? | Mutates villages? |
|-------|--------|--------|-----------------|-------------------|
| A Planning | `SettlementPlanner`, `SettlementLayoutPlanner` | Server | No | No |
| B Materialization | `ChunkSettlementMaterializer` | Server | Yes (chunk-local) | No |
| C Activation | `SettlementActivator` | Server | No (post-place finalizers only) | Yes |

## Determinism

All natural decisions derive from `WorldgenSeeds.stream(worldSeed, salt, regionX, regionZ)` with separated salts (`SALT_POSITION`, `SALT_TYPE`, `SALT_LAYOUT`, …).

Settlement UUID = mix(seed, region, village-type path). Exploration order cannot change plan identity or center.

Neighbor conflicts use unsigned priority from candidate identity (`SALT_ARBITER`), not generation order.

## Persistence

`SettlementPlanSavedData` (`millenaire_worldgen_plans`) is independent from `VillageSavedData` (`millenaire_villages`). Existing villages are never rewritten by the Phase 4 schema.

Statuses: `CANDIDATE → PLANNED → RESERVED → MATERIALIZING → PHYSICAL_COMPLETE → ACTIVATING → ACTIVE` (+ `INVALID` / `FAILED`).

## Legacy bridge

When `worldgenEngineEnabled=true`, natural `VillageSpawnQueue.enqueue` / `trySpawnNext` are suppressed. Manual `SpawnVillageCommand` / `VillageSpawner.spawnVillage` remain live-world APIs.

Set `worldgenEngineEnabled=false` to restore the legacy post-load queue.

## Content snapshot

`WorldgenContentSnapshot.rebuildFromLoadedCultures()` runs after `CultureLoader.loadAll()`. Planners read the immutable snapshot; invalid village types are quarantined from natural eligibility.

## Roads / walls / hamlets

- Internal roads planned as centre→starter polylines; path materials from village type.
- Walls planned via existing `VillageWallGenerator` location math; materialized chunk-locally.
- Hamlets stored on parent plan and expanded to child plans with parent UUID link.
- Regional connectors: `RegionalRoadPlanner` foundation (off by default).

## Activation

Creates `Village` with deterministic `VillageId`, registers buildings with stable `BuildingId`s from the plan, spawns occupants once, initializes inventories once, runs faction lifecycle, optional reflective Phase 3 `emitSettlementFounded` if present.

## Existing-world safety

Only `isNewChunk` paths run the engine. Explored terrain is not reprocessed. Legacy villages load via existing `VillageSavedData` unchanged.
