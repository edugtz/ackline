# Ackline — Architecture

## 1. Architecture Goal

Use simple, maintainable architecture.

Avoid overengineering.

The app should remain understandable by one developer years later.

Reliability and explicit state are more important than framework sophistication.

---

## 2. Source-of-Truth Rules

1. `docs/CURRENT_PHASE.md` is the active implementation scope.
2. Actual Kotlin/Python source is authoritative for exact paths, fields, interfaces, and build behavior.
3. If docs conflict with code, report before editing.
4. Hermes SQLite/outbox is authoritative for server-side notification state.
5. Room is authoritative for device Inbox and local acknowledgment state once persistence exists.
6. FCM is transport only.
7. Android notification tray state is never source of truth.
8. Setup/debug state is not Inbox state.
9. Do not add future-phase fields/screens unless the active phase requires them.
10. Persist an accepted alert locally before relying on transient tray presentation.

---

## 3. High-Level Runtime Architecture

Target MVP architecture:

```text
Hermes Personal Admin
        │
        ▼
persistent SQLite notification outbox
        │
        ▼
Firebase sender boundary
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

The active phase introduces only the layers it actually needs.

---

## 4. Android Architecture

Single Android module for MVP: `:app`.

Preferred application flow once persistence exists:

Compose UI → ViewModel → `AlertRepository` → Room.

Push path:

`FirebaseMessagingService` → `IncomingAlertEnvelope` → `AlertRepository` → Room → native notification only if newly inserted.

Manual dependency wiring is preferred over Hilt.

A small Application-owned dependency graph is sufficient.

Do not add use-case layers or interface hierarchies unless they solve a real problem.

---

## 5. Transport Isolation

Firebase must remain replaceable.

Boundary:

`FirebaseMessagingService` → Firebase-specific extraction → protocol validation → `IncomingAlertEnvelope` → normal application code.

Nothing below `IncomingAlertEnvelope` should depend on:

- `RemoteMessage`;
- `FirebaseMessagingService`.

Hermes-side boundary:

notification outbox → push sender boundary → FCM implementation.

Do not create a framework for multiple transports in MVP.

One small explicit boundary is enough.

---

## 6. FCM Registration

Use the current Firebase Installation ID registration approach.

MVP pairing:

Android obtains current FID → setup/debug surface exposes copy → user copies to Mac → store outside repo in protected config → Firebase sender targets that FID.

Do not build a device-registration backend for v1.

If the FID changes, Setup must continue exposing the current identifier so it can be re-paired.

---

## 7. FCM Message Type

Ackline alerts use **FCM data messages**.

The app processes the payload itself to:

- validate protocol;
- decrypt after E2EE;
- deduplicate;
- persist in Room;
- create Android notification locally;
- attach explicit Visto later;
- preserve acknowledgment semantics.

Do not rely on automatic FCM notification-message handling for real alerts.

---

## 8. Protocol Versioning

Durable plaintext development contract:

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

Phase 1 used a temporary transport-spike `sent_at`.

Before Room persistence, Phase 2 normalizes the durable contract to `created_at`.

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

---

## 9. App-Owned Alert Types

Core concepts:

- `IncomingAlertEnvelope`
- `Alert`
- `AlertLevel`

`IncomingAlertEnvelope` represents a validated incoming alert before persistence.

`Alert` is the app-owned persisted/read model used outside Room.

`AlertLevel` values:

- `REMEMBER`
- `IMPORTANT`
- `URGENT`

Raw unvalidated wire strings must not become the application-level severity model.

---

## 10. Local Data Model

Eventual MVP conceptual state includes:

- `notificationId`
- `protocolVersion`
- `level`
- `title`
- `message`
- `createdAt`
- `receivedAt`
- `acknowledgedAt`
- `ackSyncState`
- `ackSyncedAt`
- `lastAckError`

Active phases introduce only fields they require.

Initial Phase 2 schema:

`AlertEntity`

- `notificationId: String` primary key
- `protocolVersion: Int`
- `level: String`
- `title: String`
- `message: String`
- `createdAtEpochMillis: Long`
- `receivedAtEpochMillis: Long`
- `acknowledgedAtEpochMillis: Long?`

Phase 2 does **not** add yet:

- `ackSyncState`
- `ackSyncedAt`
- `lastAckError`

Core semantics:

`acknowledgedAt == null` → pending  
`acknowledgedAt != null` → explicitly acknowledged/viewed.

No Phase 2 user action changes `acknowledgedAt`.

---

## 11. Idempotent Receive Flow

`notificationId` is the business idempotency key.

Required flow:

FCM data arrives → parse/validate → decrypt when E2EE exists → Room insert by `notificationId` → if new row, create Android notification → if duplicate, do not duplicate and do not repost.

Duplicate transport delivery is expected and safe.

Use primary-key/conflict semantics rather than query-before-insert deduplication.

---

## 12. Persist-Before-Presentation Rule

For a valid incoming alert:

Room persist → native notification.

Reason: **Room = truth; tray = transient presentation.**

If notification permission is unavailable:

- persisted alert still exists;
- tray may be absent.

If persistence fails:

- do not create a tray-only authoritative state.

Recovery from rare persistence/transport failure belongs to later recovery phases.

---

## 13. Android Notification Design

Current proven channels:

- `Ackline · Remember`
- `Ackline · Important`
- `Ackline · Urgent`

Stable IDs:

- `ackline_remember`
- `ackline_important`
- `ackline_urgent`

Mapping:

| Level | FCM delivery priority | Android channel importance |
|---|---|---|
| REMEMBER | NORMAL | LOW |
| IMPORTANT | HIGH | DEFAULT |
| URGENT | HIGH | HIGH |

These mappings passed the Phase 1 Oppo reliability gate.

Do not rename channel IDs without a real migration/product reason because Android notification channels persist.

---

## 14. Notification Interaction

These do not acknowledge:

- delivery;
- tray display;
- notification tap;
- notification dismissal;
- opening Ackline;
- opening Alert Detail.

A neutral notification content intent may open Ackline.

Only a future explicit Visto action may acknowledge.

---

## 15. Explicit Acknowledgment Flow

Introduced in a later phase.

Both eventual UI surfaces call `acknowledge(notificationId)`.

Transaction intent:

1. set `acknowledgedAt = now`;
2. set `ackSyncState = PENDING`;
3. cancel/update Android tray notification;
4. enqueue unique ACK sync work.

Remote availability must not block local acknowledgment.

Do not implement this operation before the acknowledgment phase.

---

## 16. ACK Retry Architecture

Later phase.

Use WorkManager for durable eventual retry.

Concept:

- unique work name: `ackline-ack-sync`
- network constraint: `CONNECTED`

Worker behavior:

load rows with pending ACK → POST idempotent ACK to Hermes → 2xx = SYNCED → transient network/5xx = retry with backoff → permanent auth/protocol error = ERROR + bounded diagnostic.

Prefer one drain worker over one permanent worker per alert.

Do not create a permanently running service.

---

## 17. Recovery / Reconciliation

FCM is realtime transport, not the only eventual recovery path.

Later recovery may introduce `GET /notifications/pending`.

Potential triggers:

- `onDeletedMessages()`;
- long-offline recovery;
- explicit diagnostic sync;
- low-frequency safety sync only if evidence justifies it.

Flow:

Hermes pending notifications → Android reconciliation → Room insert by `notificationId` → missing local alert recovered.

Room idempotency makes later duplicate FCM delivery harmless.

Do not turn reconciliation into aggressive polling.

---

## 18. E2EE Boundary

Production-ready design:

Hermes plaintext alert → authenticated encryption → FCM ciphertext → Android decrypts locally → Room stores readable local alert.

Initial approved primitive: `AES-256-GCM`.

Requirements:

- standard library/platform implementation;
- unique nonce/IV under a key;
- authenticated decryption;
- key identifier for rotation;
- Android protected key storage;
- Mac key outside repo;
- no secrets in prompts, logs, docs, CLI, or GitHub.

Do not invent custom crypto.

Until E2EE passes: **fake/non-sensitive payloads only.**

---

## 19. Local Backup Policy

Ackline Room data can contain private alert content.

MVP policy: `android:allowBackup="false"`.

Do not silently cloud-backup or device-transfer the Room Inbox.

A later explicit privacy/product review may revisit this.

Hermes/reconciliation is the planned logical recovery source.

---

## 20. Secrets

Hermes-side secrets live outside source control, conceptually under `~/.hermes/secrets/`.

Never store in the repository:

- Firebase Admin service-account private key;
- E2EE key;
- ACK auth secret;
- Tailscale credentials;
- Android signing keys.

Treat FID as sensitive operational data and avoid logging it unnecessarily.

---

## 21. UI Architecture

Product screens:

- Inbox
- Alert Detail
- Setup / Diagnostics

Phase 2 may use a small root `AcklineApp` screen state instead of adding a navigation framework.

Compose data flow:

Screen → ViewModel → Repository → Room.

Do not query Room directly from composables.

---

## 22. Inbox Product Direction

Inbox is chronological and lightweight.

Required information:

- severity;
- title;
- summary;
- timestamp;
- pending/viewed distinction.

Desired qualities:

- clean;
- restrained;
- fast to scan;
- personal;
- intentional.

Reject:

- generic CRUD;
- card soup;
- enterprise dashboard;
- random colors;
- emoji iconography;
- gratuitous gradients;
- glassmorphism;
- verbose helper text.

Material 3 is a toolkit, not the Ackline identity.

---

## 23. Pendientes / Vistas

State derives from persisted `acknowledgedAt`.

Pendientes: `acknowledgedAt == null`

Vistas: `acknowledgedAt != null`

Phase 2 creates the display/filter structure.

Phase 3 introduces the explicit state transition.

Do not invent opened/read/dismissed state.

---

## 24. Alert Detail

Detail presents:

- severity;
- full title;
- full message;
- created timestamp;
- received timestamp where useful;
- pending/viewed state.

Opening detail is read-only with respect to acknowledgment state.

Do not infer acknowledgment from visibility.

---

## 25. Planned Android Project Shape

Exact paths are confirmed during preflight.

```text
app/
  src/main/java/com/edu/ackline/
    AcklineApplication.kt
    MainActivity.kt

    model/
      Alert.kt

    push/
      AcklineMessagingService.kt
      IncomingAlertEnvelope.kt

    data/
      AlertRepository.kt
      local/
        AlertEntity.kt
        AlertDao.kt
        AcklineDatabase.kt

    notifications/
      AcklineNotificationManager.kt
      AcknowledgeReceiver.kt      # later phase

    ack/
      AckClient.kt                # later phase
      AckSyncWorker.kt            # later phase

    security/
      PayloadCrypto.kt            # later phase

    feature/
      inbox/
        InboxScreen.kt
        InboxViewModel.kt
      detail/
        AlertDetailScreen.kt
      setup/
        SetupScreen.kt

    ui/
      AcklineApp.kt
      theme/
```

Do not create later-phase files/packages until their active phase.

---

## 26. Dependency Policy

Expected MVP dependencies are added only when required:

- Jetpack Compose / Material 3
- Lifecycle/ViewModel
- Room
- KSP for Room compiler
- WorkManager later
- Firebase Messaging
- small HTTP client later if needed

Do not add by default:

- Hilt
- Retrofit
- analytics SDK
- generic sync framework
- multi-module framework
- socket library

No dependency is justified merely because it is conventional.

---

## 27. Long-Term Replaceability

A future FCM migration should primarily affect:

- Android push boundary;
- Hermes push sender.

It must not require rewriting:

- Room;
- Inbox UI;
- Visto semantics;
- ACK state;
- WorkManager retry logic;
- E2EE plaintext semantics;
- Hermes notification identity.

---

## 28. Reliability Rule

Phase 1 proved FCM reliability on the target Oppo.

Future changes touching:

- `FirebaseMessagingService`;
- notification posting;
- manifest push configuration;
- FCM sender priority

must preserve that behavior.

Do not introduce:

- foreground service;
- custom persistent socket;
- manual reconnect loop;
- battery exemption;
- ColorOS workaround

without reproducible evidence that standard FCM behavior regressed.

---

## 29. Phase Discipline

Architecture describes the intended MVP.

`CURRENT_PHASE.md` decides what may be implemented now.

A later section in this document is not permission to implement it early.

Every phase remains:

- bounded;
- reviewed;
- validated;
- manually QA'd when applicable;
- merged only after final PASS.
