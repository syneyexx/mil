# Labyrinth layout v3 — Wellgrid

**Status:** implemented on this branch. **Breaking:** `LAYOUT_VERSION = 3` — delete/regenerate `millenaire:labyrinth`.

## Goal

Turn the dimension from “blackstone corridors + stamped normal rooms” into an **infinite 3D cell grid**: deep wells, arcade walls, walkable crowns, crazy hallways as the default, sparse ceremonial rooms, open horizon with De Ruit sky beam.

IP-clean: Configuratie / Orde / Gebondenen / De Ruit — no Hellraiser names.

## What changed (waves A–F)

| Wave | Deliverable |
|------|-------------|
| **A** | Corridors/walls use full Orde material palette (`LabyrinthMaterials`) |
| **B** | Each cell is an open well (vault → crown); perimeter walls; arch doorways |
| **C** | `LabyrinthHallways` archetypes on every `NONE` cell |
| **D** | `WELL_ATRIUM` / `BRIDGE_NODE` / `CLOISTER` / `DEPTH_SHAFT` + denser waypoints |
| **E** | Open void above crown; `void_beam` sky anchor over Audience/Core |
| **F** | Hub mix majority crazy (~7/8); liminal rare Bleed islands; docs |

## Hallway archetypes (`NONE` cells)

`SPIKE_TRENCH` · `ARCADE_CLOISTER` · `CROSS_BRIDGE` · `GALLERY_STACK` · `CHOKE_HOOK` · `VOID_WELL`

Zone-weighted (Bleed softer; Orde/Approach harder voids & bridges).

## New blocks

| ID | Role |
|----|------|
| `orde_arch` | Arch motif / lintels in well walls |
| `orde_parapet` | Walkable wall-crown |
| `gallery_rail` | Overlook railing |
| `void_beam` | Distant vertical light pillar (De Ruit sky anchor) |

## Hub density (lattice every 8 cells)

| Index | Type |
|-------|------|
| 0–4 | ROTARY / WELL_ATRIUM / BRIDGE_NODE / CLOISTER / DEPTH_SHAFT |
| 5 | GATE |
| 6 | ECHO / MIRROR loop |
| 7 | Ceremonial (HOST/FORGE/ARCHIVE/CHAPEL) — ~12.5% |

## Player migration

1. Update mod to this build.
2. Delete or reset dimension `millenaire:labyrinth` (or use project resetdim flow).
3. Re-enter via Configuratie — layout v3 generates fresh.

## Debug F3

`Labyrinth v3 cell x,z room=… hall=ARCHETYPE zone=…`
