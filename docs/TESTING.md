# Testing strategy

[CONTRIBUTING.md](../.github/CONTRIBUTING.md#verification) owns required local
commands and CI gates. Choose checks by what changed, then report only checks run.

## When to run tests

| Change | Local verification |
| --- | --- |
| Documentation, comments, whitespace or non-runtime metadata only | Review content/links and `git diff --check`; **do not run Gradle tests, builds or device tests**. |
| Code formatting without semantic changes | Run the relevant formatter/static check and `git diff --check`; **do not run tests**. |
| Localized behavior change | Run focused tests in the owning module, then the applicable checks in [Contributing](../.github/CONTRIBUTING.md#verification). |
| Cross-module contract, build/dependency, schema, authorization, reward, vehicle-safety or platform behavior change | Run the canonical checks; add device tests when behavior depends on Android/AAOS. |

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

## Critical coverage

| Contract | Main coverage |
| --- | --- |
| Module boundaries, VSS evidence and foreground vehicle state | `verifyModuleBoundaries`; [domain](../core/core-domain/src/test/kotlin/com/monsters/mobimon/core/domain), [VSS](../core/core-vss/src/test/kotlin/com/monsters/mobimon/core/vss), [runtime](../app/src/test/java/com/monsters/mobimon/runtime) |
| Atomic rewards, purchase/equipment and populated migrations | [database](../core/core-database/src/test/java/com/monsters/mobimon/core/database), `migrationTest`, device database tests |
| Credentials, bounded identity retries, inline errors, explicit popup recovery and conversation ownership | [auth](../core/core-auth/src/test/java/com/monsters/mobimon/core/auth), [auth feature](../feature/feature-auth/src/test/java/com/monsters/mobimon/feature/auth), [bounded reply correction](../core/core-auth/src/androidTest/java/com/monsters/mobimon/core/auth/CopilotReplyCorrectionDeviceTest.kt), Debug probe fixtures |
| Chat observation validity, references and multi-tool budgets | [chat tests](../app/src/test/java/com/monsters/mobimon/chat), [native reference validation](../app/src/androidTest/java/com/monsters/mobimon/chat/GroundedConversationReplyPolicyDeviceTest.kt), [60 synthetic cases](../app/src/debug/assets/chat/vehicle-evaluation.json) and [contract runner](../app/src/testDebug/java/com/monsters/mobimon/chat/VehicleConversationEvaluationTest.kt); native checks cover unchanged AI prose, obsolete protocol rejection and manual citations; synthetic cases cover evidence revalidation across both persona inputs and a held-out split, but do not measure model routing, persona quality or prose accuracy |
| Bundled manual retrieval, per-turn routing and cited replies | [shared manual tests](../app/src/test/java/com/monsters/mobimon/manual), [routing fixtures](../app/src/test/java/com/monsters/mobimon/chat/VehicleToolRoutingTest.kt), [Release registry/assets and checked queries](../app/src/testRelease/java/com/monsters/mobimon/chat/ReleaseConversationPolicyTest.kt) and [transport selection](../core/core-auth/src/test/java/com/monsters/mobimon/core/auth/CopilotToolRoutingTest.kt); [opt-in live fixture probe](../app/src/androidTest/java/com/monsters/mobimon/chat/CopilotGroundingLiveProbeTest.kt) runs only with `live_copilot=true` against the existing verified account and never writes chat history; default device runs skip it. Fixed fixtures and lexical recall do not prove real vehicle behavior or answer truth |
| Park restrictions, quests, vehicle cards, shared appearance and navigation | Owning [feature](../feature) and [core UI](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui) suites, app journeys |
| Reward artwork scale, common ground and equipment clearance | [Rendered happy poses](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/HappyArtworkAlignmentTest.kt) compare body pixels and ground; [reward layout](../feature/feature-quest/src/test/java/com/monsters/mobimon/feature/quest/QuestRewardLayoutTest.kt) covers equipped looks, bonus rows and enlarged text; [AAOS dialog captures](../app/src/androidTest/java/com/monsters/mobimon/quest/QuestRewardLayoutDeviceTest.kt) verify the shared footer and label bounds in the native Dialog |
| Mobi hungry artwork and six-second equipped motion | [parts](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/MobiHungryPartsTest.kt) checks equipped loading, master density, fallback and decoded bitmap budget; [motion](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/MobiHungryAnimationTest.kt) checks timing, rigid equipment, fixed body regions and loop seams. Host-dependent rendered pixel hashes are not a CI contract; GPU frame pacing and continuous playback acceptance require device review |
| Luna hungry layers and master-based equipment | [layer checks](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/LunaHungryArtworkTest.kt) verify unchanged body/equipment regions, canonical closed-mouth pixels, sequential thought timing and continuous droplet release, shared texture budget and loop boundaries; [native captures](../app/src/androidTest/java/com/monsters/mobimon/ui/LunaHungryDeviceTest.kt) cover mouth/saliva poses, status/equipment changes and still previews; [Home captures](../app/src/androidTest/java/com/monsters/mobimon/ui/LunaHungryHomeDeviceTest.kt) retain equipped looks with dialogue at normal and doubled text size for visual spacing review. Natural motion requires observed playback |
| Mobi equipped sick motion, orbit clearance and wandering preference | [motion/assets](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/MobiWarningAnimationTest.kt), [crossfades](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/DecorativeMotionTest.kt) and [native equipped capture](../app/src/androidTest/java/com/monsters/mobimon/ui/MobiSickDeviceTest.kt); deterministic samples verify routing and motion state, while natural playback and frame pacing still require observed device review |
| Startup first-frame ordering, scene-before-face ordering, local readiness, saved completion and delayed-storage escape | [first-frame tests](../app/src/test/java/com/monsters/mobimon/ui/StartupPreviewViewTest.kt), [startup Compose tests](../app/src/test/java/com/monsters/mobimon/ui/StartupLoadingTest.kt), [native splash resource contract](../app/src/testDebug/java/com/monsters/mobimon/BrandingTest.kt); system handoff, startup latency and frame pacing require device review |
| VSS background periods, same-scene fallback and registered decorations | [period/assets](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/CompanionBackgroundTest.kt), [catalog](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui/BackgroundCatalogTest.kt), [Home](../feature/feature-pet/src/test/java/com/monsters/mobimon/feature/pet/CompanionReviewTest.kt) and [Store](../feature/feature-customization/src/test/java/com/monsters/mobimon/feature/customization/StoreReferenceScreenTest.kt); Robolectric verifies updates/crop and preview/thumbnail agreement, not live vehicle integration |
| Keystore, microphone, native keyboard and AAOS behavior | [device tests](../app/src/androidTest/java/com/monsters/mobimon), core auth/database device tests |

## What tests do not prove

Journeys use isolated storage and fake external providers; they do not verify live
GitHub, Copilot or real vehicle evidence. Debug tool probes and fake adapters do not
establish provider/OEM compatibility. Any live baseline/candidate comparison must use identical fixtures/model settings and report numeric/unit accuracy, unsupported claims, correct/unnecessary abstention, tool omissions, source confusion, latency/deadline failures and token estimates separately from deterministic checks. Migration fixtures cover populated V1 and both
V3 forms, not a separately populated V2. Assembly, Robolectric and `NO-SOURCE`
results do not prove device execution. OAuth entitlement, AAOS restrictions,
offline Korean speech, OEM overlays and the
[Release vehicle gap](ARCHITECTURE.md#vehicle-interaction-authorization) require
target verification. Never record private audio, dialogue or tokens in test reports.

## CI AAOS environment

[CI](../.github/workflows/android-ci.yml) and the [AVD config](../.github/avd/cstd.ini)
own emulator specifications. Run the [system-bar](../scripts/aaos/check-aaos-system-bars.sh)
and [host](../scripts/aaos/check-aaos-environment.sh) checks before canonical device
checks. Local ARM64 results do not establish CI x86_64 coverage. For Korean text
entry without Google Play, see [HeliBoard setup](HELIBOARD_SETUP.md).
