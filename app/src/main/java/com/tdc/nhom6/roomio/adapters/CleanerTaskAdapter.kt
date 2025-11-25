package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.fragments.CleanerTask
import com.tdc.nhom6.roomio.fragments.TaskStatus
import com.tdc.nhom6.roomio.utils.CleanerStatusUtils

class CleanerTaskAdapter(
    private var tasks: MutableList<CleanerTask>,
    private val onActionClick: (Int, CleanerTask) -> Unit
) : RecyclerView.Adapter<CleanerTaskAdapter.TaskViewHolder>() {

    override fun getItemId(position: Int): Long {
        // Use task ID as stable ID to prevent swap behavior issues
        return tasks.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cleaner_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(tasks[position], onActionClick)
    }

    override fun getItemCount() = tasks.size

    fun updateData(newTasks: List<CleanerTask>) {
        // Simple update without DiffUtil to prevent swap behavior issues
        tasks.clear()
        tasks.addAll(newTasks)
        // Use notifyDataSetChanged() for stable updates
        notifyDataSetChanged()
    }

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRoomId: TextView = itemView.findViewById(R.id.tvRoomId)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val btnAction: MaterialButton = itemView.findViewById(R.id.btnAction)

        fun bind(task: CleanerTask, onActionClick: (Int, CleanerTask) -> Unit) {
            tvRoomId.text = task.roomId
            tvTimestamp.text = task.timestamp

            // Set status text and color
            tvStatus.text = CleanerStatusUtils.getStatusText(task.status)
            tvStatus.setTextColor(android.graphics.Color.parseColor(CleanerStatusUtils.getStatusColor(task.status)))

            // Set button text and state
            when (task.status) {
                TaskStatus.DIRTY -> {
                    btnAction.text = "Start"
                    btnAction.isEnabled = true
                    btnAction.alpha = 1.0f
                    btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#639DBA")))
                }
                TaskStatus.IN_PROGRESS -> {
                    btnAction.text = "Complete"
                    btnAction.isEnabled = true
                    btnAction.alpha = 1.0f
                    btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2970A8")))
                }
                TaskStatus.CLEAN -> {
                    btnAction.text = "Completed"
                    btnAction.isEnabled = false
                    btnAction.alpha = 0.6f
                    btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")))
                }
                else -> {
                    btnAction.text = "Start"
                    btnAction.isEnabled = true
                    btnAction.alpha = 1.0f
                    btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#639DBA")))
                }
            }

            btnAction.setOnClickListener {
                if (task.status != TaskStatus.CLEAN) {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        onActionClick(pos, task)
                    }
                }
            }
        }
    }
}

