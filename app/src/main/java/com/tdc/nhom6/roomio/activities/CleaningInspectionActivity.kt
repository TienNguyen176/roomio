package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.BrokenFurnitureAdapter
import com.tdc.nhom6.roomio.adapters.LostItemAdapter
import com.tdc.nhom6.roomio.adapters.MiniBarAdapter
import com.tdc.nhom6.roomio.data.CleanerTaskRepository
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

class CleaningInspectionActivity : AppCompatActivity() {
    private lateinit var tvReservationInfo: TextView
    private lateinit var tvTotalCharges: TextView
    private lateinit var tvReservationAmount: TextView

    private lateinit var lostAdapter: LostItemAdapter
    private lateinit var brokenAdapter: BrokenFurnitureAdapter
    private lateinit var miniBarAdapter: MiniBarAdapter
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cleaning_inspection)

        tvReservationInfo = findViewById(R.id.tvReservationInfo)
        tvReservationAmount = findViewById(R.id.tvReservationAmount)
        tvTotalCharges = findViewById(R.id.tvTotalCharges)

        // Back button
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Show basic room id until booking data loads
        val roomId = intent.getStringExtra("ROOM_ID") ?: ""
        tvReservationInfo.text = if (roomId.isNotEmpty()) "Room: $roomId" else getString(R.string.cleaning_reservation_placeholder)
        tvReservationAmount.text = getString(R.string.cleaning_amount_placeholder)

        // Setup lists
        val rvLost = findViewById<RecyclerView>(R.id.rvLostItems)
        val rvBroken = findViewById<RecyclerView>(R.id.rvBrokenFurniture)
        val rvMini = findViewById<RecyclerView>(R.id.rvMiniBar)

        rvLost.layoutManager = LinearLayoutManager(this)
        rvBroken.layoutManager = LinearLayoutManager(this)
        rvMini.layoutManager = LinearLayoutManager(this)

        lostAdapter = LostItemAdapter(defaultLostItems().toMutableList()) { updateTotal() }
        brokenAdapter = BrokenFurnitureAdapter(defaultBrokenItems().toMutableList()) { updateTotal() }
        miniBarAdapter = MiniBarAdapter(defaultMiniBarItems().toMutableList()) { updateTotal() }

        rvLost.adapter = lostAdapter
        rvBroken.adapter = brokenAdapter
        rvMini.adapter = miniBarAdapter

        // Done button - navigate to cleaner task detail screen
        findViewById<MaterialButton>(R.id.btnDone).setOnClickListener {
            // Post cleaning fee result back to repository for reception
            val fee = currentTotal()
            val id = intent.getStringExtra("ROOM_ID") ?: ""
            val bookingId = intent.getStringExtra("BOOKING_ID")?.takeIf { it.isNotBlank() }
            CleanerTaskRepository.postCleaningResult(id, fee)
            
            // Update booking with cleaning completed timestamp and cleaning fee to notify reception
            if (bookingId != null) {
                firestore.collection("bookings").document(bookingId)
                    .update(
                        mapOf(
                            "cleaningCompletedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                            "cleaningFee" to fee
                        )
                    )
                    .addOnFailureListener { e ->
                        android.util.Log.e("CleaningInspection", "Failed to update cleaningCompletedAt and cleaningFee", e)
                    }
            }
            
            // Navigate to cleaner task detail screen
            val detailIntent = Intent(this, CleanerTaskDetailActivity::class.java)
            detailIntent.putExtra("ROOM_ID", id)
            detailIntent.putExtra("BOOKING_ID", bookingId ?: "")
            // Get checkout time from task if available, otherwise use default
            val tasks = CleanerTaskRepository.tasks().value ?: emptyList()
            val task = tasks.find { it.roomId == id }
            val checkoutTime = "11.00 PM" // Default, can be enhanced later
            detailIntent.putExtra("CHECKOUT_TIME", checkoutTime)
            detailIntent.putExtra("NOTES", "Replace bedding and towels") // Default notes
            startActivity(detailIntent)
            finish()
        }

        updateTotal()

        val roomTypeId = intent.getStringExtra("ROOM_TYPE_ID")?.takeIf { it.isNotBlank() }
        val bookingId = intent.getStringExtra("BOOKING_ID")?.takeIf { it.isNotBlank() }

        if (bookingId != null) {
            observeBookingHeader(bookingId)
        }

        if (roomTypeId != null) {
            loadDamageRates(roomTypeId)
        } else if (bookingId != null) {
            resolveRoomTypeFromBooking(bookingId)
        }
    }

    private var bookingListener: ListenerRegistration? = null

    private fun observeBookingHeader(bookingId: String) {
        bookingListener?.remove()
        bookingListener = firestore.collection("bookings")
            .document(bookingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    bindBookingHeader(snapshot)
                }
            }
    }

    private fun bindBookingHeader(doc: com.google.firebase.firestore.DocumentSnapshot) {
        val reservationId = doc.getString("reservationId")
            ?: doc.getString("bookingCode")
            ?: doc.getString("bookingNumber")
            ?: doc.id
        val roomNumber = doc.getString("roomNumber")
            ?: doc.getString("roomId")
            ?: doc.getString("room")
            ?: intent.getStringExtra("ROOM_ID")
        val guestName = doc.getString("guestName")
            ?: doc.getString("customerName")
            ?: doc.getString("name")
        val totalFinal = when (val raw = doc.get("totalFinal")) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        }
        val amountText = totalFinal?.let { String.format(Locale.getDefault(), "Reservation amount: %,.0fVND", it) }
            ?: getString(R.string.cleaning_amount_placeholder)

        val headerParts = mutableListOf<String>()
        if (!roomNumber.isNullOrBlank()) headerParts += "Room: $roomNumber"
        if (!reservationId.isNullOrBlank()) headerParts += "Reservation: $reservationId"
        if (!guestName.isNullOrBlank()) headerParts += "Guest: $guestName"

        if (headerParts.isNotEmpty()) {
            tvReservationInfo.text = headerParts.joinToString(" • \n")
        }
        tvReservationAmount.text = amountText
    }

    override fun onDestroy() {
        super.onDestroy()
        bookingListener?.remove()
        bookingListener = null
    }

    private fun updateTotal() {
        val total = currentTotal()
        tvTotalCharges.text = String.format("Total additional chargers: %,.0fVND", total)
    }

    private fun currentTotal(): Double =
        lostAdapter.totalCharges() + brokenAdapter.totalCharges() + miniBarAdapter.totalCharges()

    private fun defaultLostItems(): List<LostItemAdapter.LostItem> = listOf(
        LostItemAdapter.LostItem("Flipflop", 0.0),
        LostItemAdapter.LostItem("Towel", 0.0),
        LostItemAdapter.LostItem("Remote", 0.0),
        LostItemAdapter.LostItem("Blanket", 0.0),
        LostItemAdapter.LostItem("Shampoo", 0.0),
        LostItemAdapter.LostItem("Hair dryer", 0.0)
    )

    private fun defaultBrokenItems(): List<BrokenFurnitureAdapter.BrokenItem> = listOf(
        BrokenFurnitureAdapter.BrokenItem("Bed", 0.0),
        BrokenFurnitureAdapter.BrokenItem("TV", 0.0),
        BrokenFurnitureAdapter.BrokenItem("Shower", 0.0),
        BrokenFurnitureAdapter.BrokenItem("Lamp", 0.0),
        BrokenFurnitureAdapter.BrokenItem("Window", 0.0),
        BrokenFurnitureAdapter.BrokenItem("Hair dryer", 0.0)
    )

    private fun defaultMiniBarItems(): List<MiniBarAdapter.MiniItem> = listOf(
        MiniBarAdapter.MiniItem("Snacks", 15000.0),
        MiniBarAdapter.MiniItem("Water", 10000.0),
        MiniBarAdapter.MiniItem("Coffee", 20000.0),
        MiniBarAdapter.MiniItem("Tea", 15000.0),
        MiniBarAdapter.MiniItem("Fruits", 30000.0)
    )

    private fun loadDamageRates(roomTypeId: String) {
        lifecycleScope.launch {
            val rates = withContext(Dispatchers.IO) {
                runCatching { fetchDamageRates(roomTypeId) }.getOrElse { DamageRateResult(emptyList(), emptyList()) }
            }
            if (rates.brokenItems.isNotEmpty()) {
                brokenAdapter.replaceItems(rates.brokenItems.toMutableList())
            }
            if (rates.lostItems.isNotEmpty()) {
                lostAdapter.replaceItems(rates.lostItems.toMutableList())
            }
            updateTotal()
        }
    }

    private fun resolveRoomTypeFromBooking(bookingId: String) {
        lifecycleScope.launch {
            val roomTypeId = withContext(Dispatchers.IO) {
                runCatching { fetchRoomTypeIdFromBooking(bookingId) }.getOrNull()
            }
            if (!roomTypeId.isNullOrBlank()) {
                loadDamageRates(roomTypeId)
            }
        }
    }

    private suspend fun fetchRoomTypeIdFromBooking(bookingId: String): String? {
        val doc = firestore.collection("bookings").document(bookingId).get().await()
        if (doc.exists()) {
            extractRoomTypeId(doc.get("roomTypeRef")
                ?: doc.get("roomTypeId")
                ?: doc.get("room_type_id")
                ?: doc.get("roomType")
                ?: doc.get("roomTypeInfo"))?.let { return it }
        }
        return null
    }

    private data class DamageRateResult(
        val lostItems: List<LostItemAdapter.LostItem>,
        val brokenItems: List<BrokenFurnitureAdapter.BrokenItem>
    )

    private suspend fun fetchDamageRates(roomTypeId: String): DamageRateResult {
        val statusLookup = loadFacilityStatus()
        val snapshot = firestore.collection("roomTypes")
            .document(roomTypeId)
            .collection("damageLossRates")
            .get()
            .await()
        if (snapshot.isEmpty) return DamageRateResult(emptyList(), emptyList())

        val entries = snapshot.documents.mapNotNull { doc ->
            val facilityId = doc.getString("facilityId") ?: return@mapNotNull null
            val price = when (val raw = doc.get("price")) {
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull()
                else -> null
            } ?: 0.0
            val status = doc.getString("statusId")
                ?: doc.getString("status")
                ?: statusLookup[facilityId]
                ?: "0"
            RateEntry(facilityId, price, status)
        }
        if (entries.isEmpty()) return DamageRateResult(emptyList(), emptyList())

        val namesMap = fetchFacilityNames(entries.map { it.facilityId }.toSet())
        val lost = mutableListOf<LostItemAdapter.LostItem>()
        val broken = mutableListOf<BrokenFurnitureAdapter.BrokenItem>()
        entries.forEach { entry ->
            val name = namesMap[entry.facilityId] ?: entry.facilityId
            if (entry.statusId == "1") {
                lost += LostItemAdapter.LostItem(name, entry.price, checked = false, quantity = 0)
            } else {
                broken += BrokenFurnitureAdapter.BrokenItem(name, entry.price)
            }
        }
        return DamageRateResult(lost, broken)
    }

    private suspend fun fetchFacilityNames(ids: Set<String>): Map<String, String> {
        if (ids.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()
        ids.chunked(10).forEach { chunk ->
            val snap = firestore.collection("facilities")
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .await()
            for (doc in snap.documents) {
                map[doc.id] = doc.getString("facilities_name") ?: doc.id
            }
        }
        return map
    }

    private data class RateEntry(val facilityId: String, val price: Double, val statusId: String)

    private suspend fun loadFacilityStatus(): Map<String, String> {
        return try {
            firestore.collection("facilitiesStatus")
                .document("damageLoss")
                .get()
                .await()
                ?.data
                ?.mapNotNull { (key, value) ->
                    if (value is Number) key to value.toInt().toString() else null
                }
                ?.toMap()
                ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun extractRoomTypeId(source: Any?): String? = when (source) {
        is String -> source
        is Map<*, *> -> {
            (source["id"] as? String)
                ?: (source["roomTypeId"] as? String)
                ?: (source["room_type_id"] as? String)
                ?: (source["id"] as? Number)?.toString()
        }
        is com.google.firebase.firestore.DocumentReference -> source.id
        else -> null
    }
}


