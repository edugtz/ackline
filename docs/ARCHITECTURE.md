# Ackline — Architecture

## 1. Architecture Goal

Use simple, maintainable architecture.

Reliability, explicit state transitions, privacy, and low maintenance matter more than framework sophistication.

Ackline remains a single-user, single-app-module Android client for Hermes-selected alerts.

Do not move Hermes Personal Admin responsibilities into Android.

---

## 2. Source-of-Truth Rules

1. `docs/CURRENT_PHASE.md` is the active implementation scope.
2. Actual source is authoritative for exact runtime behavior.
3. Hermes notification SQLite/state layer is authoritative for server notification state.
4. Room is authoritative for device Inbox, local acknowledgment, and local ACK-sync state.
5. FCM is inbound transport only.
6. Android tray is presentation only.
7. WorkManager is deferred execution, not authoritative state.
8. Tailscale is the private ACK/recovery network path; FCM push must not depend on it.
9. Only explicit `Visto` actions create local acknowledgment.
10. Remote ACK failure never reverses local `Vista`.

---

## 3. Current High-Level Runtime

After Phase 4:

```text
Hermes selected notification
        │
        │ later Phase 6 outbound integration
        ▼
       FCM
        │
        ▼
FirebaseMessagingService
        │
        ▼
IncomingAlertEnvelope
        │
        ▼
AlertRepository
        │
        ▼
       Room
   ┌────┴──────────────────┐
   │                       │
   ▼                       ▼
Inbox / Detail      native notification
   │                       │
   │ explicit Visto        │ explicit Visto
   └───────────┬───────────┘
               ▼
 LocalAcknowledgmentManager
               │
       atomic local Room ACK
               │
      ackSyncState=PENDING
               │
       cancel tray item
               │
       enqueue unique work
               ▼
        AckSyncWorker
               │
        AckSyncRunner
               │
        AckRemoteClient
               │
        HTTPS / Tailscale
               ▼
       Mac ACK endpoint
               │
 existing Hermes notification-state layer
               │
               ▼
         Hermes SQLite
               │
         success response
               ▼
 Room ackSyncState=SYNCED
```

---

## 4. Proven Inbound Boundary

FCM data messages remain the inbound transport.

Firebase types stop at:

`AcklineMessagingService`

Below that boundary the app uses app-owned models.

Current plaintext development protocol remains v1 until E2EE:

```json
{
  "protocol": "1",
  "notification_id": "test-123",
  "level": "important",
  "title": "Test alert",
  "message": "Non-sensitive development payload",
  "created_at": "2026-08-30T18:00:00Z"
}
```

Phase 4 does not change this inbound protocol.

---

## 5. Local Alert Model

Current core alert data:

```text
notificationId
protocolVersion
level
title
message
createdAt
receivedAt
acknowledgedAt
ackSyncState
ackSyncedAt
lastAckError
ackToken (storage-only; not part of Alert/UI)
```

Phase 4 adds the two sync metadata fields plus the storage-only ACK token.

User-visible read state still comes from:

```text
acknowledgedAt == null     → Pendiente
acknowledgedAt != null     → Vista
```

Remote state must never redefine whether the alert is locally viewed.

---

## 6. ACK Sync State

Phase 4:

```text
NONE
PENDING
SYNCED
ERROR
```

State machine:

```text
new alert
  ↓
NONE

explicit local Visto
  ↓
PENDING

remote idempotent success
  ↓
SYNCED
```

Permanent remote failure:

```text
PENDING
  ↓
ERROR
```

Transient failure:

```text
PENDING
  ↓
PENDING
```

No remote outcome returns the alert to `Pendiente`.

---

## 7. Room Database Evolution

### v1 — Phase 2

Alert persistence.

### v2 — Phase 3

Adds:

```text
ackSyncState TEXT NOT NULL DEFAULT 'none'
```

### v3 — Phase 4

Adds nullable:

```text
ackSyncedAtEpochMillis INTEGER
lastAckError TEXT
ackToken TEXT
```

Migration must be non-destructive.

Keep all schema exports.

---

## 8. Local ACK Transaction

Phase 3 transaction remains authoritative:

```sql
UPDATE alerts
SET acknowledgedAtEpochMillis = :timestamp,
    ackSyncState = 'pending'
WHERE notificationId = :notificationId
  AND acknowledgedAtEpochMillis IS NULL
```

First local ACK wins.

Remote synchronization must never rewrite `acknowledgedAtEpochMillis`.

---

## 9. LocalAcknowledgmentManager

Responsibilities:

1. perform atomic local ACK;
2. preserve local idempotency;
3. cancel matching tray notification;
4. request remote ACK scheduling after local persistence.

It does not:

- perform HTTP;
- block on Tailscale;
- wait for Hermes;
- implement retry loops;
- perform encryption.

If scheduling fails, local ACK remains durable.

---

## 10. WorkManager Boundary

Use WorkManager for durable deferred ACK execution.

Planning baseline:

`androidx.work:work-runtime:2.11.2`

One unique one-time drain:

```text
unique name: ackline-ack-sync
policy: APPEND_OR_REPLACE
constraint: CONNECTED
backoff: EXPONENTIAL
```

The actual policy is `ExistingWorkPolicy.APPEND_OR_REPLACE`. `KEEP` can
strand a new PENDING ACK when a running worker has already read its backlog;
the append policy leaves a successor in the unique chain.

No periodic worker.

No worker-per-alert permanent queue.

Room backlog is the durable queue.

---

## 11. Scheduling Recovery

Room and WorkManager use separate durable stores, so local ACK + enqueue cannot be one atomic transaction.

Recovery invariant:

```text
Ackline process start
→ enqueue same unique drain
```

If no PENDING rows exist, worker exits.

This prevents a process death between local persistence and enqueue from permanently stranding an ACK.

---

## 12. ACK Sync Runner

Use a narrow ACK-specific orchestration object if helpful for deterministic testing.

Conceptual responsibilities:

```text
load Room PENDING ACKs
→ remote acknowledge each
→ mark SYNCED / ERROR / retryable
→ return whether WorkManager must retry
```

Do not generalize it into a whole application sync engine.

Reconciliation remains Phase 7.

---

## 13. Remote ACK Client

App-owned boundary:

```text
AckRemoteClient
```

Input:

```text
notificationId
ackToken
```

Output categories:

```text
SUCCESS
TRANSIENT_FAILURE
PERMANENT_FAILURE
```

No alert title/message crosses this boundary.

HTTP implementation details stay below it.

---

## 14. Remote ACK Protocol

Actual Hermes endpoint:

```text
POST /ack/<notification_id>
X-Ack-Token: <per-notification token>
```

The request has no body. The notification ID is encoded as one URL path
segment. `Tailscale-User-Login` is required by the server path but is injected
by Tailscale Serve; Android must not set or spoof it.

Actual idempotent success:

`200 OK`

Repeated request for an already-acknowledged notification is still success.

Unknown ID does not create state.

---

## 15. Failure Classification

### Transient

Examples:

```text
connection failure
DNS/TLS/timeout
408
429
5xx
```

Local:

```text
Vista
ackSyncState=PENDING
```

Worker:

`retry`

### Permanent

Examples:

```text
400
403
404
unsupported protocol/client error
```

Local:

```text
Vista
ackSyncState=ERROR
lastAckError=sanitized category
```

Worker must not tight-loop.

---

## 16. Private Network Architecture

Preferred:

```text
Android
  ↓ HTTPS
private *.ts.net route
  ↓
Tailscale Serve
  ↓
127.0.0.1:<local ACK port>
  ↓
Mac ACK endpoint
```

Do not use Tailscale Funnel.

Do not expose the ACK endpoint publicly.

Do not bind the Python ACK service to all interfaces unless explicitly justified.

---

## 17. Tailscale Independence

Inbound:

```text
Hermes → FCM → Android
```

must work without Tailscale on the phone.

Outbound ACK:

```text
Android → Tailscale → Mac
```

may fail temporarily.

That failure is represented by durable local sync state and WorkManager retry.

This asymmetry is intentional.

---

## 18. Hermes Server Boundary

The ACK endpoint must call the existing Hermes notification-state abstraction.

Preferred:

```text
HTTP handler
→ notification_state acknowledgement function
→ SQLite transaction
```

Avoid duplicating raw SQL in the HTTP handler.

Do not create a separate Ackline server database if Hermes already has the authoritative notification row.

Do not touch Gmail/Calendar/Tasks/LLM logic.

---

## 19. Server Idempotency

Hermes ACK by `notification_id`.

First valid ACK:

- find existing notification;
- persist ACK;
- preserve intended timestamp semantics;
- success.

Repeat:

- no duplicate;
- no destructive rewrite;
- same success class.

Unknown:

- no phantom creation;
- permanent client error.

---

## 20. Server Exposure

For Phase 4 QA, manually launching the small ACK endpoint is sufficient.

Daemonization/launchd is not automatically part of Phase 4.

If Hermes already has a standard service lifecycle, reuse may be proposed during preflight.

Do not expand scope into generic server operations.

---

## 21. Android Networking

One endpoint does not justify a large API framework.

Default preference:

- `HttpsURLConnection` with explicit timeouts and cleanup.

A focused HTTP library is acceptable only if preflight demonstrates a concrete correctness or testing advantage.

No Retrofit by default.

No cleartext-network exception if private HTTPS works.

---

## 22. Endpoint Configuration

Do not commit user-specific private network configuration unnecessarily.

Preferred local build configuration:

```text
local.properties
→ Gradle
→ app-readable ACK base URL
```

No public configuration service.

No Firebase Remote Config.

No credentials in source.

---

## 23. Authentication

Tailscale-only private access may be sufficient for the initial single-user deployment.

Do not invent a bearer-token system without a requirement.

If the actual Hermes endpoint already requires app-layer auth, preflight must define secure provisioning and storage before implementation.

No secret logging.

---

## 24. UI Architecture

Accepted screens remain:

- Inbox;
- Detail;
- Setup.

Phase 4 should not redesign them.

Local product state remains:

- Pendiente;
- Vista.

Transport sync state stays internal by default.

A permanent-sync-error affordance requires explicit product justification.

---

## 25. Notification Receiver

`AcknowledgeReceiver` remains:

```text
onReceive
→ validate
→ goAsync
→ bounded background local ACK
→ finish
```

HTTP never runs in the BroadcastReceiver.

The manager may schedule WorkManager after local persistence.

---

## 26. Worker Lifecycle

Worker class name becomes persistent WorkManager data.

Choose a stable clear name such as:

`AckSyncWorker`

Do not casually rename/remove it in later releases while pending work may exist.

Worker stays bounded well below WorkManager execution limits.

---

## 27. Data Minimization

ACK sync requires only:

```text
notificationId
ackToken
```

Room also keeps `acknowledgedAt` in the pending projection so remote sync never
loses the original local decision. The current Hermes endpoint does not
receive that timestamp; it receives only the ID and token.

The worker and remote client should not load/send:

```text
title
message
level
receivedAt
```

unless a future protocol requirement explicitly justifies it.

This reduces privacy risk.

---

## 28. Logging

Allowed diagnostic categories:

```text
ACK sync started
ACK sync success
ACK sync transient failure
ACK sync permanent failure
pending ACK count
```

Avoid:

- alert text;
- FID;
- tokens;
- full request bodies;
- raw response bodies;
- service-account material;
- encryption keys;
- unbounded notification IDs.

---

## 29. E2EE Boundary

Phase 4 still uses fake/non-sensitive inbound payloads.

Phase 5 will add application-level E2EE for alert content.

ACK protocol itself remains metadata-only. Phase 5 must protect the eventual
production inner payload, including ACK credential material, before sensitive
traffic is enabled.

---

## 30. Future Boundaries

### Phase 5

Application-level E2EE.

### Phase 6

Real Hermes outbound FCM sender/outbox integration.

### Phase 7

Recovery/reconciliation for missed pending alerts.

Do not pull those responsibilities into Phase 4.

---

## 31. Dependency Policy

Expected:

```text
Room 2.8.4
Lifecycle 2.11.0
Firebase Messaging existing
Compose existing
WorkManager 2.11.2
```

Possible test-only:

```text
room-testing existing
work-testing if justified
```

Avoid new DI/navigation/network frameworks without a concrete need.

---

## 32. Testing Architecture

Separate deterministic business behavior from Android scheduling where useful.

Test:

- Room migration;
- DAO transitions;
- sync result classification;
- remote idempotency;
- WorkManager request configuration;
- server contract;
- physical eventual retry.

Do not rely only on sleeps/log observation for core state transitions.

---

## 33. Physical Upgrade Rule

Instrumented tests may reinstall/alter app state.

Do not use the same physical Phase 3 dataset for `connectedDebugAndroidTest` and then assume it still proves migration.

Migration QA sequence must be deliberate:

```text
real v2 data
→ install Phase 4 APK over it
→ open
→ verify preservation
```

---

## 34. Product Reliability Principle

The system is intentionally eventually consistent:

```text
Device: Vista immediately
Server: acknowledged later
```

That is not a temporary workaround.

It is the required architecture because the Mac/Tailscale path is less available than the phone's local Room state.

---

## 35. Architecture Acceptance

Phase 4 architecture is accepted when:

- local ACK remains independent;
- Room contains durable pending remote work;
- WorkManager only drains Room;
- one unique worker handles backlog;
- no periodic poller exists;
- private HTTPS/Tailscale path is used;
- server transition is idempotent;
- retry classification is bounded;
- no private content is added to ACK payload;
- no generic sync/backend architecture is introduced.
