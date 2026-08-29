# Ackline — Project Specification

## 1. Product Summary

Ackline is a private Android inbox for alerts produced by Hermes Personal Admin.

Hermes already performs source monitoring, incremental state tracking, cheap filtering, LLM analysis, prioritization, and deduplication. This Android app does **not** duplicate those responsibilities.

The app has two core responsibilities:

1. Reliably receive, persist, and present alerts already selected by Hermes.
2. Let the user explicitly acknowledge them, with durable eventual synchronization back to Hermes.

## 2. Primary Product Goal

Replace the current ntfy + persistent WebSocket delivery path with a push architecture that does not require the user to manually reopen the app after Wi-Fi/mobile transitions or temporary connectivity loss.

Target path:

```text
Hermes persistent outbox
    ↓
FCM
    ↓
Android receive boundary
    ↓
Room inbox
    ↓
native notification + app UI
    ↓
explicit Visto
    ↓
local ACK
    ↓
eventual Hermes ACK
```

## 3. Product Priorities

In order:

1. Reliability.
2. Simplicity.
3. Privacy.
4. Low maintenance.
5. UX quality.
6. Additional features.

## 4. Initial User and Deployment

- One user.
- Initially one Oppo Find X9 Pro.
- Android 16 / ColorOS at project start.
- APK sideloading is sufficient.
- No Play Store publication required for MVP.
- Hermes initially runs on a MacBook Pro.
- ACK may initially use Tailscale to reach the Mac.
- Push reception must not depend on Tailscale.

## 5. Core Product Principles

1. Hermes SQLite/outbox remains authoritative for server notification state.
2. Room remains authoritative for the device inbox and local acknowledgment state.
3. FCM is transport, not storage.
4. A notification tray entry is not the product's state.
5. Acknowledgment is always explicit.
6. Temporary ACK network failure must never undo local acknowledgment.
7. Duplicate delivery must be harmless.
8. Real sensitive payloads require app-level E2EE before production use.
9. The app should remain small and maintainable.
10. Core screens must not feel like raw CRUD or generated template UI.

## 6. Core Alert Contract

Hermes produces conceptually:

```json
{
  "notification_id": "...",
  "level": "remember | important | urgent",
  "title": "...",
  "message": "...",
  "created_at": "..."
}
```

The app owns local receipt and acknowledgment metadata.

Protocol versioning is required from the beginning.

## 7. Severity Levels

### REMEMBER

Useful reminder but not time-critical.

Initial intent:

```text
FCM priority: normal
Android channel importance: low
```

### IMPORTANT

Time-sensitive enough that significant delivery delay can reduce usefulness.

Initial intent:

```text
FCM priority: high
Android channel importance: default
```

### URGENT

Requires prompt user attention.

Initial intent:

```text
FCM priority: high
Android channel importance: high
```

Final tuning follows real-device testing and current FCM/Android policy.

## 8. Alert and ACK Semantics

Conceptual lifecycle:

```text
Hermes queued
    ↓
accepted by FCM transport
    ↓
received by Android
    ↓
pending in local inbox
    ↓
acknowledged locally by explicit user action
    ↓
remote ACK pending
    ↓
remote ACK synced
```

The following do **not** acknowledge:

- FCM delivery.
- Android showing the notification.
- Opening the app.
- Opening alert detail.
- Swiping/dismissing the notification.
- Notification disappearing from the tray.

Only explicit actions acknowledge:

- `Visto` notification action.
- `Marcar como visto` in app/detail.

## 9. MVP Screens

### 9.1 Inbox

Required:

- chronological list;
- title;
- summary/description;
- severity;
- concise timestamp;
- pending vs viewed distinction;
- explicit `Visto` action for pending items;
- simple `Pendientes` / `Vistas` filtering.

### 9.2 Alert Detail

Required:

- severity;
- full title;
- full message;
- useful created/received timestamp;
- current pending/viewed state;
- explicit acknowledgment action when pending;
- ACK sync state only when useful, without turning the screen into diagnostics.

### 9.3 Minimal Setup / Diagnostics

MVP setup may expose:

- notification permission state;
- FCM registration/FID state;
- copy FID for manual development pairing;
- app/build version;
- minimal transport health information needed for testing.

Do not turn setup into an operations dashboard.

## 10. UX Direction

Visual direction is inspired by lightweight personal inbox/reminder apps and user-supplied references.

Desired qualities:

```text
clean
lightweight
spacious but not wasteful
strong text hierarchy
restrained severity indicators
chronological
personal inbox, not enterprise dashboard
```

The app must not look AI-generated or vibe-coded.

Initial UI copy may be Spanish because the app is personal and current product examples use Spanish. Do not add localization infrastructure during MVP unless explicitly requested.

## 11. Local Persistence

Minimum conceptual fields:

```text
notificationId       String primary key
protocolVersion      Int
level                AlertLevel
title                String
message              String
createdAt            Instant
receivedAt           Instant
acknowledgedAt       Instant?
ackSyncState         NONE | PENDING | SYNCED | ERROR
ackSyncedAt          Instant?
lastAckError         String?
```

`notificationId` guarantees local idempotency.

## 12. Remote ACK

Initial route:

```text
Android
  ↓
HTTPS
  ↓
Tailscale
  ↓
Mac ACK endpoint
  ↓
Hermes SQLite
```

If ACK endpoint is unreachable:

```text
local alert remains acknowledged
ackSyncState = PENDING
WorkManager retries later
```

## 13. Privacy

Real Hermes payloads can contain private information.

Production-ready MVP uses application-level E2EE before sensitive alert content is sent through FCM.

Transport proof phases may use plaintext **only with fake/non-sensitive payloads**.

## 14. Explicit Non-Goals

Not MVP:

- Gmail/Calendar/Tasks integration in Android.
- Hermes configuration from phone.
- Chat with Hermes.
- Accounts/login.
- Multi-user support.
- Complex multi-device management.
- Analytics SDKs.
- Dashboard charts.
- Teams/collaboration.
- Complex search.
- Public cloud backend created solely for this app.
- Play Store publication.
- Billing/subscriptions.

## 15. Definition of Success

The replacement is successful when the real device repeatedly passes the end-to-end test:

1. App installed and not manually opened for delivery.
2. Screen can be off/backgrounded.
3. Alert sent from Mac arrives without manual refresh.
4. Wi-Fi → mobile transition does not require opening the app to reconnect.
5. Temporary offline state recovers when connectivity returns.
6. Dismissing a tray notification does not mark it viewed.
7. Pending alert remains visible in the app inbox.
8. Explicit `Visto` updates local state immediately.
9. Remote ACK syncs when Hermes is reachable.
10. Offline/Tailscale failure preserves pending ACK and retries later.
11. Duplicate deliveries do not create duplicate alerts.
12. Recovery can reconcile a missed transport event.
13. The UI feels intentional and polished enough for daily personal use.
