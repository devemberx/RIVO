# Project development skills

This project vendors three skills for Android development and UI/UX design and
maintains a local background asset workflow.
Upstream content is preserved except for trailing whitespace cleanup in
UI/UX Pro Max's `scripts/design_system.py`, including a docstring. Code logic is unchanged.

| Skill | Purpose | Upstream source | Pinned commit | License |
| --- | --- | --- | --- | --- |
| [compose-agent](compose-agent/SKILL.md) | Write and review Jetpack Compose code, state, lifecycle, and animations. | [hamen/compose_skill](https://github.com/hamen/compose_skill/tree/f815c31d6cc1a4af4ce2796d08eb7d8b506785c9/skills/compose-agent) | `f815c31d6cc1a4af4ce2796d08eb7d8b506785c9` | [MIT](compose-agent/LICENSE) |
| [testing-setup](testing-setup/SKILL.md) | Review and configure Android unit, UI, screenshot, and device testing. | [android/skills](https://github.com/android/skills/tree/bac232fd02b0855df9275281a2a7a47643768719/testing/testing-setup) | `bac232fd02b0855df9275281a2a7a47643768719` | [Apache-2.0](testing-setup/LICENSE.txt) |
| [ui-ux-pro-max](ui-ux-pro-max/SKILL.md) | Review visual hierarchy, interaction, accessibility, and design consistency. | [nextlevelbuilder/ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill/tree/7f69fed6a2717900085f1bc3b263721f8ba025e2/.claude/skills/ui-ux-pro-max) | `7f69fed6a2717900085f1bc3b263721f8ba025e2` | [MIT](ui-ux-pro-max/LICENSE) |

The Android skills were imported on 2026-09-09 and UI/UX Pro Max on 2026-09-12
using the Codex skill installer.

The project-maintained [background-time-variants](background-time-variants/SKILL.md)
skill generates time variants from one approved master, separating fixed scene
geometry from time-dependent lighting and requiring post-generation visual review.
Its complete folder is mirrored in `.claude/skills/`.

## Usage

Codex discovers these skills in `.agents/skills/`. For other agents, use their
skill import mechanism or ask them to read the relevant `SKILL.md` directly.

Example prompts:

- `Use compose-agent to implement this Compose screen using the project's existing dependencies.`
- `Use compose-agent to review state restoration and animation lifecycle in this feature.`
- `Use testing-setup to review the current Android test setup and identify the checks needed for this change.`
- `Use background-time-variants to create and verify Cyberpunk City time variants while preserving building heights and object placement.`

Apply these workflows within the user's requested scope and the repository's
instructions. Check upstream version assumptions against the project's actual
toolchain. The Compose skill is not a substitute for AAOS-specific documentation
and validation on the provided vehicle environment.

## UI/UX design guidance

Use UI/UX Pro Max for focused visual and interaction reviews, together with
`compose-agent` for Compose implementation. Start with the existing
[design direction](../../docs/DESIGN.md); record adopted decisions there rather
than generating a competing `design-system/` source of truth. Do not use
`--persist` in the normal project workflow.

This is a vendored skill, not a Claude marketplace installation. Resolve
`scripts/search.py` relative to the loaded `SKILL.md` and invoke its absolute
path with Python 3; do not rely on the upstream `${CLAUDE_PLUGIN_ROOT}` examples.
For example, from the repository root:

```bash
python3 "$PWD/.agents/skills/ui-ux-pro-max/scripts/search.py" "keyboard focus modal" --domain ux
python3 "$PWD/.agents/skills/ui-ux-pro-max/scripts/search.py" "accessibility semantics" --stack jetpack-compose
```

Select `jetpack-compose` for the Android app and review the current Figma source
linked in DESIGN.md for visual intent. Treat generic web dimensions and animation
suggestions as recommendations that need platform-specific review. Preserve the
[vehicle safety contracts](../../docs/ARCHITECTURE.md#state-and-lifecycle)
and use the project's [verification requirements](../../.github/CONTRIBUTING.md#verification).

## Updates

Review upstream changes before replacing a skill. Import the skill directory
with all of its references, keep its license, and update the pinned commit above.
