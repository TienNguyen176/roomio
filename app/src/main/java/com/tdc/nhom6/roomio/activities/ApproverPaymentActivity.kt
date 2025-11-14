package com.tdc.nhom6.roomio.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.PaymentApproverAdapter
import com.tdc.nhom6.roomio.databinding.ApproverPaymentLayoutBinding
import com.tdc.nhom6.roomio.models.PaymentItemModel
import java.text.SimpleDateFormat
import java.util.Locale

class ApproverPaymentActivity : AppCompatActivity() {

    private lateinit var binding: ApproverPaymentLayoutBinding
    private lateinit var paymentAdapter: PaymentApproverAdapter
    private val paymentList = mutableListOf<PaymentItemModel>()
    private val fullPaymentList = mutableListOf<PaymentItemModel>()
    private val db = FirebaseFirestore.getInstance()

    val dateFormat = SimpleDateFormat("dd MMM > hh:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ApproverPaymentLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Danh sách đơn hàng"

        setupRecyclerView()
        loadBookingsFromFirestore()

        setEvent()
    }

    private fun setEvent() {
        binding.edtSearch.addTextChangedListener { editable ->
            val query = editable.toString().lowercase()
            val filtered = if (query.isEmpty()) {
                fullPaymentList
            } else {
                fullPaymentList.filter {
                    it.bookingId?.lowercase()?.contains(query) == true ||
                            it.status?.lowercase()?.contains(query) == true
                }
            }
            paymentAdapter.setData(filtered)
        }
    }

    private fun setupRecyclerView() {
        paymentAdapter = PaymentApproverAdapter(paymentList) { item ->
            Toast.makeText(this, "Clicked: ${item.bookingId}", Toast.LENGTH_SHORT).show()
        }
        binding.rcvPayments.adapter = paymentAdapter
        binding.rcvPayments.layoutManager = LinearLayoutManager(this)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadBookingsFromFirestore() {
        db.collection("bookings")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Toast.makeText(this, "Failed to load bookings: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    fullPaymentList.clear()
                    for (document in snapshots) {
                        Log.d("Firestore", "Doc ID: ${document.id}, Data: ${document.data}")

                        val bookingId = document.id
                        val checkInDate = document.getTimestamp("checkInDate")?.toDate()?.let { dateFormat.format(it) } ?: ""
                        val checkOutDate = document.getTimestamp("checkOutDate")?.toDate()?.let { dateFormat.format(it) } ?: ""
                        val totalPaidAmount = (document.get("totalPaidAmount") as? Number)?.toDouble() ?: 0.0
                        val status = document.getString("paymentStatus") ?: "unknown"

                        val item = PaymentItemModel(
                            bookingId = bookingId,
                            checkInDate = checkInDate,
                            checkOutDay = checkOutDate,
                            totalPaidAmount = totalPaidAmount,
                            status = status
                        )
                        fullPaymentList.add(item)
                    }
                    // Hiển thị toàn bộ danh sách ban đầu
                    paymentAdapter.setData(fullPaymentList)
                }
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
