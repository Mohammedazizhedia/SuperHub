#!/usr/bin/env bash
# Built with BFB.AI — Part 9: Draggable Overlay
set -euo pipefail

mkdir -p androidApp/src/main/java/com/bfbai/superhub/service

# تحديث خدمة الستارة عشان تدعم السحب
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
import android.widget.TextView
import com.bfbai.superhub.sync.SyncManager
import kotlinx.coroutines.*

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var textView: TextView
    private lateinit var params: WindowManager.LayoutParams
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
        
        SyncManager.startServer { receivedText ->
            scope.launch {
                textView.text = "مستقبل:\n$receivedText"
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

        textView = TextView(this).apply {
            text = "SuperHub Hub\n(اسحبني)\nID: ${SyncManager.deviceId}"
            setBackgroundColor(0xFF0B1B3A.toInt())
            setTextColor(0xFF4DE1E1.toInt())
            setPadding(40, 40, 40, 40)
        }

        // إضافة مستمع اللمس لتفعيل السحب
        textView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayView, params)
                        return true
                    }
                }
                return false
            }
        })

        overlayView = textView
        windowManager.addView(overlayView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
EOF

echo "Part 9 Done."
