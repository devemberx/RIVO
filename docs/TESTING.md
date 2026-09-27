# Testing strategy

[CONTRIBUTING.md](../.github/CONTRIBUTING.md#verification) owns required commands.

## Current setup and test locations

Use JUnit 4, coroutines-test, Robolectric/Compose Testing and Hilt/Room device tests.
Review images exist; golden comparisons and system-UI automation are not configured.

Screen tests and previews follow the fixed-display scope in
[DESIGN.md](DESIGN.md#visual-language), not a multiple-resolution device matrix.
Retain reference content, AAOS compatibility density, enlarged text and IME resizing.
Review fixtures use the current [Figma content bounds](ui/README.md): 2560 × 1184,
1792 × 829 at compatibility density, and 2560 × 940 with the reference IME.
Robolectric qualifiers describe the host, not necessarily the content bounds:
a 2560 × 1248dp host leaves 1184dp after its 64dp decor inset; 1792 × 893dp
leaves 829dp. Decor-free shell tests use content sizes directly. The shell inset
test dispatches 76/96px, then 96/160px system bars and restores them in one run;
this does not replace OEM window/Popup and native IME verification.
Isolated component and synthetic motion tests may use smaller fixtures; these do
not imply support for additional display sizes. CI's physical display is defined
in [cstd.ini](../.github/avd/cstd.ini).

Tests live in the subject module's `src/test`; device tests use `src/androidTest`.
Debug-only behavior uses `src/testDebug`. Two shared source sets need explicit wiring:

- `app/src/journeyTest`: connection journeys run on Robolectric and devices via
  [app/build.gradle.kts](../app/build.gradle.kts).
- `core-database/src/migrationTest`: populated V3 migration contracts use local and
  device wrappers. Other test directories are not automatically shared.

## Writing tests

- Test behavior in the owning module/package. Construct subjects directly; use
  Hilt for integration and fresh, controllable fakes for external dependencies.
  Keep helpers out of production APKs.
- Use `runTest`, a shared scheduler and injected clocks. Virtual time does not
  advance a separate clock. Install/reset Main locally; retain real Main on devices.
- Observe pending states and cover cancellation/late results. Close databases,
  cancel jobs and restore dispatchers after tests.
- Verify transactions, constraints, concurrency and rollback with real Room.
  Test persistence by reopening files and migrations with populated schemas;
  destructive reset is not migration coverage.
- Exercise Compose callbacks, Back, focus and enabled state through semantics.
  Use screenshots for layout, not as proof of persistence or provider behavior.

## Final Figma visual acceptance

After the final UI change, compare affected states with full-resolution
[v5 exports](ui/README.md), matching data, window size and display/font scale.
Compare app content without exported system bars or Debug controls. Inspect artwork,
typography/wrapping, geometry, colors, icons, insets and touch bounds. Also check
enlarged text and the actual AAOS content window, including focus, recovery and
interrupted motion.

Record references, review images and unresolved differences in the PR. Missing
references remain explicit. Builds, behavioral tests and generated images do not
establish visual parity. SVG-only renames require XML/render and byte-preservation checks.

## Current requirement map

These are existing suites, not execution results. Update critical mappings when
behavior changes; keep implementation gaps in [Architecture](ARCHITECTURE.md#planned-features).
Related suites share the linked module/package; test names define individual cases.

### Boundaries, vehicle evidence and persistence

| Contract | Coverage |
| --- | --- |
| Module isolation and unique route registration | `verifyModuleBoundaries` in [root build](../build.gradle.kts); [FeatureRegistryTest](../core/core-navigation/src/test/java/com/monsters/mobimon/core/navigation/FeatureRegistryTest.kt) |
| Parking freshness, independent signals, original/display evidence and decorative clock | [VehicleFreshnessPolicyTest](../core/core-domain/src/test/kotlin/com/monsters/mobimon/core/domain/VehicleFreshnessPolicyTest.kt), [VehicleStateViewModelTest](../core/core-presentation/src/test/java/com/monsters/mobimon/core/presentation/VehicleStateViewModelTest.kt) |
| Debug Park interpretation, raw VSS mapping, generated signal containers, adapter lookup, one foreground connection and unavailable real adapter | [DemoVehicleRepositoryTest](../app/src/testDebug/java/com/monsters/mobimon/vehicle/DemoVehicleRepositoryTest.kt), [VssVehicleInterpreterTest](../core/core-vss/src/test/kotlin/com/monsters/mobimon/core/vss/VssVehicleInterpreterTest.kt), [VssGeneratedSignalsTest](../core/core-vss/src/test/kotlin/com/monsters/mobimon/core/vss/VssGeneratedSignalsTest.kt), [VssAdapterLocatorTest](../core/core-vss/src/test/kotlin/com/monsters/mobimon/core/vss/VssAdapterLocatorTest.kt), [CompanionRuntimeTest](../app/src/test/java/com/monsters/mobimon/runtime/CompanionRuntimeTest.kt), [UnavailableVehicleRepositoryTest](../core/core-vss/src/test/kotlin/com/monsters/mobimon/core/vss/UnavailableVehicleRepositoryTest.kt) |
| Legacy ownership/revision, later evidence, atomic completion and reopening | [QuestEvaluatorTest](../core/core-domain/src/test/kotlin/com/monsters/mobimon/core/domain/QuestEvaluatorTest.kt), [RoomCompanionRepositoryTest](../core/core-database/src/test/java/com/monsters/mobimon/core/database/RoomCompanionRepositoryTest.kt) and its device counterpart |
| Point uniqueness, concurrent purchase/equip, rollback and authorization recheck | [PointEconomyRepositoryTest](../core/core-database/src/test/java/com/monsters/mobimon/core/database/PointEconomyRepositoryTest.kt), [Q01JourneyTest](../app/src/test/java/com/monsters/mobimon/Q01JourneyTest.kt), [DebugPointRepositoryTest](../core/core-database/src/testDebug/java/com/monsters/mobimon/core/database/DebugPointRepositoryTest.kt) |
| V1→V4 and both V3 shapes preserve records/identity | [PointEconomyMigrationTest](../core/core-database/src/test/java/com/monsters/mobimon/core/database/PointEconomyMigrationTest.kt), [LevelingMigrationContract](../core/core-database/src/migrationTest/java/com/monsters/mobimon/core/database/LevelingMigrationContract.kt) with local/device wrappers |
| Supplied driving conditions, weather and catalog rules | [DrivingQuestEvaluatorTest](../core/core-domain/src/test/kotlin/com/monsters/mobimon/core/domain/DrivingQuestEvaluatorTest.kt) |

### Authentication

| Contract | Coverage |
| --- | --- |
| OAuth request/response validation, HTTP errors and redirects | [OkHttpGitHubApiTest](../core/core-auth/src/test/java/com/monsters/mobimon/core/auth/OkHttpGitHubApiTest.kt); MockWebServer |
| Copilot host validation, fixed `gpt-4o` direct request, text protocols, bounded rejection categories and no replay even with `503 Retry-After: 0` | [OkHttpCopilotApiTest](../core/core-auth/src/test/java/com/monsters/mobimon/core/auth/OkHttpCopilotApiTest.kt); MockWebServer |
| Copilot credential/model cache, model absence, expiry, parking checks and request bounds | [CopilotConversationProviderTest](../core/core-auth/src/test/java/com/monsters/mobimon/core/auth/CopilotConversationProviderTest.kt); fake provider |
| Poll intervals, slowdown, expiry, cancellation, persistence, refresh, identity retry after failure/cancellation, revision-scoped Copilot 401 recovery without replay and revocation | [PersistentGitHubAuthenticationTest](../core/core-auth/src/test/java/com/monsters/mobimon/core/auth/PersistentGitHubAuthenticationTest.kt); fake provider/store |
| Keystore encryption, reopening, tamper rejection and deletion | [EncryptedCredentialStoreTest](../core/core-auth/src/androidTest/java/com/monsters/mobimon/core/auth/EncryptedCredentialStoreTest.kt); device |
| Authentication guards/recovery, reference-layout parking guard, readiness separation, QR decoding and success/disconnect actions | [Authentication feature suites](../feature/feature-auth/src/test/java/com/monsters/mobimon/feature/auth); ViewModel and Robolectric |

### Presentation and navigation

| Contract | Coverage |
| --- | --- |
| Loading/failure differs from committed values; retries retain data | [Shared presentation suites](../core/core-presentation/src/test/java/com/monsters/mobimon/core/presentation) and owning feature tests |
| Home, Quest and Store point summaries share typography and header placement at reference size; chat connection badge has only connected/checking labels | [QuestScreenTest](../feature/feature-quest/src/test/java/com/monsters/mobimon/feature/quest/QuestScreenTest.kt), [StoreReferenceScreenTest](../feature/feature-customization/src/test/java/com/monsters/mobimon/feature/customization/StoreReferenceScreenTest.kt), [ConversationScreenTest](../feature/feature-auth/src/test/java/com/monsters/mobimon/feature/auth/ConversationScreenTest.kt) |
| Claim pending/duplicate/cancellation, committed amounts and dates, reset reconciliation | [QuestViewModelTest](../feature/feature-quest/src/test/java/com/monsters/mobimon/feature/quest/QuestViewModelTest.kt), [QuestScreenTest](../feature/feature-quest/src/test/java/com/monsters/mobimon/feature/quest/QuestScreenTest.kt), [PointEconomyRepositoryTest](../core/core-database/src/test/java/com/monsters/mobimon/core/database/PointEconomyRepositoryTest.kt) |
| Independent catalog/inventory retry, shared appearance preview without early actions, background continuity while loading and friend-specific equipment | [Customization suites](../feature/feature-customization/src/test/java/com/monsters/mobimon/feature/customization), point repository suite |
| Independent settings writes, failure/retry and DataStore keys | [SettingsViewModelTest](../feature/feature-pet/src/test/java/com/monsters/mobimon/feature/pet/SettingsViewModelTest.kt), [DataStoreSettingsRepositoryTest](../core/core-database/src/test/java/com/monsters/mobimon/core/database/DataStoreSettingsRepositoryTest.kt) |
| Mobi sprite frame timing, cache, blend continuity/opacity, delayed frames, independent transforms and fixed layout | `MobiIdleAnimationTest`, `PetAvatarTest`, and `DecorativeMotionTest` in [core-ui tests](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui) |
| Shared warning classification, interruptible 200ms crossfade, 24-frame collapsed sprite loop, fixed ground anchor, reduced motion and unchanged bounds | `VehicleConditionTest`, `MobiWarningAnimationTest`, `DecorativeMotionTest`, and `CompanionReviewTest` cover state, rendering and Home wiring; [final visual acceptance](#final-figma-visual-acceptance) checks ground alignment |
| Artwork, seven background periods, celestial disk sizes, dark midnight city, dimensions, reduced motion and shared control bounds | [Core UI suites](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui); native Robolectric images |
| Decorative local time ignores vehicle timestamps; Debug preview is separate from VSS evidence and quest weather | [VehicleStateViewModelTest](../core/core-presentation/src/test/java/com/monsters/mobimon/core/presentation/VehicleStateViewModelTest.kt), [BackgroundTimeOverrideTest](../app/src/testDebug/java/com/monsters/mobimon/di/features/BackgroundTimeOverrideTest.kt) |
| Home/Settings, vehicle, store and quest layouts, focus, recovery, fixed Store layout with delayed card placeholders and quest panel resizing | Owning feature `src/test` suites, including `CompanionReviewTest`, `VehicleReviewTest`, `StoreReferenceScreenTest` and `QuestScreenTest` |
| Vehicle v5 default six cards, 30 VSS mappings, missing readings, gallery exclusion of assigned cards, selection before confirmation, scrolling, slot replacement, cancel and local persistence, plus normal/hungry/sick artwork and popup bounds | [VehicleCardCatalogTest](../feature/feature-vehicle-info/src/test/java/com/monsters/mobimon/feature/vehicle/VehicleCardCatalogTest.kt), [VehicleInfoScreenTest](../feature/feature-vehicle-info/src/test/java/com/monsters/mobimon/feature/vehicle/VehicleInfoScreenTest.kt), [VehicleReviewTest](../feature/feature-vehicle-info/src/test/java/com/monsters/mobimon/feature/vehicle/VehicleReviewTest.kt), [VehicleCardSelectionPreferencesTest](../app/src/test/java/com/monsters/mobimon/di/features/VehicleCardSelectionPreferencesTest.kt); [final visual acceptance](#final-figma-visual-acceptance) verifies the screen and selector |
| Card signal defaults, Debug persistence and simulated fallback versus real adapter isolation | [VehicleCardCatalogTest](../feature/feature-vehicle-info/src/test/java/com/monsters/mobimon/feature/vehicle/VehicleCardCatalogTest.kt), [DebugCardVssSignalsTest](../app/src/testDebug/java/com/monsters/mobimon/debug/DebugCardVssSignalsTest.kt), [DemoVehicleRepositoryTest](../app/src/testDebug/java/com/monsters/mobimon/vehicle/DemoVehicleRepositoryTest.kt) |
| Parking badge requires complete valid Park evidence; standard routes retain the 344 × 76 Figma icon and geometry, restricted state uses themed colors and centers the pause icon with copy, conversation retains its reference badge size and separates icon and label with enlarged text; initial profile loading omits it | `ParkingBadgeStatusTest`, `MobiMonParkingBadgeTest`, and owning Home, Settings, Vehicle, Quest, Store, Connection and Conversation screen suites |
| Menu reference/AAOS-density/enlarged-text bounds, focus, latest-session chat routing from Settings and restoration, connection origin after authentication loss, recreation and restricted/outgoing input | [Shell suites](../app/src/test/java/com/monsters/mobimon/ui), [CopilotConnectionJourneyTest](../app/src/journeyTest/java/com/monsters/mobimon/CopilotConnectionJourneyTest.kt) |
| AAOS 96px status bar and 160px navigation bar | [System bar frame check](../scripts/aaos/check-aaos-system-bars.sh) in CI and after a baked-image AVD restart |
| Conversation reveal/return, stationary Home, visible touch bounds, interruption, reduced motion and scrolled action bounds | [ConversationRevealTest](../app/src/test/java/com/monsters/mobimon/ui/ConversationRevealTest.kt), [PetHomeScreenTest](../feature/feature-pet/src/test/java/com/monsters/mobimon/feature/pet/PetHomeScreenTest.kt); native Robolectric frames and pointer input |
| Live system-inset changes, destination/menu bounds, debugger unlock notice clearance and restoration | [MobiMonContentTest](../app/src/test/java/com/monsters/mobimon/ui/MobiMonContentTest.kt); platform inset dispatch in Robolectric |
| Floating companion bounds use current bars/cutouts and measured size; Debug dragging, edge reversal and resize remain inside safe content | [OverlayMovementBoundsTest](../app/src/test/java/com/monsters/mobimon/service/OverlayMovementBoundsTest.kt), [DebugOverlayPlacementTest](../app/src/testDebug/java/com/monsters/mobimon/ui/DebugOverlayPlacementTest.kt); OEM overlay placement still requires a device |
| Floating companion motion preference leaves in-app scene motion enabled | [MobiMonAppMotionTest](../app/src/test/java/com/monsters/mobimon/ui/MobiMonAppMotionTest.kt); overlay movement remains covered by service tests and needs vehicle acceptance |
| Chat draft/composition lifetime, ownership clearing, first-entry loading without a screen flash, input guards/actions, parking and connection dialogs, reference ring and failed-action geometry, failed-turn editing, compact New Chat and navigation/IME resizing | [Conversation and feature suites](../feature/feature-auth/src/test/java/com/monsters/mobimon/feature/auth), [CopilotConnectionJourneyTest](../app/src/journeyTest/java/com/monsters/mobimon/CopilotConnectionJourneyTest.kt); native review images |
| Foreground Copilot model and identity recheck, provisional draft retention, Send readiness, persistent failed turns, validated Android internet detection, immediate offline send/recheck, disconnect cancellation, usage blocking and later reentry, one visible failure notice, account guidance and access/usage Home actions, recheck without replay, 30-second wait bounds, duplicate/retry guards and limits | [AndroidConversationNetworkStatusTest](../app/src/test/java/com/monsters/mobimon/network/AndroidConversationNetworkStatusTest.kt), [ConversationViewModelTest](../feature/feature-auth/src/test/java/com/monsters/mobimon/feature/auth/ConversationViewModelTest.kt), [ConversationScreenTest](../feature/feature-auth/src/test/java/com/monsters/mobimon/feature/auth/ConversationScreenTest.kt), [CopilotConnectionJourneyTest](../app/src/journeyTest/java/com/monsters/mobimon/CopilotConnectionJourneyTest.kt); platform shadow and fake transport |
| Voice permission/lifecycle, speech-gated RMS, bounded phrase pauses and final wait, joined review, rerecord, stale callbacks, explicit Send and draft preservation | `ConversationViewModelTest`, `ConversationScreenTest`, [AndroidConversationSpeechInputTest](../app/src/test/java/com/monsters/mobimon/speech/AndroidConversationSpeechInputTest.kt) |
| Native voice action clearance, persistent disclaimer, insets and recording/review captures | `ConversationKeyboardDeviceTest.voicePreviewHasSeparateActionsAndNativeInsets`; isolated Debug preview, not speech recognition |
| Actual offline Korean microphone recognition, pause between phrases, no automatic request, parking/background cancellation and explicit Send | [ConversationVoiceDeviceTest](../app/src/androidTest/java/com/monsters/mobimon/ConversationVoiceDeviceTest.kt); opt-in `voiceIntegration=true`, installed Korean service/model, network disconnected and a host-fed microphone fixture required; other dependencies use journey fakes |
| Native keyboard resizing, Back/draft retention, short exchange visibility, failed-turn Edit and reply network-dialog recheck | [ConversationKeyboardDeviceTest](../app/src/androidTest/java/com/monsters/mobimon/preview/ConversationKeyboardDeviceTest.kt); AAOS device |
| Isolated Debug rehearsal and branding | [CopilotPreviewJourneyTest](../app/src/journeyTest/java/com/monsters/mobimon/preview/CopilotPreviewJourneyTest.kt), [BrandingTest](../app/src/testDebug/java/com/monsters/mobimon/BrandingTest.kt) |

## Integration boundaries

- Room migration fixtures cover populated V1 and original/expanded V3 upgrades and
  reopening, not a separately populated V2 fixture or `MigrationTestHelper`.
- [JourneyTestModule](../app/src/journeyTest/java/com/monsters/mobimon/testing/JourneyTestModule.kt)
  uses real MainActivity, ViewModels and repositories, in-memory Room, isolated
  DataStore and fake external providers. It never contacts GitHub or accesses user credentials;
  Debug previews also establish no provider or vehicle verification.
- Recreation, file reopening and process restart are distinct. Local tests, APK
  assembly and `NO-SOURCE` tasks do not prove device execution, live providers,
  Release behavior or launcher support. Real OAuth approval/restart/revocation and
  AAOS restriction/reconnection behavior need separate target-device verification.
  Copilot wire fixtures do not establish live account entitlement, OAuth-app access,
  model availability or compatibility with the experimental private endpoints.
- Driving tests cover supplied formulas and transaction invariants, not trusted
  driving evidence, real occurrence identity or evaluator-to-award agreement.
  On-device decorative lifecycle still lacks acceptance.

Record revision and device image/signal source with the
[required check report](../.github/CONTRIBUTING.md#verification).
Keep credentials and private logs out of reports.

## Focused commands and reports

Use `--tests <qualified-name>` with the owning module's `testDebugUnitTest`, or
`test` for plain Kotlin. Filtered tasks do not execute dependency suites or devices.
Kover: `./gradlew :app:koverHtmlReportDebug :app:koverXmlReportDebug` (local JVM only; no percentage gate).
[CI](../.github/workflows/android-ci.yml) owns artifact paths and retention.

### CI AAOS environment

The [workflow](../.github/workflows/android-ci.yml) and [cstd.ini](../.github/avd/cstd.ini)
define the image, extension, ABI and display. The workflow builds and installs the
[system bars overlay](../.github/avd/system-bars-overlay/AndroidManifest.xml) on its
disposable writable AVD, then checks the 96px top and 160px bottom bars with
[check-aaos-system-bars.sh](../scripts/aaos/check-aaos-system-bars.sh). Run the required
[host check](../scripts/aaos/check-aaos-environment.sh) and canonical device tests after
the overlay check. Use emulator 35.1.9 or newer.

For a persistent local AVD, install Python 3, JDK 17, SDK Platform 34, Build
Tools 34.0.0, `debugfs` and `e2fsck`. Install the official AAOS 34-ext9 Google
APIs revision 5 image matching the host ABI. Create a standard AAOS AVD at
2560x1440 / 160 dpi, then set `ANDROID_HOME`, `TEMPLATE_AVD` and `IMAGE_DIR`
to the local SDK, template AVD and output paths. Run from the repository root:

```bash
python3 scripts/aaos/build_aaos_baked_image.py \
  --sdk-dir "$ANDROID_HOME" \
  --template-avd-config "$TEMPLATE_AVD/config.ini" \
  --output-image-dir "$IMAGE_DIR" \
  --avd-name mobimon_baked_bars_34
```

Use `--help` for nondefault AVD homes and host path conversion. Start the new
AVD in Device Manager, fully stop and restart it, then run
`bash scripts/aaos/check-aaos-system-bars.sh`. Set `ADB` or `ANDROID_SERIAL` if needed.
The image stays at `IMAGE_DIR` outside Git; the template AVD can be removed
after verification.

CI uses a fresh, headless AVD with software rendering and disabled animations.
Local CSTD images are separate inputs; matching metadata does not establish
identical binaries/services. The host check identifies the environment, not test execution.
