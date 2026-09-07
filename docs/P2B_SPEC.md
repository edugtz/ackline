# P2B SPEC — Guided Pairing / Onboarding / Re-pair UX

## Status

**P2B product-behavior specification (normative for P2B build).**

P2A (Hermes H1 pairing protocol + Ackline A1 provisioning foundation) is
**frozen and complete** — see `docs/P2A_QA_RESULTS.md`. This spec designs
**only** the user-facing behavior P2B adds on top of it. It defines
product behavior, **not** implementation tasks (those live in
`docs/P2B_PLAN.md` / `docs/P2B_TASKS.md`).

Authoritative direction this spec implements:

```text
JSON v1 QR payload (endpoint + session_id + token only)
QR-only P2B v1 — short code DEFERRED to optional P2B.1, not designed here
CameraX + ZXing core scanner, QR-only, no ML Kit
no onboardingCompleted flag — readiness derived from real state
permission before ready
honor-system "Marcar como actualizado" deleted
no P2C self-test/health
explicit replace stays a server/operator decision
no CLI syntax in Android UX
```

---

## 1. User Flows

### 1.A First install — 4 surfaces, no tiny-wizard sprawl

```text
1. Bienvenido
2. Notificaciones
3. Conectar con Hermes
4. Listo
   → Inbox
```

**1. Bienvenido.** What Ackline is (personal inbox for Hermes alerts),
the three honest steps ahead (permiso → emparejar → listo), one primary
action (`Empezar`). No technical terms. No skipping ahead that
circumvents permission/pairing — leaving the flow just lands on the same
gate later (see §2).

**2. Notificaciones.** Explains, in one short paragraph, that Ackline
needs notification permission to show Hermes alerts. One primary action
(`Permitir notificaciones`) launching the system permission dialog, plus
a secondary path to continue without granting (see §3 for denial
behavior). This step occurs **before** pairing.

**3. Conectar con Hermes.** Two ordered responsibilities on one surface:

1. Tailscale prerequisite (§4): when no VPN network is active, this
   surface explains — before any scan attempt — that Tailscale must be
   active, with a retry/check action. When a VPN is active, the surface
   proceeds without ceremony.
2. QR scan: `[Escanear QR]` opens the scanner. After a successful decode
   the surface transitions to an honest `Emparejando…` progress state,
   then to success or to a typed error (§6, §7). The existing
   `PairingProvisioner` runs; no new provisioning path exists.

**4. Listo.** Shown **only** on server-confirmed pairing success *and*
full readiness (§2). Copy confirms what is true: permission granted,
paired with Hermes, encryption ready. One primary action
(`Ir a Mis alertas`) enters the Inbox. This screen never appears on
partial state — no fake success.

### 1.B Re-pair — same mechanism, Ajustes entry

```text
Ajustes
→ Estado: "Se requiere volver a emparejar"
→ [Emparejar nuevamente]
→ scanner / pairing flow (same as §1.A step 3)
→ success (server-confirmed)
→ back to Ajustes / Inbox, warning cleared
```

Re-pair reuses the exact scan → provision → success/error flow of first
install. It is entered from Ajustes, not from the first-run gate (§2
explains why the two entries stay distinct). The re-pair flow shows the
same typed errors (§7), including replacement-QR guidance.

### 1.C Reinstall / device replacement

A fresh QR claimed against a Hermes that still holds a **different**
FID fails with the canonical `replace_required` error. Android UX copy
for this case must be product language, **never CLI syntax**:

```text
"Hermes ya está vinculado con otra instalación. Genera un nuevo QR de
reemplazo en tu Mac."
```

Forbidden in Android UI: `--replace`, `pairing-begin`, file paths,
`ackline-fid`, FID values, token/session values. The Hermes-side
operator output (terminal text, §H1 in PLAN) **may** name the exact
command/option — that is the one place CLI syntax is allowed, because
its reader is the operator at the Mac.

---

## 2. First-Run Routing — Derived Gate, No Viewed-Flag

Current reality: the app always starts at Inbox (`AcklineApp` defaults
to `AppScreen.Inbox`). P2B adds a **derived onboarding gate**: onboarding
shows when the device is not yet in a usable paired state; it is never
driven by "the wizard was viewed".

### Readiness signals (all real system state)

```text
notificationGranted   permission state, lifted into shared state (§3)
registrationReady     Firebase registration == Ready
installationId        current installation ID available (non-null)
encryptionReady       PayloadKeyStore ready for the production kid
fidBaseline           pairing/FID baseline confirmed (lastObservedFid set)
rePairClear           rePairRequired == false
ackProvisioned        runtime ACK URL provisioned by pairing
```

`ackProvisioned` needs a genuine signal: `AckBaseUrlProvider` today only
exposes `getBaseUrl()` with silent `BuildConfig` fallback, so
"provisioned by pairing" is not observable. PLAN A1 therefore adds a
small read API (e.g. `isProvisioned()` / `hasProvisionedBaseUrl()`).
The legacy `BuildConfig` fallback may keep ACK operational on old
installs, but a fresh guided pairing must still distinguish
"provisioned" where readiness copy claims it — **do not fabricate
success** by treating fallback as paired.

### Derived states (conceptual; implementation may refine names to match
actual state ownership)

```text
UNPAIRED / SETUP_REQUIRED
   pairing prerequisites/capability incomplete → guided onboarding
PAIRING_READY_BUT_PERMISSION_MISSING
   paired, everything provisioned, notification permission denied
REPAIR_REQUIRED
   paired before, rePairRequired == true now
READY
   fullyReady → the ONLY state that displays "Todo listo"
```

### Definitions

```text
pairingConfigured =
    registrationReady
    && installationId != null
    && encryptionReady
    && fidBaseline
    && ackProvisioned
    && rePairClear

fullyReady =
    pairingConfigured
    && notificationGranted
```

`fidBaseline` = pairing/FID baseline confirmed (`lastObservedFid` set).
`rePairClear` = `rePairRequired == false`. `ackProvisioned` = runtime ACK
URL provisioned by pairing — see below; the legacy `BuildConfig`
fallback may keep ACK operational on old installs, but it must never
masquerade as guided-pairing completion.

### Routing rules

- Pairing incomplete (`pairingConfigured == false` because a pairing
  prerequisite is missing, or `rePairClear == false` on a never-paired
  device) → **onboarding** root instead of Inbox.
- Pairing established but `rePairRequired == true` → **normal app +
  prominent Ajustes re-pair entry**. The full first-run wizard does NOT
  restart automatically.
- Pairing established but notification permission denied → normal app
  remains usable, setup status reads incomplete, permission retry stays
  visible; **never shows `Todo listo`**.
- Only `fullyReady` displays `Todo listo` (Ajustes Estado and onboarding
  Listo alike).
- No `onboardingCompleted=true` flag is added anywhere. "Ready" means
  the signals above, not wizard completion.

---

## 3. Notification Permission — Before Pairing, Before Ready

- The permission step (§1.A.2) occurs **before** pairing.
- Grant → flow continues to Conectar; the grant is part of `fullyReady`.
- Denial:
  - does **not** technically block scanning/claiming (pairing needs
    tailnet, not notifications);
  - **does** block the final `Listo` / `Todo listo` state — denial plus
    successful pairing yields an honest pending state, never success;
  - shows a retry route (`Volver a solicitar` + guidance to system
    settings if permanently denied);
  - does not nag: ask via system dialog at most twice, then rest as a
    quiet pending row until the user acts.
- Permission state is lifted out of the composable `remember` into
  shared readiness state so the gate (§2) and Ajustes read one source.
- No P2C self-test is triggered here. The P2A finding (native display
  NOT PROVEN because permission was denied at canary time) is covered
  naturally: P2B physical QA grants permission during onboarding and
  then observes one real encrypted canary end-to-end (§QA in PLAN).

---

## 4. Tailscale — Explicit Prerequisite, Honest Detection

- Tailscale is an explicit, user-visible prerequisite for **pairing**
  and for **ACK/recovery**. Push reception never depends on it (reception
  copy must never imply otherwise).
- Detection uses the presence of an active `TRANSPORT_VPN` network —
  the same signal family the tailnet HTTPS factory already binds. This
  is the best signal Android offers locally.
- Because a non-Tailscale VPN could theoretically satisfy that
  predicate, copy states that **Tailscale is the supported VPN** rather
  than claiming proven Tailscale identity, e.g.:

```text
"Activa Tailscale en este teléfono para emparejar. Ackline usa
Tailscale para hablar con tu Hermes de forma privada."
```

- Tailscale OFF → the Conectar surface explains the prerequisite with a
  check-again action; it does not attempt the claim into a known-dead
  path, and it never silently falls back to the default network (the
  transport layer already refuses fallback — P2B only surfaces that
  honestly).

---

## 5. QR Contract — Versioned JSON v1

Canonical v1 conceptual shape (exact key names normative):

```json
{
  "v": 1,
  "endpoint": "https://<tailnet-host>:8443/pairing/claim",
  "session_id": "<opaque>",
  "token": "<one-time bearer>"
}
```

The QR carries **only** pairing bootstrap. It MUST NOT contain: E2EE
key, FID, ACK token, Firebase credential, service-account data,
notification content, or `ack_base_url` (the ACK URL arrives later
inside the authenticated claim *response*).

### v1 parser behavior (normative)

Required fields: `v`, `endpoint`, `session_id`, `token`.

```text
v            integer == 1, else reject (forward-version gate)
endpoint     HTTPS; valid host; no userinfo; no query; no fragment;
             path rules consistent with the P2A client validator
             (https, host present, no userinfo/query/fragment,
             bounded length)
session_id   non-blank, bounded (<= 256, matching P2A limits)
token        non-blank, bounded (<= 512, matching P2A limits)
```

- Unknown extra JSON fields are **ignored** (forward compatibility)
  while required v1 fields are strictly validated.
- Non-JSON QR content, wrong version, or any field violation → the
  malformed-QR error (§7), never a claim attempt.
- The parsed model carries a **redacted `toString()`** (no
  token/session/endpoint contents).
- The one-time token is **never persisted** — memory only, cleared
  after the claim attempt. Process death loses it by design (§6).

Token protection remains H1's boundary (short TTL, single-use atomic
consume, revoke support) — unchanged by P2B.

---

## 6. Pairing Presentation States

```text
Idle → WaitingForRegistration → ReadyToScan ⇄ TailscaleRequired
ReadyToScan → Scanning → Pairing → Success
Pairing → Error → (retry same QR | new QR | operator fix)
```

- `WaitingForRegistration`: installation ID not yet available; scanner
  waits rather than failing (registration is normally seconds).
- `TailscaleRequired`: no VPN network active (§4).
- `Scanning`: camera active, awaiting a valid v1 decode. Arbitrary
  (non-Ackline) QR codes are ignored with quiet guidance, not errors.
- `Pairing`: `Emparejando…` — the existing `PairingProvisioner` runs.
  No P2A logic is duplicated; a thin presentation holder maps P2A
  typed failures to §7 copy.
- `Success`: server-confirmed provisioning only. Never shown on
  partial/local-only progress.
- Rotation may retain ViewModel state. **Process death persists no
  token** (§5); a consumed-but-unfinalized session surfaces as the
  consumed error → new-QR guidance, which is correct, not a bug.

---

## 7. Error UX — Canonical Spanish Mapping

Classification (normative). No message exposes HTTP codes, FID, session
id, token, file paths, Firebase terminology, or CLI flags.

**RETRY SAME QR** — transient, nothing burned:

```text
transport failure / Tailscale unavailable:
"No se pudo conectar con Hermes. Activa Tailscale y vuelve a
intentarlo."

temporary client/network issue:
"La conexión falló. Vuelve a intentarlo."

rate-limited (after waiting):
"Demasiados intentos. Espera un minuto y vuelve a intentarlo."
```

**NEW QR REQUIRED** — session unusable:

```text
expired:
"Este código caducó. Genera un nuevo QR en tu Mac."

consumed / replayed:
"Este código ya fue usado. Genera un nuevo QR en tu Mac."

invalid session/token, malformed QR, invalid response:
"Este código no es válido. Genera un nuevo QR en tu Mac."
```

**REPLACEMENT QR REQUIRED:**

```text
replace_required:
"Hermes ya está vinculado con otra instalación. Genera un nuevo QR de
reemplazo en tu Mac."
```

**OPERATOR / CONFIGURATION ISSUE:**

```text
server_misconfigured:
"Hermes no está listo para emparejar. Revisa su configuración en el
Mac."
(no retry loop; no technical detail on-device)
```

**LOCAL PROVISIONING ISSUE** — claim reached the server, local
finalization failed (retry may succeed; fresh QR if repeated):

```text
key import:
"No se pudo guardar la clave en el teléfono. Vuelve a intentarlo o
genera un nuevo QR."

ACK URL persistence:
"No se pudo guardar la dirección de Hermes. Vuelve a intentarlo."

FID confirmation:
"El emparejamiento no se pudo confirmar. Vuelve a intentarlo."
```

---

## 8. Re-pair — Server Intent Stays Server-Side

- Fresh-vs-replace intent lives in the Hermes session row, chosen by
  the operator command. The QR does not need replace intent for
  protocol correctness, and Ackline does not branch on intent:
  **Ackline simply claims.**
- Fresh claim against a differing stored Hermes FID → canonical
  `replace_required` → replacement-QR guidance (§7). Same-FID claim →
  success, baseline rewritten, flag cleared.
- `rePairRequired` clears **only** through the existing server-confirmed
  P2A path (`PairingProvisioner` → `confirmServerPairing`). No other
  writer is allowed.
- The normal-UX honor system is deleted (§9). No self-attestation tap
  remains in Ajustes or anywhere else.

---

## 9. Hardening (Authorized by This Spec)

P2B is authorized to make exactly these trust/logging changes — no broad
logging framework:

```text
- redact FidRePairState.toString() (today leaks full FID via data-class default)
- redact SetupUiState's installationId representation the same way
- delete: AcklineApplication.markRePairUpdated()
         FidRePairManager.markRePairUpdated()
         FidRePairStore.markUpdated()
         SetupState.onRePairUpdated()
         the SetupScreen honor-system button/path
         related obsolete tests (rewritten, not silently dropped)
- preserve the diagnostic FID "Copiar" affordance in Ajustes (support
  usefulness confirmed; display-only, no logging)
- every new QR/pairing model carries a redacted toString(); no token,
  session, FID, or key material in logs, including scanner decode logs
```

---

## 10. Visual / Accessibility — Phase 9 Bar Preserved

- Adaptive light/dark via the existing theme; muted teal system;
  `AcklineTopBar` and shared section/row language; no bottom nav; no
  unrelated redesign.
- All interactive elements ≥ 48 dp effective touch targets (existing
  `minimumInteractiveComponentSize` / `defaultMinSize` precedent).
- Large-font resilience (no clipped Spanish copy at 1.3×), TalkBack
  semantics and status announcements for scanner + progress + errors,
  scanner surface with content description and a non-visual status
  channel (state text, not preview pixels, carries meaning).
- Honest loading/error states per §6/§7; no skeleton-success, no
  decorative spinners masking failure.

---

## 11. P2C Boundary — Explicit Non-Goals

P2B implements **none** of: end-to-end FCM self-test, ACK-correlation
self-test, last-push timestamp, pending-ACK count, last successful ACK,
last reconciliation, diagnostics dashboard, key rotation, multi-device,
account system, alternative push transport. P2B surfaces only readiness
it can prove from local/pairing state (§2). A P2C navigation
placeholder is allowed only if routing needs it; no P2C behavior.
