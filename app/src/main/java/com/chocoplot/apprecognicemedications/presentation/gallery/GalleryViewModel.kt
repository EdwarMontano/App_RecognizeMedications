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

    private var detector: Detector? = null

    fun initializeDetector(context: Context) {
        Log.d("GalleryViewModel", "Initializing detector...")
        detector = Detector(context, Constants.MODEL_PATH, Constants.LABELS_PATH, this).apply {
            setup()
        }
        Log.d("GalleryViewModel", "Detector initialized")
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
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    loadBitmapFromUri(context, currentPhoto.uri)
                }
                bitmap?.let { bmp ->
                    Log.d("GalleryViewModel", "Loaded bitmap: ${bmp.width}x${bmp.height}")
                    // Store image size for overlay scaling
                    _imageSize.postValue(Pair(bmp.width, bmp.height))
                    // Process with detector
                    Log.d("GalleryViewModel", "Running detector...")
                    detector?.detect(bmp)
                } ?: run {
                    Log.e("GalleryViewModel", "Failed to load bitmap from URI")
                    onEmptyDetect()
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Error processing photo", e)
                e.printStackTrace()
                onEmptyDetect()
            }
        }
    }

    fun processImageUri(context: Context, uri: Uri) {
        Log.d("GalleryViewModel", "Processing image URI directly: $uri")
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    loadBitmapFromUri(context, uri)
                }
                bitmap?.let { bmp ->
                    Log.d("GalleryViewModel", "Loaded bitmap from URI: ${bmp.width}x${bmp.height}")
                    // Store image size for overlay scaling
                    _imageSize.postValue(Pair(bmp.width, bmp.height))
                    // Process with detector
                    Log.d("GalleryViewModel", "Running detector on URI image...")
                    detector?.detect(bmp)
                } ?: run {
                    Log.e("GalleryViewModel", "Failed to load bitmap from URI")
                    onEmptyDetect()
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Error processing image URI", e)
                e.printStackTrace()
                onEmptyDetect()
            }
        }
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
        detector?.clear()
    }
}