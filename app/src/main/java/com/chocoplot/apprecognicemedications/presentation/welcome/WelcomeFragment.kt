// presentation/welcome/WelcomeFragment.kt
package com.chocoplot.apprecognicemedications.presentation.welcome

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.chocoplot.apprecognicemedications.databinding.FragmentWelcomeBinding

class WelcomeFragment : Fragment() {
    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.version.text = "v1.0.1"

        // Navegar automáticamente después de 2 segundos (opcional)
        view.postDelayed({
            findNavController().navigate(WelcomeFragmentDirections.actionWelcomeToMenu())
        }, 2000)


    }


    override fun onDestroyView() {
        super.onDestroyView(); _binding = null
    }
}
