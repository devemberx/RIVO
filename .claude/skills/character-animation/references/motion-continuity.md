# Motion and transition continuity

Use this for every action, whether procedural, individual frames or an atlas.
Check continuity inside a clip, at its loop seam and between requested clips.
Do not generate every possible state combination unless that is the task.

## Plan motion before frame count

Record duration, rhythm, direction/view, root path, allowed deformation, support/
contact intervals and entry/exit poses. Mark preparation, action extrema, contacts,
releases and recovery as applicable. Define moving parts and stable geometry.
Place key poses at these events rather than distributing arbitrary poses in time.

| Motion example | Useful phases and continuity checks |
| --- | --- |
| Breathing / idle | Rest, inhale, exhale; continuous reversal without head-size drift or foot movement |
| Walk | Contact, down, passing, up on each side; support, stride/travel agreement and alternating weight |
| Run / jump | Anticipation, push-off, flight, landing, recovery; intentional flight and impact |
| Wave / look / celebrate | Anticipation, gesture extrema, reversal, settle; joint arcs and item attachment |
| Hungry / sick | Entry into status pose, sustained movement, recovery; identity and ground anchor |
| Appear / disappear | Visibility and root path, arrival/departure, handoff; endpoint alignment and completion |

These are examples, not required human anatomy or identical rhythms. Use the
character's established short limbs, robot rigidity or stylized weight. Do not
invent a long-legged gait to satisfy a generic walk template. A walk may start
with eight phase keys across two steps; other actions choose their own keys.

Apply the [stage gates](review-export.md#stage-gates) to a timed key preview before
making in-betweens. Planned stepping in a key block is deferred work, not a final
smoothness failure. Compare observed poses and timing with the intended action.

## Choose sampling and inspect spacing

Honor existing frame counts/timing when their renderer is outside task scope.
For a new frame-based clip with no constraint, 24 samples/second is a practical
trial starting point, not a device benchmark or quality guarantee. For a **loop**
of duration T seconds and trial rate F, use N = ceil(T × F) samples at
t_i = iT/N, i = 0..N-1, with duration T/N each. A one-second loop starts with 24
display frames. Pose T helps seam review but is not an extra held frame zero.

For **every one-shot**, author the terminal pose even without an endpoint hold.
Specify whether the final display interval shows it or the next state supplies it
at the boundary. Record the final frame's duration, optional intentional hold,
completion time and next state's first pose/root. Durations must sum to the stated
clip duration; do not accidentally add a sample interval or omit the recovery.
For example, a 600 ms clip with holds [200, 100, 300] displays its terminal pose
from 300 to 600 ms and completes once at 600 ms; a same-pose idle can then take
over with no extra hold. A static review player may freeze the terminal image
after completion, but that is not another interval in the production timeline.
For integrated playback, verify completion fires once on natural completion and
cancelled clips cannot send a stale completion into the replacement state.

Inspect fast arcs, thin moving details and reversals at intended display size.
Where coherent poses still step visibly, add intermediate samples in that segment
if variable timing is supported, or raise the uniform rate and resample the same
duration when permitted. Do not shorten the action accidentally, duplicate frames
to claim more detail, or crossfade bad poses into apparent smoothness. More frames
increase storage and memory without repairing anatomy.

For procedural motion tune continuous paths and event timing, then sample at
events and intervening display times for review. Do not bake a 24-frame sheet
unless delivery requires it. Display refresh rate and distinct authored poses
are different quantities. Optical interpolation or generated in-betweens require
the same anatomy, transparency and temporal review as the original frames.

## Measure in a declared coordinate system

Use numerical tracking for suspected scale, contact, attachment or spacing defects,
or a task that explicitly requires measurements. All clips need visual review;
simple blinks do not need a full-body landmark spreadsheet. Select only landmarks
that can resolve the actual uncertainty.

Use timestamps and meaningful landmarks: root/pelvis, head center/orientation,
near/far hands and feet, and item attachment points. Record measured versus
estimated points; mark hidden points `unavailable` with the occlusion reason,
without inventing coordinates. Follow the [measurement/acceptance distinction](review-export.md#acceptance-evidence):
an unavailable coordinate can have a passed visual alternative, while unresolved
identity, contact or attachment remains pending.

Convert source points through crop restoration, scale, facing transform and root
motion to scene/display coordinates. Let H be the fixed canonical head width at
the target display scale; do not recompute H per frame. Use a fixed camera or
explicitly compensate for camera motion. For successive times compare displacement
d_i = |p_(i+1) - p_i| and velocity v_i = (p_(i+1) - p_i)/(t_(i+1) - t_i).
Account for unequal frame durations. Inspect velocity direction and acceleration
changes against the intended trajectory, including boundary intervals. Smooth
spatial spacing with an unexpected timing pause is still a defect.

The following are **project trial review triggers**, not published perceptual
limits, source-manifest rules or automatic pass/fail thresholds. When using a
diagnostic, record its values, target H and measurement uncertainty; tune for action/view and
stylization with a reason, never merely to hide a failed clip.

| Diagnostic | Initial trigger for closer review |
| --- | --- |
| Unplanned head-size variation, same view/pose | More than 2% from intended projected size, excluding planned rotation/squash |
| Planted contact drift or ground penetration | More than max(1 display px, 0.005 × H) over a fixed-contact interval |
| Item attachment residual | More than max(1 display px, 0.01 × H) from the planned head/item transform |
| Landmark spacing needing review | More than 0.05 × H per 1/24 second: d_i × (1/24)/delta_t; compare with planned motion |
| Unexpected endpoint position/anchor residual | More than max(1 display px, 0.01 × H) from the planned connection |

For H = 200 display pixels these are 2%, 1px, 2px, 10px per 1/24 second and 2px.
The spacing trigger is not a speed cap: fast swings can be correct. For an actual
sampling budget B display pixels, choose delta_t <= B / local speed as a starting
estimate, then inspect the resulting playback; increasing sampling does not change
the speed-normalized diagnostic. Do not arbitrarily slow correct motion to pass.
The manifests' 8% head and 10px sheet-ground estimates are separate static checks,
not animation jitter allowances. Below-threshold motion may still look wrong.
Preflight does not compute these metrics; use recorded/manual measurements or a
task-specific tracker, show evidence and inspect playback.

## Preserve contacts, weight and attachments

For a planted foot or held prop define the fixed contact point and time interval.
During toe/heel roll the active contact changes; do not demand that the entire foot
bitmap stay still. During swing/flight remove the plant constraint intentionally
and check clearance. Body motion and weight transfer should agree with support.
Grounded walking should not acquire unintended flight. Do not force smooth
velocity through an intentional impact: mark the event and inspect preparation,
compression and recovery rather than applying a universal acceleration ceiling.

In an in-place locomotion cycle feet move relative to the root. Review with the
intended scene travel or scrolling ground: for straight travel, root speed =
stride displacement / full-cycle duration. Test planted contacts in that combined
scene, not raw sprite coordinates. If travel is handled by the app, record its
speed and avoid baking translation a second time. Uncoordinated screen movement
and gait creates foot sliding even when individual poses look correct. A gesture
or idle with no travel keeps its root fixed except for declared body motion.

For flexible items add an attachment and settling contract to the rigid-fit rules
in [production](production.md#motion-and-coordinate-rules).

## Join clips and handle loops

List requested connections and actual consumer states in the clip record: source
and destination pose/phase, facing, anatomical scale, root/ground, velocity,
equipment/props, blend or bridge duration and interruption policy. Review pairs
together, not only each clip in isolation.

- For a seamless loop compare the extrapolated pose at T with pose zero and
  incoming/outgoing velocities. The last sample normally differs from the first
  by one time step; requiring identical samples creates a hold. For traveling
  cycles subtract intended stride displacement for pose/seam comparison, then
  preview at least three cycles with accumulated travel.
- Reuse compatible boundary poses or author a short bridge between incompatible
  ones, such as seated idle to standing walk. Keep anatomical scale stable; do
  not enlarge seated bodies to standing height. Carry root position and intended
  velocity into the next motion without teleporting.
- Crossfade only compatible silhouettes/anchors where overlap is visibly clean.
  It cannot replace standing up or missing limb trajectories. Check double limbs,
  floating props and scale changes during the handoff.
- For interruptions specify blending from the current phase, a recovery pose or
  immediate stop as appropriate. Render the latest requested character/item state
  and handle completion/cancellation consistently. Do not delay a safety-required
  state update merely to finish an animation gracefully.
- Test start, completion, requested reversals, mid-motion interruption and reduced
  motion when integrated. An exported preview cannot verify runtime behavior.

Record evidence as phase/frame/time ranges, actual-size playback, measured
residuals and pass/fail/pending observations. Recheck changed segments plus their
neighbors and boundaries. Final acceptance needs viewed playback of the whole
clip and requested connections; frame counts and measurements do not guarantee
naturalness.

## Sources and limits

Phase examples follow [Animation Mentor's walk-cycle workshop](https://www.animationmentor.com/blog/tutorial-animating-human-walk-cycle/).
Clip motion versus scene travel is illustrated in [Epic's root-motion documentation](https://dev.epicgames.com/documentation/unreal-engine/root-motion-in-unreal-engine).
These explain principles, not a dependency on those rigs/engines. Diagnostic
numbers above are local starting heuristics requiring real asset review.
