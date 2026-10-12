# Tick Performance — Local Validation

**Companion to:** `TICK_PERFORMANCE_ARCHITECTURE.md`  
**Primary target:** Singleplayer, 3–5 populated villages  
**Commands:** `/millenaire dev tick status`, `/millenaire dev tick metrics`, `/millenaire dev tick dump`

---

## 0. Tools

| Tool | Use |
|------|-----|
| Spark (or equivalent MSPT sampler) | 60s profiles; attribute `millenaire/*` |
| `/millenaire dev tick status` | Governor, adaptive pressure, LOD flags, `effGlobalMs`, goal-pick budget |
| `/millenaire dev tick metrics` | Phase ms + EMA/peak/defer-streak, village+entity LOD, `entityAiMs`, pathQ, sense |
| `/millenaire dev tick dump` | Same snapshot as metrics (copy/paste for baseline notes) |
| F3 + TPS / server log MSPT | Quick sanity |

Record: Millénaire version/commit, Java version, render distance, `forceLoadVillages`, `keepActiveRadius`, `globalBudgetMs`, `adaptiveBudgetEnabled`, pressure.

**Existing `config/millenaire-server.toml`:** Forge keeps previously written values. After raising code defaults, update or reset the `tickGovernor` section (and `worldgenTickTimeBudgetMs`) or a new world still uses the old instance budgets.

### Metrics field cheat-sheet

| Field | Meaning |
|-------|---------|
| `effMs` / `pressure` | Adaptive Post allowance and HEALTHY/SOFT/HARD |
| `preMs` | Wall time already used before Millenaire Post (`ServerTickEvent.Pre` → Post) |
| `postEmaMs` | EMA of Millenaire Post spend |
| `entityLod={near,mid,far,sleep,goalDefer}` | Entity swarm LOD + goal-pick deferrals last entity loop |
| `entityAiMs` | Attributed Millenaire AI wall time across loaded villagers (SLO-3) |
| `phaseMs=…/ema…/pk…/st…` | Last sample ms, EMA ms, peak ms, max defer streak per phase |

---

## 1. Reference world setup

1. Fresh or known SP world with Millénaire cultures loaded.
2. Found or locate **4 villages** within travel distance; populate so each has a normal NPC count.
3. Stay in **Village A** (Near). Villages B/C Mid or Far depending on distance.
4. Disable unrelated heavy mods for attribution runs when possible.

**Baseline (Phase 0):** profile before further perf merges.  
**After each phase:** repeat the same three scenarios below.

---

## 2. Scenarios

### S1 — At rest in village (SLO-1, SLO-2, SLO-3)

- Stand in Village A for 60s, no combat, no exploration.
- Expect: MSPT p95 ≤ 40 ms; Millénaire Post ≤ 24 ms; entity AI ≤ 15 ms with ≥80 villagers loaded if applicable.

### S2 — Path storm (SLO-4)

- Trigger many destination changes (raid, hire escorts, move villagers, or `/millenaire` debug moves if available).
- Expect: no single path search > 4 ms after Phase 3; MSPT recovers within 5s (SLO-5).

### S3 — Exploration worldgen (SLO-5)

- Fly through unexplored regions with worldgen engine on for 60s.
- Expect: spikes allowed briefly; return under SLO-1 within 5s; worldgen phase stays within quota.

### S4 — Far villages (SLO-6)

- Move far from B/C/D so they are Far/inactive; leave SavedData intact.
- Expect: Far cost ≈ background only; entity Far sleep active if chunks somehow loaded.

### S5 — CAP dry-run (optional)

- 15 villages, civ + highways on; 60s Spark.
- Target CAP-1 ≤ 45 ms p95 (architecture capacity, not SP release blocker).

---

## 3. Phase exit checklist

| Phase | Extra checks |
|-------|----------------|
| 0 | Baseline numbers saved; metrics shows phases + `entityAiMs` / `entityLod` |
| 1 | Near defer counters increment under artificial low `villageNearMs`; `globalMs` / WORLDGEN reserve in `/millenaire dev tick` |
| 2 | Far villagers show sleep/skip counters; Near combat unchanged; `villages={active,dataOnly,bgSkip}` |
| 3 | `pathQ=` rejects under load; no 25 ms searches in Spark; Mid uses async entity paths |
| 4 | CombatSenseCache `sense=` in metrics; Near raids still work |
| 5 | VillageWorkSlice spreads integrity/growth/paths across ticks |
| 6 | APPLY time capped; exploration SLO-5 green (WORLDGEN reserve); under load `effMs` drops and `pressure` leaves healthy |
| 7 | SLO evidence pasted below; L-05 updated in `FINAL_RELEASE_AUDIT.md` |

---

## 5. Baseline capture template (fill after S1–S4)

```
Commit:
Java:
Scenario: S1 | S2 | S3 | S4
MSPT p95 (Spark / F3):
/millenaire dev tick dump:
SLO-1 (≤40 ms): PASS/FAIL
SLO-2 Post (≤24 ms / effMs): PASS/FAIL
SLO-3 entityAiMs (≤15 ms): PASS/FAIL
Notes:
```

**Instrumentation status:** adaptive `effMs`, entity swarm goal-pick budget, entity LOD/sleep counters, phase EMA/peak/streak, and `entityAiMs` are implemented in `ServerTickGovernor`. Operator must still paste S1–S4 evidence to mark L-05 fully verified.

---

## 4. Gameplay regression smoke

In a Near village after each phase:

- [ ] Villagers pick jobs and walk
- [ ] Gather / hunt / build still function
- [ ] Raid or monster aggro still works
- [ ] Hired escort follows
- [ ] Village book / trade UI usable
- [ ] Approaching a Far village wakes activity within ~1s
