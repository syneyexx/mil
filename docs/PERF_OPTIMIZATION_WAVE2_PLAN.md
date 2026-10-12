# Millénaire — Performance Wave 2 (broncode-plan)

**Autoriteit:** Java-bron onder `src/main/java/org/millenaire` (NeoForge 21.1 / MC 1.21.1).  
**Niet-autoritatief:** bestaande `docs/*`-architectuurteksten. Die beschrijven intentie; dit plan volgt alleen wat de code nu doet.  
**Input:** Claude-analyse (ticks / worldgen / bestaande opt-systemen), geverifieerd tegen bron.  
**Invarianten:** niet-destructief; gedrag gelijk tenzij config-gated; geen SavedData-schema-breuk; RNG-volgorde worldgen ongewijzigd; combat-reactie ≤ ~5 ticks trager, nooit bij damage.

### Implementatiestatus (Wave 2 start)

| ID | Status |
|----|--------|
| A1 VillageSavedData cache | **done** |
| A2 config snapshot | **done** |
| A3 profiler push/pop | **done** |
| B1–B8 villager fixed costs (paddy, safety, LOD buffer, cloth dirty, pickup scatter, nanoTime sample, sleep/night cache) | **done** (GoalContext reuse overgeslagen — record; goedkoop na A1) |
| C1 isNight via LevelTickCache | **done** |
| C5 PursueWantedPlayer `level.players()` | **done** |
| C6 HiredEscort opponent cache 10t | **done** |
| C2–C4 combat-scan cadence / cooldown arrays / idleBackoff | **done** |
| D1 IdentityHashSet due-lists | **done** |
| D2 spelerpositiebuffer (LevelTickCache) | **done** |
| D3–D7 Village.tick slices / optional inner LOD / seller scatter / battle gate | **done** |
| E1 prefill tijd-gate | **done** (hasTime; nog geen ticket-only FULL path) |
| E2 ring LongOpenHashSet + footprint cadence 20t | **done** |
| E3–E6 near-spawn cache / activation scan cadence / materializeKey mix | **done** (chunk→plans index bestond al) |
| F1 chunkIndexRanges in materializer | **done** (alleen zonder preserve-ground prepend) |
| F2 section batch writes | **done** (`worldgenFastSectionWrites`, default off) |
| G1 reconstructPath reverse | **done** |
| G2 IntOpenHashSet path queue | **done** |
| G3 async walkability validate | **done** |
| G4 AbortPolicy → sync fallback (bestaand reject-pad, gedocumenteerd) | **done** |
| G5 pathMaxSearchMs onder HARD | **done** |
| G6 echte cache hit/miss | **done** |
| G7 PerVillageTickCache → HashMap (main-only) | **done** |
| G8 TickPressure schaalt AI (`tickPressureScalesAi`) | **done** |
| H1 dirty-per-plan NBT cache op save | **done** |
| H2 terrain scan nanoTime/16 + HEALTHY ×2 kolommen | **done** |

---

## 0. Diagnose (uit code, niet uit docs)

De mod heeft al governor-fasen, LOD, path-queue, compute-pool, materialisatiebudgetten. Het resterende MSPT-probleem zit in:

1. **Werk buiten de governor** — `MillVillager.tick()` en vaste kosten per entity-tick.
2. **Dure primitives op hot paths** — `VillageSavedData.get()` alloceert elke call een nieuwe `Factory`; spelerlussen; `getEntitiesOfClass` per villager.
3. **Worldgen scan + sync chunk loads** — alle plannen per tick; `level.getChunk` in prefill.
4. **Gaten in bestaande opt-code** — ongebruikte `chunkIndexRanges`, misleidende `cacheHits`, async path zonder walkability.

Geschatte winstvolgorde (hoog → laag): **A** VillageSavedData-cache → **B** villager fixed costs + GoalScheduler → **C** VillageManager lijsten/spelerbuffer → **D** SpawnRingPrefill + engine scans → **E** materializer (`chunkIndexRanges` + section writes) → **F** pathfinder → **G** config-snapshots.

---

## 1. Bron-verificatie (steekproef)

| Claim | Bron | Status |
|-------|------|--------|
| T1 `get()` maakt nieuwe Factory | `VillageSavedData.java:53-58` | ✔ |
| ~200+ call sites | `rg VillageSavedData.get` → 209 in `src/main` | ✔ |
| T2 rice paddy + LOD + nanoTime + nieuwe GoalContext elke tick | `MillVillager.java:1132-1301`, `buildGoalContext` ~1601 | ✔ |
| T3 GoalScheduler combat + `isNight` per goal | `GoalScheduler.java` (`isTimeAllowed` → `TickConstants.isNight`) | ✔ |
| T4 `PursueWantedPlayerGoal` AABB players | `PursueWantedPlayerGoal.java:87` | ✔ |
| T4 `HiredEscortGoal.acquireOpponent` AABB | `HiredEscortGoal.java:106-124` | ✔ |
| T5 `nearDue.contains` / `midDue.contains` op ArrayList | `VillageManager.java:225-251` | ✔ |
| T7 config `.get()` in try/catch in governor | `ServerTickGovernor.enabled()` / phase getters | ✔ |
| T10 `cacheHits++` altijd na `hostiles()` | `CombatSenseCache.java:49-64` | ✔ |
| W1 sync `level.getChunk` in prefill | `SpawnRingPrefillService.java:219`, drain `ensureFootprintLoaded` | ✔ |
| W1 `regionPacked().contains` O(n) | `isRingPlan` ~339-344 | ✔ |
| W2 alle-plannen scan + sort per tick | `WorldgenEngine.drainNearSpawnPriority` 161-210 | ✔ |
| W3 `setBlock` per step, `MAX_STEP_SCAN=512` | `ChunkSettlementMaterializer` | ✔ |
| W4 `chunkIndexRanges` berekend, niet geconsumeerd | `CompiledPlacementStepsCache` record; materializer leest alleen `steps` | ✔ |
| T9 `list.add(0, node)` + AbortPolicy | `MillPathFinder.reconstructPath`; `MillenaireComputePool` AbortPolicy | ✔ |
| Profiler push alleen pathfinder | één `profiler.push` in `MillPathFinder` | ✔ |

Niet onderzocht (geen claims, later meten): `VillagePathManager`, `PathGridAStar`, Living/Religion/Civ tickers, client, highway internals, template-NBT parse.

---

## 2. Principes (hard)

1. **Eerst meten, dan fixen** — Spark + governor `profiler.push("millenaire:<fase>")` vóór grote gedragswijzigingen.
2. **Eén lever per PR** (of kleine samenhangende cluster); regressietest per lever.
3. **API-stabiel waar mogelijk** — bv. T1: `VillageSavedData.get(level)` blijft hetzelfde; alleen implementatie cachen.
4. **Gedragswijzigend → config-vlag**, default = huidig gedrag tot gevalideerd.
5. **Geen feature-verwijdering**; ongebruikte structuren óf benutten óf berekening stopzetten (geen half-dode allocatie).

---

## 3. Meetplan (gate vóór Wave 2 “klaar”)

| # | Actie | Succescriterium |
|---|--------|-----------------|
| M0 | Spark `/spark profiler --timeout 120 --thread Server` met 20+ actieve dorpen | Top-N: `MillVillager.tick`, `GoalScheduler`, `VillageSavedData.get`, `VillageManager.tick`, worldgen |
| M1 | `profiler.push/pop` per `ServerTickGovernor`-fase | Spark toont `millenaire:APPLY` … `millenaire:WORLDGEN` |
| M2 | Worldgen pre-gen run ± spelers | Top: `WorldgenEngine`, `ChunkSettlementMaterializer`, `SpawnRingPrefillService` |
| M3 | Per lever: seed→plan hash; goal-log diff; SavedData round-trip | Geen onbedoelde drift |

Config-only knoppen (geen code, eerst testen op drukke server):

- `nearVillageMaxFullTicksPerTick` 4 → 2–3  
- fasequota VILLAGE_NEAR 4→3; AI_SUPPORT omlaag bij veel dorpen  
- `pathMaxSearchMs` 4 → 2  
- `worldgenMaterializationBudget` 3000 → 1500–2000 online  
- `prefill chunksPerTick` 16 → 4–8  

---

## 4. Roadmap (implementatiefasen)

Elke fase = mergeable PR-set. Volgorde = Claude-prio, aangescherpt op bronrisico.

### Fase A — Hot-path caches (H winst, laag risico)

| ID | Werk | Bestanden | Acceptatie |
|----|------|-----------|------------|
| **A1 / T1** | Cache `VillageSavedData` per `ServerLevel` / `ResourceKey`; `static final Factory`; clear op unload/`ServerStopped` | `VillageSavedData.java` | Zelfde API; geen nieuwe Factory per call; unit/smoke load/save |
| **A2 / T7** | Config-`Snapshot` (volatile) ververst op `ModConfigEvent`; governor-getters lezen snapshot | `ServerTickGovernor`, config listeners | Geen try/catch-`.get()` in `hasTime` hot path |
| **A3 / M1** | `profiler.push("millenaire:"+phase)` enter/exit | `ServerTickGovernor` / `Millenaire` Post | Zichtbaar in Spark |

### Fase B — Entity fixed costs (H, laag)

| ID | Werk | Bron-anker | Acceptatie |
|----|------|------------|------------|
| **B1** | `computeInRicePaddy` alleen bij `blockPosition()`-wijziging | `MillVillager:1133, 1316` | Gedrag rice-paddy gelijk |
| **B2** | Per-tick spelerpositiebuffer per level; hergebruik in `EntitySimulationLod` / `VillageSimulationLod` | `EntitySimulationLod.resolve`, `VillageSimulationLod` | Minder spelerlussen × entities |
| **B3** | LOD-hysterese of herbereken elke N ticks (chunk/dim uitzondering) | entity LOD | Geen flip-flop; Near blijft Near bij grens |
| **B4** | Safety probes bij positiewijziging of elke 2–4 ticks (Far blijft %40) | `MillVillager` water/suffocation | Anti-drown/suffocation nog steeds correct |
| **B5** | Herbruikbare `GoalContext` per villager (velden overschrijven) | `buildGoalContext` | Minder allocaties; zelfde goal-inputs |
| **B6** | `LevelTickCache`: `isNight` / dayTime 1× per tick | `tickSleepDebt`, GoalScheduler | Zelfde nachtlogica |
| **B7** | `nanoTime` telemetrie sample 1/N of achter config | 1138/1300 | Minder meetkosten |
| **B8** | Passive pickup scatter `id % 40`; cloth dirty-flag | pickup / cloth loop | Zelfde frequentie, gespreid |

### Fase C — GoalScheduler + combat scans (H, middel)

| ID | Werk | Risico-mitigatie |
|----|------|------------------|
| **C1** | `isNight` uit LevelTickCache doorgeven | geen |
| **C2** | Combat-urgent scan elke 2–5 ticks of event (`CombatSenseCache` / damage); **altijd** bij recent damage / bestaande target | combat ≤5 ticks trager, nooit bij hit |
| **C3** | Cooldowns: `long[]` op goal-id i.p.v. `HashMap` | gedrag gelijk |
| **C4** | `idleBackoff` ook op combat-urgent lus als geen hostiles in cache | alleen als cache “leeg” betrouwbaar is |
| **C5** | `PursueWantedPlayerGoal`: `level.players()` + afstand, of dorpscache 10 ticks | zelfde wanted-detectie |
| **C6** | `HiredEscortGoal.acquireOpponent`: cache per owner/10 ticks | escort-aggro latency ≤10 ticks |

Eerst verifiëren of `pickBest`-scoring monotoon is vóór early-exit op prioriteit (Claude: alleen als monotoon).

### Fase D — VillageManager / Village.tick (M–H, laag)

| ID | Werk |
|----|------|
| **D1** | `nearDue`/`midDue`: `IdentityHashSet` of `dueMarker` + tick-id i.p.v. `ArrayList.contains` |
| **D2** | Spelerposities doubles, geen `BlockPos` voor distSqr in LOD |
| **D3** | Config snapshot 1×/tick voor manager |
| **D4** *(optioneel, config)* tweede LOD-ring: echt-NEAR (bv. 96) vs keepActive 200 — **default uit** |
| **D5** | Buildings-reserveringlus alleen als teller > 0; UUID→entity cache |
| **D6** | Seller-check naar buiten: elke 10 ticks met `villageId % 10` |
| **D7** | `checkBattleStatus` alleen bij hostiles/raid of WorkSlice onder SOFT/HARD |

### Fase E — Worldgen scans + prefill (H worldgen, middel)

| ID | Werk | Config |
|----|------|--------|
| **E1** | Prefill `tickChunking`: tijd-gate + alleen FULL chunks / tickets i.p.v. blind `getChunk` | mag iets trager prefillen |
| **E2** | Ring-plan set → `LongOpenHashSet`; footprint load cache per plan, refresh ≤ elke 20 ticks | |
| **E3** | `drainNearSpawnPriority`: heap/gesorteerde lijst; herbereken bij speler >16 of planwijziging | |
| **E4** | `activateDuePlans`: due-tick index (`PriorityQueue`); geen volle scan + kopie elke tick | |
| **E5** | Chunk→plans index op `SettlementPlanSavedData` | |
| **E6** | `purgeStale` elke 100 ticks; sterkere `materializeKey` | |

### Fase F — Materializer + compiled steps (H worldgen, middel)

| ID | Werk | Config |
|----|------|--------|
| **F1** | **Gebruik** `CompiledSteps.chunkIndexRanges` in materializer (per chunk alleen relevante step-indexen) | laag risico |
| **F2** | Mutable `BlockPos`; hergebruik chunk/section; optioneel `section.setBlockState` batch + markUnsaved/licht/packets | `worldgenFastSectionWrites` (default uit tot getest, daarna aan) |
| **F3** | Fallback `setBlock` voor BE / waterlogged / POI | verplicht |
| **F4** | Road completion cursor; dirty coalesce (max 1 save/N ticks) | |
| **F5** | Preserve-ground: lazy of cache op (plan, rot, ground, terrain-hash) | RNG/determinisme check |

### Fase G — Path + telemetry polish (M, middel/laag)

| ID | Werk |
|----|------|
| **G1** | `reconstructPath`: achterwaarts bouwen + `Collections.reverse` |
| **G2** | `PathRequestQueue`: `IntOpenHashSet` |
| **G3** | Async pad: main-thread walkability sample elke N nodes; fail → sync queue |
| **G4** | ComputePool: bij Abort → sync fallback + queue-diepte metriek (nu AbortPolicy) |
| **G5** | HARD: `pathMaxSearchMs` adaptief lager |
| **G6** | `CombatSenseCache`: echte hit/miss in `PerVillageTickCache` |
| **G7** | `PerVillageTickCache`: als main-only → gewone `HashMap` (eerst alle async-callers grep) |
| **G8** | TickPressure schaalt ook AI-cadence / path-ms / combat-interval (**opt-in**) |

### Fase H — Save/planner (M, later)

| ID | Werk |
|----|------|
| **H1** | `SettlementPlanSavedData.save`: dirty-per-plan / compressed blob alleen gewijzigde plannen |
| **H2** | Layout planner: array pool; heightmaps; nanoTime per 16 kolommen; HEALTHY ×2 kolommen |

---

## 5. Bestaan de “opt-systemen” — gaten (bron)

| Systeem | Gat in code | Fix-fase |
|---------|-------------|----------|
| `ServerTickGovernor` | config `.get()` per call | A2 |
| `TickPressure` | alleen materialisatie/discovery-schaal | G8 |
| `VillageWorkSlice` | alles aan bij `hasTime` | D7 |
| `EntitySimulationLod` | resolve × villager × players | B2–B3 |
| `VillageSimulationLod` | NEAR ≈ keepActiveRadius | D4 opt-in |
| `PathRequestQueue` / async | boxing + 2D heights | G2–G3 |
| `MillenaireComputePool` | AbortPolicy | G4 |
| `CombatSenseCache` | `cacheHits` altijd++ | G6 |
| `CompiledPlacementStepsCache` | `chunkIndexRanges` dead weight | F1 |
| Profiling | bijna geen `getProfiler` | A3 |

---

## 6. PR-volgorde (concreet)

```
PR-1  A1 VillageSavedData cache
PR-2  A2+A3 config snapshot + profiler labels
PR-3  B* villager fixed costs (één PR of split B1-B4 / B5-B8)
PR-4  C* GoalScheduler + combat player/escort scans
PR-5  D* VillageManager + Village.tick slices
PR-6  E* prefill + WorldgenEngine scans
PR-7  F1 chunkIndexRanges (klein, veilig) → F2+ section writes (apart, geflagd)
PR-8  G* path + telemetry
PR-9  H* save split + planner (na worldgen M2-baseline)
```

Niet starten aan F2/E1 zonder M0/M2-baseline. A1–A3 mogen wél vóór volledige Spark-run (laag risico, meten wordt beter).

---

## 7. Regressiechecklist (per PR)

- [ ] Perf Contract (fase/band, worst-case, cadence, deferral, threads)
- [ ] Zelfde seed → zelfde plan-identiteit / placement (worldgen-PRs)
- [ ] Villager goals: Near combat nog steeds preëmptief bij damage
- [ ] Save/load VillageSavedData + SettlementPlanSavedData
- [ ] Geen nieuwe unbounded `getEntitiesOfClass` op entity-tick
- [ ] Config-defaults ongewijzigd tenzij expliciet getest

---

## 8. Bewust open

- Runtime-percentages zijn **niet** gemeten; prio’s zijn kostpatronen uit code.
- T11/W7-gebieden: eerst Spark, dan pas plan-uitbreiding.
- Docs (`TICK_PERFORMANCE_ARCHITECTURE.md` e.d.) mogen later bijgewerkt worden **nadat** bronwijzigingen landen — niet andersom.
