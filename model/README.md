# Case model: the 3D-printing folder

The parametric case for the [pocket music player](../README.md). Python scripts run inside Blender (in the background, you never open the window) and build every part from a table of numbers in `params.py`. For printer calibration, the watch measurements and the print order, see the [case guide](../docs/3d-printed-case.md). This file covers running the scripts.

You need **Blender 5.0**. Nothing else: Blender brings its own Python.

## Models that are kept

| Model | Command options | Parts |
| --- | --- | --- |
| **D2, one-piece body and snap-on back cover** (53.0 x 57.2 x 14.05 mm) | `--shape D2 --closure backsnap` | `body`, `cap`, `pins`, `fittest`, `dummy` |
| **E3, two pieces** (about 63 x 86 mm) | `--shape E3 --closure snap` | `front`, `shell`, `pins`, `fittest`, `dummy` |

`pins` are the push-pins for the side buttons (print upright with a brim), `fittest` is a thin slice for checking the fit, and `dummy` is a stand-in watch for trying the snap without risking the real one.

The script still accepts the other shapes (`E1`), closures (`backscrew`, `backboth`), back types (`B3`) and edge profiles (`P2`), but they are not exported or checked any more.

## Build

Run from the repository folder (Windows path shown):

```bash
BLENDER="/c/Program Files/Blender Foundation/Blender 5.0/blender.exe"

"$BLENDER" -b --factory-startup --python-exit-code 1 --python model/build.py -- \
  --shape D2 --closure backsnap --out exports

# override any number without editing a file:
#   ... --set fit_gap=0.25 --set bead_h=0.45
```

Output goes to `exports/` (git-ignored): one STL file per part, already turned face down for printing, PNG previews, and `report.md` with the size, wall and plate thickness, the button depths and any open items.

The build refuses settings that cannot work: a plate that is too thin, a screen opening that covers the display, a back window so big the watch would fall out, a snap that would not click.

## Tolerance test print

```bash
"$BLENDER" -b --factory-startup --python-exit-code 1 --python model/tolerance_test.py -- --out exports
```

This makes `exports/tolerance_test.stl`: 7 holes (gap 0.10 to 0.40 mm per side) and 7 loose pegs. The smallest gap where the peg slides in freely by hand is the value for `fit_gap`.

## Parameters

Open `params.py`. The watch measurements are already filled in from calipers, and the file explains each one. The ones you will touch most:

- `fit_gap`: the part-to-part gap. Put the tolerance-test result here, and **raise `bead_h` by the same amount**, or the snap stops clicking (the build refuses if the click drops under 0.15 mm).
- `clearance`: the gap between the watch and the case, per side.
- `watch_t`: thickness from the bezel face to the back, **without** the sensor bump. The glass bump is `glass_rise`. `button_z` is measured from the bezel face.
- `wall_min`, `back_t`, `raised_lip`: wall and plate thicknesses.

## Check the assembly

`verify.py` does what your hands do: it installs the pins, lowers the watch in, closes the back cover and drops the charger puck into its window, checking for collisions at every step and listing material too thin to print.

```bash
"$BLENDER" -b --factory-startup --python model/verify.py -- --shape D2 --closure backsnap
```

Run it after any change to the model. D2 `backsnap` passes all 28 checks.

## Tests

```bash
cd model
python -B -m unittest tests.test_params tests.test_outline                  # 2D maths, any Python 3
"$BLENDER" -b --factory-startup --python-exit-code 1 --python tests/run_blender.py   # meshes
```

## Files

| File | Role |
| --- | --- |
| `params.py` | The table of numbers and the design rules that check them |
| `outline.py` | The 2D shapes (watch outline, case outline, pockets) |
| `parts.py` | Turns the outlines into the 3D parts |
| `mesh_ops.py` | Small mesh helpers |
| `build.py` | The command-line entry point |
| `tolerance_test.py` | The tolerance test print |
| `verify.py` | The virtual assembly check |
| `tests/` | Unit tests and the Blender mesh tests |

## Licence

[GNU General Public License, version 2](../LICENSE).
