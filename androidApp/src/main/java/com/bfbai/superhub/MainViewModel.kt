// Built with BFB.AI
package com.bfbai.superhub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bfbai.superhub.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SyncItem(val id: String, val text: String, val time: String)

class MainViewModel : ViewModel() {
    private val _history = MutableStateFlow<List<SyncItem>>(emptyList())
    val history: StateFlow<List<SyncItem>> = _history

    init {
        // ربط القائمة الجديدة (clipboardHistory) بالسجل
        viewModelScope.launch {
            SyncManager.clipboardHistory.collect { items ->
                val syncItems = items.map { 
                    SyncItem(
                        id = it.timestamp.toString(),
                        text = it.text,
                        time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it.timestamp))
                    )
                }
                _history.value = syncItems
            }
        }
    }

    fun addSyncItem(text: String) {
        SyncManager.addToHistory(text)
    }
}
