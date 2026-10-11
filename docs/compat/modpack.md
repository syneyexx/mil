# Millenaire modpack compatibility

Soft integrations for biome packs and content mods. All foreign item/block IDs and biome tag entries use `required: false` / namespace soft-load so Millenaire still runs without the full pack.

## Biomes (Phase 1 + universal fallback)

Village spawn prefers datapack tags, then uses **climate fallback** so every overworld *surface* biome can host villages:

1. Tag match (`#c:is_*` / `#millenaire:is_*` / bare biome IDs in village JSON)
2. If none match and `worldgenUntaggedBiomeFallback` is true (default): classify by tags + temperature/name → culture pool (Norman / Japanese / Seljuk / Indian / Mayan / Inuits / Byzantine)
3. Oceans, rivers, beaches, caves, nether, and end stay excluded

Datapack tags under `data/c/tags/worldgen/biome/` and `data/millenaire/tags/worldgen/biome/` list optional BOP / OTBWG / Nature's Spirit biomes (`required: false`). `#millenaire:is_settlement_surface` is a broad surface allowlist for diagnostics.

| Mod | Namespace |
|-----|-----------|
| Biomes O' Plenty | `biomesoplenty` |
| Oh The Biomes We've Gone | `biomeswevegone` |
| Nature's Spirit | `natures_spirit` |

Culture extras (tags):

- Byzantine / Seljuk → `#millenaire:is_mediterranean`, `#millenaire:is_lavender` / `#millenaire:is_volcanic`
- Japanese → `#millenaire:is_cherry_grove`, hot springs inn → `#millenaire:is_hot_springs`

Naturalist does not add biomes; it is used for food/trade only.

Debug: `/millenaire dev biomecoverage` reports tag-matched / fallback-matched / excluded / uncovered.

Structure avoidance: `#millenaire:avoid_settlement` (Waystones, Dungeons Arise, Dungeons and Taverns / Nova Structures). Gated by `worldgenPreserveModdedStructures`.

## Food & farming (Phase 3)

- `villager_config/default.json` + `villager_config/modpack_foods.json` — Farmer's Delight / Naturalist growth & conception values
- Gathering types: `plant_fd_*`, `harvest_fd_cabbage`, `cook_fd_rice`
- Norman farmers reference the FD goals

## Architecture (Phase 2)

- `PathMaterials` resolves Macaw path keys (`mcw_gravel_path`, …) and raw `namespace:block` ids
- Gathering/trade for Macaw roofs/windows and Supplementaries lanterns
- Some villages prefer Macaw path materials when the mod is loaded

## Equipment (Phase 4)

`CompatEquipmentRegistry` soft-loads Epic Knights (`magistuarmory`), Immersive Armors, Fantasy Armor, Armor of the Ages, Exotic Armory into soldier/guard kits (~65% chance on spawn). Spell Engine / TaCZ appear as rare shop trophies only.

## Map — Xaero (optional)

- Soft client integration with **Xaero's Minimap** / **World Map** (NeoForge 1.21.1)
- Discovery-filtered settlement waypoints under origin `millenaire:settlements`
- See [xaero_world_map.md](xaero_world_map.md)

## Travel / Create (Phase 5)

- `placeVillageWaystones` (default true) places a Waystones waystone in:
  - every Millenaire village (on activation + periodic backfill for existing worlds)
  - every vanilla Minecraft village (when its chunks load)
- Uses Waystones 1.21.1 `WaystoneStyle` API (with double-block LOWER+UPPER fallback); repairs incomplete half-waystones on backfill
- Names Millenaire waystones after the settlement’s English city name from culture namelists (e.g. Amsterdam, The Hague); blank existing stones are renamed on backfill; vanilla villages use `Village`
- Skips sites that already have a **complete** waystone nearby; persists placed sites in saved data
- Create cogwheels / andesite alloy in Norman shops + `craft_create_cogwheel` gathering type
- Traveler's Backpack / Artifacts as rare town-hall goods

## Sand highways (Phase 6)

- Config `worldgenSandHighways` (default **false**) enables discovery-driven 4-wide sand paths between settlements as chunks load
- Endpoints: Millenaire (non-lone by default), vanilla villages, and `#millenaire:highway_endpoint` (settlement-like Dungeons Arise / Nova / D&T structures; soft `required: false`)
- Separate from `#millenaire:avoid_settlement` (spawn conflict only)
- Bridges / tunnels / road travelers (traders, warriors, bandits, optional horses) when respective highway config flags are on
- Diag: `/millenaire highway status` and `/millenaire highway audit`

## Config knobs

| Key | Default | Notes |
|-----|---------|-------|
| `worldgenSettlementDensity` | `0.42` | Slightly higher for dense biome packs |
| `worldgenUntaggedBiomeFallback` | `true` | Climate culture pool when biome_tags miss |
| `worldgenPreserveModdedStructures` | `true` | Avoid tagged mod structures |
| `placeVillageWaystones` | `true` | Requires Waystones |
| `worldgenSandHighways` | `false` | Enable sand highway network + travelers |

See also [biome_map.md](biome_map.md).
