# Current Phase

## Status

**PLANNED — READY FOR PREFLIGHT**

Phase: `3 — Explicit Local Acknowledgment`

Implementation branch: `3-explicit-local-ack`

Base branch: `dev`

---

## Objective

Implement exact local `Visto` semantics without introducing remote synchronization yet.

Phase 3 adds one durable, shared local acknowledgment operation:

`acknowledge(notificationId)`

That operation must be the only production path that turns a pending alert into a viewed alert.

Required state transition:

`Pendiente` → explicit user action → `Vista`

Required local effects:

1. persist `acknowledgedAt`;
2. mark local ACK sync state as `PENDING` for the later remote-sync phase;
3. immediately update Room-backed Inbox/Detail state;
4. cancel the corresponding Android tray notification;
5. remain correct and idempotent if the same acknowledgment is attempted again.

Phase 3 does **not** contact Hermes, Tailscale, or any remote ACK endpoint.

---

## Baseline Already Proven

Phase 0 established:

- Android project builds and installs on the Oppo;
- Firebase configuration works;
- current FID registration works;
- fake data-only FCM delivery reaches Ackline.

Phase 1 established:

- native Android notifications work;
- foreground/background delivery works;
- removed-from-Recents delivery works;
- Wi-Fi/mobile transitions recover without opening Ackline;
- temporary offline recovery works;
- IMPORTANT delivery survives the intended Doze test.

Phase 2 established:

- Room is the device Inbox source of truth;
- `notificationId` is the local primary key;
- duplicate delivery creates one row;
- duplicate delivery does not repost a known notification;
- alerts persist across app restart, process restart, and reboot;
- tray dismissal does not remove Inbox state;
- Inbox, Pendientes/Vistas, Detail, and Setup work;
- opening the app/detail does not acknowledge;
- Phase 1 transport behavior still works after Room integration;
- dark/light product review passed.

Do not redesign those proven layers without a concrete Phase 3 requirement.

---

## Phase 3 Question

This phase must answer:

> Can Ackline acknowledge an alert only when the user explicitly asks, persist that decision durably, reflect it everywhere immediately, and never infer acknowledgment from ordinary viewing or notification behavior?

The answer must be demonstrably yes before remote ACK synchronization is introduced.

---

## Core Acknowledgment Rule

Only these explicit user actions acknowledge:

1. Android notification action: `Visto`
2. Inbox action on a pending row: `Visto`
3. Alert Detail action: `Marcar como visto`

All three must use the same local acknowledgment operation.

These must **never** acknowledge:

- FCM delivery;
- Room insertion;
- Android notification display;
- tapping/opening a notification;
- swiping/dismissing a notification;
- opening Ackline;
- opening Alert Detail;
- switching Pendientes/Vistas;
- app restart;
- process restart;
- device reboot.

There is no concept of implicit read receipt.

---

## Source-of-Truth Rules

During Phase 3:

- **Room** = device Inbox + local acknowledgment truth.
- **FCM** = transport only.
- **Android tray** = presentation only.
- **Hermes** = not contacted by acknowledgment yet.
- **ackSyncState = PENDING** = durable local statement that a future remote ACK still needs synchronization.

Tray disappearance must never be treated as evidence of acknowledgment.

---

## Database Version

Phase 2 database version:

`1`

Phase 3 database version:

`2`

Phase 3 must perform a real non-destructive Room migration from v1 to v2.

Do not use:

`fallbackToDestructiveMigration()`

The migration must preserve every existing Phase 2 alert.

---

## Phase 3 Schema

Existing fields remain:

- `notificationId: String` — primary key
- `protocolVersion: Int`
- `level: String`
- `title: String`
- `message: String`
- `createdAtEpochMillis: Long`
- `receivedAtEpochMillis: Long`
- `acknowledgedAtEpochMillis: Long?`

Add:

- `ackSyncState: String`

Initial wire/storage values for Phase 3:

- `none`
- `pending`

Semantics:

`acknowledgedAtEpochMillis == null` → pending alert  
`acknowledgedAtEpochMillis != null` → viewed/locally acknowledged alert

`ackSyncState == none` → no local ACK waiting for remote synchronization  
`ackSyncState == pending` → local ACK exists and Phase 4 must eventually synchronize it

For newly received alerts:

- `acknowledgedAtEpochMillis = null`
- `ackSyncState = none`

For a newly acknowledged alert:

- `acknowledgedAtEpochMillis = now`
- `ackSyncState = pending`

Do not add yet:

- `ackSyncedAtEpochMillis`
- `lastAckError`
- retry count
- remote HTTP state
- remote response metadata

Those belong to Phase 4.

---

## Migration 1 → 2

The migration must add the new non-null column with a safe default:

`ackSyncState TEXT NOT NULL DEFAULT 'none'`

Existing Phase 2 production rows are pending, so `none` is correct for them.

Keep both exported schemas:

- schema v1
- schema v2

A migration test is mandatory because real Phase 2 data already exists on the Oppo.

The migration test must prove:

- v1 database opens as v2;
- existing rows survive;
- existing field values survive;
- new `ackSyncState` exists;
- migrated Phase 2 rows receive `none`;
- no destructive reset occurs.

---

## App-Owned ACK State

Introduce an app-owned ACK sync state representation.

Expected concept:

`AckSyncState`

Phase 3 values:

- `NONE`
- `PENDING`

Do not expose raw storage strings throughout UI/application code.

The existing app-owned `Alert` model should now include ACK sync state.

Phase 3 UI should not turn this into diagnostics.

The user primarily sees:

- `Pendiente`
- `Vista`

The internal `PENDING` sync marker exists to prepare durable remote sync in Phase 4.

---

## Atomic Local Acknowledgment

The pending → viewed transition must be atomic in Room.

Preferred DAO shape:

`UPDATE alerts SET acknowledgedAtEpochMillis = :timestamp, ackSyncState = 'pending' WHERE notificationId = :notificationId AND acknowledgedAtEpochMillis IS NULL`

Return the number of updated rows.

Expected behavior:

- `1` row updated → newly acknowledged;
- `0` rows updated → already acknowledged or unknown ID.

Do not implement acknowledgment as:

1. SELECT;
2. mutate in memory;
3. UPDATE.

The guarded SQL update is the authority for the state transition.

Repeated acknowledgment must never replace the original `acknowledgedAt` timestamp.

---

## Shared Local Acknowledgment Operation

There must be exactly one app-level orchestration path for local acknowledgment.

Expected concept:

`LocalAcknowledgmentManager`

or another equally clear, narrowly scoped name.

Responsibility:

1. call the repository/DAO atomic acknowledgment operation;
2. preserve idempotency;
3. cancel the corresponding Android notification;
4. return a small result if useful.

Possible results:

- `ACKNOWLEDGED`
- `ALREADY_ACKNOWLEDGED`
- `NOT_FOUND`

Do not create a generic use-case framework.

Do not call remote networking.

Do not enqueue WorkManager.

Both UI and notification receiver must delegate to this same operation.

---

## Notification Cancellation

Use the same stable Android notification ID scheme already used for posting:

`notificationId.hashCode()`

Add a small notification-manager operation such as:

`cancel(context, notificationId)`

After a successful local acknowledgment, the corresponding tray notification must disappear.

If the alert is already acknowledged, canceling a stale matching tray notification is harmless.

Do not cancel unrelated Ackline notifications.

---

## Android Notification Action

Every newly posted Ackline notification must expose an explicit action:

`Visto`

The action must use a `PendingIntent` targeting an app-owned `BroadcastReceiver`.

Expected concept:

`AcknowledgeReceiver`

Requirements:

- manifest-declared;
- `android:exported="false"`;
- validate the action and notification ID;
- no activity launch required;
- no remote network work;
- delegate to the shared local acknowledgment operation.

The notification action is a true ACK path.

The notification body/tap is **not** a true ACK path.

---

## BroadcastReceiver Threading

`BroadcastReceiver.onReceive()` runs on the app main thread and must return quickly.

The Room acknowledgment write must not block the receiver main thread.

Use:

`goAsync()`

and hand the returned `PendingResult` to a bounded background execution path.

The async path must always call:

`PendingResult.finish()`

in `finally`.

The local operation is a tiny Room update + notification cancellation and must remain comfortably inside the broadcast execution window.

Do not add WorkManager for this Phase 3 notification action.

Do not launch untracked fire-and-forget work after `onReceive()` returns.

A small app-owned executor or another equally bounded mechanism is acceptable.

---

## Application Wiring

Keep manual wiring small.

Expected ownership:

`AcklineApplication`
- one `AcklineDatabase`
- one `AlertRepository`
- one `LocalAcknowledgmentManager`
- optionally one small executor dedicated to receiver-side local ACK work

Do not add:

- Hilt
- Koin
- generic service locator
- generic use-case container

---

## Repository Responsibility

Extend `AlertRepository` only as needed to support local acknowledgment.

Expected new responsibility:

`acknowledge(notificationId, acknowledgedAt)`

The repository remains responsible for Room/domain mapping and database operations.

It must not:

- cancel notifications itself if that would couple data storage to Android presentation;
- perform HTTP;
- know about Hermes;
- enqueue WorkManager.

The shared acknowledgment manager handles orchestration across data + tray presentation.

---

## Inbox Behavior

Pending rows must expose an explicit, compact `Visto` action.

Preferred behavior:

Pending row:
- severity;
- title;
- summary;
- timestamp;
- compact `Visto` action.

Viewed row:
- no `Visto` action;
- subtle `Vista` state.

Tapping the row itself opens Detail and does not acknowledge.

Pressing `Visto`:

- performs explicit acknowledgment;
- row moves from Pendientes to Vistas through Room Flow;
- pending count decrements;
- associated tray notification is canceled;
- no remote call occurs.

Do not make the entire row an acknowledgment target.

Do not add swipe-to-ack in this phase.

---

## Alert Detail Behavior

Phase 2 passed a snapshot `Alert` directly to Detail.

Phase 3 changes alert state while Detail may be open, so Detail must observe current Room-backed state.

Preferred direction:

- screen navigation identifies the selected alert by `notificationId`;
- a small `AlertDetailViewModel` observes that alert from the repository;
- Detail renders live Room-backed state.

This is a justified Phase 3 change.

When pending:

- show `Marcar como visto`.

After explicit acknowledgment:

- status changes to `Vista`;
- action disappears;
- screen remains valid;
- original acknowledgment timestamp remains durable.

Opening Detail must still do nothing to acknowledgment state.

Do not add Navigation Compose solely for this.

A small ViewModel factory is acceptable if needed.

---

## Vistas Behavior

After acknowledgment, the alert must automatically appear under:

`Vistas`

because Room Flow reflects `acknowledgedAt != null`.

No manual refresh.

No duplicated in-memory list mutation.

Vistas should preserve the existing chronological ordering rule.

---

## ACK Sync State UI

Phase 3 persists `ackSyncState = pending` after local ACK, but does **not** perform remote synchronization.

Do not display noisy diagnostics such as:

- `ACK_PENDING`
- retry counters
- Hermes unavailable
- Tailscale state

in the normal Inbox.

Default Phase 3 product behavior:

- show `Vista`;
- keep remote sync state internal.

Phase 4 owns remote-sync UX.

---

## FCM Receive Behavior

The existing Phase 2 receive path must remain unchanged in semantics:

valid new alert  
→ persist pending with `ackSyncState = none`  
→ notification

duplicate alert  
→ no new row  
→ no repost

Acknowledgment must not affect FCM idempotency.

If the same already-known notification ID is delivered again after acknowledgment:

- do not create a second row;
- do not revert it to pending;
- do not repost a notification;
- preserve the acknowledgment timestamp and `ackSyncState`.

This is a required regression test.

---

## Protocol

No FCM protocol change in Phase 3.

Continue protocol v1 fields:

- `protocol`
- `notification_id`
- `level`
- `title`
- `message`
- `created_at`

Do not change `tools/firebase_sender.py` unless a test-only convenience is genuinely required.

No production Hermes integration yet.

Fake/non-sensitive payloads only.

---

## Backup / Privacy

Keep:

`android:allowBackup="false"`

No change to the E2EE gate:

before Phase 5, use only fake/non-sensitive payloads.

Do not log:

- FID;
- credentials;
- future ACK auth;
- real personal notification content.

---

## Dependencies

No new runtime framework is expected.

Phase 3 should normally reuse:

- Room 2.8.4
- KSP 2.3.10
- Lifecycle 2.11.0
- Compose / Material 3
- Firebase Messaging

A Room migration test may justify:

`androidx.room:room-testing:2.8.4`

as an `androidTestImplementation`.

Do not add:

- WorkManager
- Retrofit
- OkHttp
- Hilt
- Navigation Compose
- generic ACK libraries

---

## Tests — Database / Migration

Mandatory migration coverage:

- create/open schema v1;
- migrate to v2;
- preserve existing row;
- verify `ackSyncState = none`;
- validate migrated schema.

Mandatory DAO/repository acknowledgment coverage:

- pending row → acknowledge updates exactly one row;
- `acknowledgedAt` persisted;
- `ackSyncState = pending`;
- row disappears from pending query;
- row appears in viewed query;
- second acknowledgment does not change original timestamp;
- unknown ID does not create a row.

Also verify:

acknowledged row + duplicate FCM insert attempt  
→ duplicate ignored  
→ acknowledgment state unchanged.

---

## Tests — False ACK

Mandatory physical false-ACK cases:

- receive alert → pending;
- tray displays alert → pending;
- tap/open notification if body tap exists → pending;
- swipe/dismiss notification → pending;
- open Ackline → pending;
- open Detail → pending;
- restart app → pending.

---

## Tests — True ACK

Mandatory physical true-ACK cases:

### Notification action

Tap `Visto`.

Expected:

- Room row becomes viewed;
- tray item disappears;
- alert appears under Vistas;
- `ackSyncState = pending`;
- no remote network dependency.

### Inbox

Tap row-level `Visto`.

Expected same behavior.

### Detail

Tap `Marcar como visto`.

Expected same behavior and live Detail state changes to Vista.

---

## Idempotency QA

Acknowledge an alert, then attempt acknowledgment again through another surface where possible.

Expected:

- one original acknowledgment timestamp;
- still one Room row;
- still Vista;
- no crash;
- no duplicate state transition.

Also resend the same FCM `notification_id`.

Expected:

- no repost;
- no move back to Pendientes;
- no acknowledgment timestamp reset.

---

## Persistence QA

After acknowledgment:

- close/reopen app;
- process restart;
- device reboot.

Expected:

- viewed state remains;
- alert remains under Vistas;
- acknowledgment timestamp remains;
- `ackSyncState = pending` remains durable.

---

## Product Quality Gate

Phase 3 changes the most important recurring interaction in Ackline.

Required screenshot/manual review:

- Inbox with both pending and viewed alerts;
- compact Inbox `Visto` action;
- Detail pending state with `Marcar como visto`;
- Detail viewed state;
- Vistas list;
- notification action;
- light mode;
- dark mode.

The acknowledgment action must be easy to find but not visually dominate every row.

Reject:

- giant CTA buttons on every Inbox row;
- accidental row-level ACK;
- checkbox-style ambiguity that looks like selection;
- noisy sync diagnostics;
- card-heavy redesign;
- new dashboard structure.

---

## Existing Product UI

Preserve the accepted Phase 2 visual direction:

- flat chronological rows;
- restrained severity cue;
- deliberate typography;
- Pendientes/Vistas tabs;
- lightweight Detail;
- Setup as secondary destination.

Phase 3 should evolve the existing product, not redesign it.

---

## Out of Scope

Do **not** implement:

- HTTP ACK endpoint;
- Android HTTP client;
- Tailscale ACK transport;
- WorkManager;
- retry/backoff;
- remote `SYNCED`;
- remote `ERROR`;
- `ackSyncedAt`;
- `lastAckError`;
- Hermes SQLite changes;
- Hermes production integration;
- E2EE;
- AES-GCM;
- Keystore;
- reconciliation;
- `onDeletedMessages()` recovery;
- search;
- analytics;
- accounts/auth;
- multi-user;
- multi-device;
- Navigation Compose;
- Hilt/Koin;
- Retrofit;
- foreground service;
- persistent socket;
- MQTT;
- ntfy;
- ColorOS workaround;
- battery exemptions.

Do not start Phase 4 inside Phase 3.

---

## Automated Validation

At minimum:

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
python -m py_compile tools/firebase_sender.py
git diff --check
git status --short --untracked-files=all
```

Because database migration and notification action are Phase 3 core behavior:

```bash
./gradlew connectedDebugAndroidTest
```

is required on the Oppo or a suitable emulator before final closeout.

---

## Manual QA

Required physical Oppo cases:

1. Existing Phase 2 alerts survive install/upgrade to the Phase 3 build.
2. Existing alerts remain Pendiente after migration.
3. New FCM alert arrives normally.
4. New alert starts Pendiente.
5. Tray display does not acknowledge.
6. Tray swipe does not acknowledge.
7. Opening Ackline does not acknowledge.
8. Opening Detail does not acknowledge.
9. Inbox `Visto` acknowledges.
10. Acknowledged alert moves to Vistas automatically.
11. Inbox pending count decrements.
12. Corresponding tray notification is canceled.
13. Detail `Marcar como visto` acknowledges.
14. Detail updates live to Vista.
15. Notification `Visto` action acknowledges while app is backgrounded.
16. Notification `Visto` action works after Ackline is removed from Recents.
17. Repeated acknowledgment is harmless/idempotent.
18. Duplicate FCM delivery after acknowledgment does not repost or revert state.
19. App restart preserves Vista.
20. Process restart preserves Vista.
21. Reboot preserves Vista.
22. Setup/FID remains reachable.
23. Background FCM regression still passes.
24. Removed-from-Recents FCM regression still passes.
25. Light-mode screenshot review.
26. Dark-mode screenshot review.

Use fake/non-sensitive alerts only.

---

## AI Route

Planning / architecture: `ChatGPT + GitHub`

Preflight: `/local-quality` preferred because this phase includes a Room migration, cross-surface state transition, and Android notification action.

Primary implementation: `/local-quality` — Qwen3.8 27B 5bit + DFlash2.

Android notification receiver / PendingIntent / Room migration runtime escalation: `Gemini Android Studio`.

Independent review: `/local-review` — Qwen3.8 27B AWQ 5bpw + Lightning MTP.

Final pushed-branch review: `ChatGPT + GitHub`.

---

## Workflow

create `3-explicit-local-ack` from `dev`  
→ replace Phase 3 planning docs  
→ commit planning docs  
→ read-only preflight  
→ approve exact migration/receiver/wiring plan  
→ implement  
→ automated validation  
→ migration/instrumented tests  
→ independent local review  
→ install on existing Oppo data  
→ false-ACK / true-ACK physical QA  
→ screenshot/product review  
→ fix only real blockers  
→ user commit/push  
→ ChatGPT GitHub final review  
→ PASS  
→ merge to `dev`.

The user owns commits and pushes.

---

## Completion Criteria

All must be true:

- database migrates v1 → v2 without data loss;
- schema v1 and v2 are exported;
- `ackSyncState` exists;
- new alerts use `ackSyncState = none`;
- explicit ACK uses `ackSyncState = pending`;
- `acknowledgedAt` changes only through explicit ACK;
- acknowledgment update is atomic;
- repeated acknowledgment preserves original timestamp;
- one shared local acknowledgment operation exists;
- Inbox `Visto` uses the shared operation;
- Detail `Marcar como visto` uses the shared operation;
- notification `Visto` uses the shared operation;
- notification action receiver is private/exported false;
- receiver disk work is moved off the main thread;
- receiver always finishes `goAsync()` result;
- local ACK cancels only the matching notification;
- Pendientes/Vistas update from Room;
- Detail observes live Room state;
- false-ACK matrix passes;
- true-ACK matrix passes;
- duplicate FCM after ACK remains harmless;
- viewed state survives restart/process death/reboot;
- no remote network ACK exists;
- no WorkManager exists;
- no Phase 4 implementation exists;
- FCM regressions remain PASS;
- UI review passes;
- independent review passes;
- final GitHub review = PASS.

---

## Suggested Commits

Planning:

`docs: plan Phase 3 explicit local acknowledgment`

Implementation:

`feat: add explicit local acknowledgment`

Do not commit automatically.

The user owns commits and pushes.
