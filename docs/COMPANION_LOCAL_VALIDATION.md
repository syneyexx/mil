# LM Companion Local Validation

## Prerequisites

1. Millenaire built and loaded; nearest village exists for summon tests.
2. Local LM running:
   - **LM Studio:** server on `http://127.0.0.1:1234/v1`, one model loaded
   - **Ollama:** `ollama serve`, model pulled, base URL `http://127.0.0.1:11434/v1`
3. Server config `npcChat.enabled=true` and matching `npcChat.baseUrl`.
4. `companion.enabled=true` (code present; still inert until summon).

## Checklist

- [ ] `/millenaire companion status` — no companion before summon
- [ ] `/millenaire companion summon` — spawns bonded villager near player/village
- [ ] Companion follows / free-roams per stance; speech bubbles appear without opening chat UI
- [ ] Drop armor + ask to equip — pickup + wear
- [ ] `/millenaire companion dismiss` — entity gone; resummon restores bond/mind continuity
- [ ] Switch base URL to Ollama — chat/companion still resolve a model
- [ ] MSPT: with one companion, no sustained spike vs hired escort alone (Spark / `/millenaire tick status`)
- [ ] Let Me Despawn: companion remains after leaving area briefly (persistent bond)
- [ ] Missing Create/Ad Astra/Iron's: no crash; capability intents report unavailable

## Perf expectations

- Companion brain work skipped when `ServerTickGovernor` has no time
- Heartbeat not every tick; LM off-thread
- Max 1 companion per player
