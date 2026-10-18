# Needleguy — Pinhead-model voor Minecraft

Een blokvormige interpretatie van Pinhead, op verzoek geleverd onder de modelnaam en resource-ID **Needleguy / `needleguy`**. Bleek hoofd met littekenraster, metalen pinnen, zwarte leren gewaden, rode borstpanelen en een gouden puzzelkubus.

## Inhoud

- `mobs/needleguy/needleguy.bbmodel`: bewerkbaar Blockbench-project, Generic Model (`free`), projectversie 4.10.
- `mobs/needleguy/needleguy.png`: 256×256 RGBA-texture, ook ingebed in het project.
- `PREVIEW_NEEDLEGUY.png`: overzicht met een detail van het hoofd.
- `previews/`: daadwerkelijke modelrenders van voren, achteren en in driekwartaanzicht, plus de front-detailbron.
- `manifest.json`: maatvoering, resourcepaden, bounding box en aantallen.
- `VALIDATION.json`: uitgevoerde controles en beperkingen.
- `CURSOR_INTEGRATIE.md`: integratieopdracht.
- `tools/`: generator en validator.

## Maatvoering en bewerking

**Totale hoogte: 2,5 blocks = 40 units, inclusief de hoogste pinnen. Voeten op y=0.** 16 units = één block; Y wijst omhoog, voorzijde is -Z. De renderer moet de bron niet nogmaals met 2,5 vermenigvuldigen.

Het model bestaat uit 224 cubes. Hoofd, pinnen, armen, onderarmen, handen, benen, schenen, gesplitste gewaadpanelen, gereedschap en puzzelkubus hebben afzonderlijke groepen en passende pivots. De pinnen volgen het hoofd. De puzzelkubus hangt aan de rechterhand; de gewaadpanelen aan de bijbehorende benen. De pose heeft ontspannen armen langs het lichaam.

De hoofdatlas heeft zes eigen UV-rechthoeken in de onderste texturestrook. De rasterlittekens zijn geschilderd; de pinnen zijn echte geometrie met schachten en koppen. Kleding en overige materialen delen atlasregio's. De texture is volledig opaak; er zijn geen bijzondere shaders nodig om het basisontwerp te tonen.

## Status

JSON, identifiers, hiërarchie, cube-afmetingen, face-UV's, texture-afmetingen, ingebedde PNG en geometrische hoogte zijn structureel gecontroleerd. Previews zijn gemaakt uit de werkelijke assets en visueel bekeken.

Niet geopend in de Blockbench-app of getest in Minecraft in deze omgeving. Geen animatieclips, entity-AI, aanvallen, geluid of spawnlogica inbegrepen. De custom `.model.json`-loader van de mod was niet beschikbaar; dit pakket bevat daarom het toegestane `.bbmodel`-bronformaat. Hernoemen is geen conversie.

## Herbouwen

Benodigd: Python 3, Pillow en NumPy. Pas de DejaVuSans-fontlocatie in het bouwscript aan als die niet op jouw systeem bestaat.

```
python tools/build_needleguy.py
python tools/validate_pack.py
```

Herbouwen overschrijft de gegenereerde assets. Bewaar handmatige Blockbench-aanpassingen eerst afzonderlijk.
