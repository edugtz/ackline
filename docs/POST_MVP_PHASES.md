# Ackline — Post-MVP Phases

Post-MVP work is **evidence-driven**.

Do not implement these merely because they are listed.

`docs/CURRENT_PHASE.md` remains the only active scope.

## Principle

The successful product can remain permanently small.

If daily use does not reveal a problem, do not create a feature to solve it.

---

## P1 — UX Refinement From Real Usage

> **STATUS: NOT ACTIVE.** Phase 9 already delivered the initial deliberate
> product polish (accepted on the physical Oppo). P1 now means only
> evidence-driven refinement from actual future daily use. Do not reopen
> visual redesign without observed friction.

### Trigger

Repeated friction or screenshot feedback identifies a specific daily-use problem after MVP.

### Possible Work

- tune density/spacing;
- improve pending/viewed hierarchy;
- refine detail layout;
- refine severity visuals;
- improve timestamps;
- notification grouping if volume justifies it;
- optional swipe gestures only if explicit ACK semantics remain intact.

### Recommended AI Route

```text
ChatGPT screenshot/product review
→ /local-quality
→ Luna Medium only if quality remains generic
→ ChatGPT screenshot review
```

### Do Not Do

Do not redesign for novelty or add features unrelated to observed friction.

---

## P2 — Better Pairing / Guided Setup

> **STATUS: CURRENT ACTIVE AREA.** Promoted from future roadmap after the P2
> preflight. Manual FID copy/paste plus adb E2EE staging is the confirmed
> setup friction. P2 turns setup into a guided pairing experience:
> fresh install → notification permission → scan QR → pair/provision
> → readiness → Inbox — without adb, shell commands, manual FID file
> editing, or Firebase/Hermes-path knowledge. Automated end-to-end
> self-test is deferred to P2C.
>
> P2 direction (from preflight, authoritative unless a later plan revises it):
>
> ```text
> one-time, short-lived pairing session
> JSON v1 QR-only for P2B v1; short code deferred to optional P2B.1
> pairing claim over existing explicit-VPN Tailnet HTTPS
> existing Tailscale identity boundary remains required
> raw E2EE key NEVER in QR / code / logs / UI / clipboard
> Hermes releases the existing E2EE key once, in the claim response,
>   over authenticated HTTPS; the phone imports directly to Keystore
> Hermes updates its own single-device ackline-fid ONLY after an
>   authorized pairing claim — this is NOT a standing
>   automatic-registration endpoint
> explicit replace intent required to overwrite an existing FID
> no accounts, no general device registry, single-device semantics remain
> no custom cryptography, no Room migration expected
> ```
>
> Tailscale on the phone remains a user-visible prerequisite for
> pairing/ACK/recovery. P2 does not promise zero-configuration setup.

### P2A — Pairing backend/protocol (COMPLETE)

Implemented and physically integrated (Hermes H1 + Ackline A1;
`docs/P2A_QA_RESULTS.md` — PASS_WITH_FINDINGS). Landed architecture:
one-time short-lived pairing sessions, hash-only token persistence,
atomic consume-once, explicit replace intent (`replace_required` is the
canonical differing-FID error; `already_paired` is stale), one-time E2EE
key release over Tailnet HTTPS, `ack_base_url` provisioned in the claim
response, rate limiting, sanitized typed errors, direct Keystore import,
server-confirmed FID baseline, dynamic ACK/recovery URL resolution, no
Room migration, no UI. Physical proof: real FCM through provisioned FID,
real E2EE decrypt, Room exactly-once, provisioned ACK URL, Hermes ACK
sync. Non-blocking finding: native notification display NOT proven
(`POST_NOTIFICATIONS` denied at delivery time) — P2B onboards permission
before pairing/readiness. Manual adb staging + manual `ackline-fid`
copy are now legacy/debug fallback, not the normal path.

- pairing-session issuance (short TTL, single use, token hashes only,
  constant-time comparison, atomic consume);
- explicit fresh vs replace intent;
- pairing claim endpoint reusing the Tailscale identity boundary;
- authorized `ackline-fid` write on valid claim;
- one-time E2EE key release in the claim response (`no-store`, sanitized
  typed errors, rate limiting, revoke support);
- Ackline `PairingClaimClient` reusing `TailnetHttpsConnectionFactory`;
- direct raw-key import into the existing `PayloadKeyStore`;
  adb staging-file import retained only as debug/recovery fallback;
- server-confirmed FID baseline / `rePairRequired` clear.
- QR scanner UI is NOT P2A; it belongs to P2B.
- See `docs/P2A_QA_RESULTS.md` (evidence). The normative P2B package is
  `docs/P2B_SPEC.md`, `docs/P2B_PLAN.md`, and `docs/P2B_TASKS.md`;
  `docs/IMPLEMENTATION_PLAN.md` is the concise index.

### P2B — Guided onboarding + re-pair (IMPLEMENTATION COMPLETE — P2B-QA ACTIVE GATE)

Landed scope: guided first-run setup (Bienvenido → permission →
Tailscale prerequisite → scan QR → pair/provision → readiness → Inbox),
QR-only pairing UX on the frozen P2A contract, notification permission
before final ready state, Tailscale-off explanation, re-pair flow through
the same QR scanner (manual "Mark as updated" honor-system action
REMOVED), quiet/minimal diagnostics, no secret exposure. The manual FID
workflow is out of normal UX; operator-friendly pairing initiation on the
Hermes side. No P2C self-test beyond placeholder/navigation; no key
rotation (P3); no deeper diagnostics (P5); no multi-device (P8).

P2B itself is NOT COMPLETE: P2B-QA is the mandatory physical product
gate (partial evidence only — real scanner, fresh-QR transport,
`replace_required` mapping, replacement QR, server-confirmed replacement,
and Listo → Inbox proven; encrypted FCM canary, native notification, Room
exactly-once, and Visto → remote ACK still pending).

- first-run wizard (Bienvenido → permission → scan QR → pair/provision → readiness);
- JSON v1 QR-only in P2B v1; short code is deferred to optional P2B.1;
- re-pair flow replacing the manual "Mark as updated" honor-system action;
- no FID / file-path / Firebase jargon in user-facing copy.

### P2C — Self-test + minimal setup health (PLANNED — still future)

Kept scope: end-to-end self-test + minimal health surface (last push,
pending ACK count, last ACK sync, last reconciliation, app/build
version).

- phone-initiated end-to-end self-test (Hermes → FCM → decrypt → persist →
  ACK → Hermes) using the production E2EE protocol;
- quiet minimal health surface only (see P5 note below).

### Recommended AI Route

```text
P2A: complete (see docs/P2A_QA_RESULTS.md)
P2B: Android/Compose implementation + physical Oppo UX review
P2C: cross-system correctness review
```

### Do Not Do

No accounts, no general device-management backend, no multi-device
registry (P8 remains out of scope), no custom crypto protocol,
no standing unauthenticated registration endpoint.

---

## P3 — E2EE Key Rotation and Recovery

### Trigger

Long-term use, reinstall, or device replacement makes key lifecycle management necessary.

### Possible Work

- key rotation;
- small key-version history;
- long-term key recovery policy;
- device-loss procedure beyond fresh pairing.

> **P2 vs P3 boundary:** P2 provisions the *existing current* key to a
> (re)installed device through a fresh pairing session. That reinstall
> re-provisioning is NOT key rotation. Rotation, version history, and
> recovery policy remain P3.

### Recommended AI Route

ChatGPT threat model → `/cloud-hy3`/`/local-quality` → Gemini Keystore → Sol Codex security review.

### Do Not Do

Do not invent custom crypto protocols.

---

## P4 — VPS ACK Endpoint Migration

### Trigger

Hermes Personal Admin moves from the Mac to a VPS or a stable public endpoint becomes desirable.

### Possible Work

- replace Tailscale/Mac ACK URL;
- preserve the same ACK contract;
- add strong authentication;
- keep WorkManager retry unchanged;
- keep domain/UI unchanged.

### Recommended AI Route

ChatGPT architecture → `/local-quality` or `/cloud-ds-max` → `/cloud-hy3` correctness review.

---

## P5 — Delivery / Sync Diagnostics

### Trigger

Real-world debugging shows that a small diagnostics surface would materially reduce maintenance.

### Possible Information

- FCM/FID registration state;
- last push received;
- pending ACK count;
- last ACK sync;
- last reconciliation;
- app/build version.

> **PARTIALLY PROMOTED INTO P2C.** The P2 preflight promotes exactly this
> subset into P2C as a quiet minimal health surface for setup/support:
> last encrypted push received, pending ACK count, last successful ACK
> sync, last reconciliation, app/build version. P2C exposes nothing more.
> P5 remains available for future *deeper* diagnostics only if real-world
> maintenance justifies it.

### Do Not Do

Do not turn the app into an ops dashboard or expose secrets/identifiers unnecessarily.

---

## P6 — Retention and Local History

### Trigger

Viewed history grows enough to create storage or usability friction.

### Possible Policies

- keep all;
- delete viewed alerts older than N days;
- manual clear viewed history.

Default to preserving data until evidence says otherwise.

---

## P7 — Search / Additional Filtering

### Trigger

Inbox history becomes large enough that `Pendientes` / `Vistas` is insufficient.

### Possible Work

- local text search;
- severity filter;
- date grouping.

No backend search.

---

## P8 — Multi-Device

### Trigger

A second personal device genuinely needs independent push/ACK behavior.

### Required New Design Questions

- multiple FIDs;
- device-specific encryption keys;
- shared Hermes notification identity;
- whether ACK by one device acknowledges globally or per-device.

Do not prematurely add this complexity.

> **P2 boundary:** P8 multi-device remains explicitly OUT OF SCOPE for P2.
> P2 keeps single-device, last-writer-wins FID semantics.

---

## P9 — Reliability Fallback / Alternative Transport

> **STATUS: TRIGGER ONLY / NOT ACTIVE.** Do not design or implement now.
> The preferred outcome is that P9 is never needed.

### Trigger

Only real-world evidence showing unacceptable Ackline/FCM reliability
after reasonable attempts to fix Ackline/FCM (Phase 8 or later). The
sequence is: observed reliability problem → investigate alternatives with
current evidence → select one only if justified.

Candidate options at evaluation time may include Pushover, Telegram Bot,
or another evidence-backed service available at that future time. These
are candidates, not selected solutions. Do not promise Pushover or
Telegram. Do not maintain two transports proactively.

The core app must remain transport-isolated so a future migration, if
ever triggered, stays bounded.

> **CURRENT:** Ackline + FCM is the sole supported production
> notification path. ntfy is architecturally rejected/unsupported — not a
> fallback, rollback, or roadmap item; remaining Hermes legacy ntfy code
> removal is a dedicated cleanup after P2B QA closeout.
