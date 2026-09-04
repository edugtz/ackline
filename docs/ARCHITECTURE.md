# Ackline Architecture — FCM Transport with Phase 7 Planned Recovery

## 1. Principle

Ackline remains a small Android notification inbox.

Hermes remains the Personal Admin brain and server-side notification source of truth.

Phase 6 changed **only the production outbound transport from Hermes**: the
outbox now delivers through encrypted FCM (`ACTIVE_TRANSPORT = "fcm"`), with
ntfy retained as rollback.

Phase 7 (Redesign V2, planned, not yet implemented) adds Hermes bounded
FCM redelivery as the primary recovery safety net, plus event-driven
Android reconciliation, so that FCM remains the realtime transport without
one push attempt being the only way to recover a pending alert. Periodic
WorkManager is no longer a recovery dependency. See §17
"Phase 7 Planned Architecture (Redesign V2)".

```text
Hermes decides
Hermes queues
Hermes encrypts
FCM transports
Ackline receives
Ackline persists
User acknowledges
Hermes records ACK
            ↕ Phase 7 planned: Hermes redelivery + event-driven recovery
```

---

## 2. Boundaries

### Hermes owns

- Gmail/Calendar/Tasks ingestion;
- detection/business rules;
- LLM judgment;
- notification decision;
- persistent outbox;
- `notification_id`;
- `ack_token`;
- `level/title/message/created_at`;
- production FCM sending;
- server acknowledgment state.

### FCM owns only

- transport routing;
- provider acceptance;
- device wake/delivery behavior.

FCM is not:
- source of truth;
- inbox DB;
- ACK DB;
- reconciliation DB.

### Ackline owns

- FCM receive boundary;
- authenticated E2EE decrypt;
- Room persistence;
- native notification presentation;
- explicit local `Visto`;
- durable remote ACK scheduling.

Ackline does not own Personal Admin decision logic.

---

## 3. Production Notification Flow

```text
Hermes event/change
        │
        ▼
Personal Admin judgment
        │
        ▼
notification_state.py queue
        │
        ▼
Hermes SQLite notifications row
        │
        │ stable:
        │ notification_id
        │ ack_token
        │ created_at
        │ level/title/message
        ▼
Phase 6 dispatcher
        │
        ▼
FCM sender
        │
        ├── read configured FID
        ├── read AES key
        ├── load Firebase credential
        ├── compact inner JSON
        ├── AES-256-GCM
        └── FCM send
        │
        ▼
FCM accepted?
   ┌────┴────┐
   no        yes
   │          │
   ▼          ▼
retain      record
unsent      sent_at
state
   │
retry later
```

Then:

```text
FCM
→ Ackline FirebaseMessagingService
→ exact encrypted envelope validation
→ AndroidKeyStore key
→ AES-GCM auth/decrypt
→ strict UTF-8/string JSON
→ existing parseAcklinePayload
→ Room INSERT IGNORE
→ native notification
```

---

## 4. ACK Flow Is Independent

Phase 6 does not change ACK topology:

```text
User presses Visto
→ Room local ACK immediately
→ WorkManager
→ HTTPS/Tailscale
→ Hermes ack_server.py
→ Hermes acknowledged_at
→ Ackline SYNCED
```

Important:

```text
FCM delivery does NOT use Tailscale
ACK does
```

Tailscale outage must not block push reception.

---

## 5. Durable Outbox Semantics

Hermes SQLite remains the authoritative outbox.

Preferred transport boundary:

```text
notification row
→ one transport attempt
→ sanitized typed result
```

Dispatcher owns DB persistence.

Sender owns one FCM attempt.

Final cutover dispatcher eligibility:

```text
sent_at IS NULL
AND canceled_at IS NULL
AND acknowledged_at IS NULL
AND associated run.status = 'committed'
```

`acknowledged_at IS NULL` was added during cutover QA; already acknowledged
rows are terminal for transport delivery.

Conceptual result categories:

```text
Accepted
TransientFailure(category)
PermanentFailure(category)
```

Exact Python shape is a preflight decision. Do not create an unnecessary transport framework.

---

## 6. Acceptance vs Delivery

FCM provider acceptance and end-device delivery are different.

Phase 6 may record:

```text
sent_at = provider accepted
```

only if that matches the existing Hermes contract after preflight verification.

It must never claim:

```text
device displayed alert
```

Phase 7 reconciliation exists because realtime push can rarely be missed.

Phase 7 Redesign V2 keeps this distinction explicit:

```text
recovery eligibility != dispatch eligibility
redelivery eligibility != dispatch eligibility
```

Dispatch eligibility (Phase 6, unchanged) requires `sent_at IS NULL`.
Recovery eligibility (Phase 7 planned, §17) does **not** filter `sent_at`:
`sent_at` only proves FCM/provider acceptance, never that Ackline persisted
the alert. A row with `sent_at` PRESENT and `acknowledged_at` NULL remains
recoverable.

Hermes redelivery eligibility (Phase 7 Redesign V2, §17) requires
`sent_at IS NOT NULL` because it specifically targets notifications that
were accepted by FCM but never acknowledged by the device.

---

## 7. At-Least-Once Model

Duplicate transport attempts are intentionally safe.

Example:

```text
attempt A
→ FCM accepts
→ Hermes fails before sent_at commit

attempt B
→ same notification_id
→ fresh nonce
→ FCM accepts again
```

Ackline's `notification_id` dedupe produces one logical row and no duplicate notification repost.

Do not pursue exactly-once transport.

---

## 8. E2EE Boundary

Private inner payload:

```text
protocol
notification_id
level
title
message
created_at
ack_token
```

is encrypted before leaving the Mac.

FCM-visible data:

```text
v
kid
nonce
ciphertext
```

only.

Frozen Phase 5 protocol:

```text
v = 1
kid = ackline-main
AES-256-GCM
nonce = 12 random bytes
tag = 16 bytes
AAD = ackline-e2ee|v=1|kid=ackline-main
max inner = 2500 UTF-8 bytes
```

No Phase 6 protocol change is expected.

---

## 9. Key Ownership

Same symmetric key:

```text
Mac:
~/.hermes/secrets/hermes-notify.key

Android:
AndroidKeyStore alias
ackline.payload.ackline-main
```

Mac raw key stays outside repo.

Android raw staging is already deleted after import.

Phase 6 only reads the Mac key for encryption.

No key rotation in this phase.

---

## 10. Firebase Credential Boundary

Firebase service-account credential is Mac-side only.

It must never:
- enter Android;
- enter FCM payload;
- enter git;
- enter prompts;
- appear in diagnostics.

Unattended Hermes must load it without interactive-shell dependence.

The production path is
`~/.hermes/secrets/firebase-service-account.json`, loaded explicitly by the
Hermes-owned sender.

---

## 11. FID Boundary

FID identifies the current Ackline installation target.

Properties:
- changes after reinstall;
- stale FID may become unregistered;
- not a cryptographic secret;
- not hardcoded;
- not routinely logged in full.

Phase 6 uses `~/.hermes/secrets/ackline-fid` as its one durable local
configuration source. The implementation does not populate the file.

Phase 7 planned architecture improves re-pair/recovery behavior (§17,
FID/re-pair): Ackline persists the last observed FID, sets
`rePairRequired` on change, and the operator re-provisions manually by
copying the current FID into `~/.hermes/secrets/ackline-fid`. No automatic
provisioning and no server-side FID registry.

---

## 12. Transport State After Cutover

FCM is the active production transport:

```text
persistent outbox
       │
       ▼
explicit transport selector
   ┌───┴───┐
   │       │
  FCM     ntfy
active   rollback
```

`notification_state.py` has `ACTIVE_TRANSPORT = "fcm"`.

ntfy remains implemented and available as rollback only. No production
dual-send. No generic plugin framework.

Removal of ntfy belongs to the Phase 8 real-world replacement gate.

---

## 13. Failure Domains

### Hermes process failure
Outbox row persists.

### Network / FCM transient failure
Row remains unsent/retryable.

### FCM accepts, DB commit fails
Duplicate retry possible; safe through Ackline dedupe.

### Invalid/stale FID
Actionable target failure; no silent infinite loop.

### Missing E2EE key
Fail before FCM; row remains unsent.

### Bad Firebase credential
Operational failure; no false sent state.

### Ackline missing key
Existing Phase 5 fail-closed behavior.

### Tailscale unavailable
Push still works; ACK waits/retries.

---

## 14. Scheduler Constraint

The actual Hermes scheduler is part of the production architecture. The
existing on-demand Hermes AI/gateway invocation remains the scheduler; Phase 6
adds no cron, launchd, polling, or periodic retry process.

Phase 6 must use its real:
- interpreter;
- environment;
- working directory;
- permissions;
- cadence.

A sender that works only from an interactive terminal is not production-ready.

---

## 15. Minimalism Rules

Do not add:
- message broker;
- Redis;
- queue SaaS;
- Firebase database;
- public API;
- extra Android service;
- foreground service;
- transport abstraction framework;
- multi-device registry;
- key server.

The existing SQLite outbox is already the queue.

---

## 16. Phase 6 Completion State

Phase 6 is COMPLETE — CLOSED.

The validated production realtime route is:

```text
Hermes queue
→ encrypted FCM
→ Ackline
```

`ACTIVE_TRANSPORT = "fcm"`, cut over in production (merge
`5b5777a827e097a98687bc6fae0060a2e6fcebb3`), with the canary
`8304672d700c4056b5d456eae49b6060` retained as historical evidence.

ntfy remains available as rollback until the Phase 8 real-world replacement
gate.

Phase 7 — Recovery and Reconciliation (see `docs/MVP_PHASES.md`) adds
Hermes bounded redelivery and event-driven Android reconciliation without
changing the realtime path. Phase 7 is in PLANNING COMPLETE — REDESIGN V2
state; its planned architecture is documented in §17 with "planned" labels
until implementation lands.

---

## 17. Phase 7 Planned Architecture (Redesign V2) — Recovery and Reconciliation

> Everything in this section is **planned architecture**, not deployed
> code. Nothing here exists in production until its change unit lands and
> passes review/QA.

> **Redesign V2 note:** Phase 7 Change D uncovered a design failure — the
> periodic WorkManager safety net was a dependency that should not exist.
> This is a planning conclusion, not a product or runtime failure.
> Periodic WorkManager is retired. Hermes bounded redelivery is the primary
> recovery safety net.

### 17.1 Realtime path stays FCM

FCM remains the realtime transport. The recovery paths are Hermes bounded
redelivery and event-driven Android reconciliation — never periodic
WorkManager polling and never a replacement for push.

### 17.2 Hermes bounded redelivery (NEW — Redesign V2)

Hermes is responsible for redelivering recently accepted but
unacknowledged notifications via FCM. This is the **primary** recovery
safety net.

```text
Hermes redelivery query
→ sent_at IS NOT NULL
→ acknowledged_at IS NULL
→ within 6 hours of first sent_at
→ at least 2 hours since last_attempt_at
→ same notification_id
→ NORMAL FCM priority
→ FCM send
→ Ackline Room INSERT IGNORE
→ one notification on INSERTED
```

Key properties:
- `sent_at` is preserved as **first FCM acceptance** (never overwritten).
- `send_attempts` and `last_attempt_at` are reused for tracking.
- Redelivery copies use **NORMAL** FCM priority regardless of original
  level.
- Same `notification_id` — Room `INSERT IGNORE` absorbs duplicates.
- No Hermes DB migration — uses existing columns only.
- No delivery-receipt protocol — Hermes does not require device
  confirmation of receipt.
- Bounded by the 6-hour window — no infinite redelivery loop.

### 17.3 Event-driven Android recovery (planned)

```text
Hermes ack_server
→ GET /notifications/pending
→ HTTPS/Tailscale
→ RecoveryWorker
→ canonical AlertIngestion
→ Room INSERT IGNORE
→ notification on INSERTED
```

Triggers (event-driven only):

```text
A. onDeletedMessages()        → unique one-time recovery
B. AcklineApplication startup → unique one-time recovery
C. FID registration/change    → unique one-time recovery
```

- One-way reconcile: Hermes pending → Ackline. Local rows are never
  deleted merely because they are absent server-side.
- Duplicate `notificationId` → no overwrite, no ACK-state regression, no
  notification repost.
- ACK path remains unchanged: `Visto` → local ACK → WorkManager →
  HTTPS/Tailscale → ack_server → Hermes `acknowledged_at`.
- After a successful recovery GET, the existing AckSyncScheduler is
  enqueued once to drain the local ACK backlog. Recovery never ACKs
  manually.
- One-time work uses `ExistingWorkPolicy.KEEP`: a new trigger must not
  cancel an already queued/retrying recovery or reset its backoff.
- No periodic WorkManager. No foreground service, no AlarmManager, no
  exact alarms, no sockets, no MQTT.

### 17.4 Recovery contract (planned)

- `GET /notifications/pending` on the existing Hermes `ack_server.py`;
- same `Tailscale-User-Login` trusted identity boundary as ACK;
- read-only, fail-closed, `Cache-Control: no-store`, no server state
  mutation, no Firebase Auth, no API key, no account/device registry;
- recovery eligibility:

```text
canceled_at IS NULL
AND acknowledged_at IS NULL
AND associated run.status = 'committed'
```

- `sent_at` intentionally not filtered (`recovery eligibility !=
  dispatch eligibility`); deterministic ordering
  `created_at ASC, notification_id ASC`;
- max 200 items with cap+1 detection; `> 200` → `HTTP 409
  too_many_pending` (degraded/operator-action state, not auto-retried
  forever); no pagination in Phase 7;
- payloads reuse the Phase 5/6 E2EE envelope (`v`/`kid`/`nonce`/
  `ciphertext`, built by `fcm_sender.build_envelope`); inner payload
  unchanged; no plaintext protocol; no new crypto.

### 17.5 Canonical ingestion (planned)

A single `AlertIngestion` path shared by `FirebaseMessagingService` and
`RecoveryWorker`: kid check → decrypt → inner decode → payload parse →
`repository.insertIncoming` → native notification only on INSERTED.
Mechanical reuse of the proven Phase 5/6 receive path.

### 17.6 Failure taxonomy (planned)

- Transient (network, DNS, TLS, timeout, IOException, HTTP 408/429/5xx):
  `Result.retry()` for one-time workers, exponential WorkManager backoff.
- Permanent/configuration (blank/malformed base URL, 403, 404, contract
  4xx, 409): no retry loop, sanitized diagnostic, Room untouched.
- Per-item decrypt/validation failure: skip item, continue batch, no
  crash, no DB regression.

### 17.7 FID / re-pair (planned)

- Ackline persists the last observed FID. First observation → baseline,
  `rePairRequired = false`. Later different FID → store new FID,
  `rePairRequired = true`, enqueue recovery.
- Setup shows an actionable re-pair warning. `rePairRequired` survives
  process restarts and clears only through an explicit Setup action
  ("Mark as updated") after the operator updates `~/.hermes/secrets/
  ackline-fid` with the current FID.
- No device registry, no server write for FID, no automatic provisioning.

### 17.8 Data rules (planned)

- No Room migration (schema stays v3) unless implementation uncovers a
  concrete correctness requirement.
- No `recovered_at`, server revisions, sync version, or tombstones.
- No Hermes DB migration; recovery and redelivery derive from existing
  columns.

### 17.9 Acceptance gates (Redesign V2)

Primary gate — Hermes bounded redelivery:

```text
Hermes sent/unacknowledged
→ bounded FCM redelivery
→ same notification_id
→ Ackline Room INSERT once
→ one native notification
→ duplicate harmless
→ Visto
→ remote ACK
```

Secondary gate — event-driven recovery:

```text
Hermes creates alert → FCM accepted → device never persists
→ later startup/onDeletedMessages/FID-change
→ one-time recovery executes WITHOUT manual app open
→ missing row inserted → one notification shown → Visto
→ ACK reaches Hermes
```

**No acceptance gate requires waiting for a periodic WorkManager cycle.**
