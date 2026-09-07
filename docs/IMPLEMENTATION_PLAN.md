# Post-MVP P2B — Guided Pairing / Onboarding / Re-pair UX Implementation Plan

## 1. Status

**ACTIVE PLAN — P2B.**

Area: `Post-MVP P2 — Better Pairing / Guided Setup`
Slice: `P2B — guided onboarding + re-pair UX`

```text
P2A  pairing backend/protocol     COMPLETE — implemented + physically
                                  integrated (PASS_WITH_FINDINGS;
                                  see docs/P2A_QA_RESULTS.md)
P2B  guided onboarding + re-pair  CURRENT — authorized to implement
P2C  self-test + minimal health   PLANNED — not active
```

P2A background: Hermes H1 (one-time pairing sessions, `POST
/pairing/claim`, Tailscale identity gate, atomic consume-once, explicit
replace intent with canonical `replace_required` error, one-time E2EE key
release over Tailnet HTTPS, `ack_base_url` in claim response) + Ackline A1
(`PairingClaimClient`, direct `PayloadKeyStore` raw-key import, staged adb
import retained as legacy/debug fallback, `AckBaseUrlProvider` with
provisioned-URL precedence, dynamic ACK/recovery URL resolution,
server-confirmed FID baseline, ordered `PairingProvisioner`, no Room
migration). Machine-readable P2A contract is unchanged by P2B. Full
history in git; physical evidence in `docs/P2A_QA_RESULTS.md`.

Historical note: the previous content of this file was the P2A
implementation plan, then the Phase 7 Recovery and Reconciliation plan
(Redesign V2 — all changes landed, merged, final QA PASS). That history is
preserved in git (`dev` history up to the Phase 7 documentation closeout)
and in `docs/CURRENT_PHASE.md`; it is not repeated here.

## 2. Objective

Turn the implemented P2A protocol into a normal-user setup experience:

```text
fresh install
→ grant notification permission (BEFORE final ready state)
→ Tailscale prerequisite surfaced honestly
→ pair with Hermes (scan QR primary / code entry fallback if chosen)
→ call existing PairingProvisioner
→ honest progress/error states
→ success state ("Todo listo")
→ re-pair from Ajustes when device/install identity changes
```

Product quality goal:

> A fresh install reaches a usable encrypted state without adb, shell
> commands, manual FID file editing, Firebase/Hermes-path knowledge, or
> an honor-system self-attestation tap — with permission and Tailscale
> prerequisites explained in plain language.

P2A physical-QA lesson carried in: native notification display was NOT
proven in P2A because `POST_NOTIFICATIONS` was denied at delivery time.
P2B therefore places the notification-permission step before pairing /
future setup verification.

## 3. Design Authority

P2A contract (frozen, do not revise in P2B):

```text
short-lived, single-use pairing session (TTL on the order of minutes)
QR primary, short pairing-code fallback, same backend session model
pairing claim over existing explicit-VPN Tailnet HTTPS
existing Tailscale-User-Login identity boundary remains required
raw E2EE key NEVER in QR / code / logs / UI / clipboard / source control
no custom cryptography (random bearer token + TLS + existing AES-GCM)
no accounts, no general device registry, single-device semantics remain
explicit replace intent required to overwrite an existing FID
canonical differing-FID fresh-claim error: replace_required
  (`already_paired` is stale documentation — do not use in new UX copy/logs)
no Room migration, no Hermes notifications-schema migration
```

P2B UX constraints:

```text
no secret exposure (no E2EE key/FID/service credentials in QR;
  QR may carry pairing endpoint + session_id + one-time token only)
no fake success state
failures explain the failing link in plain language without exposing
  identifiers, paths, tokens, or protocol internals
remove honor-system "Marcar como actualizado"
keep support diagnostics quiet/minimal
redact secret-bearing pairing models (FidRePairState.toString,
  SetupUiState installationId exposure under review)
no P2C self-test beyond placeholder/navigation state if needed
```

## 4. Hermes / Operator Experience Work

Small operator-facing tooling only (separate repo
`~/.hermes/personal-admin`):

- user-friendly pairing initiation against the existing H1 backend;
- generate/show pairing QR and/or code from a fresh one-time pairing
  session;
- short-code/manual-entry fallback if retained;
- explicit replace intent surfaced where appropriate;
- never put E2EE key / FID / service credentials in QR.

No protocol change. No new transport. No key rotation. No registry.

## 5. Ackline Work

- First-run onboarding flow (Bienvenido → permission → Tailscale
  prerequisite → pair → verifying → Todo listo).
- Notification permission step before final ready state.
- Tailscale prerequisite guidance; Tailscale-off state explains the
  prerequisite honestly.
- QR scan path (scanner implementation choice settled at build time);
  code-entry fallback if chosen.
- Calls the existing `PairingProvisioner` — no new provisioning path.
- Honest progress/error states; success state.
- Re-pair flow from Ajustes; removes honor-system "Marcar como
  actualizado".
- Support diagnostics stay quiet/minimal.
- No P2C self-test implementation (placeholder/navigation only if needed).

## 6. Files Likely Touched

Ackline (this repo, single `app` module):

```text
onboarding/…               NEW — first-run flow, permission step, states
pairing/…                  UX around existing PairingProvisioner /
                           PairingClaimClient (no contract change)
setup/…                    re-pair entry, remove honor-system action
navigation                 onboarding ↔ inbox/Ajustes wiring
```

Hermes Personal Admin (`~/.hermes/personal-admin`, separate repo):

```text
pairing initiation tooling  NEW/small — QR and/or code display
ack_server.py               UNTOUCHED (claim contract frozen)
sessions store              UNTOUCHED
fcm_sender.py               UNTOUCHED
```

Explicitly NOT touched: P2A claim contract, crypto primitives,
`AlertEntity`/Room schema, `AlertIngestion` validation, scheduler /
redelivery logic, key rotation, accounts, multi-device, deeper
diagnostics, new transport.

## 7. Validation

Ackline:

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
```

Physical QA (mandatory before P2B PASS): first-run flow on the Oppo
without shell/manual docs; permission-before-ready; Tailscale-off
explanation; QR scan path; code/manual fallback if shipped; re-pair path;
no secret exposure; no honor-system action; no fake success; large-font /
accessibility / touch-target review; visual/interaction review on the
physical Oppo.

Review: product/UX architecture review + security/privacy review around
QR/token exposure + final ChatGPT + GitHub review per
`docs/AI_WORKFLOW.md`.

## 8. Bounded Future Dependencies (NOT P2B scope)

- **P2C** (planned): phone-initiated end-to-end self-test and the quiet
  five-row health surface (last push, pending ACK count, last ACK sync,
  last reconciliation, app/build version). Do NOT implement in P2B.
- **P3**: key rotation/history — still separate, not in P2B.
- **P5**: deeper diagnostics remain trigger-based, not in P2B.
- **P8 multi-device**: out of scope.
- **P9**: FCM remains the production transport; ntfy is NOT a fallback.

## 9. Open Questions (explicitly unresolved — do not guess)

1. **QR scanner implementation:** ML Kit Barcode Scanning vs ZXing —
   pending the owner's Play-services stance on a sideloaded APK.
2. **Short-code fallback:** retain code/manual entry alongside QR, or
   QR-only. Decide at P2B build time; backend session model supports both.
3. **Self-test storage semantics (P2C decision):** real committed Hermes
   run row vs ephemeral FCM-only test envelope. Settle before P2C.

## 10. Out of Scope / Do Not Do

- P2C self-test orchestration / health surface (beyond placeholder nav);
- key rotation, version history, recovery policy (P3);
- deeper diagnostics beyond the P2C five-row surface (P5);
- multi-device registry or shared-ACK semantics (P8);
- accounts, Firebase Auth, Firestore, new SaaS backend;
- custom cryptographic protocol;
- Room migration; Hermes notifications-schema migration;
- ntfy fallback/rollback work — ntfy stays legacy/disabled.

## 11. Suggested Commit

```text
feat: add guided pairing onboarding and re-pair flow
```
