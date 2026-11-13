"""2D geometry of the case: outlines (at any inset), the watch cavity, buttons,
lanyard, and the checks that keep walls printable. No Blender here.

Coordinates: X = width (buttons on -X, see button_axes), Y = height (watch at top), origin at
the watch centre. Polygons are lists of (x, y), counter-clockwise.
"""
import math
import sys

from params import SNAP_CLOSURES, CaseParams, derived, validate

sys.dont_write_bytecode = True

MIN_PLATE_PIN = 0.8  # outer wall left in front of a push-pin pocket
SEG_PER_90 = 24
CIRCLE_SEGMENTS = 192


def case_bounds(p: CaseParams):
    """(y_top, y_bottom) of the outer outline."""
    # All shapes keep the same wall on top as on the sides ("thin equal walls").
    y_top = derived(p).y_top
    return y_top, (-y_top if p.case_h <= 0 else y_top - p.case_h)


def _arcs_raw(p: CaseParams):
    """The outline as CCW arcs (cx, cy, r, a0_deg, a1_deg); straight edges
    join consecutive arcs. Insetting shrinks every radius, centres stay."""
    R = derived(p).outer_r
    y_top, y_bot = case_bounds(p)
    if p.shape == "E3":
        return [(0.0, y_top - R, R, 0.0, 180.0),
                (0.0, y_bot + R, R, 180.0, 360.0)]
    if p.shape == "E1":
        rc = p.e1_corner_r
        return [(0.0, y_top - R, R, 0.0, 180.0),
                (-(R - rc), y_bot + rc, rc, 180.0, 270.0),
                ((R - rc), y_bot + rc, rc, 270.0, 360.0)]
    if p.shape == "D2":
        rc = p.d2_corner_r
        rl = p.d2_lanyard_corner_r   # bottom-right corner, where the lanyard hole is
        return [((R - rc), y_top - rc, rc, 0.0, 90.0),
                (-(R - rc), y_top - rc, rc, 90.0, 180.0),
                (-(R - rc), y_bot + rc, rc, 180.0, 270.0),
                ((R - rl), y_bot + rl, rl, 270.0, 360.0)]
    raise ValueError(f"unknown shape {p.shape!r}")


def _arcs(p: CaseParams):
    """Outline arcs, shifted sideways so the case stays centred on the pocket: the far
    (+X) pocket edge is moved by body_extra_far, so the whole case moves half of that."""
    dx = p.body_extra_far / 2
    return [(cx + dx, cy, r, a0, a1) for cx, cy, r, a0, a1 in _arcs_raw(p)]


def case_outline(p: CaseParams, inset: float = 0.0):
    """Outer outline moved inwards by `inset` (negative grows it). The vertex
    count depends only on the shape, so rings at different insets can be lofted."""
    pts = []
    for cx, cy, r, a0, a1 in _arcs(p):
        n = round(SEG_PER_90 * (a1 - a0) / 90.0)
        rr = max(r - inset, 1e-3)
        for i in range(n + 1):
            a = math.radians(a0 + (a1 - a0) * i / n)
            pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return pts


def _ellipse(rx, ry, n=CIRCLE_SEGMENTS, extra_plus_x=0.0):
    """Ellipse; the +X half can be pushed out by extra_plus_x (the side opposite the buttons)."""
    pts = []
    for i in range(n):
        a = 2 * math.pi * i / n
        x = rx * math.cos(a)
        pts.append((x + (extra_plus_x * math.cos(a) if math.cos(a) > 0 else 0.0), ry * math.sin(a)))
    return pts


def _circle(r, cx=0.0, cy=0.0, n=CIRCLE_SEGMENTS):
    return [(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n))
            for i in range(n)]


def _horn(p: CaseParams, sx: float, sy: float):
    """One horn pocket (sx, sy = +-1 pick the corner). Inside edge parallel to the
    centreline, then a straight run from the tip out to a point on the body circle
    horn_root_deg round from 3 o'clock. The real horn blends into the body with a
    smooth concave curve, which always lies inside this straight run, so the pocket
    holds it with room to spare. Reaches into the body circle so it merges with it."""
    hc = p.horn_clear
    xi = p.horn_gap / 2 - hc
    xt = p.horn_tip_outer / 2 + hc
    yt = p.lug_extent + hc
    rc = p.body_d / 2 + p.clearance
    a = math.radians(p.horn_root_deg)
    px, py = rc * math.cos(a), rc * math.sin(a)
    y0 = 10.0
    pts = [(sx * xi, sy * y0), (sx * px, sy * y0), (sx * px, sy * py),
           (sx * xt, sy * yt), (sx * xi, sy * yt)]
    return pts if sx * sy > 0 else list(reversed(pts))


def horn_pockets(p: CaseParams):
    return [_horn(p, sx, sy) for sy in (1, -1) for sx in (1, -1)]


def button_axes(p: CaseParams):
    """[(base_point, unit_direction)] for each button, base on the body circle.

    The buttons are on the -X side. Blender's axes are right-handed and z runs from the
    front face into the case, so someone looking at the screen sees +X on their LEFT; the
    buttons (on the right of the watch when you look at the screen) therefore need -X.
    Check: print the fit test plate-down and look into the open side; buttons are on the
    left, notch at the top."""
    r = p.body_d / 2
    out = []
    for deg in p.button_angles_deg:
        a = math.radians(deg)
        u = (-math.cos(a), math.sin(a))
        out.append(((r * u[0], r * u[1]), u))
    return out


def _button_pocket(p: CaseParams, base, u):
    r0 = -1.0
    r1 = p.button_protrusion + p.clearance
    hw = p.button_w / 2 + p.clearance + p.pin_slot_extra
    n = (-u[1], u[0])            # u points to -X, so n points DOWN for the upper button and UP for the lower one
    lo, hi = -hw, hw
    if u[1] >= 0:                # upper button: extra room toward the top horn (the -n side)
        lo -= p.button_horn_gap
    else:                        # lower button: extra room toward the bottom horn (the +n side)
        hi += p.button_horn_gap + p.lower_button_extra
    pts = []
    for along, side in ((r0, lo), (r1, lo), (r1, hi), (r0, hi)):
        pts.append((base[0] + u[0] * along + n[0] * side,
                    base[1] + u[1] * along + n[1] * side))
    return pts


def cavity_parts(p: CaseParams):
    """The watch cavity as overlapping polygons (body, lug pockets, button
    pockets). Their union is the cavity; each is extruded and subtracted."""
    return [_ellipse(p.body_d / 2 + p.clearance + p.body_extra_x, p.body_d / 2 + p.clearance + p.body_extra_y,
                      extra_plus_x=p.body_extra_far)] + horn_pockets(p) \
        + [_button_pocket(p, b, u) for b, u in button_axes(p)]


def _walled_parts(p: CaseParams):
    # Button pockets open into windows on purpose, so they are not walls.
    return cavity_parts(p)[:5]


def _inside(pt, poly):
    x, y = pt
    inside = False
    for i in range(len(poly)):
        (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % len(poly)]
        if (y0 > y) != (y1 > y) and x < x0 + (y - y0) * (x1 - x0) / (y1 - y0):
            inside = not inside
    return inside


def button_recesses(p: CaseParams) -> list:
    """For each button: how far its tip sits inside the outer surface, measured
    along the button's own axis (the depth a finger has to reach)."""
    outline = case_outline(p)
    out = []
    for base, u in button_axes(p):
        t = p.button_protrusion
        while t < 40 and _inside((base[0] + u[0] * t, base[1] + u[1] * t), outline):
            t += 0.005
        out.append(t - p.button_protrusion)
    return out


def button_recess(p: CaseParams) -> float:
    """The deepest button recess (see button_recesses)."""
    r = button_recesses(p)
    return max(r) if r else 0.0


def lanyard_hole(p: CaseParams):
    """The lanyard hole runs across the bottom-right corner (looking into the open side,
    buttons on the left), at right angles to the corner's diagonal, so a cord lies flat.
    Returns (corner_tip, point_on_axis, axis_direction)."""
    d = (math.sqrt(0.5), -math.sqrt(0.5))
    t = (math.sqrt(0.5), math.sqrt(0.5))
    outline = case_outline(p)
    tip = max(outline, key=lambda v: v[0] * d[0] + v[1] * d[1])
    point = (tip[0] - p.lanyard_k * d[0], tip[1] - p.lanyard_k * d[1])
    return tip, point, t


def lanyard_clearance(p: CaseParams) -> float:
    """Closest the hole axis comes to the watch pocket (the hole itself has radius lanyard_d / 2)."""
    _, pt, t = lanyard_hole(p)
    best = 1e9
    for i in range(-80, 81):
        s = i * 0.5
        q = (pt[0] + s * t[0], pt[1] + s * t[1])
        best = min(best, min(_dist_to_poly(q, part) for part in _walled_parts(p)))
    return best


def _seg_dist(pt, a, b):
    ax, ay = a
    bx, by = b
    px, py = pt
    dx, dy = bx - ax, by - ay
    L2 = dx * dx + dy * dy
    t = 0.0 if L2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / L2))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def _dist_to_poly(pt, poly):
    return min(_seg_dist(pt, poly[i], poly[(i + 1) % len(poly)]) for i in range(len(poly)))


def _arc_dist(pt, cx, cy, r, a0, a1):
    px, py = pt[0] - cx, pt[1] - cy
    ang = math.degrees(math.atan2(py, px)) % 360.0
    lo, hi = a0 % 360.0, a1 % 360.0 if a1 % 360.0 else 360.0
    if lo <= ang <= hi or (hi == 360.0 and ang == 0.0):
        return abs(math.hypot(px, py) - r)
    ends = [(cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a))) for a in (a0, a1)]
    return min(math.hypot(pt[0] - x, pt[1] - y) for x, y in ends)


def dist_to_outline(pt, p: CaseParams) -> float:
    """Exact distance from a point to the outer outline (true arcs, not chords)."""
    arcs = _arcs(p)
    best = min(_arc_dist(pt, *a) for a in arcs)
    for i, (cx, cy, r, _, a1) in enumerate(arcs):
        nx, ny, nr, na0, _ = arcs[(i + 1) % len(arcs)]
        a = (cx + r * math.cos(math.radians(a1)), cy + r * math.sin(math.radians(a1)))
        b = (nx + nr * math.cos(math.radians(na0)), ny + nr * math.sin(math.radians(na0)))
        best = min(best, _seg_dist(pt, a, b))
    return best


def _free_radius(pt, p: CaseParams, avoid=()):
    """Radius of the biggest circle centred on pt that stays in the material: clear of the
    outer edge, every pocket/hole outline and the extra circles in `avoid` [(x, y, r)]."""
    r = dist_to_outline(pt, p)
    for part in cavity_parts(p):
        d = _dist_to_poly(pt, part)
        r = min(r, -d if _inside(pt, part) else d)
    for x, y, rr in avoid:
        r = min(r, math.hypot(pt[0] - x, pt[1] - y) - rr)
    return r


def screw_points(p: CaseParams):
    """[(x, y, free_radius)] for the three corners that do not carry the lanyard hole
    (top-left, top-right, bottom-left, looking into the open side). Each is the spot with
    the most material around it."""
    y_top, y_bot = case_bounds(p)
    half = derived(p).outer_r
    dx = p.body_extra_far / 2
    corners = [(-1, 1), (1, 1), (-1, -1)]
    out = []
    for sx, sy in corners:
        best = None
        xr = [half * (0.45 + 0.01 * i) for i in range(0, 52)]
        yr = [(y_top if sy > 0 else -y_bot * 1.0) * (0.45 + 0.01 * i) for i in range(0, 52)]
        for x in xr:
            for y in yr:
                pt = (sx * x + dx, sy * y)
                r = _free_radius(pt, p)
                if best is None or r > best[2]:
                    best = (pt[0], pt[1], r)
        out.append(best)
    return out


def min_wall(p: CaseParams) -> float:
    """Thinnest wall between the watch pocket (body + lugs) and the outside."""
    return min(dist_to_outline(v, p) for part in _walled_parts(p) for v in part)


def check(p: CaseParams) -> list:
    issues = validate(p)
    if issues:
        return issues
    w = min_wall(p)
    if w < p.wall_min - 1e-6:
        issues.append(f"wall {w:.2f} mm is thinner than wall_min {p.wall_min} mm")
    if p.closure in SNAP_CLOSURES:
        from params import MIN_PLATE, groove_depth
        inner = w - groove_depth(p)
        if inner < MIN_PLATE - 1e-6:
            issues.append(f"snap groove/rebate leaves only {inner:.2f} mm of inner wall "
                          f"(needs {MIN_PLATE}); thinner skirt_t or thicker walls")
    need = p.pin_flange_len + p.pin_travel_out + MIN_PLATE_PIN
    for deg, rec in zip(p.button_angles_deg, button_recesses(p)):
        if rec < need - 1e-6:
            issues.append(f"button at {deg:+.0f} deg is only {rec:.2f} mm below the surface; a push-pin "
                          f"needs {need:.2f} mm (flange + travel + 0.8 mm outer wall)")

    # push-pin flange: its rim must keep a printable skin to the outside (the pocket face is
    # square to the button axis, the wall is not, so one edge of the flange sits closest)
    rf = p.button_w / 2 + p.clearance - p.fit_gap + p.pin_flange_extra
    for deg, (base, u) in zip(p.button_angles_deg, button_axes(p)):
        n = (-u[1], u[0])
        a = p.button_protrusion + p.pin_flange_len
        worst = min(dist_to_outline((base[0] + u[0] * a + n[0] * rf * math.cos(math.radians(k)),
                                     base[1] + u[1] * a + n[1] * rf * math.cos(math.radians(k))), p)
                    for k in range(0, 360, 10))
        if worst < MIN_PLATE_PIN - 1e-6:
            issues.append(f"push-pin flange at {deg:+.0f} deg comes within {worst:.2f} mm of the outside "
                          f"(needs {MIN_PLATE_PIN} mm); smaller flange or thinner flange_len")
    r = p.lanyard_d / 2
    if p.lanyard_d > 0 and p.shape == "D2":   # the other shapes get their hole later
        if lanyard_clearance(p) < r + p.lanyard_wall - 1e-6:
            issues.append(f"lanyard hole has only {lanyard_clearance(p) - r:.2f} mm to the watch pocket "
                          f"(needs {p.lanyard_wall} mm); move it toward the corner, make the corner squarer, or shrink it")
        if p.lanyard_k < r + p.lanyard_wall - 1e-6:
            issues.append("lanyard hole is too close to the outer corner")
    return issues
