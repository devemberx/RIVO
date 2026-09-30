# Repository guidelines

## Workflow

- Read [CONTRIBUTING.md](.github/CONTRIBUTING.md) before development or Git/GitHub work. Follow its issue-claim procedure before implementation; verify the issue is open and assigned to you, or stop.
- Inspect source and build files before asserting what exists. Use [ARCHITECTURE](docs/ARCHITECTURE.md) for technical contracts, [DESIGN](docs/DESIGN.md) for UX/assets and [TESTING](docs/TESTING.md) for coverage.
- Before `ui-ux-pro-max`, read the [project integration rules](.agents/skills/README.md#uiux-design-guidance).

## Boundaries and safety

- Keep domain independent of Android, features independent of one another and concrete data implementations, and bindings in `app`. Follow the [module boundaries](docs/ARCHITECTURE.md#target-modules-and-dependencies).
- Isolate simulations in Debug/test with separate IDs, profiles and databases. Release vehicle data needs a verified adapter; unknown driving state cannot authorize commands.
- Example accounts/codes and rendered success do not verify a provider. Follow the [connection boundary](docs/ARCHITECTURE.md#copilot-connection-ui).
- Write rewards only through atomic repository transactions checking evidence, ownership/revision and occurrence uniqueness. UI/AI cannot grant rewards.
- Use [DESIGN](docs/DESIGN.md), shared `core-ui` and replaceable [PetAvatar](core/core-ui/src/main/java/com/monsters/mobimon/core/ui/PetAvatar.kt). Keep rewards, equipment and authorization outside rendering; do not restore legacy XP/progression UI.
- Derive asset variants from approved masters, changing only requested properties. Follow [asset constraints](docs/DESIGN.md#image-asset-locations); every Luna edit uses the [front, side and back references](docs/DESIGN.md#luna-generation-references) and preserves proportions.

## Documentation

- Keep one home per topic: ARCHITECTURE for technical contracts, DESIGN for UX/assets, TESTING for critical coverage/limits, CONTRIBUTING for workflow and `docs/ui/README.md` for exports. AGENTS holds only repository-wide rules.
- Revise docs only when a contract, visible behavior, integration limit, critical coverage or existing guidance changes. Keep one short paragraph or table row per contract; remove duplication, recaps and implementation detail. Link to code/tests and retain only safety or UX values.
- Keep plans, assignments, experiments and results in issues/PRs. Separate current behavior from plans; check links/anchors and preserve safety and verification limits. Mirror the entire affected shared-skill folder from `.agents/skills/` to `.claude/skills/` in the same commit.

## Verification

- Select local checks by [CONTRIBUTING](.github/CONTRIBUTING.md#verification) and [TESTING](docs/TESTING.md#when-to-run-tests). Skip tests for changes without behavior impact; place needed tests in the owning module.
- Keep CONTRIBUTING and CI aligned when CI required checks change. Report only checks run; APK assembly and Robolectric do not prove device or live integration results.
