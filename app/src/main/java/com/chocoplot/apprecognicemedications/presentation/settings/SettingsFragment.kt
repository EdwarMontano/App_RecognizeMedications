package com.chocoplot.apprecognicemedications.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.chocoplot.apprecognicemedications.R
import com.chocoplot.apprecognicemedications.data.SettingsRepository
import com.chocoplot.apprecognicemedications.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private val settingsViewModel: SettingsViewModel by activityViewModels()
    private lateinit var settingsRepository: SettingsRepository
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        binding.viewModel = settingsViewModel
        binding.lifecycleOwner = this
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        settingsRepository = SettingsRepository(requireContext())
        
        loadCurrentSettings()
        setupSaveButton()
        observeViewModelChanges()
    }
    
    private fun loadCurrentSettings() {
        val currentConfidence = settingsRepository.getConfidenceThreshold()
        val currentIou = settingsRepository.getIouThreshold()
        val currentDisplayVisible = settingsRepository.getDisplayElementsVisible()
        
        // Update ViewModel with current values from repository
        settingsViewModel.setConfidenceThreshold(currentConfidence)
        settingsViewModel.setIouThreshold(currentIou)
        settingsViewModel.setDisplayElementsVisible(currentDisplayVisible)
    }
    
    private fun setupSaveButton() {
        binding.saveButton.setOnClickListener {
            saveSettings()
        }
    }
    
    private fun observeViewModelChanges() {
        // Add text watchers to update ViewModel when user types
        binding.confidenceEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                s?.toString()?.let { text ->
                    settingsViewModel.updateConfidenceFromText(text)
                }
            }
        })
        
        binding.iouEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                s?.toString()?.let { text ->
                    settingsViewModel.updateIouFromText(text)
                }
            }
        })
        
        // Observe changes to update UI if needed
        settingsViewModel.confidenceThreshold.observe(viewLifecycleOwner, Observer { value ->
            // Data binding handles UI updates automatically
        })
        
        settingsViewModel.iouThreshold.observe(viewLifecycleOwner, Observer { value ->
            // Data binding handles UI updates automatically
        })
    }
    
    private fun saveSettings() {
        val confidenceValue = settingsViewModel.getConfidenceThresholdValue()
        val iouValue = settingsViewModel.getIouThresholdValue()
        val displayVisibleValue = settingsViewModel.getDisplayElementsVisibleValue()
        
        if (!isValidThreshold(confidenceValue) || !isValidThreshold(iouValue)) {
            showError(getString(R.string.error_invalid_value))
            return
        }
        
        try {
            // Save to repository for persistence
            settingsRepository.saveSettings(confidenceValue, iouValue, displayVisibleValue)
            showSuccess(getString(R.string.settings_saved))
            
        } catch (e: Exception) {
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