# Het Labyrint — Release-ready Psyche Plan (v2, nagekeken)

**Status:** owner-GO ontvangen (“Maak maar”). Implementatie P1→P5 in uitvoering.  
**Versie:** 2 — plan herzien na zelf-audit (“is dit wat ik wil shippen op release-dag?”).

**Doel van deze werkronde:** na afronding is Het Labyrint **game-release-ready** als Millénaire-feature: Revival-**psyche** (liminaal, wrong, awe), speelbaar end-to-end, stabiel, gedocumenteerd — **zonder Hellraiser-IP**, **zonder Configuratie-theater**.

---

## 0. Zelf-audit: was v1 genoeg voor release-dag?

**Kort antwoord: bijna — niet helemaal.** v1 had de juiste vibe-pijlers, maar miste release-gaten die ik op een echte ship-dag niet zou accepteren:

| Gat in v1 | Risico op release-dag | Fix in v2 |
|-----------|----------------------|-----------|
| Arrival / eerste 30s niet als eigen levering | DoD A faalt terwijl P1 “zones” claimt | **P1.0 Arrival nave** expliciet |
| Quest-pad fallenking 11/12 niet als gate | Speler strandt in nieuwe layout | **P5.0 quest continuity** |
| Soft-lock in loops | Rage-quit / broken release | **P3.4 escape guarantees** |
| Geen performance SLO | TPS-dip = unshippable | **§2.F + P1.5 budgets** |
| Geen player-facing migratie | Owners weten niet dat dim weg moet | **P5.5 + README banner** |
| “100%” klonk als Unreal | Verkeerde verwachting | **§1 ceiling** aangescherpt |
| Art-tiers vaag | P5 eindigt cosmetisch half | **P5 shippable silhouette bar** |
| Multiplayer/edge cases onbenoemd | Chase/loop rare bugs | **§2.E + P5.1** |

**Conclusie:** dit v2-plan is wat ik wél wil leveren als release-ready eindresultaat. v1 was de juiste richting; v2 is de ship-bar.

---

## 1. Ceiling (wat “100%” wél en níet betekent)

**Wel:** psyche-DoD (§2) volledig groen op verse Labyrinth-dim; compile groen; owner smoke pass; quests werkend; defaults release; migratie duidelijk.

**Niet:** Unreal photorealism, custom OST zonder jouw audio-files, Hellraiser-IP, Configuratie morph-UI.

Minecraft blijft Minecraft. De lat is: **“dit voelt als Revival’s Labyrinth-psyche in Millénaire”**, niet “Saber Interactive build”.

---

## 2. Definition of Done — Release Psyche (harde bar)

Alles hieronder moet waar zijn vóór “klaar”. Geen “bijna”.

### A. Eerste minuut (Arrival)
1. Binnen **10s** na enter: koude liminaliteit (licht, fog, **audio**) — geen Nether-blauw.
2. Arrival is een **bewuste eerste compositie** (niet een 8×8 hok met Scharnier).
3. Binnen **60s**: minstens één “dit is geen normale MC-ruimte”-beat (schaal / loop / domestic bleed).

### B. Architectuur
4. Zichtbare vocabulary: smalle gang / kamer / suite / nave — niet uniform 8×8.
5. Liminale **suites** ≥16×16 (target 24) met **≥30 props** + seed-variatie wrongness.
6. Suite leest **≥3s** als huis vóór wrongness.

### C. Psyche-systemen
7. ≥**twee** soft-loop layouts; escape altijd mogelijk (reverse of gedocumenteerde hint); **geen soft-lock**.
8. Chase dwingt leerbare keuze; presence/audio ∝ nabijheid; geen spawn-spam.
9. Zone-arc voelbaar: Arrival → Bleed → Orde → Approach → Audience/Core.

### D. De Ruit
10. Audience-nave awe-schaal (≥24–32 of multi-cell; hoge band; processie ≥20).
11. Eerste audiëntie = *gezien worden* (presence), niet “ijsdiamant gevonden”.

### E. Content & multiplayer sanity
12. `fallenking_11` / `fallenking_12` (of opvolger) **haalbaar** door nieuwe layout (Core-tag, Audience-tag, relics/poorten).
13. Loops/chase/audience werken in singleplayer; geen bekende soft-lock in SMP smoke (1–2 spelers).

### F. Release engineering
14. `./gradlew compileJava` groen.
15. Config-flags; **defaults = release-feel aan**.
16. `labyrinthLayoutVersion=2` + **player-facing** migratie (README + command/note: delete/regenerate dim).
17. Validation smoke dekt §2 A–E met pass/fail.
18. Geen Hellraiser-IP; lore = Configuratie / Gebondenen / Orde / De Ruit.
19. Performance SLO: Labyrinth idle met 1 speler mag de server Post-tick board niet stelselmatig overschrijden; large stamps lazy/budgeted; entity cap behouden/aangescherpt.

**Configuratie-theater is géén DoD-item.**

---

## 3. Campagne P1→P5 (ship-volgorde)

### Wave P1 — Ruimte, Arrival, zones

| # | Levering |
|---|----------|
| **P1.0** | **Arrival 2.0** — eerste compositie: koude nave/vestibule, Scharnier leesbaar, onmiddellijke liminal cue |
| P1.1 | Layout v2 footprints: CORRIDOR(8) / ROOM(16) / SUITE(24) / NAVE(32+) |
| P1.2 | Corridor vocabulary (3-breed, T’s, roosters/spikes, galerijen) |
| P1.3 | Zone acts: Arrival → Bleed → Orde → Approach → Core/Audience |
| P1.4 | Vertical atriums / overlooks |
| P1.5 | `labyrinthLayoutVersion=2`; stamp budgets; lazy interior where needed |

**Wave-DoD:** DoD A deels + B4; fly-through 5 zones; Arrival alleen al “release first minute”-waardig.

**Breaking:** ja — verse dim.

---

### Wave P2 — Audio presence bed

| # | Levering |
|---|----------|
| P2.1 | Sound events + layered vanilla placeholders (drone, metal, whisper, chase, Ruit) |
| P2.2 | Zone crossfade |
| P2.3 | Chase proximity audio + blink stinger |
| P2.4 | Ruit nadering-drone (eigen curve) |
| P2.5 | Resource-pack slot docs voor latere echte SFX |

**Wave-DoD:** DoD A1 audio; headphones “wrong” ≤10s in bleed; Ruit presence hoorbaar.

---

### Wave P3 — Liminal suites + veilige loops

| # | Levering |
|---|----------|
| P3.1 | Multi-cell suites (appartement, deurengang, keuken+eetkamer, kindervleugel) |
| P3.2 | 30–60 props; valse deuren; scheve frames; dichtheid |
| P3.3 | Wrongness intensity 0–3 (seed) |
| P3.4 | ≥2 loop layouts; **escape gegarandeerd**; Gids/Archivaris hint na N fails; anti-soft-lock test |
| P3.5 | Memory props (lectern lore; portraits change on revisit) |
| P3.6 | Subtiele warm→cold enter/exit (geen UI-theater) |

**Wave-DoD:** DoD B5–B6, C7.

---

### Wave P4 — De Ruit & Approach

| # | Levering |
|---|----------|
| P4.1 | Awe-nave + processie |
| P4.2 | Grote Ruit + subtiele presence (particles/beam sober) |
| P4.3 | Approach: stilte/drone; silent pew Gebondenen |
| P4.4 | First-audience aangescherpt; returns sober |
| P4.5 | Core = Architect; Audience = psyche (geen verplichte 2e boss) |

**Wave-DoD:** DoD D10–D11.

---

### Wave P5 — Chase, art, quests, release lock

| # | Levering |
|---|----------|
| **P5.0** | **Quest continuity:** fallenking_11/12 tags/routes werken in layout v2; NL/EN strings bijgewerkt |
| P5.1 | Chase 2.0: telegraphs, caps, zone-aware; SMP sanity |
| P5.2 | Orde materials (`orde_stone` / `orde_panel` / `bleed_planks`) |
| P5.3 | Gebondene **shippable** silhouette tiers (Leader/Gate/Social) — duidelijk verschillend in-game |
| P5.4 | Rammelaar + Architect presence hooks |
| P5.5 | Full smoke §2; README migratie-banner; architecture sync; config defaults; optional `/millenaire labyrinth resetdim` (of gedocumenteerde wipe) |

**Wave-DoD:** **Alle §2 items afgevinkt.** Anders geen “release-ready”.

---

## 4. Buiten scope (blijft buiten)

| Out | Reden |
|-----|--------|
| Configuratie morph-UI / vignette / theater | Owner |
| Hellraiser-IP | Policy |
| Volledige custom OST zonder jouw files | Placeholders + slots |
| Pure slashers i.p.v. frenemy-Orde | Millénaire-ziel |
| Photoreal shaders | Pipeline |

---

## 5. Release discipline

1. Eén wave; commit; push; PR updaten.  
2. Na elke wave: compile + smoke-sectie.  
3. P5 eindigt alleen op volledige §2.  
4. Assets geblokkeerd → placeholders + slots; vibe wacht niet op perfecte WAV.  
5. Na P1: harde migratie-communicatie.

---

## 6. Owner-GO (antwoord hierop)

1. Akkoord met **v2** psyche-DoD + P1→P5 (incl. Arrival P1.0 + quest gate P5.0)?  
2. Breaking layout v2 OK?  
3. Audio placeholders OK?  
4. Multi-chunk suites OK?  
5. Full P1 bij start (geen half)?  

**Aanbevolen:** alles ja.

---

## 7. Eindantwoord op “is dit je release-eindresultaat?”

**v1 was dat nog niet.**  
**v2 wél:** dit is het plan waarmee ik op een game-release-dag zou durven zeggen dat Het Labyrint psyche-ready is — mits §2 volledig groen na P5.

Geen code tot jij GO geeft op v2.
