# Product design

MobiMon is a parked companion for vehicle information, conversation, quests and
personalization. [V5 exports](ui/README.md) define visual geometry;
[ARCHITECTURE.md](ARCHITECTURE.md) distinguishes implemented support from plans.

## Concept

The product loop is **quests → points → accessories → personalization**. Do not
restore legacy XP, levels, evolution or driving-score UI. Follow the
[parking/AAOS authorization contract](ARCHITECTURE.md#vehicle-interaction-authorization).
Moving/unknown state pauses interaction while preserving drafts and committed data.
Expressions supplement vehicle facts; they never diagnose a vehicle or replace warnings.

## Visual language

- Target the fixed 2560 × 1440px AAOS display. Other display resolutions,
  aspect ratios and arbitrary compact windows are outside the product scope.
  App content excludes system bars; AAOS compatibility density changes its dp
  dimensions. Preserve the target layout with those insets, enlarged text and
  the system keyboard's reduced content height. Use the current
  [export content bounds](ui/README.md), including the larger system bars.
- Fit panel surfaces and bottom action groups to the available height. Reflow
  Menu, Settings, store hints/actions and conversation input together; keep
  footers, retry controls and pending indicators clear of adjacent controls and
  system UI. Preserve font sizes and artwork proportions instead of scaling the
  whole screen to fit.
- Use `MobiMonTheme`, Twilight colors and bundled Noto Sans KR. Follow export
  positions, typography, proportions and icons; use actual runtime insets.
- Reflow or scroll when enlarged text or the keyboard requires it. Controls are at least
  76 × 76dp, with 24dp spacing/edge clearance where possible. Maintain 4.5:1 text
  contrast and 3:1 control/icon contrast; pair status colors with labels or shapes.
- Reuse shared navigation controls and `MobiMonParkingBadge` on every loaded route.
  Keep points and
  simulation labels separate. Order focus by heading, information and actions;
  trap dialog focus and return it to the trigger on dismissal.
- Place the shared parking badge at the top right of standard routes, 72px from
  the content edge and 36px from the content top in the reference layout. It
  moves with its screen during navigation. The initial profile loading screen
  omits it. The 344 × 76 capsule retains the Figma parking icon, text placement,
  dark blue surface and cyan text for confirmed Park. The restricted state keeps
  this geometry and centers its pause icon and label together. It uses the same
  dark blue surface and border, with the conversation parking dialog's red
  status color for the icon and text. Fresh, valid nonmoving `P` at speed zero
  shows “주차 확인됨”;
  every other state shows “주차 후 이용”. Conversation retains its separate Figma
  badge at 258 × 60 when confirmed and 272 × 60 when restricted at reference
  text size, positioned beside its connection badge. Enlarged text expands the
  badge so its icon and label stay separate. Its restricted icon and label are
  also centered together. Vehicle details may describe the specific signal
  or data gap separately.

### Launcher icon and native splash

Use shared Mobi artwork on Night, without a wordmark, with adaptive-mask clearance.
The splash ends on the first app frame. These are app adaptations, not SVG references.

## Reusable Compose library and asset handoff

Use `core-ui` primitives before feature-local equivalents. Feature owners perform
[final visual acceptance](TESTING.md#final-figma-visual-acceptance).

Full-screen SVGs stay in `docs/ui`. Import original icons into the owning feature
with a feature prefix; keep shared fonts/artwork in `core-ui` and preserve licenses.
Do not package full-screen references or generation drafts as runtime UI.

### Image asset locations

| Asset | Location |
| --- | --- |
| Mobi idle sprite / Luna frames | `core/core-ui/src/main/assets/characters/{mobi,luna}/idle_breath/` |
| Luna hungry / sick frames | `core/core-ui/src/main/assets/characters/luna/{hungry,sick}/` |
| Original Mobi warning artwork | `art/characters/mobi/unhealthy/` (not packaged) |
| Shared artwork, accessories and backgrounds | `core/core-ui/src/main/res/drawable-nodpi/` |
| Feature icons/artwork | Owning module's `res/drawable/` or `res/drawable-nodpi/` |
| References | [docs/ui](ui/README.md) |
| Generation drafts | Ignored `output/imagegen/` and `tmp/imagegen/` |

Use approved master assets for variants. Preserve identity, proportions, style,
scene geometry, canvas size, framing, subject scale/anchor and transparency; change
only requested properties. Check dimensions and compare visually before use.

Mobi idle uses a lossless 6 x 4 atlas of all 24 original 1254px RGBA frames.
The atlas retains every source frame pixel; separate frame PNGs are not kept.
Forward playback already contains inhale/exhale; 24-to-01 is visually identical.
The 4.05-second cycle includes the source's repeated extreme poses, with
an independent 6.2-second, +/-2.35-degree seated-pivot tilt. Eyes and sprout are baked
in; there are no synthetic blink/sprout layers. Original source discontinuities
remain strongest at 08-to-09, 13-to-14 and 14-to-15; alpha-correct adjacent-cell blending softens these jumps but cannot repair artwork.
Breathing adds up to 1.2% width/2.4% height; a separate 6.6-second bob adds tiny
settle and lift. All transforms share the seated pivot and preserve layout.

Mobi's collapsed idle uses a lossless 24-frame sprite atlas `mobi_collapsed_sprite.png` (6 x 4 grid of 408px RGBA cells) under `core/core-ui/src/main/assets/characters/mobi/unhealthy/`.
Original collapsed and transition artwork remains under `art/characters/mobi/unhealthy/` for future edits; it is not packaged.
The 200ms normal/warning crossfade is unchanged. The 24-frame animation loop plays continuously over 4.05 seconds with smooth frame interpolation, capturing shivering, sweating, eye movements, and dizziness.
Reduced motion snaps to frame 0 and disables frame cycling.
Equipped Mobi uses base collapsed artwork during warnings and restores its equipped normal look afterward.

Use the replaceable [PetAvatar](ARCHITECTURE.md#state-and-lifecycle) renderer.

### Home scene

Home and store preview share seven 2560 × 1440 backgrounds (five WebP and two PNG).
Local hours select Midnight 00–04, Sunrise 05–06, Morning 07–11, Day 12–15,
Afternoon 16–17, Sunset 18–19 and Night 20–23. A vehicle location timestamp does
not change the scene; the separate Debug background preview override does when
Debug mode is enabled. Vehicle interpretation controls do not change the scene.
Sunrise keeps the morning sun's horizontal position and disk size near the
horizon. Midnight's moon matches the night moon's disk size. It keeps the
buildings and road fixed, turns off city windows and their reflections, and
leaves road lights on. Home
keeps the SVG artwork framing: a centered crop in
the original 2560 × 1268 rectangle at y=76, clipped by the current safe content
starting at design y=96. Changing available height does not recenter the horizon.
Store previews crop within their own cards. Home adds cool tint/daylight shadows;
artwork and tint crossfade for one second.
Controls remain untinted.

Home follows [home.svg](ui/shell/home.svg). Its menu overlays the same scene.
The animated time phrase replaces the SVG subtitle. Availability notices must not
move the main action; enlarged-text layouts remain scrollable.

## Screens and navigation

The [export index](ui/README.md#screen-index) owns the screen inventory. Back closes
the keyboard, then dialog/menu, then destination. Menu destinations return Home;
connection returns to its entry route. Purchase cancellation returns to its preview.
Explicit Home always opens Home.

The menu closes through its close control, backdrop, Back or destination selection.
It shows the equipped friend and app version, with its footer above the system bar.
Preserve the SVG layout at AAOS compatibility density, centering minimum touch
bounds around each row. Reflow only when those bounds overlap or enlarged text
needs more room.

Unavailable services explain the limitation and recovery. Pending actions block
duplicates; uncertain writes offer reconciliation before retry.

## Quests and points

Show catalog values and actual repeat eligibility, never SVG sample rewards or a
universal daily reset. Celebrate only a committed award and its returned amount.
An unknown wallet is not zero.
Use the same committed balance throughout the app, labelled Points or `1,200 P`.
Repeated claims reconcile without another celebration. Later repository updates,
including resets, replace temporary claim confirmations.
The completed quest detail shows the persisted completion date when available;
never substitute the date shown in the SVG example.
At reference size, align the Quest and Store point balance beside the parking
badge like Home: the same 36px bold font, a 48px horizontal gap and a text center
7px below the badge center. The compact Quest list keeps the balance beside its
section heading when the header cannot fit both statuses.

## Customization

Friends, accessories and backgrounds are independent. Preserve equipment per friend.
Preview stays local until Apply commits; purchase confirms ownership only. Show
compatibility, price and wallet balance before purchase; cancellation spends nothing.
On entry, reuse the committed appearance already shown on Home while the Store
refreshes its inventory and catalog. Keep the header, preview, tabs and card bounds
in place. If a read lasts beyond a brief transition, show still card placeholders
in the catalog area; reveal the cards with a short fade when ready. The in-app
character motion preference does not affect Store cards. Show errors and retry inline.

| State | Action |
| --- | --- |
| Unowned | Preview and purchase with explicit price |
| Insufficient points | Show shortfall and offer quests |
| Owned | Apply without another purchase |
| Applied | Mark active; no redundant action |
| Pending/unconfirmed | Block duplicates and reconcile or retry while preserving selection |

No cash purchases, top-ups or conversion.

## Conversation

Use the V5 split panels, empty state, suggestions and composer. Keep the companion
and chat visible together, with a compact New conversation action at the right edge
above the composer. Omit the change-of-pace follow-up suggestion. Suggestions fill
the draft without sending. Home/menu Chat opens connection settings when signed out
and chat when authenticated; the Settings account card opens connection management.
Follow the [session and provider contract](ARCHITECTURE.md#keyboard-conversation-ui).

Use the system keyboard and keep the composer visible above it. The
[keyboard export](ui/conversation/keyboard-input.svg) shows the resized layout.
Enlarged text prioritizes chat and hides secondary content. Controls retain usable
touch bounds at AAOS density. Use soft rounded message bubbles and animate new
bubbles and the pending dots when motion is enabled. Existing messages and reduced
motion remain still.

Keep the attempted user turn during recovery. After a network or timeout dialog
closes, show its inline failure until Edit or Retry. Align the single-line warning
and actions with the [failed-reply export](ui/conversation/reply-failed.svg). Edit
removes the unanswered user bubble while preserving the draft and completed
history; selection and IME composition changes do not dismiss the failure.

When Park and AAOS allow chat, show Copilot readiness and disable Send until the
connection is verified. Keep the destination visible during initial loading. An
in-chat connection badge shows “Copilot 연결됨” only when readiness is verified
without an active problem; every other state shows “Copilot 확인 중”. The detailed
problem remains in the dialog or inline notice. An
offline failure shows the [network dialog](ui/conversation/network-error.svg)
promptly. Recheck uses the [checking dialog](ui/conversation/network-checking.svg)
and never resends the draft. Account errors open connection guidance; access and
usage errors explain what to change in GitHub and offer Home without an immediate
recheck. Online timeouts explain the delayed response. Show one failure notice at a
time: the inline failure appears after the dialog closes. Preserve the draft;
a deliberate Retry may consume additional usage.

Unverified parking shows the [parking dialog](ui/conversation/parking-required.svg),
disables editing and hides the IME. Home and Back preserve the draft. Verified
parking removes the dialog; AAOS restrictions remove the screen.

Use the updated [recording](ui/conversation/voice-listening.svg) and
[review](ui/conversation/voice-review.svg) geometry. The composer contains separate
microphone and Send actions; recording replaces the microphone with Stop and keeps
Send disabled. Cancel, the live 64-bar waveform, Stop and Send retain their exported
positions. Put the recording timer below the composer. Preserve 26px draft text,
24px recording guidance and 18px footer text at reference scale; native touch bounds
remain at least 76dp without overlap at AAOS density.

Ask microphone permission on explicit activation. Stop finishes recognition into an
editable draft; only explicit Send submits. Review retains the microphone for another
recording. Cancel, Back, background and restrictions stop recording and preserve the
previous draft. Denied permission, unavailable models/services, silence and errors
offer keyboard input and a recoverable explanation. Hide the microphone when no
recognizer exists. Review guidance is exposed to accessibility without adding a
second visible caption. Playback remains planned; it must yield to calls/navigation.

Use the existing footer to name GitHub Copilot, disclose dialogue/companion-name
transmission and warn about AI accuracy. Provider retention/training policies and
account limits apply; local clearing does not promise provider deletion.

## Vehicle information

| Observed condition | Presentation |
| --- | --- |
| Low battery / charging required | Hungry expression, actual charge and charging guidance |
| Vehicle issue | Sick expression, affected item and specific warning |
| Current checked items without warnings | Default expression; describe only checked items as normal |
| Missing/stale data | Unavailable, with last update; never imply normality |

Specific warnings take priority; partial data stays partial. See
[current support](ARCHITECTURE.md#current-foundation) before using condition expressions.

The v5 vehicle screen shows six cards by default: battery charge, charging,
tire pressure, washer fluid, outside environment and driver assistance. Long
pressing any card opens a scrollable gallery drawn from 30 VSS-mapped cards at
the same size as the displayed cards. Cards already assigned to any slot are
omitted from the gallery. The active slot and selected alternative are
highlighted; Confirm becomes available after choosing an alternative and saves
that slot's choice locally. The hint below the six cards explains the gesture.
Unavailable or stale signals show missing data
instead of sample values. In the Debug app with no adapter, the screen uses
simulated defaults; the Settings-enabled Debugger can edit every card signal.
The character panel uses the normal, hungry and sick Mobi artwork for the
corresponding observed conditions.

## AI connection and settings

Settings expose save failures. Debug controls are Debug-only and off by default.
Spoken replies and vehicle-home display remain unavailable; Do Not Disturb is omitted.

### Copilot connection UI

Configured builds show GitHub's approval URL as a QR with a separate user code and
address help. Update approval status automatically and hide expired codes. Apply the
[authentication lifecycle rules](ARCHITECTURE.md#copilot-connection-ui).

Authentication success uses [connected.svg](ui/connection/connected.svg) geometry,
with “모비와 대화하기” (using the equipped friend's name) opening chat and Settings
secondary. Show the verified account, local persistence and a note that Copilot access
is checked on Send. Successful restoration replaces prior sign-in errors.
Loading/failures offer retry or local clearing; companion failures retain appearance
with Retry.

Account-row Disconnect opens confirmation and explains that only the local connection
is removed; preserve points/cosmetics. Offer approval again for revoked/expired
credentials. Unconfigured builds disable sign-in.

Loading and failure states have no v5 export; reuse panel typography and controls.

## Motion

Motion preserves context and focus; outgoing/restricted controls lose input
immediately. Animation never authorizes or commits a command. The Settings motion
switch stops the floating companion's autonomous movement on vehicle Home; dragging
and idle breathing remain. It does not change in-app scene or navigation motion.
Unknown or failed preference reads keep the floating companion stationary.

Home's conversation action reveals the destination from the activated button's
rounded bounds over 300ms, with Home stationary underneath. Back closes it toward
the same bounds over 220ms. Sample the button after scrolling and respect runtime
insets. Destination touch targets follow the visible reveal bounds.

The Home speech bubble enters from its tail on entry, pet tap and periodic
reappearance; data updates do not replay it. Other shell/drawer transitions use
220ms. Connection panels fade in over 180ms and out over 120ms; expiry/restrictions
replace content immediately, and countdown ticks keep panel identity. Prototype
delays are not success signals.

## Vehicle launcher

The floating companion can be shown outside the app with explicit opt-in and
overlay permission. Its intended location is vehicle Home; verify placement and
lifecycle on the target vehicle under the
[platform contract](ARCHITECTURE.md#shared-vehicle-condition-and-overlay).
