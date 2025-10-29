package com.tdc.nhom6.roomio.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.HotelFacilitiesLayoutBinding

class HotelFacilitiesActivity : AppCompatActivity() {
    private lateinit var binding: HotelFacilitiesLayoutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HotelFacilitiesLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}