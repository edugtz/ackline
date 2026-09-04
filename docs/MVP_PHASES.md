# Ackline — MVP Phases

This file is the detailed MVP roadmap.

`docs/CURRENT_PHASE.md` remains the only active implementation scope.

Global principle:

```text
FCM is the realtime transport.
Room is the device source of truth.
Hermes SQLite is the server source of truth.
Acknowledgment is explicit.
Reliability is proven before product expansion.
```

---

## Phase 0 — Project Foundation and FCM Registration

### Objective

Create the smallest real Android project that builds, installs, registers through current FCM/FID APIs, and can be targeted from the Mac with fake data.

### Product Quality Goal

A boring, stable baseline with no architecture experiments and no sensitive production data.

### Recommended AI Route

```text
Planning: ChatGPT
Android/Firebase project setup: Gemini Android Studio when platform/tooling is involved
Bounded Kotlin/Python implementation: /local-build
Independent review: /local-review if needed
Final pushed-branch review: ChatGPT + GitHub
```

### Tasks

- Create/initialize Android project and `dev` integration branch.
- Create one implementation branch for Phase 0.
- Kotlin + Jetpack Compose + Material 3.
- Target the current appropriate Android SDK/API at implementation time.
- Add only Firebase Messaging dependencies needed for the spike.
- Configure Firebase Android app.
- Implement current FID registration callback/handling.
- Minimal setup/debug surface that can show/copy the FID.
- Handle/understand notification runtime permission for the target Android version.
- Create a minimal Mac/Python sender spike with Firebase Admin SDK.
- Use only fake/non-sensitive data.
- Verify one data-only test message reaches app code.

### Do Not Do

- No Room.
- No inbox.
- No ACK.
- No E2EE implementation yet.
- No real personal alert contents.
- No UI polish beyond a clean setup surface.
- No Firestore/Auth/Analytics/Cloud Functions.
- No persistent socket.
- No foreground service.
- No future-phase packages/classes just to "prepare".

### Acceptance Criteria

- Project opens/builds.
- APK installs and launches.
- Firebase configuration is valid.
- Current FID registration succeeds.
- FID can be copied without depending on noisy logs.
- Mac sender can target the device with a fake data message.
- App code receives the data message.
- No deprecated token-first design is the primary registration path.
- Secrets are outside source control.

### Validation Commands

At minimum:

```bash
./gradlew clean assembleDebug
```

If lint/tests exist in Phase 0, run them too.

### Manual QA Checklist

- Install APK on Oppo.
- Launch app.
- Grant/deny notification permission as needed to understand behavior.
- Confirm FID appears.
- Copy FID.
- Send one fake data message from Mac.
- Confirm app receives it.

### Suggested Commit

```text
chore: add Android and FCM foundation
```

---

## Phase 1 — FCM Transport Reliability Gate

### Objective

Prove that FCM solves the specific ntfy failure mode: normal connectivity transitions must not require manually opening the app to restore push.

### Product Quality Goal

No product expansion until transport reliability is demonstrated on the real Oppo.

### Recommended AI Route

```text
Test design / acceptance: ChatGPT
Bounded implementation: /local-build or /local-quality
Android/FCM/Doze/ColorOS diagnosis: Gemini Android Studio
Independent review: /local-review
Final GitHub review: ChatGPT + GitHub
```

### Tasks

- Implement `FirebaseMessagingService` for data-only test messages.
- Create native Android notifications locally.
- Add minimal structured timestamps for send/receive/display debugging.
- Introduce notification channels needed for realistic priority testing.
- Use high priority only for user-visible IMPORTANT/URGENT test cases.
- Create a repeatable Mac sender command/script for controlled tests.
- Run physical-device reliability matrix.

### Do Not Do

- Do not add Room yet.
- Do not add ACK.
- Do not add E2EE yet.
- Do not explain away a manual-open requirement.
- Do not add a foreground service or custom socket.
- Do not request broad battery exemptions before testing default behavior.
- Do not Force Stop the app and classify that as normal background behavior.

### Acceptance Criteria

Required pass cases:

1. Foreground baseline.
2. Background.
3. Removed from Recents without Force Stop.
4. Screen off.
5. Wi-Fi.
6. Mobile data.
7. Wi-Fi → mobile.
8. Mobile → Wi-Fi.
9. Message sent during the network transition.
10. Airplane mode → send → restore connectivity without opening app.
11. Device idle / Doze test.
12. Multiple sends over a multi-hour real-use window.

Critical fail condition:

```text
mobile data works
+
alert does not arrive
+
opening Ackline makes it arrive
=
FAIL
```

Any ColorOS-specific setting required must be documented with evidence.

### Validation Commands

```bash
./gradlew assembleDebug
```

Use ADB/Logcat only as needed for reproducible platform diagnostics.

### Manual QA Checklist

Physical Oppo testing is mandatory. Record:

```text
test case
send timestamp
receive timestamp
network state
screen/app state
result
```

### Suggested Commit

```text
feat: prove FCM background delivery
```

---

## Phase 2 — Persistent Inbox and Alert Detail

### Objective

Make received alerts durable and useful even after the tray notification is dismissed.

### Product Quality Goal

The first real UI must already feel like a lightweight personal inbox, not a generated CRUD list.

### Recommended AI Route

```text
Architecture/product: ChatGPT
Data + baseline UI: /local-quality
Independent review: /local-review
If UI remains generic: GPT-5.6 Luna Medium or equivalent premium builder
Android/Room/navigation runtime issue: Gemini Android Studio
Screenshot review: ChatGPT
```

### Tasks

- Add Room.
- Define `AlertEntity`.
- `notificationId` primary key.
- App-owned `IncomingAlertEnvelope`.
- Idempotent insert.
- Persist before relying on UI/tray.
- Inbox screen.
- `Pendientes` / `Vistas`.
- Alert detail screen.
- Baseline deliberate Material 3 design.
- App restart/process persistence.
- Reboot persistence for stored data.

### Do Not Do

- No remote ACK yet.
- No E2EE yet.
- No search.
- No analytics.
- No complex settings.
- No card-heavy generic dashboard layout.
- No schema fields for speculative future features.

### Acceptance Criteria

- Same `notificationId` delivered repeatedly creates one row.
- App restart preserves alerts.
- Tray swipe does not remove local alert.
- Pending remains pending until explicit ACK.
- Detail shows full content.
- Empty states and list hierarchy are intentional.
- UI is acceptable after screenshot/manual review.

### Validation Commands

Typical critical gate:

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
```

### Manual QA Checklist

- Receive multiple alerts.
- Reopen app.
- Swipe tray item.
- Verify inbox item persists.
- Verify pending/viewed filter behavior.
- Open detail.
- Restart app.
- Reboot device and verify stored data remains.

### Suggested Commit

```text
feat: add persistent alert inbox
```

---

## Phase 3 — Explicit Local Acknowledgment

### Objective

Implement exact `Visto` semantics locally.

### Product Quality Goal

Acknowledgment behavior must be predictable and impossible to trigger accidentally through ordinary viewing/dismissal.

### Recommended AI Route

```text
Plan: ChatGPT
Implementation: /local-build
Review: /local-review
Notification-action runtime issue: Gemini Android Studio
Final GitHub review: ChatGPT
```

### Tasks

- Add `Visto` notification action.
- Add `Marcar como visto` in app/detail.
- One shared `acknowledge(notificationId)` operation.
- Persist `acknowledgedAt`.
- Set `ackSyncState = PENDING` for later remote sync.
- Cancel/update corresponding Android notification.

### Do Not Do

- Do not call remote ACK yet.
- Do not make notification swipe acknowledge.
- Do not make app/detail open acknowledge.
- Do not add read-receipt heuristics.

### Acceptance Criteria

False-ACK cases remain pending:

```text
delivery
tray display
swipe/dismiss
app launch
detail open
```

True-ACK cases:

```text
notification [Visto]
in-app [Marcar como visto]
```

### Validation Commands

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
```

### Manual QA Checklist

Test every false-ACK and true-ACK case on the physical device.

### Suggested Commit

```text
feat: add explicit local acknowledgment
```

---

## Phase 4 — Durable Remote ACK Sync

### Objective

Synchronize acknowledgment to Hermes without making local UX depend on Mac/Tailscale availability.

### Product Quality Goal

A user acknowledgment must never be lost because the Mac or network was temporarily unavailable.

### Recommended AI Route

```text
Architecture: ChatGPT
Bounded Android/Python implementation: /local-quality
Correctness/concurrency review: /cloud-hy3 or /cloud-ds-max when useful
WorkManager/runtime: Gemini Android Studio
Independent review: /local-review
Final GitHub review: ChatGPT
```

### Tasks

- Minimal ACK HTTP contract.
- Reuse or create the smallest Mac ACK endpoint.
- Idempotent ACK request.
- WorkManager unique sync work.
- Network constraint.
- Retry/backoff for transient failures.
- Persist sync state/error metadata.
- Drain pending ACKs rather than permanently scheduling one worker per alert.

### Do Not Do

- Do not require remote success before local acknowledgment.
- Do not build a full sync service.
- Do not add public cloud infrastructure.
- Do not create aggressive polling.
- Do not log auth secrets.

### Acceptance Criteria

- Hermes reachable → ACK syncs.
- Tailscale off → local ACK remains; remote state stays pending.
- Mobile network unstable → no ACK loss.
- Tailscale returns → pending ACK eventually syncs.
- Duplicate ACK remains idempotent.
- Permanent auth/protocol error does not create a tight infinite retry loop.

### Validation Commands

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
```

Run Python/server tests or smoke checks defined by the ACK endpoint implementation.

### Manual QA Checklist

- ACK online.
- ACK with Tailscale off.
- Restart app while pending.
- Restore Tailscale.
- Confirm eventual synced state.

### Suggested Commit

```text
feat: add durable Hermes acknowledgment sync
```

---

## Phase 5 — Application-Level E2EE

### Objective

Prevent sensitive Hermes alert content from being sent through FCM in plaintext.

### Product Quality Goal

Privacy protection must be based on standard authenticated encryption and a simple auditable key design.

### Recommended AI Route

```text
Architecture/threat model: ChatGPT + current official docs
Implementation: /local-quality
Keystore/platform: Gemini Android Studio
Security/correctness review: /cloud-hy3 and/or GPT-5.6 Sol Codex
Independent local review: /local-review
Final GitHub review: ChatGPT
```

### Tasks

- Define versioned encrypted envelope.
- AES-256-GCM unless current review selects an equally standard better fit.
- `kid`/key identifier.
- Unique nonce/IV requirements.
- Secure Android key storage.
- Secure Mac key storage outside repo.
- Reject malformed/tampered ciphertext.
- Ensure logs remain secret-free.
- Add deterministic test vectors/round-trip tests where practical.

### Do Not Do

- Do not invent crypto.
- Do not put keys in source.
- Do not send real secrets to AI/cloud tools.
- Do not enable real personal payloads before the security gate passes.
- Do not build multi-device key infrastructure.

### Acceptance Criteria

- FCM-visible payload does not reveal title/message.
- Wrong key fails safely.
- Modified ciphertext fails authentication.
- Nonce reuse is prevented by design.
- Keys never enter repo/logs/prompts.
- Real notification flow still works after encryption.

### Validation Commands

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
```

Run corresponding Python crypto tests/smoke checks.

### Manual QA Checklist

- Valid encrypted payload arrives and displays.
- Tampered payload is rejected without crash.
- Wrong-key test is rejected.
- Inspect debug output for secret leakage.

### Suggested Commit

```text
feat: encrypt Hermes notification payloads
```

---

## Phase 6 — Hermes Outbox / Sender Integration

### Objective

Replace the ntfy transport in the existing Hermes notification outbox without weakening its delivery semantics.

### Product Quality Goal

Transport replacement must preserve persistent outbox semantics and never falsely record a failed send as successful.

### Recommended AI Route

```text
Architecture: ChatGPT
Repository discovery/preflight: /cloud-mimo or /local-build
Implementation: /local-quality
Technical review: /cloud-ds-max
Correctness review when needed: /cloud-hy3
Independent local review: /local-review
Final GitHub review: ChatGPT
```

### Tasks

- Add final `firebase_sender.py` or equivalent.
- Use current FID targeting.
- Service-account credentials outside repo/prompts/logs.
- Encrypt before send.
- Integrate with `notification_state.py`.
- Mark transport accepted only after FCM accepts.
- Preserve outbox retries.
- Surface invalid-device/permanent errors.
- Redact sensitive operational identifiers.

### Do Not Do

- Do not move Personal Admin logic into Android.
- Do not let the LLM handle Firebase secrets.
- Do not mark `sent_at` on failure.
- Do not remove persistent outbox semantics.
- Do not add new SaaS infrastructure.

### Acceptance Criteria

- FCM success records transport accepted correctly.
- Send failure remains queued/retryable.
- No false sent state.
- Duplicate retry remains safe on Android.
- Invalid FID becomes actionable instead of infinite retry.
- ntfy path can remain available until replacement gate passes.

### Validation Commands

Run Android gate plus targeted Hermes/Python tests for changed files.

### Manual QA Checklist

Trigger a real Hermes-generated **non-sensitive or safely encrypted** test alert through the actual outbox path.

### Suggested Commit

```text
feat: route Hermes notification outbox through FCM
```

---

## Phase 7 — Recovery and Reconciliation (Redesign V2)

### Objective

Make FCM the realtime path without treating one push attempt as the only path to recover a pending alert.

### Product Quality Goal

A rare missed/dropped transport event must not permanently erase a Hermes pending notification.

### Redesign V2 Context

Phase 7 Change D uncovered a **design failure** — the periodic WorkManager safety net was a dependency that should not exist. This is a planning conclusion, not a product or runtime failure. Hermes bounded FCM redelivery replaces periodic WorkManager as the primary recovery safety net.

### Recommended AI Route

```text
Architecture: ChatGPT
Implementation: /local-quality
Correctness/data review: /cloud-hy3 or /cloud-ds-max
Android background behavior: Gemini Android Studio
Independent review: /local-review
Final GitHub review: ChatGPT
```

### Tasks

- Implement Hermes bounded redelivery of sent/unacknowledged notifications (same `notification_id`, NORMAL priority, 6-hour window, 2-hour minimum gap).
- Implement `GET /notifications/pending` recovery contract.
- Reconcile into Room by `notificationId` via canonical `AlertIngestion`.
- Use `onDeletedMessages()` as a recovery signal where appropriate.
- Handle FID changes/re-pair requirement.
- Recover ACK backlog after successful recovery GET.
- Cancel/retire the installed periodic WorkManager unique work.
- Event-driven triggers only (startup, onDeletedMessages, FID change) — no periodic WorkManager.

### Do Not Do

- Do not turn reconciliation into constant polling.
- Do not use periodic WorkManager as a recovery path.
- Do not duplicate Hermes business logic.
- Do not build a general sync engine.
- Do not create server accounts or cloud database.
- Do not add delivery-receipt protocol.
- Do not migrate Hermes DB or Room schema.

### Acceptance Criteria

- Hermes bounded redelivery sends same `notification_id` for sent/unacknowledged notifications.
- Ackline Room `INSERT IGNORE` absorbs duplicate delivery harmlessly.
- One native notification per unique `notificationId`.
- Event-driven recovery inserts missing alerts without manual app open.
- Later duplicate FCM/redelivery remains harmless.
- Reconciliation does not change acknowledged alerts incorrectly.
- ACK backlog remains consistent.
- Periodic WorkManager unique work is cancelled.
- No acceptance gate requires waiting for a periodic WorkManager cycle.

### Validation Commands

Android critical gate plus targeted ACK/reconciliation tests.

### Manual QA Checklist

- Verify Hermes bounded redelivery reaches Ackline.
- Simulate a missing local alert and verify event-driven recovery without duplicates.
- Verify periodic WorkManager is no longer enqueued.
- Verify duplicate FCM/redelivery is harmless.

### Suggested Commit

```text
feat: add Hermes redelivery and event-driven recovery
```

---

## Phase 8 — Real-World Replacement Gate

### Objective

Decide with evidence whether ntfy can be retired.

### Product Quality Goal

Use normal daily behavior, not a laboratory-only success, as the final reliability decision.

### Recommended AI Route

```text
Test plan + result analysis: ChatGPT
Platform investigation if needed: Gemini Android Studio
Targeted fixes: route by problem category
Final high-risk engineering review: GPT-5.6 Sol Codex if justified
Final GitHub review: ChatGPT
```

### Tasks

Run a multi-day real-device test on the Oppo under normal use.

Include:

- home Wi-Fi;
- leaving home;
- mobile coverage transitions;
- screen-off idle;
- overnight idle;
- charging/not charging;
- app out of Recents;
- temporary airplane mode;
- Mac/Tailscale unavailable for ACK;
- Mac/Tailscale restored;
- device reboot;
- repeated cron-triggered alerts.

Record at minimum:

```text
notification_id
Hermes created_at
FCM accepted_at
Android received_at
user-visible arrival observation
acknowledged_at
ack_synced_at
```

Development/test logs are sufficient. Do not add a production analytics SDK.

### Do Not Do

- Do not retire ntfy after a single successful test.
- Do not hide failures with foreground-service hacks.
- Do not call a manual-open requirement acceptable.
- Do not add analytics just for test metrics.

### Acceptance Criteria

ntfy may be retired only if:

- no normal-use scenario requires manually opening the app to restore push;
- no ACKs are lost;
- duplicate delivery is harmless;
- offline recovery works;
- reconciliation works;
- ColorOS behavior is understood/documented;
- reliability is clearly better than ntfy.

### Validation Commands

No single command replaces this physical-device gate.

Run standard Android build/test gate before starting the test window.

### Manual QA Checklist

The multi-day test is the manual QA.

### Suggested Commit

```text
test: complete Hermes notification replacement gate
```

---

## Phase 9 — MVP UX / Product Polish

### Objective

Make the small product feel deliberate and durable enough for daily use.

### Product Quality Goal

The app should feel like a purpose-built personal inbox, not a generated Compose demo.

### Recommended AI Route

```text
Screenshot/product review: ChatGPT
Primary polish builder: /local-quality
If still generic/CRUD-quality: GPT-5.6 Luna Medium or equivalent premium builder
Android runtime/Compose issue: Gemini Android Studio
Independent review: /local-review
Final screenshot + GitHub review: ChatGPT
```

### Tasks

- Review screenshots on the physical Oppo.
- Tune inbox density/spacing.
- Refine severity hierarchy.
- Refine pending/viewed treatment.
- Refine detail typography.
- Refine timestamp treatment.
- Refine notification action presentation.
- Improve empty states.
- Verify light/dark theme behavior if both are supported.
- Accessibility/touch-target pass.
- Remove debug-only setup noise from normal user flow.
- Incorporate useful user feedback/reference screenshots.

### Do Not Do

- No feature expansion disguised as polish.
- No unnecessary animations.
- No redesign for novelty.
- No card soup.
- No analytics/search/multi-device unless explicitly promoted from post-MVP.

### Acceptance Criteria

- Inbox is fast to scan.
- `Visto` is obvious but not visually dominant.
- Pending/viewed is unambiguous.
- Detail is readable.
- Severity treatment is consistent.
- Empty states are useful.
- UI does not look vibe-coded.
- User accepts the screenshots/real-device feel.

### Validation Commands

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
```

### Manual QA Checklist

Real-device visual and interaction review is mandatory.

### Suggested Commit

```text
style: polish Hermes notification inbox
```
