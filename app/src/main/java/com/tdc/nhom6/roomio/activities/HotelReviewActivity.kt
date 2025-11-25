package com.tdc.nhom6.roomio.activities

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

        // Nút quay lại
        binding.rowBack.setOnClickListener { finish() }

        // RecyclerView
        binding.rvReviews.layoutManager = LinearLayoutManager(this)

        // Tải dữ liệu
        loadReviews()
    }


    // ==========================================
    // LOAD REVIEWS + TÍNH TRUNG BÌNH SAO
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
                    review.reviewId = doc.id
                    review
                }

                // Gán dữ liệu lên giao diện
                binding.rvReviews.adapter = ReviewAdapter(hotelId!!, list)
                binding.tvTotalReview.text = "Đánh giá (${list.size})"

                // Nếu có review → tính trung bình
                if (list.isNotEmpty()) {
                    val avgRating = list.map { it.rating }.average().toFloat()

                    // Hiển thị sao trung bình
                    binding.ratingAverage.rating = avgRating

                    // Cập nhật lên Firestore
                    updateRatingInfo(avgRating, list.size)
                } else {
                    binding.ratingAverage.rating = 0f
                    updateRatingInfo(0f, 0)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi tải đánh giá: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }



    // ==========================================
    // CẬP NHẬT averageRating TRÊN FIRESTORE
    // ==========================================
    private fun updateRatingInfo(avg: Float, totalReviews: Int) {

        val data = mapOf(
            "averageRating" to avg,
            "totalReviews" to totalReviews
        )

        db.collection("hotels")
            .document(hotelId!!)
            .update(data)
            .addOnSuccessListener {
                // OK
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi cập nhật rating: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

}
