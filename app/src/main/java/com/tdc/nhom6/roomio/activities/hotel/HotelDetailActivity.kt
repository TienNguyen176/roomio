package com.tdc.nhom6.roomio.activities.hotel

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.ViewOutlineProvider
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.PhotoGridAdapter
import com.tdc.nhom6.roomio.adapters.ReviewHotelAdapter
import com.tdc.nhom6.roomio.adapters.RoomTypeAdapter
import com.tdc.nhom6.roomio.databinding.ActivityHotelDetailBinding
import com.tdc.nhom6.roomio.models.HotelModel
import com.tdc.nhom6.roomio.models.Review
import com.tdc.nhom6.roomio.models.RoomType
import com.tdc.nhom6.roomio.models.Service
import com.tdc.nhom6.roomio.models.ServiceHotelAdapter

@Suppress("DEPRECATION")
class HotelDetailActivity: AppCompatActivity() {
    private lateinit var binding: ActivityHotelDetailBinding
    private lateinit var roomTypeAdapter: RoomTypeAdapter
    private var listRoomType: MutableList<RoomType> = mutableListOf()
    private var listServices: MutableList<Service> = mutableListOf()
    lateinit var currentHotel: HotelModel
    private lateinit var serviceAdapter: ServiceHotelAdapter
    private lateinit var photoGridAdapter: PhotoGridAdapter
    private var systemBarsInsets: Insets? = null

    // Biến Listener cho Realtime Updates
    private var hotelListener: ListenerRegistration? = null
    private var servicesListener: ListenerRegistration? = null
    private var roomTypesListener: ListenerRegistration? = null
    private lateinit var reviewAdapter: ReviewHotelAdapter
    private var listReviews: MutableList<Review> = mutableListOf()
    private var reviewsListener: ListenerRegistration? = null


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
        hotelListener?.remove()
        servicesListener?.remove()
        roomTypesListener?.remove()
        reviewsListener?.remove()
    }


    private fun initial() {
        HOTEL_ID = intent.getStringExtra("HOTEL_ID").toString()
        Log.d("Intent", HOTEL_ID)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = ""
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        serviceAdapter = ServiceHotelAdapter(this, listServices)
        val spanCount = 3
        binding.gridServices.layoutManager = GridLayoutManager(this, spanCount)
        binding.gridServices.adapter = serviceAdapter

        roomTypeAdapter = RoomTypeAdapter(this, listRoomType, supportFragmentManager)
        binding.listRoomType.layoutManager = LinearLayoutManager(this)
        binding.listRoomType.adapter = roomTypeAdapter

        photoGridAdapter = PhotoGridAdapter(this, listOf())
        binding.gridPhotos.layoutManager = GridLayoutManager(this, 3)
        binding.gridPhotos.adapter = photoGridAdapter

        reviewAdapter = ReviewHotelAdapter(this, listReviews)
        binding.listReview.layoutManager = LinearLayoutManager(this)
        binding.listReview.adapter = reviewAdapter
        binding.listReview.isNestedScrollingEnabled = false

        loadHotel()
        loadRoomTypes()
        loadServices()
        loadReviews()
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

    private fun loadReviews() {
        reviewsListener?.remove()

        reviewsListener = db.collection("hotels").document(HOTEL_ID).collection("reviews")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { querySnapshot, exception ->
                if (exception != null) {
                    Log.e("Firestore", "Error listening to Reviews: ", exception)
                    return@addSnapshotListener
                }

                if (querySnapshot != null) {
                    listReviews.clear()
                    for (document in querySnapshot.documents) {
                        try {
                            val review = document.toObject(Review::class.java)
                            if (review != null) {
                                listReviews.add(review)
                            }
                        } catch (e: Exception) {
                            Log.e("Firestore", "Lỗi chuyển đổi dữ liệu cho Review: ${document.id}", e)
                        }
                    }

                    reviewAdapter.notifyDataSetChanged()

                    binding.tvReviewsCount.text = "(${listReviews.size})"
                }
            }
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
                            currentHotel = hotel

                            val imagesList = currentHotel.images

                            if (imagesList != null && imagesList.isNotEmpty()) {
                                Glide.with(this)
                                    .load(imagesList[0])
                                    .into(binding.imgHotel)
                            } else {
                                binding.imgHotel.setImageResource(R.drawable.hotel_64260231_1)
                            }

                            binding.tvAddress.text = currentHotel.hotelAddress
                            binding.ratingBar.rating = currentHotel.averageRating.toFloat()
                            binding.tvReviews.text = "(${currentHotel.totalReviews})"
                            binding.tvNameHotel.text = currentHotel.hotelName
                            binding.tvDescription.text = currentHotel.description

                            val images = currentHotel.images ?: listOf()
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

    private fun loadServices() {
        servicesListener?.remove()

        servicesListener = db.collection("serviceRates")
            .whereEqualTo("hotelId", HOTEL_ID)
            .addSnapshotListener { querySnapshot, exception ->
                if (exception != null) {
                    Log.e("Firestore", "Error listening to Service Rates: ", exception)
                    return@addSnapshotListener
                }

                if (querySnapshot != null) {
                    listServices.clear()

                    val serviceIdsToFetch = querySnapshot.documents.mapNotNull { it.getString("service_id") }

                    if (serviceIdsToFetch.isEmpty()) {
                        serviceAdapter.notifyDataSetChanged()
                        return@addSnapshotListener
                    }

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
            .whereEqualTo("hotelId", HOTEL_ID)
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
}