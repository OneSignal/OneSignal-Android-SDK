package com.onesignal.example.ui.main

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.onesignal.example.ui.theme.OsLightBackground
import com.onesignal.example.ui.theme.OneSignalTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Force light status-bar icons because the app bar is OsPrimary red.
        // Navigation bar follows system theme (light scrim on the OsLightBackground page).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            OneSignalTheme {
                Surface(
                    // testTagsAsResourceId=true exposes every Modifier.testTag(...)
                    // in the tree as an Android resource-id, which is what
                    // UiAutomator2 / Appium's `id=foo` selector resolves against.
                    // Without this, Compose only surfaces testTag as a Semantics
                    // property invisible to UiAutomator, and Appium E2E specs
                    // that look up elements via `id=` time out.
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true },
                    color = OsLightBackground
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
