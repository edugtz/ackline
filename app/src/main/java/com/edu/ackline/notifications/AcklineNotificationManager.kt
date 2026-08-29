package com.edu.ackline.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Small Phase 1 boundary for creating native Android notifications.
 *
 * It intentionally does not persist alerts, deduplicate deliveries, or attach
 * actions. Those behaviors belong to later phases.
 */
object AcklineNotificationManager {

    private data class ChannelDefinition(
        val id: String,
        val name: String,
        val importance: Int,
    )

    private val channelDefinitions = mapOf(
        "remember" to ChannelDefinition(
            id = REMEMBER_CHANNEL_ID,
            name = "Ackline · Remember",
            importance = NotificationManager.IMPORTANCE_LOW,
        ),
        "important" to ChannelDefinition(
            id = IMPORTANT_CHANNEL_ID,
            name = "Ackline · Important",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
        ),
        "urgent" to ChannelDefinition(
            id = URGENT_CHANNEL_ID,
            name = "Ackline · Urgent",
            importance = NotificationManager.IMPORTANCE_HIGH,
        ),
    )

    /** Returns the stable app-owned channel ID for a validated level. */
    internal fun channelIdForLevel(level: String): String? =
        channelDefinitions[level]?.id

    /** Returns the Android channel importance for a validated level. */
    internal fun channelImportanceForLevel(level: String): Int? =
        channelDefinitions[level]?.importance

    /**
     * Posts one native notification and reports whether the post was attempted
     * successfully. A false result means Android notification posting was not
     * available, usually because notification permission is absent.
     */
    fun show(
        context: Context,
        notificationId: String,
        level: String,
        title: String,
        message: String,
    ): Boolean {
        val definition = channelDefinitions[level] ?: return false
        val appContext = context.applicationContext
        val notificationManager =
            appContext.getSystemService(NotificationManager::class.java) ?: return false

        notificationManager.createNotificationChannels(
            channelDefinitions.values.map { definition ->
                NotificationChannel(
                    definition.id,
                    definition.name,
                    definition.importance,
                )
            },
        )

        if (!canPostNotifications(appContext, notificationManager)) {
            Log.w(TAG, "notification posting unavailable: permission_or_settings")
            return false
        }

        val notification = Notification.Builder(appContext, definition.id)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()

        return try {
            notificationManager.notify(notificationId.hashCode(), notification)
            Log.i(
                TAG,
                "notification posted: " +
                    "notification_id=${notificationId.forDiagnosticLog()} level=$level",
            )
            true
        } catch (_: SecurityException) {
            Log.w(TAG, "notification posting rejected by Android")
            false
        }
    }

    private fun canPostNotifications(
        context: Context,
        notificationManager: NotificationManager,
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return notificationManager.areNotificationsEnabled()
    }

    private fun String.forDiagnosticLog(): String =
        take(MAX_DIAGNOSTIC_VALUE_LENGTH)
            .replace('\n', ' ')
            .replace('\r', ' ')

    private const val TAG = "AcklineNotifications"
    private const val MAX_DIAGNOSTIC_VALUE_LENGTH = 128
    internal const val REMEMBER_CHANNEL_ID = "ackline_remember"
    internal const val IMPORTANT_CHANNEL_ID = "ackline_important"
    internal const val URGENT_CHANNEL_ID = "ackline_urgent"
}
