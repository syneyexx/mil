# Religion System — Local Validation

**Owner-run only.** This agent does not execute GameTests, GitHub CI, Gradle builds, or Minecraft client/server smoke tests.

Architecture: `docs/RELIGION_ARCHITECTURE.md`  
Implementation ledger: `docs/RELIGION_IMPLEMENTATION.md`

## Prerequisites

- Minecraft 1.21.1 + NeoForge matching `gradle.properties`
- Fresh or existing world with Millénaire villages
- Optional: Living Villages on/off, civilization on/off, LLM provider absent

## Build (owner)

```bash
./gradlew build
```

## Scenario checklist

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Old world without religion SavedData | Communities/profiles init lazily; no crash; no forced affiliations until villagers are evaluated |
| 2 | Save/reload during PLANNED/PREPARING/GATHERING/ACTIVE | Ceremony resumes; `outcomeApplied` / `suppliesConsumed` prevent double rewards/consumption |
| 3 | Missing location / clergy / supplies / participants | Ceremony DEFERRED or CANCELLED with history reason; no fake completion |
| 4 | Attack during gathering/active | INTERRUPTED; resumes or cancels safely when clear |
| 5 | Migration of affiliated NPC | Same UUID keeps `NpcFaithProfile`; no reset to host culture |
| 6 | Temporary pilgrimage | No duplicate villager records; no permanent housing assignment; return by deadline |
| 7 | Sleep / time skip | At most catch-up limit; no unbounded backlog of ceremonies |
| 8 | Unloaded village chunks | No force-load; no invented offscreen completion |
| 9 | `religionEnabled=false` then true | Simulation pauses; data retained; resumes without wipe |
| 10 | Custom culture + content reload | Invalid religion refs log diagnosis; other cultures remain usable |
| 11 | Unauthorized / repeated player religion actions | Server rejects; dedupe window; no inventory theft |
| 12 | No LLM provider | Dialogue still shows religion context strings; actions work via Living action buttons |
| 13 | `civilizationEvolutionEnabled=false` | Village religion still ticks and shows in Village Book |
| 14 | Existing work/trade/security goals | Combat/urgent still preempt leisure ceremony attendance |
| 15 | Designate SHRINE purpose | Operational designated shrine usable as ceremony location |
| 16 | outlawz village | Explicit no organized religion; book shows that state |
| 17 | Japanese village | Dual-practice tradition content loads; daily meditation / kami offering tags resolve |

## Content creator smoke

1. Copy `millenaire/religion/examples/example_tradition.json` pattern into a custom submod under `religion/traditions/`.
2. Add matching ritual under `religion/rituals/`.
3. Bind via `cultures/<id>/religion.json`.
4. Reload content / restart; check logs for `[Religion]` validation errors.

## Known validation limits

- Crash consistency across SavedData + chunk files is best-effort idempotent recovery, not atomic multi-file transactions.
- Supernatural blessing effects are intentionally out of scope for the first complete execution.
