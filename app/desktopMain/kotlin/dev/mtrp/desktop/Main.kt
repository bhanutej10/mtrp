package dev.mtrp.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.runBlocking

/**
 * Desktop application entry point.
 * Author: K. Bhanutej
 */
fun main() = application {
    runBlocking {
        DesktopMtrpSdk.init()
    }

    val windowState = rememberWindowState(width = 960.dp, height = 680.dp)

    Window(
        onCloseRequest = {
            DesktopMtrpSdk.stop()
            exitApplication()
        },
        title  = "MTRP — Multi Transport Relay Protocol",
        state  = windowState
    ) {
        DesktopApp(api = DesktopMtrpSdk.api)
    }
}
