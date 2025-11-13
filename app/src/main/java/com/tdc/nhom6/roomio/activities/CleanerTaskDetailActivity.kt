package com.tdc.nhom6.roomio.activities

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.CleaningImageAdapter
import com.tdc.nhom6.roomio.data.CleanerTaskRepository
import com.tdc.nhom6.roomio.fragments.TaskStatus
import java.util.UUID

class CleanerTaskDetailActivity : AppCompatActivity() {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private lateinit var imageAdapter: CleaningImageAdapter
    private val uploadedImageUrls = mutableListOf<String>()
    private var bookingId: String = ""

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                val item = CleaningImageAdapter.ImageItem(uri = uri, isUploading = false)
                val position = imageAdapter.images.size
                imageAdapter.addImage(item)
                updateMarkAsCleanButton()
            }
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
        val task = tasks.find { it.roomId == roomId }
        val status = task?.status ?: TaskStatus.DIRTY
        
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        when (status) {
            TaskStatus.DIRTY -> {
                tvStatus.text = "Dirty"
                tvStatus.setBackgroundResource(R.drawable.bg_tab_chip)
            }
            TaskStatus.IN_PROGRESS -> {
                tvStatus.text = "In progress"
                tvStatus.setBackgroundResource(R.drawable.bg_tab_chip)
            }
            TaskStatus.CLEAN -> {
                tvStatus.text = "Cleaned"
                tvStatus.setBackgroundResource(R.drawable.bg_tab_chip)
            }
            else -> {
                tvStatus.text = "Dirty"
                tvStatus.setBackgroundResource(R.drawable.bg_tab_chip)
            }
        }

        // Checked-out time
        findViewById<TextView>(R.id.tvCheckedOut).text = "Checked-out : $checkoutTime"

        // Notes
        findViewById<TextView>(R.id.tvNotes).text = notes

        // Setup image RecyclerView
        val rvImages = findViewById<RecyclerView>(R.id.rvCleaningImages)
        rvImages.layoutManager = GridLayoutManager(this, 3)
        imageAdapter = CleaningImageAdapter(mutableListOf()) { position ->
            imageAdapter.removeImage(position)
            updateMarkAsCleanButton()
        }
        rvImages.adapter = imageAdapter

        // Upload images button
        findViewById<MaterialButton>(R.id.btnUploadImages).setOnClickListener {
            pickImagesLauncher.launch("image/*")
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

    private fun updateMarkAsCleanButton() {
        val btnMarkAsClean = findViewById<MaterialButton>(R.id.btnStartCleaning)
        val hasImages = imageAdapter.itemCount > 0
        btnMarkAsClean.isEnabled = hasImages
        btnMarkAsClean.alpha = if (hasImages) 1.0f else 0.5f
    }

    private fun uploadImagesAndMarkAsClean(roomId: String, task: com.tdc.nhom6.roomio.fragments.CleanerTask?) {
        val btnMarkAsClean = findViewById<MaterialButton>(R.id.btnStartCleaning)
        btnMarkAsClean.isEnabled = false
        btnMarkAsClean.text = "Uploading..."

        val imagesToUpload = imageAdapter.images.filter { it.uri != null && it.url == null }
        if (imagesToUpload.isEmpty()) {
            // All images already uploaded or no images
            markTaskAsClean(roomId, task, uploadedImageUrls)
            return
        }

        var uploadCount = 0
        val totalUploads = imagesToUpload.size

        imagesToUpload.forEachIndexed { index, item ->
            val uri = item.uri ?: return@forEachIndexed
            val position = imageAdapter.images.indexOf(item)
            
            // Mark as uploading
            imageAdapter.updateImage(position, item.copy(isUploading = true))
            
            val imageId = UUID.randomUUID().toString()
            val storageRef = storage.reference.child("cleaning_images/$roomId/$imageId.jpg")
            
            storageRef.putFile(uri)
                .continueWithTask { uploadTask ->
                    if (!uploadTask.isSuccessful) {
                        throw uploadTask.exception ?: Exception("Upload failed")
                    }
                    storageRef.downloadUrl
                }
                .addOnSuccessListener { downloadUri ->
                    val url = downloadUri.toString()
                    uploadedImageUrls.add(url)
                    
                    // Update image item with URL
                    imageAdapter.updateImage(position, item.copy(url = url, uri = null, isUploading = false))
                    
                    uploadCount++
                    if (uploadCount == totalUploads) {
                        // All uploads complete
                        markTaskAsClean(roomId, task, uploadedImageUrls)
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("CleanerTaskDetail", "Image upload failed", e)
                    imageAdapter.updateImage(position, item.copy(isUploading = false))
                    Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
                    btnMarkAsClean.isEnabled = true
                    btnMarkAsClean.text = "Mark as clean"
                }
        }
    }

    private fun markTaskAsClean(roomId: String, task: com.tdc.nhom6.roomio.fragments.CleanerTask?, imageUrls: List<String>) {
        // Update task status to CLEAN (completed)
        task?.let { currentTask ->
            val updated = currentTask.copy(status = TaskStatus.CLEAN)
            CleanerTaskRepository.updateTask(updated)
        }

        // Save image URLs and update booking status to completed if bookingId is available
        if (bookingId.isNotEmpty()) {
            val updates = mutableMapOf<String, Any>(
                "cleaningImages" to imageUrls
            )
            
            // Update booking status to completed
            firestore.collection("bookings").document(bookingId)
                .get()
                .addOnSuccessListener { doc ->
                    val currentStatus = doc.getString("status")?.lowercase() ?: ""
                    // Only update to completed if not already completed or canceled
                    if (currentStatus != "completed" && currentStatus != "canceled" && currentStatus != "cancelled") {
                        updates["status"] = "completed"
                    }
                    
                    firestore.collection("bookings").document(bookingId)
                        .update(updates)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Room marked as clean", Toast.LENGTH_SHORT).show()
                            finish() // Navigate back to task list
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("CleanerTaskDetail", "Failed to save cleaning images and status", e)
                            Toast.makeText(this, "Room marked as clean", Toast.LENGTH_SHORT).show()
                            finish() // Still navigate back even if update fails
                        }
                }
                .addOnFailureListener { e ->
                    // If we can't read the booking, just save images
                    firestore.collection("bookings").document(bookingId)
                        .update("cleaningImages", imageUrls)
                        .addOnCompleteListener {
                            Toast.makeText(this, "Room marked as clean", Toast.LENGTH_SHORT).show()
                            finish() // Navigate back to task list
                        }
                }
        } else {
            // No bookingId, just update task and navigate back
            Toast.makeText(this, "Room marked as clean", Toast.LENGTH_SHORT).show()
            finish() // Navigate back to task list
        }
    }
}

