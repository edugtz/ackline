# Current Phase

## Status

**PLANNED — READY FOR PREFLIGHT**

Phase:

```text
1 — FCM Transport Reliability Gate
```

Implementation branch:

```text
1-fcm-reliability
```

Base branch:

```text
dev
```

---

## Objective

Prove on the physical Oppo that FCM solves the specific delivery failure that motivated Ackline:

```text
normal Android background use
+
screen off / idle
+
Wi-Fi ↔ mobile network transitions
+
temporary loss of connectivity
```

must not require manually opening Ackline to restore push delivery.

Phase 1 is a transport reliability gate.

It is **not** the persistent inbox or acknowledgment phase.

Do not expand product scope until this transport gate passes.

---

## Baseline Already Proven

Phase 0 established the working foundation:

```text
Android project builds
APK installs on the Oppo
Ackline launches successfully
notification permission works
Firebase configuration is valid
current FID registration works
FID is visible and copyable
Mac Firebase Admin credentials are external to the repository
Mac can target the Oppo FID
FCM accepts a fake data-only message
AcklineMessagingService receives the fake message
setup/debug UI surfaces the received test message
```

A dark-mode readability defect discovered during physical-device QA was also fixed.

These foundations are working.

Do not redesign or reimplement them without a concrete Phase 1 requirement.

---

## Product Principles

Ackline remains:

```text
single-user
Android-only
small
low-maintenance
explicit-acknowledgment
transport-isolated
privacy-conscious
```

Reliability takes priority over feature expansion.

Phase 1 continues using fake/non-sensitive test payload contents because application-level E2EE is a later phase.

---

## Phase 1 Question

This phase must answer:

```text
Can FCM continue delivering user-visible Ackline alerts on the real Oppo
through normal Android background conditions and network transitions
without requiring Ackline to be manually reopened?
```

If the answer is not demonstrably yes, do not proceed to Phase 2.

---

## In Scope

### FCM Transport

Continue using:

```text
FCM data-only messages
```

Do not use an automatic FCM `notification` payload.

Ackline must continue receiving messages through `FirebaseMessagingService` so app code controls:

```text
validation
diagnostics
native notification creation
future persistence
future decryption
future acknowledgment
```

Only the first three are Phase 1 work.

---

## Phase 1 Test Envelope

Use the smallest useful test envelope:

```text
notification_id
level
title
message
sent_at
```

Supported levels:

```text
remember
important
urgent
```

Validation requirements:

```text
notification_id -> non-blank
level           -> remember | important | urgent
title           -> non-blank
message         -> non-blank
sent_at         -> non-blank
```

Malformed messages must fail safely.

This is a transport/reliability test contract.

It is **not** the final Room entity or long-term domain model.

---

## FCM Priority

Use realistic FCM Android delivery priority:

```text
remember   -> NORMAL
important  -> HIGH
urgent     -> HIGH
```

High priority is only for user-visible alerts where immediate delivery is justified.

Do not mark all background traffic high priority.

The primary Phase 1 reliability matrix should use:

```text
important
```

because that represents an alert expected to reach the user promptly without using the most intrusive Android notification channel.

---

## Android Notification Channels

Create native Android notifications locally.

Required channels:

```text
Ackline · Remember
Ackline · Important
Ackline · Urgent
```

Channel importance:

```text
Remember   -> IMPORTANCE_LOW
Important  -> IMPORTANCE_DEFAULT
Urgent     -> IMPORTANCE_HIGH
```

Channel semantics:

### Remember

Low-interruption information worth retaining but not demanding immediate attention.

### Important

Normal user-visible Personal Admin alert expected to arrive promptly.

### Urgent

Exceptional alert where stronger interruption is justified.

Phase 1 validates transport and notification delivery only.

No `Visto` action exists yet.

---

## Android Receive Path

`AcklineMessagingService` remains the Firebase boundary.

Its Phase 1 responsibilities are:

```text
receive FCM data-only message
        ↓
extract expected test fields
        ↓
validate payload
        ↓
capture received_at
        ↓
hand app-owned data to native notification component
```

Firebase-specific types should not spread further into the app.

The service should remain small.

---

## Native Notification Component

Introduce the smallest app-owned component necessary to post Android notifications.

Expected concept:

```text
AcklineNotificationManager
```

Responsibilities:

```text
ensure notification channels exist
map level -> channel
post native Android notification
capture/display diagnostic timestamp if useful
```

It must not implement:

```text
persistence
deduplication database
acknowledgment
remote calls
reconciliation
```

Those belong to later phases.

---

## Diagnostics

Phase 1 needs enough diagnostics to distinguish transport delay from application failure.

Useful metadata:

```text
notification_id
level
sent_at
received_at
displayed_at
```

Logging must remain bounded and non-sensitive.

Do not log:

```text
FID
Firebase service-account contents
private keys
credentials
real personal alert contents
email/calendar/task contents
```

Fake Phase 1 test title/message values are acceptable.

---

## Mac Sender Harness

Keep one repeatable Python Firebase Admin sender.

Do not create competing sender implementations.

The Phase 1 sender should support controlled sends using:

```text
--fid
--id
--level
--title
--message
```

`sent_at` should be generated by the sender.

FCM Android priority must derive from `level`:

```text
remember   -> normal
important  -> high
urgent     -> high
```

Firebase Admin service-account credentials remain outside the repository.

The FID must not be hardcoded in source.

---

## Expected Minimal Architecture

```text
Mac test sender
      |
      | Firebase Admin SDK
      | FCM data-only
      v
FCM
      |
      v
AcklineMessagingService
      |
      | validate test envelope
      | received_at
      v
AcklineNotificationManager
      |
      | level -> channel
      | native notification
      | displayed_at
      v
Android notification tray
```

No additional architectural layers are required unless preflight identifies a concrete need.

---

## Likely File Scope

Expected existing files to modify:

```text
app/src/main/java/com/edu/ackline/push/AcklineMessagingService.kt
tools/firebase_sender.py
```

Expected new production file:

```text
app/src/main/java/com/edu/ackline/notifications/AcklineNotificationManager.kt
```

Narrowly scoped test files may be added.

Other production files may change only if preflight identifies a concrete requirement.

This list is not permission to pre-create future-phase architecture.

---

## Existing Setup UI

The existing setup/debug screen remains useful during Phase 1.

Preserve:

```text
notification permission state
FCM registration state
FID display/copy
last test message diagnostics
dark-mode readability fix
```

Do not turn the setup screen into the final Ackline product UI.

UI polish beyond keeping this diagnostic surface usable is out of scope.

---

## Out of Scope

Do **not** add:

```text
Room
persistent inbox
alert detail screen
Pendientes / Vistas
Visto semantics
local acknowledgment
notification Visto action
remote ACK
ACK HTTP server
WorkManager
E2EE
real Hermes notification contents
Hermes notification_state.py integration
reconciliation
search
analytics
login/accounts
multi-device
Play Store work
Hilt
Retrofit
multi-module architecture
foreground service
persistent WebSocket
MQTT
custom persistent connection
custom reconnect loop
battery-optimization exemption
ColorOS/OEM-specific workaround without evidence
```

Do not pre-create:

```text
database/
repository/
ack/
security/
sync/
inbox/
detail/
```

packages merely for future work.

---

## Reliability Gate

Physical-device testing on the Oppo is mandatory.

Required cases:

```text
1. foreground baseline
2. background
3. removed from Recents without Force Stop
4. screen off
5. Wi-Fi
6. mobile data
7. Wi-Fi -> mobile
8. mobile -> Wi-Fi
9. message sent during network transition
10. airplane mode -> send -> restore connectivity
11. device idle / Doze
12. multiple sends during multi-hour normal use
```

Minor transport delay during network transition or temporary offline recovery is acceptable.

Requiring the user to manually reopen Ackline is not.

---

## Critical Failure Condition

The defining failure condition is:

```text
usable Wi-Fi or mobile connectivity
+
IMPORTANT alert does not arrive
+
opening Ackline causes it to arrive or restores subsequent delivery
=
PHASE 1 FAIL
```

This must not be explained away as acceptable behavior.

It reproduces the exact class of failure Ackline is intended to eliminate.

---

## Force Stop

Android:

```text
Settings -> Apps -> Ackline -> Force Stop
```

is not equivalent to:

```text
Home
normal background
screen off
swiping the app from Recents
```

Force Stop may intentionally suppress application delivery until the user launches the app again.

It is not part of the normal Phase 1 pass/fail matrix.

It may be tested separately for documentation if useful.

---

## ColorOS Policy

Test normal/default device behavior first.

Do not proactively require:

```text
foreground service
lock Ackline in Recents
auto-launch workaround
battery optimization exemption
OEM-specific background service
custom socket
```

If a reproducible Oppo failure occurs:

```text
reproduce
        ↓
collect evidence
        ↓
determine whether failure is:
FCM / app / Play Services / Doze / ColorOS
        ↓
research current platform behavior
        ↓
propose smallest evidence-backed mitigation
```

Do not weaken the reliability gate just because an OEM workaround exists.

Any required ColorOS setting must be explicitly documented.

---

## Initial Smoke Gate

Before running the full matrix, prove three basic cases:

```text
A. foreground + Wi-Fi + IMPORTANT
B. background + Wi-Fi + IMPORTANT
C. removed from Recents + Wi-Fi + IMPORTANT
```

Expected for each:

```text
native Android notification appears
without requiring Ackline to be reopened
```

If one fails, stop the full matrix and diagnose first.

---

## Acceptance Criteria

Phase 1 is complete only when all applicable criteria are true:

### Implementation

- FCM remains data-only.
- Phase 1 envelope is validated.
- unsupported/malformed messages fail safely.
- Firebase-specific code remains bounded.
- native Android notifications are created locally.
- Remember / Important / Urgent channels exist.
- channel importance mapping is correct.
- FCM priority mapping is correct.
- sender remains repeatable and credential-safe.

### Reliability

- foreground delivery works.
- background delivery works.
- removal from Recents does not stop normal delivery.
- screen-off delivery works.
- mobile-data delivery works.
- Wi-Fi -> mobile transition works without reopening Ackline.
- mobile -> Wi-Fi transition works without reopening Ackline.
- message sent during a transition eventually arrives automatically.
- message sent while temporarily offline arrives after connectivity returns.
- IMPORTANT survives the intended Doze test.
- multiple sends during normal multi-hour use do not expose a reconnect/open-app failure.

### Scope / Security

- no Room or inbox was added.
- no ACK semantics were added.
- no WorkManager was added.
- no E2EE was added.
- no Hermes production integration was added.
- no unnecessary OEM workaround was added.
- no foreground service/custom socket was added.
- no secret or sensitive content is exposed in source or logs.

### Quality

- automated build/test validation passes.
- independent review passes.
- physical-device QA passes.
- final pushed-branch ChatGPT + GitHub review passes.

---

## Automated Validation

Run at minimum:

```bash
./gradlew clean lintDebug testDebugUnitTest assembleDebug
git diff --check
```

Validate Python syntax:

```bash
python -m py_compile tools/firebase_sender.py
```

Add automated tests only for deterministic logic where they provide real value.

Examples:

```text
level validation
level -> Android channel mapping
malformed envelope rejection
sender priority mapping
```

Do not create mocks that pretend to prove physical FCM reliability.

---

## Physical QA Record

For every significant test record:

```text
test case
notification_id
level
sent_at
received_at if available
network state
screen state
app state
arrival behavior
result
```

Do not use real personal content.

Example:

```text
wifi-to-mobile-003
IMPORTANT
send: 14:31:02
Wi-Fi disabled immediately after send
mobile network healthy
screen off
arrived: 14:31:05
PASS
```

---

## AI Implementation / Review Route

### Planning / architecture

```text
ChatGPT + GitHub
```

The phase is already planned here.

Do not ask another model to redesign the phase.

### Preflight

```text
/local-build
Qwen3.8 27B 4bit + DFlash2
```

Preflight:

```text
read repository
inspect current implementation
identify exact files
identify risks
propose bounded implementation
STOP
```

No edits before approval.

### Primary implementation

```text
/local-build
Qwen3.8 27B 4bit + DFlash2
```

### Higher-quality local fallback

```text
/local-quality
Qwen3.8 27B 5bit + DFlash2
```

### Android / Firebase / Doze / ColorOS specialist

```text
Gemini Android Studio
```

Use only for concrete platform/tooling/runtime diagnosis.

### Independent reviewer

```text
/local-review
Qwen3.8 27B AWQ 5bpw + Lightning MTP
```

Review only.

No edits.

### Final review

After the implementation branch is committed and pushed:

```text
ChatGPT + GitHub
```

Verdict:

```text
PASS
PASS_WITH_NOTES
BLOCKED
```

Merge to `dev` only after PASS by default.

---

## Workflow

```text
update Phase 1 docs
        ↓
commit planning docs
        ↓
preflight
        ↓
ChatGPT/user approval
        ↓
builder implementation
        ↓
automated validation
        ↓
basic physical smoke tests
        ↓
independent review
        ↓
full physical reliability matrix
        ↓
multi-hour real-use test
        ↓
user commit/push
        ↓
ChatGPT GitHub final review
        ↓
PASS
        ↓
merge 1-fcm-reliability -> dev
```

The user owns commits and pushes.

---

## Completion Criteria

All true:

```text
FCM data-only path preserved
native notifications work
priority mapping correct
channels correct
malformed payloads safe
build/test validation passes
basic Oppo smoke gate passes
Wi-Fi/mobile transition matrix passes
offline recovery passes
Doze IMPORTANT test passes
multi-hour normal-use test passes
opening Ackline is never required to restore normal delivery
no unjustified ColorOS workaround
independent review passes
final pushed-branch review PASS
```

---

## Suggested Commit

Planning docs:

```text
docs: plan Phase 1 FCM reliability gate
```

Final Phase 1 implementation:

```text
feat: prove FCM background delivery
```

Do not commit automatically.

The user owns commit and push.