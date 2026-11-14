# SuperHub — Design Document

> BFB.AI · Built For Benefits, by AI
> Version: 1.0.0 · Status: Approved

---

## 1. Identity

**One-line identity:** "هادئ، دقيق، فوري" — Calm, precise, instant.

**Category:** Productivity / Tools
**Visual style:** Soft Glassmorphism + Minimal
**Mode:** Open (no Google Services)

---

## 2. Design Tokens

### 2.1 Colors

| Token | Light | Dark | Usage |
|---|---|---|---|
| `primary` | `#0B1B3A` | `#4DE1E1` | AppBar, titles |
| `secondary` | `#3A4A6B` | `#A9B8D4` | Subtitles |
| `accent` | `#00A6A6` | `#00A6A6` | Bolt icon, links, focus |
| `background` | `#F7F9FC` | `#0B1B3A` | Screen bg |
| `surface` | `#FFFFFF` | `#13264D` | Cards, overlay |
| `error` | `#FF5A5F` | `#FF5A5F` | Errors |
| `success` | `#2ECC71` | `#2ECC71` | Connected, synced |
| `warning` | `#F5A623` | `#F5A623` | Battery, permissions |
| `onSurface` | `#0B1B3A` | `#E8EEF7` | Text on surface |
| `onSurfaceMuted` | `rgba(11,27,58,0.55)` | `rgba(232,238,247,0.55)` | Captions |
| `glassBg` | `rgba(255,255,255,0.72)` | `rgba(19,38,77,0.72)` | Overlay bg |
| `glassBorder` | `rgba(255,255,255,0.6)` | `rgba(77,225,225,0.18)` | Overlay border |

**Contrast:** All text ≥ 4.5:1 (headings ≥ 3:1). No color-only meaning.

### 2.2 Typography

| Role | Arabic | Latin |
|---|---|---|
| Display / Heading | Cairo | Inter |
| Body | IBM Plex Sans Arabic | Inter |
| Mono / Digits | JetBrains Mono | JetBrains Mono |

**Scale:** Display 32 / Title 22 / Body 15 / Label 12 (base 100%, adjustable 80%–200%).
**Weights:** Regular 400 / Medium 500 / Bold 700.
**Line height:** Compact 1.3 / Comfortable 1.5 / Relaxed 1.7.
**Digits:** Arabic-Indic (٠١٢) or Latin (012) — user choice.

**Forbidden:** DejaVu Sans (anywhere).

### 2.3 Shape & Spacing

- Grid: 8dp
- Card radius: 20dp (default) / 16dp (compact)
- Button/field radius: 12dp
- Overlay radius: 28dp
- Touch target: ≥ 48dp
- Icon family: Material Symbols (single weight)

### 2.4 Motion

- Duration: 200–350ms
- Easing: `cubic-bezier(0.16, 1, 0.3, 1)` (spring-like)
- Reduce-motion: respects system setting
- Haptic: LightImpact on toggles, MediumImpact on destructive

---

## 3. Screens

### 3.1 Home
- AppBar: "SuperHub" + connection dot + ⚙️ + 🌙
- Connected devices (horizontal cards)
- Sync history (vertical cards: text/image/file)
- FAB: "＋ Add device"
- States: Loading (skeleton) / Empty (illustration + CTA) / Error / Success

### 3.2 Floating Hub Overlay (core)
- Glassmorphism card, draggable, `FLAG_SECURE`
- Header: "SuperHub" + ⚡ toggle + ✕ close
- Cards list (text/image/file) with hover actions
- Footer: "Built with BFB.AI" (60% alpha)
- Bolt states: Active (glow + pulse) / Paused (35% alpha) / Error (⚠️ red)
- Long-press bolt → opens Privacy settings

### 3.3 Appearance Settings
- Live preview card
- Theme: Light / Dark / AMOLED / High-contrast / Dynamic
- Primary color picker (palette + HEX + slider) with contrast warning
- Font picker (Cairo / IBM Plex / Tajawal / Almarai / Readex / Noto Naskh / Inter / Manrope / JetBrains Mono)
- Font size slider 80%–200%
- Weight: Regular / Medium / Bold + Bold Text toggle
- Line height: Compact / Comfortable / Relaxed
- Digits: Arabic / Latin
- Corner: Sharp / Medium / Large
- Density: Comfortable / Normal / Compact
- Motion: Full / Reduced / None
- Import / Export `.bfbtheme` (JSON, max 64KB, schema-validated)

### 3.4 Privacy Settings
- Encryption: AES-256-GCM (hardware-backed Keystore)
- Pairing key: show QR / regenerate
- Sync: LAN only
- Trusted devices: list + revoke
- Error reporting: toggle + "what we collect" screen
- Delete all data

### 3.5 Onboarding (3 screens, skippable)
1. Welcome — "Instant clipboard sync, no server"
2. Accessibility permission — "To read clipboard in background"
3. Battery exemption — "To stay alive in background"

---

## 4. BFB.AI Signature

| Location | Shown? | Details |
|---|---|---|
| Splash | ✅ | Bottom, 60% alpha |
| About | ✅ | Full line + meaning (AR/EN) |
| Star Overlay | ✅ | Footer, 11sp, 60% alpha, non-interactive |
| README | ✅ | Header |
| Changelog | ✅ | Header |
| Security report | ✅ | Header |
| Code header | ✅ | `// Built with BFB.AI` first line |
| BFB.txt in ZIP | ✅ | Name + meaning + version |
| App icon | ❌ | Forbidden |
| QS Tiles | ❌ | Forbidden |

**Logo files:** `assets/brand/BFB_light.svg`, `BFB_dark.svg`, `BFB_mono.svg`.

---

## 5. Accessibility

- TalkBack: `contentDescription` on every interactive element
- Font scale up to 200% without layout break
- Logical focus order in RTL and LTR
- Keyboard / D-pad support on tablets
- Reduce-motion respected
- Contrast ≥ 4.5:1 enforced (with warning on user color pick)

---

## 6. RTL / LTR

- Full support, instant switch (no restart)
- All strings in `strings.xml` (AR + EN)
- Directional icons auto-mirror (arrows, back)
- Numbers/dates follow selected locale

---

## 7. Signature Branding

- **Name:** BFB.AI
- **Meaning:** Built For Benefits, by AI
- **Tagline:** "Built with BFB.AI"
- **Font:** Inter / IBM Plex Sans / Cairo (bold)
- **Colors:** Light `#0B1B3A` + `.AI` `#00A6A6`; Dark `#FFFFFF` + `#4DE1E1`; Mono `currentColor`

---

## 8. Competitor Benchmark

| Competitor | Strength | Gap we exploit |
|---|---|---|
| Huawei SuperHub | Seamless drag/drop | Locked to Huawei devices |
| KDE Connect | Full sync suite | Dated UI, complex setup |
| LocalSend | Fast local transfer | No clipboard sync |
| SyncClipboard | Server-based sync | Requires server, no overlay |
| Ditto (Win) | Powerful manager | Windows only |

**Signature feature:** "Floating hub + instant LAN sync + app filter + no mandatory server + open source."

---

## 9. Definition of Done

- [ ] Google mode = A (Open), no GMS SDK
- [ ] Design approved
- [ ] Appearance screen works (themes + colors + fonts + size + safe import/export)
- [ ] RTL/LTR + Light/Dark
- [ ] Tested on device without GMS
- [ ] 0 Critical/High + SBOM
- [ ] Tests + upgrade test pass
- [ ] Icon at all densities
- [ ] BFB.AI signature in About + Splash + Overlay + README + code header + BFB.txt
- [ ] README + Privacy policy + SHA256 + security report
- [ ] Size within budget (< 30MB)
- [ ] "اعتمد" approval

---

*Built with BFB.AI — Built For Benefits, by AI* 
