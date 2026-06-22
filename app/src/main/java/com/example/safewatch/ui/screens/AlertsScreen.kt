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

data class AlertItem(
    val type: String,
    val timestamp: String,
    val location: String,
    val status: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun AlertsScreen() {
    val alerts = listOf(
        AlertItem("SOS ALERT", "10:45 AM", "123 Main St, Springfield", "Acknowledged", Icons.Default.Warning, DangerRed),
        AlertItem("Geo-fence", "08:30 AM", "Green Valley School", "Safe", Icons.Default.LocationOn, Color(0xFFFFA502)),
        AlertItem("Tamper", "Yesterday", "Unknown Location", "Investigated", Icons.Default.Watch, Color(0xFF747D8C)),
        AlertItem("Geo-fence", "Yesterday", "Home", "Safe", Icons.Default.Home, Color(0xFF2ED573))
    )
    
    val filters = listOf("All", "SOS", "Geo-fence", "Tamper")
    var selectedFilter by remember { mutableStateOf("All") }

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
                    selected = selectedFilter == filter,
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
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(alerts) { alert ->
                AlertCard(alert)
            }
        }
    }
}

@Composable
fun AlertCard(alert: AlertItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (alert.type == "SOS ALERT") alert.color.copy(alpha = 0.15f) else CardBackground),
        shape = RoundedCornerShape(20.dp),
        border = if (alert.type == "SOS ALERT") androidx.compose.foundation.BorderStroke(1.dp, alert.color) else null
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
                Text(alert.location, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Text(alert.status, color = TextSecondary, fontSize = 12.sp)
            }
            
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}
