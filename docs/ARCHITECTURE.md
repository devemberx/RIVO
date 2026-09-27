# Application architecture

Technical contracts live here; [Design](DESIGN.md) owns UX, [Testing](TESTING.md)
owns coverage, and [Contributing](../.github/CONTRIBUTING.md) owns workflow.

## Current foundation

Local screens use Room/DataStore; GitHub credentials restore from encrypted storage.
Copilot chat is experimental; Korean dictation uses the installed Android service.
Real vehicle evidence, background observation and OEM launcher behavior remain
unverified. [UI exports](ui/README.md) do not prove integration.

## Scope and decisions

Use MVVM, unidirectional state and constructor-injected repositories. Domain rules
stay in plain Kotlin. Data is local: no MobiMon backend, sync or reinstall recovery.

## Target modules and dependencies

[settings.gradle.kts](../settings.gradle.kts) registers the modules.

| Module | Responsibility | Internal dependencies |
| --- | --- | --- |
| `app` | Shell, runtime and Hilt bindings | Features and core implementations |
| `core-domain` | Models, contracts and rules | None |
| `core-auth` | GitHub OAuth, credential storage and experimental Copilot transport | Domain |
| `core-database` | Room, DataStore and transactions | Domain |
| `core-vss` | VSS raw models, generated signal containers and adapter seam | Domain |
| `core-ui` | Stateless components, theme and artwork | None |
| `core-navigation` | Routes, entries and callbacks | None |
| `core-presentation` | Shared wallet, vehicle and appearance state | Domain |
| `feature-pet` | Home and Settings | Domain, UI, navigation, presentation |
| `feature-customization` | Catalog, preview, purchase and equipment | Same four core modules |
| `feature-quest` | Quest progress and commands | Same four core modules |
| `feature-vehicle-info` | Vehicle readings and availability | Same four core modules |
| `feature-auth` | Authentication, keyboard conversation UI and AI context | Same four core modules |

Features never depend on each other or concrete data implementations. Domain has
no Android, Compose, Room, Hilt or SDK DTO dependency. Map transport/storage models
at implementation boundaries; bind implementations in `app`. Test helpers stay
outside production sources.

`verifyModuleBoundaries` checks dependencies and selected imports; generated code
and leaked transport/storage types still require review. `core-vss` may host the
Android adapter seam, but cannot own persistence, DI, UI or feature code.

## State and lifecycle

Routes collect lifecycle-aware ViewModel state; screens receive state and callbacks.
The shell owns navigation. Activity ViewModels survive navigation; menus and
animation geometry are transient.

| State | Owner/lifetime |
| --- | --- |
| Profiles, rewards, wallet, inventory, equipment | Room; durable |
| Motion, Debug and launcher preferences | DataStore; independent keys, Debug/launcher default off |
| Vehicle and AAOS connection | `CompanionRuntime`; foreground only |
| Driving evaluation | Repository memory; simulated, not trusted evidence |
| Preview and animation | Renderer; equipment changes only on commit |

Read failures retain committed data; purchase/equip wait for inventory. Notifications
are read-only summaries; repositories own reward writes. Current notifications use
a three-card popup; the [left-panel reference](DESIGN.md#screens-and-navigation) is
not implemented. [PetAvatar](../core/core-ui/src/main/java/com/monsters/mobimon/core/ui/PetAvatar.kt)
owns rendering only. Display evidence and decorative previews cannot authorize commands.

### Window geometry

The shell owns safe insets; separate windows own theirs. Features reflow for enlarged
text and IME. SVG system bars are references, not runtime padding. Overlays clamp
measured bounds after placement, movement, resizing or inset changes.

### Copilot connection UI

`core-auth` owns GitHub device approval and identity validation; unconfigured builds
disable sign-in. Use the configured public client ID, no client secret and `read:user`
only. Departure, backgrounding or loss of Park/AAOS allowance cancels
approval; acceptance rechecks authorization. Authentication alone does not establish
Copilot readiness. UI never receives tokens or polls providers.

Credentials use atomic authenticated encryption with Android Keystore outside
backups. Tokens cannot enter UI/domain state, Room, preferences or logs. Persist
rotations and invalidate old revisions before validating identity and using tokens.
Network failures retain credentials; revocation requires approval again; unreadable
storage fails closed. Disconnect removes local credentials/key, not the GitHub grant,
subscription or rewards.

### Keyboard conversation UI

`feature-auth` keeps draft, conversation identity and full provider context in Activity
memory. Home entry or successful recording startup clears only the visible messages;
hidden exchanges stay hidden. New chat clears all conversation state. Account/profile
change, disconnect or process restart clears private data; initial account validation
retains an unsent draft. Temporary failures retain the attempted turn and draft.
Departure, backgrounding, parking loss or companion changes cancel work; reject late replies.

Entry/recheck requires fresh Copilot access and model responses, bypassing cached
readiness without sending a completion. Transport failures/timeouts use network
recovery. Send and reply acceptance require current identity/ownership, fresh
Park/AAOS allowance, validated internet and readiness. Requests are bounded; failed
turns require explicit Edit/Retry. Cancellation cannot undo provider processing,
and retry may consume usage.

[Copilot transport](../core/core-auth/src/main/java/com/monsters/mobimon/core/auth/OkHttpCopilotApi.kt)
uses enabled `gpt-4o` Chat Completions only, credential-scoped memory caching and
allowlisted HTTPS hosts. No redirects, fallback, session-token exchange or automatic
completion replay. A 401 invalidates only its credential revision. Logs/errors must
not expose provider bodies, dialogue or tokens; Release logging is off.

Send only companion name, fixed instruction and dialogue; no tools, vehicle data or
reward commands. Enforce [ConversationLimits](../core/core-domain/src/main/kotlin/com/monsters/mobimon/core/domain/ConversationProvider.kt)
without silently dropping context. Dialogue is not stored durably; provider retention
still applies. The integration has no stable service contract; fixtures do not prove
live access. Never impersonate another OAuth client/editor.

#### Voice input

`app` supplies continuous in-memory audio to the installed Android recognition service.
Offline preference is advisory; availability does not prove Korean/offline support.
Microphone activation and permission are explicit and require a resumed, authenticated,
authorized chat. Network recovery dialogs release local capture while preserving the draft.

Stop drains captured audio before finalizing an editable draft; only Send submits.
Errors retain confirmed text, never partial guesses. Cancellation/restrictions retain
the previous draft and release capture. Requests are bounded; reject late callbacks.
Overflow, unsupported external audio or premature termination fails explicitly rather
than silently dropping audio or changing capture mode.

[Speech implementation](../app/src/main/java/com/monsters/mobimon/speech) owns capture,
segmentation and timing details. Never store/log user audio. External-source support,
EOF completion and microphone accuracy require target-service/device verification;
universal loss-free recognition is not established.

### Vehicle interaction authorization

Debug uses separate `.demo` IDs/profiles/database and simulated VSS; Debug card values
are display-only. Release requires a verified adapter and unavailable data when absent.
**Current gap:** Release still falls back to simulated parked VSS; this is not
production verification.

Only fresh, valid nonmoving Park and current-display AAOS allowance authorize parked
interactions. Motion, stationary D/R/N, unknown/stale/unavailable state and service
loss fail closed; transactions recheck allowance. Restrictions preserve committed data.

Commands use original evidence, not freshness-normalized display. Preserve source,
quality, observation/receive time, epoch and increasing sequence; age signals
independently. Missing/stale is unavailable, never healthy. Adapters normalize units
and must not mix clock domains or manufacture fresh observation times.

## Domain and storage contracts

[AppDatabase](../core/core-database/src/main/java/com/monsters/mobimon/core/database/AppDatabase.kt)
is schema 4, one instance per process. Observe with `Flow`; suspend writes distinguish
rejection, duplicates and failure. Inject clocks/IDs and propagate cancellation.
Do not persist transient vehicle history.

Reward writes atomically validate original evidence, ownership/revision and uniqueness:

- Legacy completion needs later same-epoch evidence and commits run finish, completion
  and XP together. Evidence gaps cancel rather than complete runs.
- Point awards commit occurrence, ledger and balance together; occurrence and ledger
  references are unique. Return only the committed amount.
- Purchase validates price, compatibility, funds and ownership before debit/grant;
  Equip needs committed ownership without another debit. Debug writes are also atomic.

Concurrent calls cannot overspend or duplicate rewards/items. Keep network outside
transactions; failures roll back. Never split rewards across Room/DataStore or replace
on conflict. V1→V4 migrations preserve identities, evidence, rewards and equipment,
including both V3 forms; legacy XP is compatibility data, not converted progression.

## Planned features

### Points, cosmetics and quest occurrences

Driving rewards currently use in-memory evaluation and Debug evidence. Production
needs trusted evidence, real occurrence IDs/counts and agreement with reward amounts.
Define recurrence, reset clock/zone and interruptions explicitly; completed IDs alone
cannot represent repeat eligibility. Run extensions must validate revision/start atomically.

### Shared vehicle condition and overlay

Reuse shared vehicle/appearance streams; gaps/restarts begin a new epoch. Background
observation is not guaranteed. Overlay/launcher behavior needs explicit opt-in and
target-OEM validation of placement, lifecycle, restart and permissions; preference
or permission alone is insufficient.

### AI conversation and session

Future SDK/relay adapters must preserve the conversation contract. AI can return
replies or allowlisted proposals, never write vehicle state, rewards or ownership.
Local vehicle, quest and cosmetic UI must remain usable during AI failures.
