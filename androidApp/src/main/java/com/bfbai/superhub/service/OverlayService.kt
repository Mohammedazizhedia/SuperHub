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
