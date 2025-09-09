// presentation/menu/MenuFragment.kt
package com.chocoplot.apprecognicemedications.presentation.menu

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.chocoplot.apprecognicemedications.R
import com.chocoplot.apprecognicemedications.databinding.FragmentMenuBinding

class MenuFragment : Fragment() {
    private var _binding: FragmentMenuBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.takePhotoButton.setOnClickListener {
            findNavController().navigate(R.id.action_menu_to_camera)
        }
        binding.uploadPhotoButton.setOnClickListener {
            findNavController().navigate(R.id.action_menu_to_results)
        }

        // botones: measurement, help (F), settings (G)
    }

    override fun onDestroyView() {
        super.onDestroyView(); _binding = null
    }
}
