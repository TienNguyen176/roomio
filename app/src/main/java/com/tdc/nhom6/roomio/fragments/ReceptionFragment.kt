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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class ReceptionFragment : Fragment() {
    private lateinit var reservationAdapter: ReservationAdapter
    private val allReservations = mutableListOf<ReservationUi>()
    private var currentFilter: ReservationStatus = ReservationStatus.ALL
    private var searchQuery: String = ""
    private var bookingsListener: ListenerRegistration? = null
    private val activeJobs = mutableListOf<kotlinx.coroutines.Job>()
    private var invoicesListener: ListenerRegistration? = null
    private val reservationMeta = mutableMapOf<String, ReservationMeta>()
    private val invoiceDocuments = mutableMapOf<String, InvoiceDocInfo>()

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
        
        // Listen for invoice updates in real-time
        startInvoiceListener()
        
        // Start real-time updates from Firestore
        startListeningBookings()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopListeningBookings()
        stopInvoiceListener()
        // Cancel all active coroutines to prevent resource leaks
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        reservationMeta.clear()
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
                            val roomTypeSource =
                                doc.get("roomTypeRef")
                                    ?: doc.get("roomTypeId")
                                    ?: doc.get("room_type_id")
                                    ?: doc.get("roomType")
                            val roomTypeId = extractRoomTypeId(roomTypeSource)
                            
                            val checkInValue = doc.get("checkIn") ?: doc.get("checkInDate")
                            val checkOutValue = doc.get("checkOut") ?: doc.get("checkOutDate")
                            
                            // Check actual check-in/out dates to determine state
                            val checkInDateActual = doc.get("checkInDateActual")
                            val checkOutDateActual = doc.get("checkOutDateActual")
                            val hasCheckedIn = checkInDateActual != null
                            val hasCheckedOut = checkOutDateActual != null
                            
                            // Read totalOrigin and totalFinal from booking
                            val totalOrigin = (doc.get("totalOrigin") as? Number)?.toDouble() ?: 0.0
                            val totalFinal = (doc.get("totalFinal") as? Number)?.toDouble() ?: totalOrigin
                            
                            // Read number of guests from booking
                            val numberGuest = (doc.get("numberGuest") as? Number)?.toInt()
                                ?: (doc.get("number_guest") as? Number)?.toInt()
                                ?: (doc.get("guests") as? Number)?.toInt()
                                ?: 1
                            
                            // Store documentId for invoice lookup
                            val bookingDocId = documentId
                            val bookingReservationId = reservationId // Store reservationId for invoice lookup
                            
                            // Try to get numeric booking ID from booking document (invoices use Int bookingId)
                            val bookingNumericId = (doc.get("id") as? Number)?.toInt()
                                ?: (doc.get("bookingId") as? Number)?.toInt()
                                ?: bookingDocId.toIntOrNull()
                                ?: bookingReservationId.toIntOrNull()
                            
                            val isCanceled = (doc.getBoolean("canceled") == true) ||
                                (doc.getString("status")?.equals("canceled", ignoreCase = true) == true)
                            
                            val statusStr = (doc.getString("status") ?: "").lowercase().trim()
                            
                            // Determine status based on booking data before payment info
                            val status = when {
                                isCanceled -> ReservationStatus.CANCELED
                                statusStr == "checked_out" || statusStr == "checked out" || statusStr == "pending_payment" || statusStr == "pending payment" -> ReservationStatus.PENDING
                                statusStr == "completed" -> ReservationStatus.COMPLETED
                                statusStr == "checked_in" || statusStr == "checked in" -> ReservationStatus.UNCOMPLETED
                                statusStr.isEmpty() || statusStr.isBlank() -> {
                                    when {
                                        checkOutValue != null -> ReservationStatus.PENDING
                                        checkInValue != null -> ReservationStatus.UNCOMPLETED
                                        else -> ReservationStatus.PENDING
                                    }
                                }
                                else -> ReservationStatus.UNCOMPLETED
                            }
                            
                            // Store check-in/out state for use in coroutines
                            val bookingHasCheckedIn = hasCheckedIn
                            val bookingHasCheckedOut = hasCheckedOut

                            val invoiceKeys = mutableSetOf<String>()
                            invoiceKeys += collectInvoiceKeyVariants(bookingDocId)
                            invoiceKeys += collectInvoiceKeyVariants(bookingReservationId)
                            bookingNumericId?.let { invoiceKeys += collectInvoiceKeyVariants(it) }
                            bookingReservationId.toIntOrNull()?.let { invoiceKeys += collectInvoiceKeyVariants(it) }
                            doc.get("id")?.let { invoiceKeys += collectInvoiceKeyVariants(it) }
                            doc.get("ID")?.let { invoiceKeys += collectInvoiceKeyVariants(it) }
                            doc.get("bookingCode")?.let { invoiceKeys += collectInvoiceKeyVariants(it) }
                            doc.get("booking_code")?.let { invoiceKeys += collectInvoiceKeyVariants(it) }
                            doc.get("bookingNumber")?.let { invoiceKeys += collectInvoiceKeyVariants(it) }
                            doc.get("booking_number")?.let { invoiceKeys += collectInvoiceKeyVariants(it) }
                            doc.get("reservationCode")?.let { invoiceKeys += collectInvoiceKeyVariants(it) }
                            doc.get("reservation_code")?.let { invoiceKeys += collectInvoiceKeyVariants(it) }
                            doc.get("bookingRef")?.let { invoiceKeys += collectInvoiceKeyVariants(it) }
                            doc.get("booking_ref")?.let { invoiceKeys += collectInvoiceKeyVariants(it) }

                            val checkInText = valueToDateTimeString(checkInValue)
                            val checkOutText = valueToDateTimeString(checkOutValue)
                            val checkInMillis = valueToMillis(checkInValue)
                            val checkOutMillis = valueToMillis(checkOutValue)
                            val discountText = doc.getString("discountText")
                                ?: doc.getString("discountDescription")
                                ?: doc.getString("promotion")
                                ?: "-"
                            
                            // Read cleaningCompletedAt timestamp
                            val cleaningCompletedAtMillis = valueToMillis(doc.get("cleaningCompletedAt"))
                            
                            // Check if cleaning was just completed (was null, now has value)
                            val previousReservation = allReservations.find { it.documentId == documentId }
                            val wasJustCleaned = previousReservation?.cleaningCompletedAtMillis == null && cleaningCompletedAtMillis != null
 
                            val headerColorInitial = when {
                                isCanceled -> HeaderColor.RED
                                status == ReservationStatus.COMPLETED -> HeaderColor.GREEN
                                status == ReservationStatus.PENDING -> HeaderColor.YELLOW
                                bookingHasCheckedIn -> HeaderColor.GREEN
                                else -> HeaderColor.YELLOW
                            }
                            val badgeInitial = when {
                                isCanceled -> "Cancelled"
                                status == ReservationStatus.COMPLETED -> "Completed"
                                status == ReservationStatus.PENDING -> "Pending payment"
                                else -> ""
                            }
                            val actionInitial = when {
                                isCanceled -> "Cancelled"
                                status == ReservationStatus.COMPLETED -> "Completed"
                                status == ReservationStatus.PENDING -> "Payment"
                                bookingHasCheckedIn -> "Check-out"
                                else -> "Check-in"
                            }
 
                            val line1 = if (checkInText.isNotEmpty() || checkOutText.isNotEmpty())
                                "Check-in: $checkInText - Check-out: $checkOutText"
                            else
                                ""
                            // We will render exactly:
                            // line1: Check-in/out
                            // line2: (can be used for other info)
                            // line3: Guest name
                            // numberGuest: Number of guests (separate field)
                            // roomType: Room type (separate field)
                            var line2 = "" // Can be used for other info if needed
                            var line3 = "Guest name: Loading..."
                            
                            val ui = ReservationUi(
                                documentId = documentId,
                                reservationId = reservationId,
                                badge = badgeInitial,
                                line1 = line1,
                                line2 = line2,
                                line3 = line3,
                                action = actionInitial,
                                headerColor = headerColorInitial,
                                status = status,
                                numberGuest = numberGuest,
                                roomType = roomTypeInlineName ?: "Loading...",
                                roomTypeId = roomTypeId,
                                hotelId = doc.getString("hotelId"),
                                guestPhone = doc.getString("guestPhone"),
                                guestEmail = doc.getString("guestEmail"),
                                totalFinalAmount = totalFinal,
                                checkInText = checkInText,
                                checkOutText = checkOutText,
                                checkInMillis = checkInMillis,
                                checkOutMillis = checkOutMillis,
                                discountLabel = discountText,
                                cleaningCompletedAtMillis = cleaningCompletedAtMillis
                            )
                            
                            // Show dialog if cleaning was just completed
                            if (wasJustCleaned) {
                                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                    showCleaningCompletedDialog(reservationId, roomTypeInlineName ?: "Room")
                                }
                            }
                            updated.add(ui)
                            val uiIndex = updated.lastIndex
                            
                            if (customerIdRaw != null) {
                                // Resolve guest name concurrently; bind by index to avoid wrong item updates
                                val customerJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                                    val currentJob = coroutineContext[Job]
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
                                        val guestPhone = userDoc?.getString("phoneNumber")
                                            ?: userDoc?.getString("phone")
                                            ?: userDoc?.getString("mobile")
                                        val guestEmail = userDoc?.getString("email")
                                        // Update UI on main for this specific index
                                        launch(Dispatchers.Main) {
                                            if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.INITIALIZED) &&
                                                uiIndex >= 0 && uiIndex < updated.size) {
                                                val existing = updated[uiIndex]
                                                updated[uiIndex] = existing.copy(
                                                    line3 = "Guest name: $guestName",
                                                    guestPhone = guestPhone,
                                                    guestEmail = guestEmail
                                                )
                                                allReservations.clear()
                                                allReservations.addAll(updated)
                                                filterReservations()
                                            }
                                        }
                                    } catch (_: Exception) {
                                        // ignore
                                    } finally {
                                        currentJob?.let { activeJobs.remove(it) }
                                    }
                                }
                                activeJobs.add(customerJob)
                            } else {
                                // Fallback: keep customerId or unknown
                                val existing = updated[uiIndex]
                                val fallbackGuest = doc.getString("guestName") ?: "Guest"
                                val fallbackPhone = doc.getString("guestPhone") ?: doc.getString("phone")
                                val fallbackEmail = doc.getString("guestEmail") ?: doc.getString("email")
                                updated[uiIndex] = existing.copy(
                                    line3 = "Guest name: $fallbackGuest",
                                    guestPhone = fallbackPhone,
                                    guestEmail = fallbackEmail
                                )
                            }

                            // Resolve room type if not already present; support multiple formats
                            val roomTypeRaw = roomTypeSource
                            if (roomTypeInlineName.isNullOrBlank()) {
                                val roomTypeJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                                    val currentJob = coroutineContext[Job]
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
                                        val resolvedRoomTypeId = roomTypeId
                                            ?: typeDoc?.id
                                            ?: extractRoomTypeId(roomTypeRaw)
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
                                            if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.INITIALIZED)) {
                                                // Find the reservation in allReservations by documentId
                                                val index = allReservations.indexOfFirst { it.documentId == bookingDocId }
                                                if (index >= 0) {
                                                    val existing = allReservations[index]
                                                    allReservations[index] = existing.copy(roomType = typeName, roomTypeId = resolvedRoomTypeId)
                                                    reservationMeta[bookingDocId]?.let { meta ->
                                                        reservationMeta[bookingDocId] = meta.copy(roomTypeId = resolvedRoomTypeId)
                                                    }
                                                    applyInvoiceDataToReservations(forceFilter = true)
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {
                                        // ignore
                                    } finally {
                                        currentJob?.let { activeJobs.remove(it) }
                                    }
                                }
                                activeJobs.add(roomTypeJob)
                            }
                        } catch (_: Exception) {
                            // Skip malformed document
                        }
                    }
                    // Replace list immediately so UI shows quickly
                    if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.INITIALIZED)) {
                        val currentDocIds = updated.map { it.documentId }.toSet()
                        reservationMeta.keys.retainAll(currentDocIds)
                        allReservations.clear()
                        allReservations.addAll(updated)
                        applyInvoiceDataToReservations(forceFilter = true)
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

    private fun startInvoiceListener() {
        if (invoicesListener != null) return
        invoicesListener = Firebase.firestore.collection("invoices")
            .addSnapshotListener { snapshots, error ->
                if (!isAdded) return@addSnapshotListener
                if (error != null || snapshots == null) {
                    return@addSnapshotListener
                }
                if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.INITIALIZED)) {
                    return@addSnapshotListener
                }
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    handleInvoiceSnapshot(snapshots)
                }
            }
    }

    private fun stopInvoiceListener() {
        try {
            invoicesListener?.remove()
        } catch (_: Exception) { }
        invoicesListener = null
        invoiceDocuments.clear()
    }

    private fun handleInvoiceSnapshot(snapshots: com.google.firebase.firestore.QuerySnapshot) {
        val newDocuments = mutableMapOf<String, InvoiceDocInfo>()
        for (doc in snapshots.documents) {
            val amount = when (val totalAmount = doc.get("totalAmount")) {
                is Number -> totalAmount.toDouble()
                is java.math.BigDecimal -> totalAmount.toDouble()
                is String -> totalAmount.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
            val keys = mutableSetOf<String>()
            keys += collectInvoiceKeyVariants(doc.id)
            keys += collectInvoiceKeyVariants(doc.get("bookingId"))
            keys += collectInvoiceKeyVariants(doc.get("booking_id"))
            keys += collectInvoiceKeyVariants(doc.get("bookingDocId"))
            keys += collectInvoiceKeyVariants(doc.get("booking_doc_id"))
            keys += collectInvoiceKeyVariants(doc.get("bookingRef"))
            keys += collectInvoiceKeyVariants(doc.get("booking_ref"))
            keys += collectInvoiceKeyVariants(doc.get("reservationId"))
            keys += collectInvoiceKeyVariants(doc.get("reservation_id"))
            keys += collectInvoiceKeyVariants(doc.get("reservationCode"))
            keys += collectInvoiceKeyVariants(doc.get("reservation_code"))
            if (keys.isEmpty()) continue
            newDocuments[doc.id] = InvoiceDocInfo(amount = amount, matcherKeys = keys.filter { it.isNotEmpty() }.toSet())
        }
        invoiceDocuments.clear()
        invoiceDocuments.putAll(newDocuments)
        applyInvoiceDataToReservations()
    }

    private fun applyInvoiceDataToReservations(forceFilter: Boolean = false) {
        if (!isAdded) return
        if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.INITIALIZED)) {
            return
        }
        var changed = false
        for (index in allReservations.indices) {
            val item = allReservations[index]
            val meta = reservationMeta[item.documentId] ?: continue
            val update = computePaymentUiState(meta)
            val newItem = item.copy(
                status = update.status,
                headerColor = update.headerColor,
                badge = update.badge,
                action = update.action
            )
            if (newItem != item) {
                allReservations[index] = newItem
                changed = true
            }
        }
        if (changed || forceFilter) {
            filterReservations()
        }
    }

    private fun computePaymentUiState(meta: ReservationMeta): PaymentUiState {
        if (meta.isCanceled) {
            return PaymentUiState(
                status = ReservationStatus.CANCELED,
                headerColor = HeaderColor.RED,
                badge = "Cancelled",
                action = "Cancelled"
            )
        }

        val amountPaid = computeAmountForReservation(meta)
        val paymentStatus = when {
            meta.totalFinal > 0.0 && amountPaid >= meta.totalFinal -> PaymentStatus.FULL
            meta.totalFinal > 0.0 && amountPaid > 0.0 && amountPaid < meta.totalFinal -> PaymentStatus.PARTIAL
            else -> PaymentStatus.NONE
        }

        val normalizedStatus = meta.statusStr.lowercase()
        val finalStatus = when {
            paymentStatus == PaymentStatus.FULL -> ReservationStatus.COMPLETED
            normalizedStatus == "completed" -> ReservationStatus.COMPLETED
            normalizedStatus == "canceled" || normalizedStatus == "cancelled" -> ReservationStatus.CANCELED
            normalizedStatus == "pending_payment" || normalizedStatus == "pending payment" -> ReservationStatus.PENDING
            normalizedStatus == "checked_out" || normalizedStatus == "checked out" -> ReservationStatus.PENDING
            normalizedStatus == "checked_in" || normalizedStatus == "checked in" -> ReservationStatus.UNCOMPLETED
            meta.hasCheckedOut -> ReservationStatus.PENDING
            meta.hasCheckedIn -> ReservationStatus.UNCOMPLETED
            else -> meta.baseStatus
        }
 
        val headerColor = when {
            finalStatus == ReservationStatus.CANCELED -> HeaderColor.RED
            finalStatus == ReservationStatus.COMPLETED -> HeaderColor.GREEN
            finalStatus == ReservationStatus.PENDING -> HeaderColor.YELLOW
            paymentStatus == PaymentStatus.FULL -> HeaderColor.GREEN
            paymentStatus == PaymentStatus.PARTIAL -> HeaderColor.BLUE
            else -> HeaderColor.YELLOW
        }
 
        val badge = when {
            finalStatus == ReservationStatus.CANCELED -> "Cancelled"
            finalStatus == ReservationStatus.COMPLETED -> "Completed"
            finalStatus == ReservationStatus.PENDING -> "Pending payment"
            paymentStatus == PaymentStatus.FULL -> "Paid"
            paymentStatus == PaymentStatus.PARTIAL -> "Deposit paid"
            else -> ""
        }
 
        val action = when {
            finalStatus == ReservationStatus.CANCELED -> "Cancelled"
            finalStatus == ReservationStatus.COMPLETED -> "Completed"
            finalStatus == ReservationStatus.PENDING -> "Payment"
            meta.hasCheckedOut -> "Payment"
            meta.hasCheckedIn -> "Check-out"
            paymentStatus != PaymentStatus.NONE -> "Check-in"
            else -> "Payment"
        }

        return PaymentUiState(
            status = finalStatus,
            headerColor = headerColor,
            badge = badge,
            action = action
        )
    }

    private fun computeAmountForReservation(meta: ReservationMeta): Double {
        if (invoiceDocuments.isEmpty()) return 0.0
        if (meta.invoiceKeys.isEmpty()) return 0.0
        var total = 0.0
        for (info in invoiceDocuments.values) {
            if (info.matcherKeys.any { meta.invoiceKeys.contains(it) }) {
                total += info.amount
            }
        }
        return total
    }

    private fun extractRoomTypeId(source: Any?): String? = when (source) {
        is com.google.firebase.firestore.DocumentReference -> source.id
        is String -> source
        is Map<*, *> -> {
            (source["id"] as? String)
                ?: (source["roomTypeId"] as? String)
                ?: (source["room_type_id"] as? String)
                ?: (source["id"] as? Number)?.toString()
        }
        else -> null
    }

    private fun collectInvoiceKeyVariants(value: Any?): Set<String> {
        val normalized = when (value) {
            is Number -> value.toLong().toString()
            is String -> value.trim()
            is com.google.firebase.firestore.DocumentReference -> value.id
            is Map<*, *> -> (value["id"] as? String)?.trim()
            else -> null
        }
        if (normalized.isNullOrEmpty()) return emptySet()
        val lowercase = normalized.lowercase()
        return if (normalized == lowercase) setOf(normalized) else setOf(normalized, lowercase)
    }

    private fun valueToDateTimeString(value: Any?): String {
        return when (value) {
            null -> ""
            is String -> {
                val trimmed = value.trim()
                trimmed.toLongOrNull()?.let { formatDateTime(it) }
                    ?: parseDateString(trimmed)?.let { formatDateTime(it) }
                    ?: trimmed
            }
            is Timestamp -> formatDateTime(value.toDate().time)
            is java.util.Date -> formatDateTime(value.time)
            is Number -> formatDateTime(value.toLong())
            else -> value.toString()
        }
    }

    private fun valueToMillis(value: Any?): Long? {
        return when (value) {
            null -> null
            is Timestamp -> value.toDate().time
            is java.util.Date -> value.time
            is Number -> value.toLong()
            is String -> {
                val trimmed = value.trim()
                trimmed.toLongOrNull() ?: parseDateString(trimmed)
            }
            else -> null
        }
    }

    private fun parseDateString(value: String): Long? {
        if (value.isBlank()) return null
        val patterns = listOf(
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd"
        )
        for (pattern in patterns) {
            try {
                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                sdf.isLenient = false
                val date = sdf.parse(value)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun formatDateTime(millis: Long): String {
        return try {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(millis))
        } catch (_: Exception) {
            java.util.Date(millis).toString()
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
                searchQuery = s?.toString()?.lowercase(Locale.getDefault()) ?: ""
                filterReservations()
            }
        })
    }

    private fun filterReservations() {
        val locale = Locale.getDefault()
        val query = searchQuery.trim()
        val filtered = allReservations.filter { reservation ->
            // Filter by status using the actual status field
            val statusMatch = when (currentFilter) {
                ReservationStatus.ALL -> true
                ReservationStatus.PENDING -> {
                    reservation.status == ReservationStatus.PENDING ||
                        reservation.action.equals("payment", ignoreCase = true)
                }
                ReservationStatus.UNCOMPLETED -> reservation.status == ReservationStatus.UNCOMPLETED ||
                    reservation.action.equals("payment", ignoreCase = true)
                ReservationStatus.COMPLETED -> reservation.status == ReservationStatus.COMPLETED
                ReservationStatus.CANCELED -> reservation.status == ReservationStatus.CANCELED
            }
            
            // Filter by search query
            val searchMatch = if (query.isEmpty()) {
                true
            } else {
                val lowerQuery = query
                val textMatches = listOf(
                    reservation.reservationId,
                    reservation.line1,
                    reservation.line2,
                    reservation.line3,
                    reservation.badge,
                    reservation.action,
                    reservation.roomType,
                    reservation.guestPhone ?: "",
                    reservation.guestEmail ?: ""
                ).any { text ->
                    text.lowercase(locale).contains(lowerQuery)
                }

                val numericMatches = listOf(
                    reservation.numberGuest.toString(),
                    reservation.totalFinalAmount.toLong().toString(),
                    reservation.totalFinalAmount.toString()
                ).any { numberText ->
                    numberText.lowercase(locale).contains(lowerQuery)
                }

                textMatches || numericMatches
            }
            
            statusMatch && searchMatch
        }
        
        // Sort: reservations with recent cleaningCompletedAt go to the top
        val sorted = filtered.sortedByDescending { reservation ->
            reservation.cleaningCompletedAtMillis ?: Long.MIN_VALUE
        }
        
        reservationAdapter.updateData(sorted.toMutableList())
    }
    
    private fun showCleaningCompletedDialog(reservationId: String, roomType: String) {
        if (!isAdded || context == null) return
        AlertDialog.Builder(requireContext())
            .setTitle("Room Ready for Payment")
            .setMessage("Room $roomType (Reservation: $reservationId) has been cleaned and is ready for payment.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun placeholderItems(): List<ReservationUi> = listOf(
        ReservationUi(documentId = "doc-R8ZZPQR7", reservationId = "R8ZZPQR7", badge = "Deposit paid", line1 = "Check-in: 20/09/1025 - Check-out: 22/09/2025", line2 = "", line3 = "Guest name: Harper", action = "Check-in", headerColor = HeaderColor.BLUE, status = ReservationStatus.UNCOMPLETED, numberGuest = 2, roomType = "Deluxe", cleaningCompletedAtMillis = null),
        ReservationUi(documentId = "doc-R8ZZPQR8", reservationId = "R8ZZPQR8", badge = "Paid", line1 = "Check-in: 20/09/1025 - check-out: 22/09/2025", line2 = "", line3 = "Guest name: Lily", action = "Check-out", headerColor = HeaderColor.GREEN, status = ReservationStatus.UNCOMPLETED, numberGuest = 3, roomType = "Suite", cleaningCompletedAtMillis = null),
        ReservationUi(documentId = "doc-R8ZZPQR9", reservationId = "R9ZZPQR9", badge = "", line1 = "Check-in: 20/09/1025 - check-out: 22/09/2025", line2 = "", line3 = "Guest name: Cap", action = "Payment", headerColor = HeaderColor.YELLOW, status = ReservationStatus.PENDING, numberGuest = 1, roomType = "Standard", cleaningCompletedAtMillis = null),
        ReservationUi(documentId = "doc-R8ZZPQR0", reservationId = "R8ZZPQR0", badge = "Paid", line1 = "Check-in: 20/09/1025 - check-out: 22/09/2025", line2 = "", line3 = "Guest name: Ahri", action = "Check-in", headerColor = HeaderColor.GREEN, status = ReservationStatus.COMPLETED, numberGuest = 4, roomType = "Deluxe", cleaningCompletedAtMillis = null),
        ReservationUi(documentId = "doc-R9ABC123", reservationId = "R9ABC123", badge = "Cancelled", line1 = "Check-in: 15/09/1025 - check-out: 17/09/2025", line2 = "", line3 = "Guest name: Bob", action = "Payment", headerColor = HeaderColor.RED, status = ReservationStatus.CANCELED, numberGuest = 2, roomType = "Standard", cleaningCompletedAtMillis = null)
    )
}

private data class ReservationMeta(
    val documentId: String,
    val reservationId: String,
    val statusStr: String,
    val baseStatus: ReservationStatus,
    val isCanceled: Boolean,
    val hasCheckedIn: Boolean,
    val hasCheckedOut: Boolean,
    val totalFinal: Double,
    val invoiceKeys: Set<String>,
    val roomTypeId: String?,
    val hotelId: String?,
    val guestPhone: String?,
    val guestEmail: String?
)

private data class InvoiceDocInfo(
    val amount: Double,
    val matcherKeys: Set<String>
)

private data class PaymentUiState(
    val status: ReservationStatus,
    val headerColor: HeaderColor,
    val badge: String,
    val action: String
)

private enum class PaymentStatus {
    NONE, PARTIAL, FULL
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
    val status: ReservationStatus = ReservationStatus.UNCOMPLETED,
    val numberGuest: Int = 1,
    val roomType: String = "",
    val roomTypeId: String? = null,
    val hotelId: String? = null,
    val guestPhone: String? = null,
    val guestEmail: String? = null,
    val totalFinalAmount: Double = 0.0,
    val checkInText: String = "",
    val checkOutText: String = "",
    val checkInMillis: Long? = null,
    val checkOutMillis: Long? = null,
    val discountLabel: String = "-",
    val cleaningCompletedAtMillis: Long? = null
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
            // Don't allow any actions on canceled or completed reservations
            if (current.status == ReservationStatus.CANCELED || current.status == ReservationStatus.COMPLETED) {
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
                badge = current.badge.ifBlank { "" },
                action = "Check-out",
                headerColor = HeaderColor.GREEN,
                status = ReservationStatus.UNCOMPLETED
            )
            "check-out" -> current.copy(
                badge = "Pending payment",
                action = "Payment",
                headerColor = HeaderColor.YELLOW,
                status = ReservationStatus.PENDING
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
        val reservedGuests = items.getOrNull(position)?.numberGuest?.coerceAtLeast(1) ?: 1
        etGuests.setText(reservedGuests.toString())

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
            // Get the actual date/time from the dialog
            val dateTimeText = etDateTime.text.toString()
            val actualCheckInTime = try {
                // Parse the date/time from format "HH:mm:ss/dd/MM/yyyy"
                val formatter = java.text.SimpleDateFormat("HH:mm:ss/dd/MM/yyyy", java.util.Locale.getDefault())
                val date = formatter.parse(dateTimeText)
                date?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis() // Fallback to current time if parsing fails
            }
            
            // Advance state after confirmation
            advanceState(position)
            // Persist to Firestore: status = checked_in and save actual check-in date/time
            try {
                val docId = items[position].documentId
                Firebase.firestore.collection("bookings").document(docId)
                    .update(
                        mapOf(
                            "status" to "checked_in",
                            "checkInDateActual" to actualCheckInTime
                        )
                    )
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
        
        // Get current date/time for check-out
        val actualCheckOutTime = System.currentTimeMillis()

        val alert = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .create()

        btnConfirm.setOnClickListener {
            val current = items[position]
            val next = current.copy(
                badge = "Pending payment",
                action = "Payment",
                headerColor = HeaderColor.YELLOW,
                status = ReservationStatus.PENDING
            )
            items[position] = next
            notifyItemChanged(position)
            try {
                val docId = items[position].documentId
                Firebase.firestore.collection("bookings").document(docId)
                    .update(
                        mapOf(
                            "status" to "pending_payment",
                            "checkOutDateActual" to actualCheckOutTime
                        )
                    )
                    .addOnSuccessListener {
                        android.util.Log.d("Reception", "Checkout updated for $docId")
                    }
                    .addOnFailureListener { error ->
                        android.util.Log.e("Reception", "Failed to update checkout for $docId", error)
                        android.widget.Toast.makeText(ctx, "Failed to update checkout", android.widget.Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                android.util.Log.e("Reception", "Exception updating checkout", e)
            }
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
                intent.putExtra("GUEST_PHONE", current.guestPhone ?: "")
                intent.putExtra("GUEST_EMAIL", current.guestEmail ?: "")
                intent.putExtra("CHECK_IN_TEXT", current.checkInText)
                intent.putExtra("CHECK_OUT_TEXT", current.checkOutText)
                intent.putExtra("CHECK_IN", current.checkInText)
                intent.putExtra("CHECK_OUT", current.checkOutText)
                intent.putExtra("CHECK_IN_MILLIS", current.checkInMillis ?: -1L)
                intent.putExtra("CHECK_OUT_MILLIS", current.checkOutMillis ?: -1L)
                intent.putExtra("RESERVATION_AMOUNT", current.totalFinalAmount)
                intent.putExtra("BOOKING_ID", current.documentId)
                intent.putExtra("ROOM_TYPE_ID", current.roomTypeId ?: "")
                intent.putExtra("HOTEL_ID", current.hotelId ?: "")
                intent.putExtra("ROOM_TYPE_NAME", current.roomType)
                intent.putExtra("DISCOUNT_LABEL", current.discountLabel)
                intent.putExtra("GUESTS_COUNT", current.numberGuest)
                ctx.startActivity(intent)
                val docId = items[position].documentId
                Firebase.firestore.collection("bookings").document(docId)
                    .update("status", "pending_payment")
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
    private val tvNumberGuest: android.widget.TextView = view.findViewById(R.id.tvNumberGuest)
    private val tvRoomType: android.widget.TextView = view.findViewById(R.id.tvRoomType)
    private val btnAction: android.widget.TextView = view.findViewById(R.id.btnAction)

    fun bind(item: ReservationUi) {
        tvReservationId.text = "Reservation ID: ${item.reservationId}"
        tvStatusBadge.text = item.badge
        tvLine1.text = item.line1
        tvLine2.text = item.line2
        tvLine3.text = item.line3
        tvNumberGuest.text = "Number of guests: ${item.numberGuest}"
        tvRoomType.text = "Room type: ${item.roomType}"
        
        // Check if reservation is canceled or completed
        val isCanceled = item.status == ReservationStatus.CANCELED
        val isCompleted = item.status == ReservationStatus.COMPLETED
        
        // Set button text: "Cancelled" for canceled, "Completed" for completed, otherwise use action
        btnAction.text = when {
            isCanceled -> "Cancelled"
            isCompleted -> "Completed"
            else -> item.action
        }
        
        // Disable button and make it look frozen for canceled or completed reservations
        if (isCanceled || isCompleted) {
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




