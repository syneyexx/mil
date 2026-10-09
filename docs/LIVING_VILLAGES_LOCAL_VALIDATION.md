# Living Villages 2.0 — Local Validation Guide

All checkboxes start **unchecked**. Do not mark pass/fail until you have run the scenario locally.

Agent note: builds, CI, GameTests, Minecraft runtime, and live LM Studio were **not** executed by the implementing agent.

Dev diagnostics: `/millenaire dev living needs|events|requests|social` (permission level 2).

---

## 1. New world with generated Millénaire villages

- **Setup:** New world, village generation enabled, Living Villages defaults.
- **Actions:** Locate a Millénaire village; open `/millenaire dev living needs`.
- **Expected:** Command reports village name; needs list may be empty or show verified entries only.
- **Evidence:** Command output; no crash on join.
- **Failure:** Crash, missing command, needs inventing fake shortages with empty inventories.
- **Result:** [ ] Pass [ ] Fail

## 2. Existing world upgraded from pre-phase-3 code

- **Setup:** Load a world saved before Living Villages.
- **Actions:** Enter previously explored village; run living commands.
- **Expected:** World loads; empty living SavedData created; existing villages/quests/chat intact.
- **Evidence:** Logs show no fatal SavedData errors; villagers act normally.
- **Failure:** World load failure; wiped villages.
- **Result:** [ ] Pass [ ] Fail

## 3. Construction blocked by missing resources

- **Setup:** Village with pending project and insufficient townhall materials (or observe natural shortage).
- **Actions:** `/millenaire dev living needs`; talk to builder/chief with NPC chat.
- **Expected:** `CONSTRUCTION_MATERIAL_SHORTAGE` with real missing item ids/quantities ≤ required; dialogue context mentions verified shortage.
- **Evidence:** needs command; chat prompt/system lines; inventory counts.
- **Failure:** Need with wrong quantities; omniscient random NPCs inventing items.
- **Result:** [ ] Pass [ ] Fail

## 4. Player accepting a resource delivery

- **Setup:** Verified construction shortage; open NPC chat with chief/builder.
- **Actions:** Click **Offer help** → **Accept**.
- **Expected:** Request created/accepted; summary shows required quantities ≤ shortage; `/millenaire dev living requests` lists it.
- **Evidence:** Chat feedback; requests command; SavedData after save.
- **Failure:** Accept without offer; quantities above shortage.
- **Result:** [ ] Pass [ ] Fail

## 5. Partial delivery

- **Setup:** Accepted request needing e.g. 32 planks; player holds 16.
- **Actions:** Click **Deliver**.
- **Expected:** 16 transferred to townhall; request shows 16/32; items leave player inventory; no reward yet.
- **Evidence:** Inventories; request progress message.
- **Failure:** Full credit without items; item duplication/loss.
- **Result:** [ ] Pass [ ] Fail

## 6. Full delivery

- **Setup:** Finish remaining materials.
- **Actions:** Deliver until complete.
- **Expected:** Request completes once; reputation increases once; living event recorded; construction can resume when total resources suffice.
- **Evidence:** Reputation change; `living events`; growth resumes naturally.
- **Failure:** Double reputation; instant building completion without resources.
- **Result:** [ ] Pass [ ] Fail

## 7. Duplicate delivery attempt

- **Setup:** Completed or empty-remaining request.
- **Actions:** Spam Deliver / resend packet.
- **Expected:** Idempotent rejection; no extra items/rewards.
- **Evidence:** Inventories unchanged; single completion marker.
- **Failure:** Extra rewards or item sinks.
- **Result:** [ ] Pass [ ] Fail

## 8. Two-player delivery competition

- **Setup:** Two players; same shortage; both obtain offers.
- **Actions:** Both accept; P1 delivers enough to clear shortage; P2 attempts deliver.
- **Expected:** Shared accounting prevents over-delivery; P2 revalidated/cancelled or limited; each gets only earned rewards.
- **Evidence:** Townhall counts; both request states.
- **Failure:** Double-filled shortage; duplicate full rewards.
- **Result:** [ ] Pass [ ] Fail

## 9. Inventory capacity failure

- **Setup:** Fill townhall chests nearly full; attempt large delivery.
- **Actions:** Deliver.
- **Expected:** Only fitting items move; remainder returns to player; request progress matches transferred amount.
- **Evidence:** Player + chest inventories.
- **Failure:** Silent item loss.
- **Result:** [ ] Pass [ ] Fail

## 10. NPC-to-NPC social interaction

- **Setup:** Active village daytime leisure; Socialise/Chat goals fire.
- **Actions:** Observe two villagers complete cultural dialogue (no player LLM).
- **Expected:** Social profiles gain familiarity once per cooldown; no HTTP model calls.
- **Evidence:** `/millenaire dev living social`; network logs show no chat API for that interaction.
- **Failure:** LLM storm; relationship spam every tick.
- **Result:** [ ] Pass [ ] Fail

## 11. Social relationship progression

- **Setup:** Same pair socializes across multiple eligible chats with cooldown respected.
- **Actions:** Wait cooldowns; observe again.
- **Expected:** Familiarity increases in bounds; family types bootstrap from identity when applicable.
- **Evidence:** Social command / NBT inspection of `millenaire_living_social`.
- **Failure:** Unbounded growth; invented spouses.
- **Result:** [ ] Pass [ ] Fail

## 12. NPC knowledge of village events

- **Setup:** Trigger shortage detected or assistance completed.
- **Actions:** Talk to chief/builder vs ordinary resident/child.
- **Expected:** Role-appropriate awareness; hearsay not claimed as firsthand.
- **Evidence:** Dialogue content; knowledge entries on profiles.
- **Failure:** Global omniscience.
- **Result:** [ ] Pass [ ] Fail

## 13. NPC memory after reload

- **Setup:** Complete assistance; save; quit; reload.
- **Actions:** Talk to issuing NPC.
- **Expected:** Assistance knowledge/event still present without model invention.
- **Evidence:** SavedData files; dialogue.
- **Failure:** Memory wiped; fabricated alternate story required.
- **Result:** [ ] Pass [ ] Fail

## 14. Family relationship context

- **Setup:** Villagers with spouse/parent names set in records.
- **Actions:** Inspect social after bootstrap/chat.
- **Expected:** `FAMILY_*` relationship types when names match; no filler relatives.
- **Evidence:** Social SavedData.
- **Failure:** Random family graphs.
- **Result:** [ ] Pass [ ] Fail

## 15. Raid response

- **Setup:** Trigger or wait for real raid (`isUnderAttack`).
- **Actions:** Check needs; talk if combat policy allows; observe guards.
- **Expected:** Security need/event; existing defend/flee goals unchanged; dialogue reflects danger when chat allowed.
- **Evidence:** needs/events; combat behavior.
- **Failure:** Separate raid AI; chatting overrides combat.
- **Result:** [ ] Pass [ ] Fail

## 16. Construction completion

- **Setup:** Shortage resolved by villagers or player; building finishes.
- **Actions:** Watch need clear; event `MATERIAL_SHORTAGE_RESOLVED` / building completed chronicle as applicable.
- **Expected:** Need disappears; no stuck CRITICAL oscillation from hysteresis edge cases after clear resolution.
- **Evidence:** needs command over time.
- **Failure:** Permanent phantom shortage.
- **Result:** [ ] Pass [ ] Fail

## 17. Existing quest compatibility

- **Setup:** NPC with active Millénaire quest step.
- **Actions:** Use Open quest action / normal interact.
- **Expected:** Existing quest UI/progression; no duplicate living-quest assignment.
- **Evidence:** Quest screens; PlayerQuestData.
- **Failure:** Broken quests; double rewards.
- **Result:** [ ] Pass [ ] Fail

## 18. Existing trading compatibility

- **Setup:** Seller/merchant villager.
- **Actions:** Trade action / normal trade.
- **Expected:** Existing trade menus work.
- **Evidence:** Trade UI; stock.
- **Failure:** Trade blocked by living UI.
- **Result:** [ ] Pass [ ] Fail

## 19. Village Book and panel behavior

- **Setup:** Townhall panels / village book.
- **Actions:** Open existing UIs.
- **Expected:** Unchanged resource/pending project panels still function.
- **Evidence:** Panel content.
- **Failure:** Blank/broken panels.
- **Result:** [ ] Pass [ ] Fail

## 20. Universal NPC chat compatibility

- **Setup:** Vanilla villager / modded living entity with chat enabled.
- **Actions:** Open H-key dialogue.
- **Expected:** Universal chat works; no Millénaire request buttons granting village authority.
- **Evidence:** UI; failed living mutation attempts.
- **Failure:** Vanilla mob issues village deliveries.
- **Result:** [ ] Pass [ ] Fail

## 21. LM Studio available

- **Setup:** LM Studio running; npcChat configured.
- **Actions:** Converse about shortage.
- **Expected:** Natural language uses verified context; actions still via buttons.
- **Evidence:** Reply quality; no invented rewards.
- **Failure:** Model invents completable fake quests that mutate state.
- **Result:** [ ] Pass [ ] Fail

## 22. LM Studio unavailable

- **Setup:** Stop LM Studio / invalid endpoint.
- **Actions:** Needs, accept, deliver, socialize.
- **Expected:** Simulation and deliveries work; fallback dialogue only.
- **Evidence:** Deliveries succeed offline.
- **Failure:** Requests blocked because model is down.
- **Result:** [ ] Pass [ ] Fail

## 23. NPC unloaded mid-conversation

- **Setup:** Open chat; move away / unload chunk if possible.
- **Actions:** Send message / action.
- **Expected:** Session closes or rejects safely; no crash; no orphan mutations.
- **Evidence:** Client close/reject; logs.
- **Failure:** Crash; duplicated rewards later.
- **Result:** [ ] Pass [ ] Fail

## 24. Player disconnect during a request

- **Setup:** Accepted in-progress request.
- **Actions:** Disconnect; reconnect.
- **Expected:** Request persists; can continue delivery; proposals cleared on logout.
- **Evidence:** requests command after rejoin.
- **Failure:** Lost progress; stuck ACCEPTED with wiped data.
- **Result:** [ ] Pass [ ] Fail

## 25. Long-running village state

- **Setup:** Run village across many Minecraft days.
- **Actions:** Natural growth, births/deaths, merchants, assistance.
- **Expected:** Events accumulate then prune; social/knowledge bounded.
- **Evidence:** SavedData sizes; retention caps.
- **Failure:** Unbounded NBT growth.
- **Result:** [ ] Pass [ ] Fail

## 26. Save-data growth

- **Setup:** After extended play.
- **Actions:** Inspect `millenaire_living_*` SavedData sizes.
- **Expected:** Caps respected (events/relationships/knowledge/requests).
- **Evidence:** File size / entry counts via living commands.
- **Failure:** Megabyte-per-day growth from low-value spam.
- **Result:** [ ] Pass [ ] Fail

## 27. Village unloading and reactivation

- **Setup:** `forceLoadVillages=false` optional; leave and return.
- **Actions:** Unload area; return.
- **Expected:** No forced chunk load solely for social; needs recompute on active tick.
- **Evidence:** Chunk behavior; needs after return.
- **Failure:** Villages never unload.
- **Result:** [ ] Pass [ ] Fail

## 28. Performance with multiple populated villages

- **Setup:** Several active villages.
- **Actions:** Watch MSPT; living debug logging optional.
- **Expected:** Staggered needs eval; budget limits; no per-NPC model calls on tick.
- **Evidence:** Profiler / logs / MSPT comparison.
- **Failure:** Tick spikes scaling with NPC count from living systems.
- **Result:** [ ] Pass [ ] Fail
