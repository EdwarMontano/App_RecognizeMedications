// presentation/menu/MenuFragment.kt
package com.chocoplot.apprecognicemedications.presentation.menu

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.chocoplot.apprecognicemedications.R
import com.chocoplot.apprecognicemedications.databinding.FragmentMenuBinding
import com.chocoplot.apprecognicemedications.presentation.CameraDetectionActivity

class MenuFragment : Fragment() {
    private var _binding: FragmentMenuBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.takePhotoButton.setOnClickListener {
            findNavController().navigate(R.id.action_menu_to_camera)
        }
        binding.uploadPhotoButton.setOnClickListener {
            // Abrir CameraDetectionActivity para detectar medicamentos
            val intent = Intent(requireContext(), CameraDetectionActivity::class.java)
            startActivity(intent)
        }
        binding.galleryButton.setOnClickListener {
            // Navigate to results with gallery mode for measurement
            val bundle = Bundle().apply {
                putBoolean("gallery_mode", true)
            }
            findNavController().navigate(R.id.action_menu_to_gallery, bundle)
        }
        binding.helpButton.setOnClickListener {
            findNavController().navigate(R.id.action_menu_to_help)
        }
        
        binding.remoteInferenceButton.setOnClickListener {
            // Abrir el enlace de Hugging Face para inferencia remota
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/spaces/chocoplot/UV-RecognizerMedications"))
            startActivity(intent)
        }
        
        binding.settingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_menu_to_settings)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView(); _binding = null
    }
}
