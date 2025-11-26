package com.tdc.nhom6.roomio.activities.order

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tdc.nhom6.roomio.adapters.PaymentApproverAdapter
import com.tdc.nhom6.roomio.databinding.ApproverPaymentLayoutBinding
import com.tdc.nhom6.roomio.models.PaymentItemModel
import java.util.*

class ApproverPaymentActivity : AppCompatActivity() {

    private lateinit var binding: ApproverPaymentLayoutBinding
    private lateinit var paymentAdapter: PaymentApproverAdapter
    private val paymentList = mutableListOf<PaymentItemModel>()
    private val fullPaymentList = mutableListOf<PaymentItemModel>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ApproverPaymentLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Danh sách đơn hàng"

        setupRecyclerView()
        loadStatusMap {
            loadInvoicesRealtime()
        }
        setupSearch()
    }

    private fun setupRecyclerView() {
        paymentAdapter = PaymentApproverAdapter(paymentList) { item ->
            showStatusDialog(item)
        }
        binding.rcvPayments.adapter = paymentAdapter
        binding.rcvPayments.layoutManager = LinearLayoutManager(this)
    }

    private fun setupSearch() {
        binding.edtSearch.addTextChangedListener { editable ->
            val query = editable.toString().lowercase(Locale.getDefault())
            val filtered = if (query.isEmpty()) fullPaymentList else fullPaymentList.filter {
                it.invoiceId?.lowercase(Locale.getDefault())?.contains(query) == true ||
                        it.status?.lowercase(Locale.getDefault())?.contains(query) == true
            }
            paymentAdapter.setData(filtered)
        }
    }

    private fun showStatusDialog(item: PaymentItemModel) {
        val allowedStatusIds = listOf("pending_payment", "paid", "cancelled")

        db.collection("status")
            .whereIn(FieldPath.documentId(), allowedStatusIds)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) return@addOnSuccessListener

                val statuses = snapshot.documents.map { doc ->
                    val id = doc.id
                    val name = doc.getString("status_name") ?: id
                    id to name
                }

                val statusNames = statuses.map { it.second }.toTypedArray()
                val currentIndex = statuses.indexOfFirst { it.first == item.status }

                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Cập nhật trạng thái hóa đơn")
                    .setSingleChoiceItems(statusNames, currentIndex) { dialog, which ->
                        val newStatusId = statuses[which].first
                        val newStatusName = statuses[which].second
                        dialog.dismiss()

                        // Xác nhận update
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Xác nhận cập nhật")
                            .setMessage(
                                "Bạn có chắc muốn đổi trạng thái hóa đơn:\n" +
                                        "• Mã hóa đơn: ${item.invoiceId}\n" +
                                        "• Trạng thái mới: $newStatusName"
                            )
                            .setPositiveButton("Cập nhật") { _, _ ->
                                updateInvoiceStatus(item.invoiceId!!, item.bookingId!!, newStatusId)
                            }
                            .setNegativeButton("Hủy", null)
                            .show()
                    }
                    .setNegativeButton("Hủy", null)
                    .show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Không tải được trạng thái!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateInvoiceStatus(invoiceId: String, bookingId: String, newStatus: String) {
        val invoiceUpdates = hashMapOf<String, Any>("paymentStatus" to newStatus)
        db.collection("invoices").document(invoiceId)
            .update(invoiceUpdates)
            .addOnSuccessListener {
                val bookingStatus = when (newStatus) {
                    "paid" -> "confirmed"
                    "cancelled" -> "cancelled"
                    else -> "pending"
                }
                db.collection("bookings").document(bookingId)
                    .update("status", bookingStatus)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Đã cập nhật hóa đơn & booking!", Toast.LENGTH_SHORT).show()
                        loadInvoicesRealtime()
                    }
                    .addOnFailureListener { Toast.makeText(this, "Lỗi cập nhật booking!", Toast.LENGTH_SHORT).show() }
            }
            .addOnFailureListener { Toast.makeText(this, "Lỗi cập nhật hóa đơn!", Toast.LENGTH_SHORT).show() }
    }

    private fun loadStatusMap(onComplete: () -> Unit) {
        db.collection("status")
            .get()
            .addOnSuccessListener { snapshot ->
                val map = mutableMapOf<String, String>()
                for (doc in snapshot.documents) {
                    map[doc.id] = doc.getString("status_name") ?: doc.id
                }
                paymentAdapter.statusNameMap = map
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadInvoicesRealtime() {
        db.collection("invoices")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(this, "Failed to load invoices: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                fullPaymentList.clear()
                val invoiceDocs = snapshot.documents
                val bookingIds = invoiceDocs.mapNotNull { it.getString("bookingId") }.distinct()
                if (bookingIds.isEmpty()) {
                    paymentAdapter.setData(fullPaymentList)
                    return@addSnapshotListener
                }

                db.collection("bookings")
                    .whereIn(FieldPath.documentId(), bookingIds)
                    .get()
                    .addOnSuccessListener { bookingSnapshots ->
                        val bookingMap = bookingSnapshots.documents.associateBy({ it.id }, { it })
                        for (invoiceDoc in invoiceDocs) {
                            val invoiceId = invoiceDoc.id
                            val bookingId = invoiceDoc.getString("bookingId") ?: ""
                            val totalAmount = (invoiceDoc.get("totalAmount") as? Number)?.toDouble() ?: 0.0
                            val status = invoiceDoc.getString("paymentStatus") ?: "pending"
                            val createdAt = invoiceDoc.getTimestamp("createdAt")
                            val bookingDoc = bookingMap[bookingId]
                            val checkInDate = bookingDoc?.getTimestamp("checkInDate")
                            val checkOutDate = bookingDoc?.getTimestamp("checkOutDate")

                            fullPaymentList.add(
                                PaymentItemModel(
                                    bookingId = bookingId,
                                    invoiceId = invoiceId,
                                    checkInDate = checkInDate,
                                    checkOutDay = checkOutDate,
                                    totalPaidAmount = totalAmount,
                                    createdAt = createdAt,
                                    status = status
                                )
                            )
                        }
                        paymentAdapter.setData(fullPaymentList)
                    }
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
