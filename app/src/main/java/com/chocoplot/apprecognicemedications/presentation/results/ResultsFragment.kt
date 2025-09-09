// presentation/results/ResultsFragment.kt
package com.chocoplot.apprecognicemedications.presentation.results

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.chocoplot.apprecognicemedications.databinding.FragmentResultsBinding
import com.chocoplot.apprecognicemedications.presentation.camera.CameraViewModel

class ResultsFragment : Fragment() {
    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!
    private val cameraVm: CameraViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cameraVm.results.observe(viewLifecycleOwner) { boxes ->
            binding.overlay.setResults(boxes)
            binding.overlay.invalidate()
            binding.summaryText.text = buildSummary(boxes)
        }
    }

    private fun buildSummary(boxes: List<com.chocoplot.apprecognicemedications.ml.model.BoundingBox>): String =
        boxes.groupBy { it.label }
            .entries
            .joinToString(separator = "\n") { "${it.key}: ${it.value.size}" }

    override fun onDestroyView() {
        super.onDestroyView(); _binding = null
    }
}
