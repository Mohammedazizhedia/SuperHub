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
                if (!isExpanded) {
                    mainView.layoutParams = LinearLayout.LayoutParams(300, 300)
                    isExpanded = true
                    windowManager.updateViewLayout(mainView, params)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
