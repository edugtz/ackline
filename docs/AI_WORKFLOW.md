# Hermes Notifications — AI Workflow (August 2026)

## Purpose

This document defines the operational AI-assisted development workflow for Hermes Notifications.

The strategy is aligned with the current GymLedger `dev` workflow, but adapted to a smaller Android + FCM + Hermes integration project.

```text
Local-first for normal implementation.
Cloud-assisted when product quality or technical difficulty justifies it.
Gemini for Android platform/runtime risk.
Correctness specialist for ACK/security/data integrity.
GPT-5.6 Sol Codex for frontier/rescue risk.
ChatGPT + GitHub as tech lead, phase planner, product reviewer, and final acceptance authority.
```

AI tools are development tools only.

The runtime product must not depend on paid AI APIs.

Model names/routes are routing defaults, not project dependencies. Revalidate availability/pricing before expensive cloud use.

---

## Quick Reference

1. **Normal local implementation:** Qwen3.8 27B 4bit + DFlash2 — `/local-build`.
2. **Higher local quality:** Qwen3.8 27B 5bit + DFlash2 — `/local-quality`.
3. **Independent local reviewer / second trajectory:** Qwen3.8 27B AWQ 5bpw + Lightning MTP — `/local-review`, review-only.
4. **Cheap cloud discovery/preflight:** MiMo V2.5 — `/cloud-mimo`.
5. **Technical cloud implementation/debug:** DeepSeek V4 Flash Max — `/cloud-ds-max`.
6. **Premium cloud escalation:** GPT-5.6 Luna Medium — external/manual route.
7. **Correctness/security/backend specialist:** Hy3 — `/cloud-hy3`.
8. **Android platform/tooling:** Gemini Android Studio — external.
9. **Frontier rescue/release/security escalation:** GPT-5.6 Sol Codex — external.
10. **Tech lead/final GitHub review:** ChatGPT (GPT-5.6 Sol ChatGPT + GitHub).
11. **Who commits/pushes:** user.
12. **Stop policy:** first real error; maximum two failed attempts on same blocker; no autonomous repair loops.

If the OpenCode configuration changes, `opencode.json` is authoritative for which commands/agents actually exist. Keep this document aligned with it.

---

## 1. Source of Truth

```text
AGENTS.md                    operational agent rules
docs/PROJECT_SPEC.md         product requirements
docs/ARCHITECTURE.md         architecture boundaries
docs/MVP_PHASES.md           full MVP roadmap
docs/POST_MVP_PHASES.md      optional future roadmap
docs/CURRENT_PHASE.md        active implementation scope
docs/IMPLEMENTATION_PLAN.md  active-phase detailed plan
docs/ACCEPTANCE_CRITERIA.md  global quality/reliability gates
docs/AI_WORKFLOW.md          routing and workflow policy
```

The active implementation scope is always:

```text
docs/CURRENT_PHASE.md
```

Do not implement future roadmap work because another model notices it.

Actual source code is authoritative for exact paths/APIs once it exists.

---

## 2. Product / Architecture Non-Negotiables

Hermes Notifications is:

```text
single-user
Android-only
single Android module for MVP
APK-installable
Room-persistent once Phase 2 exists
explicit-acknowledgment
FCM as current push transport
Hermes SQLite as server notification truth
Room as device inbox/ACK truth
```

Do not add unless `CURRENT_PHASE.md` explicitly approves it:

```text
accounts/login
multi-user
multi-device
paid runtime APIs
Hilt
Retrofit
multi-module architecture
persistent app-owned socket
foreground service for push
analytics
Firestore/Auth/Realtime Database/Cloud Functions
new SaaS backend
Play Store/billing
```

---

## 3. Workflow Roles

### BUILDER

```text
edits repository
runs build/tests
reports files changed, validation evidence, and remaining risks
```

### REVIEWER

```text
independent from builder when practical
reviews diff/scope/correctness
should not edit what it reviews
```

Preferred independent local reviewer:

```text
/local-review
Qwen3.8 27B AWQ 5bpw + Lightning MTP
```

### ANDROID PLATFORM SPECIALIST

```text
Gemini Android Studio
```

Handles actual Android/Firebase/Gradle/FCM/WorkManager/Doze/ColorOS evidence.

### TECH LEAD / FINAL REVIEW

```text
ChatGPT + GitHub
```

Responsibilities:

```text
architecture
phase planning
CURRENT_PHASE / IMPLEMENTATION_PLAN
product/UX review
screenshot review
independent pushed-branch review
PASS / PASS_WITH_NOTES / BLOCKED
```

ChatGPT is not the build validator. Build/device evidence must come from the repo/device.

### USER

```text
approves preflight
performs real-device QA where required
owns commits and pushes
decides merge after final review
```

---

## 4. Authoritative Model Routing Matrix

### Local

| Role | Model | Use |
|---|---|---|
| Default Local Builder | Qwen3.8 27B 4bit + DFlash2 | Normal bounded implementation, tests, repositories, ViewModels, Python tooling, straightforward Compose, focused fixes. |
| Quality Local Builder | Qwen3.8 27B 5bit + DFlash2 | More complex implementation, higher-risk cross-layer changes, product-critical work before cloud escalation. |
| Local Independent Reviewer / Alternate Trajectory | Qwen3.8 27B AWQ 5bpw + Lightning MTP | Independent review, consistency check, alternative trajectory when DFlash2 output is questionable. Reviewer should be edit-denied. |

### Cloud / Premium

| Role | Model | Use |
|---|---|---|
| Cheap Cloud Discovery / Preflight | MiMo V2.5 | Repository discovery, builder preflight when phase already planned, cheap bounded patches, first-error extraction. Not architecture authority. |
| Technical Cloud | DeepSeek V4 Flash Max | Difficult technical implementation/debug, Python/HTTP/SQL/Android business logic. |
| Premium Cloud | GPT-5.6 Luna Medium | Complex/high-value implementation, especially when local output quality is insufficient. |
| Correctness / Security Specialist | Hy3 | ACK idempotency, concurrency, persistence correctness, server contracts, crypto-adjacent correctness review. |
| Experimental/temporary models | only when explicitly selected | Never sole authority for security/reliability work; independent review mandatory. |

### Android Specialist

| Role | Model | Use |
|---|---|---|
| Android Platform Specialist | Gemini Android Studio | Gradle/AGP/Kotlin/Compose compiler, Firebase plugin, FID, `FirebaseMessagingService`, notification permission/channels, WorkManager, Doze, Logcat, physical-device/background behavior, ColorOS diagnosis. |

### Frontier / Final Escalation

| Role | Model | Use |
|---|---|---|
| Frontier Implementation / Rescue | GPT-5.6 Sol Codex | Hard repo-wide work, risky migrations, E2EE/security-critical final review, release-hardening, failures after lower tiers. |
| Architect / Final Acceptance | ChatGPT + GitHub | Planning, product/UX, architecture, final pushed-branch review. |

---

## 5. Task Risk Routing

Route by task risk, not by provider habit.

| Category | Task | First choice | Escalation |
|---|---|---|---|
| A | Planning / architecture | ChatGPT | — |
| B | Small bounded implementation | `/local-build` | `/local-quality` → `/cloud-ds-max` |
| C | Medium/complex implementation | `/local-quality` | `/cloud-ds-max` → Luna Medium |
| D | Product-critical UI/UX | ChatGPT plan + `/local-quality` | Luna Medium if CRUD/generic → Sol Codex only if still risky |
| E | ACK / DB / concurrency / security | ChatGPT architecture + `/local-quality` | `/cloud-hy3` / `/cloud-ds-max` → Sol Codex for high risk |
| F | Android/Firebase/platform | Gemini Android Studio | `/cloud-ds-max` for non-platform code → Sol Codex if tooling/repo remains broken |
| G | Debugging | cheap first-error extraction | `/cloud-ds-max`; Gemini if platform; Sol Codex if hard/risky |
| H | Independent review | `/local-review` | `/cloud-ds-max` / Luna; final ChatGPT GitHub |
| I | E2EE / risky migration / release | ChatGPT threat model | `/cloud-hy3` + Sol Codex + ChatGPT final |
| J | Docs / handoff | ChatGPT or local/cheap cloud | premium only if architecture/quality warrants |
| K | Repo discovery / preflight | `/cloud-mimo` or `/local-build` | stop for approval, then selected builder |

### Product-critical UI rule

For inbox/detail/polish:

```text
ChatGPT product plan
→ /local-quality
→ physical screenshot
→ ChatGPT screenshot review
→ Luna Medium only if local result remains generic
→ screenshot review again
```

Do not accept CRUD-quality UI.

---

## 6. Phase Routing Matrix

| Phase | Risk | Preferred Route |
|---:|---|---|
| 0 — Project Foundation + FCM Registration | F | ChatGPT plan → local bounded work → Gemini for Firebase/Gradle/FID → local review → ChatGPT GitHub |
| 1 — FCM Reliability Gate | F/I | ChatGPT test plan → local implementation → Gemini for Doze/ColorOS/FCM → local review → ChatGPT GitHub |
| 2 — Persistent Inbox + Detail | C/D/E | ChatGPT architecture/UX → `/local-quality` → `/local-review` → Luna if UI generic → ChatGPT screenshots/GitHub |
| 3 — Explicit Local ACK | B/D | ChatGPT → `/local-build` → `/local-review` → Gemini for notification-action runtime |
| 4 — Durable Remote ACK | E | ChatGPT → `/local-quality` → `/cloud-hy3`/`/cloud-ds-max` review → Gemini WorkManager → ChatGPT |
| 5 — E2EE | I | ChatGPT threat model → `/local-quality` → Gemini Keystore → `/cloud-hy3` + Sol Codex security review → ChatGPT |
| 6 — Hermes Outbox Integration | C/E | ChatGPT → `/cloud-mimo` preflight → `/local-quality` → `/cloud-ds-max`/Hy3 → local review → ChatGPT |
| 7 — Reconciliation | E/F | ChatGPT → `/local-quality` → Hy3/DS → Gemini background behavior → ChatGPT |
| 8 — Real-World Replacement Gate | F/I | ChatGPT test analysis → Gemini if platform issue → targeted route by defect → Sol Codex only if high-risk → ChatGPT |
| 9 — MVP UX Polish | D | ChatGPT screenshots → `/local-quality` → Luna if needed → local review → ChatGPT screenshots/GitHub |

---

## 7. Universal Phase Loop

Every phase follows this loop.

### Step 0 — Clean State

```bash
git status --short --untracked-files=all
```

Expected:

```text
clean or only intentional local ignored files
```

### Step 1 — New Branch

Create one phase branch from:

```text
dev
```

Do not implement directly on `dev`.

### Step 2 — Set Active Phase

Update:

```text
docs/CURRENT_PHASE.md
```

ChatGPT should provide this directly when acting as planner.

### Step 3 — Planning

For meaningful phases, update:

```text
docs/IMPLEMENTATION_PLAN.md
```

Plan contains:

```text
objective
product quality goal
current repo state
files to touch
files not to touch
implementation order
risks
validation
manual QA
AI route
completion criteria
suggested commit
```

### Step 4 — Discovery / Preflight

Before editing, builder confirms:

```text
actual paths
actual package
existing APIs
exact files to modify/create
doc/code mismatches
validation commands
quality gate
```

A cheap `/cloud-mimo` preflight is appropriate when the phase is already fully planned.

A `/local-build` session may perform preflight + implementation when efficient.

Preflight stops for user approval.

### Step 5 — Implement

Implement only the active phase.

Prefer small, testable changes over long autonomous sessions.

### Step 6 — Validate

Default Android gate:

```bash
./gradlew assembleDebug
```

Critical phases:

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
```

Add targeted Python/server tests where relevant.

If a command fails:

```text
stop at first real error
identify root cause
one focused fix
re-run smallest relevant validation
```

Maximum two attempts on the same blocker.

### Step 7 — Independent Review

The builder should not be the only reviewer for medium/high-risk work.

Preferred:

```text
/local-review
```

Use specialist/cloud review when task risk requires it.

Reviewer result:

```text
PASS
PASS_WITH_NOTES
BLOCKED
```

### Step 8 — Manual / Physical-Device QA

Mandatory for:

```text
FCM/background behavior
Wi-Fi ↔ mobile transitions
Doze
notification actions
permissions
WorkManager ACK retry
ColorOS behavior
UI/product phases
```

AI cannot replace real-device QA.

### Step 9 — Docs Alignment

Before commit, update only docs invalidated by actual implementation.

Do not rewrite architecture/spec just to create documentation churn.

### Step 10 — User Commit + Push

Use exact paths.

Avoid `git add .` unless staged diff is fully understood.

The user owns commit and push.

### Step 11 — ChatGPT GitHub Final Review

ChatGPT reviews the pushed branch/commit through GitHub.

Final result:

```text
PASS
PASS_WITH_NOTES
BLOCKED
```

Default merge policy:

```text
merge to dev after PASS
```

`PASS_WITH_NOTES` requires explicit user decision or follow-up.

### Step 12 — Merge / Next Phase

After merge:

```text
update CURRENT_PHASE
create next branch
repeat
```

---

## 8. Stop and Escalation Policy

### If local model loops

```text
Stop.
Do not try a third speculative attempt.
Create a short handoff.
Escalate according to task risk.
```

### If build fails

Extract the first real error.

Typical route:

```text
cheap/local first-error extraction
→ /cloud-ds-max
→ Gemini if Android platform/tooling
→ Sol Codex if still broken/risky
```

### If UI is functional but mediocre

```text
screenshot
→ ChatGPT review
→ /local-quality if not already used
→ Luna Medium if still generic
→ screenshot review again
```

### If work touches ACK correctness / concurrency / security

```text
ChatGPT architecture
→ quality builder
→ /cloud-hy3 independent review
→ Sol Codex if loss/security risk is high
```

### General Stop Conditions

```text
first real error
maximum two attempts on same blocker
no autonomous repair loops
no progress after ~8 tool calls -> summarize and stop
```

---

## 9. Global Quality Gates

### Scope Gate

```text
Only CURRENT_PHASE implemented.
No future-phase work.
No unrelated refactors.
No unnecessary dependencies.
```

### Runtime Architecture Gate

```text
Hermes SQLite = server truth.
Room = device inbox/ACK truth.
FCM = transport.
Tray state = ephemeral.
No app-owned persistent socket.
No foreground-service push workaround.
```

### Privacy Gate

```text
No service-account key in repo.
No encryption key in repo.
No secret in AI prompts/logs/GitHub.
Before E2EE: fake payloads only.
After E2EE: no plaintext sensitive title/message in FCM payload.
```

### Build Gate

Default:

```bash
./gradlew assembleDebug
```

Critical:

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
```

### Transport Gate

```text
No normal network-transition scenario may require opening the app to restore push.
```

### ACK Gate

```text
Only explicit Visto acknowledges.
Remote failure never undoes local ACK.
Pending ACK survives process restart and retries.
```

### UI / Product Gate

```text
Primary information hierarchy obvious.
Pending/viewed clear.
No generic CRUD feel.
No card soup.
No unnecessary decoration.
Physical-device screenshot/manual QA passes.
```

### Data Safety Gate

```text
notificationId idempotent.
No duplicate inbox rows.
No destructive migration without explicit approval.
Reconciliation does not corrupt ACK state.
```

### Commit / Review Gate

Before merge:

```text
validation passed
manual QA passed where required
independent review completed for medium/high risk
scope clean
user commit/push complete
ChatGPT GitHub final review PASS
```

---

## 10. Sensitive Data Rules for AI

Never route these into cloud AI or public GitHub:

```text
Firebase service-account JSON
service-account private key
AES/E2EE keys
auth tokens
Tailscale credentials
Android signing keys
real private Hermes alert contents
```

Use fake payloads and redacted logs for cloud debugging.

The fact that a model is useful does not justify exposing secrets.

---

## 11. Phase Closeout Template

At the end of a phase report:

```text
Status:
PASS / PASS_WITH_NOTES / BLOCKED

Branch:
<name>

Files changed:
<list>

Validation:
<commands + result>

Manual QA:
<result>

Independent review:
<model/tool + result>

Known limitations:
<list>

Docs updated:
<list>

Suggested commit:
<message>

GitHub final review:
pending / PASS / PASS_WITH_NOTES / BLOCKED
```
