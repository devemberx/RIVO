# Scene examples

These are starting inventories for the current repository images. Inspect the
chosen master and amend them for the actual task. Paths below are repository-relative;
they identify candidates, not a guarantee that the files are unchanged or approved
for a new task. Do not mix different masters within one run.

## Cyberpunk City — `cyberpunk_city`

Source: `core/core-ui/src/main/res/drawable-nodpi/pet_background_cyberpunk_city.png`.

| Fixed structure | Time-dependent appearance |
| --- | --- |
| Tower heights, widths, rooftop profiles, antennae, skyline gaps and building positions | Sky color, cloud arrangement and atmospheric lighting |
| Window grid/openings and existing architectural light fixtures | Individual window illumination and fixture brightness |
| Elevated road's curve, deck thickness, supports and position | Road-light emission and associated glow/reflections |
| Left curved overhang, right frame and both foreground side structures | Surface exposure, highlights and cast shadows |
| Center circular platform's position, ellipse, rim and ground anchor | Platform-rim light and reflections on the same surfaces |
| Plant and planter positions, outlines and count | Leaf shading and ambient color cast |

Useful landmarks: the tall tower/antenna at left, the two tall towers right of
center, the elevated road's bend and supports, the curved left overhang, the
center platform and the right planter. Start review crops with
`skyline=0.16,0.02,0.90,0.66` and `foreground=0,0.58,1,1`; adjust after inspection.
Warm sunset illumination in a master is a time cue, not permission to preserve it
in daylight or midnight variants.

## Lake Park — `lake_park`, display name `호수 공원`

Day source candidate:
`core/core-ui/src/main/res/drawable-nodpi/pet_home_background_day.webp`.
The existing family is `pet_home_background_<period>`; the scene name does not
itself authorize renaming that resource family.

| Fixed structure | Time-dependent appearance |
| --- | --- |
| Mountain ridgelines, lake outline, shoreline bends and distant building positions/heights | Sky color, clouds, haze and illumination of existing distant buildings |
| Curved road, guardrail, supports and vegetation outlines | Shadows, road lighting and leaf shading |
| Foreground frame, dashboard/platform shape and ground anchor | Ambient exposure, highlights and existing-surface reflections |
| Lake surface's boundary and material identity | Sky/sunlight reflections and water highlights within that boundary |

Useful landmarks: the left mountain peak, right forest ridge, distant shoreline,
road/guardrail bend and center platform. Start crops with
`landscape=0,0.38,1,0.70` and `foreground=0,0.64,1,1`; adjust after inspection.
Do not alter terrain, season, vegetation density or lake extent to imply time.
