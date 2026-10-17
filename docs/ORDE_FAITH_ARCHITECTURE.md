# De Orde — Overworld Faith Architecture (v1 draft)

**Status:** design draft paired with [`ORDE_FAITH_RELEASE_PLAN.md`](ORDE_FAITH_RELEASE_PLAN.md). APIs below are target shape; update this file when O0–O5 landen.

## Problem

Connect Labyrinth lore (Gebondenen, Configuratie, Spijkermeester) to **overworld villages** as a dark religion: periodic rituals, sacrifices, abductions — without replacing culture faiths or wiping populations.

## Layering

```
Culture Faith (norman/mayan/…)     ← festivals, pray goals, identity
        │
        ▼
Secret Order Chapter (order_orde)  ← infectieus; 1 chapter / village
        │
        ├── RitualCoordinator      ← soft / sacrifice / abduct outcomes
        │
        ├── OrdeCallService        ← Configuratie-resolved → schedule ritual
        │
        └── Labyrinth bridge       ← ABDUCTED → Pelgrim/Marionet (optional)
```

Orders remain **beside** culture faiths (existing Millénaire religion model). See `ReligionService`, `OrderDefinition`, `Chapter`, `RitualCoordinator`.

## Core types (target)

| Type | Role |
|------|------|
| `OrderDefinition` `order_orde` | Content template |
| `Chapter` | Village cell; `lastRitualGameTime` drives 2-day cooldown |
| `RitualDefinition` + `outcome` | `bless` / `sacrifice` / `abduct` |
| `VillagerRecord.AbductionState` | `NONE` / `ABDUCTED` (+ optional labyrinth link) |
| `OrdeCallService` | Configuration-resolved entry point |
| `OrdeConflictService` | Quest actionData (generalized from Last Flame) |

## Ritual flow

1. `ReligionPlanner.plan` (night edge) → soft `orde_nachtwake` or (if gates) hard `orde_spijkerliturgie`
2. `RitualCoordinator.scheduleFromPlan` — cooldown `cooldownDays * 24000`
3. GATHERING → presence at shrine → RUNNING → `succeed(outcome)`
4. Outcomes:
   - **bless:** devotion/cohesion/secrecy/tension (current path)
   - **sacrifice:** optional kill (`markVillagerKilled`) + spectacle; soft-cap
   - **abduct:** set `ABDUCTED`, discard entity, bridge to Labyrinth

## Configuration call

Triggers (any one):

1. Configuratie use in Overworld near chapter
2. Return via Scharnier with recent Labyrinth tag
3. Quest `actionData` `orde_configuration_solved`

→ `OrdeCallService.onConfigurationResolved` → force-schedule `orde_configuratie_roep` if chapter eligible.

## Integrity / missing

`VillageIntegrityChecker` must **not** treat `ABDUCTED` as ordinary missing (no dusk respawn). Panel copy: taken beyond the Scharnier.

## Config surface

- Reuse: `religionEnabled`, `ordersEnabled`, `religionSupernaturalEnabled`
- New `[orde]` block: see release plan §4.3

## Non-goals

- Culture-default Orde faith
- Hellraiser IP
- Configuratie morph theater
- Uncapped bi-daily kills

## Related

- [`ORDE_FAITH_RELEASE_PLAN.md`](ORDE_FAITH_RELEASE_PLAN.md)
- [`LABYRINTH_ARCHITECTURE.md`](LABYRINTH_ARCHITECTURE.md)
- Last Flame quests under `millenaire/quests/orders/`
- Faith/order code: `org.millenaire.faith.*`
