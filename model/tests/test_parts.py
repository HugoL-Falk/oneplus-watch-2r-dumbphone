import itertools
import unittest

import mesh_ops as m
from outline import button_axes, button_recesses, case_outline, lanyard_hole
from params import CaseParams, derived
from parts import build_body, build_cap, build_front, build_pins, build_shell, lanyard_z, zones, zones_back


def point_in_polygon(pt, poly):
    x, y = pt
    inside = False
    for i in range(len(poly)):
        (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % len(poly)]
        if (y0 > y) != (y1 > y) and x < x0 + (y - y0) * (x1 - x0) / (y1 - y0):
            inside = not inside
    return inside


# A point on the straight side wall, clear of the button windows, the pin pockets and both end arcs.
Y_WALL = -2


class TestShell(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        m.clear_scene()
        cls.p = CaseParams()
        cls.d = derived(cls.p)
        cls.shell = build_shell(cls.p)

    def test_shell_manifold_and_size(self):
        self.assertTrue(m.is_manifold(self.shell))
        (x0, _, z0), (x1, _, z1) = m.bbox(self.shell)
        self.assertAlmostEqual(z0, self.d.front_t, delta=0.01)
        self.assertAlmostEqual(z1, self.d.T, delta=0.01)
        self.assertAlmostEqual(x1 - x0, 2 * self.d.outer_r, delta=0.01)

    def test_shell_cavity_and_walls(self):
        self.assertFalse(m.contains(self.shell, (0, 0, self.d.front_t + 6)))
        self.assertTrue(m.contains(self.shell, (self.d.outer_r - 1.0, 0, self.d.front_t + 6)))

    def test_shell_button_window(self):
        z = self.d.front_t + self.p.button_z
        for (base, u), recess in zip(button_axes(self.p), button_recesses(self.p)):
            t = self.p.button_protrusion + recess - 0.3
            pt = (base[0] + u[0] * t, base[1] + u[1] * t, z)
            self.assertFalse(m.contains(self.shell, pt), pt)

    def test_shell_back_window(self):
        T = self.d.T
        self.assertFalse(m.contains(self.shell, (0, 0, T - 0.5)))
        self.assertTrue(m.contains(self.shell, (0, -self.p.body_d / 2 + 1.5, T - 0.5)))

    def test_back_window_is_the_puck_rectangle(self):
        from params import back_window
        w = back_window(self.p)
        T, z = self.d.T, self.d.T - 0.5
        inside = lambda x, y: not m.contains(self.shell, (x, y, z))  # noqa: E731
        self.assertTrue(inside(0, w.cy))                                   # centre
        self.assertTrue(inside(w.w / 2 - 0.3, w.cy))                       # right edge
        self.assertTrue(inside(-w.w / 2 + w.r, w.cy + w.h / 2 - 0.3))      # where the top edge meets the corner arc
        self.assertFalse(inside(w.w / 2 + 0.3, w.cy))                      # plate just outside
        self.assertFalse(inside(0, w.cy + w.h / 2 + 0.3))                  # plate above the window
        self.assertFalse(inside(0, w.cy - w.h / 2 - 0.3))                  # plate below the window
        self.assertFalse(inside(w.w / 2 - 0.1, w.cy + w.h / 2 - 0.1))      # the corner itself is rounded off

    def test_shell_lanyard(self):
        # A hole across the bottom-right corner, at mid height above the snap seam.
        p = CaseParams().with_(shape="D2")
        d = derived(p)
        shell = build_shell(p)
        _, pt, t = lanyard_hole(p)
        zc = lanyard_z(p)
        self.assertFalse(m.contains(shell, (pt[0], pt[1], zc)))
        self.assertFalse(m.contains(shell, (pt[0] + 4 * t[0], pt[1] + 4 * t[1], zc)))
        self.assertTrue(m.contains(shell, (pt[0], pt[1], d.T - 0.4)))    # solid above the hole
        self.assertTrue(m.contains(shell, (-pt[0], pt[1], zc)))          # no hole in the other corners

    def test_shell_snap_groove(self):
        z = zones(self.p)
        x_in = self.d.outer_r - (self.p.skirt_t + self.p.fit_gap + 0.3)
        self.assertFalse(m.contains(self.shell, (x_in, Y_WALL, z.zg)))         # groove
        self.assertTrue(m.contains(self.shell, (x_in, Y_WALL, z.zs + 1.0)))     # full wall above the seam
        self.assertFalse(m.contains(self.shell, (self.d.outer_r - 0.5, Y_WALL, self.d.front_t + 1)))  # rebate


    def test_groove_depth_is_bead_plus_gap(self):
        z = zones(self.p)
        depth = self.p.skirt_t + self.p.fit_gap + self.p.bead_h  # bead apex + fit gap
        y_top = self.d.outer_r
        self.assertFalse(m.contains(self.shell, (0, y_top - depth + 0.1, z.zg)))  # groove reaches here
        self.assertTrue(m.contains(self.shell, (0, y_top - depth - 0.1, z.zg)))   # and no deeper


class TestShellVariants(unittest.TestCase):
    def test_all_shell_variants_manifold(self):
        for shape, back, edge in itertools.product(("E1", "E3", "D2"), ("B1", "B3"), ("P1", "P2")):
            m.clear_scene()
            obj = build_shell(CaseParams().with_(shape=shape, back=back, edge=edge))
            self.assertTrue(m.is_manifold(obj), (shape, back, edge))


class TestFront(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        m.clear_scene()
        cls.p = CaseParams()
        cls.d = derived(cls.p)
        cls.front = build_front(cls.p)

    def test_front_manifold_and_size(self):
        self.assertTrue(m.is_manifold(self.front))
        (_, _, z0), (_, _, z1) = m.bbox(self.front)
        self.assertAlmostEqual(z0, 0.0, delta=0.01)
        self.assertAlmostEqual(z1, self.d.front_t + self.p.skirt_len, delta=0.01)

    def test_front_plate_thickness(self):
        self.assertAlmostEqual(derived(self.p).front_t, 0.4, places=6)
        self.assertAlmostEqual(derived(self.p.with_(glass_rise=1.0, screen="flush")).front_t, 1.0, places=6)
        self.assertFalse(m.contains(self.front, (0, self.p.display_d / 2 - 0.5, self.d.front_t / 2)))
        self.assertTrue(m.contains(self.front, (0, self.p.body_d / 2 - 1.0, self.d.front_t / 2)))

    def test_front_skirt(self):
        self.assertTrue(m.contains(self.front, (self.d.outer_r - self.p.skirt_t / 2, Y_WALL, self.d.front_t + 2)))
        self.assertFalse(m.contains(self.front, (0, Y_WALL, self.d.front_t + 2)))

    def test_front_bead(self):
        z = zones(self.p)
        x = self.d.outer_r - self.p.skirt_t - self.p.bead_h / 2
        self.assertTrue(m.contains(self.front, (x, Y_WALL, z.zg)))
        self.assertFalse(m.contains(self.front, (x, Y_WALL, z.zg + self.p.bead_h + 0.2)))


class TestAssembly(unittest.TestCase):
    def test_parts_do_not_collide(self):
        for shape in ("E3", "E1", "D2"):
            m.clear_scene()
            p = CaseParams().with_(shape=shape)
            front, shell = build_front(p), build_shell(p)
            both = m.boolean(m.duplicate(front, "a"), m.duplicate(shell, "b"), "INTERSECT")
            vol = m.volume(both) if len(both.data.polygons) else 0.0
            self.assertLess(vol, 0.05, shape)

    def test_flush_front_manifold(self):
        m.clear_scene()
        self.assertTrue(m.is_manifold(build_front(CaseParams().with_(screen="flush", edge="P2"))))


class TestPins(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        m.clear_scene()
        cls.p = CaseParams()
        cls.d = derived(cls.p)
        cls.pins = build_pins(cls.p)  # one object per button, assembled coordinates

    def test_one_manifold_pin_per_button(self):
        self.assertEqual(len(self.pins), len(self.p.button_angles_deg))
        for pin in self.pins:
            self.assertTrue(m.is_manifold(pin))

    def test_pin_touches_button_tip_and_stays_inside_outline(self):
        from outline import case_outline
        outline = case_outline(self.p, self.p.pin_inset - 0.05)   # pins end at the outline grown by the protrusion
        z = self.d.front_t + self.p.button_z
        for (base, u), pin in zip(button_axes(self.p), self.pins):
            tip = self.p.button_protrusion
            at = lambda a: (base[0] + u[0] * a, base[1] + u[1] * a, z)  # noqa: E731
            self.assertTrue(m.contains(pin, at(tip + 0.2)))        # flange starts at the tip
            self.assertFalse(m.contains(pin, at(tip - 0.3)))       # nothing inside the watch button
            for v in pin.data.vertices:                             # end follows the outer surface
                self.assertTrue(point_in_polygon((v.co.x, v.co.y), outline), tuple(v.co))
            surface = case_outline(self.p, 0.0)
            self.assertTrue(any(not point_in_polygon((v.co.x, v.co.y), surface) for v in pin.data.vertices))   # sticks out

    def test_pins_do_not_collide_with_case(self):
        shell = build_shell(self.p)
        front = build_front(self.p)
        for pin in self.pins:
            for part in (shell, front):
                both = m.boolean(m.duplicate(pin, "a"), m.duplicate(part, "b"), "INTERSECT")
                vol = m.volume(both) if len(both.data.polygons) else 0.0
                self.assertLess(vol, 0.05)

    def test_shell_has_pocket_for_pin_flange(self):
        shell = build_shell(self.p)
        z = self.d.front_t + self.p.button_z
        (base, u), = [button_axes(self.p)[1]]
        n = (-u[1], u[0])
        a = self.p.button_protrusion + 0.5
        t = self.p.button_w / 2 + 0.8   # inside the widened pocket, outside the plain window
        pt = (base[0] + u[0] * a + n[0] * t, base[1] + u[1] * a + n[1] * t, z)
        self.assertFalse(m.contains(shell, pt))


class TestPinInstall(unittest.TestCase):
    def test_pins_can_be_installed_from_the_open_side(self):
        """The pin is dropped into the empty cavity along Z (stem clear of the wall), then
        slid outward along its own axis until the flange sits in its pocket. Nothing may
        block that path. (A first version had a slot narrower than the flange, so the pin
        could not go in at all.)"""
        for shape in ("D2", "E3"):
            m.clear_scene()
            p = CaseParams().with_(shape=shape)
            shell = build_shell(p)
            for (base, u), pin in zip(button_axes(p), build_pins(p)):
                def blocked(a_shift, dz):
                    probe = m.duplicate(pin, "probe")
                    m.translate(probe, u[0] * a_shift, u[1] * a_shift, dz)
                    hit = m.boolean(probe, m.duplicate(shell, "s"), "INTERSECT")
                    return m.volume(hit) if len(hit.data.polygons) else 0.0
                for dz in (-12.0, -9.0, -6.0, -3.0, 0.0):          # drop in from the open front side
                    self.assertLess(blocked(-9.0, dz), 0.02, (shape, "drop", dz))
                for a_shift in (-9.0, -7.0, -5.0, -3.0, -2.0, -1.0, 0.0):   # slide outward
                    self.assertLess(blocked(a_shift, 0.0), 0.02, (shape, "slide", a_shift))


class TestBackCover(unittest.TestCase):
    CLOSURES = ("backsnap", "backscrew", "backboth")

    def test_body_and_cap_are_manifold_and_do_not_collide(self):
        for closure in self.CLOSURES:
            m.clear_scene()
            p = CaseParams().with_(shape="D2", closure=closure)
            body, cap = build_body(p), build_cap(p)
            self.assertTrue(m.is_manifold(body), closure)
            self.assertTrue(m.is_manifold(cap), closure)
            both = m.boolean(m.duplicate(body, "a"), m.duplicate(cap, "b"), "INTERSECT")
            vol = m.volume(both) if len(both.data.polygons) else 0.0
            self.assertLess(vol, 0.05, closure)

    def test_no_hole_crosses_the_seam(self):
        """Pin pockets and the lanyard hole must all end below the seam, so the back cover's
        rim never meets a hole (the reason for moving the closure to the back)."""
        p = CaseParams().with_(shape="D2", closure="backsnap")
        d = derived(p)
        zs = zones_back(p).zs
        self.assertLess(d.front_t + p.button_z + p.button_w / 2 + p.clearance + p.pin_flange_extra, zs)
        self.assertLess(lanyard_z(p) + p.lanyard_d / 2, zs + 1.0)   # hole top vs seam: at most just reaching the rim

    def test_pins_install_from_the_back(self):
        for closure in self.CLOSURES:
            m.clear_scene()
            p = CaseParams().with_(shape="D2", closure=closure)
            body = build_body(p)
            for (base, u), pin in zip(button_axes(p), build_pins(p)):
                def blocked(a_shift, dz):
                    probe = m.duplicate(pin, "probe")
                    m.translate(probe, u[0] * a_shift, u[1] * a_shift, dz)
                    hit = m.boolean(probe, m.duplicate(body, "s"), "INTERSECT")
                    return m.volume(hit) if len(hit.data.polygons) else 0.0
                for dz in (14.0, 10.0, 6.0, 3.0, 0.0):          # drop in from the open back
                    self.assertLess(blocked(-11.0, dz), 0.02, (closure, "drop", dz))
                for a_shift in (-11.0, -8.0, -5.0, -3.0, -1.0, 0.0):     # slide outward into the pocket
                    self.assertLess(blocked(a_shift, 0.0), 0.02, (closure, "slide", a_shift))

    def test_screws_have_holes_in_both_parts(self):
        from outline import screw_points
        m.clear_scene()
        p = CaseParams().with_(shape="D2", closure="backscrew")
        d = derived(p)
        body, cap = build_body(p), build_cap(p)
        pts = screw_points(p)
        self.assertEqual(len(pts), 3)
        for x, y, free in pts:
            self.assertGreater(free, p.screw_pilot_d / 2 + 1.2)                  # enough material around the screw
            self.assertFalse(m.contains(body, (x, y, d.T - d.back_t - 2.0)))     # pilot hole
            self.assertFalse(m.contains(cap, (x, y, d.T - 0.7)))                 # clearance hole
            self.assertTrue(m.contains(cap, (x + 2.0, y, d.T - 0.7)))            # solid around it

    def test_outer_skin_is_unbroken_around_the_buttons(self):
        """Pin pockets and slots must not break through the outside. Probe a thin skin just
        inside the outer surface around each button, everywhere except the pin hole itself
        (a slicer showed a crescent where an early pocket broke through)."""
        import math
        from outline import dist_to_outline
        for closure in ("backsnap",):
            m.clear_scene()
            p = CaseParams().with_(shape="D2", closure=closure)
            d = derived(p)
            body = build_body(p)
            zc = d.front_t + p.button_z
            rw = p.button_w / 2 + p.clearance
            checked = 0
            for (base, u) in button_axes(p):
                n = (-u[1], u[0])
                for ia in range(0, 15):
                    for il in range(-14, 15):
                        a, lat = 1.0 + 0.5 * ia, 0.5 * il
                        if abs(lat) < rw + 0.4:
                            continue                         # the pin hole itself
                        x = base[0] + u[0] * a + n[0] * lat
                        y = base[1] + u[1] * a + n[1] * lat
                        skin = dist_to_outline((x, y), p)
                        if not (0.15 < skin < 0.7) or not point_in_polygon((x, y), case_outline(p)):
                            continue
                        for dz in (-3.5, 0.0, 3.5):
                            self.assertTrue(m.contains(body, (x, y, zc + dz)), (closure, round(x, 1), round(y, 1), dz))
                            checked += 1
            self.assertGreater(checked, 20)

    def test_watch_can_be_lowered_straight_into_the_body(self):
        """The watch goes in from the back, screen first, straight down. Its buttons and horns
        must pass the walls the whole way (an earlier body stopped the button slots short and
        the watch could not be dropped in). The watch is modelled slightly oval (0.35 mm
        shorter than round along Y), because the pocket is 47.0 mm tall and the real watch fits."""
        from outline import cavity_parts
        for closure in self.CLOSURES[:1]:
            m.clear_scene()
            p = CaseParams().with_(shape="D2", closure=closure)
            d = derived(p)
            body = build_body(p)
            q = p.with_(clearance=0.0, horn_clear=0.0, body_extra_y=-0.35, body_extra_x=0.0, body_extra_far=0.0,
                        button_horn_gap=0.0, lower_button_extra=0.0, pin_slot_extra=0.0)
            for dz in (20.0, 15.0, 12.0, 9.0, 6.0, 3.0, 0.0):
                parts = cavity_parts(q)
                watch = m.prism(parts[0], 0.0, p.watch_t, "w")
                for i, part in enumerate(parts[1:5]):
                    watch = m.boolean(watch, m.prism(part, 0.0, p.watch_t, f"h{i}"), "UNION")
                zc = p.button_z
                for i, part in enumerate(parts[5:]):
                    watch = m.boolean(watch, m.prism(part, zc - p.button_h / 2, zc + p.button_h / 2, f"b{i}"), "UNION")
                m.translate(watch, 0.0, 0.0, d.front_t + dz)
                hit = m.boolean(watch, m.duplicate(body, "b"), "INTERSECT")
                vol = m.volume(hit) if len(hit.data.polygons) else 0.0
                self.assertLess(vol, 0.05, (closure, "lowering", dz))

