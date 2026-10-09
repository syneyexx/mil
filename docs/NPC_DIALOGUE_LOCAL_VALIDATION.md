# NPC Dialogue — Local Validation Checklist

**Status:** All items pending until the user runs them locally.  
**Do not mark passed from source inspection alone.**

Build and run Minecraft 1.21.1 + NeoForge 21.1.256 with this branch. Configure `npcChat` API key (or `MILLENAIRE_NPC_API_KEY`) as needed.

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

## AI provider

| # | Test | Expected |
|---|------|----------|
| A1 | Provider available | Normal replies |
| A2 | Provider unavailable / wrong URL | Fallback line + failed message; UI unlocks |
| A3 | Wrong model id | Failure path; UI unlocks |
| A4 | Invalid API key | No crash; clear failure; UI unlocks |
| A5 | Timeout | Failure path; UI unlocks |
| A6 | Malformed JSON body | Fallback; UI unlocks |
| A7 | Rapid repeated sends | Rate-limit system line; UI unlocks |
| A8 | Close UI before reply arrives | Stale reply does not appear later |

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
