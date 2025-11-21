package com.tdc.nhom6.roomio.utils

import com.tdc.nhom6.roomio.fragments.TaskStatus
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

