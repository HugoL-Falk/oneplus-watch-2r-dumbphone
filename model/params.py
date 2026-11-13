"""Every number that shapes the OnePlus Watch 2R pocket case.

Units are millimetres. Values marked `# PLACEHOLDER: measure` come from
published specs or guesses; replace them with calipers once the watch arrives.
"""
import sys
import math
from dataclasses import dataclass, fields, replace

sys.dont_write_bytecode = True

SHAPES = ("E1", "E3", "D2")
CLOSURES = ("snap", "backsnap", "backscrew", "backboth", "screws", "slide")
SNAP_CLOSURES = ("snap", "backsnap", "backboth")   # closures that use a bead and a groove
BACK_CLOSURES = ("backsnap", "backscrew", "backboth")   # one-piece body + flat back cover
SCREENS = ("raised", "flush")
BACKS = ("B1", "B3")
EDGES = ("P1", "P2")
MIN_PLATE = 0.8  # two 0.4 mm lines
MIN_FRONT = 0.4  # front plate over the bezel: two 0.2 mm layers
SNAP_MIN_INTERFERENCE = 0.15  # bead_h - fit_gap: how far the bead hooks into the groove

PLACEHOLDERS = ()
OPEN_ITEMS = (
    "Charger puck corners measured (r 6.75 mm); a smaller off-brand 4-pin charger would change the back window.",
    "Push-pins and the snap fit are untested on the printer.",
)


@dataclass(frozen=True)
class CaseParams:
    # --- variant keys ---
    shape: str = "E3"
    closure: str = "snap"
    screen: str = "raised"
    back: str = "B1"
    edge: str = "P1"

    # --- the watch (measured with calipers 2026-11-08, see knowledge/watch-measure.html) ---
    body_d: float = 47.5              # Z3 (re-measured 2026-11-08; bezel Z1 is 47.5 too, never wider)
    lug_extent: float = 25.5          # B/2: centre -> horn tip (y)
    horn_gap: float = 22.0            # H3/H4: gap between the two horns of one end (inside to inside, parallel)
    horn_tip_outer: float = 26.0      # H1: across both horns at the tip, outside to outside
    horn_root_outer: float = 33.0     # H2 (no longer drives the shape, see horn_root_deg)
    horn_clear: float = 0.5           # extra room around the horns: 0.75 was a bit too loose, 0.25 caught
    body_extra_x: float = 0.0         # round pocket wider (+) on BOTH sides; +0.2 each was too loose
    body_extra_far: float = -0.2      # far side (opposite the buttons): pocket edge moves by this much, negative = tighter. The outline follows, so the case is 53.0 wide
    button_horn_gap: float = 0.5      # button pocket is this much deeper on the side toward the nearest horn
    lower_button_extra: float = 0.2   # and this much more for the lower button, toward the lower horn
    body_extra_y: float = -0.5        # round pocket is this much longer (+) or shorter (-) at each of the top and bottom ends; +0.5 was too loose, so -0.5
    horn_root_deg: float = 38.0       # the horns flow into the body tangentially; the pocket ends this far round the case from 3 o'clock
    watch_t: float = 12.0             # F: bezel face to flat back, EXCLUDING glass_rise and the sensor bump
    display_d: float = 37.0           # D: lit area
    glass_rise: float = 0.0           # G: glass is flush with the bezel
    button_angles_deg: tuple = (26.2, -30.3)  # S1=10.5 up / S2=12 down: measured 11.5 / 13, then 1 mm closer each after fit test 2026-11-12 (0 = 3 o'clock)
    button_protrusion: float = 2.5    # J
    button_w: float = 6.0             # L: along the case edge
    button_h: float = 6.0             # M: along Z
    button_z: float = 6.0             # K: bezel face to the button's centre (button spans 3..9 mm)
    pin_y: float = 15.25              # W1/W1b: charging pin row, above the watch centre (toward the top lugs)
    puck_w: float = 33.0              # Q1: charging puck width
    puck_h: float = 35.5              # Q2: puck height, pin edge to opposite edge
    puck_pin_edge: float = 5.0        # Q6: puck's pin row sits this far in from its top (pin) edge
    puck_corner_r: float = 6.75       # measured 2026-11-13 from the straight edge lengths (33 - 19.5)/2
    puck_clearance: float = 0.5       # slack around the puck in the back window, per side

    # --- the case ---
    screen_dx: float = -0.2           # screen opening centre moved sideways; negative = toward the buttons (the watch rests that way, fit test 2026-11-13)
    screen_open_d: float = 41.0       # lit area is 37.0; flat glass is 44. 41 leaves the edge swipes free (was 38)
    clearance: float = 0.25           # watch <-> case, per side
    fit_gap: float = 0.25             # part <-> part; tolerance test 2026-11-05: 4th hole (0.25) fit with light friction
    wall_min: float = 2.0
    back_t: float = 1.4
    raised_lip: float = 0.4           # lip above the glass (glass is flush with the bezel); plate must be >= MIN_FRONT
    case_h: float = 0.0               # 0 = symmetric about the watch centre (y_top above, y_top below)
    chamfer: float = 1.0              # P1
    round_r: float = 2.0              # P2
    skirt_t: float = 1.1
    skirt_len: float = 4.0
    bead_h: float = 0.45              # snap bead height; must exceed fit_gap by SNAP_MIN_INTERFERENCE
    pin_flange_len: float = 0.7       # push-pin: retention flange thickness along the button axis
    pin_flange_extra: float = 0.6     # flange is this much wider/taller than the window, per side
    pin_slot_extra: float = 0.6       # the cavity slot at each button is this much wider (per side) than the button, so the flange can be dropped in from the open side
    pin_travel_in: float = 0.6        # room for the pin to move toward the watch (button travel)
    pin_travel_out: float = 0.4       # room before the flange hits the pocket shoulder
    pin_round: float = 0.6            # rounding of the pin end where you press it
    pin_inset: float = -0.8           # pin end sits this far below the outer surface; negative = sticks out (0.8 mm, so it can be pressed; button travel is 0.6)
    keepout_r: float = 9.0            # no rebate/groove/rim within this radius of where a button axis leaves the case
    back_skirt_len: float = 1.9       # back cover: how far its rim wraps down the outside of the body (seam height)
    screw_hole_d: float = 2.4         # clearance hole in the back cover for an M2 self-tapping screw
    screw_pilot_d: float = 1.7        # pilot hole in the body corner post
    screw_depth: float = 7.0          # pilot hole depth into the body
    pry_depth: float = 1.2            # pry notch depth from the bottom edge (snap only)
    lanyard_d: float = 5.0
    lanyard_k: float = 4.4            # lanyard hole axis sits this deep from the outer corner tip
    lanyard_wall: float = 1.5         # min wall between the lanyard hole and the watch pocket
    d2_lanyard_corner_r: float = 6.0  # D2: the bottom-right corner is squarer, to leave room for the hole
    back_lip_min: float = 2.0
    funnel: float = 1.0               # B3
    e1_corner_r: float = 10.0
    d2_corner_r: float = 11.0

    def with_(self, **changes) -> "CaseParams":
        return replace(self, **changes)


@dataclass(frozen=True)
class Derived:
    front_t: float
    back_t: float
    cavity_z0: float
    cavity_z1: float
    T: float
    outer_r: float   # half the case width
    y_top: float     # top edge above the watch centre
    side_wall: float


def groove_depth(p: CaseParams) -> float:
    """How far the snap groove cuts into the wall from the outer surface."""
    return p.skirt_t + p.fit_gap + p.bead_h


@dataclass(frozen=True)
class BackWindow:
    cx: float
    cy: float
    w: float
    h: float
    r: float


def back_window(p: CaseParams) -> BackWindow:
    """Rounded rectangle cut in the back plate so the puck face reaches the watch back.
    The puck's pins line up with the watch's pins, which fixes the puck's position."""
    puck_cy = p.pin_y + p.puck_pin_edge - p.puck_h / 2
    return BackWindow(cx=0.0, cy=puck_cy, w=p.puck_w + 2 * p.puck_clearance,
                      h=p.puck_h + 2 * p.puck_clearance, r=p.puck_corner_r + p.puck_clearance)


def horn_r_top(p, horn_r, wall):
    # Straight top edge: it only has to clear the horn tip's height, not its radius.
    return max(p.lug_extent + p.horn_clear + wall, p.body_d / 2 + p.clearance + wall)


def derived(p: CaseParams) -> Derived:
    front_t = p.glass_rise + (p.raised_lip if p.screen == "raised" else 0.0)
    # A snap closure cuts a rebate into the wall, so the wall must also hold
    # the skirt, its gap, and a printable inner wall.
    wall = p.wall_min
    if p.closure in SNAP_CLOSURES or p.closure in BACK_CLOSURES:   # same outline for every back-cover variant
        wall = max(wall, groove_depth(p) + MIN_PLATE)
    side_wall = max(wall, p.button_protrusion)
    horn_r = math.hypot(p.horn_tip_outer / 2 + p.horn_clear + abs(p.body_extra_far) / 2, p.lug_extent + p.horn_clear)
    half_body = p.body_d / 2 + p.clearance + p.body_extra_x + p.body_extra_far / 2 + side_wall
    if p.shape == "D2":   # squarer top: the width follows the body, the top edge follows the horn tips
        outer_r, y_top = half_body, horn_r_top(p, horn_r, wall)
    else:                 # round top is concentric with the watch, so one radius covers both
        outer_r = max(half_body, horn_r + wall)
        y_top = outer_r
    cavity_z0 = front_t
    cavity_z1 = front_t + p.watch_t + p.clearance
    return Derived(front_t=front_t, back_t=p.back_t, cavity_z0=cavity_z0,
                   cavity_z1=cavity_z1, T=cavity_z1 + p.back_t,
                   outer_r=outer_r, y_top=y_top, side_wall=side_wall)


def validate(p: CaseParams) -> list:
    issues = []
    for key, allowed in (("shape", SHAPES), ("closure", CLOSURES),
                         ("screen", SCREENS), ("back", BACKS), ("edge", EDGES)):
        if getattr(p, key) not in allowed:
            issues.append(f"{key} must be one of {allowed}, got {getattr(p, key)!r}")
    d = derived(p)
    if d.front_t < MIN_FRONT:
        issues.append(f"front plate {d.front_t:.2f} mm is thinner than {MIN_FRONT} mm")
    if p.back_t < MIN_PLATE:
        issues.append(f"back plate {p.back_t:.2f} mm is thinner than {MIN_PLATE} mm")
    bw = back_window(p)
    cavity_r = p.body_d / 2 + p.clearance
    lips = {"top": cavity_r - (bw.cy + bw.h / 2), "bottom": cavity_r + (bw.cy - bw.h / 2),
            "side": cavity_r - (abs(bw.cx) + bw.w / 2)}
    for where, lip in lips.items():
        if lip < p.back_lip_min:
            issues.append(f"back lip at the {where} is {lip:.2f} mm, under {p.back_lip_min} mm; "
                          "the watch could fall through the back window")
    if p.screen_open_d < p.display_d:
        issues.append(f"screen opening {p.screen_open_d} mm covers the display ({p.display_d} mm)")
    if p.screen_open_d > p.body_d - 2 * MIN_PLATE:
        issues.append("screen opening is wider than the watch body; nothing holds the watch in")
    if p.closure in SNAP_CLOSURES and p.bead_h - p.fit_gap < SNAP_MIN_INTERFERENCE - 1e-9:
        issues.append(f"snap would not click: bead_h ({p.bead_h}) must be at least fit_gap "
                      f"({p.fit_gap}) + {SNAP_MIN_INTERFERENCE}; raise bead_h")
    if p.skirt_t < MIN_PLATE:
        issues.append(f"skirt {p.skirt_t} mm is thinner than {MIN_PLATE} mm")
    return issues


def field_names() -> list:
    return [f.name for f in fields(CaseParams)]
