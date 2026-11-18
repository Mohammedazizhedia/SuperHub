// Built with BFB.AI
package com.bfbai.superhub.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bfbai.superhub.service.ShelfItem
import com.bfbai.superhub.utils.HistoryManager
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HistoryScreen(onNavigateBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onNavigateBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val historyManager = remember { HistoryManager(context) }
    
    // تحميل السجل عند فتح الشاشة
    var items by remember { mutableStateOf(historyManager.loadHistory()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سجل HubSync الدائم") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        historyManager.clearHistory()
                        items = emptyList()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "مسح الكل")
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding), 
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "السجل فارغ. ابدأ بالنسخ أو المشاركة!", 
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { item ->
                    HistoryItemCard(
                        item = item, 
                        onDelete = {
                            val newList = items.toMutableList()
                            newList.remove(item)
                            items = newList
                            historyManager.saveHistory(items)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(item: ShelfItem, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), 
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.text, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${item.source} • ${formatTime(item.timestamp)}", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete, 
                    contentDescription = "حذف", 
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("hh:mm a - dd MMM", Locale.getDefault()).format(Date(timestamp))
}
