// Built with BFB.AI
package com.bfbai.superhub.sync

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object SyncManager {
    private const val TAG = "SuperHubSync"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    val deviceId = "123456"

    // رجعنا للـ String عشان نتوافق مع الكود القديم
    private val _historyEvents = MutableSharedFlow<String>()
    val historyEvents: SharedFlow<String> = _historyEvents

    fun addToHistory(text: String) {
        scope.launch { 
            _historyEvents.emit(text) 
            Log.d(TAG, "Added to history: $text")
        }
    }
}
