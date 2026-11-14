// Built with BFB.AI
package com.bfbai.superhub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.bfbai.superhub.service.OverlayService
import com.bfbai.superhub.theme.SuperHubTheme
import com.bfbai.superhub.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            SuperHubTheme {
                var showPermissionDialog by remember { mutableStateOf(false) }

                HomeScreen(
                    viewModel = viewModel,
                    onStartOverlay = {
                        if (!Settings.canDrawOverlays(this)) {
                            showPermissionDialog = true
                        } else {
                            startService(Intent(this, OverlayService::class.java))
                        }
                    }
                )

                if (showPermissionDialog) {
                    AlertDialog(
                        onDismissRequest = { showPermissionDialog = false },
                        title = { Text("صلاحية الستارة العائمة") },
                        text = { Text("محتاجين صلاحية عشان نعرض الـ Hub فوق التطبيقات التانية.") },
                        confirmButton = {
                            Button(onClick = {
                                showPermissionDialog = false
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                                startActivity(intent)
                            }) { Text("افتح الإعدادات") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPermissionDialog = false }) { Text("إلغاء") }
                        }
                    )
                }
            }
        }
    }
}
