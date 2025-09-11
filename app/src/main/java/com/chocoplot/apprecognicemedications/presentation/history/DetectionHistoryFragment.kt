package com.chocoplot.apprecognicemedications.presentation.history

import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.chocoplot.apprecognicemedications.core.Constants
import com.chocoplot.apprecognicemedications.core.CrashRecoveryManager
import com.chocoplot.apprecognicemedications.data.DetectionDatabase
import com.chocoplot.apprecognicemedications.data.DetectionSession
import com.chocoplot.apprecognicemedications.databinding.FragmentDetectionHistoryBinding
import com.chocoplot.apprecognicemedications.ml.Detector
import com.chocoplot.apprecognicemedications.ml.model.BoundingBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.*

class DetectionHistoryFragment : Fragment() {
    private var _binding: FragmentDetectionHistoryBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: DetectionHistoryAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetectionHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        setupRecyclerView()
        loadDetectionHistory()
    }
    
    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            try {
                findNavController().popBackStack()
            } catch (e: Exception) {
                Log.e(TAG, "Error navigating back", e)
                requireActivity().onBackPressed()
            }
        }
        
        // Setup button listeners
        binding.btnClearHistory.setOnClickListener {
            showClearHistoryDialog()
        }
        
        binding.btnGenerateReport.setOnClickListener {
            generateSummaryReport()
        }
        
        binding.btnDetectAllPhotos.setOnClickListener {
            detectAllGalleryPhotos()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = DetectionHistoryAdapter(
            onItemClick = { session ->
                // Navigate to detail view or show dialog with session details
                showSessionDetails(session)
            }
        )
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@DetectionHistoryFragment.adapter
        }
    }
    
    private fun loadDetectionHistory() {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.emptyText.visibility = View.GONE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = DetectionDatabase(requireContext())
                val sessions = db.getAllDetectionSessions()
                
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    
                    if (sessions.isEmpty()) {
                        binding.emptyText.visibility = View.VISIBLE
                        binding.recyclerView.visibility = View.GONE
                    } else {
                        binding.emptyText.visibility = View.GONE
                        binding.recyclerView.visibility = View.VISIBLE
                        adapter.submitList(sessions)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading detection history", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.emptyText.visibility = View.VISIBLE
                    binding.emptyText.text = "Error al cargar el historial: ${e.localizedMessage}"
                }
            }
        }
    }
    
    private fun showSessionDetails(session: DetectionSession) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = DetectionDatabase(requireContext())
                
                // Antes de mostrar, verificar si ya existe una sesión para esta foto
                val existingSession = db.findSessionByPhotoUri(session.photoUri)
                
                // Si existía una sesión anterior diferente a la actual, eliminarla
                if (existingSession != null && existingSession.id != session.id) {
                    Log.d(TAG, "Eliminando sesión duplicada anterior: ${existingSession.id}")
                    db.deleteDetectionSession(existingSession.id)
                    // Recargar la lista después de eliminar la sesión duplicada
                    withContext(Dispatchers.Main) {
                        loadDetectionHistory()
                    }
                    return@launch
                }
                
                // Continuar con la carga normal
                val detectionItems = db.getDetectionItems(session.id)
                val summary = db.getDetectionSummary(session.id)
                
                withContext(Dispatchers.Main) {
                    // Show a dialog with the detection details
                    // Or navigate to a detail fragment
                    // For simplicity, we'll just log the details for now
                    Log.d(TAG, "Session ${session.id} has ${detectionItems.size} items")
                    Log.d(TAG, "Summary: $summary")
                    
                    // TODO: Implement dialog or navigate to detail view
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading session details", e)
            }
        }
    }
    
    /**
     * Show confirmation dialog before clearing history
     */
    private fun showClearHistoryDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Limpiar Historial")
            .setMessage("¿Estás seguro de que deseas eliminar todo el historial de detecciones? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                clearDetectionHistory()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    /**
     * Clear all detection history from database
     */
    private fun clearDetectionHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = DetectionDatabase(requireContext())
                db.clearAllDetections()
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Historial eliminado exitosamente", Toast.LENGTH_SHORT).show()
                    loadDetectionHistory() // Reload the list
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing detection history", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error al eliminar el historial", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Generate and share a summary report of all detections
     */
    private fun generateSummaryReport() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = DetectionDatabase(requireContext())
                val sessions = db.getAllDetectionSessions()
                
                if (sessions.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "No hay detecciones para generar reporte", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                val report = buildSummaryReport(sessions, db)
                
                withContext(Dispatchers.Main) {
                    shareReport(report)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating report", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error al generar el reporte", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Build a comprehensive summary report with processing time information
     */
    private suspend fun buildSummaryReport(sessions: List<DetectionSession>, db: DetectionDatabase): String {
        val report = StringBuilder()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        
        report.append("📊 REPORTE RESUMEN DE DETECCIONES\n")
        report.append("================================\n\n")
        report.append("Generado el: ${dateFormat.format(Date())}\n")
        report.append("Total de sesiones: ${sessions.size}\n\n")
        
        var totalDetections = 0
        var totalProcessingTime = 0L
        var sessionsWithTiming = 0
        val medicationCounts = mutableMapOf<String, Int>()
        
        for (session in sessions) {
            val detectionItems = db.getDetectionItems(session.id)
            totalDetections += detectionItems.size
            
            // Track processing time if available
            if (session.processingTimeMs > 0) {
                totalProcessingTime += session.processingTimeMs
                sessionsWithTiming++
            }
            
            detectionItems.forEach { item ->
                val medicationName = item.className.trim().uppercase()
                medicationCounts[medicationName] = medicationCounts.getOrDefault(medicationName, 0) + 1
            }
        }
        
        report.append("RESUMEN GENERAL:\n")
        report.append("- Total detecciones: $totalDetections\n")
        report.append("- Medicamentos únicos: ${medicationCounts.size}\n")
        
        // Add processing time information
        if (sessionsWithTiming > 0) {
            val avgProcessingTime = totalProcessingTime / sessionsWithTiming
            report.append("- Tiempo total de procesamiento: ${totalProcessingTime / 1000.0} segundos\n")
            report.append("- Tiempo promedio por foto: ${avgProcessingTime}ms\n")
            report.append("- Sesiones con datos de tiempo: $sessionsWithTiming\n")
        }
        report.append("\n")
        
        if (medicationCounts.isNotEmpty()) {
            report.append("MEDICAMENTOS DETECTADOS:\n")
            medicationCounts.toList().sortedByDescending { it.second }.forEach { (name, count) ->
                report.append("• $name: $count detecciones\n")
            }
            report.append("\n")
        }
        
        report.append("ESTADÍSTICAS DE RENDIMIENTO:\n")
        if (sessionsWithTiming > 0) {
            val fastSessions = sessions.filter { it.processingTimeMs > 0 && it.processingTimeMs < 1000 }.size
            val mediumSessions = sessions.filter { it.processingTimeMs >= 1000 && it.processingTimeMs < 3000 }.size
            val slowSessions = sessions.filter { it.processingTimeMs >= 3000 }.size
            
            report.append("- Procesamiento rápido (<1s): $fastSessions sesiones\n")
            report.append("- Procesamiento medio (1-3s): $mediumSessions sesiones\n")
            report.append("- Procesamiento lento (>3s): $slowSessions sesiones\n\n")
        }
        
        report.append("DETALLE POR SESIÓN:\n")
        sessions.forEachIndexed { index, session ->
            val detectionItems = db.getDetectionItems(session.id)
            report.append("${index + 1}. ${dateFormat.format(Date(session.timestamp))}\n")
            report.append("   Foto: ${session.photoUri}\n")
            report.append("   Detecciones: ${detectionItems.size}\n")
            
            if (session.processingTimeMs > 0) {
                report.append("   Tiempo de procesamiento: ${session.processingTimeMs}ms\n")
            }
            
            if (detectionItems.isNotEmpty()) {
                val sessionMedications = detectionItems.groupBy { it.className.trim().uppercase() }
                sessionMedications.forEach { (name, items) ->
                    report.append("   - $name: ${items.size}\n")
                }
            } else {
                report.append("   - Sin medicamentos detectados\n")
            }
            report.append("\n")
        }
        
        return report.toString()
    }
    
    /**
     * Share the generated report
     */
    private fun shareReport(report: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, report)
            putExtra(Intent.EXTRA_SUBJECT, "Reporte de Detecciones de Medicamentos")
        }
        
        try {
            startActivity(Intent.createChooser(shareIntent, "Compartir reporte"))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing report", e)
            Toast.makeText(requireContext(), "Error al compartir el reporte", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Detect medications in all app gallery photos
     */
    private fun detectAllGalleryPhotos() {
        AlertDialog.Builder(requireContext())
            .setTitle("Detectar en Todas las Fotos")
            .setMessage("Esta función procesará todas las fotos tomadas con la app y puede tomar tiempo. ¿Deseas continuar?")
            .setPositiveButton("Continuar") { _, _ ->
                startBatchDetection()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    /**
     * Start batch detection process on app gallery photos with sequential processing
     */
    private fun startBatchDetection() {
        lifecycleScope.launch(Dispatchers.Main) {
            val startTime = System.currentTimeMillis()
            var totalProcessingTime = 0L
            
            try {
                val photos = getAppGalleryPhotos()
                
                if (photos.isEmpty()) {
                    Toast.makeText(requireContext(), "No se encontraron fotos tomadas con la app", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Show progress bar and disable buttons
                binding.progressBar.visibility = View.VISIBLE
                binding.btnDetectAllPhotos.isEnabled = false
                binding.btnClearHistory.isEnabled = false
                binding.btnGenerateReport.isEnabled = false
                
                Toast.makeText(requireContext(), "Iniciando procesamiento de ${photos.size} fotos...", Toast.LENGTH_SHORT).show()
                
                val db = DetectionDatabase(requireContext())
                var processedCount = 0
                var successCount = 0
                var errorCount = 0
                var totalDetections = 0
                
                // Process photos sequentially
                for ((index, photo) in photos.withIndex()) {
                    val photoStartTime = System.currentTimeMillis()
                    
                    try {
                        // Update progress dynamically
                        val progressPercentage = ((index.toFloat() / photos.size) * 100).toInt()
                        binding.toolbar.title = "Procesando: ${index + 1}/${photos.size} ($progressPercentage%)"
                        
                        Log.d(TAG, "Processing photo ${index + 1}/${photos.size}: $photo")
                        
                        // Process each photo individually using the same approach as PhotoDetailFragment
                        val detections = withContext(Dispatchers.IO) {
                            processPhotoForBatch(photo)
                        }
                        
                        val photoProcessingTime = System.currentTimeMillis() - photoStartTime
                        totalProcessingTime += photoProcessingTime
                        
                        Log.d(TAG, "Photo ${index + 1} completed with ${detections.size} detections in ${photoProcessingTime}ms")
                        
                        // Save results to database
                        withContext(Dispatchers.IO) {
                            // Check if detection already exists and delete it
                            val existingSession = db.findSessionByPhotoUri(photo)
                            if (existingSession != null) {
                                db.deleteDetectionSession(existingSession.id)
                                Log.d(TAG, "Replaced existing detection for photo: $photo")
                            }
                            
                            // Save detection results (including empty detections)
                            val sessionId = db.saveDetectionSessionWithTiming(photo, detections, photoProcessingTime)
                            
                            if (sessionId != -1L) {
                                successCount++
                                totalDetections += detections.size
                                Log.d(TAG, "Successfully saved photo: $photo with ${detections.size} detections")
                            } else {
                                errorCount++
                                Log.e(TAG, "Failed to save detection session for photo: $photo")
                            }
                        }
                        
                        processedCount++
                        
                        // Update UI progressively (every 3 photos or at the end)
                        if (index % 3 == 0 || index == photos.size - 1) {
                            loadDetectionHistory()
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing photo: $photo", e)
                        errorCount++
                        processedCount++
                        totalProcessingTime += (System.currentTimeMillis() - photoStartTime)
                    }
                }
                
                val totalTime = System.currentTimeMillis() - startTime
                
                // Reset UI
                binding.progressBar.visibility = View.GONE
                binding.btnDetectAllPhotos.isEnabled = true
                binding.btnClearHistory.isEnabled = true
                binding.btnGenerateReport.isEnabled = true
                binding.toolbar.title = "Historial de Detecciones"
                
                val message = if (errorCount == 0) {
                    "✅ Procesamiento completado exitosamente!\n" +
                    "📊 $successCount fotos procesadas\n" +
                    "🔍 $totalDetections medicamentos detectados\n" +
                    "⏱️ Tiempo total: ${totalTime / 1000}s\n" +
                    "⚡ Tiempo promedio: ${if (photos.isNotEmpty()) totalProcessingTime / photos.size else 0}ms por foto"
                } else {
                    "⚠️ Procesamiento completado con errores\n" +
                    "✅ $successCount exitosas\n" +
                    "❌ $errorCount errores\n" +
                    "🔍 $totalDetections medicamentos detectados\n" +
                    "⏱️ Tiempo total: ${totalTime / 1000}s"
                }
                
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                
                // Final reload of detection history
                loadDetectionHistory()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in batch detection", e)
                binding.progressBar.visibility = View.GONE
                binding.btnDetectAllPhotos.isEnabled = true
                binding.btnClearHistory.isEnabled = true
                binding.btnGenerateReport.isEnabled = true
                binding.toolbar.title = "Historial de Detecciones"
                
                Toast.makeText(requireContext(), "Error al procesar las fotos: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Get photos from app gallery (only photos taken with the app)
     */
    private fun getAppGalleryPhotos(): List<Uri> {
        val photos = mutableListOf<Uri>()
        
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )
            
            // Filter for app-taken photos: look for "MedRecognition" in display name
            val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("MedRecognition_%")
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            
            requireContext().contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(displayNameColumn) ?: ""
                    
                    // Double-check the naming pattern
                    if (displayName.startsWith("MedRecognition_")) {
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        photos.add(uri)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app gallery photos", e)
        }
        
        return photos
    }
    
    /**
     * Load bitmap from URI
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                
                // First pass: get dimensions
                BitmapFactory.decodeStream(inputStream, null, options)
                
                // Reset stream and decode with sample size
                requireContext().contentResolver.openInputStream(uri)?.use { newInputStream ->
                    options.inJustDecodeBounds = false
                    options.inSampleSize = calculateInSampleSize(options, 1024, 1024)
                    options.inPreferredConfig = Bitmap.Config.RGB_565
                    
                    BitmapFactory.decodeStream(newInputStream, null, options)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from URI: $uri", e)
            null
        }
    }
    
    /**
     * Calculate sample size for bitmap loading
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
    
    /**
     * Process a single photo for batch detection using same logic as manuallyProcessCurrentPhoto
     */
    private suspend fun processPhotoForBatch(photoUri: Uri): List<BoundingBox> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing photo for batch: $photoUri")

                // Reset crash recovery before manual-style processing (exactly like PhotoDetailFragment)
                CrashRecoveryManager.resetRecoveryMode()

                // Use Glide to decode full bitmap preserving aspect ratio (like PhotoDetailFragment)
                val futureTarget = Glide.with(requireContext())
                    .asBitmap()
                    .load(photoUri)
                    .submit()

                val bitmap = futureTarget.get()
                if (bitmap == null) {
                    Log.e(TAG, "Failed to load bitmap from URI: $photoUri")
                    return@withContext emptyList()
                }

                Log.d(TAG, "Loaded bitmap via Glide: ${bitmap.width}x${bitmap.height}")

                // Track processing time exactly like PhotoDetailFragment
                val startTime = System.currentTimeMillis()
                val deferred = CompletableDeferred<List<BoundingBox>>()

                val detector = Detector(requireContext(), Constants.MODEL_PATH, Constants.LABELS_PATH, object : Detector.DetectorListener {
                    override fun onEmptyDetect() {
                        Log.d(TAG, "Batch: No medications in $photoUri")
                        deferred.complete(emptyList())
                    }

                    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
                        Log.d(TAG, "Batch: Found ${boundingBoxes.size} medications in $photoUri (${inferenceTime}ms)")
                        boundingBoxes.forEach { box ->
                            Log.d(TAG, "  - ${box.clsName}: ${box.cnf}")
                        }
                        deferred.complete(boundingBoxes)
                    }
                })

                detector.setup()
                detector.detect(bitmap)

                val result = withTimeoutOrNull(15000L) { deferred.await() } ?: emptyList()
                val processingTime = System.currentTimeMillis() - startTime

                // Save results to DB exactly like PhotoDetailFragment.saveResultsToDatabase
                val db = DetectionDatabase(requireContext())
                val existing = db.findSessionByPhotoUri(photoUri)
                if (existing != null) {
                    Log.d(TAG, "Replacing existing detection for photo: ${existing.id}")
                    db.deleteDetectionSession(existing.id)
                }
                
                val sessionId = db.saveDetectionSessionWithTiming(photoUri, result, processingTime)
                Log.d(TAG, "Saved batch detection with ID: $sessionId and ${result.size} detections")

                // Clean up
                detector.clear()
                bitmap.recycle()
                
                Log.d(TAG, "Batch processing completed for $photoUri: ${result.size} detections in ${processingTime}ms")
                result
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing photo for batch: $photoUri", e)
                emptyList()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        private const val TAG = "DetectionHistoryFragment"
    }
}