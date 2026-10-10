package com.example.safewatch.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoFenceScreen() {
    val context = LocalContext.current
    var center by remember { mutableStateOf(LatLng(17.5062, 81.648)) }
    var radius by remember { mutableStateOf(500f) }
    var zoneName by remember { mutableStateOf("Safe Zone") }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, 15f)
    }

    val scaffoldState = rememberBottomSheetScaffoldState()

    // Load existing Geofence from Firebase
    DisposableEffect(Unit) {
        val database = FirebaseDatabase.getInstance("https://safe-band-7659f-default-rtdb.asia-southeast1.firebasedatabase.app")
        val ref = database.getReference("devices/safeband_001/geofence")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val lat = snapshot.child("latitude").value?.toString()?.toDoubleOrNull() ?: 17.5062
                    val lng = snapshot.child("longitude").value?.toString()?.toDoubleOrNull() ?: 81.648
                    val rad = snapshot.child("radius").value?.toString()?.toFloatOrNull() ?: 500f
                    val name = snapshot.child("name").value?.toString() ?: "Safe Zone"

                    center = LatLng(lat, lng)
                    radius = rad
                    zoneName = name
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    fun saveGeofenceToFirebase() {
        val database = FirebaseDatabase.getInstance("https://safe-band-7659f-default-rtdb.asia-southeast1.firebasedatabase.app")
        val data = mapOf(
            "latitude" to center.latitude,
            "longitude" to center.longitude,
            "radius" to radius,
            "name" to zoneName
        )

        database.getReference("devices/safeband_001/geofence").setValue(data)
        database.getReference("device/geofence").setValue(data)
            .addOnSuccessListener {
                Toast.makeText(context, "Safe zone saved successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to save safe zone", Toast.LENGTH_SHORT).show()
            }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 120.dp,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContainerColor = CardBackground,
        sheetContent = {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                Text("Zone Configuration", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = zoneName,
                    onValueChange = { zoneName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Zone Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = BackgroundDark
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text("Radius: ${radius.toInt()}m", color = Color.White)
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = 100f..2000f,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryBlue,
                        activeTrackColor = PrimaryBlue,
                        inactiveTrackColor = BackgroundDark
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { saveGeofenceToFirebase() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Safe Zone", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(BackgroundDark)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                onMapLongClick = { latLng ->
                    center = latLng
                }
            ) {
                Circle(
                    center = center,
                    radius = radius.toDouble(),
                    fillColor = PrimaryBlue.copy(alpha = 0.2f),
                    strokeColor = PrimaryBlue,
                    strokeWidth = 2f
                )
                Marker(
                    state = rememberMarkerState(position = center),
                    title = zoneName,
                    draggable = true
                )
            }

            // Top overlay card
            Card(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground.copy(alpha = 0.9f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "Long press on map to set safe zone center",
                    modifier = Modifier.padding(12.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
