"""Generate production Nether Wraith albedo + emissive textures (256x256)."""
from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image, ImageFilter

W = H = 256
rng = random.Random(42)

CHARCOAL = [(16, 12, 13), (26, 18, 20), (38, 25, 27), (20, 14, 15)]
BONE = [(123, 103, 96), (154, 129, 120), (179, 153, 141), (98, 82, 76), (140, 118, 110)]
CRIMSON = [(78, 8, 12), (121, 10, 16), (165, 13, 19), (60, 6, 10)]
EMISSIVE = [(224, 20, 27), (255, 40, 40), (255, 74, 50), (180, 10, 14)]


def lerp(a: int, b: int, t: float) -> int:
    return int(a + (b - a) * t)


def mix(c1, c2, t: float):
    return tuple(lerp(c1[i], c2[i], t) for i in range(3)) + (255,)


def main() -> None:
    img = Image.new("RGBA", (W, H), (16, 12, 13, 255))
    px = img.load()

    for y in range(H):
        for x in range(W):
            c = CHARCOAL[int(rng.random() * len(CHARCOAL)) % len(CHARCOAL)]
            band = 0.85 + 0.15 * math.sin(x * 0.08 + y * 0.03)
            px[x, y] = (int(c[0] * band), int(c[1] * band), int(c[2] * band), 255)

    def fill_rect(x0, y0, w, h, colors, crack=False, bone=False):
        for yy in range(y0, min(H, y0 + h)):
            for xx in range(x0, min(W, x0 + w)):
                t = rng.random()
                c = colors[int(t * len(colors)) % len(colors)]
                if bone:
                    if rng.random() < 0.08:
                        c = mix(c, (60, 40, 40), 0.35)[:3]
                    if crack and rng.random() < 0.03:
                        c = CRIMSON[0]
                    shade = 0.9 + 0.2 * math.sin(xx * 0.4) * math.cos(yy * 0.35)
                    px[xx, yy] = (
                        max(0, min(255, int(c[0] * shade))),
                        max(0, min(255, int(c[1] * shade))),
                        max(0, min(255, int(c[2] * shade))),
                        255,
                    )
                else:
                    px[xx, yy] = (*c, 255)

    def fill_glow(x0, y0, w, h, colors):
        for yy in range(y0, min(H, y0 + h)):
            for xx in range(x0, min(W, x0 + w)):
                cx, cy = x0 + w / 2, y0 + h / 2
                d = math.hypot(xx - cx, yy - cy) / (max(w, h) * 0.7 + 0.01)
                t = max(0.0, 1.0 - d) ** 2
                c = mix(colors[0], colors[2], t)
                if rng.random() < 0.12:
                    c = (*colors[2], 255)
                px[xx, yy] = c

    # Skull / bone regions
    fill_rect(0, 0, 128, 40, BONE, crack=True, bone=True)
    fill_rect(0, 20, 60, 20, CHARCOAL)
    fill_rect(46, 12, 40, 12, BONE, crack=True, bone=True)
    fill_rect(80, 0, 48, 40, BONE, bone=True)
    fill_rect(96, 0, 24, 32, BONE, bone=True)
    fill_rect(96, 24, 8, 8, CHARCOAL)
    fill_rect(80, 16, 40, 24, BONE, crack=True, bone=True)
    fill_rect(114, 0, 8, 8, BONE, bone=True)

    # Tendrils
    for variant in range(6):
        u = 120 + (variant % 3) * 12
        v = 40 + (variant // 3) * 40
        fill_rect(u, v, 12, 14, CHARCOAL)
        fill_rect(u, v + 16, 10, 12, [(30, 18, 20), (40, 20, 22), (50, 22, 24)])
        fill_rect(u, v + 28, 8, 12, CRIMSON)

    # Body / arms / legs
    fill_rect(0, 80, 64, 40, BONE, crack=True, bone=True)
    fill_rect(0, 90, 12, 20, BONE, bone=True)
    fill_rect(12, 90, 50, 20, BONE, bone=True)
    fill_rect(0, 110, 64, 16, BONE, bone=True)
    fill_rect(0, 130, 64, 40, BONE, crack=True, bone=True)
    fill_rect(0, 150, 64, 20, BONE, bone=True)
    fill_rect(0, 160, 64, 24, BONE, bone=True)
    for i in range(4):
        fill_rect(i * 16, 168, 8, 10, CHARCOAL)
    fill_rect(64, 130, 64, 50, BONE, crack=True, bone=True)
    fill_rect(64, 150, 40, 20, BONE, bone=True)

    # Glowing accents on albedo
    fill_glow(200, 0, 32, 16, EMISSIVE)
    fill_glow(200, 16, 24, 12, [CRIMSON[0], CRIMSON[1], EMISSIVE[0]])
    fill_glow(200, 28, 16, 4, EMISSIVE)
    fill_glow(200, 32, 20, 10, [CRIMSON[1], EMISSIVE[0], EMISSIVE[1]])
    fill_glow(212, 16, 20, 12, EMISSIVE)
    fill_glow(212, 24, 40, 12, EMISSIVE)
    fill_glow(212, 40, 40, 16, EMISSIVE)

    for _ in range(180):
        x = rng.randint(0, 180)
        y = rng.randint(0, 180)
        r, g, b, a = px[x, y]
        if r > 90:
            length = rng.randint(2, 6)
            for i in range(length):
                xx = min(W - 1, x + i)
                yy = min(H - 1, y + (1 if i % 2 == 0 else 0))
                px[xx, yy] = (*CRIMSON[1], 255)

    soft = img.filter(ImageFilter.SMOOTH_MORE)
    img = Image.blend(img, soft, 0.25)

    root = Path(__file__).resolve().parents[1]
    out_dir = root / "src/main/resources/assets/millenaire/textures/entity"
    out_dir.mkdir(parents=True, exist_ok=True)
    out = out_dir / "nether_wraith.png"
    img.save(out, "PNG")
    print("Wrote", out, img.size)

    emi = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ep = emi.load()

    def paint_emi(x0, y0, w, h, colors, alpha=255):
        for yy in range(y0, min(H, y0 + h)):
            for xx in range(x0, min(W, x0 + w)):
                cx, cy = x0 + w / 2, y0 + h / 2
                d = math.hypot(xx - cx, yy - cy) / (max(w, h) * 0.65 + 0.01)
                t = max(0.0, 1.0 - d)
                if t <= 0.05 and rng.random() > 0.3:
                    continue
                c = mix(colors[0], colors[2], t * t)
                ep[xx, yy] = (c[0], c[1], c[2], alpha)

    paint_emi(200, 0, 32, 16, EMISSIVE)
    paint_emi(200, 16, 24, 12, EMISSIVE)
    paint_emi(200, 28, 16, 4, EMISSIVE)
    paint_emi(200, 32, 20, 10, EMISSIVE)
    paint_emi(212, 16, 20, 12, EMISSIVE)
    paint_emi(212, 24, 40, 12, EMISSIVE)
    paint_emi(212, 40, 40, 16, EMISSIVE)
    paint_emi(228, 40, 12, 12, EMISSIVE)
    for variant in range(6):
        u = 120 + (variant % 3) * 12
        v = 40 + (variant // 3) * 40
        paint_emi(u, v + 28, 8, 12, EMISSIVE)
        paint_emi(u + 2, v + 32, 6, 8, EMISSIVE)

    out2 = out_dir / "nether_wraith_emissive.png"
    emi.save(out2, "PNG")
    lit = sum(1 for y in range(H) for x in range(W) if ep[x, y][3] > 0)
    print("Wrote", out2, emi.size, "lit pixels", lit)


if __name__ == "__main__":
    main()
