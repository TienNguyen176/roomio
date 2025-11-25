package com.tdc.nhom6.roomio.activities.admin

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.*
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.HotelRequestAdapter
import com.tdc.nhom6.roomio.databinding.AdminListHotelRequestLayoutBinding
import com.tdc.nhom6.roomio.databinding.TabCustomBinding
import com.tdc.nhom6.roomio.models.HotelRequestModel

class AdminListHotelRequestActivity : AppCompatActivity() {

    private lateinit var binding: AdminListHotelRequestLayoutBinding
    private val allRequests = mutableListOf<Pair<HotelRequestModel, String>>() // Pair(request, avatar)
    private val filteredRequests = mutableListOf<Pair<HotelRequestModel, String>>()
    private lateinit var adapter: HotelRequestAdapter
    private val db = FirebaseFirestore.getInstance()
    private var listenerRegistration: ListenerRegistration? = null

    private val tabTitles = listOf("Tất cả", "Đồng ý", "Từ chối", "Chờ duyệt")
    private var selectedStatus: String? = null

    companion object {
        const val REQUEST_APPROVED = "hotel_request_approved"
        const val REQUEST_REJECTED = "hotel_request_rejected"
        const val REQUEST_PENDING = "hotel_request_pending"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AdminListHotelRequestLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.app_bar_title_dang_ky_kinh_doanh)

        setupTabs()
        setEvent()
        setupRecycler()
        loadRequestsRealtime()
    }

    private fun setupRecycler() {
        adapter = HotelRequestAdapter(filteredRequests) { req ->
            val intent = Intent(this, HotelApprovalActivity::class.java)
            intent.putExtra("hotel_request_id", req.id) // gán documentId
            intent.putExtra("user_id", req.user_id)

            startActivity(intent)
        }
        binding.rvListHotelRequest.layoutManager = LinearLayoutManager(this)
        binding.rvListHotelRequest.adapter = adapter
    }

    private fun setEvent() {
        binding.edtSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
        })
    }

    private fun setupTabs() {
        tabTitles.forEachIndexed { index, title ->
            val tabBinding = TabCustomBinding.inflate(LayoutInflater.from(this))
            tabBinding.tvTab.text = title
            val tab = binding.tabStatus.newTab()
            tab.customView = tabBinding.root
            binding.tabStatus.addTab(tab)
        }

        selectTab(0)
        binding.tabStatus.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectTab(tab.position)
                selectedStatus = when(tab.position) {
                    1 -> REQUEST_APPROVED
                    2 -> REQUEST_REJECTED
                    3 -> REQUEST_PENDING
                    else -> null
                }
                filterList(binding.edtSearch.text.toString())
            }
            override fun onTabUnselected(tab: TabLayout.Tab) { unSelectTab(tab.position) }
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun selectTab(index: Int) {
        val customView = binding.tabStatus.getTabAt(index)?.customView ?: return
        val tabBinding = TabCustomBinding.bind(customView)
        tabBinding.tvTab.setBackgroundResource(R.drawable.bg_tab_selected)
        tabBinding.tvTab.setTextColor(Color.WHITE)
    }

    private fun unSelectTab(index: Int) {
        val customView = binding.tabStatus.getTabAt(index)?.customView ?: return
        val tabBinding = TabCustomBinding.bind(customView)
        tabBinding.tvTab.setBackgroundResource(R.drawable.bg_tab_unselected)
        tabBinding.tvTab.setTextColor(Color.WHITE)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadRequestsRealtime() {
        listenerRegistration?.remove()

        listenerRegistration = db.collection("hotelRequests")
            .orderBy("created_at", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                allRequests.clear()

                val docs = snapshot.documents
                if (docs.isEmpty()) {
                    filteredRequests.clear()
                    adapter.notifyDataSetChanged()
                    return@addSnapshotListener
                }

                for (doc in docs) {
                    val request = doc.toObject(HotelRequestModel::class.java) ?: continue
                    request.id = doc.id

                    // Lấy avatar user
                    db.collection("users").document(request.user_id).get()
                        .addOnSuccessListener { userDoc ->
                            val avatar = userDoc.getString("avatar") ?: ""
                            allRequests.add(Pair(request, avatar))
                            filterList(binding.edtSearch.text.toString())
                        }
                        .addOnFailureListener {
                            allRequests.add(Pair(request, ""))
                            filterList(binding.edtSearch.text.toString())
                        }
                }
            }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun filterList(query: String) {
        val q = query.lowercase()
        filteredRequests.clear()
        filteredRequests.addAll(allRequests.filter {
            val matchStatus = selectedStatus == null || it.first.status_id == selectedStatus
            val matchSearch = it.first.username.lowercase().contains(q)
            matchStatus && matchSearch
        }.sortedByDescending { it.first.created_at?.toDate()?.time })
        adapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
