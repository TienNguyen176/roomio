package com.tdc.nhom6.roomio.adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Visibility
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.ItemReviewHotelBinding
import com.tdc.nhom6.roomio.models.Reply
import com.tdc.nhom6.roomio.models.Review
import java.text.SimpleDateFormat
import java.util.Locale

class ReviewHotelAdapter(
    private val context: Context,
    private val listReviews: MutableList<Review>
) : RecyclerView.Adapter<ReviewHotelAdapter.ReviewViewHolder>() {

    private val db = FirebaseFirestore.getInstance()

    class ReviewViewHolder(val binding: ItemReviewHotelBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val binding = ItemReviewHotelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReviewViewHolder(binding)
    }

    override fun getItemCount(): Int = listReviews.size

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = listReviews[position]
        val binding = holder.binding

        if(review.comment.isNotEmpty()){
            binding.tvContent.visibility=View.VISIBLE
        }else{
            binding.tvContent.visibility=View.GONE
        }
        binding.tvContent.text = review.comment

        binding.reviewRatingBar.rating = review.rating.toFloat()

        review.createdAt?.let {
            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            binding.tvTime.text = formatter.format(it.toDate())
        }

        binding.tvFullName.text = context.getString(R.string.anonymous_user)
        binding.imgAvatar.setImageResource(R.drawable.profiles)

        if (review.userId.isNotEmpty()) {
            db.collection("users")
                .document(review.userId)
                .get()
                .addOnSuccessListener { userDocument ->
                    val userName = userDocument.getString("username")
                    val userAvatarUrl = userDocument.getString("avatar")

                    binding.tvFullName.text = userName ?: context.getString(R.string.anonymous_user)

                    if (!userAvatarUrl.isNullOrEmpty()) {
                        Glide.with(context)
                            .load(userAvatarUrl)
                            .placeholder(R.drawable.profiles)
                            .into(binding.imgAvatar)
                    } else {
                        binding.imgAvatar.setImageResource(R.drawable.profiles)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ReviewAdapter", "Lỗi tải thông tin user: ${review.userId}", e)
                    binding.tvFullName.text = context.getString(R.string.anonymous_user)
                }
        } else {
            binding.tvFullName.text = context.getString(R.string.anonymous_user)
        }
        if (review.hotelId.isNotEmpty()) {
            review.reviewId?.let {
                db.collection("hotels")
                    .document(review.hotelId)
                    .collection("reviews")
                    .document(it)
                    .collection("replies")
                    .orderBy("createdAt", Query.Direction.ASCENDING)
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        val repliesList: MutableList<Reply> = mutableListOf()
                        if (!querySnapshot.isEmpty) {

                            for (document in querySnapshot.documents) {
                                val reply = document.toObject(Reply::class.java)
                                if (reply != null) {
                                    repliesList.add(reply)
                                }
                            }

                            binding.listReplies.visibility = View.VISIBLE
                            val replyAdapter = ReplyAdapter(context, repliesList)
                            binding.listReplies.adapter = replyAdapter

                            if (binding.listReplies.layoutManager == null) {
                                binding.listReplies.layoutManager = LinearLayoutManager(context)
                            }
                        } else {
                            binding.listReplies.visibility = View.GONE
                            Log.d("firebase","reply rong")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("ReviewAdapter", "Lỗi tải replies cho review ID: ${review.reviewId}", e)
                        binding.listReplies.visibility = View.GONE
                    }
            }
        }

    }
}