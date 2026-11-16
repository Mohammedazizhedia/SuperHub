// Built with BFB.AI
package com.bfbai.superhub.sync

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

data class ClipboardItem(
    val text: String,
    val sourceApp: String = "من هاتفك",
    val timestamp: Long = System.currentTimeMillis()
)

object SyncManager {
    private const val TAG = "SuperHubSync"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    val deviceId = "123456"

    // قائمة بكل النصوص اللي اتنسخت (للسجل)
    private val _clipboardHistory = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val clipboardHistory: StateFlow<List<ClipboardItem>> = _clipboardHistory

    // للأحداث اللحظية (للستارة العائمة القديمة)
    private val _overlayEvents = MutableSharedFlow<String>()
    val overlayEvents: SharedFlow<String> = _overlayEvents

    fun addToHistory(text: String) {
        scope.launch {
            val currentList = _clipboardHistory.value.toMutableList()
            if (currentList.none { it.text == text }) {
                currentList.add(0, ClipboardItem(text))
                if (currentList.size > 20) currentList.removeAt(currentList.size - 1)
                _clipboardHistory.value = currentList
            }
        }
    }

    fun broadcastToOverlay(text: String) {
        scope.launch { 
            _overlayEvents.emit(text) 
        }
    }

    fun removeFromHistory(item: ClipboardItem) {
        scope.launch {
            val currentList = _clipboardHistory.value.toMutableList()
            currentList.remove(item)
            _clipboardHistory.value = currentList
        }
    }
}
