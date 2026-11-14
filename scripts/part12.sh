#!/usr/bin/env bash
# Built with BFB.AI — Part 12: Floating Bubble, Tile & Final Logic
set -euo pipefail

mkdir -p androidApp/src/main/java/com/bfbai/superhub/service
mkdir -p androidApp/src/main/res/drawable
mkdir -p androidApp/src/main/res/values

# 1. أيقونة لشريط الإشعارات
cat > androidApp/src/main/res/drawable/ic_hub_tile.xml <<'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M12,6c-3.31,0 -6,2.69 -6,6s2.69,6 6,6 6,-2.69 6,-6 -2.69,-6 -6,-6zM12,16c-2.21,0 -4,-1.79 -4,-4s1.79,-4 4,-4 4,1.79 4,4 -1.79,4 -4,4z"/>
</vector>
EOF

# 2. شريط الإشعارات (Quick Settings Tile)
cat > androidApp/src/main/java/com/bfbai/superhub/service/SuperHubTileService.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class SuperHubTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile.state = Tile.STATE_INACTIVE
        qsTile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, OverlayService::class.java)
        if (qsTile.state == Tile.STATE_INACTIVE) {
            startService(intent)
            qsTile.state = Tile.STATE_ACTIVE
        } else {
            stopService(intent)
            qsTile.state = Tile.STATE_INACTIVE
        }
        qsTile.updateTile()
    }
}
EOF

# 3. تحديث خدمة إمكانية الوصول لتراقب النسخ
cat > androidApp/src/main/java/com/bfbai/superhub/service/ClipboardAccessibilityService.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.bfbai.superhub.sync.SyncManager

class ClipboardAccessibilityService : AccessibilityService() {

    private lateinit var clipboardManager: ClipboardManager
    private var lastText: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        clipboardManager.addPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (!text.isNullOrEmpty() && text != lastText) {
                    lastText = text
                    Log.d("ClipboardService", "Copied: $text")
                    SyncManager.sendClipboard(text)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
EOF

# 4. تحديث الستارة العائمة (عشان تتصغر وتكبر)
cat > androidApp/src/main/java/com/bfbai/superhub/service/OverlayService.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.bfbai.superhub.sync.SyncManager
import kotlinx.coroutines.*

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var mainView: LinearLayout
    private lateinit var textView: TextView
    private lateinit var params: WindowManager.LayoutParams
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var isExpanded = true // عشان نعرف الستارة مفتوحة ولا مقفولة

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
        
        SyncManager.startServer { receivedText ->
            scope.launch {
                textView.text = "مستقبل:\n$receivedText"
                // لو الستارة مقفولة، نفتحها لما يوصل نص جديد
                if (!isExpanded) toggleOverlay() 
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        params = WindowManager.LayoutParams(
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
        params.x = 50
        params.y = 100

        mainView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B1B3A.toInt())
            setPadding(30, 30, 30, 30)
            // شكل الدايرة العائمة
            layoutParams = LinearLayout.LayoutParams(150, 150) 
            gravity = Gravity.CENTER
        }

        textView = TextView(this).apply {
            text = "SuperHub\n(اسحبني)"
            setTextColor(0xFF4DE1E1.toInt())
            textSize = 12f
        }
        
        mainView.addView(textView)

        // دالة للتبديل بين التصغير والتكبير
        fun toggleSize() {
            if (isExpanded) {
                mainView.layoutParams = LinearLayout.LayoutParams(150, 150) // تصغير
                textView.text = "SH" // اختصار
            } else {
                mainView.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ) // تكبير
                textView.text = "مستقبل:\n${SyncManager.deviceId}"
            }
            isExpanded = !isExpanded
            windowManager.updateViewLayout(mainView, params)
        }

        // دالة السحب + الضغط
        mainView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) isDragging = true
                        
                        params.x = initialX + deltaX
                        params.y = initialY + deltaY
                        windowManager.updateViewLayout(mainView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            toggleSize() // لو مجرد ضغطة، نغير الحجم
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(mainView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mainView.isInitialized) {
            windowManager.removeView(mainView)
        }
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
EOF

# 5. تحديث المانيفست عشان يقرا الـ Tile والـ Overlay
cat > androidApp/src/main/AndroidManifest.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

    <application
        android:name=".SuperHubApp"
        android:label="@string/app_name"
        android:icon="@drawable/ic_launcher"
        android:allowBackup="false"
        android:usesCleartextTraffic="true">
        
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

        <!-- خدمة شريط الإشعارات -->
        <service
            android:name=".service.SuperHubTileService"
            android:exported="true"
            android:icon="@drawable/ic_hub_tile"
            android:label="SuperHub"
            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
            <intent-filter>
                <action android:name="android.service.quicksettings.action.QS_TILE" />
            </intent-filter>
        </service>
    </application>
</manifest>
EOF

# 6. إضافة دالة `addSyncItem` في ViewModel عشان تسجل النص
cat > androidApp/src/main/java/com/bfbai/superhub/MainViewModel.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SyncItem(val id: String, val text: String, val time: String)

class MainViewModel : ViewModel() {
    private val _history = MutableStateFlow<List<SyncItem>>(emptyList())
    val history: StateFlow<List<SyncItem>> = _history

    fun addSyncItem(text: String) {
        val newItem = SyncItem(id = System.currentTimeMillis().toString(), text = text, time = "الآن")
        _history.value = listOf(newItem) + _history.value
    }
}
EOF

echo "Part 12 Done."
