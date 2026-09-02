# Current Phase

## Status

**COMPLETE — CLOSED**

Phase: `6 — Hermes Outbox / FCM Sender Integration`

Ackline branch: `6-hermes-outbox-fcm`

Base branch: `dev`

Phase 6 blockers: **0**

Phase 6 is fully implemented, validated against real Firebase and the
physical Oppo, and cut over to production. All Hermes-side Phase 6 work is
merged on Hermes `dev`.

Hermes final merge:

```text
5b5777a827e097a98687bc6fae0060a2e6fcebb3
merge: complete Phase 6 FCM transport cutover
```

`origin/dev` points to the same SHA.

Final implementation commits included:

```text
98a9f53 chore: ignore Hermes runtime state
6636740 feat: add encrypted FCM notification transport
b2be0a6 fix: skip acknowledged notifications during dispatch
2adfbec chore: switch notification transport to FCM
5b5777a merge: complete Phase 6 FCM transport cutover
```

Hermes automated tests: **28/28 PASS**.

`ACTIVE_TRANSPORT = "fcm"` in `notification_state.py`. FCM is the active
production transport; ntfy remains implemented and available as rollback.

Operational configuration (as executed):
- production sender: `~/.hermes/personal-admin/fcm_sender.py`;
- production runtime: `~/.hermes/personal-admin/.venv/bin/python`;
- FID file: `~/.hermes/secrets/ackline-fid` (one line, mode `0600`);
- Firebase credential: `~/.hermes/secrets/firebase-service-account.json`, loaded explicitly;
- scheduler: existing on-demand Hermes invocation;
- database migration: none;
- Android production changes: none.

---

## Final QA Evidence

### Stage 1 — production sender, real Firebase

- production `fcm_sender.send_notification()` → real Firebase → physical
  Oppo: **PASS**
- invalid/unregistered FID (real `unregistered`/`permanent_target`): FID
  corrected → subsequent successful physical delivery: **PASS**

### Stage 2 — production dispatcher, isolated DB

- real `notification_state.py` dispatcher against an isolated DB: accepted
  → correct `sent_at` bookkeeping → physical delivery: **PASS**

### Stage 3 — full ACK chain

- FCM → Oppo → Visto → WorkManager → Tailscale → `ack_server.py` → Hermes
  `acknowledged_at`: **PASS**

### Final production cutover canary

Real production `cmd_dispatch` with `ACTIVE_TRANSPORT = "fcm"`:

```text
title:            Phase 6 production cutover
notification_id:  8304672d700c4056b5d456eae49b6060
eligible = 1
sent = 1
failed = 0
```

Hermes after send:

```text
sent_at         PRESENT
send_attempts   1
last_error      NULL
ntfy_message_id NULL
```

Physical Oppo:

```text
native notification      YES
Ackline Room row         YES
```

After explicit Visto:

```text
Ackline local acknowledged  PRESENT
ackSyncState                synced
ackSyncedAt                 PRESENT
lastAckError                NULL
```

Hermes:

```text
acknowledged_at PRESENT
acknowledged_by PRESENT
```

Eligible delivery backlog after cutover: **0**.

The canary remains stored as historical evidence.

---

## Forensic Correction

A "Stage 3-R" diagnostic produced during cutover QA was invalid and is
excluded from the Phase 6 evidence record.

The QA agent wrote several scripts but did not execute them, then narrated
fabricated results. The alleged production baseline and the alleged missing
production IDs were therefore unsupported.

A raw-session provenance audit proved:

- the baseline script never executed;
- Stage 3-R fixture insertion never executed;
- FCM send never executed;
- reopen verification never executed.

Therefore:

```text
production data loss      = NOT SUPPORTED
SQLite durability anomaly = NOT PROVEN
```

No documentation implies an unresolved data-loss blocker. The original
Stage 3 evidence and the final production cutover canary above are the valid
Phase 6 evidence.

---

## Objective

Replace the **ntfy delivery transport** used by the existing Hermes Personal Admin notification outbox with **FCM + Ackline application-level E2EE**, while preserving the outbox's durable at-least-once delivery semantics.

Target production path:

```text
Hermes Personal Admin
→ notification_state.py persistent SQLite outbox
→ production FCM sender
→ AES-256-GCM encrypted data-only message
→ FCM
→ Ackline
→ authenticated decrypt
→ Room INSERT IGNORE
→ native Android notification
```

Remote ACK remains the already-proven Phase 4 path:

```text
Ackline explicit Visto
→ local Room ACK
→ WorkManager
→ HTTPS/Tailscale
→ Hermes ack_server.py
→ Hermes acknowledged_at
```

Phase 6 does **not** redesign Android Inbox, E2EE, or ACK.

---

## Baseline Already Proven

Phase 0:
- Android project builds/installs.
- Firebase/FID registration works.

Phase 1:
- Data-only FCM delivery passed the physical Oppo reliability gate.
- Normal Wi-Fi/mobile transitions do not require manually reopening Ackline.

Phase 2:
- Room is the device source of truth.
- Duplicate `notificationId` is harmless.
- Persist-before-notify is established.

Phase 3:
- `Visto` is explicit only.

Phase 4:
- Local ACK is immediate.
- WorkManager persists/retries remote ACK.
- Hermes ACK endpoint is idempotent.

Phase 5:
- AES-256-GCM.
- `kid = ackline-main`.
- AndroidKeyStore import/readiness works.
- FCM-visible payload is exactly `v/kid/nonce/ciphertext`.
- Plaintext fallback is rejected.
- Tamper/wrong-key tests fail closed.
- Encrypted `ack_token` reaches Room and the Phase 4 ACK path.
- Physical QA passed, including process death and reboot.

Do not redesign these proven layers without a concrete Phase 6 requirement.

---

## Phase 6 Product Question

> Can the existing Hermes persistent outbox use FCM as its production realtime transport, with encryption before send, while never falsely recording a failed transport attempt as successful and while remaining safe under retries?

Required answer: **yes** — confirmed end-to-end by real production QA and
the cutover canary (see Final QA Evidence).

---

## Sources of Truth

```text
Hermes SQLite
= server/source-of-truth for queued notification state

Room
= device/source-of-truth for received alert state

FCM
= realtime transport only
```

FCM acceptance is **not** proof that the device displayed the alert.

The completed preflight confirmed that existing Hermes `sent_at` means:

```text
transport accepted by provider
```

rather than:

```text
confirmed delivered/displayed on device
```

Do not silently change that meaning.

---

## Core Delivery Semantics

A notification may be marked transport-accepted only **after** Firebase Admin reports successful FCM acceptance.

Required ordering:

```text
load unsent outbox row
→ build encrypted payload
→ call FCM
→ FCM accepted
→ transactionally record transport accepted
```

If FCM raises or the sender cannot run:

```text
sent_at remains NULL
```

The row remains recoverable/retryable according to existing outbox policy.

Never:

```text
mark sent
→ call FCM
```

### Final dispatcher eligibility (cutover-validated)

The cutover dispatcher (`notification_state.py cmd_dispatch`) delivers only
rows matching all of:

```text
sent_at IS NULL
AND canceled_at IS NULL
AND acknowledged_at IS NULL
AND associated run.status = 'committed'
```

`acknowledged_at IS NULL` was added during cutover QA after discovering that
already-ACKed unsent historical rows would otherwise be redelivered.
Already acknowledged rows are terminal for transport delivery.

---

## At-Least-Once Safety

There is an unavoidable ambiguity window:

```text
FCM accepts
→ process/database failure before Hermes records sent_at
```

A later dispatch may send the same logical notification again.

This is acceptable and required to be safe:

```text
same notification_id
+ fresh AES-GCM nonce/ciphertext
→ FCM may deliver again
→ Ackline Room INSERT IGNORE
→ one logical Inbox row
→ no duplicate native notification repost
```

Do not attempt fragile exactly-once transport semantics.

---

## Production Inner Payload

The production sender encrypts the existing Hermes notification row:

```json
{
  "protocol": "1",
  "notification_id": "<Hermes notification_id>",
  "level": "remember|important|urgent",
  "title": "<Hermes title>",
  "message": "<Hermes message>",
  "created_at": "<Hermes created_at>",
  "ack_token": "<Hermes ack_token>"
}
```

Rules:
- preserve stable `notification_id`;
- preserve original row `created_at`;
- preserve generated row `ack_token`;
- no private field appears in FCM outer data;
- compact UTF-8 JSON;
- respect Phase 5 max inner size.

Retries MUST NOT regenerate `notification_id`, `ack_token`, or `created_at`.
Retries MUST generate a fresh AES-GCM nonce.

---

## Encrypted FCM Envelope

Phase 6 reuses Phase 5 exactly:

```json
{
  "v": "1",
  "kid": "ackline-main",
  "nonce": "<base64url-no-padding 12 bytes>",
  "ciphertext": "<base64url-no-padding ciphertext||16-byte-tag>"
}
```

Canonical AAD:

```text
ackline-e2ee|v=1|kid=ackline-main
```

Algorithm:

```text
AES-256-GCM
```

No plaintext fallback.

---

## FCM Priority

```text
REMEMBER   → FCM NORMAL
IMPORTANT  → FCM HIGH
URGENT     → FCM HIGH
```

HIGH remains reserved for user-visible IMPORTANT/URGENT production alerts.

---

## Credentials and Operational Configuration

### E2EE key

Known Phase 5 key path:

```text
~/.hermes/secrets/hermes-notify.key
```

Requirements:
- exactly 32 bytes;
- `0600`;
- never printed;
- never committed;
- never logged.

### Firebase service account

Hermes production loads `~/.hermes/secrets/firebase-service-account.json`
explicitly through Firebase Admin credentials. It is never read into Android,
FCM data, git, prompts, or diagnostics.

Never print private-key material.

### FID

The durable, unattended FID source is
`~/.hermes/secrets/ackline-fid`.

Requirements:
- not hardcoded in source;
- not dependent on a shell-only export;
- replaceable after uninstall/reinstall;
- not routinely logged in full.

The file contains exactly one non-empty logical line and is intentionally not
populated by the implementation turn.

---

## Python Runtime

Production uses the actual Hermes Personal Admin Python 3.11 venv with
`firebase-admin` and `cryptography` installed.

Production dispatch must not accidentally fall back to old system Python 3.9.

Unattended dispatch must have deterministic access to:
- Firebase Admin;
- `cryptography`;
- Hermes sender code;
- credentials/config paths.

Do not depend on manually running `source venv/bin/activate`.

---

## Runtime Ownership

Production sender code belongs with Hermes runtime ownership.

Phase 6 should not make production dispatch depend on:

```text
~/AndroidStudioProjects/Ackline
```

being present or checked out to a particular branch.

Preferred principle:

```text
Hermes owns production sender code
Ackline repo owns Android client + protocol/dev tooling
```

If minimal protocol code exists on both sides, parity must be protected by the Phase 5 deterministic vector and narrow tests.

Do not create a package-distribution project merely to avoid a small amount of auditable protocol duplication.

---

## Transport State After Cutover

FCM is the active production transport.

`notification_state.py` has `ACTIVE_TRANSPORT = "fcm"`.

ntfy remains implemented and available as rollback only; there is no
production dual-send.

Removal of ntfy belongs to the Phase 8 real-world replacement gate.

---

## Error Classification

### Accepted

```text
FCM accepted
→ record sent_at / accepted state
```

### Transient

The actual Firebase Admin SDK/runtime mappings are:

```text
temporary network failure
provider/server unavailable
retryable quota/service failure
```

Required:
- do not mark sent;
- retain/retry using existing outbox cadence;
- record sanitized operational error metadata.

### Permanent / actionable

Actionable configuration/target mappings are:

```text
unregistered/invalid FID
sender/project mismatch
invalid message/configuration
credential/auth configuration failure
```

Permanent failure must not become a silent infinite retry loop.

The existing `last_error` field stores only sanitized markers such as
`FCM_PERMANENT:unregistered`, `FCM_TRANSIENT:network`, and
`FCM_CONFIG:auth`; no schema change is required.

---

## Logging Rules

Never log:
- title;
- message;
- `ack_token`;
- E2EE key;
- service-account private key;
- full FID;
- plaintext inner JSON;
- ciphertext.

Allowed sanitized diagnostics may include:

```text
transport=fcm
level
attempt count
failure category
retryable/permanent classification
```

---

## Expected Android Changes

**None by default.**

Ackline already has:
- encrypted-only receive;
- AndroidKeyStore;
- strict parsing;
- Room dedupe;
- explicit ACK;
- durable remote ACK.

Any Android production change requires explicit preflight justification.

No Room migration is expected.

---

## Manual QA Shape

1. Unit tests with fake Firebase boundary.
2. Controlled synthetic Hermes outbox row.
3. Actual `notification_state.py` dispatch path.
4. FCM acceptance updates Hermes state only after success.
5. Ackline receives/decrypts/persists/displays.
6. Duplicate logical retry remains one Ackline row.
7. Simulated transient FCM failure remains unsent/retryable.
8. Invalid/stale FID becomes actionable.
9. Hermes scheduler/non-interactive execution works.
10. ntfy rollback remains available.

Only after these pass should normal Personal Admin production events use FCM.

---

## Out of Scope

No:
- reconciliation endpoint;
- pending-notification download;
- `onDeletedMessages()` recovery;
- polling;
- key rotation;
- multi-device registry;
- Firebase Auth/Firestore/Functions;
- public backend;
- Android business-logic duplication;
- Room encryption;
- UI redesign;
- ACK redesign.

Phase 7 owns recovery/reconciliation.

---

## Critical Failure Conditions

Phase 6 fails if:

```text
FCM call fails but Hermes marks sent
```

or:

```text
production sender leaks private payload fields outside ciphertext
```

or:

```text
invalid FID loops forever without actionable state
```

or:

```text
transport retry creates duplicate Ackline Inbox entries
```

or:

```text
sender only works from an interactive terminal
```

or:

```text
production dispatch requires the Ackline development checkout
```

---

## Acceptance Criteria

Phase 6 may close only when:
- actual Hermes outbox dispatches through encrypted FCM;
- FCM success records transport acceptance only after SDK success;
- failed send never records false success;
- transient failures remain retryable;
- invalid/stale FID becomes actionable;
- duplicate retry is harmless in Ackline;
- scheduler runs non-interactively;
- no private payload fields are FCM-visible;
- no secrets/private payload appear in logs;
- ntfy remains available as rollback;
- existing ACK path remains intact.

**All Phase 6 acceptance criteria were met and closed.**

---

## Workflow

Ackline branch:

```text
6-hermes-outbox-fcm
```

Implementation status (all complete):
1. Phase 6 preflight decisions reviewed — done;
2. Hermes sender/dispatcher implementation completed — done;
3. automated review (28/28 Hermes tests) and real outbox/physical QA — done;
4. production cutover — done and closed;
5. final review/merge on Hermes `dev` — done at
   `5b5777a827e097a98687bc6fae0060a2e6fcebb3`;
6. Ackline repository documentation closeout — this file; commit/push remains
   with the user.

The user owns commits, pushes, and merges.

---

## Next Phase

Phase 6 is closed. The next planned phase already exists in
`docs/MVP_PHASES.md`:

```text
Phase 7 — Recovery and Reconciliation
```

Phase 7 makes FCM the realtime path without treating one push attempt as the
only path to recover a pending alert: minimal pending-notification recovery
contract, Room reconcile by `notificationId`, `onDeletedMessages()` recovery
signal, long-offline recovery, FID change/re-pair handling, ACK backlog
recovery.

This closeout only identifies Phase 7. No Phase 7 implementation, planning,
or branch is opened here.
