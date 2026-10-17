# De Orde — Local Validation (owner-run)

Agent does **not** execute these checks. Run after each wave when implementing.

## Prerequisites

- NeoForge 1.21.1 Millénaire build
- Fresh or known-good world
- Server config: `religionEnabled=true`, `ordersEnabled=true`
- For hard path: `religionSupernaturalEnabled=true` + `[orde]` flags per wave

## Smoke — O0

1. Boot world; log shows Orde order + rituals loaded.
2. `/millenaire` religion/orde debug (when added): list chapters; force-seed Orde on test village.
3. Mark villager abducted via debug; confirm no dusk respawn; panel shows taken state.

## Smoke — O1

1. Advance time ≥2 days; Orde chapter schedules soft nacht wake near dusk.
2. Participants gather at shrine; ritual succeeds; chronicle `RITUAL`.
3. With hard flag: one Spijkerliturgie; at most one hard loss; second attempt within soft-cap fails/skips.

## Smoke — O2

1. Place Orde chapter; hold Configuratie; use near village.
2. Within seconds: `orde_configuratie_roep` scheduled; message shown.
3. Quest actionData path also schedules call.

## Smoke — O3

1. Complete abduction with `labyrinth.enabled=true` → Gebondene Pelgrim/Marionet appears in Labyrinth with link.
2. With labyrinth disabled → ABDUCTED + chronicle only; no crash.
3. Religion tension > 20 eventually yields story pressure (EventDirector).

## Smoke — O4

1. Quest line whisper→expose stops hard rituals.
2. Join path grants membership; player can attend ritual.
3. Last Flame village still seeds Last Flame only (one chapter invariant).

## Smoke — O5 / ship

1. `./gradlew compileJava` green.
2. Defaults match README (supernatural/hardSacrifice documented).
3. No Hellraiser-IP strings in `en_us` / `nl_nl`.
4. Full §2 DoD from release plan checked off.

## Performance spot-check

- Orde ritual night: Post-tick board not systematically over ceiling with 1 player near one chapter.
- No full-village entity scan every tick for configuration call (chunk-range query only on trigger).
