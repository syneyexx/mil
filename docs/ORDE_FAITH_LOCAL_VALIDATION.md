# De Orde — Local Validation (owner-run)

Agent does **not** execute these checks. Run after each wave when implementing.

## Prerequisites

- NeoForge 1.21.1 Millénaire build
- Fresh or known-good world
- Server config: `religionEnabled=true`, `ordersEnabled=true`
- For hard path: `religionSupernaturalEnabled=true` + `[orde]` flags per wave

## Smoke — O0

1. Boot world; log shows Orde order + rituals loaded; faith `millenaire:orde` registered.
2. Village create wand → faith step shows three buttons: own faith / **Cenobites** / culture faith.
3. Found with Cenobites: city `faithId=orde`, Orde chapter seeded, shrine queued; message `faith_orde_started`.
4. Found with culture / custom still works (regression).
5. `/millenaire` religion/orde debug (when added): list chapters; force-seed Orde on test village.
6. Mark villager abducted via debug; confirm no dusk respawn; panel shows taken state.

## Smoke — O1

1. Advance time ≥2 days; Orde chapter schedules soft nacht wake near dusk.
2. Participants gather at shrine; ritual succeeds; chronicle `RITUAL`.
3. With hard flag: one Spijkerliturgie; at most one hard loss; second attempt within soft-cap fails/skips.

## Smoke — O2 (75/25 + escape + ticks)

1. Place Orde chapter; hold Configuratie; use near village → attempt starts (event, not tick poll).
2. Force `playerAbductChance=1.0`: hunters spawn (≤3); **run away** past escape radius → `ESCAPED_RUN`; no teleport.
3. Force another attempt: **kill all hunters** → `ESCAPED_KILL`.
4. Force grab (stand still / creative allow): teleport to Labyrinth Arrival if dim enabled.
5. Force `playerAbductChance=0.0`: random NPC taken; player not chased.
6. Tick spot-check: during chase, `/millenaire dev tick status` (or board) — no sustained Post-tick overrun vs idle; max 1 active attempt.

## Smoke — O3

1. NPC abduct + `labyrinth.enabled=true` → Pelgrim/Marionet in Labyrinth with link.
2. Labyrinth disabled → ABDUCTED + chronicle only; no crash.
3. Religion tension > 20 eventually yields story pressure (EventDirector).

## Smoke — O4

1. Quest line whisper→expose stops hard rituals / new attempts.
2. Join path grants membership; player can attend ritual.
3. Last Flame village still seeds Last Flame only (one chapter invariant).

## Smoke — O5 / ship

1. `./gradlew compileJava` green.
2. Defaults match README (`playerAbductChance=0.75`, hunter caps, supernatural/hardSacrifice documented).
3. No Hellraiser-IP strings in `en_us` / `nl_nl`.
4. Full §2 DoD from release plan checked off (incl. §1.5 tick-budget).

## Performance spot-check

- Idle Orde chapter (no attempt): indistinguishable from normal religion night work.
- Active player chase: ≤3 hunter entities; AI not every tick; HARD pressure blocks **new** attempts.
- No full-village entity scan every tick for configuration call (trigger-only).
