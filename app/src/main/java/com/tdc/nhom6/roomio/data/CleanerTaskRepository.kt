package com.tdc.nhom6.roomio.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.tdc.nhom6.roomio.fragments.CleanerTask
import com.tdc.nhom6.roomio.fragments.TaskStatus
import com.tdc.nhom6.roomio.utils.CleanerStatusUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object CleanerTaskRepository {
    private val tasksInternal = mutableListOf<CleanerTask>()
    private val tasksLiveData = MutableLiveData<List<CleanerTask>>(emptyList())
    private val cleaningResultLiveData = MutableLiveData<Pair<String, Double>?>() // roomId to fee
    private val roomIdToCleaningFee = mutableMapOf<String, Double>()
    private val firestore by lazy { Firebase.firestore }

    fun tasks(): LiveData<List<CleanerTask>> = tasksLiveData

    fun latestCleaningResult(): LiveData<Pair<String, Double>?> = cleaningResultLiveData

    fun getCleaningFee(roomId: String): Double = roomIdToCleaningFee[roomId] ?: 0.0

    fun addDirtyTask(
        roomId: String,
        bookingDocId: String? = null,
        roomTypeId: String? = null,
        hotelId: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ) {
        if (bookingDocId != null && tasksInternal.any { it.bookingDocId == bookingDocId }) {
            return
        }
        val displayTime = SimpleDateFormat("d MMM hh.mm a", Locale.getDefault()).format(Date(createdAt))
        val task = CleanerTask(
            id = UUID.randomUUID().toString(),
            roomId = roomId,
            status = TaskStatus.DIRTY,
            timestamp = displayTime,
            bookingDocId = bookingDocId,
            roomTypeId = roomTypeId,
            hotelId = hotelId
        )
        tasksInternal.add(0, task)
        tasksLiveData.postValue(tasksInternal.toList())
        persistCleanerStatus(task)
    }

    fun updateTask(updated: CleanerTask) {
        val idx = tasksInternal.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            tasksInternal[idx] = updated
            tasksLiveData.postValue(tasksInternal.toList())
            persistCleanerStatus(updated)
        }
    }

    fun seedIfEmpty(seed: List<CleanerTask>) {
        if (tasksInternal.isEmpty()) {
            tasksInternal.addAll(seed)
            tasksLiveData.postValue(tasksInternal.toList())
        }
    }

    fun postCleaningResult(roomId: String, totalFee: Double) {
        roomIdToCleaningFee[roomId] = totalFee
        cleaningResultLiveData.postValue(roomId to totalFee)
    }

    /**
     * Returns and clears the latest cleaning result so observers don't receive it multiple times.
     */
    fun consumeLatestCleaningResult(): Pair<String, Double>? {
        val current = cleaningResultLiveData.value
        // Clear after read to prevent duplicate handling
        cleaningResultLiveData.postValue(null)
        return current
    }

    private fun persistCleanerStatus(task: CleanerTask) {
        val bookingId = task.bookingDocId ?: return
        val statusDocId = CleanerStatusUtils.toFirebaseStatus(task.status)
        val statusData = mapOf(
            "status" to statusDocId,
            "statusEnum" to task.status.name,
            "roomId" to task.roomId,
            "taskId" to task.id,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            // Save to cleaner subcollection instead of cleanerStatus
            firestore.collection("bookings")
                .document(bookingId)
                .collection("cleaner")
                .document() // Auto-generate document ID for each status update
                .set(statusData, SetOptions.merge())
        } catch (_: Exception) {
        }
    }
}


