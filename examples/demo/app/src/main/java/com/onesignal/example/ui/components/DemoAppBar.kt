package com.onesignal.example.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        CenterAlignedTopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = Color.White,
                titleContentColor = Color.White,
                actionIconContentColor = Color.White,
            ),
        )
    }
}
