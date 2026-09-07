# P2B TASKS — Guided Pairing / Onboarding / Re-pair UX

Executable checklist for `docs/P2B_SPEC.md` (behavior) +
`docs/P2B_PLAN.md` (units). Every implementation task maps to a SPEC
section; gates are normative, not advisory.

```text
SPEC §1 user flows        → A1 (screens), A2 (re-pair entry, scanner)
SPEC §2 readiness/gate    → A1 (gate + isProvisioned API + permission state)
SPEC §3 permission        → A1
SPEC §4 Tailscale         → A1 (surface), A2 (refinement)
SPEC §5 QR contract       → H1 (compose), A1 (parser)
SPEC §6 pairing states    → A1 (holder), A2 (scanner states)
SPEC §7 error UX          → A1 (mapping), QA (matrix)
SPEC §8 re-pair           → A2
SPEC §9 hardening         → A2
SPEC §10 visual/a11y      → A1 (screens), A2 (refinement), QA (device)
SPEC §11 P2C boundary     → enforced at every review
```

---

## PRECHECK

- [ ] Confirm `dev` clean in Ackline (`git status --short`) and
      personal-admin; record HEADs.
- [ ] Re-read SPEC §5 + §7 + PLAN H1/A1/A2 scope before branching.
- [ ] Confirm CameraX + ZXing-only scanner decision stands (no ML Kit
      creep); confirm short code stays OUT (P2B.1 at most).

## H1 — Hermes QR operator tooling (personal-admin branch off `dev`)

- [ ] Create `requirements.txt` (first declaration file): pin current
      runtime set + add `segno` (pure-Python, terminal rendering, no
      Pillow); pin exact version on the branch. (PLAN H1)
- [ ] Compose canonical SPEC §5 JSON v1 (`v/endpoint/session_id/token`;
      endpoint = `ACK_BASE_URL` + `/pairing/claim`); assert no key, FID,
      ACK token, credentials, or `ack_base_url` in payload.
- [ ] Implement `pairing-begin --qr`: machine JSON on stdout unchanged;
      terminal QR + human block (intent, expiry, scan steps, revoke
      pointer) on stderr; the QR art is SENSITIVE EPHEMERAL OUTPUT
      (endpoint + session + one-time token) — human plaintext carries no
      token/session secret and nothing is logged.
- [ ] Fresh + replace both render; default (no flag) stays
      script-compatible.
- [ ] Tests: payload round-trip, exclusion asserts, version/endpoint
      asserts, human-plaintext token/session absence (QR art excluded —
      it is the sensitive carrier), existing `test_pairing_claim.py`
      green, full Hermes suite green.
- [ ] Security self-review: print-once preserved, QR content audit,
      no secret in shell history guidance.
- [ ] **GATE — H1 REVIEW:** independent review (security-aware) →
      user commit/push → ChatGPT + GitHub `PASS` → merge to `dev`.
      **H1 must merge before Android QR physical integration.**

## H1 REVIEW

- [ ] Reviewer confirms: no `ack_server.py` / `pairing_sessions.py` /
      `fcm_sender.py` diff; no protocol change; token hygiene holds.
- [ ] ChatGPT + GitHub verdict recorded (`PASS` required).

## A1 — Android onboarding state / UI (Ackline branch off `dev`, no camera)

- [ ] QR model + strict v1 parser per SPEC §5 (endpoint rules mirror
      `normalizePairingEndpoint`; unknown fields ignored; redacted
      `toString`; token never persisted).
- [ ] Presentation holder with SPEC §6 states (Idle →
      WaitingForRegistration → ReadyToScan ⇄ TailscaleRequired →
      Scanning → Pairing → Success/Error); calls existing
      `PairingProvisioner` off-main; no P2A logic duplicated.
- [ ] SPEC §7 mapping: every P2A typed failure → Spanish class
      (retry-same / new-QR / replacement / operator / local).
- [ ] Derived gate per SPEC §2 (`pairingConfigured` / `fullyReady`,
      4 derived states, routing rules); permission lifted into
      shared state; add `AckBaseUrlProvider.isProvisioned()`-shaped read
      API (no silent-fallback inference).
- [ ] Screens Bienvenido → Notificaciones (denial blocks `Listo`,
      retry, no nag) → Conectar (Tailscale check + progress + errors) →
      Listo (fullyReady only) → Inbox wiring in `AcklineApp`.
- [ ] Debug-only payload hook (if needed) kept out of release UI and
      secret paths; NO permanent manual-token field. (SPEC §1.A)
- [ ] Tests: parser matrix, redaction asserts, mapping coverage,
      readiness matrix (incl. denied-permission-never-Ready),
      holder/rotation transitions; full gate
      `clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug`.
- [ ] **GATE — A1 REVIEW:** independent review (P2C-boundary check:
      no self-test/health/rotation/multi-device creep) → user
      commit/push → ChatGPT + GitHub `PASS` → merge to `dev`.
      **A1 must merge before the A2 branch.**

## A1 REVIEW

- [ ] Reviewer confirms SPEC→build fidelity (§2 gate derived, §3
      ordering, §5 strictness, §7 completeness) and P2C exclusion.
- [ ] ChatGPT + GitHub verdict recorded (`PASS` required).

## A2 — Scanner / re-pair / hardening (Ackline branch off `dev`)

- [ ] Deps: CameraX (core/camera2/lifecycle/view) + `zxing-core` in
      `gradle/libs.versions.toml` + `app/build.gradle.kts`; versions
      pinned on branch; no ML Kit / embedded / Play-module scanner.
- [ ] Scanner surface: CameraX preview + ZXing QR-only analyzer,
      single success event per session, non-Ackline QRs ignored with
      guidance; `CAMERA` permission flow (denial/retry/permanent).
- [ ] Integration scanner → parser → holder → provisioner; SPEC §6
      Scanning/Pairing/Error states live.
- [ ] Re-pair entry in Ajustes per SPEC §8 (same mechanism, Ajustes
      entry); success clears flag via server-confirmed path only.
- [ ] Hardening per SPEC §9: delete full honor-system chain
      (`markRePairUpdated`/`markUpdated`/`onRePairUpdated`/button),
      redact both `toString`s, rewrite obsolete tests, keep FID copy
      affordance, redact all new models incl. scanner logs.
- [ ] Copy/a11y refinement per SPEC §10 (≥48dp, large-font, TalkBack,
      light/dark, honest states).
- [ ] Tests: static-QR decode accept/reject, single-event, no-logging
      asserts, re-pair-clear, honor-absence, permission/a11y states;
      full regression gate green.
- [ ] **GATE — A2 REVIEW:** independent review (deps minimal, secrets
      audit, P2C check) → physical QA must already be scheduled →
      user commit/push → ChatGPT + GitHub review; **`PASS` requires
      completed P2B-QA below.**

## A2 REVIEW

- [ ] Reviewer confirms scanner deps are exactly CameraX + ZXing-core,
      no migration, no contract drift, redaction asserts green.
- [ ] Logcat pre-screen: no token/FID/key in normal logs.

## PHYSICAL QA — Oppo product gate (blocks A2 PASS and P2B COMPLETE)

- [ ] First install: onboarding route; permission grant AND denial
      runs; denial never shows `Todo listo`; registration-waiting;
      Tailscale-OFF explanation; real terminal QR scan; pairing;
      Listo; Inbox.
- [ ] QR matrix with real sessions: expired, consumed, malformed /
      non-Ackline QR, fresh-vs-held → `replace_required` product copy,
      `--replace` QR → success.
- [ ] Re-pair: warning visible; honor action absent; `Emparejar
      nuevamente` → server-confirmed clear.
- [ ] **Native notification proof (mandatory, closes P2A finding):**
      with permission granted, one real encrypted canary physically
      observed as a native notification; Room exactly-once. P2B cannot
      COMPLETE while this is still unproven.
- [ ] Visual/a11y on device: light + dark, 1.3× font, scanner in real
      light, touch targets, long Spanish copy, TalkBack sanity.
- [ ] Security sweep: logcat token/FID/key inspection clean; no
      app-persisted screenshots; `git status` shows no QA harness
      residue.

## PLANNING REVIEW GATE (blocks all production implementation)

- [ ] Independent planning review of SPEC + PLAN + TASKS (consistency
      per DOCS CLOSEOUT, QR security wording, routing model, P2C
      boundary).
- [ ] User-owned planning commit (docs only) after review acceptance.
- [ ] H1 implementation starts only after the planning commit exists.
      No production code merely because this package exists.

---

## DOCS CLOSEOUT

- [ ] Update `docs/CURRENT_PHASE.md` (P2B PASS + evidence pointer) only
      to reflect landed behavior; no architecture churn.
- [ ] Record QA evidence pointer (`docs/P2B_QA_RESULTS.md` only if the
      project convention from P2A applies; otherwise inline in phase
      closeout).
- [ ] Suggested commit message for the final merge, e.g.
      `feat: add guided pairing onboarding and re-pair flow`.
- [ ] Confirm SPEC→PLAN→TASKS consistency: JSON v1, QR-only, no short
      code, no P2C, explicit-replace server-side, no CLI flags
      on-device, CameraX + ZXing, derived readiness, permission-before-
      ready, honor-system deleted, notification proof required.
