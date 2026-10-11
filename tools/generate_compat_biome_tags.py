"""Generate Millenaire modpack biome/structure tag datapacks (optional entries)."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "data"


def opt(biome_id: str) -> dict:
    return {"id": biome_id, "required": False}


def write_tag(ns: str, name: str, ids: list[str]) -> None:
    path = ROOT / ns / "tags" / "worldgen" / "biome" / f"{name}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    values = []
    for i in ids:
        if i.startswith("minecraft:"):
            values.append(i)
        else:
            values.append(opt(i))
    path.write_text(json.dumps({"replace": False, "values": values}, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {path}")


def main() -> None:
    bop = "biomesoplenty"
    bwg = "biomeswevegone"
    ns = "natures_spirit"

    tags = {
        "is_plains": [
            f"{bop}:field", f"{bop}:grassland", f"{bop}:pasture", f"{bop}:prairie", f"{bop}:orchard",
            f"{bop}:pumpkin_patch", f"{bop}:origin_valley", f"{bop}:lavender_field", f"{bop}:overgrown_greens",
            f"{bop}:forested_field", f"{bop}:fir_clearing",
            f"{bwg}:allium_shrubland", f"{bwg}:amaranth_grassland", f"{bwg}:coconino_meadow",
            f"{bwg}:firecracker_chaparral", f"{bwg}:orchard", f"{bwg}:prairie", f"{bwg}:pumpkin_valley",
            f"{bwg}:rose_fields", f"{bwg}:temperate_grove", f"{bwg}:crimson_tundra",
            f"{ns}:prairie", f"{ns}:marigold_meadows", f"{ns}:carnation_fields", f"{ns}:heather_fields",
            f"{ns}:flowering_shrubland", f"{ns}:shrubland", f"{ns}:lavender_fields", f"{ns}:cypress_fields",
            f"{ns}:xeric_plains", f"{ns}:chaparral", f"{ns}:floral_ridges", f"{ns}:alpine_clearings",
        ],
        "is_forest": [
            f"{bop}:aspen_glade", f"{bop}:woodland", f"{bop}:old_growth_woodland", f"{bop}:seasonal_forest",
            f"{bop}:maple_woods", f"{bop}:redwood_forest", f"{bop}:mediterranean_forest", f"{bop}:jacaranda_glade",
            f"{bop}:mystic_grove", f"{bop}:dead_forest", f"{bop}:old_growth_dead_forest", f"{bop}:ominous_woods",
            f"{bwg}:black_forest", f"{bwg}:ebony_woods", f"{bwg}:enchanted_tangle", f"{bwg}:forgotten_forest",
            f"{bwg}:orchard", f"{bwg}:overgrowth_woodlands", f"{bwg}:redwood_thicket", f"{bwg}:sakura_grove",
            f"{bwg}:skyris_vale", f"{bwg}:weeping_witch_forest", f"{bwg}:zelkova_forest", f"{bwg}:cika_woods",
            f"{ns}:aspen_forest", f"{ns}:maple_woodlands", f"{ns}:golden_wilds", f"{ns}:wisteria_forest",
            f"{ns}:redwood_forest", f"{ns}:sugi_forest", f"{ns}:blooming_sugi_forest", f"{ns}:windswept_sugi_forest",
            f"{ns}:cedar_thicket", f"{ns}:woody_highlands",
        ],
        "is_birch_forest": [
            f"{bop}:aspen_glade", f"{bwg}:aspen_boreal", f"{ns}:aspen_forest",
        ],
        "is_dark_forest": [
            f"{bop}:ominous_woods", f"{bop}:mystic_grove", f"{bwg}:black_forest", f"{bwg}:forgotten_forest",
            f"{bwg}:weeping_witch_forest", f"{bwg}:enchanted_tangle",
        ],
        "is_taiga": [
            f"{bop}:coniferous_forest", f"{bop}:snowy_coniferous_forest", f"{bop}:tundra", f"{bop}:auroral_garden",
            f"{bop}:snowy_fir_clearing", f"{bop}:snowy_maple_woods", f"{bop}:muskeg",
            f"{bwg}:aspen_boreal", f"{bwg}:canadian_shield", f"{bwg}:coniferous_forest", f"{bwg}:dacite_ridges",
            f"{bwg}:frosted_coniferous_forest", f"{bwg}:frosted_taiga", f"{bwg}:maple_taiga", f"{bwg}:zelkova_forest",
            f"{ns}:fir_forest", f"{ns}:snowy_fir_forest", f"{ns}:boreal_taiga", f"{ns}:coniferous_covert",
            f"{ns}:snowy_redwood_forest", f"{ns}:tundra",
        ],
        "is_jungle": [
            f"{bop}:rainforest", f"{bop}:rocky_rainforest", f"{bop}:tropics", f"{bop}:fungal_jungle", f"{bop}:floodplain",
            f"{bwg}:crag_gardens", f"{bwg}:fragment_jungle", f"{bwg}:jacaranda_jungle", f"{bwg}:tropical_rainforest",
            f"{ns}:tropical_woods", f"{ns}:sparse_tropical_woods", f"{ns}:tropical_basin", f"{ns}:bamboo_wetlands",
        ],
        "is_swamp": [
            f"{bop}:bayou", f"{bop}:bog", f"{bop}:marsh", f"{bop}:wetland", f"{bop}:floodplain", f"{bop}:moor",
            f"{bwg}:bayou", f"{bwg}:cypress_swamplands", f"{bwg}:cypress_wetlands", f"{bwg}:pale_bog",
            f"{bwg}:white_mangrove_marshes",
            f"{ns}:marsh", f"{ns}:bamboo_wetlands",
        ],
        "is_savanna": [
            f"{bop}:lush_savanna", f"{bop}:scrubland", f"{bop}:dryland", f"{bop}:rocky_shrubland", f"{bop}:shrubland",
            f"{bwg}:araucaria_savanna", f"{bwg}:baobab_savanna", f"{bwg}:ironwood_gour",
            f"{ns}:oak_savanna", f"{ns}:arid_savanna", f"{ns}:drylands", f"{ns}:wooded_drylands",
        ],
        "is_desert": [
            f"{bop}:lush_desert", f"{bop}:cold_desert", f"{bop}:dune_beach", f"{bop}:wasteland", f"{bop}:wasteland_steppe",
            f"{bwg}:atacama_outback", f"{bwg}:mojave_desert", f"{bwg}:windswept_desert", f"{bwg}:rugged_badlands",
            f"{ns}:stratified_desert", f"{ns}:blooming_dunes", f"{ns}:lively_dunes", f"{ns}:scorched_dunes",
            f"{ns}:xeric_plains",
        ],
        "is_badlands": [
            f"{bop}:volcanic_plains", f"{bop}:crag", f"{bop}:jade_cliffs",
            f"{bwg}:red_rock_valley", f"{bwg}:red_rock_peaks", f"{bwg}:rugged_badlands", f"{bwg}:sierra_badlands",
            f"{ns}:red_peaks", f"{ns}:dusty_slopes", f"{ns}:arid_highlands", f"{ns}:shrubby_highlands",
        ],
        "is_snowy": [
            f"{bop}:auroral_garden", f"{bop}:snowy_coniferous_forest", f"{bop}:snowy_fir_clearing",
            f"{bop}:snowy_maple_woods", f"{bop}:snowblossom_grove", f"{bop}:tundra", f"{bop}:muskeg",
            f"{bop}:wintry_origin_valley", f"{bop}:cold_desert",
            f"{bwg}:eroded_borealis", f"{bwg}:frosted_coniferous_forest", f"{bwg}:frosted_taiga",
            f"{bwg}:howling_peaks", f"{bwg}:shattered_glacier", f"{bwg}:crimson_tundra",
            f"{ns}:snowy_fir_forest", f"{ns}:snowy_redwood_forest", f"{ns}:tundra",
            f"{ns}:snowcapped_red_peaks", f"{ns}:sleeted_slopes", f"{ns}:alpine_highlands",
        ],
        "is_snowy_plains": [
            f"{bop}:tundra", f"{bop}:cold_desert", f"{bop}:wintry_origin_valley", f"{bwg}:crimson_tundra",
            f"{ns}:tundra", f"{ns}:alpine_clearings",
        ],
        "is_hill": [
            f"{bop}:highland", f"{bop}:crag", f"{bop}:jade_cliffs", f"{bop}:rocky_shrubland",
            f"{bwg}:canadian_shield", f"{bwg}:dacite_ridges", f"{bwg}:howling_peaks",
            f"{ns}:alpine_highlands", f"{ns}:woody_highlands", f"{ns}:arid_highlands",
            f"{ns}:shrubby_highlands", f"{ns}:blooming_highlands", f"{ns}:white_cliffs",
        ],
        "is_mountain": [
            f"{bop}:crag", f"{bop}:highland", f"{bop}:jade_cliffs", f"{bop}:volcano", f"{bop}:volcanic_plains",
            f"{bwg}:crag_gardens", f"{bwg}:howling_peaks", f"{bwg}:red_rock_peaks", f"{bwg}:shattered_glacier",
            f"{ns}:red_peaks", f"{ns}:snowcapped_red_peaks", f"{ns}:alpine_highlands", f"{ns}:dusty_slopes",
            f"{ns}:sleeted_slopes",
        ],
        "is_cold": [
            f"{bop}:auroral_garden", f"{bop}:cold_desert", f"{bop}:snowy_coniferous_forest", f"{bop}:tundra",
            f"{bop}:muskeg", f"{bop}:bog", f"{bop}:moor",
            f"{bwg}:frosted_taiga", f"{bwg}:eroded_borealis", f"{bwg}:howling_peaks", f"{bwg}:shattered_glacier",
            f"{ns}:tundra", f"{ns}:boreal_taiga", f"{ns}:snowy_fir_forest", f"{ns}:snowy_redwood_forest",
            f"{ns}:alpine_highlands", f"{ns}:alpine_clearings",
        ],
    }

    for name, ids in tags.items():
        write_tag("c", name, ids)

    write_tag("millenaire", "is_temperate_forest", [
        "minecraft:forest", "minecraft:flower_forest",
        f"{bop}:woodland", f"{bop}:old_growth_woodland", f"{bop}:seasonal_forest", f"{bop}:maple_woods",
        f"{bop}:aspen_glade", f"{bop}:orchard", f"{bop}:forested_field",
        f"{bwg}:orchard", f"{bwg}:temperate_grove", f"{bwg}:overgrowth_woodlands", f"{bwg}:zelkova_forest",
        f"{bwg}:ebony_woods", f"{bwg}:cika_woods",
        f"{ns}:aspen_forest", f"{ns}:maple_woodlands", f"{ns}:golden_wilds", f"{ns}:wisteria_forest",
    ])
    write_tag("millenaire", "is_meadow", [
        "minecraft:meadow",
        f"{bop}:field", f"{bop}:grassland", f"{bop}:pasture", f"{bop}:lavender_field", f"{bop}:prairie",
        f"{bwg}:allium_shrubland", f"{bwg}:amaranth_grassland", f"{bwg}:coconino_meadow", f"{bwg}:rose_fields",
        f"{bwg}:prairie",
        f"{ns}:marigold_meadows", f"{ns}:carnation_fields", f"{ns}:heather_fields", f"{ns}:lavender_fields",
        f"{ns}:cypress_fields", f"{ns}:prairie",
    ])
    write_tag("millenaire", "is_cherry_grove", [
        "minecraft:cherry_grove",
        f"{bop}:snowblossom_grove", f"{bop}:jacaranda_glade",
        f"{bwg}:sakura_grove",
        f"{ns}:wisteria_forest", f"{ns}:blooming_sugi_forest", f"{ns}:sugi_forest",
    ])
    write_tag("millenaire", "is_mediterranean", [
        f"{bop}:mediterranean_forest", f"{bop}:lavender_field", f"{bop}:orchard", f"{bop}:lush_savanna",
        f"{ns}:cypress_fields", f"{ns}:lavender_fields", f"{ns}:carnation_fields", f"{ns}:chaparral",
        f"{bwg}:firecracker_chaparral",
    ])
    write_tag("millenaire", "is_volcanic", [
        f"{bop}:volcano", f"{bop}:volcanic_plains", f"{bop}:wasteland", f"{bop}:wasteland_steppe", f"{bop}:crag",
        f"{bwg}:basalt_barrera", f"{ns}:scorched_dunes",
    ])
    write_tag("millenaire", "is_lavender", [
        f"{bop}:lavender_field", f"{ns}:lavender_fields", f"{ns}:heather_fields", f"{bwg}:allium_shrubland",
        f"{bwg}:rose_fields",
    ])
    write_tag("millenaire", "is_hot_springs", [
        f"{bop}:hot_springs", f"{ns}:tropical_basin",
    ])

    # Broad surface allowlist used by diagnostics / optional datapack consumers.
    # Java ClimateBiomeFallback also classifies by climate when this tag misses.
    surface = []
    for name in (
        "is_plains", "is_forest", "is_birch_forest", "is_dark_forest", "is_taiga",
        "is_jungle", "is_swamp", "is_savanna", "is_desert", "is_badlands",
        "is_snowy", "is_snowy_plains", "is_hill", "is_mountain", "is_cold",
    ):
        # Re-read values we just wrote for c tags
        path = ROOT / "c" / "tags" / "worldgen" / "biome" / f"{name}.json"
        if path.exists():
            data = json.loads(path.read_text(encoding="utf-8"))
            for v in data.get("values", []):
                if isinstance(v, dict):
                    surface.append(v["id"])
                elif isinstance(v, str) and not v.startswith("minecraft:"):
                    surface.append(v)
    for mid in (
        "minecraft:plains", "minecraft:sunflower_plains", "minecraft:forest", "minecraft:flower_forest",
        "minecraft:birch_forest", "minecraft:old_growth_birch_forest", "minecraft:dark_forest",
        "minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
        "minecraft:snowy_taiga", "minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle",
        "minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:savanna", "minecraft:savanna_plateau",
        "minecraft:windswept_savanna", "minecraft:desert", "minecraft:badlands", "minecraft:wooded_badlands",
        "minecraft:eroded_badlands", "minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:meadow",
        "minecraft:cherry_grove", "minecraft:grove", "minecraft:windswept_hills", "minecraft:windswept_forest",
        "minecraft:windswept_gravelly_hills", "minecraft:stony_peaks", "minecraft:jagged_peaks",
        "minecraft:frozen_peaks", "minecraft:snowy_slopes",
    ):
        surface.append(mid)
    # de-dupe preserve order
    seen = set()
    surface_unique = []
    for s in surface:
        if s not in seen:
            seen.add(s)
            surface_unique.append(s)
    write_tag("millenaire", "is_settlement_surface", surface_unique)

    struct_tag = ROOT / "millenaire" / "tags" / "worldgen" / "structure" / "avoid_settlement.json"
    struct_tag.parent.mkdir(parents=True, exist_ok=True)
    struct_ids = [
        "waystones:waystone", "waystones:mossy_waystone", "waystones:sandy_waystone",
        "dungeons_arise:abandoned_temple", "dungeons_arise:aviary", "dungeons_arise:bandit_towers",
        "dungeons_arise:bandit_village", "dungeons_arise:bathhouse", "dungeons_arise:coliseum",
        "dungeons_arise:fishing_hut", "dungeons_arise:foundry", "dungeons_arise:illager_campsite",
        "dungeons_arise:illager_fort", "dungeons_arise:illager_windmill", "dungeons_arise:jungle_tree_house",
        "dungeons_arise:keep_kayra", "dungeons_arise:lighthouse", "dungeons_arise:merchant_campsite",
        "dungeons_arise:monastery", "dungeons_arise:mushroom_village", "dungeons_arise:plague_asylum",
        "dungeons_arise:shiraz_palace", "dungeons_arise:small_prairie_house", "dungeons_arise:thornborn_towers",
        "nova_structures:undead_crypt", "nova_structures:illager_hideout", "nova_structures:tavern",
        "nova_structures:lone_citadel", "nova_structures:poison_tower",
        "dungeons_and_taverns:illager_camp", "dungeons_and_taverns:tower", "dungeons_and_taverns:wild_ruins",
    ]
    struct_tag.write_text(
        json.dumps({"replace": False, "values": [opt(i) for i in struct_ids]}, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {struct_tag} ({len(struct_ids)} entries)")


if __name__ == "__main__":
    main()
