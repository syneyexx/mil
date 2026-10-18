# Regional Activity Implementation

**Branch base:** `main` @ `b8a985c` (Het Labyrint) when work started.  
**Runtime verification by Cursor:** NOT EXECUTED (per owner instruction).

## Integration matrix

| Onderdeel | Bestaand bestand/methode | Data-eigenaar | Gekozen koppeling | Migratie |
|---|---|---|---|---|
| Server tick / admission | `Millenaire` `ServerTickEvent.Post`; `CommitBudget.resetTick` | CommitBudget singleton per tick | `RegionalActivityService.onServerTick` na bestaande services; spendeert zelfde candidate/commit units | Geen |
| Village identity / records | `Village`, `VillagerRecord`, `VillageSavedData` | VillageSavedData | `activity_operation_id` op record; expedition mode op `VillageRaidState` | Forward-compatible NBT; absent = free / LEGACY |
| Spatial sources | `VillageManager.getAllVillages` (alleen bootstrap) | ActivitySpatialIndex | Incremental upsert + bounded bootstrap; queries via cell neighbours | Geen world reset |
| Legacy raids | `RaidManager.attemptPlanNewRaid/planRaid/tickRaid/startRaidForced` | Village raid fields | Unchanged clone path; `countUniqueActiveRaids`; `beginExpeditionRaid` | Legacy 24000 dayTime planning wait behouden |
| War waves | `WarCampaignService.startWave` | WarCampaignSavedData | `countActiveCampaignLoad` in admission; no double expedition+clone | Geen |
| Culture war / treaties | `CultureWarService`, `TreatyService.blocksWar` | CultureStanding / TreatySavedData | Planner respects war/relations/NAP | Geen |
| Story chronicle | `EventDirector`, `Village.recordChronicleEvent` | StorySavedData / village chronicle | Outcomes logged; physical ops remain activity-owned | Geen |
| Shared budget | `CommitBudget.trySpendCandidate/Commit` | CommitBudget | Admission spends 1 unit per candidate/commit step | Geen budgetverhoging |
| Economic VillageBudget | `VillageBudget` | Village | **Niet** gebruikt als CPU-budget | n.v.t. |
| Config | `MillenaireServerConfig` | NeoForge server config | Blok `regionalActivity` | Defaults enabled=true, conservatieve caps |
| Goals / AI | `GoalRegistry`, `MillVillager.initGoals` | Entity goal scheduler | `ActivityMissionGoal` | Geen |
| Hunt monster goal | `HuntMonsterGoal` | — | Ongewijzigd (monsters); dierenjacht via activity EXECUTING | n.v.t. |
| Profiles | `CultureLoader` / `ContentFs` | ActivityProfileLoader | `activity_profiles.json` per cultuur + builtins | Lege file → builtins |
| Commands | `/millenaire …` | — | `ActivityCommand` | Geen |
| Death | `MillVillager` death → `markVillagerKilled` | Village | Extra listener → activity release | Geen resurrect |

## Checkout notes / deviations

1. **README tick-governor docs** referenced in README were not treated as proof of a hard MSPT governor for this feature; pressure uses `MinecraftServer.getCurrentSmoothedTickTime()` + local ring/EWMA.
2. **`VillageBudget`** confirmed economic-only.
3. **`countActiveRaids`** previously counted attacker+defender flags; replaced usage with `countUniqueActiveRaids` (unique attacker / expedition op). Legacy method delegates.
4. **Expedition raids** skip the extra legacy 24000 dayTime wait (travel/assemble are real phases). Legacy planned raids keep 24000 dayTime behaviour.
5. **Bandit camps:** no runtime camp generation; missing suitable camp/members surfaces as defer reasons (`NO_SOURCE_CAMP` / `NO_ELIGIBLE_MEMBERS`) via explain/metrics — new camps out of scope.
6. **Loot:** expedition loot uses townhall stock only (no city-wide chest scan). Hunt cargo uses real `ItemEntity` pickup + villager inventory transfer; no fabricated meat.
7. **Boats / shipping:** not touched.

## Concrete file set (high level)

- New package `org.millenaire.activity.*`
- `goal/impl/ActivityMissionGoal.java`
- `command/ActivityCommand.java`
- Config fields + `regionalActivity` block
- `RaidManager` / `VillageRaidState` / `VillagerRecord` / `VillageSavedData` / `WarCampaignService` / `Millenaire` / `CultureLoader` / `MillVillager` hooks
- Content: `cultures/*/activity_profiles.json`
- Docs: this file, ARCHITECTURE, LOCAL_VALIDATION; README feature note

## Behaviour summary

Visible groups can leave settlements/camps to hunt, patrol, roam as bandits, skirmish rivals, and run expedition-backed village raids with real travellers. New activity waits when pressure/caps/queues say so. Existing saves keep villagers/inventories; feature adds optional NBT and a one-time settlement grace on first registration.
