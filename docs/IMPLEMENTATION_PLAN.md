# Phase 4 — Durable Remote ACK Sync Implementation Plan

## 1. Status

**APPROVED — IMPLEMENTATION IN PROGRESS**

Branch:

`4-durable-remote-ack`

Base:

`dev`

The Phase 4 preflight has confirmed:

- current Phase 3 code on `dev`;
- current Room v2 schema;
- current Gradle dependency graph;
- the actual Hermes notification-state implementation / SQLite schema;
- the actual Tailscale/HTTPS route available on the Mac.

The existing Hermes ACK server is reused; no second endpoint is planned.

---

## 2. Goal

Add durable eventual remote synchronization of explicit local acknowledgments.

The Phase 3 local path is already correct and must remain authoritative:

```text
explicit Visto
→ Room acknowledgedAt persisted
→ ackSyncState=PENDING
→ UI becomes Vista immediately
```

Phase 4 adds:

```text
PENDING
→ WorkManager unique drain
→ private HTTPS ACK endpoint
→ Hermes idempotent ACK
→ SYNCED
```

Remote failure never reverses local acknowledgment.

---

## 3. Non-Negotiable Invariants

1. `Vista` never waits for remote success.
2. `acknowledgedAt` is written only by the explicit local ACK transition.
3. Remote sync never changes the original local ACK timestamp.
4. WorkManager is a trigger/executor; Room holds durable sync truth.
5. One worker drains multiple pending ACKs.
6. No one-worker-per-alert permanent architecture.
7. No periodic polling.
8. Server ACK is idempotent.
9. Unknown remote notification IDs never create phantom rows.
10. Remote transient failure remains retryable.
11. Permanent errors do not spin forever.
12. FCM inbound delivery stays independent from Tailscale.
13. Fake/non-sensitive payloads only until Phase 5.
14. No secrets/private payloads in logs.
15. No destructive Room migration.

---

## 4. Required Repository Discovery

Before edits, inspect exact current files including:

```text
app/build.gradle.kts
gradle/libs.versions.toml
app/src/main/AndroidManifest.xml
app/src/main/java/com/edu/ackline/AcklineApplication.kt
app/src/main/java/com/edu/ackline/ack/LocalAcknowledgmentManager.kt
app/src/main/java/com/edu/ackline/ack/AcknowledgeReceiver.kt
app/src/main/java/com/edu/ackline/data/AlertRepository.kt
app/src/main/java/com/edu/ackline/data/local/AcklineDatabase.kt
app/src/main/java/com/edu/ackline/data/local/AlertDao.kt
app/src/main/java/com/edu/ackline/data/local/AlertEntity.kt
app/src/main/java/com/edu/ackline/model/AckSyncState.kt
app/src/main/java/com/edu/ackline/model/Alert.kt
app/src/androidTest/java/com/edu/ackline/data/local/AcklineMigrationTest.kt
app/src/androidTest/java/com/edu/ackline/data/local/AlertDaoTest.kt
app/schemas/com.edu.ackline.data.local.AcklineDatabase/1.json
app/schemas/com.edu.ackline.data.local.AcklineDatabase/2.json
docs/CURRENT_PHASE.md
docs/IMPLEMENTATION_PLAN.md
docs/ARCHITECTURE.md
docs/PROJECT_SPEC.md
docs/ACCEPTANCE_CRITERIA.md
docs/MVP_PHASES.md
```

Also inspect the actual Hermes local source responsible for notification state, especially any:

```text
notification_state.py
ack_server.py
SQLite notification/outbox schema
existing acknowledge/update function
```

Do not infer those paths or schemas.

If the Hermes source/state layer cannot be inspected, the server-side portion of preflight is BLOCKED.

---

## 5. Branch Setup

Before running the preflight, create the scoped Phase 4 branch from current `dev`:

```bash
git checkout dev
git pull --ff-only origin dev
git checkout -b 4-durable-remote-ack
```

Place the approved Phase 4 docs on this branch as uncommitted working-tree changes, then run preflight.

Do not commit directly to `dev`.
Do not commit the Phase 4 implementation before review.

---

## 6. Room Schema v3

Phase 3 schema v2 already contains:

```text
notificationId
protocolVersion
level
title
message
createdAtEpochMillis
receivedAtEpochMillis
acknowledgedAtEpochMillis
ackSyncState
```

Add:

```text
ackSyncedAtEpochMillis: Long?
lastAckError: String?
ackToken: String?
```

Database version:

`2 → 3`

Migration:

```sql
ALTER TABLE alerts ADD COLUMN ackSyncedAtEpochMillis INTEGER;
ALTER TABLE alerts ADD COLUMN lastAckError TEXT;
ALTER TABLE alerts ADD COLUMN ackToken TEXT;
```

Do not touch or rewrite existing v1/v2 schema exports.

Generate v3 export.

---

## 7. AckSyncState Extension

Current:

```kotlin
NONE
PENDING
```

Phase 4:

```kotlin
NONE
PENDING
SYNCED
ERROR
```

Storage:

```text
none
pending
synced
error
```

Do not encode retry count or HTTP status directly into the enum.

---

## 8. Remote-Sync Projection

The worker does not need alert title/message.

Prefer a narrow app-owned projection such as:

```text
PendingAcknowledgment
- notificationId
- acknowledgedAt
- ackToken
```

The DAO should select only fields needed for ACK sync where practical.

This reduces accidental coupling and prevents the worker from handling private alert content unnecessarily.

Do not create a generic sync DTO framework.

---

## 9. DAO Operations

Expected operations conceptually:

### Read pending ACKs

```text
findPendingAcknowledgments()
```

Criteria:

```text
acknowledgedAtEpochMillis IS NOT NULL
AND ackSyncState = 'pending'
```

### Mark synced

Atomic update must:

```text
ackSyncState = 'synced'
ackSyncedAtEpochMillis = now
lastAckError = NULL
```

for the matching acknowledged row.

### Mark permanent error

Atomic update must:

```text
ackSyncState = 'error'
lastAckError = sanitizedCategory
```

Do not change `acknowledgedAtEpochMillis`.

### Optional requeue operation

Do not add an ERROR → PENDING operation unless preflight identifies a concrete retry/recovery requirement and documents who calls it.

---

## 10. Repository Operations

Add only ACK-sync-specific operations required by the worker/runner.

Likely concepts:

```text
findPendingAcknowledgments()
markAckSynced(notificationId, syncedAt)
markAckError(notificationId, errorCategory)
```

Repository remains:

- Room/domain mapping;
- database operations.

It must not:

- create HTTP connections;
- know Tailscale;
- instantiate WorkManager;
- log response bodies.

---

## 11. ACK Remote Client Boundary

Create one narrow interface, for example:

```kotlin
interface AckRemoteClient {
    fun acknowledge(
        notificationId: String,
        ackToken: String,
    ): AckRemoteResult
}
```

Possible app-owned result categories:

```text
SUCCESS
TRANSIENT_FAILURE
PERMANENT_FAILURE(category)
```

Do not leak `HttpURLConnection`, OkHttp response types, or HTTP library types above this boundary.

---

## 12. HTTP Implementation

Preflight chooses the smallest correct implementation.

Preferred starting candidate:

`HttpsURLConnection`

because Phase 4 has one small endpoint.

If preflight demonstrates that a focused HTTP dependency materially reduces correctness risk, it may recommend it, but do not add Retrofit.

Required:

- HTTPS URL;
- `POST <ACK_BASE_URL>/ack/<encoded-notification-id>`;
- `X-Ack-Token` header;
- no request body;
- connect timeout;
- read timeout;
- automatic redirects disabled;
- strict status handling;
- stream cleanup;
- disconnect/close;
- no response-body logging.

Do not set or spoof `Tailscale-User-Login`; Tailscale Serve injects it.

No title/message.

---

## 13. HTTP Classification

### Success

The existing Hermes contract returns:

`200 OK`

Repeated ACK returns the same success class.

### Retryable

At minimum:

```text
network IO exception
DNS/connect timeout
TLS connection failure
408
429
500–599
```

Do not mark `SYNCED`.

### Permanent

At minimum:

```text
400
403
404
3xx
other 4xx as `client_error`
```

Persist only a sanitized category.

Do not retry aggressively.

There is no special `409` success/conflict model in the current endpoint.

---

## 14. ACK Sync Runner

Keep WorkManager class thin.

A narrow `AckSyncRunner` (or equally specific name) may own:

1. load Room `PENDING` ACKs;
2. invoke `AckRemoteClient`;
3. update Room result;
4. report whether retryable rows remain.

This makes the core behavior deterministic to test with a fake remote client.

This is not a general sync framework.

Do not generalize it for future reconciliation.

---

## 15. WorkManager Dependency

Approved Phase 4 dependency:

```text
androidx.work:work-runtime:2.11.2
```

Use the normal runtime artifact; no test-only WorkManager artifact is needed
for the current deterministic coverage.

Possible test-only:

```text
androidx.work:work-testing:2.11.2
```

Do not add RxWorker or multiprocess artifacts.

---

## 16. AckSyncWorker

Expected:

```text
ack/AckSyncWorker.kt
```

or another clearly ACK-owned package.

Use a standard `Worker` if the ACK runner is synchronous/blocking.

A synchronous `Worker` is acceptable because WorkManager executes `doWork()` on a background thread.

Do not create coroutine complexity unless the actual client implementation needs it.

Worker must:

- resolve app dependencies;
- invoke the runner;
- return `success` / `retry` / controlled `failure` according to the documented classification;
- never swallow a retryable failure as success.

Keep class name stable after shipping because WorkManager persists Worker class names.

---

## 17. Unique Scheduler

Create a tiny `AckSyncScheduler`.

Responsibilities only:

- create one `OneTimeWorkRequest`;
- apply network constraint;
- apply exponential backoff;
- enqueue unique work.

Conceptual unique name:

```text
ackline-ack-sync
```

Required policy:

`ExistingWorkPolicy.APPEND_OR_REPLACE`

`KEEP` can strand a new PENDING ACK when a running worker has already read
its backlog. `APPEND_OR_REPLACE` leaves a successor in the unique chain while
the shared Room backlog remains the durable queue.

The approved scheduling correction is based on current WorkManager semantics.

---

## 18. Constraints

Use:

```text
NetworkType.CONNECTED
```

Do not require unmetered Wi-Fi.

ACK payload is tiny and must work on mobile data.

Do not attempt to add a special Tailscale network constraint.

---

## 19. Backoff

Use exponential backoff.

Recommended initial interval:

`30 seconds`

Reason:

- avoids tight retry;
- still recovers reasonably quickly;
- WorkManager remains responsible for later scheduling.

Do not create your own retry loop with `sleep()`.

---

## 20. Trigger After Local ACK

Extend `LocalAcknowledgmentManager` carefully.

Current responsibilities:

- atomic local ACK;
- idempotency;
- tray cancellation.

Phase 4 addition:

- after a newly persisted local ACK, request ACK sync scheduling.

Ordering:

```text
Room ACK succeeds
→ local Vista is durable
→ tray cancel
→ enqueue unique remote sync
```

If scheduler invocation itself throws:

- local Vista remains valid;
- log sanitized failure;
- startup recovery must later enqueue drain.

Do not rollback Room acknowledgment.

---

## 21. Startup Recovery

A Room write and WorkManager enqueue cannot be one atomic transaction.

Therefore on normal app process startup:

```text
enqueue unique ACK drain
```

It is acceptable for the worker to start, find zero pending rows, and exit.

This is cheap and closes the scheduling crash window.

Do not query Room on the main thread merely to decide whether to enqueue.

Do not add periodic work.

---

## 22. Notification Receiver

`AcknowledgeReceiver` remains local-first.

Do not perform HTTP inside the BroadcastReceiver.

Required flow remains:

```text
goAsync
→ background local acknowledgment
→ shared LocalAcknowledgmentManager
→ scheduler request
→ finish
```

The network work belongs to WorkManager later.

---

## 23. Tailscale / HTTPS Deployment

Preferred:

```text
Ackline HTTPS request
→ private Tailscale hostname
→ Tailscale Serve
→ loopback ACK server
```

ACK server should normally bind:

```text
127.0.0.1
```

Do not expose `0.0.0.0` unless the preflight documents why.

Do not use Tailscale Funnel.

Do not create a public DNS/backend.

The preflight must capture:

- exact private hostname strategy;
- local server bind port;
- how HTTPS is terminated;
- whether MagicDNS/Tailscale Serve is already enabled;
- how the Android build receives the base URL.

---

## 24. Android INTERNET Permission

Because Phase 4 introduces app-owned outbound HTTP, preflight must inspect the merged manifest.

If needed, explicitly add:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Do not assume transitive library manifests are the desired source of this app-owned capability.

No cleartext traffic exception should be added if HTTPS/Tailscale Serve is used.

---

## 25. Endpoint Configuration

Preferred:

`local.properties`

Example conceptual key:

```text
ackline.ackBaseUrl=https://...
```

Gradle may expose a generated BuildConfig value to the debug app.

Rules:

- no user-specific private URL committed if avoidable;
- no credentials committed;
- no token logged;
- do not add a configuration backend.

Preflight must inspect whether BuildConfig generation is currently enabled and choose the least invasive mechanism.

---

## 26. Authentication Decision

The existing Hermes endpoint authenticates an ACK with the per-notification
`ack_token`, sent as `X-Ack-Token`. The token is carried by the development
FCM payload, stored in Room only for the remote ACK path, and never mapped to
the UI/domain `Alert` model.

The route remains private to Tailscale. `Tailscale-User-Login` is injected by
Tailscale Serve; Android must not set or spoof that header. No second bearer
scheme is introduced and no credential is tracked in source.

---

## 27. Mac ACK Endpoint

The endpoint must be deliberately small.

Responsibilities:

1. accept only the ACK route/method;
2. require `X-Ack-Token`;
3. validate the notification ID and token;
4. call the existing Hermes notification-state abstraction;
5. map result to HTTP status;
6. return no private content.

The endpoint already exists and is not rewritten merely to match the
superseded JSON proposal.

Do not add Flask/FastAPI unless existing Hermes already uses one and reuse is clearly simpler.

Python standard library is acceptable for this one route if it remains maintainable.

---

## 28. Server Request Body

The current Hermes ACK endpoint accepts no request body. Ackline therefore
sends no body and does not add upload handling or a second server endpoint.

Any server-side request limits remain owned by the existing Hermes service.

---

## 29. Server ACK Contract

The Android request is:

```text
POST /ack/<notification_id>
X-Ack-Token: <per-notification token>
```

It has no JSON body. The existing Hermes server owns ACK timestamp semantics
and idempotent state updates. Tailscale Serve injects `Tailscale-User-Login`;
Android must not set it.

---

## 30. Hermes Notification-State Integration

The approved preflight confirmed this integration path.

Preferred:

```text
ack_server
→ existing notification_state abstraction
→ existing SQLite transaction
```

Avoid:

```text
ack_server
→ raw second SQL implementation duplicating notification_state behavior
```

Reuse the existing idempotent acknowledgment function; do not duplicate raw
SQL in Ackline.

Do not modify Personal Admin source-monitoring/LLM/business logic.

Phase 4 changes only notification acknowledgment state.

Phase 5 must protect the eventual production inner payload, including ACK
credential material, with E2EE. Phase 6 still owns production Hermes outbox
and FCM sender integration.

---

## 31. Cross-Repo Rule

If Hermes notification state is in a different repository:

- document the exact repo/path;
- keep Android and Hermes diffs separately reviewable;
- do not copy Hermes code into Ackline merely to avoid a second repo;
- user still owns commits/pushes for each repo.

Preflight must list files by repository.

---

## 32. Server Lifecycle

Do not solve Mac daemonization before the ACK semantics work.

For Phase 4 functional QA, a manually started bounded server is acceptable.

A permanent launchd/service setup is only included if the current Hermes runtime already has an obvious low-maintenance service pattern and the preflight explicitly recommends it.

Do not turn Phase 4 into service-management work.

---

## 33. Server Logging

Allowed:

```text
ACK request accepted
ACK already acknowledged
ACK unknown id
ACK database failure
```

Avoid logging:

- full notification ID if not needed;
- title/message;
- credentials;
- full request body;
- SQLite contents;
- private URL/token.

Use bounded diagnostic identifiers only if required.

---

## 34. UI

No Inbox redesign.

No new card/status dashboard.

Default UI remains:

```text
Pendiente
Vista
```

Do not expose `PENDING`, `SYNCED`, `ERROR` in normal Inbox.

If permanent `ERROR` needs an affordance, preflight must propose the exact minimal product behavior before coding it.

---

## 35. Migration Test v2 → v3

Mandatory.

Create a v2 database containing at least:

1. one `NONE` pending alert;
2. one locally acknowledged `PENDING` alert.

Migrate to v3.

Verify:

- both rows survive;
- `acknowledgedAt` values survive;
- `ackSyncState` values survive;
- new columns are NULL;
- schema validates.

Do not remove the v1 → v2 migration test.

---

## 36. DAO Tests

Add deterministic tests for:

```text
find pending acknowledgments
mark synced
clear error on success
mark permanent error
preserve acknowledgedAt
do not treat NONE as syncable
do not change local viewed/pending classification
```

Do not add timing-dependent sleeps.

---

## 37. Sync Runner Tests

Fake remote client matrix:

```text
SUCCESS
→ local SYNCED

TRANSIENT_FAILURE
→ local remains PENDING
→ runner reports retry needed

PERMANENT_FAILURE
→ local ERROR
→ runner does not request tight retry for that row

mixed rows
→ one permanent/transient result does not corrupt unrelated row
```

Verify original `acknowledgedAt` remains unchanged.

---

## 38. WorkManager Tests

If using `work-testing`, verify the important configuration rather than Android internals.

At minimum prove through tests or clear code inspection:

- unique work name;
- `ExistingWorkPolicy.APPEND_OR_REPLACE`;
- one-time request;
- `NetworkType.CONNECTED`;
- exponential backoff;
- worker drains Room, not input `Data` containing one ACK.

Do not overbuild test-only DI.

---

## 39. Python Tests

Hermes-owned tests cover the existing endpoint contract. This Ackline
repository owns the development sender test only:

```bash
python3 -m py_compile tools/firebase_sender.py
python3 -m unittest tools/test_firebase_sender.py
```

The sender tests verify that `ACKLINE_ACK_TOKEN` is optional, omitted when
absent, and never printed or logged when present. No real personal data is
used.

The Android development sender is covered separately: `ACKLINE_ACK_TOKEN` is
optional, omitted when absent, and never printed or logged when present.

---

## 40. Validation Commands — Android

Expected minimum:

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
./gradlew connectedDebugAndroidTest
git diff --check
```

Do not use the physical migration dataset for `connectedDebugAndroidTest`; instrumentation can alter/install test APKs.

Physical v2 → v3 upgrade QA must be done separately.

---

## 41. Validation Commands — Python

Hermes-side tests run in the Hermes repository. Ackline's local Python gate is:

```bash
python3 -m py_compile tools/firebase_sender.py tools/test_firebase_sender.py
python3 -m unittest tools/test_firebase_sender.py
```

---

## 42. Physical Migration QA

Do not uninstall or clear the real Phase 3 app before upgrade test.

Required sequence:

```text
Phase 3 app / Room v2 with:
- at least one pending alert
- at least one Vista with ackSyncState=PENDING

install Phase 4 APK over it

open app

verify rows preserved
verify Vista/Pendiente state preserved
```

Run instrumented tests on a separate recreated dataset/order if necessary.

---

## 43. Physical Functional QA — Online

Start private Mac ACK endpoint.

Ensure Tailscale path works.

Send fake alert.

Tap explicit `Visto`.

Expected:

```text
Vista immediately
tray removed
server receives ACK
Hermes state acknowledged
local state eventually SYNCED
```

No second tap required.

---

## 44. Physical Functional QA — Offline ACK Path

Make only the ACK return path unavailable.

Do not break FCM unless intentionally testing both.

Example:

- stop ACK server; or
- disable Tailscale on phone/Mac.

Send/receive fake alert through FCM.

Tap `Visto`.

Expected:

```text
Vista immediately
no crash
no waiting UI
ACK remains durable locally
worker becomes retryable/backed off
```

---

## 45. Recovery QA

Restore ACK endpoint/Tailscale.

Do not tap `Visto` again.

Expected:

```text
existing PENDING ACK
→ WorkManager retry
→ Hermes acknowledged
→ local SYNCED
```

If backoff makes the test inconvenient, use WorkManager test/debug mechanisms rather than adding production polling.

---

## 46. Process-Restart Recovery QA

Create a `PENDING` remote ACK while server is unreachable.

Force-stop/process-kill only for the persistence test; understand Force Stop affects scheduled work behavior.

Reopen app.

Expected:

- local Vista preserved;
- startup recovery enqueues unique drain;
- after endpoint availability, ACK syncs.

Do not interpret Android manual Force Stop as normal background behavior for FCM reliability.

---

## 47. Duplicate Remote Request QA

Cause the same ACK request to reach server twice.

Expected:

```text
first → acknowledged
second → success/idempotent
server one logical ACK
local one Vista
original local acknowledgedAt unchanged
```

---

## 48. Permanent Error QA

Use a controlled development response, not real corruption.

Example:

- unknown notification ID, or
- test endpoint returns `400`.

Expected:

- local Vista remains;
- local sync becomes ERROR or documented terminal classification;
- no rapid infinite retry;
- unrelated pending ACKs can still progress.

---

## 49. Regression Scope

Do not repeat the entire Phase 1 transport matrix.

Focused regression only:

- new FCM alert still persists before notification;
- local Visto from at least one surface still immediate;
- duplicate FCM still ignored;
- Inbox/Detail opens normally.

Phase 4 QA should focus on remote ACK durability.

---

## 50. Files Likely to Modify — Ackline

Likely:

```text
gradle/libs.versions.toml
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/edu/ackline/AcklineApplication.kt
app/src/main/java/com/edu/ackline/ack/LocalAcknowledgmentManager.kt
app/src/main/java/com/edu/ackline/data/AlertRepository.kt
app/src/main/java/com/edu/ackline/data/local/AcklineDatabase.kt
app/src/main/java/com/edu/ackline/data/local/AlertDao.kt
app/src/main/java/com/edu/ackline/data/local/AlertEntity.kt
app/src/main/java/com/edu/ackline/model/AckSyncState.kt
app/src/main/java/com/edu/ackline/model/Alert.kt
app/src/main/java/com/edu/ackline/push/IncomingAlertEnvelope.kt
app/src/androidTest/java/com/edu/ackline/data/local/AcklineMigrationTest.kt
app/src/androidTest/java/com/edu/ackline/data/local/AlertDaoTest.kt
app/src/test/java/com/edu/ackline/ack/AckSyncRunnerTest.kt
app/src/test/java/com/edu/ackline/ack/HttpsAckRemoteClientTest.kt
app/src/test/java/com/edu/ackline/push/PayloadValidationTest.kt
tools/firebase_sender.py
tools/test_firebase_sender.py
docs/CURRENT_PHASE.md
docs/IMPLEMENTATION_PLAN.md
docs/ARCHITECTURE.md
```

Exact list comes from preflight.

---

## 51. Files Likely to Create — Ackline

Likely concepts:

```text
app/src/main/java/com/edu/ackline/ack/PendingAcknowledgment.kt
app/src/main/java/com/edu/ackline/ack/AckRemoteClient.kt
app/src/main/java/com/edu/ackline/ack/HttpsAckRemoteClient.kt
app/src/main/java/com/edu/ackline/ack/AckSyncRunner.kt
app/src/main/java/com/edu/ackline/ack/AckSyncScheduler.kt
app/src/main/java/com/edu/ackline/ack/AckSyncWorker.kt
app/schemas/com.edu.ackline.data.local.AcklineDatabase/3.json
```

Names may change if existing project conventions make a smaller structure clearer.

Do not create generic `network/`, `domain/usecase/`, or sync-framework layers without need.

---

## 52. Hermes-Side Files

The approved preflight confirmed the existing notification-state module and
ACK endpoint. This Ackline implementation does not modify Hermes-side files.

Hermes endpoint and state-layer tests remain owned by the Hermes repository.

Do not pre-authorize changes to unrelated Hermes files.

---

## 53. Files Explicitly Not to Touch Without New Evidence

Avoid changes to:

```text
MainActivity.kt UI behavior
InboxScreen visual design
AlertDetailScreen visual design
FCM protocol parser
firebase_sender.py except for the optional `ACKLINE_ACK_TOKEN` test-harness field
notification channel IDs
Firebase registration/FID flow
Phase 1 transport semantics
Hermes Gmail/Calendar/Tasks monitoring
Hermes Cheap Gate/LLM selection/business logic
```

No E2EE changes yet.

---

## 54. Dependency Policy

Expected new:

```text
androidx.work:work-runtime:2.11.2
```

Potential test-only:

```text
androidx.work:work-testing:2.11.2
```

Any HTTP runtime dependency requires explicit justification in preflight.

No:

```text
Retrofit
Hilt
Koin
Navigation Compose
Firebase Auth
Firestore
Cloud Functions
```

---

## 55. Implementation Order

Implementation sequence:

1. create Phase 4 branch;
2. update approved docs if not already staged;
3. add Room v3 schema + v2→v3 migration;
4. add DAO/repository sync operations;
5. extend `AckSyncState`;
6. implement narrow remote ACK result/client;
7. verify the private HTTPS/Tailscale path manually when physical QA is available;
8. implement `AckSyncRunner`;
9. add WorkManager dependency;
10. implement `AckSyncWorker`;
11. implement unique scheduler;
12. wire post-local-ACK schedule;
13. wire startup recovery enqueue;
14. add/finish automated tests;
15. run full automated validation;
16. run physical v2→v3 migration QA;
17. run focused Phase 4 functional QA;
18. local independent review;
19. user commit/push;
20. ChatGPT GitHub review;
21. merge only after PASS.

---

## 56. Stop Conditions

Stop and report instead of guessing if:

- Hermes authoritative SQLite schema is unclear;
- multiple competing notification databases exist;
- Tailscale route requires public exposure;
- HTTPS cannot be established without a security downgrade;
- dependency resolution conflicts occur;
- WorkManager semantics require periodic polling to function;
- server ACK cannot be made idempotent with current Hermes state;
- implementation would require moving Personal Admin logic into Ackline;
- real sensitive payloads would be required before Phase 5.

---

## 57. Definition of Done

Phase 4 is done when:

```text
Room v2→v3 non-destructive migration       PASS
local ACK remains immediate                 PASS
unique durable ACK drain                    PASS
online remote ACK                           PASS
server idempotency                          PASS
Tailscale/Mac unavailable preserves Vista   PASS
pending ACK survives restart                PASS
restore eventually syncs                    PASS
permanent error no tight retry               PASS
no private alert content in ACK              PASS
fake data only                               PASS
automated validation                         PASS
focused physical QA                          PASS
independent review                           PASS
GitHub review                                PASS
```

Suggested commit:

```text
feat: add durable remote acknowledgment sync
```
