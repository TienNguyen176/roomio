package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.databinding.HotelAdminLayoutBinding

class AdminHotelActivity : AppCompatActivity() {

    private lateinit var binding: HotelAdminLayoutBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private var currentHotelId: String? = null
    // Triple: (ID, Name, Location)
    private var ownedHotels: List<Triple<String, String, String>> = emptyList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HotelAdminLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Không xác định được người dùng. Vui lòng đăng nhập lại.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadHotelData(uid)
        setupButtons()
    }

    /**
     *  Tải tất cả khách sạn mà người dùng sở hữu.
     */
    private fun loadHotelData(uid: String) {
        db.collection("hotels")
            .whereEqualTo("ownerId", uid)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    ownedHotels = result.documents.map { doc ->
                        Triple(
                            doc.id,
                            doc.getString("hotelName") ?: "Khách sạn không tên",
                            doc.getString("hotelLocation") ?: "Không rõ địa điểm"
                        )
                    }

                    if (ownedHotels.size == 1) {
                        // Chọn mặc định nếu chỉ có 1
                        updateSelectedHotel(ownedHotels.first().first, ownedHotels.first().second)
                    } else {
                        // Hiển thị hộp thoại chọn nếu có nhiều
                        showHotelSelectionDialog()
                    }

                } else {
                    binding.tvHotelName.text = "Chưa có khách sạn nào"
                    Toast.makeText(this, "Bạn chưa có khách sạn nào được quản lý.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                Log.e("AdminHotelActivity", "Lỗi tải dữ liệu khách sạn", it)
                Toast.makeText(this, "Lỗi tải dữ liệu khách sạn: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     *  Hiển thị hộp thoại cho người dùng chọn một trong các khách sạn sở hữu.
     */
    private fun showHotelSelectionDialog() {
        val hotelNames = ownedHotels.map { "${it.second} (${it.third})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Chọn Khách sạn quản lý")
            .setItems(hotelNames) { _, which ->
                val (id, name, _) = ownedHotels[which]
                updateSelectedHotel(id, name)
            }
            .setCancelable(false) // Bắt buộc chọn nếu có nhiều khách sạn
            .show()
    }

    /**
     * 🔹 Cập nhật ID khách sạn đang được chọn và hiển thị tên trên UI.
     */
    private fun updateSelectedHotel(hotelId: String, hotelName: String) {
        currentHotelId = hotelId
        binding.tvHotelName.text = hotelName
        // Toast.makeText(this, "Đã chọn quản lý: $hotelName", Toast.LENGTH_SHORT).show()
    }


    /**
     * 🔹 Các nút chức năng trong màn hình admin
     */
    private fun setupButtons() = with(binding) {

        binding.tvHotelName.setOnClickListener {
            if (ownedHotels.size > 1) {
                showHotelSelectionDialog()
            } else if (ownedHotels.isEmpty()) {
                Toast.makeText(this@AdminHotelActivity, "Không có khách sạn nào để chọn.", Toast.LENGTH_SHORT).show()
            }
        }

        val checkHotelId: () -> Boolean = {
            if (currentHotelId == null) {
                Toast.makeText(this@AdminHotelActivity, "Vui lòng chọn hoặc chờ tải dữ liệu khách sạn.", Toast.LENGTH_SHORT).show()
                false
            } else {
                true
            }
        }

        btnRoomManagement.setOnClickListener {
            if (!checkHotelId()) return@setOnClickListener
            val intent = Intent(this@AdminHotelActivity, RoomHotelActivity::class.java)
            intent.putExtra("hotelId", currentHotelId)
            startActivity(intent)
        }

        btnDiscount.setOnClickListener {
            if (!checkHotelId()) return@setOnClickListener
            val intent = Intent(this@AdminHotelActivity, DiscountActivity::class.java)
            intent.putExtra("hotelId", currentHotelId)
            startActivity(intent)
        }

        btnRating.setOnClickListener {
            if (!checkHotelId()) return@setOnClickListener
            val intent = Intent(this@AdminHotelActivity, HotelReviewActivity::class.java)
            intent.putExtra("hotelId", currentHotelId)
            startActivity(intent)
        }

        btnEditHotel.setOnClickListener {
            if (!checkHotelId()) return@setOnClickListener
            val intent = Intent(this@AdminHotelActivity, EditHotelActivity::class.java)
            intent.putExtra("hotelId", currentHotelId)
            startActivity(intent)
        }

        btnRoleManager.setOnClickListener {
            if (!checkHotelId()) return@setOnClickListener
            val intent = Intent(this@AdminHotelActivity, RoleManagerActivity::class.java)
            intent.putExtra("hotelId", currentHotelId)
            startActivity(intent)
        }

        btnViewBooking.setOnClickListener {
            if (!checkHotelId()) return@setOnClickListener
            Toast.makeText(this@AdminHotelActivity, "View Booking for ID: $currentHotelId clicked", Toast.LENGTH_SHORT).show()
        }

        rowExit.setOnClickListener {
            finish()
        }
    }
}