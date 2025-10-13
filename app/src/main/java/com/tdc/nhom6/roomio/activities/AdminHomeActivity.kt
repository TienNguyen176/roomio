package com.tdc.nhom6.roomio.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tdc.nhom6.roomio.databinding.AdminHomeLayoutBinding

class AdminHomeActivity : AppCompatActivity() {
    private lateinit var binding: AdminHomeLayoutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AdminHomeLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}