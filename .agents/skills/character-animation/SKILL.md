---
name: character-animation
description: Create, repair, review and optimize MobiMon character animations from the project's registered character and equipped-item references. Use for idle, movement, status and entrance/exit animation assets or their rendering integration. Requires valid reviewed references before generation; excludes reference-sheet creation, background lighting variants and ChatGPT Pets.
---

# Character animation

Produce motion that preserves the canonical character, anatomical scale and item
fit. Use `art/characters/reference_catalog.json` as the input authority; manifests
own identity, proportions, attachment rules and review provenance. Resolve paths
from the repository root, and helper paths from this skill folder.

## 1. Establish scope and check references

Resolve the requested character, action and optional equipped item from the user
and current code. Ask only for missing choices that change the intended motion.
Keep asset production, runtime integration and unrelated cleanup within the
requested scope. Follow repository issue/branch rules before implementation.

Run the read-only gate (Python 3 with Pillow) for the selected target:

```bash
python3 .agents/skills/character-animation/scripts/preflight.py --repo . --character luna
python3 .agents/skills/character-animation/scripts/preflight.py --repo . --character luna --item accessory:luna_cap
```

The helper checks the current schema, files, hashes, decoded image metadata,
review records, view rectangles and item/base compatibility. It does not verify
identity or motion visually. Read the reported manifests and inspect their actual
canonical and turnaround images before production. Read
[reference rules](references/production.md#reference-authority) for interpretation.

**Do not generate animation if required references are missing, changed without
review, incompatible or unreviewed.** Report the exact missing input or failed
check. Planning and missing-input analysis may continue. Do not fabricate a new
master, refresh hashes to bypass failure or silently create references. Reference
creation is a separate task. A missing Pillow dependency also leaves the gate
pending; use an existing environment or a task-local environment.

To verify changes to the helper, run
`python3 -m unittest discover -s .agents/skills/character-animation/scripts/tests -v`.
The tests exercise the current catalog and invalid copies in temporary directories.

Honor recorded review authority: `accepted_for_reference` with delegated review
does not require a second user signoff. Do not call it user visual approval when
`separate_user_visual_signoff` is false. Passing this gate only permits production
from the references; it never approves generated motion.

## 2. Define the clip contract

Read [production](references/production.md). In an ignored task directory such as
`output/imagegen/character-animation/<run>/`, record the reference hashes and a
short clip contract: action, pose/view, moving and fixed parts, renderer choice,
canvas, canonical anatomical scale, root/ground anchors, intentional root motion,
prop/item policy, timeline, loop/one-shot behavior, entry/exit poses and target
display size. Use `clip.json` as a task record, not a new runtime schema.

Choose one anatomical scale for the clip; do not normalize each frame's bounds.
Do not force standing and seated bodies to the same height. Read tolerances from
the selected manifests as review aids, not universal geometric truth or permission
to distort anatomy. Define action-specific contact phases where necessary.

## 3. Produce motion using the appropriate method

Use source-part transforms for small local gestures when layers, pivots and hidden
surfaces can be handled faithfully. Use explicit key poses and frame sequences for
large pose changes. A sprite sheet is a packaging choice, not a smoother animation
method. Match the existing renderer unless its change is part of the task.

For image generation/editing, use the available image tool and its imagegen skill;
inspect inputs first and supply the canonical and relevant turnaround references
on every generation/correction. Include base and fitted item references for an
equipped clip. Do not let the last generated frame become the sole identity source.
If tools cannot carry the needed inputs or produce coherent motion, report that
limit. Never present a static sheet as a validated animation.

Preserve reference files. Keep candidate frames, layers, prompts, manifests and
review artifacts in the ignored run directory. Read
[production constraints](references/production.md#motion-and-coordinate-rules)
before fitting, cropping or generating intermediate poses.

## 4. Review motion before accepting

Follow [review and export](references/review-export.md). Inspect a contact sheet,
actual-size real-time playback, slow playback, loop seam and transitions to/from
the existing idle pose. For procedural motion, inspect rendered timeline samples
and playback; do not require a sprite export just to fit this workflow.

Record each check as pass/fail/pending with evidence and the exact candidate hash
or code revision. A passing script, plausible static frame or unviewed preview is
not visual approval. If continuous playback cannot be observed, leave motion
review pending and do not claim natural motion. Do not promote failed/pending
outputs as production-ready. Correct specific failures using the same references;
after two unsuccessful correction attempts for the same defect, report it and
reassess the method instead of weakening checks or generating indefinitely.

## 5. Export and integrate only the requested deliverables

Keep canonical art at source quality. Export only reviewed runtime derivatives;
compare resolution and encoding choices at the largest intended display size.
Preserve timing, alpha, scale and crop offsets. Revalidate the optimized output
and bind the final review to its hashes. Report file bytes, decoded pixel memory
estimate and measured runtime behavior separately.

When integration is requested, update paths, dimensions, sampling, timeline and
fallbacks together in the owning renderer; use compose-agent for Compose changes
and the repository's relevant tests. An asset-only task ends with reviewed exports
and an integration handoff, not unsolicited application changes. Never delete an
asset based only on a literal-reference search: expand dynamic paths and account
for first-frame fallbacks and repeated timeline frames.

Deliver reference identity, accepted/pending results, clip/export paths, visual
evidence, size comparisons and checks actually run. Do not claim device behavior
from file validation or an APK build.
