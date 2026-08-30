# Current Phase

## Status

**PLANNED — READY FOR PREFLIGHT**

Phase: `2 — Persistent Inbox and Alert Detail`

Implementation branch: `2-persistent-inbox`

Base branch: `dev`

---

## Objective

Turn the proven Phase 1 FCM transport into the first real Ackline product:

FCM data message → validate app protocol → persist locally in Room → deduplicate by `notificationId` → post native Android notification only for a newly inserted alert → expose the durable alert through Inbox and Alert Detail.

Phase 2 establishes **Room as the device inbox source of truth**.

An alert must remain available after tray dismissal, app restart, process recreation, and device reboot.

Phase 2 does **not** implement explicit acknowledgment actions yet.

---

## Baseline Already Proven

Phase 0 established:

- Android project builds and installs on the Oppo.
- Ackline launches successfully.
- Notification permission works.
- Firebase configuration is valid.
- Current FID registration works.
- FID can be copied from the setup surface.
- Mac Firebase Admin credentials remain outside the repository.
- Mac can target the Oppo through FCM data-only messages.

Phase 1 established on the physical Oppo:

- native Android notifications work;
- foreground delivery works;
- background delivery works;
- removing Ackline from Recents does not stop normal delivery;
- screen-off delivery works;
- mobile-data delivery works;
- Wi-Fi → mobile works;
- mobile → Wi-Fi works;
- sending during a network transition recovers automatically;
- airplane/offline recovery works;
- natural idle works;
- IMPORTANT survives the intended forced-Doze test;
- multi-hour normal use did not require manually reopening Ackline to restore delivery.

The ntfy-style failure condition was not reproduced:

> usable network + alert stuck + opening Ackline restores delivery

Do not redesign the proven FCM transport without a concrete Phase 2 reason.

---

## Product Principle

Ackline now moves from transport spike to persistent personal inbox.

Priority order remains:

1. Reliability
2. Simplicity
3. Privacy
4. Low maintenance
5. UX quality
6. Additional features

The first real product UI must already feel intentional enough for daily personal use.

A technically correct CRUD list is not accepted.

---

## Phase 2 Question

> Can Ackline reliably turn each received alert into one durable local inbox item, present it clearly, and preserve it independently of Android notification-tray state?

The answer must be demonstrably yes before Phase 3 adds explicit acknowledgment.

---

## Source-of-Truth Rules

During Phase 2:

- **FCM** = transport only.
- **Room** = device inbox truth.
- **Android notification tray** = transient presentation only.
- **SetupState** = development/setup diagnostic state only.

A valid accepted alert must be persisted before relying on tray presentation.

---

## Protocol Normalization

Phase 1 used a temporary transport-test envelope with `sent_at`.

Phase 2 introduces the durable app contract before persistent data begins.

Protocol version: `1`

Required plaintext development fields:

- `protocol`
- `notification_id`
- `level`
- `title`
- `message`
- `created_at`

Example:

```json
{
  "protocol": "1",
  "notification_id": "phase2-test-001",
  "level": "important",
  "title": "Ackline test",
  "message": "Non-sensitive Phase 2 test",
  "created_at": "2026-08-29T21:00:00Z"
}
```

Phase 2 uses `created_at` rather than the Phase 1 transport-spike `sent_at` because `created_at` is the durable Ackline/Hermes product timestamp.

Only fake/non-sensitive payloads are allowed until the E2EE phase passes.

---

## Protocol Validation

`protocol`:
- required;
- exactly `"1"`.

`notification_id`:
- required;
- non-blank.

`level` must be exactly one of:
- `remember`
- `important`
- `urgent`

`title`:
- required;
- non-blank.

`message`:
- required;
- non-blank.

`created_at`:
- required;
- must parse as a UTC/RFC3339-compatible instant.

Prefer canonical sender output ending in `Z`.

Malformed payload behavior:

- reject;
- do not persist;
- do not post a native notification;
- do not crash;
- emit bounded non-sensitive diagnostics only.

---

## App-Owned Incoming Type

Firebase-specific data must be converted at the transport boundary into an app-owned type.

Expected concept: `IncomingAlertEnvelope`

Conceptual fields:

- `protocolVersion`
- `notificationId`
- `level`
- `title`
- `message`
- `createdAt`
- `receivedAt`

No Firebase SDK type may enter Room, repository, ViewModel, or Compose UI.

---

## Alert Level

Use one app-owned severity representation:

- `REMEMBER`
- `IMPORTANT`
- `URGENT`

Existing transport semantics remain:

| Level | FCM priority | Android importance |
|---|---|---|
| REMEMBER | NORMAL | LOW |
| IMPORTANT | HIGH | DEFAULT |
| URGENT | HIGH | HIGH |

---

## Room Persistence

Add Room as the device source of truth.

Initial database version: `1`

Requirements:

- no destructive migration fallback;
- export schema when cleanly supported by the chosen Room/KSP setup;
- one database instance.

Minimum Phase 2 entity: `AlertEntity`

Fields:

- `notificationId: String` — primary key
- `protocolVersion: Int`
- `level: String`
- `title: String`
- `message: String`
- `createdAtEpochMillis: Long`
- `receivedAtEpochMillis: Long`
- `acknowledgedAtEpochMillis: Long?`

Every normal Phase 2 incoming alert starts with `acknowledgedAtEpochMillis = null`.

`acknowledgedAt` exists because Phase 2 already exposes `Pendientes / Vistas`, but **Phase 2 must not add a production operation that changes it**. The explicit transition belongs to Phase 3.

Do not add yet:

- `ackSyncState`
- `ackSyncedAt`
- `lastAckError`
- remote ACK metadata

---

## Idempotent Receive Flow

`notificationId` is the business idempotency key.

Required flow:

FCM data arrives → validate protocol → create `IncomingAlertEnvelope` → insert into Room using primary-key/conflict-ignore semantics → if inserted, post native notification → if duplicate, do not insert again and do not repost.

Do not use query-before-insert deduplication.

The database uniqueness constraint is the race-safe authority.

---

## Persist Before Notify

For a valid incoming alert:

Room persist → native notification.

Reason: **Room = truth; tray = presentation.**

If notification permission is unavailable:

- Room insert still succeeds;
- alert remains visible in Inbox;
- tray notification may be skipped.

If Room persistence fails:

- do not pretend the alert is durably received;
- do not create a tray-only source of truth;
- log bounded diagnostics.

Do not add WorkManager to solve this in Phase 2.

---

## Duplicate Delivery Semantics

For the same `notification_id`, Ackline must produce:

- one Room row;
- one logical Inbox item;
- no repeated native notification after the item is already known.

Key physical QA:

1. Receive one alert.
2. Swipe its tray notification away.
3. Send the same `notification_id` again.
4. Inbox remains one row.
5. Tray notification does **not** reappear.

---

## Room Queries

At minimum:

- observe pending alerts;
- observe viewed alerts;
- observe/find alert by `notificationId`.

Ordering: `createdAt DESC`, then `receivedAt DESC` as deterministic secondary ordering.

Pending: `acknowledgedAt IS NULL`

Viewed: `acknowledgedAt IS NOT NULL`

Phase 2 production data will normally remain entirely pending until Phase 3 introduces explicit acknowledgment.

Tests and Compose previews may construct viewed fixtures. Do not add hidden runtime acknowledgment/debug actions merely to populate Vistas.

---

## Manual Dependency Wiring

Use the smallest maintainable dependency wiring.

Preferred concept: `AcklineApplication` owns one `AcklineDatabase` and one `AlertRepository`.

Both the FCM service and UI/ViewModel need access to the same repository.

Do not add Hilt, Koin, or a generic DI framework.

---

## Local Backup Policy

Phase 2 introduces persistent alert contents.

Set `android:allowBackup="false"` for the MVP unless a later explicit privacy/product decision reverses it.

Do not build backup/restore infrastructure in Phase 2.

Hermes/reconciliation is the future logical recovery path.

---

## Inbox Screen

Required hierarchy:

- small source/eyebrow label, e.g. `PERSONAL ADMIN`;
- `Inbox`;
- pending count;
- compact `Pendientes | Vistas` filter;
- chronological alert list.

Each row should expose:

- severity;
- title;
- short message summary;
- concise timestamp;
- pending/viewed context.

Prefer flat list hierarchy, restrained dividers, intentional spacing, small severity treatment, and clear typography.

Avoid card soup, gradients, glassmorphism, oversized Material components, emoji icons, random colors, dashboard widgets, and verbose helper copy.

---

## Pendientes / Vistas

Provide a compact two-state filter:

- `Pendientes`
- `Vistas`

Phase 2 behavior:

- new alerts appear in Pendientes;
- no normal user action moves an item to Vistas.

Therefore Vistas may naturally show its empty state throughout Phase 2 production use. Phase 3 introduces the explicit state transition.

---

## Alert Detail

Tapping an Inbox row opens Alert Detail.

Detail must show:

- severity;
- full title;
- full message;
- created timestamp;
- received timestamp when useful;
- current pending/viewed status.

Current Phase 2 status will normally be `Pendiente`.

Do not add yet:

- `Marcar como visto`;
- `Visto` action;
- ACK sync state;
- remote diagnostics.

Opening detail must not modify persistence state.

---

## Existing Setup Surface

Preserve:

- notification permission;
- FCM registration;
- FID display/copy;
- last test message;
- current dark-mode readability.

Setup must remain reachable from the app through a small intentional entry.

Do not make Setup the primary screen.

---

## Navigation

Phase 2 needs only:

- Inbox;
- Alert Detail;
- Setup.

Do not add a navigation framework by default.

A small root `AcklineApp` screen-state implementation is acceptable.

If preflight determines Navigation Compose materially reduces complexity, it must justify the dependency before implementation.

---

## Android Notification Tap

A neutral content intent that opens Ackline is allowed.

Requirements:

- tap may open the app;
- tap must **not** acknowledge;
- tap must not mutate Room state.

Direct notification-to-detail routing is optional and not required in Phase 2.

---

## FCM Sender

Continue using exactly one sender: `tools/firebase_sender.py`.

Update the fake Phase 2 contract to send:

- `protocol`
- `notification_id`
- `level`
- `title`
- `message`
- `created_at`

Preserve FID targeting, level → FCM priority, external credentials, fake/non-sensitive content, and no FID printing.

---

## Dependencies Allowed

Phase 2 may add only dependencies concretely needed for:

- Room;
- Room compiler/KSP;
- Lifecycle/ViewModel;
- lifecycle-aware Compose state collection.

Navigation Compose is not approved by default; preflight must justify it.

Do not add Hilt, Retrofit, WorkManager, kotlinx serialization merely for `Map<String, String>`, analytics, Firebase database products, or generic architecture frameworks.

---

## Existing FCM Reliability Must Not Regress

After Room integration, physical QA must confirm:

- FCM still arrives in background;
- new alert persists;
- native notification still appears;
- removing Ackline from Recents still does not require manual reopen.

A focused Phase 1 regression smoke is sufficient unless evidence indicates a broader regression.

---

## Out of Scope

Do **not** implement:

- explicit `Visto`;
- `Marcar como visto`;
- notification Visto action;
- `acknowledge(notificationId)`;
- remote ACK;
- `ackSyncState`;
- WorkManager;
- Tailscale HTTP;
- Hermes ACK endpoint;
- E2EE;
- AES-GCM;
- Android Keystore;
- real sensitive Hermes payloads;
- Hermes production outbox integration;
- reconciliation;
- `onDeletedMessages()` recovery workflow;
- search;
- complex settings;
- analytics;
- authentication;
- accounts;
- multi-user;
- multi-device;
- Play Store work;
- billing;
- foreground service;
- persistent socket;
- MQTT;
- ntfy integration;
- ColorOS workaround;
- Hilt;
- Retrofit;
- multi-module architecture;
- generic use-case/interactor layers.

Do not pre-create future packages merely for later phases.

---

## Product Quality Gate

Phase 2 cannot close on functional QA alone.

Required:

- physical Oppo screenshot review;
- light-mode review;
- dark-mode review;
- Inbox hierarchy review;
- Detail readability review;
- empty-state review;
- Pendientes/Vistas treatment review.

If the UI feels like generated CRUD, the phase is not complete.

---

## Automated Testing

Meaningful deterministic coverage should include protocol parsing:

- valid v1 envelope accepted;
- wrong protocol rejected;
- missing required field rejected;
- blank required field rejected;
- invalid level rejected;
- invalid `created_at` rejected.

Prefer a small Room instrumented test where practical:

- first `notificationId` insert succeeds;
- duplicate insert is ignored;
- one row remains;
- pending query returns new row;
- viewed query distinguishes an acknowledged fixture;
- ordering is deterministic.

Do not add Robolectric or large test infrastructure solely for this phase.

---

## Automated Validation

At minimum:

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
python -m py_compile tools/firebase_sender.py
git diff --check
git status --short --untracked-files=all
```

If Room instrumented tests are added:

```bash
./gradlew connectedDebugAndroidTest
```

---

## Manual QA

Required physical Oppo cases:

1. Launch Inbox.
2. Send one unique fake alert.
3. Native notification appears.
4. Inbox receives exactly one persistent row.
5. Send several unique alerts and verify chronological order.
6. Open an alert and verify full detail.
7. Opening detail leaves it pending.
8. Swipe tray notification away.
9. Inbox row remains.
10. Re-send the same `notification_id`.
11. Inbox still contains one row.
12. Dismissed duplicate does not repost the native notification.
13. Relaunch app; rows remain.
14. Kill/restart process; rows remain.
15. Reboot Oppo; rows remain.
16. Pendientes contains new alerts.
17. Vistas empty state is intentional.
18. Setup/FID surface remains reachable.
19. Background FCM regression smoke still passes.
20. Removed-from-Recents regression smoke still passes.
21. Review light-mode screenshots.
22. Review dark-mode screenshots.

No real personal alert contents.

---

## AI Route

Planning/architecture: `ChatGPT + GitHub`

Preflight: `/local-quality` preferred, or `/local-build` if repository discovery remains straightforward.

Primary implementation: `/local-quality` — Qwen3.8 27B 5bit + DFlash2.

Android/Room/KSP runtime escalation: `Gemini Android Studio`.

Independent review: `/local-review` — Qwen3.8 27B AWQ 5bpw + Lightning MTP.

If UI remains generic/CRUD-like after one focused pass, use the premium UI route defined in `docs/AI_WORKFLOW.md`.

Final pushed-branch review: `ChatGPT + GitHub`.

---

## Workflow

create `2-persistent-inbox` from `dev` → replace Phase 2 planning docs → commit planning docs → preflight → ChatGPT/user approval → implementation → automated validation → Room/idempotency QA → physical-device QA → screenshot/product review → independent `/local-review` → resolve blockers → user commit/push → ChatGPT GitHub final review → PASS → merge to `dev`.

The user owns commits and pushes.

---

## Completion Criteria

All must be true:

- Room is local inbox truth.
- Database version 1 exists.
- No destructive migration fallback.
- Incoming FCM is converted to an app-owned envelope.
- Protocol v1 is validated.
- `created_at` is parsed.
- Alert persists before notification posting.
- `notificationId` is the Room primary key.
- Duplicate delivery creates one row.
- Duplicate delivery does not repost a known alert.
- Tray dismissal does not remove the Room alert.
- App restart preserves alerts.
- Process restart preserves alerts.
- Device reboot preserves alerts.
- Inbox is chronological.
- Pendientes / Vistas UI exists.
- Alert Detail works.
- Opening app does not acknowledge.
- Opening detail does not acknowledge.
- No explicit ACK action exists yet.
- Setup remains reachable.
- Background FCM regression smoke passes.
- Fake/non-sensitive payloads only.
- Backup policy is explicit.
- No future-phase architecture.
- Automated validation passes.
- Independent review passes.
- Physical QA passes.
- UI screenshot review passes.
- Final pushed-branch GitHub review = PASS.

---

## Suggested Commits

Planning: `docs: plan Phase 2 persistent inbox`

Implementation: `feat: add persistent alert inbox`

Do not commit automatically.

The user owns commits and pushes.
