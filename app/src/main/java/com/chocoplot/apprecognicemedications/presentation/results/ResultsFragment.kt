// presentation/results/ResultsFragment.kt
package com.chocoplot.apprecognicemedications.presentation.results

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
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
        checkPermissionsAndLoadPhotos()
    }

    private fun setupUI() {
        // Setup toolbar navigation
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        
        // Initialize gallery adapter
        galleryAdapter = GalleryAdapter { photo ->
            onPhotoClicked(photo)
        }
        
        // Setup RecyclerView
        binding.galleryRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = galleryAdapter
        }
    }

    private fun setupGallery() {
        // Observe gallery photos
        galleryVm.galleryPhotos.observe(viewLifecycleOwner) { photos ->
            Log.d("ResultsFragment", "Gallery photos updated: ${photos.size} photos")
            galleryAdapter.submitList(photos)
            
            if (photos.isEmpty()) {
                showEmptyState()
            } else {
                hideEmptyState()
            }
        }

        // Observe loading state
        galleryVm.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun checkPermissionsAndLoadPhotos() {
        if (hasStoragePermission()) {
            loadGalleryPhotos()
        } else {
            requestStoragePermission()
        }
    }

    private fun onPhotoClicked(photo: GalleryPhoto) {
        Log.d("ResultsFragment", "Photo clicked: ${photo.displayName}")
        
        // Navigate to photo detail fragment with the photo URI
        val bundle = PhotoDetailFragment.createBundle(photo.uri)
        findNavController().navigate(R.id.action_results_to_photo_detail, bundle)
    }

    private fun showEmptyState() {
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.galleryRecyclerView.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.emptyStateLayout.visibility = View.GONE
        binding.galleryRecyclerView.visibility = View.VISIBLE
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
        galleryVm.loadGalleryPhotos(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
