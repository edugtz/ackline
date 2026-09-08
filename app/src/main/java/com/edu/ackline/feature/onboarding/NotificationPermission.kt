package com.edu.ackline.feature.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.edu.ackline.SetupState

internal enum class PermissionAction { Request, Settings }
internal fun permissionAction(denied: Boolean, rationale: Boolean, runtimePermission: Boolean): PermissionAction =
    if (!runtimePermission || (denied && !rationale)) PermissionAction.Settings else PermissionAction.Request

internal data class NotificationPermissionAction(val label: String, val launch: () -> Unit, val openSettings: () -> Unit)

/**
 * Explicit actions only; refresh shared truth on Settings return. Android's public APIs cannot
 * distinguish first request from permanent denial after a fresh process when rationale is false.
 * Every permission surface therefore also offers Settings directly, without a prompt counter.
 */
@Composable
internal fun rememberNotificationPermissionAction(onGranted: () -> Unit = {}): NotificationPermissionAction {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var denied by rememberSaveable { mutableStateOf(false) }
    var rationale by rememberSaveable { mutableStateOf(false) }
    var runtimeGranted by rememberSaveable { mutableStateOf(false) }
    val grantedCallback by rememberUpdatedState(onGranted)
    val runtimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    fun refresh() {
        SetupState.onNotificationPermissionChanged(hasNotificationPermission(context))
        runtimeGranted = runtimePermission && ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        rationale = runtimePermission && activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.POST_NOTIFICATIONS,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        refresh()
        if (granted && SetupState.state.value.notificationGranted) grantedCallback()
    }
    DisposableEffect(lifecycle, context) {
        refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val action = permissionAction(denied, rationale, runtimePermission && !runtimeGranted)
    val openSettings = {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    return NotificationPermissionAction(
        label = when {
            action == PermissionAction.Settings -> "Abrir ajustes de Android"
            denied || rationale -> "Volver a solicitar"
            else -> "Permitir notificaciones"
        },
        launch = {
            if (action == PermissionAction.Settings) {
                openSettings()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        openSettings = openSettings,
    )
}

private fun hasNotificationPermission(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
