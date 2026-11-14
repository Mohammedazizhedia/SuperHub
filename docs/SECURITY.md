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
```

Fallback (API 26–33):

· Warn user if any non-tool AccessibilityService is enabled.
· Offer "Restricted mode" (disables overlay when suspicious service detected).

Layer 2 — Tapjacking Prevention

· setFilterTouchesWhenObscured(true) on all sensitive views.
· Android 16+ enables it automatically with accessibilityDataSensitive.
· Overlay windows: setHideOverlayWindows(true) (API 31+) so our app hides malicious overlays.

Layer 3 — Key Storage (Android Keystore)

· AES-256-GCM key generated in AndroidKeyStore.
· Prefer Hardware-backed (TEE/SE) when available.
· Pairing key (derived from QR/PIN) stored only in Keystore, never in SharedPreferences/DataStore plaintext.
· Key rotation on device revoke.

```kotlin
KeyGenParameterSpec.Builder("superhub_master",
    PURPOSE_ENCRYPT or PURPOSE_DECRYPT)
    .setBlockModes(BLOCK_MODE_GCM)
    .setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
    .setKeySize(256)
    .build()
```

Layer 4 — Transport Security

LAN (v1.0):

· ws:// + HMAC-SHA256 on every message, key derived from pairing secret (HKDF).
· Replay protection: monotonic nonce + timestamp window (±30s).
· Message format: {nonce, ts, payload_encrypted, hmac}.

Internet (v2.0, planned):

· wss:// (TLS 1.3 only)
· Certificate Pinning + Backup Pin
· No cleartext traffic (android:usesCleartextTraffic="false")

Layer 5 — Runtime Integrity

Detect and respond (warn + restrict, do not force-close):

Check API Response
Root su binary, Magisk, SuperSU Warn + disable sync
Debugger Debug.isDebuggerConnected() Warn + disable sync
Frida / Xposed library scan, /proc/self/maps Warn + disable sync
APK signature PackageManager.getPackageInfo(GET_SIGNING_CERTIFICATES) Refuse to run
Emulator Build.FINGERPRINT heuristics Warn only

Layer 6 — Overlay Hardening

· FLAG_SECURE on Hub Overlay → blocks screenshots & screen recording.
· setHideOverlayWindows(true) (API 31+) → hides malicious overlays above ours.
· android:exported="false" on all internal Activities/Services.
· No PendingIntent with implicit intents.

Layer 7 — Data at Rest

· Clipboard history encrypted with Keystore-backed key (AES-256-GCM).
· No plaintext in logs (PII scrubbing in crash handler).
· Optional "Auto-wipe after N minutes" for clipboard history.
· Backup file (.bfbtheme, settings export) encrypted with user passphrase (Argon2id → AES-256-GCM).

Layer 8 — Least Privilege

Permission Why When
BIND_ACCESSIBILITY_SERVICE Read clipboard User enables in Settings
FOREGROUND_SERVICE + FOREGROUND_SERVICE_DATA_SYNC Stay alive for sync On sync start
POST_NOTIFICATIONS Foreground notification Android 13+
INTERNET LAN WebSocket Always (LAN only)
ACCESS_NETWORK_STATE Detect LAN Always
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS Battery exemption Onboarding
CAMERA QR pairing On pairing only

No READ_SMS, READ_CONTACTS, LOCATION, RECORD_AUDIO, or any GMS permission.

---

Supply Chain

· All dependencies: verified license (OSI-approved), last update < 12 months, no known Critical/High CVE.
· SBOM generated per release (sbom.json — CycloneDX format).
· Dependency monitoring: GitHub Dependabot + manual weekly review.
· No GMS/HMS SDK (Open mode).

---

Build Hardening

· R8 + ProGuard (full mode)
· android:debuggable="false" in release
· android:allowBackup="false" (we do our own encrypted backup)
· Network security config: cleartext disabled, only LAN IPs allowed for ws://
· Signed with release key (injected via GitHub Secrets, never in repo)

---

Testing

Test Tool Gate
SAST Android Lint + detekt 0 Critical/High
DAST MobSF 0 Critical/High
Dependency CVE ./gradlew dependencyCheckAnalyze 0 Critical/High
Tapjacking Manual (overlay app) Blocked
Accessibility spy Manual (malicious a11y app) Blocked on API 34+
Root detection Magisk device Warns + restricts
Frida detection Frida server Warns + restricts
TLS (v2.0) testssl.sh TLS 1.3 only
Backup encryption Manual Wrong passphrase fails

---

Incident Response

1. Report to security@bfb.ai (placeholder — user to configure).
2. 72h acknowledgment, 7d fix for Critical, 30d for others.
3. Coordinated disclosure with CVE if applicable.

---

Known Limitations (honest)

· No 100% protection on rooted devices with active Frida — we warn + restrict, but cannot guarantee.
· API 26–33: Accessibility spying prevention is best-effort (warn only).
· LAN WebSocket: HMAC protects integrity, but metadata (who talks to whom) is visible on LAN.

---

Built with BFB.AI — Built For Benefits, by AI

```
