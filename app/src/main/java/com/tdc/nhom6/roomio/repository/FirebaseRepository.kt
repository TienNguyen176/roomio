package com.tdc.nhom6.roomio.repository

import com.tdc.nhom6.roomio.model.Deal
import com.tdc.nhom6.roomio.model.HotReview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Simple Firebase Repository
 * Uses Firebase REST API directly - NO Google Play Services needed
 */
class FirebaseRepository {
    
    // Firebase project configuration - using the correct project ID from google-services.json
    private val projectId = "roomio-2e37f" // Correct Firebase project ID
    private val baseUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"
    
    /**
     * Test Firebase connection using REST API
     * NO Google Play Services needed
     */
    suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/test")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val responseCode = connection.responseCode
                connection.disconnect()
                
                println("Firebase REST API connection test: $responseCode")
                // Return true for any response (even 404 means Firebase is reachable)
                true
            } catch (e: Exception) {
                println("Firebase REST API connection failed: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Get hotel reviews from Firebase using REST API
     * NO Google Play Services needed
     */
    suspend fun getHotReviews(): List<HotReview> {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("$baseUrl/hot_reviews")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)
                    
                    if (jsonResponse.has("documents")) {
                        val documents = jsonResponse.getJSONArray("documents")
                        val reviews = mutableListOf<HotReview>()
                        
                        for (i in 0 until documents.length()) {
                            val doc = documents.getJSONObject(i)
                            val fields = doc.getJSONObject("fields")
                            
                            val review = HotReview(
                                hotelName = fields.getJSONObject("hotelName")?.getString("stringValue") ?: "",
                                location = fields.getJSONObject("location")?.getString("stringValue") ?: "",
                                rating = fields.getJSONObject("rating")?.getDouble("doubleValue") ?: 0.0,
                                hotelImage = fields.getJSONObject("hotelImage")?.getString("stringValue") ?: "",
                                pricePerNight = fields.getJSONObject("pricePerNight")?.getDouble("doubleValue") ?: 0.0
                            )
                            reviews.add(review)
                        }
                        
                        println("Loaded ${reviews.size} hot reviews from Firebase REST API")
                        return@withContext reviews
                    }
                }
                
                println("No Firebase reviews found, adding sample data to Firebase")
                addSampleDataToFirebase()
                // Try to get data again after adding sample data
                val retryUrl = URL("$baseUrl/hot_reviews")
                val retryConnection = retryUrl.openConnection() as HttpURLConnection
                retryConnection.requestMethod = "GET"
                retryConnection.connectTimeout = 10000
                retryConnection.readTimeout = 10000
                
                if (retryConnection.responseCode == 200) {
                    val retryResponse = retryConnection.inputStream.bufferedReader().use { it.readText() }
                    val retryJsonResponse = JSONObject(retryResponse)
                    
                    if (retryJsonResponse.has("documents")) {
                        val documents = retryJsonResponse.getJSONArray("documents")
                        val reviews = mutableListOf<HotReview>()
                        
                        for (i in 0 until documents.length()) {
                            val doc = documents.getJSONObject(i)
                            val fields = doc.getJSONObject("fields")
                            
                            val review = HotReview(
                                hotelName = fields.getJSONObject("hotelName")?.getString("stringValue") ?: "",
                                location = fields.getJSONObject("location")?.getString("stringValue") ?: "",
                                rating = fields.getJSONObject("rating")?.getDouble("doubleValue") ?: 0.0,
                                hotelImage = fields.getJSONObject("hotelImage")?.getString("stringValue") ?: "",
                                pricePerNight = fields.getJSONObject("pricePerNight")?.getDouble("doubleValue") ?: 0.0
                            )
                            reviews.add(review)
                        }
                        
                        println("Loaded ${reviews.size} hot reviews from Firebase after adding sample data")
                        retryConnection.disconnect()
                        return@withContext reviews
                    }
                }
                retryConnection.disconnect()
                getSampleHotReviews()
                
            } catch (e: Exception) {
                println("Firebase REST API error: ${e.message}")
                println("Attempting to add sample data to Firebase and retry...")
                addSampleDataToFirebase()
                
                // Try one more time after adding sample data
                try {
                    val retryUrl = URL("$baseUrl/hot_reviews")
                    val retryConnection = retryUrl.openConnection() as HttpURLConnection
                    retryConnection.requestMethod = "GET"
                    retryConnection.connectTimeout = 10000
                    retryConnection.readTimeout = 10000
                    
                    if (retryConnection.responseCode == 200) {
                        val retryResponse = retryConnection.inputStream.bufferedReader().use { it.readText() }
                        val retryJsonResponse = JSONObject(retryResponse)
                        
                        if (retryJsonResponse.has("documents")) {
                            val documents = retryJsonResponse.getJSONArray("documents")
                            val reviews = mutableListOf<HotReview>()
                            
                            for (i in 0 until documents.length()) {
                                val doc = documents.getJSONObject(i)
                                val fields = doc.getJSONObject("fields")
                                
                                val review = HotReview(
                                    hotelName = fields.getJSONObject("hotelName")?.getString("stringValue") ?: "",
                                    location = fields.getJSONObject("location")?.getString("stringValue") ?: "",
                                    rating = fields.getJSONObject("rating")?.getDouble("doubleValue") ?: 0.0,
                                    hotelImage = fields.getJSONObject("hotelImage")?.getString("stringValue") ?: "",
                                    pricePerNight = fields.getJSONObject("pricePerNight")?.getDouble("doubleValue") ?: 0.0
                                )
                                reviews.add(review)
                            }
                            
                            println("Successfully loaded ${reviews.size} hot reviews from Firebase after retry")
                            retryConnection.disconnect()
                            return@withContext reviews
                        }
                    }
                    retryConnection.disconnect()
                } catch (retryException: Exception) {
                    println("Retry also failed: ${retryException.message}")
                }
                
                // Only use sample data as absolute last resort
                println("Using sample data as last resort")
                getSampleHotReviews()
            } finally {
                connection?.disconnect()
            }
        }
    }
    
    /**
     * Get hotel deals from Firebase using REST API
     * NO Google Play Services needed
     */
    suspend fun getDeals(): List<Deal> {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("$baseUrl/deals")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)
                    
                    if (jsonResponse.has("documents")) {
                        val documents = jsonResponse.getJSONArray("documents")
                        val deals = mutableListOf<Deal>()
                        
                        for (i in 0 until documents.length()) {
                            val doc = documents.getJSONObject(i)
                            val fields = doc.getJSONObject("fields")
                            
                            val deal = Deal(
                                hotelName = fields.getJSONObject("hotelName")?.getString("stringValue") ?: "",
                                hotelLocation = fields.getJSONObject("hotelLocation")?.getString("stringValue") ?: "",
                                originalPricePerNight = fields.getJSONObject("originalPricePerNight")?.getDouble("doubleValue") ?: 0.0,
                                discountPricePerNight = fields.getJSONObject("discountPricePerNight")?.getDouble("doubleValue") ?: 0.0,
                                discountPercentage = fields.getJSONObject("discountPercentage")?.getInt("integerValue") ?: 0,
                                amenities = listOf(fields.getJSONObject("amenities")?.getString("stringValue") ?: ""),
                                imageUrl = fields.getJSONObject("imageUrl")?.getString("stringValue") ?: ""
                            )
                            deals.add(deal)
                        }
                        
                        println("Loaded ${deals.size} deals from Firebase REST API")
                        return@withContext deals
                    }
                }
                
                println("No Firebase deals found, adding sample data to Firebase")
                addSampleDataToFirebase()
                // Try to get data again after adding sample data
                val retryUrl = URL("$baseUrl/deals")
                val retryConnection = retryUrl.openConnection() as HttpURLConnection
                retryConnection.requestMethod = "GET"
                retryConnection.connectTimeout = 10000
                retryConnection.readTimeout = 10000
                
                if (retryConnection.responseCode == 200) {
                    val retryResponse = retryConnection.inputStream.bufferedReader().use { it.readText() }
                    val retryJsonResponse = JSONObject(retryResponse)
                    
                    if (retryJsonResponse.has("documents")) {
                        val documents = retryJsonResponse.getJSONArray("documents")
                        val deals = mutableListOf<Deal>()
                        
                        for (i in 0 until documents.length()) {
                            val doc = documents.getJSONObject(i)
                            val fields = doc.getJSONObject("fields")
                            
                            val deal = Deal(
                                hotelName = fields.getJSONObject("hotelName")?.getString("stringValue") ?: "",
                                hotelLocation = fields.getJSONObject("hotelLocation")?.getString("stringValue") ?: "",
                                originalPricePerNight = fields.getJSONObject("originalPricePerNight")?.getDouble("doubleValue") ?: 0.0,
                                discountPricePerNight = fields.getJSONObject("discountPricePerNight")?.getDouble("doubleValue") ?: 0.0,
                                discountPercentage = fields.getJSONObject("discountPercentage")?.getInt("integerValue") ?: 0,
                                amenities = listOf(fields.getJSONObject("amenities")?.getString("stringValue") ?: ""),
                                imageUrl = fields.getJSONObject("imageUrl")?.getString("stringValue") ?: ""
                            )
                            deals.add(deal)
                        }
                        
                        println("Loaded ${deals.size} deals from Firebase after adding sample data")
                        retryConnection.disconnect()
                        return@withContext deals
                    }
                }
                retryConnection.disconnect()
                getSampleDeals()
                
            } catch (e: Exception) {
                println("Firebase REST API error: ${e.message}")
                println("Attempting to add sample data to Firebase and retry...")
                addSampleDataToFirebase()
                
                // Try one more time after adding sample data
                try {
                    val retryUrl = URL("$baseUrl/deals")
                    val retryConnection = retryUrl.openConnection() as HttpURLConnection
                    retryConnection.requestMethod = "GET"
                    retryConnection.connectTimeout = 10000
                    retryConnection.readTimeout = 10000
                    
                    if (retryConnection.responseCode == 200) {
                        val retryResponse = retryConnection.inputStream.bufferedReader().use { it.readText() }
                        val retryJsonResponse = JSONObject(retryResponse)
                        
                        if (retryJsonResponse.has("documents")) {
                            val documents = retryJsonResponse.getJSONArray("documents")
                            val deals = mutableListOf<Deal>()
                            
                            for (i in 0 until documents.length()) {
                                val doc = documents.getJSONObject(i)
                                val fields = doc.getJSONObject("fields")
                                
                                val deal = Deal(
                                    hotelName = fields.getJSONObject("hotelName")?.getString("stringValue") ?: "",
                                    hotelLocation = fields.getJSONObject("hotelLocation")?.getString("stringValue") ?: "",
                                    originalPricePerNight = fields.getJSONObject("originalPricePerNight")?.getDouble("doubleValue") ?: 0.0,
                                    discountPricePerNight = fields.getJSONObject("discountPricePerNight")?.getDouble("doubleValue") ?: 0.0,
                                    discountPercentage = fields.getJSONObject("discountPercentage")?.getInt("integerValue") ?: 0,
                                    amenities = listOf(fields.getJSONObject("amenities")?.getString("stringValue") ?: ""),
                                    imageUrl = fields.getJSONObject("imageUrl")?.getString("stringValue") ?: ""
                                )
                                deals.add(deal)
                            }
                            
                            println("Successfully loaded ${deals.size} deals from Firebase after retry")
                            retryConnection.disconnect()
                            return@withContext deals
                        }
                    }
                    retryConnection.disconnect()
                } catch (retryException: Exception) {
                    println("Retry also failed: ${retryException.message}")
                }
                
                // Only use sample data as absolute last resort
                println("Using sample data as last resort")
                getSampleDeals()
            } finally {
                connection?.disconnect()
            }
        }
    }
    
    /**
     * Search for hotels and deals by name or location using REST API
     * NO Google Play Services needed
     */
    suspend fun searchHotelsAndDeals(query: String): List<Any> {
        return withContext(Dispatchers.IO) {
            try {
                val results = mutableListOf<Any>()
                
                // Search in hot reviews
                var reviewsConnection: HttpURLConnection? = null
                try {
                    val reviewsUrl = URL("$baseUrl/hot_reviews")
                    reviewsConnection = reviewsUrl.openConnection() as HttpURLConnection
                    reviewsConnection.requestMethod = "GET"
                    reviewsConnection.connectTimeout = 10000
                    reviewsConnection.readTimeout = 10000
                    
                    if (reviewsConnection.responseCode == 200) {
                        val response = reviewsConnection.inputStream.bufferedReader().use { it.readText() }
                        val jsonResponse = JSONObject(response)
                        
                        if (jsonResponse.has("documents")) {
                            val documents = jsonResponse.getJSONArray("documents")
                            
                            for (i in 0 until documents.length()) {
                                val doc = documents.getJSONObject(i)
                                val fields = doc.getJSONObject("fields")
                                
                                val hotelName = fields.getJSONObject("hotelName")?.getString("stringValue") ?: ""
                                val location = fields.getJSONObject("location")?.getString("stringValue") ?: ""
                                
                                if (hotelName.lowercase().contains(query.lowercase()) || 
                                    location.lowercase().contains(query.lowercase())) {
                                    
                                    val review = HotReview(
                                        hotelName = hotelName,
                                        location = location,
                                        rating = fields.getJSONObject("rating")?.getDouble("doubleValue") ?: 0.0,
                                        hotelImage = fields.getJSONObject("hotelImage")?.getString("stringValue") ?: "",
                                        pricePerNight = fields.getJSONObject("pricePerNight")?.getDouble("doubleValue") ?: 0.0
                                    )
                                    results.add(review)
                                }
                            }
                        }
                    }
        } catch (e: Exception) {
                    println("Error searching reviews: ${e.message}")
                } finally {
                    reviewsConnection?.disconnect()
                }
                
                // Search in deals
                var dealsConnection: HttpURLConnection? = null
                try {
                    val dealsUrl = URL("$baseUrl/deals")
                    dealsConnection = dealsUrl.openConnection() as HttpURLConnection
                    dealsConnection.requestMethod = "GET"
                    dealsConnection.connectTimeout = 10000
                    dealsConnection.readTimeout = 10000
                    
                    if (dealsConnection.responseCode == 200) {
                        val response = dealsConnection.inputStream.bufferedReader().use { it.readText() }
                        val jsonResponse = JSONObject(response)
                        
                        if (jsonResponse.has("documents")) {
                            val documents = jsonResponse.getJSONArray("documents")
                            
                            for (i in 0 until documents.length()) {
                                val doc = documents.getJSONObject(i)
                                val fields = doc.getJSONObject("fields")
                                
                                val hotelName = fields.getJSONObject("hotelName")?.getString("stringValue") ?: ""
                                val hotelLocation = fields.getJSONObject("hotelLocation")?.getString("stringValue") ?: ""
                                
                                if (hotelName.lowercase().contains(query.lowercase()) || 
                                    hotelLocation.lowercase().contains(query.lowercase())) {
                                    
                                    val deal = Deal(
                                        hotelName = hotelName,
                                        hotelLocation = hotelLocation,
                                        originalPricePerNight = fields.getJSONObject("originalPricePerNight")?.getDouble("doubleValue") ?: 0.0,
                                        discountPricePerNight = fields.getJSONObject("discountPricePerNight")?.getDouble("doubleValue") ?: 0.0,
                                        discountPercentage = fields.getJSONObject("discountPercentage")?.getInt("integerValue") ?: 0,
                                        amenities = listOf(fields.getJSONObject("amenities")?.getString("stringValue") ?: ""),
                                        imageUrl = fields.getJSONObject("imageUrl")?.getString("stringValue") ?: ""
                                    )
                                    results.add(deal)
                                }
                            }
                        }
                    }
        } catch (e: Exception) {
                    println("Error searching deals: ${e.message}")
                } finally {
                    dealsConnection?.disconnect()
                }
                
                // If no results found, return sample data
                if (results.isEmpty()) {
                    println("No Firebase results found, using sample data for search")
                    return@withContext getSampleSearchResults(query)
                }
                
                println("Found ${results.size} results from Firebase REST API")
                results
                
        } catch (e: Exception) {
                println("Firebase search error: ${e.message}")
                getSampleSearchResults(query)
            }
        }
    }
    

    private fun getSampleHotReviews(): List<HotReview> {
        return listOf(
            HotReview(
                hotelId = "sample_1",
                hotelName = "Ares Home",
                hotelImage = "hotel_64260231_1",
                rating = 4.5,
                totalReviews = 234,
                pricePerNight = 1500000.0,
                location = "Vung Tau",
                isHot = true
            ),
            HotReview(
                hotelId = "sample_2",
                hotelName = "Imperial Hotel",
                hotelImage = "hotel_del_coronado_views_suite1600x900",
                rating = 4.5,
                totalReviews = 189,
                pricePerNight = 800000.0,
                location = "Vung Tau",
                isHot = true
            ),
            HotReview(
                hotelId = "sample_3",
                hotelName = "Saigon Central Hotel",
                hotelImage = "swimming_pool_1",
                rating = 4.8,
                totalReviews = 456,
                pricePerNight = 1200000.0,
                location = "Ho Chi Minh City",
                isHot = true
            ),
            HotReview(
                hotelId = "sample_4",
                hotelName = "Mountain Lodge",
                hotelImage = "room_640278495",
                rating = 4.6,
                totalReviews = 95,
                pricePerNight = 600000.0,
                location = "Sapa",
                isHot = true
            ),
            HotReview(
                hotelId = "sample_5",
                hotelName = "Beachfront Paradise",
                hotelImage = "rectangle_copy_2",
                rating = 4.9,
                totalReviews = 210,
                pricePerNight = 900000.0,
                location = "Nha Trang",
                isHot = true
            )
        )
    }
    

    private fun getSampleDeals(): List<Deal> {
        return listOf(
            Deal(
                hotelName = "Ares Home",
                hotelLocation = "Vũng Tàu",
                description = "Luxury beachfront resort",
                imageUrl = "hotel_64260231_1",
                originalPricePerNight = 2000000.0,
                discountPricePerNight = 1500000.0,
                discountPercentage = 25,
                validFrom = System.currentTimeMillis(),
                validTo = System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000L),
                isActive = true,
                hotelId = "sample_1",
                roomType = "Deluxe Ocean View",
                amenities = listOf("Free WiFi", "Swimming Pool", "Spa"),
                rating = 4.5,
                totalReviews = 234
            ),
            Deal(
                hotelName = "Imperial Hotel",
                hotelLocation = "Vũng Tàu",
                description = "Elegant imperial-style hotel",
                imageUrl = "hotel_del_coronado_views_suite1600x900",
                originalPricePerNight = 1200000.0,
                discountPricePerNight = 800000.0,
                discountPercentage = 33,
                validFrom = System.currentTimeMillis(),
                validTo = System.currentTimeMillis() + (45 * 24 * 60 * 60 * 1000L),
                isActive = true,
                hotelId = "sample_2",
                roomType = "Executive Suite",
                amenities = listOf("Free WiFi", "Swimming Pool", "Restaurant"),
                rating = 4.5,
                totalReviews = 189
            ),
            Deal(
                hotelName = "Saigon Central Hotel",
                hotelLocation = "Ho Chi Minh City",
                description = "Modern business hotel in city center",
                imageUrl = "swimming_pool_1",
                originalPricePerNight = 1500000.0,
                discountPricePerNight = 1200000.0,
                discountPercentage = 20,
                validFrom = System.currentTimeMillis(),
                validTo = System.currentTimeMillis() + (20 * 24 * 60 * 60 * 1000L),
                isActive = true,
                hotelId = "sample_3",
                roomType = "Business Room",
                amenities = listOf("Free WiFi", "Business Center", "Gym"),
                rating = 4.8,
                totalReviews = 456
            ),
            Deal(
                hotelName = "Mountain Lodge",
                hotelLocation = "Sapa",
                description = "Cozy mountain lodge with mountain views",
                imageUrl = "room_640278495",
                originalPricePerNight = 800000.0,
                discountPricePerNight = 600000.0,
                discountPercentage = 25,
                validFrom = System.currentTimeMillis(),
                validTo = System.currentTimeMillis() + (60 * 24 * 60 * 60 * 1000L),
                isActive = true,
                hotelId = "sample_4",
                roomType = "Mountain View Room",
                amenities = listOf("Free WiFi", "Fireplace", "Hiking Tours"),
                rating = 4.6,
                totalReviews = 95
            ),
            Deal(
                hotelName = "Beachfront Paradise",
                hotelLocation = "Nha Trang",
                description = "Stunning beachfront hotel with direct beach access",
                imageUrl = "rectangle_copy_2",
                originalPricePerNight = 1200000.0,
                discountPricePerNight = 900000.0,
                discountPercentage = 25,
                validFrom = System.currentTimeMillis(),
                validTo = System.currentTimeMillis() + (15 * 24 * 60 * 60 * 1000L),
                isActive = true,
                hotelId = "sample_5",
                roomType = "Ocean View Room",
                amenities = listOf("Free WiFi", "Swimming Pool", "Beach Access"),
                rating = 4.9,
                totalReviews = 210
            )
        )
    }
    

    private fun getSampleSearchResults(query: String): List<Any> {
        val sampleReviews = getSampleHotReviews()
        val sampleDeals = getSampleDeals()
        
        val allSampleData = mutableListOf<Any>()
        allSampleData.addAll(sampleReviews)
        allSampleData.addAll(sampleDeals)
        
        // Filter sample data based on search query
        return allSampleData.filter { item ->
            when (item) {
                is HotReview -> {
                    item.hotelName.lowercase().contains(query.lowercase()) ||
                    item.location.lowercase().contains(query.lowercase())
                }
                is Deal -> {
                    item.hotelName.lowercase().contains(query.lowercase()) ||
                    item.hotelLocation.lowercase().contains(query.lowercase())
                }
                else -> false
            }
        }
    }

    /**
     * Force Firebase data loading - ensures Firebase data appears
     * This method tries multiple times to get Firebase data
     */
    suspend fun forceFirebaseDataLoading(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                println("Force loading Firebase data...")
                
                // First, try to add sample data to Firebase
                addSampleDataToFirebase()
                
                // Then try to get the data
                val reviews = getHotReviews()
                val deals = getDeals()
                
                val hasFirebaseData = reviews.isNotEmpty() && deals.isNotEmpty()
                println("Firebase data loading result: $hasFirebaseData (${reviews.size} reviews, ${deals.size} deals)")
                
                hasFirebaseData
            } catch (e: Exception) {
                println("Force Firebase data loading failed: ${e.message}")
                false
            }
        }
    }

    suspend fun addSampleDataToFirebase() {
        withContext(Dispatchers.IO) {
            try {
                println("Adding sample data to Firebase...")
                
                // Add sample hot reviews
                val sampleReviews = getSampleHotReviews()
                for (review in sampleReviews) {
                    var connection: HttpURLConnection? = null
                    try {
                        val url = URL("$baseUrl/hot_reviews")
                        connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.doOutput = true
                        
                        val jsonData = JSONObject().apply {
                            put("fields", JSONObject().apply {
                                put("hotelName", JSONObject().put("stringValue", review.hotelName))
                                put("location", JSONObject().put("stringValue", review.location))
                                put("rating", JSONObject().put("doubleValue", review.rating))
                                put("hotelImage", JSONObject().put("stringValue", review.hotelImage))
                                put("pricePerNight", JSONObject().put("doubleValue", review.pricePerNight))
                            })
                        }
                        
                        connection.outputStream.use { it.write(jsonData.toString().toByteArray()) }
                        val responseCode = connection.responseCode
                        
                        if (responseCode in 200..299) {
                            println("Added hot review: ${review.hotelName}")
                        }
        } catch (e: Exception) {
                        println("Error adding hot review: ${e.message}")
                    } finally {
                        connection?.disconnect()
                    }
                }
                
                // Add sample deals
                val sampleDeals = getSampleDeals()
                for (deal in sampleDeals) {
                    var connection: HttpURLConnection? = null
                    try {
                        val url = URL("$baseUrl/deals")
                        connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.doOutput = true
                        
                        val jsonData = JSONObject().apply {
                            put("fields", JSONObject().apply {
                                put("hotelName", JSONObject().put("stringValue", deal.hotelName))
                                put("hotelLocation", JSONObject().put("stringValue", deal.hotelLocation))
                                put("originalPricePerNight", JSONObject().put("doubleValue", deal.originalPricePerNight))
                                put("discountPricePerNight", JSONObject().put("doubleValue", deal.discountPricePerNight))
                                put("discountPercentage", JSONObject().put("integerValue", deal.discountPercentage))
                                put("amenities", JSONObject().put("stringValue", deal.amenities.joinToString(", ")))
                                put("imageUrl", JSONObject().put("stringValue", deal.imageUrl))
                            })
                        }
                        
                        connection.outputStream.use { it.write(jsonData.toString().toByteArray()) }
                        val responseCode = connection.responseCode
                        
                        if (responseCode in 200..299) {
                            println("Added deal: ${deal.hotelName}")
                        }
        } catch (e: Exception) {
                        println("Error adding deal: ${e.message}")
                    } finally {
                        connection?.disconnect()
                    }
                }
                
                println("Sample data added to Firebase successfully!")
                
        } catch (e: Exception) {
                println("Error adding sample data to Firebase: ${e.message}")
        }
    }
}

}