# OnePlus Watch 2R pocket case: 3D printing guide

A printable case that holds the OnePlus Watch 2R for a pocket or a lanyard: the screen centred in a front plate, thin even walls, a window in the back so it charges in the case, and a hole for a lanyard.

The model is **parametric**: Python scripts running in Blender (in the background, you never open the window) build every part from a table of numbers. Change a number, rebuild, get new STL files.

> **Status: work in progress.** The model is built from measurements of the real watch and passes its own assembly checks, but **no finished case has been printed and fitted yet**. Early fit tests failed and were fixed (section 6). The push-pins and the snap fit have not been confirmed on a printer. Treat the STL files as a starting point and **always print the fit test first**.

---

## 0. What you need

- An FDM printer with a 0.4 mm nozzle. I used a **Creality Ender-3 V2 Neo** (Bowden, 220 x 220 mm bed, stock firmware, SD card), but any calibrated printer works
- A slicer. I use **OrcaSlicer** (it has an Ender-3 V2 Neo profile and built-in calibration prints)
- **Blender 5.0** to rebuild the model (only if you change parameters). Blender ships its own Python, so nothing else is needed
- **Digital calipers**, for calibration and for checking the watch measurements
- Filament: PLA for fit tests, **PETG** for the final case (it flexes for the snap and survives a warm pocket)

## 1. The design

Two models are kept and maintained:

| Model | What it is | Size (W x H x T) |
| --- | --- | --- |
| **D2, `backsnap`** | Rounded rectangle, short, hugging the watch. One-piece body (front plate and walls, open at the back) plus a flat back cover that snaps on. This is the model being refined and checked | 53.0 x 57.2 x 14.05 mm |
| **E3, `snap`** | A longer pill shape with room below the watch. Two pieces: a front plate and a back shell that snap together around the middle edge | about 63 x 86 mm, 14.5 mm thick |

Everything else is still a setting in the script (other shapes, closures, back and edge types), but those variants are no longer exported or checked.

| Choice | Options | Default |
| --- | --- | --- |
| Shape `shape` | `E1` round top and square bottom, `E3` pill, `D2` rounded rectangle | `E3` |
| Closure `closure` | `snap` (front and shell), `backsnap` (body and snap-on back cover), `backscrew`, `backboth` (add three M2 screws). `screws` and `slide` are planned, not built | `snap` |
| Screen edge `screen` | `raised` lip above the glass, or `flush` | `raised` |
| Back `back` | `B1` window for the charging puck, `B3` the same with a funnel that centres the puck | `B1` |
| Edge profile `edge` | `P1` 1 mm chamfer (prints cleanly face down), `P2` 2 mm round | `P1` |

Design rules that shaped it:

- **Thin, equal walls** around the watch, with the screen centred.
- **Flush button windows:** each side button sits in a window in the wall. The wall is thicker than the button sticks out, so the case has small printed **push-pins** that sit in the windows and press the buttons.
- **Charging:** the case rests on the stock magnetic puck through a window in the back. There is no cable channel, because the puck is 10.5 mm tall and a pocket for it would make the case much thicker.
- **No metal** near the back or the NFC area, so payments work through the case.
- **Each part prints face down without supports.**
- On the one-piece closures the seam sits above every button window, so the walls stay full strength there.

## 2. Set up and calibrate the printer

A case with 0.25 mm gaps needs a calibrated printer. With OrcaSlicer (**Calibration** menu), in this order, and again for every new filament:

1. **Health check:** belts snug (not overtight), no wobble in the bed or the head, the X gantry level. On a single-Z-motor printer like the Ender-3 V2 Neo, the right end of the X bar can sag: measure both sides from the frame, press the low side into line, and adjust its eccentric nut if it drifts.
2. **Bed level:** paper level the corners, set the Z-offset, run the auto level if you have a probe, then a first-layer test.
3. **Dry the filament** if it pops, strings or snaps (PLA about 45 C for 4 to 6 h).
4. **Temperature tower:** pick the lowest temperature with clean overhangs and layers that don't split.
5. **Flow ratio:** two passes; pick the smoothest top surface.
6. **Retraction:** lowest length without strings (Bowden: start at 4 to 6 mm, 40 to 45 mm/s).
7. **20 mm cube:** measure X, Y and Z. Within 0.1 mm is good.
8. **Tolerance test:** the model generates its own (`tolerance_test.stl`, section 4): 7 holes with 0.10 to 0.40 mm gap per side and 7 pegs. The **smallest gap where the peg slides in by hand** is your `fit_gap`. Mine was **0.25 mm** (PLA). Redo it in the filament you print the case in.

Suggested print settings for the case: face down, no supports, 0.16 to 0.20 mm layers (0.12 to 0.16 for the final case to hide layer lines), 4 walls, 25 to 40 % gyroid infill. PETG: 230 to 240 C, bed 70 to 80 C, fan 30 to 50 %, and a thin glue-stick layer on PEI as a release agent.

A **textured** PEI sheet gives a satin face that hides fingerprints. A glossy face needs a smooth PEI sheet. PETG sands badly; if you want to sand the edges round, print in matte PLA.

## 3. The watch measurements

The measured values are already in `model/params.py`. The 2R comes in one size, so they should fit yours, but check the important ones with calipers.

| Parameter | Value | What |
| --- | --- | --- |
| `body_d` | 47.5 | Round case diameter (the bezel is not wider) |
| `watch_t` | 12.0 | Bezel face to the flat back, **without** the 1.5 mm sensor bump |
| `glass_rise` | 0.0 | Glass is flush with the bezel |
| `display_d` | 37.0 | Lit screen diameter (the flat glass is 44) |
| `lug_extent` | 25.5 | Centre to the tip of the lugs |
| `horn_gap` | 22.0 | Inside gap between the two lugs at one end |
| `horn_tip_outer` / `horn_root_outer` | 26.0 / 33.0 | Across both lugs, outside, at the tip and at the root |
| `button_angles_deg` | 26.2 / -30.3 | Button positions from 3 o'clock, after the fit tests |
| `button_protrusion` | 2.5 | How far the buttons stick out |
| `button_w` x `button_h` | 6 x 6 | Button size |
| `button_z` | 6.0 | Bezel face to the button's centre |
| `pin_y` | 15.25 | Charging pins: row centre above the watch centre |
| `puck_w` x `puck_h` | 33 x 35.5 | Charging puck (rounded square, 10.5 mm tall). Pin row 5 mm in from its top edge |
| `puck_corner_r` | 6.75 | Puck corner radius, from the straight edge lengths |

Lessons from measuring:

- The lugs are **four separate horns**, not one solid block. The case needs a pocket for each.
- Measure button positions as a distance from the centreline at both buttons, then convert to an angle. Guessing "2 and 4 o'clock" was wrong.
- Measure thickness without the sensor bump, and the bump separately.

## 4. Build the model

From the repository folder (Windows path shown; on macOS or Linux use your Blender path):

```bash
BLENDER="/c/Program Files/Blender Foundation/Blender 5.0/blender.exe"

# Tolerance test print (do this first, section 2 step 8)
"$BLENDER" -b --factory-startup --python-exit-code 1 --python model/tolerance_test.py -- --out exports

# D2, one-piece body with a snap-on back cover
"$BLENDER" -b --factory-startup --python-exit-code 1 --python model/build.py -- \
  --shape D2 --closure backsnap --out exports --set fit_gap=0.25 --set bead_h=0.45

# E3, two pieces
"$BLENDER" -b --factory-startup --python-exit-code 1 --python model/build.py -- \
  --shape E3 --closure snap --out exports --set fit_gap=0.25 --set bead_h=0.45
```

Output in `exports/`:

- One STL per part, **already turned face down** for printing:
  - `body` and `cap` (one-piece closures): the body with the front plate, walls and button windows, and the flat back cover with the puck window
  - `front` and `shell` (two-piece closure): the face plate, and the walls with the back plate
  - `pins`: the two button push-pins (print upright, flange down, with a brim)
  - `fittest`: a fast, thin slice of the case to check the fit
  - `dummy`: a stand-in watch, to try the snap without risking the real one
- PNG previews (front, back, 3D view).
- `report.md`: outer size, plate and wall thicknesses, how deep each button sits, and open items.

The build **refuses** settings that cannot work: a plate thinner than 0.4 mm, a screen opening that covers the display, a back window so big the watch would fall out, a snap that would not click.

Key parameters:

- `fit_gap`: part-to-part gap. Use your tolerance test result. **Raise `bead_h` by the same amount**, or the snap stops clicking (bead height minus gap must stay at least 0.15 mm).
- `clearance`: gap between watch and case, per side (default 0.25).
- `wall_min`, `back_t`, `raised_lip`: wall and plate thicknesses.

**Check the assembly** after any change. `verify.py` does what your hands do: it installs the pins, lowers the watch in, closes the back cover and drops the puck into its window, checking for collisions at every step and listing any material too thin to print:

```bash
"$BLENDER" -b --factory-startup --python model/verify.py -- --shape D2 --closure backsnap
```

D2 `backsnap` passes all 28 checks.

Tests: `cd model` then `python -B -m unittest tests.test_params tests.test_outline` (2D maths), and `"$BLENDER" -b --factory-startup --python-exit-code 1 --python model/tests/run_blender.py` (meshes).

## 5. Print order

1. **Tolerance test**, then set `fit_gap` (and `bead_h`).
2. **Fit test** (`fittest_*.stl`) in PLA: does the watch drop in, do the lugs fit their pockets, do the button windows line up, is the screen fully visible?
3. **Dummy, then body and cap** (or front and shell) in PLA: check the snap clicks and opens.
4. **Push-pins:** check that they press the buttons and don't fall out.
5. **Final case** in PETG.

Check in the slicer that each part needs **no supports** and that your *calibrated* filament profile is selected, not a generic one. An easy mistake: the G-code should show your own temperature.

**Fitting the watch:** it goes in **screen down**, onto the front plate. Looked at from the open side, the buttons are on the **left**.

## 6. What went wrong so far

| Attempt | Result | Fix |
| --- | --- | --- |
| Fit test 1 | The watch did not go in: one solid lug cap that was too small, and the buttons in the wrong place | Real horn pockets for the four lugs, body diameter 47.5, buttons measured |
| Fit tests 2 and 3 | Horns still caught, then the pocket was a little loose in places | `horn_clear` 0.5, round pocket adjusted at the ends and on the side away from the buttons |
| Watch inserted the wrong way | The upper and lower button swapped, which looked like a 1.5 mm error in the model | Screen down onto the front plate, buttons on the left from the open side. Nothing was wrong with the first measurements |
| Mirrored buttons | The buttons came out on the right, not the left. Blender's axes are right-handed, so a model that looks right from the front is mirrored when printed face down | Buttons at -X in the model, and a unit test guards it |
| Lower button | Sits 4.5 to 8 mm below the outer wall, too deep to press | Printed push-pins (untested on a printer) |
| Screen opening | The first opening was close to the lit area and blocked the edge swipes | Opening 41 mm with a bevel (lit area 37) |

## 7. Still open

- A finished print: the body and cap, the snap fit and the push-pins on a real printer
- Screw and sliding-back closures are planned, not built
- A smaller off-brand 4-pin charger would change the back window
- Combining with the software side: a closed back that keeps the wrist sensor in a steady state might replace the sensor restrict mode (see the software guide, section 4.3)
