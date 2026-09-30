# Testing strategy

[CONTRIBUTING.md](../.github/CONTRIBUTING.md#verification) owns required commands.
This document maps behavior to tests and states what those tests do not prove.

## Current setup and test locations

Use JUnit 4, coroutines-test, Robolectric/Compose and Hilt/Room device tests. Keep tests
in the subject module's `src/test`, device tests in `src/androidTest`, and Debug-only
tests in `src/testDebug`. Shared sources are `app/src/journeyTest` and
`core-database/src/migrationTest`. There is no automated golden-comparison gate.

Match the [fixed-display scope](DESIGN.md#visual-language), content bounds, font scale
and IME state. Smaller component fixtures do not imply support for extra displays.
Debug previews are isolated and do not add launcher entries.

## Writing tests

- Test observable behavior in its owning module with controllable external fakes;
  keep helpers out of production.
- Use shared coroutine schedulers and injected clocks; reset dispatchers, close
  databases and cancel jobs. Keep real Main on devices.
- Cover pending/duplicate actions, cancellation and late callbacks. Use Room for
  transactions, concurrency, rollback, reopening and populated migrations.
- Exercise callbacks, Back, focus and enabled state through UI semantics. Screenshots
  verify layout, not persistence, authorization or providers.

## Current requirement map

Keep one row per critical contract group; test sources own detailed cases.
[Architecture](ARCHITECTURE.md#planned-features) owns integration gaps.

### Boundaries, vehicle evidence and persistence

| Contract | Coverage |
| --- | --- |
| Module isolation and routes | `verifyModuleBoundaries`; [navigation](../core/core-navigation/src/test/java/com/monsters/mobimon/core/navigation) |
| Freshness, original evidence and independent signals | [Domain](../core/core-domain/src/test/kotlin/com/monsters/mobimon/core/domain), [presentation](../core/core-presentation/src/test/java/com/monsters/mobimon/core/presentation) |
| VSS mapping, adapter absence and simulated/foreground connection | [VSS](../core/core-vss/src/test/kotlin/com/monsters/mobimon/core/vss), [vehicle/runtime](../app/src/test/java/com/monsters/mobimon/runtime), [Debug vehicle](../app/src/testDebug/java/com/monsters/mobimon/vehicle) |
| VSS-selected Home/Store scene periods and Debug interpretation | [VSS interpreter](../core/core-vss/src/test/kotlin/com/monsters/mobimon/core/vss/VssVehicleInterpreterTest.kt), [background presentation](../core/core-presentation/src/test/java/com/monsters/mobimon/core/presentation/VehicleStateViewModelTest.kt), [Debug scene test](../app/src/testDebug/java/com/monsters/mobimon/di/features/VssBackgroundTimeTest.kt) |
| Atomic rewards/purchases/equip, ownership, uniqueness and rollback | [Database](../core/core-database/src/test/java/com/monsters/mobimon/core/database), [Debug writes](../core/core-database/src/testDebug/java/com/monsters/mobimon/core/database), [Q01 journey](../app/src/test/java/com/monsters/mobimon/Q01JourneyTest.kt) |
| Populated migrations and supported V3 forms | Database migration tests; [shared migration contract](../core/core-database/src/migrationTest/java/com/monsters/mobimon/core/database/LevelingMigrationContract.kt) and device wrappers |
| Driving formulas/catalog rules | Domain suites |

### Authentication

| Contract | Coverage |
| --- | --- |
| Identity/credential lifecycle and bounded, fresh, no-replay transport | [Auth suites](../core/core-auth/src/test/java/com/monsters/mobimon/core/auth); fakes/MockWebServer |
| Debug tool protocol, value verification, bounded rounds and cancellation | [Probe fixtures](../core/core-auth/src/testDebug/java/com/monsters/mobimon/core/auth/CopilotToolProbeTest.kt); fake responses do not establish live tool support |
| Bounded tool execution, strict arguments, whole-turn budgets, cancellation, identity and Park changes | [Tool conversation tests](../core/core-auth/src/test/java/com/monsters/mobimon/core/auth/CopilotToolConversationTest.kt); synthetic live foundation probe remains separate evidence |
| Pet persona, catalog/token budget and server context overflow | Auth Copilot suites; Korean/context/output boundaries and no replay |
| Current-thread reopening, companion/account isolation, reset and stale save rejection | `ConversationStoreTest`, `ConversationViewModelTest`; atomic files and lifecycle fakes |
| Optional AAOS name, exact VSS time, battery/condition evidence and runtime debugger selection | `VehicleConversationContextTest`, `VehicleChatSourceTest` (Debug/Release), `VehicleChatPayloadTest`, `AndroidUserNameDeviceTest`; fake adapters do not prove OEM integration or provisioning |
| Keystore encryption, tampering and persistence | [EncryptedCredentialStoreTest](../core/core-auth/src/androidTest/java/com/monsters/mobimon/core/auth/EncryptedCredentialStoreTest.kt); device |
| Approval guards, recovery and separate Copilot readiness | [Auth feature](../feature/feature-auth/src/test/java/com/monsters/mobimon/feature/auth), [connection journey](../app/src/journeyTest/java/com/monsters/mobimon/CopilotConnectionJourneyTest.kt) |

### Presentation and navigation

| Contract | Coverage |
| --- | --- |
| Committed data, independent retry and appearance | Presentation, [Store](../feature/feature-customization/src/test/java/com/monsters/mobimon/feature/customization), [Home/Settings](../feature/feature-pet/src/test/java/com/monsters/mobimon/feature/pet) |
| Claims, duplicates and reward reconciliation | [Quests](../feature/feature-quest/src/test/java/com/monsters/mobimon/feature/quest), database suites |
| Card availability/selection and unselected warnings | [Vehicle](../feature/feature-vehicle-info/src/test/java/com/monsters/mobimon/feature/vehicle), domain/presentation and Debug suites |
| Shared artwork, badges, motion and geometry | [Core UI](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui), owning feature suites and [native vehicle captures](../app/src/androidTest/java/com/monsters/mobimon/vehicle/VehicleLayoutDeviceTest.kt) |
| Star hanger purchase/apply/removal, both friends, animated glow and reduced motion | `PointEconomyRepositoryTest.starHangerSeedsPurchasesAppliesAndRemovesForBothFriends`, `CustomizationCatalogTest.starHangerPreviewDoesNotEquipUntilAppliedAndIsAvailableToBothFriends`, `StarHangerTest`; item-only rendered reviews in `core-ui/build/reports/star-hanger/` |
| Active Vehicle/Store/Quest Park-loss dialogs, blocked input and recovery | [Shared UI](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/MobiMonParkingInterruptionTest.kt), owning feature suites |
| Shell routes, notifications, launcher identity and overlay bounds | [Shell](../app/src/test/java/com/monsters/mobimon/ui), [service](../app/src/test/java/com/monsters/mobimon/service), Debug and connection suites |
| Chat history continuity, pending composer clearing/cancel restore, failed-turn Retry/Edit after Home, draft clearing, disclosure, reset, authorization, badges and network popup/parking recovery | Auth feature/connection suites; [network status](../app/src/test/java/com/monsters/mobimon/network/AndroidConversationNetworkStatusTest.kt) |
| Microphone draft clearing, permission feedback, confirmed results, PCM integrity, cancellation and explicit Send | [Speech suites](../app/src/test/java/com/monsters/mobimon/speech), auth feature |
| Real microphone and native keyboard flows | [Voice device](../app/src/androidTest/java/com/monsters/mobimon/ConversationVoiceDeviceTest.kt), [keyboard device](../app/src/androidTest/java/com/monsters/mobimon/preview/ConversationKeyboardDeviceTest.kt); isolated dependencies |

## Integration boundaries

Journeys use real app layers with isolated storage and fake external providers;
they do not verify GitHub, Copilot or real vehicle evidence. Migration fixtures cover
populated V1 and both V3 forms, not a separately populated V2. Recreation, reopening
and process restart are distinct.

Assembly, local tests and `NO-SOURCE` tasks do not prove device execution or live
integration. OAuth/entitlement, AAOS restrictions/reconnection, microphone accuracy
and OEM overlay behavior need target checks. Formula tests do not establish trusted
reward evidence; preserve the [Release gap](ARCHITECTURE.md#vehicle-interaction-authorization).
Record actual checks, revision/device/service and limits in the PR; exclude private logs.

## Focused commands and reports

Use `--tests <qualified-name>` on the owning `testDebugUnitTest` (`test` for plain
Kotlin); it does not run dependency/device suites. Kover uses
`:app:koverHtmlReportDebug :app:koverXmlReportDebug`, with no percentage gate.
[CI](../.github/workflows/android-ci.yml) owns artifact retention.

For microphone validation, install both Debug APKs and run the linked voice device
test with `voiceIntegration=true`. Follow its synthetic-fixture readiness markers
with an installed Korean model and disconnected network. Verify repeated speech,
Stop, review and interruption for each service; connected-network success does not
prove offline processing. Never persist/log real-user audio or transcripts.

### CI AAOS environment

For Korean text entry without Google Play, follow [Gboard setup](GBOARD_SETUP.md).

[CI workflow](../.github/workflows/android-ci.yml) and [AVD config](../.github/avd/cstd.ini)
own emulator specifications. Run [system-bar](../scripts/aaos/check-aaos-system-bars.sh)
and [host](../scripts/aaos/check-aaos-environment.sh) validation before canonical device
checks. Local ARM64 success does not establish CI x86_64 coverage. For persistent
images, use [build_aaos_baked_image.py](../scripts/aaos/build_aaos_baked_image.py) `--help`;
keep generated images outside Git and revalidate after a full restart.
