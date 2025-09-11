// app/src/main/java/com/chocoplot/apprecognicemedications/presentation/results/PhotoDetailFragment.kt
package com.chocoplot.apprecognicemedications.presentation.results

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.chocoplot.apprecognicemedications.R
import com.chocoplot.apprecognicemedications.databinding.FragmentPhotoDetailBinding
import com.chocoplot.apprecognicemedications.presentation.gallery.GalleryViewModel
import com.chocoplot.apprecognicemedications.ml.model.BoundingBox
import com.chocoplot.apprecognicemedications.data.DetectionDatabase
import com.chocoplot.apprecognicemedications.ml.Detector
import com.chocoplot.apprecognicemedications.core.Constants
import com.chocoplot.apprecognicemedications.core.CrashRecoveryManager
import com.chocoplot.apprecognicemedications.presentation.MedicationCount
import com.chocoplot.apprecognicemedications.presentation.MedicationCountAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class PhotoDetailFragment : Fragment(), Detector.DetectorListener {
    private var _binding: FragmentPhotoDetailBinding? = null
    private val binding get() = _binding!!
    private val galleryVm: GalleryViewModel by viewModels()
    
    // ViewTreeObserver listener reference for cleanup
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    
    // Flag to prevent multiple processing attempts
    private var isProcessing = false
    
    // Detector and adapter for medication detection
    private lateinit var detector: Detector
    private lateinit var medicationAdapter: MedicationCountAdapter
    
    // TODO: Add navigation args when implementing navigation
    // private val args: PhotoDetailFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPhotoDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        setupRecyclerView()
        setupDetector()
        loadAndDisplayPhoto()
    }

    private fun setupUI() {
        // Fix navigation issue by using popBackStack instead of navigateUp
        binding.toolbar.setNavigationOnClickListener {
            try {
                findNavController().popBackStack()
            } catch (e: Exception) {
                Log.e("PhotoDetailFragment", "Error navigating back", e)
                // Fallback: try to go back in a different way
                requireActivity().onBackPressed()
            }
        }
        
        // Set up manual detection button
        binding.btnManualDetect.setOnClickListener {
            Log.d("PhotoDetailFragment", "Manual detection requested")
            manuallyProcessCurrentPhoto()
        }
        
        // Add long press handler to view detection history
        binding.btnManualDetect.setOnLongClickListener {
            Log.d("PhotoDetailFragment", "Navigating to detection history")
            try {
                findNavController().navigate(R.id.action_photoDetailFragment_to_detectionHistoryFragment)
                true
            } catch (e: Exception) {
                Log.e("PhotoDetailFragment", "Error navigating to history", e)
                Toast.makeText(requireContext(),
                    "Error al abrir el historial",
                    Toast.LENGTH_SHORT).show()
                false
            }
        }
    }
    
    private fun setupRecyclerView() {
        medicationAdapter = MedicationCountAdapter()
        binding.resultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = medicationAdapter
        }
    }
    
    private fun setupDetector() {
        detector = Detector(requireContext(), Constants.MODEL_PATH, Constants.LABELS_PATH, this)
        detector.setup()
    }

    private fun loadAndDisplayPhoto() {
        val photoUriString = arguments?.getString("photo_uri")
        val photoUri = photoUriString?.let { Uri.parse(it) }
        
        photoUri?.let { uri ->
            Log.d("PhotoDetailFragment", "Loading photo: $uri")
            
            // Simply load the image for display
            Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(binding.photo)
                
            // Check if we already have detections for this photo in database
            checkForExistingDetections(uri)
            
        } ?: run {
            Log.e("PhotoDetailFragment", "No photo URI provided")
            binding.totalCount.text = "Error: No se pudo cargar la foto"
        }
    }
    
    /**
     * Check if we already have detections for this photo in database
     * and load them if available
     */
    private fun checkForExistingDetections(photoUri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = DetectionDatabase(requireContext())
                val session = db.findSessionByPhotoUri(photoUri)
                
                withContext(Dispatchers.Main) {
                    if (session != null) {
                        // Existing detection found, load it
                        Log.d("PhotoDetailFragment", "Found existing detection session: ${session.id}")
                        loadExistingDetection(session.id)
                    } else {
                        // No existing detection, show default state
                        binding.progressBar.visibility = View.GONE
                        binding.totalCount.text = "Presiona el botón para detectar medicamentos"
                    }
                }
            } catch (e: Exception) {
                Log.e("PhotoDetailFragment", "Error checking for existing detections", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.totalCount.text = "Presiona el botón para detectar medicamentos"
                }
            }
        }
    }
    
    /**
     * Load existing detection from database
     */
    private fun loadExistingDetection(sessionId: Long) {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = DetectionDatabase(requireContext())
                val detectedItems = db.getDetectionItems(sessionId)
                
                // Convert DetectedItems to BoundingBoxes
                val boundingBoxes = detectedItems.map { item ->
                    val cx = (item.x1 + item.x2) / 2f
                    val cy = (item.y1 + item.y2) / 2f
                    val w = item.x2 - item.x1
                    val h = item.y2 - item.y1
                    
                    BoundingBox(
                        x1 = item.x1,
                        y1 = item.y1,
                        x2 = item.x2,
                        y2 = item.y2,
                        cx = cx,
                        cy = cy,
                        w = w,
                        h = h,
                        clsName = item.className,
                        cnf = item.confidence,
                        cls = 0 // Asignar 0 como valor predeterminado para el índice de clase
                    )
                }
                
                withContext(Dispatchers.Main) {
                    if (boundingBoxes.isNotEmpty()) {
                        // Get original bitmap to set correct dimensions for overlay
                        val photoUriString = arguments?.getString("photo_uri")
                        val photoUri = photoUriString?.let { Uri.parse(it) }
                        
                        photoUri?.let { uri ->
                            Glide.with(requireContext())
                                .asBitmap()
                                .load(uri)
                                .into(object : SimpleTarget<Bitmap>() {
                                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                        // Set image source info for overlay scaling
                                        binding.overlay.setImageSourceInfo(resource.width, resource.height)
                                        
                                        // Update UI with loaded detections
                                        binding.overlay.apply {
                                            setResults(boundingBoxes)
                                            invalidate()
                                        }
                                        
                                        // Update medication counts
                                        updateMedicationCounts(boundingBoxes)
                                        
                                        // Hide progress bar
                                        binding.progressBar.visibility = View.GONE
                                        
                                        Log.d("PhotoDetailFragment", "Loaded ${boundingBoxes.size} detections from database")
                                        Toast.makeText(requireContext(),
                                            "Detección cargada desde base de datos",
                                            Toast.LENGTH_SHORT).show()
                                    }
                                })
                        }
                    } else {
                        // No detections found in session
                        binding.progressBar.visibility = View.GONE
                        binding.totalCount.text = "Presiona el botón para detectar medicamentos"
                    }
                }
            } catch (e: Exception) {
                Log.e("PhotoDetailFragment", "Error loading existing detection", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.totalCount.text = "Presiona el botón para detectar medicamentos"
                }
            }
        }
    }

    /**
     * Manually process the current photo with proper aspect ratio
     */
    private fun manuallyProcessCurrentPhoto() {
        val photoUriString = arguments?.getString("photo_uri")
        val photoUri = photoUriString?.let { Uri.parse(it) }
        
        if (photoUri == null) {
            Toast.makeText(requireContext(), "No hay foto para procesar", Toast.LENGTH_SHORT).show()
            return
        }
        
        binding.progressBar.visibility = View.VISIBLE
        
        // Get original bitmap without modification to preserve aspect ratio
        Glide.with(requireContext())
            .asBitmap()
            .load(photoUri)
            .into(object : SimpleTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    Log.d("PhotoDetailFragment", "Got original bitmap: ${resource.width}x${resource.height}")
                    
                    // Set image source info for overlay scaling
                    binding.overlay.setImageSourceInfo(resource.width, resource.height)
                    
                    // Process with detector directly - bypass recovery mode for manual detection
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            // Reset recovery mode for manual operation
                            CrashRecoveryManager.resetRecoveryMode()
                            Log.d("PhotoDetailFragment", "Recovery mode reset for manual detection")
                            
                            val startTime = System.currentTimeMillis()
                            detector.detect(resource)
                            // Inference time will be handled in onDetect callback
                        } catch (e: Exception) {
                            Log.e("PhotoDetailFragment", "Error in manual processing", e)
                            withContext(Dispatchers.Main) {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(requireContext(),
                                    "Error al procesar la imagen",
                                    Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            })
    }
    
   private fun cleanupLayoutListener() {
       layoutListener?.let { listener ->
           try {
               binding.photo.viewTreeObserver.removeOnGlobalLayoutListener(listener)
           } catch (e: Exception) {
               Log.w("PhotoDetailFragment", "Error removing layout listener", e)
           } finally {
               layoutListener = null
           }
       }
   }
    
    /**
    * Save detection results to SQLite database
    * If a detection already exists for this photo, it will be replaced
    */
   private suspend fun saveResultsToDatabase(photoUri: Uri, detections: List<BoundingBox>) {
        return withContext(Dispatchers.IO) {
            try {
                val db = DetectionDatabase(requireContext())
                
                // Check if a detection already exists for this photo
                val existingSession = db.findSessionByPhotoUri(photoUri)
                
                // If exists, delete it first
                if (existingSession != null) {
                    Log.d("PhotoDetailFragment", "Replacing existing detection for photo: ${existingSession.id}")
                    db.deleteDetectionSession(existingSession.id)
                }
                
                // Save the new detection
                val sessionId = db.saveDetectionSession(photoUri, detections)
                
                withContext(Dispatchers.Main) {
                    if (sessionId != -1L) {
                        Toast.makeText(requireContext(),
                            if (existingSession != null) "Detección actualizada" else "Resultados guardados en base de datos",
                            Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(),
                            "Error al guardar resultados",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("PhotoDetailFragment", "Error saving to database", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(),
                        "Error al guardar en base de datos",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Detector callbacks
    override fun onEmptyDetect() {
        lifecycleScope.launch(Dispatchers.Main) {
            // Clear the overlay by setting empty results
            binding.overlay.apply {
                setResults(emptyList())
                invalidate()
            }
            
            // Clear medication counts
            medicationAdapter.updateCounts(emptyList())
            
            // Reset total count
            binding.totalCount.text = "0 total"
            
            // Hide progress and inference time
            binding.progressBar.visibility = View.GONE
            binding.inferenceTime.visibility = View.GONE
            
            // Save empty detection session to database
            val photoUriString = arguments?.getString("photo_uri")
            val photoUri = photoUriString?.let { Uri.parse(it) }
            photoUri?.let { uri ->
                lifecycleScope.launch(Dispatchers.IO) {
                    saveResultsToDatabase(uri, emptyList()) // Save with 0 detections
                }
            }
            
            Toast.makeText(requireContext(), "No se detectaron medicamentos en esta foto", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        lifecycleScope.launch(Dispatchers.Main) {
            // Show inference time
            binding.inferenceTime.text = "${inferenceTime}ms"
            binding.inferenceTime.visibility = View.VISIBLE
            
            // Update overlay with bounding boxes
            binding.overlay.apply {
                setResults(boundingBoxes)
                invalidate()
            }
            
            // Update medication counts
            updateMedicationCounts(boundingBoxes)
            
            // Hide progress bar
            binding.progressBar.visibility = View.GONE
            
            // Save results to database
            val photoUriString = arguments?.getString("photo_uri")
            val photoUri = photoUriString?.let { Uri.parse(it) }
            photoUri?.let { uri ->
                lifecycleScope.launch(Dispatchers.IO) {
                    saveResultsToDatabase(uri, boundingBoxes)
                }
            }
        }
    }
    
    private fun updateMedicationCounts(boundingBoxes: List<BoundingBox>) {
        // Count medications by name (using the same logic as CameraDetectionActivity)
        val medicationCounts = boundingBoxes
            .groupBy { it.clsName }
            .map { (name, boxes) -> MedicationCount(name, boxes.size) }
            .sortedByDescending { it.count }

        // Update total
        val totalDetections = boundingBoxes.size
        binding.totalCount.text = "$totalDetections total"

        // Update list
        medicationAdapter.updateCounts(medicationCounts)
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
        
        // Clean up detector
        try {
            if (::detector.isInitialized) {
                detector.clear()
            }
        } catch (e: Exception) {
            Log.w("PhotoDetailFragment", "Error clearing detector", e)
        }
        
        // Clean up resources to prevent memory leaks
        try {
            cleanupLayoutListener()
            
            // Clear Glide requests
            Glide.with(this).clear(binding.photo)
            
            // Reset processing flag
            isProcessing = false
            
        } catch (e: Exception) {
            Log.w("PhotoDetailFragment", "Error during cleanup", e)
        } finally {
            _binding = null
        }
    }
    

    companion object {
        fun createBundle(photoUri: Uri): Bundle {
            return Bundle().apply {
                putString("photo_uri", photoUri.toString())
            }
        }
    }
}