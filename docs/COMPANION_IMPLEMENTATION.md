# LM Companion Implementation Notes

## Package layout

```
org.millenaire.companion/
  CompanionStance, CompanionBond, CompanionBondSavedData
  CompanionService, CompanionCommand, CompanionLifecycle
  CompanionBrain, CompanionPromptBuilder, CompanionReply, CompanionReplyParser
  CompanionIntent, CompanionActionCatalog, CompanionActionExecutor
  CompanionWorldActor, CompanionWorldSensor, CompanionTickBudget
  CompanionTravelJournal, CompanionSharedEventLog
  capability/
    CompanionCapability, CompanionCapabilityRegistry
    Tacz, Waystones, IronsSpells, AdAstra, Create modules
```

## Wiring points

- Config: `MillenaireServerConfig` section `companion`
- Commands: `CompanionCommand` under `/millenaire companion`
- Tick: `CompanionLifecycle.tick` from `Millenaire` ServerTickEvent.Post (AI_SUPPORT / COMPANION phase)
- Entity: `MillVillager` companion flag + NBT; hire state without expiry while companion
- LOD: `EntitySimulationLod` treats companions as NEAR
- Model: `LmStudioModelDiscovery.isAcceptedLocalLlmBaseUrl` (LM Studio + Ollama)

## Summon algorithm

1. Require `companionEnabled` and player permission.
2. Reject if player already has a living bonded companion (unless dismiss first).
3. Resolve nearest village within config range; fail with message if none.
4. Pick villager type (argument or adult from village culture).
5. Spawn via `VillagerSpawnFactory.spawnInVillage`, set companion + hire bond, save bond record.

## Intent execution

`CompanionActionExecutor` validates catalog membership and config allow/deny, then:

- Movement/stance → bond stance + navigation / hire escort behavior
- Gear → pickup `ItemEntity`, inventory add, `EquipmentSlot` equip
- Work/social → `GoalScheduler.forceTask` / priority goals when available
- Living Villages → `NpcActionExecutor` where validated
- Generic → `CompanionWorldActor.useItem|useBlock|attack`
- Mod → capability `tryExecute`

Results append to `recentActionResults` for the next LM user prompt.

## Learning

- Chat: existing `NpcMind` quotes/turns/facts
- Travel: `CompanionTravelJournal` on biome/dimension/village change
- Shared events: combat, gifts, low HP hooks → `CompanionSharedEventLog`
- Preferences: LM `new_memories` + validated preference lines capped in bond/mind summary
