# Current Phase

## Status

**PLANNED — READY FOR REPOSITORY BOOTSTRAP / PREFLIGHT**

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

If the repository does not exist yet, initialize it first, establish `dev` as the integration branch, then create the Phase 0 branch.

---

## Objective

Create the smallest real Android project that:

```text
builds
installs
registers with current Firebase Messaging/FID APIs
exposes the current FID for manual pairing
receives one fake FCM data message in app code
```

Phase 0 does **not** prove reliability yet.

Phase 1 is the actual background/network-transition reliability gate.

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

Phase 0 uses fake/non-sensitive payloads only.

---

## In Scope

### Repository / Project

- Initialize Android project.
- Establish `dev` integration branch.
- Create `0-fcm-foundation` branch.
- Kotlin.
- Jetpack Compose.
- Material 3.
- Single `app` module.
- Set final package/application ID during bootstrap and then treat it as authoritative.

### Firebase

- Create/select Firebase project.
- Register Android app.
- Add only Firebase Messaging dependencies required for Phase 0.
- Use current Firebase Installation ID registration callback/flow.
- Do not build around deprecated registration-token APIs as the primary design.
- Add required Firebase config file locally/in repo only if it is appropriate and non-secret according to Firebase's standard Android setup; never add service-account credentials to Android.

### Android

- Minimal `MainActivity`.
- Minimal setup/debug screen.
- Show:
  - app is running;
  - notification permission state if relevant;
  - FCM registration state;
  - current FID;
  - copy FID action.
- Minimal `FirebaseMessagingService`.
- Receive one data-only fake message.
- Log only non-sensitive diagnostic metadata.

### Mac Sender Spike

- Minimal Python sender using current Firebase Admin SDK.
- Read service-account credentials from a protected external location/environment.
- Target the copied FID.
- Send fake/non-sensitive data only.
- Print success/failure without printing credentials or sensitive identifiers unnecessarily.

---

## Out of Scope

Do **not** add:

```text
Room
Inbox UI
Alert detail
Visto semantics
ACK client/server
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
persistent WebSocket/MQTT
```

Do not pre-create future-phase packages/classes merely to make the repo look complete.

---

## Authoritative Technical Decisions

### Transport

```text
FCM data messages
```

not automatic FCM notification-message handling.

### Device Addressing

Use the current FID-based registration path.

### Transport Boundary

Firebase-specific code must terminate at a small app boundary.

Phase 0 may define an app-owned test envelope, but must not create the full production data model early.

### Security

```text
Firebase Admin service-account credentials
!= Android app config
```

Service-account JSON/private keys never enter the Android app or repository.

Phase 0 payloads are fake/non-sensitive because E2EE is Phase 5.

---

## Expected Files

Exact paths are confirmed during project bootstrap.

Likely Android files:

```text
settings.gradle.kts
build.gradle.kts
gradle/libs.versions.toml
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/.../MainActivity.kt
app/src/main/java/.../push/AcklineMessagingService.kt
app/src/main/java/.../feature/setup/SetupScreen.kt
```

Possible sender location outside or alongside the Android repo, depending on final repository organization:

```text
tools/firebase_sender.py
or
~/.hermes/personal-admin/firebase_sender.py
```

Do not create both without reason.

---

## Acceptance Criteria

Phase 0 is complete when:

- Android project opens/builds.
- Debug APK installs on the Oppo.
- App launches without crash.
- Firebase Android configuration is valid.
- Current FID registration succeeds.
- FID is visible/copyable in the setup surface.
- Notification permission behavior is understood for the device/API level.
- Python sender can target the copied FID.
- One fake FCM **data** message reaches app code.
- No real personal data was used.
- No service-account secret appears in repository/logs/prompts.
- No future-phase feature was implemented.

---

## Validation Commands

At minimum:

```bash
./gradlew clean assembleDebug
```

If tests/lint are already configured:

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

Python sender validation should use a non-sensitive fake payload.

Before commit:

```bash
git status --short --untracked-files=all
git diff --check
```

---

## Manual QA Checklist

On the Oppo:

```text
[ ] APK installs
[ ] app opens
[ ] setup surface renders cleanly
[ ] notification permission state is visible/understood
[ ] FCM registration reports healthy
[ ] current FID appears
[ ] FID copy works
[ ] fake Mac send succeeds
[ ] app code receives fake data payload
[ ] no sensitive data appears in logs
```

Do not run the full Wi-Fi/5G reliability matrix in Phase 0; that is Phase 1.

---

## AI Implementation / Review Route

### Planning / architecture

```text
ChatGPT + GitHub
```

### Builder preflight

Either:

```text
/local-build
```

or a cheap repository-discovery/preflight route such as:

```text
/cloud-mimo
```

once the repo exists.

Preflight must STOP for approval before editing unless the user explicitly authorizes direct implementation.

### Primary Android implementation

```text
/local-build
Qwen3.8 27B 4bit + DFlash2
```

### Higher-quality local fallback

```text
/local-quality
Qwen3.8 27B 5bit + DFlash2
```

### Android/Firebase specialist

```text
Gemini Android Studio
```

Use it for Gradle/Firebase plugin/FID/manifest/runtime issues, not as default general builder.

### Independent review

```text
/local-review
Qwen3.8 27B AWQ 5bpw + Lightning MTP
```

Reviewer should not edit.

### Final review

After user commit/push:

```text
ChatGPT + GitHub
```

Result:

```text
PASS
PASS_WITH_NOTES
BLOCKED
```

Merge to `dev` only after PASS by default.

---

## Completion Criteria

All true:

```text
project builds
APK installs
FCM registration works
FID is copyable
fake data message reaches app code
sender secrets remain external
no sensitive data used
no Room/ACK/E2EE/future-phase work
validation passes
manual QA passes
independent review complete if used
pushed branch receives ChatGPT GitHub PASS
```

---

## Suggested Commit

```text
chore: add Android and FCM foundation
```

The user owns commit and push.
