# Hermes Notifications — Project Control Package

Working title: **Hermes Notifications**

This package defines the initial product, architecture, implementation roadmap, quality gates, and AI-assisted development workflow for the Android notification client used by Hermes Personal Admin.

The documentation workflow is intentionally aligned with the current `edugtz/gym-ledger` `dev` process as of August 2026, but reduced for the much smaller scope of this app.

## Product in one sentence

A private, reliable Android inbox for Hermes alerts, with explicit manual acknowledgment and resilient eventual ACK synchronization.

## Canonical documentation

```text
AGENTS.md                      Short operational rules for coding agents

docs/PROJECT_SPEC.md           Product behavior and non-goals
docs/ARCHITECTURE.md           Technical boundaries and contracts
docs/MVP_PHASES.md             Detailed MVP/replacement phases
docs/POST_MVP_PHASES.md        Optional evidence-driven post-MVP work
docs/CURRENT_PHASE.md          The only implementation scope currently active
docs/IMPLEMENTATION_PLAN.md    Detailed approved plan for the active phase
docs/ACCEPTANCE_CRITERIA.md    Global reliability/product/quality bar
docs/AI_WORKFLOW.md            AI/model routing, branch workflow, review gates
```

## Source-of-truth rule

The implementation scope is always:

```text
docs/CURRENT_PHASE.md
```

`docs/MVP_PHASES.md` and `docs/POST_MVP_PHASES.md` describe future work but do **not** authorize implementing it early.

Actual repository source code is the source of truth for exact package names, paths, method signatures, Gradle configuration, and current runtime behavior once the project exists.

If docs and code conflict, report the mismatch before editing.

## Branch and review workflow

Mirror the current GymLedger discipline:

```text
dev
  ↓
new branch for one phase
  ↓
CURRENT_PHASE + IMPLEMENTATION_PLAN
  ↓
builder preflight
  ↓
implementation
  ↓
validation
  ↓
independent review
  ↓
physical-device/manual QA when required
  ↓
user commit + push
  ↓
ChatGPT GitHub review
  ↓
PASS
  ↓
merge to dev
```

Do not implement directly on `dev`.

The user owns commits and pushes.

## Initial active phase

```text
Phase 0 — Project Foundation and FCM Registration
```

Phase 1 is the first critical product gate: FCM must prove that the Oppo can receive alerts without opening the app after backgrounding, screen-off, temporary disconnection, and Wi-Fi/mobile transitions.

## Key architecture principle

The app is **not a Firebase app**.

It is a Hermes app that currently uses FCM as its push transport.

Firebase-specific code remains at the transport boundary so Room, inbox UI, acknowledgment state, ACK retry, crypto, and the Hermes protocol are not coupled to Firebase APIs.

## Runtime principles

- Single user.
- Initially one Android device.
- APK sideloading is sufficient.
- No login/accounts.
- No analytics.
- No SaaS backend added solely for this app.
- No persistent app-owned WebSocket.
- No foreground service used merely to keep push alive.
- Push reception must not depend on Tailscale.
- ACK may depend on Tailscale initially and must retry when unavailable.
- Notification dismissal is never acknowledgment.
- Opening the app or detail screen is never acknowledgment.
- E2EE is required before real sensitive Hermes payloads are considered production-ready.
- Hermes SQLite remains authoritative for server-side notification/outbox state.
- Room remains authoritative for device-side inbox and local acknowledgment state.
- FCM is transport, not the database.

## Development philosophy

```text
Reliability first.
Small phases.
Explicit acceptance tests.
One phase per branch.
Independent review for meaningful risk.
No speculative architecture.
No future-phase leakage.
No secret leakage to AI/cloud tools.
UI should feel deliberately designed, not AI-generated or CRUD-like.
```
