package com.tdc.nhom6.roomio.adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.activities.HotelDetailActivity.Companion.db
import com.tdc.nhom6.roomio.databinding.ItemReviewReplyBinding // Đảm bảo import đúng binding
import com.tdc.nhom6.roomio.models.Reply // Đảm bảo import đúng model
import java.text.SimpleDateFormat
import java.util.Locale

class ReplyAdapter(
    private val context: Context,
    private val listReplies: List<Reply>
) : RecyclerView.Adapter<ReplyAdapter.ReplyViewHolder>() {

    class ReplyViewHolder(val binding: ItemReviewReplyBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReplyViewHolder {
        val binding = ItemReviewReplyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReplyViewHolder(binding)
    }

    override fun getItemCount(): Int = listReplies.size

    override fun onBindViewHolder(holder: ReplyViewHolder, position: Int) {
        val reply = listReplies[position]
        val binding = holder.binding

        binding.tvReplyContent.text = reply.message

        reply.createdAt?.let {
            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            binding.tvReplyTime.text = formatter.format(it.toDate())
        }

        if (reply.userId.isNotEmpty()) {
            db.collection("users")
                .document(reply.userId)
                .get()
                .addOnSuccessListener { userDocument ->
                    val userName = userDocument.getString("username")
                    val userAvatarUrl = userDocument.getString("avatar")

                    binding.tvReplyUserName.text = userName ?: context.getString(R.string.anonymous_user)

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
                    Log.e("ReviewAdapter", "Lỗi tải thông tin user: ${reply.userId}", e)
                    binding.tvReplyUserName.text = context.getString(R.string.anonymous_user)
                }
        } else {
            binding.tvReplyUserName.text = context.getString(R.string.anonymous_user)
        }
    }
}