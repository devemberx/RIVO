# Application architecture

Technical contracts live here. [Design](DESIGN.md) owns UX,
[Testing](TESTING.md) owns coverage and limits, and
[Contributing](../.github/CONTRIBUTING.md) owns workflow.

## Current foundation

Local state uses Room and DataStore; GitHub credentials use encrypted storage.
Copilot chat is experimental, and Korean dictation uses the installed Android
service. Real vehicle evidence, background observation, OEM launcher behavior and
live provider access remain unverified. [UI exports](ui/README.md) are references,
not integration evidence.

## Scope and decisions

Use MVVM, unidirectional state and constructor-injected repositories. Domain rules
stay in plain Kotlin. Data is local; there is no MobiMon backend, sync or reinstall
recovery.

## Target modules and dependencies

[settings.gradle.kts](../settings.gradle.kts) registers the modules.

| Module | Responsibility | Internal dependencies |
| --- | --- | --- |
| `app` | Shell, runtime and Hilt bindings | Features and core implementations |
| `core-domain` | Models, contracts and rules | None |
| `core-auth` | GitHub OAuth, credentials and experimental Copilot transport | Domain |
| `core-database` | Room, DataStore and transactions | Domain |
| `core-vss` | VSS models and adapter seam | Domain |
| `core-ui` | Shared components, theme and artwork | None |
| `core-navigation` | Routes and callbacks | None |
| `core-presentation` | Shared wallet, vehicle and appearance state | Domain |
| `feature-*` | Owning screen and presentation | Domain, UI, navigation, presentation |

Features do not depend on each other or concrete data implementations. Domain has
no Android, Compose, Room, Hilt or SDK DTO dependency. Map transport/storage models
at boundaries and bind implementations in `app`. Keep test helpers out of production.
`verifyModuleBoundaries` checks dependencies and selected imports; review generated
code and leaked types separately. `core-vss` cannot own persistence, DI or UI.

## State and lifecycle

The shell owns navigation; routes collect lifecycle-aware ViewModel state and pass
state/callbacks to screens. Activity ViewModels survive navigation.

| State | Owner |
| --- | --- |
| Profiles, rewards, wallet, inventory, equipment | Room |
| Motion, Debug and launcher preferences | DataStore; Debug/launcher default off |
| Vehicle and AAOS connection | `CompanionRuntime`; foreground only |
| Driving evaluation | Repository memory; drive evidence aggregate in DataStore |
| Preview and animation | Renderer; equipment changes only on commit |

Fresh Activity launches draw the common sky before constructing the feature graph
and Compose shell. Authentication construction/restoration runs on IO; process-owned
vehicle observation and foreground restrictions retain their existing lifecycle.

Read failures retain committed data. Notifications are read-only summaries;
repositories own reward writes. [PetAvatar](../core/core-ui/src/main/java/com/monsters/mobimon/core/ui/PetAvatar.kt)
renders appearance only; display evidence and previews cannot authorize commands.
The [background resolver](../core/core-ui/src/main/java/com/monsters/mobimon/core/ui/CompanionBackground.kt)
uses interpreted VSS time and independently persisted prop/effect selections.
The [catalog](../core/core-ui/src/main/java/com/monsters/mobimon/core/ui/CompanionBackgroundCatalog.kt)
owns scene assets, periods, crop and registered compatibility mappings; features
do not branch by scene. Repositories own purchase and equipment persistence.

### Character rendering and reference data

`art/` manifests are production inputs, not runtime configuration. Schema 2 links
items to base manifests by path/hash; revised masters retain independently checked
sources. [Preflight](../.agents/skills/character-animation/scripts/preflight.py)
checks images and derives geometry from pixel landmarks. An input review binds the
catalog, shared contract, manifests and images; metadata changes invalidate that
inventory rather than silently renewing approval. Runtime loaders use packaged
assets and renderer-specific transforms through `core-ui`; reference edits alone
do not replace runtime art. [Design](DESIGN.md#character-and-item-references) owns
the visual contract.

### Window geometry

The shell owns safe insets; separate windows own theirs. Features reflow for enlarged
text and IME. SVG system bars do not define runtime padding. Overlays stay within
measured bounds after placement, resizing or inset changes.

### Copilot connection UI

`core-auth` owns GitHub device approval and identity validation. Unconfigured builds
disable sign-in. Use the configured public client ID, no client secret and `read:user`
only. Leaving the screen, backgrounding or losing Park/AAOS allowance cancels approval;
acceptance rechecks authorization. Authentication alone does not prove Copilot
readiness; UI never receives tokens or polls providers.

Credentials use atomic Keystore-backed authenticated encryption outside backups.
Tokens never enter UI/domain state or logs. Persist rotations and invalidate old
revisions; network failures retain credentials, while revocation/unreadable storage
fail closed. Identity reads have bounded transient retries respecting provider waits;
approval/refresh writes are not replayed. Disconnect removes local credentials,
not the GitHub grant, subscription or rewards.

### Keyboard conversation UI

`feature-auth` keeps drafts/voice in Activity memory. `ConversationStore` atomically
stores one thread per profile/companion in user-specific no-backup storage. Account
changes isolate previous owners; New conversation clears only the active thread.
Commits require matching thread ID/revision. Storage failures retain committed
bytes and block sends until recovery.

Sending requires identity, fresh Copilot readiness, validated internet and Park/AAOS
allowance. Departure/backgrounding/allowance loss cancels work and rejects late replies.
Connectivity hints do not cancel pending requests or saves; turn failures do not
invalidate readiness. Recheck never resends, and failed turns need explicit Edit/Retry.
Cancellation cannot undo provider processing. [Design](DESIGN.md#conversation) owns
composer and recovery behavior.

[Copilot transport](../core/core-auth/src/main/java/com/monsters/mobimon/core/auth/OkHttpCopilotApi.kt)
uses enabled `gpt-4o` Chat Completions, credential-scoped caching and allowlisted
HTTPS hosts. No redirects, fallback, session-token exchange or automatic completion
replay. A 401 invalidates only its credential revision. Release logging is off;
provider bodies, dialogue and tokens never enter logs/errors.

[Tool contracts](../core/core-domain/src/main/kotlin/com/monsters/mobimon/core/domain/ConversationTools.kt)
are pure Kotlin. Both variants register manual search and read-only vehicle context;
[routing](../app/src/main/java/com/monsters/mobimon/chat/VehicleToolRouting.kt) restricts
recognized current-state requests to vehicle tools. Empty manual results supply no
citations and preserve valid vehicle evidence. Calls share a bounded
[turn budget](../core/core-domain/src/main/kotlin/com/monsters/mobimon/core/domain/ConversationExecutionBudget.kt)
and stop on repeated queries without semantic progress. Identity, Park and
source/session checks remain enforced; tools cannot issue commands or rewards.

The pinned Korean IONIQ 5 manual bundle fails closed on missing assets and cannot
supply current vehicle state. Manual claims need current-turn sources and
[checked citations](../app/src/main/java/com/monsters/mobimon/manual/ManualReplyPolicy.kt).
Replies use a versioned envelope with manual IDs and vehicle references;
[acceptance](../app/src/main/java/com/monsters/mobimon/chat/GroundedConversationReplyPolicy.kt)
preserves AI prose and rechecks source, session, value and validity before storage.
Equal-value receipts remain acceptable; changed values reject without substitution.
Invalid envelopes or references get at most one tool-free correction within the
original evidence/budget/guards. Network, refusal, changed evidence and authorization
failures are not corrected. These checks do not prove numerical or prose accuracy.

[The evidence reader](../app/src/main/java/com/monsters/mobimon/chat/VehicleChatEvidenceReader.kt)
serves registered fields by topic, with explicit origin and unavailable values.
Receipt, change, publication and capture times are distinct; reads/ticks cannot
refresh receipts. Periodic signals need declared TTLs, and on-change signals need
a synchronized live subscription. Missing provenance in the atomic
[source frame](../core/core-vss/src/main/kotlin/com/monsters/mobimon/core/vss/VssObservationFrame.kt)
fails closed for chat. Vehicle time is an as-of value, not receipt time; derived
conditions cannot invent diagnoses or history. Command freshness remains separate.

Catalog token limits and tokenizer must be supported or sending fails closed.
The server decides context overflow; no automatic truncation or replay. Local
conversation reset does not delete provider data. Experimental transport and
fixtures do not prove live access.

#### Voice input

`app` sends in-memory audio to the installed Android recognition service. Offline
preference does not prove Korean or offline support. Recording requires explicit
permission and an authenticated, authorized resumed chat. Stop yields editable text;
only Send submits. Cancellation retains the prior draft, releases capture and rejects
late callbacks. Never store or log user audio. External audio, EOF handling and
recognition accuracy need target-service/device verification.

### Vehicle interaction authorization

Debug uses separate `.demo` IDs, profiles and database with simulated VSS. Release
requires a verified adapter. Missing adapters currently use a simulated parked
fallback, which Release [source checks](../core/core-domain/src/main/kotlin/com/monsters/mobimon/core/domain/VehicleFreshnessPolicy.kt)
mark unavailable. This fallback cannot establish real parking or vehicle evidence.

Only fresh, valid nonmoving Park and current-display AAOS allowance authorize parked
interactions. Motion, stationary D/R/N, unknown/stale/unavailable state and service
loss fail closed; transactions recheck allowance. Restrictions retain committed data.
Commands use original evidence, not display-normalized freshness. Preserve source,
quality, observation/receive time, epoch and sequence; age signals independently.
Adapters normalize units without mixing clock domains or manufacturing observations.

## Domain and storage contracts

[AppDatabase](../core/core-database/src/main/java/com/monsters/mobimon/core/database/AppDatabase.kt)
is schema 4, one instance per process. Observe with `Flow`; suspend writes distinguish
rejection, duplicate and failure. Inject clocks/IDs, propagate cancellation and keep
transient vehicle history out of storage.

Reward transactions validate evidence, ownership/revision and unique occurrences;
awards commit occurrence, ledger and balance together. Purchases validate price,
funds and ownership of a compatible friend before atomic debit/grant; that friend
need not be active. Equipping requires an owned item and compatible active friend,
without another debit. Concurrent writes cannot overspend or duplicate rewards.
Keep network outside transactions and rewards inside Room; failures roll back.
Migrations preserve identities, evidence, rewards and equipment. Legacy XP remains
compatibility data, not progression.

Point quest occurrences are one-time, reset-zone day, ISO week, drive ID (none means not met)
or capped daily keys recording cumulative units, so a claim awards only new units up to
the cap. Quest status is completed only when its current occurrence is committed;
day/week changes are rechecked each minute.

Drive evidence advances only from REAL, non-Debug snapshots whose evidence frame comes from
a connected `VSS_ADAPTER` session with valid observations
([DriveEvidence](../core/core-domain/src/main/kotlin/com/monsters/mobimon/core/domain/DriveEvidence.kt)).
Moving starts a drive ID that survives restarts; Park ends it. Unobserved negative-event
signals (acceleration, speed, lane departure, distraction) count as not met. Driving rewards
require evidence from the profile's source, so Debug simulation cannot satisfy Release.

## Integration limits

### Points, cosmetics and quest occurrences

Real driving rewards await a verified Release adapter. Maintenance arrival and
long-trip rest currently have no evidence signal.

### Shared vehicle condition and overlay

Vehicle stream gaps/restarts start a new epoch. Background observation is not
established. Overlay and launcher behavior need explicit opt-in and target-OEM
validation of placement, lifecycle, restart and permissions.
