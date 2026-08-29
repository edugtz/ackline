# Phase 1 — FCM Transport Reliability Gate Implementation Plan

## 1. Status

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

## 2. Objective

Implement only the minimum Android and Mac sender functionality required to answer:

```text
Can Ackline reliably receive user-visible IMPORTANT alerts on the physical Oppo
through normal Android background conditions and Wi-Fi/mobile transitions
without requiring the app to be manually opened?
```

This phase proves transport behavior.

It does not build the persistent Ackline product yet.

---

## 3. Existing Working Foundation

Preserve the working Phase 0 implementation:

```text
Android project builds
APK installs
Ackline launches
notification permission works
Firebase Android configuration works
FID registration works
FID is visible/copyable
FirebaseMessagingService receives data-only messages
Mac Firebase Admin sender can target the FID
Mac -> FCM -> Oppo -> Ackline baseline has been proven
service-account credentials remain outside the repository
```

Physical QA also identified and fixed the setup-screen dark-mode readability defect.

Do not undo that fix.

Do not redesign the setup screen.

---

## 4. Product Quality Goal

The Phase 1 implementation should remain intentionally small:

```text
existing Android app
existing Firebase messaging boundary
one small native notification component
one repeatable sender harness
focused deterministic tests
physical-device reliability evidence
```

No architecture expansion for future phases.

No generic Clean Architecture layering.

No new product UI.

---

## 5. Preflight Requirements

Before editing, inspect:

1. `AGENTS.md`
2. `docs/AI_WORKFLOW.md`
3. `docs/CURRENT_PHASE.md`
4. this `docs/IMPLEMENTATION_PLAN.md`
5. relevant Phase 1 section of `docs/MVP_PHASES.md`
6. `docs/ARCHITECTURE.md`
7. current `AcklineMessagingService`
8. current setup state/UI only as needed
9. current manifest
10. current Gradle dependencies
11. current `tools/firebase_sender.py` if present
12. current git status/diff

Preflight must determine:

```text
exact files to create
exact files to modify
whether manifest changes are actually needed
how payload parsing remains bounded
how channel mapping is represented
how notification identity is generated
how sender priority is configured
which deterministic tests are worth adding
```

Preflight is read-only.

Stop before editing.

---

## 6. Phase 1 Test Envelope

The sender and Android receiver use:

```text
notification_id
level
title
message
sent_at
```

Required validation:

### `notification_id`

```text
required
non-blank
```

### `level`

Must be exactly one of:

```text
remember
important
urgent
```

### `title`

```text
required
non-blank
```

### `message`

```text
required
non-blank
```

### `sent_at`

```text
required
non-blank
```

Parsing a formal instant is optional if it would add unnecessary complexity to the spike.

The value exists primarily to correlate sender and receiver timing.

Malformed payload behavior:

```text
reject
do not crash
do not post notification
emit bounded diagnostic metadata
```

Do not create the future Room entity.

---

## 7. FCM Priority Mapping

The Mac sender must map:

```text
remember   -> FCM NORMAL
important  -> FCM HIGH
urgent     -> FCM HIGH
```

Rationale:

### Remember

Can tolerate normal Doze batching.

### Important

Represents the main real-time Personal Admin alert type for reliability testing.

### Urgent

Also requires prompt transport but maps to a stronger Android presentation channel.

Do not mark every FCM message high priority.

---

## 8. Native Android Notification Channels

Required channels:

```text
Ackline · Remember
Ackline · Important
Ackline · Urgent
```

Required Android importance:

```text
Remember   -> NotificationManager.IMPORTANCE_LOW
Important  -> NotificationManager.IMPORTANCE_DEFAULT
Urgent     -> NotificationManager.IMPORTANCE_HIGH
```

Channel IDs should be stable and app-owned.

Exact IDs can be implementation details such as:

```text
ackline_remember
ackline_important
ackline_urgent
```

Do not encode Firebase terminology into app-owned channel IDs.

---

## 9. Notification Manager

Introduce one small Android notification component.

Expected class:

```text
AcklineNotificationManager
```

Preferred package:

```text
com.edu.ackline.notifications
```

Responsibilities:

```text
ensure channels exist
accept app-owned alert/test data
select channel from level
build native Android notification
post native Android notification
return/capture displayed timestamp if useful
```

The public API should not require Firebase SDK types.

For example conceptually:

```text
show(
    notificationId,
    level,
    title,
    message
)
```

Exact Kotlin shape is determined during implementation.

Do not add an interface merely to wrap one implementation unless testing requires a compelling reason.

---

## 10. Native Notification Content

Phase 1 native notifications need only:

```text
Ackline app identity
title
message
correct channel
reasonable small icon
```

No actions are required.

Do not add:

```text
Visto
Open / detail routing
reply
snooze
group management
persistent inbox behavior
```

Tapping behavior may remain minimal unless Android requires a pending intent for normal usability.

Do not accidentally make notification interaction imply acknowledgment.

---

## 11. Notification Identity

Phase 1 does not yet have Room-backed deduplication.

Use a deterministic/bounded Android notification identity derived from `notification_id` or another simple stable method.

Requirements:

```text
same test notification_id does not require an ever-growing ID registry
different normal test IDs can coexist sufficiently for QA
no database introduced
```

Do not implement final delivery deduplication semantics yet.

That belongs to Phase 2 with Room as local truth.

---

## 12. Messaging Service Changes

Keep:

```text
AcklineMessagingService
```

as the Firebase-specific boundary.

Phase 1 flow:

```text
onMessageReceived
        ↓
extract data map
        ↓
validate notification_id
validate level
validate title
validate message
validate sent_at
        ↓
capture received_at
        ↓
build app-owned values
        ↓
AcklineNotificationManager.show(...)
        ↓
bounded diagnostic log
```

The service must remain small.

Do not inject:

```text
Room
repository
WorkManager
ACK logic
network client
Hermes logic
```

into the service.

---

## 13. Registration Behavior

Preserve the existing current FID registration flow.

Do not migrate back to deprecated token-first registration.

Do not change:

```text
FID display/copy
registration state
working Firebase initialization
```

unless a concrete bug requires it.

Phase 1 is about message delivery, not registration redesign.

---

## 14. Setup Screen

Preserve the existing setup/debug screen.

It can continue displaying:

```text
Notification permission
FCM registration
Device ID
Copy
Last test message
```

The existing dark-mode readability fix must remain intact.

Do not spend Phase 1 building the final inbox UI.

If adding a tiny diagnostic value to the setup screen would materially simplify QA, preflight must justify it before implementation.

Prefer Logcat/notification behavior over expanding UI diagnostics.

---

## 15. Mac / Python Sender

Use the existing sender file if it already exists:

```text
tools/firebase_sender.py
```

Do not create a second sender.

Required command shape:

```bash
python tools/firebase_sender.py \
  --fid "$ACKLINE_FID" \
  --id "test-id" \
  --level important \
  --title "Ackline test" \
  --message "Non-sensitive Phase 1 test"
```

Required arguments:

```text
--fid
--id
--level
```

Title/message may have sensible fake defaults.

Supported `--level` values:

```text
remember
important
urgent
```

Unsupported level:

```text
fail locally before sending
```

Sender generates:

```text
sent_at
```

using UTC.

Sender should print enough information for QA, for example:

```text
notification_id
level
sent_at
FCM acceptance result
```

Do not print the target FID unnecessarily.

---

## 16. Firebase Admin Credentials

Continue using external credentials.

Recommended current development pattern:

```text
GOOGLE_APPLICATION_CREDENTIALS
```

Credential location remains outside the repository.

Never commit:

```text
service-account JSON
private_key
credential copy
FID config containing secrets
```

The Android `google-services.json` is not the Firebase Admin private credential.

Phase 1 should not alter credential architecture.

---

## 17. Timestamp Diagnostics

Use UTC timestamps for correlation.

Sender:

```text
sent_at
```

Android:

```text
received_at
displayed_at
```

Useful diagnostic line concept:

```text
notification_id=test-wifi-mobile-01
level=important
sent_at=...
received_at=...
displayed_at=...
```

Do not log message bodies unless a fake test payload makes it useful during a bounded debugging session.

Normal implementation should prefer metadata.

---

## 18. Automated Tests

Only add tests that validate deterministic app logic.

High-value candidates:

### Level validation

```text
remember accepted
important accepted
urgent accepted
other rejected
blank rejected
```

### Level -> channel mapping

```text
remember -> Remember
important -> Important
urgent -> Urgent
```

### Envelope validation

Examples:

```text
missing notification_id rejected
blank title rejected
missing sent_at rejected
unsupported level rejected
valid payload accepted
```

### Sender validation

If practical without unnecessary Python test infrastructure:

```text
invalid --level rejected
priority mapping correct
```

Do not create large test scaffolding for three mappings.

Do not pretend unit tests prove FCM or Doze reliability.

---

## 19. Expected File Scope

Likely production changes:

```text
MODIFY
app/src/main/java/com/edu/ackline/push/AcklineMessagingService.kt

CREATE
app/src/main/java/com/edu/ackline/notifications/AcklineNotificationManager.kt

MODIFY
tools/firebase_sender.py
```

Potential bounded changes:

```text
app/src/main/AndroidManifest.xml
```

only if native notification behavior requires a real manifest change.

Potential tests:

```text
app/src/test/java/.../
```

only for deterministic logic worth testing.

Do not modify unrelated project files.

---

## 20. Explicitly Out of Scope

Do not implement:

```text
Room
SQLite local inbox
AlertEntity
DAO
Repository layer for alerts
InboxScreen
AlertDetailScreen
Pendientes / Vistas
Visto
acknowledgedAt
ackSyncState
notification Visto action
remote ACK
AckClient
AckSyncWorker
WorkManager
Tailscale HTTP
E2EE
AES-GCM
Android Keystore
Hermes outbox integration
notification_state.py changes
reconciliation
onDeletedMessages recovery workflow
search
settings architecture
authentication
analytics
multi-device support
Play Store work
foreground service
persistent socket
MQTT
ntfy integration
OEM-specific service
battery exemption request
```

Do not add placeholders for them.

---

## 21. Implementation Order

After approved preflight:

1. Re-read current files before editing.
2. Confirm working tree is understood.
3. Implement minimal Phase 1 envelope validation.
4. Add native notification manager.
5. Create required notification channels.
6. Wire messaging service to native notification component.
7. Extend existing sender with level/timestamp/priority support.
8. Add narrowly useful deterministic tests.
9. Run Android validation.
10. Run Python syntax validation.
11. Inspect final diff for scope creep.
12. Perform physical smoke tests.
13. Stop and diagnose if smoke tests fail.
14. Run independent code review.
15. Resolve blocking review findings.
16. Run full Oppo reliability matrix.
17. Run multi-hour real-use test.
18. User commits/pushes.
19. ChatGPT reviews pushed branch using GitHub.
20. Merge to `dev` only after PASS.

---

## 22. Automated Validation Commands

Android:

```bash
./gradlew clean lintDebug testDebugUnitTest assembleDebug
```

Python:

```bash
python -m py_compile tools/firebase_sender.py
```

Git hygiene:

```bash
git status --short --untracked-files=all
git diff --check
```

Inspect scope:

```bash
git diff --stat dev...
```

Do not hide unrelated dirty files.

---

## 23. Initial Physical Smoke Test

Do this before the full matrix.

### Smoke A — Foreground

State:

```text
Wi-Fi connected
Ackline open
screen on
IMPORTANT
```

Expected:

```text
native Android notification appears
```

### Smoke B — Background

State:

```text
Wi-Fi connected
Ackline sent to background with Home
screen on
IMPORTANT
```

Expected:

```text
native Android notification appears
without reopening Ackline
```

### Smoke C — Removed from Recents

State:

```text
Wi-Fi connected
Ackline removed from Recents
not Force Stopped
IMPORTANT
```

Expected:

```text
native Android notification appears
without reopening Ackline
```

If any of these fail:

```text
STOP
diagnose
do not run the complete matrix
```

---

## 24. Baseline Reliability Matrix

After smoke passes:

### Test 1 — Foreground Wi-Fi

```text
app foreground
Wi-Fi
IMPORTANT
```

Expected:

```text
arrives
```

### Test 2 — Background Wi-Fi

```text
app background
Wi-Fi
IMPORTANT
```

Expected:

```text
arrives without opening app
```

### Test 3 — Removed from Recents

```text
app removed from Recents
Wi-Fi
IMPORTANT
```

Expected:

```text
arrives without opening app
```

### Test 4 — Screen Off

```text
app background
Wi-Fi
screen off
IMPORTANT
```

Expected:

```text
arrives
```

### Test 5 — Mobile Data Only

```text
Wi-Fi off
mobile data working
app background
IMPORTANT
```

Expected:

```text
arrives
```

---

## 25. Network Transition Matrix

### Test 6 — Wi-Fi -> Mobile

Start:

```text
Wi-Fi connected
app not foreground
```

Transition:

```text
disable/leave Wi-Fi
mobile network takes over
```

Send IMPORTANT after mobile connectivity is usable.

Expected:

```text
arrives without opening Ackline
```

### Test 7 — Mobile -> Wi-Fi

Start:

```text
mobile data
app not foreground
```

Transition:

```text
connect Wi-Fi
```

Send after Wi-Fi becomes usable.

Expected:

```text
arrives without opening Ackline
```

### Test 8 — Send During Transition

Send while connectivity is changing.

Expected:

```text
delivery may be delayed
but eventually occurs automatically
without opening Ackline
```

The exact latency is diagnostic, not the primary pass criterion.

---

## 26. Temporary Offline Recovery

### Test 9 — Airplane Recovery

Procedure:

```text
Ackline not foreground
airplane mode ON
send IMPORTANT from Mac
wait approximately 2 minutes
airplane mode OFF
wait for usable network
DO NOT OPEN ACKLINE
```

Expected:

```text
message eventually arrives automatically
```

Failure:

```text
message remains absent
opening Ackline causes it to appear
```

This is a critical failure.

---

## 27. Idle / Screen-Off Test

### Test 10 — Natural Idle

Procedure:

```text
screen off
leave phone untouched 10–20 minutes
send IMPORTANT
```

Expected:

```text
notification arrives without opening Ackline
```

Record latency.

---

## 28. Doze Test

Use ADB only after ordinary screen-off/background behavior works.

A controlled Doze procedure may use current Android tooling such as:

```bash
adb shell dumpsys deviceidle force-idle
```

Exact command must be verified against the connected device/runtime before use.

Send:

```text
IMPORTANT
```

Expected:

```text
high-priority user-visible FCM alert arrives without opening Ackline
```

After test, restore normal device idle state.

Do not classify a `remember` NORMAL-priority message delayed by Doze as a Phase 1 transport failure; delayed normal-priority delivery may be intentional.

---

## 29. Multi-Hour Real-Use Test

After controlled cases pass, use the Oppo normally for several hours.

Conditions should naturally include some of:

```text
screen off/on
Wi-Fi movement
mobile data
background apps
normal phone usage
Ackline not manually reopened for testing
```

Send multiple uniquely identified IMPORTANT test messages.

Goal:

```text
detect reconnect-dependent failure
```

Not:

```text
collect artificial benchmark latency statistics
```

Every sent notification should be accounted for.

---

## 30. Critical Failure Rule

Phase 1 fails if reproducibly:

```text
working network
+
IMPORTANT message missing
+
opening Ackline makes the missing message arrive
or restores subsequent FCM delivery
```

This behavior is unacceptable even if Firebase technically accepted the message.

It reproduces the user-visible failure Ackline exists to solve.

---

## 31. Acceptable Temporary Delay

These situations can produce reasonable temporary delay:

```text
no network
network handoff still in progress
brief radio reconnection
device transition from offline -> online
```

The key distinction is:

```text
automatic recovery = potentially PASS
manual Ackline launch required = FAIL
```

Record unusual delay rather than immediately inventing a workaround.

---

## 32. Force Stop Test

Do not include explicit Android Force Stop in the normal reliability matrix.

Force Stop is separate Android behavior.

If tested:

```text
document separately
do not confuse with Recents swipe/background behavior
```

Do not modify the application to defeat Force Stop semantics.

---

## 33. ColorOS Escalation

No ColorOS workaround during initial implementation.

If a stock test fails:

1. reproduce the exact case;
2. record network/app/screen state;
3. confirm FCM sender acceptance;
4. inspect bounded Logcat/Play Services evidence;
5. determine whether `onMessageReceived` ran;
6. compare behavior before and after manually opening Ackline;
7. use current Android/FCM/Oppo evidence;
8. consult Gemini Android Studio for Android/OEM-specific diagnosis if useful;
9. propose the smallest mitigation only after evidence exists.

Do not immediately:

```text
disable all battery optimizations
require lock-in-recents
add foreground service
create custom persistent socket
```

Those would compromise the purpose of the reliability gate.

---

## 34. QA Record Format

Maintain a simple manual record:

```text
notification_id:
level:
sent_at:
network:
screen:
app state:
received/displayed:
latency:
result:
notes:
```

Example:

```text
notification_id: wifi-mobile-004
level: important
sent_at: 2026-08-29T20:14:02Z
network: Wi-Fi -> mobile
screen: off
app state: background
received/displayed: 2026-08-29T20:14:05Z
latency: ~3s
result: PASS
notes: no app reopen
```

Use fake payloads only.

---

## 35. Review Requirements

After implementation and automated validation, run:

```text
/local-review
Qwen3.8 27B AWQ 5bpw + Lightning MTP
```

Reviewer is read-only.

Review must focus on:

```text
FCM remains data-only
payload validation
priority mapping
notification channel mapping
native notification independence from UI
Firebase boundary
secret hygiene
scope discipline
absence of premature future architecture
tests are meaningful
dark-mode fix preserved
```

Verdict:

```text
PASS
PASS_WITH_NOTES
BLOCKED
```

Blocking findings are resolved before the full reliability gate is considered complete.

---

## 36. AI Route

### Planner

```text
ChatGPT + GitHub
```

Planning is complete in these documents.

### Preflight / Builder

```text
/local-build
Qwen3.8 27B 4bit + DFlash2
```

Preflight stops before editing.

### Quality fallback

```text
/local-quality
Qwen3.8 27B 5bit + DFlash2
```

Use only if the default builder cannot complete a bounded implementation cleanly.

### Android specialist

```text
Gemini Android Studio
```

Use for concrete:

```text
Gradle
manifest
Firebase runtime
notification runtime
Doze
Play Services
ColorOS
```

problems.

### Independent reviewer

```text
/local-review
Qwen3.8 27B AWQ 5bpw + Lightning MTP
```

No edits.

### Frontier escalation

```text
GPT-5.6 Sol Codex
```

Not expected for normal Phase 1 work.

Use only if a genuinely high-risk or unusually difficult engineering issue appears.

### Final review

```text
ChatGPT + GitHub
```

after the user commits and pushes.

---

## 37. Completion Criteria

All must be true:

```text
Phase 1 code remains small
FCM remains data-only
native Android notifications work
level validation works
FCM priority mapping correct
Android channel mapping correct
malformed input fails safely
credentials remain external
automated validation passes
initial Oppo smoke tests pass
Wi-Fi baseline passes
mobile baseline passes
Wi-Fi -> mobile passes
mobile -> Wi-Fi passes
send-during-transition recovers automatically
airplane-mode recovery works automatically
screen-off test passes
IMPORTANT Doze test passes
multi-hour normal-use test passes
no manual Ackline reopening required
no unjustified ColorOS workaround
independent review passes
final GitHub review PASS
```

---

## 38. Phase Exit

After final PASS:

```text
1-fcm-reliability
        ↓
merge
        ↓
dev
```

Then create the Phase 2 branch from updated `dev`.

Phase 2 is:

```text
Persistent Inbox and Alert Detail
```

Only then introduce:

```text
Room
local persistent truth
notificationId deduplication
Inbox
Pendientes / Vistas
Alert detail
```

Do not begin those features inside Phase 1.

---

## 39. Suggested Commits

Planning:

```text
docs: plan Phase 1 FCM reliability gate
```

Implementation:

```text
feat: prove FCM background delivery
```

If additional fixes are discovered during physical QA, keep them narrowly scoped and separately understandable where useful.

Do not commit automatically.

The user owns commits and pushes.