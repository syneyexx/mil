#!/usr/bin/env python3
"""Generate concept-aligned bounty_hunter albedo + emissive textures (256x256)."""

from __future__ import annotations

import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/millenaire/textures/entity"

# Concept palette
LEATHER_DK = (42, 29, 21, 255)
LEATHER = (94, 68, 56, 255)
LEATHER_HI = (140, 118, 102, 255)
STEEL = (110, 112, 117, 255)
STEEL_DK = (70, 72, 76, 255)
STEEL_HI = (160, 162, 168, 255)
WOOD = (61, 43, 35, 255)
CRIMSON = (122, 38, 33, 255)
CRIMSON_DK = (80, 24, 22, 255)
AMBER = (255, 158, 27, 255)
AMBER_HOT = (255, 200, 90, 255)
GOLD = (188, 150, 70, 255)
BLACK = (10, 8, 7, 255)
ROPE = (150, 120, 80, 255)


def clamp(v: int) -> int:
    return max(0, min(255, v))


def noise_rect(img: Image.Image, box, base, variance=10, seed=1):
    rng = random.Random(seed)
    x0, y0, x1, y1 = box
    px = img.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            r = clamp(base[0] + rng.randint(-variance, variance))
            g = clamp(base[1] + rng.randint(-variance, variance))
            b = clamp(base[2] + rng.randint(-variance, variance))
            px[x, y] = (r, g, b, 255)


def fill(draw: ImageDraw.ImageDraw, box, color):
    draw.rectangle(box, fill=color)


def stripe(draw, x0, y0, x1, y1, color, step=3):
    for y in range(y0, y1, step):
        draw.line([(x0, y), (x1, y)], fill=color, width=1)


def draw_cross(draw, cx, cy, size, color):
    draw.rectangle((cx - 1, cy - size, cx + 1, cy + size), fill=color)
    draw.rectangle((cx - size, cy - 1, cx + size, cy + 1), fill=color)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    albedo = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    em = Image.new("RGBA", (256, 256), (0, 0, 0, 255))
    d = ImageDraw.Draw(albedo)
    e = ImageDraw.Draw(em)

    # --- Mask / face (0,0 32x32) ---
    noise_rect(albedo, (0, 0, 32, 32), STEEL_DK, 6, 11)
    fill(d, (3, 4, 29, 30), STEEL)
    # vertical cage bars
    for x in (8, 12, 16, 20, 24):
        fill(d, (x, 8, x + 2, 26), BLACK)
    # brow ridge
    fill(d, (5, 6, 27, 10), STEEL_HI)

    # --- Hat (32,0 96x48) ---
    noise_rect(albedo, (32, 0, 128, 48), LEATHER, 12, 22)
    fill(d, (48, 2, 112, 18), LEATHER_DK)  # crown
    fill(d, (40, 16, 120, 22), CRIMSON_DK)  # band
    fill(d, (36, 22, 124, 46), LEATHER)  # brim
    stripe(d, 36, 24, 124, 46, LEATHER_HI, 5)
    # wear marks
    fill(d, (50, 8, 58, 14), LEATHER_HI)
    fill(d, (90, 10, 100, 16), LEATHER_DK)

    # --- Scarf / badge (128,0) ---
    noise_rect(albedo, (128, 0, 176, 32), CRIMSON, 8, 33)
    fill(d, (132, 4, 148, 20), GOLD)
    draw_cross(d, 140, 12, 5, STEEL_HI)
    fill(d, (150, 6, 172, 28), CRIMSON_DK)

    # --- Torso / armor / coat upper (0,48) ---
    noise_rect(albedo, (0, 48, 96, 96), LEATHER_DK, 10, 44)
    fill(d, (8, 52, 88, 90), LEATHER)
    # chest straps
    d.line([(20, 56), (76, 84)], fill=LEATHER_DK, width=3)
    d.line([(76, 56), (20, 84)], fill=LEATHER_DK, width=3)
    # pauldrons
    fill(d, (0, 48, 18, 70), STEEL_DK)
    fill(d, (78, 48, 96, 70), STEEL_DK)
    fill(d, (2, 50, 16, 58), STEEL)
    fill(d, (80, 50, 94, 58), STEEL)
    # rivets
    for x, y in ((6, 54), (12, 54), (84, 54), (90, 54), (40, 60), (56, 60)):
        fill(d, (x, y, x + 2, y + 2), STEEL_HI)

    # --- Belt / pouches (96,48) ---
    noise_rect(albedo, (96, 48, 160, 80), WOOD, 7, 55)
    fill(d, (96, 54, 160, 62), LEATHER)
    fill(d, (100, 62, 118, 76), LEATHER_DK)
    fill(d, (122, 62, 140, 76), LEATHER_DK)
    fill(d, (144, 62, 156, 74), STEEL)

    # --- Coat panels (0,96) ---
    noise_rect(albedo, (0, 96, 128, 144), LEATHER, 14, 66)
    # frayed hems
    for x in range(0, 128, 3):
        h = 140 + (x * 7 % 5)
        fill(d, (x, h, x + 2, 144), LEATHER_DK)
    # back crimson banner + white-ish cross
    fill(d, (40, 100, 56, 138), CRIMSON)
    fill(d, (42, 102, 54, 136), CRIMSON_DK)
    draw_cross(d, 48, 118, 6, LEATHER_HI)
    # stitching
    stripe(d, 2, 98, 126, 140, LEATHER_HI, 6)

    # --- Arms / bracers (0,144) ---
    noise_rect(albedo, (0, 144, 128, 192), LEATHER, 8, 77)
    fill(d, (8, 164, 40, 184), STEEL)
    fill(d, (40, 164, 72, 184), STEEL)
    fill(d, (12, 168, 36, 172), STEEL_HI)
    fill(d, (44, 168, 68, 172), STEEL_HI)
    fill(d, (80, 176, 112, 188), LEATHER_DK)  # hands

    # --- Legs / boots (128,144) ---
    noise_rect(albedo, (128, 144, 224, 192), LEATHER_DK, 8, 88)
    fill(d, (128, 168, 192, 190), BLACK)
    fill(d, (132, 170, 188, 176), STEEL_DK)  # toe caps
    fill(d, (140, 180, 180, 188), STEEL)

    # --- Equipment (0,192) rope/cuffs ---
    noise_rect(albedo, (0, 192, 96, 240), ROPE, 6, 99)
    fill(d, (16, 196, 48, 220), STEEL)
    fill(d, (20, 200, 28, 228), STEEL_DK)
    fill(d, (36, 200, 44, 228), STEEL_DK)
    fill(d, (52, 196, 84, 230), ROPE)

    # --- Lantern / bola (96,192) ---
    noise_rect(albedo, (96, 192, 160, 240), STEEL_DK, 5, 111)
    fill(d, (100, 196, 124, 204), STEEL)
    fill(d, (104, 204, 120, 224), AMBER)  # glass (albedo hint; glow on emissive)
    fill(d, (100, 224, 124, 232), STEEL)
    fill(d, (128, 208, 156, 236), WOOD)
    fill(d, (132, 228, 152, 238), STEEL)

    # subtle overall grit
    grit = albedo.filter(ImageFilter.SMOOTH_MORE)
    albedo = Image.blend(albedo, grit, 0.25)

    # --- Emissive islands only ---
    # Eyes
    fill(e, (200, 40, 208, 48), AMBER)
    fill(e, (202, 42, 206, 46), AMBER_HOT)
    fill(e, (208, 40, 216, 48), AMBER)
    fill(e, (210, 42, 214, 46), AMBER_HOT)
    # Lantern
    fill(e, (200, 52, 216, 68), AMBER)
    fill(e, (204, 56, 212, 64), AMBER_HOT)
    # Badge tip
    fill(e, (220, 52, 230, 62), (180, 100, 30, 255))

    albedo_path = OUT / "bounty_hunter.png"
    em_path = OUT / "bounty_hunter_emissive.png"
    albedo.save(albedo_path)
    em.save(em_path)
    print(f"Wrote {albedo_path}")
    print(f"Wrote {em_path}")


if __name__ == "__main__":
    main()
