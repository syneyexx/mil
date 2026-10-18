#!/usr/bin/env python3
"""Generate 16x16 Orde block textures + JSON assets from the concept catalog."""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
TEX = ROOT / "src/main/resources/assets/millenaire/textures/block"
BS = ROOT / "src/main/resources/assets/millenaire/blockstates"
MB = ROOT / "src/main/resources/assets/millenaire/models/block"
MI = ROOT / "src/main/resources/assets/millenaire/models/item"
LOOT = ROOT / "src/main/resources/data/millenaire/loot_table/blocks"

# Cold horror palette (from concept sheets)
INK = (12, 12, 14, 255)
NEAR_BLACK = (18, 18, 22, 255)
DARK = (28, 28, 34, 255)
STONE = (36, 36, 42, 255)
STONE2 = (44, 44, 52, 255)
MORTAR = (120, 140, 155, 255)  # cold blue-grey
PANEL = (40, 42, 48, 255)
PANEL_SEAM = (18, 18, 22, 255)
PANEL_HI = (70, 74, 84, 255)
TILE = (52, 54, 60, 255)
TILE_REC = (30, 30, 36, 255)
TILE_HI = (90, 94, 104, 255)
PLANK = (62, 66, 72, 255)
PLANK2 = (48, 52, 58, 255)
PLANK_LINE = (28, 30, 34, 255)
PLANK_HI = (78, 82, 88, 255)
WALLOBEIGE = (168, 158, 142, 255)
WALLOBEIGE2 = (152, 142, 128, 255)
FLOWER = (140, 70, 78, 255)
LEAF = (70, 95, 72, 255)
WOOD_DK = (32, 26, 22, 255)
WOOD = (48, 38, 30, 255)
WOOD_HI = (70, 56, 44, 255)
QUILT_A = (200, 200, 205, 255)
QUILT_B = (90, 90, 95, 255)
QUILT_C = (40, 40, 44, 255)
CHEVRON = (70, 72, 78, 255)
CHEVRON_DK = (28, 28, 32, 255)
METAL = (70, 72, 78, 255)
METAL_DK = (42, 40, 38, 255)
RUST = (78, 55, 42, 255)
SPIKE = (160, 165, 175, 255)
SPIKE_DK = (90, 95, 105, 255)
LEATHER = (58, 32, 26, 255)
LEATHER2 = (42, 22, 18, 255)
STITCH = (92, 60, 48, 255)
NAIL = (170, 175, 185, 255)
STRAP = (48, 26, 20, 255)
STONE_L = (110, 112, 118, 255)
VEIL = (40, 42, 48, 180)
VEIL_FRAME = (24, 24, 28, 255)
CRIMSON = (72, 28, 32, 255)
RUNE = (180, 185, 195, 255)
BLUE = (110, 170, 210, 255)
BLUE_CORE = (200, 230, 255, 255)


def new_img(fill=INK):
    return Image.new("RGBA", (16, 16), fill)


def px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c)


def fill_rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(img, x, y, c)


def hline(img, y, x0, x1, c):
    for x in range(x0, x1 + 1):
        px(img, x, y, c)


def vline(img, x, y0, y1, c):
    for y in range(y0, y1 + 1):
        px(img, x, y, c)


def save(img: Image.Image, name: str):
    TEX.mkdir(parents=True, exist_ok=True)
    path = TEX / f"{name}.png"
    img.save(path)
    print("texture", path.relative_to(ROOT))


def tex_orde_stone():
    img = new_img(NEAR_BLACK)
    # mottled base
    for y in range(16):
        for x in range(16):
            n = (x * 3 + y * 5) % 7
            c = NEAR_BLACK if n < 3 else DARK if n < 5 else STONE
            px(img, x, y, c)
    # irregular cold mortar seams (concept: geometric cracked pattern)
    for x, y in [
        (2, 0), (2, 1), (2, 2), (3, 2), (4, 2), (4, 3), (4, 4), (5, 4), (6, 4),
        (6, 5), (6, 6), (7, 6), (8, 6), (8, 7), (8, 8), (9, 8), (10, 8),
        (10, 9), (10, 10), (11, 10), (12, 10), (12, 11), (12, 12), (13, 12),
        (0, 5), (1, 5), (2, 5), (2, 6), (2, 7), (1, 7), (0, 7),
        (14, 3), (15, 3), (14, 4), (14, 5), (15, 5),
        (3, 11), (3, 12), (3, 13), (4, 13), (5, 13), (5, 14), (5, 15),
        (9, 1), (10, 1), (11, 1), (11, 2), (11, 3),
    ]:
        px(img, x, y, MORTAR)
    save(img, "orde_stone")


def tex_orde_panel():
    img = new_img(PANEL)
    for y in range(16):
        for x in range(16):
            px(img, x, y, PANEL_HI if (x + y) % 9 == 0 else PANEL)
    # vertical panel seams
    for x in (0, 5, 10, 15):
        vline(img, x, 0, 15, PANEL_SEAM)
    # top/bottom trim
    hline(img, 0, 0, 15, PANEL_SEAM)
    hline(img, 15, 0, 15, PANEL_SEAM)
    hline(img, 1, 1, 14, PANEL_HI)
    save(img, "orde_panel")


def tex_orde_tile():
    img = new_img(TILE)
    # labyrinthine recessed rectangles (puzzle motif, original)
    fill_rect(img, 0, 0, 15, 15, TILE)
    rects = [
        (1, 1, 6, 4), (8, 1, 14, 3), (1, 5, 4, 10), (6, 5, 10, 7),
        (11, 4, 14, 9), (2, 11, 7, 14), (9, 9, 14, 14), (5, 8, 8, 10),
    ]
    for x0, y0, x1, y1 in rects:
        fill_rect(img, x0, y0, x1, y1, TILE_REC)
        hline(img, y0, x0, x1, TILE_HI)
        vline(img, x0, y0, y1, TILE_HI)
    save(img, "orde_tile")


def tex_bleed_planks():
    img = new_img(PLANK)
    # cold desaturated spruce — horizontal boards with visible grain (concept sheet #4)
    bands = [(0, 3, PLANK), (4, 7, PLANK2), (8, 11, PLANK), (12, 15, PLANK2)]
    for y0, y1, base in bands:
        for y in range(y0, y1 + 1):
            for x in range(16):
                if x % 5 == (y0 // 4) % 3:
                    px(img, x, y, PLANK_LINE)
                elif x % 7 == 2:
                    px(img, x, y, PLANK_HI)
                else:
                    px(img, x, y, base)
        hline(img, y1, 0, 15, PLANK_LINE)
    save(img, "bleed_planks")


def tex_orde_bricks():
    img = new_img(DARK)
    # brick rows
    rows = [(0, 3), (4, 7), (8, 11), (12, 15)]
    for i, (y0, y1) in enumerate(rows):
        fill_rect(img, 0, y0, 15, y1, STONE if i % 2 == 0 else STONE2)
        hline(img, y1, 0, 15, MORTAR)
        # vertical mortar offset per row
        offs = 0 if i % 2 == 0 else 4
        for x in range(offs, 16, 8):
            vline(img, x, y0, y1, MORTAR)
    save(img, "orde_bricks")


def tex_orde_pillar():
    img = new_img(STONE)
    fill_rect(img, 0, 0, 15, 15, DARK)
    # diamond-stitch / criss-cross
    for i in range(16):
        px(img, i, i, MORTAR)
        px(img, 15 - i, i, MORTAR)
        if i < 15:
            px(img, i, i + 1, STONE2)
    vline(img, 0, 0, 15, PANEL_SEAM)
    vline(img, 15, 0, 15, PANEL_SEAM)
    save(img, "orde_pillar")


def tex_orde_rune():
    img = new_img(NEAR_BLACK)
    fill_rect(img, 0, 0, 15, 15, DARK)
    fill_rect(img, 1, 1, 14, 14, NEAR_BLACK)
    # square-in-square + cross
    hline(img, 3, 3, 12, RUNE)
    hline(img, 12, 3, 12, RUNE)
    vline(img, 3, 3, 12, RUNE)
    vline(img, 12, 3, 12, RUNE)
    hline(img, 7, 5, 10, RUNE)
    vline(img, 7, 5, 10, RUNE)
    save(img, "orde_rune")


def tex_orde_sigil():
    img = new_img(INK)
    fill_rect(img, 0, 0, 15, 15, NEAR_BLACK)
    # diamond with vertical line (Order sigil from shrine mural)
    diamond = [
        (8, 1), (7, 2), (9, 2), (6, 3), (10, 3), (5, 4), (11, 4),
        (4, 5), (12, 5), (3, 6), (13, 6), (2, 7), (14, 7),
        (3, 8), (13, 8), (4, 9), (12, 9), (5, 10), (11, 10),
        (6, 11), (10, 11), (7, 12), (9, 12), (8, 13),
    ]
    for x, y in diamond:
        px(img, x, y, RUNE)
    vline(img, 8, 2, 13, RUNE)
    save(img, "orde_sigil")


def tex_orde_script():
    img = new_img(NEAR_BLACK)
    fill_rect(img, 0, 0, 15, 15, DARK)
    # abstract glyphs row
    glyphs = [
        [(2, 4), (2, 5), (2, 6), (2, 7), (2, 8), (3, 4), (4, 4), (4, 6), (3, 6), (4, 8)],
        [(7, 5), (8, 4), (9, 5), (8, 6), (8, 7), (8, 8), (7, 8), (9, 8)],
        [(12, 4), (12, 5), (12, 6), (12, 7), (12, 8), (13, 4), (14, 5), (13, 6), (14, 7), (13, 8)],
    ]
    for g in glyphs:
        for x, y in g:
            px(img, x, y, RUNE)
    save(img, "orde_script")


def tex_riveted_stone():
    img = new_img(STONE)
    for y in range(16):
        for x in range(16):
            px(img, x, y, STONE2 if (x // 2 + y // 2) % 2 == 0 else STONE)
    # corner rivets
    for cx, cy in [(1, 1), (14, 1), (1, 14), (14, 14), (1, 8), (14, 8), (8, 1), (8, 14)]:
        fill_rect(img, cx - 1, cy - 1, cx, cy, METAL)
        px(img, cx, cy, NAIL)
    save(img, "riveted_stone")


def tex_crimson_band():
    img = new_img(CRIMSON)
    for y in range(16):
        for x in range(16):
            c = CRIMSON if (y < 5 or y > 10) else LEATHER
            if y in (5, 10):
                c = STITCH
            px(img, x, y, c)
    for x in range(0, 16, 4):
        px(img, x, 7, NAIL)
        px(img, x + 1, 8, NAIL)
    save(img, "crimson_band")


def tex_bound_leather():
    img = new_img(LEATHER)
    for y in range(16):
        for x in range(16):
            px(img, x, y, LEATHER2 if (x + y) % 6 == 0 else LEATHER)
    # edge stitching
    for i in range(16):
        px(img, i, 0, STITCH)
        px(img, i, 15, STITCH)
        px(img, 0, i, STITCH)
        px(img, 15, i, STITCH)
        if i % 2 == 0:
            px(img, i, 1, STITCH)
            px(img, 1, i, STITCH)
    save(img, "bound_leather")


def tex_nail_studded():
    img = new_img(LEATHER2)
    for y in range(16):
        for x in range(16):
            px(img, x, y, LEATHER2 if (x * y) % 5 else (22, 14, 12, 255))
    for y in range(2, 15, 4):
        for x in range(2, 15, 4):
            fill_rect(img, x, y, x + 1, y + 1, NAIL)
            px(img, x, y, (220, 220, 230, 255))
    save(img, "nail_studded")


def tex_strap_block():
    img = new_img(STONE_L)
    for y in range(16):
        for x in range(16):
            px(img, x, y, STONE_L if (x + y) % 3 else (95, 97, 102, 255))
    # leather straps cross
    fill_rect(img, 0, 6, 15, 9, STRAP)
    fill_rect(img, 6, 0, 9, 15, STRAP)
    for p in [(2, 7), (7, 2), (13, 7), (7, 13), (7, 7)]:
        px(img, p[0], p[1], NAIL)
        px(img, p[0] + 1, p[1], NAIL)
    save(img, "strap_block")


def tex_silk_veil():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # frame
    hline(img, 0, 0, 15, VEIL_FRAME)
    hline(img, 1, 0, 15, VEIL_FRAME)
    vline(img, 0, 0, 15, VEIL_FRAME)
    vline(img, 15, 0, 15, VEIL_FRAME)
    # hanging folds
    for y in range(2, 16):
        for x in range(1, 15):
            wave = (x // 3) % 2
            a = 140 - y * 4 + wave * 20
            px(img, x, y, (40, 42, 48, max(40, min(200, a))))
    save(img, "silk_veil")
    # edge texture for pane
    edge = new_img(VEIL_FRAME)
    save(edge, "silk_veil_edge")


def tex_orde_bars():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in (2, 5, 8, 11, 14):
        vline(img, x, 0, 15, METAL_DK)
        vline(img, x - 1 if x > 0 else x, 0, 15, RUST if x % 2 == 0 else METAL)
    hline(img, 0, 0, 15, METAL)
    hline(img, 15, 0, 15, METAL)
    save(img, "orde_bars")
    top = new_img(METAL_DK)
    fill_rect(top, 0, 0, 15, 15, METAL)
    save(top, "orde_bars_top")


def tex_orde_grate():
    img = new_img(METAL_DK)
    fill_rect(img, 0, 0, 15, 15, METAL_DK)
    for gy in range(1, 15, 5):
        for gx in range(1, 15, 5):
            fill_rect(img, gx, gy, gx + 2, gy + 2, (0, 0, 0, 0))
            # dark hole rim
            for x in range(gx, gx + 3):
                for y in range(gy, gy + 3):
                    if x in (gx, gx + 2) or y in (gy, gy + 2):
                        px(img, x, y, METAL)
    save(img, "orde_grate")


def tex_spike_floor():
    img = new_img(NEAR_BLACK)
    for y in range(16):
        for x in range(16):
            px(img, x, y, DARK if (x + y) % 4 == 0 else NEAR_BLACK)
    # spike tips as bright pixels in grid
    for y in range(2, 15, 4):
        for x in range(2, 15, 4):
            px(img, x, y, SPIKE)
            px(img, x, y - 1, SPIKE_DK)
            px(img, x - 1, y, SPIKE_DK)
            px(img, x + 1, y, SPIKE_DK)
            px(img, x, y + 1, METAL_DK)
    save(img, "spike_floor")
    save(img, "spike_floor_top")
    side = new_img(NEAR_BLACK)
    fill_rect(side, 0, 0, 15, 15, DARK)
    save(side, "spike_floor_side")


def tex_spike_wall():
    img = new_img(DARK)
    for y in range(16):
        for x in range(16):
            px(img, x, y, STONE if (x * 3 + y) % 5 == 0 else DARK)
    # 2x2 blunt spikes on face
    for cx, cy in [(4, 4), (11, 4), (4, 11), (11, 11)]:
        fill_rect(img, cx - 1, cy - 1, cx + 1, cy + 1, METAL_DK)
        px(img, cx, cy, SPIKE)
        px(img, cx, cy - 1, SPIKE_DK)
    save(img, "spike_wall")


def tex_hook_block():
    img = new_img(DARK)
    for y in range(16):
        for x in range(16):
            px(img, x, y, STONE if (x + y * 2) % 6 == 0 else DARK)
    # mount plate
    fill_rect(img, 4, 2, 11, 8, METAL_DK)
    fill_rect(img, 5, 3, 10, 7, METAL)
    # ornamental engraving
    hline(img, 4, 5, 10, NAIL)
    hline(img, 6, 5, 10, NAIL)
    # hook curve
    for x, y in [(7, 8), (7, 9), (7, 10), (7, 11), (8, 12), (9, 12), (10, 11), (10, 10)]:
        px(img, x, y, SPIKE)
        px(img, x - 1, y, METAL)
    save(img, "hook_block")
    save(img, "hook_block_side")


def tex_offering_bowl():
    img = new_img((0, 0, 0, 0))
    # top view bowl
    fill_rect(img, 2, 2, 13, 13, STONE)
    fill_rect(img, 4, 4, 11, 11, NEAR_BLACK)
    fill_rect(img, 5, 5, 10, 10, INK)
    hline(img, 2, 2, 13, METAL)
    hline(img, 13, 2, 13, METAL)
    save(img, "offering_bowl_top")
    side = new_img(STONE)
    fill_rect(side, 0, 0, 15, 15, DARK)
    fill_rect(side, 2, 6, 13, 15, STONE)
    fill_rect(side, 4, 8, 11, 14, NEAR_BLACK)
    save(side, "offering_bowl_side")


def tex_orde_altar():
    # reuse riveted look with crimson mid band on side
    tex_riveted_stone()
    side = Image.open(TEX / "riveted_stone.png").copy()
    fill_rect(side, 0, 6, 15, 9, CRIMSON)
    hline(side, 6, 0, 15, STITCH)
    hline(side, 9, 0, 15, STITCH)
    save(side, "orde_altar_side")
    top = Image.open(TEX / "riveted_stone.png").copy()
    save(top, "orde_altar_top")


def tex_orde_brazier():
    img = new_img(DARK)
    fill_rect(img, 2, 8, 13, 15, STONE)
    fill_rect(img, 3, 9, 12, 14, METAL_DK)
    for x in (3, 12, 3, 12):
        pass
    px(img, 3, 9, NAIL)
    px(img, 12, 9, NAIL)
    px(img, 3, 14, NAIL)
    px(img, 12, 14, NAIL)
    # cold flame
    for x, y, c in [
        (7, 7, BLUE), (8, 7, BLUE), (6, 6, BLUE), (9, 6, BLUE),
        (7, 5, BLUE_CORE), (8, 5, BLUE_CORE), (7, 4, BLUE), (8, 3, BLUE_CORE),
    ]:
        px(img, x, y, c)
    save(img, "orde_brazier")
    save(img, "orde_brazier_top")


def tex_orde_lantern():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # chain
    px(img, 7, 0, METAL)
    px(img, 8, 1, METAL)
    px(img, 7, 2, METAL)
    # cage
    fill_rect(img, 4, 3, 11, 12, METAL_DK)
    fill_rect(img, 5, 4, 10, 11, BLUE_CORE)
    fill_rect(img, 6, 5, 9, 10, BLUE)
    vline(img, 4, 3, 12, METAL)
    vline(img, 11, 3, 12, METAL)
    hline(img, 3, 4, 11, METAL)
    hline(img, 12, 4, 11, METAL)
    save(img, "orde_lantern")


def tex_void_sconce():
    img = new_img(DARK)
    fill_rect(img, 5, 2, 10, 6, METAL_DK)
    fill_rect(img, 6, 3, 9, 5, BLUE)
    fill_rect(img, 6, 7, 9, 14, STONE)
    px(img, 7, 4, BLUE_CORE)
    save(img, "void_sconce")


def tex_bleed_wallpaper():
    # Bleed bedroom kit: aged beige floral wallpaper with pink blooms + green leaves
    img = new_img(WALLOBEIGE)
    for y in range(16):
        for x in range(16):
            px(img, x, y, WALLOBEIGE2 if (x + y * 2) % 9 == 0 else WALLOBEIGE)
    # faint vine lattice
    for y in range(0, 16, 4):
        for x in range(16):
            if (x + y) % 4 == 0:
                px(img, x, y, (140, 132, 118, 255))
    flowers = [(3, 3), (11, 2), (7, 7), (2, 11), (12, 12), (9, 4)]
    for cx, cy in flowers:
        px(img, cx, cy, FLOWER)
        px(img, cx + 1, cy, FLOWER)
        px(img, cx, cy + 1, (110, 50, 58, 255))
        px(img, cx + 1, cy - 1 if cy else cy, LEAF)
        px(img, cx - 1 if cx else cx, cy, LEAF)
    # stain edge
    hline(img, 15, 0, 15, (120, 110, 98, 255))
    save(img, "bleed_wallpaper")


def tex_chevron_tile():
    # shrine floor: dark tile with lighter V / chevron
    img = new_img(CHEVRON_DK)
    for y in range(16):
        for x in range(16):
            px(img, x, y, CHEVRON_DK if (x + y) % 5 else (34, 34, 38, 255))
    # V pointing down-center
    for i in range(8):
        px(img, 7 - i, 2 + i, CHEVRON)
        px(img, 8 + i, 2 + i, CHEVRON)
        px(img, 7 - i, 3 + i, (90, 92, 98, 255))
        px(img, 8 + i, 3 + i, (90, 92, 98, 255))
    hline(img, 10, 3, 12, CHEVRON)
    save(img, "chevron_tile")


def tex_fluted_pillar():
    # arrival kit: vertical ribbed / fluted column
    img = new_img(DARK)
    for x in range(16):
        band = x % 4
        c = PANEL_SEAM if band == 0 else (STONE if band == 1 else (PANEL_HI if band == 2 else STONE2))
        vline(img, x, 0, 15, c)
    save(img, "fluted_pillar")


def tex_pillar_base():
    # recessed square frame capital/base
    img = new_img(DARK)
    fill_rect(img, 0, 0, 15, 15, STONE)
    fill_rect(img, 2, 2, 13, 13, DARK)
    fill_rect(img, 4, 4, 11, 11, NEAR_BLACK)
    hline(img, 2, 2, 13, PANEL_HI)
    vline(img, 2, 2, 13, PANEL_HI)
    hline(img, 13, 2, 13, PANEL_SEAM)
    vline(img, 13, 2, 13, PANEL_SEAM)
    save(img, "pillar_base")


def tex_orde_coffer():
    # coffered ceiling: concentric recessed squares
    img = new_img(NEAR_BLACK)
    fill_rect(img, 0, 0, 15, 15, DARK)
    fill_rect(img, 1, 1, 14, 14, STONE)
    fill_rect(img, 3, 3, 12, 12, DARK)
    fill_rect(img, 5, 5, 10, 10, INK)
    hline(img, 1, 1, 14, PANEL_HI)
    vline(img, 1, 1, 14, PANEL_HI)
    hline(img, 3, 3, 12, STONE2)
    save(img, "orde_coffer")


def tex_portal_frame():
    # heavy portal masonry with raised stud
    img = new_img(DARK)
    fill_rect(img, 0, 0, 15, 15, STONE)
    fill_rect(img, 2, 2, 13, 13, DARK)
    fill_rect(img, 5, 5, 10, 10, STONE2)
    fill_rect(img, 6, 6, 9, 9, PANEL_HI)
    for cx, cy in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        px(img, cx, cy, NAIL)
    save(img, "portal_frame")


def tex_void_gate():
    # blue crystalline void energy (arrival portal)
    img = new_img((8, 20, 40, 255))
    for y in range(16):
        for x in range(16):
            n = (x * 5 + y * 7) % 11
            if n < 2:
                px(img, x, y, BLUE_CORE)
            elif n < 4:
                px(img, x, y, BLUE)
            elif n < 7:
                px(img, x, y, (40, 90, 140, 255))
            else:
                px(img, x, y, (10, 30, 55, 255))
    save(img, "void_gate")


def tex_hanging_hook():
    # corridor: chain links + ceremonial hook (cutout-friendly)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # chain
    for y in (0, 2, 4, 6):
        fill_rect(img, 6, y, 9, y + 1, METAL)
        px(img, 6, y, METAL_DK)
        px(img, 9, y + 1, METAL_DK)
    # hook
    for x, y in [
        (7, 8), (8, 8), (7, 9), (7, 10), (7, 11), (7, 12),
        (8, 13), (9, 13), (10, 12), (10, 11), (9, 11),
    ]:
        px(img, x, y, SPIKE)
        px(img, x - 1, y, METAL_DK)
    save(img, "hanging_hook")


def tex_orde_censer():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # chain
    px(img, 7, 0, METAL)
    px(img, 8, 1, METAL)
    px(img, 7, 2, METAL)
    # diamond censer body
    for x, y in [(7, 3), (6, 4), (8, 4), (5, 5), (9, 5), (6, 6), (8, 6), (7, 7)]:
        px(img, x, y, METAL_DK)
    px(img, 7, 5, (180, 90, 40, 255))  # ember
    px(img, 7, 4, (220, 140, 60, 255))
    save(img, "orde_censer")


def tex_orde_barricade():
    # boarded window / barricade with cold blue bleed behind
    img = new_img((20, 40, 60, 255))
    fill_rect(img, 0, 0, 15, 15, (30, 70, 100, 255))
    for y in range(16):
        for x in range(16):
            if (x + y) % 3 == 0:
                px(img, x, y, BLUE)
    # chaotic dark beams
    for x0, y0, x1, y1 in [
        (1, 0, 3, 15), (12, 0, 14, 15), (0, 3, 15, 5), (0, 10, 15, 12),
        (4, 0, 6, 15), (8, 1, 10, 14),
    ]:
        fill_rect(img, x0, y0, x1, y1, WOOD_DK)
        hline(img, y0, x0, x1, WOOD)
    # nails
    for p in [(2, 4), (5, 11), (9, 3), (13, 10)]:
        px(img, p[0], p[1], NAIL)
    save(img, "orde_barricade")


def tex_orde_bedding():
    # grayscale noise quilt from bleed bedroom
    img = new_img(QUILT_C)
    for y in range(16):
        for x in range(16):
            n = (x * 3 + y * 5 + x * y) % 8
            c = QUILT_A if n < 2 else QUILT_B if n < 5 else QUILT_C
            px(img, x, y, c)
    # pillow stripe top
    fill_rect(img, 1, 1, 14, 4, (220, 220, 225, 255))
    hline(img, 4, 1, 14, QUILT_B)
    save(img, "orde_bedding")


def tex_orde_nightstand():
    img = new_img(WOOD_DK)
    fill_rect(img, 0, 0, 15, 15, WOOD)
    # drawer face
    fill_rect(img, 2, 5, 13, 11, WOOD_DK)
    fill_rect(img, 3, 6, 12, 10, WOOD_HI)
    # handle
    fill_rect(img, 6, 7, 9, 8, NAIL)
    # top edge
    hline(img, 0, 0, 15, WOOD_HI)
    save(img, "orde_nightstand")


def tex_orde_clock():
    img = new_img(WOOD_DK)
    fill_rect(img, 1, 1, 14, 14, INK)
    # white face
    fill_rect(img, 3, 3, 12, 12, (230, 230, 235, 255))
    # rim
    for i in range(16):
        for j in range(16):
            d2 = (i - 7.5) ** 2 + (j - 7.5) ** 2
            if 36 < d2 < 49:
                px(img, i, j, INK)
            elif d2 >= 49:
                px(img, i, j, WOOD_DK)
    # hands ~2:00
    vline(img, 8, 4, 8, INK)
    for x, y in [(8, 8), (9, 7), (10, 6), (11, 5)]:
        px(img, x, y, INK)
    px(img, 8, 8, (180, 40, 40, 255))
    save(img, "orde_clock")
    side = new_img(WOOD)
    fill_rect(side, 0, 0, 15, 15, WOOD_DK)
    save(side, "orde_clock_side")


def tex_crooked_painting():
    img = new_img(WOOD_DK)
    # frame
    fill_rect(img, 0, 0, 15, 15, WOOD)
    fill_rect(img, 2, 2, 13, 13, (25, 28, 35, 255))
    # gloomy subject — off-kilter diagonal smear
    for i in range(10):
        px(img, 3 + i, 4 + i // 2, (60, 65, 80, 255))
        px(img, 4 + i, 5 + i // 2, (40, 42, 50, 255))
    fill_rect(img, 5, 8, 9, 11, (15, 15, 20, 255))
    # crooked: thicker bottom-left frame
    fill_rect(img, 0, 13, 4, 15, WOOD_HI)
    save(img, "crooked_painting")
    side = new_img(WOOD_DK)
    save(side, "crooked_painting_side")


def tex_orde_lamp():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # base
    fill_rect(img, 5, 10, 10, 15, WOOD_DK)
    fill_rect(img, 6, 11, 9, 14, WOOD)
    # shade (cold blue)
    fill_rect(img, 3, 2, 12, 9, (80, 140, 180, 200))
    fill_rect(img, 4, 3, 11, 8, BLUE)
    fill_rect(img, 5, 4, 10, 7, BLUE_CORE)
    px(img, 7, 5, (255, 255, 255, 255))
    save(img, "orde_lamp")


def tex_ceiling_recess():
    img = new_img(NEAR_BLACK)
    fill_rect(img, 0, 0, 15, 15, DARK)
    fill_rect(img, 4, 4, 11, 11, STONE)
    fill_rect(img, 5, 5, 10, 10, BLUE_CORE)
    fill_rect(img, 6, 6, 9, 9, (240, 245, 255, 255))
    save(img, "ceiling_recess")


def tex_cold_vault():
    img = new_img((40, 55, 70, 255))
    for y in range(16):
        for x in range(16):
            n = (x + y) % 4
            c = (50, 70, 90, 255) if n == 0 else (35, 50, 65, 255) if n == 1 else (45, 62, 80, 255)
            px(img, x, y, c)
    save(img, "cold_vault")


def tex_ruit_heart():
    img = new_img(INK)
    for y in range(16):
        for x in range(16):
            d = abs(x - 8) + abs(y - 8)
            if d < 3:
                px(img, x, y, BLUE_CORE)
            elif d < 5:
                px(img, x, y, BLUE)
            elif d < 7:
                px(img, x, y, (40, 20, 50, 255))
            else:
                px(img, x, y, NEAR_BLACK)
    save(img, "ruit_heart")


def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")
    print("json", path.relative_to(ROOT))


def cube_all(name: str):
    write_json(BS / f"{name}.json", {"variants": {"": {"model": f"millenaire:block/{name}"}}})
    write_json(MB / f"{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"millenaire:block/{name}"}})
    write_json(MI / f"{name}.json", {"parent": f"millenaire:block/{name}"})
    write_loot(name)


def cube_bottom_top(name: str, top: str, side: str, bottom: str | None = None):
    bottom = bottom or top
    write_json(BS / f"{name}.json", {"variants": {"": {"model": f"millenaire:block/{name}"}}})
    write_json(
        MB / f"{name}.json",
        {
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {
                "top": f"millenaire:block/{top}",
                "bottom": f"millenaire:block/{bottom}",
                "side": f"millenaire:block/{side}",
            },
        },
    )
    write_json(MI / f"{name}.json", {"parent": f"millenaire:block/{name}"})
    write_loot(name)


def write_loot(name: str):
    write_json(
        LOOT / f"{name}.json",
        {
            "type": "minecraft:block",
            "pools": [
                {
                    "bonus_rolls": 0.0,
                    "conditions": [{"condition": "minecraft:survives_explosion"}],
                    "entries": [{"type": "minecraft:item", "name": f"millenaire:{name}"}],
                    "rolls": 1.0,
                }
            ],
            "random_sequence": f"millenaire:blocks/{name}",
        },
    )


def stairs_slab_wall(base: str):
    # stairs
    write_json(
        BS / f"{base}_stairs.json",
        {
            "variants": {
                "facing=east,half=bottom,shape=straight": {"model": f"millenaire:block/{base}_stairs", "y": 90},
                "facing=west,half=bottom,shape=straight": {"model": f"millenaire:block/{base}_stairs", "y": 270},
                "facing=south,half=bottom,shape=straight": {"model": f"millenaire:block/{base}_stairs", "y": 180},
                "facing=north,half=bottom,shape=straight": {"model": f"millenaire:block/{base}_stairs"},
                "facing=east,half=top,shape=straight": {"model": f"millenaire:block/{base}_stairs", "x": 180, "y": 90},
                "facing=west,half=top,shape=straight": {"model": f"millenaire:block/{base}_stairs", "x": 180, "y": 270},
                "facing=south,half=top,shape=straight": {"model": f"millenaire:block/{base}_stairs", "x": 180, "y": 180},
                "facing=north,half=top,shape=straight": {"model": f"millenaire:block/{base}_stairs", "x": 180},
                "facing=east,half=bottom,shape=outer_right": {"model": f"millenaire:block/{base}_stairs_outer", "y": 90},
                "facing=west,half=bottom,shape=outer_right": {"model": f"millenaire:block/{base}_stairs_outer", "y": 270},
                "facing=south,half=bottom,shape=outer_right": {"model": f"millenaire:block/{base}_stairs_outer", "y": 180},
                "facing=north,half=bottom,shape=outer_right": {"model": f"millenaire:block/{base}_stairs_outer"},
                "facing=east,half=bottom,shape=outer_left": {"model": f"millenaire:block/{base}_stairs_outer"},
                "facing=west,half=bottom,shape=outer_left": {"model": f"millenaire:block/{base}_stairs_outer", "y": 180},
                "facing=south,half=bottom,shape=outer_left": {"model": f"millenaire:block/{base}_stairs_outer", "y": 90},
                "facing=north,half=bottom,shape=outer_left": {"model": f"millenaire:block/{base}_stairs_outer", "y": 270},
                "facing=east,half=top,shape=outer_right": {"model": f"millenaire:block/{base}_stairs_outer", "x": 180, "y": 270},
                "facing=west,half=top,shape=outer_right": {"model": f"millenaire:block/{base}_stairs_outer", "x": 180, "y": 90},
                "facing=south,half=top,shape=outer_right": {"model": f"millenaire:block/{base}_stairs_outer", "x": 180},
                "facing=north,half=top,shape=outer_right": {"model": f"millenaire:block/{base}_stairs_outer", "x": 180, "y": 180},
                "facing=east,half=top,shape=outer_left": {"model": f"millenaire:block/{base}_stairs_outer", "x": 180, "y": 180},
                "facing=west,half=top,shape=outer_left": {"model": f"millenaire:block/{base}_stairs_outer", "x": 180},
                "facing=south,half=top,shape=outer_left": {"model": f"millenaire:block/{base}_stairs_outer", "x": 180, "y": 270},
                "facing=north,half=top,shape=outer_left": {"model": f"millenaire:block/{base}_stairs_outer", "x": 180, "y": 90},
                "facing=east,half=bottom,shape=inner_right": {"model": f"millenaire:block/{base}_stairs_inner", "y": 90},
                "facing=west,half=bottom,shape=inner_right": {"model": f"millenaire:block/{base}_stairs_inner", "y": 270},
                "facing=south,half=bottom,shape=inner_right": {"model": f"millenaire:block/{base}_stairs_inner", "y": 180},
                "facing=north,half=bottom,shape=inner_right": {"model": f"millenaire:block/{base}_stairs_inner"},
                "facing=east,half=bottom,shape=inner_left": {"model": f"millenaire:block/{base}_stairs_inner"},
                "facing=west,half=bottom,shape=inner_left": {"model": f"millenaire:block/{base}_stairs_inner", "y": 180},
                "facing=south,half=bottom,shape=inner_left": {"model": f"millenaire:block/{base}_stairs_inner", "y": 90},
                "facing=north,half=bottom,shape=inner_left": {"model": f"millenaire:block/{base}_stairs_inner", "y": 270},
                "facing=east,half=top,shape=inner_right": {"model": f"millenaire:block/{base}_stairs_inner", "x": 180, "y": 270},
                "facing=west,half=top,shape=inner_right": {"model": f"millenaire:block/{base}_stairs_inner", "x": 180, "y": 90},
                "facing=south,half=top,shape=inner_right": {"model": f"millenaire:block/{base}_stairs_inner", "x": 180},
                "facing=north,half=top,shape=inner_right": {"model": f"millenaire:block/{base}_stairs_inner", "x": 180, "y": 180},
                "facing=east,half=top,shape=inner_left": {"model": f"millenaire:block/{base}_stairs_inner", "x": 180, "y": 180},
                "facing=west,half=top,shape=inner_left": {"model": f"millenaire:block/{base}_stairs_inner", "x": 180},
                "facing=south,half=top,shape=inner_left": {"model": f"millenaire:block/{base}_stairs_inner", "x": 180, "y": 270},
                "facing=north,half=top,shape=inner_left": {"model": f"millenaire:block/{base}_stairs_inner", "x": 180, "y": 90},
            }
        },
    )
    for kind, parent in [
        ("", "stairs"),
        ("_inner", "inner_stairs"),
        ("_outer", "outer_stairs"),
    ]:
        write_json(
            MB / f"{base}_stairs{kind}.json",
            {
                "parent": f"minecraft:block/{parent}",
                "textures": {
                    "bottom": f"millenaire:block/{base}",
                    "top": f"millenaire:block/{base}",
                    "side": f"millenaire:block/{base}",
                },
            },
        )
    write_json(MI / f"{base}_stairs.json", {"parent": f"millenaire:block/{base}_stairs"})
    write_loot(f"{base}_stairs")
    # slab
    write_json(
        BS / f"{base}_slab.json",
        {
            "variants": {
                "type=bottom": {"model": f"millenaire:block/{base}_slab"},
                "type=top": {"model": f"millenaire:block/{base}_slab_top"},
                "type=double": {"model": f"millenaire:block/{base}"},
            }
        },
    )
    write_json(
        MB / f"{base}_slab.json",
        {
            "parent": "minecraft:block/slab",
            "textures": {
                "bottom": f"millenaire:block/{base}",
                "top": f"millenaire:block/{base}",
                "side": f"millenaire:block/{base}",
            },
        },
    )
    write_json(
        MB / f"{base}_slab_top.json",
        {
            "parent": "minecraft:block/slab_top",
            "textures": {
                "bottom": f"millenaire:block/{base}",
                "top": f"millenaire:block/{base}",
                "side": f"millenaire:block/{base}",
            },
        },
    )
    write_json(MI / f"{base}_slab.json", {"parent": f"millenaire:block/{base}_slab"})
    write_loot(f"{base}_slab")


def bars_assets(name: str, pane: str, edge: str):
    # copy wooden_bars multipart structure
    write_json(
        BS / f"{name}.json",
        json.loads((BS / "wooden_bars.json").read_text().replace("wooden_bars", name)),
    )
    for part, parent in [
        ("post", "template_glass_pane_post"),
        ("side", "template_glass_pane_side"),
        ("side_alt", "template_glass_pane_side_alt"),
        ("noside", "template_glass_pane_noside"),
        ("noside_alt", "template_glass_pane_noside_alt"),
    ]:
        write_json(
            MB / f"{name}_{part}.json",
            {
                "parent": f"minecraft:block/{parent}",
                "render_type": "minecraft:cutout",
                "textures": {"pane": f"millenaire:block/{pane}", "edge": f"millenaire:block/{edge}"},
            },
        )
    write_json(MI / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"millenaire:block/{pane}"}})
    write_loot(name)


def facing_block(name: str, texture: str):
    variants = {}
    for facing, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]:
        variants[f"facing={facing}"] = {"model": f"millenaire:block/{name}", "y": y}
    write_json(BS / f"{name}.json", {"variants": variants})
    write_json(
        MB / f"{name}.json",
        {
            "parent": "minecraft:block/orientable",
            "textures": {
                "top": f"millenaire:block/{texture}_side",
                "front": f"millenaire:block/{texture}",
                "side": f"millenaire:block/{texture}_side",
            },
        },
    )
    write_json(MI / f"{name}.json", {"parent": f"millenaire:block/{name}"})
    write_loot(name)


def main():
    # textures
    tex_orde_stone()
    tex_orde_panel()
    tex_orde_tile()
    tex_bleed_planks()
    tex_orde_bricks()
    tex_orde_pillar()
    tex_orde_rune()
    tex_orde_sigil()
    tex_orde_script()
    tex_riveted_stone()
    tex_crimson_band()
    tex_bound_leather()
    tex_nail_studded()
    tex_strap_block()
    tex_silk_veil()
    tex_orde_bars()
    tex_orde_grate()
    tex_spike_floor()
    tex_spike_wall()
    tex_hook_block()
    tex_offering_bowl()
    tex_orde_altar()
    tex_orde_brazier()
    tex_orde_lantern()
    tex_void_sconce()
    tex_bleed_wallpaper()
    tex_cold_vault()
    tex_ruit_heart()
    tex_chevron_tile()
    tex_fluted_pillar()
    tex_pillar_base()
    tex_orde_coffer()
    tex_portal_frame()
    tex_void_gate()
    tex_hanging_hook()
    tex_orde_censer()
    tex_orde_barricade()
    tex_orde_bedding()
    tex_orde_nightstand()
    tex_orde_clock()
    tex_crooked_painting()
    tex_orde_lamp()
    tex_ceiling_recess()
    # Wellgrid v3 extras are generated by the labyrinth wellgrid pass if missing;
    # keep cube registration hooks for regenerated catalogs.
    if not (TEX / "orde_arch.png").exists():
        tex_orde_stone()  # placeholder; prefer committed wellgrid textures
    if (TEX / "orde_arch.png").exists():
        cube_all("orde_arch")
    if (TEX / "void_beam.png").exists():
        cube_all("void_beam")

    # simple cubes
    for n in [
        "orde_stone", "orde_panel", "orde_tile", "bleed_planks", "orde_bricks",
        "orde_pillar", "orde_rune", "orde_sigil", "orde_script", "riveted_stone",
        "crimson_band", "bound_leather", "nail_studded", "strap_block",
        "orde_grate", "spike_wall", "cold_vault", "ruit_heart", "bleed_wallpaper",
        "void_sconce", "chevron_tile", "fluted_pillar", "pillar_base", "orde_coffer",
        "portal_frame", "void_gate", "orde_barricade", "orde_bedding", "orde_nightstand",
        "ceiling_recess",
    ]:
        cube_all(n)

    stairs_slab_wall("orde_stone")
    stairs_slab_wall("orde_panel")
    stairs_slab_wall("bleed_planks")
    # slab only for tile
    write_json(
        BS / "orde_tile_slab.json",
        {
            "variants": {
                "type=bottom": {"model": "millenaire:block/orde_tile_slab"},
                "type=top": {"model": "millenaire:block/orde_tile_slab_top"},
                "type=double": {"model": "millenaire:block/orde_tile"},
            }
        },
    )
    for suffix, parent in [("", "slab"), ("_top", "slab_top")]:
        write_json(
            MB / f"orde_tile_slab{suffix if suffix != '' else ''}.json".replace("orde_tile_slab.json", "orde_tile_slab.json"),
            {
                "parent": f"minecraft:block/{parent}",
                "textures": {
                    "bottom": "millenaire:block/orde_tile",
                    "top": "millenaire:block/orde_tile",
                    "side": "millenaire:block/orde_tile",
                },
            },
        )
    write_json(
        MB / "orde_tile_slab.json",
        {
            "parent": "minecraft:block/slab",
            "textures": {"bottom": "millenaire:block/orde_tile", "top": "millenaire:block/orde_tile", "side": "millenaire:block/orde_tile"},
        },
    )
    write_json(
        MB / "orde_tile_slab_top.json",
        {
            "parent": "minecraft:block/slab_top",
            "textures": {"bottom": "millenaire:block/orde_tile", "top": "millenaire:block/orde_tile", "side": "millenaire:block/orde_tile"},
        },
    )
    write_json(MI / "orde_tile_slab.json", {"parent": "millenaire:block/orde_tile_slab"})
    write_loot("orde_tile_slab")

    bars_assets("orde_bars", "orde_bars", "orde_bars_top")
    bars_assets("silk_veil", "silk_veil", "silk_veil_edge")

    cube_bottom_top("spike_floor", "spike_floor_top", "spike_floor_side", "spike_floor_side")
    facing_block("hook_block", "hook_block")
    # spike_wall also facing? concept is on faces - cube_all is fine for all sides spiked
    cube_bottom_top("orde_altar", "orde_altar_top", "orde_altar_side", "orde_altar_top")
    cube_bottom_top("offering_bowl", "offering_bowl_top", "offering_bowl_side", "orde_stone")
    cube_bottom_top("orde_brazier", "orde_brazier_top", "orde_brazier", "orde_stone")

    write_json(BS / "orde_lantern.json", {"variants": {"": {"model": "millenaire:block/orde_lantern"}}})
    write_json(
        MB / "orde_lantern.json",
        {
            "parent": "minecraft:block/block",
            "render_type": "minecraft:cutout",
            "textures": {"particle": "millenaire:block/orde_lantern", "lantern": "millenaire:block/orde_lantern"},
            "elements": [
                {
                    "from": [5, 1, 5],
                    "to": [11, 11, 11],
                    "faces": {
                        "north": {"uv": [5, 5, 11, 15], "texture": "#lantern"},
                        "south": {"uv": [5, 5, 11, 15], "texture": "#lantern"},
                        "west": {"uv": [5, 5, 11, 15], "texture": "#lantern"},
                        "east": {"uv": [5, 5, 11, 15], "texture": "#lantern"},
                        "up": {"uv": [5, 0, 11, 5], "texture": "#lantern"},
                        "down": {"uv": [5, 11, 11, 15], "texture": "#lantern"},
                    },
                }
            ],
        },
    )
    write_json(MI / "orde_lantern.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "millenaire:block/orde_lantern"}})
    write_loot("orde_lantern")

    facing_block("orde_clock", "orde_clock")
    facing_block("crooked_painting", "crooked_painting")

    # cutout hanging props
    for name in ("hanging_hook", "orde_censer", "orde_lamp"):
        write_json(BS / f"{name}.json", {"variants": {"": {"model": f"millenaire:block/{name}"}}})
        write_json(
            MB / f"{name}.json",
            {
                "parent": "minecraft:block/cross",
                "render_type": "minecraft:cutout",
                "textures": {"cross": f"millenaire:block/{name}"},
            },
        )
        write_json(MI / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"millenaire:block/{name}"}})
        write_loot(name)

    print("done")


if __name__ == "__main__":
    main()
