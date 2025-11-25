package com.tdc.nhom6.roomio.activities.cleaner

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.fragments.CleanerFragment

class CleanerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cleaner)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.cleaner_container, CleanerFragment())
                .commit()
        }
    }
}

