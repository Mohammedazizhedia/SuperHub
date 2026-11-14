#!/usr/bin/env bash
# Built with BFB.AI — Part 13: Fixing Core Files (Sync, Overlay, HomeScreen)
set -euo pipefail

mkdir -p androidApp/src/main/java/com/bfbai/superhub/sync
mkdir -p androidApp/src/main/java/com/bfbai/superhub/service
mkdir -p androidApp/src/main/java/com/bfbai/superhub/ui

# 1. SyncManager
cat > androidApp/src/main/java/com/bfbai/superhub/sync/SyncManager.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub.sync

import android.util.Log
import com.bfbai.superhub.SyncMessage
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

object SyncManager {
    private const val PORT = 8080
    private const val TAG = "SuperHubSync"
    
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var writer: PrintWriter? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }
    
    val deviceId = UUID.randomUUID().toString().take(6)

    fun startServer(onMessageReceived: (String) -> Unit) {
        scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                Log.d(TAG, "Server started on port $PORT")
                
                while (isActive) {
                    val client = serverSocket?.accept() ?: break
                    handleClient(client, onMessageReceived)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
            }
        }
    }

    fun connectToDevice(ip: String) {
        scope.launch {
            try {
                clientSocket = Socket(ip, PORT)
                writer = PrintWriter(clientSocket!!.getOutputStream(), true)
                
                val reader = BufferedReader(InputStreamReader(clientSocket!!.getInputStream()))
                while (isActive) {
                    val line = reader.readLine() ?: break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection error: ${e.message}")
            }
        }
    }

    fun testLoopback(text: String, onReceived: (String) -> Unit) {
        onReceived(text)
    }

    fun sendClipboard(text: String) {
        scope.launch {
            try {
                val message = SyncMessage(content = text, deviceId = deviceId)
                val jsonString = json.encodeToString(message)
                writer?.println(jsonString)
                Log.d(TAG, "Sent: $text")
            } catch (e: Exception) {
                Log.e(TAG, "Send error: ${e.message}")
            }
        }
    }

    private fun handleClient(client: Socket, onMessageReceived: (String) -> Unit) {
        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val out = PrintWriter(client.getOutputStream(), true)
                if (writer == null) writer = out
                
                while (isActive) {
                    val line = reader.readLine() ?: break
                    val msg = json.decodeFromString<SyncMessage>(line)
                    if (msg.type == "clipboard") {
                        onMessageReceived(msg.content)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Client handler error: ${e.message}")
            }
        }
    }
}
EOF

# 2. OverlayService (تم تصحيح toggleOverlay إلى toggleSize)
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
        
        SyncManager.startServer { receivedText ->
            scope.launch {
                textView.text = "مستقبل:\n$receivedText"
                if (!isExpanded) toggleSize() 
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
            layoutParams = LinearLayout.LayoutParams(200, 200) 
            gravity = Gravity.CENTER
        }

        textView = TextView(this).apply {
            text = "SuperHub\n(ID: ${SyncManager.deviceId})"
            setTextColor(0xFF4DE1E1.toInt())
            textSize = 12f
        }
        
        mainView.addView(textView)

        fun toggleSize() {
            if (isExpanded) {
                mainView.layoutParams = LinearLayout.LayoutParams(150, 150)
                textView.text = "SH"
            } else {
                mainView.layoutParams = LinearLayout.LayoutParams(200, 200)
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

# 3. HomeScreen (مع زرار الاختبار)
cat > androidApp/src/main/java/com/bfbai/superhub/ui/HomeScreen.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bfbai.superhub.MainViewModel
import com.bfbai.superhub.SyncItem
import com.bfbai.superhub.sync.SyncManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    isOverlayVisible: Boolean,
    onToggleOverlay: () -> Unit
) {
    val history by viewModel.history.collectAsState()
    var ipAddress by remember { mutableStateOf("") }

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
            FloatingActionButton(
                onClick = onToggleOverlay,
                containerColor = if (isOverlayVisible) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (isOverlayVisible) Icons.Default.Clear else Icons.Default.Add,
                    contentDescription = if (isOverlayVisible) "إخفاء الستارة" else "إظهار الستارة"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("الاتصال بجهاز (اختبار)", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        label = { Text("IP Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { SyncManager.connectToDevice(ipAddress) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("اتصال")
                        }
                        Button(
                            onClick = { 
                                SyncManager.testLoopback("نص تجريبي من التطبيق!") { received ->
                                    viewModel.addSyncItem(received)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("اختبار")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Text("آخر ما تمت مزامنته", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
            
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("انسخ أي نص من أي تطبيق تاني، وشوف الستارة هتتغير ولا لأ.", color = Color.Gray)
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

echo "Part 13 Done."
