# Review and export

## Review setup

Before full production, choose an observer and test playback with a small key
block or existing frames. Record player/renderer, display pixels, device density
or browser DPR/zoom, rates (1x and 0.25x), loop count and capture method. Check
that the observer can actually watch continuous motion; screenshots, file opening
and a generated video alone are not observation evidence.

For individual frames, create the [clip record](production.md#task-local-clip-record)
and run:

```bash
python3 .agents/skills/character-animation/scripts/prepare_review.py --clip output/imagegen/character-animation/run/clip.json --out output/imagegen/character-animation/run/review-01
```

Open the resulting `preview.html` in a browser. It embeds decoded full-canvas
frames and their declared variable durations, with 1x/0.25x playback, restart,
light/dark backgrounds, frame/time readout and cycle count. Watch the whole clip
at both speeds; watch loops for at least three cycles and one-shots through their
terminal interval. Check the display size against the intended pixel size and
record DPR/zoom; watch magnified edges separately. A high RAF gap or visible stall
invalidates smoothness conclusions from that run: retry a capable foreground
player, do not change authored timing to conceal player lag. Save observations
alongside the generated contact sheet and hash inventory. The helper refuses to
overwrite an existing review directory and never sets a visual pass.

The helper accepts restored, untrimmed single-frame images of one canvas size.
For atlases or trimmed exports, also test their real loader's crops/offsets;
reconstructed frame previews cannot validate atlas bleed or loader behavior.
For procedural motion use its actual renderer at the intended size with a
repeatable clock/seed, logging code/diff identity and parameters. Capture event
samples and a real-time recording; use renderer time scaling or 0.25x video
playback for slow review. Do not bake sprites merely to use the helper. Browser
and video playback are not target-device performance measurements.

If the agent cannot continuously observe playback, identify that limitation before
full production. Hand the user a playable candidate, its hash, exact checks and
the review-table format below. Leave motion pending until an identified observer
returns actual observations tied to that candidate. User review is an alternative
observation route, not mandatory second approval after valid delegated review.

## Stage gates

| Stage | Required to advance | Work deliberately deferred |
| --- | --- | --- |
| Inputs/setup | Current-input review bound to hashes; playback route and observer established | Generated motion and runtime behavior |
| Key poses | Identity, anatomy/reach, item/prop fit, contact intent, readable action and major timings in a timed block | Smooth in-betweens, segment polish and optimized exports; stepped key playback is expected |
| Segments | Coherent joint paths/spacing, contact and attachment through added frames; neighboring keys and seam segments agree | Final whole-clip rhythm, all requested handoffs and encoding |
| Whole clip | All applicable acceptance checks below, observed full playback and requested connections | Runtime-only checks for an asset-only task |
| Final export/integration | Revalidated final bytes; retained evidence; loader and lifecycle checks when integration is requested | Only explicitly out-of-scope checks, reported separately |

Apply a verdict to a stage, not to the entire project prematurely. A failure or
unresolved required check blocks advancement through that gate. Deferred work does
not block an earlier gate, and a key-pose pass never means production-ready motion.

## Acceptance evidence

Prepare a labeled contact sheet and playback at the real timeline, plus slow
playback and representative overlays against the canonical pose. For procedural
clips capture the renderer at rest, extrema, contacts and transition boundaries.
Review transparent edges over both light and dark backgrounds at actual display
size and magnified detail. A contact sheet alone does not verify temporal motion.

Record stage, observer/date, candidate hash (or procedural code/diff identity plus
parameters), pass/fail/pending, evidence path/time or frame range and a concrete
observation for each applicable check. Use `not_applicable` only with a reason;
out-of-scope runtime checks cannot block an asset-only visual acceptance.

| Check | Look for |
| --- | --- |
| Identity | Head/body ratio, face, material, appendages and silhouette agree with the selected masters |
| Scale/camera | Stable anatomical scale; no frame-by-frame zoom, auto-centering or unexplained camera movement |
| Motion | Coherent joints, spacing, contacts, weight and pauses; no flicker, melting, extra limbs or unintended color/lighting/texture shimmer |
| Temporal continuity | Observed timing/spacing agrees with planned phases; sampling captures fast arcs and impacts; measure timestamp-aware landmarks when diagnosing a defect |
| Attachment | Item scale/pivot and near/far occlusion remain correct; no floating, clipping or duplicated details |
| Props | Required seated props retained; intentional prop changes match the clip contract |
| Loop | Last-to-first pose and velocity are compatible; no accidental extra hold or flash |
| State boundaries | Requested pairs and idle handoffs preserve scale, root, velocity and fit; check one-shot terminal pose/duration and the next state's first pose; incompatible poses use a bridge |
| Rendering | Clean alpha edges, no gray-sheet background, clipping, atlas bleed or blending ghosts |
| Runtime, if integrated | First-frame fallback, completion exactly once, interruption without stale completion, lifecycle/cancellation and reduced motion work |

Track measurement availability separately as `measured`, `estimated`,
`unavailable` (with a reason) or `not_requested`. A normally occluded far hand need not block acceptance
if the relevant visible anatomy, occlusion and adjacent visible phases support a
documented visual pass. If those alternatives cannot establish identity, contact
or fit, the check remains pending. Do not mark an applicable but unverified check
`not_applicable`, and do not invent hidden coordinates.

Example review rows (illustrative, not evidence for a real candidate):

| Stage/check | Measurement | Verdict | Observation/evidence required |
| --- | --- | --- | --- |
| Key poses / timing | not_requested | pending | Observer, candidate hash and timed-block observations |
| Segments / far-hand anatomy | unavailable: occluded | pending | Inspect visible neighboring poses/occlusion; record a visual pass only if supported |
| Whole clip / seam | not_requested | pending | Observer, 1x and 0.25x evidence, at least three cycles |
| Asset-only / device frame time | not_requested | not_applicable | Runtime integration is outside this task; no performance claim |

Automate file decoding, dimensions, alpha presence, sequence completeness, hashes,
durations, crop bounds and restoration offsets where possible. Compare landmarks
only for equivalent poses/views with recorded estimates and uncertainty. A pixel
similarity score or a low bounding-box variance does not establish anatomy or
natural motion. A new image edit or changed renderer invalidates the affected
visual evidence; an export must be tied to the actual final bytes.

## Optimize without changing approved motion

Apply raster export steps only when creating/resizing raster derivatives or when
encoding optimization is requested. Procedural-only edits and unchanged assets do
not require PNG/WebP comparisons. Respect an unchanged loader's supported format.

1. Keep reference masters under `art/` unmodified and outside application source
   sets. Keep intermediate candidates under ignored `output/imagegen/`.
2. Measure maximum intended on-screen pixels, including density, preview sizes and
   scene enlargement. Export at a justified resolution, not an arbitrary 384px
   default. Preserve one scale transform, aspect ratio and alpha; inspect the
   smallest thin details at the largest display size.
3. For new raster exports or requested encoding optimization, compare PNG with
   lossless WebP when both are loader-compatible. Use the smaller
   compatible output only after decoding and comparing canvas and RGBA pixels.
   Lossless WebP is not guaranteed smaller. Do not silently apply lossy compression
   or palette reduction to approved art. Resizing is a separate visual change,
   requiring its own review rather than a lossless-pixel-equality claim.
4. Exact repeated frames may share storage with duration/index metadata if the
   renderer supports it. Preserve total timeline and holds. With fixed numbered
   loaders, keep required indices until the loader is deliberately updated.
5. Packing frames into an atlas does not itself reduce decoded pixel memory.
   Avoid huge sheets; consider bounded pages and playback caching only when
   supported and measured. Trimming requires offset-aware playback and filtering
   padding to prevent adjacent-frame bleed.
6. Update extension/path templates, frame size assertions, grid/crop coordinates,
   `inSampleSize` and first-frame fallbacks together when integrating. Pre-halving
   source pixels while retaining `inSampleSize = 2` would halve them again.

Report three different measurements, with before/after values where measured:

- Stored output bytes (and APK/AAB size only if actually built/measured).
- Decoded pixel memory estimate: sum of resident width × height × bytes per pixel;
  ARGB_8888 uses four. Include concurrently retained clips/transition frames.
  This estimate excludes extra GPU copies and renderer overhead.
- Measured target-device loading/frame-time/peak-memory behavior, or explicitly
  state it was not measured. Better file compression does not imply lower RAM.

For illustration, 24 decoded 627×627 ARGB frames occupy about 36 MiB of pixel
storage; 24 at 384×384 occupy 13.5 MiB. These are calculations, not recommended
dimensions or observed profiler results. Current loaders already subsample some
sources: pre-exporting the same decoded dimensions reduces stored bytes but does
not necessarily reduce their existing runtime memory.

## Handoff

Deliver the reference hashes, clip contract, accepted export files and final hash
inventory, preview/contact-sheet evidence and review statuses. Report pending
checks and unresolved defects separately. Include loader changes needed for an
asset-only handoff. For requested integration, run the repository's applicable
checks and exercise the affected transitions; do not add unrelated cleanup.

Keep disposable candidates in the ignored run directory, but retain the final
`input-review.json`, `clip.json`, verdict table, final output/evidence hash inventory
and representative playable evidence in one durable handoff bundle. For procedural
work include the exact code revision and any uncommitted diff, parameters and
capture timing. Paths within the bundle must still resolve after relocation.

Use an established retained artifact location, or an issue/PR attachment when
uploading is authorized. Do not upload merely because a review is complete. When
no destination is established, prepare a self-contained local bundle, deliver its
path and mark retention pending until a durable location is recorded. Link that
location from the task record; do not place bulk previews or per-run reports in
DESIGN. A local ignored path alone is not durable retention. Report visual
acceptance, retention and runtime verification separately; retention pending
blocks a complete production handoff, not an otherwise supported visual verdict.
