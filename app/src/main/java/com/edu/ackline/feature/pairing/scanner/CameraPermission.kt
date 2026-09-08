package com.edu.ackline.feature.pairing.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.edu.ackline.feature.onboarding.PermissionAction
import com.edu.ackline.feature.onboarding.findActivity
import com.edu.ackline.feature.onboarding.permissionAction

internal data class CameraPermission(val granted: Boolean, val settings: Boolean, val launch: () -> Unit, val openSettings: () -> Unit)

@Composable
internal fun rememberCameraPermission(): CameraPermission {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var denied by rememberSaveable { mutableStateOf(false) }
    var granted by remember { mutableStateOf(false) }
    var rationale by remember { mutableStateOf(false) }
    fun refresh() {
        granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        rationale = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        denied = !it
        refresh()
    }
    DisposableEffect(lifecycle, context) {
        refresh()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val settings = permissionAction(denied, rationale, true) == PermissionAction.Settings
    val openSettings = {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)))
    }
    return CameraPermission(granted, settings, launch = {
        if (settings) openSettings() else launcher.launch(Manifest.permission.CAMERA)
    }, openSettings = openSettings)
}
