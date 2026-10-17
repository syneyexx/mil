#!/usr/bin/env python3
"""Render full Orde block catalog PNG sheets from real 16x16 textures."""
from __future__ import annotations

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
TEX = ROOT / "src/main/resources/assets/millenaire/textures/block"
OUT = ROOT / "docs/images/orde-blocks"
ART = Path("/opt/cursor/artifacts/assets")

CATALOG = [
    ("STRUCTURE", [
        ("orde_stone", "Orde Stone", "orde_stone.png"),
        ("orde_stone_stairs", "Stone Stairs", "orde_stone.png"),
        ("orde_stone_slab", "Stone Slab", "orde_stone.png"),
        ("orde_panel", "Orde Panel", "orde_panel.png"),
        ("orde_panel_stairs", "Panel Stairs", "orde_panel.png"),
        ("orde_panel_slab", "Panel Slab", "orde_panel.png"),
        ("orde_tile", "Orde Tile", "orde_tile.png"),
        ("orde_tile_slab", "Tile Slab", "orde_tile.png"),
        ("bleed_planks", "Bleed Planks", "bleed_planks.png"),
        ("bleed_planks_stairs", "Plank Stairs", "bleed_planks.png"),
        ("bleed_planks_slab", "Plank Slab", "bleed_planks.png"),
        ("orde_bricks", "Orde Bricks", "orde_bricks.png"),
        ("orde_pillar", "Orde Pillar", "orde_pillar.png"),
        ("orde_rune", "Orde Rune", "orde_rune.png"),
        ("orde_sigil", "Orde Sigil", "orde_sigil.png"),
        ("orde_script", "Orde Script", "orde_script.png"),
        ("riveted_stone", "Riveted Stone", "riveted_stone.png"),
        ("crimson_band", "Crimson Band", "crimson_band.png"),
        ("cold_vault", "Cold Vault", "cold_vault.png"),
        ("ruit_heart", "Ruit Heart", "ruit_heart.png"),
    ]),
    ("ARCHITECTURE", [
        ("chevron_tile", "Chevron Tile", "chevron_tile.png"),
        ("fluted_pillar", "Fluted Pillar", "fluted_pillar.png"),
        ("pillar_base", "Pillar Base", "pillar_base.png"),
        ("orde_coffer", "Orde Coffer", "orde_coffer.png"),
        ("portal_frame", "Portal Frame", "portal_frame.png"),
        ("void_gate", "Void Gate", "void_gate.png"),
        ("ceiling_recess", "Ceiling Recess", "ceiling_recess.png"),
    ]),
    ("METAL & SPIKES", [
        ("orde_bars", "Orde Bars", "orde_bars.png"),
        ("orde_grate", "Orde Grate", "orde_grate.png"),
        ("spike_floor", "Spike Floor", "spike_floor.png"),
        ("spike_wall", "Spike Wall", "spike_wall.png"),
        ("hook_block", "Hook Block", "hook_block.png"),
        ("hanging_hook", "Hanging Hook", "hanging_hook.png"),
        ("orde_censer", "Orde Censer", "orde_censer.png"),
    ]),
    ("BOUND / LEATHER", [
        ("bound_leather", "Bound Leather", "bound_leather.png"),
        ("nail_studded", "Nail-Studded", "nail_studded.png"),
        ("strap_block", "Strap Block", "strap_block.png"),
        ("silk_veil", "Silk Veil", "silk_veil.png"),
    ]),
    ("BLEED BEDROOM", [
        ("bleed_wallpaper", "Bleed Wallpaper", "bleed_wallpaper.png"),
        ("orde_barricade", "Orde Barricade", "orde_barricade.png"),
        ("orde_bedding", "Orde Bedding", "orde_bedding.png"),
        ("orde_nightstand", "Nightstand", "orde_nightstand.png"),
        ("orde_clock", "Orde Clock", "orde_clock.png"),
        ("crooked_painting", "Crooked Painting", "crooked_painting.png"),
        ("orde_lamp", "Orde Lamp", "orde_lamp.png"),
    ]),
    ("RITUAL / LIGHT", [
        ("orde_altar", "Orde Altar", "orde_altar_side.png"),
        ("offering_bowl", "Offering Bowl", "offering_bowl_top.png"),
        ("orde_brazier", "Orde Brazier", "orde_brazier.png"),
        ("orde_lantern", "Orde Lantern", "orde_lantern.png"),
        ("void_sconce", "Void Sconce", "void_sconce.png"),
    ]),
]

BG = (18, 18, 22, 255)
CARD = (28, 28, 34, 255)
BORDER = (70, 74, 84, 255)
TITLE = (220, 225, 235, 255)
SUB = (140, 150, 165, 255)
ACCENT = (110, 170, 210, 255)
LABEL = (200, 205, 215, 255)
IDCOL = (120, 130, 145, 255)


def load_tex(name: str) -> Image.Image:
    p = TEX / name
    if not p.exists():
        return Image.new("RGBA", (16, 16), (255, 0, 255, 255))
    return Image.open(p).convert("RGBA")


def nearest_scale(img: Image.Image, scale: int) -> Image.Image:
    return img.resize((img.width * scale, img.height * scale), Image.NEAREST)


def try_font(size: int, bold=False):
    candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ]
    for c in candidates:
        if Path(c).exists():
            return ImageFont.truetype(c, size)
    return ImageFont.load_default()


def save_both(img: Image.Image, name: str):
    OUT.mkdir(parents=True, exist_ok=True)
    ART.mkdir(parents=True, exist_ok=True)
    rgb = img.convert("RGB")
    rgb.save(OUT / name, quality=95)
    rgb.save(ART / name, quality=95)
    print("wrote", OUT / name, img.size)


def render_full_grid():
    font_title = try_font(42, True)
    font_section = try_font(22, True)
    font_name = try_font(14, True)
    font_id = try_font(11)
    font_small = try_font(12)
    scale, cols, pad = 6, 7, 16
    tile = 16 * scale
    cell_w, cell_h = tile + 24, tile + 52
    flat = [(s, *it) for s, items in CATALOG for it in items]
    rows_layout = []
    for section, items in CATALOG:
        rows_layout.append(("section", section))
        for i in range(0, len(items), cols):
            rows_layout.append(("row", items[i : i + cols]))
    header_h = 110
    y = header_h
    for kind, *_ in rows_layout:
        y += 44 if kind == "section" else cell_h + 12
    w, h = cols * cell_w + pad * 2 + 40, y + 40
    canvas = Image.new("RGBA", (w, h), BG)
    draw = ImageDraw.Draw(canvas)
    draw.rectangle([0, 0, w, header_h], fill=(12, 12, 16, 255))
    draw.text((pad + 8, 18), "THE ORDER — FULL BLOCK CATALOG", fill=TITLE, font=font_title)
    draw.text(
        (pad + 8, 68),
        f"Creative tab  ·  {len(flat)} placeable entries  ·  actual 16×16 textures (×{scale})",
        fill=SUB,
        font=font_small,
    )
    draw.rectangle([pad + 8, 98, pad + 200, 101], fill=ACCENT)
    cy = header_h + 8
    for entry in rows_layout:
        if entry[0] == "section":
            draw.text((pad + 8, cy + 8), entry[1], fill=ACCENT, font=font_section)
            cy += 44
            continue
        for col, (bid, en, tex) in enumerate(entry[1]):
            x = pad + 20 + col * cell_w
            draw.rounded_rectangle(
                [x - 6, cy - 4, x + tile + 6, cy + tile + 40], radius=6, fill=CARD, outline=BORDER, width=1
            )
            for yy in range(0, tile, 8):
                for xx in range(0, tile, 8):
                    c = (40, 40, 48, 255) if (xx // 8 + yy // 8) % 2 == 0 else (28, 28, 34, 255)
                    draw.rectangle([x + xx, cy + yy, x + xx + 7, cy + yy + 7], fill=c)
            tex_img = nearest_scale(load_tex(tex), scale)
            canvas.paste(tex_img, (x, cy), tex_img)
            draw.text((x - 2, cy + tile + 6), en, fill=LABEL, font=font_name)
            draw.text((x - 2, cy + tile + 24), bid, fill=IDCOL, font=font_id)
        cy += cell_h + 12
    draw.text((pad + 8, h - 28), "millenaire:the_order  ·  python3 tools/gen_orde_catalog_images.py", fill=SUB, font=font_small)
    save_both(canvas, "orde-catalog-full.png")


def iso_cube(tex_top: Image.Image, tex_side: Image.Image, size=48) -> Image.Image:
    ow, oh = size * 2 + 16, int(size * 1.75) + 16
    cube = Image.new("RGBA", (ow, oh), (0, 0, 0, 0))
    cx, cy0 = ow // 2, 8
    hw, hh = size, size // 2
    top_pts = [(cx, cy0), (cx + hw, cy0 + hh), (cx, cy0 + hh * 2), (cx - hw, cy0 + hh)]
    left_pts = [(cx - hw, cy0 + hh), (cx, cy0 + hh * 2), (cx, cy0 + hh * 2 + size), (cx - hw, cy0 + hh + size)]
    right_pts = [(cx + hw, cy0 + hh), (cx, cy0 + hh * 2), (cx, cy0 + hh * 2 + size), (cx + hw, cy0 + hh + size)]

    def paste_face(src, pts, shade=0):
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
        bw, bh = maxx - minx + 1, maxy - miny + 1
        stretched = src.resize((bw, bh), Image.NEAREST)
        if shade:
            stretched = Image.blend(stretched, Image.new("RGBA", stretched.size, (0, 0, 0, shade)), shade / 255)
        mask = Image.new("L", (bw, bh), 0)
        ImageDraw.Draw(mask).polygon([(p[0] - minx, p[1] - miny) for p in pts], fill=255)
        face = Image.new("RGBA", (bw, bh), (0, 0, 0, 0))
        face.paste(stretched, (0, 0))
        face.putalpha(mask)
        cube.paste(face, (minx, miny), face)

    paste_face(tex_top, top_pts, 0)
    paste_face(tex_side, left_pts, 70)
    paste_face(tex_side, right_pts, 120)
    return cube


def render_isometric():
    font_title = try_font(42, True)
    font_name = try_font(14, True)
    font_id = try_font(11)
    font_small = try_font(12)
    primaries = []
    seen = set()
    for _, items in CATALOG:
        for bid, en, tex in items:
            if bid.endswith(("_stairs", "_slab")) or bid in seen:
                continue
            seen.add(bid)
            primaries.append((bid, en, tex))
    cols, pad, cell = 6, 16, 170
    rows = (len(primaries) + cols - 1) // cols
    w, h = cols * cell + pad * 2, 100 + rows * (cell + 10) + 40
    sheet = Image.new("RGBA", (w, h), BG)
    d = ImageDraw.Draw(sheet)
    d.rectangle([0, 0, w, 90], fill=(12, 12, 16, 255))
    d.text((pad, 20), "THE ORDER — ISOMETRIC PREVIEWS", fill=TITLE, font=font_title)
    d.text((pad, 62), f"{len(primaries)} unique blocks", fill=SUB, font=font_small)
    for i, (bid, en, tex) in enumerate(primaries):
        col, row = i % cols, i // cols
        x, y = pad + col * cell, 100 + row * (cell + 10)
        d.rounded_rectangle([x, y, x + cell - 12, y + cell - 8], radius=8, fill=CARD, outline=BORDER)
        if bid == "orde_altar":
            top, side = "orde_altar_top.png", "orde_altar_side.png"
        elif bid == "offering_bowl":
            top, side = "offering_bowl_top.png", "offering_bowl_side.png"
        elif bid == "spike_floor":
            top, side = "spike_floor_top.png", "spike_floor_side.png"
        else:
            top = side = tex
        cube = iso_cube(load_tex(top), load_tex(side), size=48)
        sheet.paste(cube, (x + (cell - 12 - cube.width) // 2, y + 8), cube)
        d.text((x + 10, y + cell - 48), en, fill=LABEL, font=font_name)
        d.text((x + 10, y + cell - 30), bid, fill=IDCOL, font=font_id)
    save_both(sheet, "orde-catalog-isometric.png")


def render_pixel_detail():
    font_title = try_font(42, True)
    font_name = try_font(14, True)
    font_small = try_font(12)
    items = [
        ("orde_stone", "orde_stone.png"),
        ("orde_panel", "orde_panel.png"),
        ("orde_tile", "orde_tile.png"),
        ("bleed_planks", "bleed_planks.png"),
        ("spike_floor", "spike_floor.png"),
        ("hook_block", "hook_block.png"),
        ("bound_leather", "bound_leather.png"),
        ("nail_studded", "nail_studded.png"),
        ("bleed_wallpaper", "bleed_wallpaper.png"),
        ("orde_sigil", "orde_sigil.png"),
        ("void_gate", "void_gate.png"),
        ("orde_altar_side", "orde_altar_side.png"),
    ]
    cols, zoom, pad = 4, 12, 16
    cell = zoom * 16 + 80
    w = cols * cell + pad * 2
    h = 90 + ((len(items) + cols - 1) // cols) * cell + 30
    sheet = Image.new("RGBA", (w, h), BG)
    d = ImageDraw.Draw(sheet)
    d.rectangle([0, 0, w, 80], fill=(12, 12, 16, 255))
    d.text((pad, 18), "THE ORDER — PIXEL DETAIL (×12)", fill=TITLE, font=font_title)
    d.text((pad, 55), "Exact 16×16 source pixels", fill=SUB, font=font_small)
    for i, (bid, tex) in enumerate(items):
        col, row = i % cols, i // cols
        x, y = pad + col * cell, 90 + row * cell
        d.rounded_rectangle([x, y, x + cell - 16, y + cell - 16], radius=8, fill=CARD, outline=BORDER)
        img = nearest_scale(load_tex(tex), zoom)
        px = x + (cell - 16 - img.width) // 2
        py = y + 16
        for yy in range(0, img.height, 16):
            for xx in range(0, img.width, 16):
                c = (40, 40, 48, 255) if ((xx // 16) + (yy // 16)) % 2 == 0 else (28, 28, 34, 255)
                d.rectangle([px + xx, py + yy, px + xx + 15, py + yy + 15], fill=c)
        sheet.paste(img, (px, py), img)
        d.text((x + 12, y + cell - 48), bid, fill=LABEL, font=font_name)
    save_both(sheet, "orde-catalog-pixel-detail.png")


def main():
    render_full_grid()
    render_isometric()
    render_pixel_detail()
    print("done")


if __name__ == "__main__":
    main()
