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

//    fun getDeals(callback: (List<Deal>) -> Unit) {
//        try {
//            // 1) Load discounts
//            db.collection("discounts")
//                .get()
//                .addOnSuccessListener { discountResult ->
//                    val discounts = mutableListOf<Discount>()
//                    for (document in discountResult) {
//                        try {
//                            val discount: Discount = document.toObject<Discount>()
//                            discounts.add(discount)
//                        } catch (e: Exception) {
//                            Log.e("Firebase", "Error converting discount ${document.id}: ${e.message}")
//                        }
//                    }
//
//                    // 2) Load hotels
//                    db.collection("hotels")
//                        .get()
//                        .addOnSuccessListener { hotelsResult ->
//                            val hotelMap = mutableMapOf<String, Hotel>()
//                            for (document in hotelsResult) {
//                                try {
//                                    val hotel: Hotel = document.toObject<Hotel>()
//                                    hotelMap[hotel.hotelId] = hotel
//                                } catch (e: Exception) {
//                                    Log.e("Firebase", "Error converting hotel ${document.id}: ${e.message}")
//                                }
//                            }
//
//                            // 3) Load room types
//                            db.collection("roomTypes")
//                                .get()
//                                .addOnSuccessListener { roomTypesResult ->
//                                    val roomTypeMap = mutableMapOf<String, RoomType>()
//                                    val hotelIdToRoomTypes = mutableMapOf<String, MutableList<RoomType>>()
//                                    for (document in roomTypesResult) {
//                                        try {
//                                            val rt: RoomType = document.toObject<RoomType>()
//                                            roomTypeMap[rt.roomTypeId] = rt
//                                            if (!hotelIdToRoomTypes.containsKey(rt.hotelId)) {
//                                                hotelIdToRoomTypes[rt.hotelId] = mutableListOf()
//                                            }
//                                            hotelIdToRoomTypes[rt.hotelId]?.add(rt)
//                                        } catch (e: Exception) {
//                                            Log.e("Firebase", "Error converting room type ${document.id}: ${e.message}")
//                                        }
//                                    }
//
//                                    val now = System.currentTimeMillis()
//
//                                    // 4) Build deals derived from discounts + hotel + room type
//                                    val deals = discounts.mapNotNull { d ->
//                                        val hotel = hotelMap[d.hotelId] ?: return@mapNotNull null
//                                        val roomType = d.roomTypeId?.let { roomTypeMap[it] }
//                                        val basePrice = when {
//                                            roomType != null -> roomType.pricePerNight
//                                            else -> {
//                                                val list = hotelIdToRoomTypes[hotel.hotelId] ?: emptyList()
//                                                list.minOfOrNull { it.pricePerNight } ?: hotel.pricePerNight
//                                            }
//                                        }
//
//                                        // Compute discounted price
//                                        val discounted = when {
//                                            d.discountPercent != null -> basePrice * (1 - d.discountPercent / 100.0)
//                                            d.discountAmount != null -> basePrice - d.discountAmount
//                                            else -> basePrice
//                                        }.coerceAtLeast(0.0)
//
//                                        val startMs = toMillisHeuristic(d.startDate)
//                                        val endMs = toMillisHeuristic(d.endDate)
//                                        val withinDate = now in startMs..endMs
//                                        if (!(d.isActive && withinDate)) {
//                                            return@mapNotNull null
//                                        }
//
//                                        val percent = when {
//                                            d.discountPercent != null -> d.discountPercent
//                                            d.discountAmount != null && basePrice > 0 -> (d.discountAmount / basePrice) * 100.0
//                                            else -> 0.0
//                                        }
//
//                                        Deal(
//                                            dealId = d.discountHotelId,
//                                            hotelName = hotel.hotelName,
//                                            hotelLocation = hotel.hotelAddress,
//                                            description = if (d.description.isNotBlank()) d.description else d.title,
//                                            imageUrl = hotel.images.firstOrNull() ?: "hotel_64260231_1",
//                                            originalPricePerNight = basePrice,
//                                            discountPricePerNight = discounted,
//                                            discountPercentage = percent.toInt(),
//                                            validFrom = startMs,
//                                            validTo = endMs,
//                                            isActive = d.isActive,
//                                            hotelId = hotel.hotelId,
//                                            roomType = roomType?.typeName ?: "",
//                                            amenities = emptyList<String>(),
//                                            rating = hotel.averageRating,
//                                            totalReviews = hotel.totalReviews,
//                                            createdAt = d.createdAt
//                                        )
//                                    }
//
//                                    Log.d("Firebase", "Load ${deals.size} deals from discounts=${discounts.size}; hotels=${hotelMap.size}")
//                                    callback(deals)
//                                }
//                                .addOnFailureListener { e ->
//                                    Log.w("Firebase", "Error loading room types: ${e.message}")
//                                    callback(emptyList())
//                                }
//                        }
//                        .addOnFailureListener { e ->
//                            Log.w("Firebase", "Error loading hotels: ${e.message}")
//                            callback(emptyList())
//                        }
//                }
//                .addOnFailureListener { exception ->
//                    Log.w("Firebase", "Error getting discounts.", exception)
//                    callback(emptyList())
//                }
//        } catch (e: Exception) {
//            Log.e("Firebase", "Exception in getDeals: ${e.message}")
//            callback(emptyList())
//        }
//    }

    /**
     * Realtime: observe discounts and map to deals by joining hotels + roomTypes
     */
//    fun observeDeals(callback: (List<Deal>) -> Unit): ListenerRegistration {
//        // Cache for discounts, hotels, and room types
//        val discountsCache = mutableListOf<Discount>()
//        val hotelMapCache = mutableMapOf<String, Hotel>()
//        val roomTypeMapCache = mutableMapOf<String, RoomType>()
//        val hotelIdToRoomTypesCache = mutableMapOf<String, MutableList<RoomType>>()
//
//        // Helper function to recompute deals when any data changes
//        fun recomputeDeals() {
//            val now = System.currentTimeMillis()
//            val deals = discountsCache.mapNotNull { d ->
//                val hotel = hotelMapCache[d.hotelId] ?: return@mapNotNull null
//                val roomType = d.roomTypeId?.let { roomTypeMapCache[it] }
//                val basePrice = when {
//                    roomType != null -> roomType.pricePerNight
//                    else -> {
//                        val list = hotelIdToRoomTypesCache[hotel.hotelId] ?: emptyList()
//                        list.minOfOrNull { it.pricePerNight } ?: hotel.pricePerNight
//                    }
//                }
//                val discounted = when {
//                    d.discountPercent != null -> basePrice * (1 - d.discountPercent / 100.0)
//                    d.discountAmount != null -> basePrice - d.discountAmount
//                    else -> basePrice
//                }.coerceAtLeast(0.0)
//                val startMs = toMillisHeuristic(d.startDate)
//                val endMs = toMillisHeuristic(d.endDate)
//                val withinDate = now in startMs..endMs
//                if (!(d.isActive && withinDate)) return@mapNotNull null
//                val percent = when {
//                    d.discountPercent != null -> d.discountPercent
//                    d.discountAmount != null && basePrice > 0 -> (d.discountAmount / basePrice) * 100.0
//                    else -> 0.0
//                }
//                Deal(
//                    dealId = d.discountHotelId,
//                    hotelName = hotel.hotelName,
//                    hotelLocation = hotel.hotelAddress,
//                    description = if (d.description.isNotBlank()) d.description else d.title,
//                    imageUrl = hotel.images.firstOrNull() ?: "hotel_64260231_1",
//                    originalPricePerNight = basePrice,
//                    discountPricePerNight = discounted,
//                    discountPercentage = percent.toInt(),
//                    validFrom = startMs,
//                    validTo = endMs,
//                    isActive = d.isActive,
//                    hotelId = hotel.hotelId,
//                    roomType = roomType?.typeName ?: "",
//                    amenities = emptyList<String>(),
//                    rating = hotel.averageRating,
//                    totalReviews = hotel.totalReviews,
//                    createdAt = d.createdAt
//                )
//            }
//            Log.d("Firebase", "Realtime deals=${deals.size} from discounts=${discountsCache.size}; hotels=${hotelMapCache.size}")
//            callback(deals)
//        }
//
//        // Observe discounts in real-time
//        val discountsListener = db.collection("discounts")
//            .addSnapshotListener { discountResult, error ->
//                if (error != null) {
//                    Log.w("Firebase", "observeDeals: discounts error: ${error.message}")
//                    callback(emptyList())
//                    return@addSnapshotListener
//                }
//                discountsCache.clear()
//                if (discountResult != null) {
//                    for (document in discountResult) {
//                        try {
//                            val discount: DiscountHotel = document.toObject<DiscountHotel>()
//                            discountsCache.add(discount)
//                        } catch (e: Exception) {
//                            Log.e("Firebase", "Error converting discount ${document.id}: ${e.message}")
//                        }
//                    }
//                }
//                recomputeDeals()
//            }
//
//        // Observe hotels in real-time
//        val hotelsListener = db.collection("hotels")
//            .addSnapshotListener { hotelsResult, error ->
//                if (error != null) {
//                    Log.w("Firebase", "observeDeals: hotels error: ${error.message}")
//                    return@addSnapshotListener
//                }
//                hotelMapCache.clear()
//                if (hotelsResult != null) {
//                    for (document in hotelsResult) {
//                        try {
//                            val hotel: Hotel = document.toObject<Hotel>()
//                            hotelMapCache[hotel.hotelId] = hotel
//                        } catch (e: Exception) {
//                            Log.e("Firebase", "Error converting hotel ${document.id}: ${e.message}")
//                        }
//                    }
//                }
//                recomputeDeals()
//            }
//
//        // Observe room types in real-time
//        val roomTypesListener = db.collection("roomTypes")
//            .addSnapshotListener { roomTypesResult, error ->
//                if (error != null) {
//                    Log.w("Firebase", "observeDeals: roomTypes error: ${error.message}")
//                    return@addSnapshotListener
//                }
//                roomTypeMapCache.clear()
//                hotelIdToRoomTypesCache.clear()
//                if (roomTypesResult != null) {
//                    for (document in roomTypesResult) {
//                        try {
//                            val rt: RoomType = document.toObject<RoomType>()
//                            roomTypeMapCache[rt.roomTypeId] = rt
//                            if (!hotelIdToRoomTypesCache.containsKey(rt.hotelId)) {
//                                hotelIdToRoomTypesCache[rt.hotelId] = mutableListOf()
//                            }
//                            hotelIdToRoomTypesCache[rt.hotelId]?.add(rt)
//                        } catch (e: Exception) {
//                            Log.e("Firebase", "Error converting room type ${document.id}: ${e.message}")
//                        }
//                    }
//                }
//                recomputeDeals()
//            }
//
//        // Return a combined listener that removes all three when called
//        return object : ListenerRegistration {
//            override fun remove() {
//                discountsListener.remove()
//                hotelsListener.remove()
//                roomTypesListener.remove()
//            }
//        }
//    }
//
//    fun observeDeals(activity: Activity, callback: (List<Deal>) -> Unit): ListenerRegistration {
//        // Cache for discounts, hotels, and room types
//        val discountsCache = mutableListOf<DiscountHotel>()
//        val hotelMapCache = mutableMapOf<String, Hotel>()
//        val roomTypeMapCache = mutableMapOf<String, RoomType>()
//        val hotelIdToRoomTypesCache = mutableMapOf<String, MutableList<RoomType>>()
//
//        // Helper function to recompute deals when any data changes
//        fun recomputeDeals() {
//            val now = System.currentTimeMillis()
//            val deals = discountsCache.mapNotNull { d ->
//                val hotel = hotelMapCache[d.hotelId] ?: return@mapNotNull null
//                val roomType = d.roomTypeId?.let { roomTypeMapCache[it] }
//                val basePrice = when {
//                    roomType != null -> roomType.pricePerNight
//                    else -> {
//                        val list = hotelIdToRoomTypesCache[hotel.hotelId] ?: emptyList()
//                        list.minOfOrNull { it.pricePerNight } ?: hotel.pricePerNight
//                    }
//                }
//                val discounted = when {
//                    d.discountPercent != null -> basePrice * (1 - d.discountPercent / 100.0)
//                    d.discountAmount != null -> basePrice - d.discountAmount
//                    else -> basePrice
//                }.coerceAtLeast(0.0)
//                val startMs = toMillisHeuristic(d.startDate)
//                val endMs = toMillisHeuristic(d.endDate)
//                val withinDate = now in startMs..endMs
//                if (!(d.isActive && withinDate)) return@mapNotNull null
//                val percent = when {
//                    d.discountPercent != null -> d.discountPercent
//                    d.discountAmount != null && basePrice > 0 -> (d.discountAmount / basePrice) * 100.0
//                    else -> 0.0
//                }
//                Deal(
//                    dealId = d.discountHotelId,
//                    hotelName = hotel.hotelName,
//                    hotelLocation = hotel.hotelAddress,
//                    description = if (d.description.isNotBlank()) d.description else d.title,
//                    imageUrl = hotel.images.firstOrNull() ?: "hotel_64260231_1",
//                    originalPricePerNight = basePrice,
//                    discountPricePerNight = discounted,
//                    discountPercentage = percent.toInt(),
//                    validFrom = startMs,
//                    validTo = endMs,
//                    isActive = d.isActive,
//                    hotelId = hotel.hotelId,
//                    roomType = roomType?.typeName ?: "",
//                    amenities = emptyList<String>(),
//                    rating = hotel.averageRating,
//                    totalReviews = hotel.totalReviews,
//                    createdAt = d.createdAt
//                )
//            }
//            Log.d("Firebase", "Realtime(owner) deals=${deals.size} from discounts=${discountsCache.size}; hotels=${hotelMapCache.size}")
//            callback(deals)
//        }
//
//        // Observe discounts in real-time
//        val discountsListener = db.collection("discounts")
//            .addSnapshotListener(activity) { discountResult, error ->
//                if (error != null) {
//                    Log.w("Firebase", "observeDeals(activity): discounts error: ${error.message}")
//                    callback(emptyList())
//                    return@addSnapshotListener
//                }
//                discountsCache.clear()
//                if (discountResult != null) {
//                    for (document in discountResult) {
//                        try {
//                            val discount: DiscountHotel = document.toObject<DiscountHotel>()
//                            discountsCache.add(discount)
//                        } catch (e: Exception) {
//                            Log.e("Firebase", "Error converting discount ${document.id}: ${e.message}")
//                        }
//                    }
//                }
//                recomputeDeals()
//            }
//
//        // Observe hotels in real-time
//        val hotelsListener = db.collection("hotels")
//            .addSnapshotListener(activity) { hotelsResult, error ->
//                if (error != null) {
//                    Log.w("Firebase", "observeDeals(activity): hotels error: ${error.message}")
//                    return@addSnapshotListener
//                }
//                hotelMapCache.clear()
//                if (hotelsResult != null) {
//                    for (document in hotelsResult) {
//                        try {
//                            val hotel: Hotel = document.toObject<Hotel>()
//                            hotelMapCache[hotel.hotelId] = hotel
//                        } catch (e: Exception) {
//                            Log.e("Firebase", "Error converting hotel ${document.id}: ${e.message}")
//                        }
//                    }
//                }
//                recomputeDeals()
//            }
//
//        // Observe room types in real-time
//        val roomTypesListener = db.collection("roomTypes")
//            .addSnapshotListener(activity) { roomTypesResult, error ->
//                if (error != null) {
//                    Log.w("Firebase", "observeDeals(activity): roomTypes error: ${error.message}")
//                    return@addSnapshotListener
//                }
//                roomTypeMapCache.clear()
//                hotelIdToRoomTypesCache.clear()
//                if (roomTypesResult != null) {
//                    for (document in roomTypesResult) {
//                        try {
//                            val rt: RoomType = document.toObject<RoomType>()
//                            roomTypeMapCache[rt.roomTypeId] = rt
//                            if (!hotelIdToRoomTypesCache.containsKey(rt.hotelId)) {
//                                hotelIdToRoomTypesCache[rt.hotelId] = mutableListOf()
//                            }
//                            hotelIdToRoomTypesCache[rt.hotelId]?.add(rt)
//                        } catch (e: Exception) {
//                            Log.e("Firebase", "Error converting room type ${document.id}: ${e.message}")
//                        }
//                    }
//                }
//                recomputeDeals()
//            }
//
//        // Return a combined listener that removes all three when called
//        return object : ListenerRegistration {
//            override fun remove() {
//                discountsListener.remove()
//                hotelsListener.remove()
//                roomTypesListener.remove()
//            }
//        }
//    }
//


}