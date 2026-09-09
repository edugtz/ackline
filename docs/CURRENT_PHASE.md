# Current Phase

## Status

**MVP IMPLEMENTATION: COMPLETE**
**PHASE 7 COMPLETE — FINAL QA PASS**
**PHASE 9 COMPLETE — PRODUCT UX ACCEPTED ON PHYSICAL OPPO**
**PHASE 8 DEFERRED — FINAL REAL-WORLD USAGE / RELIABILITY GATE (not PASS)**
**POST-MVP P2 — CURRENT ACTIVE AREA (Better Pairing / Guided Setup)**
**P2A — COMPLETE (PASS_WITH_FINDINGS — see docs/P2A_QA_RESULTS.md)**
**P2B — COMPLETE (H1 + A1 + A2 landed; P2B-QA PASS — critical physical path, owner-accepted; see docs/P2B_QA_RESULTS.md)**
**P2C — PLANNED / NOT ACTIVE**

Phase: `Post-MVP P2 — Better Pairing / Guided Setup`

Ackline docs branch: `docs-p2b-closeout` (docs-only closeout; no source changes).

Ackline base: clean `dev` — `4012532a8e146e9d2a3d82d7a6e9785885abbba7`
(`fix: add scanner accessibility semantics`).

Hermes Personal Admin is a separate repository; this docs-only Ackline change does not assert or update its current HEAD.

Phase 7 blockers: **0**

Implementation changes A/B/C/E/F/G1 are already merged. Final integration
QA (Change G) is **PASS** — the documentation closeout is recorded by this
Phase 7 documentation closeout change. Change G is integration QA/docs
closeout, not a separate runtime source merge.

```text
Change A  Hermes Recovery Contract              IMPLEMENTED / MERGED
Change B  Android Reconciliation Core           IMPLEMENTED / MERGED
Change C  Recovery Triggers + FID/Re-pair       IMPLEMENTED / MERGED
Change D  Integration / Physical QA             ABORTED — DESIGN GATE FAILED (historical, retained)
Change E  Hermes Bounded FCM Redelivery         IMPLEMENTED / REVIEWED / MERGED — PASS
Change F  Remove Periodic Recovery Dependency   IMPLEMENTED / REVIEWED / MERGED — PASS
Change G1 Explicit Tailnet HTTPS VPN Binding    IMPLEMENTED / REVIEWED / PHYSICALLY VALIDATED / MERGED — PASS
Change G  Final Integration QA / Docs Closeout  PASS — documentation closeout recorded by this documentation change
```

Current change: **docs-only P2B final closeout. No source changes.**

Current gate: **NONE — P2B is COMPLETE.** P2B-QA passed on the critical
physical product path with owner acceptance (see `docs/P2B_QA_RESULTS.md`).
P2B implementation (Hermes H1 + Ackline A1 + Ackline A2: real CameraX +
ZXing QR scanner, CAMERA permission flow, production scanner-fed pairing,
guided onboarding, server-confirmed re-pair, honor-system "Marcar como
actualizado" removed) is landed and the post-pairing encrypted-FCM →
native notification → Room exactly-once → local Visto → eventual remote
ACK path is physically proven. P2C (self-test + minimal health) remains
planned, not active.

Phase 8 — multi-day real-world Oppo usage/reliability gate — is
**DEFERRED** until Ackline has completed the selected post-MVP
setup/usability work (P2) and can be used normally. The deferral is
intentional sequencing, not a failure signal. Phase 8 is NOT marked PASS.
The `ack_server.py` lifecycle/supervision operational follow-up is resolved
(see Operational Follow-Up below).

## Post-MVP P2 — Current Active Area

P2 turns setup into a guided pairing experience: fresh install →
notification permission → scan QR → pair/provision → readiness → Inbox —
without adb, shell commands, manual FID file editing, or
Firebase/Hermes-path knowledge. Normative detail is in
`docs/P2B_SPEC.md`, `docs/P2B_PLAN.md`, and `docs/P2B_TASKS.md`; broader
roadmap context is in `docs/POST_MVP_PHASES.md`.

```text
P2A  pairing backend/protocol     COMPLETE — implemented + physically integrated (PASS_WITH_FINDINGS)
P2B  guided onboarding + re-pair  COMPLETE (H1 + A1 + A2 landed; P2B-QA PASS — see docs/P2B_QA_RESULTS.md)
P2C  self-test + minimal health   PLANNED — not active
```

Manual setup paths are legacy/debug fallback, not the normal user path:
adb E2EE staging and manual FID copy into `~/.hermes/secrets/ackline-fid`.
The honor-system "Mark as updated" action is REMOVED (P2B-A2); re-pair
goes through the same QR scanner with server confirmation. Normal setup
uses guided P2 pairing (P2B UX landed on the implemented P2A protocol).
Phase 9 visual identity is retained.

---

## P2B-QA — CLOSED (PASS — critical physical path, owner-accepted)

P2B is COMPLETE. P2B-QA passed on the critical physical product path with
owner acceptance for normal use. Full evidence: `docs/P2B_QA_RESULTS.md`.

Physically proven (critical path):

```text
real Hermes terminal QR scan; replace_required UX; replacement QR pairing;
server-confirmed pairing; Listo -> Inbox; real encrypted FCM after pairing;
delivery while Tailscale was OFF; native Android notification physically
observed; notification persisted in Ackline; duplicate delivery of same
notification_id did not create a duplicate; local Visto while Tailnet
unavailable; Hermes remained unacknowledged while Tailscale was OFF; remote
Hermes ACK succeeded after Tailscale was restored (canary
a82fc904319b4b77a6db8e498f972432).
```

Deliberately unexecuted exploratory checks (NOT blockers, NOT marked
executed — deferred to normal usage and bug-driven follow-up): exhaustive
ColorOS permission variants, repeated camera lifecycle permutations,
font-size permutations, extended accessibility/manual matrix.

P2B passes because the critical product and architecture path was physically
proven and accepted by the owner — not because every exploratory checklist
case was executed.

---

## Canonical Transport Decision — FCM Only / ntfy Rejected (CURRENT)

```text
Realtime:  Hermes Personal Admin -> encrypted FCM -> Ackline
ACK:       Ackline -> Tailnet HTTPS -> Hermes Personal Admin
Recovery:  bounded FCM redelivery + event-driven Tailnet HTTPS reconciliation
```

Ackline + FCM is the sole supported production notification path. ntfy is
architecturally REJECTED and UNSUPPORTED: not a fallback, not a rollback
option, not an alternate production transport, not roadmap. Rationale:
real-world use outside the home previously showed ntfy was not reliable
enough for these notification requirements.

Historical record stays historical: ntfy existed before the FCM cutover
(Phase 6/7 history, P2A evidence) — do not rewrite past facts. Production
Hermes still contains some legacy ntfy code at this moment; its removal is
PENDING as a dedicated Hermes cleanup change after P2B QA closeout. Do NOT
claim ntfy implementation is already deleted.

No fallback is implemented or preselected now. Ackline/FCM must first be
evaluated through real-world use (Phase 8). Only if Phase 8-or-later
evidence demonstrates unacceptable Ackline/FCM reliability does the
project open an alternative-transport investigation; candidates at that
time MAY include Pushover, Telegram Bot, or another evidence-backed
option — candidates, not selections. P9 is reframed accordingly
(see `docs/POST_MVP_PHASES.md`): TRIGGER ONLY / NOT ACTIVE; the preferred
outcome is that P9 is never needed.

Canonical near-term order:

```text
1. P2B COMPLETE (this closeout)
2. Hermes Personal Admin cleanup — remove remaining ntfy legacy code
3. P2C — self-test + minimal health
4. Phase 8 — multi-day real-world Ackline/FCM reliability gate
5. Only if reliability evidence is inadequate: evaluate alternatives
```

P1, P3, P4, P5-deep, P6, P7, P8 multi-device, and fallback transport work
are NOT active.

---

## Phase 6 Closeout Summary (HISTORY — remains CLOSED)

Phase 6 — Hermes Outbox / FCM Sender Integration was fully implemented,
validated against real Firebase and the physical Oppo, and cut over to
production. `ACTIVE_TRANSPORT = "fcm"` in `notification_state.py`; ntfy
is architecturally rejected/unsupported — remaining Hermes legacy code
removal is a dedicated cleanup after P2B QA closeout.

Hermes final merge:

```text
5b5777a827e097a98687bc6fae0060a2e6fcebb3
merge: complete Phase 6 FCM transport cutover
```

Hermes automated tests: **28/28 PASS**. Full evidence, including the
production cutover canary (`8304672d700c4056b5d456eae49b6060`) and the
forensic correction of the invalid "Stage 3-R" diagnostic, is retained in
the repository history.

Phase 6 delivery semantics survive unchanged into Phase 7:

```text
sent_at = FCM/provider accepted the transport attempt
```

It is **not** proof of device persistence, decryption, or display.

---

## Phase 7 Objective — Achieved

Make FCM the realtime path without treating **one push attempt** as the only
path to recover a pending alert.

### Product quality goal — met

> A rare missed/dropped transport event must not permanently erase a Hermes
> pending notification.

FCM remains the realtime transport. Hermes bounded redelivery and
event-driven Android recovery are the safety net — **not** periodic
WorkManager polling.

---

## Canonical Implementation State

### Final architecture

```text
Realtime:        Hermes → encrypted FCM → Ackline

Primary recent-loss safety net:
                 Hermes bounded FCM redelivery

Secondary repair:
                 event-driven GET /notifications/pending on:
                 - startup
                 - onDeletedMessages
                 - FID registration/change

ACK:             Visto local
                 → durable WorkManager
                 → explicit VPN Network HTTPS
                 → Hermes
```

```text
No periodic Android recovery.
No delivery receipt protocol.
No Hermes DB migration.
No Room migration.
ntfy is architecturally rejected/unsupported — not a fallback or rollback
path; Phase 8 tests Ackline/FCM alone.
```

### Long-offline model (implemented)

```text
FCM offline retention + bounded copies + event-driven reconciliation.
```

### Sources of truth (unchanged)

```text
Hermes SQLite = server/source-of-truth for notification state
Room          = device/source-of-truth for received alert state
FCM           = realtime transport only
tray state    = never source of truth
```

Recovery truth statements (validated in production):

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

## Change Units — Final Status and Evidence

### Change A — Hermes Recovery Contract

**RETAINED / IMPLEMENTED / MERGED.**

The `GET /notifications/pending` endpoint remains an implemented production
component. It is **not** superseded; redelivery (Change E) is a separate
Hermes component that complements it.

Hermes merge: `22e5b66aed5b372dbf5b2fd828c8a75e8d38522f`

Implemented facts (validated):

- authenticated Tailscale identity boundary (`Tailscale-User-Login`,
  fail-closed);
- read-only; `Cache-Control: no-store`; no server state mutation;
- E2EE envelopes via `fcm_sender.build_envelope(row)`; no plaintext protocol;
- recovery query: committed / unacknowledged / uncanceled
  (`run.status = 'committed'`, `acknowledged_at IS NULL`,
  `canceled_at IS NULL`);
- `sent_at` intentionally unfiltered (`sent_at` proves only provider
  acceptance, not Ackline persistence);
- deterministic ordering:
  `ORDER BY created_at ASC, notification_id ASC`;
- max 200 items with cap+1 detection; `> 200` → `HTTP 409
  {"ok": false, "error": "too_many_pending"}` — no silent truncation, no
  pagination; degraded/operator-action state, not auto-retried forever.

### Change B — Android Reconciliation Core

**IMPLEMENTED / MERGED.**

Ackline merge (landed before the Redesign V2 redesign): `de91642f2e7f12d34aa61986a1a53c745218892f`

All of the following remain the implemented reconciliation core:

- `RecoveryRunner`;
- `RecoveryWorker`;
- canonical `AlertIngestion` (shared by `FirebaseMessagingService` and
  recovery);
- HTTPS recovery client;
- Room idempotency (`insertIgnore` — INSERTED → persist + one notification;
  DUPLICATE → no overwrite, no ACK-state regression, no repost);
- ACK drain path (after a successful recovery GET, enqueue
  `AckSyncScheduler` once; recovery never ACKs manually);
- one-time work policy `ExistingWorkPolicy.KEEP`;
- failure taxonomy: transient → `Result.retry()` with exponential backoff;
  permanent/configuration → no retry loop, sanitized diagnostic, Room
  untouched; per-item decrypt/validation failure → skip item, continue
  batch, no crash, no DB regression.

### Change C — Recovery Triggers + FID/Re-pair

**IMPLEMENTED / MERGED.**

Ackline merge: `5f4d7348aa1d405f0939cfdcd09fcf719c403480`

Implemented triggers (event-driven one-time recovery):

- startup;
- `onDeletedMessages()`;
- FID registration/change.

FID/re-pair remains implemented: persistent last-observed FID
(`FidRePairStore`), `rePairRequired` semantics that survive process restart
and clear only through an explicit Setup action, and a Setup re-pair
warning. Manual provisioning only: operator copies the current FID into
`~/.hermes/secrets/ackline-fid`. No automatic provisioning.

Periodic recovery was subsequently **retired by Change F**; all three
event-driven triggers remain functional.

### Change D — Integration / Physical QA

**ABORTED — DESIGN GATE FAILED.** (HISTORICAL RECORD — retained; do not
rewrite as PASS.)

This was **not** a proven product/runtime failure and **not** a proven
RecoveryWorker bug.

The failure was architectural:

```text
periodic WorkManager should not be a critical recovery dependency.
```

Physical-device QA was aborted when Change D uncovered that the periodic
WorkManager safety net was a design dependency that should not exist.
Documentation closeout responsibilities were reassigned to Change G.

### Change E — Hermes Bounded FCM Redelivery

**IMPLEMENTED / REVIEWED / MERGED — PASS.**

Implementation commit: `61994c05a6fa7d8361c93ad941cd745d82723c80`

Hermes dev merge: `fab085d7400499353c638f93d62aa4661330aa18`

Physical evidence (real production FCM):

- lost-first synthetic canary redelivered by real production FCM;
- same `notification_id` on redelivery;
- `send_attempts` advanced `1 → 2`;
- `sent_at` preserved (never overwritten — first FCM acceptance);
- `last_attempt_at` advanced;
- **NORMAL** priority on the redelivery copy;
- Oppo persisted and notified without app opening;
- **Tailscale OFF during this test** — redelivery is pure FCM; no
  HTTPS/tailnet involvement.

Status: **PASS**.

Important scheduling nuance (do not over-promise):

- the existing Hermes scheduler cadence is **q120m**, but redelivery
  eligibility is based on `last_attempt_at >= 2h`;
- therefore a scheduler cycle can occur slightly before the strict 2h
  boundary and simply skip until the next cycle;
- do **not** promise exact +2h redelivery or exactly three copies.

### Change F — Remove Periodic Recovery Dependency

**IMPLEMENTED / REVIEWED / MERGED — PASS.**

Implementation commit: `005c40c551a9bec71ee63c9579f5a65a20cc7829`

Ackline dev merge: `7cfc9be6853d5fc826757b84ec444e29c3a0e133`

Implemented facts:

- periodic recovery scheduling removed;
- legacy unique work `ackline-notification-recovery-periodic` cancelled on
  startup/update;
- one-time recovery remains event-driven only;
- **no periodic WorkManager recovery dependency**.

Status: **PASS**.

### Change G1 — Explicit Tailnet HTTPS VPN Binding

**IMPLEMENTED / REVIEWED / PHYSICALLY VALIDATED / MERGED — PASS.**

Implementation commit: `a3c9ea07ac0f6584f42303cdba1cf722c1c5da1b`

Ackline dev merge: `b4488f9adb91985b50e052df9261fa9f4f9a20fc`

Root cause proven before fix:

```text
APP_UID_TAILNET_ROUTING_FAILURE_PROVEN
```

The default/implicit app network path could **not** reach the Mac tailnet,
while the device shell path could.

Fix:

```text
TailnetHttpsConnectionFactory
→ ConnectivityManager active VPN Network
→ vpnNetwork.openConnection(url)
```

Properties of the fix:

- no process-wide binding;
- no hardcoded Tailscale IP;
- no TLS weakening;
- no fallback to public/default network for tailnet endpoints.

Physical proof:

1. `POST /ack` — `EXPLICIT_VPN_ACK_PROVEN = YES`

   Real production path:

   ```text
   AckSyncRunner
   → HttpsAckRemoteClient
   → VPN Network.openConnection
   → Tailscale Serve
   → ack_server
   → Hermes acknowledged_at
   ```

2. `GET /notifications/pending` — `EXPLICIT_VPN_RECOVERY_PROVEN = YES`

   Fresh install:
   - Room initially empty;
   - Tailscale ON;
   - Hermes had 4 pending alerts;
   - current Hermes FCM target was stale (could not explain delivery);
   - startup recovery pulled **4/4** through HTTPS/Tailscale;
   - all decrypted and persisted;
   - WorkManager recovery succeeded;
   - `POST_NOTIFICATIONS` was **not** granted during this run, so
     native notification presentation on the recovery path was not
     separately physically exercised; those recovered rows were not used
     for a Visto→ACK end-to-end test (remote ACK over explicit VPN is
     separately proven by the G1 ACK canary, item 1 above).

### Change G — Final Integration QA

**PASS** — documentation closeout recorded by this documentation change.

Validated physical matrix (real Oppo + production Hermes):

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

Fresh-install realtime FCM proof:

```text
current FID matched Hermes config
production fcm_sender.send_notification used
Firebase accepted
Ackline received
E2EE decrypted
duplicate detected
Room unchanged
no duplicate notification
```

---

## Hermes Scheduler — Re-enabled (Operational Note)

Existing scheduler only — **no duplicate scheduler/job was created.**

```text
Job:       86c14bbbe300 — "Personal Admin"
Runtime:   Hermes built-in cron/gateway scheduler
Cadence:   every 120m (q120m)
```

- Paused since **Aug 27** during QA.
- Now **RE-ENABLED**.
- Cadence and configured path **unchanged**.

Native scheduler run executed successfully after repairing the existing
Google OAuth:

```text
Google OAuth:
- existing InstalledAppFlow mechanism used
- gmail.readonly
- calendar.readonly
- tasks.readonly
- no write scopes added
- invalid_grant repaired
```

Final native execution:

```text
scheduler job 86c14bbbe300
Google prepare succeeded
run committed
notification_state.py dispatch ACTUALLY REACHED
ACTIVE_TRANSPORT = fcm
scheduler completed successfully
q120 cadence preserved
```

### Repository distinction (do not conflate)

```text
Hermes Agent scheduler/runtime HEAD: not verified here  ← DIFFERENT repository
Hermes Personal Admin checkout b95129f71f...             ← this project's dev
```

Do **not** claim that the Personal Admin SHA must exist in hermes-agent
history.

---

## Important Limitations — Honest Claims

Do **NOT** claim:

- exact-once FCM;
- exact +2h redelivery SLA;
- indefinite autonomous recovery if every FCM attempt is lost and the app
  never starts;
- periodic WorkManager recovery;
- delivery receipt semantics;
- FID automatic provisioning.

The long-offline model remains:

```text
FCM offline retention + bounded copies + event-driven reconciliation.
```

---

## Operational Follow-Up — RESOLVED

`ack_server.py` lifecycle/supervision is **resolved**.

Supervisor: macOS system launchd LaunchDaemon
Label: `ai.hermes.personal-admin-ack`
Config: `/Library/LaunchDaemons/ai.hermes.personal-admin-ack.plist`

Process properties:
- runs as user `eduardo` via `UserName` (NOT root)
- interpreter: `/Users/eduardo/.hermes/personal-admin/.venv/bin/python`
- script: `/Users/eduardo/.hermes/personal-admin/ack_server.py`
- working directory: `/Users/eduardo/.hermes/personal-admin`
- bind remains: `127.0.0.1:2587`
- external exposure remains ONLY through existing Tailscale Serve `:8443`

Physical evidence:
- exactly one listener
- process owner `eduardo`
- PPID = system launchd
- local `/health` = 200
- Tailscale `/health` = 200
- controlled SIGTERM: old PID exited, new PID automatically spawned in ~2s,
  no kickstart, no manual restart
- obsolete user LaunchAgent removed

Status: **AUTO-RESTART / PROCESS SUPERVISION = PASS**

Honesty limitation: `RunAtLoad` is configured on the LaunchDaemon, but
actual post-reboot auto-start has not been physically verified yet; it will
be confirmed naturally on a future Mac reboot.

---

## Phase 8 — DEFERRED

Phase 8 is the **multi-day real-world Oppo replacement gate**. It is
**DEFERRED** until the MVP product experience is ready for genuine daily
use.

The already-completed Phase 7 baseline (Change G final integration QA)
remains recorded as readiness evidence, but it does not count as
executing or passing Phase 8.

It is not required to keep the development Mac running for several days
solely for a pre-product test window.

When eventually executed, Phase 8 still validates:

- Ackline + FCM is the sole notification transport under test;
- Phase 8 tests Ackline/FCM alone — no ntfy fallback or rollback;
- normal multi-day Oppo use;
- screen off / overnight;
- Wi-Fi/mobile transitions;
- ColorOS / Doze behavior;
- ACK / recovery behavior;
- natural Hermes alerts;
- if Phase 8 exposes reliability problems, the path is to improve
  Ackline/FCM or evaluate another alternative — ntfy is not an approved
  fallback.

ntfy is architecturally rejected/unsupported and is not a fallback;
remaining Hermes legacy ntfy code removal is a dedicated post-QA cleanup.

---

## Phase 7 Constraints Still in Force (retained)

- ntfy is architecturally rejected/unsupported — not a fallback; Phase 8 validates Ackline/FCM alone;
- no constant/aggressive polling;
- no periodic WorkManager as a recovery path;
- no generic bidirectional sync engine;
- no Hermes business logic in Android;
- no server accounts, public/cloud DB backend, Firebase Auth, Firestore;
- no multi-device registry;
- no foreground service, exact alarms, sockets, MQTT;
- no Room migration (schema stays v3); no Hermes DB migration;
- no `recovered_at`, server revisions, sync version, tombstones;
- no delivery-receipt protocol;
- no FID automatic provisioning or server-side FID registry;
- reconciliation never triggers ACKing (drain only).

---

## Workflow — Current Position

The Phase 7 workflow was executed in full:

```text
dev
→ 7-recovery-and-reconciliation (planning/docs)
→ planning review (Redesign V2)
→ merge planning docs to dev (827a9f9)
→ Change A/B/C/E/F/G1 implementation branches
→ implementation → validation → independent review → merge to dev
→ Change G: focused integration QA → PASS
→ documentation closeout (this session)
→ user commit + push (owned by user)
```

This documentation change records the closeout; it does not change source
code, scheduler configuration, or databases.

---

## Next Step

1. P2B is COMPLETE — no further P2B-QA gate work. Full evidence:
   `docs/P2B_QA_RESULTS.md` (critical physical path proven and
   owner-accepted; exploratory checks explicitly deferred to normal usage
   and bug-driven follow-up).
2. Phase 8 — multi-day real-world Oppo usage/reliability gate — will be
   executed after the selected post-MVP setup work (P2) is complete and
   Ackline can be used normally; Ackline/FCM is the sole transport under
   test — ntfy is rejected, not a fallback.
3. `ack_server.py` lifecycle/supervision is resolved (see Operational
   Follow-Up above).
