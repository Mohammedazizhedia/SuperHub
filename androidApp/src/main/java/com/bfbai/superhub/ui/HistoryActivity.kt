// Built with BFB.AI
package com.bfbai.superhub.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bfbai.superhub.service.ShelfItem
import com.bfbai.superhub.utils.HistoryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HistoryRepository.init(this)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HistoryScreen(onNavigateBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val all by HistoryRepository.items.collectAsState()
    val loaded by HistoryRepository.loaded.collectAsState()
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    val shown = remember(all, query) {
        val q = query.trim()
        val base = if (q.isEmpty()) all else all.filter {
            it.text.contains(q, ignoreCase = true) || it.source.contains(q, ignoreCase = true)
        }
        base.sortedByDescending { it.isPinned }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("مسح السجل؟") },
            text = { Text("هيتمسح كل شيء ما عدا العناصر المثبّتة.") },
            confirmButton = {
                TextButton(onClick = {
                    HistoryRepository.clearUnpinned()
                    confirmClear = false
                }) { Text("مسح") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("إلغاء") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سجل HubSync") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    if (all.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "مسح الكل")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true,
                placeholder = { Text("ابحث في السجل") }
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (!loaded) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (shown.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (all.isEmpty()) "السجل فارغ. ابدأ بالنسخ أو المشاركة!" else "مفيش نتائج",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        items = shown,
                        key = { "${it.timestamp}_${it.text.hashCode()}" }
                    ) { item ->
                        HistoryItemCard(
                            item = item,
                            onCopy = {
                                HistoryRepository.ignoreClipUntil = System.currentTimeMillis() + 1500
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("hubsync", item.text))
                                Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
                            },
                            onPin = { HistoryRepository.togglePin(item) },
                            onDelete = { HistoryRepository.remove(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    item: ShelfItem,
    onCopy: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCopy() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.text,
                    maxLines = 3,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${item.source} • ${formatTime(item.timestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onPin) {
                Text(
                    text = if (item.isPinned) "★" else "☆",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
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
