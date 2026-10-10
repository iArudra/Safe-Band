package com.example.safewatch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.google.firebase.database.*

data class AlertItem(
    val id: String = "",
    val type: String,
    val timestamp: String,
    val location: String,
    val status: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun AlertsScreen(initialFilter: String = "All") {
    val defaultAlerts = listOf(
        AlertItem("1", "SOS ALERT", "10:45 AM", "123 Main St, Springfield", "Acknowledged", Icons.Default.Warning, DangerRed),
        AlertItem("2", "Geo-fence", "08:30 AM", "Green Valley School", "Safe", Icons.Default.LocationOn, Color(0xFFFFA502)),
        AlertItem("3", "Tamper", "Yesterday", "Unknown Location", "Investigated", Icons.Default.Watch, Color(0xFF747D8C)),
        AlertItem("4", "Geo-fence", "Yesterday", "Home", "Safe", Icons.Default.Home, Color(0xFF2ED573))
    )

    var firebaseAlerts by remember { mutableStateOf<List<AlertItem>>(emptyList()) }
    var selectedFilter by remember(initialFilter) { mutableStateOf(if (initialFilter.isNotBlank()) initialFilter else "All") }

    // Listen to Firebase for real alerts
    DisposableEffect(Unit) {
        val database = FirebaseDatabase.getInstance("https://safe-band-7659f-default-rtdb.asia-southeast1.firebasedatabase.app")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val list = mutableListOf<AlertItem>()
                    for (child in snapshot.children) {
                        val alertId = child.key ?: ""
                        val type = child.child("type").value?.toString() ?: "SOS ALERT"
                        val time = child.child("timestamp").value?.toString() ?: "Just now"
                        val lat = child.child("latitude").value?.toString() ?: child.child("lat").value?.toString() ?: "17.5062"
                        val lng = child.child("longitude").value?.toString() ?: child.child("lng").value?.toString() ?: "81.648"
                        val st = child.child("status").value?.toString() ?: "Active"

                        val (icon, color) = when {
                            type.contains("SOS", ignoreCase = true) -> Icons.Default.Warning to DangerRed
                            type.contains("Fence", ignoreCase = true) -> Icons.Default.LocationOn to Color(0xFFFFA502)
                            else -> Icons.Default.Watch to Color(0xFF747D8C)
                        }

                        list.add(
                            AlertItem(
                                id = alertId,
                                type = type,
                                timestamp = time,
                                location = "Location: $lat, $lng",
                                status = st,
                                icon = icon,
                                color = color
                            )
                        )
                    }
                    firebaseAlerts = list
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        val ref = database.getReference("devices/safeband_001/alerts")
        ref.addValueEventListener(listener)

        onDispose { ref.removeEventListener(listener) }
    }

    val combinedAlerts = remember(firebaseAlerts) {
        firebaseAlerts + defaultAlerts
    }

    val filters = listOf("All", "SOS", "Geo-fence", "Tamper")

    // Filtering logic based on selectedFilter
    val filteredAlerts = remember(combinedAlerts, selectedFilter) {
        if (selectedFilter.equals("All", ignoreCase = true)) {
            combinedAlerts
        } else {
            combinedAlerts.filter { alert ->
                alert.type.contains(selectedFilter, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        Text("Alert History", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Spacer(modifier = Modifier.height(24.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filters) { filter ->
                FilterChip(
                    selected = selectedFilter.equals(filter, ignoreCase = true),
                    onClick = { selectedFilter = filter },
                    label = { Text(filter) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = CardBackground,
                        selectedContainerColor = PrimaryBlue,
                        labelColor = TextSecondary,
                        selectedLabelColor = Color.White
                    ),
                    border = null
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredAlerts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No $selectedFilter alerts found",
                    color = TextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filteredAlerts) { alert ->
                    AlertCard(alert)
                }
            }
        }
    }
}

@Composable
fun AlertCard(alert: AlertItem) {
    val isSos = alert.type.contains("SOS", ignoreCase = true)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSos) alert.color.copy(alpha = 0.15f) else CardBackground
        ),
        shape = RoundedCornerShape(20.dp),
        border = if (isSos) androidx.compose.foundation.BorderStroke(1.dp, alert.color) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(alert.color.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(alert.icon, contentDescription = null, tint = alert.color)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(alert.type, color = alert.color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.size(4.dp).background(TextSecondary, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(alert.timestamp, color = TextSecondary, fontSize = 12.sp)
                }
                Text(alert.location, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(alert.status, color = TextSecondary, fontSize = 12.sp)
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}
