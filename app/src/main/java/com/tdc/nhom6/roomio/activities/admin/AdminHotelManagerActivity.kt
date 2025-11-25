package com.tdc.nhom6.roomio.activities.admin

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.HotelManageAdapter
import com.tdc.nhom6.roomio.databinding.AdminHotelManagerLayoutBinding
import com.tdc.nhom6.roomio.models.Hotel
import com.tdc.nhom6.roomio.utils.navigateTo

class AdminHotelManagerActivity : AppCompatActivity() {
    private lateinit var binding: AdminHotelManagerLayoutBinding

    private lateinit var adapter: HotelManageAdapter
    private val db = FirebaseFirestore.getInstance()

    private var fullHotelList = listOf<Pair<Hotel, String>>() // Lưu tất cả dữ liệu
    private var filteredHotelList = listOf<Pair<Hotel, String>>() // Dùng cho adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AdminHotelManagerLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.app_bar_title_hotel_manager)

        setupRecyclerView()
        loadHotelsRealtime()
        setupSearch()
    }

    private fun setupRecyclerView() {
        adapter = HotelManageAdapter(emptyList()) { hotel ->
            // click item handler
        }
        binding.rcvHotels.layoutManager = LinearLayoutManager(this)
        binding.rcvHotels.adapter = adapter
    }

    private fun setupSearch() {
        binding.edtSearch.addTextChangedListener { text ->
            val query = text.toString().trim().lowercase()
            filteredHotelList = if (query.isEmpty()) {
                fullHotelList
            } else {
                fullHotelList.filter { (hotel, ownerName) ->
                    hotel.hotelName.lowercase().contains(query) || ownerName.lowercase().contains(query)
                }
            }
            adapter.updateDataWithOwner(filteredHotelList)
        }
    }

    private fun loadHotelsRealtime() {
        val hotelsRef = db.collection("hotels")
        val usersRef = db.collection("users")

        usersRef.addSnapshotListener { userSnapshot, userError ->
            if (userError != null) {
                Log.e("AdminHotel", "Error loading users: $userError")
                return@addSnapshotListener
            }

            val userMap = userSnapshot?.associate {
                it.id to (it.getString("username") ?: "Unknown")
            } ?: emptyMap()

            hotelsRef.addSnapshotListener { hotelSnapshot, hotelError ->
                if (hotelError != null) {
                    Log.e("AdminHotel", "Error loading hotels: $hotelError")
                    return@addSnapshotListener
                }

                val hotelList = mutableListOf<Pair<Hotel, String>>()
                hotelSnapshot?.forEach { doc ->
                    val hotel = doc.toObject(Hotel::class.java)
                    val ownerName = userMap[hotel.ownerId] ?: "Unknown"
                    hotelList.add(hotel to ownerName)
                }

                // Cập nhật danh sách gốc và adapter
                fullHotelList = hotelList
                filteredHotelList = hotelList
                adapter.updateDataWithOwner(filteredHotelList)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_admin_hotel, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.addHotelType -> {
                navigateTo(HotelTypesActivity::class.java, flag = false)
                return true
            }
            R.id.servicesManager -> {
                navigateTo(HotelServicesActivity::class.java, flag = false)
                return true
            }
            R.id.facilitiesManager -> {
                navigateTo(HotelFacilitiesActivity::class.java, flag = false)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}