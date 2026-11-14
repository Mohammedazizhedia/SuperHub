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
