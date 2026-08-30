package com.edu.ackline.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.edu.ackline.feature.detail.AlertDetailScreen
import com.edu.ackline.feature.inbox.InboxScreen
import com.edu.ackline.feature.setup.SetupScreen
import com.edu.ackline.model.Alert

private sealed interface AppScreen {
    data object Inbox : AppScreen
    data class Detail(val alert: Alert) : AppScreen
    data object Setup : AppScreen
}

@Composable
fun AcklineApp() {
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Inbox) }

    BackHandler(enabled = currentScreen !is AppScreen.Inbox) {
        currentScreen = AppScreen.Inbox
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val screen = currentScreen) {
            AppScreen.Inbox -> InboxScreen(
                onAlertClick = { alert -> currentScreen = AppScreen.Detail(alert) },
                onSetupClick = { currentScreen = AppScreen.Setup },
            )

            is AppScreen.Detail -> AlertDetailScreen(
                alert = screen.alert,
                onBack = { currentScreen = AppScreen.Inbox },
            )

            AppScreen.Setup -> SetupScreen(
                onBack = { currentScreen = AppScreen.Inbox },
            )
        }
    }
}
