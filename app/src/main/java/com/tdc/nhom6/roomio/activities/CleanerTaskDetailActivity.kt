package com.tdc.nhom6.roomio.activities

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.CleaningImageAdapter
import com.tdc.nhom6.roomio.data.CleanerTaskRepository
import com.tdc.nhom6.roomio.fragments.TaskStatus
import com.tdc.nhom6.roomio.utils.CleanerStatusUtils
import java.io.File
import java.util.UUID

class CleanerTaskDetailActivity : AppCompatActivity() {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private lateinit var imageAdapter: CleaningImageAdapter
    private val uploadedImageUrls = mutableListOf<String>()
    private var bookingId: String = ""
    private var cameraImageUri: Uri? = null
    private var pendingPermissionRationaleShown = false

    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            try {
                android.util.Log.d("CleanerTaskDetail", "Photo result callback: success=$success")
                
                // Check if activity is still valid
                if (isFinishing || isDestroyed) {
                    android.util.Log.w("CleanerTaskDetail", "Activity is finishing/destroyed, ignoring photo result")
                    return@registerForActivityResult
                }
                
                if (!success) {
                    android.util.Log.d("CleanerTaskDetail", "Camera canceled or failed")
                    return@registerForActivityResult
                }
                
                val uri = cameraImageUri
                if (uri == null) {
                    android.util.Log.e("CleanerTaskDetail", "Camera image URI is null")
                    return@registerForActivityResult
                }
                
                // Verify adapter is initialized
                if (!::imageAdapter.isInitialized) {
                    android.util.Log.e("CleanerTaskDetail", "ImageAdapter not initialized")
                    return@registerForActivityResult
                }
                
                // If success=true, the camera app has already written the file
                // Just add it to the adapter without verification
                android.util.Log.d("CleanerTaskDetail", "Adding photo to adapter: $uri")
                
                // Add image directly (callback is already on UI thread)
                try {
                    if (!isFinishing && !isDestroyed && ::imageAdapter.isInitialized) {
                        val item = CleaningImageAdapter.ImageItem(uri = uri, isUploading = false)
                        val beforeCount = imageAdapter.itemCount
                        imageAdapter.addImage(item)
                        val afterCount = imageAdapter.itemCount
                        android.util.Log.d("CleanerTaskDetail", "✓ Photo added: before=$beforeCount, after=$afterCount, URI=$uri")
                        
                        // Force UI update
                        runOnUiThread {
                            updateMarkAsCleanButton()
                        }
                    } else {
                        android.util.Log.w("CleanerTaskDetail", "Activity state changed, skipping image add")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CleanerTaskDetail", "Error adding image: ${e.message}", e)
                    e.printStackTrace()
                    // Show error to user
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(this@CleanerTaskDetailActivity, "Error adding photo: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CleanerTaskDetail", "Error in photo callback: ${e.message}", e)
                e.printStackTrace()
                // Don't crash - just log the error
            }
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cleaner_task_detail)

        val roomId = intent.getStringExtra("ROOM_ID") ?: ""
        bookingId = intent.getStringExtra("BOOKING_ID") ?: ""
        val checkoutTime = intent.getStringExtra("CHECKOUT_TIME") ?: "11.00 PM"
        val notes = intent.getStringExtra("NOTES") ?: "Replace bedding and towels"

        // Back button
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Room ID
        findViewById<TextView>(R.id.tvRoomId).text = "Room $roomId"

        // Status - find task in repository to get status
        val tasks = CleanerTaskRepository.tasks().value ?: emptyList()
        var task = tasks.find { it.roomId == roomId }
        var status = task?.status ?: TaskStatus.DIRTY
        
        // If status is DIRTY, change to IN_PROGRESS when opening this screen
        if (status == TaskStatus.DIRTY && bookingId.isNotEmpty()) {
            android.util.Log.d("CleanerTaskDetail", "Changing status from DIRTY to IN_PROGRESS")
            status = TaskStatus.IN_PROGRESS
            task?.let { currentTask ->
                val updated = currentTask.copy(status = TaskStatus.IN_PROGRESS)
                CleanerTaskRepository.updateTask(updated)
                task = updated
                // Save to Firebase
                updateStatusInFirebase(TaskStatus.IN_PROGRESS)
            }
        }
        
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        tvStatus.text = CleanerStatusUtils.getStatusText(status)
        tvStatus.setBackgroundResource(R.drawable.bg_tab_chip)

        // Checked-out time
        findViewById<TextView>(R.id.tvCheckedOut).text = "Checked-out : $checkoutTime"

        // Notes
        findViewById<TextView>(R.id.tvNotes).text = notes

        // Setup image RecyclerView - MUST be initialized before takePhotoLauncher can be used
        val rvImages = findViewById<RecyclerView>(R.id.rvCleaningImages)
        rvImages.layoutManager = GridLayoutManager(this, 3)
        imageAdapter = CleaningImageAdapter(mutableListOf()) { position ->
            if (::imageAdapter.isInitialized) {
                imageAdapter.removeImage(position)
                updateMarkAsCleanButton()
            }
        }
        rvImages.adapter = imageAdapter
        android.util.Log.d("CleanerTaskDetail", "ImageAdapter initialized")

        // Upload images button - require taking photos with camera
        findViewById<MaterialButton>(R.id.btnUploadImages).setOnClickListener {
            ensureCameraPermissionAndOpenCamera()
        }

        // Mark as clean button - initially disabled
        val btnMarkAsClean = findViewById<MaterialButton>(R.id.btnStartCleaning)
        btnMarkAsClean.isEnabled = false
        btnMarkAsClean.alpha = 0.5f
        btnMarkAsClean.setOnClickListener {
            if (imageAdapter.itemCount == 0) {
                Toast.makeText(this, "Please upload at least one image", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            uploadImagesAndMarkAsClean(roomId, task)
        }
    }

    private fun updateStatusInFirebase(status: TaskStatus) {
        if (bookingId.isEmpty()) {
            android.util.Log.w("CleanerTaskDetail", "No bookingId, cannot update status in Firebase")
            return
        }
        
        val statusValue = CleanerStatusUtils.toFirebaseStatus(status)
        android.util.Log.d("CleanerTaskDetail", "Updating status in Firebase to: $statusValue for booking: $bookingId")
        
        // Find or create cleaner document and update status
        firestore.collection("bookings").document(bookingId)
            .collection("cleaner")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val cleanerDocRef = if (!snapshot.isEmpty) {
                    snapshot.documents.first().reference
                } else {
                    firestore.collection("bookings")
                        .document(bookingId)
                        .collection("cleaner")
                        .document()
                }
                
                cleanerDocRef.set(
                    mapOf(
                        "status" to statusValue,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .addOnSuccessListener {
                    android.util.Log.d("CleanerTaskDetail", "✓ Status updated to $statusValue in Firebase")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("CleanerTaskDetail", "Failed to update status: ${e.message}", e)
                }
                
                // Also update parent booking document
                firestore.collection("bookings").document(bookingId)
                    .update(
                        "cleanerStatusLatest", statusValue,
                        "cleanerStatusUpdatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                    .addOnFailureListener { e ->
                        android.util.Log.e("CleanerTaskDetail", "Failed to update booking status: ${e.message}", e)
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("CleanerTaskDetail", "Error querying cleaner documents: ${e.message}", e)
            }
    }

    private fun updateMarkAsCleanButton() {
        try {
            if (isFinishing || isDestroyed) {
                android.util.Log.w("CleanerTaskDetail", "Activity finishing/destroyed, skipping button update")
                return
            }
            if (!::imageAdapter.isInitialized) {
                android.util.Log.w("CleanerTaskDetail", "Cannot update button - adapter not initialized")
                return
            }
            val btnMarkAsClean = findViewById<MaterialButton>(R.id.btnStartCleaning) ?: run {
                android.util.Log.w("CleanerTaskDetail", "Button not found")
                return
            }
            val hasImages = imageAdapter.itemCount > 0
            btnMarkAsClean.isEnabled = hasImages
            btnMarkAsClean.alpha = if (hasImages) 1.0f else 0.5f
        } catch (e: Exception) {
            android.util.Log.e("CleanerTaskDetail", "Error updating mark as clean button: ${e.message}", e)
            // Don't crash - just log
        }
    }


    private fun openCamera() {
        try {
            val fileName = "cleaning_${System.currentTimeMillis()}.jpg"
            // Try external files directory first (more persistent), fallback to internal files, then cache
            val file = when {
                getExternalFilesDir(null) != null -> {
                    File(getExternalFilesDir(null), fileName)
                }
                filesDir != null -> {
                    File(filesDir, fileName)
                }
                else -> {
                    File(cacheDir, fileName)
                }
            }
            
            file.parentFile?.mkdirs()
            if (!file.exists()) {
                file.createNewFile()
            }
            android.util.Log.d("CleanerTaskDetail", "Creating camera file: ${file.absolutePath}")
            
            cameraImageUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            android.util.Log.d("CleanerTaskDetail", "FileProvider URI: $cameraImageUri")
            takePhotoLauncher.launch(cameraImageUri!!)
        } catch (e: Exception) {
            android.util.Log.e("CleanerTaskDetail", "Failed to open camera: ${e.message}", e)
            android.util.Log.e("CleanerTaskDetail", "Exception type: ${e.javaClass.simpleName}", e)
            Toast.makeText(this, "Unable to open camera: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureCameraPermissionAndOpenCamera() {
        val permissionState =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        when {
            permissionState == PackageManager.PERMISSION_GRANTED -> openCamera()
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) && !pendingPermissionRationaleShown -> {
                pendingPermissionRationaleShown = true
                Toast.makeText(this, "Camera permission is required to take cleaning photos", Toast.LENGTH_LONG).show()
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun uploadImagesAndMarkAsClean(roomId: String, task: com.tdc.nhom6.roomio.fragments.CleanerTask?) {
        val btnMarkAsClean = findViewById<MaterialButton>(R.id.btnStartCleaning)
        btnMarkAsClean.isEnabled = false
        btnMarkAsClean.text = "Uploading..."

        // Collect all existing image URLs first
        val existingUrls = imageAdapter.images.mapNotNull { it.url }
        uploadedImageUrls.clear()
        uploadedImageUrls.addAll(existingUrls)
        
        android.util.Log.d("CleanerTaskDetail", "Starting upload: ${existingUrls.size} existing URLs, ${imageAdapter.images.size} total images")

        // Find all images that need to be uploaded (have URI but no URL)
        val imagesToUpload = imageAdapter.images.filter { it.uri != null && it.url == null }
        
        if (imagesToUpload.isEmpty()) {
            // All images already have URLs (shouldn't happen in normal flow, but handle it)
            val allUrls = imageAdapter.images.mapNotNull { it.url }
            android.util.Log.d("CleanerTaskDetail", "All images already uploaded, using ${allUrls.size} URLs")
            if (allUrls.isEmpty()) {
                Toast.makeText(this, "No images to save", Toast.LENGTH_SHORT).show()
                btnMarkAsClean.isEnabled = true
                btnMarkAsClean.text = "Mark as clean"
                return
            }
            markTaskAsClean(roomId, task, allUrls)
            return
        }
        
        android.util.Log.d("CleanerTaskDetail", "Found ${imagesToUpload.size} photos to upload")

        var uploadCount = 0
        val totalUploads = imagesToUpload.size
        android.util.Log.d("CleanerTaskDetail", "Uploading $totalUploads new images")

        imagesToUpload.forEachIndexed { index, item ->
            val uri = item.uri ?: return@forEachIndexed
            val position = imageAdapter.images.indexOf(item)
            
            // Mark as uploading
            imageAdapter.updateImage(position, item.copy(isUploading = true))
            
            val imageId = UUID.randomUUID().toString()
            val storageRef = storage.reference.child("cleaning_images/$roomId/$imageId.jpg")
            
            android.util.Log.d("CleanerTaskDetail", "Uploading image $index/$totalUploads to: cleaning_images/$roomId/$imageId.jpg")
            
            // Verify file exists before uploading
            try {
                val filePath = uri.path
                android.util.Log.d("CleanerTaskDetail", "Uploading from URI: $uri, path: $filePath")
                if (filePath != null) {
                    val file = File(filePath)
                    if (!file.exists()) {
                        android.util.Log.e("CleanerTaskDetail", "File does not exist: $filePath")
                        imageAdapter.updateImage(position, item.copy(isUploading = false))
                        Toast.makeText(this, "Image file not found", Toast.LENGTH_SHORT).show()
                        btnMarkAsClean.isEnabled = true
                        btnMarkAsClean.text = "Mark as clean"
                        return@forEachIndexed
                    }
                    android.util.Log.d("CleanerTaskDetail", "File exists, size: ${file.length()} bytes")
                }
            } catch (e: Exception) {
                android.util.Log.w("CleanerTaskDetail", "Could not verify file existence: ${e.message}")
            }
            
            storageRef.putFile(uri)
                .addOnProgressListener { taskSnapshot ->
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                    android.util.Log.d("CleanerTaskDetail", "Upload progress for image $index: ${progress.toInt()}%")
                }
                .continueWithTask { uploadTask ->
                    if (!uploadTask.isSuccessful) {
                        val exception = uploadTask.exception
                        android.util.Log.e("CleanerTaskDetail", "Upload task failed: ${exception?.message}", exception)
                        throw exception ?: Exception("Upload failed")
                    }
                    storageRef.downloadUrl
                }
                .addOnSuccessListener { downloadUri ->
                    val url = downloadUri.toString()
                    uploadedImageUrls.add(url)
                    android.util.Log.d("CleanerTaskDetail", "✓ Image $index/$totalUploads uploaded successfully: $url")
                    
                    // Update image item with URL
                    imageAdapter.updateImage(position, item.copy(url = url, uri = null, isUploading = false))
                    
                    uploadCount++
                    android.util.Log.d("CleanerTaskDetail", "Upload progress: $uploadCount/$totalUploads complete")
                    if (uploadCount == totalUploads) {
                        // All uploads complete - collect all URLs from adapter
                        val allUrls = imageAdapter.images.mapNotNull { it.url }
                        android.util.Log.d("CleanerTaskDetail", "✓ All uploads complete! Total URLs collected: ${allUrls.size}")
                        if (allUrls.isEmpty()) {
                            android.util.Log.w("CleanerTaskDetail", "⚠ Warning: No image URLs collected!")
                            Toast.makeText(this, "Warning: No images were uploaded", Toast.LENGTH_LONG).show()
                        }
                        markTaskAsClean(roomId, task, allUrls)
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("CleanerTaskDetail", "✗ Image upload failed for image $index: ${e.message}", e)
                    android.util.Log.e("CleanerTaskDetail", "Error type: ${e.javaClass.simpleName}")
                    imageAdapter.updateImage(position, item.copy(isUploading = false))
                    Toast.makeText(this, "Failed to upload image ${index + 1}: ${e.message}", Toast.LENGTH_LONG).show()
                    btnMarkAsClean.isEnabled = true
                    btnMarkAsClean.text = "Mark as clean"
                }
        }
    }

    private fun markTaskAsClean(roomId: String, task: com.tdc.nhom6.roomio.fragments.CleanerTask?, imageUrls: List<String>) {
        android.util.Log.d("CleanerTaskDetail", "markTaskAsClean called with ${imageUrls.size} images, bookingId: $bookingId")
        
        // Update task status to CLEAN (completed) in local repository first
        task?.let { currentTask ->
            val updated = currentTask.copy(status = TaskStatus.CLEAN)
            CleanerTaskRepository.updateTask(updated)
            android.util.Log.d("CleanerTaskDetail", "Updated local task status to CLEAN")
        }

        // Save image URLs to cleaner subcollection if bookingId is available
        if (bookingId.isNotEmpty()) {
            android.util.Log.d("CleanerTaskDetail", "Saving to cleaner subcollection for bookingId: $bookingId")
            // Try to find the latest cleaner document or create a new one
            // First try to find any existing cleaner document
            firestore.collection("bookings").document(bookingId)
                .collection("cleaner")
                .limit(1)
                .get()
                .addOnSuccessListener { snapshot ->
                    val cleanerDocRef = if (!snapshot.isEmpty) {
                        // Update the most recent cleaner document
                        val existingDoc = snapshot.documents.first()
                        android.util.Log.d("CleanerTaskDetail", "Found existing cleaner document: ${existingDoc.id}, updating it")
                        existingDoc.reference
                    } else {
                        // Create new cleaner document
                        android.util.Log.d("CleanerTaskDetail", "No existing cleaner document found, creating new one")
                        firestore.collection("bookings")
                            .document(bookingId)
                            .collection("cleaner")
                            .document()
                    }
                    saveCleanerData(cleanerDocRef, imageUrls)
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("CleanerTaskDetail", "Error querying cleaner documents: ${e.message}", e)
                    // Create new document as fallback
                    val newDocRef = firestore.collection("bookings")
                        .document(bookingId)
                        .collection("cleaner")
                        .document()
                    android.util.Log.d("CleanerTaskDetail", "Creating new cleaner document as fallback")
                    saveCleanerData(newDocRef, imageUrls)
                }
        } else {
            // No bookingId, just update task and navigate back
            Toast.makeText(this, "Room marked as clean", Toast.LENGTH_SHORT).show()
            finish() // Navigate back to task list
        }
    }
    
    private fun saveCleanerData(cleanerDocRef: com.google.firebase.firestore.DocumentReference, imageUrls: List<String>) {
        val statusValue = CleanerStatusUtils.toFirebaseStatus(TaskStatus.CLEAN)
        val updates = mapOf(
            "images" to imageUrls,
            "status" to statusValue,
            "cleaningCompletedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        
        android.util.Log.d("CleanerTaskDetail", "Saving cleaner data to document: ${cleanerDocRef.id}")
        android.util.Log.d("CleanerTaskDetail", "Status: $statusValue, Images count: ${imageUrls.size}")
        if (imageUrls.isNotEmpty()) {
            android.util.Log.d("CleanerTaskDetail", "First image URL: ${imageUrls.first()}")
        }
        
        // Show loading indicator
        val btnMarkAsClean = findViewById<MaterialButton>(R.id.btnStartCleaning)
        btnMarkAsClean.isEnabled = false
        btnMarkAsClean.text = "Saving..."
        
        cleanerDocRef.set(updates, SetOptions.merge())
            .addOnSuccessListener {
                android.util.Log.d("CleanerTaskDetail", "✓ Successfully saved to cleaner subcollection: ${cleanerDocRef.id}")
                android.util.Log.d("CleanerTaskDetail", "  - Status: $statusValue")
                android.util.Log.d("CleanerTaskDetail", "  - Images: ${imageUrls.size}")
                
                // Also update the parent booking document's cleanerStatusLatest
                if (bookingId.isNotEmpty()) {
                    firestore.collection("bookings").document(bookingId)
                        .update(
                            "cleanerStatusLatest", statusValue,
                            "cleanerStatusUpdatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        .addOnSuccessListener {
                            android.util.Log.d("CleanerTaskDetail", "✓ Updated booking cleanerStatusLatest to: $statusValue")
                            // Only finish after everything is saved
                            runOnUiThread {
                                Toast.makeText(this@CleanerTaskDetailActivity, "Room marked as clean ✓", Toast.LENGTH_SHORT).show()
                                // Wait a moment for toast to show, then finish
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    if (!isFinishing && !isDestroyed) {
                                        finish()
                                    }
                                }, 1000)
                            }
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("CleanerTaskDetail", "Failed to update booking cleanerStatusLatest: ${e.message}", e)
                            // Still finish even if booking update fails (cleaner data is saved)
                            runOnUiThread {
                                Toast.makeText(this@CleanerTaskDetailActivity, "Room marked as clean ✓", Toast.LENGTH_SHORT).show()
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    if (!isFinishing && !isDestroyed) {
                                        finish()
                                    }
                                }, 1000)
                            }
                        }
                } else {
                    // No bookingId, just finish
                    runOnUiThread {
                        Toast.makeText(this@CleanerTaskDetailActivity, "Room marked as clean ✓", Toast.LENGTH_SHORT).show()
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (!isFinishing && !isDestroyed) {
                                finish()
                            }
                        }, 1000)
                    }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("CleanerTaskDetail", "✗ Failed to save cleaning data: ${e.message}", e)
                android.util.Log.e("CleanerTaskDetail", "Error code: ${e.javaClass.simpleName}")
                runOnUiThread {
                    btnMarkAsClean.isEnabled = true
                    btnMarkAsClean.text = "Mark as clean"
                    Toast.makeText(this@CleanerTaskDetailActivity, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
}
