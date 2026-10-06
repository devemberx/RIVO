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

- Target 2560 × 1440 AAOS with system insets and export content bounds. Preserve
  artwork proportions and compatibility-density fonts.
- Use `MobiMonTheme`, Twilight colors, Noto Sans KR and shared controls. Reflow or
  scroll for enlarged text and IME; keep actions visible.
- Touch bounds are at least 76 × 76dp, with 24dp clearance where possible. Use
  4.5:1 text and 3:1 control/icon contrast, redundant status cues and logical focus.
  Trap focus in dialogs and restore it on dismissal.
- Loaded routes show `MobiMonParkingBadge`; initial loading omits it. Fresh
  nonmoving P shows “주차 확인됨”; other states show “주차 후 이용” with red icon/text.
  Keep points and simulation labels separate. Chat has its own adjacent badge.

Losing verified Park on Vehicle, Store or Quest shows the
[blocking parking dialog](ui/vehicle/parking-required.svg), hiding selectors and
reward dialogs. Restore the route if Park returns before Home is chosen. Initial
unavailable data uses the ordinary unavailable state; success requires a committed write.

### Launcher icon and native splash

Keep the approved launcher artwork and adaptive-mask clearance. The native splash
uses the common sky color and ends on the first app frame.
[Startup](../app/src/main/java/com/monsters/mobimon/ui/StartupLoading.kt) reveals the
equipped Home scene before the menu face. Local readiness releases Home without
waiting for OAuth or vehicle evidence. Local failures reach Home's retry UI;
slow startup offers an escape after eight seconds. Restoration skips the preview,
and disabled animations use a still face.

## Reusable Compose library and asset handoff

Use `core-ui` primitives and replaceable
[PetAvatar](../core/core-ui/src/main/java/com/monsters/mobimon/core/ui/PetAvatar.kt).
Prefix feature icons; share fonts/artwork in `core-ui` and retain licenses.
Full-screen SVGs are references, not runtime assets.

### Image asset locations

| Asset | Location |
| --- | --- |
| Character masters, turnarounds and proportions | `art/characters/<friend>/references/` |
| Item masters, turnarounds and attachment contracts | `art/items/<item>/references/` |
| Runtime character frames/sprites | `core/core-ui/src/main/assets/characters/<friend>/<variant>/<action>/` |
| Shared artwork/accessories/backgrounds | `core/core-ui/src/main/res/drawable-nodpi/` |
| Feature artwork/icons | Owning module's `res/drawable[-nodpi]/` |
| UI references / temporary drafts | [docs/ui](ui/README.md) / ignored `output/imagegen/`, `tmp/imagegen/` |

#### Character and item references

The [catalog](../art/characters/reference_catalog.json) selects current masters,
turnarounds and character/item manifests under `art/`; these are not packaged.
Manifests own identity, pixel landmarks, fitted geometry, occlusion and review
declarations. Masters override inferred views; standalone item views do not define
worn size. The [shared contract](../.agents/skills/character-animation/references/production.md)
owns coordinate conventions and production rules. Retired source sheets remain
in Git history; current revised masters retain their
review provenance and hash checks.
Use the [animation workflow](../.agents/skills/character-animation/SKILL.md) to check
current inputs and review playback before export.

#### Character and item asset names

Store accessory icons are pre-extracted lossless WebP files in `drawable-nodpi`;
keep their original crop canvas so Store sizing stays stable. Do not package mixed
item/reference sheets after all consumers use standalone exports. Reference masters
remain under `art/`.

| Use | Name / location | Example |
| --- | --- | --- |
| Store accessory icon | `drawable-nodpi/store_item_<character>_<item>.webp` | `store_item_mobi_headphones.webp` |
| Static character preview or pose | `drawable-nodpi/pet_<character>_<appearance>_<state>.<ext>` | `pet_mobi_headphones_preview.png` |
| Animation frame, atlas or part | `assets/characters/<character>/<appearance>/<action>/<character>_<action>[_direction]_<appearance>_<part-or-frame>.<ext>` | `mobi_hungry_headphones_base.webp`, `mobi_run_left_normal_sprite.png` |
| Animation data | Same animation prefix and folder with its data extension | `mobi_hungry_normal_face.morph` |

Use `normal` for an unequipped appearance and `shared` for appearance-independent
parts. A reused appearance-specific file keeps its source name; consumers alias it
instead of duplicating bytes. Keep `art` masters and cosmetic ownership IDs
independent of runtime filenames. Update dynamic loaders, reference source paths
and tests in the same change when renaming assets.

#### Background resource names

Use `pet_background_<scene>_<period>.<ext>` in `drawable-nodpi`, with lowercase
snake case and lossless WebP for new scene assets. Scene IDs are `lake_park` and
`cyberpunk_city`; periods are `midnight`, `sunrise`, `morning`, `day`, `afternoon`,
`sunset` and `night`. Cosmetic ownership IDs are independent of resource names.
The app-owned, time-independent loading sky is `pet_background_startup_common.webp`;
`common` is not a VSS period or a selectable Home scene.

### Home scene

[Home/Store backgrounds](../core/core-ui/src/main/java/com/monsters/mobimon/core/ui/CompanionBackground.kt)
use the same VSS time for scenes, previews and thumbnails, never device time.
Periods are Midnight 00–04, Sunrise 05–06, Morning 07–11, Day 12–15,
Afternoon 16–17, Sunset 18–19 and Night 20–23. Missing time uses the selected
scene's Night; unknown item IDs use Lake Park without decorations.
Keep crop, horizon and ground anchors stable as text grows; crossfade the scene,
not controls. Use the animated Home time phrase and only registered props/effects.

## Screens and navigation

[Exports](ui/README.md#screen-index) own screen inventory and geometry. Back closes
keyboard, then dialog/menu, then destination. Menu destinations return Home;
connection returns to its entry route. Notifications show a Home dot and positive
Menu count, open the Menu panel and remain until receipt. Quest alerts open the
appropriate detail or claim view. Keep headers fixed while alerts scroll.
Pending actions block duplicates; uncertain writes reconcile before retry.

## Quests and points

Use catalog rewards and actual repeat eligibility. Show one committed Points
balance; unknown is not zero. Celebrate committed amounts once per claim, label
them P and show persisted completion dates. Keep claim controls in place while
saving; reduced motion shows rewards immediately. Quest detail preserves scroll
and the “작은 도전, 큰 여정” message. The success popup uses the selected
friend's happy artwork through
[PetAvatar](../core/core-ui/src/main/java/com/monsters/mobimon/core/ui/PetAvatar.kt);
Las keeps its celebratory confetti on a transparent background.

The [reward dialog](../feature/feature-quest/src/main/java/com/monsters/mobimon/feature/quest/QuestRewardSuccessContent.kt)
shares a ground line and confirmation-button inset across friends and equipment.
Equipment padding does not shrink Mobi/Luna bodies; reserve cap clearance.
Show weather bonus details once in the green reward line, and give enlarged text
room by reducing the artwork area.

## Customization

[Store](../feature/feature-customization/src/main/java/com/monsters/mobimon/feature/customization/CustomizationScreen.kt)
places preview/actions left and catalog right: two columns for backgrounds,
three for friends, clothes, effects and props. Enter Clothes on the equipped friend;
reset clothing previews when the equipped friend changes. Allow per-friend clothing
previews and an owned filter. Show the first frame while animations load; still cards use
the same frame. Panels scroll for enlarged text.

Preview stays local until Apply. Equipment persists per friend; purchases grant
ownership without switching or equipping. Owned items apply without another
charge. Show compatibility, price, balance and shortfall; confirm price and
remaining points before purchase. Cancellation spends nothing. Pending writes
retain selection and block duplicates; failures offer Retry. There is no cash,
top-up or conversion. Shared decorations retain their shape in motion and show
still artwork with reduced motion.

## Conversation

Use split companion/chat panels, suggestions and a system-keyboard composer.
Suggestions fill without sending; signed-out Chat opens connection settings.
Mobi speaks curious rabbit banmal and Luna quietly caring cat banmal, without
habitual animal suffixes, emojis or stage directions. Verified vehicle-linked
states use the companion's first-person voice and observed reason, without
implying biological illness or mechanical diagnosis. Keep New conversation
reachable above the composer and follow the
[session contract](ARCHITECTURE.md#keyboard-conversation-ui).

Use inline guidance for recoverable network/readiness failures. Explicit Recheck
opens the [recovery popup](ui/conversation/network-error.svg) without resending;
account/access/usage failures use the same popup. Failed replies retain the turn
for Edit/Retry; Retry requires an unanswered message. Show “Copilot 확인 중” until
verified, then “Copilot 연결됨”. Parking recovery blocks editing and hides IME;
AAOS restrictions remove the screen. Send shows the bubble and waiting hint;
cancel restores editable text, while Home clears the composer.

Follow the [keyboard](ui/conversation/keyboard-input.svg),
[recording](ui/conversation/voice-listening.svg) and
[review](ui/conversation/voice-review.svg) layouts. Stop yields editable text;
only Send submits. Microphone permission is explicit; accepting the request clears
typed input even if permission is denied. Offer Settings when the system dialog
cannot reopen. Errors preserve confirmed text and drafts; inline network errors
preserve capture, while popup recovery releases it. Disclose transmitted dialogue,
available context/debug values and AI uncertainty below the composer. Local
clearing does not promise provider deletion. Spoken replies are unavailable.

Explain vehicle information from tool evidence in the companion voice, with units
and signal meanings. Label Debug simulations and unavailable observations; vehicle
time is the clock at lookup. Rejected evidence preserves history and requires
explicit retry. Keep raw tool protocol hidden and retain relevant conditions and
warnings. Use short paragraphs, lists when helpful and citations beside claims;
manual sources use a regular-weight heading
and smaller entries. Follow new replies unless reading history, offer a return
button and keep artwork clear of its caption when the keyboard opens.

## Vehicle information

[Vehicle exports](ui/README.md#vehicle) define layout. Default cards cover battery,
tire pressure, washer fluid, low beam, driver fatigue and service distance.
Selectors offer individual unassigned signals and save only after confirmation.
Use icon/text status cues and label Debug simulations. Distinguish Info, Normal,
Caution and Unavailable: missing/stale/invalid is never Normal, and Normal tire
status requires all four wheels. Confirmed warnings may show Caution. Low
battery/washer maps to Hungry; vehicle/assistance warnings map to Sick with
precedence, including warnings from unselected cards. Expressions do not diagnose.

## AI connection and settings

Debug controls default off; Release follows the
[vehicle authorization contract](ARCHITECTURE.md#vehicle-interaction-authorization).
The version-tap gesture reveals/hides Debugger settings with progress and
confirmation; hiding also disables Debugger mode. Omit unavailable spoken-reply,
vehicle-home display and Do Not Disturb controls.

Configured builds show GitHub approval QR, code and address help; unconfigured
builds disable sign-in. Only verified authentication shows connected account and
chat actions. Explain local persistence separately from Copilot readiness.
Network retry retains credentials; disconnect confirms local-only removal.

## Motion

Keep identity, anatomical scale and ground anchors stable across states. Mobi/Luna
crossfade status poses; reduced motion switches immediately while gentle idle
breathing continues. Las reduced motion keeps a representative robot pose.
Warnings take priority over hunger; explicit nonanimated previews hold a rest pose.

Mobi equipped idle shares body, blink and sprout timing. Hungry motion keeps the
six-second carrot-thought, scrunch and two-shake sequence; equipment follows the
head rigidly. Sick motion uses sleepy breathing and three stars with a broken blue
orbit. Texture density must preserve the master at the target display size.

Luna idle uses a continuous 2.2-second grounded breath without restarting on
equipment changes. Cap idle, hungry and happy follow the large fitted master and retain
the body's scale beneath added hat padding, including still previews. Other cap
actions/statuses retain earlier artwork until separately migrated.
Status and appearance fades include the cap's headroom without changing the body slot.

Luna hungry shares idle body/equipment layers and keeps breathing and cap-sprout
motion at 25% of idle amplitude. Its 4.4-second gesture shows two thought dots,
then the fish cloud, a gradual mouth opening and one continuous saliva drop
before the cloud fades and mouth closes. Still previews retain a visible hungry
pose; status fades preserve cap/cloud overflow and the original body slot.

Respect the shared motion preference; animation never authorizes commands.
Settings controls floating wandering; unknown/failed reads keep it stationary.
Restrictions replace content immediately. Chat transitions return to their
trigger; existing messages stay still. Home speech replays on entry, tap or
interval, not data updates.

## Vehicle launcher

The outside-app companion requires opt-in and overlay permission. Non-P or
unverified parking stops wandering and triggers departure; verified P restores
entry before wandering resumes. Keep idle visible while loading and preserve
scale/ground anchors through transitions. A renewed non-P signal during entrance
queues departure. Hide the overlay while MobiMon is foreground. Target-OEM Home
placement and lifecycle remain unverified under the
[platform contract](ARCHITECTURE.md#shared-vehicle-condition-and-overlay).
