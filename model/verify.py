"""Assembly check of the whole D2 back-cover case, run in Blender:

  blender -b --factory-startup --python 3dmodel/verify.py -- [--closure backsnap] [--shape D2]

It builds the real parts and then does what you do with your hands: put the pins in,
lower the watch in, close the back cover, drop the charger puck into its window. Every step
is checked for collisions, and thin or tiny features that will not print are listed.
Prints a PASS/FAIL line per check and exits 1 if anything fails.
"""
import math
import os
import sys

MODEL = os.path.dirname(os.path.abspath(__file__))
if MODEL not in sys.path:
    sys.path.insert(0, MODEL)
sys.dont_write_bytecode = True

import bmesh  # noqa: E402
import bpy  # noqa: E402
from mathutils import Vector  # noqa: E402
from mathutils.bvhtree import BVHTree  # noqa: E402

import mesh_ops as m  # noqa: E402
from outline import button_axes, cavity_parts, check, lanyard_clearance  # noqa: E402
from params import CaseParams, back_window, derived  # noqa: E402
from parts import build_body, build_cap, build_pins, lanyard_z, rounded_rect, zones_back  # noqa: E402

RESULTS = []


def report(ok, name, detail=""):
    RESULTS.append(ok)
    print(("PASS  " if ok else "FAIL  ") + name + (f"   [{detail}]" if detail else ""))


def overlap(a, b):
    """Volume (mm3) shared by two objects; neither is modified."""
    hit = m.boolean(m.duplicate(a, "oa"), m.duplicate(b, "ob"), "INTERSECT")
    return m.volume(hit) if len(hit.data.polygons) else 0.0


def moved(obj, dx=0.0, dy=0.0, dz=0.0):
    c = m.duplicate(obj, "moved")
    m.translate(c, dx, dy, dz)
    return c


def tree_of(obj):
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    t = BVHTree.FromBMesh(bm)
    bm.free()
    return t


def inside(tree, p):
    d = Vector((1.0, 0.0137, 0.0071)).normalized()
    origin, hits = Vector(p), 0
    for _ in range(200):
        loc, _n, _i, _d = tree.ray_cast(origin, d)
        if loc is None:
            break
        hits += 1
        origin = loc + d * 1e-4
    return hits % 2 == 1


def thin_spots(obj, limit, step=0.8, skip=None):
    """Solid points where the material is thinner than `limit` (thickness measured along the
    nearest surface normal). Returns [(x, y, z, thickness)] clustered to ~2 mm."""
    tree = tree_of(obj)
    (lo, hi) = m.bbox(obj)
    found = {}
    x = lo[0]
    while x <= hi[0]:
        y = lo[1]
        while y <= hi[1]:
            z = lo[2] + step / 2
            while z <= hi[2]:
                p = Vector((x, y, z))
                if inside(tree, p):
                    loc, normal, _i, dist = tree.find_nearest(p)
                    if loc is not None and dist < limit / 2 + 0.2:
                        inward = (p - loc)
                        if inward.length > 1e-6:
                            v = inward.normalized()
                            far, _n, _i2, d2 = tree.ray_cast(p + v * 1e-4, v)
                            thick = dist + (d2 if far is not None else 99.0)
                            if thick < limit and not (skip and skip(x, y, z)):
                                key = (round(x / 2), round(y / 2), round(z / 2))
                                if key not in found or thick < found[key][3]:
                                    found[key] = (round(x, 1), round(y, 1), round(z, 1), round(thick, 2))
                z += step
            y += step
        x += step
    return list(found.values())


def build_watch(p, oval=0.35):
    """Stand-in watch (screen at z = 0): oval body, horns, buttons, back bump."""
    q = p.with_(clearance=0.0, horn_clear=0.0, body_extra_y=-oval, body_extra_x=0.0, body_extra_far=0.0,
                button_horn_gap=0.0, lower_button_extra=0.0, pin_slot_extra=0.0)
    parts = cavity_parts(q)
    w = m.prism(parts[0], 0.0, p.watch_t, "watch")
    for i, part in enumerate(parts[1:5]):
        w = m.boolean(w, m.prism(part, 0.0, p.watch_t, f"h{i}"), "UNION")
    for i, part in enumerate(parts[5:]):
        w = m.boolean(w, m.prism(part, p.button_z - p.button_h / 2, p.button_z + p.button_h / 2, f"b{i}"), "UNION")
    w = m.boolean(w, m.cylinder(0.0, 0.0, 13.5, p.watch_t - 0.01, p.watch_t + 1.5, "bump"), "UNION")
    return w


def build_puck(p):
    bw = back_window(p)
    shape = rounded_rect(bw.cx, bw.cy, p.puck_w, p.puck_h, p.puck_corner_r)
    return m.prism(shape, 0.0, 10.5, "puck")


def main(argv):
    shape = argv[argv.index("--shape") + 1] if "--shape" in argv else "D2"
    closure = argv[argv.index("--closure") + 1] if "--closure" in argv else "backsnap"
    p = CaseParams().with_(shape=shape, closure=closure)
    d = derived(p)
    z_top = d.T - d.back_t
    print(f"== {shape} / {closure}: {2 * d.outer_r:.1f} x {2 * d.y_top:.1f} x {d.T:.2f} mm ==")

    issues = check(p)
    report(not issues, "design rules (check())", "; ".join(issues))

    m.clear_scene()
    body, cap = build_body(p), build_cap(p)
    pins = build_pins(p)
    report(m.is_manifold(body), "body is watertight")
    report(m.is_manifold(cap), "cap is watertight")
    report(all(m.is_manifold(x) for x in pins), "pins are watertight")

    # ---- printability: thin or tiny material --------------------------------------------
    from outline import dist_to_outline

    from outline import lanyard_hole
    _tip, lpt, lt = lanyard_hole(p)

    def deliberate(x, y, z):
        """Edges that are thin by shape, not by mistake: the 0.4 mm front plate and its bevel, the 1 mm
        chamfer along the front edge, and the knife edge where the lanyard hole leaves the corner."""
        if z < d.front_t + 0.35:
            return True
        if z < 1.4 and dist_to_outline((x, y), p) < 1.4:
            return True
        # distance from the point to the lanyard hole's surface (hole axis runs in the XY plane at height lanyard_z)
        v = (x - lpt[0], y - lpt[1])
        along = v[0] * lt[0] + v[1] * lt[1]
        perp = math.hypot(v[0] - along * lt[0], v[1] - along * lt[1], z - lanyard_z(p))
        return shape == "D2" and perp - p.lanyard_d / 2 < 0.9 and dist_to_outline((x, y), p) < 3.5
    t = thin_spots(body, 0.6, skip=deliberate)
    report(not t, "body: no material thinner than 0.6 mm (besides the 0.4 mm plate)",
           "; ".join(f"{s}" for s in t[:6]))
    zgb = zones_back(p).zg
    t = thin_spots(cap, 0.6, skip=lambda x, y, z: abs(z - zgb) < 0.6 or (z > d.T - 1.5 and dist_to_outline((x, y), p) < 1.2))   # bead wedge and the 1 mm back chamfer
    report(not t, "cap: no material thinner than 0.6 mm (besides the snap bead wedge)", "; ".join(f"{s}" for s in t[:6]))
    t = thin_spots(pins[0], 0.6)
    report(not t, "pin: no material thinner than 0.6 mm", "; ".join(f"{s}" for s in t[:4]))

    # ---- the pins go in from the back -------------------------------------------------
    ok_all = True
    for i, ((base, u), pin) in enumerate(zip(button_axes(p), pins)):
        worst = 0.0
        for dz in (16.0, 12.0, 8.0, 4.0, 0.0):
            worst = max(worst, overlap(moved(pin, u[0] * -11, u[1] * -11, dz), body))
        for a in (-11.0, -8.0, -5.0, -3.0, -1.0, 0.0):
            worst = max(worst, overlap(moved(pin, u[0] * a, u[1] * a, 0.0), body))
        ok_all &= worst < 0.02
        report(worst < 0.02, f"pin {i}: drops in from the back and slides into its pocket", f"worst overlap {worst:.3f} mm3")
    # travel: how far each pin can be pressed in and pushed out before it touches the body
    for i, ((base, u), pin) in enumerate(zip(button_axes(p), pins)):
        free_in = max((s for s in (0.0, 0.2, 0.4, 0.6, 0.8) if overlap(moved(pin, -u[0] * s, -u[1] * s, 0.0), body) < 0.02), default=-1)
        free_out = max((s for s in (0.0, 0.1, 0.2, 0.3, 0.4) if overlap(moved(pin, u[0] * s, u[1] * s, 0.0), body) < 0.02), default=-1)
        report(free_in >= p.pin_travel_in - 1e-9 and free_out >= 0.1,
               f"pin {i}: can be pressed in {p.pin_travel_in} mm and has outward play", f"free in {free_in}, free out {free_out} mm")

    # ---- the watch is lowered straight in, with both pins already in place ----------------
    watch = build_watch(p)
    report(m.volume(watch) > 0, "watch model built", f"{m.volume(watch):.0f} mm3")
    worst = 0.0
    bad_dz = None
    for dz in (22.0, 18.0, 15.0, 13.0, 11.0, 9.0, 7.0, 5.0, 3.0, 1.0, 0.0):
        w = moved(watch, 0.0, 0.0, d.front_t + dz)
        v = overlap(w, body) + sum(overlap(w, pin) for pin in pins)
        if v > worst:
            worst, bad_dz = v, dz
    report(worst < 0.05, "watch can be lowered straight in (pins already installed)",
           f"worst overlap {worst:.3f} mm3" + (f" at {bad_dz} mm above its seat" if worst >= 0.05 else ""))
    # the watch can shift a little in its pocket: x from -0.2 (toward the buttons) to 0.0, y by +-0.08
    for dx, dy in ((-0.2, 0.0), (-0.1, 0.08), (-0.1, -0.08), (0.0, 0.0)):
        v = overlap(moved(watch, dx, dy, d.front_t), body)
        report(v < 0.05, f"watch seated, shifted {dx:+.2f} / {dy:+.2f} mm in its pocket", f"overlap {v:.3f} mm3")

    # ---- the back cover closes ---------------------------------------------------------
    seated_watch = moved(watch, 0.0, 0.0, d.front_t)
    worst = 0.0
    for dz in (6.0, 4.0, 2.0, 1.0, 0.5, 0.0):
        c = moved(cap, 0.0, 0.0, -0.0 + dz)
        worst = max(worst, overlap(c, seated_watch) + sum(overlap(c, pin) for pin in pins))
    report(worst < 0.05, "back cover closes without touching the watch or the pins", f"worst overlap {worst:.3f} mm3")
    v = overlap(cap, body)
    report(v < 0.05, "closed cover and body do not overlap (snap has a gap)", f"{v:.3f} mm3")
    bz = zones_back(p)
    report(bz.zs > d.front_t + p.button_z + p.button_w / 2 + p.clearance + p.pin_flange_extra,
           "the rim/seam is above every button pocket", f"seam at z = {bz.zs:.2f}")

    # ---- charger puck in the back window ---------------------------------------------------
    bw = back_window(p)
    puck_tree = tree_of(build_puck(p))
    c_tree = tree_of(cap)
    pts = []
    gx = bw.cx - p.puck_w / 2
    while gx <= bw.cx + p.puck_w / 2:
        gy = bw.cy - p.puck_h / 2
        while gy <= bw.cy + p.puck_h / 2:
            if inside(puck_tree, (gx, gy, 5.0)):
                pts.append((gx, gy))
            gy += 1.5
        gx += 1.5
    blocked = [q for q in pts if inside(c_tree, (q[0], q[1], d.T - 0.5))]
    report(len(pts) > 100 and not blocked, "charger window is open over the whole puck face",
           f"{len(pts)} points checked, {len(blocked)} blocked")
    pin_row = bw.cy + p.puck_h / 2 - p.puck_pin_edge
    report(abs(pin_row - p.pin_y) < 0.05, "puck pins line up with the watch pins", f"puck row y = {pin_row:.2f}, watch row y = {p.pin_y}")
    report(bw.w - p.puck_w >= 0.8 and bw.h - p.puck_h >= 0.8, "window leaves at least 0.4 mm play per side around the puck")
    # the corners really are rounded for a 6.75 mm puck corner
    corner = (bw.cx + p.puck_w / 2 - 0.5, bw.cy + p.puck_h / 2 - 0.5)
    report(inside(puck_tree, (corner[0] - 2.5, corner[1] - 2.5, 5.0)) and not inside(c_tree, (corner[0] - 2.5, corner[1] - 2.5, d.T - 0.5)),
           "puck corner sits inside the window corner")

    # ---- lanyard and screen -----------------------------------------------------------------
    r = p.lanyard_d / 2
    if shape == "D2":
        report(lanyard_clearance(p) - r >= p.lanyard_wall - 1e-6, "lanyard hole keeps 1.5 mm of wall to the watch pocket",
               f"{lanyard_clearance(p) - r:.2f} mm")
        report(lanyard_z(p) + r < bz.zs, "lanyard hole is below the seam, clear of the cover rim", f"hole z {lanyard_z(p) - r:.2f}..{lanyard_z(p) + r:.2f}, seam {bz.zs:.2f}")
    margin = p.screen_open_d / 2 - p.display_d / 2 - abs(p.screen_dx) - 0.3
    report(margin > 0.5, "screen opening shows the whole lit display with room for slop", f"{margin:.2f} mm spare")
    body_tree = tree_of(body)
    report(not inside(body_tree, (p.screen_dx + p.screen_open_d / 2 - 0.2, 0.0, d.front_t / 2))
           and inside(body_tree, (p.screen_dx + p.screen_open_d / 2 + 1.0, 0.0, d.front_t / 2)),
           "front plate is 0.4 mm thick around the opening", f"plate {d.front_t:.2f} mm")

    bad = RESULTS.count(False)
    print(f"\n{len(RESULTS) - bad} checks passed, {bad} failed")
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    sys.exit(main(argv))
