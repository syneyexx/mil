# Living Villages 2.0 — Implementation Ledger

**Baseline HEAD at branch creation:** `8e57289167c6ff39b4915ae911a99f2b384d043c`  
**Branch:** `cursor/living-villages-2-7128`  
**Status legend:** `PENDING` · `IN_PROGRESS` · `IMPLEMENTED_UNVERIFIED` · `BLOCKED`

Compilation, CI, GameTests, Minecraft runtime, and live LM Studio calls are **not** executed by this agent.  
User performs local validation — see `docs/LIVING_VILLAGES_LOCAL_VALIDATION.md`.

---

## Phase 3.0 — Baseline and dependency graph

| Task ID | Item | Status | Notes |
|---------|------|--------|-------|
| LV-3.0.1 | Inspect HEAD / branch | IMPLEMENTED_UNVERIFIED | Matches known baseline; clean tree at start |
| LV-3.0.2 | Map dialogue / AI | IMPLEMENTED_UNVERIFIED | NpcChatService remains conversational authority |
| LV-3.0.3 | Map goals / villagers | IMPLEMENTED_UNVERIFIED | GoalScheduler, ChatGoal, SocialiseGoal reused |
| LV-3.0.4 | Map village / construction | IMPLEMENTED_UNVERIFIED | PendingProject + townhall inventory authoritative |
| LV-3.0.5 | Map quests | IMPLEMENTED_UNVERIFIED | Separate VillageRequestService; quest referral only |
| LV-3.0.6 | Implementation ledger | IMPLEMENTED_UNVERIFIED | This document |

### Authority map

| Concern | Owner |
|---------|-------|
| Village inventories | `BuildingInventory` on buildings / townhall |
| Construction projects | `Village.PendingProject`, `ConstructionTask`, `VillageGrowthManager` |
| Villager work | `GoalScheduler` + goals |
| Reputation | `Village.addReputation` / `adjustReputation` |
| Quests | `QuestManager` / `PlayerQuestData` |
| NPC conversation | `NpcChatService` + adapters |
| NPC memory (chat) | `NpcMind` / `NpcMindSavedData` |
| Raids | `RaidManager` / `Village.isUnderAttack` |
| New needs | Derived by `VillageNeedsEngine` (not persisted) |
| New events | `LivingEventService` |
| New social | `NpcSocialService` / `SocialSavedData` |
| New requests | `VillageRequestService` / `VillageRequestSavedData` |
| Conversation actions | `NpcActionRegistry` (server-owned proposals) |

---

## Phase 3.1 — Core event architecture

| Task ID | Item | Status | Files |
|---------|------|--------|-------|
| LV-3.1.1 | Event types + identity | IMPLEMENTED_UNVERIFIED | `living/events/*` |
| LV-3.1.2 | Journal + SavedData | IMPLEMENTED_UNVERIFIED | `LivingEventSavedData` |
| LV-3.1.3 | Dispatch + dedupe | IMPLEMENTED_UNVERIFIED | `LivingEventService` |
| LV-3.1.4 | Gameplay hooks | IMPLEMENTED_UNVERIFIED | Needs transitions, requests, social, raid flags |

## Phase 3.2 — Village needs

| Task ID | Item | Status |
|---------|------|--------|
| LV-3.2.1 | Need model + severity | IMPLEMENTED_UNVERIFIED |
| LV-3.2.2 | Construction shortage detection | IMPLEMENTED_UNVERIFIED |
| LV-3.2.3 | Security / stalled / resident loss / empty market | IMPLEMENTED_UNVERIFIED |
| LV-3.2.4 | Snapshot cache + VillageManager tick | IMPLEMENTED_UNVERIFIED |

## Phase 3.3 — NPC social model

| Task ID | Item | Status |
|---------|------|--------|
| LV-3.3.1 | Personality + relationships | IMPLEMENTED_UNVERIFIED |
| LV-3.3.2 | Family bootstrap from VillagerRecord | IMPLEMENTED_UNVERIFIED |
| LV-3.3.3 | ChatGoal completion hook | IMPLEMENTED_UNVERIFIED |
| LV-3.3.4 | SocialSavedData persistence | IMPLEMENTED_UNVERIFIED |

## Phase 3.4 — World knowledge

| Task ID | Item | Status |
|---------|------|--------|
| LV-3.4.1 | Role-scoped context providers | IMPLEMENTED_UNVERIFIED |
| LV-3.4.2 | Knowledge provenance + transfer | IMPLEMENTED_UNVERIFIED |
| LV-3.4.3 | Prompt / adapter integration | IMPLEMENTED_UNVERIFIED |

## Phase 3.5 — Dynamic requests

| Task ID | Item | Status |
|---------|------|--------|
| LV-3.5.1 | Request lifecycle | IMPLEMENTED_UNVERIFIED |
| LV-3.5.2 | Delivery transactions | IMPLEMENTED_UNVERIFIED |
| LV-3.5.3 | Rewards + invalidation | IMPLEMENTED_UNVERIFIED |

## Phase 3.6 — Action bridge

| Task ID | Item | Status |
|---------|------|--------|
| LV-3.6.1 | Server proposals + validation | IMPLEMENTED_UNVERIFIED |
| LV-3.6.2 | Network payloads | IMPLEMENTED_UNVERIFIED |
| LV-3.6.3 | Client action choices UI | IMPLEMENTED_UNVERIFIED |

## Phase 3.7 — Behavioral reactions

| Task ID | Item | Status |
|---------|------|--------|
| LV-3.7.1 | Reaction policy (no GoalScheduler replace) | IMPLEMENTED_UNVERIFIED |
| LV-3.7.2 | Role dialogue context | IMPLEMENTED_UNVERIFIED |

## Phase 3.8 — UI and localization

| Task ID | Item | Status |
|---------|------|--------|
| LV-3.8.1 | NpcChatScreen action buttons | IMPLEMENTED_UNVERIFIED |
| LV-3.8.2 | en_us + nl_nl keys | IMPLEMENTED_UNVERIFIED |

## Phase 3.9 — Performance and save hardening

| Task ID | Item | Status |
|---------|------|--------|
| LV-3.9.1 | Tick budgets / caps / cleanup | IMPLEMENTED_UNVERIFIED |
| LV-3.9.2 | Lifecycle edge cases (documented + coded paths) | IMPLEMENTED_UNVERIFIED |

## Phase 3.10 — Final static review

| Task ID | Item | Status |
|---------|------|--------|
| LV-3.10.1 | Static consistency review | IMPLEMENTED_UNVERIFIED |
| LV-3.10.2 | Local validation guide | IMPLEMENTED_UNVERIFIED |
| LV-3.10.3 | Architecture document | IMPLEMENTED_UNVERIFIED |

---

## Known limitations (source-level)

- `VILLAGE_FOOD_SHORTAGE` / `WORK_RESOURCE_SHORTAGE` types exist but only emit when defensible detectors fire; food/work detectors are conservative (no invented hunger systems).
- `TRADE_DISRUPTION` reads authoritative `TradeManager` disrupted routes for the village faction (no fictional caravans).
- Security assistance requests are informational (`SECURITY_ASSISTANCE_INFO`) unless an executable pathway exists — no fake raid-completion quests.
- Action buttons show up to four choices at once in NPC chat.
- Living event types are separate from `VillageEventType` chronicle enums to avoid corrupting legacy chronicle i18n; significant assistance still recorded in living journal + reputation.

## Integration points (implemented)

- `VillageManager.tick` → `LivingVillagesLifecycle.onVillageManagerTick`
- `ChatGoal.ChatTask` completion → social interaction
- `MillenaireVillagerAdapter` → verified living context in instructions
- `NpcChatService.handleOpen` → action choices packet
- `ModPayloads` → living action packets
- `MillenaireServerConfig` → `livingVillages` section
- `DevCommand` → `/millenaire dev living …`
- Player logout → clear ephemeral proposals
