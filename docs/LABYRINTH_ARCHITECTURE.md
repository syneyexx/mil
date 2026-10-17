# Labyrinth (Het Labyrint) — Architecture

## Overview

Custom Millénaire dimension `millenaire:labyrinth` with a deterministic cellular maze, ceremonial **Gebondenen** (25 kinds, hospitable until provoked), entry via **Configuratie**, exit via **Scharnier** / Configuratie.

**Revival-achtige uitbreiding:** Waves 1–5 implemented per [`LABYRINTH_REVIVAL_WAVES.md`](LABYRINTH_REVIVAL_WAVES.md). No Hellraiser IP; own lore (Configuratie forms, Orde relics, Architect).

## Packages

| Package | Role |
|---------|------|
| `org.millenaire.labyrinth` | Keys, bootstrap, death/progress handlers, atmosphere enum |
| `...gen` | Maze + chunk generator + room stamps + shifter |
| `...portal` | Configuratie (+ vorm), Scharnier, teleporter |
| `...puzzle` | Rotary, gates, echo loop, relic caches, puzzle SavedData |
| `...chase` | Rammelaar corridor chase |
| `...combat` | Configuratie KETTING pulse |
| `...entity` | GebondeneEntity / Kind / Attitude / Orde SavedData / Architect |
| `...spawn` | Ambient + gate + Architect population |
| `...client` | Model, renderer, fog, ambient |
| `...command` | `/millenaire labyrinth …` |

## Floor bands

| Band | Y | Role |
|------|---|------|
| Vault | 48–56 | Lower routes beyond manhattan distance 12 from Arrival |
| Primary | 64–73 | Arrival, main maze, puzzles |
| Mezzanine | 74–82 | Hub overlooks (HOST_HALL / GATE / CHAPEL stamps) |

Corridors use `CORRIDOR_MARGIN` inset + spikes/bars for claustrophobia. Cold palette mixes packed ice into walls/ceilings.

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

ARRIVAL · CORE · HOST_HALL · FORGE · ARCHIVE · CHAPEL · GATE · NICHE · **ROTARY** · **ECHO_LOOP**

## Attitude AI

`GASTVRIJ` → `GEALARMEERD` → `JACHT`. No auto-agro on sight. Kill escalates nearby Orde; gold/denier_or reconciliation via Gastheer/Notaris/Archivaris.

## Chase

When Orde alarmed/hostile, or near GATE/ROTARY: Rammelaar blinks ahead except on a seed-stable safe wall (`RammelaarChaseHandler.safeWallFlag`).

## Architect combat

Phase pulses + summons (Rammelaar/Stille) + Core arena shifts when `shiftingEnabled` (phase 3). Gate hubs also get rare ring pulses. Loot: Configuratie + deniers.

## Config (`millenaire-server.toml` → `labyrinth`)

- `enabled`, `allowCreativeEntry`, `requireFallenKingTag`
- `hostileCapPerPlayer`, `frenemyInteractions`, `arrivalRespawn`, `shiftingEnabled`
- `atmosphere` = `COLD` (default) \| `CLASSIC`
- `puzzlesEnabled`, `chaseEnabled`, `loopRoomsEnabled`, `vaultEnabled`, `configCombatEnabled`

## Commands

`/millenaire labyrinth enter|leave|status|spawn <kind>|vorm <id>|relic <zegel|masker|koord>` (permission 2)

## Quests

- `fallenking_11_thelabyrinth` — receive Configuratie, explore Core
- `fallenking_12_theconfiguration` — Seal gift, defeat Architect
