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

    private lateinit var hotReviewAdapter: HotReviewAdapter
    private lateinit var dealsAdapter: DealsAdapter

    private lateinit var firebaseRepository: FirebaseRepository

    private var hotReviews: List<Hotel> = emptyList()
    private var deals: List<Hotel> = emptyList()

    private var hotReviewsListener: ListenerRegistration? = null
    private var dealsListener: ListenerRegistration? = null

    // ... (onCreateView)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firebaseRepository = FirebaseRepository()

        setupHotReviewsRecyclerView(view)
        setupDealsRecyclerView(view)
        setupSearchFunctionality(view)

        view.findViewById<android.widget.TextView>(R.id.tvViewAllHot)?.setOnClickListener {
            navigateToSearchResults("")
        }
        view.findViewById<android.widget.TextView>(R.id.tvViewAllDeals)?.setOnClickListener {
            navigateToSearchResults("")
        }

        loadDataFromFirebase()
    }

    private fun loadDataFromFirebase() {
        val hasPlay = firebaseRepository.isPlayServicesAvailable(requireActivity())
        Log.d("Firebase", "HomeFragment: hasPlay=$hasPlay")

        hotReviewsListener?.remove()

        if (hasPlay) {
            // Realtime: Lắng nghe Hot Reviews
            hotReviewsListener = firebaseRepository.observeHotReviews { hotels ->
                hotReviews = hotels
                hotReviewAdapter.updateData(hotReviews)
                Log.d("Firebase", "Realtime: ${hotReviews.size} hot reviews loaded.")
            }
        } else {
            firebaseRepository.getHotReviews { hotels ->
                hotReviews = hotels
                hotReviewAdapter.updateData(hotReviews)
                Log.d("Firebase", "One-shot: ${hotReviews.size} hot reviews loaded.")
            }
        }

        dealsListener?.remove()

        if (hasPlay) {
            dealsListener = firebaseRepository.observeDealsHotels { hotels ->
                deals = hotels
                dealsAdapter.updateData(deals)
                Log.d("Firebase", "Realtime: ${deals.size} deals loaded.")
            }
        } else {
            firebaseRepository.observeDealsHotels { hotels ->
                deals = hotels
                dealsAdapter.updateData(deals)
                Log.d("Firebase", "One-shot: ${deals.size} deals loaded.")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        try {
            hotReviewsListener?.remove()
            dealsListener?.remove()
        } catch (e: Exception) {
            Log.e("Firebase", "Error removing listeners: ${e.message}")
        }
        hotReviewsListener = null
        dealsListener = null
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    private fun setupHotReviewsRecyclerView(view: View) {
        val rvHotReviews = view.findViewById<RecyclerView>(R.id.rvHotReview)

        val layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )
        RecyclerViewUtils.configureRecyclerView(rvHotReviews, layoutManager)

        hotReviewAdapter = HotReviewAdapter(hotReviews)
        rvHotReviews.adapter = hotReviewAdapter
    }

    private fun setupDealsRecyclerView(view: View) {
        val rvDeals = view.findViewById<RecyclerView>(R.id.rvDeals)

        val gridLayoutManager = GridLayoutManager(requireContext(), 2)
        RecyclerViewUtils.configureRecyclerView(rvDeals, gridLayoutManager)

        // Khởi tạo Adapter với biến deals đã khai báo
        dealsAdapter = DealsAdapter(deals)
        rvDeals.adapter = dealsAdapter
    }

    private fun setupSearchFunctionality(view: View) {
        val searchLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.searchLayout)
        val searchEditText = searchLayout.editText

        searchLayout.setEndIconOnClickListener {
            val query = searchEditText?.text?.toString() ?: ""
            if (query.isNotEmpty()) {
                navigateToSearchResults(query)
            }
        }

        searchEditText?.setOnEditorActionListener { _, _, _ ->
            val query = searchEditText.text?.toString() ?: ""
            if (query.isNotEmpty()) {
                navigateToSearchResults(query)
            }
            true
        }
    }

    private fun navigateToSearchResults(query: String) {
        try {
            val intent = android.content.Intent(requireContext(), com.tdc.nhom6.roomio.activities.SearchActivity::class.java)
            intent.putExtra(com.tdc.nhom6.roomio.activities.SearchActivity.EXTRA_SEARCH_QUERY, query)
            intent.putExtra(com.tdc.nhom6.roomio.activities.SearchActivity.EXTRA_LOCATION, "")
            startActivity(intent)

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