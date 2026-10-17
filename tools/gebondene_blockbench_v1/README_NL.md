# Gebondenen — 21 Minecraft entity assets · v1.0

Dit pakket bevat echte, bewerkbare cube-geometrie met toegewezen pixelart-textures. Het zijn gestileerde interpretaties van de vijf aangeleverde conceptbladen, geen exacte 3D-reconstructies van de geschilderde afbeeldingen. De achterzijde is ontworpen omdat die in de referenties grotendeels ontbreekt.

## Inhoud

- `mobs/<naam>/<naam>.bbmodel`: 21 Blockbench-projecten, Generic Model (`free`), projectformaat 4.10.
- `mobs/<naam>/<naam>.png`: 21 individuele 256×256 RGBA-textures. Elke PNG is ook ingebed in het bijbehorende project, zodat het zelfstandig te openen is.
- `PREVIEW_ALL.png`: overzicht, gerenderd uit de daadwerkelijk geleverde geometrie en textures.
- `previews/`: vooraanzicht en driekwartaanzicht per model (42 renders).
- `manifest.json`: bestandsnamen, aantallen, gekozen hoogtes en exacte geometrische bounding boxes.
- `CURSOR_INTEGRATIE.md`: concrete opdracht voor integratie in jouw repository.
- `VALIDATION.json`: uitgevoerde structurele controles en beperkingen.
- `tools/`: reproduceerbare generator en validator; Python 3, Pillow en NumPy nodig. Gebruik `python tools/build_pack.py` en daarna `python tools/validate_pack.py`. Regeneratie overschrijft de geleverde assets: bewaar handmatige wijzigingen eerst elders.

## Openen

Pak de ZIP uit en open een `.bbmodel` via File → Open in Blockbench. De texture zit ingebed. De losse PNG staat naast het project. Bewaar de structuur als je later naar externe textures overschakelt. Deze bestanden zijn geen Java block/item JSON en geen kant-en-klare mod.

De projectstructuur is gebaseerd op de officiële Blockbench-documentatie en project-codec: https://www.blockbench.net/wiki/docs/bbmodel/ en https://github.com/JannisX11/blockbench/blob/master/js/formats/bbmodel.js . Versie 4.10 gebruikt geneste groepen in `outliner`; nieuwere Blockbench-versies hebben een conversiepad voor oudere projecten. De bestanden zijn hier structureel gecontroleerd, maar niet in de Blockbench-app geopend.

## Schaal en rig

16 model-units = 1 Minecraft-block. Y wijst omhoog; de voorzijde kijkt naar -Z; voeten en laagste geometrie liggen op y=0. Links/rechts is vanuit de mob: rechts ligt op -X. Alle maten in de bbmodel zijn model-units.

De outliner heeft `root`, `body`, `head`, arm-, onderarm-, hand-, been- en scheenbeengroepen met pivots. Accessoires hangen aan een passende groep. De Danseres staat met uitgestrekte armen; de Stiltewachter heeft een opgeheven linkeronderarm. Deze poses zijn in de geometrie vastgelegd. Groepsrotaties beginnen op nul.

Er zijn **geen animatieclips** inbegrepen. De groepen bieden een basis voor animatie; dynamische kettingen, stof en touw zijn niet gesimuleerd. De lichaamsmaten zijn geen automatisch ingestelde Minecraft-hitboxes.

## Hoogtes

| Model | Gekozen lichaams-/referentiehoogte (blocks) |
|---|---:|
| Gids | 2.30 |
| Kettingzuster | 2.30 |
| Spiegelgast | 2.40 |
| Poortwachter | 2.50 |
| Fluitende | 2.50 |
| Naaister | 2.50 |
| Tweeling | 1.80 inclusief juk; ieder lichaam 1.60 |
| Echo | 2.30 |
| Gastheer | 2.40 |
| Notaris | 2.40 |
| Blinde | 2.40 |
| Smid | 2.40 |
| Archivaris | 2.40 |
| Schatter | 2.50 |
| Danseres | 2.50 |
| Klokkenluider | 2.75 |
| Marionet | 2.50 exclusief controleringen |
| Pelgrim | 2.30 |
| Stiltewachter | 2.30 |
| Sleuteldrager | circa 2.30 |
| Getuige | circa 2.30 |

Verschillende schaalmarkeringen op de conceptbladen zijn inconsistent (onder meer Echo en Gebondenen). Bovenstaande maten zijn expliciete ontwerpkeuzes; de gegeven 2.5/2.75-maatlabels zijn waar aanwezig gevolgd. Uitstekende accessoires kunnen de totale hoogte vergroten: zie `total_height_blocks` en `bounds_units` in het manifest. Alle totale hoogtes blijven onder 3 blocks.

## Textures en prestaties

De textures zijn doelbewust herbruikbare materiaal-atlassen met UV-toewijzing per vlak. Verschillende vlakken delen materiaalregio's; ze hebben niet allemaal unieke UV-eilanden. De 4×4 atlas bevat 16 vakken van 64×64: leer, huid, bot, ijzer; messing, stof, perkament, hout; spiegel, donker, bloedkleur, amber; gezicht, gehechte stof, paars, geest. Het generatorbestand bevat de precieze indeling.

De modellen hebben uiteenlopende aantallen cubes. Kettingen bestaan uit geometrische schakels en verhogen het aantal vlakken. Er is geen LOD-systeem of renderbenchmark inbegrepen. Meet prestaties met de werkelijke entity-renderer en beoogde aantallen mobs voordat je massale spawns inzet.

De Spiegelgast gebruikt een geschilderde spiegeltextuur, geen realtime reflecties. De lantaarn heeft een ambertextuur, geen automatisch emissive materiaal of lichtbron. Echo is opaak geleverd: transparantie en een eventuele after-image horen bij de renderer en zijn niet ingebouwd. Opaque PNG's voorkomen dat het pakket een onbekend blendbeleid van jouw mod veronderstelt.

## Integratiegrens

Een voorbeeld van `models/entity/monster/*.model.json` en de bijbehorende loader waren niet beschikbaar. Daarom bevat dit pakket `.bbmodel` als het door jou toegestane bronformaat. Er is geen onbevestigde custom JSON-export toegevoegd. Hernoemen van `.bbmodel` naar `.model.json` werkt niet als conversie. Laat Cursor het bestaande schema en de renderer lezen en een passende exporter/adapter maken. Zie de integratieopdracht.
