# Product design

MobiMon is a parked companion for vehicle information, conversation, quests and
personalization. [UI exports](ui/README.md) own screen geometry;
[Architecture](ARCHITECTURE.md) owns implementation and integration limits.

## Concept

The loop is **quests → points → accessories → personalization**. Do not restore
legacy XP, levels, evolution or driving-score UI. Park/AAOS restrictions preserve
drafts and committed data. Expressions supplement facts; they never diagnose a
vehicle or replace warnings.

## Visual language

- Target 2560 × 1440 AAOS with actual system insets and export content bounds.
  Preserve artwork proportions and compatibility-density fonts.
- Use `MobiMonTheme`, Twilight colors, Noto Sans KR and shared controls. Reflow or
  scroll for enlarged text and IME; keep actions visible.
- Touch bounds are at least 76 × 76dp, with 24dp clearance where possible. Use
  4.5:1 text and 3:1 control/icon contrast, redundant status cues and logical focus.
  Trap focus in dialogs and restore it on dismissal.
- Loaded routes show `MobiMonParkingBadge`; initial loading omits it. Fresh
  nonmoving P shows “주차 확인됨”; other states show “주차 후 이용” with red icon/text.
  Keep points and simulation labels separate. Chat has its own adjacent badge.

Losing verified Park on Vehicle, Store or Quest shows the route's blocking parking
dialog. Hide underlying selectors/reward dialogs; restore the route if Park returns
before Home is chosen. Pending writes show success only after repository commit.
Initial unavailable data uses the route's ordinary unavailable state. Follow the
[parking export](ui/vehicle/parking-required.svg).

### Launcher icon and native splash

Use shared Mobi artwork on Night, without a wordmark and with adaptive-mask
clearance. The splash ends on the first app frame.

## Reusable Compose library and asset handoff

Use `core-ui` primitives first. Full-screen SVGs are references, not runtime assets.
Prefix feature icons; share fonts/artwork in `core-ui` and retain licenses.

### Image asset locations

| Asset | Location |
| --- | --- |
| Character frames/sprites | `core/core-ui/src/main/assets/characters/<friend>/<variant>/<action>/` |
| Mobi warning masters | `art/characters/mobi/unhealthy/` (not packaged) |
| Shared artwork/accessories/backgrounds | `core/core-ui/src/main/res/drawable-nodpi/` |
| Feature artwork/icons | Owning module's `res/drawable[-nodpi]/` |
| References / drafts | [docs/ui](ui/README.md) / ignored `output/imagegen/`, `tmp/imagegen/` |

Generate from approved masters and change only requested properties. Compare
identity, proportions, framing, anchors, transparency and dimensions; reject drift.
Use replaceable `PetAvatar` for rendering. Variants and crossfades keep ground
anchors fixed; reduced motion uses still frames.

#### Luna generation references

Every Luna generation/edit includes both approved references; variants also include
their approved source:

- Front: [luna_idle_breath_normal_01.png](../core/core-ui/src/main/assets/characters/luna/normal/idle_breath/luna_idle_breath_normal_01.png).
- Side/back: [Luna_Side_Back.png](../art/characters/luna/references/Luna_Side_Back.png).

Preserve head/body ratio, ears, limbs, tail and facial proportions across poses.

### Home scene

[Home/Store backgrounds](../core/core-ui/src/main/java/com/monsters/mobimon/core/ui/CompanionBackground.kt)
use VSS time, not device time: Midnight 00–04, Sunrise 05–06, Morning 07–11,
Day 12–15, Afternoon 16–17, Sunset 18–19, Night 20–23. Debug uses the same VSS
interpretation controls. Keep the approved crop, horizon and ground anchors stable
as text grows; crossfade only the scene/tint, not controls. Midnight keeps its dark
windows, reflections and road lights. Use the animated Home time phrase rather than
the export subtitle.

## Screens and navigation

[Exports](ui/README.md#screen-index) own screen inventory and geometry. Back closes
keyboard, then dialog/menu, then destination. Menu destinations return Home;
connection returns to its entry route. Menus close through close, backdrop, Back or
selection. Notifications show a Home dot and positive Menu count; opening them
expands the Menu panel. Keep the header fixed while alerts scroll; quest alerts
remain until receipt. Pending actions block duplicates, and uncertain writes
reconcile before retry.

## Quests and points

Use catalog rewards and actual repeat eligibility, not export values or an assumed
daily reset. Show one committed Points balance; unknown is not zero. Celebrate only
committed amounts, once per claim, and show persisted completion dates. Keep claim
controls in place while saving. The reward popup labels amounts P; reduced motion
shows it immediately. Quest detail preserves list scroll on Back and keeps
“작은 도전, 큰 여정” in both views.

## Customization

[Store](../feature/feature-customization/src/main/java/com/monsters/mobimon/feature/customization/CustomizationScreen.kt)
uses a preview with its action on the left and a catalog on the right. Backgrounds
use two columns; friends, clothes, effects and props use three. Clothes can preview
each friend; Space groups the existing background slot into backgrounds, effects
and props. Both friends show their first sprite frame immediately, then animate
when the remaining frames load; still cards use the same first frame. The owned filter limits the catalog.
Purchase confirmation shows price and remaining points before committing; pending
writes retain selection. Enlarged text uses scrolling panels.

Friends, accessories and backgrounds are independent; equipment persists per
friend. Preview stays local until Apply. Items can be purchased for either owned
friend without switching companions; purchase grants ownership, not equipment.
Owned items Apply without another charge. Show compatibility, price, balance and
shortfall. Cancellation spends nothing; pending writes block duplicates, retain
selection and offer Retry on failure. There is no cash/top-up/conversion.

The 200-point `background:star_hanger` (별빛 모빌) is shared by both friends and
occupies the background slot. Use the supplied body/glow artwork on Home and in
Store previews; keep its chain and ornaments connected in motion. Reduced motion
shows a still hanger.

## Conversation

Use split companion/chat panels, suggestions and a system-keyboard composer.
Suggestions fill without sending. Signed-out Chat opens connection settings.
Mobi speaks curious rabbit banmal and Luna quietly caring cat banmal, without
habitual animal suffixes, emojis or stage directions. Current-thread retention
and New conversation follow the
[session contract](ARCHITECTURE.md#keyboard-conversation-ui). Keep New conversation
reachable above the composer; do not invent a name when one is absent.

Network failures use the [recovery popup](ui/conversation/network-error.svg),
say “이전 대화 기록은 보존돼요.” and retain failed turns for explicit Edit/Retry. Show “Copilot 확인 중” until readiness is
verified, then “Copilot 연결됨”. Parking recovery blocks editing and hides IME;
AAOS restrictions remove the screen. After Send, show the sent bubble and a waiting
hint; canceling a reply restores its text for editing, while Home clears the composer.

Follow the [keyboard](ui/conversation/keyboard-input.svg),
[recording](ui/conversation/voice-listening.svg) and
[review](ui/conversation/voice-review.svg) layouts. Capture has separate Stop and
Send actions; Stop yields editable text, only Send submits, and errors retain
confirmed text. Microphone permission is explicit; an accepted request clears typed input,
even if permission is later denied. Settings guidance appears when the system
dialog cannot reopen. Network recovery releases capture and keeps
unsent drafts. Keep the Copilot disclosure below the composer: transmitted dialogue,
available context/debug values and AI uncertainty. Local clearing does not promise
provider deletion. Spoken replies remain planned.

Replies keep a friendly companion voice: short paragraphs for explanations,
bullets for multiple tips and numbered steps when order matters. Keep relevant
conditions/warnings and place citations beside supported claims. Manual answers
show a regular-weight source heading with smaller entries/markers; raw tool output stays hidden. Follow new replies unless
reading history; the return button shows typing dots or a down arrow. Render
strong Markdown emphasis; failed-turn Edit reverses the short arrival motion
in place unless motion is reduced,
and keep companion artwork clear of its caption when the keyboard opens.

## Vehicle information

[Vehicle exports](ui/README.md#vehicle) define layout. Six default cards use icon,
text and accessible status labels; the selector excludes assigned cards and saves
alternatives only after confirmation. Label Debug data as simulations.
Distinguish Info, Normal, Caution and Unavailable. Missing/stale/invalid is never
Normal; all four wheels are required for a Normal tire state, while a confirmed
warning may show Caution. Low battery/washer maps to Hungry; vehicle/assistance
warnings map to Sick with precedence. Include warnings from unselected cards;
expressions do not diagnose.

## AI connection and settings

Debug controls default off; Release data follows the
[vehicle authorization contract](ARCHITECTURE.md#vehicle-interaction-authorization).
In an allowed Release session, ten version taps with no gap over three seconds reveal
Debugger settings; ten more hide them and turn Debugger mode off. Show the
remaining count for the last five taps and confirm each transition.
Spoken replies and vehicle-home display are unavailable; omit Do Not Disturb.
Configured builds show GitHub approval QR, code and address help; unconfigured
builds disable sign-in. Only verified authentication shows the connected state,
account and equipped friend's chat action. Explain local persistence and separate
Copilot readiness. Network failure offers retry without clearing credentials;
disconnect confirms local-only removal.

## Motion

Respect the shared motion preference; animation never authorizes commands. Settings
controls only floating wandering; unknown/failed reads keep it stationary. Shell
chat transition returns to its trigger; restrictions replace content immediately.
Existing messages stay still. Home speech replays on entry, tap or interval, not
data updates.

## Vehicle launcher

Outside-app companion needs explicit opt-in and overlay permission. Non-P or
unverified parking stops wandering and triggers Mobi's departure animation;
verified P restores Mobi with a 1.4-second entrance before wandering resumes.
The Debug parking button uses the same vehicle stream; a renewed non-P signal
during entrance queues departure after the entrance completes. Intended placement is vehicle Home; hide it
while MobiMon is foreground. Target-OEM placement and lifecycle remain unverified under the
[platform contract](ARCHITECTURE.md#shared-vehicle-condition-and-overlay).
