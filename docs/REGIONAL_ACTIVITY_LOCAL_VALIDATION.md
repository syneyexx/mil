# Regional Activity — Local Validation (owner only)

**Cursor status:** IMPLEMENTATIE AANWEZIG, RUNTIMEPERFORMANCE NOG NIET GEVALIDEERD.  
Cursor runs no Minecraft client/server, GameTests, Gradle builds, or benchmarks for this task.

Use a **copy** of a world. Do not overwrite production saves.

## Commands

```
/millenaire activity status
/millenaire activity metrics
/millenaire activity inspect <operationId>
/millenaire activity explain <villageOrCampId>
/millenaire activity enable
/millenaire activity disable
/millenaire activity request <kind> <villageId>
```

`request` still obeys eligibility, home reserve, caps, and pressure (no forced spawn bypass).

Config master switch: `regionalActivity.enabled` (NeoForge Millénaire server config).

## Correctness checklist

| # | Invariant | How to reproduce / recognise | Expected |
|---|---|---|---|
| 1 | One participant → one op | Inspect two ops; same villager UUID | Second reserve fails / defer |
| 2 | Insufficient members → no half group | Tiny village; `explain` | `NO_ELIGIBLE_MEMBERS` / `HOME_DEFENSE_RESERVE`; no partial away flags |
| 3 | No double loot legacy+expedition | Trigger both paths on same pair | One participant representation; one loot path |
| 4 | Unique raid cap | Attacker+defender under attack | `status` raid count +1 not +2 |
| 5 | Cooldowns vs sleep/`/time set` | After raid, sleep or set dayTime | gameTime cooldowns still hold |
| 6 | Server stop leases | Stop/start server mid-mission | SavedData ops remain; runtime leases rebuilt |
| 7 | Stale results | Disable mid-plan; reload | No mutate from stale proposal |
| 8 | Death/unload | Kill member; unload chunk; reload | No duplicate UUID / no free resurrect |
| 9 | Ambiguous cargo | Force interrupted transfer if possible | `RECOVERY_REQUIRED`; no second payout |
| 10 | Treaties/quests/children/disable | NAP; quest-tagged NPC; `/activity disable` | No illegal attack; safe return |
| 11 | Multi dim/player | Two dims / two players | Caps not ×N |
| 12 | Queue / path fail bounded | Flood requests; bad terrain | Deferred; backoff ≥200 ticks after 3 fails |
| 13 | Fairness | Many villages | Later villages also get proposals over time (`fairnessCursor`) |

## Gameplay scenarios

1. **Hunt:** Town by forest with lumberman/hunter tags → see 2–3 leave, kill ≤2 valid prey, return with cargo. No tame/named/leashed/ridden; no spawn-in meat.
2. **Patrol:** Guards leave along edge route and return.
3. **Bandit rivalry:** Two camps with rival orgs (profiles `*_bandits_a` / `*_bandits_b`) nearby → skirmish without player as target.
4. **Expedition raid:** Reserved raiders assemble, travel, `underAttack` only on arrival; defeat en route → no town attack. Loot ≤10% townhall stock and ≤16 items; no block damage by default.
5. **Save/reload** during travel, combat, cargo settle.
6. **Leave area / return:** no pop-in duplicates; no force-loaded chunks.
7. **Disable mid-mission:** `/millenaire activity disable` → return/cleanup; residents retained.
8. **Quiet world:** `/millenaire activity explain <id>` and `metrics` show concrete reason codes (not silent failure).

## Performance comparison (owner)

Same world copy, render/simulation distance, modlist, player count, warm chunks.

1. Baseline: `regionalActivity.enabled=false`, warm 5 min, measure 10 min.
2. Feature on: same route/profile.
3. Note whole-server p50/p95/p99/max MSPT, ticks >50 ms, entity counts, path requests, `/millenaire activity status` coord/nav ms, client FPS/frametime, active groups.

Acceptancerichtlijn (nog te meten): coordinator p95 ≤0.50 ms HEALTHY; no systematic whole-server p95 +>2 ms vs quiet baseline; stress → defer, not unbounded queues.

If not measured: write **IMPLEMENTATIE AANWEZIG, RUNTIMEPERFORMANCE NOG NIET GEVALIDEERD**. Do not raise caps to “pass” a bad measurement.
