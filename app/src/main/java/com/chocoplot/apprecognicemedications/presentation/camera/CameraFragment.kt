// app/src/main/java/com/chocoplot/apprecognicemedications/presentation/camera/CameraFragment.kt
package com.chocoplot.apprecognicemedications.presentation.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.chocoplot.apprecognicemedications.R
import com.chocoplot.apprecognicemedications.databinding.FragmentCameraBinding
import com.chocoplot.apprecognicemedications.ml.Detector
import com.chocoplot.apprecognicemedications.ml.model.BoundingBox
import com.chocoplot.apprecognicemedications.core.CrashRecoveryManager
import com.chocoplot.apprecognicemedications.data.SettingsRepository
import com.chocoplot.apprecognicemedications.presentation.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class CameraFragment : Fragment(), Detector.DetectorListener {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CameraViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by activityViewModels()
    private lateinit var settingsRepository: SettingsRepository

    private var detector: Detector? = null
    private var cameraExecutor: ExecutorService? = null
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var isProcessing = false
    
    // Add surface state management
    private var isSurfaceReady = false
    
    // Photo counters
    private var sessionPhotosCount = 0

    private val requestCameraPermission = registerForActivityResult(RequestPermission()) { isGranted ->
        if (isGranted) startCamera()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        binding.viewModel = settingsViewModel
        binding.lifecycleOwner = this
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Initialize settings repository
        settingsRepository = SettingsRepository(requireContext())
        
        // Initialize executor with proper thread naming and error handling
        cameraExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "CameraAnalysis").apply {
                isDaemon = true
                uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
                    android.util.Log.e("CameraFragment", "Uncaught exception in camera thread", e)
                    CrashRecoveryManager.recordCrash(
                        CrashRecoveryManager.CrashType.GENERAL,
                        "Camera thread crash: ${e.message}"
                    )
                }
            }
        }
        
        detector = viewModel.createDetector(requireContext(), this)

        binding.toolbar.setNavigationOnClickListener {
            // Clean up before navigation
            isProcessing = false
            cameraProvider?.unbindAll()
            findNavController().navigateUp()
        }
        binding.captureButton.setOnClickListener { capturePhoto() }
        
        // Setup gallery thumbnail click listener
        binding.galleryThumbnail.setOnClickListener {
            // TODO: Open gallery to select image
        }
        
        // Monitor surface readiness
        binding.viewFinder.post {
            isSurfaceReady = true
            if (hasCameraPermission()) {
                startCamera()
            } else {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }

        viewModel.inferenceTime.observe(viewLifecycleOwner) { ms ->
            binding.inferenceTime.text = getString(R.string.inference_time, ms)
        }
        viewModel.results.observe(viewLifecycleOwner) { boxes ->
            // Only update overlay if fragment is active
            if (isAdded && _binding != null) {
                binding.overlay.setResults(boxes)
                binding.overlay.invalidate()
            }
        }
        
        // Observe settings changes for dynamic updates
        settingsViewModel.confidenceThreshold.observe(viewLifecycleOwner) { threshold ->
            // Settings updated - data binding will handle UI updates automatically
            // You can also pass these values to the detector if needed
        }
        
        settingsViewModel.iouThreshold.observe(viewLifecycleOwner) { threshold ->
            // Settings updated - data binding will handle UI updates automatically
            // You can also pass these values to the detector if needed
        }
        
        // Observe display elements visibility setting
        settingsViewModel.displayElementsVisible.observe(viewLifecycleOwner) { isVisible ->
            updateElementsVisibility(isVisible)
        }
        
        // Load and apply initial display settings
        val displayVisible = settingsRepository.getDisplayElementsVisible()
        settingsViewModel.setDisplayElementsVisible(displayVisible)
        
        // Initialize photo counters
        updatePhotoCounters()
    }
    
    private fun updateElementsVisibility(isVisible: Boolean) {
        binding.settingsDisplay.visibility = if (isVisible) View.VISIBLE else View.GONE
        binding.inferenceTime.visibility = if (isVisible) View.VISIBLE else View.GONE
    }
    
    private fun updatePhotoCounters() {
        // Update session counter
        binding.sessionPhotosCount.text = getString(R.string.photos_count_format, sessionPhotosCount)
        
        // Get total gallery count
        val totalCount = getTotalGalleryPhotosCount()
        binding.totalPhotosCount.text = getString(R.string.photos_count_format, totalCount)
    }
    
    private fun getTotalGalleryPhotosCount(): Int {
        var count = 0
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("MedRecognition_%")
        
        requireContext().contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            count = cursor.count
        }
        
        return count
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        if (!isSurfaceReady) {
            android.util.Log.w("CameraFragment", "Surface not ready, delaying camera start")
            // Retry after a short delay
            binding.viewFinder.post {
                if (_binding != null) startCamera()
            }
            return
        }
        
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                val provider = cameraProvider ?: return@addListener
                
                // Ensure we're on the main thread for UI operations
                if (!isAdded || _binding == null) return@addListener
                
                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .build()
                
                // Set surface provider with error handling
                try {
                    preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                } catch (e: Exception) {
                    android.util.Log.e("CameraFragment", "Error setting surface provider", e)
                    return@addListener
                }

                imageCapture = ImageCapture.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .build()

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { analyzer ->
                        cameraExecutor?.let { executor ->
                            analyzer.setAnalyzer(executor) { image ->
                                // Check recovery status before processing
                                val memoryStatus = CrashRecoveryManager.checkMemoryPressure()
                                val recommendations = CrashRecoveryManager.getRecoveryRecommendations()
                                
                                // Apply recovery actions if needed
                                if (recommendations.contains(CrashRecoveryManager.RecoveryAction.SKIP_ML_PROCESSING)) {
                                    image.close()
                                    return@setAnalyzer
                                }
                                
                                if (recommendations.contains(CrashRecoveryManager.RecoveryAction.FORCE_GARBAGE_COLLECTION)) {
                                    CrashRecoveryManager.performRecoveryAction(CrashRecoveryManager.RecoveryAction.FORCE_GARBAGE_COLLECTION)
                                }
                                
                                // Rate limit analysis to prevent overwhelming the system
                                if (!isProcessing && isAdded && _binding != null) {
                                    isProcessing = true
                                    analyzeFrame(image)
                                    // Reset processing flag after a short delay
                                    val delay = if (CrashRecoveryManager.isInRecoveryMode()) 100L else 50L
                                    binding.root.postDelayed({ isProcessing = false }, delay)
                                } else {
                                    image.close()
                                }
                            }
                        }
                    }

                val selector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                // Unbind all previous use cases before binding new ones
                provider.unbindAll()
                
                // Bind to lifecycle with error handling
                try {
                    provider.bindToLifecycle(viewLifecycleOwner, selector, preview, imageCapture, analysis)
                    android.util.Log.d("CameraFragment", "Camera bound successfully")
                } catch (e: Exception) {
                    android.util.Log.e("CameraFragment", "Error binding camera", e)
                }
                
            } catch (e: Exception) {
                android.util.Log.e("CameraFragment", "Error starting camera", e)
                CrashRecoveryManager.recordCrash(
                    CrashRecoveryManager.CrashType.CAMERA_ERROR,
                    "Camera startup error: ${e.message}"
                )
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun analyzeFrame(image: ImageProxy) {
        var bitmap: Bitmap? = null
        var rotated: Bitmap? = null
        
        try {
            // Validate image dimensions and state
            if (image.width <= 0 || image.height <= 0 || image.planes.isEmpty()) {
                return
            }
            
            val plane = image.planes[0]
            val buffer = plane.buffer
            
            // Check if buffer is valid and has data
            if (buffer.remaining() == 0) {
                return
            }
            
            // Create bitmap with proper error handling
            bitmap = try {
                Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("CameraFragment", "OOM creating bitmap", e)
                return
            }
            
            // Thread-safe buffer access with proper synchronization
            synchronized(buffer) {
                try {
                    // Ensure buffer position is at start
                    buffer.rewind()
                    
                    // Check buffer capacity matches expected size
                    val expectedSize = image.width * image.height * 4 // RGBA_8888 = 4 bytes per pixel
                    if (buffer.remaining() < expectedSize) {
                        android.util.Log.e("CameraFragment", "Buffer size mismatch: expected $expectedSize, got ${buffer.remaining()}")
                        return
                    }
                    
                    // Copy pixels from buffer to bitmap
                    bitmap?.copyPixelsFromBuffer(buffer)
                } catch (e: Exception) {
                    android.util.Log.e("CameraFragment", "Error copying buffer to bitmap", e)
                    return
                }
            }
            
            // Create rotated bitmap if needed
            rotated = if (image.imageInfo.rotationDegrees != 0) {
                bitmap?.let { bmp ->
                    try {
                        val matrix = Matrix().apply {
                            postRotate(image.imageInfo.rotationDegrees.toFloat())
                        }
                        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                    } catch (e: OutOfMemoryError) {
                        android.util.Log.e("CameraFragment", "OOM creating rotated bitmap", e)
                        bmp // Use original bitmap if rotation fails
                    } catch (e: Exception) {
                        android.util.Log.e("CameraFragment", "Error rotating bitmap", e)
                        bmp // Use original bitmap if rotation fails
                    }
                }
            } else {
                bitmap
            }
            
            // Validate final bitmap before processing
            if (rotated?.isRecycled != false) {
                android.util.Log.e("CameraFragment", "Final bitmap is null or recycled")
                return
            }
            
            // Process with detector on background thread to avoid blocking camera
            cameraExecutor?.execute {
                try {
                    // Double-check fragment state before processing
                    if (isAdded && _binding != null && !isProcessing) {
                        detector?.detect(rotated)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CameraFragment", "Error in detector processing", e)
                    CrashRecoveryManager.recordCrash(
                        CrashRecoveryManager.CrashType.GENERAL,
                        "Detector processing error: ${e.message}"
                    )
                } finally {
                    // Clean up rotated bitmap if it's different from original
                    if (rotated != bitmap && rotated?.isRecycled == false) {
                        try {
                            rotated.recycle()
                        } catch (e: Exception) {
                            android.util.Log.w("CameraFragment", "Error recycling rotated bitmap", e)
                        }
                    }
                }
            }
            
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("CameraFragment", "OOM in analyzeFrame", e)
            CrashRecoveryManager.recordCrash(
                CrashRecoveryManager.CrashType.OUT_OF_MEMORY,
                "Camera frame analysis OOM"
            )
        } catch (e: Exception) {
            android.util.Log.e("CameraFragment", "Unexpected error in analyzeFrame", e)
            CrashRecoveryManager.recordCrash(
                CrashRecoveryManager.CrashType.GENERAL,
                "Camera frame analysis error: ${e.message}"
            )
        } finally {
            // Always close the image first to release camera resources
            try {
                image.close()
            } catch (e: Exception) {
                android.util.Log.w("CameraFragment", "Error closing image", e)
            }
            
            // Clean up original bitmap
            bitmap?.let { bmp ->
                if (!bmp.isRecycled) {
                    try {
                        bmp.recycle()
                    } catch (e: Exception) {
                        android.util.Log.w("CameraFragment", "Error recycling bitmap", e)
                    }
                }
            }
        }
    }

    override fun onEmptyDetect() {
        // Check if fragment is still active before updating UI
        if (isAdded && _binding != null) {
            viewModel.updateResults(emptyList(), 0L)
        }
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        // Check if fragment is still active before updating UI
        if (isAdded && _binding != null) {
            viewModel.updateResults(boundingBoxes, inferenceTime)
        }
    }
    
    override fun onStop() {
        super.onStop()
        // Stop processing when navigating away (e.g., to results fragment)
        isProcessing = false
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            android.util.Log.e("CameraFragment", "Error unbinding camera in onStop", e)
            CrashRecoveryManager.recordCrash(
                CrashRecoveryManager.CrashType.CAMERA_ERROR,
                "Camera cleanup error during navigation: ${e.message}"
            )
        }
    }

    override fun onPause() {
        super.onPause()
        isProcessing = false
        // Stop camera analysis when fragment is paused
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            android.util.Log.e("CameraFragment", "Error unbinding camera in onPause", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isProcessing = false
        isSurfaceReady = false
        
        try {
            // Cleanup camera provider
            cameraProvider?.unbindAll()
            cameraProvider = null
            
            // Clear detector with proper synchronization
            detector?.clear()
            detector = null
            
            // Shutdown executor with timeout
            cameraExecutor?.let { executor ->
                if (!executor.isShutdown) {
                    executor.shutdown()
                    try {
                        if (!executor.awaitTermination(1000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                            android.util.Log.w("CameraFragment", "Executor did not terminate gracefully")
                            executor.shutdownNow()
                        }
                    } catch (e: InterruptedException) {
                        android.util.Log.w("CameraFragment", "Interrupted while waiting for executor termination")
                        executor.shutdownNow()
                        Thread.currentThread().interrupt()
                    }
                }
            }
            cameraExecutor = null
            
        } catch (e: Exception) {
            android.util.Log.e("CameraFragment", "Error in onDestroyView", e)
        }
        _binding = null
    }
    
    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return

        val name = "MedRecognition_${System.currentTimeMillis()}"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MedRecognition")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            requireActivity().contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        requireContext(),
                        "Error al guardar la foto: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(
                        requireContext(),
                        "Foto guardada en galería",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Increment session counter and update displays
                    sessionPhotosCount++
                    updatePhotoCounters()
                }
            }
        )
    }
}
