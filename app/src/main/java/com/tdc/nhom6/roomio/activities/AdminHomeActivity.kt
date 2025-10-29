package com.tdc.nhom6.roomio.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tdc.nhom6.roomio.databinding.AdminHomeLayoutBinding
import com.tdc.nhom6.roomio.utils.navigateTo

class AdminHomeActivity : AppCompatActivity() {
    private lateinit var binding: AdminHomeLayoutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AdminHomeLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setEvent()
    }

    private fun setEvent() {
        binding.apply {
            btnDanhSachDon.setOnClickListener {
                navigateTo(AdminListHotelRequestActivity::class.java, flag = false)
            }
            btnQuanLyTaiKhoan.setOnClickListener {

            }
            btnQuanLyKhachSan.setOnClickListener {
                navigateTo(AdminHotelManagerActivity::class.java, flag = false)
            }
        }
    }
}