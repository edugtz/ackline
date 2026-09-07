# P2B PLAN — Guided Pairing / Onboarding / Re-pair UX

## Status

**ACTIVE BUILD PLAN — P2B.** Implements `docs/P2B_SPEC.md` on the frozen
P2A contract (Hermes H1 + Ackline A1, `docs/P2A_QA_RESULTS.md`).
Execution checklist: `docs/P2B_TASKS.md`.

```text
P2B-H1  Hermes QR operator tooling      (separate repo)
P2B-A1  Android onboarding state / UI   (no camera)
P2B-A2  Scanner / re-pair / hardening   (+ CameraX/ZXing deps)
P2B-QA  Physical Oppo product gate      (mandatory before PASS)
```

Gates: **H1 merges before Android QR physical integration. A1 merges
before the A2 branch. A2 cannot PASS without physical Oppo QA. P2B
cannot COMPLETE with native notification still unproven after permission
is granted.**

P2A is not redesigned. No protocol, crypto, Room, or Hermes-DB change.
Expected DB migration: **none**.

---

## P2B-H1 — Hermes QR Operator Tooling

Separate repo: `~/.hermes/personal-admin`. No protocol, session-store,
claim-endpoint, rate-limit, or crypto change.

### Scope

- Extend `pairing-begin` with a `--qr` mode rendering the canonical
  SPEC §5 JSON v1 payload as a terminal QR plus human instructions
  (fresh vs replace intent line, expiry, "scan with Ackline" steps,
  revoke pointer).
- Compose the full pairing endpoint server-side from the existing
  `ACK_BASE_URL` + `/pairing/claim` — the operator never assembles URLs.
- Preserve machine-readable compatibility: default `pairing-begin`
  output (JSON on stdout) is byte-compatible; `--qr` adds the terminal QR
  plus human text on **stderr** (terminal-only, pipe-safe).
- Output-hygiene rule: the QR art necessarily encodes endpoint +
  session_id + one-time token, so terminal QR output is SENSITIVE
  EPHEMERAL OUTPUT — not to be redirected, persisted, or copied
  unnecessarily. The token MUST NOT *also* be printed as plaintext human
  guidance and MUST NOT be logged; human stderr text references "el QR"
  / expiry / intent only. The stdout machine JSON continues to expose the
  token once, per its documented machine contract. Expiry, single-use
  atomic consume, and revoke remain the protection against captured QR
  output.

### Dependency decision (inspected, not installed)

Hermes has **no dependency-declaration file**: no `requirements.txt`,
no `pyproject.toml`, no `setup.py`; `.venv/` is gitignored and was
populated ad hoc (pip 26.2.1, python 3.11; installed set is
firebase/google-cloud/requests-centric). P2B-H1 therefore:

1. Creates `requirements.txt` (new file, repo root of personal-admin)
   as the dependency mechanism, pinning the runtime set already in use
   plus one addition.
2. Adds **`segno`** — pure-Python, actively maintained QR generator
   whose terminal rendering works with **no Pillow/image stack**
   (verify exact `terminal()` API at implementation time). Rationale:
   no vendored encoder (owner decision), no native/imaging dependency
   on a headless Mac runtime, smallest possible footprint for
   text→terminal-QR. `qrcode`(+Pillow) is the rejected alternative:
   heavier and without built-in terminal output.
3. Does NOT install anything during planning (owner constraint); the H1
   branch pins the exact version and installs then.

### Files (Hermes)

```text
notification_state.py   EXTEND — --qr flag, endpoint composition,
                        stderr human block (CLI only)
requirements.txt        NEW — first declaration file (runtime pins + segno)
pairing_qr.py           NEW (optional, preferred) — payload composition +
                        rendering helper, kept out of claim path
ack_server.py           UNTOUCHED
pairing_sessions.py     UNTOUCHED
fcm_sender.py           UNTOUCHED
```

### Tests (Hermes)

```text
QR payload round-trip at tooling level (compose → parse per SPEC §5)
no key / FID / ack_base_url in payload
version == 1; endpoint == ACK_BASE_URL + /pairing/claim
fresh + replace both generate
human instruction plaintext contains no token/session secret (QR art
itself excluded — it is the intentionally sensitive ephemeral carrier);
QR payload decodes to the canonical payload
default machine JSON unchanged (existing test_pairing_claim.py passes)
full existing Hermes suite passes
```

Security review required (token print-once preserved, QR content audit).
No device QA for H1 alone.

---

## P2B-A1 — Android Onboarding State / UI (No Camera)

### Scope

- P2B QR payload model + strict v1 parser per SPEC §5 (redacted
  `toString`, unknown fields ignored, endpoint rules consistent with
  the existing `normalizePairingEndpoint`: https, host, no
  userinfo/query/fragment, bounded lengths matching P2A limits).
- Presentation state holder (`PairingViewModel`-shaped; follows the
  repo's existing state-holder conventions) owning SPEC §6 states and
  mapping P2A typed failures → SPEC §7 Spanish copy. Calls the existing
  `PairingProvisioner` off-main; duplicates no P2A logic.
- Derived readiness + onboarding gate per SPEC §2 (pairingConfigured /
  fullyReady, 4 derived states, routing rules), including the small
  new `AckBaseUrlProvider` provisioned-state read API
  (e.g. `isProvisioned()` — inspected: no such API exists today,
  `getBaseUrl()` falls back silently, so this is required, not
  optional).
- Lift notification-permission state into shared readiness state.
- Screens: Bienvenido, Notificaciones (with denial/retry per SPEC §3),
  Conectar (Tailscale prerequisite per SPEC §4 + pairing progress +
  errors), Listo (fullyReady only). First-run routing in `AcklineApp`.
- Conectar ships with a **safe test hook only**: a debug-only
  payload-injection path for JVM tests / `debug` builds, never a
  permanent manual-token field in release UI, never near secret paths.
  (Implements the owner constraint: no fake "enter 43-char token" UX.)

### Files (Ackline, single `app` module)

```text
feature/onboarding/*        NEW — 4 screens + gate wiring
feature/pairing/*           NEW — QR model/parser, ViewModel/holder,
                            SPEC §7 error mapping (no scanner yet)
ui/AcklineApp.kt            EDIT — derived onboarding gate
SetupState.kt               EDIT — permission in state, redaction prep
network/AckBaseUrlProvider.kt EDIT — provisioned-state read API only
```

Explicitly NOT touched in A1: camera/manifest, `PairingProvisioner`,
`PairingClaimClient`, crypto, Room, ACK/recovery, SetupScreen re-pair
entry (A2), honor-system deletion (A2).

### Tests (Ackline, JVM)

```text
QR parser: valid v1, wrong version, bad endpoint ×5 rules,
blank/oversize session/token, unknown-fields-ignored,
non-JSON rejected, redacted toString asserts
error mapping: every P2A failure → correct Spanish class
  (retry-same / new-QR / replacement / operator / local)
readiness matrix: pairingConfigured over its 6 signals; fullyReady =
  pairingConfigured && notificationGranted; denied permission ⇒ never
  Ready; registration/key/rePair transitions
  permission denied ⇒ never Ready; registration/key/rePair transitions
ViewModel state: rotation-retained transitions incl.
  WaitingForRegistration → ReadyToScan
regression: ./gradlew clean kspDebugKotlin lintDebug
  testDebugUnitTest assembleDebug
```

Physical UI review may begin after A1 but does not close P2B.

---

## P2B-A2 — Scanner / Re-pair / Hardening

### Scope

- CameraX preview + ZXing-core QR-only analyzer as a scanner surface
  (`PreviewView` in `AndroidView`, `ImageAnalysis`, QR decode only,
  single success event per scan session, arbitrary QRs ignored with
  quiet guidance), wired scanner → parser → ViewModel.
- `CAMERA` permission flow (request, denial with retry, permanent-denial
  guidance; scanner is the one place camera is needed — no fallback
  manual field per owner decision).
- Re-pair entry in Ajustes reusing the A1 flow; success clears
  `rePairRequired` exclusively via the existing server-confirmed path.
- Hardening deletions per SPEC §9 (full honor-system chain + both
  `toString` redactions + obsolete-test rewrite).
- Final copy/accessibility refinement (≥48dp, large-font, TalkBack,
  light/dark).

### Dependency changes (exact placement, no edits in planning)

Version catalog `gradle/libs.versions.toml`:

```text
[versions]  camera = "<latest stable 1.3.x/1.4.x at branch time>"
            zxing   = "<latest stable 3.5.x at branch time>"
[libraries] androidx-camera-core / -camera2 / -lifecycle / -view
            zxing-core = { group="com.google.zxing", name="core", ... }
```

`app/build.gradle.kts`: `implementation` entries for the above. No
BOM conflicts expected (Compose BOM 2026.08, minSdk 28 satisfies
CameraX ≥21). Exact patch versions pinned on the A2 branch after
checking Maven Central — deliberately not frozen in planning. No ML
Kit, no JourneyApps, no Play-module scanner. No DB migration.

### Files

```text
feature/pairing/scanner/*   NEW — analyzer + Compose surface + CAMERA flow
feature/setup/SetupScreen.kt EDIT — re-pair entry, delete honor button
pairing/FidRePairManager.kt EDIT — delete markRePairUpdated
pairing/FidRePairStore.kt   EDIT — delete markUpdated
SetupState.kt               EDIT — delete onRePairUpdated, redact
AcklineApplication.kt       EDIT — delete markRePairUpdated
AndroidManifest.xml         EDIT — CAMERA permission
gradle/libs.versions.toml + app/build.gradle.kts  EDIT — deps
FidRePairManagerTest.kt + new tests  EDIT/NEW
```

### Tests

```text
scanner decode of static generated QR images (no camera in JVM):
  accepts canonical v1, rejects arbitrary QR
single-decode-event-per-session assert
no token/session/FID in logs (redaction asserts incl. scanner path)
re-pair success clears flag via server-confirmed path only
honor-system path absent (no references; rewritten tests)
permission/accessibility state tests
full regression suite (same gate as A1)
```

Physical Oppo QA mandatory (see P2B-QA).

---

## P2B-QA — Product Gate (Mandatory)

First install: routes to onboarding; permission grant; permission denial
(never `Todo listo`, retry works); registration-waiting state;
Tailscale-OFF explanation; real terminal QR scan; successful pairing;
Listo; Inbox.

QR matrix (real sessions): expired → new-QR copy; consumed/replayed →
new-QR copy; malformed/non-Ackline QR → ignored/guided; fresh-vs-held
FID → `replace_required` product copy (no CLI syntax on-device);
`--replace` QR → success.

Re-pair: `rePairRequired` visible in Ajustes; no `Marcar como
actualizado` anywhere; `Emparejar nuevamente` → server-confirmed clear.

Notification proof (closes the P2A finding): after granted permission,
one real encrypted canary must be **physically observed as a native
notification**, Room exactly-once. No P2C automation is built to do
this — one manual canary suffices.

Visual: Oppo, light + dark, large font (1.3×), scanner in real light,
touch targets, long Spanish copy unclipped, TalkBack/semantics sanity.

Security: logcat inspected for token/FID/key leakage; no sensitive QR
payload in normal logs; app persists no screenshots; no stale QA
harness remains in tree.

Review chain per `docs/AI_WORKFLOW.md`: builder → independent review →
user-owned commit/push → ChatGPT + GitHub final (`PASS` merges to
`dev`).

---

## Validation (applies to A1/A2 branches)

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
```

Hermes H1: existing suite + new tooling tests (see H1). Planning
validation only: `git diff --check`, `git status --short`. No Gradle,
no device, no live pairing session during planning.
