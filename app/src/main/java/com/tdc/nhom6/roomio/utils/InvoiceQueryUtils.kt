package com.tdc.nhom6.roomio.utils

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

object InvoiceQueryUtils {
    /**
     * Creates a list of queries to find invoices by bookingId or reservationId
     * Tries multiple field names and formats for maximum compatibility
     */
    fun createInvoiceQueries(
        firestore: FirebaseFirestore,
        bookingId: String,
        reservationId: String
    ): List<Query> {
        val queries = mutableListOf<Query>()
        val bookingIdInt = bookingId.toIntOrNull()
        val reservationIdInt = reservationId.toIntOrNull()
        
        // Try bookingId as String
        queries += firestore.collection("invoices").whereEqualTo("bookingId", bookingId)
        
        // Try bookingId as Int
        if (bookingIdInt != null) {
            queries += firestore.collection("invoices").whereEqualTo("bookingId", bookingIdInt)
        }
        
        // Try reservationId as String
        queries += firestore.collection("invoices").whereEqualTo("reservationId", reservationId)
        
        // Try reservationId as Int
        if (reservationIdInt != null) {
            queries += firestore.collection("invoices").whereEqualTo("reservationId", reservationIdInt)
        }
        
        // Try using FieldPath for nested or alternative field names
        try {
            queries += firestore.collection("invoices").whereEqualTo(FieldPath.of("bookingRef", "id"), bookingId)
        } catch (_: Exception) {}
        
        return queries
    }

    /**
     * Filters invoice documents by bookingId or reservationId in memory
     */
    fun filterInvoicesInMemory(
        documents: List<com.google.firebase.firestore.DocumentSnapshot>,
        bookingId: String,
        reservationId: String
    ): List<com.google.firebase.firestore.DocumentSnapshot> {
        val bookingIdInt = bookingId.toIntOrNull()
        val reservationIdInt = reservationId.toIntOrNull()
        
        return documents.filter { doc ->
            val docBookingId = doc.get("bookingId")?.toString()
                ?: doc.get("booking_id")?.toString()
                ?: doc.get("bookingDocId")?.toString()
                ?: doc.get("booking_doc_id")?.toString()
            val docReservationId = doc.get("reservationId")?.toString()
                ?: doc.get("reservation_id")?.toString()
            
            docBookingId == bookingId || docReservationId == reservationId ||
            docBookingId?.toIntOrNull() == bookingIdInt ||
            docReservationId?.toIntOrNull() == reservationIdInt
        }
    }

    /**
     * Extracts totalAmount from invoice document, handling different data types
     */
    fun extractTotalAmount(doc: com.google.firebase.firestore.DocumentSnapshot): Double {
        return when (val totalAmount = doc.get("totalAmount")) {
            is Number -> totalAmount.toDouble()
            is java.math.BigDecimal -> totalAmount.toDouble()
            is String -> totalAmount.toDoubleOrNull() ?: 0.0
            else -> {
                // Fallback to other field names
                doc.getDouble("totalPaidAmount") ?: doc.getDouble("paidAmount") ?: 0.0
            }
        }
    }
}


