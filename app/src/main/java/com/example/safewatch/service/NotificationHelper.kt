package com.example.safewatch.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.safewatch.MainActivity
import com.example.safewatch.R
import com.google.android.gms.maps.model.LatLng

object NotificationHelper {
    const val CHANNEL_ALERTS_ID = "safewatch_alerts_channel"
    const val CHANNEL_LOCATION_ID = "safewatch_location_channel"
    private const val CHANNEL_NAME = "SafeWatch Alerts"

    // Duplicate detection variables
    private var lastNotifiedLocation: LatLng? = null
    private var lastNotifiedAlertType: String? = null

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Emergency SOS and Geo-fence Safety Alerts"
                enableVibration(true)
            }

            val locationChannel = NotificationChannel(
                CHANNEL_LOCATION_ID,
                "SafeWatch Location Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background periodic location monitoring"
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(alertChannel)
            notificationManager.createNotificationChannel(locationChannel)
        }
    }

    fun sendAlertNotification(
        context: Context,
        title: String,
        message: String,
        alertType: String,
        location: LatLng
    ): Boolean {
        createNotificationChannels(context)

        // Check for duplicate location alert (same location & same alert type)
        val isDuplicateLocation = lastNotifiedLocation?.let { lastLoc ->
            val latDiff = Math.abs(lastLoc.latitude - location.latitude)
            val lngDiff = Math.abs(lastLoc.longitude - location.longitude)
            // Consider locations within ~10 meters (approx 0.0001 deg) as the same location
            latDiff < 0.0001 && lngDiff < 0.0001
        } ?: false

        if (isDuplicateLocation && alertType == lastNotifiedAlertType) {
            android.util.Log.d("NotificationHelper", "Suppressed duplicate alert notification for location: $location ($alertType)")
            return false
        }

        // Update last notified values
        lastNotifiedLocation = location
        lastNotifiedAlertType = alertType

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "alerts")
            putExtra("alert_type", alertType)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(1000, 1000, 1000, 1000))

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), builder.build())
        return true
    }
}
