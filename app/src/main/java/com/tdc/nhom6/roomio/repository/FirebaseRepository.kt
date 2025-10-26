package com.tdc.nhom6.roomio.repository

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObject
import com.tdc.nhom6.roomio.model.Deal
import com.tdc.nhom6.roomio.model.HotReview
import com.tdc.nhom6.roomio.model.Hotel
import com.tdc.nhom6.roomio.model.SearchResultItem
import com.tdc.nhom6.roomio.model.SearchResultType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL


class FirebaseRepository {

    // Firebase project configuration - using the correct project ID from google-services.json
    private val projectId = "roomio-2e37f" // Correct Firebase project ID
    private val baseUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"
    val db = Firebase.firestore
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
                    
                    // Get hotels with most reviews (top 5)
                    val topHotels = hotels.sortedByDescending { it.totalReviews }.take(5)
                    
                    Log.d("Firebase", "Load ${topHotels.size} hotels successfully")
                    callback(topHotels)
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

    fun getDeals(callback: (List<Deal>) -> Unit) {
        try {
            db.collection("deals")
                .get()
                .addOnSuccessListener { result ->
                    val deals = mutableListOf<Deal>()
                    for (document in result) {
                        try {
                            Log.d("Firebase", "Deal: ${document.id} => ${document.data}")
                            val deal: Deal = document.toObject<Deal>()
                            deals.add(deal)
                        } catch (e: Exception) {
                            Log.e("Firebase", "Error converting deal document ${document.id}: ${e.message}")
                            // Skip this document and continue with others
                        }
                    }
                    
                    Log.d("Firebase", "Load ${deals.size} deals successfully")
                    callback(deals)
                }
                .addOnFailureListener { exception ->
                    Log.w("Firebase", "Error getting deals.", exception)
                    // Return empty list on error
                    callback(emptyList())
                }
        } catch (e: Exception) {
            Log.e("Firebase", "Exception in getDeals: ${e.message}")
            callback(emptyList())
        }
    }

}