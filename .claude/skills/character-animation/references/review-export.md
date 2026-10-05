# Review and export

## Acceptance evidence

Prepare a labeled contact sheet and playback at the real timeline, plus slow
playback and representative overlays against the canonical pose. For procedural
clips capture the renderer at rest, extrema, contacts and transition boundaries.
Review transparent edges over both light and dark backgrounds at actual display
size and magnified detail. A contact sheet alone does not verify temporal motion.

Record pass/fail/pending, an evidence path/time or frame range and a concrete
observation for each applicable check:

| Check | Look for |
| --- | --- |
| Identity | Head/body ratio, face, material, appendages and silhouette agree with the selected masters |
| Scale/camera | Stable anatomical scale; no frame-by-frame zoom, auto-centering or unexplained camera movement |
| Motion | Coherent joints, spacing, contacts, weight and pauses; no flicker, melting or extra limbs |
| Attachment | Item scale/pivot and near/far occlusion remain correct; no floating, clipping or duplicated details |
| Props | Required seated props retained; intentional prop changes match the clip contract |
| Loop | Last-to-first pose and velocity are compatible; no accidental extra hold or flash |
| State boundaries | Entry/exit matches the current idle in size, root and item fit; no visible scale jump |
| Rendering | Clean alpha edges, no gray-sheet background, clipping, atlas bleed or blending ghosts |
| Runtime, if integrated | First-frame fallback, completion callbacks, lifecycle/cancellation and reduced motion work |

Automate file decoding, dimensions, alpha presence, sequence completeness, hashes,
durations, crop bounds and restoration offsets where possible. Compare landmarks
only for equivalent poses/views with recorded estimates and uncertainty. A pixel
similarity score or a low bounding-box variance does not establish anatomy or
natural motion. A new image edit or changed renderer invalidates the affected
visual evidence; an export must be tied to the actual final bytes.

When playback inspection is unavailable, report static verification and leave
motion pending. Do not mark the skill complete merely because a preview was
generated. A documented non-applicable check is allowed only with a reason (for
example, no loop seam for a one-shot clip).

## Optimize without changing approved motion

1. Keep reference masters under `art/` unmodified and outside application source
   sets. Keep intermediate candidates under ignored `output/imagegen/`.
2. Measure maximum intended on-screen pixels, including density, preview sizes and
   scene enlargement. Export at a justified resolution, not an arbitrary 384px
   default. Preserve one scale transform, aspect ratio and alpha; inspect the
   smallest thin details at the largest display size.
3. Compare PNG with lossless WebP for the final derivatives. Use the smaller
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
