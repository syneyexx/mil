# NPC Dialogue Implementation Checklist

**Baseline HEAD:** `bb63126d8d673e4a439d5cc7d4f4bd3b9441b6df`  
**Branch:** `cursor/universal-npc-dialogue-fe4d`  
**Status legend:** Pending · In progress · Implemented (not runtime verified) · Blocked

Compilation, CI, gameplay tests, and live AI verification are **not** executed by this agent. User performs local validation — see `NPC_DIALOGUE_LOCAL_VALIDATION.md`.

---

## Phase 0 — Baseline and design

| Item | Status |
|------|--------|
| Repository inventory | Implemented (not runtime verified) |
| Chat-flow analysis | Implemented (not runtime verified) |
| Confirmed issue inventory | Implemented (not runtime verified) |
| Migration strategy | Implemented (not runtime verified) |

### Confirmed issues

| ID | Finding | Resolution |
|----|---------|------------|
| I1 | Gossip scheduler already called from `VillageManager.tick` | Preserved |
| I2 | MillVillager-only resolution | Universal adapters |
| I3 | `handleSend` synthesized sessions | Open-authorized session required |
| I4 | Silent rejects froze UI | System/reject lines on all failure paths |
| I5 | Stale async replies | `sessionId` + `requestGeneration` |
| I6 | Prompt substring truncation | Deterministic budgeting; speech preserved |
| I7 | Semaphore drain on config change | Atomic in-flight counter |
| I8 | Nearest-entity H-key ambiguity | Crosshair-only targeting |

---

## Phase 1 — Stabilize existing chat

| Item | Status |
|------|--------|
| Session required before send | Implemented (not runtime verified) |
| Stale callback / request generation | Implemented (not runtime verified) |
| Silent reject → client feedback | Implemented (not runtime verified) |
| Prompt budgeting | Implemented (not runtime verified) |
| Model semaphore accounting | Implemented (not runtime verified) |
| Model fallback per entity | Implemented (not runtime verified) |
| Gossip wiring verification | Implemented (already wired) |
| UI pending-state unlock | Implemented (not runtime verified) |

## Phase 2 — Universal core

| Item | Status |
|------|--------|
| Adapter interface + registry | Implemented (not runtime verified) |
| Dialogue target / policy / identity | Implemented (not runtime verified) |
| Migrate Millénaire into adapter | Implemented (not runtime verified) |
| Single orchestration façade | Implemented (not runtime verified) |

## Phase 3 — Vanilla and generic NPCs

| Item | Status |
|------|--------|
| Villager / trader / illager / piglin | Implemented (not runtime verified) |
| Golem / allay / hostile / nonverbal | Implemented (not runtime verified) |
| Generic LivingEntity fallback | Implemented (not runtime verified) |
| Player exclusion | Implemented (not runtime verified) |

## Phase 4 — Interaction and UI

| Item | Status |
|------|--------|
| Universal H-key (crosshair only) | Implemented (not runtime verified) |
| Preserve Millénaire sneak-chat | Implemented (not runtime verified) |
| Open/line payloads for generic names | Implemented (not runtime verified) |
| Transcript wrapping / unlock | Implemented (not runtime verified) |

## Phase 5 — Persistence and intelligence

| Item | Status |
|------|--------|
| Stable UUID identity | Implemented (not runtime verified) |
| Transient mind pruning | Implemented (not runtime verified) |
| Memory provenance in prompts | Implemented (not runtime verified) |
| Persona via adapters | Implemented (not runtime verified) |
| Legacy save compatibility | Implemented (not runtime verified) |

## Phase 6 — Security and performance

| Item | Status |
|------|--------|
| Packet bounds / reject feedback | Implemented (not runtime verified) |
| Rate-limit feedback | Implemented (not runtime verified) |
| Config toggles | Implemented (not runtime verified) |
| No tick-time model dispatch | Implemented (not runtime verified) |
| Line-of-sight validation | Implemented (not runtime verified) |

## Phase 7 — Docs and static audit

| Item | Status |
|------|--------|
| Architecture doc | Implemented (not runtime verified) |
| Local validation guide | Implemented (not runtime verified) |
| Static review | Implemented (not runtime verified) |
| Final report | See PR / agent summary |
