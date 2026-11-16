// Built with BFB.AI
package com.bfbai.superhub.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bfbai.superhub.MainViewModel
import com.bfbai.superhub.SyncItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    isOverlayVisible: Boolean,
    onToggleOverlay: () -> Unit
) {
    val history by viewModel.history.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SuperHub", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* Settings */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onToggleOverlay,
                containerColor = if (isOverlayVisible) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (isOverlayVisible) Icons.Default.Clear else Icons.Default.Add,
                    contentDescription = if (isOverlayVisible) "إخفاء الستارة" else "إظهار الستارة"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("الأجهزة المتصلة", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(Color(0xFF2ECC71), RoundedCornerShape(50)))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("جهازي (هذا الهاتف)", fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Text("آخر ما تمت مزامنته (اضغط للنسخ)", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
            
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("دوس على زرار + عشان تطلع الستارة.", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(history) { item ->
                        SyncItemCard(item) {
                            // زرار النسخ اللي كان ناقص
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("SuperHub", item.text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "تم النسخ!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SyncItemCard(item: SyncItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.text, fontWeight = FontWeight.Medium)
                Text(item.time, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
            }
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "نسخ",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
