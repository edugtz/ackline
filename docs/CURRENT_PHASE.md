# Current Phase

## Status

**IMPLEMENTED — AUTOMATED REVIEW/QA PENDING**

Phase: `6 — Hermes Outbox / Sender Integration`

Proposed Ackline branch: `6-hermes-outbox-fcm`

Base branch: `dev`

Phase 5 has been merged to `dev` and is the frozen privacy/reliability baseline for this phase.

The Phase 6 preflight decisions are approved and implemented on the Hermes
`phase-6-fcm` branch. Real-FCM and physical QA remain pending.

Approved operational decisions:
- production sender: `~/.hermes/personal-admin/fcm_sender.py`;
- production runtime: `~/.hermes/personal-admin/.venv/bin/python`;
- FID file: `~/.hermes/secrets/ackline-fid` (one line, mode `0600`);
- Firebase credential: `~/.hermes/secrets/firebase-service-account.json`, loaded explicitly;
- scheduler: existing on-demand Hermes invocation;
- database migration: none;
- Android production changes: none;
- active implementation/QA transport default: `ntfy`; FCM is selected only for controlled QA.

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

Required answer: **yes**.

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

## ntfy During Phase 6

Do not delete ntfy at phase start.

Expected cutover model:

```text
one active transport selected explicitly
+ ntfy retained as rollback
```

Avoid production dual-send by default.

The transport selector is in `notification_state.py`. Its default remains
`ACTIVE_TRANSPORT = "ntfy"`; controlled QA may switch it to `"fcm"`, and
rollback returns it to `"ntfy"`.

Removal of ntfy belongs to the later real-world replacement gate.

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

---

## Workflow

Proposed Ackline branch:

```text
6-hermes-outbox-fcm
```

Implementation status:
1. Phase 6 preflight decisions reviewed;
2. Hermes sender/dispatcher implementation completed;
3. automated review and controlled outbox/physical QA pending;
4. user commit/push;
5. final GitHub review;
6. merge to `dev`.

The user owns commits, pushes, and merges.
