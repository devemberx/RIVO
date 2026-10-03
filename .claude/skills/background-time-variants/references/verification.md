# Verification

The helper needs Python 3.9+ and Pillow. Use an existing environment or install
Pillow in a task-local virtual environment. It creates comparison artifacts only;
it never changes source/background artwork or calls a generation service.

Run from the repository root, using paths relative to that root or absolute paths:

```bash
python3 .agents/skills/background-time-variants/scripts/prepare_review.py \
  --scene-id cyberpunk_city \
  --master core/core-ui/src/main/res/drawable-nodpi/pet_background_cyberpunk_city.png \
  --periods sunrise day night \
  --variant sunrise=output/imagegen/cyberpunk_city/sunrise.png \
  --variant day=output/imagegen/cyberpunk_city/day.png \
  --variant night=output/imagegen/cyberpunk_city/night.png \
  --region skyline=0.16,0.02,0.90,0.66 \
  --region foreground=0,0.58,1,1 \
  --output-dir output/imagegen/cyberpunk_city/review-01
```

Omit `--periods` for the seven-period family. `--variant` labels must match the
requested periods; use a new review directory for each run. Crop coordinates
are normalized left/top/right/bottom positions within the unchanged canvas.

Outputs are `review.json`, `review.md`, `overview.png` and `focus_<name>.png`.
JSON records source paths/hashes, metadata, failures and pending visual checks.
Exit 0 means automatic checks passed and visual review is required; exit 1 means
image checks failed; exit 2 means invalid arguments or an unusable master.
The script checks files decode as single images with matching dimensions and
transparency presence; it cannot identify buildings or prove geometry/lighting.
Missing or unreadable variants remain failures; they are not dropped from the run.
Identical file hashes receive a warning, not proof that different times are depicted.

## Required visual checks

Inspect the master and each candidate at useful resolution, using full-size inputs
as well as overview/focus images. A small contact sheet alone cannot verify antennas
or window grids. Record pass/fail/uncertain and specific evidence in `review.md`:

| Check | Evidence to inspect |
| --- | --- |
| Framing and perspective | Stable crop, horizon, vanishing directions and ground anchor; no camera zoom/shift |
| Building/terrain geometry | Roof heights, widths, antennas, skyline gaps, mountain ridgelines and shoreline bends |
| Objects and infrastructure | Same count, identity, location and outlines of roads, supports, plants, frames and platform |
| Window/fixture topology | Same openings and fixture locations; emission may differ without replacing architecture |
| Time and light coherence | Requested period is apparent; sky, light direction, shadows, windows and reflections agree |
| Art quality and series continuity | Stable style/materials, no warped edges, doubled objects, seams or unexplained structure changes between periods |

Mark inapplicable details with a reason within the relevant check; do not skip the
check itself. Clouds and color casts should not cause rejection unless they violate
the task. A new roof, missing support or shifted platform fails even if the scene
looks attractive. Dark exposure that hides a landmark makes that check uncertain,
not passed. Reused source images still need time-appropriateness review.

Final acceptance requires every automatic and visual check to pass. The reviewer
records the verdict in `review.md`, tied to the hashes in `review.json`; the helper
leaves all visual checks pending and never writes a semantic pass. If any image
changes, regenerate the comparison in a new directory and repeat its review. Verify
hashes immediately before delivery/export. Do not claim live generation quality
from tests of the helper or from existing resource images.
