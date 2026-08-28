# Hermes Notifications — Agent Rules

## 1. Source of Truth

Implement only:

```text
docs/CURRENT_PHASE.md
```

Read `docs/CURRENT_PHASE.md` first.

Use additional documents only when required:

- `docs/IMPLEMENTATION_PLAN.md` — approved active-phase plan.
- `docs/PROJECT_SPEC.md` — product behavior and non-goals.
- `docs/ARCHITECTURE.md` — architecture boundaries, FCM/ACK/E2EE contracts.
- `docs/ACCEPTANCE_CRITERIA.md` — global reliability/product quality bar.
- `docs/AI_WORKFLOW.md` — model/tool routing, branch/review workflow, escalation.
- `docs/MVP_PHASES.md` — roadmap context only.
- `docs/POST_MVP_PHASES.md` — reference only; never implement unless promoted into `CURRENT_PHASE.md`.

Actual repository source files are authoritative for exact paths, package names, APIs, fields, and runtime behavior.

If docs conflict with code, stop and report the mismatch before editing.

## 2. Current Product Principle

Hermes Notifications is a small Android client around this pipeline:

```text
Hermes SQLite/outbox
        ↓
push transport (FCM)
        ↓
Android receive boundary
        ↓
Room inbox
        ↓
explicit local acknowledgment
        ↓
durable eventual remote ACK
```

Source-of-truth boundaries:

```text
Hermes SQLite = server notification/outbox truth
Room          = device inbox + local ACK truth
FCM           = transport only
tray state    = never source of truth
```

The app receives alerts already selected by Hermes. It must never reimplement Gmail, Calendar, Tasks, LLM filtering, Personal Admin reasoning, or Hermes orchestration.

## 3. Before Editing

Before making code changes, return:

1. Files to create or modify.
2. Why each file is required.
3. Files explicitly not to touch.
4. Implementation order.
5. Validation commands.
6. Manual QA required.
7. Risks, assumptions, and doc/code mismatches.

Wait for approval before editing unless the active workflow explicitly authorizes direct implementation.

Do not widen scope because adjacent work appears easy.

## 4. Phase and Branch Workflow

Every phase starts from a new branch off `dev`.

Do not implement directly on `dev`.

Default flow:

```text
dev
→ phase branch
→ implementation
→ validation
→ independent review
→ manual/device QA when required
→ user commit + push
→ ChatGPT GitHub review
→ PASS
→ merge to dev
```

If ChatGPT is already acting as planner, ChatGPT should provide the final `docs/CURRENT_PHASE.md` and `docs/IMPLEMENTATION_PLAN.md` directly. Do not ask the user to run another planning prompt through ChatGPT.

Prompts are useful for builder preflight, implementation, critic/review, debug, or specialist escalation.

## 5. Product Non-Negotiables

Hermes Notifications is:

```text
single-user
Android-only
Kotlin + Jetpack Compose
local-persistent
push-driven
explicit-acknowledgment
APK-installable
low-maintenance
```

Initial UI language may be Spanish. Do not add a localization framework unless an active phase requests it.

## 6. Hard Architecture Rules

Do not add unless the active phase explicitly approves it:

- login, accounts, profiles, or multi-user support;
- Flutter, React Native, or multiplatform code;
- microservices;
- Firestore, Firebase Auth, Realtime Database, Cloud Functions, or Firebase Analytics;
- a new SaaS backend;
- an app-owned persistent WebSocket or MQTT connection;
- a foreground service used merely to keep push alive;
- aggressive periodic polling;
- Hilt;
- Retrofit;
- multi-module Android architecture;
- unnecessary abstraction/plugin frameworks;
- paid runtime APIs;
- Play Store-specific infrastructure;
- billing/subscriptions;
- chat with Hermes;
- configuration of Hermes from Android;
- speculative multi-device architecture.

Use a single Android `app` module for MVP unless real evidence later justifies otherwise.

## 7. FCM Rules

For Hermes alerts:

- Use **FCM data messages**, not automatic notification-message handling.
- Use the current Firebase Installation ID (FID) registration flow, not deprecated token-first design.
- Keep Firebase-specific parsing at the transport boundary.
- Convert incoming Firebase data into an app-owned envelope before persistence/business logic.
- Do not expose Firebase `RemoteMessage` types to Room/domain/UI layers.
- FCM acceptance means transport accepted the message, not that Android received it.
- FCM is the realtime path, not the authoritative notification store.

The app must not require manually reopening after normal network transitions in order to restore push.

A reproducible case where mobile data works but opening the app is required for pending FCM delivery is a reliability **FAIL**, not an acceptable UX quirk.

## 8. Acknowledgment Rules

An alert becomes acknowledged only after an explicit user action:

```text
notification action: Visto
or
in-app/detail action: Marcar como visto
```

These must never acknowledge:

- FCM delivery;
- Android notification display;
- notification swipe/dismiss;
- app launch;
- alert detail open;
- notification tray disappearance.

The acknowledgment operation is:

```text
local ACK immediately
    +
remote ACK eventually
```

If remote ACK fails, local acknowledgment remains valid and sync state remains pending.

Never require successful network access before updating local acknowledgment state.

## 9. Persistence and Idempotency Rules

- `notificationId` is the business idempotency key and local primary key.
- Repeated delivery of the same `notificationId` must not create duplicate inbox rows.
- Duplicate push delivery must not create repeated user-visible alerts after the item is already known unless a recovery phase explicitly defines such behavior.
- Persist received alerts before relying on transient UI/tray state.
- Pending ACK state must survive process death/restart.
- Do not use destructive database migration in production without explicit approval and a data-loss decision.

## 10. Security and Privacy Rules

Never expose to AI prompts, logs, commits, screenshots, GitHub, or command output:

- Firebase service-account JSON;
- service-account private keys;
- AES/E2EE keys;
- auth headers/tokens;
- Tailscale credentials;
- Android signing keys;
- real sensitive Hermes notification contents;
- any secret or credential.

Treat device identifiers/FID as sensitive operational data and redact them in shared logs.

Before E2EE is complete, transport tests use fake/non-sensitive payloads only.

E2EE must use standard, reviewed primitives and platform libraries. Do not invent cryptography.

Secrets stay outside the repository.

## 11. Product Quality Rule

Functional does not mean accepted.

The UI must not feel like:

- an AI-generated CRUD demo;
- a generic enterprise dashboard;
- card-inside-card template output;
- gratuitous gradients or glassmorphism;
- oversized Material components with weak hierarchy;
- emoji-based primary iconography;
- random colors/icons;
- verbose instructional copy;
- a settings screen pretending to be the product.

Prefer:

- clear typography hierarchy;
- restrained spacing and color;
- deliberate severity treatment;
- chronological inbox behavior;
- obvious pending vs viewed state;
- useful empty states;
- compact reachable actions;
- readable detail screens;
- screenshot-driven review using user-provided references.

If UI is technically correct but generic/CRUD-like, do not close the phase. Escalate according to `docs/AI_WORKFLOW.md`.

## 12. Tool-Calling and Debugging Rules

Avoid long autonomous loops.

Do not read the entire repository unless required.

If validation fails:

1. Stop at the first real error.
2. Identify the root cause.
3. Make one evidence-based fix.
4. Re-run the smallest relevant validation.
5. Do not refactor unrelated code.
6. After two failed attempts on the same blocker, stop and escalate.
7. If there is no meaningful progress after roughly 8 tool calls, stop and summarize.

For Android/Gradle/Firebase/FCM/WorkManager/permission/Doze/runtime problems, use the Android platform route in `docs/AI_WORKFLOW.md`.

For security/persistence/concurrency risk, use the correctness/frontier route.

## 13. Build and Validation Rules

Run the validation commands from `docs/CURRENT_PHASE.md` or `docs/IMPLEMENTATION_PLAN.md`.

Default Android gate once the project exists:

```bash
./gradlew assembleDebug
```

Critical code/data phases should normally use:

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
```

UI/platform phases require physical-device/manual QA when specified.

Build/test evidence comes from the repository/device, not from AI self-report.

## 14. Review and Git Rules

The builder should not be the only reviewer for medium/high-risk work.

Final pushed-branch review is performed through ChatGPT + GitHub and ends with:

```text
PASS
PASS_WITH_NOTES
BLOCKED
```

Default merge policy:

```text
merge to dev after PASS
```

`PASS_WITH_NOTES` requires explicit user acceptance or follow-up before merge.

The user owns commits and pushes unless explicitly changed.

Before declaring a phase ready, report:

- changed files;
- validation results;
- manual QA status;
- independent review result;
- remaining risks;
- suggested commit message.
