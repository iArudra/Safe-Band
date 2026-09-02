package com.example.safewatch.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.clip
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

@Composable
fun HomeScreen() {
    var childLocation by remember { mutableStateOf(LatLng(1.35, 103.87)) }
    var batteryLevel by remember { mutableStateOf("85%") }
    var speed by remember { mutableStateOf("0 km/h") }
    var lastUpdated by remember { mutableStateOf("Just now") }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(childLocation, 15f)
    }

    // Firebase Realtime Listener
    DisposableEffect(Unit) {
        val database = FirebaseDatabase.getInstance("https://safe-band-7659f-default-rtdb.asia-southeast1.firebasedatabase.app")
        // Updated path to "Device" with capital D to match your console screenshot
        val locationRef = database.getReference("Device/location")
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Using a safer way to parse numbers (handles both Int and Double)
                    val lat = snapshot.child("lat").value?.toString()?.toDoubleOrNull() ?: 0.0
                    val lng = snapshot.child("lng").value?.toString()?.toDoubleOrNull() ?: 0.0
                    val battery = snapshot.child("battery").value?.toString()?.toIntOrNull() ?: 0
                    val spd = snapshot.child("speed").value?.toString()?.toDoubleOrNull() ?: 0.0
                    
                    childLocation = LatLng(lat, lng)
                    batteryLevel = "$battery%"
                    speed = "${spd.toInt()} km/h"
                    lastUpdated = "Updated just now"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("Firebase", "Error: ${error.message}")
            }
        }
        
        locationRef.addValueEventListener(listener)
        onDispose { locationRef.removeEventListener(listener) }
    }

    // Animate camera to child location when it changes
    LaunchedEffect(childLocation) {
        cameraPositionState.animate(
            update = com.google.android.gms.maps.CameraUpdateFactory.newLatLng(childLocation),
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
            
            Surface(
                color = SuccessGreen.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).background(SuccessGreen, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Safe", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                    title = "Aarav"
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
                    Text("Home & School", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
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
