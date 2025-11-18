package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.tdc.nhom6.roomio.databinding.AdminHomeLayoutBinding
import com.tdc.nhom6.roomio.utils.navigateTo

class AdminHomeActivity : AppCompatActivity() {
    private lateinit var binding: AdminHomeLayoutBinding

    private val auth = FirebaseAuth.getInstance()
    private val prefs by lazy { getSharedPreferences("user_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AdminHomeLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setEvent()
        saveUserId()
    }

    private fun saveUserId() {
        auth.currentUser?.uid?.let { uid ->
            prefs.edit().putString("userId", uid).apply()
        }
    }

    private fun setEvent() {
        binding.apply {
            btnDanhSachDon.setOnClickListener {
                navigateTo(AdminListHotelRequestActivity::class.java, flag = false)
            }
            btnQuanLyTaiKhoan.setOnClickListener {
                navigateTo(AdminAccountManagerActivity::class.java, flag = false)
            }
            btnQuanLyKhachSan.setOnClickListener {
                navigateTo(AdminHotelManagerActivity::class.java, flag = false)
            }

            binding.btnLogout.setOnClickListener {
                auth.signOut()
                prefs.edit().clear().apply()
                val intent = Intent(this@AdminHomeActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }
}