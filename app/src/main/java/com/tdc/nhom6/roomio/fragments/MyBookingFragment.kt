package com.tdc.nhom6.roomio.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.tdc.nhom6.roomio.activities.auth.LoginActivity
import com.tdc.nhom6.roomio.adapters.MyBookingAdapter
import com.tdc.nhom6.roomio.databinding.FragmentMyBookingBinding
import com.tdc.nhom6.roomio.models.Booking

class MyBookingFragment : Fragment() {

    private var _binding: FragmentMyBookingBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var bookingListener: ListenerRegistration? = null
    private lateinit var bookingAdapter: MyBookingAdapter
    private val prefs by lazy {
        requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyBookingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkSession()
        setupRecyclerView()
        fetchBookings()
    }

    private fun setupRecyclerView() {
        bookingAdapter = MyBookingAdapter()
        binding.bookingRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.bookingRecyclerView.adapter = bookingAdapter
    }

    private fun fetchBookings() {
        val userId = auth.currentUser?.uid ?: prefs.getString("uid", null)

        if (userId == null) {
            Toast.makeText(requireContext(), "Lỗi: Không tìm thấy ID người dùng.", Toast.LENGTH_SHORT).show()
            return
        }

        val query = db.collection("bookings")
            .whereEqualTo("customerId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)

        bookingListener = query.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Toast.makeText(requireContext(), "Lỗi tải danh sách đặt phòng: ${e.message}", Toast.LENGTH_LONG).show()
                return@addSnapshotListener
            }

            if (snapshots != null) {
                val bookings = mutableListOf<Booking>()
                for (document in snapshots.documents) {
                    val booking = document.toObject(Booking::class.java)
                    if (booking != null) {
                        bookings.add(booking)
                    }
                }

                bookingAdapter.submitList(bookings)

                if (bookings.isEmpty()) {
                    binding.textEmptyView.visibility = View.VISIBLE
                } else {
                    binding.textEmptyView.visibility = View.GONE
                }
            }
        }
    }

    private fun checkSession() {
        val firebaseUser = auth.currentUser
        val savedUid = prefs.getString("uid", null)

        if (firebaseUser == null && savedUid == null) {
            Toast.makeText(
                requireContext(),
                "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại",
                Toast.LENGTH_SHORT
            ).show()
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            requireActivity().finish()
            return
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bookingListener?.remove()
        _binding = null
    }
}