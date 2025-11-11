package com.tdc.nhom6.roomio.activities

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.data.CleanerTaskRepository
import com.tdc.nhom6.roomio.fragments.TaskStatus

class CleanerTaskDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cleaner_task_detail)

        val roomId = intent.getStringExtra("ROOM_ID") ?: ""
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
                tvStatus.text = "Clean"
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

        // Upload images button (placeholder for now)
        findViewById<MaterialButton>(R.id.btnUploadImages).setOnClickListener {
            // TODO: Implement image upload functionality
        }

        // Start Cleaning button
        findViewById<MaterialButton>(R.id.btnStartCleaning).setOnClickListener {
            // Update task status to IN_PROGRESS if it's DIRTY
            task?.let { currentTask ->
                if (currentTask.status == TaskStatus.DIRTY) {
                    val updated = currentTask.copy(status = TaskStatus.IN_PROGRESS)
                    CleanerTaskRepository.updateTask(updated)
                }
            }
            // Navigate back or to next screen
            finish()
        }
    }
}

