package com.onesignal.example.ui.secondary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.onesignal.example.ui.components.DemoAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onesignal.example.ui.components.DestructiveButton
import com.onesignal.example.ui.theme.OsLightBackground
import com.onesignal.example.ui.theme.OneSignalTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SecondaryActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            OneSignalTheme {
                Scaffold(
                    topBar = {
                        DemoAppBar(
                            title = { Text("Secondary Screen", color = Color.White) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                    )
                                }
                            },
                        )
                    },
                    containerColor = OsLightBackground,
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Secondary Screen",
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        DestructiveButton(
                            text = "CRASH",
                            onClick = { triggerCrash() }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        DestructiveButton(
                            text = "SIMULATE ANR (10s block)",
                            onClick = { triggerAnr() }
                        )
                    }
                }
            }
        }
    }

    private fun triggerCrash() {
        val timestamp = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
            .format(Date())
        throw RuntimeException("Test crash from OneSignal Demo App - $timestamp")
    }

    @Suppress("MagicNumber")
    private fun triggerAnr() {
        Thread.sleep(10_000)
    }
}
