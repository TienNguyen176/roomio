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
        loadInvoicesRealtime()
        setupSearch()
    }

    private fun setupRecyclerView() {
        paymentAdapter = PaymentApproverAdapter(paymentList) { item ->
            //Toast.makeText(this, "Clicked: ${item.invoiceId}", Toast.LENGTH_SHORT).show()
            showStatusDialog(item)
        }
        binding.rcvPayments.adapter = paymentAdapter
        binding.rcvPayments.layoutManager = LinearLayoutManager(this)
    }

    private fun setupSearch() {
        binding.edtSearch.addTextChangedListener { editable ->
            val query = editable.toString().lowercase()
            val filtered = if (query.isEmpty()) {
                fullPaymentList
            } else {
                fullPaymentList.filter {
                    it.invoiceId?.lowercase()?.contains(query) == true ||
                            it.status?.lowercase()?.contains(query) == true
                }
            }
            paymentAdapter.setData(filtered)
        }
    }

    private fun showStatusDialog(item: PaymentItemModel) {
        val statuses = arrayOf("paid", "cancelled")
        val selectedIndex = statuses.indexOf(item.status)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Cập nhật trạng thái hóa đơn")
            .setSingleChoiceItems(statuses, selectedIndex) { dialog, which ->
                val newStatus = statuses[which]
                dialog.dismiss()

                // Hỏi người dùng có muốn cập nhật không
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Xác nhận cập nhật")
                    .setMessage(
                        "Bạn có chắc muốn đổi trạng thái hóa đơn:\n" +
                                "• Mã hóa đơn: ${item.invoiceId}\n" +
                                "• Trạng thái mới: $newStatus"
                    )
                    .setPositiveButton("Cập nhật") { _, _ ->
                        updateInvoiceStatus(item.invoiceId!!, item.bookingId!!, newStatus)
                    }
                    .setNegativeButton("Hủy", null)
                    .show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun updateInvoiceStatus(invoiceId: String, bookingId: String, newStatus: String) {

        val invoiceUpdates = hashMapOf<String, Any>(
            "paymentStatus" to newStatus
        )

        // Update invoices
        db.collection("invoices")
            .document(invoiceId)
            .update(invoiceUpdates)
            .addOnSuccessListener {

                // Xác định trạng thái booking tương ứng
                val bookingStatus = when (newStatus) {
                    "paid" -> "confirm"
                    "cancelled" -> "cancelled"
                    else -> "pending"
                }

                // Update bookings
                db.collection("bookings")
                    .document(bookingId)
                    .update("status", bookingStatus)
                    .addOnSuccessListener {
                        Toast.makeText(
                            this,
                            "Đã cập nhật hóa đơn & trạng thái booking!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Lỗi cập nhật booking!", Toast.LENGTH_SHORT).show()
                    }

            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi cập nhật hóa đơn!", Toast.LENGTH_SHORT).show()
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
                            val status = invoiceDoc.getString("paymentStatus") ?: "unknown"
                            val createdAt = invoiceDoc.getTimestamp("createdAt")

                            val bookingDoc = bookingMap[bookingId]
                            val checkInDate = bookingDoc?.getTimestamp("checkInDate")
                            val checkOutDate = bookingDoc?.getTimestamp("checkOutDate")

                            val item = PaymentItemModel(
                                bookingId = bookingId,
                                invoiceId = invoiceId,
                                checkInDate = checkInDate,
                                checkOutDay = checkOutDate,
                                totalPaidAmount = totalAmount,
                                createdAt = createdAt,
                                status = status
                            )

                            fullPaymentList.add(item)
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
