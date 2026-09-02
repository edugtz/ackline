# Ackline Architecture — Phase 6 Completed Transport View

## 1. Principle

Ackline remains a small Android notification inbox.

Hermes remains the Personal Admin brain and server-side notification source of truth.

Phase 6 changed **only the production outbound transport from Hermes**: the
outbox now delivers through encrypted FCM (`ACTIVE_TRANSPORT = "fcm"`), with
ntfy retained as rollback.

```text
Hermes decides
Hermes queues
Hermes encrypts
FCM transports
Ackline receives
Ackline persists
User acknowledges
Hermes records ACK
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

Phase 7 can later improve re-pair/recovery behavior.

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
recovery/reconciliation without changing the realtime path.
