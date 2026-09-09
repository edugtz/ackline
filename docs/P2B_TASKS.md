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

## PLANNING REVIEW GATE (blocks all production implementation)

- [ ] Independent planning review of SPEC + PLAN + TASKS (consistency
      per DOCS CLOSEOUT, QR security wording, routing model, P2C
      boundary).
- [ ] User-owned planning commit (docs only) after review acceptance.
- [ ] ALL production implementation (H1, A1, A2) is blocked until planning
      review is accepted and the planning commit exists.
      No production code merely because this package exists.

## H1 — Hermes QR operator tooling (personal-admin branch off `dev`) — COMPLETE

- [ ] Inspect direct runtime imports/dependencies; create the smallest
      deliberate `requirements.txt` + pinned `segno` (no Pillow, no vendoring).
      Do not snapshot the ad-hoc venv or blindly pip freeze packages. (PLAN H1)
- [ ] Compose canonical SPEC §5 JSON v1 (`v/endpoint/session_id/token`;
      endpoint = `ACK_BASE_URL` + `/pairing/claim`); assert no key, FID,
      ACK token, credentials, or `ack_base_url` in payload.
- [ ] Implement `pairing-begin --qr` as HUMAN QR MODE: terminal QR +
      instructions; NO plaintext token JSON on either stream. Human text has
      no raw token/session secret/key/FID. QR is canonical v1 only, SENSITIVE
      EPHEMERAL OUTPUT; discourage redirecting/persisting it. Not pipe-safe;
      TTL/single-use/revoke bound captured-QR risk. No logging.
- [ ] Fresh + replace both render; default (no flag) stays
      script/pipe-compatible, unchanged JSON stdout containing token once.
- [ ] Tests: payload round-trip, exclusion asserts, version/endpoint
      asserts, human-plaintext token/session absence (QR art excluded —
      it is the sensitive carrier), QR decodes to canonical v1, no key/FID
      in human text, --qr emits no plaintext token JSON, default JSON unchanged,
      existing `test_pairing_claim.py`
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

## A1 — Android onboarding state / UI (Ackline branch off `dev`, no camera) — COMPLETE

- [ ] **A1 PRE-EDIT DECISION GATE:** inspect durable P2A state ownership;
      document exact bounded one-time app-private confirmation bootstrap
      predicate, ordering and repeat-run behavior. Preserve proven P2A installs;
      never falsely confirm fresh installs. FID observation alone and BuildConfig
      ACK fallback alone are insufficient. Resolve ambiguous evidence before
      editing; no Room or broad DB migration. (SPEC §2, PLAN A1)
- [ ] QR model + strict v1 parser per SPEC §5 (endpoint rules mirror
      `normalizePairingEndpoint`; unknown fields ignored; redacted
      `toString`; token never persisted/logged; references discarded after use,
      mutable buffers cleared where applicable, no JVM String zeroization claim).
- [ ] Distinguish unrelated/non-JSON QR → quiet scanner guidance from
      invalid Ackline-shaped JSON / future version → explicit invalid-QR error.
      Use QR-only Spanish copy (short-code entry deferred).
- [ ] Presentation holder with SPEC §6 states (Idle →
      WaitingForRegistration → ReadyToScan ⇄ TailscaleRequired →
      Scanning → Pairing → Success/Error); calls existing
      `PairingProvisioner` off-main; no P2A logic duplicated.
- [ ] SPEC §7 mapping: every P2A typed failure → Spanish class
      (pre-consumption retry-same / new-QR / replacement / operator).
      All post-claim local failures require NEW QR (key import, ACK URL
      persistence, FID confirmation, invalid local state after 200). Ambiguous
      transport permits one retry; CONSUMED then requires new QR. Same-FID
      fresh sessions are idempotent in H1.
- [ ] Add durable `serverPairingConfirmed` / `hasConfirmedPairing`; first FID
      observation stays unconfirmed; full successful provisioning confirms.
      Later FID changes preserve prior confirmation + set rePairRequired.
- [ ] Derived gate: `pairingHealthy` and `fullyReady` per SPEC §2. Unconfirmed
      → onboarding; confirmed re-pair → normal app + Ajustes path; temporary
      registration Waiting never resets identity/onboarding. Add runtime ACK
      provisioned-state read API; lift permission into shared readiness state.
- [ ] Screens Bienvenido → Notificaciones (denial blocks `Listo`,
      explicit user retry only, permanent denial → Settings, no automatic repeated
      prompts or persisted prompt counter) → Conectar (Tailscale check + progress + errors) →
      Listo (fullyReady only) → Inbox wiring in `AcklineApp`. Pairing success
      with permission denied allows Inbox, skips Listo, keeps setup incomplete
      and permission CTA in Ajustes; never traps user in onboarding.
- [ ] Debug-only payload hook (if needed) kept out of release UI and
      secret paths; NO permanent manual-token field. (SPEC §1.A)
- [ ] Tests: parser matrix, redaction asserts, mapping coverage,
      readiness matrix (incl. denied-permission-never-Ready),
      first-observation/confirmed-FID-change/cold-start Waiting routing, P2A
      bootstrap vs fresh/fallback-only state and migration repeat safety,
      post-claim new-QR + ambiguous-consumed mapping, holder/rotation; full gate
      `clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug`.
- [ ] **GATE — A1 REVIEW:** independent review (P2C-boundary check:
      no self-test/health/rotation/multi-device creep) → user
      commit/push → ChatGPT + GitHub `PASS` → merge to `dev`.
      **A1 must merge before the A2 branch.**

## A1 REVIEW

- [ ] Reviewer confirms SPEC→build fidelity (§2 gate derived, §3
      ordering, §5 strictness, §7 completeness) and P2C exclusion.
- [ ] ChatGPT + GitHub verdict recorded (`PASS` required).

## A2 — Scanner / re-pair / hardening (Ackline branch off `dev`) — COMPLETE (implementation landed; P2B-QA PASS — see docs/P2B_QA_RESULTS.md)

- [ ] Deps: CameraX (core/camera2/lifecycle/view) + `zxing-core` in
      `gradle/libs.versions.toml` + `app/build.gradle.kts`; versions
      pinned on branch; no ML Kit / embedded / Play-module scanner.
- [ ] Scanner surface: CameraX preview + ZXing QR-only analyzer,
      single success event per session, non-Ackline QRs ignored with
      guidance ("Este QR no es de Ackline"); invalid Ackline-shaped/future-version
      QR gets explicit invalid error; `CAMERA` permission flow (denial/retry/permanent).
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
      user commit/push → ChatGPT + GitHub review; **P2B-QA has passed on the
      critical physical path (see docs/P2B_QA_RESULTS.md).**

## A2 REVIEW

- [ ] Reviewer confirms scanner deps are exactly CameraX + ZXing-core,
      no Room/DB migration (A1 private-state bootstrap remains required), no contract drift, redaction asserts green.
- [ ] Logcat pre-screen: no token/FID/key in normal logs.

## PHYSICAL QA — Oppo product gate (CLOSED — PASS on critical path; exploratory checks deferred — see docs/P2B_QA_RESULTS.md)

Proven (critical path — owner-accepted):

- [x] real Hermes terminal QR scanned on physical Oppo
- [x] fresh pairing QR against a different registered installation → expected `replace_required` flow
- [x] approved replacement-QR UX (no FID, Firebase terminology, CLI syntax, secret paths, token, or session data)
- [x] real `pairing-begin --qr --replace` replacement QR scanned
- [x] replacement pairing completed; Ackline reached "Listo"
- [x] user entered the normal Personal Admin Inbox afterward
- [x] real encrypted FCM canary after pairing (canary `a82fc904319b4b77a6db8e498f972432`)
- [x] delivery while Tailscale was OFF; native notification physically observed
- [x] notification persisted in Ackline; duplicate delivery did not create a duplicate
- [x] local Visto while Tailnet unavailable; Hermes unacknowledged until Tailscale restored; remote ACK then succeeded

Deliberately unexecuted (NOT blockers, NOT marked executed — deferred to normal usage / bug-driven follow-up):

- [ ] exhaustive ColorOS permission variants — deferred
- [ ] repeated camera lifecycle permutations — deferred
- [ ] font-size permutations — deferred
- [ ] extended accessibility/manual matrix — deferred

Checklist below retained as history (unchecked items are deferred, not open gates):

- [ ] First install: onboarding route; permission grant AND denial
      runs; successful pairing after denial enters Inbox with incomplete status
      and permission CTA, never `Todo listo`; confirmed registration Waiting
      keeps normal app; existing proven P2A upgrade remains recognized;
      Tailscale-OFF explanation; real terminal QR scan; pairing;
      Listo; Inbox.
- [ ] QR matrix with real sessions: expired, consumed, post-claim local failure → new QR;
      unrelated QR → quiet scanner guidance; malformed Ackline/future version
      → explicit invalid error; fresh-vs-held → `replace_required` product copy,
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

---

## DOCS CLOSEOUT

- [x] Update `docs/CURRENT_PHASE.md` (P2B COMPLETE + P2B-QA PASS critical path; see docs/P2B_QA_RESULTS.md)
      only to reflect landed behavior; no architecture churn.
- [x] Record QA evidence pointer (`docs/P2B_QA_RESULTS.md`).
- [ ] Suggested commit message for the final merge, e.g.
      `feat: add guided pairing onboarding and re-pair flow`.
- [ ] Confirm SPEC→PLAN→TASKS consistency: JSON v1, QR-only, no short
      code, no P2C, explicit-replace server-side, no CLI flags
      on-device, CameraX + ZXing, derived readiness, permission-before-
      ready, durable confirmation distinct from observed FID, P2A bootstrap,
      cold-start Waiting preserves pairing, re-pair stays in normal app,
      post-claim new QR, default JSON unchanged vs --qr human-only sensitive
      output, scanner classification, honest token memory wording, permission
      denial allows Inbox/no Todo listo/no counter, deliberate dependencies,
      planning gate before all implementation, honor-system deleted,
      notification proof required.
