package com.tdc.nhom6.roomio.activities.owner

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tdc.nhom6.roomio.adapters.ReviewAdapter
import com.tdc.nhom6.roomio.databinding.ActivityHotelReviewLayoutBinding
import com.tdc.nhom6.roomio.models.Review

class HotelReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHotelReviewLayoutBinding
    private val db = FirebaseFirestore.getInstance()

    private var hotelId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHotelReviewLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hotelId = intent.getStringExtra("hotelId")

        if (hotelId == null) {
            Toast.makeText(this, "Không tìm thấy hotelId!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Setup Back button
        binding.rowBack.setOnClickListener { finish() }

        // RecyclerView
        binding.rvReviews.layoutManager = LinearLayoutManager(this)

        // Load data
        loadReviews()
    }


    // ==========================================
    // LOAD REVIEWS (LẤY CẢ reviewId)
    // ==========================================
    private fun loadReviews() {

        db.collection("hotels")
            .document(hotelId!!)
            .collection("reviews")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->

                val list = snapshot.documents.map { doc ->
                    val review = doc.toObject(Review::class.java)!!
                    review.reviewId = doc.id   // GÁN REVIEW ID VÀO MODEL
                    review
                }

                binding.rvReviews.adapter = ReviewAdapter(hotelId!!, list)
                binding.tvTotalReview.text = "Đánh giá (${list.size})"
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi tải đánh giá: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
