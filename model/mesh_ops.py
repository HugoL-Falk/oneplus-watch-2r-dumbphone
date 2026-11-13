"""Small bpy/bmesh toolkit: build solids from 2D rings, combine them with
EXACT booleans, inspect them, export them. 1 Blender unit = 1 mm.
All objects keep an identity transform; geometry lives in mesh coordinates."""
import math
import sys

import bmesh
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

sys.dont_write_bytecode = True


def clear_scene():
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)
    for mesh in list(bpy.data.meshes):
        bpy.data.meshes.remove(mesh)
    for cam in list(bpy.data.cameras):
        bpy.data.cameras.remove(cam)
    for light in list(bpy.data.lights):
        bpy.data.lights.remove(light)


def _object_from_bmesh(bm, name):
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    return obj


def _rings_to_bmesh(rings, closed_loop, cap):
    """rings: [(polygon, z)], polygons with equal vertex counts."""
    bm = bmesh.new()
    vrings = [[bm.verts.new((x, y, z)) for x, y in poly] for poly, z in rings]
    n = len(vrings[0])
    if any(len(r) != n for r in vrings):
        raise ValueError("all rings must have the same vertex count")
    pairs = list(zip(vrings, vrings[1:]))
    if closed_loop:
        pairs.append((vrings[-1], vrings[0]))
    for a, b in pairs:
        for j in range(n):
            k = (j + 1) % n
            bm.faces.new((a[j], a[k], b[k], b[j]))
    if cap:
        bm.faces.new(list(reversed(vrings[0])))
        bm.faces.new(vrings[-1])
    return bm


def loft(rings, name, cap=True):
    """Solid through rings [(polygon, z)] from bottom to top, capped at both ends."""
    return _object_from_bmesh(_rings_to_bmesh(rings, closed_loop=False, cap=cap), name)


def ring_tube(profile, outline_fn, name):
    """Closed tube: `profile` is a closed list of (inset, z); each point becomes
    the ring outline_fn(inset) at height z."""
    rings = [(outline_fn(inset), z) for inset, z in profile]
    return _object_from_bmesh(_rings_to_bmesh(rings, closed_loop=True, cap=False), name)


def prism(poly, z0, z1, name):
    return loft([(poly, z0), (poly, z1)], name)


def _circle(cx, cy, r, segments):
    return [(cx + r * math.cos(2 * math.pi * i / segments),
             cy + r * math.sin(2 * math.pi * i / segments)) for i in range(segments)]


def cylinder(cx, cy, r, z0, z1, name, segments=96):
    return prism(_circle(cx, cy, r, segments), z0, z1, name)


def cone(cx, cy, r0, r1, z0, z1, name, segments=96):
    return loft([(_circle(cx, cy, r0, segments), z0), (_circle(cx, cy, r1, segments), z1)], name)


def box(min_xyz, max_xyz, name):
    (x0, y0, z0), (x1, y1, z1) = min_xyz, max_xyz
    return prism([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], z0, z1, name)


def boolean(target, tool, op):
    """Apply a MANIFOLD-solver boolean of `tool` onto `target`; deletes `tool`."""
    mod = target.modifiers.new("bool", "BOOLEAN")
    mod.operation = op
    mod.solver = "MANIFOLD"
    mod.object = tool
    depsgraph = bpy.context.evaluated_depsgraph_get()
    evaluated = target.evaluated_get(depsgraph)
    new_mesh = bpy.data.meshes.new_from_object(evaluated)
    old_mesh = target.data
    target.modifiers.remove(mod)
    target.data = new_mesh
    bpy.data.meshes.remove(old_mesh)
    tool_mesh = tool.data
    bpy.data.objects.remove(tool, do_unlink=True)
    if tool_mesh.users == 0:
        bpy.data.meshes.remove(tool_mesh)
    return target


def round_end(obj, on_end, offset, segments=3):
    """Round the rim of the pin's end face. `on_end(x, y)` says whether a point lies on that
    face; bevels the edges between the end faces and the rest so the end is comfortable to press."""
    bm = _bm(obj)
    bm.faces.ensure_lookup_table()
    end = {f for f in bm.faces if all(on_end(v.co.x, v.co.y) for v in f.verts)}
    edges = [e for e in bm.edges if len({f for f in e.link_faces if f in end}) == 1]
    if edges:
        bmesh.ops.bevel(bm, geom=edges, offset=offset, offset_type="OFFSET", segments=segments,
                        profile=0.5, affect="EDGES")
    bm.to_mesh(obj.data)
    obj.data.update()
    bm.free()


def duplicate(obj, name):
    copy = bpy.data.objects.new(name, obj.data.copy())
    bpy.context.scene.collection.objects.link(copy)
    return copy


def _bm(obj):
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    return bm


def volume(obj):
    bm = _bm(obj)
    v = abs(bm.calc_volume(signed=True))
    bm.free()
    return v


def is_manifold(obj):
    bm = _bm(obj)
    ok = (len(bm.faces) > 0
          and all(e.is_manifold and e.is_contiguous for e in bm.edges)
          and all(v.link_faces for v in bm.verts))
    bm.free()
    return ok


def bbox(obj):
    co = [v.co for v in obj.data.vertices]
    return (tuple(min(c[i] for c in co) for i in range(3)),
            tuple(max(c[i] for c in co) for i in range(3)))


def contains(obj, xyz):
    """Point-in-solid by ray parity along a skewed direction."""
    bm = _bm(obj)
    tree = BVHTree.FromBMesh(bm)
    bm.free()
    direction = Vector((1.0, 0.0137, 0.0071)).normalized()
    origin = Vector(xyz)
    hits = 0
    for _ in range(1000):
        loc, _normal, _index, _dist = tree.ray_cast(origin, direction)
        if loc is None:
            break
        hits += 1
        origin = loc + direction * 1e-4
    return hits % 2 == 1


def translate(obj, dx, dy, dz):
    for v in obj.data.vertices:
        v.co.x += dx
        v.co.y += dy
        v.co.z += dz
    obj.data.update()


def transform(obj, matrix):
    """Apply a 4x4 mathutils Matrix to every vertex."""
    obj.data.transform(matrix)
    obj.data.update()


def flip_x(obj):
    """Rotate 180 degrees about X (y -> -y, z -> -z), keeping normals valid."""
    for v in obj.data.vertices:
        v.co.y = -v.co.y
        v.co.z = -v.co.z
    obj.data.update()


def export_stl(obj, path):
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.wm.stl_export(filepath=path, export_selected_objects=True,
                          global_scale=1.0, ascii_format=False, apply_modifiers=True)


def render_preview(objs, path, view):
    """Orthographic Workbench render of `objs`; view is front | back | iso."""
    scene = bpy.context.scene
    try:
        scene.render.engine = "BLENDER_WORKBENCH"
    except TypeError:
        scene.render.engine = "BLENDER_EEVEE"
    scene.render.resolution_x = scene.render.resolution_y = 1000
    scene.render.film_transparent = False
    shading = scene.display.shading
    shading.light = "STUDIO"
    shading.color_type = "OBJECT"
    shading.show_object_outline = True
    shading.object_outline_color = (0.6, 0.6, 0.6)
    # Alternate shades so parts seen through openings stay distinguishable.
    for i, o in enumerate(objs):
        o.color = (0.10, 0.10, 0.11, 1.0) if i % 2 == 0 else (0.30, 0.30, 0.33, 1.0)
    shading.show_specular_highlight = True
    shading.show_cavity = True
    world = scene.world or bpy.data.worlds.new("World")
    scene.world = world
    world.color = (0.92, 0.92, 0.92)

    lo = [min(bbox(o)[0][i] for o in objs) for i in range(3)]
    hi = [max(bbox(o)[1][i] for o in objs) for i in range(3)]
    centre = Vector([(lo[i] + hi[i]) / 2 for i in range(3)])
    size = max(hi[i] - lo[i] for i in range(3))
    directions = {"front": Vector((0, 0, -1)), "back": Vector((0, 0, 1)),
                  "iso": Vector((0.55, 0.35, -1.0)).normalized()}
    d = directions[view]
    cam_data = bpy.data.cameras.new("cam")
    cam_data.type = "ORTHO"
    cam_data.ortho_scale = size * 1.25
    cam = bpy.data.objects.new("cam", cam_data)
    scene.collection.objects.link(cam)
    cam.location = centre + d * size * 3
    cam.rotation_euler = (centre - cam.location).to_track_quat("-Z", "Y").to_euler()
    cam_data.clip_end = size * 10
    scene.camera = cam
    hidden = [o for o in scene.objects if o.type == "MESH" and o not in objs]
    for o in hidden:
        o.hide_render = True
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
    for o in hidden:
        o.hide_render = False
    bpy.data.objects.remove(cam, do_unlink=True)
    bpy.data.cameras.remove(cam_data)
