# Phase 6 — Hermes Outbox / Sender Integration Implementation Plan

## 1. Status

**COMPLETE — CLOSED**

Phase: `6 — Hermes Outbox / FCM Sender Integration`

Ackline branch: `6-hermes-outbox-fcm`

Base: `dev`

The Phase 6 plan was executed in full and the phase is closed.

Phase 6 final result:

```text
Hermes merge SHA:        5b5777a827e097a98687bc6fae0060a2e6fcebb3
merge message:           merge: complete Phase 6 FCM transport cutover
Hermes automated tests:  28/28 PASS
ACTIVE_TRANSPORT:        fcm
```

Final implementation commits:

```text
98a9f53 chore: ignore Hermes runtime state
6636740 feat: add encrypted FCM notification transport
b2be0a6 fix: skip acknowledged notifications during dispatch
2adfbec chore: switch notification transport to FCM
5b5777a merge: complete Phase 6 FCM transport cutover
```

### Executed decisions

- Hermes owns `~/.hermes/personal-admin/fcm_sender.py`.
- `notification_state.py` remains the dispatcher and database owner.
- Production Python is `~/.hermes/personal-admin/.venv/bin/python` with
  `firebase-admin` installed.
- FID is read from `~/.hermes/secrets/ackline-fid`.
- Firebase credentials are loaded explicitly from
  `~/.hermes/secrets/firebase-service-account.json`.
- Existing on-demand Hermes invocation remains the scheduler.
- The schema, Android production code, and `ack_server.py` are unchanged.
- `ACTIVE_TRANSPORT = "fcm"`; ntfy remains implemented as rollback; no
  dual-send is used.

### Phase advances

The current phase advances to the next planned phase, identified exactly
from `docs/MVP_PHASES.md`:

```text
Phase 7 — Recovery and Reconciliation
```

Phase 7 is not yet planned or opened by this document.

---

## 2. Goal

Move the real Hermes Personal Admin notification outbox from ntfy transport to encrypted FCM without weakening durability.

Target:

```text
Hermes queue
→ persistent notifications row
→ dispatcher
→ production FCM sender
→ Phase 5 AES-GCM envelope
→ Ackline
```

Primary correctness rule:

```text
no successful FCM acceptance
=
no sent_at
```

---

## 3. Existing Contracts That Must Survive

### Hermes outbox

The completed preflight inspected the actual implementation. The surviving
contracts include:
- stable `notification_id`;
- persistent `notifications` table;
- generated `ack_token`;
- `created_at`;
- `sent_at`;
- send attempts/error metadata;
- current ntfy publisher (rollback);
- queue/dispatch/status commands.

Final cutover dispatcher eligibility (validated in production):

```text
sent_at IS NULL
AND canceled_at IS NULL
AND acknowledged_at IS NULL
AND associated run.status = 'committed'
```

`acknowledged_at IS NULL` was added during cutover QA: already-ACKed unsent
historical rows are terminal for transport delivery.

### Ackline

Do not redesign:
- encrypted-only FCM envelope;
- strict parser;
- AES-256-GCM;
- AndroidKeyStore;
- Room INSERT IGNORE;
- explicit Visto;
- durable WorkManager ACK.

---

## 4. Preflight Decisions Required

### A. Repository ownership

Determine:
- whether `~/.hermes/personal-admin` is git-controlled;
- where Hermes production code is versioned;
- whether Phase 6 spans one or two repositories;
- safe branch/review workflow.

### B. Dispatcher semantics

Inspect exact `notification_state.py`:
- pending-row query;
- ordering;
- transaction boundaries;
- `send_attempts`;
- `last_attempt_at`;
- `last_error`;
- `sent_at`;
- publisher exception behavior;
- batch behavior.

### C. ntfy publisher

Inspect:
- exact `publish(row)` contract;
- credential source;
- HTTP behavior;
- timeout/response rules;
- message ID handling;
- retry implications;
- coupling to ACK.

### D. Scheduler

Discover unattended execution:
- cron/launchd/wrapper;
- interpreter;
- working directory;
- environment;
- cadence;
- logs.

The final sender must work there without interactive shell setup.

### E. Python runtime

Confirm:
- supported interpreter;
- `firebase-admin`;
- `cryptography`;
- FID-targeting support;
- absolute production interpreter/venv path.

### F. Credentials

Confirm metadata only:
- Firebase credential path/mode/project;
- E2EE key path/size/mode;
- no secret output.

### G. FID source

Choose durable local configuration:
- not hardcoded;
- not shell-only;
- replaceable after reinstall;
- available to scheduler.

### H. Protocol parity

Confirm Phase 5 constants:

```text
v = 1
kid = ackline-main
AES-256-GCM
nonce = 12 bytes
tag = 16 bytes
AAD = ackline-e2ee|v=1|kid=<kid>
MAX_INNER_PAYLOAD_BYTES = 2500
outer exact keys = v/kid/nonce/ciphertext
```

### I. Error taxonomy

Map actual Firebase Admin exceptions into:
- accepted;
- transient/retryable;
- permanent target;
- permanent configuration;
- unknown safe handling.

### J. Invalid FID state

Determine smallest truthful persistence model:
- never set `sent_at`;
- avoid endless silent retry;
- allow re-pair/update FID;
- allow affected notification recovery.

### K. ntfy rollback

Choose minimal explicit transport selector:
- one active transport;
- ntfy retained as rollback;
- no default dual-send.

---

## 5. Proposed Sender Responsibility

Exact file path is a preflight output.

Conceptually the sender receives:

```text
notification_id
level
title
message
created_at
ack_token
```

and configured operational inputs:

```text
FID
E2EE key path
kid
Firebase credential
```

It should:
1. validate inputs;
2. build compact inner JSON;
3. reject oversize;
4. create fresh 12-byte nonce;
5. AES-256-GCM encrypt with canonical AAD;
6. construct exact four-field FCM `data`;
7. choose FCM priority from level;
8. call Firebase Admin;
9. return a small sanitized result.

Preferred separation:

```text
dispatcher owns DB state
sender owns one transport attempt
```

The sender should not independently mutate the Hermes SQLite outbox unless preflight finds a compelling reason.

---

## 6. Proposed Dispatch Ordering

Conceptual target:

```text
for each eligible unsent row:
    prepare/record attempt using existing semantics
    result = sender.send(row)

    if accepted:
        record sent_at
        clear transient error state
    elif transient:
        keep sent_at NULL
        record sanitized retryable error
    elif permanent:
        keep sent_at NULL
        record actionable terminal state
```

No tight retry loop inside one dispatch invocation unless the current design already requires it.

The existing scheduler/outbox cadence should own retries.

---

## 7. FCM Accepted Semantics

Firebase Admin success means:

```text
FCM accepted the message
```

It does not mean:
- device received;
- device decrypted;
- Room persisted;
- notification displayed.

If `sent_at` already represents provider acceptance, preserve that meaning.

The preflight confirmed that `sent_at` records provider acceptance, not
end-device display or decryption.

---

## 8. Ambiguous Success Window

Required safety case:

```text
FCM accepted
Hermes fails before sent_at commit
dispatcher retries same row
```

Expected:
- same `notification_id`;
- fresh nonce/ciphertext;
- FCM may accept twice;
- Ackline persists one logical row;
- duplicate does not repost native notification.

Do not chase exactly-once delivery.

---

## 9. Error Policy Requirements

### Transient

- remain eligible for retry;
- no `sent_at`;
- sanitized error category only.

### Invalid/stale FID

Must become actionable.

Desired operator shape:

```text
Ackline reinstalled / FID changed
→ Hermes indicates re-pair required
→ FID updated
→ delivery can resume
```

Exact DB state is a preflight decision.

### Credential/project/config errors

Operational failure, not notification success.

Avoid retry storms.

---

## 10. Security Requirements

Never log:
- service-account private key;
- E2EE key;
- `ack_token`;
- title;
- message;
- plaintext inner JSON;
- ciphertext;
- full FID.

Do not dump Firebase payload dicts.

Do not add debug plaintext transport.

---

## 11. Testing Strategy

### Sender unit tests

At minimum:
- row `created_at` preserved;
- `ack_token` encrypted inside;
- exact outer key set;
- priority mapping;
- fresh nonce;
- oversize rejection;
- missing/bad key configuration;
- missing/bad FID configuration;
- accepted result mapping;
- transient exception mapping;
- unregistered FID mapping;
- auth/config exception mapping;
- no secret-bearing error output.

### Dispatcher tests

At minimum:
- accepted → `sent_at` only after sender success;
- transient → `sent_at` remains NULL;
- permanent → no false `sent_at`;
- attempt/error metadata correct;
- later success clears/updates error state;
- batch behavior remains sane;
- one bad row does not corrupt another row.

### Integration tests

Use a narrow fake sender boundary for DB/dispatcher tests.

Do not make unit tests depend on Firebase network.

### Controlled real FCM QA

After code review:
1. queue synthetic notification via real Hermes CLI/API;
2. dispatch via production FCM sender;
3. verify FCM acceptance → correct Hermes state;
4. verify Ackline receives/decrypts/displays;
5. verify ACK regression;
6. simulate duplicate logical retry;
7. simulate transient sender failure;
8. test stale/invalid FID with controlled config;
9. restore current FID;
10. verify scheduler/non-interactive execution;
11. verify ntfy rollback selector.

---

## 12. ntfy Cutover

Do not delete ntfy.

A minimal conceptual selector is:

```text
transport = fcm | ntfy
```

but exact configuration must follow existing Hermes conventions.

No generic plugin framework.

No default dual-send.

---

## 13. Likely Files

The completed preflight discovered the exact paths and runtime ownership.

Expected Hermes-side candidates:

```text
~/.hermes/personal-admin/notification_state.py
~/.hermes/personal-admin/ack_server.py      # inspect; likely unchanged
Hermes scheduler/launchd/cron config
Hermes local secrets/config
new production FCM sender module
targeted Python tests
```

Expected Ackline-side candidates:

```text
docs/CURRENT_PHASE.md
docs/IMPLEMENTATION_PLAN.md
docs/ARCHITECTURE.md
existing Phase 5 protocol/dev sender tests for parity reference
```

Android production files should normally remain unchanged.

---

## 14. Do Not Do

No:
- Room migration by default;
- ACK redesign;
- Android UI work;
- reconciliation;
- pending-notification GET;
- polling;
- key rotation;
- multi-device support;
- SaaS/backend;
- Firestore/Auth/Functions;
- foreground service;
- production dual-send unless justified;
- deletion of ntfy rollback;
- runtime dependency on Ackline checkout;
- real secret output.

---

## 15. Preflight Exit Criteria

Valid results:

```text
READY
READY_WITH_DECISIONS
BLOCKED
```

READY requires:
- exact repository/runtime ownership;
- exact production Python interpreter;
- exact scheduler environment;
- exact Firebase credential loading;
- exact durable FID config;
- exact production sender location;
- exact dispatcher seam;
- exact error mapping;
- exact invalid-FID state/recovery semantics;
- exact ntfy rollback selector;
- exact test plan;
- no secret exposure required.

---

## 16. Implementation Status

COMPLETE — CLOSED.

- Hermes implementation: merged to Hermes `dev` at
  `5b5777a827e097a98687bc6fae0060a2e6fcebb3`.
- Hermes automated tests: 28/28 PASS.
- Real QA passed: production sender (Stage 1), production dispatcher against
  an isolated DB (Stage 2), full ACK chain (Stage 3), and the production
  cutover canary with `ACTIVE_TRANSPORT = "fcm"` (eligible 1 / sent 1 /
  failed 0; end-to-end ACK synced; eligible backlog 0).
- A "Stage 3-R" diagnostic was invalid (narrated fabrication; scripts never
  executed) and is excluded from the evidence record. Production data loss
  is NOT SUPPORTED; SQLite durability anomaly NOT PROVEN. See
  `docs/CURRENT_PHASE.md` "Forensic Correction".
- Android production code, Room schema, and `ack_server.py` remain unchanged.
- Current phase advances to Phase 7 — Recovery and Reconciliation, which is
  not yet planned or opened.

The user owns commits, pushes, and merges.
