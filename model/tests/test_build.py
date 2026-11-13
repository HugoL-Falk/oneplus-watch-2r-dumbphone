import os
import shutil
import tempfile
import unittest

import bpy

import build
import mesh_ops as m

VARIANT = "case_E3_snap_raised_B1_P1"
STLS = [f"{VARIANT}_front.stl", f"{VARIANT}_shell.stl", f"{VARIANT}_pins.stl", "fittest_E3.stl", "dummy_watch.stl"]
PNGS = [f"{VARIANT}_front.png", f"{VARIANT}_iso.png", f"{VARIANT}_back.png"]


class TestBuild(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.out = tempfile.mkdtemp(prefix="op2r_build_")
        cls.code = build.main(["--shape", "E3", "--closure", "snap", "--screen", "raised",
                               "--back", "B1", "--edge", "P1",
                               "--parts", "front,shell,pins,fittest,dummy", "--out", cls.out])

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.out, ignore_errors=True)

    def test_build_default_outputs(self):
        self.assertEqual(self.code, 0)
        for name in STLS:
            path = os.path.join(self.out, name)
            self.assertTrue(os.path.exists(path), name)
            self.assertGreater(os.path.getsize(path), 1024, name)
        for name in PNGS:
            self.assertTrue(os.path.exists(os.path.join(self.out, name)), name)

    def test_print_orientation(self):
        for name in STLS:
            m.clear_scene()
            bpy.ops.wm.stl_import(filepath=os.path.join(self.out, name))
            obj = bpy.context.selected_objects[0]
            (_, _, z0), _ = m.bbox(obj)
            self.assertAlmostEqual(z0, 0.0, delta=0.01, msg=name)

    def test_report_contents(self):
        with open(os.path.join(self.out, "report.md"), encoding="utf-8") as f:
            text = f.read()
        for needle in ("14.05", "push-pin", "Open items", "Charger puck corners",
                       "Snap click", "Wall behind the snap groove"):
            self.assertIn(needle, text)

    def test_invalid_params_exit_2(self):
        out = tempfile.mkdtemp(prefix="op2r_bad_")
        try:
            code = build.main(["--set", "screen_open_d=30", "--out", out])
            self.assertEqual(code, 2)
            self.assertEqual([f for f in os.listdir(out) if f.endswith(".stl")], [])
        finally:
            shutil.rmtree(out, ignore_errors=True)
