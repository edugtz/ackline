# Phase 5 — Physical QA Results

**Date:** 30 August 2026

**Device:** Oppo Find X9 Pro

**Branch:** `5-application-e2ee`

---

## Status

**PHYSICAL QA PASSED — FINAL REVIEW PENDING**

---

## 1. Physical QA Matrix

| # | Test | Result |
|---|------|--------|
| 1 | Safe 32-byte key generation | PASS |
| 2 | `adb` stdin provisioning (`dd` form) | PASS |
| 3 | AndroidKeyStore import / readiness | PASS |
| 4 | Staging raw key cleanup (`.e2ee_staging.bin` removed) | PASS |
| 5 | Valid encrypted FCM delivery | PASS |
| 6 | Encrypted duplicate logical ID dedupe (`INSERT IGNORE`) | PASS |
| 7 | Legacy plaintext FCM rejection | PASS |
| 8 | Tampered ciphertext rejection | PASS |
| 9 | Wrong AES key rejection | PASS |
| 10 | Valid delivery after failed decrypts | PASS |
| 11 | 3 consecutive valid HIGH deliveries | PASS |
| 12 | Encrypted `ack_token` persistence | PASS |
| 13 | Explicit local `Visto` | PASS |
| 14 | Durable WorkManager ACK | PASS |
| 15 | Hermes authenticated ACK | PASS |
| 16 | Ackline final `SYNCED` | PASS |
| 17 | Process-death encrypted delivery | PASS |
| 18 | AndroidKeyStore persistence after device reboot | PASS |
| 19 | Post-reboot encrypted delivery without re-provisioning | PASS |

**19 / 19 PASS**

---

## 2. QA Observations

### A. Phase 2/3/4 Synthetic Room History Lost

Synthetic Room history from earlier phases was not present on the device during Phase 5 physical QA. The device had been fresh-installed during automated/instrumented testing. `firstInstallTime` confirmed a new installation.

This was **not** caused by a Room v3 migration or Phase 5 production code.

**Impact:** None on Phase 5 correctness. Phase 2–4 scenarios were re-validated from scratch on the fresh installation.

### B. One Delayed FCM Delivery During Negative Crypto Tests

One HIGH-priority message experienced delayed FCM delivery after several intentional HIGH messages that produced no user-visible notification during negative crypto tests (wrong key, tampered ciphertext, plaintext rejection). Three subsequent valid HIGH encrypted messages delivered normally.

**Root cause not confirmed.** The delay may be related to FCM rate-limiting behavior under repeated rapid delivery attempts that do not produce Android notifications. No recurring latency problem was reproduced. This does not indicate an E2EE code defect.

---

## 3. Testing Safety Note

### Avoid on Persistent Oppo Installation

The following operations may destroy app data and require full re-provisioning:

| Operation | Risk |
|-----------|------|
| `adb uninstall` | Removes Keystore namespace; requires key re-provisioning |
| `pm clear com.edu.ackline` | Wipes app data including Keystore; requires re-provisioning |
| `connectedDebugAndroidTest` | May install/uninstall APK; unless data impact is understood and protected, avoid on the real device |

### Prefer Emulator/AVD

Use the emulator/AVD for destructive instrumented tests that may remove or reset app data.

### Safe APK Updates

For non-destructive APK updates on the persistent Oppo installation:

```sh
adb install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
```

This preserves app data, Room state, and the AndroidKeyStore entry.

### Force-Stop vs Process Kill

- **Normal force-stop** (`adb shell am force-stop com.edu.ackline`) is acceptable when explicitly testing force-stop semantics.
- **Process-death / background delivery** testing should use normal backgrounding (remove from Recents) or:

```sh
adb shell am kill com.edu.ackline
```

Avoid repeated `force-stop` during standard delivery QA as it does not accurately simulate production process-death behavior.

---

## 4. Provisioning Command (Physically Validated)

The recommended provisioning command:

```sh
adb shell -T \
  'run-as com.edu.ackline sh -c "umask 077; mkdir -p files; dd of=files/.e2ee_staging.bin bs=32 count=1 2>/dev/null"' \
  < ~/.hermes/secrets/hermes-notify.key
```

**Replaces** the earlier `adb exec-out` form, which could hang waiting for EOF on the Oppo Find X9 Pro.

The `dd`-based command terminates deterministically after reading exactly 32 bytes.

---

## 5. What Was NOT Tested

- Real sensitive Hermes alert content (Phase 5 uses fake/non-sensitive payloads only)
- Multi-device scenarios
- Key rotation
- Production outbox integration (Phase 6 scope)

---

## 6. Conclusion

All 19 physical QA cases passed. Two non-blocking observations were recorded. Phase 5 physical QA is complete.

Next steps:

1. Independent code review
2. User commit/push to `5-application-e2ee`
3. ChatGPT GitHub review → PASS
4. Merge to `dev`
