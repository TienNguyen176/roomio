package com.tdc.nhom6.roomio.activities.admin

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.adapters.HotelTypeAdminAdapter
import com.tdc.nhom6.roomio.databinding.HotelTypesLayoutBinding
import com.tdc.nhom6.roomio.models.HotelTypeModel

class HotelTypesActivity : AppCompatActivity() {
    private lateinit var binding: HotelTypesLayoutBinding

    private lateinit var adapter: HotelTypeAdminAdapter
    private val typeList = mutableListOf<HotelTypeModel>()
    private var selectedIndex: Int? = null
    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    companion object {
        const val TYPE_NAME = "type_name"
        const val DESCRIPTION = "description"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HotelTypesLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Quản lý loại khách sạn"

        setupRecyclerView()
        loadHotelTypesRealtime()
        setEvent()
    }

    private fun setupRecyclerView() {
        adapter = HotelTypeAdminAdapter(typeList) { type, position ->
            selectedIndex = position
            binding.edtHotelTypeName.setText(type.type_name)
            binding.edtHotelTypeDesc.setText(type.description)
        }

        binding.rvListHotelTypes.layoutManager = LinearLayoutManager(this)
        binding.rvListHotelTypes.adapter = adapter
    }

    private fun setEvent() {
        binding.apply {
            btnAddType.setOnClickListener { confirmAddDialog() }
            btnUpdateType.setOnClickListener { confirmUpdateDialog() }
            btnDeleteType.setOnClickListener { confirmDeleteDialog() }
        }
    }

    private fun loadHotelTypesRealtime() {
        listener = db.collection("hotelTypes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Lỗi đọc dữ liệu", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(HotelTypeModel::class.java) ?: emptyList()
                Log.d("Firestore", "Mapped list: $list")
                Log.d("RecyclerView", "Adapter item count: ${adapter.itemCount}")

                typeList.clear()
                typeList.addAll(list)
                adapter.updateList(typeList)
            }
    }

    private fun confirmAddDialog() {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận thêm")
            .setMessage("Bạn có chắc muốn thêm loại khách sạn này không?")
            .setPositiveButton("Thêm") { _, _ -> addHotelType() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun confirmUpdateDialog() {
        val index = selectedIndex
        if (index == null || index !in typeList.indices) {
            showToast("Vui lòng chọn loại khách sạn để cập nhật")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Xác nhận cập nhật")
            .setMessage("Bạn có chắc muốn cập nhật loại khách sạn này không?")
            .setPositiveButton("Cập nhật") { _, _ -> updateHotelType() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun confirmDeleteDialog() {
        val index = selectedIndex
        if (index == null || index !in typeList.indices) {
            showToast("Vui lòng chọn loại khách sạn để xóa")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc muốn xóa loại khách sạn này không?")
            .setPositiveButton("Xóa") { _, _ -> deleteHotelType() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    @SuppressLint("DefaultLocale")
    private fun addHotelType() {
        val name = binding.edtHotelTypeName.text.toString().trim()
        val desc = binding.edtHotelTypeDesc.text.toString().trim()

        if (name.isEmpty()) {
            showToast("Vui lòng nhập tên loại khách sạn")
            return
        }

        val counterRef: DocumentReference = db.collection("counters").document("hotelTypeCounter")

        db.runTransaction { transaction ->
            val snapshot = transaction.get(counterRef)
            val current = snapshot.getLong("current") ?: 0
            val next = current + 1
            transaction.update(counterRef, "current", next)

            val typeId = String.format("type_%02d", next)

            val type = HotelTypeModel(typeId, name, desc, Timestamp.now())

            val docRef = db.collection("hotelTypes").document(typeId)
            transaction.set(docRef, type)
            null
        }.addOnSuccessListener {
            showToast("Đã thêm loại khách sạn thành công!")
            clearForm()
        }.addOnFailureListener { e ->
            Log.w("Firestore", "Lỗi transaction", e)
            showToast("Lỗi khi thêm loại khách sạn!")
        }
    }

    private fun updateHotelType() {
        val index = selectedIndex
        if (index == null || index !in typeList.indices) {
            showToast("Vui lòng chọn loại khách sạn để cập nhật")
            return
        }

        val type = typeList[index]
        val name = binding.edtHotelTypeName.text.toString().trim()
        val desc = binding.edtHotelTypeDesc.text.toString().trim()

        if (name.isEmpty()) {
            showToast("Tên loại khách sạn không được để trống")
            return
        }

        type.type_id?.let {
            db.collection("hotelTypes").document(it)
                .update(mapOf(TYPE_NAME to name, DESCRIPTION to desc))
                .addOnSuccessListener {
                    showToast("Đã cập nhật loại khách sạn thành công!")
                    clearForm()
                    selectedIndex = null
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Lỗi khi cập nhật: ${e.message}")
                    showToast("Lỗi khi cập nhật dữ liệu!")
                }
        }
    }

    private fun deleteHotelType() {
        val index = selectedIndex
        if (index == null || index !in typeList.indices) {
            showToast("Vui lòng chọn loại khách sạn để xóa")
            return
        }
        val type = typeList[index]
        type.type_id?.let {
            db.collection("hotelTypes").document(it)
                .delete()
                .addOnSuccessListener {
                    showToast("Đã xóa loại khách sạn")
                    clearForm()
                    selectedIndex = null
                }
                .addOnFailureListener {
                    showToast("Lỗi khi xóa")
                }
        }
    }

    private fun clearForm() {
        binding.edtHotelTypeName.text?.clear()
        binding.edtHotelTypeDesc.text?.clear()
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }
}
