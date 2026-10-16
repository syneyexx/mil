# Labyrinth (Het Labyrint) — Architecture

## Overview

Custom Millénaire dimension `millenaire:labyrinth` with a deterministic cellular maze, ceremonial **Gebondenen** (25 kinds, hospitable until provoked), entry via **Configuratie**, exit via **Scharnier** / Configuratie.

## Packages

| Package | Role |
|---------|------|
| `org.millenaire.labyrinth` | Keys, bootstrap, death handler |
| `...gen` | Maze + chunk generator + room stamps |
| `...portal` | Configuratie, Scharnier, teleporter |
| `...entity` | GebondeneEntity / Kind / Attitude / Orde SavedData |
| `...spawn` | Ambient + gate + Architect population |
| `...client` | Model, renderer, fog |
| `...command` | `/millenaire labyrinth …` |

## Dimension stack

- `data/millenaire/dimension_type/labyrinth.json`
- `data/millenaire/dimension/labyrinth.json` → generator type `millenaire:labyrinth`
- `data/millenaire/worldgen/biome/labyrinth.json`
- Codec registered in `LabyrinthBootstrap`

## Attitude AI

`GASTVRIJ` → `GEALARMEERD` → `JACHT`. No auto-agro on sight. Kill escalates nearby Orde; gold/denier_or reconciliation via Gastheer/Notaris/Archivaris.

## Frenemy services (right-click while GASTVRIJ)

Smid repair · Gastheer rest · Notaris contract buffs · Gids Core hint (~75% honest) · Schatter buyout · Sleuteldrager night vision · Archivaris lore · Naaister heal · Pelgrim lead · Blinde/Poort/Ketting/Fluit/Dans/Getuige/Spijker utilities · Architect measure (pre-fight).

## Architect combat

Phase pulses + summons (Rammelaar/Stille) + Core arena shifts when `shiftingEnabled` (phase 3). Loot: Configuratie + deniers.

## Config (`millenaire-server.toml` → `labyrinth`)

- `enabled`, `allowCreativeEntry`, `requireFallenKingTag`
- `hostileCapPerPlayer`, `frenemyInteractions`, `arrivalRespawn`, `shiftingEnabled`

## Commands

`/millenaire labyrinth enter|leave|status|spawn <kind>` (permission 2)
