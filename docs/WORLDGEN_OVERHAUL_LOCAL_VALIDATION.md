# World Generation Overhaul — Local Validation Guide

**Builds, GameTests, and live Minecraft were not executed by the implementing agent.**  
Fill the Result column when you verify locally.

| Scenario | Result (unchecked) |
|----------|--------------------|
| A New SP world | ☐ |
| B Same seed, reverse explore | ☐ |
| C Chunk border building | ☐ |
| D Hilly village | ☐ |
| E Water border | ☐ |
| F Vanilla structure conflict | ☐ |
| G Multiple cultures | ☐ |
| H Existing world | ☐ |
| I Normal village growth | ☐ |
| J Restart mid-generation | ☐ |
| K Multiplayer concurrency | ☐ |
| L Heavy exploration | ☐ |
| M Custom culture | ☐ |

## Prerequisites

1. Build the mod with your usual Gradle pipeline.
2. Server config: `worldgenEngineEnabled=true`, `generateVillages=true`.
3. Optional: `worldgenDetailedDiagnostics=true`, `logSpawnAttempts=true`.
4. Commands (op): `/millenaire worldgen near`, `/millenaire worldgen content`, `/millenaire worldgen version`.

## Scenario A — New singleplayer world

- **Setup:** New world, known seed, creative flight.
- **Steps:** Explore until `/millenaire worldgen near` reports a non-INVALID plan; fly to center.
- **Expected:** Physical buildings/roads already present; villagers after activation; no whole-town delayed construction of starters.
- **Failure symptoms:** Empty footprint with ACTIVE status; duplicate villages; legacy queue spam in logs.

## Scenario B — Same seed, different order

- **Setup:** Two worlds, identical seed + config.
- **Steps:** World1 explore north then south; World2 south then north.
- **Expected:** Same plan IDs, centers, village types for the same regions (`/millenaire worldgen near` at matching coords).
- **Failure symptoms:** Different UUIDs or centers for the same region key.

## Scenario C — Chunk border

- **Setup:** Find a planned building whose footprint spans chunks (wide town hall).
- **Expected:** Full structure, one set of chests/doors, no duplicated block entities.
- **Diagnostics:** Plan building `materialized=true` after all footprint chunks generated.

## Scenario D — Hilly village

- **Setup:** Seed with rolling hills; culture that allows hills (`worldgenAllowHillyTerrain=true`).
- **Expected:** Stable foundations, accessible entrances, no giant flattened plateau beyond `worldgenMaximumTerrainModification`.

## Scenario E — Water border

- **Expected:** Wet sites rejected (`WATER_CONFLICT`) or placed on buildable land; roads not underwater without bridge support.

## Scenario F — Vanilla structure conflict

- **Expected:** Candidate `INVALID` with `VANILLA_STRUCTURE_CONFLICT`; vanilla structure untouched.

## Scenario G — Multiple cultures

- **Expected:** Biome-appropriate types from content snapshot; correct buildings/names/path materials.

## Scenario H — Existing world

- **Setup:** Pre-Phase-4 Millénaire save.
- **Expected:** Existing villages/inventories/NPCs unchanged; no new villages in old chunks; new terrain may plan when eligible.

## Scenario I — Growth

- **Expected:** After ACTIVE, `VillageGrowthManager` still queues construction; paths/walls can expand.

## Scenario J — Restart during generation

- **Steps:** Force-quit while a plan is `MATERIALIZING` mid-wall or mid-road; reload.
- **Expected:** Per-unit cursors resume; walls/roads do not restart from index 0; no blind re-flatten/deletion replay on ambiguous legacy progress; no duplicate village/NPC/inventory.

## Scenario J2 — Regional continuous route

- **Setup:** Two nearby ACTIVE/PHYSICAL settlements; regional roads + bridges enabled; load corridor chunks gradually.
- **Expected:** Continuous adjacent path cells (not only ~32-block samples); bridge budget abort keeps `PARTIAL` (not `CONNECTED`); schema-v1 coarse `CONNECTED` reopens as `RECOVERY_REQUIRED`.

## Scenario J3 — Sand highways + travelers

- **Setup:** `worldgenSandHighways=true`; explore until two Millenaire towns and one vanilla village are discovered; load corridors gradually.
- **Expected:** `/millenaire highway status` shows nodes/routes; 4-wide sand/path_sandstone corridors; water → bridge band; steep mountain → tunnel; travelers appear near player on `CONNECTED` routes and despawn when far; mid-bridge/tunnel reload resumes; mod structures in `#millenaire:highway_endpoint` become nodes, pure dungeons do not.

## Scenario K — Multiplayer

- **Expected:** One plan per region; one activation; no double NPCs.

## Scenario L — Heavy exploration

- **Expected:** Bounded activation budget; no force-load of whole radii; no unbounded queue growth.

## Scenario M — Custom culture

- **Expected:** Appears in `/millenaire worldgen content`; invalid data listed as INVALID lines, not world corruption.

## Legacy fallback check

Default: `worldgenEngineEnabled=true`, `worldgenLegacyFallback=false` (Phase 4 only).

- Set `worldgenLegacyFallback=true` to also run `VillageSpawnQueue` alongside Phase 4.
- Set `worldgenEngineEnabled=false` to restore legacy-only natural spawns.
- Manual `/millenaire spawn` must work with either setting.

Confirm logs show no premature `no_candidates` rejects before `Worldgen engine ready`, and status lines eventually include `rejectReasons={...}` when plans are rejected.
