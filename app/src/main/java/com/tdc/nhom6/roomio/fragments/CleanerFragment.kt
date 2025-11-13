package com.tdc.nhom6.roomio.fragments

import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.CleanerTaskAdapter
import com.tdc.nhom6.roomio.data.CleanerTaskRepository
import com.tdc.nhom6.roomio.activities.CleaningInspectionActivity
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class CleanerFragment : Fragment() {
    private lateinit var taskAdapter: CleanerTaskAdapter
    private val allTasks = mutableListOf<CleanerTask>()
    private var currentFilter: TaskStatus = TaskStatus.ALL
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var checkoutListener: ListenerRegistration? = null
    private val trackedBookingIds = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_cleaner, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup back button
        view.findViewById<android.widget.ImageView>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        
        // Initialize data (seed once for demo if empty)
        CleanerTaskRepository.seedIfEmpty(placeholderTasks())
        
        // Setup summary cards
        setupSummaryCards(view)
        
        // Setup filter tabs
        setupTabs(view)
        
        // Setup RecyclerView
        setupRecyclerView(view)
        
        // Observe repository tasks
        CleanerTaskRepository.tasks().observe(viewLifecycleOwner) { tasks ->
            allTasks.clear()
            allTasks.addAll(tasks)
            filterTasks()
        }

        startListeningForCheckoutBookings()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        checkoutListener?.remove()
        checkoutListener = null
    }

    private fun startListeningForCheckoutBookings() {
        checkoutListener?.remove()
        val statuses = listOf("pending_payment", "checked_out", "checked out")
        checkoutListener = firestore.collection("bookings")
            .whereIn("status", statuses)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !isAdded) {
                    return@addSnapshotListener
                }

                snapshot.documentChanges.forEach { change ->
                    val doc = change.document
                    val bookingId = doc.id
                    val status = doc.getString("status")?.lowercase()?.trim() ?: ""
                    val isCheckoutStatus = status == "pending_payment" || status == "checked_out" || status == "checked out"

                    if (!isCheckoutStatus || change.type == DocumentChange.Type.REMOVED) {
                        trackedBookingIds.remove(bookingId)
                        return@forEach
                    }

                    if (trackedBookingIds.contains(bookingId)) {
                        return@forEach
                    }

                    trackedBookingIds.add(bookingId)

                    val roomId = doc.getString("roomNumber")
                        ?: doc.getString("roomId")
                        ?: doc.getString("room")
                        ?: doc.getString("roomName")
                        ?: doc.getString("reservationId")
                        ?: bookingId

                    val roomTypeId = doc.getString("roomTypeId")
                        ?: (doc.get("roomTypeRef") as? DocumentReference)?.id

                    val hotelId = doc.getString("hotelId")
                        ?: (doc.get("hotelRef") as? DocumentReference)?.id

                    CleanerTaskRepository.addDirtyTask(
                        roomId = roomId,
                        bookingDocId = bookingId,
                        roomTypeId = roomTypeId,
                        hotelId = hotelId
                    )

                    val reservationLabel = doc.getString("reservationId") ?: roomId
                    if (isAdded) {
                        Toast.makeText(requireContext(), "New cleaning task: $reservationLabel", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun setupSummaryCards(view: View, currentTasks: List<CleanerTask> = allTasks) {
        val tvRoomsToClean = view.findViewById<android.widget.TextView>(R.id.tvRoomsToClean)
        val tvCompleted = view.findViewById<android.widget.TextView>(R.id.tvCompleted)
        val tvInProgressCard = view.findViewById<android.widget.TextView>(R.id.tvPending)

        val totalRooms = currentTasks.size
        val completed = currentTasks.count { it.status == TaskStatus.CLEAN }
        val inProgress = currentTasks.count { it.status == TaskStatus.IN_PROGRESS }
        val pending = currentTasks.count { it.status == TaskStatus.DIRTY }

        tvRoomsToClean.text = totalRooms.toString()
        tvCompleted.text = completed.toString()
        tvInProgressCard.text = inProgress.toString()

        // Optional: update content description for accessibility with detailed counts
        tvRoomsToClean.contentDescription = "Total rooms: $totalRooms"
        tvCompleted.contentDescription = "Completed rooms: $completed"
        tvInProgressCard.contentDescription = "In progress rooms: $inProgress"
    }

    private fun setupTabs(view: View) {
        val tabAll = view.findViewById<android.widget.TextView>(R.id.tabAll)
        val tabDirty = view.findViewById<android.widget.TextView>(R.id.tabDirty)
        val tabInProgress = view.findViewById<android.widget.TextView>(R.id.tabInProgress)
        val tabCompleted = view.findViewById<android.widget.TextView>(R.id.tabCompleted)
        
        val tabs = listOf(tabAll, tabDirty, tabInProgress, tabCompleted)
        
        fun updateTabSelection(selectedIndex: Int) {
            tabs.forEachIndexed { index, tab ->
                if (index == selectedIndex) {
                    // Selected tab - highlighted style
                    tab.setBackgroundResource(R.drawable.bg_tab_chip_selected)
                    tab.setTextColor(android.graphics.Color.WHITE)
                } else {
                    // Unselected tab - default style
                    tab.background = context?.getDrawable(R.drawable.bg_tab_chip)
                    tab.setTextColor(android.graphics.Color.parseColor("#2D6C8C"))
                }
            }
        }
        
        tabAll.setOnClickListener {
            currentFilter = TaskStatus.ALL
            updateTabSelection(0)
            filterTasks()
        }
        
        tabDirty.setOnClickListener {
            currentFilter = TaskStatus.DIRTY
            updateTabSelection(1)
            filterTasks()
        }
        
        tabInProgress.setOnClickListener {
            currentFilter = TaskStatus.IN_PROGRESS
            updateTabSelection(2)
            filterTasks()
        }
        
        tabCompleted.setOnClickListener {
            currentFilter = TaskStatus.CLEAN
            updateTabSelection(3)
            filterTasks()
        }
        
        // Set initial selection
        updateTabSelection(0)
    }

    private fun setupRecyclerView(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.rvTasks)
        
        val layoutManager = LinearLayoutManager(requireContext())
        rv.layoutManager = layoutManager
        rv.setItemViewCacheSize(20)
        rv.itemAnimator = null
        rv.setNestedScrollingEnabled(false)
        
        taskAdapter = CleanerTaskAdapter(allTasks.toMutableList()) { position, task ->
            handleTaskAction(position, task)
        }
        taskAdapter.setHasStableIds(true) // Enable stable IDs to prevent swap behavior issues
        rv.adapter = taskAdapter
    }

    private fun handleTaskAction(position: Int, task: CleanerTask) {
        when (task.status) {
            TaskStatus.DIRTY -> {
                // Change to In Progress
                val updated = task.copy(status = TaskStatus.IN_PROGRESS)
                CleanerTaskRepository.updateTask(updated)
                // Navigate to inspection screen
                try {
                    val intent = Intent(requireContext(), CleaningInspectionActivity::class.java)
                    intent.putExtra("ROOM_ID", task.roomId)
                    intent.putExtra("BOOKING_ID", task.bookingDocId ?: task.roomId)
                    intent.putExtra("ROOM_TYPE_ID", task.roomTypeId ?: "")
                    intent.putExtra("HOTEL_ID", task.hotelId ?: "")
                    startActivity(intent)
                } catch (_: Exception) { }
            }
            TaskStatus.IN_PROGRESS -> {
                // Change to Clean
                val updated = task.copy(status = TaskStatus.CLEAN)
                CleanerTaskRepository.updateTask(updated)
            }
            else -> { /* No action for completed tasks */ }
        }
    }

    private fun filterTasks() {
        val filtered = allTasks.filter { task ->
            when (currentFilter) {
                TaskStatus.ALL -> true
                else -> task.status == currentFilter
            }
        }
        
        taskAdapter.updateData(filtered.toMutableList())
        setupSummaryCards(requireView(), allTasks)
    }

    private fun updateSummaryCards() {
        view?.let { view ->
            setupSummaryCards(view)
        }
    }

    private fun placeholderTasks(): List<CleanerTask> = listOf(
        CleanerTask("1", "F03-07", TaskStatus.DIRTY, "9th Jun 10.00 am"),
        CleanerTask("2", "F03-08", TaskStatus.IN_PROGRESS, "9th Jun 11.00 am"),
        CleanerTask("3", "F03-09", TaskStatus.DIRTY, "9th Jun 12.00 pm"),
        CleanerTask("4", "F03-10", TaskStatus.CLEAN, "8th Jun 09.00 am"),
        CleanerTask("5", "F03-11", TaskStatus.DIRTY, "9th Jun 01.00 pm"),
        CleanerTask("6", "F03-12", TaskStatus.DIRTY, "9th Jun 02.00 pm"),
        CleanerTask("7", "F03-13", TaskStatus.IN_PROGRESS, "9th Jun 03.00 pm"),
        CleanerTask("8", "F03-14", TaskStatus.CLEAN, "8th Jun 10.00 am"),
        CleanerTask("9", "F03-15", TaskStatus.DIRTY, "9th Jun 04.00 pm"),
        CleanerTask("10", "F03-16", TaskStatus.DIRTY, "9th Jun 05.00 pm")
    )
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

enum class TaskStatus {
    ALL, DIRTY, IN_PROGRESS, CLEAN
}

