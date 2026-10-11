#!/usr/bin/env python3
"""Copy entity BH atlas into armor layer textures and generate distinct item icons."""

from __future__ import annotations

import shutil
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/millenaire/textures"
ENTITY = ASSETS / "entity"
ARMOR = ASSETS / "models/armor"
ITEM = ASSETS / "item"

LEATHER = (42, 32, 28, 255)
LEATHER_HI = (74, 50, 40, 255)
STEEL = (85, 86, 87, 255)
STEEL_HI = (140, 137, 131, 255)
CRIMSON = (108, 36, 39, 255)
AMBER = (255, 140, 22, 255)
GOLD = (173, 128, 70, 255)
BLACK = (12, 10, 9, 255)


def ensure_dirs() -> None:
    ARMOR.mkdir(parents=True, exist_ok=True)
    ITEM.mkdir(parents=True, exist_ok=True)


def copy_armor_layers() -> None:
    src = ENTITY / "bounty_hunter.png"
    em = ENTITY / "bounty_hunter_emissive.png"
    if not src.is_file():
        raise SystemExit(f"Missing entity texture: {src}")
    shutil.copy2(src, ARMOR / "bounty_hunter_layer_1.png")
    shutil.copy2(src, ARMOR / "bounty_hunter_layer_2.png")
    if em.is_file():
        shutil.copy2(em, ARMOR / "bounty_hunter_emissive.png")
    print("Wrote armor layer + emissive textures")


def icon(path: Path, draw_fn) -> None:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw_fn(d)
    img.save(path)
    print(f"Wrote {path.name}")


def draw_helmet(d: ImageDraw.ImageDraw) -> None:
    d.rectangle((1, 4, 14, 7), fill=LEATHER)  # brim
    d.rectangle((4, 1, 11, 6), fill=LEATHER_HI)  # crown
    d.rectangle((5, 6, 10, 12), fill=STEEL)  # mask
    d.point((6, 8), fill=AMBER)
    d.point((9, 8), fill=AMBER)
    d.rectangle((5, 12, 10, 14), fill=CRIMSON)


def draw_chest(d: ImageDraw.ImageDraw) -> None:
    d.rectangle((3, 1, 12, 10), fill=LEATHER)
    d.rectangle((2, 1, 4, 4), fill=STEEL)
    d.rectangle((11, 1, 13, 4), fill=STEEL)
    d.rectangle((8, 3, 10, 5), fill=GOLD)
    d.rectangle((2, 10, 6, 15), fill=LEATHER_HI)
    d.rectangle((9, 10, 13, 15), fill=LEATHER_HI)
    d.rectangle((6, 9, 9, 11), fill=STEEL_HI)


def draw_legs(d: ImageDraw.ImageDraw) -> None:
    d.rectangle((4, 1, 11, 5), fill=LEATHER)
    d.rectangle((4, 5, 7, 14), fill=LEATHER_HI)
    d.rectangle((8, 5, 11, 14), fill=LEATHER_HI)
    d.rectangle((4, 8, 7, 11), fill=STEEL)
    d.rectangle((8, 8, 11, 11), fill=STEEL)


def draw_boots(d: ImageDraw.ImageDraw) -> None:
    d.rectangle((3, 6, 7, 13), fill=LEATHER)
    d.rectangle((8, 6, 12, 13), fill=LEATHER)
    d.rectangle((2, 12, 7, 15), fill=STEEL)
    d.rectangle((8, 12, 13, 15), fill=STEEL)
    d.rectangle((3, 8, 6, 10), fill=STEEL_HI)
    d.rectangle((9, 8, 12, 10), fill=STEEL_HI)
    d.rectangle((4, 4, 6, 6), fill=BLACK)
    d.rectangle((9, 4, 11, 6), fill=BLACK)


def main() -> None:
    ensure_dirs()
    copy_armor_layers()
    icon(ITEM / "bounty_hunter_helmet.png", draw_helmet)
    icon(ITEM / "bounty_hunter_chestplate.png", draw_chest)
    icon(ITEM / "bounty_hunter_leggings.png", draw_legs)
    icon(ITEM / "bounty_hunter_boots.png", draw_boots)


if __name__ == "__main__":
    main()
