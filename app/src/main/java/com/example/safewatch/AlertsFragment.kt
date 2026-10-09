package com.example.safewatch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.safewatch.databinding.FragmentAlertsBinding
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase

class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!

    // Unified Firebase path — must match the Python backend
    private val alertsRef = FirebaseDatabase.getInstance(
        "https://safe-band-7659f-default-rtdb.asia-southeast1.firebasedatabase.app"
    ).getReference("devices/band_001/alerts")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        alertsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val type = snapshot.child("type").getValue(String::class.java)
                val status = snapshot.child("status").getValue(String::class.java)
                
                if (type == "SOS" && status != "acknowledged") {
                    val lat = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                    val lng = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                    val alertId = snapshot.key
                    
                    if (alertId != null) {
                        showSosDialog(alertId, lat, lng)
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showSosDialog(alertId: String, lat: Double, lng: Double) {
        val dialog = SosDialogFragment.newInstance(alertId, lat, lng)
        dialog.show(parentFragmentManager, "SosDialog")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
