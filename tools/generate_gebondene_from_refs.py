#!/usr/bin/env python3
"""Generate Blockbench-style NativeMonster JSON models + textures for all 25 Gebondenen.

Silhouettes follow the mod-ready ref charts in /opt/cursor/artifacts/assets/gebondenen-modready-*.jpg
Units: 16 = 1 Minecraft block. Feet at y=0.
"""
from __future__ import annotations

import json
import math
import os
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
MODEL_DIR = ROOT / "src/main/resources/assets/millenaire/models/entity/gebondene"
TEX_DIR = ROOT / "src/main/resources/assets/millenaire/textures/entity/gebondene"
TEX = 128

# Material UV tiles (8x8 cells on 128 atlas) — painted below
MAT = {
    "leather": (0, 0),
    "brass": (16, 0),
    "flesh": (32, 0),
    "black": (48, 0),
    "porcelain": (64, 0),
    "iron": (80, 0),
    "blood": (96, 0),
    "parchment": (112, 0),
    "maroon": (0, 16),
    "mirror": (16, 16),
    "wood": (32, 16),
    "bone": (48, 16),
    "glow": (64, 16),
    "purple": (80, 16),
    "eye": (96, 16),
    "ink": (112, 16),
}

COLORS = {
    "leather": [(62, 38, 24), (48, 28, 18), (78, 48, 30)],
    "brass": [(168, 130, 58), (140, 105, 42), (196, 160, 78)],
    "flesh": [(210, 190, 170), (190, 168, 148), (170, 140, 120)],
    "black": [(18, 16, 18), (28, 24, 28), (10, 10, 12)],
    "porcelain": [(230, 220, 205), (200, 190, 175), (160, 150, 140)],
    "iron": [(70, 60, 55), (90, 70, 55), (50, 45, 40)],
    "blood": [(90, 18, 18), (120, 28, 24), (60, 12, 12)],
    "parchment": [(196, 170, 120), (170, 145, 100), (140, 118, 80)],
    "maroon": [(90, 30, 40), (70, 22, 32), (110, 40, 50)],
    "mirror": [(200, 180, 90), (160, 150, 120), (230, 210, 140)],
    "wood": [(110, 75, 40), (90, 60, 30), (130, 90, 50)],
    "bone": [(220, 210, 185), (190, 180, 155), (170, 160, 140)],
    "glow": [(255, 210, 90), (255, 170, 40), (255, 240, 160)],
    "purple": [(70, 35, 70), (50, 25, 55), (95, 50, 90)],
    "eye": [(220, 230, 240), (40, 80, 120), (20, 20, 25)],
    "ink": [(30, 30, 40), (50, 50, 60), (15, 15, 20)],
}


def paint_atlas() -> Image.Image:
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(42)
    for name, (ux, uy) in MAT.items():
        cols = COLORS[name]
        for y in range(16):
            for x in range(16):
                c = cols[rng.randrange(len(cols))]
                # subtle noise / cracks
                n = rng.randint(-12, 12)
                col = (
                    max(0, min(255, c[0] + n)),
                    max(0, min(255, c[1] + n)),
                    max(0, min(255, c[2] + n)),
                    255,
                )
                if name == "porcelain" and (x + y) % 11 == 0:
                    col = (120, 110, 100, 255)
                if name == "leather" and y % 4 == 0:
                    col = (max(0, col[0] - 20), max(0, col[1] - 15), max(0, col[2] - 10), 255)
                if name == "mirror" and (x ^ y) & 3 == 0:
                    col = (240, 230, 180, 255)
                if name == "eye":
                    cx, cy = 7.5, 7.5
                    d = math.hypot(x - cx, y - cy)
                    if d < 3:
                        col = (30, 30, 35, 255)
                    elif d < 6:
                        col = (50, 90, 140, 255)
                    else:
                        col = (220, 225, 230, 255)
                px[ux + x, uy + y] = col
    # kind-specific overlays painted later into lower half
    return img


def face_uv(mat: str, face: str) -> list[float]:
    ux, uy = MAT[mat]
    # slight per-face offset inside 16x16 tile
    offs = {"north": 0, "south": 2, "east": 4, "west": 6, "up": 8, "down": 10}
    o = offs.get(face, 0)
    return [ux + o % 4, uy + (o // 4) * 2, ux + 8 + o % 4, uy + 8 + (o // 4) * 2]


def cube(name, bone, frm, to, mat="leather", origin=None, rotation=None, emissive=False):
    ox = (frm[0] + to[0]) / 2
    oy = (frm[1] + to[1]) / 2
    oz = (frm[2] + to[2]) / 2
    if origin is None:
        origin = [ox, oy, oz]
    if rotation is None:
        rotation = [0, 0, 0]
    faces = {}
    for f in ("north", "south", "east", "west", "up", "down"):
        faces[f] = {"uv": face_uv(mat, f), "texture": 0}
    return {
        "name": name,
        "type": "cube",
        "from": [round(v, 3) for v in frm],
        "to": [round(v, 3) for v in to],
        "origin": [round(v, 3) for v in origin],
        "rotation": rotation,
        "faces": faces,
        "_bone": bone,
        "_material": mat,
        "_emissive": emissive,
    }


def bone(name, parent, origin, rotation=None):
    return {
        "name": name,
        "parent": parent,
        "origin": origin,
        "rotation": rotation or [0, 0, 0],
    }


def clips_basic(leg_l="left_leg", leg_r="right_leg", arm_l="left_arm", arm_r="right_arm"):
    return {
        "idle": {
            "length": 2.0,
            "loop": True,
            "bones": {
                "body": {
                    "position": [[0, [0, 0, 0]], [1, [0, 0.4, 0]], [2, [0, 0, 0]]],
                }
            },
        },
        "walk": {
            "length": 1.0,
            "loop": True,
            "bones": {
                leg_l: {"rotation": [[0, [20, 0, 0]], [0.5, [-20, 0, 0]], [1, [20, 0, 0]]]},
                leg_r: {"rotation": [[0, [-20, 0, 0]], [0.5, [20, 0, 0]], [1, [-20, 0, 0]]]},
                arm_l: {"rotation": [[0, [-15, 0, 0]], [0.5, [15, 0, 0]], [1, [-15, 0, 0]]]},
                arm_r: {"rotation": [[0, [15, 0, 0]], [0.5, [-15, 0, 0]], [1, [15, 0, 0]]]},
            },
        },
        "hurt": {
            "length": 0.4,
            "loop": False,
            "bones": {"body": {"rotation": [[0, [0, 0, 0]], [0.1, [8, 0, 0]], [0.4, [0, 0, 0]]]}},
        },
    }


def humanoid_bones(h: float):
    """Standard biped pivots for height h (blocks)."""
    u = h * 16
    hip = u * 0.45
    shoulder = u * 0.72
    head_y = u * 0.88
    return [
        bone("root", None, [0, 0, 0]),
        bone("body", "root", [0, hip, 0]),
        bone("head", "body", [0, head_y, 0]),
        bone("left_arm", "body", [5, shoulder, 0]),
        bone("right_arm", "body", [-5, shoulder, 0]),
        bone("left_leg", "root", [2, hip, 0]),
        bone("right_leg", "root", [-2, hip, 0]),
    ], u, hip, shoulder, head_y


def base_body(els, u, hip, shoulder, head_y, torso_mat="leather", flesh="flesh", leg_mat=None):
    leg_mat = leg_mat or torso_mat
    # legs
    els.append(cube("ll", "left_leg", [0.5, 0, -2], [3.5, hip, 2], leg_mat))
    els.append(cube("rl", "right_leg", [-3.5, 0, -2], [-0.5, hip, 2], leg_mat))
    # torso
    els.append(cube("torso", "body", [-4, hip, -2.5], [4, shoulder, 2.5], torso_mat))
    # arms
    els.append(cube("la", "left_arm", [4, hip + 2, -2], [7, shoulder + 1, 2], flesh))
    els.append(cube("ra", "right_arm", [-7, hip + 2, -2], [-4, shoulder + 1, 2], flesh))
    # head base
    els.append(cube("hd", "head", [-3.5, head_y, -3.5], [3.5, head_y + 7, 3.5], flesh))


# ---- Per-kind builders matching modready charts ----

def build_spijkermeester():
    h = 2.5
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    base_body(els, u, hip, shoulder, head_y, "leather", "flesh")
    # full brass lattice helm (chart: grid of holes over whole face)
    els.append(cube("mask", "head", [-4.5, head_y - 0.5, -4.5], [4.5, head_y + 8.5, 4.0], "brass"))
    for i in range(4):
        for j in range(4):
            els.append(cube(f"lat{i}{j}", "head",
                            [-3.2 + i * 1.7, head_y + 0.8 + j * 1.7, -4.9],
                            [-2.2 + i * 1.7, head_y + 1.6 + j * 1.7, -4.4], "black"))
    for y in (hip + 2, hip + 6, hip + 10, hip + 14):
        els.append(cube(f"strap{y}", "body", [-4.5, y, -3.0], [4.5, y + 1.4, 3.0], "black"))
    els.append(cube("hookL", "body", [-6.5, hip, -1.2], [-4.5, hip + 6, 1.2], "iron"))
    els.append(cube("hookR", "body", [4.5, hip, -1.2], [6.5, hip + 6, 1.2], "iron"))
    els.append(cube("cube", "left_arm", [5, hip, -2], [9, hip + 4, 2], "brass", emissive=True))
    els.append(cube("chain", "body", [-1.5, 0, 2.8], [1.5, hip + 3, 4.5], "iron"))
    return groups, els, h


def build_rammelaar():
    h = 2.2
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    base_body(els, u, hip, shoulder, head_y, "leather", "flesh")
    # external brass jaw cage on lower face
    els.append(cube("jaw", "head", [-3.5, head_y, -4.5], [3.5, head_y + 3.5, -2.5], "brass"))
    for i in range(5):
        els.append(cube(f"tooth{i}", "head",
                        [-2.8 + i * 1.2, head_y + 0.3, -5.0],
                        [-2.0 + i * 1.2, head_y + 2.8, -4.3], "bone"))
    # chain-whip arms ending in hooks
    els.append(cube("chL", "left_arm", [5, 2, -1], [6.5, hip + 2, 1], "iron"))
    els.append(cube("hkL", "left_arm", [5, 0, -1.5], [7, 2.5, 1.5], "iron"))
    els.append(cube("chR", "right_arm", [-6.5, 2, -1], [-5, hip + 2, 1], "iron"))
    els.append(cube("hkR", "right_arm", [-7, 0, -1.5], [-5, 2.5, 1.5], "iron"))
    # sutures on torso
    els.append(cube("sut", "body", [-0.4, hip + 2, -2.7], [0.4, shoulder - 1, -2.4], "blood"))
    return groups, els, h


def build_stille():
    h = 2.0
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    base_body(els, u, hip, shoulder, head_y, "black", "flesh", "black")
    # hood + slit mask
    els.append(cube("hood", "head", [-4, head_y - 1, -4], [4, head_y + 8, 4], "black"))
    els.append(cube("mask", "head", [-2.5, head_y + 2, -4.3], [2.5, head_y + 6, -3.5], "iron"))
    els.append(cube("slitL", "head", [-1.8, head_y + 3.5, -4.5], [-0.6, head_y + 5.2, -4.2], "black"))
    els.append(cube("slitR", "head", [0.6, head_y + 3.5, -4.5], [1.8, head_y + 5.2, -4.2], "black"))
    # fingertip hooks / blades
    els.append(cube("bladeL", "left_arm", [6.5, hip, -0.5], [9, hip + 4, 0.5], "iron"))
    els.append(cube("bladeR", "right_arm", [-9, hip, -0.5], [-6.5, hip + 4, 0.5], "iron"))
    # diagonal straps
    els.append(cube("dstrap", "body", [-4.2, hip + 3, -2.8], [4.2, hip + 5, 2.8], "leather"))
    return groups, els, h


def build_architect():
    h = 3.0
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    els.append(cube("robe", "body", [-6, 0, -4], [6, shoulder + 1, 4], "black"))
    els.append(cube("ll", "left_leg", [0.5, 0, -2], [3.5, hip, 2], "black"))
    els.append(cube("rl", "right_leg", [-3.5, 0, -2], [-0.5, hip, 2], "black"))
    els.append(cube("la", "left_arm", [5, hip + 4, -2.5], [9, shoulder + 2, 2.5], "black"))
    els.append(cube("ra", "right_arm", [-9, hip + 4, -2.5], [-5, shoulder + 2, 2.5], "black"))
    # porcelain cracked grid face — oversized boss head
    els.append(cube("face", "head", [-5, head_y - 1, -5], [5, head_y + 10, 5], "porcelain"))
    for i in range(4):
        els.append(cube(f"crH{i}", "head", [-4.5, head_y + 1 + i * 2.2, -5.3], [4.5, head_y + 1.5 + i * 2.2, -4.7], "ink"))
        els.append(cube(f"crV{i}", "head", [-3.5 + i * 2.2, head_y, -5.3], [-3.0 + i * 2.2, head_y + 9, -4.7], "ink"))
    for i in range(8):
        els.append(cube(f"chain{i}", "body",
                        [-5 + i * 1.3, 2, 3.5],
                        [-3.8 + i * 1.3, shoulder + 2, 5.5], "iron"))
        els.append(cube(f"ring{i}", "body",
                        [-4.5 + i * 1.2, hip + 4 + (i % 3), 4.5],
                        [-3.2 + i * 1.2, hip + 6 + (i % 3), 5.8], "brass"))
    els.append(cube("stylus", "left_arm", [6, hip, -0.7], [7.5, shoulder + 6, 0.7], "iron"))
    els.append(cube("collar", "body", [-5.5, shoulder - 1, -3.5], [5.5, shoulder + 4, 3.5], "black"))
    return groups, els, h


def build_gids():
    h = 2.3
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    base_body(els, u, hip, shoulder, head_y, "leather", "flesh")
    # long coat
    els.append(cube("coat", "body", [-4.5, 2, -3], [4.5, shoulder, 3.2], "leather"))
    # half porcelain mask + suture
    els.append(cube("mask", "head", [-3.5, head_y + 1, -4.2], [3.5, head_y + 7, -3.2], "porcelain"))
    els.append(cube("sut", "head", [-0.3, head_y + 1, -4.3], [0.3, head_y + 7, -3.9], "blood"))
    # lantern (glow)
    els.append(cube("lantern", "left_arm", [5, hip + 1, -2], [8.5, hip + 5, 2], "iron"))
    els.append(cube("flame", "left_arm", [5.8, hip + 2, -1.2], [7.7, hip + 4.2, 1.2], "glow", emissive=True))
    return groups, els, h


def build_kettingzuster():
    h = 2.3
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    base_body(els, u, hip, shoulder, head_y, "maroon", "flesh", "maroon")
    # dress
    els.append(cube("dress", "body", [-4.5, 0, -3], [4.5, hip + 4, 3], "maroon"))
    # long hair
    els.append(cube("hair", "head", [-3.5, head_y + 4, -2], [3.5, head_y + 10, 4], "black"))
    els.append(cube("face", "head", [-3, head_y, -3.5], [3, head_y + 6.5, 3], "flesh"))
    # measuring chains silhouette
    for i in range(8):
        ang = i * 0.7
        x = math.sin(ang) * 5
        z = math.cos(ang) * 3
        els.append(cube(f"ch{i}", "body",
                        [x - 0.6, hip - 1 + (i % 3), z - 0.6],
                        [x + 0.6, shoulder - 2 + (i % 3), z + 0.6], "iron"))
    # finger hooks
    els.append(cube("fhL", "left_arm", [6.5, hip + 1, -0.5], [9, hip + 3, 0.5], "iron"))
    els.append(cube("fhR", "right_arm", [-9, hip + 1, -0.5], [-6.5, hip + 3, 0.5], "iron"))
    return groups, els, h


def build_spiegelgast():
    h = 2.4
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    # angular mirror-brass shards body
    els.append(cube("torso", "body", [-3.5, hip, -2], [3.5, shoulder, 2], "mirror"))
    els.append(cube("ll", "left_leg", [0.5, 0, -1.5], [3, hip, 1.5], "mirror"))
    els.append(cube("rl", "right_leg", [-3, 0, -1.5], [-0.5, hip, 1.5], "mirror"))
    els.append(cube("la", "left_arm", [3.5, hip + 2, -1.5], [6, shoulder, 1.5], "mirror"))
    els.append(cube("ra", "right_arm", [-6, hip + 2, -1.5], [-3.5, shoulder, 1.5], "mirror"))
    # faceless shard head
    els.append(cube("head1", "head", [-3, head_y, -3], [3, head_y + 7, 3], "mirror"))
    els.append(cube("shard1", "head", [-4, head_y + 2, -1], [-2.5, head_y + 6, 2], "mirror"))
    els.append(cube("shard2", "head", [2.5, head_y + 1, -2], [4.5, head_y + 5, 1], "mirror"))
    els.append(cube("shard3", "body", [-2, shoulder - 2, 2], [2, shoulder + 2, 4], "brass"))
    return groups, els, h


def build_poortwachter():
    h = 2.6
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    els.append(cube("body", "body", [-7, hip - 2, -5], [7, shoulder + 2, 5], "iron"))
    els.append(cube("woodL", "body", [-7.5, hip, -4], [-5.5, shoulder, 4], "wood"))
    els.append(cube("woodR", "body", [5.5, hip, -4], [7.5, shoulder, 4], "wood"))
    els.append(cube("ll", "left_leg", [0.5, 0, -3.5], [5.5, hip, 3.5], "iron"))
    els.append(cube("rl", "right_leg", [-5.5, 0, -3.5], [-0.5, hip, 3.5], "iron"))
    els.append(cube("la", "left_arm", [6, hip, -3.5], [10.5, shoulder + 1, 3.5], "wood"))
    els.append(cube("ra", "right_arm", [-10.5, hip, -3.5], [-6, shoulder + 1, 3.5], "wood"))
    # vault-door grate helm
    els.append(cube("helm", "head", [-6, head_y - 2, -6], [6, head_y + 10, 6], "iron"))
    for i in range(5):
        els.append(cube(f"grate{i}", "head",
                        [-4.5 + i * 1.9, head_y, -6.5],
                        [-3.5 + i * 1.9, head_y + 8, -5.7], "black"))
    els.append(cube("cage", "body", [-4, hip + 3, -5.5], [4, shoulder, -4.2], "iron"))
    els.append(cube("staff", "left_arm", [8, 0, -1.2], [10, shoulder + 6, 1.2], "brass"))
    els.append(cube("keybit", "left_arm", [7, shoulder + 3, -2.5], [11, shoulder + 7, 2.5], "brass"))
    return groups, els, h


def build_fluitende():
    h = 2.5
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    base_body(els, u, hip, shoulder, head_y, "leather", "bone")
    els.append(cube("coat", "body", [-4.5, 1, -3], [4.5, shoulder, 3], "leather"))
    # skull + organ pipes in throat
    els.append(cube("skull", "head", [-3.5, head_y, -3.5], [3.5, head_y + 7, 3.5], "bone"))
    for i in range(4):
        els.append(cube(f"pipe{i}", "head",
                        [-2.5 + i * 1.3, head_y - 3, -2],
                        [-1.5 + i * 1.3, head_y + 1, 0], "brass"))
    # bone flute
    els.append(cube("flute", "left_arm", [5, hip + 3, -0.5], [12, hip + 4.2, 0.5], "bone"))
    return groups, els, h


def build_naaister():
    h = 2.3
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    base_body(els, u, hip, shoulder, head_y, "leather", "flesh")
    els.append(cube("apron", "body", [-4.5, hip - 4, -3.2], [4.5, shoulder - 2, 3], "blood"))
    els.append(cube("hood", "head", [-4, head_y - 1, -4], [4, head_y + 8, 4], "black"))
    # suture smile
    els.append(cube("face", "head", [-3, head_y + 1, -4.1], [3, head_y + 6, -3.3], "flesh"))
    els.append(cube("smile", "head", [-2.5, head_y + 2, -4.3], [2.5, head_y + 3.2, -3.9], "blood"))
    # needle gauntlets
    els.append(cube("gauL", "left_arm", [4, hip + 1, -2.5], [8, shoulder, 2.5], "iron"))
    for i in range(3):
        els.append(cube(f"ndl{i}", "left_arm",
                        [7.5, hip + 1 + i * 1.2, -0.3],
                        [11, hip + 1.5 + i * 1.2, 0.3], "iron"))
    els.append(cube("gauR", "right_arm", [-8, hip + 1, -2.5], [-4, shoulder, 2.5], "iron"))
    # wire spools on hips
    els.append(cube("spL", "body", [3.5, hip, -2], [6.5, hip + 3, 2], "iron"))
    els.append(cube("spR", "body", [-6.5, hip, -2], [-3.5, hip + 3, 2], "iron"))
    return groups, els, h


def build_tweeling():
    h = 1.8
    # duo entity — two bodies under shared root
    groups = [
        bone("root", None, [0, 0, 0]),
        bone("body", "root", [0, h * 16 * 0.45, 0]),
        bone("head", "body", [-4, h * 16 * 0.85, 0]),
        bone("head2", "body", [4, h * 16 * 0.85, 0]),
        bone("left_arm", "body", [8, h * 16 * 0.7, 0]),
        bone("right_arm", "body", [-8, h * 16 * 0.7, 0]),
        bone("left_leg", "root", [5, h * 16 * 0.45, 0]),
        bone("right_leg", "root", [-5, h * 16 * 0.45, 0]),
    ]
    u = h * 16
    hip = u * 0.45
    head_y = u * 0.85
    els = []
    # left twin
    els.append(cube("tL", "body", [1, hip, -2], [6, hip + 10, 2], "leather"))
    els.append(cube("ll", "left_leg", [2, 0, -1.5], [5, hip, 1.5], "leather"))
    els.append(cube("hL", "head", [1.5, head_y, -3], [6.5, head_y + 6, 3], "porcelain"))
    # right twin
    els.append(cube("tR", "body", [-6, hip, -2], [-1, hip + 10, 2], "leather"))
    els.append(cube("rl", "right_leg", [-5, 0, -1.5], [-2, hip, 1.5], "leather"))
    els.append(cube("hR", "head2", [-6.5, head_y, -3], [-1.5, head_y + 6, 3], "porcelain"))
    # brass yoke linking heads
    els.append(cube("yoke", "body", [-5, head_y + 4, -1], [5, head_y + 6.5, 1], "brass"))
    els.append(cube("yokeCh", "body", [-1, head_y + 2, -0.5], [1, head_y + 5, 0.5], "iron"))
    # eye holes
    els.append(cube("eL1", "head", [2.5, head_y + 3, -3.3], [3.5, head_y + 4, -2.9], "black"))
    els.append(cube("eL2", "head", [4.5, head_y + 3, -3.3], [5.5, head_y + 4, -2.9], "black"))
    els.append(cube("eR1", "head2", [-5.5, head_y + 3, -3.3], [-4.5, head_y + 4, -2.9], "black"))
    els.append(cube("eR2", "head2", [-3.5, head_y + 3, -3.3], [-2.5, head_y + 4, -2.9], "black"))
    return groups, els, h


def build_echo():
    h = 2.1
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    # pale translucent body + ghost afterimage offset
    els.append(cube("torso", "body", [-3, hip, -2], [3, shoulder, 2], "porcelain"))
    els.append(cube("ll", "left_leg", [0.5, 0, -1.5], [2.5, hip, 1.5], "porcelain"))
    els.append(cube("rl", "right_leg", [-2.5, 0, -1.5], [-0.5, hip, 1.5], "porcelain"))
    els.append(cube("la", "left_arm", [3, hip + 2, -1.5], [5, shoulder, 1.5], "porcelain"))
    els.append(cube("ra", "right_arm", [-5, hip + 2, -1.5], [-3, shoulder, 1.5], "porcelain"))
    els.append(cube("hd", "head", [-3, head_y, -3], [3, head_y + 6.5, 3], "porcelain"))
    # seam smile
    els.append(cube("smile", "head", [-2.5, head_y + 1.5, -3.3], [2.5, head_y + 2.5, -2.9], "blood"))
    # straps
    els.append(cube("strap", "body", [-3.3, hip + 3, -2.3], [3.3, hip + 4.5, 2.3], "leather"))
    # faint afterimage mesh behind
    els.append(cube("ghost", "body", [-2.5, hip + 1, 3], [2.5, shoulder - 1, 5], "ink"))
    return groups, els, h


def build_gastheer():
    h = 2.05
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    base_body(els, u, hip, shoulder, head_y, "black", "flesh", "black")
    els.append(cube("coat", "body", [-4.8, 1, -3.2], [4.8, shoulder + 0.5, 3.2], "black"))
    els.append(cube("tailL", "body", [0.5, 0, 2], [4.5, hip + 3, 4.5], "black"))
    els.append(cube("tailR", "body", [-4.5, 0, 2], [-0.5, hip + 3, 4.5], "black"))
    # oversized porcelain smile-mask
    els.append(cube("mask", "head", [-4.2, head_y - 0.5, -4.8], [4.2, head_y + 8, -2.5], "porcelain"))
    els.append(cube("skull", "head", [-3.5, head_y, -2.5], [3.5, head_y + 7, 3.5], "flesh"))
    els.append(cube("eyesL", "head", [-3.0, head_y + 4.2, -5.0], [-1.0, head_y + 6.0, -4.5], "black"))
    els.append(cube("eyesR", "head", [1.0, head_y + 4.2, -5.0], [3.0, head_y + 6.0, -4.5], "black"))
    els.append(cube("grin", "head", [-3.2, head_y + 1.2, -5.0], [3.2, head_y + 3.0, -4.5], "blood"))
    els.append(cube("hookL", "head", [-5.5, head_y + 0.5, -4], [-4.0, head_y + 5, -1], "brass"))
    els.append(cube("hookR", "head", [4.0, head_y + 0.5, -4], [5.5, head_y + 5, -1], "brass"))
    return groups, els, h


def build_notaris():
    h = 1.95
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    base_body(els, u, hip, shoulder, head_y, "black", "flesh")
    els.append(cube("robe", "body", [-4.5, 0, -3], [4.5, shoulder, 3], "black"))
    # pin-studded face grid
    els.append(cube("face", "head", [-3.5, head_y, -3.5], [3.5, head_y + 7, 3.5], "flesh"))
    for i in range(3):
        for j in range(3):
            els.append(cube(f"pin{i}{j}", "head",
                            [-2.5 + i * 1.8, head_y + 1.2 + j * 1.8, -4.0],
                            [-1.8 + i * 1.8, head_y + 1.9 + j * 1.8, -3.3], "iron"))
    # quill + tablet
    els.append(cube("quill", "right_arm", [-6, hip + 4, -0.5], [-5, shoulder + 3, 0.5], "bone"))
    els.append(cube("tablet", "left_arm", [5, hip + 2, -2], [8, hip + 6, 0.5], "wood"))
    # wax seals / parchment on chest
    els.append(cube("parch", "body", [-3, hip + 5, -3.3], [3, hip + 9, -2.6], "parchment"))
    els.append(cube("wax", "body", [-1, hip + 6.5, -3.5], [1, hip + 8, -3.1], "blood"))
    return groups, els, h


def build_blinde():
    h = 1.9
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    base_body(els, u, hip, shoulder, head_y, "leather", "flesh")
    els.append(cube("coat", "body", [-4.5, 1, -3], [4.5, shoulder, 3], "leather"))
    # bandaged eyes
    els.append(cube("band", "head", [-3.8, head_y + 3, -4], [3.8, head_y + 6, 3.5], "parchment"))
    # ear trumpets (3 each side)
    for i in range(3):
        els.append(cube(f"earL{i}", "head",
                        [3.5, head_y + 2 + i * 1.5, -1 + i * 0.3],
                        [7.5, head_y + 3.5 + i * 1.5, 2 - i * 0.2], "brass"))
        els.append(cube(f"earR{i}", "head",
                        [-7.5, head_y + 2 + i * 1.5, -1 + i * 0.3],
                        [-3.5, head_y + 3.5 + i * 1.5, 2 - i * 0.2], "brass"))
    # cane
    els.append(cube("cane", "right_arm", [-5.5, 0, -0.5], [-4.5, shoulder, 0.5], "wood"))
    return groups, els, h


def build_smid():
    h = 2.1
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    # bulky riveted iron plates over flesh
    els.append(cube("torso", "body", [-5.5, hip - 1, -3.5], [5.5, shoulder + 1, 3.5], "iron"))
    els.append(cube("seam", "body", [-5.7, hip + 3, -3.7], [5.7, hip + 5, 3.7], "blood"))
    els.append(cube("ll", "left_leg", [0.5, 0, -2.5], [4.5, hip, 2.5], "iron"))
    els.append(cube("rl", "right_leg", [-4.5, 0, -2.5], [-0.5, hip, 2.5], "iron"))
    els.append(cube("la", "left_arm", [4.5, hip, -2.5], [8.5, shoulder, 2.5], "iron"))
    els.append(cube("ra", "right_arm", [-8.5, hip, -2.5], [-4.5, shoulder, 2.5], "iron"))
    els.append(cube("hd", "head", [-3, head_y, -3], [3, head_y + 6, 3], "flesh"))
    # rivets
    for i in range(4):
        els.append(cube(f"riv{i}", "body",
                        [-4 + i * 2.2, shoulder - 1, -3.8],
                        [-3 + i * 2.2, shoulder, -3.4], "brass"))
    # sledgehammer
    els.append(cube("haft", "right_arm", [-6, hip - 2, -0.6], [-5, shoulder + 2, 0.6], "wood"))
    els.append(cube("head", "right_arm", [-8, hip - 3, -2], [-3, hip - 0.5, 2], "iron"))
    return groups, els, h


def build_archivaris():
    h = 2.4
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    base_body(els, u, hip, shoulder, head_y, "black", "flesh")
    els.append(cube("robe", "body", [-4, 0, -3], [4, shoulder, 3], "black"))
    # mantle of books / parchment
    for i in range(5):
        els.append(cube(f"book{i}", "body",
                        [-5 + i * 0.3, hip + 2 + i * 0.5, 2.5],
                        [5 - i * 0.3, hip + 5 + i * 0.5, 4.5 + i * 0.2], "parchment"))
    # ink-streaked face
    els.append(cube("face", "head", [-3.5, head_y, -3.5], [3.5, head_y + 7, 3.5], "flesh"))
    els.append(cube("ink1", "head", [-1, head_y, -3.7], [0, head_y + 7, -3.3], "ink"))
    els.append(cube("ink2", "head", [1.5, head_y + 1, -3.7], [2.5, head_y + 6, -3.3], "ink"))
    return groups, els, h


def build_schatter():
    h = 2.0
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    base_body(els, u, hip, shoulder, head_y, "purple", "flesh")
    els.append(cube("coat", "body", [-4.5, 1, -3], [4.5, shoulder + 1, 3.2], "purple"))
    # monocle cone on left eye
    els.append(cube("mono", "head", [1, head_y + 3, -5.5], [3.5, head_y + 5.5, -3.5], "brass"))
    els.append(cube("lens", "head", [1.5, head_y + 3.5, -5.8], [3, head_y + 5, -5.3], "glow", emissive=True))
    # ritual scar grid on chest
    for i in range(3):
        els.append(cube(f"scar{i}", "body", [-3, hip + 4 + i * 1.5, -2.8], [3, hip + 4.4 + i * 1.5, -2.4], "blood"))
    # balance scale
    els.append(cube("scale", "right_arm", [-8, hip + 4, -2], [-4.5, hip + 6, 2], "brass"))
    return groups, els, h


def build_danseres():
    h = 2.0
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    base_body(els, u, hip, shoulder, head_y, "leather", "flesh")
    # geometric scars
    for i in range(4):
        els.append(cube(f"scar{i}", "body",
                        [-3 + (i % 2) * 2, hip + 3 + i, -2.7],
                        [-1 + (i % 2) * 2, hip + 3.4 + i, -2.3], "blood"))
    # ribbon leather strips hanging
    for i in range(6):
        els.append(cube(f"rib{i}", "body",
                        [-3 + i * 1.1, 0, -1],
                        [-2.2 + i * 1.1, hip + 2, 1], "leather"))
    # ankle rings
    els.append(cube("ankL", "left_leg", [0.3, 1, -2.3], [3.7, 2.2, 2.3], "brass"))
    els.append(cube("ankR", "right_leg", [-3.7, 1, -2.3], [-0.3, 2.2, 2.3], "brass"))
    # long hair
    els.append(cube("hair", "head", [-3.5, head_y + 3, -2], [3.5, head_y + 9, 4], "black"))
    return groups, els, h


def build_klokkenluider():
    h = 2.5
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    els.append(cube("robe", "body", [-5.5, 0, -4], [5.5, shoulder + 2, 4], "black"))
    els.append(cube("ll", "left_leg", [0.5, 0, -2], [4, hip, 2], "black"))
    els.append(cube("rl", "right_leg", [-4, 0, -2], [-0.5, hip, 2], "black"))
    els.append(cube("la", "left_arm", [4, hip, -2.5], [8, shoulder, 2.5], "black"))
    els.append(cube("ra", "right_arm", [-8, hip, -2.5], [-4, shoulder, 2.5], "black"))
    # hood with gear face
    els.append(cube("hood", "head", [-5, head_y - 1, -5], [5, head_y + 9, 5], "black"))
    els.append(cube("gear", "head", [-3, head_y + 1, -5.2], [3, head_y + 7, -3.5], "brass"))
    # clockwork chest gear
    els.append(cube("chestG", "body", [-3, hip + 5, -4.5], [3, shoulder - 1, -3.2], "brass"))
    # hanging bells
    for i, x in enumerate((-4, 0, 4)):
        els.append(cube(f"bell{i}", "body", [x - 1.5, 1, 3], [x + 1.5, 5, 6], "brass"))
    els.append(cube("handbell", "right_arm", [-7, hip + 1, -1.5], [-4, hip + 5, 1.5], "brass"))
    return groups, els, h


def build_marionet():
    h = 1.9
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    base_body(els, u, hip, shoulder, head_y, "wood", "porcelain")
    # joint pegs
    for bone_name, y in (("left_arm", shoulder), ("right_arm", shoulder), ("left_leg", hip), ("right_leg", hip)):
        els.append(cube(f"peg_{bone_name}", bone_name, [-1, y - 1, -1], [1, y + 1, 1], "brass"))
    # painted doll face
    els.append(cube("face", "head", [-3.5, head_y, -3.8], [3.5, head_y + 7, 3.5], "porcelain"))
    els.append(cube("eyeL", "head", [-2.5, head_y + 4, -4.0], [-1, head_y + 5.5, -3.6], "ink"))
    els.append(cube("eyeR", "head", [1, head_y + 4, -4.0], [2.5, head_y + 5.5, -3.6], "ink"))
    els.append(cube("lips", "head", [-1.5, head_y + 2, -4.0], [1.5, head_y + 2.8, -3.6], "blood"))
    # floating control rings above head
    groups.append(bone("rings", "head", [0, head_y + 12, 0]))
    for i, x in enumerate((-3, 3, -1.5, 1.5)):
        els.append(cube(f"ring{i}", "rings", [x - 1.2, head_y + 10, -1.2], [x + 1.2, head_y + 12.5, 1.2], "brass"))
    # control strings
    els.append(cube("str", "rings", [-0.3, head_y + 7, -0.3], [0.3, head_y + 11, 0.3], "black"))
    return groups, els, h


def build_pelgrim():
    h = 2.2
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    base_body(els, u, hip, shoulder, head_y, "leather", "flesh")
    els.append(cube("robe", "body", [-4.5, 0, -3], [4.5, shoulder, 3], "leather"))
    # shredded hem strips
    for i in range(5):
        els.append(cube(f"hem{i}", "body", [-4 + i * 1.6, 0, -2.5], [-3 + i * 1.6, 4, 2.5], "leather"))
    # deep hood + gaunt face
    els.append(cube("hood", "head", [-4.5, head_y - 1, -4.5], [4.5, head_y + 8, 4.5], "leather"))
    els.append(cube("face", "head", [-2.5, head_y + 1, -4.2], [2.5, head_y + 6, -2], "bone"))
    # prayer chains + staff with labyrinth emblem
    els.append(cube("chains", "body", [-2, hip + 2, 2.5], [2, shoulder, 4], "iron"))
    els.append(cube("staff", "right_arm", [-5.5, 0, -0.6], [-4.5, shoulder + 6, 0.6], "wood"))
    els.append(cube("emblem", "right_arm", [-6.5, shoulder + 4, -1.5], [-3.5, shoulder + 7, 1.5], "brass"))
    return groups, els, h


def build_stiltewachter():
    h = 2.2
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    base_body(els, u, hip, shoulder, head_y, "leather", "flesh")
    # suture lines
    for i in range(4):
        els.append(cube(f"sut{i}", "body", [-0.3, hip + 2 + i * 2, -2.7], [0.3, hip + 3.5 + i * 2, -2.3], "blood"))
    # massive brass silence collar (vertical cylinders)
    for i in range(6):
        ang = i * math.pi / 3
        x, z = math.cos(ang) * 4.5, math.sin(ang) * 4.5
        els.append(cube(f"col{i}", "body",
                        [x - 1, shoulder - 1, z - 1],
                        [x + 1, shoulder + 5, z + 1], "brass"))
    # shush finger pose — raised right arm toward face
    els.append(cube("finger", "right_arm", [-6, head_y, -1], [-4.5, head_y + 3, 1], "flesh"))
    # hooks on arms
    els.append(cube("hkL", "left_arm", [6, hip, -1], [8, hip + 3, 1], "iron"))
    els.append(cube("hkR", "right_arm", [-8, hip, -1], [-6, hip + 3, 1], "iron"))
    return groups, els, h


def build_sleuteldrager():
    h = 2.2
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    # body encrusted with keys
    els.append(cube("torso", "body", [-4, hip, -2.5], [4, shoulder, 2.5], "brass"))
    els.append(cube("ll", "left_leg", [0.5, 0, -2], [3.5, hip, 2], "brass"))
    els.append(cube("rl", "right_leg", [-3.5, 0, -2], [-0.5, hip, 2], "brass"))
    els.append(cube("la", "left_arm", [4, hip + 2, -2], [7, shoulder, 2], "brass"))
    els.append(cube("ra", "right_arm", [-7, hip + 2, -2], [-4, shoulder, 2], "brass"))
    # head = key ring
    els.append(cube("ring", "head", [-4, head_y, -1.5], [4, head_y + 8, 1.5], "brass"))
    els.append(cube("ringHole", "head", [-2, head_y + 2, -1.7], [2, head_y + 6, 1.7], "black"))
    els.append(cube("bow", "head", [-1.5, head_y + 7, -2], [1.5, head_y + 10, 2], "brass"))
    # protruding keys on body
    for i in range(5):
        els.append(cube(f"key{i}", "body",
                        [-3 + i * 1.3, hip + 2 + (i % 2), -3.5],
                        [-2 + i * 1.3, hip + 5 + (i % 2), -2.5], "brass"))
    # offered key in open left hand
    els.append(cube("offer", "left_arm", [5, hip + 1, -1], [10, hip + 2.5, 1], "brass"))
    els.append(cube("offerBit", "left_arm", [9, hip + 0.5, -1.5], [11, hip + 3, 1.5], "brass"))
    return groups, els, h


def build_getuige():
    h = 2.2
    groups, u, hip, shoulder, head_y = humanoid_bones(h)
    els = []
    els.append(cube("torso", "body", [-3.5, hip, -2], [3.5, shoulder, 2], "flesh"))
    els.append(cube("ll", "left_leg", [0.5, 0, -1.5], [3, hip, 1.5], "flesh"))
    els.append(cube("rl", "right_leg", [-3, 0, -1.5], [-0.5, hip, 1.5], "flesh"))
    els.append(cube("la", "left_arm", [3.5, hip + 2, -1.5], [6, shoulder, 1.5], "flesh"))
    els.append(cube("ra", "right_arm", [-6, hip + 2, -1.5], [-3.5, shoulder, 1.5], "flesh"))
    els.append(cube("cloth", "body", [-3.5, 0, -2.5], [3.5, hip + 3, 2.5], "leather"))
    # huge eye-halo disc (chart signature) — no normal head cube
    els.append(cube("haloOuter", "head", [-8, head_y, -2], [8, head_y + 12, 2], "brass"))
    els.append(cube("haloInner", "head", [-5.5, head_y + 2, -2.3], [5.5, head_y + 10, 2.3], "black"))
    els.append(cube("eye", "head", [-4, head_y + 3.5, -3], [4, head_y + 8.5, 3], "eye", emissive=True))
    els.append(cube("pupil", "head", [-1.5, head_y + 5, -3.3], [1.5, head_y + 7, -2.7], "ink"))
    els.append(cube("hk1", "head", [-7, head_y - 2, -1], [-5, head_y + 1, 1], "iron"))
    els.append(cube("hk2", "head", [5, head_y - 2, -1], [7, head_y + 1, 1], "iron"))
    els.append(cube("hk3", "head", [-1.5, head_y - 2.5, -1], [1.5, head_y, 1], "iron"))
    els.append(cube("book", "left_arm", [4, hip - 1, -2.5], [9, hip + 5, 2.5], "ink"))
    els.append(cube("chain", "left_arm", [3, hip + 1, -0.5], [5.5, hip + 4, 0.5], "iron"))
    return groups, els, h


BUILDERS = {
    "spijkermeester": build_spijkermeester,
    "rammelaar": build_rammelaar,
    "stille": build_stille,
    "architect": build_architect,
    "gids": build_gids,
    "kettingzuster": build_kettingzuster,
    "spiegelgast": build_spiegelgast,
    "poortwachter": build_poortwachter,
    "fluitende": build_fluitende,
    "naaister": build_naaister,
    "tweeling": build_tweeling,
    "echo": build_echo,
    "gastheer": build_gastheer,
    "notaris": build_notaris,
    "blinde": build_blinde,
    "smid": build_smid,
    "archivaris": build_archivaris,
    "schatter": build_schatter,
    "danseres": build_danseres,
    "klokkenluider": build_klokkenluider,
    "marionet": build_marionet,
    "pelgrim": build_pelgrim,
    "stiltewachter": build_stiltewachter,
    "sleuteldrager": build_sleuteldrager,
    "getuige": build_getuige,
}


def write_model(kind: str, groups, elements, height: float):
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    asset = {
        "name": f"gebondene_{kind}",
        "id": kind,
        "units_per_block": 16,
        "texture_size": [TEX, TEX],
        "groups": groups,
        "elements": elements,
        "clips": clips_basic(),
        "ref_height_blocks": height,
    }
    path = MODEL_DIR / f"{kind}.model.json"
    path.write_text(json.dumps(asset, separators=(",", ":")), encoding="utf-8")
    return path


def write_texture(kind: str, accent: tuple[int, int, int] | None = None):
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    img = paint_atlas()
    # kind accent strip at bottom for variety
    draw = ImageDraw.Draw(img)
    accents = {
        "architect": (220, 210, 195),
        "spijkermeester": (170, 130, 50),
        "rammelaar": (150, 110, 40),
        "stille": (20, 20, 25),
        "gids": (255, 200, 80),
        "kettingzuster": (100, 30, 45),
        "spiegelgast": (210, 190, 100),
        "poortwachter": (80, 70, 60),
        "gastheer": (230, 220, 200),
        "smid": (90, 70, 55),
        "getuige": (60, 100, 150),
        "sleuteldrager": (180, 140, 50),
        "klokkenluider": (160, 120, 40),
        "marionet": (200, 170, 130),
    }
    c = accent or accents.get(kind, (100, 80, 60))
    draw.rectangle([0, 112, 127, 127], fill=c + (255,))
    # label pixels unique per kind hash
    rng = random.Random(kind)
    for _ in range(40):
        x, y = rng.randint(0, 127), rng.randint(96, 111)
        img.putpixel((x, y), (c[0], c[1], c[2], 255))
    out = TEX_DIR / f"{kind}.png"
    img.save(out)
    return out


def main():
    assert set(BUILDERS) == set(BUILDERS.keys())
    missing = []
    for kind, builder in BUILDERS.items():
        groups, els, h = builder()
        # sanity: from < to
        for e in els:
            f, t = e["from"], e["to"]
            if not (f[0] < t[0] and f[1] < t[1] and f[2] < t[2]):
                raise SystemExit(f"{kind} bad cube {e['name']}: {f} -> {t}")
        p = write_model(kind, groups, els, h)
        t = write_texture(kind)
        print(f"OK {kind:16s} cubes={len(els):3d} h={h:.1f} -> {p.name} + {t.name}")
    print(f"Wrote {len(BUILDERS)} Gebondene models to {MODEL_DIR}")


if __name__ == "__main__":
    main()
