# Labyrinth — local validation

Owner runs these locally (agents do not claim GameTest/CI success).

## Smoke

1. New world, creative, NeoForge 1.21.1 + Millénaire.
2. `/give @s millenaire:configuratie`
3. Right-click Configuratie → arrive at Arrival (Y≈65), soul lanterns, blackstone maze.
4. Interact with Scharnier on lodestone → return to overworld.
5. `/millenaire labyrinth enter` / `leave` / `status`.
6. `/millenaire labyrinth spawn gastheer` — right-click tip; hit twice → hostility.
7. Travel toward Core cell (~448,448) — Architect may appear, initially hospitable.
8. Die in Labyrinth with `labyrinth.arrivalRespawn=true` → respawn at Arrival.

## Config

`serverconfig/millenaire-server.toml` → `[labyrinth]`

## Performance

Ambient spawn interval 100 ticks; cap `hostileCapPerPlayer`. Maze fill is block sets only.
