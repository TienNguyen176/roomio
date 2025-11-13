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
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
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
    private lateinit var tvCleaningFee: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var tvResId: TextView
    private lateinit var tvGuestName: TextView
    private lateinit var tvCheckIn: TextView
    private lateinit var tvCheckOut: TextView
    private lateinit var tvReservationAmount: TextView
    private lateinit var tvNightPeople: TextView
    private lateinit var tvExtraFee: TextView
    private lateinit var tvTaxFee: TextView
    private lateinit var tvDiscountView: TextView
    private lateinit var tvGuestPay: TextView
    private lateinit var tvGrandTotal: TextView
    private lateinit var serviceAdapter: ServiceFeeAdapter
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var cleaningFeeBase: Double = 0.0
    private var serviceFetchJob: Job? = null
    private var reservationAmount: Double = 0.0
    private var discountLabel: String = "-"
    private var guestsCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_extra_fee)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        tvCleaningFee = findViewById(R.id.tvCleaningFee)
        tvTotalAmount = findViewById(R.id.tvTotalAmount)
        tvResId = findViewById(R.id.tvResId)
        tvGuestName = findViewById(R.id.tvGuestName)
        tvCheckIn = findViewById(R.id.tvCheckIn)
        tvCheckOut = findViewById(R.id.tvCheckOut)
        tvReservationAmount = findViewById(R.id.tvReservationAmount)
        tvNightPeople = findViewById(R.id.tvNightPeople)
        tvExtraFee = findViewById(R.id.tvExtraFee)
        tvTaxFee = findViewById(R.id.tvTaxFee)
        tvDiscountView = findViewById(R.id.tvDiscount)
        tvGuestPay = findViewById(R.id.tvGuestPay)
        tvGrandTotal = findViewById(R.id.tvGrandTotal)

        val roomId = intent.getStringExtra("ROOM_ID") ?: ""
        val cleaning = CleanerTaskRepository.getCleaningFee(roomId)
        tvCleaningFee.text = String.format("Total cleaning fee: %,.0fVND", cleaning)
        cleaningFeeBase = cleaning

        // Populate guest/reservation info
        val resId = intent.getStringExtra("RESERVATION_ID") ?: ""
        val guestName = intent.getStringExtra("GUEST_NAME") ?: ""
        val rawCheckIn = intent.getStringExtra("CHECK_IN_TEXT") ?: intent.getStringExtra("CHECK_IN") ?: ""
        val rawCheckOut = intent.getStringExtra("CHECK_OUT_TEXT") ?: intent.getStringExtra("CHECK_OUT") ?: ""
        val checkInMillis = intent.getLongExtra("CHECK_IN_MILLIS", -1L)
        val checkOutMillis = intent.getLongExtra("CHECK_OUT_MILLIS", -1L)
        val roomTypeName = intent.getStringExtra("ROOM_TYPE_NAME") ?: intent.getStringExtra("ROOM_TYPE") ?: ""
        val amount = intent.getDoubleExtra("RESERVATION_AMOUNT", 0.0)
        val discountLabel = intent.getStringExtra("DISCOUNT_LABEL") ?: "-"
        val guestsCount = intent.getIntExtra("GUESTS_COUNT", 0)
        this.reservationAmount = amount
        this.discountLabel = discountLabel
        this.guestsCount = guestsCount

        val checkInTxt = if (rawCheckIn.isNotBlank()) rawCheckIn else formatDateTimeOrEmpty(checkInMillis)
        val checkOutTxt = if (rawCheckOut.isNotBlank()) rawCheckOut else formatDateTimeOrEmpty(checkOutMillis)
        val nightCount = computeNights(checkInMillis, checkOutMillis)

        tvResId.text = if (resId.isNotEmpty()) "Reservation ID: $resId" else ""
        if (guestName.isNotEmpty()) tvGuestName.text = "Guest name: $guestName" else tvGuestName.text = ""
        if (checkInTxt.isNotEmpty()) tvCheckIn.text = "Check-in: $checkInTxt" else tvCheckIn.text = ""
        if (checkOutTxt.isNotEmpty()) tvCheckOut.text = "Check-out: $checkOutTxt" else tvCheckOut.text = ""
        tvReservationAmount.text = String.format("Reservation amount: %,.0fVND", amount)

        val nightLabel = when {
            nightCount <= 0 -> "-"
            nightCount == 1 -> "1 night"
            else -> "$nightCount nights"
        }
        val guestLabel = if (guestsCount > 0) "$guestsCount people" else "-"
        tvNightPeople.text = "Night: $nightLabel - $guestLabel"

        refreshPriceSummary(0.0)

        val rv = findViewById<RecyclerView>(R.id.rvServices)
        rv.layoutManager = LinearLayoutManager(this)
        serviceAdapter = ServiceFeeAdapter(mutableListOf()) { extra -> updateTotals(cleaningFeeBase, extra) }
        rv.adapter = serviceAdapter

        findViewById<MaterialButton>(R.id.btnNext).setOnClickListener {
            val nextIntent = android.content.Intent(this, PaymentDetailsActivity::class.java)
            val incoming = intent
            // forward guest/reservation info if present
            nextIntent.putExtra("GUEST_NAME", incoming.getStringExtra("GUEST_NAME") ?: "")
            nextIntent.putExtra("GUEST_PHONE", incoming.getStringExtra("GUEST_PHONE") ?: "")
            nextIntent.putExtra("GUEST_EMAIL", incoming.getStringExtra("GUEST_EMAIL") ?: "")
            nextIntent.putExtra("RESERVATION_ID", resId)
            nextIntent.putExtra("CHECK_IN_TEXT", checkInTxt)
            nextIntent.putExtra("CHECK_OUT_TEXT", checkOutTxt)
            nextIntent.putExtra("CHECK_IN_MILLIS", checkInMillis)
            nextIntent.putExtra("CHECK_OUT_MILLIS", checkOutMillis)
            nextIntent.putExtra("ROOM_TYPE", roomTypeName)
            nextIntent.putExtra("NIGHTS_COUNT", nightCount)
            nextIntent.putExtra("ROOM_PRICE", amount)
            nextIntent.putExtra("EXTRA_FEE", lastExtraTotal)
            nextIntent.putExtra("CLEANING_FEE", cleaning)
            nextIntent.putExtra("TAX_FEE", 0.0)
            nextIntent.putExtra("DISCOUNT_TEXT", discountLabel)
            nextIntent.putExtra("GUESTS_COUNT", guestsCount)
            startActivity(nextIntent)
        }

        val hotelId = intent.getStringExtra("HOTEL_ID")?.takeIf { it.isNotBlank() }
        val bookingId = intent.getStringExtra("BOOKING_ID")?.takeIf { it.isNotBlank() }

        when {
            hotelId != null -> loadServiceRates(hotelId)
            bookingId != null -> resolveHotelIdFromBooking(bookingId)
            else -> updateTotals(cleaningFeeBase, serviceAdapter.currentTotal())
        }

        updateTotals(cleaningFeeBase, serviceAdapter.currentTotal())
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceFetchJob?.cancel()
    }

    private fun updateTotals(cleaningFee: Double, extra: Double) {
        cleaningFeeBase = cleaningFee
        refreshPriceSummary(extra)
    }

    private var lastExtraTotal: Double = 0.0

    private fun formatDateTimeOrEmpty(millis: Long): String {
        if (millis <= 0L) return ""
        return try {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))
        } catch (_: Exception) {
            Date(millis).toString()
        }
    }

    private fun formatCurrency(value: Double): String = String.format("%,.0fVND", value)

    private fun refreshPriceSummary(extraFee: Double) {
        lastExtraTotal = extraFee
        val taxFee = 0.0
        val total = reservationAmount + extraFee + cleaningFeeBase + taxFee

        findViewById<TextView>(R.id.tvRoomPrice)?.text = "Room price/night: ${formatCurrency(reservationAmount)}"
        tvExtraFee.text = "Extra fee: ${formatCurrency(lastExtraTotal)}"
        tvTaxFee.text = "Tax & service fee: ${formatCurrency(taxFee)}"
        tvCleaningFee.text = "Cleaning fee: ${formatCurrency(cleaningFeeBase)}"
        tvDiscountView.text = "Discount/ voucher: $discountLabel"
        tvGuestPay.text = "Guest pay: ${formatCurrency(total)}"
        tvTotalAmount.text = "Total amount: ${formatCurrency(total)}"
        tvGrandTotal.text = "Total amount: ${formatCurrency(total)}"
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
        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    val rates = fetchServiceRates(hotelId)
                    if (rates.isNotEmpty()) rates else fetchHotelServices(hotelId)
                }
                if (items.isNotEmpty()) {
                    serviceAdapter.replaceItems(items.toMutableList())
                    updateTotals(cleaningFeeBase, serviceAdapter.currentTotal())
                } else {
                    serviceAdapter.replaceItems(mutableListOf())
                    refreshPriceSummary(0.0)
                }
            } catch (e: Exception) {
                serviceAdapter.replaceItems(mutableListOf())
                refreshPriceSummary(0.0)
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
                updateTotals(cleaningFeeBase, serviceAdapter.currentTotal())
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


