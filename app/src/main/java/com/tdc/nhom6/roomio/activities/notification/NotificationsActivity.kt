package com.tdc.nhom6.roomio.activities.notification

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tdc.nhom6.roomio.adapters.NotificationAdapter
import com.tdc.nhom6.roomio.databinding.NotificationsLayoutBinding

class NotificationsActivity : AppCompatActivity() {

    private lateinit var binding: NotificationsLayoutBinding
    private lateinit var adapter: NotificationAdapter
    private val notifications = mutableListOf<DocumentSnapshot>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = NotificationsLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Danh sách thông báo"

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Vui lòng đăng nhập để xem thông báo", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val userId = currentUser.uid // user hiện tại

        adapter = NotificationAdapter(notifications) { doc ->
            // đánh dấu đã đọc
            db.collection("notifications").document(userId)
                .collection("notifications").document(doc.id)
                .update("read", true)
            // xử lý click theo type/screen
            Toast.makeText(this, doc.getString("title"), Toast.LENGTH_SHORT).show()
        }

        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = adapter

        loadNotifications(userId)
    }

    private fun loadNotifications(userId: String) {
        db.collection("notifications").document(userId)
            .collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Notifications", error.message ?: "")
                    return@addSnapshotListener
                }

                notifications.clear()
                if (snapshot != null && !snapshot.isEmpty) {
                    notifications.addAll(snapshot.documents)
                    binding.tvNoNotifications.visibility = android.view.View.GONE
                    binding.rvNotifications.visibility = android.view.View.VISIBLE
                } else {
                    binding.tvNoNotifications.visibility = android.view.View.VISIBLE
                    binding.rvNotifications.visibility = android.view.View.GONE
                }

                adapter.notifyDataSetChanged()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
