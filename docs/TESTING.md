# Testing strategy

[CONTRIBUTING.md](../.github/CONTRIBUTING.md#verification) owns required commands.
This document maps behavior to tests and states what those tests do not prove.

## Current setup and test locations

Use JUnit 4, coroutines-test, Robolectric/Compose and Hilt/Room device tests.
Tests belong in the subject module's `src/test`, device tests in `src/androidTest`,
and Debug-only tests in `src/testDebug`. Explicitly shared source sets are
`app/src/journeyTest` and `core-database/src/migrationTest`; other directories are
not automatically shared. Review images exist; golden comparison and system-UI
automation are not configured.

Match the [fixed-display scope](DESIGN.md#visual-language): reference content
2560 × 1184, compatibility-density content 1792 × 829, and reference IME content
2560 × 940. Robolectric host qualifiers may include decor; shell fixtures use
content bounds directly. Smaller component fixtures imply no extra display support.

## Writing tests

- Test observable behavior in its owning module; construct subjects directly and
  use controllable fakes for external dependencies. Keep helpers out of production.
- Use `runTest`, shared schedulers and injected clocks; virtual time does not advance
  separate clocks. Reset local Main/dispatchers, close databases and cancel jobs.
  Retain real Main on devices.
- Cover pending state, duplicate actions, cancellation and late callbacks. Verify
  transactions, concurrency, rollback, reopening and populated migrations with Room.
- Exercise UI callbacks, Back, focus and enabled state through semantics. Screenshots
  validate layout, not persistence, authorization or provider behavior.

## Final Figma visual acceptance

After the final UI edit, compare affected states with full-resolution
[v5 exports](ui/README.md), matching data, content window, display/font scale and
insets. Exclude exported system bars/Debug controls. Inspect artwork, typography,
geometry, colors, icons, touch bounds, enlarged text, recovery and interrupted motion.
Feature owners record references, images and unresolved differences in the PR.
Missing references remain explicit; builds/behavior tests do not prove visual parity.
SVG-only renames require XML/render and byte-preservation checks.

## Current requirement map

These are existing suites, not execution results. Update affected critical mappings;
[Architecture](ARCHITECTURE.md#planned-features) owns integration gaps. Paths below
identify the owning suites; individual test names define detailed cases.

### Boundaries, vehicle evidence and persistence

| Contract | Coverage |
| --- | --- |
| Module isolation and route registration | `verifyModuleBoundaries`; [FeatureRegistryTest](../core/core-navigation/src/test/java/com/monsters/mobimon/core/navigation/FeatureRegistryTest.kt) |
| Freshness, original evidence, independent signals and decorative clock | [Domain tests](../core/core-domain/src/test/kotlin/com/monsters/mobimon/core/domain), [presentation tests](../core/core-presentation/src/test/java/com/monsters/mobimon/core/presentation) |
| VSS mapping/adapter absence, Debug defaults and foreground connection | [VSS tests](../core/core-vss/src/test/kotlin/com/monsters/mobimon/core/vss), [DemoVehicleRepositoryTest](../app/src/testDebug/java/com/monsters/mobimon/vehicle/DemoVehicleRepositoryTest.kt), [CompanionRuntimeTest](../app/src/test/java/com/monsters/mobimon/runtime/CompanionRuntimeTest.kt) |
| Atomic rewards/purchases/equip, uniqueness, ownership, rollback and reauthorization | [Database tests](../core/core-database/src/test/java/com/monsters/mobimon/core/database), [Q01JourneyTest](../app/src/test/java/com/monsters/mobimon/Q01JourneyTest.kt), [Debug tests](../core/core-database/src/testDebug/java/com/monsters/mobimon/core/database) |
| Populated V1→V4 and original/expanded V3 preservation | `PointEconomyMigrationTest`; shared [LevelingMigrationContract](../core/core-database/src/migrationTest/java/com/monsters/mobimon/core/database/LevelingMigrationContract.kt) and device wrappers |
| Supplied driving formulas and catalog rules | [DrivingQuestEvaluatorTest](../core/core-domain/src/test/kotlin/com/monsters/mobimon/core/domain/DrivingQuestEvaluatorTest.kt) |

### Authentication

| Contract | Coverage |
| --- | --- |
| OAuth lifecycle, identity/credential revisions, expiry, cancellation and revocation | [PersistentGitHubAuthenticationTest](../core/core-auth/src/test/java/com/monsters/mobimon/core/auth/PersistentGitHubAuthenticationTest.kt) |
| HTTP validation, bounded errors, fixed model, redirects and no completion replay | [Auth transport/provider suites](../core/core-auth/src/test/java/com/monsters/mobimon/core/auth); MockWebServer/fakes |
| Keystore encryption, tampering, reopen/delete | [EncryptedCredentialStoreTest](../core/core-auth/src/androidTest/java/com/monsters/mobimon/core/auth/EncryptedCredentialStoreTest.kt); device |
| Approval/parking guards, recovery, QR, disconnect and readiness separation | [Authentication feature suites](../feature/feature-auth/src/test/java/com/monsters/mobimon/feature/auth), [connection journey](../app/src/journeyTest/java/com/monsters/mobimon/CopilotConnectionJourneyTest.kt) |

### Presentation and navigation

| Contract | Coverage |
| --- | --- |
| Committed data on failure, independent settings/catalog retry and appearance | [Presentation](../core/core-presentation/src/test/java/com/monsters/mobimon/core/presentation), [customization](../feature/feature-customization/src/test/java/com/monsters/mobimon/feature/customization), [pet](../feature/feature-pet/src/test/java/com/monsters/mobimon/feature/pet) and database suites |
| Quest claims, duplicates, committed amount/date and reset reconciliation | [Quest suites](../feature/feature-quest/src/test/java/com/monsters/mobimon/feature/quest) and point repository tests |
| Shared condition, unselected warnings, card availability/selection/persistence | [Vehicle suites](../feature/feature-vehicle-info/src/test/java/com/monsters/mobimon/feature/vehicle), `VehicleConditionTest`, [selection preferences](../app/src/test/java/com/monsters/mobimon/di/features/VehicleCardSelectionPreferencesTest.kt), [Debug signal tests](../app/src/testDebug/java/com/monsters/mobimon/debug) |
| Parking badges, sprites, ground anchors, backgrounds, motion and shared bounds | [Core UI suites](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui), presentation and owning feature review tests |
| Navigation/restoration, alert counts/popup, reveal input, safe insets and enlarged text | [Shell suites](../app/src/test/java/com/monsters/mobimon/ui), connection journey, owning Home/Quest/Vehicle suites |
| Overlay clamping, resize/drag and independent motion preference | [OverlayMovementBoundsTest](../app/src/test/java/com/monsters/mobimon/service/OverlayMovementBoundsTest.kt), [DebugOverlayPlacementTest](../app/src/testDebug/java/com/monsters/mobimon/ui/DebugOverlayPlacementTest.kt), shell motion tests |
| Chat draft/ownership, failed turns, network/parking recovery, guarded Send and deadlines | [Conversation feature suites](../feature/feature-auth/src/test/java/com/monsters/mobimon/feature/auth), connection journey, [network status tests](../app/src/test/java/com/monsters/mobimon/network/AndroidConversationNetworkStatusTest.kt) |
| Voice permission/cancellation, blocked suggestions and restored editing, confirmed text on errors/timeouts, review/footer, explicit Send and deadlines | `ConversationViewModelTest`, `ConversationScreenTest` in the auth feature |
| External PCM request, repeated phrases/full final results, EOF Stop, pause timer and late callbacks | [AndroidConversationSpeechInputTest](../app/src/test/java/com/monsters/mobimon/speech/AndroidConversationSpeechInputTest.kt); Robolectric |
| PCM ordering, microphone tail/queue drain, explicit overflow and cancellation | [SpeechAudioBufferTest](../app/src/test/java/com/monsters/mobimon/speech/SpeechAudioBufferTest.kt), [SpeechPcmCaptureTest](../app/src/test/java/com/monsters/mobimon/speech/SpeechPcmCaptureTest.kt); actual capture/pipe uses the device test below |
| Actual offline Korean microphone, three repeated greetings, pauses, Stop and interruption | [ConversationVoiceDeviceTest](../app/src/androidTest/java/com/monsters/mobimon/ConversationVoiceDeviceTest.kt); opt-in, see below |
| Native IME, Back/draft, recovery dialogs, voice action clearance and footer | [ConversationKeyboardDeviceTest](../app/src/androidTest/java/com/monsters/mobimon/preview/ConversationKeyboardDeviceTest.kt); isolated Debug preview |

## Integration boundaries

- Journey tests use real app layers with in-memory Room/isolated preferences and
  fake external providers, not GitHub or user credentials. Debug previews establish
  neither provider approval nor real vehicle evidence.
- Migration fixtures cover populated V1 and both V3 forms, not a separately populated
  V2 fixture or `MigrationTestHelper`. Recreation, reopen and process restart differ.
- APK assembly, local tests and `NO-SOURCE` tasks do not prove device execution,
  Release safety, OEM placement or actual providers. Live OAuth/revocation, Copilot
  entitlement/endpoints, AAOS restrictions/reconnection and decorative lifecycle
  need separate target verification. Driving formulas do not prove trusted evidence
  or evaluator-to-award agreement. Release's current fallback remains an open gap.

Record revision, device image, signal/service source and actual checks in the PR.
Keep credentials/private logs out of reports.

## Focused commands and reports

Filter the owning `testDebugUnitTest` with `--tests <qualified-name>` (`test` for
plain Kotlin); this does not execute dependency/device suites. Kover reports use
`:app:koverHtmlReportDebug :app:koverXmlReportDebug`; there is no percentage gate.
[CI](../.github/workflows/android-ci.yml) owns artifact paths/retention.

For actual microphone validation, install both Debug APKs and run
`ConversationVoiceDeviceTest` with instrumentation argument `voiceIntegration=true`.
Use an installed Korean system model, disconnect network and feed synthetic
“안녕하세요” at `MICROPHONE_READY_FOR_FIXTURE`, `NEXT_PHRASE_READY_FOR_FIXTURE` and
`THIRD_PHRASE_READY_FOR_FIXTURE`. The test includes a six-second pause, stops after
the third greeting, checks the full review and then explicit Send/cancellation.
The production path uses continuous `AudioRecord` capture and an external PCM pipe.
Account/vehicle/Copilot dependencies remain fakes. A separate muted-microphone probe
verified external-source support and EOF finalization on GoogleTTSRecognitionService.
A successful run proves neither all OEM audio-source support nor offline use when
network is available. Never put real-user audio or transcripts in production logs.

### CI AAOS environment

[Workflow](../.github/workflows/android-ci.yml) and [cstd.ini](../.github/avd/cstd.ini)
are authoritative: AAOS 34-ext9, CI x86_64, 2560 × 1440 / 160dpi, emulator 35.1.9+.
CI installs the [system-bars overlay](../.github/avd/system-bars-overlay/AndroidManifest.xml)
on a disposable writable AVD. Run [bar validation](../scripts/aaos/check-aaos-system-bars.sh)
(96px top/160px bottom), [host validation](../scripts/aaos/check-aaos-environment.sh)
and canonical device tests. A local ARM64 run does not establish CI x86_64 coverage.

For a persistent local AVD, use
[build_aaos_baked_image.py](../scripts/aaos/build_aaos_baked_image.py) `--help`.
It requires Python 3, JDK 17, Platform/Build Tools 34, `debugfs`, `e2fsck`, a matching
AAOS 34-ext9 Google APIs revision 5 image and a 2560 × 1440 / 160dpi template.
Keep generated images outside Git. Fully stop/restart the resulting AVD, then rerun
bar validation; set `ADB`/`ANDROID_SERIAL` for the intended device.
