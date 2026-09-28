# Product design

MobiMon is a parked companion for vehicle information, conversation, quests and
personalization. [UI exports](ui/README.md) own screen geometry;
[Architecture](ARCHITECTURE.md) separates implementation from integration gaps.

## Concept

The loop is **quests → points → accessories → personalization**. Do not restore
legacy XP, levels, evolution or driving-score UI. Follow the
[Park/AAOS contract](ARCHITECTURE.md#vehicle-interaction-authorization): restrictions
preserve drafts and committed data. Expressions supplement facts, never diagnose
vehicles or replace warnings.

## Visual language

- Target 2560 × 1440 AAOS only, with actual system insets and
  [export content bounds](ui/README.md). Preserve artwork proportions and compatibility-density fonts.
- Use `MobiMonTheme`, Twilight colors, Noto Sans KR and shared controls. Reflow/scroll
  for enlarged text and IME; keep actions/footers clear. Do not shrink whole screens.
- Touch bounds are at least 76 × 76dp, with 24dp clearance where possible. Maintain
  4.5:1 text and 3:1 control/icon contrast; pair color with labels/shapes. Keep logical
  focus order, trap focus in dialogs and restore it on dismissal.
- Loaded routes use `MobiMonParkingBadge`; initial loading omits it. Fresh nonmoving
  P shows “주차 확인됨”; otherwise show “주차 후 이용” with red icon/text. Keep
  points/simulation labels separate. Conversation uses its own adjacent badge.
  Badge geometry follows exports; enlarged text must not overlap icon/label.

- On an active Vehicle, Store, or Quest route, losing verified Park shows a
  blocking, route-specific parking interruption dialog over the current screen.
  The restricted badge, pause icon, explanation, preserved-progress note and
  Home action follow the `수정본_v5` Figma frames (Vehicle `770:2`, Store
  `770:155`, Quest `770:282`). Hide any underlying selector or reward dialog;
  restore the route when verified Park returns if the user has not gone Home.
  Initial unavailable vehicle data stays in each route's ordinary unavailable
  state. Pending writes receive no success presentation after interruption;
  committed inventory and rewards continue to come from repository observation.

### Launcher icon and native splash

Use shared Mobi artwork on Night, no wordmark, with adaptive-mask clearance.
The splash ends on the first app frame; these are app adaptations, not SVG references.

## Reusable Compose library and asset handoff

Use `core-ui` primitives first.
Full-screen SVGs remain references, not runtime assets. Import original feature
icons with feature prefixes; share fonts/artwork in `core-ui` and retain licenses.

### Image asset locations

| Asset | Location |
| --- | --- |
| Character frames/sprites | `core/core-ui/src/main/assets/characters/<friend>/<variant>/<action>/` |
| Mobi warning masters | `art/characters/mobi/unhealthy/` (not packaged) |
| Shared artwork/accessories/backgrounds | `core/core-ui/src/main/res/drawable-nodpi/` |
| Feature artwork/icons | Owning module's `res/drawable[-nodpi]/` |
| References / drafts | [docs/ui](ui/README.md) / ignored `output/imagegen/`, `tmp/imagegen/` |

Generate from approved masters, changing only requested properties. Preserve identity,
proportions, style, canvas, framing, geometry, anchors and transparency; compare variants
visually and check dimensions. Use replaceable [PetAvatar](ARCHITECTURE.md#state-and-lifecycle)
rendering. Accessory/warning variants retain approved artwork; animation and crossfades
must not move ground anchors or layout. Reduced motion uses a still frame.

#### Luna generation references

Every Luna generation/edit must include both approved references; variants also
include their approved source:

- Front: [luna_idle_breath_normal_01.png](../core/core-ui/src/main/assets/characters/luna/normal/idle_breath/luna_idle_breath_normal_01.png).
- Side/back: [Luna_Side_Back.png](../art/characters/luna/references/Luna_Side_Back.png).

Preserve head/body ratio, ears, limbs, tail and facial proportions through pose and
perspective changes. Never stretch anatomy to fit accessories/canvas; reject drift.

### Home scene

[Home/Store backgrounds](../core/core-ui/src/main/java/com/monsters/mobimon/core/ui/CompanionBackground.kt)
share seven periods selected by VSS time: Midnight 00–04,
Sunrise 05–06, Morning 07–11, Day 12–15, Afternoon 16–17, Sunset 18–19,
Night 20–23. `Vehicle.CurrentLocation.Timestamp` and its Debug VSS interpretation
control select the period; device time does not. Preserve approved scene geometry
and celestial disk sizes; Midnight retains dark windows, reflections and road lights.

Follow [home.svg](ui/shell/home.svg): keep the centered artwork crop and horizon stable
when content height changes. Store crops within cards. Crossfade background/tint
without tinting controls; notices must not move the main action. Use the animated
time phrase rather than the export subtitle, with scrolling for enlarged text.

## Screens and navigation

[Exports](ui/README.md#screen-index) own screen inventory and geometry. Back closes
keyboard, then dialog/menu, then destination. Menu destinations return Home;
connection returns to its entry route. Explicit Home opens Home. Menus close through
close/backdrop/Back/selection, with equipped friend/version and safe footer placement.

[Shell references](ui/README.md#shell) define menu and notification geometry.
Home shows a notification dot, and Menu shows a count only when positive. Opening
notifications expands the menu panel; reduced motion switches immediately. Back
returns to Menu, while Close/backdrop returns Home. The header stays fixed as
vehicle cautions and claimable rewards scroll; quest alerts remain until receipt.

Unavailable services explain recovery. Pending actions block duplicates; uncertain
writes reconcile before retry.

## Quests and points

Use catalog rewards and actual repeat eligibility, not export sample values or an
assumed daily reset. Show one committed Points balance; unknown is not zero.
Celebrate only committed amounts, once per claim; later repository data replaces
confirmations. Show persisted completion dates. Align point headers with parking
badges; compact Quest may place balance beside its heading.
Keep the claim button and quest card in place while saving, with progress shown in
the button. After commit, show one centered reward popup with a short entrance
motion, a consistently framed happy companion, and amounts labeled P; reduced
motion displays the popup immediately.

## Customization

Friends, accessories and backgrounds are independent; equipment persists per friend.
Preview stays local until Apply. Purchase grants ownership, not equipment; owned
items Apply without another charge. Show compatibility, price, balance and shortfall.
Cancellation spends nothing; applied items have no redundant action. No cash/top-up/conversion.

Keep header, preview, tabs and cards stable during loading; reuse committed appearance
and show still placeholders. Pending/uncertain writes block duplicates and preserve
selection. Read/save failures offer inline Retry.

The 200-point `background:star_hanger` (별빛 모빌) uses the supplied body and glow
canvases on one continuous mesh, suspended from the top center. Chain joints between
the ribbon, star, moon and tip swing with increasing phase delay; solid ornaments
retain their shape. Glow follows the same joints and fades in/out independently.
It occupies the background slot, is shared by both friends, and appears on Home and
in Store previews; reduced motion shows a still hanger.

## Conversation

Use split companion/chat panels, suggestions and a system-keyboard composer.
Suggestions fill without sending; omit change-of-pace. Signed-out Chat opens connection
settings. Follow the [session contract](ARCHITECTURE.md#keyboard-conversation-ui)
for retained screen history and provider context, and full New conversation reset;
keep New conversation reachable above the composer while messages exist. It deletes
only the active companion's previous local thread; switching companions restores
their separate current threads. Missing names receive no invented fallback title.

Recovery copy says “이전 대화 기록은 보존돼요.” Network failures/timeouts use the
[network popup](ui/conversation/network-error.svg), including when voice is available.
Badges show only “Copilot 확인 중” or verified “Copilot 연결됨”. Recheck never resends;
after recovery, preserve failed turns for explicit Edit/Retry, with the composer empty
until Edit restores the failed text. Account/access/usage failures
explain appropriate account recovery. [Parking recovery](ui/conversation/parking-required.svg)
blocks editing and hides IME; AAOS restrictions remove the screen.

After Send, show the text in its bubble and a waiting hint in the empty composer.
Canceling the reply returns the text to the composer for editing; Home clears it.

Use [keyboard](ui/conversation/keyboard-input.svg), [recording](ui/conversation/voice-listening.svg)
and [review](ui/conversation/voice-review.svg) geometry; enlarged text prioritizes chat.
Microphone/Stop and Send are separate, with Cancel, waveform and disabled Send during
capture. Keep the Copilot disclosure unchanged below the composer; place voice status
above it. Make the empty-field hint quieter than typed text but legible. Hide suggestions
during permission/capture; hide microphone if no system service exists. No listening timer
or extra review/rerecord caption; waveform stays flat before speech detection.

The native dialog handles permission; the first denial needs no additional hint.
Show settings guidance only when the permission dialog cannot reopen. An accepted
microphone request clears typed input, including when permission is denied afterward.
Returning Home clears typed input; backgrounding and restrictions alone retain it.
Stop produces editable text; only explicit Send submits. Cancel preserves the current
draft. Errors retain confirmed text for review;
no-match/silence leaves the cleared composer and disclosure in place.
Network popups release the microphone and retain unsent drafts or failed turns.

Exports may show older recovery copy or visible history; use the current session
behavior with their geometry. Footer names GitHub Copilot, discloses transmitted
conversation, available name/time and AI uncertainty. Local clearing does not promise
provider deletion. Spoken replies remain planned and must yield to calls/navigation.

## Vehicle information

Six default cards use a gallery that excludes assigned cards; Confirm saves a selected
alternative locally. Keep card sizes stable and scroll additional choices. Debug values
are simulations, not vehicle verification.

Distinguish Info, Normal, Caution and Unavailable. Missing/stale/invalid is never Normal;
four-wheel Normal requires all readings, while one confirmed warning permits Caution.
Specific warnings take priority and partial data stays partial. Low battery/washer
maps to Hungry; vehicle/assistance warnings map to Sick with precedence. Condition
includes unselected warnings; information-only values and expressions are not diagnosis.

## AI connection and settings

Show save failures. Debug controls default off; production behavior must follow the
[vehicle integration boundary](ARCHITECTURE.md#vehicle-interaction-authorization).
Spoken replies/vehicle-home display are unavailable; omit Do Not Disturb.

### Copilot connection UI

Configured builds show GitHub approval QR, code and address help; update status and
hide expired codes. Unconfigured builds disable sign-in. Follow the
[authentication contract](ARCHITECTURE.md#copilot-connection-ui).

[Verified success](ui/connection/connected.svg) shows the account and equipped friend's
conversation action plus Settings. Explain local persistence and separate Copilot
readiness. Network failures omit connection steps and offer retry without local
clearing; other authentication failures retain the steps and clearing option.
Disconnect confirms local-only removal.
Reuse shared loading/failure panels where exports are absent.

## Motion

Respect the shared motion preference; animation never authorizes commands. Settings
controls only floating wandering, with unknown/failed reads keeping it stationary;
dragging, idle breathing and in-app motion are independent.

[Shell motion](../app/src/main/java/com/monsters/mobimon/ui/MobiMonApp.kt) reveals chat
from its triggering button over stationary Home and returns to that bound.
Use current insets and visible touch bounds. Restrictions/expiry replace content
immediately; new bubbles/pending dots animate only when enabled. Existing messages
stay still; Home speech replays on entry/tap/interval, not data updates.

## Vehicle launcher

Outside-app companion requires explicit opt-in and overlay permission. Intended
placement is vehicle Home; verify actual placement/lifecycle under the
[platform contract](ARCHITECTURE.md#shared-vehicle-condition-and-overlay).
