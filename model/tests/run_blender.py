"""Run the Blender-side tests inside headless Blender.

blender -b --factory-startup --python-exit-code 1 --python model/tests/run_blender.py [-- test_parts ...]
"""
import os
import sys
import unittest

sys.dont_write_bytecode = True

TESTS = os.path.dirname(os.path.abspath(__file__))
MODEL = os.path.dirname(TESTS)
for path in (MODEL, TESTS):
    if path not in sys.path:
        sys.path.insert(0, path)

ALL = ["test_mesh_ops", "test_parts", "test_build", "test_tolerance"]
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
names = argv or [n for n in ALL if os.path.exists(os.path.join(TESTS, n + ".py"))]

suite = unittest.defaultTestLoader.loadTestsFromNames(names)
result = unittest.TextTestRunner(verbosity=2, stream=sys.stdout).run(suite)
sys.exit(0 if result.wasSuccessful() else 1)
