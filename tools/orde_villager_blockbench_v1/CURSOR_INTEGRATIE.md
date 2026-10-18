# Cursor-opdracht: vier bewoners van labyrintdorpen

Integreer de vier visuele NPC-assets uit dit pakket in de bestaande mod. Lees eerst de repository-instructies en bestaande entity-, model-, renderer-, animatie- en settlementcode. Hergebruik bestaande voorzieningen. Behoud de vorige 21 mobs.

## Modellen

- `drempelhouder`: De Drempelhouder — dorpsoudste, 2,15 blocks.
- `ruilmeester`: De Ruilmeester — handelaar, 2,10 blocks.
- `slotenmaker`: De Slotenmaker — ambachtsman, 2,20 blocks.
- `aswaard`: De Aswaard — herbergier, 2,10 blocks.

De `.bbmodel`-bronprojecten zijn Generic Model, projectversie 4.10. Ze bevatten geneste groepen, absolute pivots, cubes met eigen Z-rotaties en face-UV's voor één texture van 256×256. De texture is zowel los als ingebed beschikbaar. De bron kijkt naar -Z; y=0 is de grond en 16 units is één block.

1. Onderzoek het bestaande `models/entity/monster/*.model.json`-schema of het equivalent voor NPC's. Controleer ook de daadwerkelijke loader. Gebruik een bestaande betrouwbare exporter als die er is; pas anders de eerdere conversie gericht aan. Behoud alle pivots, parents, lokale cube-rotaties, UV-richtingen en de schaal. Hernoem geen bbmodel naar model.json als schijnconversie.
2. Registreer de vier modellen en textures onder de bestaande namespace en conventies. Zorg dat accessoires aan hun hand, torso of hoofd blijven hangen. De gesplitste gewaden hebben aparte groepen onder de benen. Lege groepen mogen bestaan. Schorten vereisen animatiecontrole op clipping.
3. Koppel de modellen aan een bestaande burger-/villagerbasis als die beschikbaar is. De herkenbare beroepen zijn visuele ontwerpen; de bronbestanden implementeren zelf geen handel, AI, dialoog of dorp. Gebruik geen standaard vijandige monster-AI uitsluitend omdat het model in een monster-resourcepad terechtkomt. Laat bestaand factie-/reputatiegedrag leidend zijn.
4. Gebruik bestaande idle-/loopanimaties of maak passende clips binnen de aanwezige animatievoorziening. Neem niet aan dat deze assets clips bevatten. Controleer grondcontact, handen met voorwerpen, schortclipping en de rugzak. Gebruik geen lantaarntexture als bewijs dat een dynamische lichtbron al bestaat.
5. Als de mod al labyrintdorpen en beroepstoewijzing heeft, maak deze vier typen beschikbaar in dat systeem en koppel werk-/verblijfspunten via de bestaande API. Behoud opgeslagen entity-ID's en backwards compatibility. Voeg geen tweede concurrerend settlement- of worldgensysteem toe. Als een dorpssysteem ontbreekt, meld expliciet welke aparte implementatie nodig is; alleen het toevoegen van deze modellen laat geen dorpen ontstaan.
6. Hitboxes staan los van de visuele grenzen inclusief staf en rugzak. Controleer gangbreedte, deurhoogte en pathfinding op de werkelijke mobmaten. Houd rekening met de hoogte van 2,10–2,20 blocks: een doorgang van exact twee blocks kan te laag zijn, afhankelijk van de ingestelde collisionhoogte. Verlaag niet stilzwijgend de modelmaat om dit te maskeren.
7. Doe build-/loadchecks en controleer alle vier visueel in de echte renderer: voor-, zij- en achteraanzicht, UV's, schaal naast blocks, grondcontact en loopanimatie. Controleer handels- en burgergedrag alleen als dat daadwerkelijk gekoppeld is. Meet de renderkosten bij het beoogde aantal gelijktijdig zichtbare inwoners.

Lever concrete wijzigingen en de werkelijk uitgevoerde controles. Benoem resterende beperkingen, vooral wanneer een in-game test of settlementkoppeling nog ontbreekt. Claim geen compleet werkend dorp op basis van alleen modelbestanden.
