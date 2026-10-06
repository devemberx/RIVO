These test-only references were captured from the unchanged renderer at PR #408
commit `9e36b37e71d3a80ce9ceed045dceb6d1285f5834` using Robolectric API 34 native
graphics on macOS ARM64. The original whole-loop SHA-256 assertions passed during
capture for all three appearances:

| Appearance | Original full-pixel loop SHA-256 |
| --- | --- |
| normal | d732b0b1e284e849af8268956d7546da0bf65fcd025fa750b571a79ddfe00d62 |
| headphones | 4e76e3094aeef56d22b92279ce294d2f02eff56e7e814bce39fb5904ad5aeec4 |
| goggles | acf4dea7d407f83202384d5deae11178dcbb7806fa7c3be1377a38aeaa0214ca |

Each `.argb.gz` contains 241 consecutive 64x64 ARGB byte arrays, covering 0 through
6000 ms at 25 ms intervals. Each channel averages a 4x4 cell of the 256x256 native
render, with RGB premultiplied by alpha before averaging. This avoids unstable
straight RGB near transparent edges. The test bounds both the maximum cell error
and the mean frame error; negative controls verify missing bubbles, wrong equipment
and shifted poses are rejected. `assets.sha256` pins all 11 packaged WebP/morph
files exactly, independently of native rasterization. References belong only to
test resources and are not packaged into APKs. Update them only after intentional
artwork/timeline changes have been reviewed; CI failures alone are not a reason to
regenerate them.
