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
import com.tdc.nhom6.roomio.fragments.TaskStatus
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
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

        lostAdapter = LostItemAdapter(mutableListOf()) { updateTotal() }
        brokenAdapter = BrokenFurnitureAdapter(mutableListOf()) { updateTotal() }
        miniBarAdapter = MiniBarAdapter(mutableListOf()) { updateTotal() }

        rvLost.adapter = lostAdapter
        rvBroken.adapter = brokenAdapter
        rvMini.adapter = miniBarAdapter

        // Done button - navigate to cleaner task detail screen
        findViewById<MaterialButton>(R.id.btnDone).setOnClickListener {
            try {
                android.util.Log.d("CleaningInspection", "btnDone clicked")
                android.widget.Toast.makeText(this, "Saving inspection result...", android.widget.Toast.LENGTH_SHORT).show()

                val fee = currentTotal()
                val id = intent.getStringExtra("ROOM_ID") ?: ""
                val bookingId = intent.getStringExtra("BOOKING_ID")?.takeIf { it.isNotBlank() }
                CleanerTaskRepository.postCleaningResult(id, fee)

                if (bookingId != null) {
                    val cleaningTimestamp = com.google.firebase.firestore.FieldValue.serverTimestamp()
                    lifecycleScope.launch {
                        // Save cleaning fee to invoice (only cleaning fee, not room fees)
                        val invoiceRef = resolveInvoiceDocument(bookingId)
                        invoiceRef
                            .set(
                                mapOf(
                                    "cleaningCompletedAt" to cleaningTimestamp,
                                    "cleaningFee" to fee
                                ),
                                SetOptions.merge()
                            )
                            .addOnFailureListener { e ->
                                android.util.Log.e("CleaningInspection", "Failed to update invoice cleaning fee", e)
                            }

                        // Save room fees to subcollections (NOT to invoices)
                        // 1. Save lost items and broken furniture to facilitiesUsed
                        val checkedLostItems = lostAdapter.getCheckedItems()
                        val checkedBrokenItems = brokenAdapter.getCheckedItems()
                        
                        checkedLostItems.forEach { item ->
                            if (item.checked && item.quantity > 0 && item.facilityId.isNotEmpty()) {
                                val facilityData = mapOf(
                                    "bookingId" to bookingId,
                                    "facilityId" to item.facilityId,
                                    "facilityStatus" to "1", // 1 = lost item
                                    "quantity" to item.quantity,
                                    "createdAt" to cleaningTimestamp
                                )
                                firestore.collection("bookings")
                                    .document(bookingId)
                                    .collection("facilitiesUsed")
                                    .add(facilityData)
                                    .addOnSuccessListener {
                                        android.util.Log.d("CleaningInspection", "Saved lost item to facilitiesUsed: ${it.id}")
                                    }
                                    .addOnFailureListener { e ->
                                        android.util.Log.e("CleaningInspection", "Failed to save lost item: ${e.message}", e)
                                    }
                            }
                        }
                        
                        checkedBrokenItems.forEach { item ->
                            if (item.checked && item.facilityId.isNotEmpty()) {
                                val facilityData = mapOf(
                                    "bookingId" to bookingId,
                                    "facilityId" to item.facilityId,
                                    "facilityStatus" to "0", // 0 = broken item
                                    "quantity" to 1, // Broken items are typically quantity 1
                                    "createdAt" to cleaningTimestamp
                                )
                                firestore.collection("bookings")
                                    .document(bookingId)
                                    .collection("facilitiesUsed")
                                    .add(facilityData)
                                    .addOnSuccessListener {
                                        android.util.Log.d("CleaningInspection", "Saved broken item to facilitiesUsed: ${it.id}")
                                    }
                                    .addOnFailureListener { e ->
                                        android.util.Log.e("CleaningInspection", "Failed to save broken item: ${e.message}", e)
                                    }
                            }
                        }
                        
                        // 2. Save minibar items to miniBarUsed
                        val checkedMiniBarItems = miniBarAdapter.getCheckedItems()
                        checkedMiniBarItems.forEach { item ->
                            if (item.checked && item.quantity > 0 && item.minibarId.isNotEmpty()) {
                                val minibarData = mapOf(
                                    "bookingId" to bookingId,
                                    "minibarId" to item.minibarId,
                                    "quantity" to item.quantity,
                                    "createdAt" to cleaningTimestamp
                                )
                                firestore.collection("bookings")
                                    .document(bookingId)
                                    .collection("miniBarUsed")
                                    .add(minibarData)
                                    .addOnSuccessListener {
                                        android.util.Log.d("CleaningInspection", "Saved minibar item to miniBarUsed: ${it.id}")
                                    }
                                    .addOnFailureListener { e ->
                                        android.util.Log.e("CleaningInspection", "Failed to save minibar item: ${e.message}", e)
                                    }
                            }
                        }
                        
                        // 3. Save cleaner data to cleaner subcollection (status and cleaning fee only)
                        val cleanerData = mapOf(
                            "cleaningFee" to fee,
                            "status" to "in_progress", // Status is in_progress after inspection, will be completed after photos
                            "images" to emptyList<String>(),
                            "updatedAt" to cleaningTimestamp
                        )
                        
                        val cleanerDocRef = firestore.collection("bookings")
                            .document(bookingId)
                            .collection("cleaner")
                            .document() // Auto-generate document ID
                        
                        cleanerDocRef.set(cleanerData)
                            .addOnSuccessListener {
                                android.util.Log.d("CleaningInspection", "Saved cleaner data to cleaner subcollection: ${cleanerDocRef.id}")
                                markCleanerTaskClean(bookingId)
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.e("CleaningInspection", "Failed to save cleaner data to subcollection for booking $bookingId", e)
                            }
                    }
                }

                val detailIntent = Intent(this, CleanerTaskDetailActivity::class.java)
                detailIntent.putExtra("ROOM_ID", id)
                detailIntent.putExtra("BOOKING_ID", bookingId ?: "")
                detailIntent.putExtra("HOTEL_ID", intent.getStringExtra("HOTEL_ID") ?: "")
                val tasks = CleanerTaskRepository.tasks().value ?: emptyList()
                val task = tasks.find { it.roomId == id }
                val checkoutTime = "11.00 PM"
                detailIntent.putExtra("CHECKOUT_TIME", checkoutTime)
                detailIntent.putExtra("NOTES", "Replace bedding and towels")
                startActivity(detailIntent)
                finish()
            } catch (e: Exception) {
                android.util.Log.e("CleaningInspection", "Error handling btnDone click", e)
                android.widget.Toast.makeText(this, "Error saving inspection. Check logs.", android.widget.Toast.LENGTH_SHORT).show()
            }
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

        // Load minibar items - try multiple approaches
        if (bookingId != null) {
            loadMinibarFromFirebase(bookingId)
        }
        
        // Also try getting hotelId from roomType if available
        if (roomTypeId != null) {
            lifecycleScope.launch {
                val hotelIdFromRoomType = withContext(Dispatchers.IO) {
                    runCatching { fetchHotelIdFromRoomType(roomTypeId) }.getOrNull()
                }
                if (!hotelIdFromRoomType.isNullOrBlank()) {
                    android.util.Log.d("CleaningInspection", "Got hotelId from roomType: $hotelIdFromRoomType")
                    loadMinibarFromHotel(hotelIdFromRoomType)
                }
            }
        }
        
        // Fallback: try direct hotelId from intent
        val hotelId = intent.getStringExtra("HOTEL_ID")?.takeIf { it.isNotBlank() }
        if (hotelId != null) {
            android.util.Log.d("CleaningInspection", "Using hotelId from intent: $hotelId")
            loadMinibarFromHotel(hotelId)
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

    private fun loadMinibarFromFirebase(bookingId: String) {
        lifecycleScope.launch {
            android.util.Log.d("CleaningInspection", "Loading minibar for bookingId: $bookingId")
            val hotelId = withContext(Dispatchers.IO) {
                runCatching { fetchHotelIdFromBooking(bookingId) }.getOrElse { e ->
                    android.util.Log.e("CleaningInspection", "Error fetching hotelId from booking: ${e.message}", e)
                    null
                }
            }
            if (!hotelId.isNullOrBlank()) {
                android.util.Log.d("CleaningInspection", "Resolved hotelId: $hotelId, loading minibar items...")
                loadMinibarFromHotel(hotelId)
            } else {
                android.util.Log.w("CleaningInspection", "Could not resolve hotelId from booking $bookingId")
                // Try alternative: check if hotelId is passed directly
                val directHotelId = intent.getStringExtra("HOTEL_ID")?.takeIf { it.isNotBlank() }
                if (directHotelId != null) {
                    android.util.Log.d("CleaningInspection", "Using direct hotelId from intent: $directHotelId")
                    loadMinibarFromHotel(directHotelId)
                }
            }
        }
    }

    private suspend fun fetchHotelIdFromRoomType(roomTypeId: String): String? {
        try {
            val roomTypeDoc = firestore.collection("roomTypes").document(roomTypeId).get().await()
            if (roomTypeDoc.exists()) {
                val hotelId = roomTypeDoc.getString("hotelId")
                    ?: roomTypeDoc.getString("hotel_id")
                    ?: (roomTypeDoc.get("hotelRef") as? com.google.firebase.firestore.DocumentReference)?.id
                if (hotelId != null) {
                    android.util.Log.d("CleaningInspection", "Found hotelId from roomType: $hotelId")
                    return hotelId
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CleaningInspection", "Error fetching hotelId from roomType: ${e.message}", e)
        }
        return null
    }

    private suspend fun fetchHotelIdFromBooking(bookingId: String): String? {
        try {
            val bookingDoc = firestore.collection("bookings").document(bookingId).get().await()
            if (bookingDoc.exists()) {
                android.util.Log.d("CleaningInspection", "Booking document exists, checking for hotelId fields...")
                // Try multiple field name variations
                val hotelId = bookingDoc.getString("hotel_id")
                    ?: bookingDoc.getString("hotelId")
                    ?: bookingDoc.getString("hotel")
                    ?: (bookingDoc.get("hotelRef") as? com.google.firebase.firestore.DocumentReference)?.id
                    ?: (bookingDoc.get("hotel") as? Map<*, *>)?.get("id")?.toString()
                    ?: (bookingDoc.get("hotel") as? Map<*, *>)?.get("hotelId")?.toString()
                
                if (hotelId != null) {
                    android.util.Log.d("CleaningInspection", "Found hotelId: $hotelId")
                    return hotelId
                } else {
                    android.util.Log.w("CleaningInspection", "No hotelId field found in booking document. Available fields: ${bookingDoc.data?.keys}")
                }
            } else {
                android.util.Log.w("CleaningInspection", "Booking document $bookingId does not exist")
            }
        } catch (e: Exception) {
            android.util.Log.e("CleaningInspection", "Error fetching booking document: ${e.message}", e)
        }
        return null
    }

    private fun loadMinibarFromHotel(hotelId: String) {
        lifecycleScope.launch {
            android.util.Log.d("CleaningInspection", "loadMinibarFromHotel called with hotelId: $hotelId")
            val minibarItems = withContext(Dispatchers.IO) {
                runCatching { 
                    fetchMinibarItems(hotelId) 
                }.getOrElse { e ->
                    android.util.Log.e("CleaningInspection", "Error in fetchMinibarItems: ${e.message}", e)
                    emptyList()
                }
            }
            android.util.Log.d("CleaningInspection", "Fetched ${minibarItems.size} minibar items from Firebase for hotelId: $hotelId")
            if (minibarItems.isNotEmpty()) {
                miniBarAdapter.replaceItems(minibarItems)
                updateTotal()
            } else {
                android.util.Log.w("CleaningInspection", "No minibar items to display for hotelId: $hotelId")
            }
        }
    }

    private suspend fun fetchMinibarItems(hotelId: String): List<MiniBarAdapter.MiniItem> {
        try {
            android.util.Log.d("CleaningInspection", "Fetching minibar items for hotelId: $hotelId")
            val snapshot = firestore.collection("hotels")
                .document(hotelId)
                .collection("minibar")
                .whereEqualTo("status", true)
                .get()
                .await()
            
            android.util.Log.d("CleaningInspection", "Minibar query returned ${snapshot.size()} documents")
            
            if (snapshot.isEmpty) {
                android.util.Log.w("CleaningInspection", "No minibar items found for hotelId: $hotelId (status=true)")
                // Try without status filter as fallback
                val allSnapshot = firestore.collection("hotels")
                    .document(hotelId)
                    .collection("minibar")
                    .get()
                    .await()
                android.util.Log.d("CleaningInspection", "Trying without status filter: found ${allSnapshot.size()} documents")
                if (allSnapshot.isEmpty) {
                    return emptyList()
                }
                // Process all items regardless of status
                return allSnapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("name") ?: return@mapNotNull null
                    val price = when (val raw = doc.get("price")) {
                        is Number -> raw.toDouble()
                        is String -> raw.toDoubleOrNull()
                        is java.math.BigDecimal -> raw.toDouble()
                        else -> null
                    } ?: 0.0
                    val minibarId = doc.id // Use document ID as minibarId
                    
                    if (price > 0) {
                        android.util.Log.d("CleaningInspection", "Found minibar item: id=$minibarId, name=$name, price=$price")
                        MiniBarAdapter.MiniItem(name, price, checked = false, quantity = 0, minibarId = minibarId)
                    } else {
                        android.util.Log.w("CleaningInspection", "Skipping minibar item with invalid price: name=$name")
                        null
                    }
                }
            }

            return snapshot.documents.mapNotNull { doc ->
                val name = doc.getString("name") ?: return@mapNotNull null
                val price = when (val raw = doc.get("price")) {
                    is Number -> raw.toDouble()
                    is String -> raw.toDoubleOrNull()
                    is java.math.BigDecimal -> raw.toDouble()
                    else -> null
                } ?: 0.0
                val minibarId = doc.id // Use document ID as minibarId
                
                if (price > 0) {
                    android.util.Log.d("CleaningInspection", "Found minibar item: id=$minibarId, name=$name, price=$price")
                    MiniBarAdapter.MiniItem(name, price, checked = false, quantity = 0, minibarId = minibarId)
                } else {
                    android.util.Log.w("CleaningInspection", "Skipping minibar item with invalid price: name=$name")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CleaningInspection", "Error fetching minibar items: ${e.message}", e)
            return emptyList()
        }
    }

    private fun loadDamageRates(roomTypeId: String) {
        lifecycleScope.launch {
            val rates = withContext(Dispatchers.IO) {
                runCatching { fetchDamageRates(roomTypeId) }.getOrElse { DamageRateResult(emptyList(), emptyList()) }
            }
            
            android.util.Log.d("CleaningInspection", "Fetched ${rates.brokenItems.size} broken items and ${rates.lostItems.size} lost items from Firebase")
            

            brokenAdapter.replaceItems(rates.brokenItems.toMutableList())
            lostAdapter.replaceItems(rates.lostItems.toMutableList())
            
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
        if (snapshot.isEmpty) {
            android.util.Log.d("CleaningInspection", "No damageLossRates found for roomTypeId: $roomTypeId")
            return DamageRateResult(emptyList(), emptyList())
        }

        // First, collect all facility IDs and fetch their names
        val facilityIds = snapshot.documents.mapNotNull { doc ->
            doc.getString("facilityId")
        }.toSet()
        
        if (facilityIds.isEmpty()) {
            android.util.Log.d("CleaningInspection", "No facilityIds found in damageLossRates")
            return DamageRateResult(emptyList(), emptyList())
        }

        val namesMap = fetchFacilityNames(facilityIds)
        android.util.Log.d("CleaningInspection", "Fetched ${namesMap.size} facility names for ${facilityIds.size} facility IDs")

        val entries = snapshot.documents.mapNotNull { doc ->
            val facilityId = doc.getString("facilityId") ?: return@mapNotNull null
            val price = when (val raw = doc.get("price")) {
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull()
                is java.math.BigDecimal -> raw.toDouble()
                else -> null
            } ?: 0.0
            val status = doc.getString("statusId")
                ?: doc.getString("status")
                ?: statusLookup[facilityId]
                ?: "0"
            
            val facilityName = namesMap[facilityId] ?: facilityId
            
            // Only include entries with valid prices from Firebase
            if (price > 0) {
                android.util.Log.d("CleaningInspection", "Found rate: facilityId=$facilityId, name=$facilityName, price=$price, status=$status")
                RateEntry(facilityId, facilityName, price, status)
            } else {
                null
            }
        }
        
        if (entries.isEmpty()) {
            android.util.Log.d("CleaningInspection", "No valid entries with prices > 0")
            return DamageRateResult(emptyList(), emptyList())
        }

        val lost = mutableListOf<LostItemAdapter.LostItem>()
        val broken = mutableListOf<BrokenFurnitureAdapter.BrokenItem>()
        entries.forEach { entry ->
            if (entry.statusId == "1") {
                lost += LostItemAdapter.LostItem(entry.facilityName, entry.price, checked = false, quantity = 0, facilityId = entry.facilityId)
            } else {
                broken += BrokenFurnitureAdapter.BrokenItem(entry.facilityName, entry.price, facilityId = entry.facilityId)
            }
        }
        
        android.util.Log.d("CleaningInspection", "Created ${lost.size} lost items and ${broken.size} broken items from Firebase")
        return DamageRateResult(lost, broken)
    }

    private suspend fun fetchFacilityNames(ids: Set<String>): Map<String, String> {
        if (ids.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()
        ids.chunked(10).forEach { chunk ->
            try {
                val snap = firestore.collection("facilities")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .await()
                for (doc in snap.documents) {
                    val name = doc.getString("facilities_name")
                        ?: doc.getString("name")
                        ?: doc.getString("facility_name")
                        ?: doc.id
                    map[doc.id] = name
                    android.util.Log.d("CleaningInspection", "Mapped facilityId ${doc.id} to name: $name")
                }
            } catch (e: Exception) {
                android.util.Log.e("CleaningInspection", "Error fetching facility names for chunk", e)
            }
        }
        return map
    }

    private data class RateEntry(val facilityId: String, val facilityName: String, val price: Double, val statusId: String)

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

    private fun markCleanerTaskClean(bookingId: String) {
        try {
            val tasks = CleanerTaskRepository.tasks().value.orEmpty()
            val task = tasks.firstOrNull { it.bookingDocId == bookingId }
            if (task != null && task.status != TaskStatus.CLEAN) {
                CleanerTaskRepository.updateTask(task.copy(status = TaskStatus.CLEAN))
            }
        } catch (e: Exception) {
            android.util.Log.e("CleaningInspection", "Failed to mark cleaner task clean for $bookingId", e)
        }
    }

    private suspend fun resolveInvoiceDocument(bookingId: String): com.google.firebase.firestore.DocumentReference {
        val invoices = firestore.collection("invoices")
        val existing = invoices
            .whereEqualTo("bookingId", bookingId)
            .limit(1)
            .get()
            .await()
        return existing.documents.firstOrNull()?.reference ?: invoices.document(bookingId)
    }
}


