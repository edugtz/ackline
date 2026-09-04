# Current Phase

## Status

**PLANNING COMPLETE — REDESIGN V2 — READY FOR CHANGE E**

Phase: `7 — Recovery and Reconciliation`

Ackline branch: `dev`

Base branch: `dev`

**Phase 6 remains COMPLETE — CLOSED** (see closeout summary below).

Phase 7 blockers: **0**

Implementation has **NOT** started.

- No implementation branches exist (neither in Ackline nor in Hermes).
- No Kotlin, Java, Gradle, AndroidManifest, Python/Hermes source, test, or
  production database file has been modified by this planning session.
- No Phase 7 **implementation** commits, pushes, or merges have occurred;
  the Phase 7 planning documentation itself is now versioned (branch
  `7-recovery-and-reconciliation`, docs only).

Current change:

```text
Change E — Hermes Bounded FCM Redelivery
NOT STARTED
```

Physical-device/manual QA is scheduled **only** in Change G.

**Change D physical QA is ABORTED — DESIGN GATE FAILED.** This is a
planning/design conclusion, not a product or runtime failure. The abort
resulted from discovering that the periodic WorkManager safety net was a
design dependency that should not exist, not from a code defect or
production incident.

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

FCM remains the realtime transport. Hermes bounded redelivery and
event-driven Android recovery are the safety net — not periodic WorkManager
polling.

---

## Canonical Design Decisions (Redesign V2)

Phase 7 Change D uncovered a **DESIGN failure**, not a proven RecoveryWorker
bug. The following decisions are now canonical:

1. FCM remains the realtime transport.
2. Periodic WorkManager is **NOT** a critical recovery guarantee.
3. Remove the 2-hour periodic recovery dependency from Ackline.
4. Hermes becomes responsible for **bounded FCM redelivery** of recently
   accepted but still unacknowledged notifications.
5. Redelivery uses the **SAME `notification_id`**.
6. Ackline Room `INSERT IGNORE` absorbs duplicates.
7. Initial priority mapping remains:
   ```text
   REMEMBER → NORMAL
   IMPORTANT → HIGH
   URGENT → HIGH
   ```
8. Recovery/redelivery copies use **NORMAL** FCM priority regardless of
   original level.
9. Hermes redelivery policy:
   - eligible only while `acknowledged_at IS NULL`
   - `canceled_at IS NULL`
   - committed run (`run.status = 'committed'`)
   - `sent_at IS NOT NULL`
   - within **6 hours** of first `sent_at`
   - at least **2 hours** since `last_attempt_at`
10. Preserve `sent_at` as **FIRST FCM acceptance** (never overwritten by
    redelivery).
11. Reuse `send_attempts` and `last_attempt_at` for redelivery tracking.
12. **NO Hermes DB migration.**
13. **NO Room migration.**
14. **NO delivery-receipt protocol** in Phase 7 v2.
15. Keep `GET /notifications/pending`.
16. Keep `AlertIngestion` / `RecoveryRunner` / `RecoveryWorker` for
    **event-driven** recovery only:
    - startup
    - `onDeletedMessages`
    - FID registration/change
17. **Retire/cancel** the already-installed unique periodic work:
    `ackline-notification-recovery-periodic`
18. Keep ntfy rollback through Phase 8.
19. Change D physical QA is **ABORTED** as DESIGN GATE FAILED.
20. Do not represent the aborted QA as a product/runtime failure.
21. Do not modify source yet.

---

## Required Roadmap Scope

- Hermes bounded FCM redelivery for sent/unacknowledged notifications;
- minimal `GET /notifications/pending` or equivalent recovery contract;
- reconcile into Room by `notificationId`;
- `onDeletedMessages()` recovery signal where appropriate;
- event-driven recovery (startup, FID change);
- ACK backlog recovery;
- cancel existing periodic WorkManager unique work;
- no periodic WorkManager as recovery dependency.

---

## Explicitly Out of Scope (Phase 7)

- ntfy retirement (Phase 8);
- constant/aggressive polling;
- periodic WorkManager as a critical recovery path;
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
- analytics SDK;
- delivery-receipt protocol;
- Hermes DB migration;
- Room migration.

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
- Hermes bounded redelivery is the primary safety net for missed transport;
  Android event-driven recovery supplements it.

---

## Final Architecture Decisions (Redesign V2)

### 1. Recovery semantics

Recovery eligibility for `GET /notifications/pending` remains:

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

### 2. Hermes bounded redelivery (NEW — Redesign V2)

Hermes is now responsible for redelivering recently accepted but
unacknowledged notifications via FCM. This is the primary recovery safety
net.

Redelivery eligibility:

```text
acknowledged_at IS NULL
AND canceled_at IS NULL
AND run.status = 'committed'
AND sent_at IS NOT NULL
AND (now - sent_at) <= 6 hours
AND (now - last_attempt_at) >= 2 hours
```

Key properties:
- redelivery uses the **same `notification_id`**;
- `sent_at` is preserved as **first FCM acceptance** (never overwritten);
- `send_attempts` and `last_attempt_at` are reused for tracking;
- redelivery copies use **NORMAL** FCM priority;
- no Hermes DB migration required — uses existing columns;
- no delivery-receipt protocol — Hermes does not require device
  confirmation of receipt;
- bounded by the 6-hour window — no infinite redelivery loop.

### 3. Recovery endpoint

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

### 4. Payload

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

### 5. Endpoint bound

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

### 6. Android reconciliation

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

### 7. Canonical ingestion

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

### 8. Recovery triggers (Redesign V2)

Event-driven triggers only:

```text
A. onDeletedMessages()                     → unique one-time recovery
B. AcklineApplication startup              → unique one-time recovery
C. FID registration/change                 → unique one-time recovery
```

No periodic WorkManager. No foreground service. No AlarmManager. No exact
alarms. No sockets. No MQTT.

The 2-hour periodic WorkManager safety net has been **retired** as a design
dependency. Hermes bounded redelivery (decision 2 above) replaces it as the
primary recovery safety net for missed transport events.

The existing installed periodic work `ackline-notification-recovery-periodic`
must be **cancelled** in Change F.

### 9. Unique work policy

One-time recovery:

```text
ExistingWorkPolicy.KEEP
```

Reason: a new trigger must not cancel an already queued/retrying recovery and
reset its backoff.

Periodic recovery: **RETIRED** — no longer used.

### 10. Failure / retry

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
- exponential WorkManager backoff;

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

### 11. ACK backlog

Reconciliation **never** manually ACKs.

After a successful recovery GET proves Hermes/Tailscale is reachable:

```text
enqueue existing AckSyncScheduler once
```

This opportunistically drains the local ACK backlog.

`INSERT IGNORE` must preserve locally acknowledged state if Hermes still
returns that row because the remote ACK is pending.

### 12. FID / re-pair

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

### 13. Databases

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

### 14. Acceptance validation (Redesign V2)

New Phase 7 acceptance must **NOT** require waiting for a natural periodic
WorkManager cycle.

Required validation scenario:

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

Event-driven `GET /pending` remains separately validated:

```text
Hermes creates alert
→ FCM accepted
→ device never persists
→ later startup/onDeletedMessages/FID-change
→ one-time recovery executes
→ missing row inserted
→ one notification shown
→ Visto
→ ACK reaches Hermes
```

---

## Change Units

Phase 7 (Redesign V2) is documented as separate reviewable changes, not
one opaque implementation.

### Change A — Hermes Recovery Contract

**SUPERSEDED** by Change E for the redelivery portion. The
`GET /notifications/pending` endpoint contract is retained; the periodic
WorkManager dependency is removed. See Change E.

Repo: **Hermes Personal Admin**

Scope (retained):

- `GET /notifications/pending`;
- recovery query (eligibility + deterministic ordering);
- E2EE envelopes via `fcm_sender.build_envelope`;
- Tailscale auth;
- bounds/error semantics (cap 200, HTTP 409);
- server tests.

### Change B — Android Reconciliation Core

**RETAINED** — no design change to the core reconciliation logic.

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

**MODIFIED** — periodic WorkManager trigger removed.

Repo: **Ackline**

Scope:

- `onDeletedMessages`;
- startup trigger;
- ~~2-hour periodic safety net~~ **REMOVED**;
- FID persistence/change detection;
- `rePairRequired`;
- Setup warning + explicit clear action.

Acceptance:

- recovery can occur without manual app open (event-driven);
- trigger scheduling deterministic;
- FID change actionable.

### Change D — Integration / Physical QA / Docs

**ABORTED — DESIGN GATE FAILED.**

Repos: **Ackline + Hermes**

Physical-device QA was aborted when Change D uncovered that the periodic
WorkManager safety net was a design dependency that should not exist. This
is a planning/design conclusion, **not** a product or runtime failure.

No code defects were proven. The periodic WorkManager path was not
validated in production and is now retired by design.

Documentation closeout responsibilities are reassigned to Change G.

### Change E — Hermes Bounded FCM Redelivery (NEW)

Repo: **Hermes Personal Admin**

Scope:

- Hermes bounded redelivery of sent/unacknowledged notifications;
- redelivery eligibility query using existing columns;
- redelivery policy: `acknowledged_at IS NULL`, `canceled_at IS NULL`,
  committed run, `sent_at IS NOT NULL`, within 6 hours of first
  `sent_at`, at least 2 hours since `last_attempt_at`;
- preserve `sent_at` as first FCM acceptance (never overwrite);
- reuse `send_attempts` and `last_attempt_at`;
- redelivery copies use NORMAL FCM priority;
- same `notification_id` on redelivery;
- no Hermes DB migration;
- no delivery-receipt protocol;
- server tests.

Acceptance:

- sent/unacknowledged notification redelivered within policy bounds;
- `sent_at` preserved as first acceptance;
- same `notification_id` used;
- NORMAL priority on redelivery copies;
- no DB migration;
- 6-hour window enforced;
- 2-hour minimum gap enforced.

### Change F — Ackline Remove Periodic Recovery Dependency (NEW)

Repo: **Ackline**

Scope:

- cancel/retire the installed unique periodic work
  `ackline-notification-recovery-periodic`;
- remove periodic WorkManager scheduling code;
- remove periodic recovery configuration;
- verify event-driven triggers remain functional;
- verify no regression to one-time recovery paths.

Acceptance:

- periodic work cancelled on app start/update;
- no periodic WorkManager enqueued;
- event-driven recovery (startup, onDeletedMessages, FID change) unchanged;
- `./gradlew assembleDebug` passes.

### Change G — Focused Integration QA / Docs Closeout (NEW)

Repos: **Ackline + Hermes**

Scope:

- focused integration QA validating the Redesign V2 acceptance path;
- physical Oppo matrix for the redesigned recovery flow;
- Phase 7 documentation closeout (update all docs to implemented state).

Acceptance (Redesign V2):

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

Plus separately:

```text
event-driven GET /pending
→ startup/onDeletedMessages/FID-change triggers
→ one-time recovery
→ missing row inserted
→ one notification shown
```

Documentation closeout:

- update `docs/CURRENT_PHASE.md` to Phase 7 COMPLETE;
- update `docs/IMPLEMENTATION_PLAN.md` to implemented state;
- update `docs/ARCHITECTURE.md` to remove "planned" labels;
- keep `docs/MVP_PHASES.md` consistent.

### Dependencies

```text
A before B integration
B before C triggers consume recovery scheduler/core
E before G (Hermes redelivery must exist before integration QA)
F before G (periodic removal must land before integration QA)
G after A+B+C+E+F are reviewed/landed
```

---

## Acceptance Gates (phase-level, Redesign V2)

- Hermes bounded redelivery sends same `notification_id` for
  sent/unacknowledged notifications within policy bounds.
- Ackline Room `INSERT IGNORE` absorbs duplicate delivery harmlessly.
- One native notification per unique `notificationId`.
- Event-driven recovery inserts missing alerts without manual app open.
- Later duplicate FCM/redelivery remains harmless.
- Reconciliation never changes acknowledged alerts incorrectly; local ACK
  state is never regressed.
- `rePairRequired` survives process restart and clears only through explicit
  Setup action.
- ACK backlog drains after a successful recovery GET without manual ACKing.
- No Room migration (stay on v3); no Hermes DB migration.
- Periodic WorkManager unique work is cancelled.
- **No acceptance gate requires waiting for a periodic WorkManager cycle.**

---

## Out of Scope (Phase 7)

See "Explicitly Out of Scope" above. Recovery must not become polling,
Hermes logic must not move into Android, and no server/account/registry
infrastructure may be introduced. Periodic WorkManager is not a recovery
path.

---

## Workflow

Current state:

```text
dev
→ 7-recovery-and-reconciliation (planning/docs only)
→ planning review (this document set — Redesign V2)
→ merge planning docs to dev
→ Change E implementation branch (Hermes redelivery)
→ Change F implementation branch (cancel periodic)
→ Change A/B/C implementation branches (retained scope)
→ implementation
→ validation
→ independent review
→ Change G: focused integration QA + docs closeout
→ user commit + push
→ ChatGPT GitHub review
→ PASS
→ merge to dev
```

Recommended review branches (conceptual only; **not created** in this
session):

```text
Hermes:  7a-recovery-contract (retained)
Ackline: 7b-reconciliation-core (retained)
Ackline: 7c-triggers-fid (retained, periodic removed)
Hermes:  7e-bounded-redelivery (new)
Ackline: 7f-remove-periodic (new)
Final:   7g-integration-qa-docs (new, replaces 7d)
```

The user owns commits, pushes, and merges.

---

## Next Step

1. Review this planning record (`CURRENT_PHASE.md`),
   `docs/IMPLEMENTATION_PLAN.md`, and the Phase 7 section of
   `docs/ARCHITECTURE.md`.
2. On approval, open **Change E — Hermes Bounded FCM Redelivery** on Hermes
   branch `7e-bounded-redelivery` and implement per the plan.

No implementation branch is created by this planning session.
