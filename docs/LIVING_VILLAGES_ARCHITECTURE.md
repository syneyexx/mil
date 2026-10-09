# Living Villages 2.0 — Architecture

**Baseline HEAD:** `8e57289167c6ff39b4915ae911a99f2b384d043c`  
**Package root:** `org.millenaire.living`

This document describes state ownership, pipelines, and extension points for the Living Villages gameplay layer. It does not claim runtime verification.

---

## 1. State ownership

| Concern | Authoritative owner | Living Villages role |
|---------|---------------------|----------------------|
| Village inventories | `BuildingInventory` | Read shortages; deliver into townhall only via validated service |
| Construction / pending projects | `Village.PendingProject`, `ConstructionTask`, `VillageGrowthManager` | Derive construction needs; never place buildings |
| Villager jobs | `GoalScheduler` + goals | Observe; ChatGoal completion hooks social updates |
| Reputation | `Village.adjustReputation` | Apply bounded rewards on request completion |
| Quests | `QuestManager` / `PlayerQuestData` | Referral via existing `VillagerInteraction.openInfoScreen` |
| Player↔NPC dialogue | `NpcChatService` + adapters | Inject verified living context; never AI-authorize mutations |
| Chat memory | `NpcMind` / `NpcMindSavedData` | Remains; living social is separate NPC↔NPC layer |
| Raids | `RaidManager` / `Village.isUnderAttack` | Security need + events |
| Derived needs | *(none persisted)* | `VillageNeedsEngine` recomputes snapshots |
| Living events | `LivingEventSavedData` | Canonical journal with dedupe |
| Social / personality | `SocialSavedData` | Directed relationships + traits + knowledge |
| Assistance requests | `VillageRequestSavedData` / `VillageRequestService` | Persistent lifecycle; live need caps via `NeedRevalidationResult` |
| Action proposals | In-memory `NpcActionRegistry` | Ephemeral server-owned proposals |

AI model output is never authoritative.

---

## 2. Event lifecycle

```
Authoritative gameplay transition
        │
        ▼
LivingEventService.emit(...)
        │
        ├─ dedupe key + TTL
        ├─ bounded journal (per village)
        ├─ listeners (reactions / knowledge seeding paths)
        └─ no recursive emit loops from listeners for the same cause
```

Shortage detection uses **transition** emission (`MATERIAL_SHORTAGE_DETECTED` / `RESOLVED`), not per-tick spam.

---

## 3. Village need evaluation

- Interval: `villageNeedsEvaluationInterval` (default 200 ticks), staggered by village id hash.
- Global budget: `villageNeedsGlobalBudget` evaluations/tick.
- Construction shortage: pending/controlled project or active `isBeingBuilt` building vs townhall inventory (including `anywood` via `AnywoodHelper`).
- Security: `village.isUnderAttack()`.
- Resident loss: count of killed `VillagerRecord`s.
- Merchant/supply: market/shop building with empty scanned inventory.
- Trade disruption: `EconomySavedData.getTradeManager().getRoutesInvolving(factionId)` where `TradeRouteStatus.DISRUPTED` (requires village faction id).
- Severity: ratio bands with hysteresis (`VillageNeedPriority.fromShortageRatio`).
- Mutations always revalidate with `requireFresh` / live inventory — never authorize from a stale snapshot alone.

---

## 4. NPC personality and social relationships

- Personality dimensions (0–100): sociability, caution, generosity, ambition, patience, loyalty.
- Seeded once from stable UUID + role/tags; not rerolled on load.
- Mood is temporary (`calm`, `afraid`, `sad`, `happy`, `worried`) with decay.
- Relationships are **directed** (`from → to`): familiarity 0–100; trust/affection/respect/rivalry −100..100.
- Family bootstrap uses existing `VillagerIdentity` spouse/parent/child names and shared home building — no invented genealogy.
- `ChatGoal` completion → `SocialInteractionResolver` (cooldown, familiarity++, bounded knowledge hops ≤ 3).
- No LLM calls for NPC↔NPC socialization.

---

## 5. NPC knowledge

- Stored on `NpcSocialProfile` as `NpcKnowledgeItem` with provenance (source, hops, firsthand flag, expiry).
- Role-scoped exposure via `NpcWorldKnowledgeProvider` (chief / builder / guard / farmer / trader / child / resident).
- Injected into dialogue through `MillenaireVillagerAdapter` → `communicationInstructions` + prompt rules forbidding invented quests/rewards.

---

## 6. Action validation

```
Player UI / chat action button
        │
        ▼
LivingActionRequestPayload (server)
        │
        ▼
NpcActionRegistry.executeDirect
        │
        ├─ distance / entity / session checks
        ├─ Millénaire affiliation for village mutations
        ├─ server-created proposal + idempotency window
        └─ NpcActionExecutor → needs / requests / VillagerInteraction
```

Model text cannot create proposals or mutate inventories.

---

## 7. Request persistence and delivery semantics

States: `AVAILABLE → OFFERED → ACCEPTED → IN_PROGRESS → READY_TO_COMPLETE → COMPLETED`  
(+ `DECLINED` / `EXPIRED` / `CANCELLED`)

Delivery ordering:

1. Validate ownership, state, village, destination inventory.
2. Revalidate live shortage / shared committed remaining across players.
3. Remove items from player.
4. Add to townhall `BuildingInventory`.
5. Return unaccepted remainder to player.
6. Update request delivered counts only for committed amounts.
7. On full fill: reputation reward once (`rewardClaimed`), living event, knowledge seed.

If need resolves elsewhere: cancel remaining work; **do not confiscate** player items.

---

## 8. Quest interoperability

Living requests are a dedicated system. Existing quests open through `VillagerInteraction.openInfoScreen` (quest payload when applicable). No duplicate quest assignment schema.

---

## 9. GoalScheduler integration

- Living Villages does **not** replace goal priorities.
- `NpcReactionPolicy` only influences dialogue/context priority bands.
- Social updates attach to existing `ChatGoal` completion.

---

## 10. Save migration

New SavedData names (overworld storage, same pattern as `NpcMindSavedData`):

- `millenaire_living_events` (schema v1)
- `millenaire_living_social` (schema v1)
- `millenaire_living_requests` (schema v1)

Missing data on old worlds → empty maps. Corrupt entries skipped with warnings. Newer schema versions log a warning and load defensively.

Disabling Living Villages pauses evaluation/offers; persisted requests remain until expiry/cleanup and are not deleted on disable.

---

## 11. Performance

- Needs: staggered + budgeted.
- Social: event/completion driven + cooldowns.
- Events: budgeted emit path; bounded journals.
- Knowledge: capped per NPC; orphan profile prune.
- Proposals: in-memory, pruned, max 500.
- No per-NPC LLM during ordinary ticks.
- No chunk force-load for social simulation.

---

## 12. Network

| Payload | Direction | Purpose |
|---------|-----------|---------|
| `living_action_choices` | S→C | Contextual action buttons |
| `living_action_request` | C→S | Player chose an action |
| `living_action_feedback` | S→C | Result line for chat UI |

---

## 13. Extension points

- New need detectors: `VillageNeedsEngine.compute` + `VillageNeedType`.
- New actions: `NpcActionType` + `NpcActionExecutor` + localization.
- New knowledge categories: `NpcKnowledgeCategory`.
- Listeners: `LivingEventService.addListener`.

Do not add a second inventory, quest engine, or goal scheduler.
