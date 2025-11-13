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
    private lateinit var tvExtraFee: TextView
    private lateinit var tvGrandTotal: TextView
    private lateinit var serviceAdapter: ServiceFeeAdapter
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var serviceFetchJob: Job? = null
    private var reservationAmount: Double = 0.0
    private var extraTotal: Double = 0.0
    private var lastExtraTotal: Double = 0.0
    private var discountLabel: String = "-"
    private var guestsCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_extra_fee)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        tvResId = findViewById(R.id.tvResId)
        tvGuestName = findViewById(R.id.tvGuestName)
        tvCheckIn = findViewById(R.id.tvCheckIn)
        tvCheckOut = findViewById(R.id.tvCheckOut)
        tvReservationAmount = findViewById(R.id.tvReservationAmount)
//        tvCleaningFee = findViewById(R.id.tvCleaningFee)
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

        val cleaning = CleanerTaskRepository.getCleaningFee(roomId)
        updateCleaningFee(cleaning)
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
            nextIntent.putExtra("CLEANING_FEE", cleaning)
            nextIntent.putExtra("DISCOUNT_TEXT", discountLabel)
            nextIntent.putExtra("GUESTS_COUNT", guestsCount)
            nextIntent.putExtra("BOOKING_ID", bookingId ?: "")
            nextIntent.putExtra("HOTEL_ID", hotelId ?: "")
            startActivity(nextIntent)
            finish()
            btnNext.postDelayed({ btnNext.isEnabled = true }, 600)
        }

        when {
            hotelId != null -> loadServiceRates(hotelId)
            bookingId != null -> resolveHotelIdFromBooking(bookingId)
            else -> serviceAdapter.replaceItems(emptyList())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceFetchJob?.cancel()
    }

    private fun updateCleaningFee(value: Double) {

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
        val grandTotal = reservationAmount + extraTotal
        tvGrandTotal.text = "Total amount: ${formatCurrency(grandTotal)}"
    }

    private fun iconForService(name: String): Int = when {
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

    private fun loadServiceRates(hotelId: String) {
        serviceFetchJob?.cancel()
        serviceFetchJob = lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    val rates = fetchServiceRates(hotelId)
                    if (rates.isNotEmpty()) rates else fetchHotelServices(hotelId)
                }
                serviceAdapter.replaceItems(items)
            } catch (e: Exception) {
                serviceAdapter.replaceItems(emptyList())
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

    private suspend fun fetchServiceRates(hotelId: String): List<ServiceFeeAdapter.ServiceItem> {
        val docs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
        val collection = firestore.collection("serviceRates")

        val hotelIdNumber = hotelId.toLongOrNull()
        val hotelDocRef = firestore.collection("hotels").document(hotelId)

        val queries = mutableListOf<com.google.firebase.firestore.Query>()
        queries += collection.whereEqualTo("hotel_id", hotelId)
        queries += collection.whereEqualTo("hotelId", hotelId)
        if (hotelIdNumber != null) {
            queries += collection.whereEqualTo("hotel_id", hotelIdNumber)
            queries += collection.whereEqualTo("hotelId", hotelIdNumber)
        }
        queries += collection.whereEqualTo("hotelRef", hotelDocRef)

        for (query in queries) {
            val snap = query.get().await()
            if (!snap.isEmpty) {
                docs.addAll(snap.documents)
                break
            }
        }

        if (docs.isEmpty()) return emptyList()

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
                ?: doc.getString("serviceName")
                ?: doc.getString("service")
            ServiceRateEntry(serviceId, serviceRef, servicePath, serviceName, price)
        }
        if (entries.isEmpty()) return emptyList()

        val nameLookup = fetchServiceNames(hotelId, entries)
        return entries.map { entry ->
            val resolvedName = entry.serviceName
                ?: entry.serviceRef?.let { nameLookup[it.path] ?: nameLookup[it.id] }
                ?: entry.serviceRefPath?.let { path ->
                    nameLookup[path] ?: nameLookup[path.substringAfterLast('/')] ?: nameLookup[path.substringBefore('/')] }
                ?: entry.serviceId?.let { nameLookup[it] }
                ?: entry.serviceId
                ?: entry.serviceName
                ?: "Unknown service"
            ServiceFeeAdapter.ServiceItem(iconForService(resolvedName), resolvedName, entry.price)
        }
    }

    private suspend fun fetchHotelServices(hotelId: String): List<ServiceFeeAdapter.ServiceItem> {
        val snap = firestore.collection("hotels")
            .document(hotelId)
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
        hotelId: String,
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
            }
        }

        if (idSet.isNotEmpty()) {
            idSet.chunked(10).forEach { chunk ->
                val snap = firestore.collection("services")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .await()
                addDocs(snap.documents)
            }

            idSet.chunked(10).forEach { chunk ->
                val hotelSnap = firestore.collection("hotels")
                    .document(hotelId)
                    .collection("services")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .await()
                addDocs(hotelSnap.documents)
            }
        }

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

    private fun resolveHotelIdFromBooking(bookingId: String) {
        lifecycleScope.launch {
            val hotelId = withContext(Dispatchers.IO) {
                runCatching { fetchHotelIdFromBooking(bookingId) }.getOrNull()
            }
            if (!hotelId.isNullOrBlank()) {
                loadServiceRates(hotelId)
            } else {
                serviceAdapter.replaceItems(emptyList())
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
        return snapshot.getString("hotelId")
    }
}


