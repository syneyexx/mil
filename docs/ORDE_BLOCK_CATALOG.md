# Orde Block Catalog — Complete Set (implemented)

**Creative tab:** **The Order** (`itemGroup.millenaire.the_order`)  
**Status:** all blocks from concept sheets + kit scenes catalogued; 16×16 textures; registered in Creative.  
**Generator:** `tools/gen_orde_assets.py` (re-run to regenerate textures/JSON).

---

## Visual references

| Sheet / kit | File |
|-------------|------|
| Structure A | `docs/images/orde-blocks/orde-block-structure.jpg` |
| Metal & spikes | `docs/images/orde-blocks/orde-block-metal-spikes.jpg` |
| Bound leather | `docs/images/orde-blocks/orde-block-bound-leather.jpg` |
| Arrival kit | `docs/images/orde-blocks/orde-kit-arrival.jpg` |
| Spike corridor | `docs/images/orde-blocks/orde-kit-spike-corridor.jpg` |
| Bleed bedroom | `docs/images/orde-blocks/orde-kit-bleed-bedroom.jpg` |
| City shrine | `docs/images/orde-blocks/orde-kit-shrine.jpg` |

![Structure](images/orde-blocks/orde-block-structure.jpg)
![Metal spikes](images/orde-blocks/orde-block-metal-spikes.jpg)
![Bound leather](images/orde-blocks/orde-block-bound-leather.jpg)
![Arrival](images/orde-blocks/orde-kit-arrival.jpg)
![Corridor](images/orde-blocks/orde-kit-spike-corridor.jpg)
![Bedroom](images/orde-blocks/orde-kit-bleed-bedroom.jpg)
![Shrine](images/orde-blocks/orde-kit-shrine.jpg)

---

## Complete inventory (The Order tab)

### Structure sheets

| ID | EN | Source in art |
|----|----|---------------|
| `orde_stone` | Orde Stone | Structure #1 |
| `orde_stone_stairs` / `_slab` | — | variants |
| `orde_panel` | Orde Panel | Structure #2 |
| `orde_panel_stairs` / `_slab` | — | variants |
| `orde_tile` | Orde Tile | Structure #3 |
| `orde_tile_slab` | — | variant |
| `bleed_planks` | Bleed Planks | Structure #4 |
| `bleed_planks_stairs` / `_slab` | — | variants |
| `orde_bricks` | Orde Bricks | Shrine masonry |
| `orde_pillar` | Orde Pillar | Shrine diamond-stitch columns |
| `orde_rune` | Orde Rune | Shrine border squares |
| `orde_sigil` | Orde Sigil | Shrine mural diamond |
| `orde_script` | Orde Script | Shrine scripture row |
| `riveted_stone` | Riveted Stone | Altar body rivets |
| `crimson_band` | Crimson Band | Altar mahogany band |
| `cold_vault` | Cold Vault | Labyrinth vault palette |
| `ruit_heart` | Ruit Heart | Audience core accent |

### Arrival / shrine architecture (from kits)

| ID | EN | Source |
|----|----|--------|
| `chevron_tile` | Chevron Tile | Shrine V-floor |
| `fluted_pillar` | Fluted Pillar | Arrival ribbed columns |
| `pillar_base` | Pillar Base | Inset-square capital/base |
| `orde_coffer` | Orde Coffer | Coffered ceiling panels |
| `portal_frame` | Portal Frame | Heavy portal masonry |
| `void_gate` | Void Gate | Blue portal energy |
| `ceiling_recess` | Ceiling Recess | Bedroom recessed light |

### Metal / spikes / hooks

| ID | EN | Notes |
|----|----|-------|
| `orde_bars` | Orde Bars | IronBars; cold rusted steel |
| `orde_grate` | Orde Grate | Floor grate openings |
| `spike_floor` | Spike Floor | **1♥ contact damage** on step |
| `spike_wall` | Spike Wall | Side spikes visual |
| `hook_block` | Hook Block | Wall mount, horizontal facing |
| `hanging_hook` | Hanging Hook | Ceiling chain + hook (corridor) |
| `orde_censer` | Orde Censer | Hanging censer, light 6 |

### Bound / leather

| ID | EN |
|----|----|
| `bound_leather` | Bound Leather |
| `nail_studded` | Nail-Studded Leather |
| `strap_block` | Strap Block |
| `silk_veil` | Silk Veil (pane) |

### Bleed bedroom domestic

| ID | EN | Notes |
|----|----|-------|
| `bleed_wallpaper` | Bleed Wallpaper | Beige floral (kit) |
| `orde_barricade` | Orde Barricade | Boarded window + blue bleed |
| `orde_bedding` | Orde Bedding | Noise quilt |
| `orde_nightstand` | Orde Nightstand | Drawer furniture |
| `orde_clock` | Orde Clock | Wall clock, facing |
| `crooked_painting` | Crooked Painting | Skewed wall art, facing |
| `orde_lamp` | Orde Lamp | Table lamp, light 10 |

### Ritual / light

| ID | EN | Notes |
|----|----|-------|
| `orde_altar` | Orde Altar | Riveted + crimson side |
| `offering_bowl` | Offering Bowl | Basin top |
| `orde_brazier` | Orde Brazier | Light 10 |
| `orde_lantern` | Orde Lantern | Light 12, cutout |
| `void_sconce` | Void Sconce | Light 4 |

### Also on The Order tab (existing)

| ID | EN |
|----|----|
| `scharnier` | Closed Hinge |
| `configuratie` | Configuratie item |
| `labyrinth_zegel` / `masker` / `koord` | Relics |

---

## Code hooks

- Blocks: `ModBlocks` (Orde section)
- Items: `ModItems` block-items
- Tab: `ModCreativeTabs.THE_ORDER_TAB`
- Labyrinth stamps: `LabyrinthMaterials` → `orde_stone` / `orde_panel` / `bleed_planks` / `cold_vault` / `ruit_heart`
- Specials: `SpikeFloorBlock`, `HookBlock` (also clock / painting facing)

---

## Regenerating textures

```bash
python3 tools/gen_orde_assets.py
```
