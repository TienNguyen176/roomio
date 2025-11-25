package com.tdc.nhom6.roomio.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.databinding.ActivityReviewLayoutBinding
import com.tdc.nhom6.roomio.models.Review

class ReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewLayoutBinding
    private val db = FirebaseFirestore.getInstance()

    private var hotelId: String = ""
    private var bookingId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityReviewLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hotelId = intent.getStringExtra("HOTEL_ID") ?: ""
        bookingId = intent.getStringExtra("BOOKING_ID") ?: ""

        if (hotelId.isEmpty()) {
            Toast.makeText(this, "Hotel ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.btnSubmitReview.setOnClickListener { submitReview() }
    }

    private fun submitReview() {
        val rating = binding.ratingBar.rating.toInt()
        val comment = binding.edtComment.text.toString().trim()

        if (rating == 0) {
            Toast.makeText(this, "Please rate the hotel", Toast.LENGTH_SHORT).show()
            return
        }

        val authUser = FirebaseAuth.getInstance().currentUser
        if (authUser == null) {
            Toast.makeText(this, "You must login", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = authUser.uid

        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { doc ->

                val userName = doc.getString("username") ?: "Unknown"

                val newReviewId = db.collection("hotels")
                    .document(hotelId)
                    .collection("reviews")
                    .document().id

                val review = Review(
                    userId = userId,
                    hotelId = hotelId,
                    userName = userName,
                    rating = rating,
                    comment = comment,
                    createdAt = Timestamp.now()
                )

                db.collection("hotels")
                    .document(hotelId)
                    .collection("reviews")
                    .document(newReviewId)
                    .set(review)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Thank you for your review!", Toast.LENGTH_LONG).show()
                        finish()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

}
