# Current Phase

## Status

**PLANNING COMPLETE — READY FOR CHANGE A**

Phase: `7 — Recovery and Reconciliation`

Ackline branch: `dev`

Base branch: `dev`

**Phase 6 remains COMPLETE — CLOSED** (see closeout summary below).

Phase 7 blockers: **0**

Implementation has **NOT** started.

- No implementation branches exist (neither in Ackline nor in Hermes).
- No Kotlin, Java, Gradle, AndroidManifest, Python/Hermes source, test, or
  production database file has been modified by this planning session.
- No commits, pushes, or merges were made.

Current change:

```text
Change A — Hermes Recovery Contract
NOT STARTED
```

Physical-device/manual QA is scheduled **only** in Change D.

---

## Phase 6 Closeout Summary (remains CLOSED)

Phase 6 — Hermes Outbox / FCM Sender Integration was fully implemented,
validated against real Firebase and the physical Oppo, and cut over to
production. `ACTIVE_TRANSPORT = "fcm"` in `notification_state.py`; ntfy
remains implemented as rollback only.

Hermes final merge:

```text
5b5777a827e097a98687bc6fae0060a2e6fcebb3
merge: complete Phase 6 FCM transport cutover
```

Hermes automated tests: **28/28 PASS**. Full evidence, including the
production cutover canary (`8304672d700c4056b5d456eae49b6060`) and the
forensic correction of the invalid "Stage 3-R" diagnostic, is retained in
the repository history (previous version of this file and the Phase 6
implementation plan).

Phase 6 delivery semantics survive unchanged into Phase 7:

```text
sent_at = FCM/provider accepted the transport attempt
```

It is **not** proof of device persistence, decryption, or display.

---

## Phase 7 Objective

Make FCM the realtime path without treating **one push attempt** as the only
path to recover a pending alert.

### Product quality goal

> A rare missed/dropped transport event must not permanently erase a Hermes
> pending notification.

FCM remains the realtime transport. The recovery path is a **safety net**,
not a replacement for push.

---

## Required Roadmap Scope

- minimal `GET /notifications/pending` or equivalent recovery contract;
- reconcile into Room by `notificationId`;
- `onDeletedMessages()` recovery signal where appropriate;
- long-offline recovery;
- FID changes / re-pair requirement;
- ACK backlog recovery;
- avoid aggressive periodic polling.

---

## Explicitly Out of Scope (Phase 7)

- ntfy retirement (Phase 8);
- constant/aggressive polling;
- generic bidirectional sync engine;
- Hermes business logic in Android;
- server accounts;
- public/cloud DB backend;
- Firebase Auth;
- Firestore;
- multi-device registry;
- foreground service;
- exact alarms;
- Room encryption;
- key rotation;
- UX redesign;
- analytics SDK.

---

## Sources of Truth (unchanged + recovery)

```text
Hermes SQLite = server/source-of-truth for notification state
Room          = device/source-of-truth for received alert state
FCM           = realtime transport only
tray state    = never source of truth
```

Recovery-specific truth statements:

- Recovery derives **only from existing Hermes columns**; no Hermes DB
  migration, no new server-side state.
- Reconciliation is **one-way** (Hermes pending → Ackline); local rows are
  never deleted merely because they are absent server-side.
- A concurrently pending remote ACK does **not** make a Hermes row
  ineligible for recovery; Room `INSERT IGNORE` preserves local
  acknowledged state.

---

## Final Architecture Decisions (approved after Phase 7 preflight)

### 1. Recovery semantics

Recovery eligibility is:

```text
canceled_at IS NULL
AND acknowledged_at IS NULL
AND associated run.status = 'committed'
```

`sent_at` is intentionally **NOT** filtered.

Reason: `sent_at` only means FCM/provider accepted the transport attempt.
It does **not** prove Ackline persisted the alert. Therefore both:

```text
sent_at = NULL
```

and:

```text
sent_at = PRESENT
```

remain recoverable while `acknowledged_at IS NULL`.

Deterministic server ordering:

```text
ORDER BY n.created_at ASC, n.notification_id ASC
```

### 2. Recovery endpoint

Preferred production contract:

```text
GET /notifications/pending
```

on the existing Hermes `ack_server.py`.

Security: same `Tailscale-User-Login` trusted identity boundary as ACK.

Endpoint properties:

- read-only;
- fail-closed;
- `Cache-Control: no-store`;
- no server state mutation;
- no public authentication system;
- no Firebase Auth;
- no API key;
- no account/device registry.

### 3. Payload

Reuse the existing Phase 5/6 E2EE envelope:

```json
{
  "v": "1",
  "kid": "ackline-main",
  "nonce": "<base64url-no-padding 12 bytes>",
  "ciphertext": "<base64url-no-padding ciphertext||16-byte-tag>"
}
```

Use existing `fcm_sender.build_envelope(row)`.

Inner payload remains:

```text
protocol
notification_id
level
title
message
created_at
ack_token
```

No second plaintext notification protocol. No new crypto design.

### 4. Endpoint bound

Maximum pending recovery items: **200**.

Detection must use cap+1 semantics.

If `> 200`:

```text
HTTP 409
{
  "ok": false,
  "error": "too_many_pending"
}
```

- No silent truncation.
- No Phase 7 pagination.
- This overflow is a degraded/operator-action state, **not** automatically
  retried forever.

### 5. Android reconciliation

One-way only:

```text
Hermes pending → Ackline
```

For every encrypted recovered item:

```text
envelope parse
→ AES-GCM decrypt
→ inner decode
→ parse existing Ackline payload
→ Room insertIgnore(notificationId)
```

If **INSERTED**:

- persist row;
- post one native notification.

If **DUPLICATE**:

- no overwrite;
- no ACK state regression;
- no native notification repost.

Never DELETE local rows merely because absent server-side.

### 6. Canonical ingestion

Extract/reuse one canonical ingestion path shared by:

```text
FirebaseMessagingService
```

and:

```text
RecoveryWorker
```

Expected conceptual component: **`AlertIngestion`**.

It contains the existing sequence:

```text
kid check
→ decrypt
→ inner decode
→ payload parse
→ repository.insertIncoming
→ show notification only on INSERTED
```

This is a mechanical reuse/extraction, not a new business layer.

### 7. Recovery triggers

- **A.** `FirebaseMessagingService.onDeletedMessages()` → enqueue unique
  one-time recovery;
- **B.** `AcklineApplication` startup → enqueue unique one-time recovery;
- **C.** FID registration/change → enqueue unique one-time recovery;
- **D.** periodic WorkManager safety net → every **2 hours**,
  `NetworkType.CONNECTED`.

No foreground service. No AlarmManager. No exact alarms. No sockets. No
MQTT.

Reason for the periodic fallback: `onDeletedMessages`/startup/FID callbacks
alone cannot guarantee recovery after a fully missed transport event without
user app interaction. 2 hours provides a bounded recovery backstop with
negligible personal-use network cost.

### 8. Unique work policy

One-time recovery:

```text
ExistingWorkPolicy.KEEP
```

Reason: a new trigger must not cancel an already queued/retrying recovery and
reset its backoff.

Periodic recovery: unique periodic work.

### 9. Failure / retry

Transient failures (retryable):

```text
network
DNS
TLS
timeout
IOException
HTTP 408
HTTP 429
HTTP 5xx
```

- One-time `RecoveryWorker`: `Result.retry()`;
- Periodic `RecoveryWorker`: also `Result.retry()`;
- exponential WorkManager backoff;
- do **not** convert a transient periodic failure to success merely to wait
  for the next 2-hour period.

Permanent/configuration (no retry loop):

```text
blank/malformed base URL
403
404
contract 4xx
409 too_many_pending
```

→ no retry loop; sanitized diagnostic; Room untouched.

Per-item decrypt/validation failure:

```text
skip item
continue batch
no crash
no DB regression
```

### 10. ACK backlog

Reconciliation **never** manually ACKs.

After a successful recovery GET proves Hermes/Tailscale is reachable:

```text
enqueue existing AckSyncScheduler once
```

This opportunistically drains the local ACK backlog.

`INSERT IGNORE` must preserve locally acknowledged state if Hermes still
returns that row because the remote ACK is pending.

### 11. FID / re-pair

Persist last observed FID locally.

First observation after introducing this feature:

```text
store baseline FID
rePairRequired = false
```

Later different FID:

```text
store new FID
rePairRequired = true
enqueue recovery
```

Setup surface displays an actionable re-pair warning.

Manual provisioning remains:

```text
user copies current FID
→ updates Hermes ~/.hermes/secrets/ackline-fid
```

IMPORTANT:

```text
rePairRequired MUST NOT clear merely on app/process restart.
```

It remains `true` until explicit user action in Setup ("Mark as updated" /
equivalent concise UX) after the operator updates Hermes.

No device registry. No server write for FID. No automatic provisioning.

### 12. Databases

Room migration:

```text
NO — stay on v3
```

unless implementation uncovers a concrete correctness requirement.

Do NOT add:

```text
recovered_at
server revisions
sync version
tombstones
```

Hermes DB migration:

```text
NO
```

Recovery state derives from existing columns.

### 13. Long-offline guarantee (Phase 7 acceptance gate)

Required scenario:

```text
Hermes creates alert
→ FCM accepted
→ device never persists
→ Hermes sent_at PRESENT
→ Hermes acknowledged_at NULL
→ Ackline row ABSENT
→ later device has connectivity + Tailscale
→ periodic/event recovery executes WITHOUT manual app open
→ missing row inserted
→ one notification shown
→ Visto
→ ACK reaches Hermes
→ later duplicate FCM harmless
```

---

## Change Units

Phase 7 is documented as separate reviewable changes, not one opaque
implementation.

### Change A — Hermes Recovery Contract

Repo: **Hermes Personal Admin**

Scope:

- `GET /notifications/pending`;
- recovery query (eligibility + deterministic ordering);
- E2EE envelopes via `fcm_sender.build_envelope`;
- Tailscale auth;
- bounds/error semantics (cap 200, HTTP 409);
- server tests.

Acceptance:

- read-only contract proven;
- `sent_at` PRESENT recovery proven;
- no plaintext leakage;
- no DB mutation.

### Change B — Android Reconciliation Core

Repo: **Ackline**

Scope:

- canonical `AlertIngestion` extraction;
- HTTPS recovery client;
- `RecoveryRunner`;
- `RecoveryWorker`;
- one-time scheduler;
- idempotent Room ingestion;
- ACK drain after successful GET.

Acceptance:

- missing alert inserts once;
- duplicate harmless;
- ACK state not regressed;
- failure taxonomy proven.

### Change C — Recovery Triggers + FID/Re-pair

Repo: **Ackline**

Scope:

- `onDeletedMessages`;
- startup trigger;
- 2-hour periodic safety net;
- FID persistence/change detection;
- `rePairRequired`;
- Setup warning + explicit clear action.

Acceptance:

- recovery can occur without manual app open;
- trigger scheduling deterministic;
- FID change actionable.

### Change D — Integration / Physical QA / Docs

Repos: **Ackline + Hermes**

Scope:

- controlled integration;
- physical Oppo matrix (long-offline/drop simulation, ACK backlog, duplicate
  FCM, reboot, Tailscale outage/recovery);
- final Phase 7 documentation closeout.

Acceptance:

- all Phase 7 roadmap gates PASS.

### Dependencies

```text
A before B integration
B before C triggers consume recovery scheduler/core
D after A+B+C are reviewed/landed
```

---

## Acceptance Gates (phase-level)

- Missing pending alert is recoverable once Hermes is reachable, including
  when `sent_at` is PRESENT on the Hermes row.
- Long-offline return recovers expected pending data **without** manually
  opening the app (decision 13 gate).
- Later duplicate FCM delivery remains harmless.
- Reconciliation never changes acknowledged alerts incorrectly; local ACK
  state is never regressed.
- `rePairRequired` survives process restart and clears only through explicit
  Setup action.
- ACK backlog drains after a successful recovery GET without manual ACKing.
- No Room migration (stay on v3); no Hermes DB migration.

---

## Out of Scope (Phase 7)

See "Explicitly Out of Scope" above. Recovery must not become polling,
Hermes logic must not move into Android, and no server/account/registry
infrastructure may be introduced.

---

## Workflow

Current state:

```text
dev
→ [no phase branches exist yet]
→ planning documents updated (this session)
→ document review
→ Change A branch + implementation
→ validation
→ independent review
→ manual/device QA (Change D)
→ user commit + push
→ ChatGPT GitHub review
→ PASS
→ merge to dev
```

Recommended review branches (conceptual only; **not created** in this
session):

```text
Hermes:  7a-recovery-contract
Ackline: 7b-reconciliation-core
Ackline: 7c-recovery-triggers-fid
Final:   7d-recovery-qa-docs
```

The user owns commits, pushes, and merges.

---

## Next Step

1. Review this planning record (`CURRENT_PHASE.md`),
   `docs/IMPLEMENTATION_PLAN.md`, and the Phase 7 section of
   `docs/ARCHITECTURE.md`.
2. On approval, open **Change A — Hermes Recovery Contract** on Hermes
   branch `7a-recovery-contract` and implement per the plan.

No implementation branch is created by this planning session.