// app/src/main/java/com/chocoplot/apprecognicemedications/presentation/gallery/GalleryViewModel.kt
package com.chocoplot.apprecognicemedications.presentation.gallery

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chocoplot.apprecognicemedications.core.Constants
import com.chocoplot.apprecognicemedications.ml.Detector
import com.chocoplot.apprecognicemedications.ml.model.BoundingBox
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class GalleryPhoto(
    val uri: Uri,
    val dateAdded: Long,
    val displayName: String,
    var isSelected: Boolean = false
)

@HiltViewModel
class GalleryViewModel @Inject constructor() : ViewModel(), Detector.DetectorListener {

    private val _galleryPhotos = MutableLiveData<List<GalleryPhoto>>(emptyList())
    val galleryPhotos: LiveData<List<GalleryPhoto>> = _galleryPhotos

    private val _currentPhotoIndex = MutableLiveData(0)
    val currentPhotoIndex: LiveData<Int> = _currentPhotoIndex

    private val _currentPhoto = MutableLiveData<GalleryPhoto?>()
    val currentPhoto: LiveData<GalleryPhoto?> = _currentPhoto

    private val _results = MutableLiveData<List<BoundingBox>>(emptyList())
    val results: LiveData<List<BoundingBox>> = _results

    private val _inferenceTime = MutableLiveData(0L)
    val inferenceTime: LiveData<Long> = _inferenceTime

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _imageSize = MutableLiveData<Pair<Int, Int>?>()
    val imageSize: LiveData<Pair<Int, Int>?> = _imageSize

    // Selection mode properties
    private val _isSelectionMode = MutableLiveData(false)
    val isSelectionMode: LiveData<Boolean> = _isSelectionMode

    private val _selectedPhotos = MutableLiveData<Set<Uri>>(emptySet())
    val selectedPhotos: LiveData<Set<Uri>> = _selectedPhotos

    private val _selectedCount = MutableLiveData(0)
    val selectedCount: LiveData<Int> = _selectedCount

    // Operation states
    private val _isOperationInProgress = MutableLiveData(false)
    val isOperationInProgress: LiveData<Boolean> = _isOperationInProgress

    private val _operationMessage = MutableLiveData<String>()
    val operationMessage: LiveData<String> = _operationMessage

    // Result messages
    private val _showSuccessMessage = MutableLiveData<String?>()
    val showSuccessMessage: LiveData<String?> = _showSuccessMessage

    private val _showErrorMessage = MutableLiveData<String?>()
    val showErrorMessage: LiveData<String?> = _showErrorMessage

    private var detector: Detector? = null
    private var isDetectorInitialized = false
    private var isDetectorInitializing = false

    fun initializeDetector(context: Context) {
        if (isDetectorInitialized || isDetectorInitializing) {
            Log.d("GalleryViewModel", "Detector already initialized or initializing")
            return
        }
        
        isDetectorInitializing = true
        Log.d("GalleryViewModel", "Initializing detector in background...")
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newDetector = Detector(context, Constants.MODEL_PATH, Constants.LABELS_PATH, this@GalleryViewModel)
                newDetector.setup()
                
                // Switch to main thread to update the detector reference
                withContext(Dispatchers.Main) {
                    detector = newDetector
                    isDetectorInitialized = true
                    Log.d("GalleryViewModel", "Detector initialized successfully")
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Error initializing detector", e)
                withContext(Dispatchers.Main) {
                    // Signal initialization failure
                    onEmptyDetect()
                }
            } finally {
                isDetectorInitializing = false
            }
        }
    }

    fun loadGalleryPhotos(context: Context) {
        Log.d("GalleryViewModel", "Starting to load gallery photos...")
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val photos = withContext(Dispatchers.IO) {
                    getGalleryPhotos(context)
                }
                Log.d("GalleryViewModel", "Found ${photos.size} photos in gallery")
                _galleryPhotos.value = photos
                if (photos.isNotEmpty()) {
                    Log.d("GalleryViewModel", "Selecting first photo: ${photos[0]}")
                    selectPhoto(0)
                } else {
                    Log.w("GalleryViewModel", "No photos found in gallery")
                    // No photos available
                    _currentPhoto.value = null
                    _currentPhotoIndex.value = 0
                    onEmptyDetect()
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Error loading gallery photos", e)
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun getGalleryPhotos(context: Context): List<GalleryPhoto> {
        val photos = mutableListOf<GalleryPhoto>()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        
        // Filter for app-taken photos: look for "MedRecognition" in display name
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("MedRecognition_%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(displayNameColumn) ?: ""
                val dateAdded = cursor.getLong(dateAddedColumn)
                
                // Double-check the naming pattern
                if (displayName.startsWith("MedRecognition_")) {
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    photos.add(GalleryPhoto(uri, dateAdded, displayName))
                }
            }
        }
        
        return photos
    }

    fun selectPhoto(index: Int) {
        val photos = _galleryPhotos.value ?: return
        if (photos.isEmpty()) return
        if (index in 0 until photos.size) {
            _currentPhotoIndex.value = index
            _currentPhoto.value = photos[index]
        }
    }

    fun navigateToNext() {
        val currentIndex = _currentPhotoIndex.value ?: 0
        val photos = _galleryPhotos.value ?: return
        if (photos.isEmpty()) return
        val nextIndex = (currentIndex + 1) % photos.size
        selectPhoto(nextIndex)
    }

    fun navigateToPrevious() {
        val currentIndex = _currentPhotoIndex.value ?: 0
        val photos = _galleryPhotos.value ?: return
        if (photos.isEmpty()) return
        val previousIndex = if (currentIndex > 0) currentIndex - 1 else photos.size - 1
        selectPhoto(previousIndex)
    }

    fun processCurrentPhoto(context: Context) {
        val currentPhoto = _currentPhoto.value ?: return
        Log.d("GalleryViewModel", "Processing photo: ${currentPhoto.uri}")
        
        if (!isDetectorInitialized) {
            Log.w("GalleryViewModel", "Detector not initialized, skipping processing")
            onEmptyDetect()
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = loadBitmapFromUri(context, currentPhoto.uri)
                bitmap?.let { bmp ->
                    Log.d("GalleryViewModel", "Loaded bitmap: ${bmp.width}x${bmp.height}")
                    
                    // Store image size for overlay scaling on main thread
                    withContext(Dispatchers.Main) {
                        _imageSize.value = Pair(bmp.width, bmp.height)
                    }
                    
                    // Process with detector in background
                    Log.d("GalleryViewModel", "Running detector...")
                    detector?.detect(bmp)
                } ?: run {
                    Log.e("GalleryViewModel", "Failed to load bitmap from URI")
                    onEmptyDetect()
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Error processing photo", e)
                onEmptyDetect()
            }
        }
    }

    fun processImageUri(context: Context, uri: Uri) {
        Log.d("GalleryViewModel", "Processing image URI directly: $uri")
        
        if (!isDetectorInitialized) {
            Log.w("GalleryViewModel", "Detector not initialized, skipping processing")
            onEmptyDetect()
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = loadBitmapFromUri(context, uri)
                bitmap?.let { bmp ->
                    Log.d("GalleryViewModel", "Loaded bitmap from URI: ${bmp.width}x${bmp.height}")
                    
                    // Store image size for overlay scaling on main thread
                    withContext(Dispatchers.Main) {
                        _imageSize.value = Pair(bmp.width, bmp.height)
                    }
                    
                    // Process with detector in background
                    Log.d("GalleryViewModel", "Running detector on URI image...")
                    detector?.detect(bmp)
                } ?: run {
                    Log.e("GalleryViewModel", "Failed to load bitmap from URI")
                    onEmptyDetect()
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Error processing image URI", e)
                onEmptyDetect()
            }
        }
    }
    
    /**
     * Process a bitmap directly without scaling/cropping to maintain aspect ratio
     * This is used for manual detection to ensure proper aspect ratio
     */
    fun processImageDirectly(bitmap: Bitmap) {
        Log.d("GalleryViewModel", "Processing bitmap directly: ${bitmap.width}x${bitmap.height}")
        
        if (!isDetectorInitialized) {
            Log.w("GalleryViewModel", "Detector not initialized, skipping processing")
            onEmptyDetect()
            return
        }
        
        try {
            // Store original image size for overlay scaling
            _imageSize.postValue(Pair(bitmap.width, bitmap.height))
            
            // Process with detector
            Log.d("GalleryViewModel", "Running detector on original bitmap...")
            detector?.detect(bitmap)
        } catch (e: Exception) {
            Log.e("GalleryViewModel", "Error processing bitmap directly", e)
            onEmptyDetect()
        }
    }

    // Selection mode functions
    fun toggleSelectionMode() {
        val currentMode = _isSelectionMode.value ?: false
        Log.d("GalleryViewModel", "Toggling selection mode from $currentMode to ${!currentMode}")
        _isSelectionMode.value = !currentMode
        
        if (!currentMode) {
            // Entering selection mode - clear any previous selections
            Log.d("GalleryViewModel", "Entering selection mode")
            clearAllSelections()
        } else {
            // Exiting selection mode - clear selections
            Log.d("GalleryViewModel", "Exiting selection mode")
            clearAllSelections()
        }
    }

    fun exitSelectionMode() {
        Log.d("GalleryViewModel", "Explicitly exiting selection mode")
        _isSelectionMode.value = false
        clearAllSelections()
    }

    fun togglePhotoSelection(photoUri: Uri) {
        val currentSelections = _selectedPhotos.value ?: emptySet()
        val newSelections = if (currentSelections.contains(photoUri)) {
            currentSelections - photoUri
        } else {
            currentSelections + photoUri
        }
        
        _selectedPhotos.value = newSelections
        _selectedCount.value = newSelections.size
        
        // Update the photo selection state in the list
        updatePhotoSelectionState(photoUri, newSelections.contains(photoUri))
    }

    private fun updatePhotoSelectionState(uri: Uri, isSelected: Boolean) {
        val currentPhotos = _galleryPhotos.value ?: return
        val updatedPhotos = currentPhotos.map { photo ->
            if (photo.uri == uri) {
                photo.copy(isSelected = isSelected)
            } else {
                photo
            }
        }
        _galleryPhotos.value = updatedPhotos
    }

    private fun clearAllSelections() {
        Log.d("GalleryViewModel", "Clearing all selections")
        val currentPhotos = _galleryPhotos.value ?: return
        val updatedPhotos = currentPhotos.map { it.copy(isSelected = false) }
        _galleryPhotos.value = updatedPhotos
        _selectedPhotos.value = emptySet()
        _selectedCount.value = 0
        Log.d("GalleryViewModel", "Selections cleared - count: ${_selectedCount.value}")
    }

    fun selectAllPhotos() {
        val currentPhotos = _galleryPhotos.value ?: return
        val allUris = currentPhotos.map { it.uri }.toSet()
        val updatedPhotos = currentPhotos.map { it.copy(isSelected = true) }
        
        _galleryPhotos.value = updatedPhotos
        _selectedPhotos.value = allUris
        _selectedCount.value = allUris.size
    }

    // Deletion functions
    fun deleteSelectedPhotos(context: Context) {
        val selectedUris = _selectedPhotos.value ?: emptySet()
        if (selectedUris.isEmpty()) {
            _showErrorMessage.value = "No hay fotos seleccionadas"
            return
        }

        viewModelScope.launch {
            _isOperationInProgress.value = true
            _operationMessage.value = "Eliminando fotos seleccionadas..."

            try {
                val deletedCount = withContext(Dispatchers.IO) {
                    var count = 0
                    selectedUris.forEach { uri ->
                        try {
                            val rowsDeleted = context.contentResolver.delete(uri, null, null)
                            if (rowsDeleted > 0) count++
                        } catch (e: Exception) {
                            Log.e("GalleryViewModel", "Error deleting photo: $uri", e)
                        }
                    }
                    count
                }

                if (deletedCount > 0) {
                    _showSuccessMessage.value = "Se eliminaron $deletedCount fotos exitosamente"
                    // Reload gallery to reflect changes
                    loadGalleryPhotos(context)
                    exitSelectionMode()
                } else {
                    _showErrorMessage.value = "No se pudieron eliminar las fotos"
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Error during deletion operation", e)
                _showErrorMessage.value = "Error al eliminar las fotos: ${e.localizedMessage}"
            } finally {
                _isOperationInProgress.value = false
                _operationMessage.value = ""
            }
        }
    }

    fun deleteAllPhotos(context: Context) {
        val allPhotos = _galleryPhotos.value ?: emptyList()
        if (allPhotos.isEmpty()) {
            _showErrorMessage.value = "No hay fotos para eliminar"
            return
        }

        viewModelScope.launch {
            _isOperationInProgress.value = true
            _operationMessage.value = "Vaciando galería..."

            try {
                val deletedCount = withContext(Dispatchers.IO) {
                    var count = 0
                    allPhotos.forEach { photo ->
                        try {
                            val rowsDeleted = context.contentResolver.delete(photo.uri, null, null)
                            if (rowsDeleted > 0) count++
                        } catch (e: Exception) {
                            Log.e("GalleryViewModel", "Error deleting photo: ${photo.uri}", e)
                        }
                    }
                    count
                }

                if (deletedCount > 0) {
                    _showSuccessMessage.value = "Galería vaciada exitosamente. Se eliminaron $deletedCount fotos"
                    // Reload gallery to reflect changes
                    loadGalleryPhotos(context)
                    exitSelectionMode()
                } else {
                    _showErrorMessage.value = "No se pudieron eliminar las fotos"
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Error during delete all operation", e)
                _showErrorMessage.value = "Error al vaciar la galería: ${e.localizedMessage}"
            } finally {
                _isOperationInProgress.value = false
                _operationMessage.value = ""
            }
        }
    }

    // UI State helper functions
    fun hasSelectedPhotos(): Boolean {
        return (_selectedCount.value ?: 0) > 0
    }

    fun hasPhotos(): Boolean {
        return (_galleryPhotos.value?.size ?: 0) > 0
    }

    fun getSelectedPhotosCount(): Int {
        return _selectedCount.value ?: 0
    }

    // Message clearing functions
    fun clearSuccessMessage() {
        _showSuccessMessage.value = null
    }

    fun clearErrorMessage() {
        _showErrorMessage.value = null
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // Use options to avoid memory issues
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                
                // First pass: get dimensions
                BitmapFactory.decodeStream(inputStream, null, options)
                
                // Reset stream
                context.contentResolver.openInputStream(uri)?.use { newInputStream ->
                    // Calculate sample size if image is too large
                    options.inJustDecodeBounds = false
                    options.inSampleSize = calculateInSampleSize(options, 1024, 1024)
                    options.inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory
                    
                    BitmapFactory.decodeStream(newInputStream, null, options)
                }
            }
        } catch (e: OutOfMemoryError) {
            Log.e("GalleryViewModel", "OutOfMemory loading bitmap", e)
            System.gc() // Force garbage collection
            null
        } catch (e: Exception) {
            Log.e("GalleryViewModel", "Error loading bitmap", e)
            null
        }
    }
    
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

    override fun onEmptyDetect() {
        Log.d("GalleryViewModel", "No detections found")
        _results.postValue(emptyList())
        _inferenceTime.postValue(0L)
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        Log.d("GalleryViewModel", "Detected ${boundingBoxes.size} objects in ${inferenceTime}ms")
        _results.postValue(boundingBoxes)
        _inferenceTime.postValue(inferenceTime)
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("GalleryViewModel", "Clearing ViewModel resources")
        
        try {
            detector?.clear()
            detector = null
            isDetectorInitialized = false
            isDetectorInitializing = false
        } catch (e: Exception) {
            Log.e("GalleryViewModel", "Error clearing detector", e)
        }
    }
}