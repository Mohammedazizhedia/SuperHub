#!/usr/bin/env bash
# Built with BFB.AI — Part 8: Real Sync
set -euo pipefail

mkdir -p androidApp/src/main/java/com/bfbai/superhub/sync
mkdir -p shared/src/commonMain/kotlin/com/bfbai/superhub

# 1. تحديث رسالة المزامنة في الـ Shared Module
cat > shared/src/commonMain/kotlin/com/bfbai/superhub/SyncMessage.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub

import kotlinx.serialization.Serializable

@Serializable
data class SyncMessage(
    val type: String = "clipboard",
    val content: String,
    val deviceId: String,
    val timestamp: Long = System.currentTimeMillis()
)
EOF

# 2. إنشاء مدير المزامنة (SyncManager)
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

# 3. تحديث خدمة إمكانية الوصول لتراقب النسخ وتبعته
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

echo "Part 8 Done."
