package com.tdc.nhom6.roomio.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.DealsAdapter
import com.tdc.nhom6.roomio.adapters.HotReviewAdapter
import com.tdc.nhom6.roomio.databinding.FragmentHomeLayoutBinding
import com.tdc.nhom6.roomio.models.Deal
import com.tdc.nhom6.roomio.models.DealItem
import com.tdc.nhom6.roomio.models.HotReviewItem
import com.tdc.nhom6.roomio.models.Hotel
import com.tdc.nhom6.roomio.repositories.FirebaseRepository


class HomeFragment : Fragment() {
    // ViewBinding instance
    private var _binding: FragmentHomeLayoutBinding? = null
    private val binding get() = _binding!!

    // Adapters for our RecyclerViews
    private lateinit var hotReviewAdapter: HotReviewAdapter
    private lateinit var dealsAdapter: DealsAdapter

    // Firebase repository for data operations
    private lateinit var firebaseRepository: FirebaseRepository
    private var hotReviews = emptyList<Hotel>()
    private var hotReviewsListener: ListenerRegistration? = null
    private var dealsListener: ListenerRegistration? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentHomeLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.searchEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.searchEditText.clearFocus()  // ngăn EditText chặn click
                openSearchResults()
            }
        }

        // Initialize Firebase repository
        firebaseRepository = FirebaseRepository()

        // Set up the Hot Reviews RecyclerView
        setupHotReviewsRecyclerView(view)

        // Set up the Deals RecyclerView
        setupDealsRecyclerView(view)

        // Set up search functionality
        setupSearchFunctionality(view)

        // Set up "View all" actions to show list view
        view.findViewById<android.widget.TextView>(R.id.tvViewAllHot)?.setOnClickListener {
            navigateToSearchResults("")
        }
        view.findViewById<android.widget.TextView>(R.id.tvViewAllDeals)?.setOnClickListener {
            navigateToSearchResults("")
        }

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
        val searchLayout =
            view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.searchLayout)
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
        val hasPlay = firebaseRepository.isPlayServicesAvailable(requireActivity())

        if (hasPlay) {
            // Observe hot reviews (realtime)
            hotReviewsListener?.remove()
            hotReviewsListener = firebaseRepository.observeHotReviews { hotels ->
                hotReviews = hotels
                hotReviewAdapter.updateData(hotReviews)
                Log.d("Firebase", "Realtime: ${hotReviews.size} hot reviews")
            }

            // Observe deals (realtime)
            dealsListener?.remove()
            dealsListener = firebaseRepository.observeDeals { deals ->
                if (::dealsAdapter.isInitialized) {
                    dealsAdapter.updateData(deals.map { it.toDealItem() })
                    Log.d("Firebase", "Realtime: ${deals.size} deals")
                }
            }
        } else {
            // Fallback to one-shot loads when Play Services missing
            firebaseRepository.getHotReviews { hotels ->
                hotReviews = hotels
                hotReviewAdapter.updateData(hotReviews)
                Log.d("Firebase", "One-shot: ${hotReviews.size} hot reviews")
            }
            firebaseRepository.getDeals { deals ->
                if (::dealsAdapter.isInitialized) {
                    dealsAdapter.updateData(deals.map { it.toDealItem() })
                    Log.d("Firebase", "One-shot: ${deals.size} deals")
                }
            }
        }
    }


    /**
     * Navigates to search results screen
     * This is called when user searches for something
     */
    private fun navigateToSearchResults(query: String) {

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

    private fun openSearchResults() {
        val fragment = SearchResultsFragment.newInstance("", "") // query rỗng để load ALL

        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_container, fragment)
            .addToBackStack(null)
            .commit()

        // Đợi fragment tạo view xong rồi mới focus
        fragment.viewLifecycleOwnerLiveData.observe(viewLifecycleOwner) { owner ->
            if (owner != null) {
                fragment.focusSearchBox()
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        try {
            hotReviewsListener?.remove()
            dealsListener?.remove()
        } catch (_: Exception) {
        }
        hotReviewsListener = null
        dealsListener = null
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
        "bungalow" -> R.drawable.bungalow
        "caption" -> R.drawable.ic_not_image
        "bungalow" -> R.drawable.bungalow
        else -> R.drawable.ic_not_image
    }
}
