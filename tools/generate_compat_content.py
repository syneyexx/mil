"""Generate Millenaire soft-compat content overlays (food, trade, gathering)."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "millenaire"


def write(rel: str, data: dict | list) -> None:
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("wrote", path)


def gathering(name: str, handler: str, inputs: list, outputs: list, held: str, label: str) -> dict:
    return {
        "handler": handler,
        "priority": 45,
        "scanRadius": 32,
        "batchRadius": 8,
        "maxActionsPerTask": 3,
        "actionCooldown": 100,
        "stuckTimeout": 4000,
        "walkSpeed": 0.6,
        "buildingLimit": {outputs[0]["item"]: 40},
        "handlerParams": {"inputs": inputs, "outputs": outputs},
        "sentenceKey": label,
        "labelKey": label,
        "heldItems": [held],
    }


def good(gid: str, item: str, sell: int, buy: int = 0, cat: str = "misc", target: int = 0) -> dict:
    g = {
        "id": gid,
        "item": item,
        "selling_price": sell,
        "category": cat,
    }
    if buy:
        g["buying_price"] = buy
    if target:
        g["target_quantity"] = target
    return g


def main() -> None:
    # Farmer's Delight / Naturalist food overlay (merged into default via separate named config + also patched default)
    food_overlay = {
        "_comment": "Modpack food values — Farmer's Delight + Naturalist (inactive IDs ignored)",
        "food_growth_overrides": {
            "farmersdelight:cooked_rice": 3,
            "farmersdelight:vegetable_noodles": 5,
            "farmersdelight:steak_and_potatoes": 5,
            "farmersdelight:roasted_mutton_chops": 4,
            "farmersdelight:chicken_soup": 4,
            "farmersdelight:beef_stew": 5,
            "farmersdelight:pumpkin_soup": 3,
            "farmersdelight:noodle_soup": 4,
            "farmersdelight:bacon_and_eggs": 4,
            "farmersdelight:hamburger": 5,
            "farmersdelight:cabbage": 1,
            "farmersdelight:tomato": 1,
            "farmersdelight:onion": 1,
            "farmersdelight:rice": 1,
            "naturalist:cooked_venison": 4,
            "naturalist:cooked_lizard_tail": 3,
        },
        "food_conception_overrides": {
            "farmersdelight:apple_cider": 2,
            "farmersdelight:melon_juice": 1,
            "farmersdelight:glow_berry_custard": 3,
            "farmersdelight:chocolate_pie": 4,
            "farmersdelight:sweet_berry_cheesecake": 4,
        },
    }
    write("villager_config/modpack_foods.json", food_overlay)

    # Patch default.json growth/conception with FD foods (soft — missing items skipped at resolve)
    default_path = ROOT / "villager_config" / "default.json"
    default = json.loads(default_path.read_text(encoding="utf-8"))
    default["food_growth"].update(food_overlay["food_growth_overrides"])
    default["food_conception"].update(food_conception := food_overlay["food_conception_overrides"])
    default_path.write_text(json.dumps(default, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("patched default villager_config")

    # Gathering types (foreign namespaces soft-fail when mods missing)
    gtypes = {
        "farming/plant_fd_cabbage.json": {
            "handler": "planting",
            "priority": 45,
            "scanRadius": 32,
            "batchRadius": 8,
            "maxActionsPerTask": 16,
            "actionCooldown": 10,
            "handlerParams": {
                "cropBlock": "farmersdelight:cabbages",
                "soilBlock": "minecraft:farmland",
                "buildingTag": "wheat",
            },
            "heldItems": ["farmersdelight:cabbage"],
            "sentenceKey": "plantcabbage",
            "labelKey": "plantcabbage",
        },
        "farming/plant_fd_tomato.json": {
            "handler": "planting",
            "priority": 45,
            "scanRadius": 32,
            "batchRadius": 8,
            "maxActionsPerTask": 16,
            "actionCooldown": 10,
            "handlerParams": {
                "cropBlock": "farmersdelight:tomatoes",
                "soilBlock": "minecraft:farmland",
                "buildingTag": "wheat",
            },
            "heldItems": ["farmersdelight:tomato"],
            "sentenceKey": "planttomato",
            "labelKey": "planttomato",
        },
        "farming/harvest_fd_cabbage.json": {
            "handler": "harvesting",
            "priority": 40,
            "scanRadius": 32,
            "batchRadius": 8,
            "maxActionsPerTask": 16,
            "actionCooldown": 10,
            "villageLimit": {"farmersdelight:cabbage": 256},
            "handlerParams": {
                "targetBlock": "farmersdelight:cabbages",
                "targetState": {"age": 7},
                "buildingTag": "wheat",
                "harvestItems": [
                    {"item": "farmersdelight:cabbage", "chance": 100},
                    {"item": "farmersdelight:cabbage_seeds", "chance": 50},
                ],
            },
            "sentenceKey": "collectcrop",
            "labelKey": "collectcrop",
            "heldItems": ["farmersdelight:cabbage"],
        },
        "food/cook_fd_rice.json": {
            "handler": "smelting",
            "priority": 50,
            "scanRadius": 32,
            "batchRadius": 8,
            "maxActionsPerTask": 3,
            "actionCooldown": 100,
            "buildingLimit": {"farmersdelight:cooked_rice": 60},
            "itemsBalance": {"farmersdelight:rice": "farmersdelight:cooked_rice"},
            "handlerParams": {
                "inputs": [{"item": "farmersdelight:rice", "count": 1}],
                "outputs": [{"item": "farmersdelight:cooked_rice", "count": 1}],
            },
            "sentenceKey": "cookrice",
            "labelKey": "cookrice",
            "heldItems": ["farmersdelight:rice"],
        },
        "construction/craft_mcw_roof_oak.json": {
            "handler": "crafting",
            "priority": 35,
            "scanRadius": 32,
            "batchRadius": 8,
            "maxActionsPerTask": 5,
            "actionCooldown": 100,
            "townhallLimit": {"mcwroofs:oak_roof": 256},
            "handlerParams": {
                "inputs": [{"item": "minecraft:oak_planks", "count": 2}],
                "outputs": [{"item": "mcwroofs:oak_roof", "count": 2}],
            },
            "heldItems": ["minecraft:oak_planks", "mcwroofs:oak_roof"],
        },
        "construction/craft_mcw_window_oak.json": {
            "handler": "crafting",
            "priority": 35,
            "scanRadius": 32,
            "batchRadius": 8,
            "maxActionsPerTask": 5,
            "actionCooldown": 100,
            "townhallLimit": {"mcwwindows:window_base": 128},
            "handlerParams": {
                "inputs": [{"item": "minecraft:oak_planks", "count": 2}, {"item": "minecraft:glass_pane", "count": 1}],
                "outputs": [{"item": "mcwwindows:window_base", "count": 1}],
            },
            "heldItems": ["minecraft:glass_pane", "mcwwindows:window_base"],
        },
        "construction/craft_supplementaries_lantern.json": {
            "handler": "crafting",
            "priority": 30,
            "scanRadius": 32,
            "batchRadius": 8,
            "maxActionsPerTask": 5,
            "actionCooldown": 100,
            "townhallLimit": {"supplementaries:copper_lantern": 64},
            "handlerParams": {
                "inputs": [{"item": "minecraft:copper_ingot", "count": 1}, {"item": "minecraft:torch", "count": 1}],
                "outputs": [{"item": "supplementaries:copper_lantern", "count": 1}],
            },
            "heldItems": ["minecraft:torch", "supplementaries:copper_lantern"],
        },
        "construction/craft_create_cogwheel.json": {
            "handler": "crafting",
            "priority": 30,
            "scanRadius": 32,
            "batchRadius": 8,
            "maxActionsPerTask": 4,
            "actionCooldown": 120,
            "townhallLimit": {"create:cogwheel": 32},
            "handlerParams": {
                "inputs": [{"item": "create:andesite_alloy", "count": 1}, {"item": "minecraft:oak_planks", "count": 1}],
                "outputs": [{"item": "create:cogwheel", "count": 1}],
            },
            "heldItems": ["create:andesite_alloy", "create:cogwheel"],
        },
    }
    for rel, data in gtypes.items():
        write(f"gathering_type/{rel}", data)

    # Compat traded goods appended per culture
    compat_goods = {
        "norman": [
            good("fd_cabbage", "farmersdelight:cabbage", 3, 1, "food", 32),
            good("fd_tomato", "farmersdelight:tomato", 3, 1, "food", 32),
            good("fd_cooked_rice", "farmersdelight:cooked_rice", 6, 0, "food", 16),
            good("fd_hamburger", "farmersdelight:hamburger", 14, 0, "food", 8),
            good("mcw_oak_roof", "mcwroofs:oak_roof", 8, 2, "construction", 32),
            good("mcw_oak_window", "mcwwindows:oak_window", 10, 3, "construction", 16),
            good("mcw_oak_door", "mcwdoors:oak_barn_door", 12, 4, "construction", 8),
            good("supp_lantern", "supplementaries:copper_lantern", 10, 3, "decoration", 16),
            good("create_cogwheel", "create:cogwheel", 20, 8, "construction", 8),
            good("create_andesite_alloy", "create:andesite_alloy", 16, 6, "construction", 16),
            good("waystone_item", "waystones:waystone", 200, 0, "misc", 1),
            good("travelersbackpack", "travelersbackpack:standard", 80, 0, "misc", 2),
            good("artifacts_bunny_hoppers", "artifacts:bunny_hoppers", 120, 0, "armor", 1),
            good("ek_arming_sword", "magistuarmory:arming_sword", 60, 20, "weapon", 4),
            good("immersive_heavy_helmet", "immersive_armors:heavy_helmet", 50, 15, "armor", 2),
            good("spell_tome_trophy", "spell_engine:scroll_fireball", 150, 0, "misc", 1),
            good("tacz_ammo_box", "tacz:ammo_box", 100, 0, "misc", 1),
        ],
        "byzantines": [
            good("fd_noodle_soup", "farmersdelight:noodle_soup", 12, 0, "food", 8),
            good("mcw_spruce_roof", "mcwroofs:spruce_roof", 8, 2, "construction", 24),
            good("supp_sconce", "supplementaries:sconce", 8, 2, "decoration", 12),
            good("create_water_wheel", "create:water_wheel", 40, 15, "construction", 2),
            good("ek_estoc", "magistuarmory:estoc", 70, 25, "weapon", 3),
        ],
        "japanese": [
            good("fd_cooked_rice", "farmersdelight:cooked_rice", 5, 0, "food", 24),
            good("fd_vegetable_noodles", "farmersdelight:vegetable_noodles", 12, 0, "food", 8),
            good("mcw_cherry_roof", "mcwroofs:cherry_roof", 10, 3, "construction", 16),
            good("mcw_paper_wall", "mcwwindows:paper_oak_window", 14, 4, "construction", 12),
            good("immersive_warrior_chest", "immersive_armors:warrior_chestplate", 55, 18, "armor", 2),
        ],
        "indian": [
            good("fd_rice", "farmersdelight:rice", 2, 1, "food", 64),
            good("fd_cabbage", "farmersdelight:cabbage", 3, 1, "food", 32),
            good("fd_chicken_soup", "farmersdelight:chicken_soup", 10, 0, "food", 8),
            good("mcw_jungle_roof", "mcwroofs:jungle_roof", 8, 2, "construction", 16),
        ],
        "mayan": [
            good("fd_tomato", "farmersdelight:tomato", 3, 1, "food", 32),
            good("fd_pumpkin_soup", "farmersdelight:pumpkin_soup", 8, 0, "food", 8),
            good("mcw_dark_oak_roof", "mcwroofs:dark_oak_roof", 9, 3, "construction", 12),
            good("naturalist_venison", "naturalist:cooked_venison", 8, 0, "food", 8),
        ],
        "seljuk": [
            good("fd_onion", "farmersdelight:onion", 2, 1, "food", 32),
            good("fd_beef_stew", "farmersdelight:beef_stew", 12, 0, "food", 8),
            good("mcw_sandstone_path", "mcwpaths:sand_path", 6, 2, "construction", 24),
            good("exotic_oriental_helmet", "exotic_armory:oriental_helmet", 55, 18, "armor", 2),
        ],
        "inuits": [
            good("fd_salmon_roll", "farmersdelight:salmon_roll", 10, 0, "food", 8),
            good("naturalist_venison", "naturalist:cooked_venison", 8, 0, "food", 12),
            good("mcw_spruce_path", "mcwpaths:spruce_planks_path", 6, 2, "construction", 16),
        ],
    }

    for culture, goods in compat_goods.items():
        path = ROOT / "cultures" / culture / "traded_goods.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        existing = {g.get("id") for g in data.get("goods", [])}
        added = 0
        for g in goods:
            if g["id"] not in existing:
                data["goods"].append(g)
                added += 1
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"appended {added} goods to {culture}")

    # Shop sell lists — append compat good ids to key shops
    shop_appends = {
        "norman/shops/townhall.json": ["fd_hamburger", "mcw_oak_roof", "create_cogwheel", "waystone_item", "ek_arming_sword"],
        "norman/shops/armoury.json": ["ek_arming_sword", "immersive_heavy_helmet", "artifacts_bunny_hoppers"],
        "norman/shops/bakery.json": ["fd_cooked_rice", "fd_hamburger"],
        "byzantines/shops/taverna.json": ["fd_noodle_soup"],
        "japanese/shops/japanesetownhall.json": ["fd_cooked_rice", "fd_vegetable_noodles", "mcw_cherry_roof"],
        "indian/shops/townhall.json": ["fd_rice", "fd_cabbage"],
        "seljuk/shops/townhall.json": ["fd_onion", "fd_beef_stew", "mcw_sandstone_path"],
        "mayan/shops/mayantownhall.json": ["fd_tomato", "naturalist_venison"],
        "inuits/shops/inuittownhall.json": ["fd_salmon_roll", "naturalist_venison"],
    }
    for rel, ids in shop_appends.items():
        path = ROOT / "cultures" / rel
        if not path.exists():
            print("missing shop", rel)
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        sells = data.setdefault("sells", [])
        for i in ids:
            if i not in sells:
                sells.append(i)
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print("updated shop", rel)

    # Traveler's Titles-style datapack biome titles are client-side; ship a small resource hint file
    titles = {
        "replace": False,
        "values": [
            {"id": "biomesoplenty:mediterranean_forest", "required": False},
            {"id": "biomesoplenty:lavender_field", "required": False},
            {"id": "biomeswevegone:sakura_grove", "required": False},
            {"id": "natures_spirit:sugi_forest", "required": False},
        ],
    }
    # already covered by biome tags; write docs mapping instead below


if __name__ == "__main__":
    main()
