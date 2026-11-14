// Built with BFB.AI
package com.bfbai.superhub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                var showOverlayDialog by remember { mutableStateOf(false) }
                var showAccessDialog by remember { mutableStateOf(false) }

                HomeScreen(
                    viewModel = viewModel,
                    onStartOverlay = {
                        if (!Settings.canDrawOverlays(this)) {
                            showOverlayDialog = true
                        } else {
                            startService(Intent(this, OverlayService::class.java))
                            // طلب صلاحية قراءة الحافظة
                            if (!isAccessibilityServiceEnabled()) {
                                showAccessDialog = true
                            }
                        }
                    }
                )

                if (showOverlayDialog) {
                    AlertDialog(
                        onDismissRequest = { showOverlayDialog = false },
                        title = { Text("صلاحية الستارة") },
                        text = { Text("محتاجين صلاحية عشان نعرض الستارة فوق التطبيقات.") },
                        confirmButton = {
                            Button(onClick = {
                                showOverlayDialog = false
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                                startActivity(intent)
                            }) { Text("موافق") }
                        }
                    )
                }

                if (showAccessDialog) {
                    AlertDialog(
                        onDismissRequest = { showAccessDialog = false },
                        title = { Text("صلاحية قراءة الحافظة") },
                        text = { Text("محتاجين نفعل خدمة SuperHub من إعدادات إمكانية الوصول.") },
                        confirmButton = {
                            Button(onClick = {
                                showAccessDialog = false
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }) { Text("افتح الإعدادات") }
                        }
                    )
                }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = packageName + "/" + com.bfbai.superhub.service.ClipboardAccessibilityService::class.java.canonicalName
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(service) == true
    }
}
