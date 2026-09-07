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
> fresh install → notification permission → pair with Hermes → automatic
> end-to-end verification → "Todo listo" — without adb, shell commands,
> manual FID file editing, or Firebase/Hermes-path knowledge.
>
> P2 direction (from preflight, authoritative unless a later plan revises it):
>
> ```text
> one-time, short-lived pairing session
> QR primary, short pairing-code fallback, same backend session model
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
before pairing/verification. Manual adb staging + manual `ackline-fid`
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
- See `docs/P2A_QA_RESULTS.md` (evidence). `docs/IMPLEMENTATION_PLAN.md`
  now holds the active P2B plan.

### P2B — Guided onboarding + re-pair (CURRENT ACTIVE)

Expected scope: guided first-run setup (Bienvenido → permission →
Tailscale prerequisite → pair → verifying → Todo listo), QR/code pairing
UX on the frozen P2A contract, notification permission before final ready
state, Tailscale-off explanation, re-pair flow replacing the manual
"Mark as updated" honor-system action, quiet/minimal diagnostics, no
secret exposure. Removes the manual FID workflow from normal UX;
operator-friendly pairing initiation on the Hermes side. No P2C self-test
beyond placeholder/navigation; no key rotation (P3); no deeper
diagnostics (P5); no multi-device (P8).

- first-run wizard (Bienvenido → permission → pair → verifying → Todo listo);
- QR scan primary, short-code entry fallback;
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

## P9 — Alternative Push Transport

### Trigger

Only if FCM becomes unsuitable because of platform, cost, privacy, device ecosystem, or product requirements.

The core app must remain transport-isolated so this is a bounded migration.

Do not maintain two push transports proactively.

> **CURRENT:** FCM remains the production transport. ntfy is
> legacy/disabled and is not an approved fallback or rollback path.
