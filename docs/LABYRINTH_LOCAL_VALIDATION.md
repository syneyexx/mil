# Labyrinth — local validation

Owner runs these locally (agents do not claim GameTest/CI success).

**Note:** Geometry/atmosphere changes require a **fresh Labyrinth dimension** (new world, or delete `millenaire_labyrinth` dim data). Old chunks will not regenerate.

## Smoke — Wave 1 (atmosphere / geometry)

1. New world, creative, NeoForge 1.21.1 + Millénaire.
2. `/give @s millenaire:configuratie`
3. Right-click Configuratie → Arrival (Y≈65). Fog should read **cold blue-grey** (unless `atmosphere=CLASSIC`).
4. Corridors feel narrower; spikes/chains denser; packed ice accents on ceilings.
5. Find HOST_HALL / GATE / CHAPEL hub → ladder to mezzanine (~Y75).
6. Far from Arrival (manhattan ≥12 cells): vault shaft at GATE/CORE centers → Y≈49 landing.

## Smoke — Wave 2 (Configuratie puzzles)

7. Sneak-use Configuratie → cycle vorm; name/tooltip update.
8. `/millenaire labyrinth vorm wiel` at a ROTARY hub (lodestone) → walkways flip N/S ↔ E/W.
9. Visit FORGE / ARCHIVE / CHAPEL → pick up Zegel / Masker / Koord once each.
10. At GATE: vorm `sleutel_a` + Zegel → bars clear; repeat for masker/koord.

## Smoke — Wave 3 (loop / chase)

11. Enter ECHO_LOOP hub → walk toward north wall → room resets; after a few loops, face south and walk out → escape message.
12. Near GATE with chase on: Rammelaar blinks ahead; one yaw direction stays clear (learn by failing).
13. Escalate Orde (hit twice) → chase intensifies; Fluitende / gold reconcile still works.

## Smoke — Wave 4–5 (vault / combat / quest)

14. Architect at Core (~448,~448): phases, summons, ring shifts; KETTING vorm pulses hunters.
15. Die with `arrivalRespawn=true` → Arrival.
16. Quest path: fallenking_11 → fallenking_12 (Architect fallen tag).

## Smoke — Wave 6 (persistence / frenemy polish)

17. Open a rotary to E/W and a gate; leave the area until chunks unload; return — walkways/gates still open.
18. At GATE: talk to Gids → chase-safe direction hint.
19. Spiegelgast right-click → teleport ~1 hub toward Core; Echo → loop tip; Tweeling → place-swap + invis.
20. Schatter will buy a relic for deniers (tempting bad bargain).

## Admin helpers

- `/millenaire labyrinth enter|leave|status`
- `/millenaire labyrinth spawn <kind>`
- `/millenaire labyrinth vorm <scharnier|wiel|sleutel_a|sleutel_b|sleutel_c|ketting>`
- `/millenaire labyrinth relic <zegel|masker|koord>`

## Config

`serverconfig/millenaire-server.toml` → `[labyrinth]`

## Performance

Ambient spawn interval 100 ticks; chase 45; relic check 40; gate shift 200. Caps via `hostileCapPerPlayer`. Maze fill is block sets only.
