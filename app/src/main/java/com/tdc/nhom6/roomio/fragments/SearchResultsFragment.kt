package com.tdc.nhom6.roomio.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tdc.nhom6.roomio.activities.hotel.HotelDetailActivity
import com.tdc.nhom6.roomio.adapters.SearchResultsAdapter
import com.tdc.nhom6.roomio.databinding.FragmentSearchResultsLayoutBinding
import com.tdc.nhom6.roomio.models.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SearchResultsFragment : Fragment() {

    // ====================================
    // Binding & Adapter
    // ====================================
    private var _binding: FragmentSearchResultsLayoutBinding? = null
    private val binding get() = _binding!!

    private lateinit var searchResultsAdapter: SearchResultsAdapter

    // ====================================
    // Firebase
    // ====================================
    private val db = FirebaseFirestore.getInstance()
    private var hotelsListener: ListenerRegistration? = null
    private var roomTypesListener: ListenerRegistration? = null

    // ====================================
    // Data Caches
    // ====================================
    private val hotelsCache = mutableListOf<Hotel>()
    private val roomTypesCache = mutableMapOf<String, MutableList<RoomType>>()
    private var allServices: List<Service> = emptyList()
    //private var allTienIch: List<Facility> = emptyList()

    // ====================================
    // Filter & Sort
    // ====================================
    private var minPrice = 0
    private var maxPrice = 10_000_000
    private var appliedServiceIds = mutableSetOf<String>()
    private var appliedFacilityIds = mutableSetOf<String>()
    private var currentComparator: Comparator<Hotel>? = compareBy { it.pricePerNight } // Mặc định sort giá Thấp -> Cao

    // ====================================
    // Search
    // ====================================
    private var searchQuery: String = ""

    companion object {
        private const val TAG = "SearchResultsFragment"

        fun newInstance(query: String, location: String): SearchResultsFragment {
            val fragment = SearchResultsFragment()
            val args = Bundle()
            args.putString("query", query)
            args.putString("location", location)
            fragment.arguments = args
            return fragment
        }
    }

    // ====================================
    // Lifecycle
    // ====================================
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchResultsLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchQuery = arguments?.getString("query") ?: ""

        setupUI()
        setupRecyclerView()
        setupSearchRealtime()

        loadAllServices()
        //loadAllTienIch()

        loadHotelsFromFirebase()
        loadRoomTypesFromFirebase()

        // Tự động focus vào thanh tìm kiếm
        binding.edtSearchQuery.requestFocus()
        binding.edtSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                filterHotels(binding.edtSearchQuery.text.toString().trim())

                val imm =
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.edtSearchQuery.windowToken, 0)

                true
            } else false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hotelsListener?.remove()
        roomTypesListener?.remove()
        _binding = null
    }

    // ====================================
    // UI Setup
    // ====================================
    private fun setupUI() {
        binding.edtSearchQuery.setText(searchQuery)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnSort.setOnClickListener { showSortOptions() }

        binding.btnFilter.setOnClickListener {
            val filterFragment = FilterBottomSheetFragment()

            filterFragment.setAmenities(allServices)
            //filterFragment.setTienIch(allTienIch)

            // Truyền lại lựa chọn đã áp dụng
            filterFragment.setSelectedServices(appliedServiceIds)
            filterFragment.setSelectedFacilities(appliedFacilityIds)
            filterFragment.setSelectedPrice(minPrice, maxPrice)

            // Nhận kết quả sau khi Apply
            filterFragment.setOnApplyFilterListener { min, max, selectedServices, selectedFacilities ->
                minPrice = min
                maxPrice = max
                appliedServiceIds = selectedServices.toMutableSet()
                appliedFacilityIds = selectedFacilities.toMutableSet()

                applyFilter(min, max, selectedServices, selectedFacilities)
            }

            filterFragment.show(parentFragmentManager, "FilterBottomSheetFragment")
        }

        // Cho phép focus và show keyboard
        binding.searchBar.isFocusableInTouchMode = true
    }

    // ====================================
    // RecyclerView Setup
    // ====================================
    private fun setupRecyclerView() {
        binding.rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        searchResultsAdapter = SearchResultsAdapter(emptyList())

        searchResultsAdapter.onHotelClick = { hotelId ->
            val intent = Intent(requireContext(), HotelDetailActivity::class.java)
            intent.putExtra("HOTEL_ID", hotelId)
            startActivity(intent)
        }

        binding.rvSearchResults.adapter = searchResultsAdapter
    }

    fun focusSearchBox() {
        view?.post {
            binding.edtSearchQuery.requestFocus()
        }
    }

    // ====================================
    // Firebase Data Loading
    // ====================================
    private fun loadHotelsFromFirebase() {
        hotelsListener?.remove()
        hotelsListener = db.collection("hotels").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error loading hotels: ${error.message}")
                return@addSnapshotListener
            }

            if (snapshot != null) {
                hotelsCache.clear()
                hotelsCache.addAll(snapshot.toObjects(Hotel::class.java))

                if (searchQuery.isBlank()) {
                    val listToShow =
                        hotelsCache.filter { it.pricePerNight.toInt() in minPrice..maxPrice }
                    updateHotelResults(listToShow)
                } else {
                    filterHotels(searchQuery)
                }
            }
        }
    }

    private fun loadRoomTypesFromFirebase() {
        roomTypesListener?.remove()
        roomTypesListener = db.collection("roomTypes").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error loading room types: ${error.message}")
                return@addSnapshotListener
            }

            roomTypesCache.clear()
            if (snapshot != null) {
                for (doc in snapshot) {
                    val roomType = doc.toObject(RoomType::class.java)
                    val hotelId = roomType.hotelId
                    if (!roomTypesCache.containsKey(hotelId)) {
                        roomTypesCache[hotelId] = mutableListOf()
                    }
                    roomTypesCache[hotelId]?.add(roomType)
                }
            }

            // Cập nhật lại danh sách hotel sau khi có room types
            applyFilter(minPrice, maxPrice, appliedServiceIds, appliedFacilityIds)
        }
    }

    // ====================================
    // Filter / Search
    // ====================================
    private fun setupSearchRealtime() {
        binding.edtSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString()?.trim() ?: ""
                searchQuery = text

                if (text.isEmpty()) {
                    val listToShow = hotelsCache
                        .map { getHotelWithRoomPrice(it) }
                        .filter { it.pricePerNight.toInt() in minPrice..maxPrice }
                    updateHotelResults(listToShow)
                } else {
                    filterHotels(text)
                }
            }
        })
    }

    private fun applyFilter(
        min: Int,
        max: Int,
        selectedServiceIds: Set<String>,
        selectedFacilityIds: Set<String>
    ) {
        var filtered = hotelsCache.map { getHotelWithRoomPrice(it) }.filter { hotel ->
            val priceMatch = hotel.pricePerNight.toInt() in min..max
            val nameMatch = hotel.hotelName.lowercase().contains(searchQuery.lowercase())
            val serviceMatch = if (selectedServiceIds.isEmpty()) true else hotel.serviceIds.containsAll(selectedServiceIds)
            val facilityMatch = if (selectedFacilityIds.isEmpty()) true else hotel.facilityIds.containsAll(selectedFacilityIds)

            priceMatch && nameMatch && serviceMatch && facilityMatch
        }

        currentComparator?.let { comparator ->
            filtered = filtered.sortedWith(comparator)
        }

        updateHotelResults(filtered)
    }

    private fun filterHotels(query: String) {
        val lower = query.lowercase()
        var filtered = hotelsCache.map { getHotelWithRoomPrice(it) }.filter { hotel ->
            val nameOk = hotel.hotelName.lowercase().contains(lower)
            val priceOk = hotel.pricePerNight.toInt() in minPrice..maxPrice
            nameOk && priceOk
        }

        currentComparator?.let { comparator ->
            filtered = filtered.sortedWith(comparator)
        }

        updateHotelResults(filtered)
    }

    private fun updateHotelResults(list: List<Hotel>) {
        val updatedList = list.map { hotel ->
            val roomTypes = roomTypesCache[hotel.hotelId] ?: emptyList()
            val lowestPrice = roomTypes.minOfOrNull { it.pricePerNight } ?: hotel.pricePerNight
            val highestPrice = roomTypes.maxOfOrNull { it.pricePerNight }

            hotel.copy(
                pricePerNight = lowestPrice,
                lowestPricePerNight = if (roomTypes.isNotEmpty()) lowestPrice else null,
                highestPricePerNight = highestPrice
            )
        }

        searchResultsAdapter.updateData(
            updatedList.map {
                SearchResultItem(
                    type = SearchResultType.HOTEL,
                    hotel = it,
                    deal = null,
                    review = null
                )
            }
        )
    }

    // ====================================
    // Sort
    // ====================================
    @SuppressLint("SetTextI18n")
    private fun showSortOptions() {
        val sortOptions = arrayOf(
            "Price: Low to High",
            "Price: High to Low",
            "Rating: High to Low",
            "Name: A to Z"
        )

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Sort by")
            .setItems(sortOptions) { _, which ->
                val selectedSort = sortOptions[which]
                binding.tvSortStatus.text = "Sorted by: $selectedSort"
                currentComparator = when (which) {
                    0 -> compareBy { it.pricePerNight }
                    1 -> compareByDescending { it.pricePerNight }
                    2 -> compareByDescending { it.averageRating }
                    3 -> compareBy { it.hotelName }
                    else -> null
                }

                if (searchQuery.isBlank()) filterHotels("") else filterHotels(searchQuery)
            }
            .show()
    }

    // ====================================
    // Utilities
    // ====================================
    private fun getHotelWithRoomPrice(hotel: Hotel): Hotel {
        val roomTypes = roomTypesCache[hotel.hotelId] ?: emptyList()
        val lowestPrice = roomTypes.minOfOrNull { it.pricePerNight } ?: hotel.pricePerNight
        val highestPrice = roomTypes.maxOfOrNull { it.pricePerNight }

        return hotel.copy(
            pricePerNight = lowestPrice,
            lowestPricePerNight = if (roomTypes.isNotEmpty()) lowestPrice else null,
            highestPricePerNight = highestPrice
        )
    }

    // ====================================
    // Load Services / Facilities
    // ====================================
    private fun loadAllServices() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val snap = db.collection("services").get().await()
                allServices = snap.toObjects(Service::class.java)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed service load!", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "loadAllServices error: ${e.message}")
                }
            }
        }
    }

//    private fun loadAllTienIch() {
//        lifecycleScope.launch(Dispatchers.IO) {
//            try {
//                val snap = db.collection("facilities").get().await()
//                allTienIch = snap.toObjects(Facility::class.java)
//            } catch (e: Exception) {
//                withContext(Dispatchers.Main) {
//                    Toast.makeText(requireContext(), "Failed facilities load!", Toast.LENGTH_SHORT).show()
//                    Log.e(TAG, "loadAllTienIch error: ${e.message}")
//                }
//            }
//        }
//    }
}
