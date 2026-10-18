# De Doolhofsoeverein — godfiguur van het labyrint

Een originele, monumentale entiteit voor de wereld van de eerdere 21 mobs en vier labyrintbewoners. Gesloten albasten masker, donkere rituele gewaden, messing labyrint-halo, vier armen en een verzegeld reliek. De twee buitenarmen hangen langs het lichaam; de binnenarmen bieden het reliek naar voren aan.

## Schaal

**De volledige geometrie is exact 9 blocks hoog: y=0 t/m y=144.** De bovenkant van de halo telt mee. 16 model-units = 1 Minecraft-block. Voeten staan op y=0, Y wijst omhoog, de voorzijde kijkt naar -Z. Het lichaam zonder halo is lager dan 9 blocks; 9 blocks is de totale modelhoogte.

De preview toont een gestileerde 2-block referentiefiguur voor schaal. Deze figuur hoort niet bij het bbmodel. De godfiguur hoeft niet opnieuw negenmaal geschaald te worden bij het laden; de brongeometrie is al op de gevraagde maat.

## Bestanden

- `mobs/doolhofsoeverein/doolhofsoeverein.bbmodel`: bewerkbaar Blockbench Generic Model, projectversie 4.10.
- `mobs/doolhofsoeverein/doolhofsoeverein.png`: 256×256 RGBA pixelart-materiaalatlas, tevens ingebed in het project.
- `PREVIEW_GOD.png`: gerenderd overzicht met schaalreferentie.
- `previews/`: voor-, driekwart- en achteraanzicht uit de daadwerkelijke geometrie.
- `manifest.json`: exacte bounding box, aantallen en resourcepaden.
- `VALIDATION.json`: structurele en geometrische controles.
- `CURSOR_INTEGRATIE.md`: opdracht voor aansluiting op de bestaande mod.
- `tools/`: reproduceerbare generator en validator.

Open het bbmodel rechtstreeks in Blockbench. Er zijn 344 cubes, met afzonderlijke groepen voor hoofd, halo, mantel, benen, gewaadpanelen, vier armen met onderarmen en handen, en het reliek. De texture bevat hergebruikte materiaalregio's: niet ieder vlak heeft een uniek UV-eiland. De rasterresolutie is bewust 256×256 in dezelfde pixelart-aanpak als de eerdere assets.

## Rig en beperkingen

Er zijn geen animatieclips, boss-AI, aanvallen, goddelijke krachten, encounter-logica of dorp-/tempelgeneratie inbegrepen. Dit is een modelpakket. Het reliek hangt aan de torso in de rustpose; voor een animatie moeten de binnenhanden en het reliek samen worden aangestuurd, of moet een expliciete attachment-oplossing worden gekozen.

De halo hangt aan de hoofdgroep. De mantel is torso-gebonden; de gesplitste gewaadpanelen volgen de benen. Controleer clipping en de ruimte tussen halo, schouders en hoofd bij het toevoegen van animaties. De twee aangeboden onderarmen gebruiken gecombineerde X- en Z-cuberotaties; de exporter moet de juiste rotatievolgorde behouden en mag geen componenten laten vallen.

Alle materialen zijn opaak. Messing en albast zijn geschilderde textures, geen shader-effecten. De figuur is ontworpen voor een grote ruimte, niet voor normale villager-deuren of gangen. Hitbox, kijkhoogte, renderculling en spawnvoorwaarden moeten in de mod apart worden ingesteld.

Structureel gecontroleerd en met een eigen renderer visueel bekeken. Nog niet geopend in de Blockbench-app of getest in Minecraft. Er is geen onbevestigde custom `.model.json`-export toegevoegd: het bestaande schema was niet beschikbaar.

## Herbouwen

Python 3, Pillow en NumPy nodig. De preview gebruikt DejaVuSans via de Linux-fontlocatie in `tools/build_god.py`; pas die aan voor een ander systeem.

```
python tools/build_god.py
python tools/validate_pack.py
```

Herbouwen overschrijft de gegenereerde bestanden. Bewaar eventuele handmatige modelwijzigingen eerst apart.
