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

`GASTVRIJ` → `GEALARMEERD` → `JACHT`. Right-click tips while hospitable. No auto-agro on sight.

## Config (`millenaire-server.toml` → `labyrinth`)

- `enabled`, `allowCreativeEntry`, `requireFallenKingTag`
- `hostileCapPerPlayer`, `frenemyInteractions`, `arrivalRespawn`

## Commands

`/millenaire labyrinth enter|leave|status|spawn <kind>` (permission 2)
