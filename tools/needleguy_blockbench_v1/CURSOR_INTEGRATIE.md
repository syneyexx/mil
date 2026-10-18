# Cursor: integreer Needleguy

Lees de repository-instructies, bestaande model-loader, renderer en animatiecode. Voeg deze entity-asset toe onder de naam `Needleguy` en resource-ID `needleguy`. Behoud de eerdere 21 mobs, vier villagers en De Doolhofsoeverein.

1. Gebruik het bijgeleverde `.bbmodel` als bron. Het is Generic Model, projectversie 4.10, met geneste outliner-groepen, absolute pivots, lokale Z-cuberotaties en face-UV's. Onderzoek het bestaande `models/entity/monster/*.model.json`-schema en de daadwerkelijke loader. Gebruik of maak een passende conversie. Hernoemen van de extensie is geen export.
2. Behoud de totale hoogte van **40 units = 2,5 blocks inclusief pinnen**. Voeten staan op y=0; Y is omhoog; voorzijde kijkt naar -Z. Bereken na export de volledige geometrische grenzen en controleer dat er geen extra schaalfactor is toegepast.
3. De 256×256 PNG is zowel los als ingebed beschikbaar. Het hoofd gebruikt specifieke UV-rechthoeken in de onderste atlasstrook; kleding gebruikt gedeelde materiaalregio's. Gebruik de expliciete face-UV's, niet automatisch gegenereerde box-UV's. Controleer de gridrichting van de littekens op alle hoofdzijden.
4. De pinnen zijn kleine maar echte cubes. Houd ze gekoppeld aan de hoofdgroep. Verwijder ze niet stilzwijgend bij een minimum-cube-size-optimalisatie. Controleer aliasing op de uiteindelijke texturefilter-/renderinstellingen; maak eventuele LOD's als aparte assets en behoud de bron.
5. De puzzelkubus volgt de rechterhand. De gesplitste robe-panelen volgen de benen. Controleer bij lopen en aanvallen op clipping. De bron bevat geen clips; gebruik de bestaande animatievoorziening en meld duidelijk welke animaties je toevoegt.
6. Koppel naam, texture en model via de bestaande registratieconventies. Dit modelpakket bevat geen AI, kettingaanvallen, geluid, loot of spawnregels. Verzin zulke systemen niet als reeds bestaande of meegeleverde functies.
7. Valideer bouw, laden, UV's, pinnen, grondcontact, schaal naast blocks en arm-/gewaadanimatie in de echte renderer. Scheid de visuele bounding box van collision- en ooghoogtekeuzes. Meld eerlijk welke in-game tests niet zijn uitgevoerd.
