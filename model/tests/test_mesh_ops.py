import math
import unittest

import mesh_ops as m
from outline import case_outline
from params import CaseParams, derived


class TestMeshOps(unittest.TestCase):
    def setUp(self):
        m.clear_scene()
        self.p = CaseParams()
        self.R = derived(self.p).outer_r

    def test_loft_e3_prism(self):
        o = m.loft([(case_outline(self.p), 0.0), (case_outline(self.p), 15.0)], "prism")
        self.assertTrue(m.is_manifold(o))
        (x0, y0, z0), (x1, y1, z1) = m.bbox(o)
        self.assertAlmostEqual(x1 - x0, 2 * self.R, delta=0.01)
        self.assertAlmostEqual(y1 - y0, 2 * derived(self.p).y_top, delta=0.01)
        self.assertAlmostEqual(z1 - z0, 15.0, delta=0.01)
        self.assertGreater(m.volume(o), 0)

    def test_chamfer_loft(self):
        T, c = 15.0, 1.0
        plain = m.volume(m.loft([(case_outline(self.p), 0.0), (case_outline(self.p), T)], "a"))
        rings = [(case_outline(self.p, c), 0.0), (case_outline(self.p), c),
                 (case_outline(self.p), T - c), (case_outline(self.p, c), T)]
        ch = m.loft(rings, "b")
        self.assertTrue(m.is_manifold(ch))
        outline = case_outline(self.p)
        perimeter = sum(math.dist(outline[i], outline[(i + 1) % len(outline)])
                        for i in range(len(outline)))
        expected_removed = 2 * perimeter * c * c / 2  # two 45-degree edges
        self.assertAlmostEqual(plain - m.volume(ch), expected_removed,
                               delta=0.1 * expected_removed)

    def test_boolean_hole(self):
        o = m.loft([(case_outline(self.p), 0.0), (case_outline(self.p), 15.0)], "prism")
        o = m.boolean(o, m.cylinder(0, 0, 5, -1, 16, "hole"), "DIFFERENCE")
        self.assertTrue(m.is_manifold(o))
        self.assertFalse(m.contains(o, (0, 0, 7)))
        self.assertTrue(m.contains(o, (0, -25, 7)))  # solid below the hole

    def test_ring_tube_manifold(self):
        prof = [(0.0, 2.0), (0.5, 2.5), (0.0, 3.0)]
        o = m.ring_tube(prof, lambda inset: case_outline(self.p, inset), "tube")
        self.assertTrue(m.is_manifold(o))
        self.assertGreater(m.volume(o), 0)
