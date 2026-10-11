# LM Companion Architecture

**Package root:** `org.millenaire.companion`  
**Delivery:** Inside Millenaire (same jar). Not a standalone addon.  
**Activation:** Opt-in only via `/millenaire companion summon|bind|dismiss`. Never auto-spawned.

## Overview

The Companion is a bonded `MillVillager` driven by a local LM (LM Studio or Ollama) plus a whitelist of intents. Millenaire goals, village economy, and NPC mind memory are the body and life; the LM chooses speech and high-level intents on a budgeted cadence.

```
Player ──chat/events──► CompanionBrain ──async HTTP──► Local LM
                              │
                              ├── CompanionWorldSensor (cached)
                              ├── NpcMind + travel/events
                              ▼
                     CompanionActionExecutor
                              │
              ┌───────────────┼────────────────┐
              ▼               ▼                ▼
        GoalScheduler   CompanionWorldActor   Capability modules
        (Millenaire)    (FakePlayer uses)     (TACZ/Create/…)
```

## Delivery decision

Built **in Millenaire** under `org.millenaire.companion` (+ soft `capability` modules). A separate addon would need a huge Millenaire API for goals/minds/villages. Package boundaries keep a future extract optional.

## Core components

| Component | Role |
|-----------|------|
| `CompanionBondSavedData` | Persistent `playerUuid → bond` (entity UUID, stance, culture, village) |
| `CompanionService` | Summon / dismiss / bind / status; never runs without bond |
| `CompanionBrain` | Cadence + events; builds prompts; applies LM replies |
| `CompanionPromptBuilder` / `CompanionReplyParser` | Companion JSON contract |
| `CompanionActionCatalog` / `CompanionActionExecutor` | Whitelist intents → server actions |
| `CompanionWorldActor` | FakePlayer-backed USE_ITEM / USE_BLOCK / ATTACK |
| `CompanionWorldSensor` | Cached deterministic world facts |
| `CompanionTravelJournal` / `CompanionSharedEventLog` | Episodic learning |
| `CompanionTickBudget` | Governor-gated main-thread work |
| `capability.*` | Soft-loaded mod modules (TACZ, Waystones, Iron's, Ad Astra, Create) |

## Opt-in lifecycle

1. Nothing active until `/millenaire companion summon` or `bind`.
2. Summon finds nearest village, spawns/binds a `MillVillager` with companion flag + permanent hire-like bond.
3. `dismiss` removes the entity; bond + mind persist for resummon.
4. Cap: 1 companion per player (config).

## LM contract (reply JSON)

```json
{
  "thought": "",
  "say": "",
  "mood": "calm",
  "new_memories": [],
  "opinion_delta": {"trust": 0, "fear": 0, "affection": 0},
  "stance": "FREE_ROAM",
  "intents": [{"type": "FOLLOW_PLAYER", "target": ""}]
}
```

Intents must be catalog types. Unknown types are dropped with a feedback line for the next prompt.

## Player-parity layers

| Layer | Mechanism |
|-------|-----------|
| A Millenaire body | Goals, inventory, speech, village |
| B Generic player acts | `CompanionWorldActor` FakePlayer |
| C Mod modules | Soft `ModList` capabilities |
| D Out of scope | Client-only GUI macros |

## Tick budget contract

- LM HTTP: **never** on the server thread.
- Brain main-thread apply/sensor: **≤ 0.5 ms** p95 when it runs; otherwise skip via `CompanionTickBudget` / `ServerTickGovernor.hasTime()`.
- Heartbeat: 20–40s near player; slower far away; skip if request in-flight.
- Proactive speech: rate-limited (config lines/min).
- Companions stay LOD **NEAR** when bonded (same class as hired escort).
- Sensors cache 1–2s; no extra pathfinding stack.

## Local models

Accepted base URLs: LM Studio (`:1234` / lmstudio host) and Ollama (`:11434` / ollama host), loopback or private LAN. Cloud hosts rejected.

## Safety

- No invented prices/coords/items in prompts.
- Existing NPC chat OOC / sexual / child rules apply.
- Soft mod load only; missing mods disable that capability.
