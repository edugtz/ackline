# Phase 3 — Explicit Local Acknowledgment Implementation Plan

## 1. Status

**PLANNED — READY FOR PREFLIGHT**

Phase: `3 — Explicit Local Acknowledgment`

Implementation branch: `3-explicit-local-ack`

Base branch: `dev`

---

## 2. Objective

Add exact, durable local acknowledgment semantics to the existing persistent Ackline Inbox.

Required end state:

pending Room alert  
→ explicit `Visto` / `Marcar como visto`  
→ atomic Room acknowledgment  
→ `acknowledgedAt` persisted  
→ `ackSyncState = pending`  
→ matching Android notification canceled  
→ Pendientes/Vistas update automatically.

Three explicit user surfaces must share the same local acknowledgment operation:

1. notification action;
2. Inbox row action;
3. Alert Detail action.

No remote ACK is performed in Phase 3.

---

## 3. Existing Working Foundation

The current `dev` baseline already has:

- single Android `:app` module;
- Kotlin / Compose / Material 3;
- Firebase Messaging data-only receive path;
- FID registration;
- three stable Ackline notification channels;
- Room 2.8.4;
- KSP 2.3.10;
- Lifecycle 2.11.0;
- database v1;
- exported schema v1;
- `AlertEntity`;
- `AlertDao`;
- `AcklineDatabase`;
- `AlertRepository`;
- app-owned `Alert` and `AlertLevel`;
- `IncomingAlertEnvelope`;
- race-safe insert dedupe by `notificationId`;
- `AcklineApplication` manual wiring;
- Inbox + Pendientes/Vistas;
- Alert Detail;
- Setup/FID screen;
- Phase 2 persistence/instrumented tests;
- fake FCM sender.

Do not rebuild these pieces.

---

## 4. Mandatory Preflight

Before editing, inspect at minimum:

- `AGENTS.md`
- `docs/CURRENT_PHASE.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/ARCHITECTURE.md`
- `docs/PROJECT_SPEC.md`
- `docs/ACCEPTANCE_CRITERIA.md`
- Phase 3 section of `docs/MVP_PHASES.md`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/AndroidManifest.xml`
- `AcklineApplication.kt`
- `model/Alert.kt`
- `data/local/AlertEntity.kt`
- `data/local/AlertDao.kt`
- `data/local/AcklineDatabase.kt`
- `data/AlertRepository.kt`
- `notifications/AcklineNotificationManager.kt`
- `push/AcklineMessagingService.kt`
- `feature/inbox/InboxViewModel.kt`
- `feature/inbox/InboxScreen.kt`
- `feature/detail/AlertDetailScreen.kt`
- `ui/AcklineApp.kt`
- existing JVM/instrumented tests
- exported schema v1
- current git state

Preflight is read-only.

It must report:

- exact v1 → v2 migration;
- exact schema;
- exact shared acknowledgment abstraction;
- exact receiver threading design;
- exact PendingIntent identity strategy;
- exact Inbox/Detail state flow;
- exact files to change/create;
- test plan;
- validation;
- physical QA.

Stop before implementation.

---

## 5. Database Version 2

Update `AcklineDatabase` from version `1` to version `2`.

Keep `exportSchema = true`.

Do not delete schema v1.

Generate and check in schema v2.

No destructive fallback.

---

## 6. Schema Change

Add exactly one Phase 3 persistence field:

`ackSyncState: String`

Expected `AlertEntity` shape after migration:

- `notificationId`
- `protocolVersion`
- `level`
- `title`
- `message`
- `createdAtEpochMillis`
- `receivedAtEpochMillis`
- `acknowledgedAtEpochMillis`
- `ackSyncState`

Do not add:

- `ackSyncedAtEpochMillis`
- `lastAckError`
- retries
- remote status codes
- server metadata

---

## 7. AckSyncState

Expected app-owned enum:

`AckSyncState`

Phase 3 values:

- `NONE("none")`
- `PENDING("pending")`

Potential location:

`model/AckSyncState.kt`

or colocated with `Alert.kt` if that keeps the model cleaner.

Do not add future `SYNCED` / `ERROR` behavior in Phase 3.

`Alert` should expose:

`ackSyncState: AckSyncState`

Invalid stored values should fail clearly or be handled defensively according to preflight findings; do not silently reinterpret an unknown state as a valid ACK.

---

## 8. New Alert Defaults

Update the `IncomingAlertEnvelope` → `AlertEntity` mapping.

Every newly received Phase 3 alert must persist:

`acknowledgedAtEpochMillis = null`

and:

`ackSyncState = "none"`

The FCM protocol does not carry local ACK state.

Never trust remote transport input for these local fields.

---

## 9. Migration 1 → 2

Expected migration:

```sql
ALTER TABLE alerts
ADD COLUMN ackSyncState TEXT NOT NULL DEFAULT 'none'
```

Expose the migration from the database layer using a clearly named constant such as:

`MIGRATION_1_2`

Register it in:

`Room.databaseBuilder(...).addMigrations(MIGRATION_1_2)`

The migration must preserve the existing Room database.

Do not reinstall/clear app data to avoid testing migration.

The physical Oppo upgrade must exercise the real migration.

---

## 10. Atomic DAO Acknowledgment

Add a guarded SQL update.

Preferred concept:

```sql
UPDATE alerts
SET acknowledgedAtEpochMillis = :acknowledgedAtEpochMillis,
    ackSyncState = 'pending'
WHERE notificationId = :notificationId
  AND acknowledgedAtEpochMillis IS NULL
```

DAO method returns affected row count.

This creates an atomic local transition.

Important:

- first ACK updates one row;
- second ACK updates zero rows;
- original acknowledgment timestamp is preserved;
- unknown ID updates zero rows.

Do not SELECT-before-UPDATE for the initial state transition.

A lookup after a zero-row result is acceptable only if the app needs to distinguish `ALREADY_ACKNOWLEDGED` from `NOT_FOUND`.

---

## 11. Repository Acknowledgment API

Extend `AlertRepository` with a narrow operation appropriate to the final threading design.

Concept:

`acknowledge(notificationId, acknowledgedAt): AcknowledgeResult`

Possible result:

- `ACKNOWLEDGED`
- `ALREADY_ACKNOWLEDGED`
- `NOT_FOUND`

The repository should:

- perform the Room update;
- map storage state;
- remain Android-notification agnostic.

Do not make the repository own `NotificationManager`.

Do not add networking.

---

## 12. Shared LocalAcknowledgmentManager

Create one small app-level orchestrator.

Expected path:

`app/src/main/java/com/edu/ackline/ack/LocalAcknowledgmentManager.kt`

This package is allowed because Phase 3 is the ACK phase.

Responsibilities:

1. ask repository to acknowledge;
2. preserve idempotent result;
3. cancel the matching native notification for acknowledged/already-acknowledged rows;
4. return a small result if callers need it.

Do not:

- call HTTP;
- enqueue WorkManager;
- know Tailscale;
- know Hermes transport;
- implement retries.

This is not a generic use-case layer.

It is one concrete cross-layer product operation.

---

## 13. AcklineApplication Wiring

Extend the current manual graph.

Expected ownership:

`AcklineApplication`
- `database`
- `alertRepository`
- `localAcknowledgmentManager`
- optional `acknowledgmentExecutor`

The executor is justified only for the manifest BroadcastReceiver path.

Keep one small executor rather than creating a new thread pool per ACK.

Do not add DI frameworks.

---

## 14. Notification Cancellation API

Extend `AcklineNotificationManager` with a small operation:

`cancel(context, notificationId)`

It must use the exact same stable integer ID formula used to post:

`notificationId.hashCode()`

Do not use `cancelAll()`.

Do not cancel other alerts.

---

## 15. Notification Action Intent

Add a notification action to every newly posted alert:

`Visto`

Use a broadcast `PendingIntent`.

Expected receiver:

`AcknowledgeReceiver`

Use a namespaced action string, for example:

`com.edu.ackline.action.ACKNOWLEDGE`

Include the business `notificationId`.

PendingIntent identity must be unique per alert.

Do not rely only on extras for PendingIntent identity because extras are not part of PendingIntent matching.

Use an identity mechanism such as:

- unique requestCode based on the existing notification ID plus
- a unique `Intent.data` URI derived from an encoded `notificationId`

if preflight confirms that as the smallest robust design.

Use immutable PendingIntent flags.

The receiver must still validate the supplied business ID.

---

## 16. AcknowledgeReceiver

Expected path:

`app/src/main/java/com/edu/ackline/ack/AcknowledgeReceiver.kt`

Manifest:

`android:exported="false"`

`onReceive()` must:

1. reject unrelated/malformed intents quickly;
2. call `goAsync()`;
3. submit the bounded local ACK operation to the app-owned executor;
4. always call `PendingResult.finish()` in `finally`.

Do not block Room disk IO on the receiver main thread.

Do not use WorkManager.

Do not start an Activity.

Do not perform network access.

---

## 17. Receiver Error Behavior

A malformed/stale notification action must not crash the app.

Use bounded diagnostics only.

If acknowledgment fails due to a local unexpected exception:

- keep database truth unchanged;
- avoid false success;
- call `finish()`;
- log a generic bounded error;
- do not retry forever.

Phase 3 does not need a user-facing error toast from the notification action.

---

## 18. Inbox ViewModel

The existing `InboxViewModel` already observes Room.

Extend it with an explicit method such as:

`acknowledge(notificationId)`

The ViewModel should run the blocking local acknowledgment manager off the main thread using the existing coroutine/lifecycle facilities.

Expected result:

Room Flow drives all visual changes.

Do not manually remove the row from `pendingAlerts`.

Do not manually insert into `viewedAlerts`.

Do not duplicate state outside Room.

---

## 19. Inbox UI

For a pending row, replace the passive repeated `Pendiente` affordance with a compact explicit action where appropriate:

`Visto`

The row itself remains clickable for Detail.

The `Visto` action must be a distinct click target and must not also trigger navigation.

After acknowledgment:

- the row leaves Pendientes;
- pending count changes;
- row appears in Vistas;
- no `Visto` action remains.

Viewed rows may show a restrained `Vista` status.

Do not use:

- giant full-width CTA per row;
- checkbox semantics;
- swipe-to-ack;
- long explanatory copy.

---

## 20. Detail Must Become Live

Phase 2 uses:

`AppScreen.Detail(alert: Alert)`

That snapshot is no longer sufficient once the Detail screen can change acknowledgment state.

Change the root screen state to identify Detail by:

`notificationId`

Expected:

`AppScreen.Detail(notificationId: String)`

Introduce:

`AlertDetailViewModel`

It should observe:

`repository.observeById(notificationId)`

so Detail always displays current Room truth.

A small explicit factory is acceptable because the ID is a runtime argument.

Do not add Navigation Compose only for this.

---

## 21. AlertDetailViewModel

Expected path:

`feature/detail/AlertDetailViewModel.kt`

Responsibilities:

- observe one alert by ID;
- expose current app-owned alert state;
- invoke shared local acknowledgment manager when explicitly requested.

No networking.

No ACK retry.

No Firebase.

No UI formatting logic beyond state.

---

## 22. Alert Detail UI

Pending state:

- severity;
- title;
- full message;
- timestamps;
- `Pendiente`;
- explicit `Marcar como visto`.

Viewed state:

- severity;
- title;
- full message;
- timestamps;
- `Vista`;
- no acknowledgment button.

When the button is tapped:

- local ACK runs;
- Room emits updated row;
- screen updates without navigation/reload;
- matching tray notification disappears.

The button should be visible and deliberate but consistent with the existing lightweight design.

---

## 23. Vistas UI

No new screen is required.

Existing Vistas tab becomes functional automatically through Room.

Verify that:

- acknowledged rows appear;
- chronological order remains correct;
- row click opens Detail;
- viewed Detail shows Vista;
- no ACK action appears for viewed rows.

---

## 24. Notification Action UI

Use the native notification action label:

`Visto`

Do not add multiple actions.

Do not add reply/input.

Do not change severity channel IDs.

Do not change FCM priority.

Do not require the app UI to be open.

---

## 25. Notification Body Tap

Phase 3 does not need to change body-tap behavior.

If body tap remains inert, that is acceptable.

If a neutral content PendingIntent is already added or preflight chooses to add one, opening the app/detail must not acknowledge.

Do not conflate notification body tap with `Visto`.

---

## 26. Duplicate Delivery After ACK

This regression is mandatory.

Sequence:

1. receive unique ID;
2. acknowledge it;
3. verify Vista;
4. resend the same FCM `notification_id`.

Expected:

- insert remains duplicate;
- same one row;
- no native repost;
- `acknowledgedAt` unchanged;
- `ackSyncState` remains pending;
- row remains in Vistas.

Do not let incoming FCM overwrite local ACK metadata.

---

## 27. Migration Test Dependency

Add only if required for the chosen official Room migration-test approach:

`androidx.room:room-testing:2.8.4`

as:

`androidTestImplementation`

No new runtime dependency should be necessary.

Do not add Robolectric.

---

## 28. Migration Instrumented Test

Expected new/extended test:

`AcklineMigrationTest`

Use Room's migration test tooling or the smallest official equivalent.

Test v1 → v2 against the exported schema.

Insert a realistic v1 row.

After migration verify:

- row exists;
- notification ID unchanged;
- title/message/timestamps unchanged;
- acknowledgedAt remains null;
- `ackSyncState = none`;
- schema validates.

Keep schema v1 and schema v2 checked in.

---

## 29. DAO / Repository ACK Tests

Extend instrumented coverage.

Required:

### First acknowledgment

pending row  
→ update count 1  
→ acknowledged timestamp equals requested timestamp  
→ sync state pending  
→ pending query excludes row  
→ viewed query includes row.

### Second acknowledgment

same ID with later timestamp  
→ update count 0  
→ original timestamp preserved.

### Unknown ID

→ update count 0  
→ no row created.

### Duplicate receive after ACK

→ insert conflict ignored  
→ ACK state remains intact.

---

## 30. Receiver / Notification Tests

Do not create a large Android testing framework solely to unit-test the receiver.

At minimum code review must verify:

- private receiver;
- action validation;
- `goAsync()`;
- background executor;
- `finish()` in `finally`;
- shared manager use;
- immutable unique PendingIntent.

Physical Oppo QA is the authoritative notification-action test.

---

## 31. False-ACK Physical Matrix

Use a fresh unique alert for each ambiguous case where useful.

Verify:

- FCM receive → Pendiente;
- tray display → Pendiente;
- tray body tap if supported → Pendiente;
- tray swipe → Pendiente;
- launch Ackline → Pendiente;
- open Detail → Pendiente;
- return from Detail → Pendiente;
- app restart → Pendiente.

No hidden state transition is acceptable.

---

## 32. Inbox True-ACK QA

Receive a unique pending alert.

Tap row-level:

`Visto`

Expected:

- pending count decrements;
- row disappears from Pendientes;
- row appears in Vistas;
- corresponding tray notification disappears;
- Detail subsequently shows Vista;
- state persists after app restart.

---

## 33. Detail True-ACK QA

Receive another unique alert.

Open Detail.

Confirm still Pendiente.

Tap:

`Marcar como visto`

Expected:

- Detail changes live to Vista;
- button disappears;
- tray item disappears;
- Inbox Vistas contains row;
- Pendientes no longer contains it.

---

## 34. Notification True-ACK QA

Receive another unique alert.

Do not open Ackline.

Tap notification action:

`Visto`

Expected:

- notification disappears;
- opening Ackline later shows the alert under Vistas;
- state is durable.

Repeat with Ackline removed from Recents before tapping the action.

This is a core Phase 3 Android gate.

---

## 35. Idempotent ACK QA

Acknowledge a unique alert.

Attempt to acknowledge it a second time if a stale/alternate surface remains available.

Expected:

- no crash;
- still one row;
- original `acknowledgedAt` unchanged;
- still Vistas;
- sync state remains pending.

---

## 36. Persistence QA

After several alerts are acknowledged through different surfaces:

- close/reopen;
- force stop + manual reopen;
- reboot.

Expected:

- viewed alerts stay viewed;
- pending alerts stay pending;
- timestamps remain;
- pending count remains correct.

Force Stop here tests persistence only, not FCM delivery while force-stopped.

---

## 37. FCM Regression QA

Do a focused regression:

1. Background + IMPORTANT
2. Removed from Recents + IMPORTANT

For each:

- new alert persists;
- native notification appears;
- alert starts pending.

Do not rerun the full Phase 1 matrix unless evidence requires it.

---

## 38. Screenshot Review

Collect Oppo screenshots for:

- Inbox with mixed pending/viewed data;
- Pendientes with row-level Visto action;
- Vistas;
- Detail pending;
- Detail viewed;
- native notification with Visto action;
- light-mode Inbox/Detail;
- dark-mode Inbox/Detail.

Review:

- action prominence;
- accidental-tap risk;
- hierarchy;
- row density;
- tab behavior;
- viewed-state clarity;
- Detail CTA quality;
- no sync-diagnostic clutter.

---

## 39. Expected Files to Modify

Likely:

- `gradle/libs.versions.toml` — only if room-testing alias is needed
- `app/build.gradle.kts` — only if room-testing dependency is needed
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/edu/ackline/AcklineApplication.kt`
- `app/src/main/java/com/edu/ackline/model/Alert.kt`
- `app/src/main/java/com/edu/ackline/data/local/AlertEntity.kt`
- `app/src/main/java/com/edu/ackline/data/local/AlertDao.kt`
- `app/src/main/java/com/edu/ackline/data/local/AcklineDatabase.kt`
- `app/src/main/java/com/edu/ackline/data/AlertRepository.kt`
- `app/src/main/java/com/edu/ackline/notifications/AcklineNotificationManager.kt`
- `app/src/main/java/com/edu/ackline/feature/inbox/InboxViewModel.kt`
- `app/src/main/java/com/edu/ackline/feature/inbox/InboxScreen.kt`
- `app/src/main/java/com/edu/ackline/feature/detail/AlertDetailScreen.kt`
- `app/src/main/java/com/edu/ackline/ui/AcklineApp.kt`
- existing Room instrumented tests

Potential schema output:

- `app/schemas/com.edu.ackline.data.local.AcklineDatabase/2.json`

Preflight confirms exact scope before implementation.

---

## 40. Expected Files to Create

Likely:

- `app/src/main/java/com/edu/ackline/model/AckSyncState.kt` if not colocated
- `app/src/main/java/com/edu/ackline/ack/LocalAcknowledgmentManager.kt`
- `app/src/main/java/com/edu/ackline/ack/AcknowledgeReceiver.kt`
- `app/src/main/java/com/edu/ackline/feature/detail/AlertDetailViewModel.kt`
- `app/src/androidTest/java/com/edu/ackline/data/local/AcklineMigrationTest.kt`

Do not create unrelated future-phase classes.

---

## 41. Files Expected Not to Need Changes

Normally preserve:

- `push/IncomingAlertEnvelope.kt`
- `push/AcklineMessagingService.kt` except a tiny mapping adjustment if the entity/repository signature requires it
- `SetupState.kt`
- `feature/setup/SetupScreen.kt`
- theme files
- `tools/firebase_sender.py`
- Firebase project config
- `google-services.json`

Do not change FCM protocol merely because the phase changes local state.

---

## 42. Explicit Out of Scope

Do not implement:

- remote ACK request;
- HTTP client;
- Tailscale logic;
- Hermes ACK endpoint;
- WorkManager;
- retry/backoff;
- remote SYNCED/ERROR behavior;
- ACK auth;
- E2EE;
- crypto;
- Keystore;
- Hermes production sender integration;
- reconciliation;
- `onDeletedMessages()` sync;
- search;
- analytics;
- auth/accounts;
- multi-user/device;
- Navigation Compose;
- Hilt/Koin;
- Retrofit;
- multi-module;
- foreground service;
- custom socket;
- MQTT;
- ntfy;
- battery exemption;
- ColorOS workaround.

---

## 43. Automated Validation

Required:

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
python -m py_compile tools/firebase_sender.py
git diff --check
git status --short --untracked-files=all
git diff --stat
```

Required instrumented gate:

```bash
./gradlew connectedDebugAndroidTest
```

Do not skip the migration test.

---

## 44. Implementation Order

1. Confirm exact current v1 schema and migration-test requirements.
2. Add `AckSyncState`.
3. Add `ackSyncState` to entity/model mapping.
4. Implement `MIGRATION_1_2`.
5. Register migration and generate schema v2.
6. Add guarded DAO acknowledgment update.
7. Add repository acknowledgment result.
8. Add shared `LocalAcknowledgmentManager`.
9. Add notification cancellation API.
10. Add private `AcknowledgeReceiver`.
11. Add notification `Visto` PendingIntent/action.
12. Wire manager/executor in `AcklineApplication`.
13. Extend `InboxViewModel`.
14. Add compact Inbox Visto action.
15. Replace snapshot Detail navigation with notification ID.
16. Add `AlertDetailViewModel`.
17. Add Detail `Marcar como visto`.
18. Add/extend migration and DAO acknowledgment tests.
19. Run automated validation.
20. Run connected instrumented tests.
21. Independent review.
22. Install upgrade over existing Phase 2 app/data.
23. Physical false/true ACK QA.
24. Screenshot/product review.
25. Commit/push only after PASS.

---

## 45. Android Upgrade Rule

Do not uninstall Ackline before migration QA.

Install the Phase 3 build over the existing Phase 2 installation.

Reason:

the real user database must exercise:

v1 → v2.

If the app is uninstalled or data is cleared before the migration gate, that test is invalid and must be repeated from a real v1 database.

---

## 46. Stop Policy

At first real failure:

1. stop;
2. identify the root cause;
3. make one bounded evidence-based fix;
4. rerun the smallest relevant test.

Maximum two failed attempts on the same blocker.

Escalate concrete:

- Room migration issue;
- PendingIntent/action issue;
- BroadcastReceiver lifecycle issue

to the Android specialist route rather than inventing a workaround.

---

## 47. Builder Final Report

Return:

- RESULT: SUCCESS | BLOCKED
- files created
- files modified
- exact database v2 schema
- migration details
- exported schema result
- acknowledgment transaction behavior
- notification receiver behavior
- PendingIntent uniqueness strategy
- Inbox acknowledgment behavior
- Detail live-state behavior
- tests
- validation results
- connectedAndroidTest result
- manual QA still required
- scope check
- security/privacy check
- remaining risks

Explicitly confirm:

- no remote HTTP ACK;
- no WorkManager;
- no E2EE;
- no destructive migration;
- no implicit ACK path;
- no Phase 4 implementation.

---

## 48. Completion Gate

Phase 3 is ready for final GitHub review only after:

- v1 → v2 migration PASS;
- existing Phase 2 data preserved;
- schema v2 checked in;
- DAO ACK test PASS;
- first ACK writes timestamp + pending sync state;
- second ACK preserves original timestamp;
- unknown ACK does not create data;
- notification action PASS;
- Inbox Visto PASS;
- Detail Marcar como visto PASS;
- Room-backed live UI PASS;
- false-ACK matrix PASS;
- duplicate-after-ACK PASS;
- restart persistence PASS;
- reboot persistence PASS;
- FCM regression PASS;
- light screenshot review PASS;
- dark screenshot review PASS;
- independent review PASS;
- no Phase 4 scope creep;
- final pushed-branch GitHub review = PASS.

---

## 49. Suggested Commit

`feat: add explicit local acknowledgment`

Do not commit automatically.

The user owns commits and pushes.
