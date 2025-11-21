package com.tdc.nhom6.roomio.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.models.Hotel
import com.tdc.nhom6.roomio.models.SearchResultItem
import com.tdc.nhom6.roomio.models.SearchResultType
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObject
import com.tdc.nhom6.roomio.adapters.SearchResultsAdapter
import com.tdc.nhom6.roomio.models.RoomType
import com.tdc.nhom6.roomio.repositories.FirebaseRepository

class SearchResultsFragment : Fragment() {
    private lateinit var firebaseRepository: FirebaseRepository
    private lateinit var searchResultsAdapter: SearchResultsAdapter
    private var searchQuery: String = ""
    private var hotelsListener: ListenerRegistration? = null
    private var roomTypesListener: ListenerRegistration? = null
    private var dealsListener: ListenerRegistration? = null
    private val roomTypesCache = mutableMapOf<String, MutableList<RoomType>>()
    private val hotelsCache = mutableListOf<Hotel>()

    companion object {
        fun newInstance(query: String, location: String): SearchResultsFragment {
            val fragment = SearchResultsFragment()
            val args = Bundle()
            args.putString("query", query)
            args.putString("location", location)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search_results_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firebaseRepository = FirebaseRepository()

        // Get search query from arguments
        searchQuery = arguments?.getString("query") ?: ""

        // Set up UI elements
        setupUI(view)

        // Set up RecyclerView with improved configuration
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvSearchResults)

        // Configure RecyclerView to prevent swap behavior issues
        val layoutManager = LinearLayoutManager(requireContext())
        recyclerView.layoutManager = layoutManager
        recyclerView.setItemViewCacheSize(20) // Cache more views to prevent recycling issues
        recyclerView.itemAnimator = null // Disable animations to prevent swap behavior errors
        recyclerView.setNestedScrollingEnabled(false) // Disable nested scrolling
        recyclerView.isNestedScrollingEnabled = false

        searchResultsAdapter = SearchResultsAdapter(emptyList())
        recyclerView.adapter = searchResultsAdapter

        // Perform search
        performSearch()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            hotelsListener?.remove()
            roomTypesListener?.remove()
            dealsListener?.remove()
        } catch (_: Exception) { }
        hotelsListener = null
        roomTypesListener = null
        dealsListener = null
    }

    private fun setupUI(view: View) {
        // Set search query in the search field
        val tvSearchQuery = view.findViewById<android.widget.TextView>(R.id.tvSearchQuery)
        tvSearchQuery.text = searchQuery

        // Set up back button
        val btnBack = view.findViewById<android.widget.ImageView>(R.id.btnBack)
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Set up sort button
        val btnSort = view.findViewById<android.widget.LinearLayout>(R.id.btnSort)
        btnSort.setOnClickListener {
            showSortOptions()
        }

        // Set up filter button
        val btnFilter = view.findViewById<android.widget.LinearLayout>(R.id.btnFilter)
        btnFilter.setOnClickListener {
            showFilterOptions()
        }

        // Set up search field click to edit
        val searchField = view.findViewById<android.widget.LinearLayout>(R.id.searchField)
        searchField.setOnClickListener {
            // Navigate back to home to search again
            parentFragmentManager.popBackStack()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showSortOptions() {
        val sortOptions = arrayOf("Price: Low to High", "Price: High to Low", "Rating: High to Low", "Name: A to Z")

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Sort by")
            .setItems(sortOptions) { _, which ->
                val selectedSort = sortOptions[which]
                val tvSortStatus = view?.findViewById<android.widget.TextView>(R.id.tvSortStatus)
                tvSortStatus?.text = "Sorted by: $selectedSort"

                // Apply sorting to results
                applySorting(selectedSort)
            }
            .show()
    }

    private fun showFilterOptions() {
        val filterOptions = arrayOf("All", "Hotels Only", "Deals Only", "Reviews Only", "Price Range", "Rating")

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Filter by")
            .setItems(filterOptions) { _, which ->
                val selectedFilter = filterOptions[which]

                // Apply filtering to results
                applyFiltering(selectedFilter)
            }
            .show()
    }

    private fun applySorting(sortType: String) {
        // Sort the current results based on the selected option
        val currentItems = searchResultsAdapter.items.toMutableList()

        when (sortType) {
            "Price: Low to High" -> {
                currentItems.sortBy { item ->
                    when (item.type) {
                        SearchResultType.HOTEL -> item.hotel?.pricePerNight ?: 0.0
                        SearchResultType.DEAL -> item.deal?.discountPricePerNight ?: 0.0
                        SearchResultType.REVIEW -> item.review?.pricePerNight ?: 0.0
                    }
                }
            }
            "Price: High to Low" -> {
                currentItems.sortByDescending { item ->
                    when (item.type) {
                        SearchResultType.HOTEL -> item.hotel?.pricePerNight ?: 0.0
                        SearchResultType.DEAL -> item.deal?.discountPricePerNight ?: 0.0
                        SearchResultType.REVIEW -> item.review?.pricePerNight ?: 0.0
                    }
                }
            }
            "Rating: High to Low" -> {
                currentItems.sortByDescending { item ->
                    when (item.type) {
                        SearchResultType.HOTEL -> item.hotel?.averageRating ?: 0.0
                        SearchResultType.DEAL -> item.deal?.rating ?: 0.0
                        SearchResultType.REVIEW -> item.review?.rating ?: 0.0
                    }
                }
            }
            "Name: A to Z" -> {
                currentItems.sortBy { item ->
                    when (item.type) {
                        SearchResultType.HOTEL -> item.hotel?.hotelName ?: ""
                        SearchResultType.DEAL -> item.deal?.hotelName ?: ""
                        SearchResultType.REVIEW -> item.review?.hotelName ?: ""
                    }
                }
            }
        }

        searchResultsAdapter.updateData(currentItems)
    }

    private fun applyFiltering(filterType: String) {
        // Filter the current results based on the selected option
        val allItems = searchResultsAdapter.items.toMutableList()

        val filteredItems = when (filterType) {
            "All" -> allItems
            "Hotels Only" -> allItems.filter { it.type == SearchResultType.HOTEL }
            "Deals Only" -> allItems.filter { it.type == SearchResultType.DEAL }
            "Reviews Only" -> allItems.filter { it.type == SearchResultType.REVIEW }
            else -> allItems
        }

        searchResultsAdapter.updateData(filteredItems)

        val message = if (filteredItems.isEmpty()) {
            "No results found for '$searchQuery' with filter: $filterType"
        } else {
            "Showing ${filteredItems.size} results for '$searchQuery'"
        }

        android.widget.Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun performSearch() {
        val message = if (searchQuery.isBlank()) "Showing all results" else "Searching for: $searchQuery"
        android.widget.Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

        // Query Firebase directly without any sample seeding
        performFirebaseSearch()
    }

    private fun performFirebaseSearch() {
        // Simple search using Firebase directly with real-time updates
        val results = mutableListOf<SearchResultItem>()
        var dealsSearchInitialized = false

        try {
            // Helper function to filter and update hotel results
            fun updateHotelResults(hotels: List<Hotel>) {
                val filteredHotels = hotels.filter { hotel ->
                    searchQuery.isBlank() || 
                    hotel.hotelName.lowercase().contains(searchQuery.lowercase()) ||
                    hotel.hotelAddress.lowercase().contains(searchQuery.lowercase())
                }

                // Update prices using cached room types
                val hotelsWithPrices = filteredHotels.map { hotel ->
                    val roomTypes = roomTypesCache[hotel.hotelId] ?: emptyList()
                    val lowestPrice = roomTypes.minOfOrNull { it.pricePerNight } ?: hotel.pricePerNight
                    val highestPrice = roomTypes.maxOfOrNull { it.pricePerNight }

                    hotel.copy(
                        pricePerNight = lowestPrice,
                        lowestPricePerNight = if (roomTypes.isNotEmpty()) lowestPrice else null,
                        highestPricePerNight = highestPrice
                    )
                }

                // Remove old hotel results
                results.removeAll { it.type == SearchResultType.HOTEL }
                
                // Add new hotel results
                hotelsWithPrices.forEach { hotel ->
                    results.add(
                        SearchResultItem(
                        type = SearchResultType.HOTEL,
                        hotel = hotel,
                        deal = null,
                        review = null
                    )
                    )
                }

                // Update the adapter
                searchResultsAdapter.updateData(results.toList())

                // Continue with deals search (only once)
                if (!dealsSearchInitialized) {
                    dealsSearchInitialized = true
                    performDealsSearch(results)
                }
            }

            // Observe hotels in real-time
            hotelsListener?.remove()
            hotelsListener = firebaseRepository.db.collection("hotels")
                .addSnapshotListener { hotelResult, error ->
                    if (error != null) {
                        android.widget.Toast.makeText(requireContext(), "Error searching hotels: ${error.message}", Toast.LENGTH_LONG).show()
                        Log.e("Search", "Error observing hotels: ${error.message}")
                        return@addSnapshotListener
                    }

                    hotelsCache.clear()
                    if (hotelResult != null) {
                        for (document in hotelResult) {
                            try {
                                val hotel: Hotel = document.toObject<Hotel>()
                                hotelsCache.add(hotel)
                            } catch (e: Exception) {
                                Log.e("Search", "Error converting hotel document ${document.id}: ${e.message}")
                            }
                        }
                    }

                    updateHotelResults(hotelsCache)
                }

            // Observe room types in real-time
            roomTypesListener?.remove()
            roomTypesListener = firebaseRepository.db.collection("roomTypes")
                .addSnapshotListener { roomTypesResult, error ->
                    if (error != null) {
                        Log.e("Search", "Error observing room types: ${error.message}")
                        return@addSnapshotListener
                    }

                    roomTypesCache.clear()
                    if (roomTypesResult != null) {
                        for (document in roomTypesResult) {
                            try {
                                val roomType: RoomType = document.toObject<RoomType>()
                                val hotelId = roomType.hotelId
                                if (!roomTypesCache.containsKey(hotelId)) {
                                    roomTypesCache[hotelId] = mutableListOf()
                                }
                                roomTypesCache[hotelId]?.add(roomType)
                            } catch (e: Exception) {
                                Log.e("Search", "Error converting room type ${document.id}: ${e.message}")
                            }
                        }
                    }

                    // Re-update hotel results with new room types using cached hotels
                    if (hotelsCache.isNotEmpty()) {
                        updateHotelResults(hotelsCache)
                    }
                }
        } catch (e: Exception) {
            android.widget.Toast.makeText(requireContext(), "Search error: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("Search", "Exception in search: ${e.message}")
        }
    }

    /**
     * Loads room types from Firebase and updates hotel prices with the lowest room price
     */
    private fun loadRoomTypesAndUpdatePrices(hotels: List<Hotel>, callback: (List<Hotel>) -> Unit) {
        firebaseRepository.db.collection("roomTypes")
            .get()
            .addOnSuccessListener { roomTypesResult ->
                val roomTypesMap = mutableMapOf<String, MutableList<RoomType>>()

                // Group room types by hotel ID
                for (document in roomTypesResult) {
                    try {
                        val roomType: RoomType = document.toObject<RoomType>()
                        val hotelId = roomType.hotelId

                        if (!roomTypesMap.containsKey(hotelId)) {
                            roomTypesMap[hotelId] = mutableListOf()
                        }
                        roomTypesMap[hotelId]?.add(roomType)
                    } catch (e: Exception) {
                        Log.e("Search", "Error converting room type ${document.id}: ${e.message}")
                    }
                }

                // Update hotel prices with lowest and highest room prices
                val hotelsWithPrices = hotels.map { hotel ->
                    val roomTypes = roomTypesMap[hotel.hotelId] ?: emptyList()
                    val lowestPrice = roomTypes.minOfOrNull { it.pricePerNight } ?: hotel.pricePerNight
                    val highestPrice = roomTypes.maxOfOrNull { it.pricePerNight }

                    hotel.copy(
                        pricePerNight = lowestPrice,
                        lowestPricePerNight = if (roomTypes.isNotEmpty()) lowestPrice else null,
                        highestPricePerNight = highestPrice
                    )
                }

                callback(hotelsWithPrices)
            }
            .addOnFailureListener { exception ->
                Log.w("Search", "Error loading room types: ${exception.message}")
                // Return hotels without price updates
                callback(hotels)
            }
    }

    private fun performDealsSearch(results: MutableList<SearchResultItem>) {
        // Realtime deals based on discounts
        val hasPlay = firebaseRepository.isPlayServicesAvailable(requireActivity())
        if (hasPlay) {
            dealsListener?.remove()
            dealsListener = firebaseRepository.observeDeals { deals ->
                try {
                    deals.filter {
                        it.hotelName.lowercase().contains(searchQuery.lowercase()) ||
                        it.hotelLocation.lowercase().contains(searchQuery.lowercase())
                    }.forEach { deal ->
                        results.add(
                            SearchResultItem(
                                type = SearchResultType.DEAL,
                                hotel = null,
                                deal = deal,
                                review = null
                            )
                        )
                    }

                    searchResultsAdapter.updateData(results)

                    val message = if (results.isEmpty()) {
                        "No results found for '$searchQuery'. Try: Ares, Vung Tau, Saigon, Sapa, or Nha Trang"
                    } else {
                        "Found ${results.size} results for '$searchQuery'"
                    }
                    android.widget.Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(requireContext(), "Error searching deals: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            // One-shot fallback
            firebaseRepository.getDeals { deals ->
                try {
                    deals.filter {
                        it.hotelName.lowercase().contains(searchQuery.lowercase()) ||
                        it.hotelLocation.lowercase().contains(searchQuery.lowercase())
                    }.forEach { deal ->
                        results.add(
                            SearchResultItem(
                                type = SearchResultType.DEAL,
                                hotel = null,
                                deal = deal,
                                review = null
                            )
                        )
                    }
                    searchResultsAdapter.updateData(results)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(requireContext(), "Error searching deals: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // All offline/sample search code removed
}