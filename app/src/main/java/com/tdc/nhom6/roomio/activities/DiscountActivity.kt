    package com.tdc.nhom6.roomio.activities

    import android.app.Dialog
    import android.os.Bundle
    import android.util.Log
    import android.view.LayoutInflater
    import android.view.View
    import android.view.ViewGroup
    import android.widget.RadioButton
    import android.widget.Toast
    import androidx.appcompat.app.AppCompatActivity
    import androidx.recyclerview.widget.LinearLayoutManager
    import com.google.firebase.firestore.FirebaseFirestore
    import com.tdc.nhom6.roomio.adapters.DiscountAdapter
    import com.tdc.nhom6.roomio.databinding.ActivityDiscountLayoutBinding
    import com.tdc.nhom6.roomio.databinding.DialogAddDiscountLayoutBinding
    import com.tdc.nhom6.roomio.models.Diiiiiscount
    import java.text.SimpleDateFormat
    import java.util.*

    class DiscountActivity : AppCompatActivity() {

        private lateinit var binding: ActivityDiscountLayoutBinding
        private val db = FirebaseFirestore.getInstance()

        private val discountList = mutableListOf<Diiiiiscount>()
        private lateinit var adapter: DiscountAdapter

        private var hotelId: String? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            binding = ActivityDiscountLayoutBinding.inflate(layoutInflater)
            setContentView(binding.root)

            hotelId = intent.getStringExtra("hotelId")
            if (hotelId.isNullOrBlank()) {
                Toast.makeText(this, "Không tìm thấy hotelId", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            setupUI()
            listenRealtime()
        }

        // ---------------------- UI ----------------------

        private fun setupUI() = with(binding) {

            rowExit.setOnClickListener { finish() }

            adapter = DiscountAdapter(discountList) { item ->
                showAddEditDialog(item)
            }

            rvCoupons.layoutManager = LinearLayoutManager(this@DiscountActivity)
            rvCoupons.adapter = adapter

            btnAddCoupon.setOnClickListener {
                showAddEditDialog(null)
            }
        }

        // ---------------------- REALTIME ----------------------

        private fun listenRealtime() {
            db.collection("discounts")
                .whereEqualTo("hotelId", hotelId)
                .addSnapshotListener { snapshot, _ ->

                    if (snapshot == null) return@addSnapshotListener

                    discountList.clear()

                    for (doc in snapshot.documents) {
                        val item = doc.toObject(Diiiiiscount::class.java)
                        if (item != null) {
                            item.id = doc.id

                            // 🔥 Auto reset khi sang ngày mới
                            checkAndResetInfiniteDiscount(item)

                            discountList.add(item)
                        }
                    }

                    val empty = discountList.isEmpty()
                    binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                    binding.rvCoupons.visibility = if (empty) View.GONE else View.VISIBLE

                    adapter.notifyDataSetChanged()
                }
        }

        // ---------------------- RESET LOGIC ----------------------

        private fun checkAndResetInfiniteDiscount(dc: Diiiiiscount) {
            if (dc.type != "infinite") return

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val last = dc.lastResetDate ?: ""

            if (today == last) return  // 🔥 Không reset trong cùng 1 ngày

            // 🔥 Reset
            val updates = mapOf(
                "availableCount" to dc.dailyReset,
                "lastResetDate" to today
            )

            db.collection("discounts")
                .document(dc.id!!)
                .update(updates)
                .addOnSuccessListener {
                    Log.d("DISCOUNT", "Reset ${dc.discountName} về ${dc.dailyReset}")
                }
        }

        // ---------------------- DIALOG ----------------------

        private fun showAddEditDialog(edit: Diiiiiscount?) {
            val dialog = Dialog(this)
            val dlg = DialogAddDiscountLayoutBinding.inflate(layoutInflater)
            dialog.setContentView(dlg.root)

            // ---- TITLE ----
            dlg.tvDialogTitle.text =
                if (edit == null) "Thêm mã giảm giá" else "Chỉnh sửa mã giảm giá"

            // ---- FILL DATA WHEN EDIT ----
            if (edit != null) {
                dlg.etName.setText(edit.discountName)
                dlg.etDescription.setText(edit.description)
                dlg.etPercent.setText(edit.discountPercent.toString())
                dlg.etMaxDiscount.setText(edit.maxDiscount.toString())
                dlg.etMinOrder.setText(edit.minOrder.toString())

                if (edit.type == "infinite") {
                    dlg.rbInfinite.isChecked = true
                    dlg.etDailyReset.visibility = View.VISIBLE
                    dlg.etDailyReset.setText(edit.dailyReset.toString())
                } else {
                    dlg.rbLimited.isChecked = true
                    dlg.etDailyReset.visibility = View.GONE
                    dlg.etAvailableCount.setText(edit.availableCount.toString())
                }
            }

            // ---- SWITCH TYPE ----
            dlg.rgType.setOnCheckedChangeListener { _, checked ->
                val btn = dlg.rgType.findViewById<RadioButton>(checked)
                dlg.etDailyReset.visibility =
                    if (btn.id == dlg.rbInfinite.id) View.VISIBLE else View.GONE
            }

            // ---- SAVE ----
            dlg.btnSave.setOnClickListener {

                val name = dlg.etName.text.toString()
                val desc = dlg.etDescription.text.toString()
                val percent = dlg.etPercent.text.toString().toIntOrNull() ?: 0
                val max = dlg.etMaxDiscount.text.toString().toLongOrNull() ?: 0
                val minOrder = dlg.etMinOrder.text.toString().toLongOrNull() ?: 0

                val type = if (dlg.rbInfinite.isChecked) "infinite" else "limited"

                val dailyReset = dlg.etDailyReset.text.toString().toIntOrNull() ?: 0
                val availableCount =
                    if (type == "infinite") dailyReset
                    else dlg.etAvailableCount.text.toString().toIntOrNull() ?: 0

                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                // ⭐ Firestore cần Map<String, Any>, phải ép kiểu đúng
                val data: Map<String, Any> = mapOf(
                    "hotelId" to hotelId!!,
                    "discountName" to name,
                    "description" to desc,
                    "discountPercent" to percent,
                    "maxDiscount" to max,
                    "minOrder" to minOrder,
                    "type" to type,
                    "dailyReset" to dailyReset,
                    "availableCount" to availableCount,
                    "lastResetDate" to today
                )

                if (edit == null) {
                    db.collection("discounts")
                        .add(data)
                } else {
                    db.collection("discounts")
                        .document(edit.id!!)
                        .update(data)
                }

                dialog.dismiss()
            }

            dialog.show()

            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    }
