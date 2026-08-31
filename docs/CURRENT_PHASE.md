# Current Phase

## Status

**IMPLEMENTED — PHYSICAL QA PASSED — FINAL REVIEW PENDING**

Phase: `5 — Application-Level E2EE`

Implementation branch: `5-application-e2ee`

Base branch: `dev`

---

## Objective

Protect Hermes alert contents from being visible to Firebase/FCM or intermediaries as plaintext.

Phase 5 changes the inbound alert transport from:

```text
Hermes/test sender
→ plaintext FCM data payload
→ Ackline parser
→ Room
```

to:

```text
Hermes/test sender
→ encrypt complete inner alert payload
→ FCM sees encrypted envelope only
→ Ackline receive boundary
→ authenticated decrypt
→ existing inner payload parser
→ Room
```

Phase 5 is the privacy gate that must pass before real sensitive Personal Admin alert contents are allowed through FCM.

---

## Baseline Already Proven

Phase 0:
- Android project builds/installs on the physical Oppo.
- Firebase/FID registration works.

Phase 1:
- FCM transport passed the physical reliability gate.
- Wi-Fi/mobile transitions do not require manually reopening Ackline.

Phase 2:
- Room is the device Inbox source of truth.
- Duplicate `notificationId` is harmless.
- Inbox/Detail/Setup baseline passed.

Phase 3:
- `Visto` is explicit only.
- Local acknowledgment is durable and idempotent.

Phase 4:
- Room v3 stores durable remote ACK state.
- `ackToken` is storage-only and does not enter the UI/domain `Alert`.
- local `Vista` remains immediate.
- WorkManager drains pending ACKs.
- private HTTPS/Tailscale ACK to Hermes works.
- offline ACK remains `PENDING`.
- reconnect automatically reaches `SYNCED`.
- startup recovery works after process restart.
- permanent `403` becomes terminal `ERROR` without undoing local `Vista`.
- duplicate remote ACK is idempotent.
- Phase 4 passed automated, physical, independent, and final GitHub review and was merged to `dev`.

Do not redesign those proven layers without a Phase 5 requirement.

---

## Phase 5 Security Question

This phase must answer:

> Can Ackline receive a real Hermes alert through FCM without Firebase/FCM-visible data containing the alert title, message, notification ID, or ACK credential, while still authenticating the payload and preserving the existing Inbox/ACK behavior?

Required answer: **yes**.

---

## Threat Model

Phase 5 protects alert content against plaintext visibility in the FCM transport path.

Protected inside ciphertext:

- `protocol`
- `notification_id`
- `level`
- `title`
- `message`
- `created_at`
- `ack_token` when present

FCM-visible metadata may still reveal:

- that a message was sent;
- approximate message size;
- target Firebase installation/device routing metadata;
- transport timing;
- Android FCM priority;
- encryption envelope version;
- non-secret key identifier (`kid`).

Phase 5 does **not** attempt to hide transport metadata.

Phase 5 does **not** encrypt Room at rest. After authenticated decryption, the normal alert is stored in app-private Room as today. `android:allowBackup="false"` remains required.

---

## Cryptographic Primitive

Use standard **AES-256-GCM**.

Android transformation:

```text
AES/GCM/NoPadding
```

Parameters:

```text
key:   32 bytes / 256 bits
nonce: 12 bytes / 96 bits
tag:   128 bits
```

Every encryption must use a fresh cryptographically random nonce.

Never reuse a nonce with the same key.

Do not implement AES/GCM manually.

---

## Versioned Encrypted Envelope

FCM remains a data-only message.

Phase 5 outer data payload:

```json
{
  "v": "1",
  "kid": "device-1",
  "nonce": "<base64url-no-padding>",
  "ciphertext": "<base64url-no-padding ciphertext+GCM-tag>"
}
```

These are the only Phase 5 alert-content fields that should be visible to FCM.

The outer envelope must not contain:

```text
notification_id
level
title
message
created_at
ack_token
```

`kid` is deployment metadata, not a secret.

---

## Inner Payload

After authenticated decryption, plaintext is compact UTF-8 JSON using the existing logical alert protocol:

```json
{
  "protocol": "1",
  "notification_id": "...",
  "level": "remember|important|urgent",
  "title": "...",
  "message": "...",
  "created_at": "2026-08-30T00:00:00Z",
  "ack_token": "..."
}
```

`ack_token` remains optional at the inner parser boundary for compatibility/testing, but production ACK-capable alerts require it.

Reuse the existing `IncomingAlertEnvelope` and `parseAcklinePayload()` after decryption rather than duplicating alert validation.

---

## Additional Authenticated Data

Canonical Phase 5 AAD:

```text
ackline-e2ee|v=1|kid=<kid>
```

Encoding: UTF-8.

Both Python sender and Android decryptor must construct the exact same bytes.

Changing `v` or `kid` must cause authentication/decryption failure.

---

## Encoding

Use URL-safe Base64 without padding for:

- nonce
- ciphertext

Decoder must reject malformed Base64 safely.

---

## `kid`

`kid` identifies the expected payload-encryption key.

Phase 5 MVP remains one user / one phone / one active key.

Requirements:

- `kid` is not secret;
- validate it as a bounded simple identifier;
- unknown `kid` fails closed;
- do not guess/fallback to another key;
- future rotation can add a new key without changing envelope v1.

Planning identifier shape:

```text
[A-Za-z0-9._-]{1,64}
```

Preflight must confirm the exact deployment mechanism.

---

## Mac Key

Preferred file:

```text
~/.hermes/secrets/hermes-notify.key
```

Contents:

```text
exactly 32 random bytes
```

Permissions:

```text
0600
```

The key must never be:

- committed;
- printed;
- pasted into prompts;
- passed as a normal command-line argument;
- logged;
- stored in `device.json` as plaintext;
- included in FCM.

Generate with a CSPRNG. Do not derive from a human password.

---

## Android Key Storage

The shared AES key must end in `AndroidKeyStore`.

Use an imported AES `SecretKey` protected by Android Keystore policy.

Target authorization:

```text
PURPOSE_DECRYPT
BLOCK_MODE_GCM
ENCRYPTION_PADDING_NONE
```

Do not require biometric/user authentication for every decrypt because background notifications must decrypt while the phone is locked.

Do not require StrongBox as an MVP prerequisite.

The raw key must not remain in SharedPreferences, Room, BuildConfig, resources, or normal app files after successful import.

---

## Key Provisioning

Preferred MVP direction:

```text
Mac 32-byte key file
→ local USB/ADB-only staging into Ackline app-private storage
→ Ackline imports key into AndroidKeyStore
→ staging file deleted
```

This avoids clipboard/paste, QR/camera dependencies, public-network provisioning, and hardcoded secrets.

The approved one-time provisioning command is:

```sh
adb shell -T \
  'run-as com.edu.ackline sh -c "umask 077; mkdir -p files; dd of=files/.e2ee_staging.bin bs=32 count=1 2>/dev/null"' \
  < ~/.hermes/secrets/hermes-notify.key
```

**Why this form:**

- `run-as` resolves the correct app sandbox instead of hardcoding `/data/data` vs `/data/user/0`.
- `mkdir -p files` handles a fresh installation where `files/` does not yet exist.
- `-T` disables PTY allocation, preventing interactive hang on some devices.
- Key bytes are supplied only via stdin.
- `dd bs=32 count=1` reads exactly the 32-byte AES-256 key and terminates deterministically instead of depending on EOF behavior.
- `umask 077` keeps staging permissions restrictive.
- No key material is printed or supplied in argv.
- Ackline deletes `.e2ee_staging.bin` after the import attempt.

The previous `adb exec-out` form was physically validated but could hang waiting for EOF on certain devices. The `dd`-based command terminates deterministically.

Normal process restart, device reboot, and app update/install-replace do not normally require re-provisioning because the Keystore entry persists. App uninstall removes the app Keystore namespace, so uninstall/reinstall requires re-provisioning.

Do not fall back to putting the key in source or `local.properties`.

---

## Key Import State

The app should distinguish:

```text
NOT_CONFIGURED
READY
```

A minimal Setup status such as:

```text
Cifrado: Listo
```

or:

```text
Cifrado: No configurado
```

is acceptable.

Never display/copy raw key material.

---

## Missing Key Behavior

If an encrypted FCM envelope arrives before the matching key is provisioned:

- fail closed;
- do not persist ciphertext as an Alert;
- do not display title/message;
- do not fabricate a plaintext fallback;
- do not crash;
- record only sanitized diagnostics where useful.

Phase 5 acceptance requires provisioning before real sensitive traffic.

Recovery of a missed alert because the key was absent belongs to Phase 7 reconciliation, not polling here.

---

## Plaintext FCM Behavior After Phase 5

The external FCM receive path becomes encrypted-only.

A legacy plaintext data payload containing:

```text
protocol
notification_id
level
title
message
created_at
```

must not be accepted as a normal incoming alert after Phase 5.

The inner plaintext parser remains reusable **after decryption**.

Do not add an implicit decrypt-failed → plaintext fallback.

---

## Receive Flow

```text
FirebaseMessagingService.onMessageReceived()
→ parse encrypted outer envelope
→ validate v/kid/nonce/ciphertext bounds
→ resolve AndroidKeyStore key
→ AES-GCM authenticated decrypt with canonical AAD
→ decode UTF-8 inner JSON
→ map to existing inner payload fields
→ existing parseAcklinePayload()
→ Room INSERT IGNORE
→ native notification
```

Persist-before-presentation remains unchanged.

Decryption/parsing stays short and bounded in the existing FCM worker-thread callback.

No WorkManager is needed for decryption.

---

## Authentication Failure

These fail safely:

- wrong key;
- tampered ciphertext/tag;
- tampered nonce;
- tampered `v`/`kid` through AAD;
- malformed Base64;
- wrong nonce length;
- unknown key ID;
- invalid inner UTF-8/JSON;
- invalid inner alert protocol.

Result:

```text
no Room insert
no native alert
no crash
no plaintext logging
```

Do not create detailed user-visible cryptographic error oracles.

---

## Payload Size

Encryption + tag + Base64 add overhead to the FCM payload.

Phase 5 must define a conservative maximum inner plaintext size and reject oversize before FCM send.

Planning target:

```text
~2500 UTF-8 bytes maximum compact inner JSON
```

Preflight must calculate/confirm a safe bound against the actual four-field envelope and FCM limit.

---

## Sender Tooling

Phase 5 may update the development sender so fake/non-sensitive alerts are encrypted before FCM.

Preferred local inputs:

```text
ACKLINE_E2EE_KEY_FILE
ACKLINE_E2EE_KID
ACKLINE_ACK_TOKEN
```

Rules:

- key bytes are read from file, never printed;
- `ACKLINE_ACK_TOKEN`, when present, goes inside encrypted inner JSON;
- sender output may print non-secret notification metadata as today;
- sender must never print key or plaintext payload;
- no production Hermes outbox integration yet.

Phase 6 still owns routing the actual Hermes persistent outbox through the final encrypted sender.

---

## Python Crypto

Preferred:

```text
cryptography.hazmat.primitives.ciphers.aead.AESGCM
```

Use the current environment only if its `cryptography` and Python versions are appropriate.

Do not implement AES-GCM directly.

---

## Android Crypto Dependencies

Prefer platform JCA/JCE + AndroidKeyStore:

```text
Cipher
GCMParameterSpec
KeyStore
KeyProtection
SecretKeySpec during import only
```

Do not add a crypto SDK unless preflight finds a concrete blocker.

No Tink dependency by default.

---

## Room

Phase 5 should not require a Room migration.

Expected DB version remains `3`.

If implementation proposes v4 solely for crypto bookkeeping, stop and justify it before changing schema.

---

## ACK Compatibility

Phase 4 remote ACK remains unchanged.

The transport difference is only:

```text
ackToken plaintext in FCM
```

becomes:

```text
ackToken inside encrypted inner payload
```

After decryption:

```text
IncomingAlertEnvelope.ackToken
→ AlertEntity.ackToken
→ PendingAcknowledgment
→ AckRemoteClient
```

No change to:

```text
POST /ack/<notification_id>
X-Ack-Token
```

---

## No Sensitive Production Traffic Yet

During implementation and QA:

- fake/non-sensitive contents only;
- dedicated test key is acceptable;
- never paste real key material into AI/cloud tools;
- never expose real `ack_token` values in reports/screenshots.

Only after Phase 5 final PASS may real sensitive Hermes payloads be considered for Phase 6 integration.

---

## Mandatory Tests

### Outer envelope
- valid v1;
- missing/unsupported fields;
- invalid `kid`;
- malformed Base64;
- nonce != 12 bytes;
- oversize rejection.

### Crypto
- known AES-256-GCM vector;
- Python-generated vector decrypts through Android/JCA;
- wrong key;
- tampered ciphertext/tag;
- wrong nonce;
- changed AAD;
- no failed payload reaches inner parser/Room.

### Key storage
- 32-byte key imports into AndroidKeyStore;
- key decrypts;
- key material is not exportable through normal `getEncoded()` after import;
- wrong-sized import rejected;
- staging file removed after successful import;
- app restart preserves key.

### Inner protocol
- encrypted payload reuses existing parser;
- `ack_token` reaches storage boundary;
- plaintext FCM rejected;
- duplicate encrypted delivery remains INSERT IGNORE.

### Sender
- outer data contains only `v`, `kid`, `nonce`, `ciphertext`;
- no private inner field outside ciphertext;
- nonce fresh per encryption;
- bad key size rejected;
- oversize rejected;
- secrets never printed.

---

## Cross-Language Test Vector

Create at least one deterministic test vector with:

- fixed 32-byte **test** key;
- fixed 12-byte **test** nonce;
- fixed `kid`;
- fixed AAD;
- fixed fake compact inner JSON;
- expected Base64URL ciphertext.

The fixed nonce is **test-only**.

Production sender always generates a fresh random nonce.

---

## Physical QA

Mandatory on Oppo, fake/non-sensitive only.

### Provisioning
- stage/import test key;
- verify Setup encryption ready;
- restart app;
- key remains ready.

### Valid encrypted alert
- encrypted IMPORTANT arrives;
- decrypts;
- persists;
- native notification and Inbox show expected local plaintext.

### ACK regression
- encrypted payload includes ACK credential;
- explicit `Visto`;
- Phase 4 remote ACK still reaches `SYNCED`.

### Tamper
- modified ciphertext/tag is rejected;
- no row/tray.

### Wrong key
- sender uses wrong key with expected `kid`;
- reject safely.

### Plaintext rejection
- legacy Phase 4 plaintext FCM payload creates no row/tray.

### Background
- encrypted alert arrives/decrypts while Ackline is backgrounded.

No full Phase 1 matrix unless a regression appears.

---

## Explicit Out of Scope

Phase 5 does **not** implement:

- production Hermes outbox → FCM integration;
- ntfy removal;
- recovery/reconciliation;
- periodic sync;
- multi-device key management;
- automatic key rotation service;
- accounts/login;
- cloud key server;
- QR/camera pairing unless ADB provisioning is impossible and separately approved;
- Room/database encryption;
- biometric-gated notification decryption;
- public backend;
- UI redesign;
- Firebase Auth/Firestore/Functions;
- chat with Hermes.

---

## Acceptance Gate

```text
AES-256-GCM standard implementation          PASS
32-byte key generated/stored safely          PASS
AndroidKeyStore import                       PASS
FCM outer envelope contains no private data  PASS
valid encrypted alert decrypts               PASS
tampered payload rejected                    PASS
wrong key rejected                           PASS
AAD tampering rejected                       PASS
plaintext FCM alert rejected                 PASS
duplicate delivery still harmless            PASS
Phase 4 ACK path still works                 PASS
key survives app restart                     PASS
no key/token/plaintext leakage in logs       PASS
physical Oppo QA                             PASS
independent review                           PASS
final GitHub review                          PASS
```

Only after this gate may Phase 6 use real sensitive Hermes payloads.

---

## Suggested Commit

```text
feat: encrypt notification payloads end to end
```

User owns commit, push, and merge.
