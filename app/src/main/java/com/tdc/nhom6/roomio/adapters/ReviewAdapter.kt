package com.tdc.nhom6.roomio.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tdc.nhom6.roomio.databinding.ItemReviewLayoutBinding
import com.tdc.nhom6.roomio.models.Reply
import com.tdc.nhom6.roomio.models.Review
import java.text.SimpleDateFormat
import java.util.*

class ReviewAdapter(
    private val hotelId: String,
    private val reviews: List<Review>
) : RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder>() {

    inner class ReviewViewHolder(val binding: ItemReviewLayoutBinding)
        : RecyclerView.ViewHolder(binding.root)

    private val db = FirebaseFirestore.getInstance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val binding = ItemReviewLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ReviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val rv = reviews[position]

        holder.binding.apply {

            // --- SET REVIEW CONTENT ---
            tvUserName.text = rv.userName
            ratingBar.rating = rv.rating.toFloat()
            tvComment.text = rv.comment

            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            tvDate.text = sdf.format(rv.createdAt.toDate())

            // --- LOAD REPLIES ---
            loadReplies(rv, this)

            // --- SEND REPLY ---
            btnSendReply.setOnClickListener {
                sendReply(rv, edtReply.text.toString(), this)
            }
        }
    }

    override fun getItemCount(): Int = reviews.size


    // ===============================
    // LOAD REPLIES
    // ===============================
    private fun loadReplies(review: Review, binding: ItemReviewLayoutBinding) {

        if (review.reviewId.isNullOrEmpty()) return

        db.collection("hotels")
            .document(hotelId)
            .collection("reviews")
            .document(review.reviewId!!)
            .collection("replies")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snap ->

                binding.layoutReplies.removeAllViews()

                for (doc in snap) {
                    val reply = doc.toObject(Reply::class.java)

                    val tv = TextView(binding.root.context)
                    tv.text = "- ${reply.userName}: ${reply.message}"
                    tv.textSize = 14f
                    tv.setTextColor(Color.DKGRAY)
                    tv.setPadding(10, 6, 0, 6)

                    binding.layoutReplies.addView(tv)
                }
            }
    }


    // ===============================
    // SEND REPLY
    // ===============================
    private fun sendReply(review: Review, message: String, binding: ItemReviewLayoutBinding) {

        if (message.isBlank()) return
        if (review.reviewId.isNullOrEmpty()) return

        val user = FirebaseAuth.getInstance().currentUser ?: return

        val reply = Reply(
            userId = user.uid,
            userName = user.displayName ?: "Người dùng",
            message = message,
            createdAt = Timestamp.now()
        )

        db.collection("hotels")
            .document(hotelId)
            .collection("reviews")
            .document(review.reviewId!!)
            .collection("replies")
            .add(reply)
            .addOnSuccessListener {
                binding.edtReply.setText("")
                loadReplies(review, binding)
            }
    }
}
