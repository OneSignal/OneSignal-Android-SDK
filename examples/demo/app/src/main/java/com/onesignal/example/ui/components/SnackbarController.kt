package com.onesignal.example.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.onesignal.example.ui.theme.DemoLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * UI-layer snackbar controller per sdk-shared/demo/build.md Prompt 7.6.
 * Feedback messages are owned by the UI layer, never by the ViewModel.
 * Replace-on-show: dismisses any visible snackbar and resets the
 * [DemoLayout.toastDurationMs] timer on every call.
 */
class SnackbarController(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    private var showJob: Job? = null
    private var dismissJob: Job? = null

    fun show(message: String) {
        showJob?.cancel()
        dismissJob?.cancel()
        hostState.currentSnackbarData?.dismiss()
        dismissJob = scope.launch {
            delay(DemoLayout.toastDurationMs)
            hostState.currentSnackbarData?.dismiss()
        }
        showJob = scope.launch {
            hostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Indefinite,
            )
        }
    }
}

@Composable
fun rememberSnackbarController(hostState: SnackbarHostState): SnackbarController {
    val scope = rememberCoroutineScope()
    return remember(hostState, scope) { SnackbarController(hostState, scope) }
}

val LocalSnackbarController = compositionLocalOf<SnackbarController> {
    error("No SnackbarController provided. Wrap your UI in CompositionLocalProvider(LocalSnackbarController provides ...).")
}
