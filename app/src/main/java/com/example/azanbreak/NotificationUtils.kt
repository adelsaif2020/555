package com.example.azanbreak

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import androidx.core.app.NotificationCompat

object NotificationUtils {
    const val CHANNEL_ID = "azan_channel"
    const val CHANNEL_NAME = "أذان وبريك"

    fun createChannelIfNeeded(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            ch.description = "قنوات إشعارات أذان وبريك"
            ch.enableLights(true)
            ch.lightColor = Color.BLUE
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
        }
    }

    fun buildNotification(context: Context, title: String, body: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm) // ensure drawable exists
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
    }
}
