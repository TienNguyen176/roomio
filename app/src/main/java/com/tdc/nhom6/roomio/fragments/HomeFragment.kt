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
import com.tdc.nhom6.roomio.utils.RecyclerViewUtils
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.DealsAdapter
import com.tdc.nhom6.roomio.adapters.HotReviewAdapter
import com.tdc.nhom6.roomio.models.Hotel
import com.tdc.nhom6.roomio.repository.FirebaseRepository
import com.google.firebase.firestore.ListenerRegistration

class HomeFragment : Fragment() {

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
        RecyclerViewUtils.configureRecyclerView(rvHotReviews, layoutManager)

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
        RecyclerViewUtils.configureRecyclerView(rvDeals, gridLayoutManager)

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
        val hasPlay = firebaseRepository.isPlayServicesAvailable(requireActivity())
        Log.d("Firebase", "HomeFragment: hasPlay=$hasPlay")
        if (hasPlay) {
            // Observe hot reviews (realtime)
            hotReviewsListener?.remove()
            hotReviewsListener = firebaseRepository.observeHotReviews { hotels ->
                hotReviews = hotels
                hotReviewAdapter.updateData(hotReviews)
                Log.d("Firebase", "Realtime: ${hotReviews.size} hot reviews")
            }

        } else {
            // Fallback to one-shot loads when Play Services missing
            firebaseRepository.getHotReviews { hotels ->
                hotReviews = hotels
                hotReviewAdapter.updateData(hotReviews)
                Log.d("Firebase", "One-shot: ${hotReviews.size} hot reviews")
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

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            hotReviewsListener?.remove()
            dealsListener?.remove()
        } catch (_: Exception) { }
        hotReviewsListener = null
        dealsListener = null
    }
}
