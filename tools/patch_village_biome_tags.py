"""Append Millenaire culture-specific biome tags to village JSONs."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "millenaire" / "cultures"

PATCHES = {
    "byzantines": ["#millenaire:is_mediterranean", "#millenaire:is_lavender"],
    "seljuk": ["#millenaire:is_mediterranean", "#millenaire:is_volcanic"],
    "japanese": ["#millenaire:is_cherry_grove", "#millenaire:is_hot_springs"],
}


def main() -> None:
    for culture, extra in PATCHES.items():
        villages = ROOT / culture / "villages"
        if not villages.is_dir():
            continue
        for path in villages.glob("*.json"):
            data = json.loads(path.read_text(encoding="utf-8"))
            tags = data.get("biome_tags")
            if not isinstance(tags, list):
                continue
            # Skip intentionally empty / weight-0 hamlets with empty tags unless they already have tags
            if not tags and data.get("weight", 0) == 0:
                continue
            changed = False
            for tag in extra:
                if tag not in tags:
                    # Japanese hot springs only on the inn
                    if tag == "#millenaire:is_hot_springs" and "hotspring" not in path.name.lower():
                        continue
                    # Don't force cherry grove onto every japanese village if they already specialize
                    if tag == "#millenaire:is_cherry_grove" and culture == "japanese":
                        if "#millenaire:is_cherry_grove" in tags or "#millenaire:is_meadow" in tags or "#c:is_taiga" in tags:
                            if tag not in tags and ("seiji" in path.name or "religious" in path.name or "trading" in path.name):
                                tags.append(tag)
                                changed = True
                        continue
                    tags.append(tag)
                    changed = True
            if changed:
                data["biome_tags"] = tags
                path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
                print("patched", path.relative_to(ROOT))


if __name__ == "__main__":
    main()
