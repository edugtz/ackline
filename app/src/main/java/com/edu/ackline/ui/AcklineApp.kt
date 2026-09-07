package com.edu.ackline.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.edu.ackline.SetupState
import com.edu.ackline.feature.onboarding.OnboardingScreen
import com.edu.ackline.feature.onboarding.rememberNotificationPermissionAction
import com.edu.ackline.feature.pairing.PairingPresentation
import com.edu.ackline.feature.pairing.PairingViewModel
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.edu.ackline.feature.detail.AlertDetailScreen
import com.edu.ackline.feature.inbox.InboxScreen
import com.edu.ackline.feature.setup.SetupScreen

private sealed interface AppScreen {
    data object Inbox : AppScreen
    data class Detail(val notificationId: String) : AppScreen
    data object Setup : AppScreen
}

@Composable
fun AcklineApp() {
    val setup by SetupState.state.collectAsState()
    val pairing: PairingViewModel = viewModel()
    val presentation by pairing.presenter.state.collectAsState()
    var enteredInbox by rememberSaveable { mutableStateOf(false) }
    rememberNotificationPermissionAction()
    val showOnboarding = setup.needsOnboarding ||
        (!enteredInbox && (presentation == PairingPresentation.Pairing || presentation == PairingPresentation.Success))
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Inbox) }

    BackHandler(enabled = !showOnboarding && currentScreen !is AppScreen.Inbox) {
        currentScreen = AppScreen.Inbox
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        if (!setup.legacyBootstrapResolved && !setup.hasConfirmedPairing) {
            Text("Preparando Ackline…")
        } else if (showOnboarding) {
            OnboardingScreen(setup, pairing, onInbox = { enteredInbox = true })
        } else when (val screen = currentScreen) {
            AppScreen.Inbox -> InboxScreen(
                onAlertClick = { notificationId ->
                    currentScreen = AppScreen.Detail(notificationId)
                },
                onSetupClick = { currentScreen = AppScreen.Setup },
            )

            is AppScreen.Detail -> AlertDetailScreen(
                notificationId = screen.notificationId,
                onBack = { currentScreen = AppScreen.Inbox },
            )

            AppScreen.Setup -> SetupScreen(
                onBack = { currentScreen = AppScreen.Inbox },
            )
        }
    }
}
