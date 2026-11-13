"""Build the case: parameters -> checks -> parts -> STLs, previews, report.

Run inside Blender (see README.md):
  blender -b --factory-startup --python-exit-code 1 --python model/build.py -- \
      --shape E3 --closure snap --screen raised --back B1 --edge P1 \
      --parts front,shell,fittest,dummy --out exports [--set key=value ...]
"""
import argparse
import os
import sys
from dataclasses import fields

sys.dont_write_bytecode = True
MODEL = os.path.dirname(os.path.abspath(__file__))
if MODEL not in sys.path:
    sys.path.insert(0, MODEL)

import mesh_ops as m  # noqa: E402
from outline import button_recesses, case_bounds, check, min_wall  # noqa: E402
from params import OPEN_ITEMS, PLACEHOLDERS, CaseParams, derived, groove_depth  # noqa: E402
from parts import (build_body, build_cap, build_dummy_watch, build_fit_test, build_front,  # noqa: E402
                   build_pins, build_shell)

RECESS_WARN = 1.5
ALL_PARTS = ("front", "shell", "body", "cap", "pins", "fittest", "dummy")
DEFAULT_PARTS = {"front_back": "front,shell,pins,fittest,dummy", "one_piece": "body,cap,pins,fittest,dummy"}


def _parse_value(name, text):
    default = getattr(CaseParams(), name)
    if isinstance(default, tuple):
        return tuple(float(v) for v in text.split(",") if v.strip())
    if isinstance(default, str):
        return text
    return float(text)


def params_from_args(args) -> CaseParams:
    names = {f.name for f in fields(CaseParams)}
    changes = {k: getattr(args, k) for k in ("shape", "closure", "screen", "back", "edge")
               if getattr(args, k) is not None}
    for item in args.set or []:
        key, _, value = item.partition("=")
        if key not in names:
            raise SystemExit(f"unknown parameter {key!r}")
        changes[key] = _parse_value(key, value)
    return CaseParams().with_(**changes)


def variant_name(p: CaseParams) -> str:
    return f"case_{p.shape}_{p.closure}_{p.screen}_{p.back}_{p.edge}"


def _orient_for_print(name, obj, p):
    """Every part ends up outer face down with min z = 0."""
    if name in ("shell", "cap"):
        m.flip_x(obj)            # back face (z = T) goes to the bed
    (_, _, z0), _ = m.bbox(obj)
    m.translate(obj, 0, 0, -z0)


def _build_pins_for_printing(p):
    """All push-pins as one object: each stood upright (flange on the bed, stem up), side by side."""
    import math
    from mathutils import Matrix
    from outline import button_axes
    pins = build_pins(p)
    placed = []
    for i, ((base, u), pin) in enumerate(zip(button_axes(p), pins)):
        ang = math.atan2(u[1], u[0])
        mat = (Matrix.Rotation(math.radians(-90.0), 4, "Y") @ Matrix.Rotation(-ang, 4, "Z")
               @ Matrix.Translation((-base[0], -base[1], 0.0)))
        m.transform(pin, mat)
        (x0, y0, z0), _ = m.bbox(pin)
        m.translate(pin, -x0, i * 14.0 - y0, -z0)
        placed.append(pin)
    out = placed[0]
    for other in placed[1:]:
        out = m.boolean(out, other, "UNION")
    out.name = "pins"
    return out


def _report(p, out_dir, exported):
    d = derived(p)
    default = CaseParams()
    lines = [f"# Build report: {variant_name(p)}", ""]
    changed = [f"- `{f.name}` = {getattr(p, f.name)} (default {getattr(default, f.name)})"
               for f in fields(CaseParams) if getattr(p, f.name) != getattr(default, f.name)]
    lines += ["## Parameters changed from default", ""] + (changed or ["- none"]) + [""]
    lines += ["## Size", "",
              f"- Outer: **{2 * d.outer_r:.2f} × {(case_bounds(p)[0] - case_bounds(p)[1]):.2f} × {d.T:.2f} mm** (W × H × T)",
              f"- Thickness target 15–16 mm: {'within' if 15 <= d.T <= 16 else 'outside'} (soft target)",
              f"- Front plate {d.front_t:.2f} mm, back plate {d.back_t:.2f} mm, side wall {d.side_wall:.2f} mm",
              f"- Thinnest wall around the watch: {min_wall(p):.2f} mm"]
    if p.closure == "snap":
        lines += [f"- Wall behind the snap groove: {min_wall(p) - groove_depth(p):.2f} mm (min 0.8)",
                  f"- Snap click (bead_h − fit_gap): {p.bead_h - p.fit_gap:.2f} mm. If you raise "
                  f"`fit_gap`, raise `bead_h` by the same amount to keep the click"]
    lines.append("")
    lines += ["## Buttons and push-pins", "",
              "How far each button tip sits inside the outer surface, along the button's axis. "
              "Each button gets a printed push-pin of about this length (plus its flange).", ""]
    for deg, r in zip(p.button_angles_deg, button_recesses(p)):
        lines.append(f"- Button at {deg:+.0f}° (0° = 3 o'clock): {r:.2f} mm below the surface, "
                     f"push-pin about {r - p.pin_inset:.2f} mm long")
    lines.append("")
    still = [n for n in PLACEHOLDERS if getattr(p, n) == getattr(default, n)]
    lines += ["## Open items", ""]
    lines += ([f"- PLACEHOLDER values still in use (measure the watch): " + ", ".join(f"`{n}`" for n in still)]
              if still else [])
    lines += [f"- {item}" for item in OPEN_ITEMS] or ["- none"]
    lines.append("")
    lines += ["## Files", ""]
    for fname, vol in exported:
        lines.append(f"- `{fname}`: {vol / 1000:.1f} cm³")
    with open(os.path.join(out_dir, "report.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def main(argv) -> int:
    ap = argparse.ArgumentParser(prog="build.py")
    for key in ("shape", "closure", "screen", "back", "edge"):
        ap.add_argument(f"--{key}")
    ap.add_argument("--parts", default=None)
    ap.add_argument("--out", default=os.path.join(os.path.dirname(MODEL), "exports"))
    ap.add_argument("--set", action="append", metavar="KEY=VALUE")
    ap.add_argument("--no-previews", action="store_true")
    args = ap.parse_args(argv)
    args.out = os.path.abspath(args.out)

    p = params_from_args(args)
    issues = check(p)
    if issues:
        print("Cannot build:\n  " + "\n  ".join(issues))
        return 2
    if args.parts is None:
        args.parts = DEFAULT_PARTS["one_piece" if p.closure.startswith("back") else "front_back"]
    wanted = [s.strip() for s in args.parts.split(",") if s.strip()]
    unknown = set(wanted) - set(ALL_PARTS)
    if unknown:
        print(f"Unknown parts: {sorted(unknown)}")
        return 2
    os.makedirs(args.out, exist_ok=True)

    m.clear_scene()
    builders = {"front": build_front, "shell": build_shell, "body": build_body, "cap": build_cap,
                "pins": _build_pins_for_printing,
                "fittest": build_fit_test, "dummy": build_dummy_watch}
    names = {"front": f"{variant_name(p)}_front.stl", "shell": f"{variant_name(p)}_shell.stl",
             "body": f"{variant_name(p)}_body.stl", "cap": f"{variant_name(p)}_cap.stl",
             "pins": f"{variant_name(p)}_pins.stl",
             "fittest": f"fittest_{p.shape}.stl", "dummy": "dummy_watch.stl"}
    objs = {}
    for part in wanted:
        obj = builders[part](p)
        if not m.is_manifold(obj):
            print(f"{part} is not watertight; refusing to export")
            return 3
        objs[part] = obj

    if "front" in objs:
        pair = [objs[k] for k in ("front", "shell") if k in objs]
    else:
        pair = [objs[k] for k in ("body", "cap") if k in objs]
    if not args.no_previews and len(pair) == 2:
        for view in ("front", "iso", "back"):
            m.render_preview(pair,
                             os.path.join(args.out, f"{variant_name(p)}_{view}.png"), view)

    exported = []
    for part, obj in objs.items():
        _orient_for_print(part, obj, p)
        path = os.path.join(args.out, names[part])
        m.export_stl(obj, path)
        exported.append((names[part], m.volume(obj)))
    _report(p, args.out, exported)
    print(f"Built {len(exported)} part(s) into {args.out}")
    return 0


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    sys.exit(main(argv))
