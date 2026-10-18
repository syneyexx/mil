# Labyrintbewoners — vier villager-NPC’s · v1.0

Vier originele, blokvormige bewoners met duistere rituele kleding, verweerd leer, messing, ijzeren gespen en gehechte details. Ontworpen als burgers met herkenbare beroepen in het labyrint. De grote neus, zware wenkbrauwen en hoofdverhoudingen maken ze herkenbaar als villager-achtige NPC's. Ze zijn geen kopieën van specifieke Hellraiser-personages.

## De bewoners

| ID | Naam | Beoogde rol | Hoogte | Cubes |
|---|---|---|---:|---:|
| drempelhouder | De Drempelhouder | Dorpsoudste; contracten en toegang tot het dorp | 2,15 blocks | 84 |
| ruilmeester | De Ruilmeester | Handelaar; voorraad, kaarten en ruilhandel | 2,10 blocks | 93 |
| slotenmaker | De Slotenmaker | Ambachtsman; sleutels, sloten en reparaties | 2,20 blocks | 114 |
| aswaard | De Aswaard | Herbergier; onderdak, voedsel en geruchten | 2,10 blocks | 104 |

Dit zijn visuele rollen en suggesties voor de mod; handel, gesprekken, AI en dorpsgeneratie zijn niet geïmplementeerd in deze modelbestanden.

## Inhoud

- Vier bewerkbare `.bbmodel`-projecten in `mobs/<id>/`, elk met een losse **256×256 RGBA PNG**.
- Dezelfde PNG is ook in het bijbehorende project ingebed, zodat een ontbrekend extern pad het beeld niet afhankelijk maakt van een andere computer.
- `PREVIEW_VILLAGERS.png`: overzicht, gerenderd uit de geleverde geometrie en textures.
- `previews/`: vooraanzicht, driekwartaanzicht en achteraanzicht per model.
- `manifest.json`: exacte bestanden, geometrische grenzen, rollen en aantallen.
- `VALIDATION.json`: controleverslag en expliciete testbeperkingen.
- `CURSOR_INTEGRATIE.md`: integratieopdracht voor de bestaande mod.
- `tools/`: deterministische generator en structurele validator.

## Openen en schaal

Pak de ZIP uit en open een `.bbmodel` in Blockbench. Formaat: Generic Model (`free`), projectversie 4.10 met geneste groepen in de outliner. Dit is hetzelfde bronformaat als het vorige pakket met 21 mobs; het is geen custom `monster/*.model.json`-export.

**16 units = 1 block. Voeten staan op y=0. Y wijst omhoog en de voorzijde kijkt naar -Z.** Rechts van de NPC is -X. Groepspivots en cube-coördinaten zijn absoluut in de bronbestanden; een exporter moet ze naar het daadwerkelijke schema van de mod vertalen.

Hoofd, bovenarmen, onderarmen, handen, bovenbenen en schenen hebben afzonderlijke groepen. Accessoires zoals rugzak, staf, gereedschap, boek en lantaarn hebben passende parents. De gesplitste rokpanden van de gewaden hangen aan de bijbehorende benen, zodat ze bij een loopanimatie kunnen meebewegen. De schorten hebben een eigen groep, maar moeten bij het animeren op clipping worden gecontroleerd.

Geen animatieclips inbegrepen. De lantern-texture is amberkleurig maar veroorzaakt geen dynamisch licht. Alle textures zijn opaak. UV's gebruiken gedeelde materiaalregio's; de atlas is geen verzameling volledig unieke UV-eilanden per cube.

## Controles en beperkingen

De vier modellen zijn gecontroleerd op geldige JSON, unieke identifiers, volledige outliner-hiërarchie, positieve cube-afmetingen, zes toegewezen texturevlakken per cube, UV-grenzen, PNG-afmetingen, overeenstemming tussen ingebedde en externe texture en geometrische grenzen na lokale cube-rotaties. Van alle vier zijn renders van de werkelijke assets gemaakt; het overzicht en een achteraanzicht zijn visueel geïnspecteerd.

Niet in de Blockbench-app of in Minecraft geopend in deze omgeving. Er zijn geen claims over bewezen in-game prestaties. Het relatief beperkte aantal cubes is een ontwerpkeuze voor meerdere zichtbare bewoners, geen vervanging voor een benchmark van de daadwerkelijke renderer.

Het custom model-schema van de repository is niet beschikbaar. Laat Cursor de bestaande loader lezen en correct converteren. Hernoemen van `.bbmodel` naar `.model.json` is geen conversie.

## Herbouwen

Benodigd: Python 3, Pillow en NumPy. De previewgenerator gebruikt op Linux standaard DejaVuSans op `/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf`; pas `FONT` aan als die fontlocatie ontbreekt. Voor alleen openen/bewerken in Blockbench is Python niet nodig.

```
python tools/build_villagers.py
python tools/validate_pack.py
```

Regeneratie overschrijft de models, textures en previews. Bewaar handmatige Blockbench-aanpassingen voordat je het bouwscript opnieuw draait.
