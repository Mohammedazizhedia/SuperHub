#!/usr/bin/env bash
# Built with BFB.AI — Part 11: Connection UI & Clipboard Test
set -euo pipefail

mkdir -p androidApp/src/main/java/com/bfbai/superhub/ui
mkdir -p androidApp/src/main/java/com/bfbai/superhub/sync

# 1. تحديث شاشة Home عشان نضيف زرار الاتصال واختبار المزامنة
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
fun HomeScreen(viewModel: MainViewModel, onStartOverlay: () -> Unit) {
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
            // قسم الاتصال بجهاز تاني
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
                        label = { Text("IP Address (مثلاً 192.168.1.5)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { SyncManager.connectToDevice(ipAddress) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("اتصال")
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

# 2. تحديث MainActivity عشان نطلب صلاحية الـ Accessibility
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                var showOverlayDialog by remember { mutableStateOf(false) }
                var showAccessDialog by remember { mutableStateOf(false) }

                HomeScreen(
                    viewModel = viewModel,
                    onStartOverlay = {
                        if (!Settings.canDrawOverlays(this)) {
                            showOverlayDialog = true
                        } else {
                            startService(Intent(this, OverlayService::class.java))
                            // بعد ما الستارة تشتغل، نطلب صلاحية قراءة الحافظة
                            if (!isAccessibilityServiceEnabled()) {
                                showAccessDialog = true
                            }
                        }
                    }
                )

                if (showOverlayDialog) {
                    AlertDialog(
                        onDismissRequest = { showOverlayDialog = false },
                        title = { Text("صلاحية الستارة") },
                        text = { Text("محتاجين صلاحية عشان نعرض الستارة فوق التطبيقات.") },
                        confirmButton = {
                            Button(onClick = {
                                showOverlayDialog = false
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                                startActivity(intent)
                            }) { Text("موافق") }
                        }
                    )
                }

                if (showAccessDialog) {
                    AlertDialog(
                        onDismissRequest = { showAccessDialog = false },
                        title = { Text("صلاحية قراءة الحافظة") },
                        text = { Text("محتاجين نفعل خدمة SuperHub من إعدادات إمكانية الوصول عشان نقدر نقرا اللي بتنسخه.") },
                        confirmButton = {
                            Button(onClick = {
                                showAccessDialog = false
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }) { Text("افتح الإعدادات") }
                        }
                    )
                }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = packageName + "/" + com.bfbai.superhub.service.ClipboardAccessibilityService::class.java.canonicalName
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(service) == true
    }
}
EOF

# 3. إضافة دالة للاستماع للنص في الـ SyncManager وتحديث الستارة
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
                    Log.d(TAG, "Client connected: ${client.inetAddress}")
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
                Log.d(TAG, "Connected to $ip")
                
                val reader = BufferedReader(InputStreamReader(clientSocket!!.getInputStream()))
                while (isActive) {
                    val line = reader.readLine() ?: break
                    Log.d(TAG, "Received: $line")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection error: ${e.message}")
            }
        }
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

echo "Part 11 Done."
