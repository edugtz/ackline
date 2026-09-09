# Post-MVP P2B — Guided Pairing / Onboarding / Re-pair UX

## Status

**CLOSED — P2B COMPLETE (P2B-QA PASS on critical physical path; see docs/P2B_QA_RESULTS.md).**

Area: `Post-MVP P2 — Better Pairing / Guided Setup`
Slice: `P2B — guided onboarding + re-pair UX`

```text
P2A  pairing backend/protocol     COMPLETE — implemented and physically integrated
P2B  guided onboarding + re-pair  COMPLETE (H1 + A1 + A2 landed; P2B-QA PASS — see docs/P2B_QA_RESULTS.md)
P2C  self-test + minimal health   PLANNED — not active
```

This file is the concise active-plan index. The normative P2B package is:

- `docs/P2B_SPEC.md` — behavior and acceptance rules;
- `docs/P2B_PLAN.md` — implementation units, boundaries, and validation;
- `docs/P2B_TASKS.md` — executable checklist and review gates.

P2A remains frozen. Its implementation evidence is in
`docs/P2A_QA_RESULTS.md`.

## Approved P2B decisions

- P2B v1 is **JSON v1 QR-only**. Short code is deferred to optional
  **P2B.1**; it is not a P2B v1 fallback.
- Android scanning is **CameraX + ZXing core**. Do not add ML Kit,
  JourneyApps, or a Play-services scanner.
- The normal flow is:

  ```text
  fresh install
  → notification permission
  → scan QR
  → pair/provision
  → readiness
  → Inbox
  ```

- P2B adds guided onboarding, QR pairing, re-pair, and honest readiness
  states on the existing P2A claim contract.
- P2B-A1 adds durable `serverPairingConfirmed`. The first observed FID is
  not confirmation; only successful provisioning through the server-confirmed
  path establishes pairing.
- Missing notification permission never means `Todo listo`. Pairing may
  leave the user in Inbox with setup incomplete and a permission action.
- Explicit replace remains server/operator-side. A differing FID uses the
  `replace_required` flow.
- `pairing-begin --qr` is human QR mode: it emits the QR carrier and
  instructions, not plaintext token JSON.
- P2B does not implement the P2C end-to-end self-test or health surface.
  P2C remains planned and separate.

## Scope boundaries

P2B uses the existing pairing session, claim, E2EE, ACK URL, and FID
provisioning contracts. Do not change the claim protocol, cryptographic
primitives, Room schema, Hermes notification schema, ACK/recovery behavior,
or transport architecture.

Implementation order and exact file/test scopes remain in `docs/P2B_PLAN.md`
and `docs/P2B_TASKS.md`. H1, A1, and A2 are landed; P2B-QA has passed on the
critical physical path (see `docs/P2B_QA_RESULTS.md`).

## Validation

Planning/docs changes use:

```bash
git diff --check
git status --short
```

Production implementation validation is defined by `docs/P2B_PLAN.md` and
is not part of this documentation alignment.
