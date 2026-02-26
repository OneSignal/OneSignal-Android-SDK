package com.onesignal.sdktest.ui.secondary

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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onesignal.sdktest.ui.components.DestructiveButton
import com.onesignal.sdktest.ui.theme.LightBackground
import com.onesignal.sdktest.ui.theme.OneSignalRed
import com.onesignal.sdktest.ui.theme.OneSignalTheme
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
                        CenterAlignedTopAppBar(
                            title = { Text("Secondary Activity", color = Color.White) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = OneSignalRed
                            )
                        )
                    },
                    containerColor = LightBackground
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Secondary Activity",
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
