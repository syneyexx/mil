# Het Labyrint — Release-ready Psyche Plan (na Wave 8)

**Status:** wacht op owner-akkoord. Geen implementatie tot GO.  
**Doel van deze werkronde:** na merge voelt Het Labyrint als een **ware Revival-psyche-ruimte** — liminaal, onheilspellend, architecturaal wrong — en is het **release-ready** binnen Millénaire (Minecraft 1.21.1 / NeoForge), **zonder Hellraiser-IP**.

**Niet het doel:** Configuratie-“theater” (morph UI, vignettes, showy VFX). Functionele Configuratie blijft; spectakel is bijzaak.

---

## 1. Eerlijk startpunt

Wave 1–8 = **juiste motieven, te weinig psyche**.

| Nu | Nodig voor Revival-psyche |
|----|---------------------------|
| 8×8 grid-dungeon | Ongelijke schaal, lange gangen, atriums, suites |
| Prop-kamertjes | Huizen die 3+ seconden “echt” voelen, dan wrong |
| De Ruit ≈ ijsdiamant | Overweldigende aanwezigheid |
| Vanilla cave-audio | Zone-bed dat je lichaam raakt |
| Chase = blink | Paniek-navigatie die je leert |

**100% in deze context** = psyche-DoD hieronder gehaald op een verse Labyrinth-dimensie, compile groen, smoke-checklist door owner speelbaar — niet Unreal photorealism.

---

## 2. Definition of Done — Release Psyche (harde bar)

Alles hieronder moet waar zijn vóór “klaar”:

### A. Eerste minuut
1. Binnen **10s** na enter: koude liminaliteit (licht, fog, audio) — geen “Nether met blauwe tint”.
2. Binnen **60s**: minstens één moment van *“dit is geen normale Minecraft-ruimte”* (schaal, loop, of domestic bleed).

### B. Architectuur
3. Corridors **niet** uniform 8×8-kamers; zichtbare vocabulary: smalle gang / kamer / suite / nave.
4. Liminale **suites** ≥ 16×16 (idealiter 24) met **≥30 props** en seed-variatie in wrongness.
5. Speler kan een suite **≥3 seconden** als “huis” lezen vóór wrongness klikt.

### C. Psyche-systemen
6. Minstens **twee** soft-loop / reverse-escape ervaringen (niet alleen één ECHO hub).
7. Chase dwingt **keuze** (veilige tak leerbaar); audio/presence schaalt met nabijheid.
8. Domestic → Orde bleed voelbaar in zones (Arrival → Bleed → Orde → Approach → Audience).

### D. De Ruit
9. Audience-nave is **groot genoeg voor awe** (multi-cell of ≥24–32 footprint, hoge plafondband).
10. Eerste audiëntie voelt als *gezien worden* (stilte/drone + presence), niet als “ik vond een diamantje”.

### E. Release engineering
11. `./gradlew compileJava` groen.
12. Config-flags voor zware systemen; defaults aan voor release-feel.
13. `labyrinthLayoutVersion` (breaking layout) gedocumenteerd; verse dim verplicht na upgrade.
14. `LABYRINTH_LOCAL_VALIDATION.md` smoke A–D hierboven dekt.
15. Geen Hellraiser-IP-namen/assets; lore blijft Configuratie / Gebondenen / Orde / De Ruit.
16. Tick-budget: geen per-tick mass rebuilds; stamp/lazy rules behouden of verbeterd.

**Configuratie-theater is expliciet géén DoD-item.**

---

## 3. Campagne — vijf waves tot release

Volgorde is strikt. Elke wave eindigt speelbaar + smoke; volgende start pas als DoD-stukken van die wave groen zijn.

### Wave P1 — Ruimte & zones (fundament van psyche)

**Waarom eerst:** Zonder schaal blijft alles “Minecraft-kamer”.

| # | Levering |
|---|----------|
| P1.1 | Layout v2: chamber footprints `CORRIDOR(8)` / `ROOM(16)` / `SUITE(24)` / `NAVE(32+)` |
| P1.2 | Corridor vocabulary: 3-breed gangen, T-junctions, roosters boven spikes, galerij-leuning |
| P1.3 | Zone acts (seed-stabiel): Arrival → Domestic Bleed → Orde Industrial → Approach → Core/Audience |
| P1.4 | Vertical atriums: 2–3 banden zichtbaar (overlook), niet alleen ladderschacht |
| P1.5 | `labyrinthLayoutVersion=2` + docs; performance budgets voor large stamps |

**Wave-DoD:** Fly-through toont 5 zone-karakters; corridors voelen langer/enger dan Wave 8.

**Breaking:** ja — verse Labyrinth-dimensie verplicht.

---

### Wave P2 — Audio presence bed (~40% vibe)

**Waarom:** Psyche is half gehoor.

| # | Levering |
|---|----------|
| P2.1 | Sound events + layered vanilla placeholders: cold drone, metal stress, whisper bed, chase clicks, Ruit pulse |
| P2.2 | Zone crossfade (Arrival / Bleed / Orde / Audience) |
| P2.3 | Chase: volume/paniek ∝ afstand; stinger op blink |
| P2.4 | Ruit: naderende drone (eigen curve, geen Warden-kloon) |
| P2.5 | Document slot-namen voor latere echte SFX (resource-pack vriendelijk) |

**Wave-DoD:** Headphones, UI uit: “wrong” ≤10s in bleed; “presence” bij Ruit.

**Theater:** geen morph-UI; alleen presence audio.

---

### Wave P3 — Liminal psyche suites (mind-fuck kern)

**Waarom:** Revival’s huis/bleed is het psyche-wapen.

| # | Levering |
|---|----------|
| P3.1 | Multi-cell suites: appartement, lange deurengang, keuken+eetkamer, kindervleugel |
| P3.2 | Detail density 30–60 props; scheve frames; valse deuren; dubbele meubels |
| P3.3 | Wrongness intensity 0–3 (seed): cosmetisch → onmogelijk → Orde-bleed door muren |
| P3.4 | Soft corridor-loops in suites (reverse = escape); ≥2 distinct loop layouts |
| P3.5 | Memory props: lectern-lore; portraits die veranderen bij herbezoek |
| P3.6 | Enter/exit: korte warmte-snap → terug naar cold (subtiel, geen UI-theater) |

**Wave-DoD:** “Dit voelt als een huis” ≥3s; daarna wrongness; loops leerbaar.

---

### Wave P4 — De Ruit & Approach (climax presence)

**Waarom:** God-moment moet zwaar wegen.

| # | Levering |
|---|----------|
| P4.1 | Audience nave ≥24–32 footprint (of multi-cell); processie ≥20 blokken; hoge plafondband |
| P4.2 | De Ruit span ~15–21; particle shell; subtiele beam naar dais (presence, geen fireworks) |
| P4.3 | Approach corridor: stilte + drone build; silent Gebondenen in pews (kijken, geen chat) |
| P4.4 | First-audience ritueel aangescherpt (bestaande tag); return visits sober |
| P4.5 | Core blijft Architect-fight; Audience blijft psyche/presence (geen tweede boss verplicht) |

**Wave-DoD:** Screenshot/ervaring = “dit is de god van deze plek”.

---

### Wave P5 — Chase, materialen, release lock

**Waarom:** Paniek + art consistency + ship bar.

| # | Levering |
|---|----------|
| P5.1 | Chase 2.0: safe-branch blijft; betere telegraphs; geen spam-spawns; zone-aware |
| P5.2 | Orde material set (`orde_stone` / `orde_panel` / `bleed_planks`) — breekt blackstone-monotonie |
| P5.3 | Gebondene silhouette tiers (Leader/Gate/Social) — leesbaar zonder gore-IP |
| P5.4 | Rammelaar presence (animatie/SFX hook); Architect meetlat-silhouet |
| P5.5 | Full smoke rewrite; config defaults release; README/architecture sync; layout migration note |

**Wave-DoD:** Alle §2 DoD-items afgevinkt; owner smoke A–D; compile groen.

---

## 4. Expliciet buiten deze release-ronde

| Out | Reden |
|-----|--------|
| Configuratie morph-UI / vignette / “theater” | Owner: hoeft niet |
| Letterlijke Hellraiser-IP | Policy + lore |
| AAA custom OST zonder owner-files | Placeholders + slots; echte stems later |
| Pure combat-cenobite rewrite | Millénaire frenemy-ziel behouden |
| Photoreal shaders | Buiten mod-pipeline |

---

## 5. Werkwijze (release discipline)

1. Eén wave tegelijk; commit met wave-tag; push; PR-body updaten.
2. Na elke wave: compile + smoke-sectie in validation docs.
3. Geen “bijna klaar”: P5 eindigt alleen als §2 volledig waar is.
4. Bij blokkade (assets): ship placeholders + documenteer owner-drop slots — vibe mag niet wachten op perfecte WAV.
5. Fresh dim na P1; communiceer hard in README.

---

## 6. Owner-beslissingen (antwoord = GO)

Bevestig of corrigeer:

1. **Akkoord** met dit psyche-DoD + waves P1→P5 (geen Configuratie-theater)?
2. **Breaking layout v2** OK (verse Labyrinth-dimensie na P1)?
3. **Audio placeholders** OK tot je eigen SFX drop’t?
4. Suites mogen **meerdere chunks** beslaan?
5. Start bij GO met **P1 full** (niet de halfslachtige 16-only shortcut)?

**Aanbevolen antwoorden voor maximale Revival-psyche:** 1 ja · 2 ja · 3 ja · 4 ja · 5 ja (full P1).

---

## 7. Één zin

We bouwen geen showy kubus-theater; we bouwen **ruimte, geluid, huis-bleed, god-presence en jacht** tot het Labyrint psyche aanvalt — en we stoppen pas als de release-DoD-lijst volledig groen is.
