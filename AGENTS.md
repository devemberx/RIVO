# Repository guidelines

## Workflow

- Read [CONTRIBUTING.md](.github/CONTRIBUTING.md) before development or Git/GitHub work; it owns the workflow.
- Before implementation, follow the [issue claim procedure](.github/CONTRIBUTING.md#before-changing-code). Stop if ownership cannot be verified.
- Inspect source/build configuration before claiming a module, integration or task exists. Read [ARCHITECTURE.md](docs/ARCHITECTURE.md) for structural/behavior changes, [DESIGN.md](docs/DESIGN.md) for UI, and [TESTING.md](docs/TESTING.md) for tests.
- Before using `ui-ux-pro-max`, read the [project integration rules](.agents/skills/README.md#uiux-design-guidance).
- For PR/commit descriptions, write exactly two one-line English bullets starting with `- `: why, then what changed; at most 120 characters each.

## Code and safety

- Prefer clear names and structure. Comment only non-obvious intent, constraints or workarounds; keep comments and API contracts current.
- Keep domain code independent of Android, features independent of each other/concrete data implementations, and bindings in `app`. Follow the [module boundaries](docs/ARCHITECTURE.md#target-modules-and-dependencies).
- Keep simulations in Debug/test sources with separate IDs, profiles and databases. Release vehicle data stays unavailable until a real adapter is verified; unknown driving state cannot authorize commands.
- Example accounts/codes and rendered connection success are not provider verification. Follow the [connection boundary](docs/ARCHITECTURE.md#copilot-connection-ui).
- Reward writes must use atomic repository transactions with evidence, ownership/revision and occurrence-uniqueness checks. UI/AI must not grant rewards directly.
- Use the current [visual specification](docs/DESIGN.md), shared `core-ui` primitives and replaceable [PetAvatar](core/core-ui/src/main/java/com/monsters/mobimon/core/ui/PetAvatar.kt). Keep rewards, equipment and authorization outside the renderer. Do not restore obsolete XP/progression UI.
- Generate asset variants from the approved master. Follow the [variant constraints](docs/DESIGN.md#image-asset-locations), change only requested properties and reject unintended drift. Every Luna generation/edit must use the [front, side and back references](docs/DESIGN.md#luna-generation-references) and preserve character proportions.

## Documentation

- Keep one home per topic: ARCHITECTURE for technical contracts, DESIGN for UX/assets, TESTING for critical coverage/limits, CONTRIBUTING for workflow, and `docs/ui/README.md` for exports. AGENTS contains only essential repository-wide rules.
- Update reference docs only when a contract, user-visible behavior, integration limit or critical coverage changes, or existing guidance becomes wrong. Do not append a feature recap for every change; revise the affected section in the same change.
- Use one short paragraph (usually 2–3 sentences) or table row per changed contract. Keep algorithms, callbacks, tunable constants and detailed cases in code/tests; link instead of copying. Retain values needed to enforce safety or UX requirements.
- Remove repeated/stale text before adding more. Keep assignments, plans, experiments, reproduction procedures, execution results and environment snapshots in issues/PRs. Local `docs/superpowers/` plans remain ignored; separate current behavior from planned work.
- Before finishing doc changes, review the added detail for necessity and check links/anchors. Preserve safety contracts and verification limits when shortening.
- Update CONTRIBUTING and CI together when required checks change. For shared skill changes, edit `.agents/skills/` and copy the entire affected folder, including references/licenses, to `.claude/skills/` in the same commit.

## Verification

- Run the [canonical checks](.github/CONTRIBUTING.md#verification) for the change type. Place focused tests in the subject module/package; one test file per source file is unnecessary.
- Complete [final visual acceptance](docs/TESTING.md#final-figma-visual-acceptance) after the final UI code change. Feature owners own screen acceptance; shared-component or build success is insufficient.
- Report only checks actually executed. APK assembly and Robolectric do not prove device tests or real integrations ran.
