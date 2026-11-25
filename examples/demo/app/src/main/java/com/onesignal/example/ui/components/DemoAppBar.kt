package com.onesignal.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.onesignal.example.ui.theme.DemoLayout
import com.onesignal.example.ui.theme.OsPrimary

/**
 * Branded app bar with a visible drop shadow below the header.
 * [Surface.shadowElevation] is used instead of [Modifier.shadow] so the shadow
 * renders reliably inside [Scaffold]'s topBar slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoAppBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = OsPrimary,
        shadowElevation = DemoLayout.appBarElevation,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                .height(DemoLayout.appBarHeight),
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
                    navigationIcon()
                }
                Box(modifier = Modifier.align(Alignment.Center)) {
                    title()
                }
            }
        }
    }
}
