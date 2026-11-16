package com.tprobot.ovejero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tprobot.ovejero.core.theme.OvejeroTheme
import com.tprobot.ovejero.presentation.OvejeroApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OvejeroTheme {
                OvejeroApp()
            }
        }
    }
}
