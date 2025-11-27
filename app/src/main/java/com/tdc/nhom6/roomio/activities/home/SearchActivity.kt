package com.tdc.nhom6.roomio.activities.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObject
import com.tdc.nhom6.roomio.activities.hotel.HotelDetailActivity
import com.tdc.nhom6.roomio.adapters.SearchResultsAdapter
import com.tdc.nhom6.roomio.databinding.FragmentSearchResultsBinding
import com.tdc.nhom6.roomio.models.Hotel
import com.tdc.nhom6.roomio.models.RoomType
import com.tdc.nhom6.roomio.models.SearchResultItem
import com.tdc.nhom6.roomio.models.SearchResultType
import com.tdc.nhom6.roomio.repository.FirebaseRepository
import com.tdc.nhom6.roomio.utils.RecyclerViewUtils

class SearchActivity : AppCompatActivity() {
    private lateinit var binding: FragmentSearchResultsBinding
    private lateinit var firebaseRepository: FirebaseRepository
    private lateinit var searchResultsAdapter: SearchResultsAdapter
    private var searchQuery: String = ""
    private var hotelsListener: ListenerRegistration? = null
    private var roomTypesListener: ListenerRegistration? = null
    private val roomTypesCache = mutableMapOf<String, MutableList<RoomType>>()
    private val hotelsCache = mutableListOf<Hotel>()

    companion object {
        const val EXTRA_SEARCH_QUERY = "search_query"
        const val EXTRA_LOCATION = "location"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentSearchResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseRepository = FirebaseRepository()

        // Get search query from intent
        searchQuery = intent.getStringExtra(EXTRA_SEARCH_QUERY) ?: ""

        // Set up UI elements
        setupUI()

        // Set up RecyclerView
        RecyclerViewUtils.configureRecyclerView(binding.rvSearchResults)

        searchResultsAdapter = SearchResultsAdapter(emptyList())
        searchResultsAdapter.onHotelClick = { hotelId ->
            // Navigate to hotel detail
            val intent = android.content.Intent(this, HotelDetailActivity::class.java)
            intent.putExtra("HOTEL_ID", hotelId)
            startActivity(intent)
        }
        binding.rvSearchResults.adapter = searchResultsAdapter

        // Perform search
        performSearch()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            hotelsListener?.remove()
            roomTypesListener?.remove()
        } catch (_: Exception) { }
        hotelsListener = null
        roomTypesListener = null
    }

    private fun setupUI() {
        // Set search query in the search field
        binding.tvSearchQuery.text = searchQuery

        // Set up back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Set up sort button
        binding.btnSort.setOnClickListener {
            showSortOptions()
        }

        // Set up filter button
        binding.btnFilter.setOnClickListener {
            showFilterOptions()
        }

        // Set up search field click to edit
        binding.searchField.setOnClickListener {
            // Navigate back to home to search again
            finish()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showSortOptions() {
        val sortOptions = arrayOf("Price: Low to High", "Price: High to Low", "Rating: High to Low", "Name: A to Z")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Sort by")
            .setItems(sortOptions) { _, which ->
                val selectedSort = sortOptions[which]
                binding.tvSortStatus.text = "Sorted by: $selectedSort"

                // Apply sorting to results
                applySorting(selectedSort)
            }
            .show()
    }

    private fun showFilterOptions() {
        val filterOptions = arrayOf("All", "Hotels Only", "Deals Only", "Reviews Only", "Price Range", "Rating")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Filter by")
            .setItems(filterOptions) { _, which ->
                val selectedFilter = filterOptions[which]

                // Apply filtering to results
                applyFiltering(selectedFilter)
            }
            .show()
    }

    private fun applySorting(sortType: String) {
        val currentItems = searchResultsAdapter.items.toMutableList()

        when (sortType) {
            "Price: Low to High" -> {
                currentItems.sortBy { item ->
                    when (item.type) {
                        SearchResultType.HOTEL -> item.hotel?.pricePerNight ?: 0.0
                        SearchResultType.DEAL -> item.deal?.pricePerNight ?: 0.0
                        SearchResultType.REVIEW -> item.review?.pricePerNight ?: 0.0
                    }
                }
            }
            "Price: High to Low" -> {
                currentItems.sortByDescending { item ->
                    when (item.type) {
                        SearchResultType.HOTEL -> item.hotel?.pricePerNight ?: 0.0
                        SearchResultType.DEAL -> item.deal?.pricePerNight ?: 0.0
                        SearchResultType.REVIEW -> item.review?.pricePerNight ?: 0.0
                    }
                }
            }
            "Rating: High to Low" -> {
                currentItems.sortByDescending { item ->
                    when (item.type) {
                        SearchResultType.HOTEL -> item.hotel?.averageRating ?: 0.0
                        SearchResultType.DEAL -> item.deal?.averageRating ?: 0.0
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

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun performSearch() {
        val message = if (searchQuery.isBlank()) "Showing all results" else "Searching for: $searchQuery"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        performFirebaseSearch()
    }

    /**
     * Calculates similarity score between search query and text using fuzzy matching
     * Returns a score from 0.0 to 1.0, where 1.0 is exact match
     */
    private fun calculateSimilarity(query: String, text: String): Double {
        if (query.isBlank()) return 1.0
        if (text.isBlank()) return 0.0

        val queryLower = query.lowercase().trim()
        val textLower = text.lowercase().trim()

        // Exact match gets highest score
        if (textLower == queryLower) return 1.0

        // Check if text contains query (substring match)
        if (textLower.contains(queryLower)) {
            // Calculate position-based score (earlier matches are better)
            val index = textLower.indexOf(queryLower)
            val positionScore = 1.0 - (index.toDouble() / textLower.length)
            return 0.7 + (positionScore * 0.3) // Between 0.7 and 1.0
        }

        // Check if query contains text (partial match)
        if (queryLower.contains(textLower)) {
            return 0.5
        }

        // Word-by-word matching
        val queryWords = queryLower.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val textWords = textLower.split("\\s+".toRegex()).filter { it.isNotEmpty() }

        if (queryWords.isEmpty()) return 0.0

        var matchedWords = 0
        var totalWordScore = 0.0

        queryWords.forEach { queryWord ->
            var bestMatch = 0.0
            textWords.forEach { textWord ->
                if (textWord.contains(queryWord)) {
                    bestMatch = maxOf(bestMatch, queryWord.length.toDouble() / textWord.length)
                } else if (queryWord.contains(textWord)) {
                    bestMatch = maxOf(bestMatch, textWord.length.toDouble() / queryWord.length)
                } else {
                    // Character similarity (Levenshtein-like)
                    val similarity = calculateCharacterSimilarity(queryWord, textWord)
                    bestMatch = maxOf(bestMatch, similarity)
                }
            }
            if (bestMatch > 0.3) { // Threshold for considering a match
                matchedWords++
                totalWordScore += bestMatch
            }
        }

        val wordMatchScore = if (queryWords.isNotEmpty()) {
            (matchedWords.toDouble() / queryWords.size) * (totalWordScore / matchedWords.coerceAtLeast(1))
        } else {
            0.0
        }

        return wordMatchScore
    }

    /**
     * Calculates character-level similarity between two strings
     */
    private fun calculateCharacterSimilarity(str1: String, str2: String): Double {
        if (str1 == str2) return 1.0
        if (str1.isEmpty() || str2.isEmpty()) return 0.0

        val longer = if (str1.length > str2.length) str1 else str2
        val shorter = if (str1.length > str2.length) str2 else str1

        if (longer.length == 0) return 1.0

        // Count common characters
        var commonChars = 0
        val longerChars = longer.toCharArray().toMutableList()
        shorter.forEach { char ->
            val index = longerChars.indexOf(char)
            if (index >= 0) {
                commonChars++
                longerChars.removeAt(index)
            }
        }

        return (commonChars * 2.0) / (longer.length + shorter.length)
    }

    /**
     * Filters hotels based on fuzzy matching with search query
     */
    private fun filterHotelsWithFuzzyMatch(hotels: List<Hotel>, query: String): List<Pair<Hotel, Double>> {
        if (query.isBlank()) {
            return hotels.map { it to 1.0 }
        }

        return hotels.map { hotel ->
            val nameScore = calculateSimilarity(query, hotel.hotelName)
            val addressScore = calculateSimilarity(query, hotel.hotelAddress)
            val descriptionScore = if (hotel.description.isNotEmpty()) {
                calculateSimilarity(query, hotel.description)
            } else {
                0.0
            }

            // Weighted average: name is most important, then address, then description
            val combinedScore = (nameScore * 0.6) + (addressScore * 0.3) + (descriptionScore * 0.1)
            hotel to combinedScore
        }.filter { it.second > 0.1 } // Only include results with at least 10% similarity
            .sortedByDescending { it.second } // Sort by relevance (highest first)
    }

    private fun performFirebaseSearch() {
        val results = mutableListOf<SearchResultItem>()

        try {
            // Helper function to filter and update hotel results
            fun updateHotelResults(hotels: List<Hotel>) {
                // Use fuzzy matching to filter hotels
                val filteredHotelsWithScores = filterHotelsWithFuzzyMatch(hotels, searchQuery)

                // Update prices using cached room types
                val hotelsWithPrices = filteredHotelsWithScores.map { (hotel, _) ->
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
            }

            // Observe hotels in real-time
            hotelsListener?.remove()
            hotelsListener = firebaseRepository.db.collection("hotels")
                .addSnapshotListener { hotelResult, error ->
                    if (error != null) {
                        Toast.makeText(this, "Error searching hotels: ${error.message}", Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, "Search error: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("Search", "Exception in search: ${e.message}")
        }
    }
}