package com.tdc.nhom6.roomio.adapters

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.models.AccountModel
import com.tdc.nhom6.roomio.databinding.ItemAdminAccountBinding

class AccountAdapter(
    private var list: List<AccountModel> = emptyList(),
    private val onEditClick: (AccountModel) -> Unit
) : RecyclerView.Adapter<AccountAdapter.AccountViewHolder>() {

    companion object {
        const val ROLE_ADMIN = "admin"
        const val ROLE_OWNER = "owner"
        const val ROLE_LETAN= "letan"
        const val ROLE_XULYDON = "xulydon"
        const val ROLE_DONPHONG = "donphong"
        const val ROLE_USER = "user"
    }

    private var sortedList: List<AccountModel> = emptyList()

    inner class AccountViewHolder(val binding: ItemAdminAccountBinding)
        : RecyclerView.ViewHolder(binding.root)

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newList: List<AccountModel>) {
        list = newList
        sortedList = list.sortedWith(compareBy { roleOrder(it.roleId) })
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateItemRole(userId: String, newRoleId: String) {
        val item = list.find { it.userId == userId }
        item?.roleId = newRoleId
        sortedList = list.sortedWith(compareBy { roleOrder(it.roleId) })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val binding = ItemAdminAccountBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AccountViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        val item = sortedList[position]

        val acountBinding = holder.binding
        acountBinding.tvUserId.text = "UserID: ${item.userId}"
        acountBinding.tvFullName.text = item.fullName

        Glide.with(acountBinding.imgAvatar.context)
            .load(item.avatarUrl)
            .placeholder(R.drawable.ic_not_image)
            .circleCrop()
            .into(acountBinding.imgAvatar)

        val roleName = when(item.roleId.lowercase()) {
            ROLE_ADMIN -> "Quản Trị Viên"
            ROLE_OWNER -> "Chủ Khách Sạn"
            ROLE_LETAN -> "Lễ Tân"
            ROLE_XULYDON -> "Xử Lý Đơn"
            ROLE_DONPHONG -> "Dọn Phòng"
            ROLE_USER -> "Người Dùng"
            else -> "Người Dùng"
        }

        acountBinding.tvRole.text = roleName

        when (item.roleId.lowercase()) {
            ROLE_ADMIN -> {
                acountBinding.tvRole.setTextColor(Color.RED);
                setCardBorderColor(acountBinding.cardAccount, Color.RED)
            }
            ROLE_OWNER -> {
                acountBinding.tvRole.setTextColor(Color.parseColor("#FF9800"));
                setCardBorderColor(acountBinding.cardAccount, Color.parseColor("#FF9800"))
            }
            ROLE_LETAN -> {
                acountBinding.tvRole.setTextColor(Color.parseColor("#FF00FF"));
                setCardBorderColor(acountBinding.cardAccount, Color.MAGENTA)
            }
            ROLE_XULYDON -> {
                acountBinding.tvRole.setTextColor(Color.parseColor("#76EE00"));
                setCardBorderColor(acountBinding.cardAccount, Color.parseColor("#76EE00"))
            }
            ROLE_DONPHONG -> {
                acountBinding.tvRole.setTextColor(Color.BLUE);
                setCardBorderColor(acountBinding.cardAccount, Color.BLUE)
            }
            ROLE_USER-> {
                acountBinding.tvRole.setTextColor(Color.BLACK)
                acountBinding.tvRole.setBackgroundResource(R.drawable.bg_role_default);
                setCardBorderColor(acountBinding.cardAccount, Color.GRAY)
            }
            else -> {
                acountBinding.tvRole.setTextColor(Color.BLACK)
                acountBinding.tvRole.setBackgroundResource(R.drawable.bg_role_default);
                setCardBorderColor(acountBinding.cardAccount, Color.GRAY)
            }
        }

        acountBinding.btnEdit.setOnClickListener { onEditClick(item) }
    }

    override fun getItemCount() = sortedList.size

    private fun setCardBorderColor(card: MaterialCardView, color: Int) {
        card.strokeColor = color
        card.strokeWidth = 3
    }

    private fun roleOrder(roleId: String): Int {
        return when(roleId.lowercase()) {
            ROLE_ADMIN -> 0
            ROLE_OWNER -> 1
            else -> 2
        }
    }
}
