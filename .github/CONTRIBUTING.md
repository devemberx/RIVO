# Contributing to MobiMon

This guide covers the shortest path from a fresh checkout to a verified change.
Use the linked architecture, design, and testing documents when your change needs
more context.

## Quick start

Install Android Studio with JDK 17, Android SDK Platform 34, SDK Build Tools
34.0.0, and Python 3.9 or newer. Use the repository's Gradle Wrapper; do not
install a separate Gradle.

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

In WSL, use `./gradlew` with a Linux JDK and Android SDK instead of Windows
binaries, even when the checkout is under `/mnt/c`.

## Bundled speech models

App builds run `:app:prepareSpeechModels` to download the pinned SenseVoice INT8
archive and Silero VAD model (about 164 MB), verify SHA-256 values, and generate
`app/build/generated/speechAssets/stt/`. The APK contains the models; recording
never downloads them. Sources and hashes live in
[models.json](../scripts/stt/models.json); packaged attribution lives in
[NOTICE.txt](../app/src/main/assets/stt/NOTICE.txt).

Downloads are cached under `GRADLE_USER_HOME/caches/mobimon-stt` and verified on
every preparation. A corrupt cache fails the build; remove only the reported
file and rerun the task. A populated cache permits offline model preparation.
Neither CI nor local builds need the ignored research `.artifacts` directory.
Set `-Pmobimon.python=python` if your Python 3 executable is named `python`.

The official sherpa-onnx AAR is resolved from an exclusive GitHub-release Ivy
repository and pinned by version, dependency locks and SHA-256 verification.
The verification metadata currently checks this AAR only; existing Maven
artifacts retain their prior trust policy.

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

Validate an issue locally with Python 3.9 or newer:

```bash
python3 scripts/github/validate_issue.py --title 'Restore the session on startup' --body-file /tmp/issue.md
```

## Verification

For code or build changes, format first and review the resulting diff:

```bash
./gradlew ktlintFormat
./gradlew :app:testSpeechModelProvisioning verifyModuleBoundaries ktlintCheck lintDebug testDebugUnitTest :core:core-domain:test :core:core-vss:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :core:core-database:assembleDebugAndroidTest :core:core-auth:assembleDebugAndroidTest
git diff --check
```

Add or update tests for changed behavior. Follow [TESTING.md](../docs/TESTING.md)
for test placement and Fakes. Check affected flows on a device or emulator when
changing UI, permissions, or platform integrations.

With a compatible device or emulator, run the Room, Keystore and shared app journeys:

```bash
./gradlew :core:core-database:connectedDebugAndroidTest :core:core-auth:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

CI runs local checks and AAOS API 34-ext9 device tests in parallel. Both jobs
must pass through `Android checks`. See the
[AAOS environment guide](../docs/TESTING.md#ci-aaos-environment) for host validation,
local reproduction and the limits of simulated device coverage.

For documentation-only changes, verify the content and relative links and run
`git diff --check`; an Android build is unnecessary. Record every check performed,
including skipped device or integration checks and their reasons, in the PR.

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
choosing one side of a conflict. Keep assignments and execution logs in issues/PRs;
component readiness and final screen acceptance are separate.

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
