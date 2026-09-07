# P2A QA Results

> **HISTORICAL EVIDENCE — P2A CLOSED.** Do not rewrite. P2B is the active
> slice (see `docs/CURRENT_PHASE.md`, `docs/IMPLEMENTATION_PLAN.md`).

## Result

```text
P2A PHYSICAL QA: PASS_WITH_FINDINGS
P2A STATUS: COMPLETE
```

## Scope

Hermes H1 (pairing backend/protocol):

```text
durable short-lived one-time pairing sessions
separate pairing_sessions.db
hash-only token persistence
fresh/replace intent
pairing-begin / pairing-revoke CLI
POST /pairing/claim
Tailscale identity gate
atomic consume-once semantics
atomic FID update
explicit replace authorization
one-time E2EE key release over Tailnet HTTPS
ack_base_url returned in claim response
rate limiting
sanitized typed errors
canonical error for differing-FID fresh claim: replace_required
```

Ackline A1 (provisioning foundation):

```text
PairingClaimClient
explicit pairing endpoint input
direct PayloadKeyStore raw-key import
existing alias preservation
staged adb import retained as legacy/debug fallback
AckBaseUrlProvider
provisioned ACK URL precedence
BuildConfig ACK URL fallback
dynamic ACK/recovery URL resolution
server-confirmed FID baseline
ordered PairingProvisioner
no Room migration
no UI implementation (P2B owns UX)
```

## Automated Validation

- Hermes suite executed as part of H1 review/merge.
- Ackline JVM/build/lint validation executed as part of A1 review/merge.
- Final builds/tests/diff validation passed after temporary QA
  harness/artifacts were removed.

No test counts are invented here. Exact suites and commits live in
git history and the H1/A1 review records.

## Independent Reviews

```text
H1 security review        — done, no blocking findings carried into closeout
A1 R1                     — done, no blocking findings carried into closeout
A1 R2                     — done, no blocking findings carried into closeout
no M1/M2 final blockers
```

## Physical QA (physical Oppo)

```text
AndroidKeyStore direct-import instrumentation              PASS
production alias untouched by instrumentation tests         PASS
Tailscale OFF pairing fails closed                         PASS
same unconsumed session usable after Tailscale restored    PASS
fresh pairing vs stale/different Hermes FID →              PASS
  replace_required
explicit replace pairing succeeded                         PASS
consumed session replay rejected                           PASS
real expired session rejected                              PASS
fresh reinstall required explicit replace                  PASS
automatically provisioned FID worked for real FCM          PASS
automatically provisioned E2EE key decrypted real FCM      PASS
canary persisted exactly once in Room                      PASS
provisioned ACK URL worked                                 PASS
real local acknowledgment synced back to Hermes            PASS
temporary QA harness/artifacts removed                     PASS
final builds/tests/diff validation                         PASS
```

## Finding (non-blocking)

Real canary arrived, decrypted, and persisted exactly once, but Android
notification permission (`POST_NOTIFICATIONS`) was denied at delivery
time.

Therefore:

```text
native notification display: NOT PROVEN in this P2A QA run
verdict: NOT PROVEN — not failed, not blocking
```

Permission was granted afterward; no second canary was sent.

This does NOT block P2A because FCM reception, E2EE decryption, Room
ingestion, and the ACK round-trip were each proven. P2B explicitly places
notification-permission onboarding before pairing / future setup
verification.

## Operational Note (lesson, not product defect)

A stale pre-H1 Hermes listener initially returned 404 during QA. After
restarting the supervised listener from the reviewed H1 version, all
pairing scenarios passed. Classify as operational QA lesson.

## Cleanup

Temporary QA harness/scripts/artifacts removed before final validation.

## P2B Hardening Items (recorded, NOT P2A blockers)

```text
redact FidRePairState.toString()
review/redact SetupUiState installationId exposure
remove honor-system "Marcar como actualizado"
ensure secret-bearing pairing models never stringify sensitive fields
notification permission precedes final ready/self-test path
```

Do NOT implement them in a docs change. P2B owns them.

## Conclusion

```text
P2A COMPLETE
P2B NEXT
P2C PLANNED
Phase 8 DEFERRED — final real-world daily-usage / reliability gate
```

No secrets in this file: no tokens, FIDs, hostnames, keys, ACK tokens,
or service-account information.
