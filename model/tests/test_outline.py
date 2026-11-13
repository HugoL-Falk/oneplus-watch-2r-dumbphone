import sys
import unittest

sys.dont_write_bytecode = True

from params import CaseParams, derived
from outline import (button_axes, button_recess, button_recesses, case_outline, check, min_wall)


def extent(poly):
    xs = [x for x, _ in poly]
    ys = [y for _, y in poly]
    return max(xs) - min(xs), max(ys) - min(ys)


class TestOutline(unittest.TestCase):
    def test_vertex_count_stable_under_inset(self):
        for shape in ("E1", "E3", "D2"):
            p = CaseParams().with_(shape=shape)
            self.assertEqual(len(case_outline(p, 0.0)), len(case_outline(p, 1.0)), shape)

    def test_e3_width_and_height(self):
        p = CaseParams()
        w, h = extent(case_outline(p))
        self.assertAlmostEqual(w, 2 * derived(p).outer_r, delta=1e-6)
        self.assertAlmostEqual(h, 2 * derived(p).y_top, delta=1e-6)  # symmetric by default

    def test_inset_shrinks_uniformly(self):
        p = CaseParams()
        w, _ = extent(case_outline(p, 1.0))
        self.assertAlmostEqual(w, 2 * derived(p).outer_r - 2.0, delta=1e-6)

    def test_min_wall_defaults(self):
        for shape in ("E1", "E3", "D2"):
            self.assertGreaterEqual(min_wall(CaseParams().with_(shape=shape)), 2.0 - 1e-6, shape)

    def test_min_wall_holds_for_enlarged_watch(self):
        for shape in ("E1", "E3", "D2"):
            p = CaseParams().with_(shape=shape, body_d=48.5, lug_extent=27.0)
            self.assertEqual(check(p), [], shape)

    def test_button_recess_reported(self):
        p = CaseParams().with_(button_angles_deg=(0.0,))
        expected = derived(p).outer_r - (p.body_d / 2 + p.button_protrusion)
        self.assertAlmostEqual(button_recess(p), expected, delta=0.15)  # case shifted 0.1 mm sideways
        self.assertGreater(button_recess(CaseParams()), 1.5)  # measured watch triggers the report warning

    def test_button_recess_per_button_follows_outline(self):
        p = CaseParams()
        upper, lower = button_recesses(p)  # E3: upper button under the concentric top arc
        self.assertAlmostEqual(upper, derived(p).outer_r - (p.body_d / 2 + p.button_protrusion), delta=0.15)  # the case is shifted 0.1 mm sideways
        self.assertGreaterEqual(lower, upper - 0.02)   # the lower button is never closer to the surface

    def test_pin_needs_enough_recess(self):
        self.assertEqual(check(CaseParams()), [])
        short = CaseParams().with_(button_angles_deg=(0.0,), lug_extent=22.0, horn_tip_outer=20.0, horn_root_outer=24.0)  # recess ~0.85 mm
        self.assertTrue(any("push-pin" in m for m in check(short)), check(short))

    def test_snap_rebate_leaves_inner_wall(self):
        for p in (CaseParams(), CaseParams().with_(skirt_t=1.8),
                  CaseParams().with_(lug_extent=27.0)):
            self.assertEqual(check(p), [])
            self.assertGreaterEqual(min_wall(p) - (p.skirt_t + p.fit_gap), 0.8 - 1e-6)

    def test_buttons_are_on_minus_x(self):
        # Printed plate-down and viewed into the open side, +X is on the right, so the
        # buttons (left when you look into the open side) must be at -X. the first print
        # 2026-11-12 proved it: buttons at +X came out on the right.
        for base, u in button_axes(CaseParams()):
            self.assertLess(base[0], 0)
            self.assertLess(u[0], 0)

    def test_d2_top_wall_above_horn_tips(self):
        from outline import case_bounds
        p = CaseParams().with_(shape="D2")
        self.assertAlmostEqual(case_bounds(p)[0], p.lug_extent + p.horn_clear + 2.6, delta=1e-9)  # wall above the horn tips

    def test_groove_leaves_printable_wall(self):
        from params import MIN_PLATE
        for q in (CaseParams(), CaseParams().with_(body_d=48.5, lug_extent=27.0),
                  CaseParams().with_(shape="D2", lug_extent=27.0)):
            groove_depth = q.skirt_t + q.fit_gap + q.bead_h
            self.assertEqual(check(q), [])
            self.assertGreaterEqual(min_wall(q) - groove_depth, MIN_PLATE - 1e-6)

    def test_lanyard_corner_has_room(self):
        from outline import lanyard_clearance
        p = CaseParams().with_(shape="D2")
        self.assertEqual([m for m in check(p) if "lanyard" in m], [])
        self.assertGreaterEqual(lanyard_clearance(p), p.lanyard_d / 2 + p.lanyard_wall - 1e-6)

    def test_lanyard_flags_a_round_corner(self):
        # With the default round corner (r 11) the hole would cut into the watch pocket.
        p = CaseParams().with_(shape="D2", d2_lanyard_corner_r=11.0)
        self.assertTrue(any("lanyard" in m for m in check(p)))


if __name__ == "__main__":
    unittest.main()
