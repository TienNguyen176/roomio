//package com.tdc.nhom6.roomio.repositories
//
//import androidx.lifecycle.LiveData
//import androidx.lifecycle.MutableLiveData
//import com.google.firebase.Firebase
//import com.google.firebase.firestore.FieldValue
//import com.google.firebase.firestore.SetOptions
//import com.google.firebase.firestore.firestore
//import com.tdc.nhom6.roomio.models.CleanerTask
//import com.tdc.nhom6.roomio.models.TaskStatus
//import com.tdc.nhom6.roomio.utils.CleanerStatusUtils
//import java.text.SimpleDateFormat
//import java.util.Date
//import java.util.Locale
//import java.util.UUID
//
//object CleanerTaskRepository {
//    private val tasksInternal = mutableListOf<CleanerTask>()
//    private val tasksLiveData = MutableLiveData<List<CleanerTask>>(emptyList())
//    private val cleaningResultLiveData = MutableLiveData<Pair<String, Double>?>() // roomId to fee
//    private val roomIdToCleaningFee = mutableMapOf<String, Double>()
//    private val firestore by lazy { Firebase.firestore }
//
//    fun tasks(): LiveData<List<CleanerTask>> = tasksLiveData
//
//    fun latestCleaningResult(): LiveData<Pair<String, Double>?> = cleaningResultLiveData
//
//    fun getCleaningFee(roomId: String): Double = roomIdToCleaningFee[roomId] ?: 0.0
//
//    fun addDirtyTask(
//        roomId: String,
//        bookingDocId: String? = null,
//        roomTypeId: String? = null,
//        hotelId: String? = null,
//        createdAt: Long = System.currentTimeMillis(),
//        initialStatus: TaskStatus = TaskStatus.DIRTY
//    ) {
//        if (bookingDocId != null) {
//            val idx = tasksInternal.indexOfFirst { it.bookingDocId == bookingDocId }
//            if (idx >= 0) {
//                val existing = tasksInternal[idx]
//                val updated = existing.copy(
//                    roomId = if (roomId.isNotBlank()) roomId else existing.roomId,
//                    status = initialStatus,
//                    roomTypeId = roomTypeId ?: existing.roomTypeId,
//                    hotelId = hotelId ?: existing.hotelId
//                )
//                if (updated != existing) {
//                    tasksInternal[idx] = updated
//                    tasksLiveData.postValue(tasksInternal.toList())
//                    if (existing.status != initialStatus) {
//                        persistCleanerStatus(updated)
//                    }
//                }
//                return
//            }
//        }
//        val displayTime = SimpleDateFormat("d MMM hh.mm a", Locale.getDefault()).format(Date(createdAt))
//        val task = CleanerTask(
//            id = UUID.randomUUID().toString(),
//            roomId = roomId,
//            status = initialStatus,
//            timestamp = displayTime,
//            bookingDocId = bookingDocId,
//            roomTypeId = roomTypeId,
//            hotelId = hotelId
//        )
//        tasksInternal.add(0, task)
//        tasksLiveData.postValue(tasksInternal.toList())
//        persistCleanerStatus(task)
//    }
//
//    fun updateTask(updated: CleanerTask) {
//        val idx = tasksInternal.indexOfFirst { it.id == updated.id }
//        if (idx >= 0) {
//            tasksInternal[idx] = updated
//            tasksLiveData.postValue(tasksInternal.toList())
//            persistCleanerStatus(updated)
//        }
//    }
//
//    fun seedIfEmpty(seed: List<CleanerTask>) {
//        if (tasksInternal.isEmpty()) {
//            tasksInternal.addAll(seed)
//            tasksLiveData.postValue(tasksInternal.toList())
//        }
//    }
//
//    fun postCleaningResult(roomId: String, totalFee: Double) {
//        roomIdToCleaningFee[roomId] = totalFee
//        cleaningResultLiveData.postValue(roomId to totalFee)
//    }
//
//    /**
//     * Returns and clears the latest cleaning result so observers don't receive it multiple times.
//     */
//    fun consumeLatestCleaningResult(): Pair<String, Double>? {
//        val current = cleaningResultLiveData.value
//        // Clear after read to prevent duplicate handling
//        cleaningResultLiveData.postValue(null)
//        return current
//    }
//
//    private fun persistCleanerStatus(task: CleanerTask) {
//        val bookingId = task.bookingDocId ?: return
//        val statusDocId = CleanerStatusUtils.toFirebaseStatus(task.status)
//        val statusData = mapOf(
//            "status" to statusDocId,
//            "statusEnum" to task.status.name,
//            "roomId" to task.roomId,
//            "taskId" to task.id,
//            "updatedAt" to FieldValue.serverTimestamp()
//        )
//        try {
//            // Try to find and update existing cleaner document, or create new one
//            firestore.collection("bookings")
//                .document(bookingId)
//                .collection("cleaner")
//                .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
//                .limit(1)
//                .get()
//                .addOnSuccessListener { snapshot ->
//                    val docRef = if (!snapshot.isEmpty) {
//                        // Update existing document
//                        snapshot.documents.first().reference
//                    } else {
//                        // Create new document only if none exists
//                        firestore.collection("bookings")
//                            .document(bookingId)
//                            .collection("cleaner")
//                            .document()
//                    }
//                    docRef.set(statusData, SetOptions.merge())
//                }
//                .addOnFailureListener {
//                    // Fallback: create new document if query fails
//                    firestore.collection("bookings")
//                        .document(bookingId)
//                        .collection("cleaner")
//                        .document()
//                        .set(statusData, SetOptions.merge())
//                }
//        } catch (_: Exception) {
//            android.util.Log.e("CleanerTaskRepository", "Error persisting cleaner status",)
//        }
//    }
//}
//
//
