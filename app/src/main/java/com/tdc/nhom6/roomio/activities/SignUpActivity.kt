package com.tdc.nhom6.roomio.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tdc.nhom6.roomio.databinding.SignUpLayoutBinding

class SignUpActivity : AppCompatActivity() {
    private lateinit var binding: SignUpLayoutBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SignUpLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}