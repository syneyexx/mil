# De Doolhofsoeverein — god of the Labyrinth

Monumental **9-block** native entity (`millenaire:doolhofsoeverein`) that embodies the god of Het Labyrint.

## Role

- Spawns **once** in the Audience nave (on the dais under De Ruit / void beam).
- Immobile awe presence: looks at players, ambient heartbeat, right-click acknowledgment.
- **Not** a farmable boss — player damage is blocked; incidental damage heavily resisted.
- De Ruit geometry floats **above** the sovereign (~Y floor+12) so the 9-block silhouette reads cleanly.

## Assets

| Path | Content |
|------|---------|
| `models/entity/doolhofsoeverein/doolhofsoeverein.model.json` | Native JSON (344 cubes, 24 groups, 4 arms) |
| `textures/entity/doolhofsoeverein/doolhofsoeverein.png` | 256×256 atlas |
| `tools/doolhofsoeverein_blockbench_v1/` | Source bbmodel + README |

Height verified at conversion: **y=0..144 units = exactly 9.00 blocks**. No extra ×9 scale.

## Gameplay sizes

| | Blocks |
|--|--------|
| Visual model | 9.0 |
| Collision | 2.4 × 7.5 |
| Tracking range | 16 chunks |

## Code

- `DoolhofsoevereinEntity` — PathfinderMob, no combat AI
- `DoolhofsoevereinRenderer` — `NativeJsonEntityModel`
- `LabyrinthSpawnHandler.maybeSpawnSovereign` — ensure-one near Audience
- Audience first-visit copy references the Sovereign

IP-clean: Millénaire name **Doolhofsoeverein** / Labyrinth Sovereign — not Leviathan.
