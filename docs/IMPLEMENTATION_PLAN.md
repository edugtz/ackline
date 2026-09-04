# Phase 7 — Recovery and Reconciliation Implementation Plan (Redesign V2)

## 1. Status

**ACTIVE — PLANNING COMPLETE — REDESIGN V2 — READY FOR CHANGE E**

Phase: `7 — Recovery and Reconciliation`

Ackline branch: `dev`

Base branch: `dev`

Phase 6 remains **COMPLETE — CLOSED** (Hermes merge
`5b5777a827e097a98687bc6fae0060a2e6fcebb3`, Hermes tests 28/28 PASS,
`ACTIVE_TRANSPORT = "fcm"`).

Phase 7 blockers: **0**.

Implementation has **NOT** started. No implementation branches exist. No
source, test, or database file has been modified by planning.

Current change:

```text
Change E — Hermes Bounded FCM Redelivery
NOT STARTED
```

---

## 2. Redesign V2 Context

Phase 7 Change D uncovered a **DESIGN failure**, not a proven
RecoveryWorker bug. The periodic WorkManager safety net was a design
dependency that should not exist. This is a planning conclusion, not a
product or runtime failure.

Canonical redesign decisions:

1. FCM remains the realtime transport.
2. Periodic WorkManager is **NOT** a critical recovery guarantee.
3. Remove the 2-hour periodic recovery dependency.
4. Hermes becomes responsible for **bounded FCM redelivery**.
5. Redelivery uses the same `notification_id`.
6. Ackline Room `INSERT IGNORE` absorbs duplicates.
7. Keep `GET /notifications/pending` for event-driven recovery.
8. Keep `AlertIngestion` / `RecoveryRunner` / `RecoveryWorker` for
   event-driven triggers only.
9. Cancel the already-installed periodic work.
10. No Hermes DB migration, no Room migration, no delivery-receipt protocol.

---

## 3. Objective

Make FCM the realtime path without treating one push attempt as the only
path to recover a pending alert.

Product quality goal:

> A rare missed/dropped transport event must not permanently erase a Hermes
> pending notification.

The recovery path is Hermes bounded redelivery plus event-driven Android
reconciliation — not periodic WorkManager polling.

---

## 4. Approved Architecture Contract (Redesign V2)

### Recovery query (GET /notifications/pending)

Eligibility:

```text
canceled_at IS NULL
AND acknowledged_at IS NULL
AND associated run.status = 'committed'
```

`sent_at` **not** filtered — `sent_at` proves only provider acceptance, not
Ackline persistence. Both `sent_at = NULL` and `sent_at = PRESENT` remain
recoverable while `acknowledged_at IS NULL`.

Ordering:

```text
ORDER BY n.created_at ASC, n.notification_id ASC
```

### Hermes bounded redelivery (NEW)

Hermes redelivers recently accepted but unacknowledged notifications via
FCM. This is the primary recovery safety net.

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

- same `notification_id` on redelivery;
- `sent_at` preserved as first FCM acceptance (never overwritten);
- `send_attempts` and `last_attempt_at` reused for tracking;
- NORMAL FCM priority on all redelivery copies;
- no Hermes DB migration — uses existing columns;
- no delivery-receipt protocol;
- bounded by 6-hour window — no infinite redelivery loop.

### Endpoint

```text
GET /notifications/pending
```

on existing Hermes `ack_server.py`. Same `Tailscale-User-Login` trusted
identity boundary as ACK. Read-only, fail-closed, `Cache-Control: no-store`,
no state mutation, no Firebase Auth, no API key, no account/device registry.

### Payload

Reuse the Phase 5/6 E2EE envelope (`v`/`kid`/`nonce`/`ciphertext`) via
`fcm_sender.build_envelope(row)`. Inner payload unchanged
(`protocol`, `notification_id`, `level`, `title`, `message`, `created_at`,
`ack_token`). No plaintext protocol. No new crypto.

### Bound

Max pending items: **200**, detected with cap+1 semantics. `> 200` →
`HTTP 409 {"ok": false, "error": "too_many_pending"}`. No silent truncation,
no pagination, degraded/operator-action state not auto-retried forever.

### Android reconcile

One-way (Hermes pending → Ackline). Per item: envelope parse → AES-GCM
decrypt → inner decode → payload parse → Room
`insertIgnore(notificationId)`.

- INSERTED → persist row + one native notification;
- DUPLICATE → no overwrite, no ACK-state regression, no repost;
- never delete local rows absent server-side.

### Canonical ingestion

One shared `AlertIngestion` path for `FirebaseMessagingService` and
`RecoveryWorker`: kid check → decrypt → inner decode → payload parse →
`repository.insertIncoming` → notification only on INSERTED. Mechanical
reuse/extraction only.

### Triggers (Redesign V2)

Event-driven only:

```text
A. onDeletedMessages()         → unique one-time recovery
B. AcklineApplication startup  → unique one-time recovery
C. FID registration/change     → unique one-time recovery
```

No periodic WorkManager. No foreground service, AlarmManager, exact alarms,
sockets, or MQTT.

The 2-hour periodic WorkManager safety net has been **retired**. Hermes
bounded redelivery replaces it as the primary recovery safety net.

### Work policy

One-time recovery: `ExistingWorkPolicy.KEEP` (a new trigger must not cancel
an already queued/retrying recovery and reset its backoff).

Periodic recovery: **RETIRED** — no longer used.

### Failure taxonomy

- Transient (`network`, `DNS`, `TLS`, `timeout`, `IOException`, `HTTP 408`,
  `HTTP 429`, `HTTP 5xx`) → `Result.retry()` for one-time workers;
  exponential WorkManager backoff.
- Permanent/configuration (`blank/malformed base URL`, `403`, `404`,
  contract 4xx, `409 too_many_pending`) → no retry loop; sanitized
  diagnostic; Room untouched.
- Per-item decrypt/validation failure → skip item, continue batch; no
  crash; no DB regression.

### ACK backlog

Reconciliation never manually ACKs. After a successful recovery GET:
enqueue existing `AckSyncScheduler` once to drain the local ACK backlog.
`INSERT IGNORE` preserves locally acknowledged state when the remote ACK is
still pending.

### FID / re-pair

Persist last observed FID locally. First observation → baseline and
`rePairRequired = false`. Later different FID → store new FID,
`rePairRequired = true`, enqueue recovery. Setup shows an actionable warning.
Manual provisioning: user copies FID into
`~/.hermes/secrets/ackline-fid`. `rePairRequired` must **not** clear on
process restart; it clears only through explicit Setup action ("Mark as
updated"). No device registry, no server FID write, no automatic
provisioning.

### Databases

- Room: **no migration, stay on v3** unless a concrete correctness
  requirement emerges.
- No `recovered_at`, server revisions, sync version, or tombstones.
- Hermes DB: **no migration**; recovery/redelivery derives from existing
  columns.

---

## 5. Change Units

Each change is a separate reviewable unit with SPEC / PLAN / TASKS / GATE.

---

### Change A — Hermes Recovery Contract

**SUPERSEDED** by Change E for the redelivery portion. The
`GET /notifications/pending` endpoint contract is retained.

Repo: **Hermes Personal Admin**

#### SPEC

`GET /notifications/pending` on `ack_server.py`:

- recovery query: eligibility (`canceled_at IS NULL`, `acknowledged_at IS
  NULL`, `run.status = 'committed'`, `sent_at` unfiltered), ordered by
  `created_at ASC, notification_id ASC`;
- cap 200 with cap+1 detection → `HTTP 409 too_many_pending` above cap;
- each item serialized as the Phase 5/6 E2EE envelope through
  `fcm_sender.build_envelope(row)`; inner payload fields unchanged;
- `Tailscale-User-Login` required, fail-closed, read-only,
  `Cache-Control: no-store`, no state mutation, no new auth system;
- sanitized diagnostics only; no plaintext fields on the wire.

#### PLAN

- Inspect `ack_server.py` GET handling and the ACK identity check.
- Implement the recovery query in `ack_server.py` or, only if it
  materially improves testability, a small pure recovery helper, reusing
  existing columns only.
- Do **not** modify `notification_state.py` dispatcher semantics.
- Add the endpoint reusing `build_envelope`; enforce cap+1.
- Add server tests: eligibility matrix, ordering, cap/409, auth failure,
  read-only guarantee.

#### TASKS

- [ ] recovery query + deterministic ordering
- [ ] endpoint route + identity boundary + headers
- [ ] E2EE envelope responses
- [ ] cap 200 / HTTP 409 semantics
- [ ] server tests
- [ ] no plaintext/secret logging verification

#### GATE

```text
read-only contract proven
sent_at PRESENT recovery proven
no plaintext leakage
no DB mutation
```

Only after GATE passes may Change B integrate against it.

---

### Change B — Android Reconciliation Core

**RETAINED** — no design change to the core reconciliation logic.

Repo: **Ackline**

#### SPEC

- Extract canonical `AlertIngestion` shared by `FirebaseMessagingService`
  and `RecoveryWorker` (kid check → decrypt → inner decode → parse →
  `insertIncoming` → notification only on INSERTED). No behavior change to
  the push path.
- HTTPS recovery client for `GET /notifications/pending`
  (Tailscale-User-Login trusted network; base URL from existing config).
- `RecoveryRunner` + `RecoveryWorker` implementing the failure taxonomy
  (transient → `Result.retry()`; permanent → no retry, sanitized
  diagnostic, Room untouched; per-item skip).
- One-time scheduling with `ExistingWorkPolicy.KEEP`.
- After a successful GET: enqueue `AckSyncScheduler` once.

#### PLAN

- Refactor the existing receive path into `AlertIngestion` first, with the
  existing tests proving parity (mechanical extraction).
- Add the recovery client/runner/worker; wire Room `insertIgnore` with no
  schema change (v3).
- Verify failure taxonomy with unit tests on a fake HTTP boundary.

#### TASKS

- [ ] `AlertIngestion` extraction (push path unchanged)
- [ ] HTTPS recovery client (timeouts, no-store, sanitized errors)
- [ ] `RecoveryRunner` / `RecoveryWorker` + retry/backoff policy
- [ ] one-time unique scheduling (`ExistingWorkPolicy.KEEP`)
- [ ] idempotent Room ingestion (INSERTED → notify; DUPLICATE → nothing)
- [ ] ACK drain enqueue after successful GET
- [ ] unit tests: taxonomy, dedupe, ACK-state preservation

#### GATE

```text
missing alert inserts once
duplicate harmless
ACK state not regressed
failure taxonomy proven
```

---

### Change C — Recovery Triggers + FID/Re-pair

**MODIFIED** — periodic WorkManager trigger removed.

Repo: **Ackline**

#### SPEC

- `onDeletedMessages()` → unique one-time recovery.
- `AcklineApplication` startup → unique one-time recovery.
- FID registration/change → unique one-time recovery.
- ~~Periodic WorkManager safety net: 2 hours, `NetworkType.CONNECTED`,
  unique periodic work.~~ **REMOVED.**
- Persistent last-observed FID; `rePairRequired` state; Setup re-pair
  warning with explicit "Mark as updated" action; `rePairRequired` survives
  restarts and clears only via explicit action.
- No foreground service, no AlarmManager, no exact alarms, no sockets, no
  MQTT.

#### PLAN

- Implement persistent FID observation storage (local, no Room schema
  change — use existing preferences mechanism; do **not** add Room fields).
- Add trigger points reusing the Change B scheduler.
- Add Setup re-pair surface and explicit clear action.

#### TASKS

- [ ] `onDeletedMessages` trigger
- [ ] startup trigger
- [ ] FID change trigger
- [ ] FID persistence + `rePairRequired` semantics (restart-proof)
- [ ] Setup warning + explicit clear action
- [ ] unit tests: trigger determinism, restart persistence of
      `rePairRequired`

#### GATE

```text
recovery can occur without manual app open (event-driven)
trigger scheduling deterministic
FID change actionable
```

---

### Change D — Integration / Physical QA / Docs

**ABORTED — DESIGN GATE FAILED.**

Repos: **Ackline + Hermes**

Physical-device QA was aborted when Change D uncovered that the periodic
WorkManager safety net was a design dependency that should not exist. This
is a planning/design conclusion, **not** a product or runtime failure.

No code defects were proven. The periodic WorkManager path was not
validated in production and is now retired by design.

Documentation closeout responsibilities are reassigned to Change G.

---

### Change E — Hermes Bounded FCM Redelivery (NEW)

Repo: **Hermes Personal Admin**

#### SPEC

Hermes bounded redelivery of recently accepted but unacknowledged
notifications via FCM:

- Redelivery eligibility query using existing columns:
  ```text
  acknowledged_at IS NULL
  AND canceled_at IS NULL
  AND run.status = 'committed'
  AND sent_at IS NOT NULL
  AND (now - sent_at) <= 6 hours
  AND (now - last_attempt_at) >= 2 hours
  ```
- Preserve `sent_at` as first FCM acceptance (never overwrite on
  redelivery).
- Reuse `send_attempts` and `last_attempt_at` for redelivery tracking.
- Redelivery copies use NORMAL FCM priority regardless of original level.
- Same `notification_id` on redelivery.
- No Hermes DB migration — uses existing columns only.
- No delivery-receipt protocol.
- Server tests for eligibility, timing bounds, priority, and idempotency.

#### PLAN

- Implement redelivery query in Hermes (alongside or integrated with the
  existing dispatcher, depending on code structure).
- Enforce 6-hour window and 2-hour minimum gap.
- Ensure `sent_at` is never overwritten on redelivery.
- Set FCM priority to NORMAL for redelivery copies.
- Add server tests: eligibility matrix, timing bounds, priority
  verification, `sent_at` preservation.

#### TASKS

- [ ] redelivery eligibility query (existing columns only)
- [ ] 6-hour window enforcement
- [ ] 2-hour minimum gap enforcement
- [ ] `sent_at` preservation (never overwrite)
- [ ] `send_attempts` / `last_attempt_at` reuse
- [ ] NORMAL FCM priority on redelivery copies
- [ ] same `notification_id` on redelivery
- [ ] server tests: eligibility, timing, priority, idempotency

#### GATE

```text
sent/unacknowledged notification redelivered within policy bounds
sent_at preserved as first acceptance
same notification_id used
NORMAL priority on redelivery copies
no DB migration
6-hour window enforced
2-hour minimum gap enforced
```

Only after GATE passes may Change G integrate against it.

---

### Change F — Ackline Remove Periodic Recovery Dependency (NEW)

Repo: **Ackline**

#### SPEC

- Cancel/retire the installed unique periodic work
  `ackline-notification-recovery-periodic`.
- Remove periodic WorkManager scheduling code.
- Remove periodic recovery configuration.
- Verify event-driven triggers remain functional.
- Verify no regression to one-time recovery paths.

#### PLAN

- Identify all code related to periodic WorkManager recovery scheduling.
- Add cancellation of the named periodic work on app start/update.
- Remove the periodic scheduling registration.
- Verify one-time recovery paths (startup, onDeletedMessages, FID change)
  are unaffected.
- Run `./gradlew assembleDebug` to confirm no compilation regression.

#### TASKS

- [ ] cancel `ackline-notification-recovery-periodic` on app start/update
- [ ] remove periodic WorkManager scheduling code
- [ ] remove periodic recovery configuration
- [ ] verify event-driven triggers unchanged
- [ ] `./gradlew assembleDebug` passes

#### GATE

```text
periodic work cancelled on app start/update
no periodic WorkManager enqueued
event-driven recovery unchanged
./gradlew assembleDebug passes
```

---

### Change G — Focused Integration QA / Docs Closeout (NEW)

Repos: **Ackline + Hermes**

#### SPEC

Focused integration QA validating the Redesign V2 acceptance path, physical
OppO matrix for the redesigned recovery flow, and Phase 7 documentation
closeout.

#### PLAN

- Controlled integration: verify Hermes redelivery reaches Ackline.
- Physical Oppo matrix for Redesign V2 acceptance path:
  - Hermes sent/unacknowledged → bounded redelivery → same notification_id
    → Room INSERT once → one native notification → duplicate harmless →
    Visto → remote ACK.
  - Event-driven recovery: startup/onDeletedMessages/FID-change triggers
    → one-time recovery → missing row inserted → notification shown.
  - ACK backlog drain after recovery.
  - FID change → re-pair → recovery flow.
- Documentation closeout: update all four docs to implemented state.

#### TASKS

- [ ] Hermes redelivery → Ackline end-to-end
- [ ] physical Oppo: redelivery acceptance matrix
- [ ] physical Oppo: event-driven recovery matrix
- [ ] duplicate FCM + redelivery harmless
- [ ] ACK backlog drain
- [ ] FID change → re-pair → recovery
- [ ] documentation closeout (all four docs)

#### GATE

```text
Redesign V2 acceptance path PASS:
  Hermes sent/unacknowledged
  → bounded FCM redelivery
  → same notification_id
  → Ackline Room INSERT once
  → one native notification
  → duplicate harmless
  → Visto
  → remote ACK

Event-driven recovery PASS:
  startup/onDeletedMessages/FID-change
  → one-time recovery
  → missing row inserted
  → notification shown

Documentation closeout:
  all four docs updated to implemented state
```

---

## 6. Dependencies and Order

```text
A before B integration  (Change B clients use the real contract)
B before C              (triggers consume recovery scheduler/core from B)
E before G              (Hermes redelivery must exist before integration QA)
F before G              (periodic removal must land before integration QA)
G after A+B+C+E+F      (reviewed and landed)
```

Change A and Change E live in the Hermes repo. Changes B, C, and F live in
the Ackline repo. Change G spans both.

---

## 7. Branch Strategy (conceptual — NOT created)

Review branches for implementation:

```text
Hermes:  7a-recovery-contract (retained)
Ackline: 7b-reconciliation-core (retained)
Ackline: 7c-triggers-fid (retained, periodic removed)
Hermes:  7e-bounded-redelivery (new)
Ackline: 7f-remove-periodic (new)
Final:   7g-integration-qa-docs (new, replaces 7d)
```

Flow per change:

```text
dev
→ change branch
→ implementation
→ validation
→ independent review
→ manual/device QA when required (Change G)
→ user commit + push
→ ChatGPT GitHub review
→ PASS
→ merge to dev
```

Branches are **not** created by this planning session.

---

## 8. Validation Commands

### Hermes (Changes A, E, D-equivalent)

Targeted Python tests for the recovery contract and redelivery logic plus
the existing server test harness. Evidence must come from executed tests,
not self-report.

### Ackline (Changes B, C, F)

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
```

### Integration/device (Change G)

Physical Oppo matrix; no single command replaces it. Run the Android gate
before starting the matrix.

---

## 9. Out of Scope / Do Not Do

- ntfy retirement (Phase 8);
- constant/aggressive polling;
- periodic WorkManager as a recovery path;
- generic bidirectional sync engine;
- Hermes business logic in Android;
- server accounts, public/cloud DB backend, Firebase Auth, Firestore;
- multi-device registry;
- foreground service, exact alarms, sockets, MQTT;
- Room encryption, key rotation;
- UX redesign, analytics SDK;
- Room migration (stay v3) and Hermes DB migration (none);
- adding `recovered_at`, server revisions, sync version, tombstones;
- automatic FID provisioning or server-side FID registry;
- reconciliation-triggered ACKing (drain only);
- delivery-receipt protocol;
- any change to `MVP_PHASES.md` beyond the approved clarification.

---

## 10. Review and Git Ownership

- Builder is not the only reviewer for medium/high-risk work (network,
  persistence, security, background scheduling).
- Final pushed-branch review through ChatGPT + GitHub ends in
  `PASS` / `PASS_WITH_NOTES` / `BLOCKED`.
- Default merge policy: merge to dev after `PASS`.
- The **user** owns commits, pushes, and merges; this plan does not commit,
  push, merge, or create branches.

---

## 11. Implementation Status

```text
Change A  Hermes Recovery Contract            SUPERSEDED by E (endpoint retained)
Change B  Android Reconciliation Core         NOT STARTED
Change C  Recovery Triggers + FID/Re-pair     NOT STARTED (periodic removed)
Change D  Integration / QA / Docs             ABORTED — DESIGN GATE FAILED
Change E  Hermes Bounded FCM Redelivery       NOT STARTED
Change F  Remove Periodic Recovery Dependency  NOT STARTED
Change G  Focused Integration QA / Docs       NOT STARTED
```

Current change: **Change E — Hermes Bounded FCM Redelivery**.
