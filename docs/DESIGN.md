# Product design

MobiMon is a parked companion for vehicle information, conversation, quests and
personalization. [V5 exports](ui/README.md) own screen geometry;
[Architecture](ARCHITECTURE.md) separates implementation from integration gaps.

## Concept

The loop is **quests → points → accessories → personalization**. Do not restore
legacy XP, levels, evolution or driving-score UI. Follow the
[Park/AAOS contract](ARCHITECTURE.md#vehicle-interaction-authorization): restrictions
preserve drafts and committed data. Expressions supplement facts, never diagnose
vehicles or replace warnings.

## Visual language

- Target the fixed 2560 × 1440 AAOS display; other resolutions/aspect ratios are
  outside scope. Use actual system insets and the [export content bounds](ui/README.md).
  Preserve artwork proportions and font sizes at compatibility density.
- Use `MobiMonTheme`, Twilight colors, bundled Noto Sans KR and shared controls.
  Reflow/scroll for enlarged text and IME height; keep footers, hints and actions
  clear of other controls and system UI. Do not shrink the whole screen to fit.
- Touch bounds are at least 76 × 76dp, with 24dp clearance where possible. Maintain
  4.5:1 text and 3:1 control/icon contrast; pair status color with labels/shapes.
  Order focus logically, trap it in dialogs and restore it on dismissal.
- Loaded routes share `MobiMonParkingBadge`; initial profile loading omits it.
  Standard reference geometry is 344 × 76, 72px from the right and 36px from the
  top. Center icon and label together in both states. Fresh valid nonmoving P at
  zero speed shows “주차 확인됨”; otherwise “주차 후 이용”. Restricted status uses
  red icon/text on the same dark capsule. Keep points/simulation labels separate.
- Conversation retains its own badge beside connection status: 258 × 60 confirmed,
  272 × 60 restricted. Enlarged text expands it without overlapping icon/label.

### Launcher icon and native splash

Use shared Mobi artwork on Night, no wordmark, with adaptive-mask clearance.
The splash ends on the first app frame; these are app adaptations, not SVG references.

## Reusable Compose library and asset handoff

Use `core-ui` primitives first. Feature owners perform
[final visual acceptance](TESTING.md#final-figma-visual-acceptance).
Full-screen SVGs remain references, not runtime assets. Import original feature
icons with feature prefixes; share fonts/artwork in `core-ui` and retain licenses.

### Image asset locations

| Asset | Location |
| --- | --- |
| Mobi/Luna idle | `core/core-ui/src/main/assets/characters/{mobi,luna}/idle_breath/` |
| Mobi warning sprite | `core/core-ui/src/main/assets/characters/mobi/unhealthy/` |
| Luna expressions | `core/core-ui/src/main/assets/characters/luna/{hungry,sick}/` |
| Mobi warning masters | `art/characters/mobi/unhealthy/` (not packaged) |
| Shared artwork/accessories/backgrounds | `core/core-ui/src/main/res/drawable-nodpi/` |
| Feature artwork/icons | Owning module's `res/drawable[-nodpi]/` |
| References / generation drafts | [docs/ui](ui/README.md) / ignored `output/imagegen/`, `tmp/imagegen/` |

Generate variants from approved masters. Preserve identity, proportions, style,
scene geometry, canvas, framing, subject scale/anchor and transparency; change only
requested properties. Check dimensions and compare visually before accepting.
Use replaceable [PetAvatar](ARCHITECTURE.md#state-and-lifecycle) rendering. Mobi normal
and warning idle use sprite atlases with fixed ground anchors; warning crossfades
must not shift layout. Equipped Mobi uses base warning artwork and restores equipment
on recovery. Reduced animation stops frame cycling; source artwork stays unmodified.

#### Luna generation references

Inspect and provide both approved references for every Luna generation/edit,
including poses, expressions, frames and accessories:

- Front: [luna_idle_breath_01.png](../core/core-ui/src/main/assets/characters/luna/idle_breath/luna_idle_breath_01.png).
- Side/back: [Luna_Side_Back.png](../art/characters/luna/references/Luna_Side_Back.png).

Preserve head/body ratio, head/ears, limbs, tail and facial feature proportions
through pose/perspective changes. Never stretch anatomy to fit a canvas/accessory.
Also provide the approved source for variants; reject unintended drift against
both references.

### Home scene

Home and Store share seven local-time backgrounds: Midnight 00–04, Sunrise 05–06,
Morning 07–11, Day 12–15, Afternoon 16–17, Sunset 18–19, Night 20–23. Vehicle
timestamps do not select them; only the separate Debug background preview overrides
the period. Variants retain scene geometry and celestial disk sizes; Midnight has
dark city windows/reflections with road lights on.

Home follows [home.svg](ui/shell/home.svg): preserve its centered original artwork
crop so changing content height does not move the horizon. Store crops within its
cards. Background/tint crossfade for one second; controls remain untinted. The
animated time phrase replaces the SVG subtitle. Notices must not move the main
action; enlarged-text layouts remain scrollable.

## Screens and navigation

The [export index](ui/README.md#screen-index) owns screen inventory. Back closes
keyboard, then dialog/menu, then destination. Menu destinations return Home;
connection returns to its entry route. Explicit Home always opens Home.
Menu closes through close, backdrop, Back or selection; show equipped friend/version
and keep its footer above system UI.

Home/menu counts reflect selected Vehicle card cautions followed by claimable
quests. The bell popup has empty state and a three-card scrolling viewport; each
card opens its owning screen. Quest alerts remain until reward receipt.
Unavailable services explain recovery. Pending actions block duplicates; uncertain
writes reconcile before retry.

## Quests and points

Use catalog rewards and actual repeat eligibility, never SVG sample values or an
assumed daily reset. Show one committed balance as Points or `1,200 P`; unknown is
not zero. Celebrate only committed amounts; duplicate claims do not celebrate
again. Later repository updates replace temporary confirmations. Use persisted
completion dates. Quest/Store point headers align with Home's parking badge;
compact Quest may place balance beside its section heading.

## Customization

Friends, accessories and backgrounds are independent; equipment persists per friend.
Preview stays local until Apply. Purchase confirms ownership, not equipment. Show
compatibility, price and balance; cancellation spends nothing. Owned items Apply
without another purchase; applied items have no redundant action. Insufficient
points show the shortfall and quests. Pending/uncertain writes block duplicates
and preserve selection during recovery. No cash purchase, top-up or conversion.

Reuse committed Home appearance while inventory loads. Keep header, preview, tabs
and cards stable; delayed reads use still placeholders followed by a short card
fade. Show read/save failures inline with Retry.

## Conversation

Use V5 split panels, suggestions and composer; companion/chat stay visible together.
New conversation sits above the composer at the right. Suggestions fill without
sending; omit the change-of-pace suggestion. Signed-out Chat opens connection
settings; authenticated Chat opens conversation. Follow the
[session/provider contract](ARCHITECTURE.md#keyboard-conversation-ui).

Use the system keyboard and [resized layout](ui/conversation/keyboard-input.svg).
Enlarged text prioritizes chat over secondary content. Animate new rounded bubbles
and pending dots only when motion is enabled; existing messages remain still.
Keep attempted turns during recovery. Edit removes only the unanswered turn while
preserving draft/history. Selection/IME composition alone do not dismiss failures.

Show “Copilot 연결됨” only with verified readiness and no active problem; otherwise
“Copilot 확인 중”. Keep chat visible during loading and disable Send until ready.
Network/timeout failures use the existing dialogs, then one inline failure row.
Recheck never resends. Account errors offer connection guidance; access/usage errors
explain GitHub account action and offer Home. Unverified parking shows the
[parking dialog](ui/conversation/parking-required.svg), disables editing and hides
IME; Home/Back retain the draft. AAOS restrictions remove the screen.

Use [recording](ui/conversation/voice-listening.svg) and
[review](ui/conversation/voice-review.svg) geometry. Microphone/Stop and Send are
separate; recording retains Cancel, live waveform and disabled Send. Preserve
26px draft text, the 18px transmission/AI footer and nonoverlapping 76dp touch bounds.
No listening timer or extra review/rerecord caption is shown. No-match/silence
leaves the draft and normal footer in place; actionable permission/service failures
retain recovery guidance.

Permission is requested on microphone activation. Stop combines recognized phrases
into an editable draft; only explicit Send submits. Review permits another recording.
Cancel, Back, background or restrictions stop capture and preserve the previous
draft. Voice can remain usable during Copilot network failure while Send stays
blocked. Hide microphone if no system service exists. The waveform stays flat
before speech detection. Timing/service limits live in Architecture.

The footer names GitHub Copilot, discloses dialogue/companion-name transmission and
warns about AI accuracy. Local clearing does not promise provider deletion.
Spoken replies remain planned and must yield to calls/navigation.

## Vehicle information

Six default cards cover charge, charging, tires, washer, environment and assistance.
Long press opens the 30-card gallery, excluding assigned cards. Highlight active
slot/selection; Confirm saves only a selected alternative locally. Keep gallery
cards the same size and scroll additional choices. Debug fallback values/edits are
simulated, never real vehicle verification.

Badges distinguish Info, Normal, Caution and Unavailable. Missing/stale/invalid is
never Normal. Four-wheel Normal needs all readings; one confirmed warning suffices
for Caution. Specific warnings take priority and partial data stays partial.
Low battery/washer maps to Hungry; vehicle/assistance warnings map to Sick, which
takes precedence. Information-only readings do not imply a fault. Companion
condition uses all evidence, including unselected cards; expressions are not diagnosis.

## AI connection and settings

Show save failures. Debug controls default off; production behavior must follow the
[vehicle integration boundary](ARCHITECTURE.md#vehicle-interaction-authorization).
Spoken replies/vehicle-home display are unavailable; omit Do Not Disturb.

### Copilot connection UI

Configured builds show GitHub's approval QR, separate code and address help;
automatically update status and hide expired codes. Follow the
[authentication lifecycle](ARCHITECTURE.md#copilot-connection-ui).
Verified success uses [connected.svg](ui/connection/connected.svg), the verified
account and “모비와 대화하기” with the equipped friend's name, plus Settings.
Explain local persistence and separate Copilot access checking. Restoration clears
stale errors; failures offer retry/local clearing while retaining committed appearance.
Disconnect requires confirmation and explains local-only removal. Unconfigured
builds disable sign-in. Loading/failure states reuse shared panels where exports
are absent.

## Motion

Motion preserves context/focus; outgoing or restricted controls lose input immediately.
Animation never authorizes a command. The Settings motion switch stops only floating
companion wandering; dragging/idle breathing remain. Unknown/failed preference reads
keep it stationary; in-app scenes/navigation are independent.

Chat reveals from the activated button over stationary Home (300ms), returning to
that bound on Back (220ms). Sample bounds after scroll/insets and constrain touch
bounds to the visible reveal. Shell/drawer transitions use 220ms; connection panels
fade in/out over 180/120ms. Restrictions/expiry replace content immediately. Home
speech bubbles replay on entry/tap/periodic reappearance, not data updates.

## Vehicle launcher

Outside-app companion requires explicit opt-in and overlay permission. Intended
placement is vehicle Home; verify actual placement/lifecycle under the
[platform contract](ARCHITECTURE.md#shared-vehicle-condition-and-overlay).
