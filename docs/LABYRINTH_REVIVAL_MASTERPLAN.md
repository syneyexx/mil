# Het Labyrint — Eerlijke vibe-audit & masterplan (na Wave 8)

## Verdict

**Nee — nog geen topniveau Hellraiser: Revival-gevoel.**

Wat er staat is een **sterke Minecraft-systemenfundament** met de *juiste motieven* (koude liminaliteit, kubus, jacht, ingerichte kamers, geometrische god). Het *voelt* nog vooral als: doolhof van blackstone + gestempelde kamers + fog. Revival voelt als: **surreal horror-ruimte die je psyche aanvalt**, met productie, audio, schaal en puzzels die niet “Minecraft” lezen.

Dit document is de eerlijke gap-analyse en het plan om daar wél naartoe te werken — zonder Hellraiser-IP.

---

## Wat Revival écht doet (meetlat)

| As | Revival | Millénaire nu (Wave 8) | Gap |
|----|---------|------------------------|-----|
| **Schaal & architectuur** | Onmogelijke, hoge naves, Escher-bruggen, corridors die draaien | 8×8 cellen, één hoofdband + mezz/vault | **Kritiek** — te klein, te regelmatig |
| **Liminale “echte” kamers** | Volle huizen, looping hallways, detail tot foto’s scheef | 8×8 spruce kamertjes met 8–12 props | **Hoog** — te krap, te spaarzaam |
| **De god / Audience** | Leviathan als overweldigende aanwezigheid | De Ruit = ~7 blokken octaëder | **Hoog** — te klein, geen awe |
| **Configuratie** | Visuele morph, wereldmanipulatie, combat kit | Vorm-enum + block swaps | **Hoog** — functioneel, weinig theater |
| **Jacht** | Chatterer teleports, audio overal, paniek | Rammelaar blink + skeleton ambient | **Medium-hoog** |
| **Audio** | Dedicated hellscape bed, chatter, metal | Cave + chain fall + note | **Kritiek** |
| **Creatures** | Unieke cenobite sculpturen | Humanoid + straps/mask cubes + PNG skins | **Hoog** |
| **Lighting / fog** | Directed cold blue, god-rays, haze layers | Flat fog planes 18–22 | **Medium** |
| **Pacing** | Real world ↔ Labyrinth bleed, story beats | Maze wander + hubs | **Hoog** |
| **Wrongness** | Subtiel → escalerend | Props (ketting op bed) | **Medium** — goed begin, te predictabel |

### Wat wél al klopt (niet weggooien)

- Motieven & lore-scheidingslijn (Configuratie, Gebondenen, De Ruit, Orde)
- Frenemy→jacht escalatie (ceremonieel, niet pure combat-hell)
- Puzzle foundations (WIEL, poorten, echo-loop, persist)
- Liminale kamertypes als *categorie*
- Config-gates en documentatie

---

## Ontwerpdoelen voor “echte vibes”

1. **Eerste 30 seconden** moeten aanvoelen alsof je in een andere logica bent beland — niet “Nether met fog”.
2. **Ingerichte zones** moeten *ruimtes* zijn (meerdere cellen / 16–24 blok breed), niet doodlopende props-dozen.
3. **De Ruit** moet *awe* geven: schaal, licht, stilte, dan stem — niet een hangende diamant van ijsblokken.
4. **Audio is 40% van de vibe.** Zonder custom bed blijft het Minecraft.
5. **Configuratie-theater** — morph particles, camera punch, wereld die hoorbaar kraakt.
6. **Onvoorspelbare wrongness** — niet elke slaapkamer dezelfde ketting-op-kussen formula.
7. Blijf **IP-schoon** (geen Pinhead/Leviathan/Chatterer-namen).

---

## Masterplan — Wave 9 → 14

Elke wave is mergebaar, speelbaar, en meetbaar met smoke-criteria. Prioriteit: **schaal → audio → liminal suites → Ruit → Configuratie-theater → creature art**.

### Wave 9 — Ruimtelijke herstructurering (“stop thinking in 8×8”)

**Doel:** Het Labyrint leest als architectuur, niet als dungeon-grid.

| Stap | Werk |
|------|------|
| 9.1 | Introduceer **chamber sizes**: `CORRIDOR` (8), `ROOM` (16), `SUITE` (24), `NAVE` (32) — maze nodes met footprint, niet 1 cell = 1 room |
| 9.2 | **Corridor vocabulary**: smalle 3-breed gangen, T-junctions, galerijen met rails/kettingen, stekelputten onder roosters |
| 9.3 | **Vertical drama**: 2–3 verdiepingen zichtbaar tegelijk (atrium shafts met overlooks), niet alleen ladder-schachten |
| 9.4 | Seed-stabiele **region “acts”**: Arrival belt → Domestic bleed belt → Industrial Orde → Approach → Core/Audiëntie |
| 9.5 | Performance: stamp per chamber chunk-budget; lazy stamp interiors when player near |

**Done when:** Creative fly-through toont duidelijk verschillende zones; corridors voelen langer/claustrofobischer dan nu.

**Risico:** Grootste technische wave — breaking chunk layout. Fresh dim verplicht; version `labyrinthLayoutVersion=2` in SavedData.

---

### Wave 10 — Audio & presence bed (“40% van de vibe”)

**Doel:** Je *hoort* het Labyrint voordat je het ziet.

| Stap | Werk |
|------|------|
| 10.1 | Custom sound events (of hergepackte vanilla layered): `labyrinth.drone_cold`, `labyrinth.metal_stress`, `labyrinth.whisper_bed`, `labyrinth.ruit_pulse`, `labyrinth.chase_clicks` |
| 10.2 | Zone-based music/ambiance crossfade (Arrival / Liminal / Orde / Audience) |
| 10.3 | Chase audio: clicks volume ∝ afstand tot Rammelaar; paniek-stingers bij blink |
| 10.4 | Configuratie use: morph whoosh + world crack per vorm |
| 10.5 | De Ruit: heartbeat/drone dat luider wordt dichterbij (niet Warden-copy-pasta) |

**Done when:** Headphones-run zonder UI voelt “wrong” binnen 10s in liminal + “awe” bij De Ruit.

**Asset note:** Owner mag later echte SFX droppen; agent levert events + layered vanilla placeholders + volume curves.

---

### Wave 11 — Liminal suites (écht mind-fuck)

**Doel:** Ingerichte zones die Revival’s huis/bleed benaderen.

| Stap | Werk |
|------|------|
| 11.1 | **Suite stamps** spanning 2×2 of 3×2 cells: appartement, lange gang met deuren, keuken+eetkamer |
| 11.2 | **Detail density**: 30–60 props/suite; scheve frames (item frames), deuren die nergens heen gaan, dubbele bedden, klokken die ticksen |
| 11.3 | **Wrongness layers** (seed-driven intensity 0–3): cosmetisch → onmogelijk (trap die in plafond eindigt) → hostile bleed (spikes onder tapijt, Orde-bars door muur) |
| 11.4 | **Soft loops** in suite hallways (niet alleen ECHO_LOOP hubs): 1–2 corridor loops met escape-by-reverse |
| 11.5 | **Photo/memory props**: lectern books with short lore pages; “family” portraits that change on revisit |
| 11.6 | Enter/exit: light level snap, temporary warmer fog then cold snap-back |

**Done when:** Speler zegt “dit voelt als een huis” ≥3 seconden voordat wrongness klikt.

---

### Wave 12 — De Ruit 2.0 (awe, geen ijs-diamantje)

**Doel:** Audience chamber als climax-ruimte.

| Stap | Werk |
|------|------|
| 12.1 | **Nave scale**: 32×32 footprint of multi-cell nave; ceiling ≥ Y90 equivalent; processie van 20+ blokken |
| 12.2 | De Ruit als **grote lozenge** (15–21 blok span) + particle shell + beam naar dais |
| 12.3 | **Ritual states**: distant hum → approach silence → first audience (bestaande tag) → optional Architect pact dialogue |
| 12.4 | Configuratie in Audience: auto-hint volgende vorm; of “Ruit weigert” als Orde hostile |
| 12.5 | Side chapels met silent Gebondenen in pews (GASTVRIJ, kijken, niet praten) |

**Done when:** Screenshot van De Ruit is onmiddellijk herkenbaar als “dit is de god van deze plek”.

---

### Wave 13 — Configuratie-theater & surreal puzzels

**Doel:** Kubus voelt als Revival’s Genesis Configuration.

| Stap | Werk |
|------|------|
| 13.1 | Client: vorm-cycle animatie (particles + foil color + screen vignette per vorm) |
| 13.2 | **Multi-chamber rotary**: 16×16 spike hall with 3 walkway rings |
| 13.3 | **Statue gates 2.0**: full-height sealed doors + pedestal ritual (item + vorm + short delay) |
| 13.4 | **Perspective room**: stairs that teleport to mirrored mezzanine (Escher-lite) |
| 13.5 | KETTING: visible chain projectiles / pull-to-anchor in puzzle rooms |

**Done when:** Elke Configuratie-vorm heeft unieke feedback; minstens 3 puzzle types voelen distinct.

---

### Wave 14 — Creature & material art pass

**Doel:** Gebondenen en materialen lezen niet meer als “zombie met straps”.

| Stap | Werk |
|------|------|
| 14.1 | Per-tier model variants (Leader / Gate / Social) met distinct silhouettes |
| 14.2 | Custom blockset `orde_stone` / `orde_panel` / `bleed_planks` (textures) — stopt blackstone-monotonie |
| 14.3 | Rammelaar: jaw/click animation + louder chase silhouette |
| 14.4 | Architect: taller model, measuring staff, phase VFX polish |
| 14.5 | Optional: resource-pack friendly texture slots documented for owner artists |

**Done when:** Screenshot van 3 Gebondenen + liminal suite + De Ruit haalt “mod showcase” bar.

---

## Wat we bewust níet beloven

- Letterlijke Hellraiser-assets of namen
- Unreal-level photorealism in vanilla MC pipeline
- Volledige custom soundtrack zonder owner audio files (placeholders eerst)
- Vervangen van frenemy-Orde door pure slashers (verliest Millénaire-ziel)

## Aanbevolen volgorde (strikt)

`9 → 10 → 11 → 12 → 13 → 14`

Reden: schaal zonder audio voelt nog steeds plat; audio zonder schaal klinkt als cave; liminal suites zonder grotere footprints blijven dozen; De Ruit zonder nave blijft klein; theater/art last.

## Effort / invasiviteit (technisch, geen kalender)

| Wave | Invasiviteit | Afhankelijkheden |
|------|--------------|------------------|
| 9 | **Zeer hoog** — generator rewrite | Breaking layout version |
| 10 | Medium — sounds + client handlers | Assets optional |
| 11 | Hoog — stamps + suite graph | Wave 9 idealiter eerst |
| 12 | Hoog — special structure | Wave 9 nave sizes |
| 13 | Medium-hoog | Puzzels op nieuwe footprints |
| 14 | Medium — art/models | Kan parallel na 11 |

**Pragmatische shortcut:** Als Wave 9 te groot is voor één beurt: doe **9.1–9.3 minimal** (ROOM=16 footprints voor liminal + Audience only) daarna 10+11, full maze rewrite later.

---

## Acceptatievragen aan owner

1. Akkoord met eerlijk verdict + Wave 9→14 volgorde?
2. Mag Wave 9 **breaking** zijn (`labyrinthLayoutVersion=2`, verse dim)?
3. Audio: placeholders OK, of wacht je op eigen SFX?
4. Mag liminal suites **meerdere chunks** beslaan (duurder stampen)?
5. Start met **full Wave 9** of met **pragmatic 16-block liminal/Audience only**?

---

## Samenvatting in één zin

We hebben het **skelet van Revival**; voor het **vlees** moeten schaal, audio, suite-dichtheid en De Ruit-awe omhoog — dat is een tweede campagne (Waves 9–14), geen polish-pass.
