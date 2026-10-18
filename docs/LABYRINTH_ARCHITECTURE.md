# Labyrinth (Het Labyrint) — Architecture

## Overview

Custom Millénaire dimension `millenaire:labyrinth` with a deterministic cellular maze, ceremonial **Gebondenen** (26 kinds, hospitable until provoked), entry via **Configuratie**, exit via **Scharnier** / Configuratie.

**Revival-achtige uitbreiding:** Waves 1–8 ([`LABYRINTH_REVIVAL_WAVES.md`](LABYRINTH_REVIVAL_WAVES.md)) plus psyche release (P1–P5) per [`LABYRINTH_REVIVAL_RELEASE_PLAN.md`](LABYRINTH_REVIVAL_RELEASE_PLAN.md). **Layout v3 wellgrid:** [`LABYRINTH_WELLGRID_V3.md`](LABYRINTH_WELLGRID_V3.md). Earlier vibe audit: [`LABYRINTH_REVIVAL_MASTERPLAN.md`](LABYRINTH_REVIVAL_MASTERPLAN.md). No Hellraiser IP; own lore (Configuratie forms, Orde relics, Architect, De Ruit).

**Breaking:** `labyrinthLayoutVersion=3` — delete/regenerate `millenaire:labyrinth` after upgrading.

## Packages

| Package | Role |
|---------|------|
| `org.millenaire.labyrinth` | Keys, bootstrap, death/progress handlers, atmosphere enum |
| `...gen` | Maze + chunk generator + room stamps + shifter |
| `...portal` | Configuratie (+ vorm), Scharnier, teleporter |
| `...puzzle` | Rotary, gates, echo loop, relic caches, puzzle SavedData, chunk-load restore |
| `...chase` | Rammelaar corridor chase |
| `...combat` | Configuratie KETTING pulse |
| `...entity` | GebondeneEntity / Kind / Attitude / Orde SavedData / Architect |
| `...spawn` | Ambient + gate + Architect population |
| `...client` | Model, renderer, fog, ambient |
| `...command` | `/millenaire labyrinth …` |

## Floor bands (wellgrid v3)

| Band | Y | Role |
|------|---|------|
| Vault | 48–56 | Pit floors / lower bridges (beyond manhattan 12) |
| Primary | 64–73 | Gallery rings, cross-bridges, doorway arches |
| Mezzanine | 74–82 | Upper overlooks + wall crowns (`WALL_CROWN_Y`) |
| Sky void | ≥83 | Open horizon; `void_beam` over Audience/Core |

Each maze cell is an **open well** framed by Orde walls with arcade openings. `NONE` cells stamp crazy hallway archetypes (`LabyrinthHallways`). Chambers claim multi-cell footprints: CORRIDOR 8 / ROOM 16 / SUITE 24 / NAVE 24–32. Zone arc: Arrival → Bleed → Orde → Approach → Core/Audience.

## Configuratie forms

Cycle with **sneak-use** inside the Labyrinth:

| Vorm | Effect |
|------|--------|
| SCHARNIER | Leave (outside: enter) |
| WIEL | Rotate ROTARY walkways |
| SLEUTEL_A/B/C | Open Zegel / Masker / Koord gates (needs matching relic) |
| KETTING | Root hunting Gebondenen (labyrinth-only) |

Relics spawn once per FORGE / ARCHIVE / CHAPEL hub visit.

## Room types

ARRIVAL (3×3 nave) · CORE (2×2) · **AUDIENCE (3×3 De Ruit + sky beam)** · WELL_ATRIUM · BRIDGE_NODE · CLOISTER · DEPTH_SHAFT · ATRIUM · HOST_HALL · FORGE · ARCHIVE · CHAPEL · GATE · NICHE · **ROTARY** · **ECHO_LOOP** · **LIMINAL_*** (Bleed only, sparse) · CHAMBER_PAD · `NONE` → hallway archetype

## Attitude AI

`GASTVRIJ` → `GEALARMEERD` → `JACHT`. No auto-agro on sight. Kill escalates nearby Orde; gold/denier_or reconciliation via Gastheer/Notaris/Archivaris/Needleguy (Naaldpriester).

**Needleguy** (`needleguy`): Orde priest frenemy — Blockbench model 2.5 blocks, SOCIAL tier with chapel/cloister/audience spawn bias; right-click blessing for deniers/gold.

## Chase

When Orde alarmed/hostile, or near GATE/ROTARY: Rammelaar blinks ahead except on a seed-stable safe wall (`RammelaarChaseHandler.safeWallFlag`).

## Architect combat

Phase pulses + summons (Rammelaar/Stille) + Core arena shifts when `shiftingEnabled` (phase 3). Gate hubs also get rare ring pulses. Loot: Configuratie + deniers.

## Config (`millenaire-server.toml` → `labyrinth`)

- `enabled`, `allowCreativeEntry`, `requireFallenKingTag`
- `hostileCapPerPlayer`, `frenemyInteractions`, `arrivalRespawn`, `shiftingEnabled`
- `atmosphere` = `COLD` (default) \| `CLASSIC`
- `puzzlesEnabled`, `chaseEnabled`, `loopRoomsEnabled`, `vaultEnabled`, `configCombatEnabled`, `liminalRoomsEnabled`
- `layoutVersion` (default 3) — informational; generation uses `LabyrinthKeys.LAYOUT_VERSION`

## Materials (logical palette)

`LabyrinthMaterials` drives **all** well/corridor fill: stone/panel/bricks/riveted/bars/grate/spikes/hooks/lanterns/arch/parapet/gallery_rail/void_beam + bleed/ruit accents.

**Block catalog:** [`ORDE_BLOCK_CATALOG.md`](ORDE_BLOCK_CATALOG.md). Wellgrid notes: [`LABYRINTH_WELLGRID_V3.md`](LABYRINTH_WELLGRID_V3.md).

## Audio

Registered events `millenaire:labyrinth.{drone,metal,whisper,chase_stinger,ruit_drone}` — vanilla placeholders; see `docs/LABYRINTH_AUDIO_SLOTS.md`.

## Commands

`/millenaire labyrinth enter|leave|status|resetdim|spawn <kind>|vorm <id>|relic <zegel|masker|koord>` (permission 2)

## Quests

- `fallenking_11_thelabyrinth` — receive Configuratie, explore Core
- `fallenking_12_theconfiguration` — Seal gift, defeat Architect
