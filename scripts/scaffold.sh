#!/usr/bin/env bash
# Built with BFB.AI — Built For Benefits, by AI
set -euo pipefail

# 1. Create directory structure
mkdir -p .github/workflows androidApp/src/main/java/com/bfbai/superhub/service androidApp/src/main/java/com/bfbai/superhub/ui/home androidApp/src/main/java/com/bfbai/superhub/ui/overlay androidApp/src/main/java/com/bfbai/superhub/ui/settings androidApp/src/main/java/com/bfbai/superhub/ui/onboarding androidApp/src/main/java/com/bfbai/superhub/theme androidApp/src/main/java/com/bfbai/superhub/security androidApp/src/main/res/values androidApp/src/main/res/values-ar androidApp/src/main/res/mipmap-anydpi-v26 shared/src/commonMain/kotlin/com/bfbai/superhub docs assets/brand gradle/wrapper

# 2. Settings & Root Gradle
cat > settings.gradle.kts <<'EOF'
// Built with BFB.AI — Built For Benefits, by AI
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "SuperHub"
include(":androidApp")
include(":shared")
EOF

cat > build.gradle.kts <<'EOF'
// Built with BFB.AI — Built For Benefits, by AI
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("com.android.library") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
}
EOF

cat > gradle.properties <<'EOF'
# Built with BFB.AI — Built For Benefits, by AI
org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
superhub.versionName=1.0.0
superhub.versionCode=1
EOF

cat > gradle/wrapper/gradle-wrapper.properties <<'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
networkTimeout=10000
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF

# 3. Gitignore & README
cat > .gitignore <<'EOF'
# Built with BFB.AI
.gradle/
build/
local.properties
.idea/
*.iml
.DS_Store
keystore.properties
*.jks
*.apk
*.aab
*.zip
EOF

cat > README.md <<'EOF'
# SuperHub

> **BFB.AI · Built For Benefits, by AI**

مزامنة لحظية للحافظة بين أجهزة أندرويد على نفس الشبكة المحلية — بدون سيرفر، بدون حساب، بدون خدمات جوجل.

## Features
- 🔄 Instant sync
- 🪟 Floating Hub overlay
- 🎯 App filter
- 🔒 AES-256-GCM + HMAC-SHA256
- 🌍 Arabic + English

*Built with BFB.AI — Built For Benefits, by AI*
EOF

# 4. Module Gradles
cat > shared/build.gradle.kts <<'EOF'
// Built with BFB.AI
plugins { id("org.jetbrains.kotlin.multiplatform"); id("com.android.library"); id("org.jetbrains.kotlin.plugin.serialization") }
kotlin {
    androidTarget()
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("io.ktor:ktor-client-core:2.3.12")
            implementation("io.ktor:ktor-client-websockets:2.3.12")
        }
        androidMain.dependencies { implementation("io.ktor:ktor-client-okhttp:2.3.12") }
    }
}
android { namespace = "com.bfbai.superhub.shared"; compileSdk = 34; defaultConfig { minSdk = 26 } }
EOF

cat > androidApp/build.gradle.kts <<'EOF'
// Built with BFB.AI
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.bfbai.superhub"
    compileSdk = 34
    defaultConfig { applicationId = "com.bfbai.superhub"; minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "1.0.0" }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":shared"))
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
}
EOF

cat > androidApp/proguard-rules.pro <<'EOF'
# Built with BFB.AI
-keep class com.bfbai.superhub.** { *; }
EOF

# 5. Android Manifest & Main
cat > androidApp/src/main/AndroidManifest.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="SuperHub" android:icon="@mipmap/ic_launcher">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

cat > androidApp/src/main/java/com/bfbai/superhub/MainActivity.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("SuperHub — Built with BFB.AI") }
    }
}
EOF

# 6. Strings (AR & EN - Short version for scaffolding)
cat > androidApp/src/main/res/values/strings.xml <<'EOF'
<resources>
    <string name="app_name">SuperHub</string>
    <string name="about_signature">Built with BFB.AI</string>
</resources>
EOF

cat > androidApp/src/main/res/values-ar/strings.xml <<'EOF'
<resources>
    <string name="app_name">SuperHub</string>
    <string name="about_signature">Built with BFB.AI</string>
</resources>
EOF

# 7. Docs (Placeholders to be filled later)
echo "# SuperHub Design" > docs/DESIGN.md
echo "# SuperHub Security" > docs/SECURITY.md
echo "# SuperHub Privacy" > docs/PRIVACY.md
echo "# Changelog" > CHANGELOG.md
echo "GPL-3.0 License" > LICENSE

# 8. GitHub Actions
cat > .github/workflows/build-android.yml <<'EOF'
name: Build Android
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'temurin', java-version: '17' }
      - run: chmod +x gradlew && ./gradlew :androidApp:assembleDebug
EOF

echo "✅ Done! All files generated."
