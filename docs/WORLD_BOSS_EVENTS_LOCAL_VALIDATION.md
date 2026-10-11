# World Boss Events — Local Validation

Runtime Minecraft verification is **owner-local**. Agents for this repository do not run GameTests, GitHub CI, or client/server smoke tests.

---

## Automated (agent / CI-safe)

```bash
./gradlew test --tests org.millenaire.worldboss.*
./gradlew compileJava compileTestJava
```

Covers timer pause/advance/no-backlog, player anti-repeat weighting, and weighted boss pick helpers.

---

## Commands (in-game)

```
/millenaire worldboss status
/millenaire worldboss next
/millenaire worldboss list
/millenaire worldboss start
/millenaire worldboss start hemelwachter
/millenaire worldboss end
```

Permission level 2+.

Suggested short-interval config for testing:

- `intervalMinTicks` / `intervalMaxTicks` → `200` / `400`  
- `neutralDwellTicks` → `400`  

Restore defaults after testing. Disabling `enabled` must not wipe SavedData.

---

## Scenario checklist

| # | Scenario | Expect |
|---|----------|--------|
| 1 | Sleep / `/time set` while timer running | Elapsed active ticks unchanged by time jump |
| 2 | All eligible players log out | Timer pauses |
| 3 | Two+ survival players | Anchor selection not always the same UUID |
| 4 | Single boss in pool | Hemelwachter can repeat |
| 5 | Bad spawn (village/structure) | Bounded retries; no every-tick heavy scan; eventual fail → new cooldown |
| 6 | No attack for dwell time | Peaceful depart, no loot |
| 7 | Melee / arrow / owned wolf damage | Provokes; eye red; bossbar |
| 8 | Cactus / fall / unattributed explosion | No provoke |
| 9 | Combat phases | Faster cadence under 60%/30%; telegraphs remain |
| 10 | Melee reach | Core lowers enough to hit |
| 11 | Save/reload in WAITING / NEUTRAL / ENGAGED | No second boss; interval kept; health kept; bossbar sane |
| 12 | Unload boss chunk then return | Reconcile UUID; no replace-spawn |
| 13 | Participant death / logout / dimension change | Disengage timeout → depart; no endless chase |
| 14 | Defeat | One Hemelkern, limited fragments, XP, advancement once |
| 15 | Config disable with active boss | `force_depart` or `leave` per config |
| 16 | Attacks near builds | No terrain/building breaks |

---

## Visual preview

```bash
./gradlew runClient -Pmillenaire.hemelwachterPreview=true
```

Screenshots land under `run/screenshots/hemelwachter_preview/` (`front`, orbits, `provoked_front`).

Compare against `assets/millenaire/textures/entity/hemelwachter/reference/` materials and the written silhouette (3 wing pairs, eye core, rings/halo, ivory/gold/cyan).

**Visual 1:1 match is owner-confirmed.** Agent cannot claim pixel-perfect silhouette agreement without those screenshots.

---

## Hemelkern use

Craft **Hemellantaarn** (`hemellantaarn.json`): gold nuggets + Hemelkern + Hemelfragment → decorative block with light level 15 matching boss palette.
