# Phase 5 — Application-Level E2EE Implementation Plan

## 1. Status

**IMPLEMENTED — PHYSICAL QA PASSED — FINAL REVIEW PASSED — READY TO MERGE**

Branch: `5-application-e2ee`

Base: `dev`

Implementation and automated validation are complete. Physical QA on the Oppo Find X9 Pro passed all 19 test cases. The first Phase 5 commit was pushed, and final independent/GitHub review passed. This branch is ready to merge after this documentation cleanup commit is remotely verified.

---

## 2. Goal

Make the FCM transport payload opaque to Firebase/FCM.

Before:

```text
FCM data:
protocol
notification_id
level
title
message
created_at
ack_token
```

After:

```text
FCM data:
v
kid
nonce
ciphertext
```

After authenticated decryption, the existing Phase 4 inner alert model continues unchanged.

---

## 3. Non-Negotiable Invariants

1. No title/message/notification ID/ACK token appears in FCM-visible `data`.
2. AES-GCM authentication failure produces no alert.
3. No plaintext fallback on the production receive path.
4. The key is not stored in source, BuildConfig, Room, SharedPreferences, logs, or prompts.
5. The Mac key file is exactly 32 random bytes.
6. Android stores the imported shared key in AndroidKeyStore.
7. Every production encryption uses a fresh 12-byte random nonce.
8. GCM authentication tag is 128 bits.
9. `v` and `kid` are authenticated through canonical AAD.
10. Unknown `kid` fails closed.
11. Existing Room dedupe remains by decrypted `notificationId`.
12. Existing Phase 4 ACK sync still receives the decrypted `ackToken`.
13. No Room migration was required.
14. No real personal alert content during Phase 5 QA.
15. No custom cryptographic implementation.

---

## 4. Required Repository Discovery

Read:

```text
docs/CURRENT_PHASE.md
docs/IMPLEMENTATION_PLAN.md
docs/ARCHITECTURE.md
docs/PROJECT_SPEC.md
docs/ACCEPTANCE_CRITERIA.md
docs/MVP_PHASES.md
AGENTS.md
```

Inspect exact current paths for:

```text
app/build.gradle.kts
gradle/libs.versions.toml
app/src/main/AndroidManifest.xml

AcklineApplication.kt
AcklineMessagingService.kt
IncomingAlertEnvelope.kt
AlertRepository.kt
AlertEntity.kt
AcklineDatabase.kt
AlertDao.kt

ack/AckRemoteClient.kt
ack/AckSyncRunner.kt
ack/AckSyncScheduler.kt
ack/AckSyncWorker.kt

current Setup implementation
all payload/parser tests
all relevant instrumented tests

tools/firebase_sender.py
tools/test_firebase_sender.py
```

Do not assume documentation path guesses are exact.

---

## 5. Local Environment Discovery

Inspect metadata only for:

```text
~/.hermes/secrets/
```

Determine whether:

```text
~/.hermes/secrets/hermes-notify.key
```

already exists.

Never print/read key contents into reports.

If present, verify only:

- size;
- mode;
- ownership if useful.

Also inspect which Python environment is actually used for the FCM sender and whether `cryptography`/`AESGCM` is available.

Do not modify the real Hermes outbox in Phase 5.

---

## 6. Crypto Protocol v1

Outer FCM data:

```text
v
kid
nonce
ciphertext
```

Exact semantics:

```text
v = "1"
kid = configured simple identifier
nonce = base64url-no-padding(12 random bytes)
ciphertext = base64url-no-padding(AES-GCM ciphertext || 16-byte tag)
```

No separate `tag` field.

---

## 7. Inner JSON

Build compact UTF-8 JSON from existing alert data:

```json
{
  "protocol": "1",
  "notification_id": "...",
  "level": "...",
  "title": "...",
  "message": "...",
  "created_at": "...",
  "ack_token": "..."
}
```

Omit `ack_token` when absent.

Do not duplicate validation rules already owned by `parseAcklinePayload()`.

---

## 8. AAD

Canonical bytes:

```text
ackline-e2ee|v=1|kid=<kid>
```

UTF-8.

Changing `v` or `kid` must fail authentication.

---

## 9. Base64URL

Use RFC 4648 URL-safe alphabet without `=` padding.

Reject malformed input.

Nonce decoded length must equal exactly `12`.

---

## 10. AndroidKeyStore Alias

Implemented alias:

```text
ackline.payload.ackline-main
```

The MVP alias is fixed as shown above.

Key lookup:

```text
expected configured kid
→ exact Keystore alias
```

Do not iterate aliases and guess.

---

## 11. Android Key Import

Preferred implementation:

1. raw 32-byte key is staged into app-private storage;
2. app reads exact bytes;
3. validates length == 32;
4. temporarily wraps as `SecretKeySpec(bytes, "AES")`;
5. imports via `KeyStore.setEntry(...)`;
6. `KeyProtection` restricts:
   - `PURPOSE_DECRYPT`
   - `BLOCK_MODE_GCM`
   - `ENCRYPTION_PADDING_NONE`
7. wipe temporary byte array best-effort;
8. delete staging file;
9. future decrypt obtains `SecretKey` from AndroidKeyStore.

Direct import was verified on the target device/API.

---

## 12. Key Provisioning Transport

Preferred MVP: USB/ADB-only.

Requirements:

- key bytes do not appear in shell command arguments/history;
- no terminal output of key bytes;
- staging destination is app-private;
- restrictive permissions;
- staging file removed after successful import;
- failure behavior documented.

The approved one-time provisioning command is:

```sh
adb exec-out run-as com.edu.ackline sh -c \
  'umask 077; cat > /data/data/com.edu.ackline/files/.e2ee_staging.bin' \
  < ~/.hermes/secrets/hermes-notify.key
```

Key bytes travel through stdin, not command-line arguments, and are never printed. The staging file is app-private and uses explicit `umask 077`. Ackline imports it into AndroidKeyStore, deletes the staging file after the import attempt, and never silently replaces an existing Keystore alias.

Normal process restart, device reboot, and app update/install-replace do not normally require re-provisioning because the Keystore entry persists. App uninstall removes the app Keystore namespace, so uninstall/reinstall requires re-provisioning.

Do not put the key in `local.properties`.

---

## 13. Import Trigger

Preferred minimal behavior:

```text
Ackline startup or explicit Setup action
→ if approved staging file exists
→ import once
→ delete staging file
```

Do not rewrite an existing alias silently.

If startup import implies main-thread/blocking issues, use the smallest bounded background path.

---

## 14. Setup Status

Minimal product addition:

```text
Cifrado: Listo
```

or:

```text
Cifrado: No configurado
```

Optional `kid` display only if useful.

No raw key, no key copy button, no broad Setup redesign.

---

## 15. PayloadCrypto

Expected narrow component:

```text
security/PayloadCrypto.kt
```

Responsibilities:

- retrieve expected Keystore key;
- decode nonce/ciphertext;
- construct AAD;
- AES/GCM decrypt;
- return plaintext or small failure result.

It must not know Room, network, FCM classes, notifications, or ACK logic.

---

## 16. Encrypted Envelope Parser

Expected concept:

```text
push/EncryptedPushEnvelope.kt
```

Responsibilities:

- require `v`, `kid`, `nonce`, `ciphertext`;
- validate version/kid/bounds;
- Base64URL decode safely.

Firebase types remain at the messaging boundary.

---

## 17. Inner JSON Decoder

After decrypt:

```text
plaintext bytes
→ UTF-8 JSON object
→ Map<String,String>
→ existing parseAcklinePayload()
```

Prefer a simple existing/platform JSON mechanism if sufficient.

Do not add a serialization framework solely for this object without evidence.

---

## 18. Messaging Service

Replace the plaintext FCM path with encrypted-only receive behavior:

```text
onMessageReceived(remoteMessage)
→ EncryptedPushEnvelope.parse(remoteMessage.data)
→ PayloadCrypto.decrypt(...)
→ InnerPayloadDecoder.decode(...)
→ parseAcklinePayload(...)
→ repository.insertIncoming(...)
→ notify only when INSERTED
```

Persist-before-notify remains.

No plaintext fallback.

No WorkManager/coroutine solely for decryption.

---

## 19. Failure Taxonomy

Small sanitized categories only, for example:

```text
malformed_envelope
unsupported_version
unknown_kid
key_not_configured
authentication_failed
invalid_inner_payload
oversize
```

No detailed crypto oracle in UI.

No raw exception/payload logging.

---

## 20. AES-GCM Android

Use:

```kotlin
Cipher.getInstance("AES/GCM/NoPadding")
GCMParameterSpec(128, nonce)
cipher.init(Cipher.DECRYPT_MODE, key, spec)
cipher.updateAAD(aad)
cipher.doFinal(ciphertextAndTag)
```

Catch authentication failure and normalize safely.

Do not compare tags manually.

---

## 21. Python E2EE Helper

Prefer:

```text
tools/payload_crypto.py
```

Use:

```python
cryptography.hazmat.primitives.ciphers.aead.AESGCM
```

Responsibilities:

- load exactly 32-byte key file;
- validate `kid`;
- generate fresh 12-byte nonce;
- build AAD;
- encrypt compact UTF-8 JSON;
- Base64URL encode without padding;
- return outer `dict[str, str]`.

No Firebase imports in the crypto helper.

---

## 22. Development Sender

`tools/firebase_sender.py` should:

1. build existing inner data;
2. keep optional `ack_token` inside inner payload;
3. encrypt using Phase 5 helper;
4. send only encrypted outer data;
5. preserve level-based FCM priority selection.

Preferred configuration:

```text
ACKLINE_E2EE_KEY_FILE
ACKLINE_E2EE_KID
```

If key/kid missing, fail before FCM send.

Do not silently send plaintext.

---

## 23. Mac Key Generation

The implemented helper is `tools/generate_e2ee_key.py`; it safely creates a new key file without overwrite.

Requirements:

- 32 CSPRNG bytes;
- restrictive permissions;
- refuse overwrite;
- never stdout key material.

If the real key file exists, do not regenerate it.

---

## 24. Key File Validation

Reject size != 32 bytes.

No truncation.

No hashing arbitrary contents into a key.

No password derivation.

---

## 25. `kid` Configuration

Same `kid` must be used by:

- sender;
- Android expected configuration;
- Android Keystore alias mapping.

`kid` is non-secret.

The implemented deployment configuration uses the fixed non-secret MVP `kid` `ackline-main`.

Never put key bytes in BuildConfig.

---

## 26. Payload Size

The implemented conservative cap is:

Implemented target:

```text
MAX_INNER_PAYLOAD_BYTES = 2500
```

Sender rejects larger compact UTF-8 JSON before encryption/send.

Android applies an outer ciphertext bound too.

---

## 27. Cross-Language Vector

Create deterministic test fixture:

```text
fixed TEST key
fixed TEST nonce
fixed kid
fixed AAD
fixed fake compact JSON
expected ciphertext+tag Base64URL
```

Python verifies encryption result.

Android/JCA verifies decryption.

Fixed nonce is tests only.

---

## 28. Keystore Tests

Instrumented tests should verify:

- import succeeds;
- key is retrievable as a `SecretKey` reference;
- `getEncoded()` does not expose raw material on target path;
- known ciphertext decrypts;
- wrong-sized import rejected;
- test alias cleanup;
- staging file cleanup;
- key survives process/app restart where practical.

Use dedicated test aliases, never the real user alias.

---

## 29. Crypto Tests

Cover:

- envelope validation;
- Base64URL;
- AAD;
- `kid`;
- valid deterministic decrypt;
- wrong key;
- tampered ciphertext;
- tampered tag;
- wrong nonce;
- changed AAD;
- invalid inner JSON;
- plaintext FCM rejection.

---

## 30. Duplicate Delivery

Encrypt same logical alert twice with same inner `notification_id` and different fresh nonces.

After decrypt, Room `INSERT IGNORE` still yields one row and no repost.

---

## 31. ACK Regression

Valid encrypted payload containing `ack_token` must still flow:

```text
receive
→ decrypt
→ persist storage-only token
→ explicit Visto
→ PENDING
→ WorkManager
→ Hermes ACK
→ SYNCED
```

Do not modify ACK HTTP protocol.

---

## 32. Logging

Forbidden:

```text
key
Base64 key
plaintext JSON
title/message
ACK token
ciphertext dump
FID
service-account contents
```

Allowed bounded categories:

```text
Encrypted payload rejected: authentication_failed
Encrypted payload rejected: key_not_configured
```

Avoid expected-failure stack traces.

---

## 33. Room / Data Model

Expected:

```text
AcklineDatabase version = 3
```

No new columns for nonce/ciphertext/kid/errors.

If schema v4 is proposed, stop and justify before implementation.

---

## 34. App Backup / Storage

Keep `android:allowBackup="false"`.

No external-storage key persistence.

Temporary provisioning file must be app-private and removed after successful import.

---

## 35. WorkManager / Tailscale

No Phase 5 architecture change expected.

ACK worker remains unaware of E2EE.

No Tailscale Serve/Funnel changes.

---

## 36. Physical Provisioning QA

1. install Phase 5 APK;
2. create/verify test key file;
3. stage via approved USB/ADB method without printing;
4. import;
5. verify staging file removed;
6. verify Setup ready;
7. restart app;
8. verify still ready.

---

## 37. Physical Encrypted Delivery QA

Send fake encrypted alert.

Expected:

```text
FCM accepted
→ background service decrypts
→ Room row
→ native notification
→ Inbox row
```

---

## 38. Physical Tamper QA

Flip ciphertext/tag byte after encryption.

Expected:

```text
FCM may deliver envelope
→ Ackline rejects
→ no row
→ no tray
→ no crash
```

---

## 39. Physical Wrong-Key QA

Encrypt fake payload with another 32-byte test key but same expected `kid`.

Expected authentication failure and no alert.

---

## 40. Physical Plaintext Rejection QA

Send controlled legacy Phase 4 plaintext payload.

Expected no row/no tray.

Do not keep plaintext as normal sender behavior.

---

## 41. Background QA

With key provisioned:

- background/remove Ackline from Recents without Force Stop;
- send encrypted IMPORTANT fake alert;
- verify decrypt/notification without manually opening app.

No full Phase 1 matrix unless regression appears.

---

## 42. Files Likely to Modify

Likely:

```text
AcklineApplication.kt
AcklineMessagingService.kt
IncomingAlertEnvelope.kt
current Setup implementation
tools/firebase_sender.py
tools/test_firebase_sender.py
docs/CURRENT_PHASE.md
docs/IMPLEMENTATION_PLAN.md
docs/ARCHITECTURE.md
```

Possibly build config for non-secret `kid`.

The final implementation files are listed below.

---

## 43. Files Likely to Create

Conceptually:

```text
security/PayloadCrypto.kt
security/PayloadKeyStore.kt
push/EncryptedPushEnvelope.kt
push/InnerPayloadDecoder.kt

corresponding tests

tools/payload_crypto.py
tools/test_payload_crypto.py
```

Do not over-split.

---

## 44. Files Not to Touch Without Evidence

Avoid changes to:

```text
Room schema/database version
AlertDao ACK SQL
AckSyncRunner
AckSyncScheduler
AckSyncWorker
HttpsAckRemoteClient
notification channels
Inbox visual design
Detail visual design
Hermes ack_server.py
Tailscale configuration
Hermes Personal Admin business logic
```

Phase 6 owns production outbox integration.

---

## 45. Dependency Policy

Android: prefer no new runtime crypto dependency.

Python: `cryptography` AESGCM is expected if available/appropriately versioned.

Do not add by default:

```text
Tink
BouncyCastle solely for AES-GCM
Retrofit
Hilt
Koin
Firebase Auth
Firestore
```

---

## 46. Implementation Order

Implementation sequence completed as follows:

1. create Phase 5 branch from current `dev`;
2. place approved docs;
3. define constants/AAD/kid/bounds;
4. implement Python crypto helper + deterministic vector;
5. implement Android envelope/Base64/AAD;
6. implement AndroidKeyStore import/storage;
7. implement PayloadCrypto;
8. implement inner JSON decoder → existing parser;
9. switch FCM receive to encrypted-only;
10. update development sender to encrypted-only;
11. add minimal Setup readiness;
12. add unit tests;
13. add Keystore instrumented tests;
14. automated validation;
15. provision Oppo test key;
16. valid encrypted delivery QA;
17. tamper/wrong-key/plaintext rejection;
18. Phase 4 ACK regression;
19. background receive regression;
20. independent review passed;
21. first Phase 5 commit pushed;
22. final ChatGPT/GitHub review passed;
23. merge after this documentation cleanup commit is remotely verified.

---

## 47. Automated Validation

Expected:

```bash
./gradlew clean kspDebugKotlin lintDebug testDebugUnitTest assembleDebug
./gradlew connectedDebugAndroidTest

python3 -m py_compile tools/firebase_sender.py
python3 -m py_compile tools/payload_crypto.py
python3 -m unittest discover -s tools -p "test_*.py"

git diff --check
```

The validation commands are listed above and were executed for this phase.

---

## 48. Stop Conditions

Stop rather than improvise if:

- AndroidKeyStore AES import fails on target device;
- provisioning would require key in shell args/source/clipboard;
- Python crypto environment is missing/incompatible and changes would affect unrelated Hermes runtime;
- envelope cannot fit safely within FCM bounds;
- crypto requires plaintext fallback;
- Room v4 is proposed without clear product need;
- real personal payloads are needed for testing;
- Phase 6 changes become necessary;
- multi-device/rotation infrastructure becomes necessary;
- tests expose real key material.

---

## 49. Definition of Done

```text
crypto protocol fixed                         PASS
Mac key handling safe                         PASS
AndroidKeyStore import                        PASS
encrypted-only FCM receive                    PASS
valid decrypt                                 PASS
tamper rejection                              PASS
wrong-key rejection                           PASS
AAD binding                                   PASS
plaintext rejection                           PASS
sender outer payload privacy                  PASS
duplicate idempotency                         PASS
Phase 4 ACK regression                        PASS
physical background receive                   PASS
no secret leakage                             PASS
automated validation                          PASS
independent review                            PASS
GitHub review                                 PASS
```

Suggested commit:

```text
feat: encrypt notification payloads end to end
```
