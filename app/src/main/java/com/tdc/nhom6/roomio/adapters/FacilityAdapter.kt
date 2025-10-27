// adapters/FacilityAdapter.kt

package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.ItemFacilityLayoutBinding
import com.tdc.nhom6.roomio.models.Facility

class FacilityAdapter(
    private val facilities: List<Facility>,
    // Set này dùng để lưu các ID của tiện ích được chọn
    private val selectedFacilityIds: MutableSet<String>
) : RecyclerView.Adapter<FacilityAdapter.FacilityViewHolder>() {

    class FacilityViewHolder(val binding: ItemFacilityLayoutBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FacilityViewHolder {
        val binding = ItemFacilityLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FacilityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FacilityViewHolder, position: Int) {
        val facility = facilities[position]
        val isSelected = selectedFacilityIds.contains(facility.id)

        holder.binding.tvFacilityName.text = facility.facilities_name
        holder.binding.cbSelected.isChecked = isSelected

        // Thiết lập giao diện CardView khi được chọn/không được chọn
        val context = holder.itemView.context
        // Giả định bạn có màu blue_light và white trong res/values/colors.xml
        val colorRes = if (isSelected) R.color.light_blue else R.color.white

        // Giả định ItemFacilityBinding có CardView ID là 'cardFacility'
        holder.binding.cardFacility.setCardBackgroundColor(context.getColor(colorRes))

        holder.binding.root.setOnClickListener {
            // Xử lý logic chọn/bỏ chọn
            if (selectedFacilityIds.contains(facility.id)) {
                selectedFacilityIds.remove(facility.id)
            } else {
                selectedFacilityIds.add(facility.id)
            }
            notifyItemChanged(position) // Cập nhật giao diện của item hiện tại
        }
    }

    override fun getItemCount() = facilities.size
}