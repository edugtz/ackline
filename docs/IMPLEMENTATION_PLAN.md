# Phase 0 — Project Foundation and FCM Registration Implementation Plan

## 1. Status

**PLANNED — NOT IMPLEMENTED**

Phase:

```text
0 — Project Foundation and FCM Registration
```

Planned implementation branch:

```text
0-fcm-foundation
```

Base branch:

```text
dev
```

---

## 2. Objective

Create the minimum real Android + Firebase Messaging foundation needed to answer:

```text
Can this device register with current FCM/FID APIs,
and can the Mac target it with a fake data message?
```

Do not solve persistence, acknowledgment, encryption, or production Hermes integration in this phase.

---

## 3. Product Quality Goal

The result should be intentionally small:

```text
one Android app
one setup/debug surface
one messaging service
one fake sender path
```

No placeholder architecture for future work.

No AI-generated dashboard look.

---

## 4. Preflight Requirements

Before editing, confirm:

1. Whether the repository already exists.
2. Current branch and git status.
3. `dev` exists or must be created.
4. Android Studio/SDK/JDK versions available.
5. Final package/application ID.
6. Current Firebase Android SDK setup requirements.
7. Current Firebase Messaging/FID API surface.
8. Whether Firebase BoM is appropriate for the chosen dependency setup.
9. Exact location for the fake sender.
10. Exact local location/environment variable for the service-account credential.

Do not guess package paths before project creation.

---

## 5. Repository / Branch Setup

If creating a new repo:

```text
initialize repository
create initial dev integration branch
create 0-fcm-foundation from dev
```

If repo already exists:

```bash
git status --short --untracked-files=all
```

Require a clean or intentionally understood state before implementation.

---

## 6. Android Project Setup

Create a standard single-module Android project.

Requirements:

```text
Kotlin
Jetpack Compose
Material 3
single :app module
current appropriate compileSdk/targetSdk
minimum SDK chosen conservatively for personal use
Gradle version catalog if generated/appropriate
```

Do not introduce:

```text
Hilt
Retrofit
Room
WorkManager
Navigation graph unless genuinely needed for the one setup surface
multi-module structure
```

A single `MainActivity` with one composable setup surface is sufficient.

---

## 7. Firebase Setup

### Firebase project/app

- Create or select the Firebase project.
- Register the final Android application ID.
- Add standard Android Firebase configuration.
- Add only the Messaging dependency required for Phase 0.
- Follow current official setup at implementation time.

### Registration

Use the current FID registration mechanism.

The app should maintain a simple setup state:

```text
registrationState
installationId/FID
notificationPermissionState
lastFakeMessageSummary (optional, non-sensitive)
```

Do not make deprecated token APIs the primary design.

---

## 8. Minimal Setup Screen

The screen should be clean but intentionally utilitarian.

Suggested content:

```text
Hermes Notifications

Push setup

Notification permission     Granted / Not granted
FCM registration            Ready / Waiting / Error
Device ID                   <FID>
                            [Copy]

Last test message           optional
```

No cards-inside-cards or dashboard metrics.

No production inbox UI.

Copying the FID should not require Logcat.

---

## 9. Minimal Messaging Service

Create a small `FirebaseMessagingService`.

Responsibilities in Phase 0:

```text
receive data message
validate that expected test keys exist
record/log non-sensitive test metadata
surface enough state to prove app code received it
```

It does not:

```text
persist Room alerts
acknowledge
decrypt
show production inbox
call Hermes
```

The production local Android notification behavior belongs to Phase 1.

If showing a minimal test notification materially helps Phase 0 verification, keep it explicitly development-only and do not pre-implement full severity/ACK behavior.

---

## 10. Mac / Python Sender Spike

Use current Firebase Admin SDK.

Responsibilities:

```text
load service credentials externally
read target FID from protected local config or explicit safe input
send one data-only fake message
return transport success/error
never print credential contents
```

Fake payload example:

```json
{
  "protocol": "1",
  "notification_id": "phase0-test-001",
  "level": "important",
  "title": "FCM test",
  "message": "Non-sensitive Phase 0 payload",
  "created_at": "2026-08-28T00:00:00Z"
}
```

Do not use real email/calendar/task content.

---

## 11. Secret Handling

Service-account credentials:

```text
outside Android
outside repository
outside prompts
outside logs
```

Recommended pattern:

```text
GOOGLE_APPLICATION_CREDENTIALS=/protected/path/service-account.json
```

or another current Admin SDK credential mechanism validated at implementation time.

The Firebase Android config file is not the service-account private key; still review repository hygiene before committing.

---

## 12. Exact Files — Discovery First

Because the repo may not exist yet, exact package paths must be confirmed during bootstrap.

Expected production files are limited to the project skeleton plus roughly:

```text
MainActivity.kt
SetupScreen.kt
HermesMessagingService.kt
small setup/registration state holder if needed
```

Expected non-Android sender file:

```text
one firebase sender spike
```

Do not create:

```text
database/
repository/
ack/
security/
inbox/
detail/
sync/
```

packages until their phases require them.

---

## 13. Implementation Order

1. Initialize/inspect repository.
2. Establish `dev`.
3. Create `0-fcm-foundation`.
4. Create Android project.
5. Confirm clean `assembleDebug`.
6. Register Firebase Android app.
7. Add Firebase Messaging dependency/plugin/config.
8. Implement FID registration handling.
9. Implement minimal setup surface.
10. Implement minimal messaging service.
11. Build/install on Oppo.
12. Copy FID.
13. Create/configure fake Python sender.
14. Send fake data message.
15. Confirm app code receives it.
16. Run validation.
17. Run manual QA.
18. Independent review if used.
19. User commits/pushes.
20. ChatGPT reviews pushed branch through GitHub.
21. Merge only after PASS.

---

## 14. Risks / Build Traps

### Firebase API drift

The exact FID callback/API must be checked against current official docs at implementation time.

Do not copy a stale tutorial.

### Android permission behavior

Notification runtime permission behavior must match the actual target/device API.

### ColorOS

Do not diagnose Phase 1 reliability during Phase 0 unless Phase 0 cannot receive even the baseline message.

### Secret leakage

Do not paste service-account JSON or real FID into cloud AI conversations or public GitHub.

### Scope creep

It is tempting to add Room/inbox immediately. Do not.

### Over-abstracted transport

Do not create a generic multi-provider plugin system. One small boundary is enough.

---

## 15. Validation Commands

Minimum:

```bash
./gradlew clean assembleDebug
```

If configured:

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

Git hygiene:

```bash
git status --short --untracked-files=all
git diff --check
```

Python sender:

- run one fake send;
- verify successful Admin SDK response;
- verify app receives message;
- verify no secret is printed.

---

## 16. Manual QA

On the Oppo:

```text
PASS — APK installs
PASS — app opens
PASS — setup screen renders
PASS — notification permission state is understandable
PASS — FCM registration becomes ready
PASS — FID appears
PASS — copy FID works
PASS — fake Mac sender succeeds
PASS — app code receives fake data message
PASS — no crash
PASS — no sensitive payload used
```

Phase 0 does not require the Wi-Fi/5G/Doze matrix.

---

## 17. AI Implementation / Review Route

### Planning

```text
ChatGPT + GitHub
```

### Discovery / preflight

Preferred options:

```text
/cloud-mimo
```

for cheap discovery/preflight when the phase is already planned, or:

```text
/local-build
```

for same-agent local preflight + implementation.

Preflight stops for approval.

### Default builder

```text
/local-build
Qwen3.8 27B 4bit + DFlash2
```

### Quality local fallback

```text
/local-quality
Qwen3.8 27B 5bit + DFlash2
```

### Android platform specialist

```text
Gemini Android Studio
```

Use for Firebase plugin/Gradle/manifest/FID/runtime issues.

### Independent reviewer

```text
/local-review
Qwen3.8 27B AWQ 5bpw + Lightning MTP
```

Review only; no edits.

### Frontier escalation

```text
GPT-5.6 Sol Codex
```

Not expected for normal Phase 0 work. Use only if tooling/repo state becomes unusually risky.

### Final review

After user commit/push:

```text
ChatGPT + GitHub
```

End with:

```text
PASS
PASS_WITH_NOTES
BLOCKED
```

---

## 18. Completion Criteria

All must be true:

```text
single-module Android project exists
dev + phase branch workflow established
assembleDebug passes
APK installs
Firebase setup valid
FID registration works
FID copy works
fake data message reaches app code
service-account secrets external
no sensitive payload used
no future-phase work
manual QA passes
final pushed-branch GitHub review PASS
```

---

## 19. Suggested Commit

```text
chore: add Android and FCM foundation
```

Do not commit automatically.

The user owns commit and push.
