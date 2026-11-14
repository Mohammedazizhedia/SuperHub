#!/usr/bin/env bash
# Built with BFB.AI — Part 1: Structure & Gradle
set -euo pipefail

mkdir -p .github/workflows androidApp/src/main/java/com/bfbai/superhub/service androidApp/src/main/java/com/bfbai/superhub/ui/home androidApp/src/main/java/com/bfbai/superhub/ui/overlay androidApp/src/main/java/com/bfbai/superhub/ui/settings androidApp/src/main/java/com/bfbai/superhub/ui/onboarding androidApp/src/main/java/com/bfbai/superhub/theme androidApp/src/main/java/com/bfbai/superhub/security androidApp/src/main/res/values androidApp/src/main/res/values-ar shared/src/commonMain/kotlin/com/bfbai/superhub docs assets/brand gradle/wrapper

cat > settings.gradle.kts <<'EOF'
// Built with BFB.AI — Built For Benefits, by AI
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "SuperHub"
include(":androidApp")
include(":shared")
EOF

cat > build.gradle.kts <<'EOF'
// Built with BFB.AI
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

cat > .gitignore <<'EOF'
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

cat > shared/build.gradle.kts <<'EOF'
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
-keep class com.bfbai.superhub.** { *; }
EOF
echo "Part 1 Done."
