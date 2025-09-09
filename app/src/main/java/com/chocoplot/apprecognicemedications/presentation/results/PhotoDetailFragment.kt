// app/src/main/java/com/chocoplot/apprecognicemedications/presentation/results/PhotoDetailFragment.kt
package com.chocoplot.apprecognicemedications.presentation.results

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.chocoplot.apprecognicemedications.databinding.FragmentPhotoDetailBinding
import com.chocoplot.apprecognicemedications.presentation.gallery.GalleryViewModel
import com.chocoplot.apprecognicemedications.ml.model.BoundingBox
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PhotoDetailFragment : Fragment() {
    private var _binding: FragmentPhotoDetailBinding? = null
    private val binding get() = _binding!!
    private val galleryVm: GalleryViewModel by viewModels()
    
    // TODO: Add navigation args when implementing navigation
    // private val args: PhotoDetailFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPhotoDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        setupObservers()
        processPhoto()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupObservers() {
        // Initialize detector
        galleryVm.initializeDetector(requireContext())
        
        // Observe detection results
        galleryVm.results.observe(viewLifecycleOwner) { boxes ->
            Log.d("PhotoDetailFragment", "Detection results: ${boxes.size} boxes")
            binding.overlay.setResults(boxes)
            binding.overlay.invalidate()
            binding.summaryText.text = buildSummary(boxes)
            binding.progressBar.visibility = View.GONE
        }
        
        // Observe image size for overlay scaling
        galleryVm.imageSize.observe(viewLifecycleOwner) { imageSize ->
            imageSize?.let { (width, height) ->
                Log.d("PhotoDetailFragment", "Image size set: ${width}x${height}")
                binding.overlay.setImageSourceInfo(width, height)
            }
        }
    }

    private fun processPhoto() {
        // TODO: Get photo URI from navigation arguments
        // For now, we'll use a placeholder approach
        val photoUriString = arguments?.getString("photo_uri")
        val photoUri = photoUriString?.let { Uri.parse(it) }
        
        photoUri?.let { uri ->
            Log.d("PhotoDetailFragment", "Processing photo: $uri")
            
            // Show loading indicator
            binding.progressBar.visibility = View.VISIBLE
            
            // Load image into ImageView
            Glide.with(this)
                .load(uri)
                .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                    ) {
                        binding.photo.setImageDrawable(resource)
                        
                        // Wait for layout to complete before processing
                        binding.photo.post {
                            if (binding.photo.width > 0 && binding.photo.height > 0) {
                                Log.d("PhotoDetailFragment", "ImageView sized: ${binding.photo.width}x${binding.photo.height}")
                                galleryVm.processImageUri(requireContext(), uri)
                            } else {
                                Log.w("PhotoDetailFragment", "ImageView not properly sized, retrying...")
                                binding.photo.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                                    override fun onGlobalLayout() {
                                        if (binding.photo.width > 0 && binding.photo.height > 0) {
                                            binding.photo.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                            Log.d("PhotoDetailFragment", "ImageView sized after layout: ${binding.photo.width}x${binding.photo.height}")
                                            galleryVm.processImageUri(requireContext(), uri)
                                        }
                                    }
                                })
                            }
                        }
                    }
                    
                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                        binding.photo.setImageDrawable(placeholder)
                        binding.progressBar.visibility = View.GONE
                    }
                })
        } ?: run {
            Log.e("PhotoDetailFragment", "No photo URI provided")
            binding.summaryText.text = "Error: No se pudo cargar la foto"
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun buildSummary(boxes: List<BoundingBox>): String {
        if (boxes.isEmpty()) {
            return "No se detectaron medicamentos en esta foto"
        }
        
        // Consolidate results by normalizing class names and summing counts
        val consolidatedResults = boxes
            .groupBy { it.clsName.trim().uppercase() }
            .mapValues { (_, detections) -> detections.size }
            .toList()
            .sortedByDescending { it.second }
        
        val totalMedications = consolidatedResults.sumOf { it.second }
        val uniqueMedications = consolidatedResults.size
        
        val resultText = StringBuilder()
        resultText.append("📊 Resumen de detección:\n")
        resultText.append("• Total detectados: $totalMedications\n")
        resultText.append("• Medicamentos únicos: $uniqueMedications\n\n")
        
        resultText.append("🔍 Detalle por medicamento:\n")
        consolidatedResults.forEachIndexed { _, (label, count) ->
            val bullet = when {
                count >= 4 -> "🔴"
                count >= 2 -> "🟡"
                else -> "🟢"
            }
            resultText.append("$bullet $label: $count\n")
        }
        
        return resultText.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun createBundle(photoUri: Uri): Bundle {
            return Bundle().apply {
                putString("photo_uri", photoUri.toString())
            }
        }
    }
}