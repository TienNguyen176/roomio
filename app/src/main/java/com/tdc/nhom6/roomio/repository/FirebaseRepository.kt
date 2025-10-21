package com.tdc.nhom6.roomio.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tdc.nhom6.roomio.model.Deal
import com.tdc.nhom6.roomio.model.HotReview
import com.tdc.nhom6.roomio.model.Hotel
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    val firestore = FirebaseFirestore.getInstance()
    
    // Collections
    private val dealsCollection = firestore.collection("deals")
    private val hotReviewsCollection = firestore.collection("hot_reviews")
    private val hotelsCollection = firestore.collection("hotels")
    
    // Get active hotel deals
    suspend fun getActiveDeals(): List<Deal> {
        return try {
            println("FirebaseRepository: Fetching active deals...")
            
            // First try to get deals with isActive = true
            var result = dealsCollection
                .whereEqualTo("isActive", true)
                .limit(10)
                .get()
                .await()
            
            var deals = result.toObjects(Deal::class.java)
            println("FirebaseRepository: Found ${deals.size} active deals with isActive=true")
            
            // If no results, try to get all deals and filter in memory
            if (deals.isEmpty()) {
                println("FirebaseRepository: No active deals found, trying all deals...")
                result = dealsCollection
                    .limit(20)
                    .get()
                    .await()
                
                deals = result.toObjects(Deal::class.java)
                println("FirebaseRepository: Found ${deals.size} total deals")
            }
            
            deals
        } catch (e: Exception) {
            println("FirebaseRepository: Error fetching deals - ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    // Get hot reviews
    suspend fun getHotReviews(): List<HotReview> {
        return try {
            println("FirebaseRepository: Fetching hot reviews...")
            
            // First try to get reviews with isHot = true
            var result = hotReviewsCollection
                .whereEqualTo("isHot", true)
                .limit(10)
                .get()
                .await()
            
            var reviews = result.toObjects(HotReview::class.java)
            println("FirebaseRepository: Found ${reviews.size} hot reviews with isHot=true")
            
            // If no results, try to get all reviews and filter in memory
            if (reviews.isEmpty()) {
                println("FirebaseRepository: No hot reviews found, trying all reviews...")
                result = hotReviewsCollection
                    .limit(20)
                    .get()
                    .await()
                
                reviews = result.toObjects(HotReview::class.java)
                println("FirebaseRepository: Found ${reviews.size} total reviews")
            }
            
            reviews
        } catch (e: Exception) {
            println("FirebaseRepository: Error fetching hot reviews - ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    // Get all hotel deals
    suspend fun getAllDeals(): List<Deal> {
        return try {
            dealsCollection
                .limit(50)
                .get()
                .await()
                .toObjects(Deal::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Get all hot reviews
    suspend fun getAllHotReviews(): List<HotReview> {
        return try {
            hotReviewsCollection
                .limit(50)
                .get()
                .await()
                .toObjects(HotReview::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Get deals by location
    suspend fun getDealsByLocation(location: String): List<Deal> {
        return try {
            dealsCollection
                .whereEqualTo("hotelLocation", location)
                .whereEqualTo("isActive", true)
                .limit(20)
                .get()
                .await()
                .toObjects(Deal::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Get deals by price range
    suspend fun getDealsByPriceRange(minPrice: Double, maxPrice: Double): List<Deal> {
        return try {
            dealsCollection
                .whereGreaterThanOrEqualTo("discountPricePerNight", minPrice)
                .whereLessThanOrEqualTo("discountPricePerNight", maxPrice)
                .whereEqualTo("isActive", true)
                .limit(20)
                .get()
                .await()
                .toObjects(Deal::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Get deals by rating
    suspend fun getDealsByRating(minRating: Double): List<Deal> {
        return try {
            dealsCollection
                .whereGreaterThanOrEqualTo("rating", minRating)
                .whereEqualTo("isActive", true)
                .limit(20)
                .get()
                .await()
                .toObjects(Deal::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Get all hotels
    suspend fun getAllHotels(): List<Hotel> {
        return try {
            hotelsCollection
                .limit(50)
                .get()
                .await()
                .toObjects(Hotel::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Get hotel by ID
    suspend fun getHotelById(hotelId: String): Hotel? {
        return try {
            hotelsCollection
                .document(hotelId)
                .get()
                .await()
                .toObject(Hotel::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    // Test Firebase connection
    suspend fun testFirebaseConnection(): Boolean {
        return try {
            println("FirebaseRepository: Testing Firebase connection...")
            println("FirebaseRepository: Project ID: ${firestore.app.options.projectId}")
            println("FirebaseRepository: App Name: ${firestore.app.name}")
            println("FirebaseRepository: API Key: ${firestore.app.options.apiKey}")
            
            // Test with a simple read operation
            val testDoc = firestore.collection("test").document("connection")
            val docSnapshot = testDoc.get().await()
            
            println("FirebaseRepository: Test document read successful")
            
            // If document doesn't exist, create it
            if (!docSnapshot.exists()) {
                println("FirebaseRepository: Test document doesn't exist, creating it...")
                testDoc.set(mapOf(
                    "timestamp" to System.currentTimeMillis(),
                    "projectId" to firestore.app.options.projectId,
                    "appName" to firestore.app.name,
                    "test" to "connection_test"
                )).await()
                println("FirebaseRepository: Test document created successfully")
            } else {
                println("FirebaseRepository: Test document already exists")
            }
            
            println("FirebaseRepository: Firebase connection successful!")
            true
        } catch (e: Exception) {
            println("FirebaseRepository: Firebase connection failed - ${e.message}")
            println("FirebaseRepository: Error type: ${e.javaClass.simpleName}")
            println("FirebaseRepository: Error details: ${e.toString()}")
            e.printStackTrace()
            false
        }
    }
    
    // Get collection counts for debugging
    suspend fun getCollectionCounts(): Map<String, Int> {
        return try {
            val dealsCount = dealsCollection.get().await().size()
            val reviewsCount = hotReviewsCollection.get().await().size()
            val hotelsCount = hotelsCollection.get().await().size()
            
            mapOf(
                "deals" to dealsCount,
                "hot_reviews" to reviewsCount,
                "hotels" to hotelsCount
            )
        } catch (e: Exception) {
            println("FirebaseRepository: Error getting collection counts - ${e.message}")
            mapOf()
        }
    }
    
    // Initialize database structure
    suspend fun initializeDatabase(): Boolean {
        return try {
            println("FirebaseRepository: Initializing database structure...")
            
            // Create collections by adding a test document to each
            val testData = mapOf(
                "initialized" to true,
                "timestamp" to System.currentTimeMillis(),
                "version" to "1.0"
            )
            
            // Initialize deals collection
            dealsCollection.document("_init").set(testData).await()
            println("FirebaseRepository: Deals collection initialized")
            
            // Initialize hot_reviews collection
            hotReviewsCollection.document("_init").set(testData).await()
            println("FirebaseRepository: Hot reviews collection initialized")
            
            // Initialize hotels collection
            hotelsCollection.document("_init").set(testData).await()
            println("FirebaseRepository: Hotels collection initialized")
            
            println("FirebaseRepository: Database structure initialized successfully!")
            true
        } catch (e: Exception) {
            println("FirebaseRepository: Error initializing database - ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    // Comprehensive search across all collections
    suspend fun searchAll(query: String): Map<String, List<Any>> {
        return try {
            println("FirebaseRepository: Comprehensive search for: $query")
            
            val results = mutableMapOf<String, List<Any>>()
            
            // Search hotels
            val hotels = searchHotels(query)
            results["hotels"] = hotels
            
            // Search deals
            val deals = searchDeals(query)
            results["deals"] = deals
            
            // Search hot reviews
            val reviews = searchHotReviews(query)
            results["reviews"] = reviews
            
            println("FirebaseRepository: Search results - Hotels: ${hotels.size}, Deals: ${deals.size}, Reviews: ${reviews.size}")
            results
        } catch (e: Exception) {
            println("FirebaseRepository: Error in comprehensive search: ${e.message}")
            e.printStackTrace()
            mapOf()
        }
    }
    
    // Enhanced hotel search with multiple fields including location
    suspend fun searchHotels(query: String): List<Hotel> {
        return try {
            println("FirebaseRepository: Searching hotels for: $query")
            
            // Get all hotels and filter in memory for better search
            val allHotels = hotelsCollection
                .limit(100)
                .get()
                .await()
                .toObjects(Hotel::class.java)
            
            // Filter hotels by multiple fields including location matching
            val filteredHotels = allHotels.filter { hotel ->
                val searchQuery = query.lowercase()
                val hotelName = hotel.hotelName.lowercase()
                val hotelAddress = hotel.hotelAddress.lowercase()
                val description = hotel.description.lowercase()
                val typeId = hotel.typeId.lowercase()
                
                // Basic field matching
                hotelName.contains(searchQuery) ||
                hotelAddress.contains(searchQuery) ||
                description.contains(searchQuery) ||
                typeId.contains(searchQuery) ||
                
                // Enhanced location matching
                matchesLocation(searchQuery, hotelAddress) ||
                matchesLocation(searchQuery, hotelName) ||
                matchesLocation(searchQuery, description)
            }
            
            println("FirebaseRepository: Found ${filteredHotels.size} hotels for query: $query")
            filteredHotels
        } catch (e: Exception) {
            println("FirebaseRepository: Error searching hotels: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    // Enhanced deal search with location matching
    suspend fun searchDeals(query: String): List<Deal> {
        return try {
            println("FirebaseRepository: Searching deals for: $query")
            
            // Get all deals and filter in memory
            val allDeals = dealsCollection
                .limit(100)
                .get()
                .await()
                .toObjects(Deal::class.java)
            
            // Filter deals by multiple fields including location
            val filteredDeals = allDeals.filter { deal ->
                val searchQuery = query.lowercase()
                val hotelName = deal.hotelName.lowercase()
                val hotelLocation = deal.hotelLocation.lowercase()
                val description = deal.description.lowercase()
                val roomType = deal.roomType.lowercase()
                
                // Basic field matching
                hotelName.contains(searchQuery) ||
                hotelLocation.contains(searchQuery) ||
                description.contains(searchQuery) ||
                roomType.contains(searchQuery) ||
                deal.amenities.any { it.lowercase().contains(searchQuery) } ||
                
                // Enhanced location matching
                matchesLocation(searchQuery, hotelLocation) ||
                matchesLocation(searchQuery, hotelName) ||
                matchesLocation(searchQuery, description)
            }
            
            println("FirebaseRepository: Found ${filteredDeals.size} deals for query: $query")
            filteredDeals
        } catch (e: Exception) {
            println("FirebaseRepository: Error searching deals: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    // Enhanced review search with location matching
    suspend fun searchHotReviews(query: String): List<HotReview> {
        return try {
            println("FirebaseRepository: Searching hot reviews for: $query")
            
            // Get all reviews and filter in memory
            val allReviews = hotReviewsCollection
                .limit(100)
                .get()
                .await()
                .toObjects(HotReview::class.java)
            
            // Filter reviews by multiple fields including location
            val filteredReviews = allReviews.filter { review ->
                val searchQuery = query.lowercase()
                val hotelName = review.hotelName.lowercase()
                val location = review.location.lowercase()
                
                // Basic field matching
                hotelName.contains(searchQuery) ||
                location.contains(searchQuery) ||
                
                // Enhanced location matching
                matchesLocation(searchQuery, location) ||
                matchesLocation(searchQuery, hotelName)
            }
            
            println("FirebaseRepository: Found ${filteredReviews.size} hot reviews for query: $query")
            filteredReviews
        } catch (e: Exception) {
            println("FirebaseRepository: Error searching hot reviews: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    // Helper function to match locations with various formats
    private fun matchesLocation(searchQuery: String, text: String): Boolean {
        val locationMappings = mapOf(
            // Ho Chi Minh City variations
            "ho chi minh" to listOf("ho chi minh", "hồ chí minh", "tp. hồ chí minh", "tp hcm", "hcm", "saigon", "sài gòn", "quận 1", "quận 2", "quận 3", "quận 4", "quận 5", "quận 6", "quận 7", "quận 8", "quận 9", "quận 10", "quận 11", "quận 12", "thủ đức", "bình thạnh", "tân bình", "phú nhuận", "gò vấp", "bình tân", "tân phú", "hóc môn", "củ chi", "nhà bè", "cần giờ"),
            
            // Vung Tau variations
            "vung tau" to listOf("vung tau", "vũng tàu", "vt", "bà rịa vũng tàu", "ba ria vung tau"),
            
            // Da Nang variations
            "da nang" to listOf("da nang", "đà nẵng", "danang", "dn"),
            
            // Hanoi variations
            "hanoi" to listOf("hanoi", "hà nội", "hn", "hoàn kiếm", "ba đình", "đống đa", "hai bà trưng", "cầu giấy", "thanh xuân", "hoàng mai", "long biên", "tây hồ", "nam từ liêm", "bắc từ liêm", "hà đông", "sơn tây", "mê linh", "đông anh", "gia lâm", "sóc sơn", "thạch thất", "quốc oai", "chương mỹ", "thanh oai", "thường tín", "phú xuyên", "ứng hòa", "mỹ đức"),
            
            // Nha Trang variations
            "nha trang" to listOf("nha trang", "nt", "khánh hòa", "khanh hoa"),
            
            // Phu Quoc variations
            "phu quoc" to listOf("phu quoc", "phú quốc", "pq", "kiên giang", "kien giang"),
            
            // Hue variations
            "hue" to listOf("hue", "huế", "thừa thiên huế", "thua thien hue"),
            
            // Hoi An variations
            "hoi an" to listOf("hoi an", "hội an", "quảng nam", "quang nam"),
            
            // Sapa variations
            "sapa" to listOf("sapa", "sa pa", "sapa", "lào cai", "lao cai"),
            
            // Ha Long variations
            "ha long" to listOf("ha long", "hạ long", "quảng ninh", "quang ninh"),
            
            // Can Tho variations
            "can tho" to listOf("can tho", "cần thơ", "ct", "đồng bằng sông cửu long", "dong bang song cuu long"),
            
            // Dalat variations
            "dalat" to listOf("dalat", "đà lạt", "da lat", "lâm đồng", "lam dong")
        )
        
        // Check if search query matches any location variations
        locationMappings.forEach { (key, variations) ->
            if (searchQuery.contains(key)) {
                return variations.any { variation -> text.contains(variation) }
            }
        }
        
        // Also check reverse - if text contains location variations
        locationMappings.forEach { (key, variations) ->
            if (variations.any { variation -> text.contains(variation) }) {
                return searchQuery.contains(key) || variations.any { variation -> searchQuery.contains(variation) }
            }
        }
        
        return false
    }
    
    // Get all hotels for search results (fallback)
    suspend fun getAllHotelsForSearch(): List<Hotel> {
        return try {
            hotelsCollection
                .limit(100)
                .get()
                .await()
                .toObjects(Hotel::class.java)
        } catch (e: Exception) {
            println("FirebaseRepository: Error getting all hotels - ${e.message}")
            emptyList()
        }
    }
    
    // Debug method to inspect actual data structure
    suspend fun inspectDataStructure(): Map<String, Any?> {
        return try {
            println("FirebaseRepository: Inspecting data structure...")
            
            // Get a sample deal
            val dealSample = dealsCollection.limit(1).get().await()
            val dealFields = if (dealSample.documents.isNotEmpty()) {
                dealSample.documents[0].data?.keys?.toList() ?: emptyList()
            } else {
                emptyList()
            }
            
            // Get a sample hot review
            val reviewSample = hotReviewsCollection.limit(1).get().await()
            val reviewFields = if (reviewSample.documents.isNotEmpty()) {
                reviewSample.documents[0].data?.keys?.toList() ?: emptyList()
            } else {
                emptyList()
            }
            
            // Get a sample hotel
            val hotelSample = hotelsCollection.limit(1).get().await()
            val hotelFields = if (hotelSample.documents.isNotEmpty()) {
                hotelSample.documents[0].data?.keys?.toList() ?: emptyList()
            } else {
                emptyList()
            }
            
            val result = mapOf<String, Any?>(
                "deal_fields" to dealFields,
                "review_fields" to reviewFields,
                "hotel_fields" to hotelFields,
                "deal_count" to dealsCollection.get().await().size(),
                "review_count" to hotReviewsCollection.get().await().size(),
                "hotel_count" to hotelsCollection.get().await().size()
            )
            
            println("FirebaseRepository: Data structure inspection: $result")
            result
        } catch (e: Exception) {
            println("FirebaseRepository: Error inspecting data structure: ${e.message}")
            mapOf("error" to e.message)
        }
    }
    
    // Clear all collections (for testing)
    suspend fun clearAllData() {
        try {
            // Clear deals
            val dealsSnapshot = dealsCollection.get().await()
            dealsSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            
            // Clear hot reviews
            val reviewsSnapshot = hotReviewsCollection.get().await()
            reviewsSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            
            // Clear hotels
            val hotelsSnapshot = hotelsCollection.get().await()
            hotelsSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }
}
