# Universal NPC Dialogue Architecture

## Overview

One dialogue engine (`NpcChatService`) serves all NPC types. Entity diversity lives in **adapters** registered with `NpcDialogueAdapterRegistry`. There is no second chat implementation.

## Core services

| Component | Responsibility |
|-----------|----------------|
| `NpcChatService` | Sessions, open/send/close, model dispatch, memory updates, client sync, lifecycle cleanup |
| `NpcDialogueAdapterRegistry` | Deterministic adapter resolution by priority + entity capability |
| `NpcDialogueAdapter` | Identity, policy, refusal, persona/context, optional speech broadcast |
| `NpcDialogueTarget` | Validated server-side participant |
| `NpcMind` / `NpcMindSavedData` | Persistent memory (UUID-keyed); legacy Millénaire minds remain readable |
| `NpcModelClient` | Async OpenAI-compatible HTTP; in-flight concurrency accounting |
| `NpcPromptBuilder` | System prompts + deterministic user-prompt budgeting |
| `NpcGossipScheduler` | Daily same-culture rumor propagation (Millénaire) |

## Adapter resolution precedence

1. Explicit high-priority type adapter (Millénaire villagers = 1000)
2. Vanilla specializations (villager, trader, illager, piglin, golem, allay, intelligent hostile)
3. Nonverbal animal / monster atmospheric adapters
4. Generic `LivingEntity` fallback (modded / unknown registered living entities)
5. Unsupported → client feedback, no session

Players are never dialogue targets. Resolution caches by entity type id and respects config toggles.

## Supported categories

- Millénaire villagers (spoken, persistent mind, gossip-capable)
- Vanilla villagers & wandering traders
- Illagers, piglins
- Iron/snow golems, allays (nonverbal)
- Witch / enderman (limited)
- Animals (nonverbal), remaining monsters including bosses (atmospheric, non-intrusive)
- Generic registered living entities (modded-safe; no hard third-party classloading)

## Session lifecycle

1. Client H-key (crosshair only) or Millénaire sneak-right-click → open request
2. Server resolves adapter, validates range, optional line-of-sight, refusal policy
3. Session created with `sessionId` + generation counter (open-authorized only)
4. Send requires matching session; rejects unlock the client UI
5. Model requests carry session id + request generation; stale callbacks are dropped
6. Close / logout / death / unload / dimension / leave-range cancel pending work

## Memory

- Keyed by entity UUID in overworld `NpcMindSavedData`
- Categories: quotes, turns, facts, opinions, rumors, summary
- Transient minds (`transientMind`) pruned by retention days / max stored minds
- Persistent Millénaire minds are never auto-wiped by adapter changes
- Save adds optional `transientMind` / `lastTransientTouch` fields; absence loads as legacy persistent

## Persona / context

Adapters build `NpcDialogueContext`. Millénaire reuses `NpcPersonaBuilder`. Prompts distinguish FACTS, EXACT WORDS, RUMORS. Fabrication of village membership is forbidden.

## Model request lifecycle

Acquire in-flight slot → resolve model → async HTTP → `server.execute` → validate session/generation/entity/range → apply sanitized reply → release slot exactly once. Model output cannot spawn items, run commands, or mutate inventories.

### LM Studio only

NPC chat talks exclusively to a local LM Studio server (OpenAI-compatible `/v1`). Cloud URLs (`api.openai.com`, `api.x.ai`, etc.) are rejected with `gui.millenaire.npcchat.lmstudio.only`.

Default base URL: `http://127.0.0.1:1234/v1` (world `serverconfig`). Completions always use a normalized `…/v1/chat/completions` path.

1. `LmStudioModelDiscovery` calls `GET /api/v1/models` (host-derived native API) and falls back to `GET /v1/models`.
2. Only entries with `type` llm/vlm and non-empty `loaded_instances` are eligible (OpenAI-compat listings are also accepted).
3. **Exactly one** loaded LLM → selected automatically (no manual model name required).
4. **Multiple** loaded LLMs → `/millenaire npcchat model select <id>` or configured `npcChat.model`.
5. **None** loaded / unreachable → clear system message in the chat UI (no fake greeting fallback).
6. Warm discovery cache is reused on send; `/millenaire npcchat probe` checks reachability + latency.
7. Interactive turns use a short `CHAT_SYSTEM_PROMPT`; consolidation keeps a heavier prompt.

## Civilization facts

`CivilizationContextProvider` appends role-scoped verified facts from `CivilizationPresentationService` snapshots (same derivation as the village book Civilization section). Chiefs see housing pressure / colonization / faction status; merchants see shortages and route issues; builders see projects; residents get limited local events. Dialogue may point at server-owned `NpcActionType` buttons; the model never invents migrants, colonies, trades, rewards, or player authority, and never authorizes mutations.

## Gossip

`VillageManager.tick` → `NpcGossipScheduler.tick` once per Minecraft day transition. Culture isolation preserved. Generic/vanilla NPCs do not auto-join Millénaire culture gossip.

## Performance constraints

- No per-tick entity scans for chat eligibility
- No model calls from ordinary entity AI ticks
- Session tick work is look/stop-nav for active sessions only
- Transient mind prune ~every 60s
- Prompt construction only on send

## Extension points

```java
NpcDialogueAdapterRegistry.registerExternal(myAdapter);
```

Optional mods should register after presence checks. Do not classload missing mods from core adapters.

## Configuration (`npcChat`)

LM Studio base URL + optional model id / API token. Legacy `provider` field is unused (keep `lmstudio`). Also: millenaire/vanilla/modded/genericLiving/nonverbal toggles, line-of-sight, transient retention, max minds, entity allow/deny lists.
