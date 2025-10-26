package com.tdc.nhom6.roomio.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapter.DealsAdapter
import com.tdc.nhom6.roomio.adapter.HotReviewAdapter
import com.tdc.nhom6.roomio.model.Deal
import com.tdc.nhom6.roomio.model.DealItem
import com.tdc.nhom6.roomio.model.HotReview
import com.tdc.nhom6.roomio.model.HotReviewItem
import com.tdc.nhom6.roomio.model.Hotel
import com.tdc.nhom6.roomio.repository.FirebaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class HomeFragment : Fragment() {

    // Adapters for our RecyclerViews
    private lateinit var hotReviewAdapter: HotReviewAdapter
    private lateinit var dealsAdapter: DealsAdapter

    // Firebase repository for data operations
    private lateinit var firebaseRepository: FirebaseRepository
    private var hotReviews = emptyList<Hotel>()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase repository
        firebaseRepository = FirebaseRepository()

        // Set up the Hot Reviews RecyclerView
        setupHotReviewsRecyclerView(view)

        // Set up the Deals RecyclerView
        setupDealsRecyclerView(view)

        // Set up search functionality
        setupSearchFunctionality(view)

        // Load data from Firebase
        loadDataFromFirebase()

    }
    /**
     * Sets up the Hot Reviews RecyclerView
     * This shows hotel reviews in a horizontal scrolling list
     */
    private fun setupHotReviewsRecyclerView(view: View) {
        val rvHotReviews = view.findViewById<RecyclerView>(R.id.rvHotReview)

        // Set horizontal layout manager with improved configuration
        val layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )

        // Configure RecyclerView to prevent swap behavior issues
        rvHotReviews.layoutManager = layoutManager
        rvHotReviews.setItemViewCacheSize(20) // Cache more views to prevent recycling issues
        rvHotReviews.itemAnimator = null // Disable animations to prevent swap behavior errors
        rvHotReviews.setNestedScrollingEnabled(false) // Disable nested scrolling
        rvHotReviews.isNestedScrollingEnabled = false

        // Create adapter with empty list initially
        hotReviewAdapter = HotReviewAdapter(hotReviews)
        rvHotReviews.adapter = hotReviewAdapter
    }

    /**
     * Sets up the Deals RecyclerView
     * This shows hotel deals in a grid layout (2 columns)
     */
    private fun setupDealsRecyclerView(view: View) {
        val rvDeals = view.findViewById<RecyclerView>(R.id.rvDeals)

        // Set grid layout manager with 2 columns
        val gridLayoutManager = GridLayoutManager(requireContext(), 2)

        // Configure RecyclerView to prevent swap behavior issues
        rvDeals.layoutManager = gridLayoutManager
        rvDeals.setItemViewCacheSize(20) // Cache more views to prevent recycling issues
        rvDeals.itemAnimator = null // Disable animations to prevent swap behavior errors
        rvDeals.setNestedScrollingEnabled(false) // Disable nested scrolling
        rvDeals.isNestedScrollingEnabled = false

        // Create adapter with empty list initially
        dealsAdapter = DealsAdapter(emptyList())
        rvDeals.adapter = dealsAdapter
    }

    /**
     * Sets up search functionality
     * When user types and presses search, it navigates to search results
     */
    private fun setupSearchFunctionality(view: View) {
        val searchLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.searchLayout)
        val searchEditText = searchLayout.editText

        // Handle search button click
        searchLayout.setEndIconOnClickListener {
            val query = searchEditText?.text?.toString() ?: ""
            if (query.isNotEmpty()) {
                navigateToSearchResults(query)
            }
        }

        // Handle search on enter key press
        searchEditText?.setOnEditorActionListener { _, _, _ ->
            val query = searchEditText.text?.toString() ?: ""
            if (query.isNotEmpty()) {
                navigateToSearchResults(query)
            }
            true
        }
    }

    /**
     * Loads data from Firebase
     * This replaces sample data with real Firebase data
     */
    private fun loadDataFromFirebase() {
        // Load hot reviews
        firebaseRepository.getHotReviews { hotels ->
            if (hotels.isNotEmpty()) {
                hotReviews = hotels
                hotReviewAdapter.updateData(hotReviews)
                Log.d("Firebase", "Loaded ${hotReviews.size} hotels")
            } else {
                // If no Firebase data, show sample data
                loadSampleData()
            }
        }

        // Load deals
        firebaseRepository.getDeals { deals ->
            if (::dealsAdapter.isInitialized) {
                if (deals.isNotEmpty()) {
                    dealsAdapter.updateData(deals.map { it.toDealItem() })
                    Log.d("Firebase", "Loaded ${deals.size} deals")
                } else {
                    // If no Firebase data, show sample deals
                    loadSampleDeals()
                }
            }
        }
    }

    /**
     * Loads sample data when Firebase is not available
     */
    private fun loadSampleData() {
        val sampleHotels = listOf(
            Hotel(
                hotelId = "sample_1",
                hotelName = "Ares Home",
                hotelAddress = "123 Beach Road, Vung Tau",
                images = listOf("hotel_64260231_1"),
                averageRating = 4.5,
                totalReviews = 234,
                pricePerNight = 1500000.0
            ),
            Hotel(
                hotelId = "sample_2", 
                hotelName = "Imperial Hotel",
                hotelAddress = "456 Imperial Street, Vung Tau",
                images = listOf("hotel_del_coronado_views_suite1600x900"),
                averageRating = 4.8,
                totalReviews = 189,
                pricePerNight = 1200000.0
            )
        )
        
        hotReviews = sampleHotels
        hotReviewAdapter.updateData(hotReviews)
        Log.d("Firebase", "Loaded ${hotReviews.size} sample hotels")
    }

    /**
     * Loads sample deals when Firebase is not available
     */
    private fun loadSampleDeals() {
        val sampleDeals = listOf(
            DealItem(
                imageRes = R.drawable.hotel_del_coronado_views_suite1600x900,
                title = "Imperial Hotel",
                subtitle = "Vung Tau"
            ),
            DealItem(
                imageRes = R.drawable.swimming_pool_1,
                title = "Beach Resort",
                subtitle = "Nha Trang"
            )
        )
        
        if (::dealsAdapter.isInitialized) {
            dealsAdapter.updateData(sampleDeals)
            Log.d("Firebase", "Loaded ${sampleDeals.size} sample deals")
        }
    }

    /**
     * Navigates to search results screen
     * This is called when user searches for something
     */
    private fun navigateToSearchResults(query: String) {
        if (query.trim().isEmpty()) {
            android.widget.Toast.makeText(
                requireContext(),
                "Please enter a search term",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            // Create SearchResultsFragment with the search query
            val searchFragment = SearchResultsFragment.newInstance(query, "")

            // Navigate to search results fragment
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_container, searchFragment)
                .addToBackStack("search_results")
                .commit()

        } catch (e: Exception) {
            android.widget.Toast.makeText(
                requireContext(),
                "Error opening search: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
            println("Search navigation error: ${e.message}")
        }
    }
}

// Extension functions to convert Firebase models to UI models
private fun Hotel.toHotReviewItem(): HotReviewItem {
    val imageRes = getDrawableResourceId(images.firstOrNull() ?: "hotel_64260231_1")
    return HotReviewItem(
        imageRes = imageRes,
        title = hotelName,
        ratingText = "${averageRating} (${totalReviews})",
        priceText = "VND ${String.format("%.0f", pricePerNight)}"
    )
}

private fun Deal.toDealItem(): DealItem {
    val imageRes = getDrawableResourceId(imageUrl)
    return DealItem(
        imageRes = imageRes,
        title = hotelName,
        subtitle = hotelLocation
    )
}

// Helper function to get drawable resource ID from name
private fun getDrawableResourceId(imageName: String): Int {
    return when (imageName) {
        "hotel_64260231_1" -> R.drawable.hotel_64260231_1
        "hotel_del_coronado_views_suite1600x900" -> R.drawable.hotel_del_coronado_views_suite1600x900
        "swimming_pool_1" -> R.drawable.swimming_pool_1
        "room_640278495" -> R.drawable.room_640278495
        "radisson_blue_camranh" -> R.drawable.radisson_blue_camranh
        "bungalow" -> R.drawable.bungalow
        "dsc04512_scaled_1" -> R.drawable.dsc04512_scaled_1
        "dn587384532" -> R.drawable.dn587384532
        "cc449227_khach_san_quan_1_view_dep_19" -> R.drawable.cc449227_khach_san_quan_1_view_dep_19
        "caption" -> R.drawable.caption
        else -> R.drawable.rectangle_copy_2
    }
}
