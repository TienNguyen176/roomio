package com.tdc.nhom6.roomio.activities

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.adapters.PhotoGridAdapter
import com.tdc.nhom6.roomio.adapters.RoomTypeAdapter
import com.tdc.nhom6.roomio.databinding.ActivityHotelDetailBinding
import com.tdc.nhom6.roomio.models.FacilityDamageLossRateModel
import com.tdc.nhom6.roomio.models.FacilityPriceRateModel
import com.tdc.nhom6.roomio.models.RoomImage
import com.tdc.nhom6.roomio.models.RoomType
import java.util.Date

class HotelDetailActivity: AppCompatActivity() {
    private lateinit var binding: ActivityHotelDetailBinding
    private lateinit var roomTypeAdapter: RoomTypeAdapter
    private lateinit var listPhoto: List<String>
    private var listRoomType: MutableList<RoomType> = mutableListOf()
    private var listRoomImage: MutableList<RoomImage> = mutableListOf()
    companion object {
        val HOTEL_ID = "hotel-001"
        val db = FirebaseFirestore.getInstance()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHotelDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initialLayout()
        initialAction()
    }

    private fun initialLayout() {

        listPhoto = listOf(
            "https://cf.bstatic.com/xdata/images/hotel/max1024x768/738129545.jpg?k=e715bb89becf44df04deb39a73a43cdc6048c6fd1f04eda71630eba62d771337&o=",
            "https://cf.bstatic.com/xdata/images/hotel/max1024x768/738129545.jpg?k=e715bb89becf44df04deb39a73a43cdc6048c6fd1f04eda71630eba62d771337&o=",
            "https://cf.bstatic.com/xdata/images/hotel/max1024x768/738129545.jpg?k=e715bb89becf44df04deb39a73a43cdc6048c6fd1f04eda71630eba62d771337&o=",
            "https://cf.bstatic.com/xdata/images/hotel/max1024x768/738129545.jpg?k=e715bb89becf44df04deb39a73a43cdc6048c6fd1f04eda71630eba62d771337&o=",
            "https://cf.bstatic.com/xdata/images/hotel/max1024x768/738129545.jpg?k=e715bb89becf44df04deb39a73a43cdc6048c6fd1f04eda71630eba62d771337&o=",
            "https://cf.bstatic.com/xdata/images/hotel/max1024x768/738129545.jpg?k=e715bb89becf44df04deb39a73a43cdc6048c6fd1f04eda71630eba62d771337&o=",
            "https://cf.bstatic.com/xdata/images/hotel/max1024x768/738129545.jpg?k=e715bb89becf44df04deb39a73a43cdc6048c6fd1f04eda71630eba62d771337&o="
        )

//        binding.gridPhotos.adapter = PhotoGridAdapter(this, listPhoto)
        roomTypeAdapter=RoomTypeAdapter(this,listRoomType,supportFragmentManager)
        binding.listRoomType.adapter= roomTypeAdapter
//        addRoomTypeFireBase()
        loadRoomTypes()
    }


    private fun initialAction() {
    }

    private fun loadRoomTypes() {
        val collectionPath = "roomTypes"

        db.collection(collectionPath)
            .whereEqualTo("hotelId",HOTEL_ID)
            .get()
            .addOnSuccessListener { result ->
                // Xóa dữ liệu cũ và thêm dữ liệu mới
                listRoomType.clear()
                for (document in result) {
                    try {
                        // Chuyển Document thành đối tượng RoomType
                        val roomType = document.toObject(RoomType::class.java)
                        listRoomType.add(roomType)
                    } catch (e: Exception) {
                        Log.e("Firestore", "Lỗi chuyển đổi dữ liệu cho RoomType: ${document.id}", e)
                    }
                }

                roomTypeAdapter.notifyDataSetChanged()

            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Lỗi khi tải RoomTypes: ", exception)
            }
    }


    private fun addRoomTypeFireBase() {
        val ROOM_ID = "DLXR-002"
        // 1. Tạo đối tượng Facility Rates
        val sampleFacilityRates = listOf(
            FacilityPriceRateModel(
                facilityId = "facilities_01",
                price = 0.0,
                updateDate = Date().time.toFirestoreTimestamp() // Lấy timestamp hiện tại
            )
        )

        // 2. Tạo đối tượng Damage/Loss Rates
        val sampleDamageLossRates = listOf(
            FacilityDamageLossRateModel(
                facilityId = "facilities_01",
                statusId = "0",
                price = 100000.0,
                updateDate = Date().time.toFirestoreTimestamp()
            ),
            FacilityDamageLossRateModel(
                facilityId = "facilities_01",
                statusId = "1",
                price = 200000.0,
                updateDate = Date().time.toFirestoreTimestamp()
            ),
        )


        val newImage = RoomImage(
            imageUrl = "https://cf.bstatic.com/xdata/images/hotel/max1024x768/738129545.jpg?k=e715bb89becf44df04deb39a73a43cdc6048c6fd1f04eda71630eba62d771337&o=",
            isThumbnail = true,
            uploadedAt = Date().time.toFirestoreTimestamp()
        )
        val myTypeSafeRoom = RoomType(
            typeId = ROOM_ID,
            hotelId = HOTEL_ID,
            typeName = "SUPERIOR DOUBLE ROOM",
            pricePerNight = 1300000000.0,
            maxPeople = 6,
            area = 120,
            viewId = "0",
            roomImages = listOf(newImage,newImage,newImage)
        )
        // Thêm RoomType vào firestore
        addRoomType(myTypeSafeRoom, ROOM_ID)

        // Thêm FacilityRate vào RoomType
        sampleFacilityRates.forEach { rate ->
            addFacilityRate(rate, ROOM_ID)
        }

        sampleDamageLossRates.forEach { rate ->
            addDamageLossRate(rate, ROOM_ID)
        }

    }

    private fun addRoomType(roomType: RoomType, roomTypeIdAbbr: String) {
        db.collection("roomTypes")
            .document(roomTypeIdAbbr)
            .set(roomType)
            .addOnSuccessListener {
                Log.d("Firestore_Add", "RoomType $roomTypeIdAbbr added successfully!")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore_Add", "Error adding RoomType $roomTypeIdAbbr:", e)
            }
    }


    private fun addFacilityRate(rate: FacilityPriceRateModel, roomTypeId: String) {

        db.collection("roomTypes")
            .document(roomTypeId)
            .collection("facilityRates")
            .add(rate)
            .addOnSuccessListener { documentReference ->
                println("FacilityRate added successfully. ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                println("Error adding FacilityRate: $e")
            }
    }

    private fun addDamageLossRate(rate: FacilityDamageLossRateModel, roomTypeId: String) {

        db.collection("roomTypes")
            .document(roomTypeId)
            .collection("damageLossRates")
            .add(rate)
            .addOnSuccessListener { documentReference ->
                println("DamageLossRate added successfully. ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                println("Error adding DamageLossRate: $e")
            }
    }


    private fun Long.toFirestoreTimestamp(): Timestamp {
        val seconds = this / 1000 // Chuyển từ mili giây sang giây
        val nanoseconds = (this % 1000) * 1_000_000 // Phần còn lại là mili giây, chuyển sang nano
        return Timestamp(seconds, nanoseconds.toInt())
    }
}
