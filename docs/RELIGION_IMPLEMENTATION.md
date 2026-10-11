# Religion System — Implementation Ledger

**Baseline HEAD:** `76d661459d73d72ed32230f0cc3f1b3d477c9087`  
**Branch:** `cursor/religion-system-239c`  
**Status legend:** `PENDING` · `IN_PROGRESS` · `IMPLEMENTED_UNVERIFIED` · `BLOCKED`

Compilation, CI, GameTests, and Minecraft runtime are **not** executed by this agent.  
Owner performs local validation — see `docs/RELIGION_LOCAL_VALIDATION.md`.

---

## Progress checklist

| Phase | Item | Status |
|-------|------|--------|
| 0 | Architecture doc | IMPLEMENTED_UNVERIFIED |
| 1 | Content model + loader + validation | IMPLEMENTED_UNVERIFIED |
| 1 | ReligionSavedData + migration | IMPLEMENTED_UNVERIFIED |
| 1 | Config keys (`religion.*`) | IMPLEMENTED_UNVERIFIED |
| 2 | Daily practice hooks (`VisitBuildingGoal` completion) | IMPLEMENTED_UNVERIFIED |
| 2 | Clergy resolution | IMPLEMENTED_UNVERIFIED |
| 2 | Ceremony state machine + coordinator | IMPLEMENTED_UNVERIFIED |
| 2 | AttendCeremonyGoal | IMPLEMENTED_UNVERIFIED |
| 3 | Supply checks + townhall consumption | IMPLEMENTED_UNVERIFIED |
| 3 | Social/mood/knowledge effects | IMPLEMENTED_UNVERIFIED |
| 3 | Living event links (death/attack/building) | IMPLEMENTED_UNVERIFIED |
| 4 | Calendar + catch-up limits | IMPLEMENTED_UNVERIFIED |
| 4 | Built-in culture religion content (all 10) | IMPLEMENTED_UNVERIFIED |
| 4 | Village Book / Travel Book / dialogue | IMPLEMENTED_UNVERIFIED |
| 5 | Player religion actions (via NpcActionType bridge) | IMPLEMENTED_UNVERIFIED |
| 5 | BuildingPurpose.SHRINE + designation | IMPLEMENTED_UNVERIFIED |
| 6 | Mixed community / migration preserve (UUID profiles) | IMPLEMENTED_UNVERIFIED |
| 6 | Pilgrimage temporary travel | IMPLEMENTED_UNVERIFIED |
| 6 | Policy + shrine incident service | IMPLEMENTED_UNVERIFIED |
| 7 | Docs (LOCAL_VALIDATION, README, examples) | IMPLEMENTED_UNVERIFIED |
| 7 | Full checklist audit (no stubs) | IMPLEMENTED_UNVERIFIED |

---

## Authority map

| Concern | Owner |
|---------|-------|
| Tradition/ritual definitions | `ReligionContentRegistry` |
| Village community / ceremonies / NPC faith | `ReligionSavedData` (`millenaire_religion` v1) |
| Inventories | `BuildingInventory` |
| Assistance requests | `VillageRequestService` (optional bridge; direct delivery also supported) |
| Mood / relationships | `NpcSocialService` / `SocialSavedData` |
| Chronicle | `LivingEventService` + `CEREMONY_COMPLETED` / `RELIGIOUS_INCIDENT` |
| Work goals | `GoalScheduler` + `AttendCeremonyGoal` / visit goals |
| Cultural aggression | `CulturalTemperament` (unchanged) |
| Festivals | `FestivalWindow` / `InteractionSavedData` (feast alignment) |

---

## Legacy audit notes

- Religious visit goals remain valid daily/location selectors; religion layer adds effects on completion and ceremony coordination.
- `tend_sacrifices.json` still uses legacy `goalKey: "gopray"` for speech/label compatibility; content id path remains `tend_sacrifices`.
- `perform_pujas` / `meditate` use tag `pujas`; `tend_sacrifices` uses `sacrifices` — both wired as daily-compatible location tags for matching traditions.
- Cultures without `religion.json` infer traditions from tradition `cultures` lists; `outlawz` sets `no_organized_religion`.

---

## Assumptions

1. NPC faith is UUID-keyed in religion SavedData (not VillagerRecord NBT).
2. First complete ceremony applies social/economic effects only.
3. Owner-local validation only.
4. Pilgrimage trips are in-memory on the pilgrimage service; history lines persist in community SavedData.
5. Recovery is idempotent via flags, not atomic across SavedData + chunks.
