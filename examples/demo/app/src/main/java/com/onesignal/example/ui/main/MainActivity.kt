package com.onesignal.example.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.onesignal.example.ui.theme.OsLightBackground
import com.onesignal.example.ui.theme.OneSignalTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OneSignalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = OsLightBackground
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
