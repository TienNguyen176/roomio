package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.BrokenFurnitureAdapter
import com.tdc.nhom6.roomio.adapters.LostItemAdapter
import com.tdc.nhom6.roomio.adapters.MiniBarAdapter
import com.tdc.nhom6.roomio.data.CleanerTaskRepository

class CleaningInspectionActivity : AppCompatActivity() {
    private lateinit var tvReservationInfo: TextView
    private lateinit var tvTotalCharges: TextView

    private lateinit var lostAdapter: LostItemAdapter
    private lateinit var brokenAdapter: BrokenFurnitureAdapter
    private lateinit var miniBarAdapter: MiniBarAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cleaning_inspection)

        tvReservationInfo = findViewById(R.id.tvReservationInfo)
        tvTotalCharges = findViewById(R.id.tvTotalCharges)

        // Back button
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Show simple reservation/room id if provided
        val roomId = intent.getStringExtra("ROOM_ID") ?: ""
        tvReservationInfo.text = roomId

        // Setup lists
        val rvLost = findViewById<RecyclerView>(R.id.rvLostItems)
        val rvBroken = findViewById<RecyclerView>(R.id.rvBrokenFurniture)
        val rvMini = findViewById<RecyclerView>(R.id.rvMiniBar)

        rvLost.layoutManager = LinearLayoutManager(this)
        rvBroken.layoutManager = LinearLayoutManager(this)
        rvMini.layoutManager = LinearLayoutManager(this)

        lostAdapter = LostItemAdapter(defaultLostItems().toMutableList()) { updateTotal() }
        brokenAdapter = BrokenFurnitureAdapter(defaultBrokenItems().toMutableList()) { updateTotal() }
        miniBarAdapter = MiniBarAdapter(defaultMiniBarItems().toMutableList()) { updateTotal() }

        rvLost.adapter = lostAdapter
        rvBroken.adapter = brokenAdapter
        rvMini.adapter = miniBarAdapter

        // Done button - navigate to cleaner task detail screen
        findViewById<MaterialButton>(R.id.btnDone).setOnClickListener {
            // Post cleaning fee result back to repository for reception
            val fee = currentTotal()
            val id = intent.getStringExtra("ROOM_ID") ?: ""
            CleanerTaskRepository.postCleaningResult(id, fee)
            
            // Navigate to cleaner task detail screen
            val detailIntent = Intent(this, CleanerTaskDetailActivity::class.java)
            detailIntent.putExtra("ROOM_ID", id)
            // Get checkout time from task if available, otherwise use default
            val tasks = CleanerTaskRepository.tasks().value ?: emptyList()
            val task = tasks.find { it.roomId == id }
            val checkoutTime = "11.00 PM" // Default, can be enhanced later
            detailIntent.putExtra("CHECKOUT_TIME", checkoutTime)
            detailIntent.putExtra("NOTES", "Replace bedding and towels") // Default notes
            startActivity(detailIntent)
            finish()
        }

        updateTotal()
    }

    private fun updateTotal() {
        val total = currentTotal()
        tvTotalCharges.text = String.format("Total additional chargers: %,.0fVND", total)
    }

    private fun currentTotal(): Double =
        lostAdapter.totalCharges() + brokenAdapter.totalCharges() + miniBarAdapter.totalCharges()

    private fun defaultLostItems(): List<LostItemAdapter.LostItem> = listOf(
        LostItemAdapter.LostItem("Flipflop", 0.0),
        LostItemAdapter.LostItem("Towel", 0.0),
        LostItemAdapter.LostItem("Remote", 0.0),
        LostItemAdapter.LostItem("Blanket", 0.0),
        LostItemAdapter.LostItem("Shampoo", 0.0),
        LostItemAdapter.LostItem("Hair dryer", 0.0)
    )

    private fun defaultBrokenItems(): List<BrokenFurnitureAdapter.BrokenItem> = listOf(
        BrokenFurnitureAdapter.BrokenItem("Bed", 0.0),
        BrokenFurnitureAdapter.BrokenItem("TV", 0.0),
        BrokenFurnitureAdapter.BrokenItem("Shower", 0.0),
        BrokenFurnitureAdapter.BrokenItem("Lamp", 0.0),
        BrokenFurnitureAdapter.BrokenItem("Window", 0.0),
        BrokenFurnitureAdapter.BrokenItem("Hair dryer", 0.0)
    )

    private fun defaultMiniBarItems(): List<MiniBarAdapter.MiniItem> = listOf(
        MiniBarAdapter.MiniItem("Snacks", 15000.0),
        MiniBarAdapter.MiniItem("Water", 10000.0),
        MiniBarAdapter.MiniItem("Coffee", 20000.0),
        MiniBarAdapter.MiniItem("Tea", 15000.0),
        MiniBarAdapter.MiniItem("Fruits", 30000.0)
    )
}


