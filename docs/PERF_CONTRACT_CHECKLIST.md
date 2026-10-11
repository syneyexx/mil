# Perf Contract Checklist

Use this block in any PR that adds or changes **server-tick** or **entity-tick** work.  
Authority: `docs/TICK_PERFORMANCE_ARCHITECTURE.md` §4.9.

Copy into the PR description:

```markdown
## Perf Contract
- **Phase / band:** (APPLY | VILLAGE_NEAR | VILLAGE_MID | AI_SUPPORT | CIV_FACTION | WORLDGEN | HIGHWAY | TAIL | Entity Near/Mid/Far)
- **Worst-case cost:** (ms and/or ops per invocation)
- **Cadence:** (every tick / every N ticks / event-driven)
- **Deferral when over budget:** (queue name | skip | coalesce | N/A with justification)
- **Thread rules:** (main-only | compute job + mailbox apply; no ServerLevel on workers)
- **SLO impact:** (SLO-1…SLO-6 / CAP-*; how capped)
- **Telemetry:** (counter or tick-metrics field added? yes/no)
```

## Reviewer gates

- [ ] No new unbounded main-thread A\* or full-village entity AABB scan
- [ ] Path work goes through `PathRequestQueue` / async entity path (no uncapped sync storms)
- [ ] Budget check before expensive unit starts (`ServerTickGovernor.hasTime` / phase quotas)
- [ ] Far/inactive path does not call the new work (or is abstract-only / OffscreenReconciler)
- [ ] Tests or counters prove deferral / cadence
- [ ] Config knobs documented if cost is tunable

## Implemented baselines (mod-wide pass)

- PathRequestQueue + TickBudgetBoard WORLDGEN reserve
- Far data-only village background stagger
- CombatSenseCache + Mid async entity paths
- VillageWorkSlice under budget
- Settlement map deltas + client name-tag LOD + dirty coalesce (see wave 5)
