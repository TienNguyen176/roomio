package com.tdc.nhom6.roomio.repository

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.ListenerRegistration
import android.app.Activity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.tdc.nhom6.roomio.models.Deal
import com.tdc.nhom6.roomio.models.Discount
import com.tdc.nhom6.roomio.models.Hotel
import com.tdc.nhom6.roomio.models.RoomType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL


class FirebaseRepository {

    // Firebase project configuration - using the correct project ID from google-services.json
    private val projectId = "roomio-2e37f" // Correct Firebase project ID
    private val baseUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"
    val db = Firebase.firestore

    fun isPlayServicesAvailable(activity: Activity): Boolean {
        val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)
        return availability == ConnectionResult.SUCCESS
    }
    suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("$baseUrl/test")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val code = connection.responseCode
                // Ensure any streams are closed to avoid resource leak warnings
                val stream = try {
                    connection.inputStream
                } catch (_: Exception) {
                    connection.errorStream
                }
                try {
                    stream?.let { s ->
                        // Read and discard to fully consume
                        val buffer = ByteArray(1024)
                        while (s.read(buffer) != -1) { /* discard */ }
                    }
                } catch (_: Exception) { } finally {
                    try { stream?.close() } catch (_: Exception) { }
                }

                println("Firebase REST API connection test: $code")
                true
            } catch (e: Exception) {
                println("Firebase REST API connection failed: ${e.message}")
                false
            } finally {
                try { connection?.disconnect() } catch (_: Exception) { }
            }
        }
    }

    private fun toMillis(value: Any): Long {
        return try {
            when (value) {
                is Long -> value
                is Int -> value.toLong()
                is Double -> value.toLong()
                is com.google.firebase.Timestamp -> value.toDate().time
                is java.util.Date -> value.time
                is String -> value.toLongOrNull() ?: System.currentTimeMillis()
                else -> System.currentTimeMillis()
            }
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun toMillisHeuristic(value: Any): Long {
        val raw = toMillis(value)
        // If value looks like seconds since epoch (10 digits within reasonable year range), convert to millis
        return if (raw in 946684800..4102444800) raw * 1000 else raw
    }

    fun getHotReviews(callback: (List<Hotel>) -> Unit) {
        try {
            db.collection("hotels")
                .get()
                .addOnSuccessListener { result ->
                    val hotels = mutableListOf<Hotel>()
                    for (document in result) {
                        try {
                            Log.d("Firebase", "${document.id} => ${document.data}")
                            val hotel: Hotel = document.toObject<Hotel>()
                            hotels.add(hotel)
                        } catch (e: Exception) {
                            Log.e("Firebase", "Error converting document ${document.id}: ${e.message}")
                            // Skip this document and continue with others
                        }
                    }
                    
                    // Load room types to get correct prices
                    loadRoomTypesAndUpdatePrices(hotels) { hotelsWithPrices ->
                        // Get hotels with most reviews (top 5)
                        val topHotels = hotelsWithPrices.sortedByDescending { it.totalReviews }.take(5)
                        
                        Log.d("Firebase", "Load ${topHotels.size} hotels successfully with prices from room types")
                        callback(topHotels)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.w("Firebase", "Error getting documents.", exception)
                    // Return empty list on error
                    callback(emptyList())
                }
        } catch (e: Exception) {
            Log.e("Firebase", "Exception in getHotReviews: ${e.message}")
            callback(emptyList())
        }
    }

    /**
     * Realtime: observe hotels, then recompute prices from roomTypes on each change
     */
    fun observeHotReviews(callback: (List<Hotel>) -> Unit): ListenerRegistration {
        // Cache for room types
        val roomTypesCache = mutableMapOf<String, MutableList<RoomType>>()
        var hotelsCache = mutableListOf<Hotel>()

        // Helper function to update prices using cached room types
        fun updatePricesAndCallback() {
            val hotelsWithPrices = hotelsCache.map { hotel ->
                val roomTypes = roomTypesCache[hotel.hotelId] ?: emptyList()
                val lowestPrice = roomTypes.minOfOrNull { it.pricePerNight } ?: hotel.pricePerNight
                val highestPrice = roomTypes.maxOfOrNull { it.pricePerNight }

                hotel.copy(
                    pricePerNight = lowestPrice,
                    lowestPricePerNight = if (roomTypes.isNotEmpty()) lowestPrice else null,
                    highestPricePerNight = highestPrice
                )
            }
            val topHotels = hotelsWithPrices.sortedByDescending { it.totalReviews }.take(5)
            callback(topHotels)
        }

        // Observe room types in real-time
        val roomTypesListener = db.collection("roomTypes")
            .addSnapshotListener { result, error ->
                if (error != null) {
                    Log.w("Firebase", "observeHotReviews: roomTypes error: ${error.message}")
                    return@addSnapshotListener
                }
                roomTypesCache.clear()
                if (result != null) {
                    for (document in result) {
                        try {
                            val roomType: RoomType = document.toObject<RoomType>()
                            val hotelId = roomType.hotelId
                            if (!roomTypesCache.containsKey(hotelId)) {
                                roomTypesCache[hotelId] = mutableListOf()
                            }
                            roomTypesCache[hotelId]?.add(roomType)
                        } catch (e: Exception) {
                            Log.e("Firebase", "Error converting room type ${document.id}: ${e.message}")
                        }
                    }
                }
                // Update prices whenever room types change
                if (hotelsCache.isNotEmpty()) {
                    updatePricesAndCallback()
                }
            }

        // Observe hotels in real-time
        val hotelsListener = db.collection("hotels")
            .addSnapshotListener { result, error ->
                if (error != null) {
                    Log.w("Firebase", "observeHotReviews error: ${error.message}")
                    callback(emptyList())
                    return@addSnapshotListener
                }
                hotelsCache = mutableListOf()
                if (result != null) {
                    for (document in result) {
                        try {
                            val hotel: Hotel = document.toObject<Hotel>()
                            hotelsCache.add(hotel)
                        } catch (e: Exception) {
                            Log.e("Firebase", "Error converting hotel ${document.id}: ${e.message}")
                        }
                    }
                }
                // Update prices whenever hotels change
                updatePricesAndCallback()
            }

        // Return a combined listener that removes both when called
        return object : ListenerRegistration {
            override fun remove() {
                roomTypesListener.remove()
                hotelsListener.remove()
            }
        }
    }

    fun observeHotReviews(activity: Activity, callback: (List<Hotel>) -> Unit): ListenerRegistration {
        // Cache for room types
        val roomTypesCache = mutableMapOf<String, MutableList<RoomType>>()
        var hotelsCache = mutableListOf<Hotel>()

        // Helper function to update prices using cached room types
        fun updatePricesAndCallback() {
            val hotelsWithPrices = hotelsCache.map { hotel ->
                val roomTypes = roomTypesCache[hotel.hotelId] ?: emptyList()
                val lowestPrice = roomTypes.minOfOrNull { it.pricePerNight } ?: hotel.pricePerNight
                val highestPrice = roomTypes.maxOfOrNull { it.pricePerNight }

                hotel.copy(
                    pricePerNight = lowestPrice,
                    lowestPricePerNight = if (roomTypes.isNotEmpty()) lowestPrice else null,
                    highestPricePerNight = highestPrice
                )
            }
            val topHotels = hotelsWithPrices.sortedByDescending { it.totalReviews }.take(5)
            callback(topHotels)
        }

        // Observe room types in real-time
        val roomTypesListener = db.collection("roomTypes")
            .addSnapshotListener(activity) { result, error ->
                if (error != null) {
                    Log.w("Firebase", "observeHotReviews(activity): roomTypes error: ${error.message}")
                    return@addSnapshotListener
                }
                roomTypesCache.clear()
                if (result != null) {
                    for (document in result) {
                        try {
                            val roomType: RoomType = document.toObject<RoomType>()
                            val hotelId = roomType.hotelId
                            if (!roomTypesCache.containsKey(hotelId)) {
                                roomTypesCache[hotelId] = mutableListOf()
                            }
                            roomTypesCache[hotelId]?.add(roomType)
                        } catch (e: Exception) {
                            Log.e("Firebase", "Error converting room type ${document.id}: ${e.message}")
                        }
                    }
                }
                // Update prices whenever room types change
                if (hotelsCache.isNotEmpty()) {
                    updatePricesAndCallback()
                }
            }

        // Observe hotels in real-time
        val hotelsListener = db.collection("hotels")
            .addSnapshotListener(activity) { result, error ->
                if (error != null) {
                    Log.w("Firebase", "observeHotReviews(activity): ${error.message}")
                    callback(emptyList())
                    return@addSnapshotListener
                }
                hotelsCache = mutableListOf()
                if (result != null) {
                    for (document in result) {
                        try {
                            val hotel: Hotel = document.toObject<Hotel>()
                            hotelsCache.add(hotel)
                        } catch (e: Exception) {
                            Log.e("Firebase", "Error converting hotel ${document.id}: ${e.message}")
                        }
                    }
                }
                // Update prices whenever hotels change
                updatePricesAndCallback()
            }

        // Return a combined listener that removes both when called
        return object : ListenerRegistration {
            override fun remove() {
                roomTypesListener.remove()
                hotelsListener.remove()
            }
        }
    }

    /**
     * Loads room types from Firebase and updates hotel prices with the lowest room price
     */
    private fun loadRoomTypesAndUpdatePrices(hotels: List<Hotel>, callback: (List<Hotel>) -> Unit) {
        db.collection("roomTypes")
            .get()
            .addOnSuccessListener { roomTypesResult ->
                val roomTypesMap = mutableMapOf<String, MutableList<RoomType>>()
                
                // Group room types by hotel ID
                for (document in roomTypesResult) {
                    try {
                        val roomType: RoomType = document.toObject<RoomType>()
                        val hotelId = roomType.hotelId
                        
                        if (!roomTypesMap.containsKey(hotelId)) {
                            roomTypesMap[hotelId] = mutableListOf()
                        }
                        roomTypesMap[hotelId]?.add(roomType)
                    } catch (e: Exception) {
                        Log.e("Firebase", "Error converting room type ${document.id}: ${e.message}")
                    }
                }
                
                // Update hotel prices with lowest and highest room prices
                val hotelsWithPrices = hotels.map { hotel ->
                    val roomTypes = roomTypesMap[hotel.hotelId] ?: emptyList()
                    val lowestPrice = roomTypes.minOfOrNull { it.pricePerNight } ?: hotel.pricePerNight
                    val highestPrice = roomTypes.maxOfOrNull { it.pricePerNight }
                    
                    hotel.copy(
                        pricePerNight = lowestPrice,
                        lowestPricePerNight = if (roomTypes.isNotEmpty()) lowestPrice else null,
                        highestPricePerNight = highestPrice
                    )
                }
                
                callback(hotelsWithPrices)
            }
            .addOnFailureListener { exception ->
                Log.w("Firebase", "Error loading room types: ${exception.message}")
                // Return hotels without price updates
                callback(hotels)
            }
    }

    fun observeDealsHotels(callback: (List<Hotel>) -> Unit): ListenerRegistration {

        val discountsCache = mutableListOf<Discount>()
        val hotelMapCache = mutableMapOf<String, Hotel>()
        val hotelIdToRoomTypesCache = mutableMapOf<String, MutableList<RoomType>>()

        fun recomputeDealsHotels() {
            Log.d("Firebase", "Recomputing list of hotels with any discount...")

            val hotelIdsWithAnyDiscount = mutableSetOf<String>()

            for (discount in discountsCache) {
                if (!discount.hotelId.isNullOrEmpty()) {
                    hotelIdsWithAnyDiscount.add(discount.hotelId!!)
                }
            }

            // 2. Lọc Hotels
            val hotelsWithDiscount = hotelMapCache.values
                .filter { hotel -> hotelIdsWithAnyDiscount.contains(hotel.hotelId) }
                .toList()

            // 3. Cập nhật giá phòng
            val finalHotels = hotelsWithDiscount.map { hotel ->
                val roomTypes = hotelIdToRoomTypesCache[hotel.hotelId] ?: emptyList()
                val lowestPrice = roomTypes.minOfOrNull { it.pricePerNight } ?: hotel.pricePerNight
                val highestPrice = roomTypes.maxOfOrNull { it.pricePerNight }

                hotel.copy(
                    pricePerNight = lowestPrice,
                    lowestPricePerNight = if (roomTypes.isNotEmpty()) lowestPrice else null,
                    highestPricePerNight = highestPrice
                )
            }

            Log.d("Firebase", "Realtime update: Found ${finalHotels.size} hotels matching deals criteria (any discount).")
            callback(finalHotels)
        }


        // 1. Lắng nghe Discounts (Collection Group)
        val discountsListener = db.collectionGroup("discounts")
            .addSnapshotListener { discountResult, error ->
                if (error != null) {
                    Log.w("Firebase", "observeDealsHotels: discounts error: ${error.message}")
                    return@addSnapshotListener
                }
                discountsCache.clear()
                if (discountResult != null) {
                    for (document in discountResult) {
                        try {
                            var discount: Discount = document.toObject<Discount>()
                            if (discount.hotelId.isNullOrEmpty()) {
                                val parentId = document.reference.parent.parent?.id
                                if (!parentId.isNullOrEmpty()) {
                                    discount = discount.copy(hotelId = parentId)
                                }
                            }
                            discountsCache.add(discount)
                        } catch (e: Exception) {
                            Log.e("Firebase", "Error converting discount ${document.id}: ${e.message}")
                        }
                    }
                }
                recomputeDealsHotels()
            }

        // 2. Lắng nghe Hotels
        val hotelsListener = db.collection("hotels")
            .addSnapshotListener { hotelsResult, error ->
                if (error != null) {
                    Log.w("Firebase", "observeDealsHotels: hotels error: ${error.message}")
                    return@addSnapshotListener
                }
                hotelMapCache.clear()
                if (hotelsResult != null) {
                    for (document in hotelsResult) {
                        try {
                            val hotel: Hotel = document.toObject<Hotel>()
                            hotelMapCache[hotel.hotelId] = hotel
                        } catch (e: Exception) {
                            Log.e("Firebase", "Error converting hotel ${document.id}: ${e.message}")
                        }
                    }
                }
                recomputeDealsHotels()
            }

        // 3. Lắng nghe Room Types (để cập nhật giá)
        val roomTypesListener = db.collection("roomTypes")
            .addSnapshotListener { roomTypesResult, error ->
                if (error != null) {
                    Log.w("Firebase", "observeDealsHotels: roomTypes error: ${error.message}")
                    return@addSnapshotListener
                }
                hotelIdToRoomTypesCache.clear()
                if (roomTypesResult != null) {
                    for (document in roomTypesResult) {
                        try {
                            val rt: RoomType = document.toObject<RoomType>()
                            if (!hotelIdToRoomTypesCache.containsKey(rt.hotelId)) {
                                hotelIdToRoomTypesCache[rt.hotelId] = mutableListOf()
                            }
                            hotelIdToRoomTypesCache[rt.hotelId]?.add(rt)
                        } catch (e: Exception) {
                            Log.e("Firebase", "Error converting room type ${document.id}: ${e.message}")
                        }
                    }
                }
                recomputeDealsHotels()
            }

        // Trả về ListenerRegistration kết hợp
        return object : ListenerRegistration {
            override fun remove() {
                discountsListener.remove()
                hotelsListener.remove()
                roomTypesListener.remove()
                Log.d("Firebase", "All listeners for observeDealsHotels removed.")
            }
        }
    }
}