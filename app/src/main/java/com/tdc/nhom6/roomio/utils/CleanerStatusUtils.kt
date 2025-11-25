package com.tdc.nhom6.roomio.utils

import com.tdc.nhom6.roomio.models.TaskStatus
import java.util.Locale

object CleanerStatusUtils {
    /**
     * Maps TaskStatus enum to Firebase status string
     */
    fun toFirebaseStatus(status: TaskStatus): String = when (status) {
        TaskStatus.DIRTY -> "dirty"
        TaskStatus.IN_PROGRESS -> "in_progress"
        TaskStatus.CLEAN -> "completed"
        else -> status.name.lowercase(Locale.getDefault())
    }

    /**
     * Maps Firebase status string back to TaskStatus, defaults to DIRTY if unknown.
     */
    fun fromFirebaseStatus(status: String?): TaskStatus {
        val normalized = status?.lowercase(Locale.getDefault()) ?: return TaskStatus.DIRTY
        return when (normalized) {
            "in_progress", "in-progress" -> TaskStatus.IN_PROGRESS
            "completed", "complete", "clean", "cleaned" -> TaskStatus.CLEAN
            else -> TaskStatus.DIRTY
        }
    }

    /**
     * Gets display text for TaskStatus
     */
    fun getStatusText(status: TaskStatus): String = when (status) {
        TaskStatus.DIRTY -> "Dirty"
        TaskStatus.IN_PROGRESS -> "In progress"
        TaskStatus.CLEAN -> "Cleaned"
        else -> "Dirty"
    }

    /**
     * Gets color hex code for TaskStatus
     */
    fun getStatusColor(status: TaskStatus): String = when (status) {
        TaskStatus.DIRTY -> "#FF9800"
        TaskStatus.IN_PROGRESS -> "#2196F3"
        TaskStatus.CLEAN -> "#4CAF50"
        else -> "#757575"
    }
}


