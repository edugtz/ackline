# Ackline — Architecture

## 1. Architecture Goal

Use simple, maintainable architecture.

Avoid overengineering.

Ackline should remain understandable by one developer years later.

Reliability, explicit state transitions, privacy, and low maintenance matter more than framework sophistication.

---

## 2. Source-of-Truth Rules

1. `docs/CURRENT_PHASE.md` is the active implementation scope.
2. Actual Kotlin/Python source is authoritative for exact paths, fields, APIs, and runtime behavior.
3. If docs conflict with code, stop and report before editing.
4. Hermes SQLite/outbox is authoritative for server-side notification state.
5. Room is authoritative for device Inbox and local acknowledgment state.
6. FCM is transport only.
7. Android notification tray state is never source of truth.
8. Setup/debug state is not Inbox/ACK state.
9. Only explicit acknowledgment actions may modify local acknowledgment state.
10. Do not add future-phase fields/services unless the active phase requires them.

---

## 3. High-Level Runtime Architecture

Target MVP:

```text
Hermes Personal Admin
        │
        ▼
persistent SQLite notification outbox
        │
        ▼
FCM sender boundary
        │
        ▼
FCM
        │
        ▼
FirebaseMessagingService
        │
        ▼
IncomingAlertEnvelope
        │
        ▼
AlertRepository
        │
        ▼
Room
   ┌────┴─────────────────────┐
   │                          │
   ▼                          ▼
Compose Inbox/Detail     Android notification
   │                          │
   │ explicit Visto           │ explicit Visto
   └─────────────┬────────────┘
                 ▼
      LocalAcknowledgmentManager
                 │
         atomic Room ACK
                 │
          cancel tray item
                 │
                 ▼
       ackSyncState = PENDING
                 │
       Phase 4 WorkManager sync
                 │
                 ▼
          Hermes ACK endpoint
                 │
                 ▼
          Hermes SQLite ACK
```

Phase 3 stops at durable local `PENDING`.

No remote ACK is performed yet.

---

## 4. Android Architecture

Single Android module:

`:app`

Primary UI data flow:

Compose  
→ ViewModel  
→ app-owned repository/manager  
→ Room.

Push receive flow:

`FirebaseMessagingService`  
→ protocol parse  
→ `IncomingAlertEnvelope`  
→ `AlertRepository`  
→ Room  
→ native notification only for new insert.

Local acknowledgment flow:

Inbox / Detail / notification action  
→ one `LocalAcknowledgmentManager`  
→ `AlertRepository` atomic ACK  
→ Room  
→ cancel matching tray notification.

Manual dependency wiring remains preferred over DI frameworks.

---

## 5. Transport Isolation

Firebase-specific types stop at the push boundary.

Boundary:

`FirebaseMessagingService`  
→ Firebase map extraction  
→ protocol validation  
→ `IncomingAlertEnvelope`  
→ normal application code.

Nothing below the push boundary depends on `RemoteMessage`.

FCM must remain replaceable without rewriting:

- Room;
- Inbox;
- acknowledgment semantics;
- ACK retry state;
- E2EE plaintext model.

---

## 6. FCM Registration

Continue current Firebase Installation ID registration.

Development pairing remains:

Android current FID  
→ Setup copy action  
→ protected local Mac configuration  
→ Firebase sender targets FID.

No registration backend is required for MVP.

Treat FID as sensitive operational data.

---

## 7. FCM Message Type

Use FCM data messages.

Current plaintext development protocol:

```json
{
  "protocol": "1",
  "notification_id": "test-123",
  "level": "important",
  "title": "Test alert",
  "message": "Non-sensitive development payload",
  "created_at": "2026-08-29T20:00:00Z"
}
```

Local ACK fields never come from FCM.

The device owns:

- `receivedAt`;
- `acknowledgedAt`;
- `ackSyncState`.

---

## 8. App-Owned Models

Core app concepts:

- `IncomingAlertEnvelope`
- `Alert`
- `AlertLevel`
- `AckSyncState`

`AlertLevel`:

- REMEMBER
- IMPORTANT
- URGENT

Phase 3 `AckSyncState`:

- NONE
- PENDING

Future remote-sync phase may extend ACK state as required.

Do not expose raw wire/storage strings throughout UI.

---

## 9. Local Database Evolution

### Version 1

Phase 2 schema:

- `notificationId`
- `protocolVersion`
- `level`
- `title`
- `message`
- `createdAtEpochMillis`
- `receivedAtEpochMillis`
- `acknowledgedAtEpochMillis`

### Version 2

Phase 3 adds:

- `ackSyncState`

Migration:

```sql
ALTER TABLE alerts
ADD COLUMN ackSyncState TEXT NOT NULL DEFAULT 'none'
```

No destructive migration.

Keep exported schemas for each version.

Future schema versions require explicit migration review.

---

## 10. Local Alert Semantics

Pending:

`acknowledgedAt == null`

Viewed:

`acknowledgedAt != null`

Newly received:

- `acknowledgedAt = null`
- `ackSyncState = NONE`

Newly locally acknowledged:

- `acknowledgedAt = now`
- `ackSyncState = PENDING`

Local ACK remains valid even when remote ACK does not yet exist.

---

## 11. Receive Idempotency

`notificationId` is the business/local idempotency key.

Receive:

FCM  
→ validate  
→ Room INSERT IGNORE by primary key  
→ new row = notify  
→ existing row = no repost.

A duplicate message must never overwrite local acknowledgment metadata.

Therefore a duplicate received after an alert is Vista must remain Vista.

---

## 12. Persist Before Presentation

For a new valid alert:

Room persist  
→ then tray notification.

If notification permission is unavailable, Room still contains the alert.

If Room persistence fails, do not create a tray-only authoritative state.

---

## 13. Notification Channels

Stable channels remain:

| Level | Channel ID | Channel name | Importance |
|---|---|---|---|
| REMEMBER | `ackline_remember` | `Ackline · Remember` | LOW |
| IMPORTANT | `ackline_important` | `Ackline · Important` | DEFAULT |
| URGENT | `ackline_urgent` | `Ackline · Urgent` | HIGH |

Do not rename channel IDs casually because Android persists channels.

FCM priorities remain:

- remember → normal
- important → high
- urgent → high

---

## 14. Notification Identity

Current native notification identity:

`notificationId.hashCode()`

Use the same identity for:

- posting;
- canceling the matching notification.

Do not call `cancelAll()` for one acknowledged alert.

---

## 15. Explicit ACK Semantics

Only explicit actions acknowledge:

- notification action `Visto`;
- Inbox `Visto`;
- Detail `Marcar como visto`.

These never acknowledge:

- FCM delivery;
- tray display;
- body tap;
- tray dismissal;
- app launch;
- Detail open;
- tab selection;
- restart/reboot.

There is no inferred read state.

---

## 16. Atomic Acknowledgment Transaction

The database owns the state transition.

Preferred SQL:

```sql
UPDATE alerts
SET acknowledgedAtEpochMillis = :timestamp,
    ackSyncState = 'pending'
WHERE notificationId = :notificationId
  AND acknowledgedAtEpochMillis IS NULL
```

This makes the first acknowledgment win.

A later duplicate acknowledgment updates zero rows and cannot replace the original timestamp.

---

## 17. Shared LocalAcknowledgmentManager

Phase 3 introduces one concrete cross-layer operation.

Responsibilities:

1. perform local atomic ACK through repository;
2. treat repeated ACK idempotently;
3. cancel the matching Android notification;
4. return a small result where useful.

This manager does **not**:

- perform HTTP;
- schedule retries;
- know Hermes networking;
- use Tailscale;
- perform encryption.

It is not a generic Clean Architecture use-case layer.

---

## 18. Inbox ACK Flow

Pending Inbox row:

`Visto` action  
→ ViewModel background call  
→ `LocalAcknowledgmentManager`  
→ atomic Room update  
→ Room Flow changes  
→ row leaves Pendientes  
→ row appears in Vistas  
→ tray item canceled.

The row body remains navigation-only.

---

## 19. Detail ACK Flow

Detail must observe live Room data in Phase 3.

Navigation identifies Detail by `notificationId`.

`AlertDetailViewModel` observes the repository.

Pending Detail:

`Marcar como visto`  
→ shared manager  
→ Room update  
→ Flow emits Vista  
→ button disappears.

Opening Detail alone is read-only.

---

## 20. Notification Action Flow

Native notification action:

`Visto`

uses an immutable broadcast PendingIntent targeting private:

`AcknowledgeReceiver`.

Receiver flow:

`onReceive()`  
→ validate action/ID  
→ `goAsync()`  
→ bounded background executor  
→ shared local manager  
→ `finish()` in finally.

The receiver does not start network work or an Activity.

---

## 21. BroadcastReceiver Lifecycle Rule

Manifest BroadcastReceiver `onReceive()` runs on the main thread.

Disk IO must not block that thread.

For the local Room update:

- use `goAsync()`;
- hand work to a bounded background execution path;
- always call `PendingResult.finish()`.

Work must remain short.

Long remote work belongs to later WorkManager phase, not this receiver.

---

## 22. PendingIntent Identity Rule

Notification actions require one PendingIntent per business notification.

Do not rely only on extras for uniqueness.

Use a unique identity based on the `notificationId`, such as:

- stable requestCode plus
- an encoded unique `Intent.data` URI.

Use immutable flags.

Receiver must still validate the supplied business ID.

---

## 23. ACK Sync State Boundary

Phase 3 creates durable:

`PENDING`

but does not consume it.

Phase 4 will own:

- HTTP ACK contract;
- WorkManager;
- retry/backoff;
- remote success/failure;
- eventual SYNCED/ERROR state;
- optional sync timestamps/errors.

Do not implement those early.

---

## 24. Application Wiring

`AcklineApplication` owns:

- one `AcklineDatabase`;
- one `AlertRepository`;
- one `LocalAcknowledgmentManager`;
- optionally one small ACK receiver executor.

FCM service and UI share the same data layer.

No Hilt/Koin required.

---

## 25. UI Architecture

Screens:

- Inbox;
- Alert Detail;
- Setup.

Phase 3 remains a small manually navigated app.

No Navigation Compose is required.

Inbox uses `InboxViewModel`.

Detail now justifiably uses `AlertDetailViewModel` because acknowledgment changes Room state while Detail is visible.

---

## 26. Inbox Product Direction

Preserve the accepted Phase 2 visual system:

- flat rows;
- restrained severity;
- strong typography;
- Pendientes/Vistas;
- compact density;
- no card soup.

Phase 3 adds a compact explicit `Visto` action.

The action must be clear but not visually dominate every alert.

Viewed rows no longer expose acknowledgment action.

---

## 27. Detail Product Direction

Preserve the lightweight Detail hierarchy.

Pending:

- severity;
- title;
- message;
- timestamps;
- Pendiente;
- `Marcar como visto`.

Viewed:

- same information;
- Vista;
- no ACK button.

Do not add remote-sync diagnostics by default.

---

## 28. Migration Testing

Because Phase 2 data already exists on the real device, schema migration is product behavior.

Keep:

- schema v1;
- schema v2.

Use Room migration-test tooling where practical.

Physical upgrade over the existing installation is also mandatory.

Do not validate migration by clearing data.

---

## 29. ACK Testing Invariants

False ACK:

- receive;
- display;
- tap/open;
- swipe;
- app launch;
- Detail open;
- restart.

True ACK:

- notification Visto;
- Inbox Visto;
- Detail Marcar como visto.

Idempotency:

- repeated ACK preserves first timestamp;
- duplicate FCM after ACK preserves Vista.

Persistence:

- local ACK survives process death/reboot.

---

## 30. Remote ACK Architecture

Introduced in Phase 4.

Target later flow:

Room `ackSyncState=PENDING`  
→ WorkManager drain  
→ HTTPS/Tailscale  
→ Hermes ACK endpoint  
→ Hermes SQLite  
→ local SYNCED.

Remote availability must never undo local Vista state.

---

## 31. E2EE Boundary

Production personal content still requires application-level E2EE before real use.

Target later:

Hermes plaintext  
→ AES-256-GCM  
→ FCM ciphertext  
→ Android authenticated decrypt  
→ Room readable local alert.

Until that phase passes:

fake/non-sensitive content only.

---

## 32. Backup / Privacy

Keep:

`android:allowBackup="false"`

Secrets remain outside the repository.

Do not log:

- private Firebase service-account material;
- encryption keys;
- future ACK credentials;
- FID;
- real private alert content.

---

## 33. Dependency Policy

Current core dependencies:

- Compose / Material 3
- Firebase Messaging
- Room 2.8.4
- KSP 2.3.10
- Lifecycle 2.11.0

Phase 3 may add Room testing only for migration coverage.

Do not add:

- WorkManager yet;
- Retrofit;
- Hilt;
- Koin;
- Navigation Compose;
- generic sync frameworks;
- socket libraries.

---

## 34. Planned Project Shape

Conceptual shape after Phase 3:

```text
app/
  src/main/java/com/edu/ackline/
    AcklineApplication.kt
    MainActivity.kt

    model/
      Alert.kt
      AckSyncState.kt

    push/
      AcklineMessagingService.kt
      IncomingAlertEnvelope.kt

    data/
      AlertRepository.kt
      local/
        AlertEntity.kt
        AlertDao.kt
        AcklineDatabase.kt

    ack/
      LocalAcknowledgmentManager.kt
      AcknowledgeReceiver.kt

    notifications/
      AcklineNotificationManager.kt

    feature/
      inbox/
        InboxScreen.kt
        InboxViewModel.kt
      detail/
        AlertDetailScreen.kt
        AlertDetailViewModel.kt
      setup/
        SetupScreen.kt

    ui/
      AcklineApp.kt
```

Later phases add remote ACK/security/recovery files only when active.

---

## 35. Long-Term Replaceability

Changing FCM transport should not require rewriting:

- Room;
- local acknowledgment;
- Inbox/Detail;
- remote ACK state machine;
- E2EE plaintext semantics.

Local ACK remains an app-owned state transition independent of transport provider.

---

## 36. Reliability Rule

Changes to notification actions must not regress Phase 1/2 behavior.

Do not introduce:

- foreground service;
- custom reconnect loop;
- persistent socket;
- battery exemption;
- ColorOS hacks.

Standard FCM remains the push path unless evidence proves otherwise.

---

## 37. Phase Discipline

Architecture describes the intended product.

`CURRENT_PHASE.md` defines what may be implemented now.

Phase 3 ends at:

durable local acknowledgment + `ackSyncState=PENDING`.

It does not include remote synchronization.

Every phase remains bounded, validated, physically QA'd where appropriate, independently reviewed, and merged only after final PASS.
