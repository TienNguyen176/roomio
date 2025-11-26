package com.tdc.nhom6.roomio.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.firestore
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.activities.receptionist.ServiceExtraFeeActivity
import com.tdc.nhom6.roomio.adapters.ReservationAdapter
import com.tdc.nhom6.roomio.repositories.CleanerTaskRepository
import com.tdc.nhom6.roomio.models.Booking
import com.tdc.nhom6.roomio.models.CleanerTask
import com.tdc.nhom6.roomio.models.HeaderColor
import com.tdc.nhom6.roomio.models.ReservationStatus
import com.tdc.nhom6.roomio.models.ReservationUi
import com.tdc.nhom6.roomio.models.TaskStatus
import com.tdc.nhom6.roomio.utils.RecyclerViewUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ReceptionFragment : Fragment() {
    private lateinit var reservationAdapter: ReservationAdapter
    private val allReservations = mutableListOf<ReservationUi>()
    private var currentFilter: ReservationStatus = ReservationStatus.ALL
    private var searchQuery: String = ""
    private var currentBooking: Booking?= null
    private var bookingsListener: ListenerRegistration? = null
    private var invoicesListener: ListenerRegistration? = null
    private val activeJobs = mutableListOf<Job>()
    private val reservationMeta = mutableMapOf<String, ReservationMeta>()
    private val invoiceDocuments = mutableMapOf<String, InvoiceDocInfo>()
    private val reservationDisplayCodes = mutableMapOf<String, String>()
    private val cleaningStatusByBooking = mutableMapOf<String, TaskStatus>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_reception, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvReservations)
        RecyclerViewUtils.configureRecyclerView(rv)

        view.findViewById<ImageView>(R.id.btnBack)?.setOnClickListener {
            requireActivity().finish()
        }

        CleanerTaskRepository.latestCleaningResult().observe(viewLifecycleOwner) {
            val consumed = CleanerTaskRepository.consumeLatestCleaningResult() ?: return@observe
            runCatching {
                val roomId = currentBooking?.roomId
                val ctx = requireContext()
                val intent = Intent(ctx, ServiceExtraFeeActivity::class.java)
                intent.putExtra("ROOM_ID", roomId)
                intent.putExtra("RESERVATION_ID", roomId)
                intent.putExtra("RESERVATION_AMOUNT", 450000.0)
                startActivity(intent)
            }
        }

        CleanerTaskRepository.tasks().observe(viewLifecycleOwner) { tasks ->
            handleCleanerTasksSnapshot(tasks)
        }

        allReservations.clear()
        reservationAdapter = ReservationAdapter(allReservations.toMutableList())
        rv.adapter = reservationAdapter

        setupTabs(view)
        setupSearch(view)
        startInvoiceListener()
        startListeningBookings()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopListeningBookings()
        stopInvoiceListener()
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        reservationMeta.clear()
        reservationDisplayCodes.clear()
    }

    private fun startListeningBookings() {
        stopListeningBookings()
        val db = Firebase.firestore
        bookingsListener = db.collection("bookings")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    android.util.Log.e("ReceptionFragment", "Error listening to bookings: ${error.message}", error)
                    view?.let {
                        android.widget.Toast.makeText(
                            it.context,
                            "Database error: ${error.message}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    return@addSnapshotListener
                }
                if (snapshots == null || !isLifecycleActive()) return@addSnapshotListener
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    handleBookingSnapshot(snapshots)
                }
            }
    }

    private fun handleBookingSnapshot(snapshots: QuerySnapshot) {
        if (!isLifecycleActive()) return

        val cleaningNotifications = collectCleaningNotifications(snapshots)
        val builds = mutableListOf<ReservationBuildResult>()
        val updatedReservations = mutableListOf<ReservationUi>()

        for (doc in snapshots.documents) {
            val build = buildReservation(doc) ?: continue
            reservationMeta[build.meta.documentId] = build.meta
            builds.add(build)
            updatedReservations.add(applyCleaningStatusToReservation(build.ui))
        }

        val currentDocIds = updatedReservations.map { it.documentId }.toSet()
        reservationMeta.keys.retainAll(currentDocIds)
        reservationDisplayCodes.keys.retainAll(currentDocIds)

        allReservations.clear()
        allReservations.addAll(updatedReservations)
        applyInvoiceDataToReservations(forceFilter = true)

        cleaningNotifications.forEach { (reservationId, roomType) ->
            showCleaningCompletedDialog(reservationId, roomType)
        }

        builds.forEach { build ->
            launchCleanerTimestampRefresh(build.meta.documentId)
            launchGuestEnrichment(build.meta.documentId, build.customerRaw)
            launchRoomTypeEnrichment(
                documentId = build.meta.documentId,
                roomTypeSource = build.roomTypeSource,
                fallbackName = build.ui.roomType,
                knownRoomTypeId = build.meta.roomTypeId
            )
        }
    }
    private fun collectCleaningNotifications(snapshots: QuerySnapshot): List<Pair<String, String>> {
        val previous = allReservations.associateBy { it.documentId }
        val notifications = mutableListOf<Pair<String, String>>()
        snapshots.documentChanges.forEach { change ->
            if (change.type != DocumentChange.Type.MODIFIED) return@forEach
            val doc = change.document
            if (!shouldIncludeBooking(doc)) return@forEach
            val previousReservation = previous[doc.id]
            val previousCleaning = previousReservation?.cleaningCompletedAtMillis
            val newCleaningTimestamp = valueToTimestamp(doc.get("cleaningCompletedAt"))
            if (previousCleaning == null && newCleaningTimestamp != null) {
                val reservationId = doc.getString("reservationId") ?: doc.id
                val roomType = resolveRoomTypeName(resolveRoomTypeData(doc))
                notifications.add(reservationId to roomType)
            }
        }
        return notifications
    }

    private fun buildReservation(doc: DocumentSnapshot): ReservationBuildResult? {
        if (!shouldIncludeBooking(doc)) return null

        val documentId = doc.id
        val reservationId = doc.getString("reservationId") ?: documentId
        val customerRaw =
             doc.get("customerId")
            ?: doc.get("userId")
            ?: doc.get("userRef")
            ?: doc.get("customer")

        val roomTypeData = resolveRoomTypeData(doc)
        val roomTypeId = extractRoomTypeId(roomTypeData.source)

        val checkInValue = doc.get("checkIn") ?: doc.get("checkInDate")
        val checkOutValue = doc.get("checkOut") ?: doc.get("checkOutDate")
        val checkInTimestamp = valueToTimestamp(checkInValue)
        val checkOutTimestamp = valueToTimestamp(checkOutValue)
        val checkInText = valueToDateTimeString(checkInValue)
        val checkOutText = valueToDateTimeString(checkOutValue)
        val cleaningCompletedTimestamp = valueToTimestamp(doc.get("cleaningCompletedAt"))

        val hasCheckedIn = doc.get("checkInDateActual") != null
        val hasCheckedOut = doc.get("checkOutDateActual") != null

        val totalOrigin = (doc.get("totalOrigin") as? Number)?.toDouble() ?: 0.0
        val totalFinal = (doc.get("totalFinal") as? Number)?.toDouble() ?: totalOrigin
        val numberGuest = (doc.get("numberGuest") as? Number)?.toInt()
            ?: (doc.get("number_guest") as? Number)?.toInt()
            ?: (doc.get("guests") as? Number)?.toInt()
            ?: 1

        val isCanceled = doc.getBoolean("canceled") == true ||
                doc.getString("status")?.equals("canceled", ignoreCase = true) == true
        val statusStr = (doc.getString("status") ?: "").lowercase(Locale.getDefault()).trim()
        val baseStatus = resolveBaseStatus(statusStr, isCanceled, checkInValue, checkOutValue)

        val invoiceKeys = buildInvoiceKeys(doc, reservationId)
        val finalRoomTypeName = resolveRoomTypeName(roomTypeData)

        val badgeInitial = when {
            isCanceled -> "Cancelled"
            baseStatus == ReservationStatus.COMPLETED -> "Completed"
            baseStatus == ReservationStatus.PENDING -> "Pending payment"
            else -> ""
        }
        val actionInitial = when {
            isCanceled -> "Cancelled"
            baseStatus == ReservationStatus.COMPLETED -> "Completed"
            baseStatus == ReservationStatus.PENDING -> "Payment"
            hasCheckedIn -> "Check-out"
            else -> "Check-in"
        }
        val headerColorInitial = when {
            isCanceled -> HeaderColor.RED
            baseStatus == ReservationStatus.COMPLETED -> HeaderColor.GREEN
            baseStatus == ReservationStatus.PAID -> HeaderColor.PURPLE
            baseStatus == ReservationStatus.PENDING -> HeaderColor.YELLOW
            else -> HeaderColor.BLUE
        }

        val line1 = if (checkInText.isNotEmpty() || checkOutText.isNotEmpty())
            "Check-in: $checkInText - Check-out: $checkOutText" else ""

        val fallbackGuest = doc.getString("guestName") ?: "Guest"
        val fallbackPhone = doc.getString("guestPhone") ?: doc.getString("phone")
        val fallbackEmail = doc.getString("guestEmail") ?: doc.getString("email")
        val line3Initial = if (customerRaw != null) "Guest name: Loading..." else "Guest name: $fallbackGuest"

        val ui = ReservationUi(
            documentId = documentId,
            reservationId = reservationId,
            displayReservationCode = getOrCreateDisplayCode(documentId),
            badge = badgeInitial,
            line1 = line1,
            line2 = "",
            line3 = line3Initial,
            action = actionInitial,
            headerColor = headerColorInitial,
            status = baseStatus,
            numberGuest = numberGuest,
            roomType = finalRoomTypeName,
            roomTypeId = roomTypeId,
            hotelId = doc.getString("hotelId"),
            guestPhone = fallbackPhone,
            guestEmail = fallbackEmail,
            totalFinalAmount = totalFinal,
            checkInText = checkInText,
            checkOutText = checkOutText,
            checkInMillis = checkInTimestamp,
            checkOutMillis = checkOutTimestamp,
            discountLabel = doc.getString("discountText")
                ?: doc.getString("discountDescription")
                ?: doc.getString("promotion")
                ?: "-",
            cleaningCompletedAtMillis = cleaningCompletedTimestamp
        )

        val meta = ReservationMeta(
            documentId = documentId,
            reservationId = reservationId,
            statusStr = statusStr,
            baseStatus = baseStatus,
            isCanceled = isCanceled,
            hasCheckedIn = hasCheckedIn,
            hasCheckedOut = hasCheckedOut,
            totalFinal = totalFinal,
            invoiceKeys = invoiceKeys,
            roomTypeId = roomTypeId,
            hotelId = doc.getString("hotelId") ?: doc.getString("hotelId"),
            guestPhone = fallbackPhone,
            guestEmail = fallbackEmail
        )

        return ReservationBuildResult(
            meta = meta,
            ui = ui,
            customerRaw = customerRaw,
            roomTypeSource = roomTypeData.source ?: roomTypeId
        )
    }

    private fun shouldIncludeBooking(doc: DocumentSnapshot): Boolean {
        val paymentStatus = doc.getString("status")?.lowercase(Locale.getDefault()) ?: return true
        return paymentStatus == "confirmed" || paymentStatus == "checked_in" || paymentStatus == "checked_out"
    }


    private fun resolveBaseStatus(
        statusStr: String,
        isCanceled: Boolean,
        checkInValue: Any?,
        checkOutValue: Any?
    ): ReservationStatus = when {
        isCanceled -> ReservationStatus.CANCELED
        statusStr == "checked_out" || statusStr == "checked out" ||
                statusStr == "pending_payment" || statusStr == "pending payment" -> ReservationStatus.PENDING
        statusStr == "completed" -> ReservationStatus.COMPLETED
        statusStr == "checked_in" || statusStr == "checked in" -> ReservationStatus.UNCOMPLETED
        statusStr.isBlank() -> when {
            checkOutValue != null -> ReservationStatus.PENDING
            checkInValue != null -> ReservationStatus.UNCOMPLETED
            else -> ReservationStatus.PENDING
        }
        else -> ReservationStatus.UNCOMPLETED
    }

    private fun buildInvoiceKeys(doc: DocumentSnapshot, reservationId: String): Set<String> {
        val keys = mutableSetOf<String>()
        keys += collectInvoiceKeyVariants(doc.id)
        keys += collectInvoiceKeyVariants(reservationId)
        listOf(
            doc.get("id"),
            doc.get("ID"),
            doc.get("bookingId"),
            doc.get("bookingDocId"),
            doc.get("bookingCode"),
            doc.get("booking_code"),
            doc.get("bookingNumber"),
            doc.get("booking_number"),
            doc.get("bookingRef"),
            doc.get("booking_ref"),
            doc.get("reservationCode"),
            doc.get("reservation_code")
        ).forEach { value ->
            keys += collectInvoiceKeyVariants(value)
        }
        reservationId.toIntOrNull()?.let { keys += collectInvoiceKeyVariants(it) }
        return keys.filter { it.isNotEmpty() }.toSet()
    }

    private fun launchCleanerTimestampRefresh(documentId: String) {
        val job = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cleanerSnapshot = Firebase.firestore.collection("bookings")
                    .document(documentId)
                    .collection("cleaner")
                    .orderBy("cleaningCompletedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()
                if (!cleanerSnapshot.isEmpty) {
                    val timestamp = valueToTimestamp(cleanerSnapshot.documents.first().get("cleaningCompletedAt"))
                    launch(Dispatchers.Main) {
                        if (isLifecycleActive()) {
                            updateReservation(documentId) { existing ->
                                existing.copy(cleaningCompletedAtMillis = timestamp)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ReceptionFragment", "Error fetching cleaner data for $documentId", e)
            }
        }
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
    }

    private fun launchGuestEnrichment(documentId: String, customerRaw: Any?) {
        if (customerRaw == null) return
        val job = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userDoc = fetchUserDocument(customerRaw)
                val guestInfo = resolveGuestInfo(userDoc, customerRaw)
                launch(Dispatchers.Main) {
                    if (isLifecycleActive()) {
                        updateReservation(documentId) { existing ->
                            existing.copy(
                                line3 = "Guest name: ${guestInfo.name}",
                                guestPhone = guestInfo.phone ?: existing.guestPhone,
                                guestEmail = guestInfo.email ?: existing.guestEmail
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
    }

    private fun launchRoomTypeEnrichment(
        documentId: String,
        roomTypeSource: Any?,
        fallbackName: String,
        knownRoomTypeId: String?
    ) {
        val source = roomTypeSource ?: knownRoomTypeId ?: return
        val job = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (typeName, resolvedId) = fetchRoomTypeDetails(source, fallbackName)
                launch(Dispatchers.Main) {
                    if (isLifecycleActive()) {
                        updateReservation(documentId) { existing ->
                            existing.copy(roomType = typeName, roomTypeId = resolvedId)
                        }
                        reservationMeta[documentId]?.let { meta ->
                            reservationMeta[documentId] = meta.copy(roomTypeId = resolvedId)
                        }
                        applyInvoiceDataToReservations(forceFilter = true)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ReceptionFragment", "Failed to fetch room type for $documentId", e)
            }
        }
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
    }

    private suspend fun fetchUserDocument(customerRaw: Any?): DocumentSnapshot? {
        return when (customerRaw) {
            is DocumentReference -> customerRaw.get().await()
            is String -> Firebase.firestore.collection("users").document(customerRaw).get().await()
            is Number -> Firebase.firestore.collection("users").document(customerRaw.toString()).get().await()
            is Map<*, *> -> {
                val id = (customerRaw["id"] as? String) ?: (customerRaw["id"] as? Number)?.toString()
                id?.let { Firebase.firestore.collection("users").document(it).get().await() }
            }
            else -> null
        }
    }

    private fun resolveGuestInfo(userDoc: DocumentSnapshot?, customerRaw: Any?): GuestInfo {
        if (userDoc != null && userDoc.exists()) {
            val first = userDoc.getString("firstName") ?: userDoc.getString("first_name")
            val last = userDoc.getString("lastName") ?: userDoc.getString("last_name")
            val combined = listOfNotNull(first, last).joinToString(" ").trim()
            val name = firstNonBlank(
                combined.takeIf { it.isNotEmpty() },
                userDoc.getString("fullName"),
                userDoc.getString("full_name"),
                userDoc.getString("displayName"),
                userDoc.getString("username"),
                userDoc.getString("name")
            ) ?: "Guest"
            val phone = userDoc.getString("phoneNumber") ?: userDoc.getString("phone") ?: userDoc.getString("mobile")
            val email = userDoc.getString("email")
            return GuestInfo(name, phone, email)
        }

        val fallback = when (customerRaw) {
            is String -> customerRaw
            is Number -> customerRaw.toString()
            is DocumentReference -> customerRaw.id
            is Map<*, *> -> (customerRaw["id"] as? String) ?: "Guest"
            else -> "Guest"
        }
        return GuestInfo(fallback, null, null)
    }

    private suspend fun fetchRoomTypeDetails(source: Any, fallbackName: String): Pair<String, String?> {
        val doc = when (source) {
            is DocumentReference -> source.get().await()
            is String -> {
                val primary = Firebase.firestore.collection("roomTypes").document(source).get().await()
                if (primary.exists()) primary else Firebase.firestore.collection("room_types").document(source).get().await()
            }
            is Map<*, *> -> {
                val id = (source["id"] as? String) ?: (source["id"] as? Number)?.toString()
                id?.let { Firebase.firestore.collection("roomTypes").document(it).get().await() }
            }
            else -> null
        }
        val resolvedId = doc?.id ?: extractRoomTypeId(source)
        val name = doc?.let {
            it.getString("name")
                ?: it.getString("typeName")
                ?: it.getString("title")
                ?: it.getString("type_name")
        } ?: fallbackName
        return name to resolvedId
    }

    private fun resolveRoomTypeData(doc: DocumentSnapshot): RoomTypeData {
        val inlineName = doc.getString("roomTypeName")
            ?: doc.getString("room_type_name")
            ?: (doc.get("roomType") as? String)
            ?: ((doc.get("roomType") as? Map<*, *>)?.get("name") as? String)
            ?: ((doc.get("room") as? Map<*, *>)?.get("typeName") as? String)
        val source = doc.get("roomTypeRef")
            ?: doc.get("roomTypeId")
            ?: doc.get("room_type_id")
            ?: doc.get("roomType")
        return RoomTypeData(inlineName, source)
    }

    private fun resolveRoomTypeName(data: RoomTypeData): String {
        val inline = data.inlineName
        if (!inline.isNullOrBlank()) return inline
        return when (val source = data.source) {
            is String -> source.takeIf { it.isNotBlank() } ?: "Room"
            is Map<*, *> -> (source["name"] as? String) ?: "Room"
            else -> "Room"
        }
    }

    private fun updateReservation(documentId: String, transform: (ReservationUi) -> ReservationUi) {
        val index = allReservations.indexOfFirst { it.documentId == documentId }
        if (index < 0) return
        val updated = transform(allReservations[index])
        if (updated != allReservations[index]) {
            allReservations[index] = updated
            filterReservations()
        }
    }

    private fun handleInvoiceSnapshot(snapshots: QuerySnapshot) {
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
            keys += collectInvoiceKeyVariants(doc.get("bookingDocId"))
            keys += collectInvoiceKeyVariants(doc.get("bookingRef"))
            keys += collectInvoiceKeyVariants(doc.get("reservationId"))
            keys += collectInvoiceKeyVariants(doc.get("reservationCode"))
            if (keys.isEmpty()) continue
            newDocuments[doc.id] = InvoiceDocInfo(amount = amount, matcherKeys = keys.filter { it.isNotEmpty() }.toSet())
        }
        invoiceDocuments.clear()
        invoiceDocuments.putAll(newDocuments)
        applyInvoiceDataToReservations()
    }

    private fun startInvoiceListener() {
        if (invoicesListener != null) return
        invoicesListener = Firebase.firestore.collection("invoices")
            .addSnapshotListener { snapshots, error ->
                if (!isAdded || error != null || snapshots == null) return@addSnapshotListener
                if (!isLifecycleActive()) return@addSnapshotListener
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    handleInvoiceSnapshot(snapshots)
                }
            }
    }

    private fun stopListeningBookings() {
        runCatching { bookingsListener?.remove() }
        bookingsListener = null
    }

    private fun stopInvoiceListener() {
        runCatching { invoicesListener?.remove() }
        invoicesListener = null
        invoiceDocuments.clear()
    }

    private fun applyInvoiceDataToReservations(forceFilter: Boolean = false) {
        if (!isLifecycleActive()) return
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
            val adjustedItem = applyCleaningStatusToReservation(newItem)
            if (adjustedItem != item) {
                allReservations[index] = adjustedItem
                changed = true
            }
        }
        if (changed || forceFilter) {
            filterReservations()
        }
    }

    private fun handleCleanerTasksSnapshot(tasks: List<CleanerTask>) {
        val latestStatuses = tasks.mapNotNull { task ->
            task.bookingDocId?.let { it to task.status }
        }.toMap()
        if (latestStatuses != cleaningStatusByBooking) {
            cleaningStatusByBooking.clear()
            cleaningStatusByBooking.putAll(latestStatuses)
            applyCleaningStatusLocks()
        }
    }

    private fun applyCleaningStatusLocks(forceFilter: Boolean = false) {
        if (!isLifecycleActive()) return
        var changed = false
        for (index in allReservations.indices) {
            val updated = applyCleaningStatusToReservation(allReservations[index])
            if (updated != allReservations[index]) {
                allReservations[index] = updated
                changed = true
            }
        }
        if (changed || forceFilter) {
            filterReservations()
        }
    }

    private fun applyCleaningStatusToReservation(reservation: ReservationUi): ReservationUi {
        val shouldDisable = shouldDisablePaymentAction(reservation)
        val desiredEnabled = !shouldDisable
        return if (reservation.actionEnabled == desiredEnabled) reservation else reservation.copy(actionEnabled = desiredEnabled)
    }

    private fun shouldDisablePaymentAction(reservation: ReservationUi): Boolean {
        if (!reservation.action.equals("payment", ignoreCase = true)) return false
        val meta = reservationMeta[reservation.documentId] ?: return false
        val normalizedStatus = meta.statusStr.lowercase(Locale.getDefault())
        val requiresCleaner = meta.hasCheckedOut ||
                normalizedStatus == "pending_payment" ||
                normalizedStatus == "pending payment" ||
                normalizedStatus == "checked_out" ||
                normalizedStatus == "checked out"
        if (!requiresCleaner) return false
        val cleaningDone = reservation.cleaningCompletedAtMillis != null ||
                cleaningStatusByBooking[reservation.documentId] == TaskStatus.CLEAN
        if (cleaningDone) return false
        cleaningStatusByBooking[reservation.documentId]?.let { status ->
            if (status != TaskStatus.CLEAN) return true
        }
        return true
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

        val normalizedStatus = meta.statusStr.lowercase(Locale.getDefault())
        val finalStatus = when {
            paymentStatus == PaymentStatus.FULL -> ReservationStatus.PAID
            normalizedStatus == "completed" -> ReservationStatus.COMPLETED
            normalizedStatus == "canceled" || normalizedStatus == "cancelled" -> ReservationStatus.CANCELED
            normalizedStatus == "pending_payment" || normalizedStatus == "pending payment" -> ReservationStatus.PENDING
            normalizedStatus == "checked_out" || normalizedStatus == "checked out" -> ReservationStatus.PENDING
            normalizedStatus == "checked_in" || normalizedStatus == "checked in" -> ReservationStatus.UNCOMPLETED
            meta.hasCheckedOut -> ReservationStatus.PENDING
            meta.hasCheckedIn -> ReservationStatus.UNCOMPLETED
            else -> meta.baseStatus
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

        val headerColor = when {
            finalStatus == ReservationStatus.CANCELED -> HeaderColor.RED
            paymentStatus == PaymentStatus.FULL -> HeaderColor.PURPLE
            paymentStatus == PaymentStatus.PARTIAL -> HeaderColor.BLUE
            finalStatus == ReservationStatus.COMPLETED -> HeaderColor.GREEN
            action.equals("payment", ignoreCase = true) -> HeaderColor.YELLOW
            else -> HeaderColor.BLUE
        }

        val badge = when {
            finalStatus == ReservationStatus.CANCELED -> "Cancelled"
            finalStatus == ReservationStatus.COMPLETED -> "Completed"
            action == "Payment" -> "Pending payment"
            paymentStatus == PaymentStatus.FULL -> "Paid"
            paymentStatus == PaymentStatus.PARTIAL -> "Deposit paid"
            else -> ""
        }

        return PaymentUiState(
            status = finalStatus,
            headerColor = headerColor,
            badge = badge,
            action = action
        )
    }

    private fun computeAmountForReservation(meta: ReservationMeta): Double {
        if (invoiceDocuments.isEmpty() || meta.invoiceKeys.isEmpty()) return 0.0
        var total = 0.0
        for (info in invoiceDocuments.values) {
            if (info.matcherKeys.any { meta.invoiceKeys.contains(it) }) {
                total += info.amount
            }
        }
        return total
    }

    private fun extractRoomTypeId(source: Any?): String? = when (source) {
        is DocumentReference -> source.id
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
            is DocumentReference -> value.id
            is Map<*, *> -> (value["id"] as? String)?.trim()
            else -> null
        }
        if (normalized.isNullOrEmpty()) return emptySet()
        val lowercase = normalized.lowercase(Locale.getDefault())
        return if (normalized == lowercase) setOf(normalized) else setOf(normalized, lowercase)
    }

    private fun valueToDateTimeString(value: Any?): String = when (value) {
        null -> ""
        is Timestamp -> convertTimestampToString(value)
        is String -> {
            val trimmed = value.trim()
            trimmed.toLongOrNull()?.let { convertTimestampToString(Timestamp(Date(it))) }
                ?: parseDateString(trimmed)?.let { convertTimestampToString(Timestamp(Date(it))) }
                ?: trimmed
        }
        else -> value.toString()
    }

    private fun valueToTimestamp(value: Any?): Timestamp? = when (value) {
        null -> null
        is Timestamp -> value
        is String -> {
            val trimmed = value.trim()
            trimmed.toLongOrNull()?.let { Timestamp(Date(it)) }
                ?: parseDateString(trimmed)?.let { Timestamp(Date(it)) }
        }
        is Number -> Timestamp(Date(value.toLong()))
        else -> null
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
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                sdf.isLenient = false
                val date = sdf.parse(value)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun convertTimestampToString(timestamp: Timestamp): String {
        val date: Date = timestamp.toDate()
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return dateFormatter.format(date)
    }

    private fun getOrCreateDisplayCode(documentId: String): String {
        return reservationDisplayCodes.getOrPut(documentId) {
            formatReservationCode(reservationDisplayCodes.size + 1)
        }
    }

    private fun formatReservationCode(number: Int): String {
        return String.format(Locale.US, "RIO-%03d", number.coerceAtLeast(1))
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }

    private fun setupTabs(view: View) {
        val tabAll = view.findViewById<android.widget.TextView>(R.id.tabAll)
        val tabUncompleted = view.findViewById<android.widget.TextView>(R.id.tabUncompleted)
        val tabPending = view.findViewById<android.widget.TextView>(R.id.tabPending)
        val tabCompleted = view.findViewById<android.widget.TextView>(R.id.tabCompleted)
        val tabCanceled = view.findViewById<android.widget.TextView>(R.id.tabCanceled)

        val tabs = listOf(tabAll, tabUncompleted, tabPending, tabCompleted, tabCanceled)
        val statuses = listOf(
            ReservationStatus.ALL,
            ReservationStatus.UNCOMPLETED,
            ReservationStatus.PENDING,
            ReservationStatus.COMPLETED,
            ReservationStatus.CANCELED
        )

        fun updateTabSelection(selectedIndex: Int) {
            tabs.forEachIndexed { index, tab ->
                if (index == selectedIndex) {
                    tab.setBackgroundColor(android.graphics.Color.parseColor("#2D6C8C"))
                    tab.setTextColor(android.graphics.Color.WHITE)
                } else {
                    tab.background = context?.getDrawable(R.drawable.bg_tab_chip)
                    tab.setTextColor(android.graphics.Color.parseColor("#2D6C8C"))
                }
            }
        }

        tabs.forEachIndexed { index, tab ->
            tab.setOnClickListener {
                currentFilter = statuses[index]
                updateTabSelection(index)
                filterReservations()
            }
        }

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
                ReservationStatus.PAID -> TODO()
            }

            val searchMatch = if (query.isEmpty()) {
                true
            } else {
                val lowerQuery = query
                val textMatches = listOf(
                    reservation.reservationId,
                    reservation.displayReservationCode,
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

        val sorted = filtered.sortedByDescending { reservation ->
            reservation.cleaningCompletedAtMillis?.toDate()?.time ?: Long.MIN_VALUE
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

    private fun isLifecycleActive(): Boolean {
        return isAdded && viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)
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

    private data class ReservationBuildResult(
        val meta: ReservationMeta,
        val ui: ReservationUi,
        val customerRaw: Any?,
        val roomTypeSource: Any?
    )

    private data class RoomTypeData(
        val inlineName: String?,
        val source: Any?
    )

    private data class GuestInfo(
        val name: String,
        val phone: String?,
        val email: String?
    )
}

