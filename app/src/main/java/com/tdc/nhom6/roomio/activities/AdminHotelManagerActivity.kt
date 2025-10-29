package com.tdc.nhom6.roomio.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.AdminHotelManagerLayoutBinding
import com.tdc.nhom6.roomio.utils.navigateTo

class AdminHotelManagerActivity : AppCompatActivity() {
    private lateinit var binding: AdminHotelManagerLayoutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AdminHotelManagerLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.app_bar_title_hotel_manager)

        setEvent()
    }

    private fun setEvent() {
        binding.apply {
            lnServiceHotelManager.setOnClickListener {
                navigateTo(HotelServicesActivity::class.java, flag = false)
            }
            lnFacilityManager.setOnClickListener {
                navigateTo(HotelFacilitiesActivity::class.java, flag = false)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}