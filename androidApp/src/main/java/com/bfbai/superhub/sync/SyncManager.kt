// Built with BFB.AI
package com.bfbai.superhub.sync

import android.util.Log
import com.bfbai.superhub.SyncMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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

    private val _overlayEvents = MutableSharedFlow<String>()
    val overlayEvents: SharedFlow<String> = _overlayEvents

    private val _historyEvents = MutableSharedFlow<String>()
    val historyEvents: SharedFlow<String> = _historyEvents

    fun addToHistory(text: String) {
        scope.launch { _historyEvents.emit(text) }
    }

    fun broadcastToOverlay(text: String) {
        scope.launch { _overlayEvents.emit(text) }
    }

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
                    val msg = json.decodeFromString<SyncMessage>(line)
                    if (msg.type == "clipboard") {
                        addToHistory(msg.content)
                        broadcastToOverlay(msg.content)
                    }
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
                        addToHistory(msg.content)
                        broadcastToOverlay(msg.content)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Client handler error: ${e.message}")
            }
        }
    }
}
