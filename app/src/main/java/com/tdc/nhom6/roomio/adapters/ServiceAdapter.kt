package com.tdc.nhom6.roomio.models

import android.content.Context
package com.tdc.nhom6.roomio.adapters

import android.app.AlertDialog
import android.text.InputType
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.databinding.ItemFacilityBinding
import com.tdc.nhom6.roomio.databinding.ItemServiceLayoutBinding
import com.tdc.nhom6.roomio.models.Service

class ServiceAdapter(
    private val context: Context,
    private val listFacility: List<Service>
) : RecyclerView.Adapter<ServiceAdapter.FacilityViewHolder>() {
    private val services: List<Service>,
    private val selectedRates: MutableMap<String, Double>
) : RecyclerView.Adapter<ServiceAdapter.ViewHolder>() {

    class FacilityViewHolder(val binding: ItemFacilityBinding) :
    inner class ViewHolder(val binding: ItemServiceLayoutBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FacilityViewHolder {
        val binding = ItemFacilityBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FacilityViewHolder(binding)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemServiceLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FacilityViewHolder, position: Int) {
        val facility = listFacility[position]
    override fun getItemCount() = services.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val service = services[position]
        val binding = holder.binding

        holder.binding.tvFacilityName.text = facility.service_name
        binding.tvServiceName.text = service.service_name

        Glide.with(context)
            .load(facility.iconUrl)
            .into(holder.binding.iconFacility)
    }
        // Khởi tạo trạng thái ban đầu
        val isSelected = selectedRates.containsKey(service.id)
        binding.cbSelected.isChecked = isSelected

        // Khi bấm vào card => toggle checkbox
        binding.cardService.setOnClickListener {
            if (!binding.cbSelected.isChecked) {
                // Tick chọn → mở dialog nhập giá
                val input = EditText(holder.itemView.context).apply {
                    hint = "Nhập giá cho dịch vụ"
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setPadding(50, 40, 50, 40)
                }

                AlertDialog.Builder(holder.itemView.context)
                    .setTitle("Giá cho ${service.service_name}")
                    .setView(input)
                    .setPositiveButton("Lưu") { dialog, _ ->
                        val priceText = input.text.toString().trim()
                        val price = priceText.toDoubleOrNull()
                        if (price != null && price > 0) {
                            selectedRates[service.id] = price
                            binding.cbSelected.isChecked = true
                            Toast.makeText(
                                holder.itemView.context,
                                "Đã thêm ${service.service_name} - $price",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                holder.itemView.context,
                                "Giá không hợp lệ!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("Hủy") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setCancelable(false)
                    .show()
            } else {
                // Nếu bỏ chọn → xóa khỏi selectedRates
                binding.cbSelected.isChecked = false
                selectedRates.remove(service.id)
            }
        }

    override fun getItemCount(): Int = listFacility.size
    }
}