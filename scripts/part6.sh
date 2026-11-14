#!/usr/bin/env bash
# Built with BFB.AI — Part 6: Core Logic & UI
set -euo pipefail

mkdir -p androidApp/src/main/java/com/bfbai/superhub/ui androidApp/src/main/java/com/bfbai/superhub/service androidApp/src/main/res/drawable

# 1. Main ViewModel (State Management)
cat > androidApp/src/main/java/com/bfbai/superhub/MainViewModel.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SyncItem(val id: String, val text: String, val time: String)

class MainViewModel : ViewModel() {
    private val _history = MutableStateFlow<List<SyncItem>>(emptyList())
    val history: StateFlow<List<SyncItem>> = _history

    fun addSyncItem(text: String) {
        val newItem = SyncItem(id = System.currentTimeMillis().toString(), text = text, time = "الآن")
        _history.value = listOf(newItem) + _history.value
    }
}
EOF

# 2. Home Screen UI
cat > androidApp/src/main/java/com/bfbai/superhub/ui/HomeScreen.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bfbai.superhub.MainViewModel
import com.bfbai.superhub.SyncItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val history by viewModel.history.collectAsState()

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
            FloatingActionButton(onClick = { viewModel.addSyncItem("نص تجريبي ${history.size + 1}") }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
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
            DeviceCard("جهازي (هذا الهاتف)", true)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("آخر ما تمت مزامنته", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
            
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("مفيش حاجة هنا لسه. اضغط + لتجربة المزامنة.", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(history) { item ->
                        SyncItemCard(item)
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(name: String, isActive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).background(if (isActive) Color(0xFF2ECC71) else Color.Gray, RoundedCornerShape(50)))
            Spacer(modifier = Modifier.width(12.dp))
            Text(name, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SyncItemCard(item: SyncItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.text, fontWeight = FontWeight.Medium)
            Text(item.time, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
EOF

# 3. Update MainActivity
cat > androidApp/src/main/java/com/bfbai/superhub/MainActivity.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.bfbai.superhub.theme.SuperHubTheme
import com.bfbai.superhub.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            SuperHubTheme {
                HomeScreen(viewModel)
            }
        }
    }
}
EOF

# 4. Foreground Service
cat > androidApp/src/main/java/com/bfbai/superhub/service/ForegroundSyncService.kt <<'EOF'
// Built with BFB.AI
package com.bfbai.superhub.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ForegroundSyncService : Service() {
    private val CHANNEL_ID = "SuperHubSyncChannel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SuperHub نشط")
            .setContentText("جاري المزامنة في الخلفية...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .build()
        
        startForeground(1, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Sync Service Channel", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
EOF

echo "Part 6 Done."
