#!/usr/bin/env bash
# Built with BFB.AI — Part 7: Overlay & Accessibility
set -euo pipefail

mkdir -p androidApp/src/main/java/com/bfbai/superhub/service
mkdir -p androidApp/src/main/java/com/bfbai/superhub/ui/overlay

# 1. تحديث خدمة إمكانية الوصول (لمراقبة الحافظة)
cat > androidApp/src/main/java/com/bfbai/superhub/service/ClipboardAccessibilityService.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent

class ClipboardAccessibilityService : AccessibilityService() {

    private lateinit var clipboardManager: ClipboardManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        // مراقبة تغييرات الحافظة
        clipboardManager.addPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (!text.isNullOrEmpty()) {
                    // هنا بنبعت النص للخدمة عشان تتم مزامنته
                    // SyncManager.sendToNetwork(text)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // مش محتاجينه هنا لأننا بنستخدم Clipboard Listener
    }

    override fun onInterrupt() {}
}
EOF

# 2. الـ Overlay Service (الستارة العائمة)
cat > androidApp/src/main/java/com/bfbai/superhub/service/OverlayService.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.bfbai.superhub.R

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
    }

    private fun showOverlay() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        
        // هنستخدم تصميم برمجي بسيط مؤقتاً للتجربة
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        val textView = TextView(this).apply {
            text = "SuperHub Hub\n(اسحبني)" // تصميم مبدئي
            setBackgroundColor(0xFF0B1B3A.toInt())
            setTextColor(0xFF4DE1E1.toInt())
            setPadding(32, 32, 32, 32)
        }

        overlayView = textView
        windowManager.addView(overlayView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
EOF

# 3. تحديث المانيفست لإضافة الـ Overlay والصلاحيات
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
    
    <!-- صلاحية الـ Overlay (الستارة) -->
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    
    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <application
        android:name=".SuperHubApp"
        android:label="@string/app_name"
        android:icon="@drawable/ic_launcher"
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

        <!-- خدمة الستارة العائمة -->
        <service
            android:name=".service.OverlayService"
            android:exported="false" />

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

# 4. تحديث الـ MainActivity عشان نختبر تشغيل الستارة
cat > androidApp/src/main/java/com/bfbai/superhub/MainActivity.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.bfbai.superhub.service.OverlayService
import com.bfbai.superhub.theme.SuperHubTheme
import com.bfbai.superhub.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            SuperHubTheme {
                var showPermissionDialog by remember { mutableStateOf(false) }

                HomeScreen(
                    viewModel = viewModel,
                    onStartOverlay = {
                        if (!Settings.canDrawOverlays(this)) {
                            showPermissionDialog = true
                        } else {
                            startService(Intent(this, OverlayService::class.java))
                        }
                    }
                )

                if (showPermissionDialog) {
                    AlertDialog(
                        onDismissRequest = { showPermissionDialog = false },
                        title = { Text("صلاحية الستارة العائمة") },
                        text = { Text("محتاجين صلاحية عشان نعرض الـ Hub فوق التطبيقات التانية.") },
                        confirmButton = {
                            Button(onClick = {
                                showPermissionDialog = false
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                                startActivity(intent)
                            }) { Text("افتح الإعدادات") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPermissionDialog = false }) { Text("إلغاء") }
                        }
                    )
                }
            }
        }
    }
}
EOF

# 5. تحديث شاشة HomeScreen عشان نضيف زرار الستارة
cat > androidApp/src/main/java/com/bfbai/superhub/ui/HomeScreen.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bfbai.superhub.MainViewModel
import com.bfbai.superhub.SyncItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel, onStartOverlay: () -> Unit) {
    val history by viewModel.history.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SuperHub", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* Settings */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onStartOverlay) {
                Icon(Icons.Default.Add, contentDescription = "تشغيل الستارة")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("الأجهزة المتصلة", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
            DeviceCard("جهازي (هذا الهاتف)", true)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("آخر ما تمت مزامنته", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
            
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("اضغط على زرار + لتشغيل الستارة العائمة.", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(history) { item ->
                        SyncItemCard(item)
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(name: String, isActive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).background(if (isActive) Color(0xFF2ECC71) else Color.Gray, RoundedCornerShape(50)))
            Spacer(modifier = Modifier.width(12.dp))
            Text(name, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SyncItemCard(item: SyncItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.text, fontWeight = FontWeight.Medium)
            Text(item.time, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
EOF

echo "Part 7 Done."
