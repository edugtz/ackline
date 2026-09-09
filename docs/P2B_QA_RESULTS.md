# P2B QA Results

> P2B closeout evidence. P2A history is untouched
> (see `docs/P2A_QA_RESULTS.md`). No secrets in this file:
> no FID, tokens, private endpoints, keys, or credentials.

## Result

```text
P2A:    COMPLETE
P2B-H1: COMPLETE
P2B-A1: COMPLETE
P2B-A2: COMPLETE
P2B-QA: PASS (critical physical product path, owner-accepted)
P2B:    COMPLETE
NTFY CLEANUP: COMPLETE (executable transport removed)
P2C:    DEFERRED / OPTIONAL (retained candidate, not implemented)
Phase 8: DEFERRED AS FORMAL GATE (superseded by normal use; not PASS)
```

P2B passes because the critical product and architecture path was
physically proven and accepted by the owner for normal use — not because
every exploratory/manual checklist case was executed.

## Physically proven critical path

After guided pairing (real Hermes terminal QR scan → `replace_required`
UX → replacement QR pairing → server-confirmed pairing → Listo → Inbox),
one real encrypted FCM canary proved the end-to-end architecture path:

```text
real Hermes terminal QR scan                                  PROVEN
replace_required UX                                           PROVEN
replacement QR pairing                                        PROVEN
server-confirmed pairing                                      PROVEN
Listo -> Inbox                                                PROVEN
real encrypted FCM after pairing                              PROVEN
delivery while Tailscale was OFF (pure FCM, no HTTPS)         PROVEN
native Android notification physically observed               PROVEN
notification persisted in Ackline                             PROVEN
duplicate delivery of same notification_id -> no duplicate    PROVEN
local Visto while Tailnet unavailable (local ACK immediate)   PROVEN
Hermes remained unacknowledged while Tailscale was OFF        PROVEN
remote Hermes ACK succeeded after Tailscale was restored      PROVEN
```

Final canary:

```text
notification_id: a82fc904319b4b77a6db8e498f972432
```

This closes the P2A non-blocking finding (native notification display was
NOT proven in P2A): the encrypted-FCM → native notification → Room
exactly-once → local-Visto → eventual remote-ACK path is now physically
proven on the production pairing flow.

## Explicitly NOT exhaustively executed (deferred, not blockers)

The following exploratory checks were deliberately left unexecuted and are
NO LONGER P2B blockers. They are NOT marked executed or passed here:

```text
exhaustive ColorOS permission variants                       NOT EXECUTED
repeated camera lifecycle permutations                       NOT EXECUTED
font-size permutations                                       NOT EXECUTED
extended accessibility / manual QA matrix                    NOT EXECUTED
```

Policy: these ride normal usage and bug-driven follow-up. A future bug
report reopens only the affected behavior — it does not retroactively
un-pass P2B.

## Owner acceptance

The owner has accepted P2B for normal use on the strength of the critical
physical end-to-end path above.

## Roadmap from here

```text
1. P2B COMPLETE (this closeout)
2. ntfy cleanup COMPLETE (executable ntfy transport removed; inert historical schema compatibility only)
3. No required feature development — operationalization next
4. P2C DEFERRED / OPTIONAL (retained candidate, not implemented, not next mandatory)
5. No manufactured Phase 8 session — normal real-world use provides reliability evidence
6. Alternative transport investigation only if future reliability evidence requires it
```

ntfy remains architecturally rejected, unsupported, not a fallback, not a
rollback, and not roadmap. Executable ntfy transport removal in Hermes is
COMPLETE.
