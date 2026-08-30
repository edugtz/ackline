# Current Phase

## Status

**IMPLEMENTATION — IN PROGRESS**

Phase: `4 — Durable Remote ACK Sync`

Implementation branch: `4-durable-remote-ack`

Base branch: `dev`

---

## Objective

Synchronize explicit local acknowledgments from Ackline back to Hermes without making local UX depend on Mac, Tailscale, or ACK-endpoint availability.

Phase 3 already established the durable local truth:

`Pendiente` → explicit user action → `Vista`

and persists:

- `acknowledgedAt`
- `ackSyncState = PENDING`

Phase 4 consumes that durable `PENDING` state and adds eventual remote synchronization:

```text
explicit Visto
    ↓
Room local ACK = durable immediately
    ↓
ackSyncState = PENDING
    ↓
unique WorkManager drain
    ↓
HTTPS over private Tailscale path
    ↓
Mac ACK endpoint
    ↓
Hermes notification state
    ↓
Ackline ackSyncState = SYNCED
```

Remote availability must never undo, delay, or block the local `Vista` transition.

---

## Baseline Already Proven

Phase 0:

- Android project builds/installs on the physical Oppo.
- Firebase/FID registration works.
- Mac fake sender can target the device.

Phase 1:

- FCM data-only delivery works.
- Foreground/background/removed-from-Recents delivery works.
- Wi-Fi/mobile transitions recover without manually reopening Ackline.
- Temporary offline recovery works.
- Native severity channels work.

Phase 2:

- Room is the device Inbox source of truth.
- `notificationId` is the local idempotency key.
- Alerts persist locally.
- Duplicate FCM does not create duplicate rows or repost known alerts.
- Inbox/Detail/Setup product baseline passed physical and visual QA.

Phase 3:

- explicit `Visto` is the only local ACK path;
- notification action, Inbox action, and Detail action share one local ACK operation;
- opening app/detail and swiping tray do not ACK;
- Room v1 → v2 migration passed automated and physical upgrade QA;
- `ackSyncState = PENDING` is durable;
- repeated local ACK preserves the first timestamp;
- duplicate FCM after ACK preserves `Vista`;
- tray cancellation works;
- Phase 3 passed final GitHub review and was merged to `dev`.

Do not redesign those proven layers without a concrete Phase 4 requirement.

---

## Phase 4 Question

This phase must answer:

> If I mark an alert `Visto` while Hermes/Tailscale/the Mac is unavailable, can Ackline preserve the local decision, retry safely later, and eventually synchronize exactly that ACK to Hermes without duplicates, tight retry loops, or user intervention?

The answer must be demonstrably yes.

---

## Source-of-Truth Rules

During Phase 4:

- **Room** = device Inbox + local ACK + local ACK-sync state.
- **Hermes notification SQLite/state layer** = server-side notification truth.
- **FCM** = inbound alert transport only.
- **Android tray** = presentation only.
- **WorkManager** = durable deferred execution trigger, not state storage.
- **Tailscale** = private ACK network path only; FCM push must remain independent of it.

An ACK is locally valid even when remote synchronization has not happened yet.

---

## Exact ACK Semantics

Local acknowledgment remains unchanged from Phase 3.

Only these actions locally acknowledge:

1. notification action `Visto`
2. Inbox row action `Visto`
3. Detail action `Marcar como visto`

Remote synchronization happens **after** local persistence.

Forbidden behavior:

```text
tap Visto
    ↓
wait for Mac/Tailscale
    ↓
only then show Vista
```

Required behavior:

```text
tap Visto
    ↓
persist Vista locally
    ↓
UI updates immediately
    ↓
remote sync happens independently
```

---

## Phase 4 ACK Sync States

Extend the existing app-owned `AckSyncState`.

Phase 4 values:

- `NONE`
- `PENDING`
- `SYNCED`
- `ERROR`

Semantics:

### NONE

No local ACK exists.

Expected invariant:

`acknowledgedAt == null`

### PENDING

A local ACK exists and has not yet been confirmed by Hermes.

Expected invariant:

`acknowledgedAt != null`

### SYNCED

Hermes has idempotently accepted/confirmed the ACK.

Expected invariant:

`acknowledgedAt != null`

### ERROR

A non-transient remote problem was detected and automatic tight retry must stop.

Examples:

- malformed request/protocol mismatch;
- authentication/authorization rejection if auth is used;
- notification ID permanently unknown to Hermes;
- other explicit non-retryable 4xx response.

`ERROR` does **not** undo local `Vista`.

---

## Database Version

Current Phase 3 Room database version:

`2`

Phase 4 target:

`3`

Add only metadata justified by remote synchronization:

- `ackSyncedAtEpochMillis: Long?`
- `lastAckError: String?`
- `ackToken: String?` (storage-only credential for the Hermes ACK request)

`ackSyncState` already exists and must not be duplicated.

Migration v2 → v3 must be non-destructive.

Expected SQL shape:

```sql
ALTER TABLE alerts ADD COLUMN ackSyncedAtEpochMillis INTEGER;
ALTER TABLE alerts ADD COLUMN lastAckError TEXT;
ALTER TABLE alerts ADD COLUMN ackToken TEXT;
```

Existing rows retain their current `ackSyncState`.

Existing `PENDING` rows are intentionally important: Phase 4 must be able to drain ACKs created before the Phase 4 worker existed.

Keep exported schemas:

- v1
- v2
- v3

No destructive fallback.

---

## Error Metadata Rule

`lastAckError` is internal diagnostic state.

Store only a small sanitized machine-readable category, for example:

- `http_400`
- `http_403`
- `http_404`
- `protocol`
- `client_error`

Do not store:

- bearer tokens;
- full exception dumps;
- response bodies containing unknown content;
- personal notification text;
- FID;
- full private URLs if avoidable.

Transient network failures may remain `PENDING`; WorkManager already owns retry timing.

---

## Remote ACK Contract

The existing Hermes ACK server is authoritative. Reuse its contract:

```text
POST /ack/<notification_id>
X-Ack-Token: <per-notification token>
```

There is no request body. Android must safely encode the notification ID path
segment and must not set or spoof `Tailscale-User-Login`; Tailscale Serve
injects that identity header on the private route.

Only ACK metadata is sent. No alert title/message is required.

### Success

A newly acknowledged Hermes notification and an already-acknowledged Hermes notification must both be treated idempotently as success.

Actual success response:

`200 OK`

### Permanent client errors

Examples:

- `400` invalid request/route;
- `403` invalid/missing ACK token or missing Tailscale identity;
- `404` notification ID does not exist.

These must not enter a tight automatic retry loop.

### Transient errors

Examples:

- DNS/connect/TLS/timeout;
- Tailscale unavailable;
- Mac asleep/unreachable;
- `408`;
- `429`;
- `5xx`.

These remain retryable.

The approved preflight confirmed the existing Hermes notification-state ACK
operation and server contract. Android does not duplicate that server state
layer.

---

## Mac / Tailscale Boundary

Preferred deployment shape:

```text
Android
    ↓ HTTPS
private Tailscale hostname
    ↓
Tailscale Serve / private tailnet route
    ↓
127.0.0.1 Mac ACK endpoint
    ↓
existing Hermes notification-state layer
    ↓
Hermes SQLite
```

Preferred properties:

- ACK server binds to loopback, not public interfaces.
- No Tailscale Funnel/public exposure.
- HTTPS terminates through the approved private Tailscale path.
- Do not add public cloud infrastructure.
- Do not make inbound FCM delivery depend on Tailscale.

If the actual environment differs, the preflight must document it before implementation.

---

## Server-Side Idempotency

The Hermes-side ACK transition must be idempotent.

First request:

- identify notification by `notification_id`;
- persist acknowledged state/timestamp using the existing Hermes notification-state layer;
- return success.

Repeated request for the same already-acknowledged notification:

- do not duplicate records;
- do not corrupt state;
- do not replace an authoritative first ACK timestamp unless the existing Hermes semantics explicitly require otherwise;
- return the same success class.

Unknown ID:

- do not create a phantom notification;
- return a permanent client error such as `404`.

Do not create a second parallel server database just for Ackline if Hermes already has authoritative notification state.

---

## WorkManager Strategy

Use WorkManager for durable deferred ACK synchronization.

Current official stable WorkManager selected for Phase 4:

`androidx.work:work-runtime:2.11.2`

Preflight must verify the resolved dependency graph before editing.

Use one unique one-time drain worker conceptually:

`AckSyncWorker`

Unique work name conceptually:

`ackline-ack-sync`

Use `ExistingWorkPolicy.APPEND_OR_REPLACE`. `KEEP` is unsafe for the local
ACK scheduling path: a worker can read its current backlog, a new local ACK
can become pending while it is running, and `KEEP` would ignore the enqueue
request after the old worker exits. Appending a successor preserves the
drain trigger without creating a worker per alert.

The worker drains Room rows with:

`ackSyncState = PENDING`

Do **not** create one permanent worker chain per alert.

Do **not** add periodic polling.

---

## Scheduling Rule

After a newly successful local ACK:

1. Room transition to `PENDING` completes.
2. Enqueue the unique ACK drain work.
3. Local UI remains `Vista` regardless of enqueue/network outcome.

There is an unavoidable cross-database boundary between Room and WorkManager. Therefore Phase 4 must also provide a recovery trigger.

Required recovery rule:

- on normal Ackline process startup, enqueue the same unique drain work;
- the worker exits successfully when there is no pending ACK.

This closes the practical crash window:

```text
Room ACK persisted
↓
process dies before WorkManager enqueue
↓
next app process start
↓
unique drain is enqueued
↓
PENDING ACK is recovered
```

This is not periodic polling.

---

## Work Constraints and Backoff

Required:

- one-time work;
- unique work;
- `NetworkType.CONNECTED`;
- exponential backoff;
- a non-aggressive initial backoff such as 30 seconds.

Important:

Tailscale being off may still satisfy Android's generic `CONNECTED` constraint.

That is expected.

The worker attempts the private ACK endpoint, receives a connection/network failure, and returns retryable work.

Do not attempt to model Tailscale as a custom permanent socket or foreground service.

---

## Drain Semantics

The worker must read the durable backlog from Room.

For each pending ACK:

1. send `notification_id` + original local `acknowledged_at`;
2. classify the result;
3. on remote success:
   - set `ackSyncState = SYNCED`;
   - set `ackSyncedAtEpochMillis`;
   - clear `lastAckError`;
4. on transient failure:
   - leave local alert `Vista`;
   - leave sync state retryable;
5. on permanent failure:
   - set `ackSyncState = ERROR`;
   - store sanitized error category;
   - continue safely without a tight retry loop.

A duplicate network request must be safe because the server contract is idempotent.

The worker must never send title/message merely to acknowledge an alert.

---

## Worker Result Rule

The exact implementation is a preflight decision, but the behavior must satisfy:

- if every pending ACK is synchronized or permanently classified → worker can finish successfully;
- if one or more retryable failures remain → worker returns retry;
- a permanent error for one row must not permanently block unrelated ACK rows;
- an exception must not silently mark an ACK `SYNCED`.

---

## Android Networking

Prefer the smallest maintainable HTTPS client.

Preflight must compare:

1. platform `HttpsURLConnection`, or
2. one focused HTTP dependency if it materially improves correctness/testing.

Do not add Retrofit just for one endpoint.

Do not add a generic API layer.

Required client behavior:

- explicit connect timeout;
- explicit read timeout;
- POST with no request body;
- `X-Ack-Token` header only;
- no secret/response-body logging;
- strict status classification;
- close streams/connections correctly.

---

## Endpoint Configuration

Do not hardcode user-specific private Tailscale hostnames into source unless explicitly approved.

Preferred dev configuration:

- local untracked build configuration (`local.properties`) or another repo-safe mechanism;
- produce one app-readable ACK base URL;
- never commit private credentials.

If the local ACK base URL is absent or blank, the sync runner treats the
deployment as `NotConfigured`: it makes no HTTP request, leaves the row
`PENDING`, preserves its local acknowledgment timestamp, records no error, and
does not request an immediate retry solely because configuration is absent. A
later correctly configured build recovers pending rows through the normal
startup drain. This is distinct from a missing per-alert `ackToken`, which is a
terminal `ERROR/missing_ack_token` condition.

Preflight must inspect the existing Gradle/property pattern before choosing the exact implementation.

If app-layer ACK authentication is already required by the Hermes environment, preflight must define secure provisioning before implementation.

Do not invent a new token scheme without evidence.

---

## Application Wiring

Keep manual wiring.

Expected Phase 4 concepts:

- `AckRemoteClient`
- narrow production HTTP implementation
- `AckSyncRunner` or equally narrow ACK-specific orchestration
- `AckSyncScheduler`
- `AckSyncWorker`

`AcklineApplication` may own the non-Worker collaborators.

The Worker may resolve the app-owned dependencies from `applicationContext as AcklineApplication`.

Do not add:

- Hilt;
- Koin;
- generic service locator;
- Retrofit framework stack unless explicitly justified;
- multi-module architecture.

---

## UI Scope

Default Phase 4 UI behavior remains the accepted Phase 3 product:

- local `Vista` is immediate;
- Inbox remains uncluttered;
- normal pending/synced transport details stay internal.

Do not turn Inbox or Setup into an operations dashboard.

If preflight identifies a product-critical need to expose permanent `ERROR`, propose the smallest possible Detail/Setup affordance and require approval before implementing it.

Otherwise Phase 4 has no visual redesign.

---

## Phase 4 Tests

Mandatory Android automated coverage:

### Room v2 → v3 migration

Prove:

- v2 row survives;
- `ackSyncState` survives;
- new nullable fields exist;
- pending ACK remains pending;
- no destructive migration.

### DAO/repository

Prove:

- query returns pending ACK metadata needed by worker;
- remote success → `SYNCED` + sync timestamp + cleared error;
- permanent failure → `ERROR` + sanitized error;
- local `acknowledgedAt` never changes during remote synchronization;
- `Vista` remains `Vista` on remote failure.

### Sync orchestration

Using a fake ACK client, prove:

- success drains pending;
- already-remote-ack success is treated as success;
- transient error remains retryable;
- permanent error does not request tight retry;
- one failed row does not corrupt another row.

### Worker scheduling

At minimum verify:

- unique work;
- network constraint;
- backoff policy;
- startup recovery enqueue.

Use WorkManager test tooling only if it gives meaningful deterministic coverage without architecture inflation.

Hermes-owned server tests cover the already-existing contract:

- valid first ACK with `X-Ack-Token` → `200`;
- repeated ACK → `200` without duplicate state mutation;
- unknown ID → `404`;
- invalid/missing token or Tailscale identity → `403`.

Ackline-owned sender validation covers optional token inclusion without
printing or logging the token.

---

## Phase 4 Physical QA

Use fake/non-sensitive alerts only.

Required:

### Online ACK

Tailscale + Mac endpoint reachable:

- receive alert;
- tap explicit `Visto`;
- UI becomes `Vista` immediately;
- Hermes server state becomes acknowledged;
- local sync state eventually becomes `SYNCED`.

### Tailscale unavailable

- make ACK path unreachable;
- tap `Visto`;
- local state becomes/stays `Vista`;
- app remains usable;
- remote sync remains pending/retryable;
- no crash or spinner waiting for server.

### Recovery

- restore Tailscale/Mac endpoint;
- without re-acknowledging the alert, pending ACK eventually synchronizes.

### Process restart while pending

- create pending remote ACK;
- terminate/restart app process;
- local `Vista` remains;
- startup recovery enqueues drain;
- remote sync eventually succeeds when endpoint is reachable.

### Duplicate remote ACK

- cause/replay same ACK more than once;
- Hermes state remains correct;
- local state remains one `Vista`;
- no duplicate record/timestamp corruption.

### Permanent client error

- controlled fake endpoint response;
- no aggressive infinite retry loop;
- local `Vista` remains valid.

---

## Explicit Out of Scope

Phase 4 does **not** implement:

- FCM sender integration into the real Hermes outbox;
- replacement/removal of ntfy;
- E2EE;
- reconciliation / `GET /notifications/pending`;
- `onDeletedMessages()` recovery;
- periodic background polling;
- multi-device ACK fanout;
- accounts/login;
- public backend;
- Hermes Gmail/Calendar/Tasks logic;
- Android Personal Admin logic;
- search;
- analytics;
- notification UI redesign;
- Tailscale battery hacks;
- foreground sockets/services.

Phase 6 still owns real Hermes outbound FCM sender/outbox integration.

Phase 7 still owns reconciliation.

---

## Dependencies

Expected new runtime dependency:

- WorkManager stable runtime (`2.11.2`).

Possible test-only dependency:

- WorkManager testing artifact if justified.

No new runtime dependency should be added merely for JSON or DI without demonstrated need.

Keep existing Room/Lifecycle/Firebase/Compose versions unless a concrete blocker requires a change.

---

## Privacy Gate

Phase 5 E2EE has not happened yet.

Therefore all Phase 4 end-to-end payloads remain fake/non-sensitive.

ACK requests contain only:

- notification ID in the URL path;
- the per-notification `X-Ack-Token` header.

The Room sync projection also retains the original local acknowledgment
timestamp for durable local state, but the current Hermes endpoint does not
send that timestamp over HTTP. `ackToken` is never mapped into the `Alert`
UI/domain model or exposed to Compose.

Phase 5 E2EE must protect the eventual production inner payload, including
ACK credential material, before real sensitive Hermes traffic is enabled.

Do not send title/message back to Hermes as part of ACK.

---

## Acceptance Gate

Phase 4 passes only when all are true:

```text
local Visto still immediate                 PASS
Room v2 → v3 migration                      PASS
ACK online                                  PASS
ACK idempotent server-side                  PASS
Tailscale/Mac unavailable preserves Vista   PASS
pending ACK survives restart                PASS
unique WorkManager drain retries            PASS
restore path eventually syncs               PASS
permanent error avoids tight loop           PASS
no title/message in ACK request              PASS
no secret leakage                            PASS
Phase 1–3 core behavior not broken           PASS
final GitHub review                          PASS
```

---

## Suggested Commit

```text
feat: add durable remote acknowledgment sync
```

User owns commit and push.
