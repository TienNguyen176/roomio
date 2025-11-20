package com.tdc.nhom6.roomio.activities

import android.os.Bundle
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
import com.google.firebase.firestore.FieldPath

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.ServiceFeeAdapter
import com.tdc.nhom6.roomio.data.CleanerTaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

class ServiceExtraFeeActivity : AppCompatActivity() {
    private lateinit var tvResId: TextView
    private lateinit var tvGuestName: TextView
    private lateinit var tvCheckIn: TextView
    private lateinit var tvCheckOut: TextView
    private lateinit var tvReservationAmount: TextView
    private lateinit var tvPaidAmount: TextView
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
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        tvResId = findViewById(R.id.tvResId)
        tvGuestName = findViewById(R.id.tvGuestName)
        tvCheckIn = findViewById(R.id.tvCheckIn)
        tvCheckOut = findViewById(R.id.tvCheckOut)
        tvReservationAmount = findViewById(R.id.tvReservationAmount)
        tvPaidAmount = findViewById(R.id.tvPaidAmount)
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
            val cleaningForPayment = if (roomFeesLoaded) 0.0 else cleaningFee
            nextIntent.putExtra("CLEANING_FEE", cleaningForPayment)
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
        refreshPriceSummary()
    }

    private fun updateExtraTotal(total: Double) {
        extraTotal = total
        tvExtraFee.text = "Total additional charges: ${formatCurrency(extraTotal)}"
        refreshPriceSummary()
    }

    private fun formatDateTimeOrEmpty(millis: Long): String {
        if (millis <= 0L) return ""
        return try {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))
        } catch (_: Exception) {
            Date(millis).toString()
        }
    }

    private fun formatCurrency(value: Double): String = String.format("%,.0fVND", value)

    private fun refreshPriceSummary() {
        lastExtraTotal = extraTotal
        val effectiveCleaningFee = if (roomFeesLoaded) 0.0 else cleaningFee
        // Grand total = (reservation amount + extra fees + cleaning fee) - paid amount
        val grandTotal = max(0.0, reservationAmount + extraTotal + effectiveCleaningFee - paidAmount)
        tvGrandTotal.text = "Total amount: ${formatCurrency(grandTotal)}"
    }
    
    private fun observeInvoices(bookingId: String, reservationId: String) {
        invoiceListener?.remove()
        
        // Try multiple field names and formats for bookingId
        val bookingIdInt = bookingId.toIntOrNull()
        val reservationIdInt = reservationId.toIntOrNull()
        
        val queries = mutableListOf<com.google.firebase.firestore.Query>()
        
        // Try bookingId as String
        queries += firestore.collection("invoices").whereEqualTo("bookingId", bookingId)

        
        // Try bookingId as Int
        if (bookingIdInt != null) {
            queries += firestore.collection("invoices").whereEqualTo("bookingId", bookingIdInt)

        }
        
        // Try reservationId as String
        queries += firestore.collection("invoices").whereEqualTo("reservationId", reservationId)

        
        // Try reservationId as Int
        if (reservationIdInt != null) {
            queries += firestore.collection("invoices").whereEqualTo("reservationId", reservationIdInt)

        }
        
        // Try using FieldPath for nested or alternative field names
        try {
            queries += firestore.collection("invoices").whereEqualTo(FieldPath.of("bookingRef", "id"), bookingId)
        } catch (_: Exception) {}
        
        // Try each query until one returns results
        var listenerSet = false
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
                                updatePaidAmount(snapshots)
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
                        // Filter by bookingId or reservationId in memory
                        val filtered = snapshots.documents.filter { doc ->
                            val docBookingId = doc.get("bookingId")?.toString()
                                ?: doc.get("booking_id")?.toString()
                                ?: doc.get("bookingDocId")?.toString()
                                ?: doc.get("booking_doc_id")?.toString()
                            val docReservationId = doc.get("reservationId")?.toString()
                                ?: doc.get("reservation_id")?.toString()
                            
                            docBookingId == bookingId || docReservationId == reservationId ||
                            docBookingId?.toIntOrNull() == bookingIdInt ||
                            docReservationId?.toIntOrNull() == reservationIdInt
                        }
                        if (filtered.isNotEmpty()) {
                            updatePaidAmountFromDocs(filtered)
                        } else {
                            // No invoices found, set paid amount to 0
                            paidAmount = 0.0
                            tvPaidAmount.text = "Paid amount: ${formatCurrency(paidAmount)}"
                            refreshPriceSummary()
                        }
                    }
                }
        }
    }
    
    private fun updatePaidAmount(snapshots: com.google.firebase.firestore.QuerySnapshot) {
        updatePaidAmountFromDocs(snapshots.documents)
    }
    
    private fun updatePaidAmountFromDocs(docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
        var total = 0.0
        for (doc in docs) {
            val amount = when (val totalAmount = doc.get("totalAmount")) {
                is Number -> totalAmount.toDouble()
                is java.math.BigDecimal -> totalAmount.toDouble()
                is String -> totalAmount.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
            total += amount
        }
        paidAmount = total
        tvPaidAmount.text = "Paid amount: ${formatCurrency(paidAmount)}"
        refreshPriceSummary()
    }
    
    private fun observeCleaningFee(bookingId: String) {
        cleaningFeeListener?.remove()
        cleaningFeeListener = firestore.collection("bookings")
            .document(bookingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("ServiceExtraFee", "Cleaning fee listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val fee = when (val raw = snapshot.get("cleaningFee")) {
                        is Number -> raw.toDouble()
                        is String -> raw.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    if (fee > 0.0) {
                        cleaningFee = fee
                        updateCleaningFee(cleaningFee)
                    }
                    // Reload room fees when booking is updated
                    lifecycleScope.launch {
                        val roomFees = withContext(Dispatchers.IO) {
                            runCatching { fetchRoomFees(bookingId) }.getOrElse { emptyList() }
                        }
                        applyRoomFees(roomFees)
                    }
                }
            }
    }

    private fun iconForService(name: String): Int = when {
        name.contains("lost", true) -> R.drawable.ic_service_roomsvc // Placeholder for lost items
        name.contains("broken", true) -> R.drawable.ic_service_roomsvc // Placeholder for broken furniture
        name.contains("mini bar", true) || name.contains("minibar", true) -> R.drawable.ic_service_roomsvc // Placeholder for mini bar
        name.contains("tour", true) -> R.drawable.ic_service_tour
        name.contains("laundry", true) -> R.drawable.ic_service_laundry
        name.contains("spa", true) || name.contains("gym", true) -> R.drawable.ic_service_spa
        name.contains("airport", true) -> R.drawable.ic_service_airport
        name.contains("meeting", true) -> R.drawable.ic_service_meeting
        name.contains("pet", true) -> R.drawable.ic_service_pet
        name.contains("upgrade", true) -> R.drawable.ic_service_upgrade
        name.contains("meal", true) || name.contains("dinner", true) -> R.drawable.ic_service_meal
        name.contains("delivery", true) -> R.drawable.ic_service_delivery
        name.contains("decoration", true) -> R.drawable.ic_service_decoration
        name.contains("room", true) && name.contains("service", true) -> R.drawable.ic_service_roomsvc
        else -> R.drawable.ic_service_roomsvc
    }

    private fun loadServiceRates(hotel_id: String) {
        serviceFetchJob?.cancel()
        serviceFetchJob = lifecycleScope.launch {
            try {
                val serviceItems = withContext(Dispatchers.IO) {
                    val rates = fetchServiceRates(hotel_id)
                    if (rates.isNotEmpty()) rates else fetchHotelServices(hotel_id)
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

    private data class ServiceRateEntry(
        val serviceId: String?,
        val serviceRef: DocumentReference?,
        val serviceRefPath: String?,
        val serviceName: String?,
        val price: Double
    )

    private suspend fun fetchServiceRates(hotel_id: String): List<ServiceFeeAdapter.ServiceItem> {
        val docs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
        android.util.Log.d("ServiceExtraFee", "Fetching serviceRates for hotel_id=$hotel_id")
        val collection = firestore.collection("serviceRates")

        val hotelIdNumber = hotel_id.toLongOrNull()
        val hotelDocRef = firestore.collection("hotels").document(hotel_id)

        val queries = mutableListOf<com.google.firebase.firestore.Query>()
        queries += collection.whereEqualTo("hotel_id", hotel_id)
        if (hotelIdNumber != null) {
            queries += collection.whereEqualTo("hotel_id", hotelIdNumber)
        }
        queries += collection.whereEqualTo("hotelRef", hotelDocRef)
        queries += collection.whereEqualTo("hotelRef.id", hotel_id)

        for (query in queries) {
            val snap = query.get().await()
            if (!snap.isEmpty) {
                docs.addAll(snap.documents)
                break
            }
        }

        val docCount = docs.size
        android.util.Log.d("ServiceExtraFee", "Found $docCount serviceRate docs for hotel_id=$hotel_id")
        if (docs.isEmpty()) {
            return emptyList()
        }

        val entries = docs.mapNotNull { doc ->
            val price = when (val raw = doc.get("price")) {
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull()
                else -> null
            } ?: return@mapNotNull null

            val serviceRefField = doc.get("serviceRef")
            val serviceRef = when (serviceRefField) {
                is DocumentReference -> serviceRefField
                is String -> if (serviceRefField.contains('/')) firestore.document(serviceRefField) else null
                else -> null
            }
            val servicePath = when (serviceRefField) {
                is String -> serviceRefField
                is DocumentReference -> serviceRefField.path
                else -> doc.getString("servicePath")
                    ?: doc.getString("service_ref")
                    ?: doc.getString("serviceRefPath")
            }

            val serviceId = extractServiceId(doc, serviceRef)
            val serviceName = doc.getString("service_name")
            android.util.Log.d(
                "ServiceExtraFee",
                "serviceRate doc=${doc.id} hotel_id=$hotel_id service_id=$serviceId price=$price name=$serviceName"
            )
            ServiceRateEntry(serviceId, serviceRef, servicePath, serviceName, price)
        }
        if (entries.isEmpty()) return emptyList()

        val nameLookup = fetchServiceNames(hotel_id, entries)
        return entries.map { entry ->
            val resolvedName = entry.serviceName
                ?: entry.serviceRef?.let { nameLookup[it.path] ?: nameLookup[it.id] }
                ?: entry.serviceRefPath?.let { path ->
                    nameLookup[path] ?: nameLookup[path.substringAfterLast('/')] ?: nameLookup[path.substringBefore('/')] }
                ?: entry.serviceId?.let { nameLookup[it] }
                ?: entry.serviceId
                ?: entry.serviceName
                ?: "Service"
            ServiceFeeAdapter.ServiceItem(iconForService(resolvedName), resolvedName, entry.price)
        }
    }

    private suspend fun fetchHotelServices(hotel_id: String): List<ServiceFeeAdapter.ServiceItem> {
        val snap = firestore.collection("hotels")
            .document(hotel_id)
            .collection("services")
            .get()
            .await()
        if (snap.isEmpty) return emptyList()
        return snap.documents.mapNotNull { doc ->
            val name = doc.getString("service_name")
                ?: doc.getString("name")
                ?: doc.getString("service")
                ?: return@mapNotNull null
            val price = (
                when (val raw = doc.get("price")) {
                    is Number -> raw.toDouble()
                    is String -> raw.toDoubleOrNull()
                    else -> null
                }
                    ?: when (val raw = doc.get("servicePrice")) {
                        is Number -> raw.toDouble()
                        is String -> raw.toDoubleOrNull()
                        else -> null
                    }
                    ?: when (val raw = doc.get("amount")) {
                        is Number -> raw.toDouble()
                        is String -> raw.toDoubleOrNull()
                        else -> null
                    }
                    ?: when (val raw = doc.get("cost")) {
                        is Number -> raw.toDouble()
                        is String -> raw.toDoubleOrNull()
                        else -> null
                    }
                    ?: when (val raw = doc.get("serviceRate")) {
                        is Number -> raw.toDouble()
                        is String -> raw.toDoubleOrNull()
                        else -> null
                    }
                    ?: doc.getString("servicePrice")?.toDoubleOrNull()
                    ?: 0.0
                )
            ServiceFeeAdapter.ServiceItem(iconForService(name), name, price)
        }
    }

    private suspend fun fetchServiceNames(
        hotel_id: String,
        entries: List<ServiceRateEntry>
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()

        entries.forEach { entry ->
            entry.serviceName?.let { explicitName ->
                entry.serviceRef?.let { result[it.path] = explicitName; result[it.id] = explicitName }
                entry.serviceRefPath?.let { result[it] = explicitName }
                entry.serviceId?.let { result[it] = explicitName }
            }
        }

        val idSet = entries.mapNotNull { it.serviceId }.toMutableSet()
        val refSet = entries.mapNotNull { it.serviceRef }.toMutableSet()
        val refPathSet = entries.mapNotNull { it.serviceRefPath }.toMutableSet()

        suspend fun addDocs(docs: List<DocumentSnapshot>) {
            for (doc in docs) {
                val name = doc.getString("service_name")
                    ?: doc.getString("name")
                    ?: doc.getString("service")
                    ?: doc.id
                result[doc.id] = name
                result[doc.reference.path] = name
                doc.getString("id")?.let { result[it] = name }
            }
        }

        suspend fun queryServices(
            collectionRef: com.google.firebase.firestore.CollectionReference,
            fieldPath: FieldPath
        ) {
            if (idSet.isEmpty()) return
            idSet.chunked(10).forEach { chunk ->
                val snap = collectionRef.whereIn(fieldPath, chunk).get().await()
                addDocs(snap.documents)
            }
        }

        suspend fun queryServices(
            collectionRef: com.google.firebase.firestore.CollectionReference,
            fieldName: String
        ) {
            if (idSet.isEmpty()) return
            idSet.chunked(10).forEach { chunk ->
                val snap = collectionRef.whereIn(fieldName, chunk).get().await()
                addDocs(snap.documents)
            }
        }

        val servicesRoot = firestore.collection("services")
        queryServices(servicesRoot, FieldPath.documentId())
        queryServices(servicesRoot, "id")
        queryServices(servicesRoot, "service_id")
        queryServices(servicesRoot, "serviceId")

        val hotelServices = firestore.collection("hotels").document(hotel_id).collection("services")
        queryServices(hotelServices, FieldPath.documentId())
        queryServices(hotelServices, "id")
        queryServices(hotelServices, "service_id")
        queryServices(hotelServices, "serviceId")

        for (ref in refSet) {
            val doc = ref.get().await()
            if (doc.exists()) addDocs(listOf(doc))
        }

        for (path in refPathSet) {
            if (result.containsKey(path)) continue
            runCatching {
                val doc = firestore.document(path).get().await()
                if (doc.exists()) addDocs(listOf(doc))
            }
        }

        return result
    }

    private fun computeNights(checkInMillis: Long, checkOutMillis: Long): Int {
        if (checkInMillis <= 0L || checkOutMillis <= 0L) return 0
        val diff = checkOutMillis - checkInMillis
        val dayMillis = TimeUnit.DAYS.toMillis(1)
        return if (diff <= 0) 1 else max(1, ((diff + dayMillis - 1) / dayMillis).toInt())
    }

    private fun extractServiceId(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        serviceRef: DocumentReference?
    ): String? {
        return doc.getString("service_id")
            ?: doc.getString("serviceId")
            ?: doc.getString("serviceID")
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

    private fun loadRoomFeesThenServices(bookingId: String, hotel_id: String) {
        lifecycleScope.launch {
            val roomFees = withContext(Dispatchers.IO) {
                runCatching { fetchRoomFees(bookingId) }.getOrElse { emptyList() }
            }
            android.util.Log.d("ServiceExtraFee", "Loaded ${roomFees.size} room fees")
            applyRoomFees(roomFees)
            
            // Then load services
            try {
                val serviceItems = withContext(Dispatchers.IO) {
                    val rates = fetchServiceRates(hotel_id)
                    if (rates.isNotEmpty()) rates else fetchHotelServices(hotel_id)
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
            val hotel_id = withContext(Dispatchers.IO) {
                runCatching { fetchHotelIdFromBooking(bookingId) }.getOrNull()
            }
            if (!hotel_id.isNullOrBlank()) {
                try {
                    val serviceItems = withContext(Dispatchers.IO) {
                        val rates = fetchServiceRates(hotel_id)
                        if (rates.isNotEmpty()) rates else fetchHotelServices(hotel_id)
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
        val roomFees = when {
            invoiceDoc != null && invoiceDoc.get("roomFees") is Map<*, *> -> {
                android.util.Log.d("ServiceExtraFee", "Found roomFees in invoice ${invoiceDoc.id}")
                invoiceDoc.get("roomFees") as Map<*, *>
            }
            else -> {
                val bookingDoc = firestore.collection("bookings").document(bookingId).get().await()
                val bookingRoomFees = bookingDoc.get("roomFees") as? Map<*, *>
                if (bookingRoomFees != null) {
                    android.util.Log.d("ServiceExtraFee", "Found roomFees in booking $bookingId")
                    bookingRoomFees
                } else {
                    android.util.Log.d("ServiceExtraFee", "No roomFees found in invoice/booking $bookingId")
                    return emptyList()
                }
            }
        }

        return buildRoomFeeItems(roomFees)
    }

    private suspend fun findInvoiceDocument(bookingId: String): com.google.firebase.firestore.DocumentSnapshot? {
        val invoices = firestore.collection("invoices")
        val direct = invoices.document(bookingId).get().await()
        if (direct.exists()) return direct
        val query = invoices.whereEqualTo("bookingId", bookingId).limit(1).get().await()
        return query.documents.firstOrNull()
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

        val lostItems = roomFees["lostItems"] as? List<*> ?: emptyList<Any>()
        lostItems.forEach { item ->
            val map = item as? Map<*, *> ?: return@forEach
            val name = map["name"] as? String ?: return@forEach
            val price = asDouble(map["pricePerItem"]) ?: 0.0
            val qty = asInt(map["quantity"]) ?: 1
            val total = price * qty
            if (total > 0) {
                items.add(ServiceFeeAdapter.ServiceItem(
                    iconRes = iconForService("lost items"),
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
            val name = map["name"] as? String ?: return@forEach
            val price = asDouble(map["pricePerItem"]) ?: 0.0
            if (price > 0) {
                items.add(ServiceFeeAdapter.ServiceItem(
                    iconRes = iconForService("broken items"),
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
            val name = map["name"] as? String ?: return@forEach
            val price = asDouble(map["pricePerItem"]) ?: 0.0
            val qty = asInt(map["quantity"]) ?: 1
            val total = price * qty
            if (total > 0) {
                items.add(ServiceFeeAdapter.ServiceItem(
                    iconRes = iconForService("mini bar"),
                    name = "$name (x$qty)",
                    price = total,
                    checked = true,
                    isReadOnly = true
                ))
            }
        }

        return items
    }

    private fun resolveHotelIdFromBooking(bookingId: String) {
        lifecycleScope.launch {
            val hotel_id = withContext(Dispatchers.IO) {
                runCatching { fetchHotelIdFromBooking(bookingId) }.getOrNull()
            }
            if (!hotel_id.isNullOrBlank()) {
                try {
                    val serviceItems = withContext(Dispatchers.IO) {
                        val rates = fetchServiceRates(hotel_id)
                        if (rates.isNotEmpty()) rates else fetchHotelServices(hotel_id)
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

            bookingDoc.getString("hotel_id")?.let { return it }
        }
        val query = firestore.collection("bookings")
            .whereEqualTo("reservationId", bookingId)
            .limit(1)
            .get()
            .await()
        val snapshot = query.documents.firstOrNull() ?: return null
        return snapshot.getString("hotel_id") ?: snapshot.getString("hotel_id")
    }
}


