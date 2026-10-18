# De Orde — Labyrinth culture & villages

**Culture id:** `orde` (display: De Orde). Labyrinth-only villages themed as cenobite-style settlements. IP-clean lore names.

## Villagers (Blockbench → native models)

| ID | Role | Native model |
|----|------|----------------|
| `drempelhouder` | Chief — holds **Configuratie** | `orde_villager/drempelhouder` |
| `ruilmeester` | Merchant / seller | `orde_villager/ruilmeester` |
| `slotenmaker` | Craftsman / raider | `orde_villager/slotenmaker` |
| `aswaard` | Innkeeper | `orde_villager/aswaard` |

Source pack: `tools/orde_villager_blockbench_v1/`. Converted via `tools/convert_bbmodel_to_native.py` pattern into `models/entity/orde_villager/*.model.json`.

Rendering: `VillagerType.native_model` → `NativeJsonEntityModel` in `MillVillagerRenderer` (keeps Millénaire AI / trade / chief UI).

## Village type

- **Klooster** (`millenaire:orde/cloister`)
- `dimensions: ["millenaire:labyrinth"]` — **never** Overworld
- `biome_tags: ["millenaire:labyrinth"]`
- `carries_raid: true` — can war with other cultures
- Compact Orde buildings (hall / cell / forge / inn) for wellgrid footprints

## Systems wired

| Feature | How |
|---------|-----|
| Natural spawn | Separate Labyrinth `VillageSpawnQueue` in `Millenaire.java` |
| Command spawn | `/millenaire spawn …` allowed if village type allows the dimension |
| Diplomacy / war | `CultureStandingSavedData` stored on Overworld (shared matrix) |
| Trade | `traded_goods.json` + townhall shop; Ruilmeester has `seller` + foreign stock |
| Configuratie | Drempelhouder `initial_inventory` + travel-book held item |

## Player notes

1. Enter Labyrinth, explore / wait for chunk gen → Orde kloosters can appear on labyrinth biome chunks.
2. Or force: stand in Labyrinth → `/millenaire spawn cloister` (or full id `millenaire:orde/cloister`).
3. Overworld cultures can declare war on Orde via shared standing; physical cross-dim raids are not teleported automatically.
4. Gebondenen (26 maze entities) remain separate from this culture — villages are Millénaire `MillVillager`s.
