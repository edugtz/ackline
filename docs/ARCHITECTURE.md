# Ackline — Architecture

## 1. Architecture Goal

Ackline remains a small single-module Android client.

Reliability, explicit state, privacy, and low maintenance matter more than framework sophistication.

Phase 5 adds one security boundary around the already-proven inbound FCM payload.

---

## 2. Source-of-Truth Rules

1. Hermes notification state remains authoritative server-side.
2. Room remains authoritative for device Inbox/local ACK/sync state.
3. FCM remains transport only.
4. Android tray remains presentation only.
5. WorkManager remains ACK deferred execution only.
6. AndroidKeyStore becomes authoritative for the device-side payload-decryption key reference.
7. The Mac secrets file is authoritative for sender-side shared payload-encryption key material.
8. `kid` is routing/version metadata, not secret material.
9. Failed authentication never becomes an Alert.
10. Decryption must complete before any private alert field reaches normal app layers.

---

## 3. Runtime After Phase 5

```text
Hermes / development sender
        │
        │ plaintext inner alert exists only locally
        ▼
   AES-256-GCM encrypt
        │
        ▼
FCM data envelope:
v / kid / nonce / ciphertext
        │
        ▼
FirebaseMessagingService
        │
        ▼
EncryptedPushEnvelope
        │
        ▼
PayloadCrypto
        │
 AndroidKeyStore SecretKey
        │
        ▼
authenticated plaintext
        │
        ▼
InnerPayloadDecoder
        │
        ▼
existing parseAcklinePayload()
        │
        ▼
IncomingAlertEnvelope
        │
        ▼
AlertRepository
        │
        ▼
Room
  ┌─────┴───────────────┐
  ▼                     ▼
Inbox/Detail      native notification
  │                     │
  └──── explicit Visto ─┘
             │
             ▼
 existing Phase 4 ACK sync
```

---

## 4. Privacy Boundary

Before successful AES-GCM authentication, private alert fields must not enter:

- Room;
- native notifications;
- Inbox;
- logs;
- ACK sync.

After successful authentication, the existing application flow resumes.

---

## 5. FCM Envelope v1

Only these four data keys:

```text
v
kid
nonce
ciphertext
```

Example:

```json
{
  "v": "1",
  "kid": "ackline-main",
  "nonce": "base64url",
  "ciphertext": "base64url"
}
```

FCM does not need the decrypted `notification_id`.

---

## 6. Encrypted Inner Protocol

Existing logical payload is encrypted as UTF-8 JSON:

```text
protocol
notification_id
level
title
message
created_at
ack_token?
```

Encryption-envelope version and inner-alert protocol version are separate.

---

## 7. AES-GCM Parameters

```text
algorithm: AES
key size: 256 bits
mode: GCM
padding: none
nonce: 96 bits
tag: 128 bits
```

Android:

```text
AES/GCM/NoPadding
```

Python:

```text
cryptography AESGCM
```

No tag truncation.

---

## 8. Nonce Rule

Generate a fresh cryptographically random 12-byte nonce for every encryption.

Nonce is public and carried in the envelope.

Nonce reuse with the same key is forbidden.

---

## 9. AAD Rule

Canonical:

```text
ackline-e2ee|v=1|kid=<kid>
```

UTF-8 bytes.

AAD binds envelope version and key identifier.

Changing either causes authentication failure.

---

## 10. Base64URL Rule

Nonce and ciphertext use URL-safe Base64 without padding.

Transport parser validates and bounds decoded size.

---

## 11. Key Identifier Rule

`kid` is validated, bounded, exact-match metadata.

Planning form:

```text
[A-Za-z0-9._-]{1,64}
```

Unknown `kid` is rejected.

Never try every key or silently fall back.

---

## 12. Mac Key Storage

One 32-byte random shared key:

```text
~/.hermes/secrets/hermes-notify.key
```

Mode:

```text
0600
```

Key bytes are never committed or logged.

Phase 6 may later centralize device metadata, but Phase 5 does not build multi-device management.

---

## 13. Android Key Storage

The same AES key is imported into AndroidKeyStore.

Keystore alias derives from expected `kid`.

The app obtains a `SecretKey` reference for decryption.

Raw bytes are needed only during import and discarded afterward.

---

## 14. Keystore Authorization

Least-privilege target:

```text
PURPOSE_DECRYPT
GCM
NoPadding
```

Do not require per-use biometric authentication because background FCM decryption must work while locked.

StrongBox is not an MVP requirement.

---

## 15. Provisioning Boundary

Preferred:

```text
Mac secret file
→ USB/ADB stdin
→ app-private temporary file
→ AndroidKeyStore import
→ temporary file deletion
```

Provisioning is local deployment, not runtime transport.

No cloud key exchange.

No key in FCM.

---

## 16. Setup Diagnostics

Setup may expose encryption readiness and optional non-secret `kid`.

Never expose or copy key bytes.

---

## 17. EncryptedPushEnvelope

App-owned model independent of Firebase classes.

Conceptual fields:

```text
version
kid
nonce
ciphertext
```

Firebase service maps `RemoteMessage.data` into it.

---

## 18. PayloadCrypto

Security-layer component.

Input: `EncryptedPushEnvelope`.

Output: authenticated plaintext bytes or safe failure.

No Room, network, notifications, or ACK responsibilities.

---

## 19. InnerPayloadDecoder

Input:

```text
authenticated UTF-8 plaintext JSON
```

Output:

```text
Map<String, String>
```

Then call existing `parseAcklinePayload()`.

Do not maintain two independent alert validators.

---

## 20. FirebaseMessagingService

Phase 5:

```text
RemoteMessage.data
→ encrypted envelope only
→ decrypt
→ existing parser
→ persist
→ notify
```

Legacy plaintext top-level alert fields are not accepted.

---

## 21. Failure Semantics

Fail closed for:

```text
malformed envelope
unsupported version
unknown kid
missing key
malformed Base64URL
invalid nonce length
GCM auth failure
invalid UTF-8/JSON
invalid inner protocol
```

No Alert is created.

Expected failures should not dump sensitive stack traces.

---

## 22. Local Persistence

Phase 5 does not encrypt Room.

After successful decryption, Room stores alert plaintext as before.

This is intentional MVP scope.

Database version remains expected at v3.

---

## 23. ACK Token Boundary

Before:

```text
FCM plaintext ack_token
→ Room storage-only
```

After:

```text
encrypted inner ack_token
→ authenticated decrypt
→ Room storage-only
```

It still never enters `Alert`/Compose.

---

## 24. ACK Transport Boundary

Unchanged:

```text
Room PENDING
→ WorkManager
→ HTTPS/Tailscale
→ POST /ack/<notification_id>
→ X-Ack-Token
→ Hermes
```

E2EE is inbound FCM protection, not ACK redesign.

---

## 25. Sender Architecture

Development sender:

```text
build inner alert
→ compact JSON
→ PayloadCrypto.encrypt
→ encrypted data map
→ Firebase Admin SDK
```

Level remains locally available to choose FCM priority but is not a plaintext FCM data field.

---

## 26. Metadata Leakage

E2EE does not hide:

- timing;
- destination;
- message size;
- FCM priority;
- envelope version;
- `kid`.

Do not claim traffic-analysis resistance.

---

## 27. Payload Size Boundary

Encryption adds GCM tag, nonce field, and Base64 expansion.

Sender enforces a conservative inner JSON limit.

Android bounds ciphertext size before decryption.

---

## 28. Cross-Language Compatibility

Python encryption and Android/JCA decryption are one protocol.

A deterministic test vector is mandatory to catch disagreement around:

- Base64URL;
- AAD;
- tag placement;
- nonce size;
- plaintext bytes.

---

## 29. Key Rotation Future-Proofing

Phase 5 has one active key.

`kid` allows future new-key rotation without changing envelope v1.

Do not implement rotation service now.

---

## 30. Plaintext Downgrade Resistance

Once Phase 5 is enabled, sending the old plaintext FCM map cannot bypass encryption.

Receive boundary accepts encrypted envelope v1 only.

---

## 31. No User Authentication Gate

Do not configure key use to require unlock/biometric per message.

That would break screen-off/background delivery and Phase 1 reliability guarantees.

---

## 32. Logs

Never log:

```text
key
ack token
plaintext JSON
title
message
ciphertext
FID
service-account contents
```

Use only bounded sanitized reason categories.

---

## 33. Dependencies

Android:

platform cryptography + AndroidKeyStore.

Python:

`cryptography` AESGCM.

No broad security framework is used; platform cryptography and AndroidKeyStore are sufficient.

---

## 34. Testing Layers

Pure unit:
- encoding;
- AAD;
- envelope parser;
- JSON;
- sender outer privacy.

Cross-language:
- Python encrypt;
- Android/JCA decrypt.

Instrumented:
- actual AndroidKeyStore import/decrypt/non-exportability.

Physical:
- provisioning;
- background encrypted delivery;
- tamper rejection;
- wrong-key rejection;
- plaintext rejection;
- Phase 4 ACK regression.

---

## 35. Future Phase Boundaries

Phase 6:

```text
Hermes persistent outbox
→ production encrypted FCM sender
```

Phase 7:

```text
recovery/reconciliation
```

Do not move those responsibilities into Phase 5.

---

## 36. Architecture Acceptance

Phase 5 architecture is accepted when:

- FCM-visible alert data is encrypted-only;
- raw shared key exists only in approved Mac secret storage and transient Android import memory/file;
- Android runtime key lives in AndroidKeyStore;
- AES-GCM authentication gates the existing parser;
- no plaintext downgrade exists;
- existing Room/ACK architecture remains unchanged;
- no unnecessary crypto/framework architecture is introduced.
