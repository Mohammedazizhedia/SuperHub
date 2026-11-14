#!/usr/bin/env bash
# Built with BFB.AI — Part 4: Full Code & Core Files
set -euo pipefail

# 1. Gradle Files
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
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.ktor:ktor-client-okhttp:2.3.12")
    implementation("io.ktor:ktor-client-websockets:2.3.12")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
EOF

cat > androidApp/proguard-rules.pro <<'EOF'
-keep class com.bfbai.superhub.SuperHubApp { *; }
-keep class com.bfbai.superhub.MainActivity { *; }
-keep class com.bfbai.superhub.service.ClipboardAccessibilityService { *; }
-keep class com.bfbai.superhub.service.ForegroundSyncService { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.bfbai.superhub.**$$serializer { *; }
-keepclassmembers class com.bfbai.superhub.** { *** Companion; }
-keepclasseswithmembers class com.bfbai.superhub.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
EOF

# 2. Manifest
cat > androidApp/src/main/AndroidManifest.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <application
        android:name=".SuperHubApp"
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:allowBackup="false"
        android:usesCleartextTraffic="false">
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.SuperHub.Splash">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.ForegroundSyncService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />

        <service
            android:name=".service.ClipboardAccessibilityService"
            android:exported="true"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>
    </application>
</manifest>
EOF

mkdir -p androidApp/src/main/res/xml
cat > androidApp/src/main/res/xml/accessibility_service_config.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeViewFocused|typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault"
    android:canRetrieveWindowContent="true"
    android:description="@string/onboarding_2_body"
    android:notificationTimeout="100" />
EOF

# 3. Kotlin Classes
cat > androidApp/src/main/java/com/bfbai/superhub/SuperHubApp.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub
import android.app.Application
class SuperHubApp : Application()
EOF

cat > androidApp/src/main/java/com/bfbai/superhub/service/ForegroundSyncService.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub.service
import android.app.*
import android.content.Intent
import android.os.IBinder
class ForegroundSyncService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
}
EOF

cat > androidApp/src/main/java/com/bfbai/superhub/service/ClipboardAccessibilityService.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub.service
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
class ClipboardAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
EOF

cat > androidApp/src/main/java/com/bfbai/superhub/theme/Color.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub.theme
import androidx.compose.ui.graphics.Color
val PrimaryLight = Color(0xFF0B1B3A)
val SecondaryLight = Color(0xFF3A4A6B)
val AccentLight = Color(0xFF00A6A6)
val BackgroundLight = Color(0xFFF7F9FC)
val SurfaceLight = Color(0xFFFFFFFF)
val ErrorLight = Color(0xFFFF5A5F)
val SuccessLight = Color(0xFF2ECC71)
val WarningLight = Color(0xFFF5A623)
val OnSurfaceLight = Color(0xFF0B1B3A)
val OnSurfaceMutedLight = Color(0x8C0B1B3A)
val GlassBgLight = Color(0xB8FFFFFF)
val GlassBorderLight = Color(0x99FFFFFF)

val PrimaryDark = Color(0xFF4DE1E1)
val SecondaryDark = Color(0xFFA9B8D4)
val AccentDark = Color(0xFF00A6A6)
val BackgroundDark = Color(0xFF0B1B3A)
val SurfaceDark = Color(0xFF13264D)
val ErrorDark = Color(0xFFFF5A5F)
val SuccessDark = Color(0xFF2ECC71)
val WarningDark = Color(0xFFF5A623)
val OnSurfaceDark = Color(0xFFE8EEF7)
val OnSurfaceMutedDark = Color(0x8CE8EEF7)
val GlassBgDark = Color(0xB813264D)
val GlassBorderDark = Color(0x2E4DE1E1)
EOF

cat > androidApp/src/main/java/com/bfbai/superhub/theme/Shape.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub.theme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
val SuperHubShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
EOF

cat > androidApp/src/main/java/com/bfbai/superhub/theme/Type.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub.theme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
val SuperHubTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp)
)
EOF

cat > androidApp/src/main/java/com/bfbai/superhub/theme/Theme.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
private val LightColors = lightColorScheme(
    primary = PrimaryLight, secondary = SecondaryLight, tertiary = AccentLight,
    background = BackgroundLight, surface = SurfaceLight, error = ErrorLight,
    onPrimary = SurfaceLight, onSecondary = SurfaceLight, onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight, onError = SurfaceLight
)
private val DarkColors = darkColorScheme(
    primary = PrimaryDark, secondary = SecondaryDark, tertiary = AccentDark,
    background = BackgroundDark, surface = SurfaceDark, error = ErrorDark,
    onPrimary = BackgroundDark, onSecondary = BackgroundDark, onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark, onError = SurfaceLight
)
@Composable
fun SuperHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SuperHubTypography,
        shapes = SuperHubShapes,
        content = content
    )
}
EOF

cat > androidApp/src/main/java/com/bfbai/superhub/MainActivity.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.bfbai.superhub.theme.SuperHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            SuperHubTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreenPlaceholder()
                }
            }
        }
    }
}

@Composable
fun HomeScreenPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SuperHub",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "مزامنة لحظية للحافظة",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Built with BFB.AI",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
EOF

cat > shared/src/commonMain/kotlin/com/bfbai/superhub/SyncMessage.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub
import kotlinx.serialization.Serializable
@Serializable
data class SyncMessage(
    val type: String,
    val content: String,
    val deviceId: String,
    val timestamp: Long,
    val nonce: String = "",
    val hmac: String = ""
)
EOF

echo "Part 4 Done."
