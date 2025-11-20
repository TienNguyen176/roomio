package com.tdc.nhom6.roomio.dialogs

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.adapters.FacilitySelectorAdapter
import com.tdc.nhom6.roomio.databinding.DialogFacilitySelectorLayoutBinding
import com.tdc.nhom6.roomio.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class FacilityPriceSelectorDialog(
    private val context: Context,
    private val scope: CoroutineScope,
    private val preselected: MutableList<FacilityPrice>,
    private val hotelId: String,
    private val onConfirm: (SelectedRates) -> Unit
) {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var binding: DialogFacilitySelectorLayoutBinding
    private lateinit var adapter: FacilitySelectorAdapter
    private var allFacilities: MutableList<Facility> = mutableListOf()

    // Map giá hư hỏng
    val damagePriceMap = mutableMapOf<String, Long>()

    fun show() {
        binding = DialogFacilitySelectorLayoutBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()

        binding.btnConfirm.setOnClickListener {
            val selectedFacilities = adapter.getSelectedFacilities()

            val facilityRates = selectedFacilities.map { f ->
                val priceUse = preselected.find { it.facilityId == f.id }?.price ?: 0L
                FacilityPrice(
                    facilityId = f.id,
                    facilityName = f.facilities_name,
                    price = priceUse
                )
            }

            val now = System.currentTimeMillis().toString()
            val damageRates = selectedFacilities.map { f ->
                DamageLossPrice(
                    facilityId = f.id,
                    price = damagePriceMap[f.id] ?: 0L,
                    statusId = "0",
                    updateDate = now
                )
            }

            onConfirm(SelectedRates(facilityRates, damageRates))
            dialog.dismiss()
        }

        binding.btnAddFacility.setOnClickListener { showAddNewFacilityDialog() }

        loadFacilities {
            if (it.isEmpty()) {
                binding.tvEmpty.visibility = android.view.View.VISIBLE
            } else {
                binding.tvEmpty.visibility = android.view.View.GONE
                setupRecycler(it)
            }
        }

        dialog.show()
    }

    // ========================== LOAD FACILITY ==========================

    private fun loadFacilities(onLoaded: (List<Facility>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val snapshot = db.collection("facilities").get().await()

                val all = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Facility::class.java)?.apply { id = doc.id }
                }.filter { facility ->
                    // Facility global hoặc Facility thuộc hotel
                    facility.hotelId.isEmpty() || facility.hotelId == hotelId
                }.toMutableList()

                allFacilities = all

                withContext(Dispatchers.Main) { onLoaded(all) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Lỗi tải tiện ích!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    // ========================== RECYCLER ==========================

    private fun setupRecycler(facilities: List<Facility>) {
        adapter = FacilitySelectorAdapter(facilities, preselected) { facility, onCancel ->
            showEnterPriceDialog(facility, onCancel)
        }
        binding.rvFacilities.layoutManager = LinearLayoutManager(context)
        binding.rvFacilities.adapter = adapter
    }

    // ========================== NHẬP GIÁ ==========================

    private fun showEnterPriceDialog(facility: Facility, onCancel: () -> Unit) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val edtUse = EditText(context).apply {
            hint = "Giá sử dụng (VND)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(preselected.find { it.facilityId == facility.id }?.price?.toString() ?: "")
        }

        val edtDamage = EditText(context).apply {
            hint = "Giá hư hỏng (VND)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(damagePriceMap[facility.id]?.toString() ?: "")
        }

        layout.addView(edtUse)
        layout.addView(edtDamage)

        AlertDialog.Builder(context)
            .setTitle("Giá cho ${facility.facilities_name}")
            .setView(layout)
            .setPositiveButton("Lưu") { _, _ ->
                val usePrice = edtUse.text.toString().toLongOrNull() ?: 0L
                val damagePrice = edtDamage.text.toString().toLongOrNull() ?: 0L

                preselected.removeAll { it.facilityId == facility.id }
                preselected.add(
                    FacilityPrice(
                        facilityId = facility.id,
                        facilityName = facility.facilities_name,
                        price = usePrice
                    )
                )

                damagePriceMap[facility.id] = damagePrice

                Toast.makeText(context, "Đã lưu: dùng $usePrice • hư hỏng $damagePrice", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy") { _, _ -> onCancel() }
            .show()
    }

    // ========================== THÊM FACILITY MỚI ==========================

    private fun showAddNewFacilityDialog() {
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        val edtName = EditText(context).apply { hint = "Tên tiện ích" }
        val edtDesc = EditText(context).apply { hint = "Mô tả (tùy chọn)" }

        layout.addView(edtName)
        layout.addView(edtDesc)

        AlertDialog.Builder(context)
            .setTitle("Thêm tiện ích mới")
            .setView(layout)
            .setPositiveButton("Thêm") { _, _ ->
                val name = edtName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(context, "Tên không được trống", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val data = mapOf(
                    "facilities_name" to name,
                    "description" to edtDesc.text.toString(),
                    "hotelId" to hotelId
                )

                db.collection("facilities").add(data)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Đã thêm '$name'", Toast.LENGTH_SHORT).show()
                        show() // reload dialog
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Lỗi thêm tiện ích!", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
