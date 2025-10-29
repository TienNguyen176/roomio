package com.tdc.nhom6.roomio.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.ServiceAdapter
import com.tdc.nhom6.roomio.databinding.HotelServicesLayoutBinding
import com.tdc.nhom6.roomio.models.ServiceModel

class HotelServicesActivity : AppCompatActivity() {
    private lateinit var binding: HotelServicesLayoutBinding

    private lateinit var adapter: ServiceAdapter
    private val serviceList = mutableListOf<ServiceModel>()
    private var selectedIndex: Int? = null
    private val db = FirebaseFirestore.getInstance()
    private var serviceListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HotelServicesLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.app_bar_title_service)

        setupRecyclerView()
        loadServices()
        setEvent()
    }

    private fun setEvent() {
        binding.apply {
            btnAddService.setOnClickListener {
                confirmAddDialog()
            }
            btnUpdateService.setOnClickListener {
                confirmUpdateDialog()
            }
            btnDeleteService.setOnClickListener {
                confirmDeleteDialog()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ServiceAdapter(serviceList) { service, position ->
            selectedIndex = position
            binding.edtServiceName.setText(service.service_name)
            binding.edtServiceDesc.setText(service.description)
        }

        binding.rvListServices.layoutManager = LinearLayoutManager(this)
        binding.rvListServices.adapter = adapter
    }

    private fun loadServices() {
        serviceListener = db.collection("services")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    Log.e("Firestore", "Lỗi đọc dữ liệu", error)
                    return@addSnapshotListener
                }
                val list = value?.toObjects(ServiceModel::class.java) ?: emptyList()
                serviceList.clear()
                serviceList.addAll(list)
                adapter.updateList(list)
            }
    }

    private fun confirmAddDialog() {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận thêm")
            .setMessage("Bạn có chắc muốn thêm dịch vụ này không?")
            .setPositiveButton("Thêm") { _, _ -> addService() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun confirmUpdateDialog() {
        val pos = selectedIndex
        if (pos == null || pos < 0 || pos >= serviceList.size) {
            showToast("Vui lòng chọn dịch vụ để cập nhật")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Xác nhận cập nhật")
            .setMessage("Bạn có chắc muốn cập nhật dịch vụ này?")
            .setPositiveButton("Cập nhật") { _, _ -> updateService() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun confirmDeleteDialog() {
        val pos = selectedIndex
        if (pos == null || pos < 0 || pos >= serviceList.size) {
            showToast("Vui lòng chọn dịch vụ để xóa")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc muốn xóa dịch vụ này không?")
            .setPositiveButton("Xóa") { _, _ -> deleteService() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    @SuppressLint("DefaultLocale")
    private fun addService() {
        val name = binding.edtServiceName.text.toString().trim()
        val desc = binding.edtServiceDesc.text.toString().trim()

        if (name.isEmpty()) {
            showToast("Vui lòng nhập tên dịch vụ")
            return
        }

        val counterRef: DocumentReference = db.collection("counters").document("serviceCounter")

        db.runTransaction { transaction ->
            val snapshot = transaction.get(counterRef)
            val current = snapshot.getLong("current") ?: 0
            val next = current + 1

            transaction.update(counterRef, "current", next)

            val serviceId = String.format("service_%02d", next)

            val service = ServiceModel(
                id = serviceId,
                service_name = name,
                description = desc
            )

            val docRef = db.collection("services").document(serviceId)
            transaction.set(docRef, service)

            null
        }.addOnSuccessListener {
            showToast("Đã thêm dịch vụ thành công!")
            clearForm()
        }.addOnFailureListener { e ->
            Log.w("Firestore", "Lỗi transaction", e)
            showToast("Lỗi khi thêm dịch vụ!")
        }
    }

    private fun updateService() {
        val pos = selectedIndex
        if (pos == null || pos < 0 || pos >= serviceList.size) {
            showToast("Vui lòng chọn dịch vụ để cập nhật")
            return
        }

        val service = serviceList[pos]
        val name = binding.edtServiceName.text.toString().trim()
        val desc = binding.edtServiceDesc.text.toString().trim()

        if (name.isEmpty()) {
            showToast("Tên dịch vụ không được để trống")
            return
        }

        if (service.id.isNullOrEmpty()) {
            showToast("Không tìm thấy ID dịch vụ để cập nhật")
            return
        }

        db.collection("services").document(service.id!!)
            .update(
                mapOf(
                    "service_name" to name,
                    "description" to desc
                )
            )
            .addOnSuccessListener {
                showToast("Đã cập nhật dịch vụ thành công!")
                clearForm()
                selectedIndex = null
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Lỗi khi cập nhật: ${e.message}")
                showToast("Lỗi khi cập nhật dữ liệu!")
            }
    }

    private fun deleteService() {
        val pos = selectedIndex
        if (pos == null || pos < 0 || pos >= serviceList.size) {
            showToast("Vui lòng chọn dịch vụ để xóa")
            return
        }

        val service = serviceList[pos]
        service.id?.let {
            db.collection("services").document(it)
                .delete()
                .addOnSuccessListener {
                    showToast("Đã xóa dịch vụ")
                    clearForm()
                    selectedIndex = null
                }
                .addOnFailureListener {
                    showToast("Lỗi khi xóa")
                }
        }
    }

    private fun clearForm() {
        binding.edtServiceName.text?.clear()
        binding.edtServiceDesc.text?.clear()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}