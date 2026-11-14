#!/usr/bin/env bash
# Built with BFB.AI — Part 14: Auto Sync & Overlay Popup
set -euo pipefail

# 1. تحديث خدمة الحافظة
cat > androidApp/src/main/java/com/bfbai/superhub/service/ClipboardAccessibilityService.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
                    
                    // 1. حفظ في السجل
                    SyncManager.addToHistory(text)
                    
                    // 2. إرسال للستارة عشان تظهر
                    SyncManager.broadcastToOverlay(text)
                    
                    // 3. إرسال لجهاز تاني (لو متصل)
                    SyncManager.sendClipboard(text)
                    
                    // 4. إظهار الستارة تلقائياً لو مخفية
                    val intent = Intent(this, OverlayService::class.java)
                    intent.action = "SHOW_OVERLAY"
                    startService(intent)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
EOF

# 2. تحديث الستارة عشان تظهر لوحدها لما يجي نص
cat > androidApp/src/main/java/com/bfbai/superhub/service/OverlayService.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
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
    private var isExpanded = true 

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
        
        // الاستماع لأي نص جديد
        scope.launch {
            SyncManager.overlayEvents.collect { receivedText ->
                textView.text = "مستقبل:\n$receivedText"
                // لو الستارة صغيرة، نكبره تلقائياً عشان يبان
                if (!isExpanded) {
                    mainView.layoutParams = LinearLayout.LayoutParams(300, 300)
                    isExpanded = true
                    windowManager.updateViewLayout(mainView, params)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // لو جالنا أمر نعرض الستارة، بنعرضها
        if (intent?.action == "SHOW_OVERLAY") {
            mainView.visibility = View.VISIBLE
        }
        return START_STICKY
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
            setPadding(40, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(300, 300) 
            gravity = Gravity.CENTER
        }

        textView = TextView(this).apply {
            text = "SuperHub\n(ID: ${SyncManager.deviceId})"
            setTextColor(0xFF4DE1E1.toInt())
            textSize = 14f
        }
        
        mainView.addView(textView)

        fun toggleSize() {
            if (isExpanded) {
                mainView.layoutParams = LinearLayout.LayoutParams(150, 150)
                textView.text = "SH"
            } else {
                mainView.layoutParams = LinearLayout.LayoutParams(300, 300)
                textView.text = "SuperHub\n(ID: ${SyncManager.deviceId})"
            }
            isExpanded = !isExpanded
            windowManager.updateViewLayout(mainView, params)
        }

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
                        if (!isDragging) toggleSize()
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

echo "Part 14 Done."
