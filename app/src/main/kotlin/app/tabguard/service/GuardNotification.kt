package app.tabguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.tabguard.R
import app.tabguard.ui.MainActivity

/**
 * Persistent "protection is on" notification. Running as a foreground service keeps
 * aggressive OEM firmwares (Tecno/Infinix/Xiaomi…) from killing the accessibility service.
 */
object GuardNotification {
    const val CHANNEL_ID = "guard"
    const val ID = 1

    fun build(context: Context): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.guard_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.guard_title))
            .setContentText(context.getString(R.string.guard_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }
}
