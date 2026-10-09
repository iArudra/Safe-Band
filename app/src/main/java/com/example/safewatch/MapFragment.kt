package com.example.safewatch

import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.safewatch.databinding.FragmentMapBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null
    private var deviceMarker: Marker? = null

    private var fenceCenterMarker: Marker? = null
    private var fenceEdgeMarker: Marker? = null
    private var geofenceCircle: Circle? = null

    // Unified Firebase paths — must match the Python backend (a9g_relay.py / app.py)
    private val database = FirebaseDatabase.getInstance(
        "https://safe-band-7659f-default-rtdb.asia-southeast1.firebasedatabase.app"
    )
    private val locationRef = database.getReference("devices/band_001/location")
    private val geofenceRef = database.getReference("devices/band_001/geofence")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        startLocationUpdates()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Setup long click for geofence
        googleMap?.setOnMapLongClickListener { latLng ->
            setupGeofence(latLng, 200.0) // default 200m radius
            saveGeofenceToFirebase()
        }

        // Setup marker drag listener for resizing geofence
        googleMap?.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker) {}

            override fun onMarkerDrag(marker: Marker) {
                if (marker == fenceEdgeMarker) {
                    updateGeofenceRadiusFromEdge()
                }
            }

            override fun onMarkerDragEnd(marker: Marker) {
                if (marker == fenceEdgeMarker) {
                    updateGeofenceRadiusFromEdge()
                    saveGeofenceToFirebase()
                }
            }
        })

        // Load existing geofence if any
        loadGeofenceFromFirebase()
    }

    private fun startLocationUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                locationRef.get().addOnSuccessListener { snapshot ->
                    // Using unified field names: lat / lng (matching Python backend)
                    val lat = snapshot.child("lat").value?.toString()?.toDoubleOrNull()
                    val lng = snapshot.child("lng").value?.toString()?.toDoubleOrNull()
                    if (lat != null && lng != null && !(lat == 0.0 && lng == 0.0)) {
                        val deviceLatLng = LatLng(lat, lng)
                        updateDeviceLocation(deviceLatLng)
                    }
                }
                delay(5000)
            }
        }
    }

    private fun updateDeviceLocation(latLng: LatLng) {
        googleMap?.let { map ->
            if (deviceMarker == null) {
                deviceMarker = map.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("Device Location")
                )
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            } else {
                deviceMarker?.position = latLng
            }
        }
    }

    private fun setupGeofence(center: LatLng, radius: Double) {
        googleMap?.let { map ->
            // Clean up old geofence visuals
            fenceCenterMarker?.remove()
            fenceEdgeMarker?.remove()
            geofenceCircle?.remove()

            // Add center marker
            fenceCenterMarker = map.addMarker(
                MarkerOptions()
                    .position(center)
                    .title("Geofence Center")
            )

            // Calculate an edge point for the draggable marker
            val edgeLatLng = getEdgePoint(center, radius)
            fenceEdgeMarker = map.addMarker(
                MarkerOptions()
                    .position(edgeLatLng)
                    .draggable(true)
                    .title("Drag to resize")
            )

            // Add circle
            geofenceCircle = map.addCircle(
                CircleOptions()
                    .center(center)
                    .radius(radius)
                    .strokeColor(0xFF0000FF.toInt())
                    .fillColor(0x220000FF)
            )
        }
    }

    private fun updateGeofenceRadiusFromEdge() {
        val center = fenceCenterMarker?.position ?: return
        val edge = fenceEdgeMarker?.position ?: return

        val results = FloatArray(1)
        Location.distanceBetween(center.latitude, center.longitude, edge.latitude, edge.longitude, results)
        val radius = results[0].toDouble()

        geofenceCircle?.radius = radius
    }

    private fun getEdgePoint(center: LatLng, radiusInMeters: Double): LatLng {
        val latOffset = radiusInMeters / 111111.0
        return LatLng(center.latitude, center.longitude + latOffset)
    }

    private fun saveGeofenceToFirebase() {
        val center = fenceCenterMarker?.position ?: return
        val radius = geofenceCircle?.radius ?: return

        // Unified field names: lat / lng (matching Python backend and HomeScreen)
        val geofenceData = mapOf(
            "lat" to center.latitude,
            "lng" to center.longitude,
            "radius" to radius
        )
        geofenceRef.setValue(geofenceData)
    }

    private fun loadGeofenceFromFirebase() {
        geofenceRef.get().addOnSuccessListener { snapshot ->
            // Unified field names: lat / lng
            val lat = snapshot.child("lat").value?.toString()?.toDoubleOrNull()
            val lng = snapshot.child("lng").value?.toString()?.toDoubleOrNull()
            val radius = snapshot.child("radius").getValue(Double::class.java)

            if (lat != null && lng != null && radius != null) {
                setupGeofence(LatLng(lat, lng), radius)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
