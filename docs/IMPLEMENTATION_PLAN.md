# Phase 7 — Recovery and Reconciliation Implementation Plan (Redesign V2)

## 1. Status

**IMPLEMENTATION COMPLETE — ALL CHANGES LANDED, REVIEWED, MERGED — FINAL QA PASS**

Phase: `7 — Recovery and Reconciliation`

Ackline branch: `dev` — HEAD `b4488f9adb91985b50e052df9261fa9f4f9a20fc`

Hermes Personal Admin branch: `dev` — HEAD
`fab085d7400499353c638f93d62aa4661330aa18`

Phase 6 remains **COMPLETE — CLOSED** (Hermes merge
`5b5777a827e097a98687bc6fae0060a2e6fcebb3`, Hermes tests 28/28 PASS,
`ACTIVE_TRANSPORT = "fcm"`).

Phase 7 blockers: **0**.

All Phase 7 change units (A, B, C, E, F, G1) are implemented, reviewed,
and merged to `dev`. Change G is integration QA/docs closeout, not a
separate runtime source merge. Change D remains **ABORTED — DESIGN GATE
FAILED** (historical record, retained). Final integration QA (Change G) is
**PASS** — documentation closeout recorded by this documentation change.

Final status:

```text
Change A  Hermes Recovery Contract              IMPLEMENTED / MERGED
Change B  Android Reconciliation Core           IMPLEMENTED / MERGED
Change C  Recovery Triggers + FID/Re-pair       IMPLEMENTED / MERGED
Change D  Integration / Physical QA             ABORTED — DESIGN GATE FAILED (historical)
Change E  Hermes Bounded FCM Redelivery         IMPLEMENTED / REVIEWED / MERGED — PASS
Change F  Remove Periodic Recovery Dependency   IMPLEMENTED / REVIEWED / MERGED — PASS
Change G1 Explicit Tailnet HTTPS VPN Binding    IMPLEMENTED / REVIEWED / PHYSICALLY VALIDATED / MERGED — PASS
Change G  Focused Integration QA / Docs         PASS — docs closeout executed
```

---

## 2. Redesign V2 Context (HISTORICAL RECORD — retained)

Phase 7 Change D uncovered a **DESIGN failure**, not a proven
RecoveryWorker bug. The periodic WorkManager safety net was a design
dependency that should not exist. This was a planning conclusion, not a
product or runtime failure, and not a proven RecoveryWorker bug. Periodic
WorkManager recovery is retired by Change F; Hermes bounded redelivery is
the primary recent-loss safety net.

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

## 4. Approved Architecture Contract (Redesign V2 — IMPLEMENTED)

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

### Hermes bounded redelivery (IMPLEMENTED)

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

**IMPLEMENTED / MERGED.** The `GET /notifications/pending` endpoint
remains an implemented production component; it is **not** superseded.
Redelivery of sent/unacknowledged notifications is implemented separately
in Change E.

Hermes merge: `22e5b66aed5b372dbf5b2fd828c8a75e8d38522f`

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

- [x] recovery query + deterministic ordering
- [x] endpoint route + identity boundary + headers
- [x] E2EE envelope responses
- [x] cap 200 / HTTP 409 semantics
- [x] server tests
- [x] no plaintext/secret logging verification

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

**IMPLEMENTED / MERGED** — landed before the Redesign V2 redesign.
`RecoveryRunner`, `RecoveryWorker`, canonical `AlertIngestion`, HTTPS
recovery client, Room idempotency, and the ACK drain path all remain the
implemented core.

Ackline merge: `de91642f2e7f12d34aa61986a1a53c745218892f` ("add Phase 7
notification reconciliation core")

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

- [x] `AlertIngestion` extraction (push path unchanged)
- [x] HTTPS recovery client (timeouts, no-store, sanitized errors)
- [x] `RecoveryRunner` / `RecoveryWorker` + retry/backoff policy
- [x] one-time unique scheduling (`ExistingWorkPolicy.KEEP`)
- [x] idempotent Room ingestion (INSERTED → notify; DUPLICATE → nothing)
- [x] ACK drain enqueue after successful GET
- [x] unit tests: taxonomy, dedupe, ACK-state preservation

#### GATE

```text
missing alert inserts once
duplicate harmless
ACK state not regressed
failure taxonomy proven
```

---

### Change C — Recovery Triggers + FID/Re-pair

**IMPLEMENTED / MERGED.** Startup, `onDeletedMessages`, and FID
registration/change one-time recovery remain implemented. The periodic
WorkManager trigger was removed by Change F.

Ackline merge: `5f4d7348aa1d405f0939cfdcd09fcf719c403480` ("complete Phase 7 recovery triggers and FID
re-pair flow")

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

- [x] `onDeletedMessages` trigger
- [x] startup trigger
- [x] FID change trigger
- [x] FID persistence + `rePairRequired` semantics (restart-proof)
- [x] Setup warning + explicit clear action
- [x] unit tests: trigger determinism, restart persistence of
      `rePairRequired`

#### GATE

```text
recovery can occur without manual app open (event-driven)
trigger scheduling deterministic
FID change actionable
```

---

### Change D — Integration / Physical QA / Docs

**ABORTED — DESIGN GATE FAILED.** (HISTORICAL RECORD — retained. Not a
product/runtime failure, not a proven RecoveryWorker bug.)

Repos: **Ackline + Hermes**

Physical-device QA was aborted when Change D uncovered that the periodic
WorkManager safety net was a design dependency that should not exist. This
is a planning/design conclusion, **not** a product or runtime failure.

No code defects were proven. The periodic WorkManager path was not
validated in production and is now retired by design.

Documentation closeout responsibilities are reassigned to Change G.

---

### Change E — Hermes Bounded FCM Redelivery (IMPLEMENTED)

**IMPLEMENTED / REVIEWED / MERGED — PASS.**

Implementation commit: `61994c05a6fa7d8361c93ad941cd745d82723c80`

Hermes dev merge: `fab085d7400499353c638f93d62aa4661330aa18`

Physical evidence: lost-first synthetic canary redelivered by real
production FCM — same `notification_id`, `send_attempts` 1→2, `sent_at`
preserved, `last_attempt_at` advanced, NORMAL priority; Oppo persisted and
notified without app opening; Tailscale OFF during this test.

Scheduling nuance: Hermes scheduler cadence is q120m while eligibility is
`last_attempt_at >= 2h`, so a scheduler cycle can occur slightly before the
strict 2h boundary and skip until the next cycle. No exact +2h redelivery
SLA; no guarantee of exactly three copies.

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

- [x] redelivery eligibility query (existing columns only)
- [x] 6-hour window enforcement
- [x] 2-hour minimum gap enforcement
- [x] `sent_at` preservation (never overwrite)
- [x] `send_attempts` / `last_attempt_at` reuse
- [x] NORMAL FCM priority on redelivery copies
- [x] same `notification_id` on redelivery
- [x] server tests: eligibility, timing, priority, idempotency

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

### Change F — Ackline Remove Periodic Recovery Dependency (IMPLEMENTED)

**IMPLEMENTED / REVIEWED / MERGED — PASS.**

Implementation commit: `005c40c551a9bec71ee63c9579f5a65a20cc7829`

Ackline dev merge: `7cfc9be6853d5fc826757b84ec444e29c3a0e133`

Implemented facts: periodic recovery scheduling removed; legacy unique work
`ackline-notification-recovery-periodic` cancelled on startup/update;
one-time recovery remains event-driven; no periodic WorkManager recovery
dependency. `./gradlew assembleDebug` passes.

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

- [x] cancel `ackline-notification-recovery-periodic` on app start/update
- [x] remove periodic WorkManager scheduling code
- [x] remove periodic recovery configuration
- [x] verify event-driven triggers unchanged
- [x] `./gradlew assembleDebug` passes

#### GATE

```text
periodic work cancelled on app start/update
no periodic WorkManager enqueued
event-driven recovery unchanged
./gradlew assembleDebug passes
```

---

### Change G1 — Explicit Tailnet HTTPS VPN Binding (IMPLEMENTED)

**IMPLEMENTED / REVIEWED / PHYSICALLY VALIDATED / MERGED — PASS.**

Implementation commit: `a3c9ea07ac0f6584f42303cdba1cf722c1c5da1b`

Ackline dev merge: `b4488f9adb91985b50e052df9261fa9f4f9a20fc`

Repo: **Ackline**

#### Root cause (proven before fix)

`APP_UID_TAILNET_ROUTING_FAILURE_PROVEN` — the default/implicit app
network path could not reach the Mac tailnet, while the device shell path
could.

#### Fix

`TailnetHttpsConnectionFactory` → `ConnectivityManager` active VPN
`Network` → `vpnNetwork.openConnection(url)`. No process-wide binding, no
hardcoded Tailscale IP, no TLS weakening, no fallback to public/default
network for tailnet endpoints.

#### Physical proof

- `POST /ack` — `EXPLICIT_VPN_ACK_PROVEN = YES`:
  `AckSyncRunner → HttpsAckRemoteClient → VPN Network.openConnection →
  Tailscale Serve → ack_server → Hermes acknowledged_at`.
- `GET /notifications/pending` — `EXPLICIT_VPN_RECOVERY_PROVEN = YES`:
  fresh install, Room initially empty, Hermes FCM target stale, 4 server
  pending alerts; startup recovery pulled 4/4 via HTTPS/Tailscale; all
  decrypted and persisted; WorkManager recovery succeeded; no FCM delivery
  could explain these rows.

---

### Change G — Focused Integration QA / Docs Closeout (COMPLETE)

**PASS** — documentation closeout recorded by this documentation change.

Repos: **Ackline + Hermes**

#### SPEC

Focused integration QA validating the Redesign V2 acceptance path, physical
Oppo matrix for the redesigned recovery flow, and Phase 7 documentation
closeout. All executed.

Validated physical matrix:

```text
bounded FCM redelivery            PASS
local Visto while Tailscale OFF   PASS
remote ACK over explicit VPN      PASS
recovery GET over explicit VPN    PASS
fresh-install recovery            PASS
FID re-pair performed manually    PASS
fresh-install realtime FCM        PASS
duplicate realtime FCM ignored    PASS
duplicate / idempotency           PASS
```

Fresh-install realtime FCM proof: current FID matched Hermes config;
production `fcm_sender.send_notification` used; Firebase accepted; Ackline
received; E2EE decrypted; duplicate detected; Room unchanged; no duplicate
notification.

Recovery-path scope (component-level evidence — no overclaim):

- Event-driven recovery transport + ingestion: **PASS** — Room initially
  empty, Tailscale ON, Hermes had 4 pending alerts, startup recovery GET
  executed through the explicit VPN Network, 4/4 envelopes decrypted and
  persisted, WorkManager recovery succeeded. FCM could not explain
  delivery because the Hermes target FID was stale.
- Native notification presentation on the recovery path: **not
  separately physically exercised** in this fresh-install run because
  `POST_NOTIFICATIONS` was not granted; the canonical `AlertIngestion`
  path is shared with the separately proven FCM path.
- Remote ACK: **separately physically proven by the G1 ACK canary**; the
  four recovered rows were not used for a Visto→ACK end-to-end test.
- ACK sync after recovery: a successful `RecoveryRunner` enqueues
  `AckSyncScheduler`; after fresh recovery, actual ack-sync workers ran
  and **SUCCEEDED**, but the fresh Room contained no locally
  acknowledged pending ACK backlog to drain — a non-empty physical
  backlog was not separately exercised in Change G.
- FID: uninstall/reinstall generated a new FID and erased
  `FidRePairStore`, so the new FID became the fresh-install baseline;
  FID-registration recovery executed and succeeded; the operator manually
  updated `~/.hermes/secrets/ackline-fid`; subsequent production FCM to
  the new installation PASS. In-place changed-FID warning semantics
  (later-FID-change → `rePairRequired = true`) were **not** forced
  physically during final QA; they remain covered by unit tests.

#### PLAN

- Controlled integration: verify Hermes redelivery reaches Ackline.
- Physical Oppo matrix for Redesign V2 acceptance path:
  - Hermes sent/unacknowledged → bounded redelivery → same notification_id
    → Room INSERT once → one native notification → duplicate harmless →
    Visto → remote ACK. (PASS — bounded redelivery canary)
  - Event-driven recovery: startup/onDeletedMessages/FID-change triggers
    → one-time recovery → missing row inserted (fresh-install 4/4).
    Native notification presentation on the recovery path was not
    separately physically exercised (`POST_NOTIFICATIONS` not granted);
    canonical `AlertIngestion` is shared with the separately proven FCM
    path.
  - ACK backlog drain after recovery: behavior covered by
    implementation/tests. After fresh recovery, actual ack-sync workers
    ran and succeeded, but the fresh Room had no locally acknowledged
    pending backlog to drain; a non-empty physical backlog was not
    separately exercised in Change G.
  - FID change → re-pair → recovery: fresh-install FID registration +
    manual Hermes re-pair + recovery (PASS). In-place changed-FID
    warning semantics were not forced physically during final QA; they
    remain covered by unit tests.
- Documentation closeout: update all four docs to implemented state.

#### TASKS

- [x] Hermes redelivery → Ackline end-to-end
- [x] physical Oppo: redelivery acceptance matrix
- [x] physical Oppo: event-driven recovery matrix (transport + ingestion;
      presentation via canonical shared path, not separately forced)
- [x] duplicate FCM + redelivery harmless
- [x] recovery success enqueues ACK sync; worker execution observed
- [x] fresh-install FID registration + manual Hermes re-pair + recovery
- [x] documentation closeout (all four docs)

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

Event-driven recovery PASS (component-level):
  startup/onDeletedMessages/FID-change
  → one-time recovery executes without manual app open
  → missing row inserted (fresh-install 4/4)

Recovery-path presentation:
  not separately physically exercised in this fresh-install run
  (POST_NOTIFICATIONS not granted); canonical AlertIngestion is shared
  with the separately proven FCM path.

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

Change A and Change E live in the Hermes repo. Changes B, C, F, and G1 live
in the Ackline repo. Change G spans both.

All dependencies were fulfilled; every change landed and merged to `dev`
(Ackline `b4488f9…`, Hermes Personal Admin `fab085d…`).

---

## 7. Branch Strategy (HISTORICAL — executed and merged)

Review branches for implementation (all merged to `dev`):

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

Branches were created/reviewed/merged according to this flow; the
planning session itself created none. Branch deletion state is not
asserted.

---

## 8. Validation Commands (executed — see per-change evidence in §5)

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

- ntfy is legacy/disabled — not a fallback; Phase 8 validates Ackline/FCM alone;
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

## 11. Final Implementation Status

```text
Change A  Hermes Recovery Contract              IMPLEMENTED / MERGED
Change B  Android Reconciliation Core           IMPLEMENTED / MERGED
Change C  Recovery Triggers + FID/Re-pair       IMPLEMENTED / MERGED
Change D  Integration / QA / Docs               ABORTED — DESIGN GATE FAILED (historical)
Change E  Hermes Bounded FCM Redelivery         IMPLEMENTED / REVIEWED / MERGED — PASS
Change F  Remove Periodic Recovery Dependency   IMPLEMENTED / REVIEWED / MERGED — PASS
Change G1 Explicit Tailnet HTTPS VPN Binding    IMPLEMENTED / REVIEWED / PHYSICALLY VALIDATED / MERGED — PASS
Change G  Focused Integration QA / Docs         PASS — docs closeout executed
```

Current change: **none — Phase 7 implementation is complete.**

---

## 12. Post-Implementation Operating State

- Hermes scheduler job `86c14bbbe300` ("Personal Admin", Hermes built-in
  cron/gateway scheduler, cadence q120m) was paused since Aug 27 during QA
  and is **re-enabled**; cadence and configured path unchanged; no
  duplicate scheduler/job created. A native scheduler run completed
  successfully after repairing the existing Google OAuth
  (`InstalledAppFlow`, `gmail.readonly`, `calendar.readonly`,
  `tasks.readonly`, no write scopes added, `invalid_grant` repaired);
  `notification_state.py` dispatch was reached with `ACTIVE_TRANSPORT =
  "fcm"`; scheduler completed successfully with q120 cadence preserved.
- **Repository distinction:** Hermes Agent scheduler/runtime HEAD
  `96ed0e71ea` and Hermes Personal Admin checkout `fab085d7400…` are
  DIFFERENT repositories; do not claim `fab085d` exists in hermes-agent
  history.
- **Operational follow-up — RESOLVED:** `ack_server.py` lifecycle/supervision
  is resolved via macOS system launchd LaunchDaemon
  (`ai.hermes.personal-admin-ack`). Runs as user `eduardo` (not root),
  binds `127.0.0.1:2587`, external exposure through Tailscale Serve `:8443`.
  Controlled SIGTERM confirmed auto-restart in ~2s. RunAtLoad is configured
  but post-reboot auto-start has not been physically verified yet.
- **Honest claims:** do not claim exact-once FCM, an exact +2h redelivery
  SLA, indefinite autonomous recovery when every FCM attempt is lost and
  the app never starts, periodic WorkManager recovery, delivery-receipt
  semantics, or FID automatic provisioning. Long-offline model: FCM
  offline retention + bounded copies + event-driven reconciliation.
