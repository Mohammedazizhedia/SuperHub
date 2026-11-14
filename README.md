# SuperHub

> **BFB.AI · Built For Benefits, by AI**

مزامنة لحظية للحافظة بين أجهزة أندرويد على نفس الشبكة المحلية — بدون سيرفر، بدون حساب، بدون خدمات جوجل.

Instant clipboard sync between Android devices on the same LAN — no server, no account, no Google Services.

---

## ✨ الميزات / Features

- 🔄 **مزامنة لحظية** — انسخ على جهاز، الصق على التاني في أقل من ثانية
- 🪟 **Hub عائم** — نافذة عائمة فوق أي تطبيق، اسحب وأفلت المحتوى
- 🎯 **فلتر تطبيقات** — اختر تطبيقات معينة بس للمزامنة (اختياري)
- 🔒 **تشفير end-to-end** — AES-256-GCM + HMAC-SHA256
- 🛡️ **حماية متعددة الطبقات** — ضد التجسس، Tapjacking، Root، Frida
- 🌐 **بدون إنترنت** — LAN فقط، بدون سيرفر سحابي
- 🎨 **تخصيص كامل** — ثيمات، خطوط، أحجام، أرقام عربية/لاتينية
- 🌍 **عربي + إنجليزي** — RTL/LTR كامل
- 📴 **Offline-first** — كل الميزات الأساسية تعمل بدون إنترنت
- 🆓 **مفتوح المصدر** — بدون خدمات جوجل، بدون تتبع

---

## 📱 المتطلبات / Requirements

- Android 8.0 (API 26) أو أحدث
- نفس الشبكة المحلية (Wi-Fi) للأجهزة المتزامنة

---

## 🚀 التثبيت / Installation

### من GitHub Releases
1. نزّل أحدث `SuperHub_Build.apk` من [Releases](../../releases)
2. فعّل "تثبيت من مصادر غير معروفة" لمرة واحدة
3. ثبّت وافتح التطبيق
4. اتبع الـ Onboarding (3 شاشات)

### التحديث التلقائي
- التطبيق يفحص تحديثات من GitHub Releases
- التحقق من SHA256 + التوقيع قبل التثبيت
- شاشة Changelog + موافقة المستخدم

---

## 🎯 الاستخدام السريع / Quick Start

1. **افتح التطبيق** → اتبع Onboarding (صلاحية إمكانية الوصول + استثناء البطارية)
2. **أضف جهاز** → اضغط FAB "＋ Add device" → امسح QR على الجهاز التاني
3. **انسخ أي حاجة** → المحتوى يظهر تلقائياً على الجهاز التاني
4. **استخدم الـ Hub العائم** → اسحب من أي تطبيق، أفلت في الـ Overlay

---

## 🏗️ البنية / Architecture

```

SuperHub/
├── shared/              # KMP — منطق مشترك (SyncMessage, Crypto, Discovery)
├── androidApp/          # Android (Kotlin + Compose)
│   ├── service/         # ForegroundSyncService + ClipboardAccessibilityService
│   ├── ui/              # Home, Overlay, Appearance, Privacy, Onboarding
│   └── theme/           # Design Tokens
├── docs/                # DESIGN.md, SECURITY.md, PRIVACY.md
├── .github/workflows/   # CI (build + security scan)
└── BFB.txt

```

**التقنيات:**
- **Kotlin Multiplatform** — منطق مشترك
- **Jetpack Compose** — UI
- **OkHttp WebSocket** — المزامنة
- **Android Keystore** — تخزين المفاتيح
- **mDNS/UDP Broadcast** — اكتشاف الأجهزة

---

## 🔒 الأمان / Security

انظر [`SECURITY.md`](docs/SECURITY.md) للتفاصيل الكاملة. ملخص:

| الطبقة | التقنية |
|---|---|
| منع التجسس | `accessibilityDataSensitive` (API 34+) |
| منع Tapjacking | `filterTouchesWhenObscured` |
| تخزين المفاتيح | Android Keystore (Hardware-backed) |
| النقل | HMAC-SHA256 على كل رسالة |
| كشف العبث | Root / Debugger / Frida detection |
| الـ Overlay | `FLAG_SECURE` + `setHideOverlayWindows` |

**للإبلاغ عن ثغرة:** انظر [SECURITY.md](docs/SECURITY.md) — قسم Incident Response.

---

## 🎨 التصميم / Design

انظر [`DESIGN.md`](docs/DESIGN.md) للتفاصيل الكاملة. ملخص:

- **الهوية:** "هادئ، دقيق، فوري"
- **الألوان:** كحلي `#0B1B3A` + تركواز `#00A6A6`
- **الخطوط:** Cairo (عناوين) + IBM Plex Sans Arabic (نصوص) + Inter (لاتيني)
- **الحركة:** 200–350ms، spring-like

---

## 🛠️ البناء / Build

### المتطلبات
- JDK 17
- Android SDK 34
- Gradle 8.x (wrapper مضمّن)

### الأوامر
```bash
# Clone
git clone https://github.com/YOUR_USER/SuperHub.git
cd SuperHub

# Build (open flavor)
./gradlew :androidApp:assembleDebug

# Build release (يحتاج مفتاح توقيع في keystore.properties)
./gradlew :androidApp:assembleRelease
```

التوقيع

مفاتيح التوقيع لا تُدرج في المستودع. تُحقن عبر GitHub Secrets:

· KEYSTORE_BASE64
· KEYSTORE_PASSWORD
· KEY_ALIAS
· KEY_PASSWORD

---

🤝 المساهمة / Contributing

1. Fork المستودع
2. أنشئ branch: git checkout -b feature/amazing
3. Commit: git commit -m "Add amazing feature"
4. Push: git push origin feature/amazing
5. افتح Pull Request

قبل الـ PR: شغّل ./gradlew lint detekt test وتأكد من 0 أخطاء.

---

📜 الترخيص / License

GPL-3.0 — مفتوح المصدر، مع Attribution لـ BFB.AI.

---

🙏 شكر / Credits

· Kotlin Multiplatform — JetBrains
· Jetpack Compose — Google
· OkHttp — Square
· Cairo / IBM Plex / Inter / JetBrains Mono — Google Fonts (OFL)

---

📞 التواصل / Contact

· Issues: GitHub Issues
· Security: انظر SECURITY.md
· BFB.AI: Built For Benefits, by AI

---

Built with BFB.AI — Built For Benefits, by AI

```
