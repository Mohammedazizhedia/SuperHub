// Built with BFB.AI
package com.bfbai.superhub.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewModelStore
import com.bfbai.superhub.sync.ClipboardItem
import com.bfbai.superhub.sync.SyncManager
import kotlinx.coroutines.*

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var composeView: ComposeView? = null
    private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = androidx.savedstate.SavedStateRegistryController.create(this)

    override val lifecycle: androidx.lifecycle.Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
        savedStateRegistryController.performRestore(null)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
    }

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

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            
            setContent {
                val history by SyncManager.clipboardHistory.collectAsState()
                OverlayContent(
                    history = history, 
                    onDelete = { SyncManager.removeFromHistory(it) }, 
                    deviceId = SyncManager.deviceId
                )
            }
        }

        composeView?.setOnTouchListener(object : View.OnTouchListener {
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
                        windowManager.updateViewLayout(composeView, params)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(composeView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
        if (composeView != null) {
            windowManager.removeView(composeView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayContent(history: List<ClipboardItem>, onDelete: (ClipboardItem) -> Unit, deviceId: String) {
    Surface(
        modifier = Modifier.width(300.dp).heightIn(max = 450.dp), // تحكم في حجم الستارة
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0B1B3A),
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("SuperHub (ID: $deviceId)", color = Color(0xFF4DE1E1), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (history.isEmpty()) {
                Text("مفيش نصوص محفوظة لسه", color = Color.Gray, fontSize = 14.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(history) { item ->
                        SwipeToDismissBox(
                            state = rememberSwipeToDismissBoxState(),
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFFFF5A5F), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 20.dp), 
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.White)
                                }
                            },
                            onDismiss = { onDelete(item) }
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF13264D)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(item.text, color = Color.White, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(item.sourceApp, color = Color(0xFF4DE1E1), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
