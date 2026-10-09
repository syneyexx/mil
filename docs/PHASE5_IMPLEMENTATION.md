# Phase 5 — Civilization Evolution & Release Hardening

**Baseline HEAD:** `cc7af9ce9acb0da99f732a70d5cc01e4be3e3973` (main)  
**Branch:** `cursor/civilization-evolution-7128`  
**Status legend:** `PENDING` · `IN_PROGRESS` · `IMPLEMENTED_UNVERIFIED` · `BLOCKED`

Builds / CI / Minecraft / live AI were **not** executed by this agent.

## Merged baseline inventory

| Phase | SHA | Present |
|-------|-----|---------|
| Universal NPC dialogue | `8e57289` | Yes |
| Living Villages 2.0 | `e5894a6` | Yes (`org.millenaire.living`) |
| Worldgen overhaul | `cc7af9c` | Yes (`org.millenaire.worldgen`) |

## Authority map (5.0)

| Subsystem | Canonical owner | SavedData | Lifecycle entry | Phase 5 action |
|-----------|-----------------|-----------|-----------------|----------------|
| Factions / membership | `FactionManager` | `millenaire_factions` | `FactionLifecycle` / orchestrator | EXTEND orchestration |
| Relationships | `RelationshipManager` | relationship SD | `tickDecay` | Keep; orchestrate cadence |
| Economy / trade | `TradeManager` | economy SD | `TradeManager.tick` | EXTEND physical transfer + needs bridge |
| Diplomacy | `DiplomaticManager` | diplomacy SD | `tick` | EXTEND event publish |
| Politics | `PoliticsManager` | politics SD | `tick` | EXTEND succession events |
| Military | `MilitaryManager` | military SD | `tick` | Keep; orchestrate |
| War | `WarManager` | war SD | `tick` | EXTEND event publish |
| Territory | `TerritoryManager` | territory SD | `tick` | Keep |
| Internal conflict | `InternalConflictManager` | conflict SD | `tick` | Keep |
| Village needs / events | `LivingVillagesService` / `LivingEventService` | living event SD | Living Villages lifecycle | INTEGRATE |
| Worldgen activation | `SettlementActivator` | worldgen plans SD | `WorldgenEngine` | FIX founded event |
| Population | `VillagerRecord` in `Village` | villages SD | village tick | ADD aggregator + migration txn |
| Offscreen | `OffscreenReconciler` | civilization SD | orchestrator | ADD reconciler |

## Phase checklist

| Phase | Status |
|-------|--------|
| 5.0 Baseline audit | IMPLEMENTED_UNVERIFIED |
| 5.1 Lifecycle orchestration | IMPLEMENTED_UNVERIFIED |
| 5.2 Identity / territory linkage | IMPLEMENTED_UNVERIFIED |
| 5.3 Demographics / migration | IMPLEMENTED_UNVERIFIED |
| 5.4 Economy / conserved trade | IMPLEMENTED_UNVERIFIED |
| 5.5 Politics event integration | IMPLEMENTED_UNVERIFIED |
| 5.6 Diplomacy event integration | IMPLEMENTED_UNVERIFIED |
| 5.7 War/peace event integration | IMPLEMENTED_UNVERIFIED |
| 5.8 Settlement evolution labels | IMPLEMENTED_UNVERIFIED |
| 5.9 World history bridge | IMPLEMENTED_UNVERIFIED |
| 5.10 Player UI / localization | IMPLEMENTED_UNVERIFIED |
| 5.11 Offscreen reconciliation | IMPLEMENTED_UNVERIFIED |
| 5.12 Whole-project release audit | IMPLEMENTED_UNVERIFIED |
| 5.13 Documentation + final report | IMPLEMENTED_UNVERIFIED |

## Files created / modified (ledger)

### Created
- `src/main/java/org/millenaire/civilization/**` (orchestrator, saved data, bridges, trade, population, offscreen, settlement, command)
- `docs/PHASE5_IMPLEMENTATION.md`
- `docs/CIVILIZATION_ARCHITECTURE.md`
- `docs/CIVILIZATION_LOCAL_VALIDATION.md`
- `docs/FINAL_RELEASE_AUDIT.md`
- `docs/FINAL_FEATURE_INVENTORY.md`

### Modified
- `Millenaire.java` — tick → `CivilizationOrchestrator` when enabled
- `MillenaireServerConfig.java` — civilization section
- `FactionManager.java` — `reconcileSettlements` (pre-existing extension)
- `LivingEventType.java` — civilization event types
- `SettlementActivator.java` — LivingEvent founded emit
- `VillageOverviewPanelGenerator.java` — civilization panel lines
- `VillageContextProvider.java` / `NpcWorldKnowledgeProvider.java` — civ context
- `DevCommand.java` — register civilization command
- `en_us.json` / `nl_nl.json` — config/panel/event strings

## Decisions
1. Extend FactionLifecycle via orchestrator wrapper; keep original as disable fallback.
2. Physical trade conservation uses inventory remove/add with compensation; abstract ledgers stay derived.
3. `FactionId` is ResourceLocation-based — never call `.uuid()`; use `asString()`.
4. Autonomous founding remains disabled until safe live colonization exists.
5. Chronicle uses state markers to avoid reload spam.

## Next for user
Run local Gradle build, GameTests if any, and `docs/CIVILIZATION_LOCAL_VALIDATION.md` scenarios.
