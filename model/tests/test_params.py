import math
import sys
import unittest

sys.dont_write_bytecode = True

from params import CaseParams, derived, validate


class TestParams(unittest.TestCase):
    def test_defaults_valid(self):
        self.assertEqual(validate(CaseParams()), [])

    def test_derived_thickness(self):
        d = derived(CaseParams())
        self.assertAlmostEqual(d.front_t, 0.4, places=6)  # glass flush + 0.4 mm lip
        self.assertAlmostEqual(d.T, 14.05, places=6)  # 0.4 + 12.0 + 0.25 + 1.4
        self.assertAlmostEqual(d.outer_r, math.hypot(13 + CaseParams().horn_clear + abs(CaseParams().body_extra_far) / 2, 25.5 + CaseParams().horn_clear) + 2.6, places=6)  # outer horn tip + horn_clear, plus wall (groove 1.8 + 0.8)

    def test_flush_plate_too_thin_rejected(self):
        issues = validate(CaseParams().with_(screen="flush", glass_rise=0.3))
        self.assertTrue(any("front plate" in m for m in issues), issues)

    def test_back_window_keeps_lip(self):
        issues = validate(CaseParams().with_(puck_w=44.0))
        self.assertTrue(any("back lip" in m for m in issues), issues)

    def test_back_window_follows_the_puck_and_pins(self):
        from params import back_window
        p = CaseParams()
        w = back_window(p)
        self.assertAlmostEqual(w.w, 34.0, places=6)    # puck 33 + 2 * 0.5
        self.assertAlmostEqual(w.h, 36.5, places=6)    # puck 35.5 + 2 * 0.5
        # pins 15.25 above centre sit 5 mm below the puck's top edge -> puck centre 2.5 above centre
        self.assertAlmostEqual(w.cy, 2.5, places=6)
        self.assertAlmostEqual(w.cx, 0.0, places=6)

    def test_back_window_too_high_loses_the_top_lip(self):
        issues = validate(CaseParams().with_(pin_y=19.0))   # window top would be ~24.5 mm
        self.assertTrue(any("back lip" in m for m in issues), issues)

    def test_bad_variant_key(self):
        issues = validate(CaseParams().with_(shape="E2"))
        self.assertTrue(any("shape" in m for m in issues), issues)

    def test_screen_opening_covers_display(self):
        issues = validate(CaseParams().with_(screen_open_d=36.0))
        self.assertTrue(any("display" in m for m in issues), issues)

    def test_fit_gap_cannot_cancel_snap(self):
        issues = validate(CaseParams().with_(fit_gap=0.35))
        self.assertTrue(any("snap" in m for m in issues), issues)
        self.assertEqual(validate(CaseParams().with_(fit_gap=0.35, bead_h=0.55)), [])


if __name__ == "__main__":
    unittest.main()
