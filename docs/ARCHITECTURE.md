# Application architecture

Technical contracts live here; [Design](DESIGN.md) owns UX, [Testing](TESTING.md)
owns coverage, and [Contributing](../.github/CONTRIBUTING.md) owns workflow.

## Current foundation

Home, Settings, customization, Vehicle and Quests use Room/DataStore. GitHub device
authentication restores encrypted credentials; Copilot text chat is experimental.
Korean dictation uses the installed Android recognition service without bundled
ASR models. Mobi/Luna artwork, condition rendering and an opt-in overlay exist.
Real vehicle evidence, background observation and OEM launcher behavior remain
unverified. Catalogs and [UI exports](ui/README.md) do not prove integrations.

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

Routes collect ViewModel `StateFlow` with `collectAsStateWithLifecycle()` and pass
state/callbacks to screens. The shell owns navigation and connection origin;
`FeatureRegistry` rejects missing/duplicate routes. Activity-scoped ViewModels
survive navigation; menu state and animation geometry are transient.

| State | Owner/lifetime |
| --- | --- |
| Profiles, rewards, wallet, inventory, equipment | Room; durable |
| Motion, Debug, launcher preferences | DataStore; independent keys, Debug/launcher default off |
| Vehicle connection and AAOS listener | `CompanionRuntime`; one foreground connection |
| Driving evaluation | Repository memory; simulated, not durable evidence |
| Preview and animation | Feature/renderer; equipment changes only on commit |

Read failures retain committed values with explicit retry. Store can preview shared
appearance while loading, but purchase/equip wait for its inventory. Notifications
are read-only summaries of selected-card cautions and claimable quests; the shell
routes them, and repositories remain responsible for reward writes.

[PetAvatar](../core/core-ui/src/main/java/com/monsters/mobimon/core/ui/PetAvatar.kt)
owns rendering only. Shared freshness-filtered condition includes warnings from
unselected cards; display-only card evidence cannot authorize rewards or commands.
Decorative backgrounds use local time, independent of vehicle timestamps and quest
weather. The Debug background override changes display only. Playback stops when
removed; floating-companion motion preferences do not disable in-app motion.

### Window geometry

The shell owns safe-drawing insets; sibling menus/Debug panels handle their own
window. Features use available constraints and reference width scaling, reflowing
for enlarged text. Conversation additionally handles IME resizing. SVG system bars
are reference coordinates, not runtime padding. Overlays clamp measured bounds to
current bars/cutouts after placement, movement, resize or inset changes.

### Copilot connection UI

`app` binds domain authentication to `core-auth`. The GitHub device flow uses the
configured public client ID, no client secret and `read:user`; unconfigured builds
disable sign-in. Polling respects intervals, slowdown and expiry. Leaving,
backgrounding or losing Park/AAOS allowance cancels pending approval; acceptance
rechecks authorization.

Authentication requires approval, `/user` validation and durable credential storage;
it does not establish Copilot readiness. UI sees no tokens and does no polling.
Credentials use atomic AES-256-GCM storage in `noBackupFilesDir` with Android
Keystore. Tokens never enter UI/domain state, Room, preferences, logs or backups.
Foreground restoration refreshes expiring credentials. Persist rotated tokens
before identity validation, invalidate old revisions, and require successful identity
validation before use. Network errors retain credentials; revocation requires new
approval. Unreadable storage fails closed. Disconnect clears local credentials/key,
not the GitHub grant, subscription, points or cosmetics. Preview success is no proof
of provider approval.

### Keyboard conversation UI

`feature-auth` owns the Activity-memory draft, selection/composition and exchanges.
Navigation/configuration changes retain them; new chat, profile/account change,
disconnect or process restart clear them. Initial account validation retains an
unsent provisional draft. Temporary failures retain draft and attempted turn.
Leaving, backgrounding, parking loss or companion changes cancel work; generations
reject late replies. Pending restoration keeps an existing chat visible with Send
disabled; new signed-out entry opens connection management.

Foreground entry/recheck validates identity and Copilot model access without sending
a completion. Send requires current authentication, bound profile, fresh Park/AAOS
allowance, validated internet and readiness. Provider stages and reply acceptance
recheck ownership and authorization. Checks/replies time out after 30 seconds;
recheck never replays dialogue. Failed turns require explicit Edit or Retry.
Account errors open connection guidance; access/usage failures require account
changes and a later successful check. Cancellation cannot undo provider processing;
retry may consume additional usage.

[OkHttpCopilotApi](../core/core-auth/src/main/java/com/monsters/mobimon/core/auth/OkHttpCopilotApi.kt)
owns the experimental endpoints and metadata. Select enabled Chat Completions
`gpt-4o` only, with no fallback or session-token exchange. Access/model cache is
memory-only, credential-scoped and at most five minutes. Requests use allowlisted
HTTPS hosts, no redirects and no automatic completion replay, including HTTP 503.
A 401 invalidates only the matching credential revision. Errors/logs expose fixed
categories, never provider bodies, dialogue or credentials; Release logging is off.

Send only companion name, fixed instruction and dialogue: no tools, vehicle data
or reward/ownership commands. Display complete text without reasoning. Enforce
[ConversationLimits](../core/core-domain/src/main/kotlin/com/monsters/mobimon/core/domain/ConversationProvider.kt)
with room for the reply; require editing/new chat rather than silently dropping
context. Dialogue never enters durable app storage. Provider retention still applies.
This HTTP integration has no supported Android SDK or stable service contract;
fixtures do not prove live access. Never impersonate another OAuth client/editor.

#### Voice input

`app` implements `ConversationSpeechInput` using `SpeechRecognizer`: prefer dedicated
on-device recognition, otherwise request Korean/offline from the default service.
Offline preference is advisory; service availability does not prove Korean/offline
support. Permission and microphone activation are explicit. Voice requires a resumed,
authenticated, authorized chat but remains usable during Copilot network failures.

The app captures mono 16kHz PCM continuously with `AudioRecord` and supplies it
through `EXTRA_AUDIO_SOURCE` in one segmented recognition request. A bounded memory
queue separates capture from pipe delivery; slow writes never overwrite queued
samples. Stop drains the microphone tail and queued PCM, then closes the stream rather than
calling `stopListening` early. Cancel releases capture/pipe and clears queued audio.
Nothing is written to disk. Overflow or premature service termination ends with an
error, without a silent fallback to microphone sessions with capture gaps.

Keep repeated segments and separate confirmed text from partial hypotheses. A full
final result replaces that request's segments. Service errors or finalization timeout
retain confirmed text as an editable draft with the failure; partial guesses are
never promoted. Explicit cancellation/restrictions retain the previous draft instead.
Only Send submits. Reject callbacks after session cancellation. Startup/capture/
finalization remain bounded at 20/60/20 seconds; the 12-second pause timer is suspended
during speech. Ambient RMS and empty segments cannot extend silence.

External PCM input and stream-close completion were verified on the local AAOS 34-ext9
GoogleTTSRecognitionService with the microphone disabled and network disconnected.
That does not establish support on other services/OEMs; verify those before rollout.
The app does not replay unconfirmed audio after a service failure, and temporary
capture buffers cannot survive process death. Never claim universal loss-free speech
or store/log user audio; OS/driver capture overruns remain possible under overload.
Physical-device accuracy and microphone behavior still need verification.

### Vehicle interaction authorization

Debug uses `.demo`, `mobimon-demo.db`, `demo-profile` and simulated VSS (15-second
freshness). Release uses `mobimon.db`/`local-profile`. Production requires a verified
adapter and unavailable data when it is absent. **Current gap:** the Release binding
still falls back to simulated parked VSS; it is not production verification.
Debug card defaults/edits are display-only; real/unreported signals inherit none.

Only fresh, valid nonmoving Park authorizes parked interactions. Motion blocks;
stationary D/R/N, unknown/stale/unavailable state and AAOS service loss fail closed.
Use current-display `CarAppUseMonitor` allowance and recheck inside transactions.
Restrictions preserve committed data while blocking writes.

`VehicleReading` keeps original evidence separate from freshness-normalized display.
Commands use original evidence, not display ages. Signals carry source, quality,
receive time, epoch and increasing sequence; parking, battery and warnings age
independently. Missing/stale is unavailable, never healthy. Adapters must preserve
observation time, normalize units/order and avoid mixing clock domains across restarts.

## Domain and storage contracts

[AppDatabase](../core/core-database/src/main/java/com/monsters/mobimon/core/database/AppDatabase.kt)
is schema 4, one instance per process. Do not persist transient vehicle history.
Observe with `Flow`; suspend writes distinguish rejection, duplicates and failure.
Inject clocks/IDs and propagate cancellation.

All reward writes atomically validate evidence, ownership/revision and uniqueness:

- Legacy runs fix source/profile/rules/start; completion requires later same-epoch
  evidence and commits completion, run finish and XP together. A gap can cancel,
  never complete, a run. Run and profile/quest identities remain unique.
- Point awards commit occurrence, ledger and balance together; profile/quest/occurrence
  and ledger references are unique. Return the committed amount only after success.
- Purchase validates price, compatibility, funds and ownership before debit/grant;
  Equip requires committed ownership without another debit. Concurrent calls cannot
  overspend or duplicate items. Debug changes also update ledger/balance atomically.

Keep network calls outside transactions. Never split rewards across Room/DataStore
or replace on conflict. Failures roll back. Migrations preserve identities, evidence,
rewards and equipment without destructive reset or XP conversion. Register the full
V1→V4 chain, including original/expanded V3 support; legacy XP remains compatibility data.

## Planned features

### Points, cosmetics and quest occurrences

Driving awards check per-quest in-memory evaluation inside the transaction; Debug
supplies simulated signals/history. Production still needs trusted evidence, real
occurrence IDs/counts and agreement with weather-scaled amounts. Define recurrence,
reset clock/zone and interruption explicitly; `completedQuestIds` cannot express
repeat eligibility. Run extensions must finish with revision/start checks atomically.

### Shared vehicle condition and overlay

Reuse shared vehicle/appearance streams; gaps/restarts begin a new epoch. Background
observation is not guaranteed. Overlay/launcher support needs explicit opt-in and
verified OEM placement, lifecycle and service/permission state. Permission/preference
alone is insufficient. Verify restarts, failures and restrictions on the target.

### AI conversation and session

Future SDK/relay adapters must preserve the conversation contract. AI can return
replies or allowlisted proposals, never write vehicle state, rewards or ownership.
Local vehicle, quest and cosmetic UI must remain usable during AI failures.
