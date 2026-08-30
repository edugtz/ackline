package com.edu.ackline.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.edu.ackline.MainActivity
import com.edu.ackline.ack.AcknowledgeReceiver
import com.edu.ackline.model.AlertLevel

/**
 * Small Phase 1 boundary for creating native Android notifications.
 *
 * It intentionally does not persist alerts or deduplicate deliveries. Those
 * behaviors belong to the repository and Room layers.
 */
object AcklineNotificationManager {

    private data class ChannelDefinition(
        val id: String,
        val name: String,
        val importance: Int,
    )

    private val channelDefinitions = mapOf(
        AlertLevel.REMEMBER to ChannelDefinition(
            id = REMEMBER_CHANNEL_ID,
            name = "Ackline · Remember",
            importance = NotificationManager.IMPORTANCE_LOW,
        ),
        AlertLevel.IMPORTANT to ChannelDefinition(
            id = IMPORTANT_CHANNEL_ID,
            name = "Ackline · Important",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
        ),
        AlertLevel.URGENT to ChannelDefinition(
            id = URGENT_CHANNEL_ID,
            name = "Ackline · Urgent",
            importance = NotificationManager.IMPORTANCE_HIGH,
        ),
    )

    /** Returns the stable app-owned channel ID for a validated level. */
    internal fun channelIdForLevel(level: AlertLevel): String =
        channelDefinitions.getValue(level).id

    /** Compatibility helper for the Phase 1 channel mapping tests. */
    internal fun channelIdForLevel(level: String): String? =
        AlertLevel.fromWireValue(level)?.let(::channelIdForLevel)

    /** Returns the Android channel importance for a validated level. */
    internal fun channelImportanceForLevel(level: AlertLevel): Int =
        channelDefinitions.getValue(level).importance

    /** Compatibility helper for the Phase 1 channel mapping tests. */
    internal fun channelImportanceForLevel(level: String): Int? =
        AlertLevel.fromWireValue(level)?.let(::channelImportanceForLevel)

    /**
     * Posts one native notification and reports whether the post was attempted
     * successfully. A false result means Android notification posting was not
     * available, usually because notification permission is absent.
     */
    fun show(
        context: Context,
        notificationId: String,
        level: AlertLevel,
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
            .setContentIntent(contentPendingIntent(appContext, notificationId))
            .setAutoCancel(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(appContext, android.R.drawable.ic_menu_view),
                    "Visto",
                    acknowledgePendingIntent(appContext, notificationId),
                ).build(),
            )
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

    /** Compatibility overload for callers that still hold a validated wire value. */
    fun show(
        context: Context,
        notificationId: String,
        level: String,
        title: String,
        message: String,
    ): Boolean =
        AlertLevel.fromWireValue(level)?.let {
            show(
                context = context,
                notificationId = notificationId,
                level = it,
                title = title,
                message = message,
            )
        } ?: false

    fun cancel(context: Context, notificationId: String) {
        context.applicationContext
            .getSystemService(NotificationManager::class.java)
            ?.cancel(notificationId.hashCode())
    }

    private fun acknowledgePendingIntent(
        context: Context,
        notificationId: String,
    ): PendingIntent {
        val intent = Intent(context, AcknowledgeReceiver::class.java).apply {
            action = AcknowledgeReceiver.ACTION_ACKNOWLEDGE
            putExtra(AcknowledgeReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            data = Uri.Builder()
                .scheme("ackline")
                .authority("acknowledge")
                .appendPath(notificationId)
                .build()
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId.hashCode(),
            intent,
            pendingIntentFlags(),
        )
    }

    private fun contentPendingIntent(
        context: Context,
        notificationId: String,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            notificationId.hashCode(),
            intent,
            pendingIntentFlags(),
        )
    }

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

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
