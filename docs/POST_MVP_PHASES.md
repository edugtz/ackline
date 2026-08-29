# Ackline — Post-MVP Phases

Post-MVP work is **evidence-driven**.

Do not implement these merely because they are listed.

`docs/CURRENT_PHASE.md` remains the only active scope.

## Principle

The successful product can remain permanently small.

If daily use does not reveal a problem, do not create a feature to solve it.

---

## P1 — UX Refinement From Real Usage

### Trigger

Repeated friction or screenshot feedback identifies a specific daily-use problem after MVP.

### Possible Work

- tune density/spacing;
- improve pending/viewed hierarchy;
- refine detail layout;
- refine severity visuals;
- improve timestamps;
- notification grouping if volume justifies it;
- optional swipe gestures only if explicit ACK semantics remain intact.

### Recommended AI Route

```text
ChatGPT screenshot/product review
→ /local-quality
→ Luna Medium only if quality remains generic
→ ChatGPT screenshot review
```

### Do Not Do

Do not redesign for novelty or add features unrelated to observed friction.

---

## P2 — Better Pairing / Device Registration

### Trigger

Manual FID copy/paste becomes annoying, fragile, or frequent.

### Possible Work

- QR pairing;
- one-time pairing secret;
- device replacement flow;
- registration health indicator.

### Recommended AI Route

ChatGPT architecture → `/local-quality` → Gemini for Android/Firebase platform behavior.

### Do Not Do

No accounts or general device-management backend.

---

## P3 — E2EE Key Rotation and Recovery

### Trigger

Long-term use, reinstall, or device replacement makes key lifecycle management necessary.

### Possible Work

- key rotation;
- small key-version history;
- safe re-pairing;
- device-loss/reinstall procedure.

### Recommended AI Route

ChatGPT threat model → `/cloud-hy3`/`/local-quality` → Gemini Keystore → Sol Codex security review.

### Do Not Do

Do not invent custom crypto protocols.

---

## P4 — VPS ACK Endpoint Migration

### Trigger

Hermes Personal Admin moves from the Mac to a VPS or a stable public endpoint becomes desirable.

### Possible Work

- replace Tailscale/Mac ACK URL;
- preserve the same ACK contract;
- add strong authentication;
- keep WorkManager retry unchanged;
- keep domain/UI unchanged.

### Recommended AI Route

ChatGPT architecture → `/local-quality` or `/cloud-ds-max` → `/cloud-hy3` correctness review.

---

## P5 — Delivery / Sync Diagnostics

### Trigger

Real-world debugging shows that a small diagnostics surface would materially reduce maintenance.

### Possible Information

- FCM/FID registration state;
- last push received;
- pending ACK count;
- last ACK sync;
- last reconciliation;
- app/build version.

### Do Not Do

Do not turn the app into an ops dashboard or expose secrets/identifiers unnecessarily.

---

## P6 — Retention and Local History

### Trigger

Viewed history grows enough to create storage or usability friction.

### Possible Policies

- keep all;
- delete viewed alerts older than N days;
- manual clear viewed history.

Default to preserving data until evidence says otherwise.

---

## P7 — Search / Additional Filtering

### Trigger

Inbox history becomes large enough that `Pendientes` / `Vistas` is insufficient.

### Possible Work

- local text search;
- severity filter;
- date grouping.

No backend search.

---

## P8 — Multi-Device

### Trigger

A second personal device genuinely needs independent push/ACK behavior.

### Required New Design Questions

- multiple FIDs;
- device-specific encryption keys;
- shared Hermes notification identity;
- whether ACK by one device acknowledges globally or per-device.

Do not prematurely add this complexity.

---

## P9 — Alternative Push Transport

### Trigger

Only if FCM becomes unsuitable because of platform, cost, privacy, device ecosystem, or product requirements.

The core app must remain transport-isolated so this is a bounded migration.

Do not maintain two push transports proactively.
