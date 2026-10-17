# Het Labyrint → Revival-achtig — Wave Plan

Doel: Het Millénaire-Labyrint dichter bij de *ervaring* van Hellraiser: Revival brengen **zonder Hellraiser-IP**.

Status: **Waves 1–5 geïmplementeerd** op branch `cursor/labyrinth-revival-waves-d8bb` (bovenop baseline Labyrinth PR #18).

## Ontwerpprincipes (vast)

1. Mood > 1:1 kopie
2. Configuratie groeit: portaal → vormen → puzzels → ketting-pulse
3. Orde blijft ceremonieel (frenemy-kern)
4. Deterministisch & tick-vriendelijk
5. Elke wave speelbaar / config-gated
6. Geen franchise-namen/assets

## Wave status

| Wave | Focus | Status |
|------|--------|--------|
| 0 | Plan + acceptatiecriteria | Done |
| 1 | Koude atmosfeer, smallere gangen, mezzanine hubs, vault band | Done |
| 2 | Configuratie-vormen, ROTARY, relic gates, relic caches | Done |
| 3 | ECHO_LOOP, Rammelaar chase | Done |
| 4 | Multi-band shafts, gate shifting, vault routes | Done |
| 5 | KETTING combat, quest 12, lang/docs/commands | Done |
| 6 | Puzzle persist on chunk load; Gids chase hint; Spiegelgast/Echo/Tweeling services; relic art | Done |
| 7 | Liminal furnished rooms (bedroom/kitchen/study/parlor/nursery) + enter whispers | Done |

## Config defaults (owner-approved)

- `atmosphere=COLD`
- `puzzlesEnabled=true`, `chaseEnabled=true`, `loopRoomsEnabled=true`
- `vaultEnabled=true`, `configCombatEnabled=true`, `liminalRoomsEnabled=true`
- Chase: nabij GATE/ROTARY **of** na Orde-escalatie
- Nieuwe relic items: Zegel / Masker / Koord

## Validatie

Zie [`LABYRINTH_LOCAL_VALIDATION.md`](LABYRINTH_LOCAL_VALIDATION.md). Agents claimen geen in-game smoke; `./gradlew compileJava` is de agent-check.

## Expliciet buiten scope (later)

- Letterlijke Hellraiser-IP
- Full custom Blockbench gore meshes
- AAA shaders
- Vervangen frenemy-Orde door pure combat-cenobites
