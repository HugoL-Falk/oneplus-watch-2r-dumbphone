"""The printable parts, built in assembled coordinates (z = 0 is the front
face, z = T the back face). Orientation for printing happens in build.py."""
import math
import sys
from dataclasses import dataclass

import mesh_ops as m
from outline import (button_axes, case_bounds, case_outline, cavity_parts,
                     lanyard_hole)
from params import MIN_PLATE, CaseParams, back_window, derived

sys.dont_write_bytecode = True

BIG = 500.0
EPS = 0.01


@dataclass(frozen=True)
class Zones:
    zs: float   # outer seam: where the front skirt edge meets the shell shoulder
    zg: float   # height of the snap bead / groove apex


def zones(p: CaseParams) -> Zones:
    d = derived(p)
    zs = d.front_t + p.skirt_len
    return Zones(zs=zs, zg=zs - 1.2)


def _edge_insets(p: CaseParams, T: float):
    """[(inset, z)] for the outer body's side profile, bottom to top."""
    if p.edge == "P1":
        c = p.chamfer
        return [(c, 0.0), (0.0, c), (0.0, T - c), (c, T)]
    r = p.round_r
    steps = 8
    bottom = []
    for i in range(steps + 1):
        z = r * i / steps
        bottom.append((r - math.sqrt(max(r * r - (r - z) ** 2, 0.0)), z))
    top = [(inset, T - z) for inset, z in reversed(bottom)]
    return bottom + top


def outer_body(p: CaseParams, name: str):
    """Full-thickness outer solid with the chosen edge profile front and back."""
    T = derived(p).T
    rings = [(case_outline(p, inset), z) for inset, z in _edge_insets(p, T)]
    return m.loft(rings, name)


def _clip_z(obj, z0, z1):
    return m.boolean(obj, m.box((-BIG, -BIG, z0), (BIG, BIG, z1), "clip"), "INTERSECT")


def rounded_rect(cx, cy, w, h, r, grow=0.0, per_corner=12):
    """Rounded-rectangle polygon (CCW), optionally grown by `grow` on every side.
    The vertex count never changes, so rings of different `grow` can be lofted."""
    hw, hh, rr = w / 2 + grow, h / 2 + grow, r + grow
    pts = []
    for (ox, oy, a0) in ((hw - rr, hh - rr, 0.0), (-(hw - rr), hh - rr, 90.0),
                         (-(hw - rr), -(hh - rr), 180.0), (hw - rr, -(hh - rr), 270.0)):
        for i in range(per_corner + 1):
            a = math.radians(a0 + 90.0 * i / per_corner)
            pts.append((cx + ox + rr * math.cos(a), cy + oy + rr * math.sin(a)))
    return pts


def _axis_poly(base, u, a0, a1, half_w):
    """Rectangle in the XY plane along a button axis, from a0 to a1 along it, half_w each side."""
    nx, ny = -u[1], u[0]
    return [(base[0] + u[0] * a + nx * s, base[1] + u[1] * a + ny * s)
            for a, s in ((a0, -half_w), (a1, -half_w), (a1, half_w), (a0, half_w))]


def _axis_cylinder(base, u, a0, a1, r, zc, name):
    """Round bar along a button axis (in the XY plane) at height zc, from a0 to a1 along it."""
    from mathutils import Matrix
    obj = m.cylinder(0.0, 0.0, r, a0, a1, name, segments=64)
    ang = math.atan2(u[1], u[0])
    m.transform(obj, Matrix.Translation((base[0], base[1], zc))
                @ Matrix.Rotation(ang, 4, "Z") @ Matrix.Rotation(math.pi / 2, 4, "Y"))
    return obj


def _button_window_prisms(p: CaseParams, z_lo, z_hi):
    """Round holes through the wall for each button's push-pin, plus a wider round
    pocket that holds the pin's flange. Clipped to [z_lo, z_hi]."""
    d = derived(p)
    rw = p.button_w / 2 + p.clearance
    zc = d.front_t + p.button_z
    ex = p.pin_flange_extra
    out = []
    for (base, u) in button_axes(p):
        length = 2 * d.outer_r
        pockets = [(0.0, length, rw, "button_window"),
                   (p.button_protrusion - p.pin_travel_in,
                    p.button_protrusion + p.pin_flange_len + p.pin_travel_out,
                    rw + ex, "pin_pocket")]
        for a0, a1, r, name in pockets:
            if zc + r < z_lo or zc - r > z_hi:
                continue  # this part never reaches the hole
            cyl = _axis_cylinder(base, u, a0, a1, r, zc, name)
            cyl = m.boolean(cyl, m.box((-BIG, -BIG, z_lo), (BIG, BIG, z_hi), "clip"), "INTERSECT")
            if name == "pin_pocket":
                # The pocket face is square to the button axis, but the wall is not, so one edge
                # of the pocket would break through the outside. Keep a skin of MIN_PLATE.
                skin = m.prism(case_outline(p, MIN_PLATE), z_lo - 1.0, z_hi + 1.0, "skin")
                cyl = m.boolean(cyl, skin, "INTERSECT")
            out.append(cyl)
    return out


def _pry_notch(p: CaseParams, z0, z1):
    _, y_bot = case_bounds(p)
    return m.box((-4.0, y_bot - 1.0, z0), (4.0, y_bot + p.pry_depth, z1), "pry_notch")


def lanyard_z(p: CaseParams) -> float:
    """Hole height: centred in the case thickness, but kept at least 0.2 mm above the snap seam."""
    return max(derived(p).T / 2, zones(p).zs + p.lanyard_d / 2 + 0.2)


def _lanyard(p: CaseParams):
    """Hole across the bottom-right corner, above the snap seam so it only cuts the shell."""
    if p.lanyard_d <= 0 or p.shape != "D2":
        return None   # only the D2 has the corner hole so far
    _, pt, t = lanyard_hole(p)
    d = derived(p)
    zc = lanyard_z(p)
    return _axis_cylinder(pt, t, -40.0, 40.0, p.lanyard_d / 2, zc, "lanyard")


def build_shell(p: CaseParams):
    """Back shell: walls + back plate, holding the watch from behind."""
    d = derived(p)
    z = zones(p)
    shell = _clip_z(outer_body(p, "shell"), d.front_t, d.T + 1.0)

    if p.closure == "snap":
        gap = p.skirt_t + p.fit_gap
        outline_fn = lambda inset: case_outline(p, inset)  # noqa: E731
        rebate = m.ring_tube([(-1.0, d.front_t - EPS), (gap, d.front_t - EPS),
                              (gap, z.zs), (-1.0, z.zs)], outline_fn, "rebate")
        shell = m.boolean(shell, rebate, "DIFFERENCE")
        # Apex sits fit_gap beyond the bead apex (skirt_t + bead_h); half-height gets
        # fit_gap of slack in Z. Flanks stay steeper than 45 degrees.
        g = p.bead_h + p.fit_gap
        groove = m.ring_tube([(gap - EPS, z.zg - g), (gap + p.bead_h, z.zg), (gap - EPS, z.zg + g)],
                             outline_fn, "groove")
        shell = m.boolean(shell, groove, "DIFFERENCE")
        shell = m.boolean(shell, _pry_notch(p, z.zs - EPS, z.zs + 1.0), "DIFFERENCE")

    for i, part in enumerate(cavity_parts(p)):
        shell = m.boolean(shell, m.prism(part, d.front_t - EPS, d.cavity_z1, f"cavity{i}"),
                          "DIFFERENCE")
    for window in _button_window_prisms(p, d.front_t - EPS, d.cavity_z1):
        shell = m.boolean(shell, window, "DIFFERENCE")

    bw = back_window(p)
    window = rounded_rect(bw.cx, bw.cy, bw.w, bw.h, bw.r)
    shell = m.boolean(shell, m.prism(window, d.cavity_z1 - EPS, d.T + 1.0, "back_window"), "DIFFERENCE")
    if p.back == "B3":  # sloped lead-in around the window, helps the puck find its place
        funnel = m.loft([(rounded_rect(bw.cx, bw.cy, bw.w, bw.h, bw.r), d.T - p.funnel),
                         (rounded_rect(bw.cx, bw.cy, bw.w, bw.h, bw.r, grow=p.funnel), d.T + EPS)], "funnel")
        shell = m.boolean(shell, funnel, "DIFFERENCE")
    lan = _lanyard(p)
    if lan is not None:
        shell = m.boolean(shell, lan, "DIFFERENCE")
    return shell


def build_front(p: CaseParams):
    """Front part: the face plate with the screen opening, plus the skirt that
    wraps over the shell's rebate and clicks into its groove."""
    d = derived(p)
    z = zones(p)
    front = _clip_z(outer_body(p, "front"), -1.0, z.zs)
    front = m.boolean(front, m.prism(case_outline(p, p.skirt_t), d.front_t, z.zs + 1.0, "hollow"),
                      "DIFFERENCE")
    if p.closure == "snap":
        outline_fn = lambda inset: case_outline(p, inset)  # noqa: E731
        bead = m.ring_tube([(p.skirt_t - EPS, z.zg - p.bead_h), (p.skirt_t + p.bead_h, z.zg),
                            (p.skirt_t - EPS, z.zg + p.bead_h)], outline_fn, "bead")
        front = m.boolean(front, bead, "UNION")
        front = m.boolean(front, _pry_notch(p, z.zs - 1.0, z.zs + EPS), "DIFFERENCE")

    r_open = p.screen_open_d / 2
    front = m.boolean(front, m.cylinder(p.screen_dx, 0, r_open, -1.0, d.front_t + 1.0, "screen"), "DIFFERENCE")
    bevel = min(0.3, d.front_t / 2)   # sloped edge so a finger glides onto the glass
    front = m.boolean(front, m.cone(p.screen_dx, 0, r_open + bevel, r_open, -EPS, bevel, "swipe_edge"), "DIFFERENCE")
    for window in _button_window_prisms(p, d.front_t, z.zs + EPS):
        front = m.boolean(front, window, "DIFFERENCE")
    return front



# ---- one-piece body + flat back cover (closures backsnap / backscrew / backboth) ----

def zones_back(p: CaseParams) -> Zones:
    """Seam height and bead height for the back cover. The seam sits above every hole and
    pocket (pockets top out at about z = 10.9) so no hole crosses it."""
    d = derived(p)
    zs = d.T - d.back_t - p.back_skirt_len
    return Zones(zs=zs, zg=zs + 0.6)


def _screws(p: CaseParams) -> bool:
    return p.closure in ("backscrew", "backboth")


def _snap(p: CaseParams) -> bool:
    from params import SNAP_CLOSURES
    return p.closure in SNAP_CLOSURES


def _button_keepouts(p: CaseParams, z0, z1):
    """Vertical cylinders on the outline where each button axis leaves the case. The rebate,
    groove and bead are left out inside them: near the buttons the wall is too thin for them,
    and the watch has to be lowered past its buttons through the open slot."""
    from outline import button_recesses
    out = []
    for (base, u), rec in zip(button_axes(p), button_recesses(p)):
        t = p.button_protrusion + rec
        out.append(m.cylinder(base[0] + u[0] * t, base[1] + u[1] * t, p.keepout_r, z0, z1, "keepout"))
    return out


def _minus_keepouts(p: CaseParams, obj, z0, z1):
    for k in _button_keepouts(p, z0, z1):
        obj = m.boolean(obj, k, "DIFFERENCE")
    return obj


def build_body(p: CaseParams):
    """Front plate + all four walls in one piece, open at the back. The watch and the
    push-pins drop in from the back; the buttons, lanyard hole and screen opening are all
    in this one piece, so no seam crosses them."""
    from outline import screw_points
    d = derived(p)
    z = zones_back(p)
    z_top = d.T - d.back_t      # the back cover's plate sits on the wall top here
    body = _clip_z(outer_body(p, "body"), -1.0, z_top)

    gap = p.skirt_t + p.fit_gap
    outline_fn = lambda inset: case_outline(p, inset)  # noqa: E731
    rebate = m.ring_tube([(-1.0, z.zs), (gap, z.zs), (gap, z_top + 1.0), (-1.0, z_top + 1.0)],
                         outline_fn, "rebate")
    rebate = _minus_keepouts(p, rebate, z.zs - 2.0, z_top + 3.0)
    body = m.boolean(body, rebate, "DIFFERENCE")
    if _snap(p):
        g = p.bead_h + p.fit_gap
        groove = m.ring_tube([(gap - EPS, z.zg - g), (gap + p.bead_h, z.zg), (gap - EPS, z.zg + g)],
                             outline_fn, "groove")
        groove = _minus_keepouts(p, groove, z.zs - 2.0, z_top + 3.0)
        body = m.boolean(body, groove, "DIFFERENCE")

    for i, part in enumerate(cavity_parts(p)):
        body = m.boolean(body, m.prism(part, d.front_t - EPS, z_top + 1.0, f"cavity{i}"), "DIFFERENCE")
    r_open = p.screen_open_d / 2
    body = m.boolean(body, m.cylinder(p.screen_dx, 0, r_open, -1.0, d.front_t + 1.0, "screen"), "DIFFERENCE")
    bevel = min(0.3, d.front_t / 2)
    body = m.boolean(body, m.cone(p.screen_dx, 0, r_open + bevel, r_open, -EPS, bevel, "swipe_edge"), "DIFFERENCE")
    for window in _button_window_prisms(p, d.front_t - EPS, z_top + 1.0):
        body = m.boolean(body, window, "DIFFERENCE")
    lan = _lanyard(p)
    if lan is not None:
        body = m.boolean(body, lan, "DIFFERENCE")
    if _screws(p):
        for (x, y, _r) in screw_points(p):
            body = m.boolean(body, m.cylinder(x, y, p.screw_pilot_d / 2, z_top - p.screw_depth, z_top + 1.0,
                                              "pilot"), "DIFFERENCE")
    return body


def build_cap(p: CaseParams):
    """Flat back cover with the charger window. Its rim wraps the outside of the body wall
    (so the outline stays smooth) and, for the snap versions, a bead clicks into the groove."""
    from outline import screw_points
    d = derived(p)
    z = zones_back(p)
    z_top = d.T - d.back_t
    cap = _clip_z(outer_body(p, "cap"), z.zs, d.T + 1.0)
    cap = m.boolean(cap, m.prism(case_outline(p, p.skirt_t), z.zs - 1.0, z_top, "hollow"), "DIFFERENCE")
    if _snap(p):
        outline_fn = lambda inset: case_outline(p, inset)  # noqa: E731
        bead = m.ring_tube([(p.skirt_t - EPS, z.zg - p.bead_h), (p.skirt_t + p.bead_h, z.zg),
                            (p.skirt_t - EPS, z.zg + p.bead_h)], outline_fn, "bead")
        cap = m.boolean(cap, bead, "UNION")
    cap = _minus_keepouts(p, cap, z.zs - 2.0, z_top)     # no rim over the button slots
    bw = back_window(p)
    window = rounded_rect(bw.cx, bw.cy, bw.w, bw.h, bw.r)
    cap = m.boolean(cap, m.prism(window, z_top - EPS, d.T + 1.0, "back_window"), "DIFFERENCE")
    if p.back == "B3":
        funnel = m.loft([(rounded_rect(bw.cx, bw.cy, bw.w, bw.h, bw.r), d.T - p.funnel),
                         (rounded_rect(bw.cx, bw.cy, bw.w, bw.h, bw.r, grow=p.funnel), d.T + EPS)], "funnel")
        cap = m.boolean(cap, funnel, "DIFFERENCE")
    if _screws(p):
        for (x, y, _r) in screw_points(p):
            cap = m.boolean(cap, m.cylinder(x, y, p.screw_hole_d / 2, z_top - 1.0, d.T + 1.0, "screw_hole"),
                            "DIFFERENCE")
            cap = m.boolean(cap, m.cone(x, y, p.screw_hole_d / 2, p.screw_hole_d / 2 + 1.0, d.T - 1.0, d.T + EPS,
                                        "countersink"), "DIFFERENCE")
    return cap


def build_fit_test(p: CaseParams):
    """Quick print: front + shell fused and cut just below the watch with the back plate
    removed. The watch goes in FACE FIRST, dropped in from the open side so the screen
    sits against the front plate and shows through its round opening. Checks the horn
    pockets, round pocket, button holes and the screen opening.

    The outline is symmetric about the watch centre (the full case is long below the
    watch for the lanyard and puck). One mark only: a small notch in the top edge marks
    the top end. The lanyard hole is in the bottom-right corner; there is no pry notch."""
    q = p.with_(closure="snap", case_h=0.0, pry_depth=0.0)
    d = derived(q)
    front, shell = build_front(q), build_shell(q)
    fit = m.boolean(front, shell, "UNION")
    fit = m.boolean(fit, m.box((-BIG, -BIG, -1.0), (BIG, BIG, d.cavity_z1), "fit_clip"), "INTERSECT")
    marker = m.box((-1.5, d.y_top - 1.2, -1.0), (1.5, d.y_top + 1.0, d.cavity_z1 + 1.0), "top_marker")
    fit = m.boolean(fit, marker, "DIFFERENCE")
    fit.name = "fittest"
    return fit


def build_dummy_watch(p: CaseParams):
    """Stand-in watch at the placeholder size (the cavity without clearance),
    for trying the snap fit and the look before the real watch arrives."""
    q = p.with_(clearance=0.0, horn_clear=0.0, body_extra_y=0.0,
                  body_extra_x=0.0, body_extra_far=0.0, button_horn_gap=0.0,
                  lower_button_extra=0.0, pin_slot_extra=0.0)
    parts = cavity_parts(q)
    body = m.prism(parts[0], 0.0, p.watch_t, "dummy_watch")
    for i, lug in enumerate(parts[1:5]):
        body = m.boolean(body, m.prism(lug, 0.0, p.watch_t, f"lug{i}"), "UNION")
    zc = p.button_z
    for i, button in enumerate(parts[5:]):
        body = m.boolean(body, m.prism(button, zc - p.button_h / 2, zc + p.button_h / 2,
                                       f"button{i}"), "UNION")
    return body


def build_pins(p: CaseParams):
    """One round push-pin per button, in assembled coordinates. It is dropped into the
    shell from the inside before the watch goes in: a round flange sits in the pocket
    (the watch and the frame keep it there, it cannot pop out) and a round stem runs
    through the wall to just under the outer surface."""
    d = derived(p)
    zc = d.front_t + p.button_z
    rs = p.button_w / 2 + p.clearance - p.fit_gap    # stem radius
    rf = rs + p.pin_flange_extra                       # flange radius
    tip = p.button_protrusion
    trim = m.prism(case_outline(p, p.pin_inset), -1.0, d.T + 1.0, "pin_trim")
    from outline import _inside, dist_to_outline
    surface = case_outline(p, 0.0)

    def on_end(x, y):   # outside the case, on the offset outline the pin was trimmed against
        return (not _inside((x, y), surface)) and abs(dist_to_outline((x, y), p) - abs(p.pin_inset)) < 0.03
    pins = []
    for i, (base, u) in enumerate(button_axes(p)):
        length = 2 * d.outer_r
        flange = _axis_cylinder(base, u, tip, tip + p.pin_flange_len, rf, zc, f"flange{i}")
        stem = _axis_cylinder(base, u, tip, length, rs, zc, f"stem{i}")
        pin = m.boolean(stem, flange, "UNION")
        pin = m.boolean(pin, m.duplicate(trim, f"trim{i}"), "INTERSECT")
        m.round_end(pin, on_end, p.pin_round)
        pin.name = f"pin{i}"
        pins.append(pin)
    _discard(trim)
    return pins


def _discard(obj):
    mesh = obj.data
    m.bpy.data.objects.remove(obj, do_unlink=True)
    m.bpy.data.meshes.remove(mesh)
