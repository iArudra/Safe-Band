package com.example.safewatch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.safewatch.databinding.DialogSosBinding
import com.google.firebase.database.FirebaseDatabase

class SosDialogFragment : DialogFragment() {

    private var _binding: DialogSosBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(alertId: String, lat: Double, lng: Double): SosDialogFragment {
            val frag = SosDialogFragment()
            val args = Bundle()
            args.putString("alertId", alertId)
            args.putDouble("lat", lat)
            args.putDouble("lng", lng)
            frag.arguments = args
            return frag
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val alertId = arguments?.getString("alertId")
        val lat = arguments?.getDouble("lat") ?: 0.0
        val lng = arguments?.getDouble("lng") ?: 0.0

        binding.sosCoordinates.text = "Coordinates: $lat, $lng"

        binding.btnAcknowledge.setOnClickListener {
            if (alertId != null) {
                FirebaseDatabase.getInstance().getReference("device/alerts")
                    .child(alertId)
                    .child("status")
                    .setValue("acknowledged")
            }
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
