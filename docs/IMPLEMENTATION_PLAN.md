# Phase 2 — Persistent Inbox and Alert Detail Implementation Plan

## 1. Status

**PLANNED — READY FOR PREFLIGHT**

Phase: `2 — Persistent Inbox and Alert Detail`

Implementation branch: `2-persistent-inbox`

Base branch: `dev`

---

## 2. Objective

Implement the minimum durable Android data and UI layers required to convert the proven FCM path into a persistent Ackline inbox.

Required end state:

FCM → `AcklineMessagingService` → `IncomingAlertEnvelope` → `AlertRepository` → Room → Inbox / Detail → native notification for newly inserted alerts only.

The implementation must preserve the Phase 1 FCM reliability behavior.

---

## 3. Existing Working Foundation

The repository already contains:

- single `:app` Android module;
- Kotlin / Compose / Material 3;
- Firebase Messaging;
- current FID registration;
- `SetupState` + `SetupScreen`;
- `AcklineMessagingService`;
- `AcklineNotificationManager`;
- three proven Android notification channels;
- Python Firebase Admin sender;
- Phase 1 payload validation;
- Phase 1 deterministic tests.

Phase 1 physical delivery passed on the Oppo.

Do not rebuild those pieces from scratch.

---

## 4. Preflight Is Mandatory

Before editing, inspect:

- `AGENTS.md`
- `docs/CURRENT_PHASE.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/ARCHITECTURE.md`
- `docs/PROJECT_SPEC.md`
- `docs/ACCEPTANCE_CRITERIA.md`
- `docs/AI_WORKFLOW.md`
- `docs/MVP_PHASES.md`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/AndroidManifest.xml`
- `MainActivity.kt`
- `SetupState.kt`
- `SetupScreen.kt`
- `AcklineMessagingService.kt`
- `AcklineNotificationManager.kt`
- `tools/firebase_sender.py`
- existing tests
- current git state

Preflight is read-only.

It must report:

1. exact files to modify;
2. exact files to create;
3. Room/KSP version compatibility;
4. dependency additions;
5. database schema;
6. manual dependency wiring;
7. receive/persist/notify sequencing;
8. duplicate behavior;
9. UI/navigation shape;
10. tests;
11. validation commands;
12. manual QA;
13. risks and doc/code mismatches.

Stop before editing.

---

## 5. Dependency Policy

Phase 2 is expected to require:

- AndroidX Room runtime;
- Room KTX only if actual APIs used justify it;
- Room compiler through KSP;
- Lifecycle ViewModel Compose;
- Lifecycle Runtime Compose.

Current project baseline to respect:

- AGP `9.3.2`
- Compose compiler `2.2.10`
- compile SDK `37`
- target SDK `36`
- min SDK `28`

Do not guess incompatible Room/KSP versions.

Preflight must verify the supported current combination.

Do not add merely for convenience:

- Hilt;
- Retrofit;
- WorkManager;
- Navigation Compose;
- kotlinx serialization;
- Robolectric;
- generic architecture libraries.

Navigation Compose requires explicit preflight justification.

---

## 6. KSP / Room Build Configuration

Expected changes:

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`

Add the minimum Room/KSP configuration required by the current toolchain.

Preferred database configuration:

- Room database version = `1`;
- `exportSchema = true`.

If current Room/KSP integration supports a clean schema export directory, configure it and check generated schema metadata into source control where appropriate.

Do **not** use `fallbackToDestructiveMigration()`.

No migrations are required while the schema remains version 1.

---

## 7. Backup Policy

Current manifest has backup enabled.

Phase 2 stores persistent alert contents.

Modify `app/src/main/AndroidManifest.xml` and set:

`android:allowBackup="false"`

Do not build backup/restore infrastructure.

Existing backup XML template files do not need to be deleted solely for cleanliness.

---

## 8. Manual Application Wiring

Expected new concept: `AcklineApplication`.

Preferred responsibility:

- create `AcklineDatabase` once;
- create `AlertRepository` once;
- expose the repository to app components.

Conceptually:

```kotlin
class AcklineApplication : Application() {
    val database by lazy { ... }
    val alertRepository by lazy { AlertRepository(database.alertDao()) }
}
```

Exact code is decided during implementation.

Register the Application class in the manifest.

Do not add Hilt, Koin, a service-locator framework, or a large `AppContainer` hierarchy.

---

## 9. App-Owned Alert Model

Create a small app-owned model rather than exposing Room or Firebase types across layers.

Expected concept: `Alert` and `AlertLevel`.

Potential path: `app/src/main/java/com/edu/ackline/model/Alert.kt`

Conceptually:

```kotlin
enum class AlertLevel {
    REMEMBER,
    IMPORTANT,
    URGENT,
}

data class Alert(
    val notificationId: String,
    val protocolVersion: Int,
    val level: AlertLevel,
    val title: String,
    val message: String,
    val createdAt: Instant,
    val receivedAt: Instant,
    val acknowledgedAt: Instant?,
)
```

Do not introduce a generic domain/use-case layer.

---

## 10. IncomingAlertEnvelope

Expected path: `app/src/main/java/com/edu/ackline/push/IncomingAlertEnvelope.kt`

Fields:

- `protocolVersion`
- `notificationId`
- `level`
- `title`
- `message`
- `createdAt`
- `receivedAt`

No Firebase SDK types.

---

## 11. Protocol Parser

Refactor the Phase 1 parser into the Phase 2 durable contract.

Input: `Map<String, String>`

Expected keys:

- `protocol`
- `notification_id`
- `level`
- `title`
- `message`
- `created_at`

Validation:

- `protocol == "1"`
- `notification_id` nonblank
- valid level
- title nonblank
- message nonblank
- `created_at` parses as `Instant`

Capture `receivedAt = Instant.now()` after the wire payload is accepted.

Malformed payload:

- rejection;
- no Room insert;
- no native notification;
- bounded generic log.

Do not add JSON serialization just for this string map.

---

## 12. Sender Contract

Modify the existing `tools/firebase_sender.py`.

Do not create another sender.

Phase 2 payload:

- `protocol = "1"`
- `notification_id`
- `level`
- `title`
- `message`
- `created_at`

Generate canonical UTC ending in `Z`.

Continue FCM priority:

| Level | Priority |
|---|---|
| remember | normal |
| important | high |
| urgent | high |

Continue FID targeting through `messaging.Message(..., fid=args.fid)`.

Continue external credential loading.

Do not print FID or credential contents.

---

## 13. Room Entity

Expected path: `app/src/main/java/com/edu/ackline/data/local/AlertEntity.kt`

Conceptual schema:

```kotlin
@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val notificationId: String,
    val protocolVersion: Int,
    val level: String,
    val title: String,
    val message: String,
    val createdAtEpochMillis: Long,
    val receivedAtEpochMillis: Long,
    val acknowledgedAtEpochMillis: Long?,
)
```

Use Room-friendly primitive storage.

Avoid type converters unless they materially simplify the implementation.

Normal Phase 2 incoming rows always use `acknowledgedAtEpochMillis = null`.

Do not add:

- `ackSyncState`
- `ackSyncedAt`
- `lastAckError`
- server IDs
- tags
- categories
- metadata blobs

---

## 14. DAO

Expected path: `app/src/main/java/com/edu/ackline/data/local/AlertDao.kt`

Minimum operations:

- `insertIgnore(alert)`
- `observePending()`
- `observeViewed()`
- `observeById(notificationId)` or equivalent

Ordering:

`ORDER BY createdAtEpochMillis DESC, receivedAtEpochMillis DESC`

Pending:

`WHERE acknowledgedAtEpochMillis IS NULL`

Viewed:

`WHERE acknowledgedAtEpochMillis IS NOT NULL`

Insertion must use conflict-ignore behavior and return enough information to know whether the row was newly inserted.

Do not query before insert for deduplication.

---

## 15. Database

Expected path: `app/src/main/java/com/edu/ackline/data/local/AcklineDatabase.kt`

Requirements:

```kotlin
@Database(
    entities = [AlertEntity::class],
    version = 1,
    exportSchema = true,
)
```

Single database instance.

Database name may be `ackline.db`.

No destructive fallback.

No migration class is needed until a schema version changes.

---

## 16. Repository

Expected path: `app/src/main/java/com/edu/ackline/data/AlertRepository.kt`

Responsibilities only:

- map `IncomingAlertEnvelope` → `AlertEntity`;
- insert incoming alert idempotently;
- report inserted vs duplicate;
- observe pending alerts as app-owned `Alert`;
- observe viewed alerts as app-owned `Alert`;
- observe/find alert by ID when needed.

No networking.

No ACK.

No WorkManager.

No Hermes logic.

No Firebase types.

A Boolean or tiny result enum such as `INSERTED` / `DUPLICATE` is enough.

Do not build a generic repository framework.

---

## 17. Receive Flow

Modify `AcklineMessagingService.kt`.

Required sequence:

RemoteMessage → validate/parse → `IncomingAlertEnvelope` → persist through `AlertRepository` → if INSERTED, `AcklineNotificationManager.show(...)` → if DUPLICATE, bounded diagnostic only.

Persistence must complete before native notification posting.

The service callback must remain bounded.

Preflight must explicitly inspect the safest simple Room call/threading pattern for current Firebase/Room APIs.

Do not solve a local database write by adding WorkManager.

Do not launch fire-and-forget work that can outlive the service callback without understanding its lifecycle.

If the correct threading/lifecycle approach is unclear, stop and escalate to the Android platform route rather than inventing concurrency architecture.

---

## 18. Notification Manager

Modify only as required: `AcklineNotificationManager.kt`.

Preferred improvement:

- accept validated `AlertLevel` or app-owned values;
- retain existing channel IDs;
- retain existing channel names;
- retain existing importance mapping.

Stable channels remain:

- `ackline_remember` — `Ackline · Remember`
- `ackline_important` — `Ackline · Important`
- `ackline_urgent` — `Ackline · Urgent`

Do not recreate them under new IDs.

The temporary small icon remains acceptable for Phase 2.

A neutral PendingIntent that opens `MainActivity` is allowed if implementation is small.

It must not acknowledge, mark viewed, or mutate Room.

Direct notification-to-detail routing is optional.

---

## 19. MainActivity

Modify `MainActivity.kt`.

Preserve:

- `FirebaseMessaging.register()`;
- registration error handling;
- `AcklineTheme`.

Replace direct `SetupScreen` hosting with a root `AcklineApp`.

Do not move FID setup code merely for style.

Do not create multiple activities.

---

## 20. Root App UI

Expected path: `app/src/main/java/com/edu/ackline/ui/AcklineApp.kt`

Responsibilities:

- host Inbox;
- host Detail;
- host Setup;
- maintain small screen state/back behavior.

No Navigation Compose by default.

A small screen model such as `Inbox`, `Detail(notificationId)`, and `Setup` is enough.

Back behavior:

- Detail → Inbox
- Setup → Inbox

Process death may return to Inbox.

Restoring the full navigation stack across process death is not required.

---

## 21. Inbox ViewModel

Expected path: `app/src/main/java/com/edu/ackline/feature/inbox/InboxViewModel.kt`

Responsibilities:

- observe pending flow;
- observe viewed flow;
- expose immutable UI state;
- hold selected `Pendientes / Vistas` filter if useful.

Use lifecycle-aware state collection in Compose.

Do not expose DAO directly to composables.

Do not add use cases/interactors.

A manual ViewModel factory is acceptable.

---

## 22. Inbox Screen

Expected path: `app/src/main/java/com/edu/ackline/feature/inbox/InboxScreen.kt`

Keep small composables in this file unless separation materially improves clarity.

Required visual structure:

- small source/eyebrow label;
- `Inbox`;
- pending count;
- `Pendientes | Vistas`;
- alert rows.

Row content:

- severity cue;
- title;
- 1–2 line summary;
- timestamp.

Row tap opens detail.

No Visto action.

No swipe acknowledgment.

No card soup.

Prefer one coherent scrolling surface.

---

## 23. UI Direction

Use Material 3 as implementation foundation, not visual identity.

Preferred:

- restrained typography;
- clean hierarchy;
- intentional whitespace;
- flat list rows;
- subtle dividers;
- small severity cue;
- limited radii;
- limited elevation.

Avoid:

- gradient headers;
- nested cards;
- emoji;
- large colorful chips everywhere;
- glass;
- generic dashboard;
- verbose explanatory paragraphs.

Suggested initial copy:

- `PERSONAL ADMIN`
- `Inbox`
- `Pendientes`
- `Vistas`

Pending count may appear as `3 pendientes`.

Do not add localization infrastructure.

---

## 24. Severity Treatment

Severity should be distinguishable without dominating rows.

Reasonable approaches:

- small dot;
- thin side marker;
- small uppercase label;
- subtle icon.

Do not default to large colored backgrounds/cards for severity.

Exact treatment is a screenshot/product decision.

---

## 25. Empty States

Required conceptual empty states:

Pendientes: `No hay alertas pendientes`

Vistas: `Aún no hay alertas vistas`

Keep secondary copy minimal.

No illustration dependency is needed.

---

## 26. Alert Detail

Expected path: `app/src/main/java/com/edu/ackline/feature/detail/AlertDetailScreen.kt`

Input should be app-owned `Alert`, not `AlertEntity`.

Display:

- severity;
- title;
- full message;
- created time;
- received time if useful;
- status.

Current status derives only from `acknowledgedAt`.

No explicit acknowledgment button in Phase 2.

No remote ACK state.

No debugging panel.

---

## 27. Setup Navigation

Existing `SetupScreen.kt` and `SetupState.kt` should remain largely unchanged.

Inbox should provide a restrained way to reach Setup, for example a small top-bar device/settings action.

Do not make Setup one of the Pendientes/Vistas tabs.

---

## 28. Viewed Data During Phase 2

Production flow does not set `acknowledgedAt`.

Therefore Vistas will normally be empty.

That is intentional.

Viewed test data may exist in instrumented DAO fixtures or Compose Preview fixtures.

Do not add a hidden runtime ACK button merely to populate the Vistas UI.

---

## 29. Tests — Protocol

Update or replace the Phase 1 payload tests.

Required meaningful cases:

- valid protocol 1 accepted;
- protocol missing rejected;
- wrong protocol rejected;
- missing notification ID rejected;
- blank notification ID rejected;
- invalid level rejected;
- blank title rejected;
- blank message rejected;
- missing `created_at` rejected;
- invalid `created_at` rejected;
- valid `created_at` parsed.

---

## 30. Tests — Persistence

Prefer a small Android instrumented test using an in-memory Room database.

Minimum:

- first insert succeeds;
- duplicate same `notificationId` is ignored;
- one row remains;
- pending query contains new row;
- acknowledged fixture appears in viewed query;
- ordering is newest first.

Do not add Robolectric just to run these as JVM tests.

If Room testing requires one small test dependency, preflight must justify it.

---

## 31. Tests — Repository

If natural with the same Room fixture, verify:

- envelope maps correctly;
- duplicate insert reports duplicate;
- Room row maps back to `Alert`.

Do not create fake repository interfaces solely for testing.

---

## 32. Tests — UI

Compose UI tests are optional.

Add them only if they provide durable value for simple behavior such as tab selection or row click → detail.

Do not create screenshot/pixel-test infrastructure.

Physical screenshot review remains mandatory.

---

## 33. Duplicate Physical QA

Use a dedicated ID such as `phase2-dedupe-001`.

Procedure:

1. Send once.
2. Verify one Inbox row.
3. Swipe tray notification away.
4. Send identical `notification_id` again.
5. Verify Inbox row count unchanged.
6. Verify tray notification does not reappear.

This is a key Phase 2 acceptance test.

---

## 34. Persistence QA

Required:

1. receive multiple alerts;
2. close/reopen Ackline;
3. verify rows;
4. kill process;
5. reopen Ackline;
6. verify rows;
7. reboot Oppo;
8. verify rows.

Force Stop may be used as a persistence test only when followed by manually reopening the app.

It is not a Phase 2 transport reliability test.

---

## 35. Notification-Dismissal QA

Procedure:

1. receive unique alert;
2. confirm Inbox row;
3. swipe Android notification away;
4. open Ackline.

Expected:

- same Inbox row remains;
- status remains Pendiente.

Tray state must never delete local state.

---

## 36. False-ACK QA

Phase 2 must prove ordinary observation does not modify `acknowledgedAt`.

Verify:

- receive → pending;
- open app → pending;
- open detail → pending;
- swipe notification → pending;
- restart app → pending.

Do not add true-ACK actions yet.

---

## 37. FCM Regression Smoke

After Room integration, repeat only:

- Background + Wi-Fi + IMPORTANT
- Removed from Recents + IMPORTANT

Expected for each:

- persisted in Room;
- native notification appears;
- no manual app reopen required.

Do not rerun the full Phase 1 matrix unless actual regression evidence requires it.

---

## 38. Screenshot Review

Before final closeout, collect Oppo screenshots for:

- Inbox with several pending alerts;
- Inbox empty state;
- Vistas empty state;
- Alert Detail;
- dark-mode Inbox;
- dark-mode Detail.

Review criteria:

- hierarchy;
- density;
- severity treatment;
- spacing;
- typography;
- generic CRUD feel;
- dark-mode quality.

If product quality is insufficient, fix UI before closing Phase 2.

---

## 39. Expected File Scope

Likely modified:

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/edu/ackline/MainActivity.kt`
- `app/src/main/java/com/edu/ackline/push/AcklineMessagingService.kt`
- `app/src/main/java/com/edu/ackline/notifications/AcklineNotificationManager.kt`
- `tools/firebase_sender.py`
- existing payload tests

Likely created:

- `app/src/main/java/com/edu/ackline/AcklineApplication.kt`
- `app/src/main/java/com/edu/ackline/model/Alert.kt`
- `app/src/main/java/com/edu/ackline/push/IncomingAlertEnvelope.kt`
- `app/src/main/java/com/edu/ackline/data/local/AlertEntity.kt`
- `app/src/main/java/com/edu/ackline/data/local/AlertDao.kt`
- `app/src/main/java/com/edu/ackline/data/local/AcklineDatabase.kt`
- `app/src/main/java/com/edu/ackline/data/AlertRepository.kt`
- `app/src/main/java/com/edu/ackline/ui/AcklineApp.kt`
- `app/src/main/java/com/edu/ackline/feature/inbox/InboxViewModel.kt`
- `app/src/main/java/com/edu/ackline/feature/inbox/InboxScreen.kt`
- `app/src/main/java/com/edu/ackline/feature/detail/AlertDetailScreen.kt`

Potential test:

- `app/src/androidTest/.../AlertDaoInstrumentedTest.kt`

Preflight must confirm exact paths before implementation.

Do not mechanically create every listed file if responsibilities combine cleanly.

---

## 40. Preserve Unless Required

Do not redesign unless necessary:

- `SetupState.kt`
- `SetupScreen.kt`
- `Theme.kt`
- Firebase project configuration
- `google-services.json`

No new Firebase Console products are required.

---

## 41. Explicit Out of Scope

Do not implement:

- `acknowledge(notificationId)`;
- `Marcar como visto`;
- notification Visto action;
- ACK sync state;
- WorkManager;
- HTTP client;
- Tailscale;
- Hermes ACK endpoint;
- E2EE;
- crypto;
- Keystore;
- real Hermes data;
- production Hermes sender integration;
- reconciliation;
- `onDeletedMessages()` sync;
- search;
- advanced settings;
- analytics;
- accounts;
- multi-user;
- multi-device;
- Play Store;
- billing;
- foreground service;
- custom socket;
- MQTT;
- ntfy;
- battery exemptions;
- ColorOS hacks;
- Hilt;
- Retrofit;
- multi-module architecture;
- generic Clean Architecture;
- use-case/interactor layer.

---

## 42. Automated Validation

Required:

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
python -m py_compile tools/firebase_sender.py
git diff --check
git status --short --untracked-files=all
git diff --stat
```

If instrumented Room tests are added:

```bash
./gradlew connectedDebugAndroidTest
```

Use the physical Oppo if ADB is healthy.

Wireless-ADB problems are tooling/environment issues, not reasons to change Ackline source.

---

## 43. Manual QA Order

After automated validation:

A. install current APK  
B. launch Inbox  
C. unique alert persistence  
D. duplicate idempotency  
E. tray-dismissal persistence  
F. app/process restart persistence  
G. Alert Detail  
H. false-ACK checks  
I. Pendientes/Vistas  
J. Setup accessibility  
K. background FCM regression smoke  
L. reboot persistence  
M. screenshots light/dark.

Stop at the first genuine correctness blocker.

---

## 44. Review Route

After implementation:

automated validation → physical data/persistence QA → screenshot/product review with ChatGPT → `/local-review` → fix blockers → repeat affected validation → user commit/push → ChatGPT GitHub final review.

For Room/KSP/platform blocker: `Gemini Android Studio`.

For UI that remains generic after one focused pass: use the premium UI route in `docs/AI_WORKFLOW.md`.

---

## 45. Stop Policy

At the first real failure:

1. stop;
2. identify root cause;
3. make one focused fix;
4. rerun the smallest relevant check.

Maximum two failed attempts on the same blocker.

Do not enter autonomous repair loops.

Do not refactor unrelated Phase 1 code while debugging Room/UI.

---

## 46. Final Builder Report

Builder must return:

- RESULT: `SUCCESS` or `BLOCKED`;
- files created;
- files modified;
- dependency changes;
- database schema;
- protocol changes;
- receive/persist/notify behavior;
- dedupe behavior;
- tests;
- validation results;
- instrumented-test result;
- manual QA still required;
- security/privacy check;
- scope check;
- remaining risks.

Explicitly confirm:

- no ACK implementation;
- no WorkManager;
- no E2EE;
- no real personal data;
- no Hilt;
- no Retrofit;
- no multi-module;
- no destructive migration fallback;
- explicit `allowBackup` policy.

---

## 47. Completion Gate

Phase 2 is ready for final GitHub review only after:

- build PASS;
- lint PASS;
- unit tests PASS;
- Room persistence test PASS;
- duplicate QA PASS;
- restart persistence PASS;
- reboot persistence PASS;
- tray dismissal PASS;
- false-ACK PASS;
- background FCM regression PASS;
- Inbox screenshot review PASS;
- Detail screenshot review PASS;
- dark-mode review PASS;
- independent review PASS;
- scope review PASS;
- security/privacy review PASS.

---

## 48. Suggested Commit

`feat: add persistent alert inbox`

Do not commit automatically.

The user owns commits and pushes.
