package com.tdc.nhom6.roomio.ui

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
import com.tdc.nhom6.roomio.model.Deal
import com.tdc.nhom6.roomio.model.HotReview
import com.tdc.nhom6.roomio.model.Hotel
import com.tdc.nhom6.roomio.model.RoomType
import com.tdc.nhom6.roomio.model.SearchResultItem
import com.tdc.nhom6.roomio.model.SearchResultType
import com.tdc.nhom6.roomio.repository.FirebaseRepository
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchResultsFragment : Fragment() {
    private lateinit var firebaseRepository: FirebaseRepository
    private lateinit var searchResultsAdapter: SearchResultsAdapter
    private var searchQuery: String = ""

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
        return inflater.inflate(R.layout.fragment_search_results, container, false)
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
        // Simple search using Firebase directly
        val results = mutableListOf<SearchResultItem>()

        try {
            // Search hotels
            firebaseRepository.db.collection("hotels")
            .get()
            .addOnSuccessListener { hotelResult ->
                val hotels = mutableListOf<Hotel>()
                for (document in hotelResult) {
                    try {
                        val hotel: Hotel = document.toObject<Hotel>()
                        if (hotel.hotelName.lowercase().contains(searchQuery.lowercase()) ||
                            hotel.hotelAddress.lowercase().contains(searchQuery.lowercase())) {
                            hotels.add(hotel)
                        }
                    } catch (e: Exception) {
                        Log.e("Search", "Error converting hotel document ${document.id}: ${e.message}")
                        // Skip this document and continue with others
                    }
                }

                // Load room types to get correct prices for hotels
                loadRoomTypesAndUpdatePrices(hotels) { hotelsWithPrices ->
                    hotelsWithPrices.forEach { hotel ->
                        results.add(SearchResultItem(
                            type = SearchResultType.HOTEL,
                            hotel = hotel,
                            deal = null,
                            review = null
                        ))
                    }

                    // Continue with deals search
                    performDealsSearch(results)
                }
            }
            .addOnFailureListener { exception ->
                android.widget.Toast.makeText(requireContext(), "Error searching hotels: ${exception.message}", Toast.LENGTH_LONG).show()
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

                // Update hotel prices with lowest room price
                val hotelsWithPrices = hotels.map { hotel ->
                    val roomTypes = roomTypesMap[hotel.hotelId] ?: emptyList()
                    val lowestPrice = roomTypes.minOfOrNull { it.pricePerNight } ?: hotel.pricePerNight

                    hotel.copy(pricePerNight = lowestPrice)
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
        // Search deals
        firebaseRepository.db.collection("deals")
            .get()
            .addOnSuccessListener { dealResult ->
                for (document in dealResult) {
                    try {
                        val deal: Deal = document.toObject<Deal>()
                        if (deal.hotelName.lowercase().contains(searchQuery.lowercase()) ||
                            deal.hotelLocation.lowercase().contains(searchQuery.lowercase())) {
                            results.add(SearchResultItem(
                                type = SearchResultType.DEAL,
                                hotel = null,
                                deal = deal,
                                review = null
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("Search", "Error converting deal document ${document.id}: ${e.message}")
                        // Skip this document and continue with others
                    }
                }

                // Update adapter with results
                searchResultsAdapter.updateData(results)

                // Show results message
                val message = if (results.isEmpty()) {
                    "No results found for '$searchQuery'. Try: Ares, Vung Tau, Saigon, Sapa, or Nha Trang"
                } else {
                    "Found ${results.size} results for '$searchQuery'"
                }
                android.widget.Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { exception ->
                android.widget.Toast.makeText(requireContext(), "Error searching deals: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    // All offline/sample search code removed
}

// Adapter for search results
class SearchResultsAdapter(var items: List<SearchResultItem>) : RecyclerView.Adapter<SearchResultsViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_hotel, parent, false)
        return SearchResultsViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: SearchResultsViewHolder, position: Int) {
        holder.bind(items[position])
    }

    fun updateData(newItems: List<SearchResultItem>) {
        // Simple update without complex notifications to prevent swap behavior issues
        items = newItems
        // Use notifyDataSetChanged() for stable updates
        notifyDataSetChanged()
    }
}

// ViewHolder for search results
class SearchResultsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val img = itemView.findViewById<android.widget.ImageView>(R.id.img)
    private val title = itemView.findViewById<android.widget.TextView>(R.id.tvTitle)
    private val location = itemView.findViewById<android.widget.TextView>(R.id.tvLocation)
    private val price = itemView.findViewById<android.widget.TextView>(R.id.tvPrice)

    private val layoutRating = itemView.findViewById<android.widget.LinearLayout>(R.id.layoutRating)

    @SuppressLint("SetTextI18n")
    fun bind(item: SearchResultItem) {
        when (item.type) {
            SearchResultType.HOTEL -> {
                item.hotel?.let { hotel ->
                    img.setImageResource(getDrawableResourceId(hotel.images.firstOrNull() ?: "hotel_64260231_1"))
                    title.text = hotel.hotelName
                    location.text = extractLocationFromAddress(hotel.hotelAddress)
                    price.text = formatPrice(hotel.pricePerNight)

                    setStarRating(hotel.averageRating)
                }
            }
            SearchResultType.DEAL -> {
                item.deal?.let { deal ->
                    img.setImageResource(getDrawableResourceId(deal.imageUrl))
                    title.text = deal.hotelName
                    location.text = deal.hotelLocation
                    price.text = formatPrice(deal.discountPricePerNight)

                    setStarRating(deal.rating)
                }
            }
            SearchResultType.REVIEW -> {
                item.review?.let { review ->
                    img.setImageResource(getDrawableResourceId(review.hotelImage))
                    title.text = review.hotelName
                    location.text = review.location
                    price.text = formatPrice(review.pricePerNight)

                    setStarRating(review.rating)
                }
            }
        }
    }

    private fun extractLocationFromAddress(address: String): String {
        // Extract city name from address (e.g., "123 Nguyen Hue, Quận 1, TP. Hồ Chí Minh" -> "Ho Chi Minh City")
        return when {
            address.contains("TP. Hồ Chí Minh") || address.contains("Ho Chi Minh") -> "Ho Chi Minh City"
            address.contains("Vũng Tàu") || address.contains("Vung Tau") -> "Vung Tau"
            address.contains("Đà Nẵng") || address.contains("Da Nang") -> "Da Nang"
            address.contains("Hà Nội") || address.contains("Hanoi") -> "Hanoi"
            address.contains("Nha Trang") -> "Nha Trang"
            address.contains("Phú Quốc") || address.contains("Phu Quoc") -> "Phu Quoc"
            else -> "Vietnam"
        }
    }

    @SuppressLint("DefaultLocale")
    private fun formatPrice(price: Double): String {
        return "VND ${String.format("%,.0f", price)}"
    }

    private fun setStarRating(rating: Double) {
        val stars = listOf(
            itemView.findViewById<android.widget.ImageView>(R.id.ivStar1),
            itemView.findViewById<android.widget.ImageView>(R.id.ivStar2),
            itemView.findViewById<android.widget.ImageView>(R.id.ivStar3),
            itemView.findViewById<android.widget.ImageView>(R.id.ivStar4),
            itemView.findViewById<android.widget.ImageView>(R.id.ivStar5)
        )

        val fullStars = rating.toInt()
        val hasHalfStar = rating - fullStars >= 0.5

        stars.forEachIndexed { index, star ->
            when {
                index < fullStars -> {
                    star.setImageResource(R.drawable.ic_star_filled)
                    star.setColorFilter(itemView.context.getColor(android.R.color.holo_orange_dark))
                }
                index == fullStars && hasHalfStar -> {
                    star.setImageResource(R.drawable.ic_star_half)
                    star.setColorFilter(itemView.context.getColor(android.R.color.holo_orange_dark))
                }
                else -> {
                    star.setImageResource(R.drawable.ic_star)
                    star.setColorFilter(itemView.context.getColor(android.R.color.darker_gray))
                }
            }
        }
    }

    private fun getDrawableResourceId(imageName: String): Int {
        return when (imageName) {
            "hotel_64260231_1" -> R.drawable.hotel_64260231_1
            "hotel_del_coronado_views_suite1600x900" -> R.drawable.hotel_del_coronado_views_suite1600x900
            "swimming_pool_1" -> R.drawable.swimming_pool_1
            "room_640278495" -> R.drawable.room_640278495
            "rectangle_copy_2" -> R.drawable.rectangle_copy_2
            "property_colombo" -> R.drawable.property_colombo
            "dsc04512_scaled_1" -> R.drawable.dsc04512_scaled_1
            else -> R.drawable.hotel_64260231_1
        }
    }
}