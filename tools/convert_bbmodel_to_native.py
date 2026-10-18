#!/usr/bin/env python3
"""Convert Gebondene Blockbench .bbmodel packs into NativeMonster-style .model.json.

Source: ChatGPT/Blockbench pack (format_version 4.10, free model).
Target: millenaire models/entity/gebondene/<id>.model.json + textures/.../<id>.png
"""
from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PACK = Path("/tmp/gebondene_bb/Minecraft_21_Mobs")
MODEL_OUT = ROOT / "src/main/resources/assets/millenaire/models/entity/gebondene"
TEX_OUT = ROOT / "src/main/resources/assets/millenaire/textures/entity/gebondene"


def flatten_outliner(outliner: list) -> tuple[list[dict], dict[str, str]]:
    """Return flat groups [{name,parent,origin,rotation}] and cube_uuid -> bone name."""
    groups: list[dict] = []
    cube_bone: dict[str, str] = {}

    def walk(node, parent_name: str | None):
        if isinstance(node, str):
            cube_bone[node] = parent_name or "root"
            return
        if not isinstance(node, dict):
            raise TypeError(f"Unexpected outliner node: {type(node)}")
        name = node["name"]
        origin = node.get("origin") or [0, 0, 0]
        rotation = node.get("rotation") or [0, 0, 0]
        if isinstance(rotation, dict):
            rotation = [rotation.get("x", 0), rotation.get("y", 0), rotation.get("z", 0)]
        groups.append(
            {
                "name": name,
                "parent": parent_name,
                "origin": [float(origin[0]), float(origin[1]), float(origin[2])],
                "rotation": [float(rotation[0]), float(rotation[1]), float(rotation[2])],
            }
        )
        for child in node.get("children", []):
            walk(child, name)

    for root in outliner:
        walk(root, None)
    return groups, cube_bone


def normalize_rotation(rot) -> list[float]:
    if rot is None:
        return [0.0, 0.0, 0.0]
    if isinstance(rot, list):
        return [float(rot[0]), float(rot[1]), float(rot[2])]
    if isinstance(rot, dict):
        return [float(rot.get("x", 0)), float(rot.get("y", 0)), float(rot.get("z", 0))]
    raise TypeError(f"Unsupported rotation: {rot!r}")


def convert_element(elem: dict, bone: str) -> dict:
    faces = {}
    for face_name, face in elem["faces"].items():
        uv = face["uv"]
        faces[face_name] = {
            "uv": [float(uv[0]), float(uv[1]), float(uv[2]), float(uv[3])],
            "texture": 0,
        }
    name = elem.get("name", "cube")
    emissive = any(k in name.lower() for k in ("amber", "lantern_glow", "flame", "glow", "eye_glint"))
    return {
        "name": name,
        "uuid": elem.get("uuid"),
        "type": "cube",
        "from": [float(x) for x in elem["from"]],
        "to": [float(x) for x in elem["to"]],
        "origin": [float(x) for x in elem["origin"]],
        "rotation": normalize_rotation(elem.get("rotation")),
        "faces": faces,
        "_bone": bone,
        "_emissive": emissive,
    }


def clips_for(groups: list[dict]) -> dict:
    names = {g["name"] for g in groups}

    def rot_channel(frames):
        return {"rotation": frames}

    bones = {}
    # Standard biped
    pairs = [
        ("left_leg", "right_leg", 20),
        ("left_shin", "right_shin", 10),
        ("left_arm", "right_arm", 15),
        ("left_forearm", "right_forearm", 8),
    ]
    # Tweeling dual rig
    for prefix in ("", "a_", "b_"):
        for left, right, amp in pairs:
            ln, rn = prefix + left, prefix + right
            if ln in names and rn in names:
                bones[ln] = rot_channel([[0, [amp, 0, 0]], [0.5, [-amp, 0, 0]], [1, [amp, 0, 0]]])
                bones[rn] = rot_channel([[0, [-amp, 0, 0]], [0.5, [amp, 0, 0]], [1, [-amp, 0, 0]]])

    idle_bones = {}
    if "body" in names:
        idle_bones["body"] = {"position": [[0, [0, 0, 0]], [1, [0, 0.3, 0]], [2, [0, 0, 0]]]}
    if "a_body" in names:
        idle_bones["a_body"] = {"position": [[0, [0, 0, 0]], [1, [0, 0.3, 0]], [2, [0, 0, 0]]]}
    if "b_body" in names:
        idle_bones["b_body"] = {"position": [[0, [0, 0, 0]], [1, [0, 0.3, 0]], [2, [0, 0, 0]]]}
    if "puzzle_cube" in names:
        idle_bones["puzzle_cube"] = {"rotation": [[0, [0, 0, 0]], [1, [0, 8, 0]], [2, [0, 0, 0]]]}
    if "pins" in names:
        idle_bones["pins"] = {"rotation": [[0, [0, 0, 0]], [1, [1.5, 0, 0]], [2, [0, 0, 0]]]}

    return {
        "idle": {"length": 2.0, "loop": True, "bones": idle_bones},
        "walk": {"length": 1.0, "loop": True, "bones": bones},
        "hurt": {
            "length": 0.35,
            "loop": False,
            "bones": {"body": {"rotation": [[0, [0, 0, 0]], [0.1, [6, 0, 0]], [0.35, [0, 0, 0]]]}}
            if "body" in names
            else {},
        },
    }


def convert_bbmodel(bb_path: Path, kind_id: str) -> dict:
    data = json.loads(bb_path.read_text(encoding="utf-8"))
    res = data.get("resolution") or {}
    tw = int(res.get("width", 256))
    th = int(res.get("height", 256))
    groups, cube_bone = flatten_outliner(data["outliner"])
    uuid_to_elem = {e["uuid"]: e for e in data["elements"]}

    elements = []
    missing = []
    for uuid, bone in cube_bone.items():
        elem = uuid_to_elem.get(uuid)
        if elem is None:
            missing.append(uuid)
            continue
        frm, to = elem["from"], elem["to"]
        if not (frm[0] < to[0] and frm[1] < to[1] and frm[2] < to[2]):
            raise ValueError(f"{kind_id}: invalid extents on {elem.get('name')}: {frm} -> {to}")
        elements.append(convert_element(elem, bone))

    if missing:
        raise ValueError(f"{kind_id}: {len(missing)} cube UUIDs missing from elements")

    # Ensure every referenced bone exists
    group_names = {g["name"] for g in groups}
    for e in elements:
        if e["_bone"] not in group_names:
            raise ValueError(f"{kind_id}: element {e['name']} bone {e['_bone']} missing")

    return {
        "name": f"gebondene_{kind_id}",
        "id": kind_id,
        "units_per_block": 16,
        "texture_size": [tw, th],
        "groups": groups,
        "elements": elements,
        "clips": clips_for(groups),
        "source": "blockbench_pack_v1",
    }


def convert_pack(pack_root: Path) -> list[str]:
    mobs_dir = pack_root / "mobs"
    if not mobs_dir.is_dir():
        raise SystemExit(f"No mobs/ under {pack_root}")
    MODEL_OUT.mkdir(parents=True, exist_ok=True)
    TEX_OUT.mkdir(parents=True, exist_ok=True)

    converted = []
    for mob_dir in sorted(mobs_dir.iterdir()):
        if not mob_dir.is_dir():
            continue
        kind = mob_dir.name
        bb = mob_dir / f"{kind}.bbmodel"
        png = mob_dir / f"{kind}.png"
        if not bb.is_file() or not png.is_file():
            print(f"SKIP {kind}: missing bbmodel/png", file=sys.stderr)
            continue
        asset = convert_bbmodel(bb, kind)
        out_model = MODEL_OUT / f"{kind}.model.json"
        out_model.write_text(json.dumps(asset, separators=(",", ":")), encoding="utf-8")
        shutil.copy2(png, TEX_OUT / f"{kind}.png")
        # Keep source bbmodel outside resources (not packaged into the jar)
        src_dir = ROOT / "tools/gebondene_blockbench_v1" / kind
        src_dir.mkdir(parents=True, exist_ok=True)
        shutil.copy2(bb, src_dir / f"{kind}.bbmodel")
        shutil.copy2(png, src_dir / f"{kind}.png")
        print(
            f"OK {kind:16s} groups={len(asset['groups']):3d} cubes={len(asset['elements']):4d} "
            f"tex={asset['texture_size']}"
        )
        converted.append(kind)
    return converted


def main():
    pack = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_PACK
    converted = convert_pack(pack)
    print(f"Converted {len(converted)} models from {pack}")
    # Report kinds still on generated fallback
    all_kinds = {
        "architect",
        "spijkermeester",
        "needleguy",
        "rammelaar",
        "stille",
        "gids",
        "kettingzuster",
        "spiegelgast",
        "poortwachter",
        "fluitende",
        "naaister",
        "tweeling",
        "echo",
        "gastheer",
        "notaris",
        "blinde",
        "smid",
        "archivaris",
        "schatter",
        "danseres",
        "klokkenluider",
        "marionet",
        "pelgrim",
        "stiltewachter",
        "sleuteldrager",
        "getuige",
    }
    missing = sorted(all_kinds - set(converted))
    if missing:
        print(f"Fallback (no pack model): {', '.join(missing)}")


if __name__ == "__main__":
    main()
