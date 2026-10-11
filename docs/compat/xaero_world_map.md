# Xaero World Map / Minimap integration

Optional client integration that projects Millénaire settlements onto **Xaero's Minimap** third-party waypoints (also shown on **Xaero's World Map**).

## Requirements

| Component | Version |
|-----------|---------|
| Minecraft / NeoForge | 1.21.1 / 21.1.x |
| Xaero's Minimap | NeoForge 1.21.1 (dev pin: see `xaero_minimap_version` in `gradle.properties`) |
| Xaero's World Map | NeoForge 1.21.1 (dev pin: `xaero_worldmap_version`) |

Millénaire runs without Xaero. Markers appear only when Minimap is installed on the client.

## Behaviour

- Server builds per-player DTOs from `VillageSavedData`, filtered by `PlayerCultureReputation.discoveredVillages` plus owned / sovereign settlements.
- Client caches DTOs and upserts waypoints under origin `millenaire:settlements`.
- Player sovereign city uses **gold** colour and `★` symbol.
- Development labels: hamlet / village / town / regional_hub (city).
- Approximate building AABB corners are optional (`showApproxBounds` + server `mapSyncIncludeApproxBounds`) and are **not** territorial claims.

## Config

**Client** (`millenaire-client.toml` → `xaero_map`): `enableXaeroSync`, `showXaeroSettlements`, category toggles, `showMyCity`, `showSettlementNames`, `showApproxBounds`.

**Server**: `mapSyncEnabled`, `mapSyncShowLoneBuildings`, `mapSyncIncludeApproxBounds`.

## Dev run

`localRuntime` pulls Minimap + World Map from [Chocolate Maven](https://chocolateminecraft.com/maven) into `runClient`.
