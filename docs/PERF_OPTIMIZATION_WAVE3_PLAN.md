# Millénaire — Performance Wave 3 (Codex audit follow-through)

**Autoriteit:** Java-bron onder `src/main/java/org/millenaire` (NeoForge 21.1 / MC 1.21.1).  
**Input:** Codex Sol 6.1 bronaudit op commit `583399e`, geverifieerd en geïmplementeerd.  
**Invarianten:** niet-destructief; `worldgenFastSectionWrites` blijft default **uit**; RNG/worldgen-identiteit ongewijzigd; combat/urgent Far-taken blijven responsief.

## Doel

Wave 2 zette governor, LOD, path-queue, compute-pool en materialisatiebudgetten neer. Wave 3 maakt die systemen **betrouwbaar**: juiste deadlines, begrensde wachtrijen, exacte dirty-IDs, eventgedreven highway/village-work, hervatbare prefill.

Geen MSPT-%-claims — owner valideert met Spark volgens `TICK_PERFORMANCE_LOCAL_VALIDATION.md`.

## Status

| Stap | Werk | Status |
|------|------|--------|
| 1 | Counters (entity admission, mailbox drops, path rejects, placement compile) | **done** |
| 2 | Pre/entity-deadline; WORLDGEN/HIGHWAY `min(global, phase)` + leftover | **done** |
| 3 | Prefill 0–8 cell-cursor; begrensde footprint chunk/ticket pipeline | **done** |
| 4 | Mailbox cap + apply deadline + session epoch; immutable highway attachments | **done** |
| 5 | Exact `markDirtyPlan(planId)`; echte map dirty-ID delta + packetlimiet | **done** |
| 6 | Highway topologyRevision + active-routequeue + journey connected cache | **done** |
| 7 | Hot village set + Far due-slots; Far task-aware pause; waterTicks elapsed | **done** |
| 8 | Nav capture zonder force-load; span-cap; path reject counters | **done** (gedeelde navtiles bewust beperkt) |
| 9 | Client naamcache per entity (LRU 64) | **done** |
| 10 | Fast section writer / opslagpartitionering | **uitgesteld** (correctheidsbewijs ontbreekt) |

## Bewust open / niet gedaan

- Volledige shared navigation tiles tussen NPC’s (alleen unload-safe capture + kleinere span).
- Incrementeel save-formaat / region-partitionering van `SettlementPlanSavedData`.
- `worldgenFastSectionWrites` aanzetten of heightmap/POI-equivalentie.
- Runtime p50/p95/p99 sampling van serverticks (owner Spark).
- Civilization stages volledig hervatbaar over ticks (wel: `hasTime` tussen managers binnen multi-unit stages).

## Acceptatie (owner)

Zie Codex benchmarkplan: zelfde seed/wereldkopie, warmup + herhalingen, één maatregelgroep tegelijk. Afwijzen bij stilstaande dorpen, oplopende queueleeftijd, tragere combat, inventarisdupes, incomplete wegen.
