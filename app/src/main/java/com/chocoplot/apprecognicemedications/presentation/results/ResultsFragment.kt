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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.chocoplot.apprecognicemedications.databinding.FragmentResultsBinding
import com.chocoplot.apprecognicemedications.presentation.camera.CameraViewModel
import com.chocoplot.apprecognicemedications.presentation.gallery.GalleryViewModel
import dagger.hilt.android.AndroidEntryPoint
import android.content.Intent
import android.net.Uri

@AndroidEntryPoint
class ResultsFragment : Fragment() {
    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!
    private val cameraVm: CameraViewModel by activityViewModels()
    private val galleryVm: GalleryViewModel by viewModels()
    
    private var isGalleryMode = false

    private val requestStoragePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("ResultsFragment", "Permission result: $isGranted")
        if (isGranted) {
            loadGalleryPhotos()
        } else {
            Log.e("ResultsFragment", "Storage permission denied")
            binding.summaryText.text = "Permisos de galería requeridos"
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        try {
            uri?.let {
                Log.d("ResultsFragment", "Image selected: $uri")
                loadSelectedImage(it)
            } ?: run {
                Log.w("ResultsFragment", "No image selected")
                binding.summaryText.text = "No se seleccionó ninguna imagen"
            }
        } catch (e: Exception) {
            Log.e("ResultsFragment", "Error handling image selection", e)
            binding.summaryText.text = "Error al procesar la imagen seleccionada"
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            // Check if we're in gallery mode with proper null safety
            val args = arguments
            isGalleryMode = if (args != null) {
                args.getBoolean("gallery_mode", false)
            } else {
                false
            }
            
            Log.d("ResultsFragment", "Gallery mode: $isGalleryMode")
            
            setupUI()
            
            if (isGalleryMode) {
                setupGalleryMode()
            } else {
                setupCameraMode()
            }
        } catch (e: Exception) {
            Log.e("ResultsFragment", "Error in onViewCreated", e)
            // Fallback to camera mode
            isGalleryMode = false
            setupUI()
            setupCameraMode()
        }
    }

    private fun setupUI() {
        // Setup toolbar navigation
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        
        // Update toolbar title based on mode
        binding.toolbar.title = if (isGalleryMode) "Medición" else "Resultados"
    }

    private fun setupGalleryMode() {
        // Hide navigation buttons for single image picker
        binding.btnPrevious.visibility = View.GONE
        binding.btnNext.visibility = View.GONE
        
        galleryVm.initializeDetector(requireContext())
        
        // Observe detection results from gallery
        galleryVm.results.observe(viewLifecycleOwner) { boxes ->
            Log.d("ResultsFragment", "Detection results: ${boxes.size} boxes")
            binding.overlay.setResults(boxes)
            binding.overlay.invalidate()
            binding.summaryText.text = buildSummary(boxes)
        }
        
        // Observe image size for overlay scaling
        galleryVm.imageSize.observe(viewLifecycleOwner) { imageSize ->
            imageSize?.let { (width, height) ->
                Log.d("ResultsFragment", "Image size set: ${width}x${height}")
                binding.overlay.setImageSourceInfo(width, height)
            }
        }
        
        // Open image picker directly
        Log.d("ResultsFragment", "Opening image picker...")
        pickImageLauncher.launch("image/*")
    }

    private fun setupCameraMode() {
        // Hide navigation buttons for camera mode
        binding.btnPrevious.visibility = View.GONE
        binding.btnNext.visibility = View.GONE
        
        // Observe detection results from camera
        cameraVm.results.observe(viewLifecycleOwner) { boxes ->
            binding.overlay.setResults(boxes)
            binding.overlay.invalidate()
            binding.summaryText.text = buildSummary(boxes)
        }
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
        galleryVm.loadGalleryPhotos(requireContext())
    }

    private fun loadSelectedImage(uri: Uri) {
        Log.d("ResultsFragment", "Loading selected image: $uri")
        
        // Load image into ImageView with proper callbacks
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
                            Log.d("ResultsFragment", "ImageView sized: ${binding.photo.width}x${binding.photo.height}")
                            Log.d("ResultsFragment", "Starting image processing...")
                            galleryVm.processImageUri(requireContext(), uri)
                        } else {
                            Log.w("ResultsFragment", "ImageView not properly sized, retrying...")
                            binding.photo.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                                override fun onGlobalLayout() {
                                    if (binding.photo.width > 0 && binding.photo.height > 0) {
                                        binding.photo.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                        Log.d("ResultsFragment", "ImageView sized after layout: ${binding.photo.width}x${binding.photo.height}")
                                        galleryVm.processImageUri(requireContext(), uri)
                                    }
                                }
                            })
                        }
                    }
                }
                
                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                    binding.photo.setImageDrawable(placeholder)
                }
            })
    }

    private fun buildSummary(boxes: List<com.chocoplot.apprecognicemedications.ml.model.BoundingBox>): String {
        if (boxes.isEmpty()) {
            return "No se detectaron medicamentos"
        }
        
        // Consolidate results by normalizing class names and summing counts
        val consolidatedResults = boxes
            .groupBy { it.clsName.trim().uppercase() } // Normalize class names
            .mapValues { (_, detections) -> detections.size }
            .toList()
            .sortedByDescending { it.second } // Sort by count descending
        
        val totalMedications = consolidatedResults.sumOf { it.second }
        val uniqueMedications = consolidatedResults.size
        
        val resultText = StringBuilder()
        resultText.append("📊 Resumen de detección:\n")
        resultText.append("• Total detectados: $totalMedications\n")
        resultText.append("• Medicamentos únicos: $uniqueMedications\n\n")
        
        resultText.append("🔍 Detalle por medicamento:\n")
        consolidatedResults.forEachIndexed { index, (label, count) ->
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
}
