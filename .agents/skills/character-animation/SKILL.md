---
name: character-animation
description: Create, repair, review and optimize MobiMon character animations from the project's registered character and equipped-item references. Use for idle, movement, status and entrance/exit animation assets or their rendering integration. Requires valid reviewed references before generation; excludes reference-sheet creation, background lighting variants and ChatGPT Pets.
---

# Character animation

Produce motion that preserves canonical identity, anatomical scale and item fit.
Use `art/characters/reference_catalog.json` as the input authority. Resolve input
paths from the repository root and helper paths from this skill folder.

## 1. Establish scope and qualify inputs

Resolve character, action and optional equipped item from the user and current
code. Ask only for missing choices that change the intended motion. Follow the
repository issue/branch rules before implementation; keep asset production and
runtime integration within the requested scope.

Run the read-only input checks with Python 3.9+ and Pillow:

```bash
python3 .agents/skills/character-animation/scripts/preflight.py --repo . --character luna
python3 .agents/skills/character-animation/scripts/preflight.py --repo . --character luna --item accessory:luna_cap
```

Exit **2** means file/metadata checks passed but current-input review is pending;
exit **1** means invalid inputs. Manifest review fields alone do not prove that
current contract text, measurements and images were reviewed. Inspect the reported
manifests and actual images and establish a byte-bound review using
[reference authority](references/production.md#reference-authority). Re-run with
`--reviewed-inputs <input-review.json>`; exit **0** means the inventory matches that
review assertion. Verify its evidence and authority before treating it as ready.
The helper never authenticates reviewers or approves generated motion.

**Do not generate from missing, incompatible or unreviewed inputs.** Planning may
continue. Do not fabricate masters, silently create references or refresh a review
inventory merely to bypass a mismatch. Reference creation is a separate task.
Missing Pillow leaves checks pending; use an existing or task-local environment.
Recorded delegated review does not require a second user signoff; retain its
provenance and never describe it as separate user visual approval.

## 2. Define the clip and its review route

Read [production](references/production.md) and the planning/sampling sections of
[motion continuity](references/motion-continuity.md). Record a compact `clip.json`
in an ignored run directory such as `output/imagegen/character-animation/<run>/`.
Use the [clip example](references/production.md#task-local-clip-record), adapting
fields to the chosen method; this is a task record, not a runtime schema.

Before expensive production, establish a working playback route and observer using
[review setup](references/review-export.md#review-setup). If continuous observation
is unavailable, arrange a user-review handoff or report the capability limit before
producing a full sequence. Do not assume a screenshot tool can observe playback.

## 3. Produce and review in stages

Use source-part transforms for local gestures when layers, pivots and revealed
surfaces can be handled faithfully; use key poses and frame sequences for large
pose changes. Match the existing renderer unless changing it is requested.

For raster generation/editing, use the imagegen skill and available image tool.
Inspect and supply canonical and relevant turnaround references on every call;
include base and fitted-item references for equipped motion. Supply the bracketing
accepted keys for in-betweens, not just the previous generated frame. Report tools'
input/coherence limits. Preserve masters and keep candidates and prompts in the
ignored run directory.

Apply the [stage gates](references/review-export.md#stage-gates): approve key poses
and their timing before filling segments, then review the complete clip and its
requested connections. Follow [coordinate rules](references/production.md#motion-and-coordinate-rules)
and action-specific continuity guidance. Numerical diagnostics are optional tools
for suspected drift/contact/spacing defects, not a requirement for every gesture.

For looping animation review, deliver the dark card-style
[comparison HTML](references/review-export.md#comparison-html) by default: before
and after on one clock, equipped variants, speed/background/size controls, seek
and key poses. Use the helper for procedural Canvas adapters as well as atlas
players. For new motion without an earlier clip, hold a reviewed reference pose
on the left and label it as a still reference. Do not bake a procedural clip just
to fit a frame-only review tool.

## 4. Accept only observed, evidenced results

Use the [acceptance checks](references/review-export.md#acceptance-evidence).
Record stage, candidate hash or code/diff identity, observer, evidence and verdict.
Measurement availability is separate from acceptance; use the documented visual
alternative for occluded points. A required check still pending or failed blocks
that stage; work deferred to a later stage does not block earlier stages.

Correct specific defects against the same references and recheck their neighbors
and boundaries. After two unsuccessful corrections of the same defect, report it
and reassess the method. Final acceptance requires observed whole-clip playback
and requested handoffs; static sheets and scripts cannot establish natural motion.

## 5. Export and hand off

Follow [export and evidence retention](references/review-export.md#optimize-without-changing-approved-motion).
Revalidate final derivatives and bind evidence to final hashes. Apply encoding
comparisons only to relevant raster exports. Report stored bytes, decoded memory
estimates and measured device behavior separately, marking unmeasured values.

For requested integration, update paths, dimensions, sampling, timing and fallbacks
together in the owning renderer; use compose-agent for Compose changes and the
repository's applicable tests. Asset-only work ends with reviewed exports and a
loader handoff. Never delete assets using literal references alone: expand dynamic
paths and account for fallbacks and repeated timeline frames.

Deliver exports, reference identity, clip/review records, durable evidence location
and checks actually run. Keep motion, evidence-retention and runtime-integration
statuses separate; an APK build cannot verify device behavior.

For helper changes, run:

```bash
python3 -B -m unittest discover -s .agents/skills/character-animation/scripts/tests -v
```
