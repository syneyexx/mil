# Hemelwachter — entity-textuuratlas

PNG-atlas op basis van het goedgekeurde monsterconcept.

- Grootte: 1254 × 1254 pixels, RGBA.
- Amberkleurig oog: neutrale toestand.
- Rood oog: geprovoceerde toestand.
- Herbruikbare vlakken voor veren, kern, ringen, gouden randen en runen.
- UV-vlakken staan in hemelwachter_uv_regions.json. Dit is een gids, geen standaard Blockbench-importbestand.
- Stel de modeltextuurgrootte in op 1254 × 1254 en gebruik de aangegeven pixelcoördinaten per modelvlak. Bij een aangepaste textuurgrootte moeten UV-coördinaten mee veranderen.
- Gebruik nearest-neighbor filtering voor de pixelstijl.
- Het monster heeft nog een bijpassend 3D-model en renderer nodig. De atlas is niet gekoppeld aan een bestaande Minecraft-entity.
- De rode oogvariant vereist dat de renderer/modelcode bij provocatie de UV-regio van het oog omschakelt.
- De gekleurde runen zijn albedokleuren. Echt oplichten in het donker vereist een apart emissief masker en een emissieve renderpass.
- Niet in Minecraft gevalideerd; geen repositorybestanden gewijzigd.
