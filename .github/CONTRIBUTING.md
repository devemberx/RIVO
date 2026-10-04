# Contributing to MobiMon

This guide covers the shortest path from a fresh checkout to a verified change.
Use the linked architecture, design, and testing documents when your change needs
more context.

## Quick start

Install Android Studio with JDK 17, Android SDK Platform 34, and SDK Build Tools
34.0.0. Use the repository's Gradle Wrapper; do not install a separate Gradle.

1. Point Android Studio's **Gradle JDK** and your terminal's `JAVA_HOME` to JDK 17.
2. Set the SDK path in the ignored `local.properties` file or with `ANDROID_HOME`.
3. Install the repository hooks:

   ```bash
   bash scripts/setup-hooks.sh
   ```

4. Confirm the environment and build the Debug APK:

   ```bash
   ./gradlew --version
   ./gradlew :app:assembleDebug
   ```

On Windows, use `gradlew.bat` instead of `./gradlew`. The Debug APK is written to
`app/build/outputs/apk/debug/` and installs as the separate simulated `.demo`
application.

For local Release testing, connect a device or emulator and run
`./gradlew :app:installRelease` (`gradlew.bat :app:installRelease` on Windows).
Gradle signs `app/build/outputs/apk/release/app-release.apk`
with the local Debug key; the app keeps its Release build type and
`com.monsters.mobimon` ID. Use a dedicated Release signing key before distribution.

In WSL, use `./gradlew` with a Linux JDK and Android SDK instead of Windows
binaries, even when the checkout is under `/mnt/c`.

## Before changing code

- Before creating a branch or worktree, search open issues and pull requests for
  overlapping work. Coordinate on a matching issue owned by someone else or an
  open pull request; reuse an issue already assigned to you.
- Assign yourself to an unclaimed issue, or create one from
  [ISSUE_TEMPLATE.md](ISSUE_TEMPLATE.md) and assign yourself. Verify that the
  issue is open and assigned to you. Release or hand off your claim if you stop.
- Start implementation only after the issue passes its format check. If GitHub
  ownership cannot be verified, stop and report the blocker.
- Read the relevant parts of [ARCHITECTURE.md](../docs/ARCHITECTURE.md),
  [DESIGN.md](../docs/DESIGN.md), and [TESTING.md](../docs/TESTING.md).
- Confirm the module, dependency, task, or integration in source and build files;
  design documents may describe work that is not implemented yet.

The issue and PR validators use Python 3 and only its standard library.
Validate an issue locally:

```bash
python3 scripts/github/validate_issue.py --title 'Restore the session on startup' --body-file /tmp/issue.md
```

## Verification

Choose local checks using [TESTING.md](../docs/TESTING.md#when-to-run-tests).
For documentation, comments, whitespace and non-runtime metadata, review content
and links and run `git diff --check`; skip Gradle tests, builds and device tests.
For format-only source changes, run the relevant format/static check and
`git diff --check`; skip tests. If behavior may have changed, use the checks below.

For behavior or build changes, format first, review the diff and run the canonical
checks:

```bash
./gradlew ktlintFormat
./gradlew verifyModuleBoundaries ktlintCheck lintDebug testDebugUnitTest :core:core-domain:test :core:core-vss:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :core:core-database:assembleDebugAndroidTest :core:core-auth:assembleDebugAndroidTest :app:assembleRelease :app:testReleaseUnitTest :core:core-auth:testReleaseUnitTest :feature:feature-auth:testReleaseUnitTest
git diff --check
```

Add or update focused tests for changed behavior in the owning module. Check
affected flows on a device or emulator for UI, permissions and platform
integrations. With a compatible device or emulator, the canonical device suites
are:

```bash
./gradlew :core:core-database:connectedDebugAndroidTest :core:core-auth:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

CI runs local and AAOS API 34-ext9 device suites in parallel; both must pass
through `Android checks` for pull requests, including documentation-only ones.
See the [AAOS environment guide](../docs/TESTING.md#ci-aaos-environment) for
host validation and simulated-device limits. Record checks actually run and why
any device/integration check was skipped in the PR.

## Dependency changes

- Declare exact library and plugin versions in `gradle/libs.versions.toml`.
- Use version-catalog aliases from module build files.
- Keep dependency locking enabled and commit every affected `gradle.lockfile` and
  `settings-gradle.lockfile` change with the build change.

Regenerate and inspect lock state before running the normal verification commands:

```bash
./gradlew -p build-logic dependencies --write-locks
./gradlew resolveDependencies --write-locks
```

`resolveDependencies` includes every registered module automatically. Refresh the
build-logic lock first when plugin dependencies change. Never run normal CI
verification with `--write-locks`.

## Parallel feature development

Use a separate checkout/worktree and topic branch for each task. Keep normal
screen changes in the owning feature and use the shared
[UI contracts](../docs/DESIGN.md#reusable-compose-library-and-asset-handoff).
The [module map](../docs/ARCHITECTURE.md#target-modules-and-dependencies) defines
boundaries and registrations. Coordinate shared API, schema and dependency changes
with their consumers; integrate structural changes before rebasing dependent work.

Use the existing convention plugins in [build-logic](../build-logic/src/main/kotlin)
and the version catalog. Regenerate combined lock/schema changes rather than
choosing one side of a conflict. Keep assignments and execution logs in issues/PRs.

## Branches and commits

Create one branch per topic from an up-to-date `main`. Use
`<type>/<short-kebab-summary>`, keep the full name within 50 characters, and use
lowercase ASCII letters, digits, and single hyphens. Valid types are `feat`, `fix`,
`docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, and `revert`.

Commit messages use an ASCII subject of at most 50 characters followed by exactly
two ASCII bullets: why the change is needed, then what changed. Each bullet must
be at most 120 characters including `- `.

```text
feat(auth): restore login

- Reduce repeated sign-ins after restarting the app
- Store tokens and restore the login state
```

The hooks enforce branch and commit formats and block direct pushes to `main`.

## Pull requests and merges

Use [PULL_REQUEST_TEMPLATE.md](PULL_REQUEST_TEMPLATE.md) without removing its
sections. Write the title and body in English. The PR title follows the commit
subject format, and `Changes` contains exactly two single-line ASCII bullets:
why, then what changed.

Validate the PR before creating or updating it:

```bash
python3 scripts/github/validate_pr.py --title 'feat(auth): restore login' --body-file /tmp/pr-body.md
```

Before merging, confirm `PR format` and `Android checks` passed for the current
revision. Use squash merge. Copy the PR title exactly as the squash commit subject
and copy only the two `Changes` bullets as its body; do not add generated text or
rewrite them during the merge.
