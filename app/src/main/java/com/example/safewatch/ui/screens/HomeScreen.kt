package com.example.safewatch.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safewatch.ui.theme.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.*
import com.google.maps.android.compose.*

@Composable
fun HomeScreen(
    onNavigateToAlerts: (filter: String) -> Unit = {},
    onNavigateToGeoFence: () -> Unit = {}
) {
    val context = LocalContext.current
    var childLocation by remember { mutableStateOf(LatLng(17.5062, 81.648)) }
    var batteryLevel by remember { mutableStateOf("85%") }
    var speed by remember { mutableStateOf("0 km/h") }
    var lastUpdated by remember { mutableStateOf("Just now") }
    var deviceStatus by remember { mutableStateOf("online") }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(childLocation, 15f)
    }

    // Firebase Realtime Listener
    DisposableEffect(Unit) {
        val database = FirebaseDatabase.getInstance("https://safe-band-7659f-default-rtdb.asia-southeast1.firebasedatabase.app")

        val parseData = { snapshot: DataSnapshot ->
            val lat = snapshot.child("latitude").value?.toString()?.toDoubleOrNull()
                ?: snapshot.child("lat").value?.toString()?.toDoubleOrNull()
            val lng = snapshot.child("longitude").value?.toString()?.toDoubleOrNull()
                ?: snapshot.child("lng").value?.toString()?.toDoubleOrNull()
            val battery = snapshot.child("battery").value?.toString()?.toIntOrNull()
            val spd = snapshot.child("speed").value?.toString()?.toDoubleOrNull()
            val st = snapshot.child("status").value?.toString()

            if (lat != null && lng != null && (lat != 0.0 || lng != 0.0)) {
                childLocation = LatLng(lat, lng)
            }
            if (battery != null) {
                batteryLevel = "$battery%"
            }
            if (spd != null) {
                speed = "${spd.toInt()} km/h"
            }
            if (!st.isNullOrBlank()) {
                deviceStatus = st
            }
            lastUpdated = "Just now"
        }

        // Listeners for devices/safeband_001, device/location, Device/location
        val ref1 = database.getReference("devices/safeband_001")
        val listener1 = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) parseData(snapshot)
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        val ref2 = database.getReference("device/location")
        val listener2 = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) parseData(snapshot)
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        ref1.addValueEventListener(listener1)
        ref2.addValueEventListener(listener2)

        onDispose {
            ref1.removeEventListener(listener1)
            ref2.removeEventListener(listener2)
        }
    }

    // Animate camera when child location updates
    LaunchedEffect(childLocation) {
        cameraPositionState.animate(
            update = CameraUpdateFactory.newLatLngZoom(childLocation, 15f),
            durationMs = 1000
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        // Top Greeting Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Hello, Parent", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text("Child: Aarav", style = MaterialTheme.typography.titleLarge, color = Color.White)
            }

            val badgeColor = when (deviceStatus.lowercase()) {
                "sos" -> DangerRed
                "offline" -> TextSecondary
                else -> SuccessGreen
            }
            val badgeText = when (deviceStatus.lowercase()) {
                "sos" -> "SOS ALERT"
                "offline" -> "Offline"
                else -> "Safe (${deviceStatus.capitalize()})"
            }

            Surface(
                color = badgeColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).background(badgeColor, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(badgeText, color = badgeColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Map Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(24.dp)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = false)
            ) {
                Marker(
                    state = rememberMarkerState(position = childLocation),
                    title = "Aarav (${childLocation.latitude.toString().take(7)}, ${childLocation.longitude.toString().take(7)})"
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard("Updated", lastUpdated, Icons.Default.History, Modifier.weight(1f))
            StatCard("Battery", batteryLevel, Icons.Default.BatteryChargingFull, Modifier.weight(1f))
            StatCard("Speed", speed, Icons.Default.Speed, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Geo-fence Status
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToGeoFence() },
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.GppGood, contentDescription = null, tint = SuccessGreen)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Inside Safe Zone", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Home & School - Tap to edit", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionButton(
                label = "SOS History",
                icon = Icons.Default.Warning,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToAlerts("SOS") }
            )
            QuickActionButton(
                label = "Edit Fence",
                icon = Icons.Default.EditLocation,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToGeoFence() }
            )
            QuickActionButton(
                label = "Share",
                icon = Icons.Default.Share,
                modifier = Modifier.weight(1f),
                onClick = { shareLocation(context, childLocation, deviceStatus) }
            )
        }
    }
}

private fun shareLocation(context: Context, location: LatLng, status: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "SafeWatch - Aarav's Live Location:\nhttps://maps.google.com/?q=${location.latitude},${location.longitude}\nStatus: $status"
        )
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Location Via"))
}

private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(label, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
fun QuickActionButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier.height(44.dp),
        color = CardBackground,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = SecondaryCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}
