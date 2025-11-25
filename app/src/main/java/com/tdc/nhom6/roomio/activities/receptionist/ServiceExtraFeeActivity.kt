package com.tdc.nhom6.roomio.activities.receptionist

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.ServiceFeeAdapter
import com.tdc.nhom6.roomio.repositories.CleanerTaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.tdc.nhom6.roomio.utils.FormatUtils
import com.tdc.nhom6.roomio.utils.InvoiceQueryUtils
import kotlin.math.max

class ServiceExtraFeeActivity : AppCompatActivity() {
    private lateinit var tvResId: TextView
    private lateinit var tvGuestName: TextView
    private lateinit var tvCheckIn: TextView
    private lateinit var tvCheckOut: TextView
    private lateinit var tvReservationAmount: TextView
    private lateinit var tvPaidAmount: TextView
    private lateinit var tvCleaningFee: TextView
    private lateinit var tvExtraFee: TextView
    private lateinit var tvGrandTotal: TextView
    private lateinit var serviceAdapter: ServiceFeeAdapter
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var serviceFetchJob: Job? = null
    private var reservationAmount: Double = 0.0
    private var paidAmount: Double = 0.0
    private var cleaningFee: Double = 0.0
    private var extraTotal: Double = 0.0
    private var lastExtraTotal: Double = 0.0
    private var roomFeesLoaded: Boolean = false
    private var currentRoomFeeItems: List<ServiceFeeAdapter.ServiceItem> = emptyList()
    private var discountLabel: String = "-"
    private var guestsCount: Int = 0
    private var invoiceListener: ListenerRegistration? = null
    private var cleaningFeeListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_extra_fee)
//        findViewById<ImageView>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        tvResId = findViewById(R.id.tvResId)

        tvGuestName = findViewById(R.id.tvGuestName)
        tvCheckIn = findViewById(R.id.tvCheckIn)
        tvCheckOut = findViewById(R.id.tvCheckOut)
        tvReservationAmount = findViewById(R.id.tvReservationAmount)
        tvPaidAmount = findViewById(R.id.tvPaidAmount)
        tvCleaningFee = findViewById(R.id.tvCleaningFee)
        tvExtraFee = findViewById(R.id.tvExtraFee)
        tvGrandTotal = findViewById(R.id.tvGrandTotal)

        val resId = intent.getStringExtra("RESERVATION_ID") ?: ""
        val guestName = intent.getStringExtra("GUEST_NAME") ?: ""
        val rawCheckIn = intent.getStringExtra("CHECK_IN_TEXT") ?: intent.getStringExtra("CHECK_IN") ?: ""
        val rawCheckOut = intent.getStringExtra("CHECK_OUT_TEXT") ?: intent.getStringExtra("CHECK_OUT") ?: ""
        val checkInMillis = intent.getLongExtra("CHECK_IN_MILLIS", -1L)
        val checkOutMillis = intent.getLongExtra("CHECK_OUT_MILLIS", -1L)
        val roomTypeName = intent.getStringExtra("ROOM_TYPE_NAME") ?: intent.getStringExtra("ROOM_TYPE") ?: ""
        val amount = intent.getDoubleExtra("RESERVATION_AMOUNT", 0.0)
        val discountLabelExtra = intent.getStringExtra("DISCOUNT_LABEL") ?: "-"
        val guestsCountExtra = intent.getIntExtra("GUESTS_COUNT", 0)
        val roomId = intent.getStringExtra("ROOM_ID") ?: ""

        reservationAmount = amount
        discountLabel = discountLabelExtra
        guestsCount = guestsCountExtra

        // Initialize cleaning fee from repository (fallback)
        cleaningFee = CleanerTaskRepository.getCleaningFee(roomId)
        updateCleaningFee(cleaningFee)
        updateExtraTotal(0.0)

        val checkInText = if (rawCheckIn.isNotBlank()) rawCheckIn else formatDateTimeOrEmpty(checkInMillis)
        val checkOutText = if (rawCheckOut.isNotBlank()) rawCheckOut else formatDateTimeOrEmpty(checkOutMillis)
        val nightCount = computeNights(checkInMillis, checkOutMillis)

        tvResId.text = if (resId.isNotEmpty()) "Reservation ID: $resId" else ""
        tvGuestName.text = if (guestName.isNotEmpty()) "Guest name: $guestName" else ""
        tvCheckIn.text = if (checkInText.isNotEmpty()) "Check-in: $checkInText" else "Check-in: -"
        tvCheckOut.text = if (checkOutText.isNotEmpty()) "Check-out: $checkOutText" else "Check-out: -"
        tvReservationAmount.text = "Reservation amount: ${formatCurrency(amount)}"

        val rvServices = findViewById<RecyclerView>(R.id.rvServices)
        rvServices.layoutManager = LinearLayoutManager(this)
        serviceAdapter = ServiceFeeAdapter(mutableListOf()) { total -> updateExtraTotal(total) }
        rvServices.adapter = serviceAdapter

        val hotelId = intent.getStringExtra("HOTEL_ID")?.takeIf { it.isNotBlank() }
        val bookingId = intent.getStringExtra("BOOKING_ID")?.takeIf { it.isNotBlank() }

        // Load paid amount from invoices and cleaning fee from Firebase booking document
        if (bookingId != null) {
            observeInvoices(bookingId, resId)
            observeCleaningFee(bookingId)
        }

        val btnNext = findViewById<MaterialButton>(R.id.btnNext)
        btnNext.setOnClickListener {
            if (!btnNext.isEnabled) return@setOnClickListener
            btnNext.isEnabled = false
            val nextIntent = android.content.Intent(this, PaymentDetailsActivity::class.java)
            val incoming = intent
            nextIntent.putExtra("GUEST_NAME", incoming.getStringExtra("GUEST_NAME") ?: "")
            nextIntent.putExtra("GUEST_PHONE", incoming.getStringExtra("GUEST_PHONE") ?: "")
            nextIntent.putExtra("GUEST_EMAIL", incoming.getStringExtra("GUEST_EMAIL") ?: "")
            nextIntent.putExtra("RESERVATION_ID", resId)
            nextIntent.putExtra("CHECK_IN_TEXT", checkInText)
            nextIntent.putExtra("CHECK_OUT_TEXT", checkOutText)
            nextIntent.putExtra("CHECK_IN_MILLIS", checkInMillis)
            nextIntent.putExtra("CHECK_OUT_MILLIS", checkOutMillis)
            nextIntent.putExtra("ROOM_TYPE", roomTypeName)
            nextIntent.putExtra("NIGHTS_COUNT", nightCount)
            nextIntent.putExtra("ROOM_PRICE", amount)
            nextIntent.putExtra("EXTRA_FEE", lastExtraTotal)
            nextIntent.putExtra("CLEANING_FEE", cleaningFee)
            nextIntent.putExtra("DISCOUNT_TEXT", discountLabel)
            nextIntent.putExtra("GUESTS_COUNT", guestsCount)
            nextIntent.putExtra("BOOKING_ID", bookingId ?: "")
            nextIntent.putExtra("HOTEL_ID", hotelId ?: "")
            startActivity(nextIntent)
            finish()
            btnNext.postDelayed({ btnNext.isEnabled = true }, 600)
        }

        // Load room fees first, then hotel services
        if (bookingId != null) {
            when {
                hotelId != null -> loadRoomFeesThenServices(bookingId, hotelId)
                else -> loadRoomFeesThenResolveHotel(bookingId)
            }
        } else {
            when {
                hotelId != null -> loadServiceRates(hotelId)
                else -> serviceAdapter.replaceItems(emptyList())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceFetchJob?.cancel()
        invoiceListener?.remove()
        cleaningFeeListener?.remove()
    }

    private fun updateCleaningFee(value: Double) {
        cleaningFee = value
        tvCleaningFee.text = "Cleaning inspection fee: ${formatCurrency(cleaningFee)}"
        tvCleaningFee.visibility = if (cleaningFee > 0.0) View.VISIBLE else View.GONE
        refreshPriceSummary()
    }

    private fun updateExtraTotal(total: Double) {
        extraTotal = total
        refreshPriceSummary()
    }

    private fun formatDateTimeOrEmpty(millis: Long): String = FormatUtils.formatDateTimeOrEmpty(millis)

    private fun formatCurrency(value: Double): String = FormatUtils.formatCurrency(value)

    private fun refreshPriceSummary() {
        lastExtraTotal = extraTotal
        val totalAdditional = cleaningFee // cleaningFee already includes lost/broken/minibar fees
        tvExtraFee.text = "Total additional charges: ${formatCurrency(totalAdditional)}"
        val grandTotal = max(0.0, reservationAmount + totalAdditional - paidAmount)
        tvGrandTotal.text = "Total amount: ${formatCurrency(grandTotal)}"
    }

    private fun observeInvoices(bookingId: String, reservationId: String) {
        invoiceListener?.remove()
        val queries = InvoiceQueryUtils.createInvoiceQueries(firestore, bookingId, reservationId)
        var listenerSet = false

        // Try each query until one returns results
        for (query in queries) {
            try {
                query.get().addOnSuccessListener { snapshot ->
                    if (!snapshot.isEmpty && !listenerSet) {
                        listenerSet = true
                        invoiceListener = query.addSnapshotListener { snapshots, error ->
                            if (error != null) {
                                android.util.Log.e("ServiceExtraFee", "Invoice listener error", error)
                                return@addSnapshotListener
                            }
                            if (snapshots != null) {
                                updatePaidAmountFromDocs(snapshots.documents)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ServiceExtraFee", "Error trying invoice query", e)
            }
        }

        // If no query worked, try a general query and filter in memory
        if (!listenerSet) {
            invoiceListener = firestore.collection("invoices")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        android.util.Log.e("ServiceExtraFee", "Invoice listener error", error)
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        val filtered = InvoiceQueryUtils.filterInvoicesInMemory(
                            snapshots.documents,
                            bookingId,
                            reservationId
                        )
                        if (filtered.isNotEmpty()) {
                            updatePaidAmountFromDocs(filtered)
                        } else {
                            paidAmount = 0.0
                            tvPaidAmount.text = "Paid amount: ${formatCurrency(paidAmount)}"
                            refreshPriceSummary()
                        }
                    }
                }
        }
    }

    private fun updatePaidAmountFromDocs(docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
        paidAmount = docs.sumOf { InvoiceQueryUtils.extractTotalAmount(it) }
        tvPaidAmount.text = "Paid amount: ${formatCurrency(paidAmount)}"
        refreshPriceSummary()
    }

    private fun observeCleaningFee(bookingId: String) {
        cleaningFeeListener?.remove()
        // Listen to cleaner subcollection instead of booking document
        cleaningFeeListener = firestore.collection("bookings")
            .document(bookingId)
            .collection("cleaner")
            .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    android.util.Log.e("ServiceExtraFee", "Cleaning fee listener error", error)
                    return@addSnapshotListener
                }
                if (snapshots != null && !snapshots.isEmpty) {
                    val cleanerDoc = snapshots.documents.first()
                    val fee = when (val raw = cleanerDoc.get("cleaningFee")) {
                        is Number -> raw.toDouble()
                        is String -> raw.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    if (fee > 0.0) {
                        cleaningFee = fee
                        updateCleaningFee(cleaningFee)
                    }
                    // Reload room fees from cleaner document
                    lifecycleScope.launch {
                        val roomFees = withContext(Dispatchers.IO) {
                            runCatching {
                                val roomFeesMap = cleanerDoc.get("roomFees") as? Map<*, *>
                                if (roomFeesMap != null) {
                                    buildRoomFeeItems(roomFeesMap)
                                } else {
                                    fetchRoomFees(bookingId)
                                }
                            }.getOrElse { emptyList() }
                        }
                        applyRoomFees(roomFees)
                    }
                } else {
                    // No cleaner document found, try to load from invoice as fallback
                    lifecycleScope.launch {
                        val roomFees = withContext(Dispatchers.IO) {
                            runCatching { fetchRoomFees(bookingId) }.getOrElse { emptyList() }
                        }
                        applyRoomFees(roomFees)
                    }
                }
            }
    }


    private fun loadServiceRates(hotelId: String) {
        serviceFetchJob?.cancel()
        serviceFetchJob = lifecycleScope.launch {
            try {
                val serviceItems = withContext(Dispatchers.IO) {
                    fetchServiceRates(hotelId)
                }
                val currentSelections = serviceAdapter.getServiceItems()
                serviceAdapter.replaceItems(currentRoomFeeItems + currentSelections)
            } catch (e: Exception) {
                val currentSelections = serviceAdapter.getServiceItems()
                serviceAdapter.replaceItems(currentRoomFeeItems + currentSelections)
                Toast.makeText(this@ServiceExtraFeeActivity, "Failed to load service prices", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private data class ServiceRateEntry(
        val serviceId: String?,
        val serviceIcon: String?,
        val serviceName: String?,
        val price: Double
    ) {
    }

    private suspend fun fetchServiceRates(hotelId: String): List<ServiceFeeAdapter.ServiceItem> {
        val docs = mutableListOf<DocumentSnapshot>()
        android.util.Log.d("ServiceExtraFee", "Fetching serviceRates for hotelId=$hotelId")
        val collection = firestore.collection("serviceRates")
        val serviceRef = firestore.collection("services")
        val queries = mutableListOf<com.google.firebase.firestore.Query>()
        queries += collection.whereEqualTo("hotelId", hotelId)

        for (query in queries) {
            val snap = query.get().await()
            if (!snap.isEmpty) {
                docs.addAll(snap.documents)
                break
            }
        }

        val docCount = docs.size
        android.util.Log.d("ServiceExtraFee", "Found $docCount serviceRate docs for hotelId=$hotelId")
        if (docs.isEmpty()) {
            return emptyList()
        }

        val entries = docs.mapNotNull { doc ->
            val price = when (val raw = doc.get("price")) {
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull()
                else -> null
            } ?: return@mapNotNull null

            val serviceId = doc.getString("service_id")
            val serviceName = serviceRef.document(serviceId ?: "").get().await().getString("service_name")
            val serviceIcon = serviceRef.document(serviceId ?: "").get().await().getString("iconUrl")


            android.util.Log.d(
                "ServiceExtraFee",
                "serviceRate doc=${doc.id} hotelId=$hotelId service_id=$serviceId price=$price name=$serviceName"
            )
            ServiceRateEntry(serviceId,serviceIcon, serviceName, price)
        }
        if (entries.isEmpty()) return emptyList()

        return entries.map { entry ->
            entry.serviceIcon?.let { entry.serviceName?.let { it1 ->
                ServiceFeeAdapter.ServiceItem(it,
                    it1, entry.price)
            } }!!
        }
    }
//

    private fun computeNights(checkInMillis: Long, checkOutMillis: Long): Int =
        FormatUtils.computeNights(checkInMillis, checkOutMillis)

    private fun extractServiceId(
        doc: DocumentSnapshot,
        serviceRef: DocumentReference?
    ): String? {
        return doc.getString("service_id")
            ?: doc.getString("service_id".uppercase())
            ?: doc.get("service_id")?.let { (it as? Number)?.toLong()?.toString() }
            ?: serviceRef?.id
            ?: doc.getString("service")
            ?: doc.getString("serviceRefId")
    }

    private fun applyRoomFees(roomFees: List<ServiceFeeAdapter.ServiceItem>) {
        currentRoomFeeItems = roomFees
        roomFeesLoaded = roomFees.isNotEmpty()
        val existingServices = serviceAdapter.getServiceItems()
        serviceAdapter.replaceItems(currentRoomFeeItems + existingServices)
    }

    private fun mergeServiceSelections(
        newItems: List<ServiceFeeAdapter.ServiceItem>,
        previousSelections: List<ServiceFeeAdapter.ServiceItem>
    ): List<ServiceFeeAdapter.ServiceItem> {
        val previousMap = previousSelections.associateBy { it.name.lowercase() }
        return newItems.map { item ->
            val prev = previousMap[item.name.lowercase()]
            if (prev != null) item.copy(checked = prev.checked) else item
        }
    }

    private fun loadRoomFees(bookingId: String) {
        lifecycleScope.launch {
            val roomFees = withContext(Dispatchers.IO) {
                runCatching { fetchRoomFees(bookingId) }.getOrElse { emptyList() }
            }
            applyRoomFees(roomFees)
        }
    }

    private fun loadRoomFeesThenServices(bookingId: String, hotelId: String) {
        lifecycleScope.launch {
            val roomFees = withContext(Dispatchers.IO) {
                runCatching { fetchRoomFees(bookingId) }.getOrElse { emptyList() }
            }
            android.util.Log.d("ServiceExtraFee", "Loaded ${roomFees.size} room fees")
            applyRoomFees(roomFees)

            // Then load services
            try {
                val serviceItems = withContext(Dispatchers.IO) {
                    fetchServiceRates(hotelId)
                }
                android.util.Log.d("ServiceExtraFee", "Loaded ${serviceItems.size} service items")
                val currentSelections = serviceAdapter.getServiceItems()
                val mergedServices = mergeServiceSelections(serviceItems, currentSelections)
                serviceAdapter.replaceItems(currentRoomFeeItems + mergedServices)
            } catch (e: Exception) {
                android.util.Log.e("ServiceExtraFee", "Error loading services", e)
                val currentSelections = serviceAdapter.getServiceItems()
                serviceAdapter.replaceItems(currentRoomFeeItems + currentSelections)
                Toast.makeText(this@ServiceExtraFeeActivity, "Failed to load service prices", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadRoomFeesThenResolveHotel(bookingId: String) {
        lifecycleScope.launch {
            val roomFees = withContext(Dispatchers.IO) {
                runCatching { fetchRoomFees(bookingId) }.getOrElse { emptyList() }
            }
            applyRoomFees(roomFees)

            // Then resolve hotel and load services
            val hotelId = withContext(Dispatchers.IO) {
                runCatching { fetchHotelIdFromBooking(bookingId) }.getOrNull()
            }
            if (!hotelId.isNullOrBlank()) {
                try {
                    val serviceItems = withContext(Dispatchers.IO) {
                        fetchServiceRates(hotelId)
                    }
                    val currentSelections = serviceAdapter.getServiceItems()
                    val mergedServices = mergeServiceSelections(serviceItems, currentSelections)
                    serviceAdapter.replaceItems(currentRoomFeeItems + mergedServices)
                } catch (e: Exception) {
                    val currentSelections = serviceAdapter.getServiceItems()
                    serviceAdapter.replaceItems(currentRoomFeeItems + currentSelections)
                    Toast.makeText(this@ServiceExtraFeeActivity, "Failed to load service prices", Toast.LENGTH_SHORT).show()
                }
            } else {
                val currentSelections = serviceAdapter.getServiceItems()
                serviceAdapter.replaceItems(currentRoomFeeItems + currentSelections)
            }
        }
    }

    private suspend fun fetchRoomFees(bookingId: String): List<ServiceFeeAdapter.ServiceItem> {
        val invoiceDoc = findInvoiceDocument(bookingId)
        val invoiceFees = if (invoiceDoc != null && invoiceDoc.get("roomFees") is Map<*, *>) {
            android.util.Log.d("ServiceExtraFee", "Found roomFees in invoice ${invoiceDoc.id}")
            invoiceDoc.get("roomFees") as Map<*, *>
        } else {
            emptyMap<Any, Any>()
        }

        val sourceMap = if (invoiceFees.isNotEmpty()) {
            invoiceFees
        } else {
            android.util.Log.d("ServiceExtraFee", "Invoice missing roomFees; using cleaner data for bookingId=$bookingId")
            fetchRoomFeesFromCleaner(bookingId)
        }

        return buildRoomFeeItems(sourceMap)
    }

    private suspend fun findInvoiceDocument(bookingId: String): com.google.firebase.firestore.DocumentSnapshot? {
        val invoices = firestore.collection("invoices")
        val direct = invoices.document(bookingId).get().await()
        if (direct.exists()) return direct
        val query = invoices.whereEqualTo("bookingId", bookingId).limit(1).get().await()
        return query.documents.firstOrNull()
    }

    private suspend fun fetchRoomFeesFromCleaner(bookingId: String): Map<*, *> {
        val snapshot = firestore.collection("bookings")
            .document(bookingId)
            .collection("cleaner")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
        val cleanerDoc = snapshot.documents.firstOrNull()
        return cleanerDoc?.get("roomFees") as? Map<*, *> ?: emptyMap<Any, Any>()
    }

    private fun buildRoomFeeItems(roomFees: Map<*, *>): List<ServiceFeeAdapter.ServiceItem> {
        val items = mutableListOf<ServiceFeeAdapter.ServiceItem>()

        fun asDouble(raw: Any?): Double? = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        }
        fun asInt(raw: Any?): Int? = when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }

        val iconDefault = defaultRoomFeeIcon()

        val lostItems = roomFees["lostItems"] as? List<*> ?: emptyList<Any>()
        lostItems.forEach { item ->
            val map = item as? Map<*, *> ?: return@forEach
            val icon = (map["iconUrl"] as? String).orEmpty().ifBlank { iconDefault }
            val name = map["name"] as? String ?: return@forEach
            val price = asDouble(map["pricePerItem"]) ?: 0.0
            val qty = asInt(map["quantity"]) ?: 1
            val total = price * qty
            if (total > 0) {
                items.add(ServiceFeeAdapter.ServiceItem(
                    iconRes = icon,
                    name = "$name (x$qty)",
                    price = total,
                    checked = true,
                    isReadOnly = true
                ))
            }
        }

        val brokenItems = roomFees["brokenItems"] as? List<*> ?: emptyList<Any>()
        brokenItems.forEach { item ->
            val map = item as? Map<*, *> ?: return@forEach
            val icon = (map["iconUrl"] as? String).orEmpty().ifBlank { iconDefault }
            val name = map["name"] as? String ?: return@forEach
            val price = asDouble(map["pricePerItem"]) ?: 0.0
            if (price > 0) {
                items.add(ServiceFeeAdapter.ServiceItem(
                    iconRes = icon,
                    name = name,
                    price = price,
                    checked = true,
                    isReadOnly = true
                ))
            }
        }

        val miniBarItems = roomFees["miniBarItems"] as? List<*> ?: emptyList<Any>()
        miniBarItems.forEach { item ->
            val map = item as? Map<*, *> ?: return@forEach
            val icon = (map["iconUrl"] as? String).orEmpty().ifBlank { iconDefault }
            val name = map["name"] as? String ?: return@forEach
            val price = asDouble(map["pricePerItem"]) ?: 0.0
            val qty = asInt(map["quantity"]) ?: 1
            val total = price * qty
            if (total > 0) {
                items.add(ServiceFeeAdapter.ServiceItem(
                    iconRes = icon,
                    name = "$name (x$qty)",
                    price = total,
                    checked = true,
                    isReadOnly = true
                ))
            }
        }

        return items
    }

    private fun defaultRoomFeeIcon(): String =
        "android.resource://$packageName/${R.drawable.ic_service_roomsvc}"

    private fun resolveHotelIdFromBooking(bookingId: String) {
        lifecycleScope.launch {
            val hotelId = withContext(Dispatchers.IO) {
                runCatching { fetchHotelIdFromBooking(bookingId) }.getOrNull()
            }
            if (!hotelId.isNullOrBlank()) {
                try {
                    val serviceItems = withContext(Dispatchers.IO) {
                        fetchServiceRates(hotelId)
                    }
                    val currentSelections = serviceAdapter.getServiceItems()
                    val mergedServices = mergeServiceSelections(serviceItems, currentSelections)
                    serviceAdapter.replaceItems(currentRoomFeeItems + mergedServices)
                } catch (e: Exception) {
                    val currentSelections = serviceAdapter.getServiceItems()
                    serviceAdapter.replaceItems(currentRoomFeeItems + currentSelections)
                    Toast.makeText(this@ServiceExtraFeeActivity, "Failed to load service prices", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun fetchHotelIdFromBooking(bookingId: String): String? {
        val bookingDoc = firestore.collection("bookings").document(bookingId).get().await()
        if (bookingDoc.exists()) {

            bookingDoc.getString("hotelId")?.let { return it }
        }
        val query = firestore.collection("bookings")
            .whereEqualTo("reservationId", bookingId)
            .limit(1)
            .get()
            .await()
        val snapshot = query.documents.firstOrNull() ?: return null
        return snapshot.getString("hotelId") ?: snapshot.getString("hotelId")
    }
}


