package com.example.safewatch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safewatch.ui.theme.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.*
import com.google.maps.android.compose.*

// ─────────────────────────────────────────────────────────────────────────────
// IMPORTANT: This screen listens to the UNIFIED Firebase path:
//   devices/{DEVICE_ID}/location
//
// Fields expected by the Python backend (a9g_relay.py / app.py):
//   lat       (Double)  — latitude
//   lng       (Double)  — longitude
//   battery   (Int)     — optional; only present when hardware reports it
//   speed     (Double)  — optional; only present when hardware reports it
//   timestamp (Long)    — Unix epoch, seconds
//   updated_at (String) — ISO-8601 string
//
// To change the tracked device, update DEVICE_ID below.
// ─────────────────────────────────────────────────────────────────────────────

// The device ID used as the Firebase path segment.
// Must match the DEVICE_ID env var in your .env / a9g_relay.py.
private const val DEVICE_ID = "band_001"

// Firebase database URL — keep in sync with google-services.json.
private const val DB_URL =
    "https://safe-band-7659f-default-rtdb.asia-southeast1.firebasedatabase.app"

@Composable
fun HomeScreen() {

    // ── State ────────────────────────────────────────────────────────────────
    var childLocation by remember { mutableStateOf<LatLng?>(null) }
    var batteryLevel  by remember { mutableStateOf<String?>(null) }
    var speedText     by remember { mutableStateOf<String?>(null) }
    var lastUpdated   by remember { mutableStateOf("Waiting for fix...") }
    var isLoading     by remember { mutableStateOf(true) }
    var dbError       by remember { mutableStateOf<String?>(null) }

    val defaultLocation = LatLng(1.35, 103.87)   // shown only before first fix
    val displayLocation = childLocation ?: defaultLocation

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(displayLocation, 15f)
    }

    // ── Firebase real-time listener ──────────────────────────────────────────
    DisposableEffect(Unit) {
        val database = FirebaseDatabase.getInstance(DB_URL)
        // Unified path: devices/{device_id}/location
        val locationRef = database.getReference("devices/$DEVICE_ID/location")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isLoading = false
                dbError = null

                if (!snapshot.exists()) {
                    lastUpdated = "No location data yet"
                    return
                }

                // Parse lat / lng — robust to Int or Double stored by Firebase.
                val lat = snapshot.child("lat").value?.toString()?.toDoubleOrNull()
                val lng = snapshot.child("lng").value?.toString()?.toDoubleOrNull()

                if (lat == null || lng == null || lat == 0.0 && lng == 0.0) {
                    lastUpdated = "Waiting for GPS fix…"
                    return
                }

                childLocation = LatLng(lat, lng)

                // Only display battery / speed if the hardware actually sent them.
                val battRaw = snapshot.child("battery").value?.toString()?.toIntOrNull()
                batteryLevel = if (battRaw != null) "$battRaw%" else null

                val spdRaw = snapshot.child("speed").value?.toString()?.toDoubleOrNull()
                speedText = if (spdRaw != null) "${spdRaw.toInt()} km/h" else null

                lastUpdated = "Just updated"
            }

            override fun onCancelled(error: DatabaseError) {
                isLoading = false
                dbError = error.message
                android.util.Log.e("HomeScreen", "Firebase error: ${error.message}")
            }
        }

        locationRef.addValueEventListener(listener)
        onDispose { locationRef.removeEventListener(listener) }
    }

    // ── Animate camera when location changes ─────────────────────────────────
    LaunchedEffect(childLocation) {
        childLocation?.let { loc ->
            cameraPositionState.animate(
                update = com.google.android.gms.maps.CameraUpdateFactory.newLatLng(loc),
                durationMs = 1000
            )
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Hello, Parent",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    "Device: $DEVICE_ID",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }

            Surface(
                color = if (dbError != null) DangerRed.copy(alpha = 0.1f)
                        else SuccessGreen.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (dbError != null) DangerRed else SuccessGreen
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (dbError != null) DangerRed else SuccessGreen,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (dbError != null) "Error" else "Live",
                        color = if (dbError != null) DangerRed else SuccessGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Error banner
        if (dbError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Firebase error: $dbError",
                    modifier = Modifier.padding(12.dp),
                    color = DangerRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Map
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false)
                ) {
                    childLocation?.let { loc ->
                        Marker(
                            state = rememberMarkerState(position = loc),
                            title = DEVICE_ID
                        )
                    }
                }

                // Loading overlay
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BackgroundDark.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = PrimaryBlue)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Connecting to Firebase…",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Stats Row — only show battery / speed when real data exists
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard("Updated", lastUpdated, Icons.Default.History, Modifier.weight(1f))
            StatCard(
                "Battery",
                batteryLevel ?: "—",
                Icons.Default.BatteryChargingFull,
                Modifier.weight(1f)
            )
            StatCard(
                "Speed",
                speedText ?: "—",
                Icons.Default.Speed,
                Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Geofence status card
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                    Text(
                        "Home & School",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
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
            QuickActionButton("SOS History", Icons.Default.Warning, Modifier.weight(1f))
            QuickActionButton("Edit Fence", Icons.Default.EditLocation, Modifier.weight(1f))
            QuickActionButton("Share", Icons.Default.Share, Modifier.weight(1f))
        }
    }
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
fun QuickActionButton(label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(44.dp),
        color = CardBackground,
        shape = RoundedCornerShape(12.dp),
        onClick = {}
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
