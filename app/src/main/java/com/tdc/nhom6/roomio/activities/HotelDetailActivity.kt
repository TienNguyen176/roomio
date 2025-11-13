package com.tdc.nhom6.roomio.activities

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.PhotoGridAdapter
import com.tdc.nhom6.roomio.adapters.RoomTypeAdapter
import com.tdc.nhom6.roomio.databinding.ActivityHotelDetailBinding
import com.tdc.nhom6.roomio.models.FacilityDamageLossRateModel
import com.tdc.nhom6.roomio.models.FacilityPriceRateModel
import com.tdc.nhom6.roomio.models.HotelModel
import com.tdc.nhom6.roomio.models.RoomImage
import com.tdc.nhom6.roomio.models.RoomType
import com.tdc.nhom6.roomio.models.Service
import com.tdc.nhom6.roomio.adapters.ServiceAdapter
import com.tdc.nhom6.roomio.models.ServiceRate
import java.util.Date

@Suppress("DEPRECATION")
class HotelDetailActivity: AppCompatActivity() {
    private lateinit var binding: ActivityHotelDetailBinding
    private lateinit var roomTypeAdapter: RoomTypeAdapter
    private var listRoomType: MutableList<RoomType> = mutableListOf()
    private var listServices: MutableList<Service> = mutableListOf()
    private var selectedServiceRates: MutableMap<String, Double> = mutableMapOf()
    private lateinit var hotelData: HotelModel
    private lateinit var serviceAdapter: ServiceAdapter
    private lateinit var photoGridAdapter: PhotoGridAdapter
    private var systemBarsInsets: Insets? = null

    // Biến Listener cho Realtime Updates
    private var hotelListener: ListenerRegistration? = null
    private var servicesListener: ListenerRegistration? = null
    private var roomTypesListener: ListenerRegistration? = null



    companion object {
        lateinit var HOTEL_ID:String
        val db = FirebaseFirestore.getInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHotelDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.appBarLayout.updatePadding(
                top = systemBars.top
            )

            v.setPadding(
                systemBars.left,
                0,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        initial()
        setupToolbarScrollEffect()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Dừng lắng nghe khi Activity bị hủy
        hotelListener?.remove()
        servicesListener?.remove()
        roomTypesListener?.remove()
    }


    private fun initial() {
        HOTEL_ID = intent.getStringExtra("HOTEL_ID").toString()
        Log.d("Intent", HOTEL_ID)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = ""
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        serviceAdapter = ServiceAdapter(listServices, selectedServiceRates)
        val spanCount = 3
        binding.gridServices.layoutManager = GridLayoutManager(this, spanCount)
        binding.gridServices.adapter = serviceAdapter

        roomTypeAdapter = RoomTypeAdapter(this, listRoomType, supportFragmentManager)
        binding.listRoomType.layoutManager = LinearLayoutManager(this)
        binding.listRoomType.adapter = roomTypeAdapter

        photoGridAdapter = PhotoGridAdapter(this, listOf())
        binding.gridPhotos.layoutManager = GridLayoutManager(this, 3)
        binding.gridPhotos.adapter = photoGridAdapter

        loadHotel()
        loadRoomTypes()
        loadServices()
    }

    private fun setupToolbarScrollEffect() {
        val maxScrollHeightDp = 300 - binding.appBarLayout.height
        val maxScroll = resources.displayMetrics.density * maxScrollHeightDp

        val solidColor = ContextCompat.getColor(this, R.color.white)

        val controller = WindowCompat.getInsetsController(window, window.decorView)

        controller?.isAppearanceLightStatusBars=false
        binding.toolbar.navigationIcon?.setTint(solidColor)

        binding.scrollView.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->

                val scrollRatio = if (scrollY > 0) {
                    (scrollY.toFloat() / maxScroll).coerceIn(0.0f, 1.0f)
                } else {
                    0.0f
                }

                val alpha = (scrollRatio * 255).toInt()

                val newColor = (alpha shl 24) or (solidColor and 0x00FFFFFF)

                binding.appBarLayout.setBackgroundColor(newColor)

                if (scrollRatio >= 0.7f) {
                    binding.appBarLayout.setBackgroundColor((solidColor))
                    binding.appBarLayout.outlineProvider = ViewOutlineProvider.BOUNDS
                    controller?.isAppearanceLightStatusBars=true
                    binding.toolbar.navigationIcon?.setTint(resources.getColor(R.color.black))
                }else{
                    binding.appBarLayout.outlineProvider = null
                    controller?.isAppearanceLightStatusBars=false
                    binding.toolbar.navigationIcon?.setTint(solidColor)
                }

            }
        )
    }

    private fun loadHotel() {
        hotelListener?.remove()

        hotelListener = db.collection("hotels")
            .document(HOTEL_ID)
            .addSnapshotListener { snapShot, exception ->
                if (exception != null) {
                    Log.e("firestore", "Error listening to hotel document:", exception)
                    return@addSnapshotListener
                }

                if (snapShot != null && snapShot.exists()) {
                    try {
                        val hotel = snapShot.toObject(HotelModel::class.java)

                        if (hotel != null) {
                            hotelData = hotel

                            Glide.with(this).load(hotelData.images?.get(0)).into(binding.imgHotel)
                            binding.tvAddress.text = hotelData.hotelAddress
                            binding.ratingBar.rating = hotelData.averageRating.toFloat()
                            binding.tvReviews.text = "(${hotelData.totalReviews})"
                            binding.tvNameHotel.text = hotelData.hotelName
                            binding.tvDescription.text = hotelData.description

                            val images = hotelData.images ?: listOf()
                            photoGridAdapter = PhotoGridAdapter(this, images)
                            binding.gridPhotos.adapter = photoGridAdapter

                        } else {
                            Log.e("firestore", "Error converting document to HotelModel object.")
                        }

                    } catch (e: Exception) {
                        Log.e("firestore", "Loi chuyen doi Hotel", e)
                    }
                } else {
                    Log.w("firestore", "Hotel document with ID $HOTEL_ID does not exist or was deleted.")
                }
            }
    }

    fun loadServices() {
        servicesListener?.remove()

        servicesListener = db.collection("serviceRates")
            .whereEqualTo("hotel_id", HOTEL_ID)
            .addSnapshotListener { querySnapshot, exception ->
                if (exception != null) {
                    Log.e("Firestore", "Error listening to Service Rates: ", exception)
                    return@addSnapshotListener
                }

                if (querySnapshot != null) {
                    listServices.clear()
                    selectedServiceRates.clear()

                    // Extract service IDs and prices from serviceRates
                    val serviceRateMap = querySnapshot.documents.associate { doc ->
                        val serviceId = doc.getString("service_id") ?: ""
                        val price = doc.getDouble("price") ?: 0.0
                        serviceId to price
                    }

                    val serviceIdsToFetch = serviceRateMap.keys.filter { it.isNotEmpty() }

                    if (serviceIdsToFetch.isEmpty()) {
                        serviceAdapter.notifyDataSetChanged()
                        return@addSnapshotListener
                    }

                    // Populate selectedServiceRates with prices
                    selectedServiceRates.putAll(serviceRateMap)

                    var fetchCount = 0
                    for (serviceId in serviceIdsToFetch) {
                        db.collection("services")
                            .document(serviceId)
                            .get()
                            .addOnSuccessListener { serviceDocument ->
                                val service = serviceDocument.toObject(Service::class.java)
                                if (service != null) {
                                    listServices.add(service)
                                }
                            }
                            .addOnCompleteListener {
                                fetchCount++
                                if (fetchCount == serviceIdsToFetch.size) {
                                    serviceAdapter.notifyDataSetChanged()
                                }
                            }
                    }
                }
            }
    }

    private fun loadRoomTypes() {
        roomTypesListener?.remove()

        val collectionPath = "roomTypes"
        roomTypesListener = db.collection(collectionPath)
            .whereEqualTo("hotelId",HOTEL_ID)
            .addSnapshotListener { result, exception ->
                if (exception != null) {
                    Log.e("Firestore", "Error listening to RoomTypes: ", exception)
                    return@addSnapshotListener
                }

                if (result != null) {
                    listRoomType.clear()
                    for (document in result) {
                        try {
                            val roomType = document.toObject(RoomType::class.java)
                            listRoomType.add(roomType)
                        } catch (e: Exception) {
                            Log.e("Firestore", "Lỗi chuyển đổi dữ liệu cho RoomType: ${document.id}", e)
                        }
                    }
                    roomTypeAdapter.notifyDataSetChanged()
                }
            }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun addServiceRateToFirebase(hotelId: String, serviceId: String, price: Double) {
        val newRate = ServiceRate(
            hotel_id = hotelId,
            service_id = serviceId,
            price = price,
        )
        db.collection("serviceRates")
            .add(newRate)
            .addOnSuccessListener {
                println("Thêm giá dịch vụ thành công: Hotel $hotelId, Service $serviceId")
            }
            .addOnFailureListener { e ->
                println("Lỗi khi thêm giá dịch vụ: $e")
            }
    }

    private fun addRoomTypeFireBase() {
        val ROOM_ID = "DLXR-003"
        val sampleFacilityRates = listOf(
            FacilityPriceRateModel(
                facilityId = "facilities_01",
                price = 0.0,
                updateDate = Date().time.toFirestoreTimestamp(),
            )
        )

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
            thumbnail = true,
            uploadedAt = Date().time
        )
        val myTypeSafeRoom = RoomType(
            roomTypeId = ROOM_ID,
            hotelId = HOTEL_ID,
            typeName = "SUPERIOR DOUBLE ROOM",
            pricePerNight = 1300000.0,
            maxPeople = 6,
            area = 120,
            viewId = "0",
            roomImages = listOf(newImage,newImage,newImage)
        )
        addRoomType(myTypeSafeRoom, ROOM_ID)
        sampleFacilityRates.forEach { rate -> addFacilityRate(rate, ROOM_ID) }
        sampleDamageLossRates.forEach { rate -> addDamageLossRate(rate, ROOM_ID) }
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

    fun Long.toFirestoreTimestamp(): Timestamp {
        val seconds = this / 1000
        val nanoseconds = (this % 1000) * 1_000_000
        return Timestamp(seconds, nanoseconds.toInt())
    }
}