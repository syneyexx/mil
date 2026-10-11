# NPC Dialogue — Local Validation Checklist

**Status:** All items pending until the user runs them locally.  
**Do not mark passed from source inspection alone.**

Build and run Minecraft 1.21.1 + NeoForge 21.1.256 with this branch. NPC chat is **LM Studio only** — start the local server, load one LLM, set world `npcChat.baseUrl` to `http://127.0.0.1:1234/v1`. Optional token: config or `LM_API_TOKEN`.

---

## Millénaire

| # | Test | Expected |
|---|------|----------|
| M1 | H-key on Millénaire villager under crosshair | Chat opens with name, role, history |
| M2 | Sneak-right-click villager | Chat opens (existing behaviour) |
| M3 | Normal conversation with model available | Player line + NPC reply; input unlocks |
| M4 | Reopen same villager | Prior turns visible; no stale reply from old session |
| M5 | Save, quit, reload world | Memories/quotes still present |
| M6 | Culture/profession context | Speech matches culture/job tone |
| M7 | Ask NPC to keep a secret; advance day (`/millenaire` gossip force if available) | Secret does not spread; ordinary gossip may |
| M8 | Open trading / quest / chief / info GUIs | Unchanged; not replaced by chat |
| M9 | Talk during combat / sleep | Refusal messages; no freeze |

## Vanilla

| # | Test | Expected |
|---|------|----------|
| V1 | Farmer | Opens; occupation-aware spoken chat |
| V2 | Librarian | Opens; librarian tone |
| V3 | Nitwit | Opens; nitwit occupation |
| V4 | Wandering trader | Travel-oriented persona |
| V5 | Pillager | Hostile/suspicious; AI/combat intact |
| V6 | Piglin | Limited/broken style; barter AI intact |
| V7 | Iron golem | Nonverbal / restrained |
| V8 | Allay | Playful nonverbal chimes |
| V9 | Wolf or cat | Body language only; no politics |
| V10 | Zombie or creeper | Atmospheric nonverbal; abilities unchanged |
| V11 | Right-click trade/tame/feed/mount | Vanilla interactions still work; H is primary chat |

## Modded / generic

| # | Test | Expected |
|---|------|----------|
| X1 | Another mod’s living entity | Generic adapter dialogue if toggles allow |
| X2 | Same entity right-click from owning mod | Owning mod behaviour preserved |
| X3 | Optional third-party mod absent | Game starts; no classloading crash |
| X4 | Deny-list entity type in config | Generic adapter rejects; clear feedback |

## LM Studio (only supported provider)

| # | Test | Expected |
|---|------|----------|
| A1 | LM Studio running + model loaded | Normal replies; request visible in LM Studio |
| A2 | LM Studio stopped / wrong URL | System error (`unreachable` / `http_error`); **no** fake greeting fallback; UI unlocks |
| A3 | Wrong model id | Failure path; UI unlocks |
| A4 | Optional bad API token | No crash; clear failure; UI unlocks |
| A5 | Timeout | Failure path; UI unlocks |
| A6 | Malformed JSON body | Plain-text parse or failure; UI unlocks |
| A7 | Rapid repeated sends | Rate-limit system line; UI unlocks |
| A8 | Close UI before reply arrives | Stale reply does not appear later |
| LMS1 | `baseUrl=http://127.0.0.1:1234/v1`, exactly one LLM loaded, empty model id | Chat works without manual model name |
| LMS2 | No LLM loaded | Clear `no_loaded` message; UI unlocks |
| LMS3 | Two+ LLMs loaded, no selection | Clear `choose_model`; `/millenaire npcchat model select <id>` works |
| LMS4 | Unload/load different single model | Next refresh/chat uses the newly loaded model |
| LMS5 | Remote x.ai/OpenAI URL in config | Rejected with `lmstudio.only`; no cloud HTTP |
| LMS6 | `/millenaire npcchat probe` | Reports reachability, model count, latency |

## Lifecycle

| # | Test | Expected |
|---|------|----------|
| L1 | Save/reload mid-session | Session closed cleanly; mind persists if persistent |
| L2 | Quit to title | No crash; sessions cleared |
| L3 | NPC unload / death while chatting | Session closes with reason |
| L4 | Dimension change | Session closes |
| L5 | Player disconnect | Session removed |
| L6 | Walk out of leave range | Session closes too_far |
| L7 | Switch to another NPC | Previous closed; new opens; no crossed replies |
| L8 | H without crosshair target | Hint: look at an NPC (no nearest steal) |

## Performance / gossip

| # | Test | Expected |
|---|------|----------|
| P1 | Populated Millénaire village, idle | No model HTTP traffic without chat |
| P2 | Many nearby NPCs, no dialogue | Stable tick; no global chat scans |
| P3 | Extended session + many memories | Bounded prompt; no freeze |
| P4 | Day transition with rumors | Gossip runs once per day; culture isolation holds |
| P5 | Long-term save size | Transient minds prune; Millenaire minds retained |

## Security

| # | Test | Expected |
|---|------|----------|
| S1 | Send packet without open session | Rejected; session_expired |
| S2 | Oversized message | Truncated/rejected safely |
| S3 | Model asks for items/commands | Words only; no gameplay mutation |
