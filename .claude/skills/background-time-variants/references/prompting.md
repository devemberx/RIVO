# Prompting and time presets

Use this as an edit prompt, inserting the run's actual inventory and landmarks.
Keep task-specific restrictions more precise than the general preset.

```text
Use case: lighting-weather
Asset type: companion scene background, time-of-day variant
Input image: the approved master is the edit target and geometric authority.
Primary request: depict this exact place at [requested time].
Fixed structure: [camera, perspective, crop, horizon, buildings and object inventory].
Landmarks: [named roof tops, antennas, road bends, supports, foreground anchors].
Allowed changes: [sky, clouds, existing light emission, exposure, shadows, reflections].
Time treatment: [light direction, sky treatment, window/fixture state for this time].
Style: preserve the master's rendering style, materials and texture identity.
Canvas: [master width x height], identical framing and companion ground anchor.
Constraints: do not change building height, width, roof silhouette, antenna position,
window placement, object count, terrain or shoreline. Do not move the camera,
redesign architecture, add/remove structures, add text or introduce new scene props.
Natural color/exposure differences are allowed; structural redesign is not.
```

Time boundaries in MobiMon follow `docs/DESIGN.md#home-scene` and
`core/core-ui/src/main/java/com/monsters/mobimon/core/ui/CompanionBackground.kt`.
Generate only the requested subset; for a full family use all seven periods:

| Period | VSS hours | Suggested appearance, subject to the scene contract |
| --- | --- | --- |
| midnight | 00–04 | Deep night, weak ambient light, selective existing lights; no sunset-colored horizon |
| sunrise | 05–06 | Low early light, cool-to-warm sky transition, some lights still on |
| morning | 07–11 | Clearer daylight, soft directional shadows, reduced artificial light |
| day | 12–15 | Strong daylight, short/less dominant shadows, minimal artificial glow |
| afternoon | 16–17 | Lower warm daylight and longer shadows |
| sunset | 18–19 | Warm horizon, cooler upper sky, existing lights beginning to dominate |
| night | 20–23 | Night sky, visible artificial light and consistent associated reflections |

These are visual presets, not a solar-position model. Match the source scene and
keep light direction, shadows and reflections internally consistent. Daylight
must be a plausible relighting of the scene, not just the night image brightened.
Window openings remain fixed even when different windows illuminate at night.
Do not add celestial objects unless the task permits them.

Save the exact prompt per variant. For a correction, name the drift and ask for
only that correction from the original master, retaining the full fixed inventory.
For example: "Restore the left tower's original roof height and antenna position;
retain the requested morning lighting and every other fixed landmark."
