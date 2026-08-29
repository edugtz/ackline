# Ackline — Acceptance Criteria

## 1. Global MVP Acceptance

The app is acceptable as the ntfy replacement when:

- APK installs on the target Oppo.
- App opens without crash.
- FCM/FID registration is healthy.
- Alerts can arrive while app is backgrounded.
- Alerts can arrive with screen off under expected FCM priority semantics.
- Wi-Fi/mobile transitions do not require manually opening the app to restore push.
- Temporary offline periods do not cause silent permanent loss when recovery is possible.
- Alerts are persisted locally.
- Duplicate `notificationId` does not duplicate inbox entries.
- Notification dismissal does not mark an alert viewed.
- Opening app/detail does not mark an alert viewed.
- Explicit `Visto` marks local state immediately.
- Remote ACK can fail temporarily without losing local acknowledgment.
- Pending ACK syncs when connectivity/Tailscale/Hermes returns.
- Recovery/reconciliation can restore a missed pending alert.
- Real sensitive payloads use application-level E2EE.
- No paid runtime service is required for the normal single-user flow beyond no-cost FCM availability.
- No unnecessary backend/account infrastructure exists.
- UI is intentional enough for daily use and does not feel like generated CRUD.

## 2. Transport Reliability Acceptance

Required pass cases:

- foreground;
- background;
- removed from Recents without Force Stop;
- screen off;
- Wi-Fi;
- mobile data;
- Wi-Fi → mobile;
- mobile → Wi-Fi;
- message sent during transition;
- airplane mode → message queued → connectivity restored;
- Doze/high-priority visible alert test;
- multi-hour idle;
- multi-day real-use test before retiring ntfy.

Critical fail condition:

```text
network works
+
alert does not arrive
+
opening Ackline causes it to arrive
```

If reproducible in normal use, the push solution is not accepted.

Manual Android `Force stop` is evaluated separately from normal background/Recents behavior.

## 3. Inbox Acceptance

- Chronological list works.
- `Pendientes` is distinguishable from `Vistas`.
- Title and summary are readable at a glance.
- Severity is distinguishable without dominating the UI.
- Timestamp is discreet but available.
- Tapping an alert opens detail.
- Full message is readable in detail.
- App restart preserves list state.
- Reboot preserves persisted alert state.
- Empty states guide the user without filler copy.
- Swiping a tray notification does not remove the inbox record.

## 4. Acknowledgment Acceptance

Only explicit actions acknowledge.

Required false-ACK tests:

```text
receive        -> still pending
show tray      -> still pending
swipe tray     -> still pending
open app       -> still pending
open detail    -> still pending
```

Required true-ACK tests:

```text
notification [Visto]        -> acknowledged
in-app [Marcar como visto]  -> acknowledged
```

After local ACK:

- `acknowledgedAt` is persisted;
- item appears in viewed state;
- tray notification is canceled/updated;
- remote sync state is pending until confirmed.

## 5. Remote ACK Acceptance

- HTTP ACK is idempotent.
- ACK succeeds when Hermes is reachable.
- ACK remains pending when Tailscale/Mac is unreachable.
- WorkManager retries later.
- App/process restart does not lose pending ACK.
- Duplicate retry does not corrupt Hermes state.
- Permanent errors do not create aggressive infinite retry loops.
- Local UX never waits for remote ACK success.

## 6. E2EE Acceptance

Before real sensitive alerts:

- FCM-visible payload does not contain plaintext title/message.
- Authenticated encryption is used.
- Invalid/tampered ciphertext is rejected.
- Wrong-key payload is rejected safely.
- Nonce/IV handling follows standard primitive requirements.
- Key identifier supports future rotation.
- Android key material is protected using the approved Keystore-backed design.
- Mac key material is outside repo and restrictively permissioned.
- No secret appears in logs/prompts/command arguments/GitHub.

## 7. Product Quality Acceptance

The app is not accepted merely because all buttons work.

Reject product-critical UI if it feels like:

- generic CRUD;
- generated Compose sample;
- card soup;
- unnecessary gradients/glass;
- excessive rounded containers;
- enterprise dashboard;
- random icons/colors;
- verbose instructional copy.

Accept when:

- hierarchy is obvious;
- list scanning is fast;
- pending vs viewed is clear;
- `Visto` is reachable but not visually noisy;
- severity is restrained and consistent;
- detail screen is readable;
- spacing is deliberate;
- user reference screenshots/feedback are reflected where appropriate;
- manual QA feels natural on the physical Oppo.

## 8. Architecture Acceptance

- Single Android module unless explicitly changed later.
- Firebase types stay near transport boundary.
- Room/domain/UI do not depend on `RemoteMessage`.
- Hermes SQLite is server-side source of truth.
- Room is device-side source of truth.
- Tray state is not source of truth.
- No foreground socket workaround.
- No unnecessary Firebase products.
- No account system.
- No premature multi-device design.
- No unnecessary dependencies.
- No destructive migration without explicit approval.
- FCM can be replaced without rewriting the core inbox/ACK model.

## 9. AI / Workflow Acceptance

Every completed phase must have:

- one scoped phase branch from `dev`;
- scoped diff;
- validation commands passed;
- manual QA completed when applicable;
- physical-device QA for push/background/platform phases;
- independent review for medium/high-risk phases;
- no future-phase work;
- no unnecessary dependency;
- docs updated if architecture/behavior changed;
- no secret leakage to AI/cloud tools/GitHub;
- suggested commit message;
- user-owned commit/push;
- pushed branch reviewed through ChatGPT + GitHub;
- default merge to `dev` only after `PASS`.

## 10. Replacement Gate

ntfy is retired only after:

```text
transport reliability PASS
local inbox persistence PASS
explicit Visto semantics PASS
ACK retry PASS
E2EE PASS
Hermes sender integration PASS
reconciliation PASS
multi-day Oppo real-use PASS
product/UX acceptance PASS
final GitHub review PASS
```
