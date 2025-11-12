package com.tdc.nhom6.roomio.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.data.CleanerTaskRepository
import android.content.Intent
import com.tdc.nhom6.roomio.activities.ServiceExtraFeeActivity
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ReceptionFragment : Fragment() {
    private lateinit var reservationAdapter: ReservationAdapter
    private val allReservations = mutableListOf<ReservationUi>()
    private var currentFilter: ReservationStatus = ReservationStatus.ALL
    private var searchQuery: String = ""
    private var bookingsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_reception, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvReservations)
        
        // Configure RecyclerView to prevent swap behavior issues
        val layoutManager = LinearLayoutManager(requireContext())
        rv.layoutManager = layoutManager
        rv.setItemViewCacheSize(20) // Cache more views to prevent recycling issues
        rv.itemAnimator = null // Disable animations to prevent swap behavior errors
        rv.setNestedScrollingEnabled(false) // Disable nested scrolling
        rv.isNestedScrollingEnabled = false
        
        // Observe cleaner results and navigate to Service extra fee when a new fee is posted
        CleanerTaskRepository.latestCleaningResult().observe(viewLifecycleOwner) {
            // Consume to avoid re-triggering on configuration changes
            val consumed = CleanerTaskRepository.consumeLatestCleaningResult() ?: return@observe
            try {
                val roomId = consumed.first
                val ctx = requireContext()
                val intent = Intent(ctx, ServiceExtraFeeActivity::class.java)
                intent.putExtra("ROOM_ID", roomId)
                // Fallback: map reservation id to room id if needed
                intent.putExtra("RESERVATION_ID", roomId)
                // Forward some placeholder booking data if available later
                intent.putExtra("RESERVATION_AMOUNT", 450000.0)
                startActivity(intent)
            } catch (_: Exception) { }
        }
        
        // Initialize data
        allReservations.clear()
        allReservations.addAll(placeholderItems())
        
        reservationAdapter = ReservationAdapter(allReservations.toMutableList())
        rv.adapter = reservationAdapter
        
        // Setup tabs
        setupTabs(view)
        
        // Setup search
        setupSearch(view)
        
        // Start real-time updates from Firestore
        startListeningBookings()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopListeningBookings()
    }

    private fun startListeningBookings() {
        stopListeningBookings()
        val db = Firebase.firestore
        bookingsListener = db.collection("bookings")
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) {
                    return@addSnapshotListener
                }
                if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.INITIALIZED)) {
                    return@addSnapshotListener
                }
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.INITIALIZED)) {
                        return@launch
                    }
                    val updated = mutableListOf<ReservationUi>()
                    for (doc in snapshots.documents) {
                        try {
                            val documentId = doc.id
                            val reservationId = doc.getString("reservationId") ?: documentId
                            // Support multiple potential customer id fields
                            val customerIdRaw = doc.get("customerid")
                                ?: doc.get("customerId")
                                ?: doc.get("userId")
                                ?: doc.get("userRef")
                                ?: (doc.get("customer") as? Map<*, *>)?.get("id")
                                ?: doc.get("customer")
                            // Try to read room type information directly if available
                            val roomTypeInlineName =
                                doc.getString("roomTypeName")
                                    ?: doc.getString("room_type_name")
                                    ?: (doc.get("roomType") as? String)
                                    ?: ((doc.get("roomType") as? Map<*, *>)?.get("name") as? String)
                                    ?: ((doc.get("room") as? Map<*, *>)?.get("typeName") as? String)
                            
                            val checkInValue = doc.get("checkIn") ?: doc.get("checkInDate")
                            val checkOutValue = doc.get("checkOut") ?: doc.get("checkOutDate")
                            val checkIn = valueToDateString(checkInValue)
                            val checkOut = valueToDateString(checkOutValue)
                            
                            val amountPaidNumber =
                                (doc.get("amountPaid") as? Number)?.toDouble()
                                    ?: (doc.get("paid") as? Number)?.toDouble()
                                    ?: (doc.get("depositAmount") as? Number)?.toDouble()
                                    ?: 0.0
                            val totalAmountNumber =
                                (doc.get("totalAmount") as? Number)?.toDouble()
                                    ?: (doc.get("grandTotal") as? Number)?.toDouble()
                                    ?: (doc.get("total") as? Number)?.toDouble()
                                    ?: 0.0
                            val isCanceled = (doc.getBoolean("canceled") == true) ||
                                (doc.getString("status")?.equals("canceled", ignoreCase = true) == true)
                            
                            val statusStr = (doc.getString("status") ?: "").lowercase()
                            
                            val isFullPaid = amountPaidNumber >= totalAmountNumber && totalAmountNumber > 0.0
                            val isPartial = amountPaidNumber > 0.0 && amountPaidNumber < totalAmountNumber
                            
                            val headerColor = when {
                                isFullPaid -> HeaderColor.GREEN
                                isPartial -> HeaderColor.BLUE
                                else -> HeaderColor.BLUE
                            }
                            val badge = when {
                                isFullPaid -> "Paid"
                                isPartial -> "Deposit paid"
                                else -> ""
                            }
                            
                            val action = if (isCanceled) {
                                "Cancelled"
                            } else if (statusStr == "checked_in") {
                                "Check-out"
                            } else {
                                "Check-in"
                            }
                            val status = if (isCanceled) ReservationStatus.CANCELED else ReservationStatus.UNCOMPLETED
                            
                            val line1 = if (checkIn.isNotEmpty() || checkOut.isNotEmpty())
                                "Check-in: $checkIn - check-out: $checkOut"
                            else
                                ""
                            // We will render exactly:
                            // line1: Check-in/out
                            // line2: Room type
                            // line3: Guest name
                            var line2 = "Room type: ${roomTypeInlineName ?: "Loading..."}"
                            var line3 = "Guest name: Loading..."
                            
                            val ui = ReservationUi(
                                documentId = documentId,
                                reservationId = reservationId,
                                badge = badge,
                                line1 = line1,
                                line2 = line2,
                                line3 = line3,
                                action = action,
                                headerColor = headerColor,
                                status = status
                            )
                            updated.add(ui)
                            val uiIndex = updated.lastIndex
                            
                            if (customerIdRaw != null) {
                                // Resolve guest name concurrently; bind by index to avoid wrong item updates
                                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                                    try {
                                        if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.INITIALIZED)) {
                                            return@launch
                                        }
                                        val userDoc = when (customerIdRaw) {
                                            is com.google.firebase.firestore.DocumentReference -> customerIdRaw.get().await()
                                            is String -> Firebase.firestore.collection("users").document(customerIdRaw).get().await()
                                            is Number -> Firebase.firestore.collection("users").document(customerIdRaw.toString()).get().await()
                                            is Map<*, *> -> {
                                                val id = (customerIdRaw["id"] as? String) ?: (customerIdRaw["id"] as? Number)?.toString()
                                                if (id != null) Firebase.firestore.collection("users").document(id).get().await() else null
                                            }
                                            else -> null
                                        }
                                        val guestName = if (userDoc != null && userDoc.exists()) {
                                            val first = userDoc.getString("firstName") ?: userDoc.getString("first_name")
                                            val last = userDoc.getString("lastName") ?: userDoc.getString("last_name")
                                            val combined = listOfNotNull(first, last).joinToString(" ").trim()
                                            when {
                                                combined.isNotEmpty() -> combined
                                                !userDoc.getString("fullName").isNullOrBlank() -> userDoc.getString("fullName")!!
                                                !userDoc.getString("full_name").isNullOrBlank() -> userDoc.getString("full_name")!!
                                                !userDoc.getString("displayName").isNullOrBlank() -> userDoc.getString("displayName")!!
                                                !userDoc.getString("username").isNullOrBlank() -> userDoc.getString("username")!!
                                                !userDoc.getString("name").isNullOrBlank() -> userDoc.getString("name")!!
                                                else -> "Guest"
                                            }
                                        } else {
                                            when (customerIdRaw) {
                                                is String -> customerIdRaw
                                                is Number -> customerIdRaw.toString()
                                                is com.google.firebase.firestore.DocumentReference -> customerIdRaw.id
                                                is Map<*, *> -> (customerIdRaw["id"] as? String) ?: "Guest"
                                                else -> "Guest"
                                            }
                                        }
                                        // Update UI on main for this specific index
                                        launch(Dispatchers.Main) {
                                            if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.INITIALIZED) &&
                                                uiIndex >= 0 && uiIndex < updated.size) {
                                                val existing = updated[uiIndex]
                                                updated[uiIndex] = existing.copy(line3 = "Guest name: $guestName")
                                                allReservations.clear()
                                                allReservations.addAll(updated)
                                                filterReservations()
                                            }
                                        }
                                    } catch (_: Exception) {
                                        // ignore
                                    }
                                }
                            } else {
                                // Fallback: keep customerId or unknown
                                val existing = updated[uiIndex]
                                val fallbackGuest = doc.getString("guestName") ?: "Guest"
                                updated[uiIndex] = existing.copy(line3 = "Guest name: $fallbackGuest")
                            }

                            // Resolve room type if not already present; support multiple formats
                            val roomTypeRaw =
                                doc.get("roomTypeRef")
                                    ?: doc.get("roomTypeId")
                                    ?: doc.get("room_type_id")
                                    ?: doc.get("roomType")
                            if (roomTypeInlineName.isNullOrBlank()) {
                                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                                    try {
                                        if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.INITIALIZED)) {
                                            return@launch
                                        }
                                        val typeDoc = when (roomTypeRaw) {
                                            is com.google.firebase.firestore.DocumentReference -> roomTypeRaw.get().await()
                                            is String -> {
                                                // Try common collection names
                                                val t1 = Firebase.firestore.collection("roomTypes").document(roomTypeRaw).get().await()
                                                if (t1.exists()) t1 else Firebase.firestore.collection("room_types").document(roomTypeRaw).get().await()
                                            }
                                            is Map<*, *> -> {
                                                val id = (roomTypeRaw["id"] as? String)
                                                    ?: (roomTypeRaw["id"] as? Number)?.toString()
                                                if (id != null) Firebase.firestore.collection("roomTypes").document(id).get().await() else null
                                            }
                                            else -> null
                                        }
                                        val typeName = if (typeDoc != null && typeDoc.exists()) {
                                            typeDoc.getString("name")
                                                ?: typeDoc.getString("typeName")
                                                ?: typeDoc.getString("title")
                                                ?: "Room"
                                        } else {
                                            // fallback to any inline strings if present
                                            (roomTypeRaw as? String)
                                                ?: ((roomTypeRaw as? Map<*, *>)?.get("name") as? String)
                                                ?: "Room"
                                        }
                                        launch(Dispatchers.Main) {
                                            if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.INITIALIZED) &&
                                                uiIndex >= 0 && uiIndex < updated.size) {
                                                val existing = updated[uiIndex]
                                                updated[uiIndex] = existing.copy(line2 = "Room type: $typeName")
                                                allReservations.clear()
                                                allReservations.addAll(updated)
                                                filterReservations()
                                            }
                                        }
                                    } catch (_: Exception) {
                                        // ignore
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            // Skip malformed document
                        }
                    }
                    // Replace list immediately so UI shows quickly
                    if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.INITIALIZED)) {
                        allReservations.clear()
                        allReservations.addAll(updated)
                        filterReservations()
                    }
                }
            }
    }

    private fun stopListeningBookings() {
        try {
            bookingsListener?.remove()
        } catch (_: Exception) { }
        bookingsListener = null
    }

    private fun valueToDateString(value: Any?): String {
        return when (value) {
            null -> ""
            is String -> value
            is Timestamp -> {
                val date = value.toDate()
                try {
                    java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(date)
                } catch (_: Exception) {
                    date.toString()
                }
            }
            is java.util.Date -> {
                try {
                    java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(value)
                } catch (_: Exception) {
                    value.toString()
                }
            }
            else -> value.toString()
        }
    }

    private fun setupTabs(view: View) {
        val tabAll = view.findViewById<android.widget.TextView>(R.id.tabAll)
        val tabUncompleted = view.findViewById<android.widget.TextView>(R.id.tabUncompleted)
        val tabPending = view.findViewById<android.widget.TextView>(R.id.tabPending)
        val tabCompleted = view.findViewById<android.widget.TextView>(R.id.tabCompleted)
        val tabCanceled = view.findViewById<android.widget.TextView>(R.id.tabCanceled)
        
        val tabs = listOf(tabAll, tabUncompleted, tabPending, tabCompleted, tabCanceled)
        val statuses = listOf(ReservationStatus.ALL, ReservationStatus.UNCOMPLETED, ReservationStatus.PENDING, ReservationStatus.COMPLETED, ReservationStatus.CANCELED)
        
        fun updateTabSelection(selectedIndex: Int) {
            tabs.forEachIndexed { index, tab ->
                if (index == selectedIndex) {
                    // Selected tab - highlighted style
                    tab.setBackgroundColor(android.graphics.Color.parseColor("#2D6C8C"))
                    tab.setTextColor(android.graphics.Color.WHITE)
                } else {
                    // Unselected tab - default style
                    tab.background = context?.getDrawable(R.drawable.bg_tab_chip)
                    tab.setTextColor(android.graphics.Color.parseColor("#2D6C8C"))
                }
            }
        }
        
        tabAll.setOnClickListener {
            currentFilter = ReservationStatus.ALL
            updateTabSelection(0)
            filterReservations()
        }
        
        tabUncompleted.setOnClickListener {
            currentFilter = ReservationStatus.UNCOMPLETED
            updateTabSelection(1)
            filterReservations()
        }
        
        tabPending.setOnClickListener {
            currentFilter = ReservationStatus.PENDING
            updateTabSelection(2)
            filterReservations()
        }
        
        tabCompleted.setOnClickListener {
            currentFilter = ReservationStatus.COMPLETED
            updateTabSelection(3)
            filterReservations()
        }
        
        tabCanceled.setOnClickListener {
            currentFilter = ReservationStatus.CANCELED
            updateTabSelection(4)
            filterReservations()
        }
        
        // Set initial selection
        updateTabSelection(0)
    }

    private fun setupSearch(view: View) {
        val etSearch = view.findViewById<android.widget.EditText>(R.id.etSearch)
        
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                searchQuery = s?.toString()?.lowercase() ?: ""
                filterReservations()
            }
        })
    }

    private fun filterReservations() {
        val filtered = allReservations.filter { reservation ->
            // Filter by status based on action
            val statusMatch = when (currentFilter) {
                ReservationStatus.ALL -> true
                ReservationStatus.PENDING -> reservation.action.equals("Payment", ignoreCase = true)
                ReservationStatus.UNCOMPLETED -> reservation.action.equals("Check-in", ignoreCase = true) || 
                                                 reservation.action.equals("Check-out", ignoreCase = true)
                ReservationStatus.COMPLETED -> reservation.status == ReservationStatus.COMPLETED
                ReservationStatus.CANCELED -> reservation.status == ReservationStatus.CANCELED
            }
            
            // Filter by search query
            val searchMatch = searchQuery.isEmpty() || 
                reservation.reservationId.lowercase().contains(searchQuery) ||
                reservation.line1.lowercase().contains(searchQuery) ||
                reservation.line2.lowercase().contains(searchQuery) ||
                reservation.line3.lowercase().contains(searchQuery)
            
            statusMatch && searchMatch
        }
        
        reservationAdapter.updateData(filtered.toMutableList())
    }

    private fun placeholderItems(): List<ReservationUi> = listOf(
        ReservationUi("doc-R8ZZPQR7", "R8ZZPQR7", "Deposit paid", "Check-in: 20/09/1025 - Check-out: 22/09/2025", "Room type: Deluxe", "Guest name: Harper", "Check-in", HeaderColor.BLUE, ReservationStatus.UNCOMPLETED),
        ReservationUi("doc-R8ZZPQR8", "R8ZZPQR8", "Paid", "Check-in: 20/09/1025 - check-out: 22/09/2025", "Room type: Suite", "Guest name: Lily", "Check-out", HeaderColor.GREEN, ReservationStatus.UNCOMPLETED),
        ReservationUi("doc-R8ZZPQR9", "R8ZZPQR9", "", "Check-in: 20/09/1025 - check-out: 22/09/2025", "Room type: Standard", "Guest name: Cap", "Payment", HeaderColor.YELLOW, ReservationStatus.PENDING),
        ReservationUi("doc-R8ZZPQR0", "R8ZZPQR0", "Paid", "Check-in: 20/09/1025 - check-out: 22/09/2025", "Room type: Deluxe", "Guest name: Ahri", "Check-in", HeaderColor.GREEN, ReservationStatus.COMPLETED),
        ReservationUi("doc-R9ABC123", "R9ABC123", "Cancelled", "Check-in: 15/09/1025 - check-out: 17/09/2025", "Room type: Standard", "Guest name: Bob", "Payment", HeaderColor.RED, ReservationStatus.CANCELED)
    )
}

data class ReservationUi(
    val documentId: String,
    val reservationId: String,
    val badge: String,
    val line1: String,
    val line2: String,
    val line3: String,
    val action: String,
    val headerColor: HeaderColor,
    val status: ReservationStatus = ReservationStatus.UNCOMPLETED
)

enum class ReservationStatus {
    ALL, UNCOMPLETED, PENDING, COMPLETED, CANCELED
}

enum class HeaderColor { BLUE, GREEN, YELLOW, RED }

class ReservationAdapter(private val items: MutableList<ReservationUi>) : RecyclerView.Adapter<ReservationViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReservationViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reservation_card, parent, false)
        return ReservationViewHolder(v) { position ->
            val current = items[position]
            // Don't allow any actions on canceled reservations
            if (current.status == ReservationStatus.CANCELED) {
                return@ReservationViewHolder
            }
            when (current.action.lowercase()) {
                "check-in" -> showCheckInDialog(v, position)
                "check-out" -> showCheckOutDialog(v, position)
                "payment" -> showPaymentDialog(v, position)
                else -> advanceState(position)
            }
        }
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: ReservationViewHolder, position: Int) = holder.bind(items[position])
    
    fun updateData(newItems: List<ReservationUi>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    private fun advanceState(position: Int) {
        val current = items[position]
        val next = when (current.action.lowercase()) {
            "check-in" -> current.copy(
                badge = "Paid",
                action = "Check-out",
                headerColor = HeaderColor.GREEN
            )
            "check-out" -> current.copy(
                badge = "",
                action = "Payment",
                headerColor = HeaderColor.YELLOW
            )
            else -> current
        }
        if (next !== current) {
            items[position] = next
            notifyItemChanged(position)
        }
    }

    private fun showCheckInDialog(anchorView: View, position: Int) {
        val ctx = anchorView.context
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_check_in, null, false)
        val etDateTime = dialogView.findViewById<android.widget.EditText>(R.id.etDateTime)
        val etGuests = dialogView.findViewById<android.widget.EditText>(R.id.etGuests)
        val btnMinus = dialogView.findViewById<android.widget.Button>(R.id.btnMinus)
        val btnPlus = dialogView.findViewById<android.widget.Button>(R.id.btnPlus)
        val btnOk = dialogView.findViewById<android.widget.Button>(R.id.btnOk)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)

        val formatter = java.text.SimpleDateFormat("HH:mm:ss/dd/MM/yyyy", java.util.Locale.getDefault())
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                etDateTime.setText(formatter.format(java.util.Date()))
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(tick)
        etGuests.setText("2")

        btnMinus.setOnClickListener {
            val n = (etGuests.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1)
            etGuests.setText((n - 1).coerceAtLeast(1).toString())
        }
        btnPlus.setOnClickListener {
            val n = etGuests.text.toString().toIntOrNull() ?: 1
            etGuests.setText((n + 1).toString())
        }

        val alert = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .create()

        alert.setOnDismissListener {
            try { handler.removeCallbacksAndMessages(null) } catch (_: Exception) {}
        }

        btnOk.setOnClickListener {
            // Advance state after confirmation
            advanceState(position)
            // Persist to Firestore: status = checked_in
            try {
                val docId = items[position].documentId
                Firebase.firestore.collection("bookings").document(docId)
                    .update("status", "checked_in")
            } catch (_: Exception) { }
            alert.dismiss()
        }
        btnCancel.setOnClickListener {
            alert.dismiss() 
        }

        alert.show()
    }

    private fun showCheckOutDialog(anchorView: View, position: Int) {
        val ctx = anchorView.context
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_check_out, null, false)
        val btnConfirm = dialogView.findViewById<android.widget.Button>(R.id.btnConfirm)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)

        val alert = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .create()

        btnConfirm.setOnClickListener {
            // Advance state after confirmation
            advanceState(position)
            try {
                val current = items[position]
                // Use reservationId as a stand-in for room number if real room id not available
                val roomId = current.reservationId
                CleanerTaskRepository.addDirtyTask(roomId)
                // Persist to Firestore: status = checked_out
                val docId = current.documentId
                Firebase.firestore.collection("bookings").document(docId)
                    .update("status", "checked_out")
            } catch (_: Exception) { }
            alert.dismiss()
        }
        btnCancel.setOnClickListener {
            alert.dismiss()
        }

        alert.show()
    }

    private fun showPaymentDialog(anchorView: View, position: Int) {
        val ctx = anchorView.context
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_payment, null, false)
        val btnConfirm = dialogView.findViewById<android.widget.Button>(R.id.btnConfirm)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)

        val alert = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .create()

        btnConfirm.setOnClickListener {
            // Advance state after confirmation
            advanceState(position)
            // Navigate to service extra fee screen with guest info
            try {
                val current = items[position]
                val roomId = current.reservationId
                val intent = Intent(ctx, ServiceExtraFeeActivity::class.java)
                intent.putExtra("ROOM_ID", roomId)
                intent.putExtra("RESERVATION_ID", current.reservationId)
                // Parse from lines when possible
                // line3 format: "Guest name: XYZ" (fallback to raw line)
                val guestName = current.line3.substringAfter(":", current.line3).trim()
                intent.putExtra("GUEST_NAME", guestName)
                // line1 format: "Check-in: dd/MM/yyyy - check-out: dd/MM/yyyy"
                val lower = current.line1.lowercase()
                val inText = lower.substringAfter("check-in:", "").substringBefore("-").trim()
                val outText = lower.substringAfter("check-out:", "").trim()
                intent.putExtra("CHECK_IN", inText)
                intent.putExtra("CHECK_OUT", outText)
                intent.putExtra("RESERVATION_AMOUNT", 450000.0)
                ctx.startActivity(intent)
                val docId = items[position].documentId
                Firebase.firestore.collection("bookings").document(docId)
                    .update("status", " pending_payment")
            } catch (_: Exception) { }
            alert.dismiss()
        }
        btnCancel.setOnClickListener {
            alert.dismiss()
        }

        alert.show()
    }
}

class ReservationViewHolder(view: View, private val onActionClick: (Int) -> Unit) : RecyclerView.ViewHolder(view) {
    private val header: View = view.findViewById(R.id.header)
    private val tvReservationId: android.widget.TextView = view.findViewById(R.id.tvReservationId)
    private val tvStatusBadge: android.widget.TextView = view.findViewById(R.id.tvStatusBadge)
    private val tvLine1: android.widget.TextView = view.findViewById(R.id.tvLine1)
    private val tvLine2: android.widget.TextView = view.findViewById(R.id.tvLine2)
    private val tvLine3: android.widget.TextView = view.findViewById(R.id.tvLine3)
    private val btnAction: android.widget.TextView = view.findViewById(R.id.btnAction)

    fun bind(item: ReservationUi) {
        tvReservationId.text = "Reservation ID: ${item.reservationId}"
        tvStatusBadge.text = item.badge
        tvLine1.text = item.line1
        tvLine2.text = item.line2
        tvLine3.text = item.line3
        
        // Check if reservation is canceled
        val isCanceled = item.status == ReservationStatus.CANCELED
        
        // Set button text: "Cancelled" for canceled reservations, otherwise use action
        btnAction.text = if (isCanceled) "Cancelled" else item.action
        
        // Disable button and make it look frozen for canceled reservations
        if (isCanceled) {
            btnAction.isEnabled = false
            btnAction.isClickable = false
            btnAction.alpha = 0.5f // Make it look disabled/frozen
            btnAction.setOnClickListener(null) // Remove click listener
        } else {
            btnAction.isEnabled = true
            btnAction.isClickable = true
            btnAction.alpha = 1.0f
            btnAction.setOnClickListener {
                onActionClick(bindingAdapterPosition)
            }
        }
        
        header.setBackgroundColor(
            when (item.headerColor) {
                HeaderColor.BLUE -> android.graphics.Color.parseColor("#D3E7F6")
                HeaderColor.GREEN -> android.graphics.Color.parseColor("#CDEFD7")
                HeaderColor.YELLOW -> android.graphics.Color.parseColor("#F7E7A8")
                HeaderColor.RED -> android.graphics.Color.parseColor("#841919") //
            }
        )
    }
}




