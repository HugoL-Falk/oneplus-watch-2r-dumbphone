"""Tolerance test print: a plate with round holes of growing clearance, plus
loose pegs of the nominal size. Press each peg into its hole by hand: the
smallest gap where the peg slides in freely is the value for `fit_gap`.

Peg i has height PEG_H0 + i * 1.0 mm, so the peg that is 1 mm taller than the
previous one is the next gap up. Holes and pegs are in the same left-to-right
order, smallest gap first.

Run (see README.md):
  blender -b --factory-startup --python model/tolerance_test.py -- --out exports
"""
import os
import sys

sys.dont_write_bytecode = True
MODEL = os.path.dirname(os.path.abspath(__file__))
if MODEL not in sys.path:
    sys.path.insert(0, MODEL)

import mesh_ops as m  # noqa: E402

GAPS = (0.10, 0.15, 0.20, 0.25, 0.30, 0.35, 0.40)  # radial clearance per side, mm
PEG_D = 8.0
PLATE_T = 3.0
PITCH = 13.0
MARGIN = 7.0
PLATE_W = 14.0            # plate depth (Y)
PLATE_Y = 0.0             # plate centre line
PEGS_Y = 20.0             # pegs centre line
PEG_H0 = 8.0


def hole_x(i: int) -> float:
    return MARGIN + PEG_D / 2 + i * PITCH


def peg_height(i: int) -> float:
    return PEG_H0 + float(i)


def build_tolerance_test():
    n = len(GAPS)
    length = 2 * MARGIN + PEG_D + (n - 1) * PITCH
    plate = m.box((0.0, PLATE_Y - PLATE_W / 2, 0.0), (length, PLATE_Y + PLATE_W / 2, PLATE_T), "plate")
    for i, gap in enumerate(GAPS):
        plate = m.boolean(plate, m.cylinder(hole_x(i), PLATE_Y, PEG_D / 2 + gap, -1.0, PLATE_T + 1.0,
                                            f"hole{i}", segments=128), "DIFFERENCE")
    for i in range(n):
        peg = m.cylinder(hole_x(i), PEGS_Y, PEG_D / 2, 0.0, peg_height(i), f"peg{i}", segments=128)
        plate = m.boolean(plate, peg, "UNION")
    plate.name = "tolerance_test"
    return plate


def main(argv):
    import argparse
    ap = argparse.ArgumentParser(prog="tolerance_test.py")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(MODEL), "exports"))
    args = ap.parse_args(argv)
    out = os.path.abspath(args.out)
    os.makedirs(out, exist_ok=True)
    m.clear_scene()
    obj = build_tolerance_test()
    if not m.is_manifold(obj):
        print("tolerance test is not watertight; refusing to export")
        return 3
    path = os.path.join(out, "tolerance_test.stl")
    m.export_stl(obj, path)
    print(f"Wrote {path}: gaps {GAPS}, peg heights {[peg_height(i) for i in range(len(GAPS))]}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []))
