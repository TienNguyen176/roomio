package com.tdc.nhom6.roomio.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.model.Deal
import com.tdc.nhom6.roomio.model.HotReview
import com.tdc.nhom6.roomio.repository.FirebaseRepository
import com.tdc.nhom6.roomio.utils.FirebaseDataSeeder
import com.tdc.nhom6.roomio.utils.ToastUtils
import com.tdc.nhom6.roomio.utils.PlayServicesUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

class HomeFragment : Fragment() {
    private lateinit var firebaseRepository: FirebaseRepository
    private lateinit var hotReviewAdapter: HotReviewAdapter
    private lateinit var dealsAdapter: DealsAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        firebaseRepository = FirebaseRepository()

        val rvHot = view.findViewById<RecyclerView>(R.id.rvHotReview)
        rvHot.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        hotReviewAdapter = HotReviewAdapter(emptyList())
        rvHot.adapter = hotReviewAdapter

        val rvDeals = view.findViewById<RecyclerView>(R.id.rvDeals)
        rvDeals.layoutManager = GridLayoutManager(requireContext(), 2)
        dealsAdapter = DealsAdapter(emptyList())
        rvDeals.adapter = dealsAdapter

        
        // Set up search functionality
        val searchLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.searchLayout)
        val searchEditText = searchLayout.editText
        searchLayout.setEndIconOnClickListener {
            val query = searchEditText?.text?.toString() ?: ""
            if (query.isNotEmpty()) {
                navigateToSearchResults(query)
            }
        }
        
        //Handle search on enter key
        searchEditText?.setOnEditorActionListener { _, _, _ ->
            val query = searchEditText.text?.toString() ?: ""
            if (query.isNotEmpty()) {
                navigateToSearchResults(query)
            }
            true
        }
        
//        // Set up debug button (temporary)
//        val btnDebugFirebase = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDebugFirebase)
//        btnDebugFirebase.setOnClickListener {
//            debugFirebaseConnection()
//        }
//
        loadDataFromFirebase()
    }
    
    private fun loadDataFromFirebase() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                println("HomeFragment: Starting Firebase data loading...")
                println("HomeFragment: Context: ${requireContext()}")
                println("HomeFragment: Fragment: ${this@HomeFragment}")
                
                // Show loading state
                ToastUtils.show(requireContext(), "Connecting to Firebase...", Toast.LENGTH_SHORT)

                // Check Google Play Services first
                val playServicesAvailable = PlayServicesUtils.isAvailable(requireContext())
                println("HomeFragment: Google Play Services available: $playServicesAvailable")

                if (!playServicesAvailable) {
                    ToastUtils.show(requireContext(), "❌ Google Play Services not available", Toast.LENGTH_LONG)
                    return@launch
                }

                // Always attempt Firebase connection
                println("HomeFragment: Testing Firebase connection...")
                val isConnected = withContext(Dispatchers.IO) {
                    firebaseRepository.testFirebaseConnection()
                }

                if (!isConnected) {
                    println("HomeFragment: Firebase connection failed - retrying...")
                    ToastUtils.show(requireContext(), "Retrying Firebase connection...", Toast.LENGTH_SHORT)

                    // Wait a bit before retry
                    kotlinx.coroutines.delay(2000)

                    // Retry connection once
                    val retryConnected = withContext(Dispatchers.IO) {
                        firebaseRepository.testFirebaseConnection()
                    }

                    if (!retryConnected) {
                        println("HomeFragment: Firebase connection failed after retry")
                        ToastUtils.show(requireContext(), "❌ Cannot connect to Firebase. Check console logs for details.", Toast.LENGTH_LONG)
                        return@launch
                    }
                }

                println("HomeFragment: Firebase connected! Checking database...")
                ToastUtils.show(requireContext(), "Loading database...", Toast.LENGTH_SHORT)

                // Get collection counts
                val counts = withContext(Dispatchers.IO) {
                    firebaseRepository.getCollectionCounts()
                }
                println("HomeFragment: Database collections: $counts")

                // If database is empty, automatically initialize and seed it
                val totalCount = counts.values.sum()
                if (totalCount == 0) {
                    println("HomeFragment: Database is empty, initializing...")
                    ToastUtils.show(requireContext(), "Setting up Firebase database...", Toast.LENGTH_SHORT)
                    
                    // Initialize database structure
                    val initialized = withContext(Dispatchers.IO) {
                        firebaseRepository.initializeDatabase()
                    }
                    
                    if (initialized) {
                        println("HomeFragment: Database initialized, seeding data...")
                        ToastUtils.show(requireContext(), "Creating hotel data...", Toast.LENGTH_SHORT)
                        
                        // Seed data with retry mechanism
                        var seedingSuccess = false
                        var retryCount = 0
                        val maxRetries = 3
                        
                        while (!seedingSuccess && retryCount < maxRetries) {
                            try {
                                withContext(Dispatchers.IO) {
                                    FirebaseDataSeeder().seedInitialData()
                                }
                                seedingSuccess = true
                                println("HomeFragment: Data seeding completed successfully")
                            } catch (e: Exception) {
                                retryCount++
                                println("HomeFragment: Data seeding attempt $retryCount failed: ${e.message}")
                                if (retryCount < maxRetries) {
                                    ToastUtils.show(requireContext(), "Retrying data creation... ($retryCount/$maxRetries)", Toast.LENGTH_SHORT)
                                    kotlinx.coroutines.delay(2000) // Wait 2 seconds before retry
                                }
                            }
                        }
                        
                        if (!seedingSuccess) {
                            println("HomeFragment: Failed to seed data after $maxRetries attempts")
                            ToastUtils.show(requireContext(), "❌ Failed to create data. Check console logs.", Toast.LENGTH_LONG)
                            return@launch
                        }
                    } else {
                        println("HomeFragment: Failed to initialize database")
                        ToastUtils.show(requireContext(), "❌ Failed to create database. Check console logs.", Toast.LENGTH_LONG)
                        return@launch
                    }
                }

                println("HomeFragment: Loading hotel reviews from Firebase...")
                ToastUtils.show(requireContext(), "Loading hotel reviews...", Toast.LENGTH_SHORT)

                // Load hot reviews
                val hotReviews = withContext(Dispatchers.IO) {
                    firebaseRepository.getHotReviews()
                }
                println("HomeFragment: Loaded ${hotReviews.size} hotel reviews from Firebase")
                hotReviewAdapter.updateData(hotReviews.map { it.toHotReviewItem() })

                println("HomeFragment: Loading hotel deals from Firebase...")
                ToastUtils.show(requireContext(), "Loading hotel deals...", Toast.LENGTH_SHORT)

                // Load deals
                val deals = withContext(Dispatchers.IO) {
                    firebaseRepository.getActiveDeals()
                }
                println("HomeFragment: Loaded ${deals.size} hotel deals from Firebase")
                dealsAdapter.updateData(deals.map { it.toDealItem() })

                // Show success message
                if (hotReviews.isNotEmpty() || deals.isNotEmpty()) {
                    ToastUtils.show(requireContext(), "✅ Firebase loaded! ${hotReviews.size} hotels, ${deals.size} deals", Toast.LENGTH_LONG)
                } else {
                    ToastUtils.show(requireContext(), "⚠️ No data found in Firebase. Check console logs.", Toast.LENGTH_LONG)
                }

                println("HomeFragment: Firebase data loading completed successfully")

            } catch (e: Exception) {
                println("HomeFragment: Error loading Firebase data: ${e.message}")
                println("HomeFragment: Error type: ${e.javaClass.simpleName}")
                println("HomeFragment: Error details: ${e.toString()}")
                e.printStackTrace()

                ToastUtils.show(requireContext(), "❌ Firebase error: ${e.message}", Toast.LENGTH_LONG)

                // Don't fallback to sample data - show error instead
                println("HomeFragment: Firebase loading failed - no fallback")
            }
        }
    }



    // Method to navigate to search results
    private fun navigateToSearchResults(query: String) {
        val searchResultsFragment = SearchResultsFragment.newInstance(query, "")

        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_container, searchResultsFragment)
            .addToBackStack("search_results")
            .commit()
    }

    // Debug method to test Firebase connection
    private fun debugFirebaseConnection() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                ToastUtils.show(requireContext(), "🔧 Starting comprehensive Firebase debug...", Toast.LENGTH_SHORT)

                // Step 1: Check Google Play Services
                val playServicesAvailable = PlayServicesUtils.isAvailable(requireContext())
                ToastUtils.show(requireContext(), "Step 1 - Google Play Services: $playServicesAvailable", Toast.LENGTH_SHORT)
                println("DEBUG: Google Play Services available: $playServicesAvailable")

                if (!playServicesAvailable) {
                    ToastUtils.show(requireContext(), "❌ Google Play Services not available - cannot proceed", Toast.LENGTH_LONG)
                    return@launch
                }

                // Step 2: Test basic Firebase connection
                ToastUtils.show(requireContext(), "Step 2 - Testing Firebase connection...", Toast.LENGTH_SHORT)
                val isConnected = withContext(Dispatchers.IO) {
                    firebaseRepository.testFirebaseConnection()
                }

                if (!isConnected) {
                    ToastUtils.show(requireContext(), "❌ Step 2 FAILED - Firebase connection failed!", Toast.LENGTH_LONG)
                    return@launch
                }

                ToastUtils.show(requireContext(), "✅ Step 2 PASSED - Firebase connected!", Toast.LENGTH_SHORT)

                // Step 3: Test basic document creation
                ToastUtils.show(requireContext(), "Step 3 - Testing document creation...", Toast.LENGTH_SHORT)
                val testDocCreated = withContext(Dispatchers.IO) {
                    try {
                        val testDoc = firebaseRepository.firestore.collection("debug_test").document("test_${System.currentTimeMillis()}")
                        testDoc.set(mapOf(
                            "test" to true,
                            "timestamp" to System.currentTimeMillis(),
                            "message" to "Debug test document"
                        )).await()
                        println("DEBUG: Test document created successfully")
                        true
                    } catch (e: Exception) {
                        println("DEBUG: Failed to create test document: ${e.message}")
                        e.printStackTrace()
                        false
                    }
                }

                if (!testDocCreated) {
                    ToastUtils.show(requireContext(), "❌ Step 3 FAILED - Cannot create documents!", Toast.LENGTH_LONG)
                    return@launch
                }

                ToastUtils.show(requireContext(), "✅ Step 3 PASSED - Document creation works!", Toast.LENGTH_SHORT)

                // Step 4: Test collection counts
                ToastUtils.show(requireContext(), "Step 4 - Testing collection access...", Toast.LENGTH_SHORT)
                val counts = withContext(Dispatchers.IO) {
                    try {
                        firebaseRepository.getCollectionCounts()
                    } catch (e: Exception) {
                        println("DEBUG: Failed to get collection counts: ${e.message}")
                        e.printStackTrace()
                        mapOf<String, Int>()
                    }
                }

                ToastUtils.show(requireContext(), "Step 4 - Collections: $counts", Toast.LENGTH_LONG)
                println("DEBUG: Collection counts: $counts")
                
                // Step 4.5: Inspect data structure
                ToastUtils.show(requireContext(), "Step 4.5 - Inspecting data structure...", Toast.LENGTH_SHORT)
                val dataStructure = withContext(Dispatchers.IO) {
                    firebaseRepository.inspectDataStructure()
                }
                ToastUtils.show(requireContext(), "Data structure: $dataStructure", Toast.LENGTH_LONG)
                println("DEBUG: Data structure: $dataStructure")
                
                // Step 5: Test data loading
                ToastUtils.show(requireContext(), "Step 5 - Testing data loading...", Toast.LENGTH_SHORT)

                val hotReviews = withContext(Dispatchers.IO) {
                    try {
                        firebaseRepository.getHotReviews()
                    } catch (e: Exception) {
                        println("DEBUG: Failed to load hot reviews: ${e.message}")
                        e.printStackTrace()
                        emptyList()
                    }
                }

                val deals = withContext(Dispatchers.IO) {
                    try {
                        firebaseRepository.getActiveDeals()
                    } catch (e: Exception) {
                        println("DEBUG: Failed to load deals: ${e.message}")
                        e.printStackTrace()
                        emptyList()
                    }
                }

                ToastUtils.show(requireContext(), "Step 5 - Data loaded: ${hotReviews.size} reviews, ${deals.size} deals", Toast.LENGTH_LONG)
                println("DEBUG: Data loaded - Reviews: ${hotReviews.size}, Deals: ${deals.size}")

                // Step 6: Test data seeding if collections are empty
                val totalCount = counts.values.sum()
                if (totalCount == 0) {
                    ToastUtils.show(requireContext(), "Step 6 - Collections empty, testing data seeding...", Toast.LENGTH_SHORT)

                    val seedingResult = withContext(Dispatchers.IO) {
                        try {
                            FirebaseDataSeeder().seedInitialData()
                            println("DEBUG: Data seeding completed")
                            true
                        } catch (e: Exception) {
                            println("DEBUG: Data seeding failed: ${e.message}")
                            e.printStackTrace()
                            false
                        }
                    }

                    if (seedingResult) {
                        ToastUtils.show(requireContext(), "✅ Step 6 PASSED - Data seeded successfully!", Toast.LENGTH_LONG)
                    } else {
                        ToastUtils.show(requireContext(), "❌ Step 6 FAILED - Data seeding failed!", Toast.LENGTH_LONG)
                    }
                } else {
                    ToastUtils.show(requireContext(), "✅ Step 6 SKIPPED - Data already exists", Toast.LENGTH_SHORT)
                }

                // Final result
                ToastUtils.show(requireContext(), "🎯 Debug complete! Check console logs for details.", Toast.LENGTH_LONG)

            } catch (e: Exception) {
                ToastUtils.show(requireContext(), "❌ Debug error: ${e.message}", Toast.LENGTH_LONG)
                println("DEBUG: Debug error: ${e.message}")
                e.printStackTrace()
            }
        }
    }


}

    // Extension functions to convert Firebase models to UI models
    private fun HotReview.toHotReviewItem(): HotReviewItem {
        val imageRes = getDrawableResourceId(hotelImage)
        return HotReviewItem(
            imageRes = imageRes,
            imageUrl = null, // Using drawable resources instead of URLs
            title = hotelName,
            ratingText = "${rating} (${totalReviews})",
            priceText = "VND ${String.format("%.0f", pricePerNight)}"
        )
    }

    private fun Deal.toDealItem(): DealItem {
        val imageRes = getDrawableResourceId(imageUrl)
        return DealItem(
            imageRes = imageRes,
            imageUrl = null, // Using drawable resources instead of URLs
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
            "rectangle_copy_2" -> R.drawable.rectangle_copy_2
            "rectangle_copy_3" -> R.drawable.rectangle_copy_3
            "dsc04512_scaled_1" -> R.drawable.dsc04512_scaled_1
            else -> R.drawable.rectangle_copy_2
        }
    }

data class HotReviewItem(
    val imageRes: Int,
    val imageUrl: String? = null,
    val title: String,
    val ratingText: String,
    val priceText: String
)

class HotReviewAdapter(private var items: List<HotReviewItem>) : RecyclerView.Adapter<HotReviewViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HotReviewViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hot_review, parent, false)
        return HotReviewViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: HotReviewViewHolder, position: Int) {
        holder.bind(items[position])
    }
    
    fun updateData(newItems: List<HotReviewItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}

class HotReviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val img = itemView.findViewById<android.widget.ImageView>(R.id.imgPhoto)
    private val title = itemView.findViewById<android.widget.TextView>(R.id.tvTitle)
    private val rating = itemView.findViewById<android.widget.TextView>(R.id.tvRating)
    private val price = itemView.findViewById<android.widget.TextView>(R.id.tvPrice)

    fun bind(item: HotReviewItem) {
        // Use drawable resource directly since we're using local images
        img.setImageResource(item.imageRes)
        title.text = item.title
        rating.text = item.ratingText
        price.text = item.priceText
    }
}

data class DealItem(
    val imageRes: Int,
    val imageUrl: String? = null,
    val title: String,
    val subtitle: String
)

class DealsAdapter(private var items: List<DealItem>) : RecyclerView.Adapter<DealsViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DealsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_deal, parent, false)
        return DealsViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: DealsViewHolder, position: Int) {
        holder.bind(items[position])
    }
    
    fun updateData(newItems: List<DealItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}

class DealsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val img = itemView.findViewById<android.widget.ImageView>(R.id.img)
    private val title = itemView.findViewById<android.widget.TextView>(R.id.tvTitle)
    private val subtitle = itemView.findViewById<android.widget.TextView>(R.id.tvSubtitle)

    fun bind(item: DealItem) {
        // Use drawable resource directly since we're using local images
        img.setImageResource(item.imageRes)
        title.text = item.title
        subtitle.text = item.subtitle
    }
}


