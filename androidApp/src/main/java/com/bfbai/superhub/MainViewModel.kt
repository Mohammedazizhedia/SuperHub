// Built with BFB.AI
package com.bfbai.superhub

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SyncItem(val id: String, val text: String, val time: String)

class MainViewModel : ViewModel() {
    private val _history = MutableStateFlow<List<SyncItem>>(emptyList())
    val history: StateFlow<List<SyncItem>> = _history

    fun addSyncItem(text: String) {
        val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val newItem = SyncItem(
            id = System.currentTimeMillis().toString(), 
            text = text, 
            time = currentTime
        )
        _history.value = listOf(newItem) + _history.value
    }
}
