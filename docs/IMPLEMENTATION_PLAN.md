# Phase 7 — Recovery and Reconciliation Implementation Plan

## 1. Status

**ACTIVE — PLANNING COMPLETE — READY FOR CHANGE A**

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
Change A — Hermes Recovery Contract
NOT STARTED
```

---

## 2. Objective

Make FCM the realtime path without treating one push attempt as the only
path to recover a pending alert.

Product quality goal:

> A rare missed/dropped transport event must not permanently erase a Hermes
> pending notification.

The recovery path is a bounded safety net — not constant polling, not a sync
engine.

---

## 3. Approved Architecture Contract (Phase 7 decisions)

These decisions are final for the phase unless a change unit uncovers a
concrete correctness requirement, which must be reported before editing.

### Recovery query

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

### Triggers

```text
A. onDeletedMessages()                     → unique one-time recovery
B. AcklineApplication startup              → unique one-time recovery
C. FID registration/change                 → unique one-time recovery
D. periodic WorkManager, every 2 hours     → NetworkType.CONNECTED
```

No foreground service, AlarmManager, exact alarms, sockets, or MQTT.
The 2-hour fallback is the bounded backstop for a fully missed transport
event without user app interaction.

Periodic cadence semantics (approved):

- 2 hours is the requested/nominal periodic cadence, not a bound on
  execution time.
- WorkManager execution is inexact and OS-managed; Doze/OEM background
  restrictions may delay execution.
- 2 hours is **NOT** a recovery SLA.
- Acceptance requirement: eventual recovery without manually opening
  Ackline, not recovery within 2 hours.

### Work policy

One-time recovery: `ExistingWorkPolicy.KEEP` (a new trigger must not cancel
an already queued/retrying recovery and reset its backoff). Periodic:
unique periodic work.

### Failure taxonomy

- Transient (`network`, `DNS`, `TLS`, `timeout`, `IOException`, `HTTP 408`,
  `HTTP 429`, `HTTP 5xx`) → `Result.retry()` for both one-time and periodic
  workers; exponential WorkManager backoff; never convert a transient
  periodic failure to success just to wait for the next period.
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
- Hermes DB: **no migration**; recovery derives from existing columns.

---

## 4. Change Units

Each change is a separate reviewable unit with SPEC / PLAN / TASKS / GATE.

---

### Change A — Hermes Recovery Contract

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

Repo: **Ackline**

#### SPEC

- `onDeletedMessages()` → unique one-time recovery.
- `AcklineApplication` startup → unique one-time recovery.
- FID registration/change → unique one-time recovery.
- Periodic WorkManager safety net: 2 hours, `NetworkType.CONNECTED`, unique
  periodic work.
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
- [ ] 2-hour periodic work (unique, CONNECTED)
- [ ] FID persistence + `rePairRequired` semantics (restart-proof)
- [ ] Setup warning + explicit clear action
- [ ] unit tests: trigger determinism, restart persistence of
      `rePairRequired`

#### GATE

```text
recovery can occur without manual app open
trigger scheduling deterministic
FID change actionable
```

---

### Change D — Integration / Physical QA / Docs

Repos: **Ackline + Hermes**

#### SPEC

Controlled end-to-end integration of Changes A+B+C, physical Oppo matrix,
and Phase 7 documentation closeout.

#### PLAN

- Controlled integration: live `GET /notifications/pending` against real
  Hermes/Tailscale with synthetic pending rows.
- Physical Oppo matrix: long-offline/drop simulation, `sent_at` PRESENT
  recovery, ACK backlog drain, duplicate FCM, reboot, Tailscale
  outage/recovery, FID change/re-pair flow.
- Closeout: update `docs/CURRENT_PHASE.md`, `docs/IMPLEMENTATION_PLAN.md`,
  `docs/ARCHITECTURE.md` to the implemented state (remove "planned"
  labels), keep `docs/MVP_PHASES.md` consistent.

#### TASKS

- [ ] live integration harness (both repos)
- [ ] physical Oppo matrix incl. decision 13 long-offline gate
- [ ] duplicate FCM + ACK backlog + reboot cases
- [ ] Tailscale outage/recovery case
- [ ] FID change → re-pair → recovery case
- [ ] documentation closeout

#### GATE

```text
all Phase 7 roadmap gates PASS
```

---

## 5. Dependencies and Order

```text
A before B integration  (Change B clients use the real contract)
B before C              (triggers consume recovery scheduler/core from B)
D after A+B+C           (reviewed and landed)
```

Change A lives in the Hermes repo; Changes B and C live in the Ackline repo.
They proceed on independent review tracks; Change D spans both.

---

## 6. Branch Strategy (conceptual — NOT created)

Review branches for implementation:

```text
Hermes:  7a-recovery-contract
Ackline: 7b-reconciliation-core
Ackline: 7c-recovery-triggers-fid
Final:   7d-recovery-qa-docs
```

Flow per change:

```text
dev
→ change branch
→ implementation
→ validation
→ independent review
→ manual/device QA when required (Change D)
→ user commit + push
→ ChatGPT GitHub review
→ PASS
→ merge to dev
```

Branches are **not** created by this planning session.

---

## 7. Validation Commands

### Hermes (Changes A, D)

Targeted Python tests for the recovery contract plus the existing server
test harness. Evidence must come from executed tests, not self-report.

### Ackline (Changes B, C)

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
```

### Integration/device (Change D)

Physical Oppo matrix; no single command replaces it. Run the Android gate
before starting the matrix.

---

## 8. Out of Scope / Do Not Do

- ntfy retirement (Phase 8);
- constant/aggressive polling;
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
- any change to `MVP_PHASES.md` beyond the approved clarification.

---

## 9. Review and Git Ownership

- Builder is not the only reviewer for medium/high-risk work (network,
  persistence, security, background scheduling).
- Final pushed-branch review through ChatGPT + GitHub ends in
  `PASS` / `PASS_WITH_NOTES` / `BLOCKED`.
- Default merge policy: merge to dev after `PASS`.
- The **user** owns commits, pushes, and merges; this plan does not commit,
  push, merge, or create branches.

---

## 10. Implementation Status

```text
Change A  Hermes Recovery Contract            NOT STARTED
Change B  Android Reconciliation Core         NOT STARTED
Change C  Recovery Triggers + FID/Re-pair     NOT STARTED
Change D  Integration / QA / Docs             NOT STARTED
```

Current change: **Change A — Hermes Recovery Contract**.