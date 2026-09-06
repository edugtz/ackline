# Ackline Architecture — FCM Transport with Phase 7 Recovery (Implemented)

## 1. Principle

Ackline remains a small Android notification inbox.

Hermes remains the Personal Admin brain and server-side notification source of truth.

Phase 6 changed **only the production outbound transport from Hermes**: the
outbox now delivers through encrypted FCM (`ACTIVE_TRANSPORT = "fcm"`);
ntfy is legacy/disabled state pending later cleanup/removal.

Phase 7 (implemented, merged, and PASSED final integration QA) added Hermes
bounded FCM redelivery as the primary recent-loss safety net, plus
event-driven Android reconciliation, so that FCM remains the realtime
transport without one push attempt being the only way to recover a pending
alert. Periodic WorkManager is no longer a recovery dependency. See §17
"Phase 7 Implemented Recovery Architecture".

```text
Hermes decides
Hermes queues
Hermes encrypts
FCM transports            ← realtime, with bounded redelivery for unacknowledged
Ackline receives
Ackline persists
User acknowledges
Hermes records ACK        ← explicit tailnet VPN Network HTTPS
            ↕ event-driven GET /notifications/pending recovery
              (startup / onDeletedMessages / FID registration-change)
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
- production FCM sending and bounded redelivery;
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
- durable remote ACK scheduling;
- event-driven recovery (startup, `onDeletedMessages`, FID change).

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
→ canonical AlertIngestion
→ Room INSERT IGNORE
→ native notification only on INSERTED
```

### Bounded FCM redelivery (Phase 7, primary recent-loss safety net)

```text
Hermes redelivery query
→ sent_at IS NOT NULL
→ acknowledged_at IS NULL
→ canceled_at IS NULL
→ committed run
→ within 6 hours of first sent_at
→ at least 2 hours since last_attempt_at
→ same notification_id
→ NORMAL FCM priority
→ FCM send
→ Ackline Room INSERT IGNORE (duplicate harmless)
```

Properties (validated in production):

- `sent_at` is preserved as **first FCM acceptance** — never overwritten;
- `send_attempts` and `last_attempt_at` are reused for tracking;
- redelivery copies use **NORMAL** priority regardless of original level;
- bounded by the 6-hour window — no infinite redelivery loop;
- no Hermes DB migration; no delivery-receipt protocol.

Scheduling nuance: the existing Hermes scheduler cadence is q120m, but
eligibility requires `last_attempt_at >= 2h`; a scheduler cycle may land
slightly before the strict 2h boundary and skip until the next cycle. There
is **no exact +2h redelivery SLA** and no guarantee of exactly three copies.

---

## 4. ACK Flow Is Independent — Explicit Tailnet VPN Binding

```text
User presses Visto
→ Room local ACK immediately
→ durable WorkManager (AckSyncRunner)
→ HttpsAckRemoteClient
→ ConnectivityManager active VPN Network
→ vpnNetwork.openConnection(url)
→ Tailscale Serve
→ Hermes ack_server.py
→ Hermes acknowledged_at
→ Ackline SYNCED
```

Important:

```text
FCM delivery does NOT use Tailscale
ACK does — over the explicit VPN Network
```

All tailnet HTTPS (ACK and recovery GET) is bound explicitly to the active
VPN `Network` via `TailnetHttpsConnectionFactory`:

- no process-wide binding;
- no hardcoded Tailscale IP;
- no TLS weakening;
- no fallback to public/default network for tailnet endpoints.

This was required because the default/implicit app network path could not
reach the Mac tailnet while the device shell path could
(`APP_UID_TAILNET_ROUTING_FAILURE_PROVEN`). Both paths were physically
validated: `EXPLICIT_VPN_ACK_PROVEN = YES` and
`EXPLICIT_VPN_RECOVERY_PROVEN = YES`.

A Tailscale outage must not block push reception; it only delays ACK/recovery.

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

Dispatch eligibility (unchanged):

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

---

## 6. Acceptance vs Delivery

FCM provider acceptance and end-device delivery are different.

```text
sent_at = provider accepted
```

It is never a claim of:

```text
device displayed alert
```

Phase 7 reconciliation exists because realtime push can rarely be missed.

Phase 7 keeps this distinction explicit:

```text
recovery eligibility != dispatch eligibility
redelivery eligibility != dispatch eligibility
```

- Dispatch eligibility (Phase 6, unchanged) requires `sent_at IS NULL`.
- Recovery eligibility (Phase 7) does **not** filter `sent_at`:
  `sent_at` only proves FCM/provider acceptance, never that Ackline
  persisted the alert. A row with `sent_at` PRESENT and `acknowledged_at`
  NULL remains recoverable.
- Redelivery eligibility (Phase 7) requires `sent_at IS NOT NULL` because
  it specifically targets notifications that were accepted by FCM but
  never acknowledged by the device.

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

Ackline's `notification_id` dedupe produces one logical row and no duplicate
notification repost.

Do not pursue exactly-once transport. Do not claim exact-once FCM.

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

No Phase 7 protocol change. Recovery payloads reuse the same envelope via
`fcm_sender.build_envelope(row)`.

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

Hermes uses `~/.hermes/secrets/ackline-fid` as its one durable local
configuration source. Hermes never writes the file; the operator
provisions it manually.

Phase 7 (implemented): Ackline persists the last observed FID, detects
FID registration/change, sets `rePairRequired`, enqueues event-driven
recovery, and surfaces an actionable re-pair warning in Setup.
`rePairRequired` survives process restart and clears only through an
explicit Setup action ("Mark as updated") after the operator updates
`~/.hermes/secrets/ackline-fid` with the current FID.

No automatic provisioning and no server-side FID registry.

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
active   disabled
```

`notification_state.py` has `ACTIVE_TRANSPORT = "fcm"`.

ntfy is legacy/disabled — NOT an approved fallback or rollback path. Phase 8
must test Ackline/FCM alone and must not silently switch to ntfy. Any
remaining ntfy code/configuration is pending later cleanup/removal.

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
Push still works; ACK waits/retries; event-driven recovery skips and
retries via WorkManager backoff.

### Tailnet unreachable from default app network
Explicit VPN Network binding (Phase 7 Change G1) routes tailnet HTTPS
through the active VPN; no fallback to the public/default network for
tailnet endpoints.

---

## 14. Scheduler Constraint

The actual Hermes scheduler is part of the production architecture: the
existing Hermes built-in cron/gateway scheduler, job `86c14bbbe300`
("Personal Admin"), cadence **q120m**. It was paused during Phase 7 QA and
is **re-enabled**. No duplicate scheduler/job was created; cadence and
configured path unchanged.

Important repository distinction: the Hermes Agent scheduler/runtime
(HEAD `96ed0e71ea`) and the Hermes Personal Admin checkout
(`fab085d7400…`) are **DIFFERENT repositories**. Do not claim that
`fab085d` must exist in hermes-agent history.

Hermes must use its real:
- interpreter;
- environment;
- working directory;
- permissions;
- cadence.

A sender that works only from an interactive terminal is not
production-ready.

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

## 16. Phase 6 Completion State (HISTORY — remains CLOSED)

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

ntfy is legacy/disabled state — Phase 8 validates Ackline/FCM alone with no
ntfy fallback.

Phase 7 — Recovery and Reconciliation (see `docs/MVP_PHASES.md`) is
**implemented and merged**; its final architecture is documented in §17.

---

## 17. Phase 7 Implemented Recovery Architecture

> This section documents the **implemented, merged, and physically
> validated** Phase 7 architecture — not plans. See `docs/CURRENT_PHASE.md`
> for per-change evidence (commits, merges, QA matrix).

> **Redesign V2 history:** Phase 7 Change D uncovered a design failure —
> the periodic WorkManager safety net was a dependency that should not
> exist. This was a planning/design conclusion, not a product or runtime
> failure, and not a proven RecoveryWorker bug. Periodic WorkManager is
> retired; Hermes bounded redelivery is the primary recent-loss safety net.

### 17.1 Realtime path stays FCM

FCM remains the realtime transport. The recovery paths are Hermes bounded
redelivery and event-driven Android reconciliation — never periodic
WorkManager polling and never a replacement for push.

### 17.2 Hermes bounded redelivery (implemented)

Hermes redelivers recently accepted but unacknowledged notifications via
FCM. This is the **primary** recent-loss safety net.

```text
Hermes redelivery query
→ sent_at IS NOT NULL
→ acknowledged_at IS NULL
→ canceled_at IS NULL
→ committed run
→ within 6 hours of first sent_at
→ at least 2 hours since last_attempt_at
→ same notification_id
→ NORMAL FCM priority
→ FCM send
→ Ackline Room INSERT IGNORE
→ one notification on INSERTED
```

Key properties (validated):

- `sent_at` is preserved as **first FCM acceptance** (never overwritten).
- `send_attempts` and `last_attempt_at` are reused for tracking.
- Redelivery copies use **NORMAL** FCM priority regardless of original
  level.
- Same `notification_id` — Room `INSERT IGNORE` absorbs duplicates.
- No Hermes DB migration — uses existing columns only.
- No delivery-receipt protocol.
- Bounded by the 6-hour window — no infinite redelivery loop.
- No exact +2h redelivery SLA; scheduler cadence is q120m while eligibility
  is `last_attempt_at >= 2h`, so a cycle may skip until the next one.

Physical proof: a lost-first synthetic canary was redelivered by real
production FCM (`send_attempts` 1→2, `sent_at` preserved, `last_attempt_at`
advanced, NORMAL priority), with Tailscale OFF and Oppo persisting and
notifying without app opening.

### 17.3 Event-driven Android recovery (implemented)

```text
Hermes ack_server
→ GET /notifications/pending
→ explicit VPN Network HTTPS
→ RecoveryWorker
→ canonical AlertIngestion
→ Room INSERT IGNORE
→ notification on INSERTED
```

Triggers (event-driven only, all implemented):

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

Physical proof: fresh install with empty Room and a stale Hermes FCM target
pulled 4/4 server pending alerts via startup recovery over HTTPS/Tailscale;
all decrypted and persisted; no FCM delivery could explain the rows.
`POST_NOTIFICATIONS` was **not** granted during this run, so native
notification presentation on the recovery path was not separately
physically exercised; canonical `AlertIngestion` is shared with the
separately proven FCM path.

### 17.4 Recovery contract (implemented)

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

### 17.5 Canonical ingestion (implemented)

A single `AlertIngestion` path shared by `FirebaseMessagingService` and
`RecoveryWorker`: kid check → decrypt → inner decode → payload parse →
`repository.insertIncoming` → native notification only on INSERTED.
Mechanical reuse of the proven Phase 5/6 receive path.

### 17.6 Failure taxonomy (implemented)

- Transient (network, DNS, TLS, timeout, IOException, HTTP 408/429/5xx):
  `Result.retry()` for one-time workers, exponential WorkManager backoff.
- Permanent/configuration (blank/malformed base URL, 403, 404, contract
  4xx, 409): no retry loop, sanitized diagnostic, Room untouched.
- Per-item decrypt/validation failure: skip item, continue batch, no
  crash, no DB regression.

### 17.7 FID / re-pair (implemented)

- Ackline persists the last observed FID (`FidRePairStore`). First
  observation → baseline, `rePairRequired = false`. Later different FID →
  store new FID, `rePairRequired = true`, enqueue recovery.
- Setup shows an actionable re-pair warning. `rePairRequired` survives
  process restarts and clears only through an explicit Setup action
  ("Mark as updated") after the operator updates
  `~/.hermes/secrets/ackline-fid` with the current FID.
- No device registry, no server write for FID, no automatic provisioning.
- FID re-pair was performed manually during final QA: uninstall/reinstall
  generated a new FID and erased `FidRePairStore`, so the new FID became
  the fresh-install baseline; FID-registration recovery executed and
  succeeded; the operator manually updated
  `~/.hermes/secrets/ackline-fid`; subsequent production FCM to the new
  installation PASS. In-place changed-FID warning semantics
  (later-FID-change → `rePairRequired = true` → Setup warning) were
  **not** forced physically during final QA; they remain covered by unit
  tests.

### 17.8 Data rules (implemented)

- No Room migration (schema stays v3).
- No `recovered_at`, server revisions, sync version, or tombstones.
- No Hermes DB migration; recovery and redelivery derive from existing
  columns.

### 17.9 Validation outcome (final integration QA — PASS)

Primary gate — Hermes bounded redelivery:

```text
Hermes sent/unacknowledged
→ bounded FCM redelivery
→ same notification_id
→ Ackline Room INSERT once
→ one native notification
→ duplicate harmless
→ Visto
→ remote ACK            PASS
```

Secondary gate — event-driven recovery (component-level evidence):

```text
Hermes creates alert → FCM accepted → device never persists
→ later startup/onDeletedMessages/FID-change
→ one-time recovery executes WITHOUT manual app open
→ missing row inserted (fresh-install 4/4)         PASS

native notification presentation on recovery path:
  not separately physically exercised in this fresh-install run
  because POST_NOTIFICATIONS was not granted; canonical
  AlertIngestion path is shared with the separately proven FCM path.

Visto → remote ACK over explicit VPN:               PASS
  separately physically proven by the G1 ACK canary; the four
  recovered rows were not used for a Visto→ACK end-to-end test.
```

Additional validated matrix:

```text
bounded FCM redelivery            PASS
duplicate / idempotency           PASS
local Visto while Tailscale OFF   PASS
remote ACK over explicit VPN      PASS
recovery GET over explicit VPN    PASS
fresh-install recovery            PASS
FID re-pair performed manually    PASS
fresh-install realtime FCM        PASS
duplicate realtime FCM ignored    PASS
```

**No acceptance gate required waiting for a periodic WorkManager cycle.**

### 17.10 Honest limits

Do **not** claim:

- exact-once FCM;
- exact +2h redelivery SLA;
- indefinite autonomous recovery if every FCM attempt is lost and the app
  never starts;
- periodic WorkManager recovery;
- delivery receipt semantics;
- FID automatic provisioning;
- that a single recovery run physically exercised the full chain
  recovery → notification shown → Visto → ACK (component-level evidence
  only; see §17.9);
- that a non-empty physical ACK backlog was drained in Change G (worker
  execution observed and succeeded; behavior covered by
  implementation/tests);
- that an in-place changed-FID rotation was forced physically during
  final QA (covered by unit tests; physical FID evidence is the
  fresh-install baseline + manual re-pair).

The long-offline model is:

```text
FCM offline retention + bounded copies + event-driven reconciliation.
```

### 17.11 Operational follow-up — RESOLVED

`ack_server.py` lifecycle/supervision is resolved via macOS system launchd
LaunchDaemon (`ai.hermes.personal-admin-ack`). Runs as user `eduardo` (not
root), binds `127.0.0.1:2587`, external exposure through Tailscale Serve
`:8443`. Controlled SIGTERM confirmed auto-restart in ~2s. RunAtLoad is
configured but post-reboot auto-start has not been physically verified yet.
