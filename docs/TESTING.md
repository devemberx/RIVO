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
| Credentials, Copilot protocol/tools and conversation ownership | [auth](../core/core-auth/src/test/java/com/monsters/mobimon/core/auth), [auth feature](../feature/feature-auth/src/test/java/com/monsters/mobimon/feature/auth), Debug probe fixtures |
| Park restrictions, quests, vehicle cards, shared appearance and navigation | Owning [feature](../feature) and [core UI](../core/core-ui/src/test/java/com/monsters/mobimon/core/ui) suites, app journeys |
| Keystore, microphone, native keyboard and AAOS behavior | [device tests](../app/src/androidTest/java/com/monsters/mobimon), core auth/database device tests |

## What tests do not prove

Journeys use isolated storage and fake external providers; they do not verify live
GitHub, Copilot or real vehicle evidence. Debug tool probes and fake adapters do not
establish provider/OEM compatibility. Migration fixtures cover populated V1 and both
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
