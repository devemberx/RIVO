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

## Motion and coordinate rules

Choose the action before its packaging. Establish key poses for contact, passing,
apex and recovery as relevant, with explicit durations. Do not impose 24 frames
or a constant frame rate on every action. Preserve an existing loader contract
when integration changes are not authorized.

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
| `PetAvatar.kt`, `LunaScene.kt` | Luna numbered PNG paths, sampling, frame timing and pose-specific scale/translation |
| `MobiIdleAnimation.kt` | Mobi atlas layout, first-frame fallback, size assertions, sampling and selective blending |
| `LasIdleAnimation.kt` | Procedural masks/pivots tied to the 475px canonical; never apply them to turnaround sheets |
| `Las*Animation.kt`, `Luna*Animation.kt` | Entry/exit and status playback, completion callbacks and fallbacks |

Crossfade changes opacity, not anatomy or joint trajectories. Use it only where
overlapping poses remain visually acceptable; it cannot repair missing in-betweens.
Preserve reduced-motion behavior and the rendering/ownership boundary: animation
code renders appearance; it does not equip items or write rewards.

## Task-local clip record

Keep a compact `clip.json` with these fields appropriate to the chosen method:

- Selected character/item IDs, manifest/image paths and hashes from preflight.
- Action, camera/view, pose, immutable identity and allowed movement.
- Method (`procedural` or `frames`), canvas pixels, canonical head scale,
  root/ground anchors, root trajectory, item attachment and prop policy.
- Key poses, frame order and durations, loop/one-shot, entry/exit reference poses.
- For trimmed frames: source canvas, crop rectangle and restoration offset.
- Target screen size in pixels and density, output encoding and loader assumptions.
- Review evidence paths and output hashes; procedural clips also record code
  revision/diff identity and the timeline used to capture review samples.

This is a production record under the ignored run directory. Do not add an unused
metadata parser to the app or duplicate canonical measurements into DESIGN.
