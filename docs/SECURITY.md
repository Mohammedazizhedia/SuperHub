# SuperHub — Security Architecture

> BFB.AI · Built For Benefits, by AI
> Version: 1.0.0

---

## Threat Model

SuperHub handles **clipboard content** (often passwords, OTPs, private messages). Adversaries we defend against:

1. **Malicious app with Accessibility Service** — reading overlay content
2. **Tapjacking overlay** — tricking user into unintended taps
3. **Network eavesdropper (LAN)** — intercepting WebSocket traffic
4. **Rooted device / Frida / Xposed** — runtime hooking
5. **Forensic extraction** — reading stored keys/data
6. **Screenshot/recording** — capturing sensitive overlay

Out of scope: physical device seizure with unlocked screen, supply-chain compromise of OS.

---

## Defense Layers

### Layer 1 — Accessibility Spying Prevention

**Android 14+ (API 34):**
- Set `accessibilityDataSensitive` on every Composable/View showing clipboard content.
- Prevents non-tool AccessibilityServices from reading or interacting.
- Android 16+ extends this to block touch simulation on sensitive views.

```kotlin
Modifier.semantics { sensitiveData = true }
