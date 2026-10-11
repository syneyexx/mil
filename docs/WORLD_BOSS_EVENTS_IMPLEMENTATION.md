# World Boss Events — Implementation

Companion to `docs/WORLD_BOSS_EVENTS_ARCHITECTURE.md`. Describes what landed in code.

---

## Packages / files

| Area | Location |
|------|----------|
| Planner / FSM / SavedData | `org.millenaire.worldboss.*` |
| Commands | `org.millenaire.worldboss.command.WorldBossCommand` |
| Spawn | `org.millenaire.worldboss.spawn.*` |
| Hemelwachter entity | `org.millenaire.entity.HemelwachterEntity` |
| Projectile | `org.millenaire.entity.CelestialBoltEntity` |
| Model / atlas UV | `org.millenaire.client.model.HemelwachterModel`, `HemelwachterAtlas` |
| Renderer / emissive | `org.millenaire.client.render.HemelwachterRenderer`, `HemelwachterEmissiveLayer` |
| Preview | `org.millenaire.client.HemelwachterPreviewSession` (`-Dmillenaire.hemelwachterPreview=true`) |
| Config | `MillenaireServerConfig` → `worldBossEvents` |
| Items / block | `hemelkern`, `hemelfragment`, `hemellantaarn` |
| Textures | `assets/millenaire/textures/entity/hemelwachter.png` (+ `_emissive.png`) |
| Reference originals | `assets/millenaire/textures/entity/hemelwachter/reference/` |
| Loot / recipe / advancement | `data/millenaire/loot_table/...`, `recipe/hemellantaarn.json`, `advancement/hemelwachter_defeat.json` |
| Unit tests | `src/test/java/org/millenaire/worldboss/*Test.java` |

Wiring: `WorldBossDefinitions.bootstrap()` and `WorldBossEventPlanner.tick` from `Millenaire`; renderers/layers in `ClientEvents`.

---

## Behaviour delivered

- One world timer (default 72k–108k active ticks), pauses without eligible players, persists interval + elapsed, no sleep/`/time set` coupling, no backlog multi-fire.
- Extensible `WorldBossDefinitionRegistry` with weighted pick + anti-repeat; only Hemelwachter registered.
- Aerial spawn 40–70 blocks out, clearance/village/structure/border checks, bounded retries + backoff.
- Neutral arrive → observe → depart (~5 min) or provoke (attributed player damage) → combat with eye beam / wing buffet / 3 celestial bolts, phases at 60%/30%.
- Bossbar for nearby combat-eligible players; cleared on depart/death.
- Defeat: one Hemelkern, few Hemelfragmenten, XP, advancement; Hemelkern crafts Hemellantaarn (light 15).
- Admin: `/millenaire worldboss status|next|start [bossId]|end|list` (also `/worldboss …`), permission 2.
- Disable policy: `force_depart` (default) or `leave`; SavedData retained.

---

## Visual silhouette decisions

Approved inputs were **1254×1254 atlas materials** (PNG + JPEG source + UV JSON), not a separate 3D concept render. Geometry follows written requirements:

- **3 wing pairs** (upper/mid/lower), layered blocky feathers, gold leading edges  
- Central ivory core + large front eye (amber → red UV swap)  
- Outer + inner gold rings, cyan-gem halo arc  
- Hitbox ~3.2×4.5 around core (not full wingspan)  
- Model units aim at ~12 block height / ~20 block span  

Owner must confirm silhouette match via preview screenshots (see local validation).

---

## Adding a second boss

1. Entity + client model/renderer (+ optional projectile).  
2. Register in `ModEntities` / `ClientEvents` / `MillModelLayers`.  
3. `WorldBossDefinitions.bootstrap()` → `registry.register(WorldBossDefinition.builder(...).build())` **before** `freeze()` (or unfreeze pattern if extended later).  
4. Config weight/availability keys as needed.  
5. Lang / loot / advancement.  

No planner changes required.

---

## Recovery guarantees (honest)

- Timer fields and active event record live in overworld `SavedData` (`millenaire_world_boss_events`).  
- Entity combat/health in entity NBT.  
- Unloaded boss chunk ≠ missing; planner waits.  
- Not an atomic multi-file transaction across entity chunk + SavedData.
