package com.tdc.nhom6.roomio.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.AdminHotelManagerLayoutBinding

class AdminHotelManagerActivity : AppCompatActivity() {
    private lateinit var binding: AdminHotelManagerLayoutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AdminHotelManagerLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}