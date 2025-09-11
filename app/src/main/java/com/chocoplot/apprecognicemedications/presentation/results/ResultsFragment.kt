// presentation/results/ResultsFragment.kt
package com.chocoplot.apprecognicemedications.presentation.results

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.chocoplot.apprecognicemedications.R
import com.chocoplot.apprecognicemedications.databinding.FragmentGalleryBinding
import com.chocoplot.apprecognicemedications.presentation.gallery.GalleryViewModel
import com.chocoplot.apprecognicemedications.presentation.gallery.GalleryAdapter
import com.chocoplot.apprecognicemedications.presentation.gallery.GalleryPhoto
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ResultsFragment : Fragment() {
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private val galleryVm: GalleryViewModel by viewModels()
    
    private lateinit var galleryAdapter: GalleryAdapter

    private val requestStoragePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("ResultsFragment", "Permission result: $isGranted")
        if (isGranted) {
            loadGalleryPhotos()
        } else {
            Log.e("ResultsFragment", "Storage permission denied")
            showEmptyState()
            showErrorSnackbar("Permisos de almacenamiento necesarios para acceder a las fotos")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        setupGallery()
        setupObservers()
        checkPermissionsAndLoadPhotos()
    }

    private fun setupUI() {
        // Setup toolbar navigation
        binding.toolbar.setNavigationOnClickListener {
            if (galleryVm.isSelectionMode.value == true) {
                galleryVm.exitSelectionMode()
            } else {
                findNavController().navigateUp()
            }
        }
        
        // Initialize gallery adapter with both click handlers
        galleryAdapter = GalleryAdapter(
            onPhotoClick = { photo -> onPhotoClicked(photo) },
            onPhotoSelectionToggle = { photo -> onPhotoSelectionToggle(photo) }
        )
        
        // Setup RecyclerView
        binding.galleryRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = galleryAdapter
        }

        // Setup button click listeners
        setupButtonListeners()
    }

    private fun setupButtonListeners() {
        // Select Multiple Button
        binding.selectMultipleButton.setOnClickListener {
            Log.d("ResultsFragment", "Select Multiple button clicked")
            galleryVm.toggleSelectionMode()
        }

        // Delete All Button  
        binding.deleteAllButton.setOnClickListener {
            showDeleteAllConfirmationDialog()
        }

        // Selection Toolbar Navigation (Cancel Selection)
        binding.selectionToolbar.setNavigationOnClickListener {
            galleryVm.exitSelectionMode()
        }

        // Delete Selected Button
        binding.deleteSelectedButton.setOnClickListener {
            showDeleteSelectedConfirmationDialog()
        }

        // Cancel Selection Button (in helper card)
        binding.cancelSelectionButton.setOnClickListener {
            galleryVm.exitSelectionMode()
        }
    }

    private fun setupGallery() {
        // Observe gallery photos
        galleryVm.galleryPhotos.observe(viewLifecycleOwner) { photos ->
            Log.d("ResultsFragment", "Gallery photos updated: ${photos.size} photos")
            galleryAdapter.submitList(photos)
            
            if (photos.isEmpty()) {
                showEmptyState()
                updateButtonStates(hasPhotos = false, isSelectionMode = false, selectedCount = 0)
            } else {
                hideEmptyState()
                updateButtonStates(
                    hasPhotos = true, 
                    isSelectionMode = galleryVm.isSelectionMode.value ?: false,
                    selectedCount = galleryVm.selectedCount.value ?: 0
                )
            }
        }

        // Observe loading state
        galleryVm.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun setupObservers() {
        // Selection mode observer
        galleryVm.isSelectionMode.observe(viewLifecycleOwner) { isSelectionMode ->
            Log.d("ResultsFragment", "Selection mode changed: $isSelectionMode")
            handleSelectionModeChange(isSelectionMode)
        }

        // Selected count observer
        galleryVm.selectedCount.observe(viewLifecycleOwner) { count ->
            Log.d("ResultsFragment", "Selected count changed: $count")
            updateSelectionUI(count)
            updateButtonStates(
                hasPhotos = galleryVm.hasPhotos(),
                isSelectionMode = galleryVm.isSelectionMode.value ?: false,
                selectedCount = count
            )
        }

        // Operation progress observer
        galleryVm.isOperationInProgress.observe(viewLifecycleOwner) { inProgress ->
            if (inProgress) {
                showLoadingOverlay()
            } else {
                hideLoadingOverlay()
            }
        }

        // Operation message observer
        galleryVm.operationMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                binding.loadingText.text = message
            }
        }

        // Success message observer
        galleryVm.showSuccessMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                showSuccessSnackbar(it)
                galleryVm.clearSuccessMessage()
            }
        }

        // Error message observer
        galleryVm.showErrorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                showErrorSnackbar(it)
                galleryVm.clearErrorMessage()
            }
        }
    }

    private fun handleSelectionModeChange(isSelectionMode: Boolean) {
        Log.d("ResultsFragment", "Handling selection mode change: $isSelectionMode")
        
        // Update adapter selection mode FIRST
        galleryAdapter.setSelectionMode(isSelectionMode)
        
        if (isSelectionMode) {
            // Enter selection mode
            Log.d("ResultsFragment", "Entering selection mode")
            
            // Show selection UI first
            binding.selectionToolbar.visibility = View.VISIBLE
            binding.selectionHelpCard.visibility = View.VISIBLE
            
            // Hide normal UI
            binding.toolbar.visibility = View.GONE
            binding.actionButtonContainer.visibility = View.GONE
            
            // Update RecyclerView constraint to be below selection help card
            updateRecyclerViewConstraints(true)
            
            // Animate selection mode UI
            try {
                val slideIn = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_in_bottom)
                binding.selectionToolbar.startAnimation(slideIn)
                binding.selectionHelpCard.startAnimation(slideIn)
            } catch (e: Exception) {
                Log.e("ResultsFragment", "Error loading animation", e)
            }
            
        } else {
            // Exit selection mode
            Log.d("ResultsFragment", "Exiting selection mode")
            
            // Show normal UI first
            binding.toolbar.visibility = View.VISIBLE
            binding.actionButtonContainer.visibility = View.VISIBLE
            
            // Hide selection UI
            binding.selectionToolbar.visibility = View.GONE
            binding.selectionHelpCard.visibility = View.GONE
            
            // Update RecyclerView constraint back to main toolbar
            updateRecyclerViewConstraints(false)
            
            // Animate normal mode UI
            try {
                val slideIn = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_in_bottom)
                binding.actionButtonContainer.startAnimation(slideIn)
            } catch (e: Exception) {
                Log.e("ResultsFragment", "Error loading animation", e)
            }
        }
    }

    private fun updateSelectionUI(selectedCount: Int) {
        val selectionText = if (selectedCount > 0) {
            getString(R.string.selected_count, selectedCount)
        } else {
            "Selecciona fotos"
        }
        binding.selectionToolbar.title = selectionText
        binding.deleteSelectedButton.isEnabled = selectedCount > 0
    }

    private fun updateButtonStates(hasPhotos: Boolean, isSelectionMode: Boolean, selectedCount: Int) {
        if (!isSelectionMode) {
            binding.selectMultipleButton.isEnabled = hasPhotos
            binding.deleteAllButton.isEnabled = hasPhotos
        }
        binding.deleteSelectedButton.isEnabled = selectedCount > 0
    }

    private fun onPhotoClicked(photo: GalleryPhoto) {
        Log.d("ResultsFragment", "Photo clicked: ${photo.displayName}")
        
        // Navigate to photo detail fragment with the photo URI
        val bundle = PhotoDetailFragment.createBundle(photo.uri)
        findNavController().navigate(R.id.action_results_to_photo_detail, bundle)
    }

    private fun onPhotoSelectionToggle(photo: GalleryPhoto) {
        galleryVm.togglePhotoSelection(photo.uri)
    }

    private fun showDeleteAllConfirmationDialog() {
        val photoCount = galleryVm.galleryPhotos.value?.size ?: 0
        
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.confirm_delete_all_title))
            .setMessage(getString(R.string.confirm_delete_all_message))
            .setIcon(R.drawable.ic_delete_all_24)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                galleryVm.deleteAllPhotos(requireContext())
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showDeleteSelectedConfirmationDialog() {
        val selectedCount = galleryVm.selectedCount.value ?: 0
        
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.confirm_delete_selected_title))
            .setMessage(getString(R.string.confirm_delete_selected_message, selectedCount))
            .setIcon(R.drawable.ic_delete_all_24)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                galleryVm.deleteSelectedPhotos(requireContext())
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showEmptyState() {
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.galleryRecyclerView.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.emptyStateLayout.visibility = View.GONE
        binding.galleryRecyclerView.visibility = View.VISIBLE
    }

    private fun showLoadingOverlay() {
        binding.loadingOverlay.visibility = View.VISIBLE
        val fadeIn = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in)
        binding.loadingOverlay.startAnimation(fadeIn)
    }

    private fun hideLoadingOverlay() {
        val fadeOut = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_out)
        binding.loadingOverlay.startAnimation(fadeOut)
        binding.loadingOverlay.visibility = View.GONE
    }

    private fun showSuccessSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.menu_button_tint))
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            .show()
    }

    private fun showErrorSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.shutter_red))
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            .setAction("OK") { }
            .show()
    }

    private fun checkPermissionsAndLoadPhotos() {
        if (hasStoragePermission()) {
            loadGalleryPhotos()
        } else {
            requestStoragePermission()
        }
    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestStoragePermission.launch(permission)
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun loadGalleryPhotos() {
        Log.d("ResultsFragment", "Loading gallery photos...")
        galleryVm.initializeDetector(requireContext())
        galleryVm.loadGalleryPhotos(requireContext())
    }

    private fun updateRecyclerViewConstraints(isSelectionMode: Boolean) {
        val constraintSet = androidx.constraintlayout.widget.ConstraintSet()
        constraintSet.clone(binding.root)
        
        if (isSelectionMode) {
            // In selection mode: RecyclerView should be below selection help card
            constraintSet.connect(
                binding.galleryRecyclerView.id,
                androidx.constraintlayout.widget.ConstraintSet.TOP,
                binding.selectionHelpCard.id,
                androidx.constraintlayout.widget.ConstraintSet.BOTTOM
            )
        } else {
            // Normal mode: RecyclerView should be below main toolbar
            constraintSet.connect(
                binding.galleryRecyclerView.id,
                androidx.constraintlayout.widget.ConstraintSet.TOP,
                binding.toolbar.id,
                androidx.constraintlayout.widget.ConstraintSet.BOTTOM
            )
        }
        
        constraintSet.applyTo(binding.root)
        Log.d("ResultsFragment", "Updated RecyclerView constraints, selection mode: $isSelectionMode")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
