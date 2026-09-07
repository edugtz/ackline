# Post-MVP P2A — Pairing Backend / Protocol Implementation Plan

## 1. Status

**ACTIVE PLAN — NOT YET IMPLEMENTED.**

Area: `Post-MVP P2 — Better Pairing / Guided Setup`
Slice: `P2A — pairing backend/protocol`

P2B (guided onboarding + re-pair UX) and P2C (self-test + minimal health)
are **planned, not active**. They appear below only as bounded future
dependencies (§8). Do not implement them in P2A.

Historical note: the previous content of this file was the Phase 7
Recovery and Reconciliation plan (Redesign V2 — all changes landed, merged,
final QA PASS). That history is preserved in git (`dev` history up to the
Phase 7 documentation closeout) and in `docs/CURRENT_PHASE.md`; it is not
repeated here.

## 2. Objective

Replace today's manual setup handshake:

```text
LEGACY (still works, becomes fallback after P2 ships):
  adb staging of the raw E2EE key + process restart
  + manual FID copy into ~/.hermes/secrets/ackline-fid
  + honor-system "Mark as updated" tap
  + eyeballed verification
```

with an authorized protocol:

```text
P2A (this slice — protocol only, no wizard UI):
  short-lived, single-use pairing session
  → phone claims over explicit-VPN Tailnet HTTPS
  → Hermes writes its own ackline-fid (authorized, one-time)
  → Hermes releases the existing E2EE key once in the claim response
  → phone imports directly to Keystore, baseline confirmed by server
```

Product quality goal:

> A fresh install can reach a usable encrypted state without adb, shell
> commands, manual FID file editing, or Firebase/Hermes-path knowledge —
> once P2B/P2C complete the experience on top of this protocol.

## 3. Design Authority

The P2 preflight report is the discovery source of truth for this plan.
Authoritative direction (do not silently revise without evidence):

```text
one-time, short-lived pairing session (TTL on the order of minutes)
QR primary, short pairing-code fallback, same backend session model
pairing claim over existing explicit-VPN Tailnet HTTPS
existing Tailscale-User-Login identity boundary remains required
raw E2EE key NEVER in QR / code / logs / UI / clipboard / source control
no custom cryptography (random bearer token + TLS + existing AES-GCM)
no accounts, no general device registry, single-device semantics remain
explicit replace intent required to overwrite an existing FID
no Room migration, no Hermes notifications-schema migration
```

Standing automatic (token-less) registration is explicitly rejected: any
tailnet identity could silently repoint push delivery and request the E2EE
key. The claim endpoint authorizes exactly one FID-write plus one
key-release — never ACK writes, never notification reads.

## 4. Hermes Work

### 4.1 Pairing-session issuance

Operator-issued (e.g. `notification_state.py pairing-begin`), producing:

- 128-bit random token (QR form) or a bound short code (fallback form);
- server-side session: token **hash** only (SHA-256 or better),
  `created_at`, short expiry, `consumed = false`,
  intent `fresh | replace`;
- QR encodes endpoint + token only. No key, no FID, no secret paths.
- Revoke support (`pairing-revoke`) for a lost/exposed pre-use token.

### 4.2 Pairing claim endpoint (`ack_server.py`)

`POST /pairing/claim { fid, token | code }`:

- require `Tailscale-User-Login` (existing boundary), fail-closed;
- validate: session exists, unexpired, unconsumed — constant-time token
  comparison;
- **atomic consume-first** (`UPDATE … WHERE consumed = 0`), so concurrent
  claims cannot double-spend;
- overwrite of an existing differing FID requires `replace` intent in the
  session; otherwise a typed `already_paired`-class error — never silently
  repoint;
- on success: write `~/.hermes/secrets/ackline-fid` (single line, same
  strict format `load_fid_file` enforces), then respond `200` with
  `{ e2ee_key_b64, kid, ack_base_url }`, `Cache-Control: no-store`;
- failed pairing must not overwrite a working FID;
- sanitized typed errors only (`expired`, `invalid`, `consumed/replay`,
  `already_paired`, `rate_limited`); no secret or path leakage;
- rate-limit claim attempts (brute-force bound for the short-code form).

### 4.3 Key release

- Read the **existing** `hermes-notify.key` into memory, return once in the
  claim response body over tailnet TLS. No rotation, no new key, no key
  server. `kid` is echoed so the phone stores under the correct alias.
- Never log the key; never include it in error paths.

### 4.4 Tests (Hermes unittest harness, alongside existing suites)

- issuance format/entropy shape, hash-only storage;
- expiry enforcement, single-use consume, consume-race (double claim →
    exactly one success);
- invalid/expired/replayed token rejected;
- replace-intent gating (overwrite without intent fails closed; working
    FID preserved);
- `ackline-fid` write format still satisfies `load_fid_file`;
- key-release-once semantics; no key material in logs/errors.

## 5. Ackline Work

- New `PairingClaimClient` (HTTPS client for `POST /pairing/claim`,
  mirroring the `HttpsRecoveryRemoteClient` structure: timeouts,
  no-fallback-to-public-network, sanitized error taxonomy, unit tests with
  a fake HTTP boundary).
- Reuse `TailnetHttpsConnectionFactory` — no new network path, no new
  network permission model.
- `PayloadKeyStore`: add direct in-memory raw-key import
  (`importRawKey(bytes, kid)` under the same `KeyProtection`/GCM
  constraints, zeroing the array after import). The staging-file import
  path is retained **only as debug/recovery fallback** (operational,
  superseded as the user path once P2 ships).
- `FidRePairStore`/manager: baseline the claimed FID and clear
  `rePairRequired` **only on server-confirmed claim success** — the
  honor-system "Mark as updated" action is removed in P2B.
- Do NOT implement QR scanner UI in P2A. No wizard, no camera dependency.
  P2A is exercisable via debug/CLI-driven claims plus unit tests.

## 6. Files Likely Touched

Hermes Personal Admin (`~/.hermes/personal-admin`, separate repo):

```text
ack_server.py                 claim (+ self-test decision, §9.2) handlers
notification_state.py         pairing-begin / pairing-revoke CLI
sessions store                NEW — isolated store (see open question §9.1)
fcm_sender.py                 UNTOUCHED — envelope/build/send reused as-is
test_recovery_server.py       new claim/security cases
```

Ackline (this repo, single `app` module):

```text
pairing/PairingClaimClient.kt NEW (+ unit tests)
network/                      reuse only — no changes expected
security/PayloadKeyStore.kt   add importRawKey; keep staging fallback
pairing/FidRePairStore.kt     server-confirmed baseline/clear
                              (+ manager/test updates)
```

Explicitly NOT touched: `AlertEntity`/Room schema, `AlertIngestion`
validation, crypto primitives, notification channels, inbox/detail/setup
screens (P2B owns UX), scheduler/redelivery logic, launchd/Tailscale wiring.

## 7. Validation

Hermes: existing unittest suites plus new claim cases — evidence from
executed tests, not self-report.

Ackline:

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
```

Physical QA (mandatory before P2A PASS): fresh-install claim on the Oppo
(QR-equivalent token supplied out-of-band until P2B builds the scanner),
expiry/replay rejection, replace-intent overwrite, Tailscale-OFF claim
fails closed with sanitized error.

Review: independent security review required (token lifecycle,
constant-time compare, consume-atomicity, key zeroization, rate limits,
error sanitization) before merge; final ChatGPT + GitHub review per
`docs/AI_WORKFLOW.md`.

## 8. Bounded Future Dependencies (NOT P2A scope)

- **P2B** consumes the claim contract: QR scanner / code-entry UI, first-run
  wizard, re-pair flow, removal of the honor-system clear action.
- **P2C** consumes pairing success: phone-initiated self-test
  (`POST /pairing/self-test` vs claim-flag orchestration) and the quiet
  five-row health surface. The self-test storage decision (§9.2) must be
  settled before P2C implementation but must not block the core claim
  contract unless required.

## 9. Open Questions (explicitly unresolved — do not guess)

1. **Pairing-session persistence:** separate tiny SQLite/file vs another
   safe durable mechanism. Must survive `ack_server.py` launchd restarts
   during the TTL window without touching the production notifications
   table.
2. **Self-test storage semantics (P2C decision):** real committed Hermes
   run row (truthful redelivery bookkeeping; one `pairing-test-*` history
   row) vs ephemeral FCM-only test envelope (no DB trace; bypasses
   `send_transport` bookkeeping). Settle before P2C; do not block the core
   claim contract on it.
3. **QR implementation (P2B, not P2A):** ML Kit Barcode Scanning vs ZXing —
   pending the owner's Play-services stance on a sideloaded APK.
4. **ackBaseUrl provisioning:** retain pure build-time config
   (`local.properties` → `BuildConfig`) vs shipping a stable tailnet
   default with override. Affects whether a fresh-checkout APK is pairable.

## 10. Out of Scope / Do Not Do

- QR scanner / wizard / re-pair UX (P2B);
- self-test orchestration / health surface (P2C);
- key rotation, version history, recovery policy (P3);
- deeper diagnostics beyond the P2C five-row surface (P5);
- multi-device registry or shared-ACK semantics (P8);
- accounts, Firebase Auth, Firestore, new SaaS backend;
- standing token-less registration endpoint;
- custom cryptographic protocol;
- Room migration; Hermes notifications-schema migration;
- ntfy fallback/rollback work — ntfy stays legacy/disabled.

## 11. Suggested Commit

```text
feat: add one-time pairing claim backend and client
```
