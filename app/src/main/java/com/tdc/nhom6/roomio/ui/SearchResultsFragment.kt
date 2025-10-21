package com.tdc.nhom6.roomio.ui

import android.annotation.SuppressLint
import android.os.Bundle
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
import com.tdc.nhom6.roomio.repository.FirebaseRepository
import com.tdc.nhom6.roomio.utils.ToastUtils
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
        
        // Set up RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvSearchResults)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
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
        
        ToastUtils.show(requireContext(), message, Toast.LENGTH_SHORT)
    }
    
    private fun performSearch() {
        if (searchQuery.isEmpty()) {
            ToastUtils.show(requireContext(), "Please enter a search term", Toast.LENGTH_SHORT)
            return
        }
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                ToastUtils.show(requireContext(), "Searching for: $searchQuery", Toast.LENGTH_SHORT)
                
                // Perform comprehensive search
                val searchResults = withContext(Dispatchers.IO) {
                    firebaseRepository.searchAll(searchQuery)
                }
                
                // Convert results to SearchResultItem list
                val allResults = mutableListOf<SearchResultItem>()
                
                // Add hotels
                @Suppress("UNCHECKED_CAST")
                val hotels = searchResults["hotels"] as? List<Hotel> ?: emptyList()
                hotels.forEach { hotel ->
                    allResults.add(SearchResultItem(
                        type = SearchResultType.HOTEL,
                        hotel = hotel,
                        deal = null,
                        review = null
                    ))
                }
                
                // Add deals
                @Suppress("UNCHECKED_CAST")
                val deals = searchResults["deals"] as? List<Deal> ?: emptyList()
                deals.forEach { deal ->
                    allResults.add(SearchResultItem(
                        type = SearchResultType.DEAL,
                        hotel = null,
                        deal = deal,
                        review = null
                    ))
                }
                
                // Add reviews
                @Suppress("UNCHECKED_CAST")
                val reviews = searchResults["reviews"] as? List<HotReview> ?: emptyList()
                reviews.forEach { review ->
                    allResults.add(SearchResultItem(
                        type = SearchResultType.REVIEW,
                        hotel = null,
                        deal = null,
                        review = review
                    ))
                }
                
                // Update adapter
                searchResultsAdapter.updateData(allResults)
                
                // Show results message
                val totalResults = allResults.size
                val message = if (totalResults > 0) {
                    "Found $totalResults results for '$searchQuery'"
                } else {
                    "No results found for '$searchQuery'"
                }
                ToastUtils.show(requireContext(), message, Toast.LENGTH_LONG)
                
            } catch (e: Exception) {
                ToastUtils.show(requireContext(), "Search error: ${e.message}", Toast.LENGTH_LONG)
                println("Search error: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}

// Data classes for search results
enum class SearchResultType {
    HOTEL, DEAL, REVIEW
}

data class SearchResultItem(
    val type: SearchResultType,
    val hotel: Hotel?,
    val deal: Deal?,
    val review: HotReview?
)

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
        items = newItems
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
            "rectangle_copy_3" -> R.drawable.rectangle_copy_3
            "dsc04512_scaled_1" -> R.drawable.dsc04512_scaled_1
            else -> R.drawable.hotel_64260231_1
        }
    }
}