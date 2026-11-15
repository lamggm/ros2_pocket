# Offline URDF mesh assets (v1)

ROS Pocket remains a passive topic observer. A URI in `robot_description` is a
lookup key, not permission to access that URI. Import a ZIP through the native
**Importar malhas (ZIP)** button after receiving the description. Nothing is
installed on the robot. The Android document provider reads the chosen file; the
app never fetches mesh/texture URLs. The picker can suspend DDS under the existing
foreground-only lifecycle. After return, description intake restarts normally.

## Package contract

ZIP without comments or ZIP64, at most16MiB compressed,32MiB inflated,65entries.
Only `manifest.json` and referenced `.glb` paths are accepted; no directories,
unused entries, duplicates, traversal, absolute paths or extraction to disk.

```json
{"schema":1,"robot_name":"actual_robot","description_sha256":"<64 lowercase hex>","meshes":[{"uri":"package://robot/meshes/base.dae","file":"meshes/m000.glb","sha256":"<64 lowercase hex>"}]}
```

- The description hash covers its exact UTF-8 bytes, including whitespace. The
  source must be the expanded description actually published, not an unexpanded
  xacro or differently rewritten package/file URI variant.
- Each URI and name is literal. No filename guessing, basename matching, wildcard
  URI rewriting, remote downloads or association based only on the robot name.
- A single validated package is retained in app-private storage, independent of
  connection profiles. It is automatically usable only for the matching name and
  description hash. Other descriptions remain partial, never render stale assets.
- The importer validates/decompresses/decodes off the DDS/UI threads, checks hashes
  before persistence and uses an AtomicFile. Cancellation, invalid input and
  incompatible-description rejection do not replace the retained valid package.
  Destroyed owners cannot apply late callbacks. No document permission retained.
- Devices advertising OpenGL ES2 or newer use an inline GLSurfaceView at its
  actual viewport resolution, a hardware depth buffer, per-material vertex colors
  and directional plus ambient lighting, subtle view-dependent specular response
  and orientation-dependent sky reflection (basic lighting, not PBR). EGL tries4xMSAA, then2x, then noMSAA,
  with24/16-bit depth. The driver logs its renderer, samples, viewport and payload.
  No continuously rendering loop: render-on-demand and the existing bounded
  orbit/visibility/lifecycle/touch scheduler remain in charge. Repeated identical
  description bytes reuse the prepared geometry and preserve pinch zoom; changed
  XML or asset imports rebuild normally, and Disconnect still clears the model.
- Devices without ES2 retain the bounded Canvas/depth-buffer compatibility path
  (640x640), with a lower face budget. Transparency is approximate in both paths;
  this is not order-independent transparency or a physically based renderer.
- Embedded GLB material/vertex colors take precedence over a generic URDF mesh
  color. Unmaterialized geometry can use the URDF fallback. Default glTF base color
  is white, so vertex colors are not multiplied by an invented grey tint.
- GLB2 supports embedded BIN only, TRIANGLES, indexed/unindexed POSITION float,
  optional vertex colors/NORMAL, flat baseColorFactor and static node matrix/TRS.
  Normals use inverse-transpose through node and URDF mesh scales, with flat
  fallback for older assets that have no NORMAL.
  Standard glTF Y-up is converted into URDF Z-up before mesh scale/visual/joint
  transforms. No images, external buffers, required extensions, skins, animation,
  morphs or sparse accessors. Colors are flat shaded, not a PBR renderer.
- Limits:8MiB/GLB,128KiBJSON,256nodes/accessors/views,depth32,360000accessor elements,
  120000unique decoded triangles/package and120000assembled GPU triangles,
  including every repeated URDF visual and primitive. Canvas compatibility stays
  limited to8192assembled triangles. Exceeding the applicable render limit is an explicit
  error, not an apparently complete truncated model.

## Desktop COLLADA conversion

Optional host-only dependencies, kept out of the Android APK:

```sh
uv venv /path/to/converter-env
uv pip install --python /path/to/converter-env/bin/python trimesh pycollada meshoptimizer scipy
/path/to/converter-env/bin/python tools/prepare_robot_assets.py \
  --urdf /path/to/exact-published.urdf \
  --mesh-root package://robot/=/path/to/robot-package \
  --output /path/to/robot.pocket.zip --max-triangles 120000
```

For a simulator's rewritten description, supply the exact prefix, for example
`--mesh-root file:///opt/simulator/share/robot/=/path/to/robot-package`.
Multiple explicit roots are supported. Only local DAE files under those roots
are read; sources and robot projects are not modified. Existing output ZIPs are
not overwritten. Outputs are a ZIP and a separate provenance report; the report
can contain private source paths and is not needed by Android.

The current converter requires explicit COLLADA Z_UP, applies its declared metre
unit and scene transforms, groups the original diffuse material palette, and
welds normal/UV seams only within a material group. Each group receives its own
bounded LOD allowance; source extrema faces are retained, quadric/prune reduction
is preferred, and a topology-permissive fallback handles fragmented CAD.
Materials are not averaged into one color per file. Fine details, holes and
disconnected pieces may still be lost: this remains deliberately approximate.
The default assembled budget is120000triangles, counting every repeated visual
instance. Per-asset allocation is proportional to the square root of actual
post-weld face complexity, capped by source demand; it is no longer an equal
slice per filename. This is a complexity heuristic, not screen-space error LOD.
After reduction, smooth patches are split at35-degree creases and explicit
vertex normals are exported, preserving hard mechanical corners. Invalid zero
normals on degenerate geometry receive an explicit reported fallback. The GPU
vertex stride stays unchanged, but optional CPU normal arrays and larger GLBs
consume additional memory. The converter rejects >5% bounding-box drift and records per-material
colors and before/after face counts. No textures. Simplification loses fine detail
and may introduce seams: this is a visual context model, never collision geometry
or a metrology reference. Source units, bounds, hashes and before/after face
counts are recorded separately. Other source formats/conversion policies can be
added later without granting Android arbitrary filesystem/network resolution.

## Full geometry and real transforms

[CAD + TF requirements](CAD-TF-REQUIREMENTS.md) records the original requirement.
The subsequent [indexed CAD/TF viewer](CAD-TF-VIEWER.md) is now implemented and
in validation. Earlier visual-polish acceptance remains static/mobile LOD and
does not transfer to this candidate. PBR/textures remain unsupported. Preserve the operator's
manual description publisher while testing; it is not a disposable smoke fixture.

## Evidence levels

Synthetic unit/native-Canvas fixtures verify protocol and rendering contracts.
An external real-project replay verifies transferred/converted project files with
the actual Android parser and direct GPU vertex preparation on the host.
Host Canvas fallback evidence does not establish EGL, MSAA, shader execution or
visual fidelity on the tablet. Compare the actual device image to the original
RViz reference before accepting color, detail and resolution. Physical DEV import, DDS intake
and on-screen interaction must be reported separately. An isolated description
publisher is not a running Gazebo stack or physical-robot acceptance. No BART CAD,
ZIP, real URDF, screenshot or private path is embedded into the application.
