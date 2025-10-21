package com.tdc.nhom6.roomio.utils

import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.model.Deal
import com.tdc.nhom6.roomio.model.HotReview
import com.tdc.nhom6.roomio.model.Hotel
import kotlinx.coroutines.tasks.await

class FirebaseDataSeeder {
    private val firestore = FirebaseFirestore.getInstance()
    
    suspend fun seedInitialData() {
        try {
            println("FirebaseDataSeeder: Starting data seeding...")
            
            // Clear existing data first
            clearExistingData()
            
            // Seed Hotels
            seedHotels()
            
            // Seed Hot Reviews
            seedHotReviews()
            
            // Seed Hotel Deals
            seedDeals()
            
            println("FirebaseDataSeeder: Data seeding completed successfully!")
        } catch (e: Exception) {
            println("FirebaseDataSeeder: Error seeding data: ${e.message}")
            e.printStackTrace()
            throw e // Re-throw to let caller handle
        }
    }
    
    private suspend fun clearExistingData() {
        try {
            // Clear deals
            val dealsSnapshot = firestore.collection("deals").get().await()
            dealsSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            
            // Clear hot reviews
            val reviewsSnapshot = firestore.collection("hot_reviews").get().await()
            reviewsSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            
            // Clear hotels
            val hotelsSnapshot = firestore.collection("hotels").get().await()
            hotelsSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            
            println("Existing data cleared successfully!")
        } catch (e: Exception) {
            println("Error clearing existing data: ${e.message}")
        }
    }
    
    private suspend fun seedHotels() {
        try {
            println("FirebaseDataSeeder: Seeding hotels...")
            val hotels = listOf(
            Hotel(
                hotelId = "hotel_1",
                ownerId = "owner_1",
                hotelName = "Ares Home",
                hotelAddress = "123 Tran Phu, Vũng Tàu",
                hotelFloors = 15,
                hotelTotalRooms = 120,
                typeId = "luxury",
                description = "Luxury beachfront resort in Vung Tau with stunning ocean views and world-class amenities",
                statusId = "active",
                images = listOf("hotel_64260231_1", "swimming_pool_1"),
                averageRating = 4.5,
                totalReviews = 234,
                pricePerNight = 7000000.0
            ),
            Hotel(
                hotelId = "hotel_2",
                ownerId = "owner_2",
                hotelName = "Imperial Hotel",
                hotelAddress = "456 Le Loi, Vũng Tàu",
                hotelFloors = 18,
                hotelTotalRooms = 180,
                typeId = "luxury",
                description = "Elegant imperial-style hotel in Vung Tau with traditional architecture and modern amenities",
                statusId = "active",
                images = listOf("hotel_del_coronado_views_suite1600x900", "room_640278495"),
                averageRating = 4.5,
                totalReviews = 189,
                pricePerNight = 4000000.0
            ),
            Hotel(
                hotelId = "hotel_3",
                ownerId = "owner_3",
                hotelName = "Saigon Central Hotel",
                hotelAddress = "123 Nguyen Hue, Quận 1, TP. Hồ Chí Minh",
                hotelFloors = 30,
                hotelTotalRooms = 250,
                typeId = "luxury",
                description = "Luxury hotel in the heart of Ho Chi Minh City with modern amenities and excellent service",
                statusId = "active",
                images = listOf("swimming_pool_1", "dsc04512_scaled_1"),
                averageRating = 4.8,
                totalReviews = 456,
                pricePerNight = 3500000.0
            ),
            Hotel(
                hotelId = "hotel_4",
                ownerId = "owner_4",
                hotelName = "Mountain Lodge",
                hotelAddress = "321 Fansipan, Sapa",
                hotelFloors = 8,
                hotelTotalRooms = 80,
                typeId = "mountain_lodge",
                description = "Cozy mountain lodge with breathtaking mountain views and traditional charm",
                statusId = "active",
                images = listOf("room_640278495", "rectangle_copy_2"),
                averageRating = 4.6,
                totalReviews = 95,
                pricePerNight = 1800000.0
            ),
            Hotel(
                hotelId = "hotel_5",
                ownerId = "owner_5",
                hotelName = "City Center Plaza",
                hotelAddress = "654 Le Loi, Quận 3, TP. Hồ Chí Minh",
                hotelFloors = 30,
                hotelTotalRooms = 300,
                typeId = "business",
                description = "Modern business hotel in the city center with excellent connectivity",
                statusId = "active",
                images = listOf("hotel_64260231_1", "rectangle_copy_3"),
                averageRating = 4.5,
                totalReviews = 178,
                pricePerNight = 2200000.0
            ),
            Hotel(
                hotelId = "hotel_6",
                ownerId = "owner_6",
                hotelName = "Luxury Island Resort",
                hotelAddress = "987 Long Beach, Phu Quoc",
                hotelFloors = 12,
                hotelTotalRooms = 100,
                typeId = "luxury_resort",
                description = "Exclusive island resort with private beach and luxury amenities",
                statusId = "active",
                images = listOf("hotel_del_coronado_views_suite1600x900", "swimming_pool_1"),
                averageRating = 4.9,
                totalReviews = 67,
                pricePerNight = 4500000.0
            )
        )
        
            for (hotel in hotels) {
                firestore.collection("hotels").document(hotel.hotelId).set(hotel).await()
            }
            println("FirebaseDataSeeder: Hotels seeded successfully")
        } catch (e: Exception) {
            println("FirebaseDataSeeder: Error seeding hotels: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    private suspend fun seedHotReviews() {
        try {
            println("FirebaseDataSeeder: Seeding hot reviews...")
            val hotReviews = listOf(
            HotReview(
                hotelId = "hotel_1",
                hotelName = "Ares Home",
                hotelImage = "hotel_64260231_1",
                rating = 4.5,
                totalReviews = 234,
                pricePerNight = 7000000.0,
                location = "Vung Tau",
                isHot = true
            ),
            HotReview(
                hotelId = "hotel_2",
                hotelName = "Imperial Hotel",
                hotelImage = "hotel_del_coronado_views_suite1600x900",
                rating = 4.5,
                totalReviews = 189,
                pricePerNight = 4000000.0,
                location = "Vung Tau",
                isHot = true
            ),
            HotReview(
                hotelId = "hotel_3",
                hotelName = "Beachfront Paradise",
                hotelImage = "swimming_pool_1",
                rating = 4.9,
                totalReviews = 210,
                pricePerNight = 250000.0,
                location = "Nha Trang",
                isHot = true
            ),
            HotReview(
                hotelId = "hotel_4",
                hotelName = "Mountain Lodge",
                hotelImage = "room_640278495",
                rating = 4.7,
                totalReviews = 180,
                pricePerNight = 300000.0,
                location = "Sapa",
                isHot = true
            ),
            HotReview(
                hotelId = "hotel_5",
                hotelName = "City Center Plaza",
                hotelImage = "rectangle_copy_2",
                rating = 4.6,
                totalReviews = 95,
                pricePerNight = 180000.0,
                location = "Ho Chi Minh City",
                isHot = true
            )
        )
        
            for (review in hotReviews) {
                firestore.collection("hot_reviews").add(review).await()
            }
            println("FirebaseDataSeeder: Hot reviews seeded successfully")
        } catch (e: Exception) {
            println("FirebaseDataSeeder: Error seeding hot reviews: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    private suspend fun seedDeals() {
        try {
            println("FirebaseDataSeeder: Seeding deals...")
            val deals = listOf(
            Deal(
                hotelName = "Ares Home",
                hotelLocation = "Vũng Tàu",
                description = "Luxury beachfront resort in Vung Tau with stunning ocean views and world-class amenities",
                imageUrl = "hotel_64260231_1",
                originalPricePerNight = 7000000.0,
                discountPricePerNight = 4900000.0,
                discountPercentage = 30,
                validFrom = System.currentTimeMillis(),
                validTo = System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000L), // 30 days
                isActive = true,
                hotelId = "hotel_1",
                roomType = "Deluxe Ocean View",
                amenities = listOf("Free WiFi", "Swimming Pool", "Spa", "Restaurant", "Beach Access", "Water Sports"),
                rating = 4.5,
                totalReviews = 234
            ),
            Deal(
                hotelName = "Imperial Hotel",
                hotelLocation = "Vũng Tàu",
                description = "Elegant imperial-style hotel in Vung Tau with traditional architecture and modern amenities",
                imageUrl = "hotel_del_coronado_views_suite1600x900",
                originalPricePerNight = 4000000.0,
                discountPricePerNight = 2800000.0,
                discountPercentage = 30,
                validFrom = System.currentTimeMillis(),
                validTo = System.currentTimeMillis() + (45 * 24 * 60 * 60 * 1000L), // 45 days
                isActive = true,
                hotelId = "hotel_2",
                roomType = "Executive Suite",
                amenities = listOf("Free WiFi", "Swimming Pool", "Spa", "Restaurant", "Gym", "Rooftop Bar"),
                rating = 4.5,
                totalReviews = 189
            ),
            Deal(
                hotelName = "Beachfront Paradise",
                hotelLocation = "Nha Trang",
                description = "Stunning beachfront hotel with direct beach access and tropical views",
                imageUrl = "swimming_pool_1",
                originalPricePerNight = 2800000.0,
                discountPricePerNight = 1960000.0,
                discountPercentage = 30,
                validFrom = System.currentTimeMillis(),
                validTo = System.currentTimeMillis() + (60 * 24 * 60 * 60 * 1000L), // 60 days
                isActive = true,
                hotelId = "hotel_3",
                roomType = "Ocean View Room",
                amenities = listOf("Free WiFi", "Swimming Pool", "Spa", "Restaurant", "Beach Access", "Water Sports"),
                rating = 4.7,
                totalReviews = 289
            ),
            Deal(
                hotelName = "Mountain Lodge",
                hotelLocation = "Sapa",
                description = "Cozy mountain lodge with breathtaking mountain views and traditional charm",
                imageUrl = "room_640278495",
                originalPricePerNight = 1800000.0,
                discountPricePerNight = 1260000.0,
                discountPercentage = 30,
                validFrom = System.currentTimeMillis(),
                validTo = System.currentTimeMillis() + (90 * 24 * 60 * 60 * 1000L), // 90 days
                isActive = true,
                hotelId = "hotel_4",
                roomType = "Mountain View Room",
                amenities = listOf("Free WiFi", "Fireplace", "Restaurant", "Hiking Tours", "Traditional Spa"),
                rating = 4.6,
                totalReviews = 95
            ),
            Deal(
                hotelName = "City Center Plaza",
                hotelLocation = "Quận 3, TP. Hồ Chí Minh",
                description = "Modern business hotel in the city center with excellent connectivity",
                imageUrl = "rectangle_copy_2",
                originalPricePerNight = 2200000.0,
                discountPricePerNight = 1540000.0,
                discountPercentage = 30,
                validFrom = System.currentTimeMillis(),
                validTo = System.currentTimeMillis() + (20 * 24 * 60 * 60 * 1000L), // 20 days
                isActive = true,
                hotelId = "hotel_5",
                roomType = "Business Room",
                amenities = listOf("Free WiFi", "Business Center", "Restaurant", "Gym", "Concierge"),
                rating = 4.5,
                totalReviews = 178
            ),
            Deal(
                hotelName = "Luxury Island Resort",
                hotelLocation = "Phu Quoc",
                description = "Exclusive island resort with private beach and luxury amenities",
                imageUrl = "rectangle_copy_3",
                originalPricePerNight = 4500000.0,
                discountPricePerNight = 3150000.0,
                discountPercentage = 30,
                validFrom = System.currentTimeMillis(),
                validTo = System.currentTimeMillis() + (15 * 24 * 60 * 60 * 1000L), // 15 days
                isActive = true,
                hotelId = "hotel_6",
                roomType = "Villa Suite",
                amenities = listOf("Free WiFi", "Private Beach", "Spa", "Restaurant", "Gym", "Water Sports", "Butler Service"),
                rating = 4.9,
                totalReviews = 67
            )
        )
        
            for (deal in deals) {
                firestore.collection("deals").add(deal).await()
            }
            println("FirebaseDataSeeder: Deals seeded successfully")
        } catch (e: Exception) {
            println("FirebaseDataSeeder: Error seeding deals: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}
