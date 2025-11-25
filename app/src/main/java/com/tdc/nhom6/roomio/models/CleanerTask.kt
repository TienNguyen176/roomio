package com.tdc.nhom6.roomio.models

enum class TaskStatus {
    ALL, DIRTY, IN_PROGRESS, CLEAN
}

data class CleanerTask(
    val id: String,
    val roomId: String,
    val status: TaskStatus,
    val timestamp: String,
    val bookingDocId: String? = null,
    val roomTypeId: String? = null,
    val hotelId: String? = null
)
