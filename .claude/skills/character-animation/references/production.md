# Production and reference contract

## Reference authority

- Start at `art/characters/reference_catalog.json`. For an equipped clip resolve
  the item ID through `accessories`; use its `asset_variant` rather than deriving
  a folder from the item ID (`accessory:luna_cap` uses `hat`).
- Base input: `canonical`, `turnaround`, `identity_constraints` and
  `animation_contract`. Item input additionally requires `canonical_fitted`,
  `base_character`, `attachment_contract` and `generation_gate`.
- The original canonical defines identity; fitted canonical defines item fit.
  Turnarounds describe other views but do not override those masters. Their hidden
  surfaces are inferred, and side views are not calibrated orthographic views.
- An explicitly requested reference correction may use `reviewed_reference_revision`
  with a `revision_review` binding the original source and revised image hashes,
  reviewer, authority, date, reason, evidence and signoff provenance. Its scope is
  `reference_only`: the recorded runtime source stays independently verified and
  is not claimed to match the corrected master. Current-input review is still required.
- Read any `construction_references` with the master; preflight includes their bytes
  in the reviewed inventory. A rotated detail preserves 2D part construction and
  proportions, not calibrated perspective or a new pose approval.
- Current sheets contain seated/standing front, left-side and back views; item
  sheets also contain item-only views. Use explicit `cell_rect_px` / `rect_px`, not
  equal grid division. Las has unequal row heights and sunglasses unequal columns.
  Coordinates are sheet-global pixels; subtract the cell origin when making crops.
- Opaque gray sheets are reference-only. Do not ship their cells as transparent
  frames, remove gray with a crude color key or infer alpha bounds from them.
- Item-only drawings are enlarged inspection views, not a fit-scale authority.
  Use fitted masters and head-relative attachment points. Normalized item points
  may lie outside 0..1 when equipment extends beyond the skull.
- `*_estimate` landmarks are manual estimates. The current head-drift and sheet
  ground-spread thresholds apply only to comparable views/poses; they are not a
  promise of calibrated anatomy or a universal tolerance for moving frames.
- Right-side motion cannot automatically mirror asymmetric details. Request or
  establish any additional required view in a separately scoped reference task.

### Bind review to the current inputs

Preflight without `--reviewed-inputs` returns `reference_checks_passed`,
`generation_ready: false` and exit 2. It verifies image hashes against manifests,
file metadata and declared review fields, not the review history of current
identity rules, measurements or attachment contracts. Those fields are prose and
must be read, including limitations and contradictory observations.

Before generation, locate review evidence identifying the inspected version and
compare it with the current manifests/images. Inspect the current inputs under
that recorded authority. If changed or ambiguous inputs lack applicable review,
leave them pending and resolve reference review separately. Do not reinterpret a
previous review of different bytes as approval of new inputs.

After that check, record `input-review.json` with `schema_version: 1`,
`status: "accepted_for_reference"`, nonempty `reviewer`, `authority`, `date`,
`evidence` (the inspection record or durable review link), boolean
`separate_user_visual_signoff`, and the exact `checked_sha256` map from preflight.
The map covers the catalog, selected manifests and checked images, including
source copies. No helper creates an accepted record. Capture it at review time;
copying a current inventory alone is not a review. Reuse a valid existing record
instead of requesting redundant signoff.

```bash
python3 .agents/skills/character-animation/scripts/preflight.py --repo . --character luna --item accessory:luna_cap --reviewed-inputs output/imagegen/character-animation/run/input-review.json
```

Exit 0 / `reference_gate_passed` means exact inventory match; a changed or missing
entry blocks with exit 1. This is a consistency check on a review assertion, not a
signature or proof of reviewer authority. Preserve the review record and its hash
with the clip. An old inventory mismatch requires review of the changed inputs,
not an automatic hash refresh. Motion acceptance remains a separate stage.

## Motion and coordinate rules

Use [motion continuity](motion-continuity.md) for phase keys, sampling and action
connections. A sprite sheet is packaging, not a smoother animation method.
Preserve the existing loader contract when integration is outside task scope.

Use one anatomical scale derived from the canonical head, a stable coordinate
system and an explicit root trajectory. Rotation changes projected head width;
do not resize turned heads to match a front-view bounding box. During jumps the
feet leave the ground intentionally. During contact the planted foot should not
slide unless sliding is part of the requested action.

Keep canvas and camera fixed. Do not independently fit, crop or center each pose.
If trimming is needed, retain original canvas size and per-frame offsets so the
renderer reconstructs the same coordinates. Do not stretch a frame to correct a
proportion error. Repair the pose or its generation instead.

Items follow their attachment contract, including pivot, rigid movement and near/
far occlusion. Do not independently generate an item at a new scale per frame.
For a layered workflow, account for surfaces revealed behind the moving part.
For baked frames, inspect the same attachment and occlusion rules in every pose.
Read character/item constraints rather than memorizing traits in this skill.
The seated steering wheel is a pose prop, not equipped clothing; a standing
reference does not authorize replacing the application's default seated pose.

Current renderer inspection points (verify current code before editing):

| Owner | Relevant contract |
| --- | --- |
| `PetAvatar.kt`, `LunaIdleArtwork.kt`, `LunaIdleAnimation.kt`, `LunaHungryArtwork.kt`, `LunaScene.kt` | Luna idle/hungry shared WebP body/equipment, fixed sprout root and procedural gestures; other clips retain numbered frames and pose-specific transforms |
| `MobiIdleAnimation.kt`, `MobiIdleArtwork.kt` | Mobi source parts, eye poses, procedural transforms, first-pose fallback and sampling |
| `LasIdleAnimation.kt` | Procedural masks/pivots tied to the 475px canonical; never apply them to turnaround sheets |
| `Las*Animation.kt`, `Luna*Animation.kt` | Entry/exit and status playback, completion callbacks and fallbacks |

Preserve reduced-motion behavior and the rendering/ownership boundary: animation
code renders appearance; it does not equip items or write rewards.

## Task-local clip record

Keep a compact `clip.json` with these fields appropriate to the chosen method:

- Selected character/item IDs, manifest/image paths and hashes from preflight.
- Action, camera/view, pose, immutable identity and allowed movement.
- Method (`procedural` or `frames`), canvas pixels, canonical head scale,
  root/ground anchors, root trajectory, item attachment and prop policy.
- Key poses, frame order and durations, loop/one-shot, entry/exit reference poses.
  One-shots include terminal pose, final display duration, completion and handoff.
- Motion phases and applicable contact/release intervals; for diagnosed defects,
  add selected landmark paths, thresholds and measurement uncertainty.
- Requested transition pairs, their root/velocity/pose handoff and interruption
  policy; locomotion also records stride displacement and scene travel speed.
- For trimmed frames: source canvas, crop rectangle and restoration offset.
- Target screen size in pixels and density, output encoding and loader assumptions.
- Review evidence paths and output hashes; procedural clips also record code
  revision/diff identity and the timeline used to capture review samples.

This is a production record under the ignored run directory. Do not add an unused
metadata parser to the app or duplicate canonical measurements into DESIGN.


Minimal **illustrative key-blocking** record for the preview helper (not an
approved motion or a recommended duration/resolution). Paths are relative to
`clip.json`; supply your own reviewed-input record and full-canvas frames. The
helper reads only canvas/display, frames, duration, loop and one-shot terminal
fields; the other fields document production decisions.

```json
{
  "character_id": "friend:luna",
  "item_id": null,
  "stage": "key_poses",
  "reference_review": "input-review.json",
  "action": "seated blink",
  "view": "front",
  "method": "frames",
  "canvas_px": [512, 512],
  "display_px": [256, 256],
  "display_density": 1,
  "geometry": {
    "scale": "one canonical-head transform recorded in source frames",
    "root": "canonical seated anchor; no translation",
    "fixed": "body, head outline, wheel, camera",
    "moving": "eyelids only",
    "props": "retain steering wheel"
  },
  "duration_ms": 600,
  "loop": false,
  "frames": [
    {"path": "frames/open.png", "duration_ms": 200},
    {"path": "frames/closed.png", "duration_ms": 100},
    {"path": "frames/open.png", "duration_ms": 300}
  ],
  "terminal": {
    "pose": "canonical open eyes, same seated root",
    "preview_end": "hold_last",
    "completion": "once at 600 ms, after the final 300 ms display interval",
    "handoff": "idle starts from the same pose and root; no added hold"
  },
  "review": {"observer": null, "status": "pending", "evidence": []}
}
```

Replace semantic anchor descriptions with measured transforms where needed.
For procedural motion, replace the frame list with source code/diff identity,
parameters and a capture timeline. Do not invent a sprite list for that method.
