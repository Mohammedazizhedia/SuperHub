#!/usr/bin/env bash
# Built with BFB.AI — Part 2: Code
set -euo pipefail

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

cat > shared/src/commonMain/kotlin/com/bfbai/superhub/SyncMessage.kt <<'EOF'
package com.bfbai.superhub
import kotlinx.serialization.Serializable

@Serializable
data class SyncMessage(
    val type: String,
    val content: String,
    val deviceId: String,
    val timestamp: Long
)
EOF
echo "Part 2 Done."
