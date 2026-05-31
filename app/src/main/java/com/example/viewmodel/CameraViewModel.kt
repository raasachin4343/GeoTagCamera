package com.example.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.PhotoDatabase
import com.example.data.PhotoEntry
import com.example.repository.PhotoRepository
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraViewModel(private val repository: PhotoRepository) : ViewModel() {

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _currentAddress = MutableStateFlow("Awaiting precise GPS lock...")
    val currentAddress: StateFlow<String> = _currentAddress.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _isCompassHeading = MutableStateFlow(0f)
    val isCompassHeading: StateFlow<Float> = _isCompassHeading.asStateFlow()

    val photosHistory: StateFlow<List<PhotoEntry>> = repository.allPhotosFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList<PhotoEntry>()
        )

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    init {
        // Generate random mock directions for a subtle organic rotating compass UI
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(100)
                // Smoothly drift heading slightly to look active
                val change = (Math.random() - 0.5) * 5
                _isCompassHeading.value = ((_isCompassHeading.value + change + 360) % 360).toFloat()
            }
        }
    }

    /**
     * Start requesting location updates.
     */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates(context: Context) {
        if (fusedLocationClient == null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val location = locationResult.lastLocation ?: return
                _currentLocation.value = location
                geocodeLocation(context, location.latitude, location.longitude)
            }
        }

        try {
            locationCallback?.let {
                fusedLocationClient?.requestLocationUpdates(
                    locationRequest,
                    it,
                    Looper.getMainLooper()
                )
            }
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Error starting location updates", e)
        }
    }

    /**
     * Stop requesting location updates.
     */
    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
        }
    }

    /**
     * Reverse geocodes the lat/lon coordinates into a street-level address.
     */
    private fun geocodeLocation(context: Context, latitude: Double, longitude: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                        val addressStr = addresses.firstOrNull()?.let { addr ->
                            val parts = mutableListOf<String>()
                            for (i in 0..addr.maxAddressLineIndex) {
                                val line = addr.getAddressLine(i)
                                if (!line.isNullOrBlank()) {
                                    parts.add(line)
                                }
                            }
                            parts.joinToString(", ")
                        } ?: "Coordinates Saved (${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)})"
                        _currentAddress.value = addressStr
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    val addressStr = addresses?.firstOrNull()?.let { addr ->
                        val parts = mutableListOf<String>()
                        for (i in 0..addr.maxAddressLineIndex) {
                            val line = addr.getAddressLine(i)
                            if (!line.isNullOrBlank()) {
                                parts.add(line)
                            }
                        }
                        parts.joinToString(", ")
                    } ?: "Coordinates Saved (${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)})"
                    _currentAddress.value = addressStr
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Geocoding failed", e)
                _currentAddress.value = "Coordinates Saved (${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)})"
            }
        }
    }

    fun updateCompassHeading(newHeading: Float) {
        _isCompassHeading.value = newHeading
    }

    /**
     * Capture Photo and embed Location EXIF tags.
     */
    fun capturePhoto(
        context: Context,
        imageCapture: ImageCapture,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isCapturing.value) return
        _isCapturing.value = true

        val outputDirectory = File(context.getExternalFilesDir(null), "GeoTagCameraPhotos")
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }

        val filename = "GEOTAG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val photoFile = File(outputDirectory, filename)

        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputFileOptions,
            Dispatchers.IO.asExecutor(),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedPath = photoFile.absolutePath
                    val loc = _currentLocation.value
                    val lat = loc?.latitude ?: 0.0
                    val lon = loc?.longitude ?: 0.0
                    val alt = loc?.altitude ?: 0.0
                    val addr = _currentAddress.value
                    val time = System.currentTimeMillis()

                    // Embed EXIF coordinates
                    writeExifData(savedPath, lat, lon, alt, time)

                    viewModelScope.launch {
                        // Persist to Room
                        val entry = PhotoEntry(
                            filePath = savedPath,
                            latitude = lat,
                            longitude = lon,
                            altitude = alt,
                            address = addr,
                            timestamp = time
                        )
                        repository.insertPhoto(entry)

                        // Scan file so it's registered on device media scanner
                        withContext(Dispatchers.IO) {
                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(savedPath),
                                arrayOf("image/jpeg"),
                                null
                            )
                        }

                        _isCapturing.value = false
                        onSuccess(savedPath)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    _isCapturing.value = false
                    Log.e("CameraViewModel", "Photo capture failed: ${exception.message}", exception)
                    onError(exception.message ?: "Unknown image capture error")
                }
            }
        )
    }

    /**
     * Writes precise EXIF Latitude, Longitude, Altitude, and timestamp to JPEG file tags.
     */
    private fun writeExifData(filePath: String, lat: Double, lon: Double, alt: Double, timestamp: Long) {
        try {
            val exif = ExifInterface(filePath)
            exif.setLatLong(lat, lon)
            
            // Set Altitude
            val altVal = Math.abs(alt)
            val altRef = if (alt >= 0) "0" else "1"
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, altRef)
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "$altVal/1")

            // Set Timestamp
            val formattedDate = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(timestamp))
            exif.setAttribute(ExifInterface.TAG_DATETIME, formattedDate)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, formattedDate)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, formattedDate)

            exif.saveAttributes()
            Log.d("CameraViewModel", "Successfully wrote EXIF data to $filePath: Lat=$lat, Lon=$lon, Alt=$alt")
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Failed to write EXIF metadata", e)
        }
    }

    /**
     * Delete a photo entry from database and disk.
     */
    fun deletePhoto(photo: PhotoEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete from disk
            try {
                val file = File(photo.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Failed to delete physical photo file", e)
            }
            // Delete from Room
            repository.deletePhoto(photo)
        }
    }
}

class CameraViewModelFactory(private val repository: PhotoRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CameraViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CameraViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


