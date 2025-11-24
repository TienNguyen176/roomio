package com.tdc.nhom6.roomio.fragments

import android.content.Context
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import com.tdc.nhom6.roomio.databinding.FragmentSearchResultsLayoutBinding
import com.tdc.nhom6.roomio.models.Hotel
import com.tdc.nhom6.roomio.models.SearchResultItem
import com.tdc.nhom6.roomio.models.SearchResultType
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestore
import com.tdc.nhom6.roomio.adapters.SearchResultsAdapter
import com.tdc.nhom6.roomio.models.Facility
import com.tdc.nhom6.roomio.models.Service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SearchResultsFragment : Fragment() {

    private var _binding: FragmentSearchResultsLayoutBinding? = null
    private val binding get() = _binding!!

    private lateinit var searchResultsAdapter: SearchResultsAdapter
    private var searchQuery: String = ""

    private val db = FirebaseFirestore.getInstance()
    private var hotelsListener: ListenerRegistration? = null

    private var minPrice = 1_000_000 // Giá mặc định Min
    private var maxPrice = 10_000_000 // Giá mặc định Max

    private val hotelsCache = mutableListOf<Hotel>()
    private var currentComparator: Comparator<Hotel>? = null

    private var allTienIch: List<Facility> = emptyList()
    private var allServices: List<Service> = emptyList()

    companion object {
        fun newInstance(query: String, location: String): SearchResultsFragment {
            val fragment = SearchResultsFragment()
            val args = Bundle()
            args.putString("query", query)
            args.putString("location", location)
            fragment.arguments = args
            return fragment
        }
        private const val TAG = "SearchResultsFragment"
    }

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
        loadAllTienIch()

        loadHotelsFromFirebase()


        // Tự động focus vào thanh tìm kiếm sau khi màn hình được tạo
        binding.edtSearchQuery.requestFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hotelsListener?.remove()
        _binding = null
    }

    // ====================================
    // UI
    // ====================================
    private fun setupUI() {
        // NOTE: ensure your layout's EditText id is edtSearchQuery (not tvSearchQuery)
        binding.edtSearchQuery.setText(searchQuery)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnSort.setOnClickListener { showSortOptions() }

        binding.btnFilter.setOnClickListener {
            val filterFragment = FilterBottomSheetFragment()
            filterFragment.setAmenities(allServices)
            filterFragment.setTienIch(allTienIch)

            // ⭐ NHẬN KẾT QUẢ LỌC TỪ FILTER BOTTOM SHEET
            filterFragment.setOnApplyFilterListener { min, max, selectedServices, selectedFacilities ->
                Log.d(TAG, "Filter applied: min=$min max=$max services=${selectedServices.size} facilities=${selectedFacilities.size}")
                applyFilter(min, max, selectedServices, selectedFacilities)
            }

            filterFragment.show(parentFragmentManager, FilterBottomSheetFragment::class.java.simpleName)
        }

        // Cho phép focus và show keyboard khi cần
        binding.searchBar.isFocusableInTouchMode = true

        binding.edtSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {

                // Chạy filter
                filterHotels(binding.edtSearchQuery.text.toString().trim())

                // Ẩn bàn phím
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.edtSearchQuery.windowToken, 0)

                true
            } else {
                false
            }
        }
    }

    private fun setupRecyclerView() {
        binding.rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        searchResultsAdapter = SearchResultsAdapter(emptyList())
        binding.rvSearchResults.adapter = searchResultsAdapter
    }

    fun focusSearchBox() {
        view?.post {
            binding.edtSearchQuery.requestFocus()
        }
    }

    // Áp filter nhận từ bottom sheet
    private fun applyFilter(
        min: Int,
        max: Int,
        selectedServiceIds: Set<String>,
        selectedFacilityIds: Set<String>
    ) {
        minPrice = min
        maxPrice = max

        var filtered = hotelsCache.filter { hotel ->

            // Convert pricePerNight Double → Int
            val price = hotel.pricePerNight.toInt()
            val priceMatch = price in minPrice..maxPrice

            val nameMatch = hotel.hotelName.lowercase().contains(searchQuery.lowercase())
            // Lọc dịch vụ bằng service_name
            val serviceMatch =
                if (selectedServiceIds.isEmpty()) true
                else hotel.serviceIds.containsAll(selectedServiceIds)

            // Lọc tiện ích bằng facility_name (nếu bạn dùng tên)
            val facilityMatch =
                if (selectedFacilityIds.isEmpty()) true
                else hotel.facilityIds.containsAll(selectedFacilityIds)

            priceMatch && nameMatch && serviceMatch && facilityMatch
        }



        currentComparator?.let { comparator ->
            filtered = filtered.sortedWith(comparator)
        }

        updateHotelResults(filtered)
    }

    // ====================================
    // SEARCH REALTIME
    // ====================================
    private fun setupSearchRealtime() {
        // use EditText's change listener
        binding.edtSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString()?.trim() ?: ""
                searchQuery = text

                if (text.isEmpty()) {
                    // nếu đang có filter giá => vẫn áp filter giá
                    val listToShow = hotelsCache.filter {
                        it.pricePerNight.toInt() in minPrice..maxPrice
                    }
                    updateHotelResults(listToShow)
                } else {
                    filterHotels(text)
                }
            }
        })
    }

    // ====================================
    // FIREBASE
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
                    // show with current price filter applied
                    val listToShow = hotelsCache.filter { it.pricePerNight.toInt() in minPrice..maxPrice }
                    updateHotelResults(listToShow)
                } else {
                    filterHotels(searchQuery)
                }
            }
        }
    }

    // ====================================
    // FILTER / SEARCH / UPDATE UI
    // ====================================
    private fun filterHotels(query: String) {
        val lower = query.lowercase()
        var filtered = hotelsCache.filter { hotel ->
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
        searchResultsAdapter.updateData(
            list.map {
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
    // SORT
    // ====================================
    private fun showSortOptions() {
        val sortOptions = arrayOf(
            "Giá: Thấp đến Cao",
            "Giá: Cao xuống thấp",
            "Tên: A - Z"
        )

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Sort by")
            .setItems(sortOptions) { _, which ->

                currentComparator = when (which) {
                    0 -> compareBy { it.pricePerNight }
                    1 -> compareByDescending { it.pricePerNight }
                    2 -> compareBy { it.hotelName }
                    else -> null
                }

                // Áp lại filter/search
                if (searchQuery.isBlank()) filterHotels("") else filterHotels(searchQuery)
            }
            .show()
    }

    // ====================================
    // LOAD FACILITIES & SERVICES
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

    private fun loadAllTienIch() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val snap = db.collection("facilities").get().await()
                allTienIch = snap.toObjects(Facility::class.java)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed facilities load!", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "loadAllTienIch error: ${e.message}")
                }
            }
        }
    }
}
