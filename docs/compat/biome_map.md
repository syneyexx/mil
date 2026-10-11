# Culture ↔ mod biome families

High-level mapping used by the datapack tags. Exact biome IDs live in `src/main/resources/data/*/tags/worldgen/biome/`.

| Culture | Primary Common / Millenaire tags | Typical mod biomes |
|---------|----------------------------------|--------------------|
| Norman | `#c:is_plains`, `#millenaire:is_temperate_forest`, `#c:is_birch_forest` | BOP grassland/orchard/woodland, BWG prairie/temperate_grove, NS maple/aspen |
| Byzantine | plains/savanna/hill + `#millenaire:is_mediterranean` / `#millenaire:is_lavender` | BOP mediterranean/lavender, NS cypress/lavender/chaparral |
| Seljuk | desert/savanna/badlands + mediterranean/volcanic | BOP lush_desert/dryland/volcano, BWG mojave/badlands, NS dunes/drylands |
| Indian | jungle/savanna/desert/swamp | BOP rainforest/tropics, BWG tropical_rainforest, NS tropical_woods |
| Mayan | jungle/swamp/dark_forest | BOP fungal_jungle/bayou, BWG jacaranda/fragment jungle, NS bamboo wetlands |
| Japanese | taiga/hill/meadow/cherry + hot springs | BOP snowblossom/jacaranda, BWG sakura_grove, NS sugi/wisteria, BOP hot_springs |
| Inuits | snowy / snowy_plains | BOP auroral_garden/tundra, BWG frosted_taiga/eroded_borealis, NS snowy fir/tundra |

Regenerate tag JSON with:

```bash
python tools/generate_compat_biome_tags.py
```

If a biome is still missing from tags, `ClimateBiomeFallback` still assigns a culture from temperature / name heuristics when `worldgenUntaggedBiomeFallback` is enabled. Use `/millenaire dev biomecoverage` to see tag vs fallback vs uncovered.
