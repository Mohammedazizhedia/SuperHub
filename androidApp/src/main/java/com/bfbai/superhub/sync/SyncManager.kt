// Built with BFB.AI
package com.bfbai.superhub.sync

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ClipboardItem(
    val text: String,
    val sourceApp: String = "من هاتفك",
    val timestamp: Long = System.currentTimeMillis()
)

object SyncManager {
    private const val TAG = "SuperHubSync"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    val deviceId = "123456"

    // قائمة بكل النصوص اللي اتنسخت
    private val _clipboardHistory = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val clipboardHistory: StateFlow<List<ClipboardItem>> = _clipboardHistory

    fun addToHistory(item: ClipboardItem) {
        scope.launch {
            val currentList = _clipboardHistory.value.toMutableList()
            // لو النص مش موجود، نضيفه في الأول
            if (currentList.none { it.text == item.text }) {
                currentList.add(0, item)
                if (currentList.size > 20) currentList.removeAt(currentList.size - 1) // آخر 20 نص
                _clipboardHistory.value = currentList
            }
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
