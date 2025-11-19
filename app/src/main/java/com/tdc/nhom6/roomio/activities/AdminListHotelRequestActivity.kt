package com.tdc.nhom6.roomio.activities

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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.HotelRequestAdapter
import com.tdc.nhom6.roomio.databinding.AdminListHotelRequestLayoutBinding
import com.tdc.nhom6.roomio.databinding.TabCustomBinding
import com.tdc.nhom6.roomio.models.HotelRequestModel

class AdminListHotelRequestActivity : AppCompatActivity() {
    private lateinit var binding: AdminListHotelRequestLayoutBinding

    private val requestList = ArrayList<Pair<HotelRequestModel, String>>() // Hiển thị
    private val originalList = ArrayList<Pair<HotelRequestModel, String>>() // Gốc

    private lateinit var adapter: HotelRequestAdapter
    private val db = FirebaseFirestore.getInstance()

    private val tabTitles = listOf("Tất cả", "Đồng ý", "Từ chối", "Chờ duyệt")

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

        setEvent()
        setupTabs()
        setupRecycler()
        loadRequests()
    }

    private fun setEvent() {
        binding.apply {
            edtSearch.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filter(s.toString())
                }
            })
        }
    }

    private fun setupRecycler() {
        adapter = HotelRequestAdapter(requestList) { req ->
            val intent = Intent(this, HotelApprovalActivity::class.java)
            intent.putExtra("user_id", req.user_id)
            startActivity(intent)
        }

        binding.rvListHotelRequest.layoutManager = LinearLayoutManager(this)
        binding.rvListHotelRequest.adapter = adapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadRequests() {
        originalList.clear()
        requestList.clear()
        adapter.notifyDataSetChanged()

        db.collection("hotelRequests")
            .orderBy("created_at", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->

                requestList.clear()

                if (snap.isEmpty) {
                    adapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                var total = snap.size()
                var count = 0

                snap.documents.forEach { doc ->
                    val request = doc.toObject(HotelRequestModel::class.java)!!

                    db.collection("users")
                        .document(request.user_id)
                        .get()
                        .addOnSuccessListener { userDoc ->
                            val avatar = userDoc.getString("avatar") ?: ""
                            val pair = Pair(request, avatar)

                            originalList.add(pair)
                            requestList.add(pair)
                            count++

                            if (count == total) {
                                // Sort bằng Timestamp
                                requestList.sortByDescending {
                                    it.first.created_at?.toDate()?.time
                                }

                                adapter.notifyDataSetChanged()
                            }
                        }
                }
            }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadRequestsByStatus(status: String) {
        originalList.clear()
        requestList.clear()
        adapter.notifyDataSetChanged()

        db.collection("hotelRequests")
            .whereEqualTo("status_id", status)
            .orderBy("created_at", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->

                val total = snap.size()
                var count = 0

                snap.documents.forEach { doc ->
                    val request = doc.toObject(HotelRequestModel::class.java)!!

                    db.collection("users")
                        .document(request.user_id)
                        .get()
                        .addOnSuccessListener { userDoc ->
                            val avatar = userDoc.getString("avatar") ?: ""
                            val pair = Pair(request, avatar)

                            originalList.add(pair)
                            requestList.add(pair)
                            count++

                            if (count == total) {
                                requestList.sortByDescending {
                                    it.first.created_at?.toDate()?.time
                                }

                                adapter.notifyDataSetChanged()
                            }
                        }
                }
            }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun filter(text: String) {
        requestList.clear()

        if (text.isEmpty()) {
            requestList.addAll(originalList)
        } else {
            val query = text.lowercase()
            for (pair in originalList) {
                val item = pair.first
                if (item.username.lowercase().contains(query)) {
                    requestList.add(pair)
                }
            }
        }

        adapter.notifyDataSetChanged()
    }

    private fun setupTabs() {
        tabTitles.forEachIndexed { index, title ->
            val tabBinding = TabCustomBinding.inflate(LayoutInflater.from(this))

            tabBinding.tvTab.text = title

            // tạo tab mới
            val tab = binding.tabStatus.newTab()
            tab.customView = tabBinding.root

            binding.tabStatus.addTab(tab)
        }

        // chọn tab đầu tiên
        selectTab(0)

        binding.tabStatus.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectTab(tab.position)

                when (tab.position) {
                    0 -> loadRequests()
                    1 -> loadRequestsByStatus(REQUEST_APPROVED)
                    2 -> loadRequestsByStatus(REQUEST_REJECTED)
                    3 -> loadRequestsByStatus(REQUEST_PENDING)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                unSelectTab(tab.position)
            }

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun selectTab(index: Int) {
        val customView = binding.tabStatus.getTabAt(index)?.customView ?: return
        val tabBinding = TabCustomBinding.bind(customView)

        tabBinding.tvTab.apply {
            setBackgroundResource(R.drawable.bg_tab_selected)
            setTextColor(Color.WHITE)
        }
    }

    private fun unSelectTab(index: Int) {
        val customView = binding.tabStatus.getTabAt(index)?.customView ?: return
        val tabBinding = TabCustomBinding.bind(customView)

        tabBinding.tvTab.apply {
            setBackgroundResource(R.drawable.bg_tab_unselected)
            setTextColor(Color.WHITE)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}