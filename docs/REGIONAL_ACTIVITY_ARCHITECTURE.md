# Regional Activity Architecture

**Status:** implemented (code wired). **Runtime performance:** NOT VALIDATED (owner tests locally; Cursor runs no builds/tests).

## Ownership

| Responsibility | Owner |
|---|---|
| Global admission + fair dimension work | `RegionalActivityService` |
| Persisted ops/groups/cooldowns/rivalry | `ActivitySavedData` (per dimension) |
| Spatial cells 256×256 | `ActivitySpatialIndex` |
| Caps / leases / shared CommitBudget spend | `ActivityAdmissionController` |
| Scoring / proposals | `ActivityPlanner` |
| State machine advance | `ActivityLifecycleService` |
| Materialize / park (identity-preserving) | `ActivityProjectionService` |
| Coarse + local nav requests | `ActivityNavigationService` |
| Local hostility / retreat signals | `ActivityCombatBridge` |
| Expedition village raids | `ActivityRaidBridge` + `RaidManager.beginExpeditionRaid` |
| Legacy clone raids / war waves | `RaidManager`, `WarCampaignService` (unchanged path) |
| Profiles | `ActivityProfileLoader` + `activity_profiles.json` |
| MSPT pressure | `ActivityPressureMonitor` |
| Admin visibility | `ActivityDiagnostics` + `/millenaire activity …` |

`VillageBudget` remains economic policy only — not a CPU governor.

## Lifecycle

```
PROPOSED → RESERVED → ASSEMBLING → TRAVELLING → EXECUTING
        → RETURNING / RETREATING → SETTLING → COMPLETED
Extra: WAITING_CHUNKS, RECOVERY_REQUIRED, CANCELLED
```

- **PROPOSED:** intent only.
- **RESERVED:** single server-thread step reserves members, target, leases after live re-check. Partial failure rolls back.
- **ASSEMBLING / TRAVELLING / EXECUTING:** real NPCs; no force-chunkload.
- **SETTLING:** outcome applied once (`outcomeApplied` + `transferId`); ambiguous cargo → `RECOVERY_REQUIRED` (no re-pay).
- One NPC ≤ one exclusive `activityOperationId`.

## Budgets (server-wide defaults)

Configured under `regionalActivity` in server config. Caps are not multiplied per player/dimension. Legacy unique raid count + open war campaigns reduce remaining capacity; running legacy raids are never cancelled to make room.

Pressure states HEALTHY / SOFT / HARD use whole-server smoothed MSPT + p95 (EWMA + 200-sample ring). Coordinator target ≈ 0.50 / 0.25 / 0.10 ms cooperative deadline with hard step counts.

## Integrations

- Tick: `Millenaire` `ServerTickEvent.Post` → `RegionalActivityService.onServerTick` (after CommitBudget reset and existing village/war/economy work).
- Goals: `ActivityMissionGoal` injected for non-child villagers; does not override live combat targets.
- Death: existing death owner + `ActivityCombatBridge.onParticipantDeath` releases reservation (no clone replacement).
- Raids: expedition mode sets `RaidParticipantMode.EXPEDITION_MEMBERS`; never mixed with legacy clones on the same operation. `RaidManager.countUniqueActiveRaids` counts unique attacker/operation identity.
- Chronicle: settlement outcomes via `Village.recordChronicleEvent` (does not replace physical behaviour).
- Content: culture `activity_profiles.json` via existing `ContentFs` / `CultureLoader` path.
