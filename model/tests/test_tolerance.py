import unittest

import mesh_ops as m
import tolerance_test as t


class TestToleranceTest(unittest.TestCase):
    def setUp(self):
        m.clear_scene()
        self.obj = t.build_tolerance_test()

    def test_manifold_and_flat_on_bed(self):
        self.assertTrue(m.is_manifold(self.obj))
        (_, _, z0), _ = m.bbox(self.obj)
        self.assertAlmostEqual(z0, 0.0, delta=0.001)

    def test_holes_grow_by_the_listed_gaps(self):
        # A hole is empty at its edge minus a hair, and solid just outside it.
        for i, gap in enumerate(t.GAPS):
            cx = t.hole_x(i)
            r = t.PEG_D / 2 + gap
            z = t.PLATE_T / 2
            self.assertFalse(m.contains(self.obj, (cx + r - 0.02, t.PLATE_Y, z)), (i, gap))
            self.assertTrue(m.contains(self.obj, (cx + r + 0.02, t.PLATE_Y, z)), (i, gap))

    def test_pegs_have_nominal_diameter_and_ascending_heights(self):
        for i in range(len(t.GAPS)):
            cx = t.hole_x(i)
            h = t.peg_height(i)
            self.assertTrue(m.contains(self.obj, (cx + t.PEG_D / 2 - 0.02, t.PEGS_Y, h - 0.5)), i)
            self.assertFalse(m.contains(self.obj, (cx + t.PEG_D / 2 + 0.02, t.PEGS_Y, h - 0.5)), i)
            self.assertFalse(m.contains(self.obj, (cx, t.PEGS_Y, h + 0.5)), i)
        heights = [t.peg_height(i) for i in range(len(t.GAPS))]
        self.assertEqual(heights, sorted(set(heights)))

    def test_fits_the_ender_bed(self):
        (x0, y0, _), (x1, y1, z1) = m.bbox(self.obj)
        self.assertLess(x1 - x0, 200)
        self.assertLess(y1 - y0, 200)
        self.assertLess(z1, 30)
