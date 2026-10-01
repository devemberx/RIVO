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
| `core-ui` | Stateless components, theme and artwork | None |
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
| Driving evaluation | Repository memory; simulated, not trusted evidence |
| Preview and animation | Renderer; equipment changes only on commit |

Read failures retain committed data. Notifications are read-only summaries;
repositories own reward writes. [PetAvatar](../core/core-ui/src/main/java/com/monsters/mobimon/core/ui/PetAvatar.kt)
renders appearance only; display evidence and previews cannot authorize commands.
Home and Store backgrounds use interpreted VSS time, never device time.

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

Credentials use atomic authenticated encryption with Android Keystore outside
backups. Tokens never enter UI/domain state, Room, preferences or logs. Persist token
rotations and invalidate old revisions before use. Network failure retains credentials; revocation and unreadable
storage fail closed. Disconnect removes local credentials and key, not the GitHub
grant, subscription or rewards.

### Keyboard conversation UI

`feature-auth` keeps drafts and voice in Activity memory. The singleton
`ConversationStore` writes atomic files in the Android user's no-backup storage.
Each profile/companion has one current thread. Account changes hide or replace the
previous owner's thread; New conversation atomically clears only the active thread.
Committed user/reply pairs require matching thread ID and revision. Storage failures
retain committed bytes and block sends until explicit recovery.

Sending requires current identity, fresh Copilot readiness, validated internet and
Park/AAOS allowance. Departure, backgrounding or allowance loss cancels work; late
replies are rejected. Failed turns require explicit Edit/Retry; cancellation cannot
undo provider processing, so retry may consume usage. Composer and draft behavior
follows [Design](DESIGN.md#conversation).

[Copilot transport](../core/core-auth/src/main/java/com/monsters/mobimon/core/auth/OkHttpCopilotApi.kt)
uses enabled `gpt-4o` Chat Completions, credential-scoped caching and allowlisted
HTTPS hosts. No redirects, fallback, session-token exchange or automatic completion
replay. A 401 invalidates only its credential revision. Release logging is off;
provider bodies, dialogue and tokens never enter logs/errors.

[Tool contracts](../core/core-domain/src/main/kotlin/com/monsters/mobimon/core/domain/ConversationTools.kt)
are pure Kotlin. Debug registers manual search and read-only `get_vehicle_context(topic)`;
Release registers no tools but uses the same checked reply policy. Sequential validated
batches share a [turn budget](../core/core-domain/src/main/kotlin/com/monsters/mobimon/core/domain/ConversationExecutionBudget.kt):
30 seconds, 64,000 estimated prompt plus reserved output tokens, 64,000 characters per
and combined tool results, and 12,000 reply characters. Counts are not fixed. Repeated
normalized queries with unchanged semantic evidence stop; receipt/ticker changes alone
are not progress. Identity, Park and source/session checks remain enforced. No automatic
replay, commands or rewards are available. Synthetic Debug probes do not establish live compatibility.

The pinned 2027 Korean IONIQ 5 bundle fails closed on missing assets; it cannot supply
current vehicle state. Manual claims need current-turn sources and
[checked citations](../app/src/debug/java/com/monsters/mobimon/manual/ManualReplyPolicy.kt).
All app replies use a versioned envelope with separate manual IDs and vehicle references;
[acceptance](../app/src/main/java/com/monsters/mobimon/chat/GroundedConversationReplyPolicy.kt)
renders vehicle values/units and rechecks source, session, value and validity before storage.
Equal-value receipts remain acceptable; a changed value rejects the answer without substitution.
Reference checks do not prove routing or arbitrary prose semantics.

Basic context retains bounded AAOS name, battery, vehicle clock, condition and reasons;
[the shared reader](../app/src/main/java/com/monsters/mobimon/chat/VehicleChatEvidenceReader.kt)
also serves every [registered field](../core/core-domain/src/main/kotlin/com/monsters/mobimon/core/domain/VehicleChatFieldCatalog.kt)
by topic. Simulated and real sources share IDs, with explicit origin and unavailable values.
Receipt, change, publication and capture times are distinct monotonic metadata; reads/ticks
cannot refresh receipts. Periodic signals use adapter-declared TTLs; on-change signals need
a synchronized live subscription/lease; unknown delivery fails closed. The atomic
[source frame](../core/core-vss/src/main/kotlin/com/monsters/mobimon/core/vss/VssObservationFrame.kt)
is optional: legacy adapters without provenance remain unavailable to chat. Real bindings
must declare delivery/dependency semantics; this does not change command freshness policy.
Vehicle clock is an as-of value, not receipt time; Debug clock uses a 60-second TTL.
Derived conditions expose checked scope and cannot invent diagnoses or historical causes.

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
requires a verified adapter and unavailable data when absent. **Current gap:**
Release still falls back to simulated parked VSS; it is not production verification.

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

Reward transactions atomically validate evidence, ownership/revision and occurrence
uniqueness. Run completion commits finish, completion and legacy XP together; point
awards commit occurrence, ledger and balance together. Purchase validates price,
funds, item ownership and ownership of any compatible friend before debit/grant;
the friend need not be active. Equip requires committed item ownership and the
compatible active friend without another debit. Concurrent calls cannot overspend
or duplicate rewards/items. Keep network outside transactions; failures roll back. Never
split rewards across Room/DataStore or replace committed data on conflict. V1–V4
migrations preserve identities, evidence, rewards and equipment, including both V3
forms; legacy XP is compatibility data, not progression.

## Planned features

### Points, cosmetics and quest occurrences

Production rewards need trusted vehicle evidence and occurrence IDs/counts. Recurrence,
reset time and interrupted runs require explicit rules; completed IDs alone do not
establish repeat eligibility.

### Shared vehicle condition and overlay

Vehicle stream gaps/restarts start a new epoch. Background observation is not
established. Overlay and launcher behavior need explicit opt-in and target-OEM
validation of placement, lifecycle, restart and permissions.

### AI conversation and session

Future SDK/relay adapters must preserve the conversation contract. AI may return
replies or allowlisted proposals, never write vehicle state, rewards or ownership.
Local vehicle, quest and cosmetic UI remains usable during AI failures.
