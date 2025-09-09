package com.chocoplot.apprecognicemedications.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.chocoplot.apprecognicemedications.R
import com.chocoplot.apprecognicemedications.data.SettingsRepository
import com.chocoplot.apprecognicemedications.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var settingsRepository: SettingsRepository
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        settingsRepository = SettingsRepository(requireContext())
        
        loadCurrentSettings()
        setupSaveButton()
    }
    
    private fun loadCurrentSettings() {
        val currentConfidence = settingsRepository.getConfidenceThreshold()
        val currentIou = settingsRepository.getIouThreshold()
        
        binding.confidenceEditText.setText(currentConfidence.toString())
        binding.iouEditText.setText(currentIou.toString())
    }
    
    private fun setupSaveButton() {
        binding.saveButton.setOnClickListener {
            saveSettings()
        }
    }
    
    private fun saveSettings() {
        val confidenceText = binding.confidenceEditText.text.toString().trim()
        val iouText = binding.iouEditText.text.toString().trim()
        
        if (confidenceText.isEmpty() || iouText.isEmpty()) {
            showError(getString(R.string.error_invalid_value))
            return
        }
        
        try {
            val confidenceValue = confidenceText.toFloat()
            val iouValue = iouText.toFloat()
            
            if (!isValidThreshold(confidenceValue) || !isValidThreshold(iouValue)) {
                showError(getString(R.string.error_invalid_value))
                return
            }
            
            settingsRepository.saveSettings(confidenceValue, iouValue)
            showSuccess(getString(R.string.settings_saved))
            
        } catch (e: NumberFormatException) {
            showError(getString(R.string.error_invalid_value))
        }
    }
    
    private fun isValidThreshold(value: Float): Boolean {
        return value in 0.0f..1.0f
    }
    
    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
    
    private fun showSuccess(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}