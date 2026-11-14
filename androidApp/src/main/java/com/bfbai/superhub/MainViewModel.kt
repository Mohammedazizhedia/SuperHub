// Built with BFB.AI
package com.bfbai.superhub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bfbai.superhub.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SyncItem(val id: String, val text: String, val time: String)

class MainViewModel : ViewModel() {
    private val _history = MutableStateFlow<List<SyncItem>>(emptyList())
    val history: StateFlow<List<SyncItem>> = _history

    init {
        // استقبال النصوص من مدير المزامنة
        viewModelScope.launch {
            SyncManager.historyEvents.collect { text ->
                addSyncItem(text)
            }
        }
    }

    fun addSyncItem(text: String) {
        val newItem = SyncItem(id = System.currentTimeMillis().toString(), text = text, time = "الآن")
        _history.value = listOf(newItem) + _history.value
    }
}
