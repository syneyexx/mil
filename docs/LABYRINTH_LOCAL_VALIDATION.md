# Labyrinth — local validation

Owner runs these locally (agents do not claim GameTest/CI success).

**Note:** Geometry/atmosphere changes require a **fresh Labyrinth dimension** (new world, or delete `millenaire_labyrinth` dim data). Old chunks will not regenerate.

## Smoke — Wave 1 (atmosphere / geometry)

1. New world, creative, NeoForge 1.21.1 + Millénaire.
2. `/give @s millenaire:configuratie`
3. Right-click Configuratie → Arrival **nave** (~24×24, Scharnier on dais, spawn south of pedestal, Y≈65). Fog should read **cold blue-grey** (unless `atmosphere=CLASSIC`). F3 debug: `Labyrinth v3` + `zone=ARRIVAL` (+ `hall=` outside hubs).
4. **Wellgrid:** open pits between Orde walls; walkable parapet crowns; arch doorways; Orde materials (not blackstone).
5. Most cells are crazy hallways (`SPIKE_TRENCH` / `CROSS_BRIDGE` / `VOID_WELL` / …), not forge/chapel boxes.
6. Far from Arrival: deep wells to vault grate/spikes; bridge nodes; De Ruit `void_beam` visible on open sky toward Core/Audience.

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
15. Die with `arrivalRespawn=true` → Arrival nave spawn (not 2.5,2.5).
16. **Layout v3 zones:** fly Arrival → Bleed → Orde → Approach → Core/Audience; F3 `zone=` + `hall=` changes.
17. **Audio:** headphones in Bleed ≤10s — drone/whisper/metal; near Audience — Ruit drone / void beam.
18. **Loops:** enter ECHO_LOOP and MIRROR_LOOP; escape after ≥1 fail; hint at 3–4 loops.
19. **Chase 2.0:** no blink in Arrival; telegraph particles before Rammelaar; safe wall learnable.
20. `/millenaire labyrinth resetdim` prints migration wipe instructions; `status` shows `v3`.
16. Quest path: fallenking_11 → fallenking_12 (Architect fallen tag).

## Smoke — Wave 6 (persistence / frenemy polish)

17. Open a rotary to E/W and a gate; leave the area until chunks unload; return — walkways/gates still open.
18. At GATE: talk to Gids → chase-safe direction hint.
19. Spiegelgast right-click → teleport ~1 hub toward Core; Echo → loop tip; Tweeling → place-swap + invis.
20. Schatter will buy a relic for deniers (tempting bad bargain).

## Smoke — Wave 7 (liminal furnished rooms)

21. Walk the maze past manhattan ~5: enter wood-floored **bedroom / kitchen / study / parlor / nursery** cells (not only blackstone).
22. Spot wrongness: chains over beds, spikes in kitchens, false doors onto bars, crying under carpet.
23. Enter message appears once per room type change; occasional door-close whisper.
24. ECHO_LOOP hubs look like furnished parlors; forward still loops, reverse escapes.
25. Config `liminalRoomsEnabled=false` falls back liminal cells to niches (fresh chunks).

## Smoke — Wave 8 (Audiëntie / De Ruit)

26. Reach Core (~448, ~448), walk **north** one cell into the tall nave.
27. Floating ice/crying/gold lozenge (**De Ruit**) hangs above the lodestone dais.
28. First visit: darkness + glow, audience messages, tag `labyrinth_audience_ruit`, Orde reputation bump.
29. Return visit: quieter acknowledgment. No Leviathan/Hellraiser names.

## Admin helpers

- `/millenaire labyrinth enter|leave|status`
- `/millenaire labyrinth spawn <kind>`
- `/millenaire labyrinth vorm <scharnier|wiel|sleutel_a|sleutel_b|sleutel_c|ketting>`
- `/millenaire labyrinth relic <zegel|masker|koord>`

## Config

`serverconfig/millenaire-server.toml` → `[labyrinth]`

## Performance

Ambient spawn interval 100 ticks; chase 45; relic check 40; gate shift 200. Caps via `hostileCapPerPlayer`. Maze fill is block sets only.
