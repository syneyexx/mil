# Labyrinth — local validation

Owner runs these locally (agents do not claim GameTest/CI success).

## Smoke

1. New world, creative, NeoForge 1.21.1 + Millénaire.
2. `/give @s millenaire:configuratie`
3. Right-click Configuratie → arrive at Arrival (Y≈65), soul lanterns, blackstone maze.
4. Interact with Scharnier on lodestone → return to overworld.
5. `/millenaire labyrinth enter` / `leave` / `status`.
6. `/millenaire labyrinth spawn smid` — hold damaged sword + iron/deniers → repair.
7. `/millenaire labyrinth spawn gastheer` — heal/sat; spawn gids → direction tip.
8. Hit a Gebondene twice → JACHT; kill → nearby Orde hunts; reconcile with gold offhand on Gastheer.
9. Travel toward Core (~448, ~448) — Architect appears hospitable; attack → phased fight + summons; phase 3 shifts bars if `shiftingEnabled`.
10. Die in Labyrinth with `arrivalRespawn=true` → respawn at Arrival.
11. Fog should feel tight/crimson; ash/soul particles occasionally.

## Config

`serverconfig/millenaire-server.toml` → `[labyrinth]`

## Performance

Ambient spawn interval 100 ticks; cap `hostileCapPerPlayer`. Maze fill is block sets only.
