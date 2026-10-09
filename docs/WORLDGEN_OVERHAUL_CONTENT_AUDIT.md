# World Generation Overhaul — Content Audit

Counts derived from bundled files under `src/main/resources/millenaire/cultures/` at implementation time.  
Natural eligibility is decided at runtime by `WorldgenContentSnapshot` after loaders resolve plans/NBT — use `/millenaire worldgen content` for the live report.

| Culture folder | Village JSON defs | Building JSON (approx) | NBT templates | Notes |
|----------------|-------------------:|------------------------:|--------------:|-------|
| norman | 27 | 130 | 449 | Includes hamlets (`hameau_*`), loners, controlled, marvels |
| byzantines | 14 | 83 | 299 | Military / religious / trading / towers |
| indian | 14 | 67 | 229 | Desert + jungle tags; hamlets present |
| japanese | 13 | 50 | 201 | Gunji / nogyo / seiji + loners |
| mayan | 14 | 70 | 236 | Jungle-focused; many lone ruins |
| inuits | 25 | 55 | 137 | Cold/snowy tags; many lone buildings |
| seljuk | 19 | 114 | 260 | Desert/savanna/badlands |

**Totals (bundled):** 126 village definition files · ~569 building JSON · ~1811 NBT.

## Village IDs by culture (filenames)

- **norman:** abandonedmill, abandonedmine, abandonedtower, agricole, artisans, banditlair, bandittower, bourg_autonome, controlled, ecclesiastique, feloncastle, grosbourg, hameau_abbatiale, hameau_agricole, hameau_artisans, loneabbey, loneinn, lonelumberman, lonelumbermantaiga, lonemill, loneminer, loneroadhouse, militaire, notredame, pantheon, ruinedfarm, walledcontrolled
- **byzantines:** ancienttemple, ancienttheatre, birchtower, controlled, lighthouse, lonemonastery, lonesheepfarm, lonesheepfarm_x, militaryvillage, oaktower, pinetower, religiousvillage, tradingvillage, tropicaltower
- **indian:** adivasifarm, agricole_hamlet, agricole, controlled, controlled_walled, deserttradeoutpost, fort_hamlet, fort, indiandesertedfarm, loneshrine, palace_autonomous, palace, ruinedfort, sandblasted
- **japanese:** bandit_cave, banditfort, controlled, gunji, hotspringsinn, japaneseoutpost, kufun_tomb, lonebuddha, lonemine, nogyo, seiji, trading, woods_shrine
- **mayan:** agriculture, controlled, lonemayancave, lonemayancornfarm, lonemayanfarm, lonemayanmining, lonemayanpyramid, lonemayanruin, lonemayanshaman, mayanbandithome, mayanbandits, mayanruinedpyramid, military, religious
- **inuits:** artisanalvillage, banditshack, bison, communityvillage, farmingvillage, huntingvillage_controlled, huntingvillage, inuksuit, lake2, loneanimistic, loneanishaman, loneartisan, lonedisiglu2, lonedisiglu, lonedislogcabin2, lonedislogcabin, lonefisherman, loneiglu2, loneiglu, lonelogcabin2, lonelogcabin, lonewoodworker, polarbearcave, religiousvillage, sanduit
- **seljuk:** agriculture_small_seljuks, agriculture_village_seljuks, artisans_small_seljuks, banditcamp, bandittower, controlled_fort_seljuks, controlled_small_village_seljuks, hamamruin, lonecowfarm, lonefarm, lonemarket, lonemine, military_village_seljuks, religious_village_seljuks, ruin, ruinlonemine, ruinwall, ruinwell, trading_village_seljuks

## Validation categories (runtime)

`WorldgenContentSnapshot` checks per village type:

- Missing centre / mandatory starter plan set or NBT
- Invalid dimensions
- Invalid wall / hamlet references
- Player-controlled / zero-weight / non-spawnable exclusion from natural pool

Invalid types are logged in the snapshot report and excluded from natural generation; they are not silently substituted.
