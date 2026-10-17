# Het Labyrint → Revival-achtig — Wave Plan

Doel: Het bestaande Millénaire-Labyrint (`millenaire:labyrinth`) dichter brengen bij de *ervaring* van Hellraiser: Revival (koude liminale hellscape, kubus als wereldmanipulator, surreal puzzels, jacht/psychologische druk), **zonder Hellraiser-IP** (geen Pinhead/Leviathan/Chatterer/Genesis Configuration-namen; eigen lore: Configuratie, Gebondenen, Architect, Orde).

Huidige baseline (PR #18): deterministisch 2D-cel-doolhof, crimson fog, Configuratie = alleen enter/leave, Gebondenen gastvrij tot geprovoceerd, Architect-fight met beperkte Core-shifts.

## Ontwerpprincipes (vast)

1. **Mood > 1:1 kopie** — Minecraft-blokken, Millénaire-economie/quests, eigen namen.
2. **Configuratie groeit mee** — van portaal → sleutelvormen → corridor/walkway-manipulatie → beperkte combat-hulp.
3. **Orde blijft ceremonieel** — frenemy-kern behouden; horror komt uit ruimte, jacht-escalatie en puzzels, niet uit “alles agro op zicht”.
4. **Deterministisch & tick-vriendelijk** — maze seed-stabiel; shifting/puzzels budgeted; geen per-tick massale rebuilds.
5. **Elke wave is speelbaar** — mergeable, config-gated waar riskant, lokale smoke-checklist bijgewerkt.
6. **IP-grens** — visuele/structurele *motieven* (kettingen, koude blauwe liminaliteit, draaiende gangen, looping rooms) OK; franchise-namen/assets niet.

## Gap-analyse (nu → Revival-gevoel)

| As | Nu | Doel |
|----|----|------|
| Palette / fog | Crimson/slate | Koud blauw-grijs, strakke close fog |
| Geometrie | Één vloerband (Y64–73), 8×8 cellen | Multi-niveau hubs, smallere gangen, stekel/chain densiteit |
| Configuratie | Enter/leave | Modes/vormen als sleutel + rotatie/brug |
| Puzzels | Nauwelijks | Walkway-wiel, standbeeld-poorten, looping kamer |
| Jacht | Attitude JACHT na hit/kill | Corridor-jager (Rammelaar) met “verkeerde gang = teleport-blokkade”-logica |
| Shifting | Alleen Core fase 3 | Puzzle-kamers + optioneel regio-schuiven |
| Narratief | fallenking_11 | Uitbreiding: Kern → Contract → Ontsnapping |

---

## Wave 0 — Freeze & meetlat *(docs only, deze PR)*

**Doel:** Plan + acceptatiecriteria vastzetten vóór code.

**Deliverables**
- Dit document
- Korte update in `LABYRINTH_ARCHITECTURE.md` met link naar waves
- Geen gameplay-wijzigingen

**Done when:** Owner akkoord op volgorde/scope van Wave 1–5 (of feedback → herzien).

---

## Wave 1 — Atmosfeer & geometrie (“koude liminaliteit”)

**Doel:** Eerste viewport/gevoel van Revival: koud, dichtbij, stekelig, niet “Nether-rood”.

### Stappen
1. **Fog/palette**
   - `LabyrinthDimensionEffects`: fog → koud blauw-grijs (niet crimson)
   - Biome `sky_color` / `fog_color` / water-kleuren alignen
   - Ambient: minder ash/warm; meer soul + sparse drip / chain-klank
2. **Corridor densiteit**
   - Walkable interior versmallen (rand-kolommen solid of bars) zodat 8×8-cellen smaller aanvoelen
   - Meer hanging chains, pointed dripstone als “spikes”, iron bars op vloer-/plafondaccenten
3. **Verticale hint (nog geen full Escher)**
   - Op hub-rooms (`HOST_HALL`, `GATE`, `CHAPEL`): mezzanine of schacht (ladder/chain-climb) 1 niveau omhoog
   - `CEILING_Y` lokaal ophogen in die room-stamps; elders ongewijzigd houden voor performance
4. **Config**
   - Optioneel `labyrinthAtmosphere=classic|cold` (default `cold`) zodat rollback makkelijk is
5. **Docs/smoke**
   - Validation: fog kleur, corridorbreedte, hub verticaliteit

**Niet in Wave 1:** Configuratie-puzzels, looping rooms, chase AI, combat powers.

**Risico’s:** Bestaande werelden regenereren chunks niet → note “nieuwe Labyrinth-dimensie / `/millenaire labyrinth` op verse world” in validation.

**Done when:** Creative enter → Arrival voelt koud/claustrofobisch; hubs hebben verticale leesbaarheid; `./gradlew compileJava` groen.

---

## Wave 2 — Configuratie als wereldgereedschap (puzzel-kern)

**Doel:** Kubus doet wat Revival’s box doet *structureel*: omgeving verdraaien en poorten openen — binnen Millénaire-namen.

### Stappen
1. **Configuratie-state**
   - Item component / NBT: `vorm` enum (bijv. `SCHARNIER`, `WIEL`, `SLEUTEL_A/B/C`)
   - Sneak+use of GUI-cyclus wisselt vorm (cooldown behouden)
2. **Draaikamer (`ROTARY` room type)**
   - Stamp: kruisvormige walkways boven “spike”-vloer (dripstone/bars)
   - Gebruik Configuratie in `WIEL`-vorm → roteert walkway-segmenten 90° (scripted block swaps, budgeted, seed-deterministisch eindstaat)
3. **Standbeeld-poorten (`GATE` upgrade)**
   - Drie poorten; elke poort eist (a) ontbrekend “offer”-item (eigen items: tong/hand → hernoemen naar Millénaire-reliquieën, bijv. *zegel*, *masker*, *koord*) + (b) juiste Configuratie-vorm
4. **Quest hooks**
   - `fallenking_11` uitbreiden of `fallenking_12`: “bereik Kern via poorten”
5. **Anti-cheat / sync**
   - Server-side only rotaties; client particles/sound feedback

**Niet in Wave 2:** Full combat telekinesis; looping P.T.-rooms; regio-wide maze regen.

**Done when:** Speler kan zonder Creative-vlucht een Rotary + Gate-route oplossen naar dichter bij Core; docs + smoke checklist.

---

## Wave 3 — Psychologische druk (loops & jacht)

**Doel:** Revival’s “leer of sterf”-gevoel: verkeerde aannames straffen; jacht die navigatie forceert.

### Stappen
1. **Loopkamer (`ECHO_LOOP`)**
   - Kamer die eruitziet als Arrival/huis-achtig Millénaire-interieur
   - Vooruit lopen teleport/scrollt naar “zelfde” kamer met subtle verschillen; **teruglopen** breekt de loop
   - Gids/Archivaris kan cryptische hint geven
2. **Rammelaar-corridorjacht**
   - Als Orde ≥ GEALARMEERD of nabij Gate: Rammelaar “knippert” naar gang vóór speler (niet door muren)
   - Escape-regel: één open zijgang waar hij *niet* verschijnt (deterministisch per cell+seed) — leesbaar na 1–2 fails
3. **Audio bed**
   - Dichter ambient “chatter”-achtig (eigen geluid: rapid bone/chain clicks), volume schaalt met nabije JACHT
4. **Config**
   - `labyrinthChaseEnabled`, `labyrinthLoopRoomsEnabled`

**Niet in Wave 3:** Genesis combat kit; Escher gravity flips.

**Done when:** Loop + chase smokeable; frenemy-pad blijft bestaan als Orde GASTVRIJ.

---

## Wave 4 — Onmogelijke architectuur & diepere shifting

**Doel:** Escher-achtige leesbaarheid zonder echte non-Euclidische engine.

### Stappen
1. **Meerlaagse regio’s**
   - 2–3 floor bands verbonden via schachten/teleport-“vallei”-scharnieren
   - Hub-spacing behouden; Core op diepste band
2. **Perspective tricks**
   - Stair-loop stamps (visueel doorlopend, functioneel éénrichting via barrier/portal)
   - Spiegelgangen (`SPIEGELGAST` utility koppelt aan echte shortcut)
3. **Shifting buiten boss**
   - Periodieke maar zeldzame corridor-seals nabij Gate-tiers (budget + player proximity)
   - Hergebruik/uitbreiding `LabyrinthShifter`
4. **Configuratie traversal powers (licht)**
   - Korte “ketting-trek” (pull naar chain-anker) of “schuif blok” in puzzle-kamers only — geen global grief

**Done when:** Multi-floor route van Arrival → Core speelbaar; shifter niet tick-explosief (budget metrics in command status).

---

## Wave 5 — Polish, combat-hulp, narratief, art

**Doel:** Afwerking naar “voelt als een plek”, niet alleen systemen.

### Stappen
1. **Configuratie combat-ondersteuning (labyrinth-only)**
   - Beperkt: stun-pulse / ketting-root op JACHT-doelen, fueled door “orde-schuld” of cooldown — geen ammo-shooter
2. **Art pass**
   - Gebondene accessory-parts (masker/haken/lantaarn) zoals al genoteerd in PR #18
   - Palette-consistent textures (koud metaal, geen paarse glow)
3. **Quest/lore**
   - Volledige fallenking-tak: deur → Configuratie → poorten → Architect-contract → terugkeer
   - NL/EN quest strings
4. **Validation pack**
   - Uitgebreide `LABYRINTH_LOCAL_VALIDATION.md` per wave
   - Optioneel: `/millenaire labyrinth smoke` dat markers plaatst (rotary/gate/loop)

**Done when:** Owner smoke op verse world door hele keten; compile groen; architecture docs synchroon.

---

## Volgorde & werkbeurten

| Beurt | Wave | Geschatte invasiviteit |
|-------|------|-------------------------|
| 1 (na akkoord) | Wave 1 | Generator + client fog/ambient + room stamps |
| 2 | Wave 2 | Item state + nieuwe room types + quest |
| 3 | Wave 3 | Teleport-loop + chase AI + audio |
| 4 | Wave 4 | Multi-floor gen + shifter uitbreiding |
| 5 | Wave 5 | Art/combat/quest polish |

Na elke wave: commit → push → PR-body updaten met “Wave N done / Wave N+1 next” → stoppen voor owner-review tenzij je zegt “ga door”.

## Expliciet buiten scope (tenzij later gevraagd)

- Letterlijke Hellraiser-IP of assets
- Full custom Blockbench gore meshes
- AAA shader/deferred pipeline
- Vervangen van frenemy-Orde door pure combat-cenobites
- Automatische GameTest/CI-claims (repo-policy: owner lokaal)

## Acceptatievraag aan owner

1. Akkoord met Wave 1→5 volgorde?
2. Default atmosfeer meteen `cold` (breaking look) of config-toggle met default `cold`?
3. Poort-reliquieën: nieuwe items OK, of hergebruik bestaande Millénaire-items?
4. Mag Wave 3 chase de GASTVRIJ-ervaring onder druk zetten nabij Gates, of alleen na Orde-escalatie?
