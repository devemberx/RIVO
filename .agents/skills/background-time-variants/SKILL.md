---
name: background-time-variants
description: Create and verify time-of-day variants from an approved background image while preserving building heights, silhouettes, object placement, perspective and framing. Use for stable scenes whose sky, clouds, lighting, shadows and reflections change over time. Excludes redesigning the scene or requiring identical pixels.
---

# Background time variants

Produce views of the same place at different times. Geometry and object identity
stay stable; illumination and atmosphere may change. Pixel equality is not an
acceptance criterion. Follow the project's approved-master and asset-location rules.

## Establish the scene contract

1. Inspect the chosen master before generation. Treat it as the edit target, not
   merely a style reference. Record its path, dimensions and source time when known.
2. Read [scene examples](references/scenes.md) for Cyberpunk City or Lake Park.
   For another scene, derive the same contract from its actual image.
3. Write a short fixed/variable inventory in the run's working directory:
   - **Fixed:** camera, crop, perspective, horizon, terrain/shoreline, building
     count, height, width, silhouette, antennae and relative positions; window
     openings, roads, bridges, vegetation and foreground object geometry.
   - **Time-dependent:** sky color, clouds, existing lamps/window illumination,
     ambient exposure, shadow direction/softness and reflections on existing
     surfaces. A tree becoming darker is allowed; its canopy changing shape is not.
   - **Conditional:** sun/moon/stars in the sky only when appropriate to the
     requested time. Do not add celestial objects unless the task permits them.
     Cloud variation does not authorize rain, snow, new vehicles or other objects.
4. Identify concrete landmarks for verification: representative roof/antenna tops,
   skyline gaps, bridge supports, shoreline bends, foreground corners and the
   companion's ground anchor. Describe where they are and what must stay stable.
   Choose normalized crop regions around the skyline and foreground for comparison.

Store prompts, inventory and review artifacts in a task-local directory, such as
the project's ignored `output/imagegen/<scene>/`. Keep background source images
outside the skill folder. Preserve task-specific restrictions over these defaults.

## Generate independent edits

Read [prompting and time presets](references/prompting.md). Use the available
image-generation/editing tool; the built-in image tool is preferred and requires
no API key. Inspect local inputs with the image viewer before referencing them.
If no image editing tool is available, report that limit instead of claiming
generated variants or substituting a simple color filter for scene relighting.

- Make one edit per requested time from the **same master**, repeating the fixed
  inventory and landmarks in each prompt. Never use one time variant as the next
  variant's source. Keep the source's style and material identities.
- Request the master's canvas and framing. Do not silently crop, stretch or upscale
  outputs to hide drift; record size mismatches for correction.
- If the master already depicts a requested time, it may be reused unchanged for
  that time, with that decision recorded. Do not infer its time solely from its name.
- Keep the original master unchanged. Save candidates as sibling or versioned
  files; keep all requested accepted deliverables in the workspace.
- Change only the time-dependent properties. Window lights can turn on/off while
  window openings stay put; highlights may move while surface shape stays stable.

## Verify before accepting

Read [verification](references/verification.md) and run the comparison helper.
It checks readability, dimensions, transparency and completeness, and prepares
an overview, focused crops and a review checklist. It never verifies scene geometry
or the depicted time automatically.

Inspect every candidate against the original master and inspect the time series.
Record pass/fail/uncertain plus concrete evidence for each required visual check in
the generated `review.md`. A successful helper exit means **ready for review**.
Accept only when automatic checks pass and every visual check passes. Uncertain
or unviewed checks remain pending. Do not use pixel differences or a similarity
score as proof that building heights or objects were preserved.

For a failed variant, make a targeted correction from the original master with the
failed landmark named explicitly, then rebuild the review in a new directory.
Allow at most two correction attempts per variant in an ordinary run; report any
remaining failures instead of repeatedly generating or weakening the contract.
Recheck corrected variants and series consistency before export.

## Deliver

Report accepted and pending/rejected times separately, output paths, prompt paths,
the master used and the review evidence. Reconfirm source/output hashes from
`review.json` before exporting so the reviewed bytes are the delivered bytes.
Use scene IDs such as `lake_park` and `cyberpunk_city`, and proposed resource names
`pet_background_<scene>_<period>`. Rename existing resources or wire runtime time
selection only when that work is included in the user's request.
