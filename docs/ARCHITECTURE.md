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
are read-only summaries; repositories own reward writes.
[PetAvatar](../core/core-ui/src/main/java/com/monsters/mobimon/core/ui/PetAvatar.kt)
owns rendering only. Display evidence and decorative previews cannot authorize commands.
Decorative Home and Store backgrounds use interpreted VSS time from the vehicle
snapshot, independent of device time and quest weather. Debug uses the same VSS
timestamp and interpretation controls; it has no separate background time override.

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

`feature-auth` keeps drafts and voice in Activity memory. The app injects a singleton
`ConversationStore` backed by atomic files in the Android user's no-backup app storage.
Each profile/companion has one current thread, restored after restart or companion
switch. Display names never identify owners. Loading under a different GitHub account
replaces that companion's thread before sending; sign-out hides it. New conversation
atomically replaces the active thread with an empty one, with no archive. Successful
user/reply pairs commit together using thread ID and revision checks. Read/write
failures preserve committed bytes and block sending until explicit recovery/reset.
Home navigation and recording startup preserve the current thread while clearing the
composer. Account/profile/companion changes clear transient private state; initial
account validation retains an unsent draft. Failed sends keep the attempted turn for
Edit/Retry while clearing the composer.
Sending clears the composer while the turn awaits a reply; canceling that wait restores
the text for editing. Home clears it even when a reply is pending.
Departure, backgrounding, parking loss or companion changes cancel work; reject late replies.
Backgrounding and parking loss alone retain the current draft.

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

The opt-in [Debug tool probe](../core/core-auth/src/debug/java/com/monsters/mobimon/core/auth/CopilotToolProbe.kt)
uses the same guarded credentials and HTTP transport with synthetic data, outside
conversation storage. It checks two automatic tool round trips and forced selection
separately; only reproduced live results establish compatibility. The shell-only
Debug entry cancels on pause and has no Release component. Its foundation mode exercises
the shared provider with a synthetic tool and exact local-value acceptance policy.
Manual evaluation uses bundled synthetic questions and writes accepted answers, source
IDs and timing/usage to app cache for review; it never reads or writes user threads.
Service/access limits stop the batch, with no automatic retry.

[Local tool contracts](../core/core-domain/src/main/kotlin/com/monsters/mobimon/core/domain/ConversationTools.kt)
are pure Kotlin and explicitly bound by build variant. Debug enables the bundled manual
lookup; Release keeps an empty registry. An enabled registry uses fixed gpt-4o with
automatic tool selection, at most two model requests and one local execution within one 30-second turn. The adapter validates
the allowlist, bounded string arguments and call IDs, rejects multiple/repeated calls,
and checks identity and Park/AAOS allowance throughout pending work. Failed tools stop
before further generation; every final response passes the injected acceptance policy.
Protocol messages and evidence remain in turn memory; only accepted final text reaches
existing atomic conversation storage.

Debug bundles one Korean 2027 IONIQ 5 NE1 manual prepared by the
[offline extractor](../scripts/manual/prepare_manual.py), with a pinned source hash and
[curation rules](../scripts/manual/curation.json) that omit publication material and
visual page directories. Local BM25 retrieval preserves retained procedures, conditions
and required warnings; missing/corrupt assets fail closed. Extraction and retrieval
need no network, while AI generation uses online Copilot.

Ordinary companion conversation and discussion of supplied vehicle context can answer
directly without manual retrieval. The app supplies bounded time, battery and pet
condition evidence when fresh and authorized; other current readings are unavailable.
The manual tool never supplies live vehicle state.
`search_vehicle_manual` supplies excerpts only when called, and each manual-grounded answer
requires current-turn sources checked by the
[acceptance policy](../app/src/debug/java/com/monsters/mobimon/manual/ManualReplyPolicy.kt).
The app renders its own page metadata and requests abstention or clarification for
unsupported manual questions. The model chooses the conversation route; source checks
do not prove correct routing or semantic truth. Figures, other years/markets and
IONIQ 5 N are outside the bundled manual's coverage, not a limit on ordinary conversation.

The system instruction gives Mobi a curious rabbit persona and Luna a quietly caring
cat persona, using short natural Korean banmal without habitual animal suffixes,
emojis or stage directions. Each send adds optional bounded AAOS context-user name
(`QUERY_USERS`, absent without permission) and independently fresh VSS timestamp.
These values are untrusted data, never instructions or ownership identifiers. Battery
SOC is also sent using the shared freshness policy; unavailable battery is explicit.
Each send reads the runtime debugger setting: enabled selects labeled test readings
in either build, disabled accepts only real-source readings. Source mismatches are
unavailable during switching; simulated fallback is never sent with the debugger off.
Pet condition and its current hunger/sickness evidence use the same classifier as
the display, including valid warning signals. Derived readings are labeled separately
from raw signal paths; explanations cannot invent historical causes or diagnoses.
Declared read-only tools are available only with an explicitly enabled registry:
Debug enables local manual lookup, while Release has none. No live vehicle tools or
reward commands are sent. Debugger warning inputs are available in both build variants
while the debugger is enabled.

Chat time uses the original VSS timestamp's hours, minutes, seconds and UTC offset;
the scene mapper's period is not sent or substituted for an exact time. It is not
converted to system wall-clock time. A timestamp must have its own monotonic observation within
60 seconds. Ticker publications never refresh it. Real adapters must supply time and
battery observation timestamps through `VssRawVehicleSource`; missing battery provenance
is unavailable, and ticker publications cannot refresh it. Debugger readings are
marked simulated. This is an observation
freshness policy, not proof that GNSS provides a continuously advancing clock.

Copilot catalog metadata supplies `max_prompt_tokens`, optional combined
`max_context_window_tokens`, `max_output_tokens` and supported tokenizer. Missing or
unsupported required metadata fails closed. JTokkit counts all prompt text, persona,
context and chat framing; tool requests also count serialized schemas, calls, IDs,
arguments and results with protocol slack. Per-turn numeric usage sums are separate
from per-request estimates; absent provider usage stays unknown. Reserve the requested reply budget (up to 2,048 tokens,
bounded by the advertised output maximum). The server remains authoritative: explicit
context overflow maps to LIMIT with no replay or truncation. The former 16-exchange
and 48,000-character cumulative caps are removed; 4,000 input and 12,000 reply character
bounds remain per-message UI/transport limits. Model limits bound current-thread
growth; New conversation removes its old content locally. Provider retention still
applies. The experimental transport has no stable service contract; fixtures do not
prove live access. Never impersonate another OAuth client/editor.

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
