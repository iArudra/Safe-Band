package com.example.safewatch.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.safewatch.R
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.*

class LocationFetchService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var fetchJob: Job? = null

    private val databaseUrl = "https://safe-band-7659f-default-rtdb.asia-southeast1.firebasedatabase.app"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        start5MinFetchLoop()
        listenToLiveAlerts()
        return START_STICKY
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_LOCATION_ID)
            .setContentTitle("SafeWatch Active Monitoring")
            .setContentText("Monitoring location every 5 minutes for safety alerts...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun start5MinFetchLoop() {
        fetchJob?.cancel()
        fetchJob = serviceScope.launch {
            while (isActive) {
                try {
                    fetchLocationAndCheckAlerts()
                } catch (e: Exception) {
                    Log.e("LocationFetchService", "Error fetching location: ${e.message}", e)
                }
                // Fetch every 5 minutes
                delay(5 * 60 * 1000L)
            }
        }
    }

    private fun fetchLocationAndCheckAlerts() {
        val database = FirebaseDatabase.getInstance(databaseUrl)
        
        // Primary path: devices/safeband_001
        database.getReference("devices/safeband_001").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                processSnapshotAndCheckAlert(snapshot)
            } else {
                // Fallback path: device/location
                database.getReference("device/location").get().addOnSuccessListener { fallbackSnap ->
                    if (fallbackSnap.exists()) {
                        processSnapshotAndCheckAlert(fallbackSnap)
                    }
                }
            }
        }
    }

    private fun processSnapshotAndCheckAlert(snapshot: DataSnapshot) {
        val lat = snapshot.child("latitude").value?.toString()?.toDoubleOrNull()
            ?: snapshot.child("lat").value?.toString()?.toDoubleOrNull() ?: 17.5062
        val lng = snapshot.child("longitude").value?.toString()?.toDoubleOrNull()
            ?: snapshot.child("lng").value?.toString()?.toDoubleOrNull() ?: 81.648
        val status = snapshot.child("status").value?.toString() ?: "online"

        val location = LatLng(lat, lng)

        // Check if there's an emergency status or alert
        if (status.equals("SOS", ignoreCase = true) || status.equals("emergency", ignoreCase = true)) {
            NotificationHelper.sendAlertNotification(
                context = this,
                title = "🚨 Emergency SOS Alert!",
                message = "Aarav triggered an SOS at ($lat, $lng). Tap for details.",
                alertType = "SOS",
                location = location
            )
        } else if (status.equals("offline", ignoreCase = true)) {
            NotificationHelper.sendAlertNotification(
                context = this,
                title = "⚠️ Device Offline Warning",
                message = "Device safeband_001 went offline at ($lat, $lng).",
                alertType = "Tamper",
                location = location
            )
        }
    }

    private fun listenToLiveAlerts() {
        val database = FirebaseDatabase.getInstance(databaseUrl)
        val alertsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (child in snapshot.children) {
                        val type = child.child("type").value?.toString() ?: "SOS"
                        val alertStatus = child.child("status").value?.toString() ?: ""
                        val lat = child.child("latitude").value?.toString()?.toDoubleOrNull()
                            ?: child.child("lat").value?.toString()?.toDoubleOrNull() ?: 17.5062
                        val lng = child.child("longitude").value?.toString()?.toDoubleOrNull()
                            ?: child.child("lng").value?.toString()?.toDoubleOrNull() ?: 81.648

                        if (!alertStatus.equals("acknowledged", ignoreCase = true)) {
                            NotificationHelper.sendAlertNotification(
                                context = this@LocationFetchService,
                                title = "🚨 $type Alert Received!",
                                message = "Alert reported near ($lat, $lng)",
                                alertType = type,
                                location = LatLng(lat, lng)
                            )
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("LocationFetchService", "Alerts listener cancelled: ${error.message}")
            }
        }

        database.getReference("devices/safeband_001/alerts").addValueEventListener(alertsListener)
        database.getReference("device/alerts").addValueEventListener(alertsListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 1001
    }
}
