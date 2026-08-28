# Hermes Notifications — Architecture

## 1. Architecture Goal

Use simple, maintainable architecture.

Avoid overengineering.

The app should remain understandable by one developer years later.

## 2. Source-of-Truth Rules

1. `docs/CURRENT_PHASE.md` is the active implementation scope.
2. Actual Kotlin/Python source is authoritative for exact paths, fields, interfaces, and build behavior.
3. If docs conflict with code, report before editing.
4. Hermes SQLite/outbox is authoritative for server-side notification state.
5. Room is authoritative for device inbox and local acknowledgment state.
6. FCM is transport only.
7. Android notification tray state is never source of truth.
8. Do not add future-phase fields/screens unless the active phase requires them.

## 3. High-Level Runtime Architecture

```text
Hermes Personal Admin
        │
        ▼
persistent SQLite notification outbox
        │
        ▼
firebase_sender.py
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
AlertRepository / Room
        │
   ┌────┴───────────────┐
   │                    │
   ▼                    ▼
Compose Inbox      Android notification
                        │
                     [Visto]
                        │
                        ▼
                  local ACK transaction
                        │
                        ▼
                  ACK_PENDING in Room
                        │
                        ▼
                  WorkManager sync
                        │
                        ▼
                  Hermes ACK endpoint
                        │
                        ▼
                  Hermes SQLite ACK
```

## 4. Android Architecture

Single Android module for MVP:

```text
:app
```

Preferred flow:

```text
Compose UI
  ↓
ViewModel
  ↓
Repository
  ↓
Room / small network client / platform services
```

Manual dependency wiring is preferred over Hilt for this scope.

Do not add use-case layers or interface hierarchies unless they solve a real problem.

## 5. Transport Isolation

Firebase must remain replaceable.

Desired boundary:

```text
FirebaseMessagingService
        ↓
Firebase-specific parse
        ↓
IncomingAlertEnvelope   ← app-owned type
        ↓
normal application code
```

Nothing below `IncomingAlertEnvelope` should depend on `RemoteMessage`.

On the Hermes side:

```text
notification outbox
        ↓
PushSender boundary
        ↓
FCM implementation
```

Do not create a framework for multiple transports in MVP. A small explicit boundary is enough.

## 6. FCM Registration

Use the current Firebase Installation ID (FID) registration approach.

MVP pairing:

```text
Android obtains current FID
        ↓
minimal setup/debug surface shows copy action
        ↓
user copies it to Mac
        ↓
store outside repo in protected config
        ↓
firebase_sender.py targets that FID
```

Do not build a device-registration backend for v1.

If the FID changes, the app must expose/update the current identifier in a way that can be re-paired.

## 7. FCM Message Type

Hermes alerts use **data messages**.

Reason:

The app must process the payload itself to:

- validate protocol;
- decrypt after E2EE;
- deduplicate;
- persist in Room;
- create the Android notification locally;
- attach explicit `Visto`;
- preserve acknowledgment semantics.

Do not rely on automatic notification-message handling for real alerts.

## 8. Protocol Versioning

Version the Hermes-to-Android envelope from day one.

Before E2EE, fake transport-test payloads may conceptually look like:

```json
{
  "protocol": "1",
  "notification_id": "test-123",
  "level": "important",
  "title": "Test alert",
  "message": "Non-sensitive development payload",
  "created_at": "2026-08-28T20:00:00Z"
}
```

After E2EE:

```json
{
  "protocol": "1",
  "kid": "device-1",
  "nonce": "base64...",
  "ciphertext": "base64..."
}
```

The decrypted payload contains the alert fields.

Do not include sensitive title/message text outside ciphertext once E2EE is production-enabled.

## 9. Local Data Model

Conceptual Room entity:

```text
AlertEntity
- notificationId: String PRIMARY KEY
- protocolVersion: Int
- level: AlertLevel
- title: String
- message: String
- createdAt: Instant
- receivedAt: Instant
- acknowledgedAt: Instant?
- ackSyncState: AckSyncState
- ackSyncedAt: Instant?
- lastAckError: String?
```

Enums:

```text
AlertLevel:
REMEMBER
IMPORTANT
URGENT

AckSyncState:
NONE
PENDING
SYNCED
ERROR
```

Semantics:

```text
acknowledgedAt == null
=> pending

acknowledgedAt != null
=> explicitly viewed
```

Opening/dismissing never changes `acknowledgedAt`.

## 10. Idempotent Receive Flow

```text
FCM data arrives
    ↓
parse / validate
    ↓
decrypt when E2EE enabled
    ↓
insert by notificationId
    ↓
already exists?
  ├─ yes -> do not duplicate
  └─ no  -> create Android notification
```

Duplicate transport delivery is expected and safe.

## 11. Android Notification Design

Create separate channels:

```text
Hermes · Remember
Hermes · Important
Hermes · Urgent
```

Initial intent:

| Level | FCM delivery priority | Android channel importance |
|---|---|---|
| REMEMBER | NORMAL | LOW |
| IMPORTANT | HIGH | DEFAULT |
| URGENT | HIGH | HIGH |

High-priority FCM is reserved for time-sensitive, user-visible alerts.

The exact mapping is validated against current Android/FCM policy and real Oppo behavior during the reliability phase.

## 12. Explicit Acknowledgment Flow

Both UI surfaces call one operation:

```text
acknowledge(notificationId)
```

Transaction intent:

```text
1. Set acknowledgedAt = now
2. Set ackSyncState = PENDING
3. Cancel/update Android tray notification
4. Enqueue unique ACK sync work
```

Remote availability must not block step 1.

## 13. ACK Retry Architecture

Use WorkManager for durable eventual retry.

Recommended design:

```text
Unique work name: hermes-ack-sync
Network constraint: CONNECTED
```

Worker behavior:

```text
load rows where ackSyncState = PENDING
    ↓
POST idempotent ACK to Hermes
    ↓
2xx => SYNCED
transient network/5xx => retry with backoff
permanent auth/protocol error => ERROR + diagnostic
```

Do not create a permanently running worker or one long-lived service per alert.

## 14. Recovery / Reconciliation

FCM is the fast path, not the only recovery path.

Before fully retiring ntfy, implement a small reconciliation path:

```text
GET /notifications/pending
```

Potential triggers:

- `onDeletedMessages()`;
- app returns online after long offline period;
- explicit/manual diagnostic sync during testing;
- future low-frequency safety sync only if real evidence justifies it.

Flow:

```text
Hermes pending notifications
        ↓
Android reconciliation
        ↓
Room insert by notificationId
        ↓
missing alert becomes available
```

Room idempotency makes later duplicate FCM delivery harmless.

## 15. E2EE Boundary

Production-ready design:

```text
Hermes plaintext alert
      ↓
standard authenticated encryption
      ↓
FCM ciphertext
      ↓
Android decrypts locally
      ↓
Room stores readable local alert
```

Initial recommended primitive:

```text
AES-256-GCM
```

Requirements:

- standard library/platform implementation only;
- unique nonce/IV under a key;
- authenticated decryption failure rejects the payload;
- key identifier supports future rotation;
- Android key material protected using Android Keystore or an appropriate Keystore-backed design;
- Mac key material stored outside repo with restrictive permissions;
- no key in prompts, logs, docs, command-line arguments, or GitHub.

Do not invent custom crypto constructions.

## 16. Secrets

Hermes-side secrets live outside source control, for example conceptually:

```text
~/.hermes/secrets/
```

Exact paths are decided during implementation.

Do not store service-account JSON or encryption keys in the repository.

Use environment/config-file indirection with restrictive filesystem permissions.

## 17. Planned Android Project Shape

Exact paths are confirmed from the real repository before editing.

Conceptual layout:

```text
app/
  src/main/java/.../
    MainActivity.kt
    app/
    push/
      HermesMessagingService.kt
      IncomingAlertEnvelope.kt
    data/
      db/
      repository/
    notifications/
      HermesNotificationManager.kt
      AcknowledgeReceiver.kt
    ack/
      AckClient.kt
      AckSyncWorker.kt
    security/
      PayloadCrypto.kt
    feature/
      inbox/
      detail/
      setup/
```

Do not create every package in Phase 0. Introduce structure only when its phase requires it.

## 18. Dependency Policy

Expected MVP dependencies include only what phases prove necessary:

```text
Jetpack Compose / Material 3
Lifecycle/ViewModel
Room
WorkManager
Firebase Messaging
Kotlinx Serialization
OkHttp or another small HTTP client if needed
```

Do not add:

```text
Hilt
Retrofit
analytics SDKs
crash analytics merely for development
multi-module framework
socket libraries
generic sync frameworks
```

unless explicitly justified later.

## 19. Long-Term Replaceability

A future FCM API or provider migration should primarily affect:

```text
Android push boundary
Hermes push sender
```

It should not require rewriting:

```text
Room
Inbox UI
Visto semantics
ACK state
WorkManager
E2EE payload semantics
Hermes notification identity
```

That is the long-term architecture goal.
