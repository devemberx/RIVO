# Testing strategy

[CONTRIBUTING.md](../.github/CONTRIBUTING.md#verification) owns required local
commands and CI gates. Choose checks by what changed, then report only checks run.

## When to run tests

| Change | Local verification |
| --- | --- |
| Documentation, comments, whitespace or non-runtime metadata only | Review content/links and `git diff --check`; **do not run Gradle tests, builds or device tests**. |
| Character reference schema or production helpers, without Android/runtime changes | Run reference preflight for all registered combinations and the [Python helper tests](../.agents/skills/character-animation/scripts/tests); check skill mirrors and links. |
| Code formatting without semantic changes | Run the relevant formatter/static check and `git diff --check`; **do not run tests**. |
| Localized behavior change | Run focused tests in the owning module, then the applicable checks in [Contributing](../.github/CONTRIBUTING.md#verification). |
| Android cross-module contract, build/dependency, storage schema, authorization, reward, vehicle-safety or platform change | Run the canonical checks; add device tests when behavior depends on Android/AAOS. |

If a change's effect is uncertain, treat it as behavior-changing. Do not add tests
that only mirror a reversible, low-impact implementation. CI's required Android
checks still run for pull requests; these rules avoid unnecessary **local** runs.

## Test placement

Use JUnit 4, coroutines-test, Robolectric/Compose and Hilt/Room device tests.
Keep tests in the owning module's `src/test`, `src/testDebug` or `src/androidTest`;
shared journeys/migrations use `app/src/journeyTest` and
`core-database/src/migrationTest`. Keep fakes out of production. Inject clocks and
schedulers, close databases/cancel jobs, and use real Main on devices. Test
observable behavior, including pending actions, cancellation and late callbacks.
Screenshots verify layout, not persistence, authorization or providers.
Use Robolectric native graphics for WebP pixel geometry, such as [Store friend
selection](../feature/feature-customization/src/test/java/com/monsters/mobimon/feature/customization/ClothesFriendSelectionTest.kt);
the legacy decoder can return placeholder dimensions.

## Critical coverage

| Contract | Main coverage |
| --- | --- |
| Store clothing handoff | [loading regressions](../feature/feature-customization/src/test/java/com/monsters/mobimon/feature/customization/StoreClothingPreviewLoadingTest.kt) keep Mobi/Luna visible during cold clothing loads, reject superseded completion and leave equipment/purchase writes untouched; device frame pacing needs observed review |
| Module boundaries, VSS evidence and foreground vehicle state | `verifyModuleBoundaries`; [domain](../core/core-domain/src/test/kotlin/com/monsters/mobimon/core/domain), [VSS](../core/core-vss/src/test/kotlin/com/monsters/mobimon/core/vss), [runtime](../app/src/test/java/com/monsters/mobimon/runtime) |
| Atomic rewards, purchase/equipment and populated migrations | [database](../core/core-database/src/test/java/com/monsters/mobimon/core/database), `migrationTest`, device database tests |
| Credentials, bounded identity retries, inline errors, explicit popup recovery and conversation ownership | [auth](../core/core-auth/src/test/java/com/monsters/mobimon/core/auth), [auth feature](../feature/feature-auth/src/test/java/com/monsters/mobimon/feature/auth), [bounded reply correction](../core/core-auth/src/androidTest/java/com/monsters/mobimon/core/auth/CopilotReplyCorrectionDeviceTest.kt), Debug probe fixtures |
| Chat evidence, references and tool budgets | [chat tests](../app/src/test/java/com/monsters/mobimon/chat), [native reply policy](../app/src/androidTest/java/com/monsters/mobimon/chat/GroundedConversationReplyPolicyDeviceTest.kt) and [synthetic runner](../app/src/testDebug/java/com/monsters/mobimon/chat/VehicleConversationEvaluationTest.kt); fixtures verify protocol/evidence handling, not live routing, persona or prose accuracy |
| Manual retrieval, routing and citations | [manual tests](../app/src/test/java/com/monsters/mobimon/manual), [routing](../app/src/test/java/com/monsters/mobimon/chat/VehicleToolRoutingTest.kt), [Release policy](../app/src/testRelease/java/com/monsters/mobimon/chat/ReleaseConversationPolicyTest.kt) and [opt-in live probe](../app/src/androidTest/java/com/monsters/mobimon/chat/CopilotGroundingLiveProbeTest.kt); the live probe requires `live_copilot=true` and a verified account, spends provider usage and does not write history |
| Park restrictions, quests, vehicle cards, shared appearance and navigation | Owning [feature](../feature) and [core UI](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui) suites, app journeys |
| Reference integrity and current-input review | [preflight tests](../.agents/skills/character-animation/scripts/tests/test_preflight.py) cover base/item links, shared-contract hashes, retained sources, archived-source provenance, revised masters and derived geometry; file checks alone leave generation review pending |
| Store entry artwork loading | [Las loading](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/LasIdleLoadingTest.kt) and [Store backgrounds](../feature/feature-customization/src/test/java/com/monsters/mobimon/feature/customization/StoreBackgroundLoadingTest.kt) cover worker decoding, responsive pending slots, cache reuse, bounded scene retention and rejection of late results; [Store rendering](../feature/feature-customization/src/test/java/com/monsters/mobimon/feature/customization/StoreBackgroundRenderingTest.kt) checks VSS periods and crop after loading. Entry latency requires paired device measurements |
| Luna idle/hungry first-image loading | [worker loading](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/LunaIdleLoadingTest.kt) checks cold animated/still asset reads, pending equipment, cached preview responsiveness and nonblocking invalidation of first images/layers; actual startup latency and frame pacing require device measurement |
| Luna cap fade bounds | [host regressions](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/LunaFadeBoundsTest.kt) and [native captures](../app/src/androidTest/java/com/monsters/mobimon/ui/LunaFadeBoundsDeviceTest.kt) retain the sprout above the unchanged body slot during status recovery and appearance handoff |
| Reward artwork and dialog layout | [happy poses](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/HappyArtworkAlignmentTest.kt), [reward layout](../feature/feature-quest/src/test/java/com/monsters/mobimon/feature/quest/QuestRewardLayoutTest.kt) and [AAOS captures](../app/src/androidTest/java/com/monsters/mobimon/quest/QuestRewardLayoutDeviceTest.kt) cover scale/ground, cap clearance, one weather-bonus line, common footer and enlarged text |
| Mobi hungry artwork and six-second equipped motion | [parts](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/MobiHungryPartsTest.kt) checks equipped loading, master density, fallback and decoded bitmap budget; [expression cache](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/MobiHungryExpressionCacheTest.kt) checks unchanged submitted textures, exact endpoint alpha and bounded interpolation error across equipment; [motion](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/MobiHungryAnimationTest.kt) checks timing, rigid equipment, fixed body regions and loop seams. Host-dependent rendered pixel hashes are not a CI contract; GPU frame pacing and continuous playback acceptance require device review |
| Mobi first-frame loading and equipped sick motion | [loading](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/MobiLoadingTest.kt) checks worker asset reads and pending equipment; [ring cache](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/MobiSickArtworkCacheTest.kt) checks concurrent sharing and unchanged texture pixels; [motion/assets](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/MobiWarningAnimationTest.kt), [crossfades](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/DecorativeMotionTest.kt) and [native equipped capture](../app/src/androidTest/java/com/monsters/mobimon/ui/MobiSickDeviceTest.kt) cover orbit clearance and wandering preference. Natural playback and frame pacing still require observed device review |
| Mobi/Luna run and launcher transitions | [Mobi memory](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/MobiAtlasMemoryTest.kt), [Luna memory](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/LunaAtlasMemoryTest.kt) and [Luna holds](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/LunaSceneAssetsTest.kt) cover equipped loaders, scene extent and size-aware cache selection. [Luna loading](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/LunaAppearLoadingTest.kt) checks the empty pending entrance slot, failed-load fallback and completion once. Native decoders ([Mobi](../core/core-ui/src/androidTest/java/com/monsters/mobimon/core/ui/MobiAtlasDeviceTest.kt), [Luna](../core/core-ui/src/androidTest/java/com/monsters/mobimon/core/ui/LunaAtlasDeviceTest.kt)) check both cell sizes and allocations; equipped playback ([Mobi](../app/src/androidTest/java/com/monsters/mobimon/ui/MobiAtlasPlaybackDeviceTest.kt), [Luna](../app/src/androidTest/java/com/monsters/mobimon/ui/LunaAtlasPlaybackDeviceTest.kt)) checks handoff completion and records controlled-clock process PSS. This includes instrumentation overhead and does not establish OEM performance or continuous visual acceptance |
| Startup first-frame ordering, scene-before-face ordering, local readiness, saved completion and delayed-storage escape | [first-frame tests](../app/src/test/java/com/monsters/mobimon/ui/StartupPreviewViewTest.kt), [startup Compose tests](../app/src/test/java/com/monsters/mobimon/ui/StartupLoadingTest.kt), [native splash resource contract](../app/src/testDebug/java/com/monsters/mobimon/BrandingTest.kt); system handoff, startup latency and frame pacing require device review |
| VSS background periods, same-scene fallback and registered decorations | [period/assets](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/CompanionBackgroundTest.kt), [catalog](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/BackgroundCatalogTest.kt), [Home](../feature/feature-pet/src/test/java/com/monsters/mobimon/feature/pet/CompanionReviewTest.kt) and [Store](../feature/feature-customization/src/test/java/com/monsters/mobimon/feature/customization/StoreReferenceScreenTest.kt); Robolectric verifies updates/crop and preview/thumbnail agreement, not live vehicle integration |
| Luna sick source layers and equipped hungry cap | [layer/contact tests](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/LunaSickArtworkTest.kt), [cache/status tests](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/PetAvatarTest.kt), [worker loading](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/LunaSickLoadingTest.kt), [native equipped capture](../app/src/androidTest/java/com/monsters/mobimon/ui/LunaSickDeviceTest.kt) and [Home dialogue captures](../app/src/androidTest/java/com/monsters/mobimon/ui/LunaSickHomeDeviceTest.kt); loading checks cover cold animated/still reads, pending equipment and nonblocking invalidation without late cache retention. Deterministic samples verify compact face scale, independent body motion, rest/loop/contact and hardware crossfades; Home captures support visual clearance review at normal and enlarged text. Occluded limbs use reference-based visual review, while natural playback and frame pacing require observed review |
| Keystore, microphone, native keyboard and AAOS behavior | [device tests](../app/src/androidTest/java/com/monsters/mobimon), core auth/database device tests |

## What tests do not prove

Fixtures do not verify live GitHub/Copilot, real vehicle evidence or OEM compatibility.
Live comparisons need identical fixtures/model settings and separate accuracy,
abstention, tool/source errors, latency and usage measurements. Migration fixtures
cover populated V1 and both V3 forms, not a separately populated V2. Assembly,
Robolectric and `NO-SOURCE` do not prove device execution. Offline Korean speech,
AAOS restrictions, overlays and the [Release vehicle gap](ARCHITECTURE.md#vehicle-interaction-authorization)
need target verification. Never record private audio, dialogue or tokens.

## CI AAOS environment

[CI](../.github/workflows/android-ci.yml) and the [AVD config](../.github/avd/cstd.ini)
own emulator specifications. Run the [system-bar](../scripts/aaos/check-aaos-system-bars.sh)
and [host](../scripts/aaos/check-aaos-environment.sh) checks before canonical device
checks. Local ARM64 results do not establish CI x86_64 coverage. For Korean text
entry without Google Play, see [HeliBoard setup](HELIBOARD_SETUP.md).
