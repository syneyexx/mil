# De Orde — Overworld Faith Architecture (v1.1 draft)

**Status:** design draft paired with [`ORDE_FAITH_RELEASE_PLAN.md`](ORDE_FAITH_RELEASE_PLAN.md). APIs below are target shape; update this file when O0–O5 landen.

## Problem

Connect Labyrinth lore (Gebondenen, Configuratie, Spijkermeester) to **overworld villages** as a dark religion: periodic rituals, sacrifices, abductions — without replacing culture faiths, wiping populations, or saturating the server tick.

## Layering

```
Culture Faith (norman/mayan/…)     ← festivals, pray goals, identity
        │
        ▼
Secret Order Chapter (order_orde)  ← infectieus; 1 chapter / village
        │
        ├── RitualCoordinator      ← soft / sacrifice / abduct_attempt
        │
        ├── OrdeCallService        ← Configuratie-resolved (event-only)
        │
        ├── OrdeAbductionAttempt   ← 75% player chase / 25% NPC; budgeted tick
        │
        └── Labyrinth bridge       ← GRABBED / ABDUCTED → dim (optional)
```

Orders remain **beside** culture faiths (existing Millénaire religion model). See `ReligionService`, `OrderDefinition`, `Chapter`, `RitualCoordinator`.

## Core types (target)

| Type | Role |
|------|------|
| `OrderDefinition` `order_orde` | Content template |
| `Chapter` | Village cell; `lastRitualGameTime` drives 2-day cooldown |
| `RitualDefinition` + `outcome` | `bless` / `sacrifice` / `abduct_attempt` |
| `OrdeAbductionAttempt` | State machine for one hunt (global cap 1) |
| `VillagerRecord.AbductionState` | `NONE` / `ABDUCTED` (+ optional labyrinth link) |
| `OrdeCallService` | Configuration-resolved entry (no tick poll) |
| `OrdeConflictService` | Quest actionData (generalized from Last Flame) |

## Ritual flow

1. `ReligionPlanner.plan` (night edge) → soft `orde_nachtwake` or (if gates) hard `orde_spijkerliturgie`
2. `RitualCoordinator.scheduleFromPlan` — cooldown `cooldownDays * 24000`
3. GATHERING → presence at shrine → RUNNING → `succeed(outcome)`
4. Outcomes:
   - **bless:** devotion/cohesion/secrecy/tension (current path)
   - **sacrifice:** optional kill + spectacle; soft-cap
   - **abduct_attempt:** start `OrdeAbductionAttempt` (see below)

## Abduction attempt (v1.1)

```
onConfigurationResolved / ritual abduct_attempt
        │
        ▼
   roll target (playerAbductChance = 0.75)
        │
        ├── 75% player in range → CHASING (≤3 hunters)
        │         ├── run beyond radius / timeout → ESCAPED_RUN
        │         ├── all hunters dead → ESCAPED_KILL
        │         └── grab channel complete → GRABBED → Labyrinth teleport
        │
        └── 25% (or player unavailable) → random eligible NPC
                  └── success → ABDUCTED + bridge / soft chronicle
```

Player branch is a **fight-or-flight encounter**, never an unavoidable teleport.

## Tick budget (non-negotiable)

| Path | When it runs | Cap |
|------|--------------|-----|
| Planning | Religion night-edge / deadlines only | Shared religion planner |
| Configuratie call | Item use / Scharnier / quest actionData | O(range) chapter lookup |
| Chase tick | Only while `OrdeAbductionAttempt` active | 1 global attempt; ≤3 hunters; AI every N ticks |
| Hard pressure | Tick governor HARD | Block **new** attempts; let active expire |

No per-tick village scans. No unbounded hunter swarms. VFX only if player ≤ 48 blocks.

## Configuration call

Triggers (any one — all event-driven):

1. Configuratie use in Overworld near chapter
2. Return via Scharnier with recent Labyrinth tag
3. Quest `actionData` `orde_configuration_solved`

→ `OrdeCallService.onConfigurationResolved` → schedule ritual / attempt if chapter eligible.

## Integrity / missing

`VillageIntegrityChecker` must **not** treat `ABDUCTED` as ordinary missing (no dusk respawn). Panel copy: taken beyond the Scharnier.

## Config surface

- Reuse: `religionEnabled`, `ordersEnabled`, `religionSupernaturalEnabled`
- New `[orde]` block: see release plan §4.3 (`playerAbductChance`, hunter/timeout caps, pressure block)

## Player city founding

Wizard faith step offers three choices:

| Choice | Packet `faithChoice` | Effect |
|--------|----------------------|--------|
| Own faith | `custom` | `startPlayerFaithInstance` |
| **Cenobites** | `orde` | `startOrdeFaithInstance` + force-seed `order_orde` chapter |
| Culture NPC faith | `culture` | Keep spawn default |

Faith template: `millenaire:orde` (`faiths/orde.json`). UI label: Cenobites.

## Non-goals

- Culture-default Orde faith for all NPC villages
- Hellraiser IP product names beyond player-facing “Cenobites” label (owner choice)
- Configuratie morph theater
- Uncapped bi-daily kills
- Instant unavoidable player teleport
- Per-tick Orde world scans

## Related

- [`ORDE_FAITH_RELEASE_PLAN.md`](ORDE_FAITH_RELEASE_PLAN.md)
- [`LABYRINTH_ARCHITECTURE.md`](LABYRINTH_ARCHITECTURE.md)
- Last Flame quests under `millenaire/quests/orders/`
- Faith/order code: `org.millenaire.faith.*`
- Chase reference: `RammelaarChaseHandler` (budgeted blink pattern)
