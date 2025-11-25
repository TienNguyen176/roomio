package com.tdc.nhom6.roomio.activities

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.FirebaseApp
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.fragments.HomeFragment
import com.tdc.nhom6.roomio.fragments.MyBookingFragment

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG_HOME = "tag_home"
        private const val TAG_BOOKING = "tag_booking"
        private const val TAG_PROFILE = "tag_profile"
        private const val KEY_CURRENT_TAG = "current_fragment_tag"
    }
    private lateinit var bottomNav: BottomNavigationView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this)
            Log.d("Firebase", "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e("Firebase", "Failed to initialize Firebase:${e.message}")
        }

        // Enable StrictMode in debug builds only
        try {
            val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable) {
                val vmBuilder = StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                StrictMode.setVmPolicy(vmBuilder.build())
            }
        } catch (_: Exception) { }

        // Edge-to-edge
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        // Apply window inset padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val initialTag = savedInstanceState?.getString(KEY_CURRENT_TAG) ?: TAG_HOME
        showFragment(initialTag)
        setupBottomNavigation(initialTag)
        handleIntent(intent)
    }

    private fun setupBottomNavigation(initialTag: String) {
        bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.itemIconTintList = resources.getColorStateList(R.color.nav_item_color)
        bottomNav.itemTextColor = resources.getColorStateList(R.color.nav_item_color)
        bottomNav.selectedItemId = if (initialTag == TAG_PROFILE) R.id.menu_profile else R.id.menu_home

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> {
                    showFragment(TAG_HOME)
                    true
                }
                R.id.menu_booking -> {
                    showFragment(TAG_BOOKING)
                    true
                }
                R.id.menu_profile -> {
                    showFragment(TAG_PROFILE)
                    true
                }
                else -> false
            }
        }
    }

    private fun showFragment(tag: String) {
        val fragmentManager = supportFragmentManager
        val fragment = fragmentManager.findFragmentByTag(tag) ?: createFragmentForTag(tag)

        fragmentManager.beginTransaction().apply {
            setReorderingAllowed(true)
            fragmentManager.fragments.forEach { existing ->
                if (existing.isAdded && existing != fragment) {
                    hide(existing)
                }
            }
            if (fragment.isAdded) {
                show(fragment)
            } else {
                add(R.id.nav_host_container, fragment, tag)
            }
            setPrimaryNavigationFragment(fragment)
            commit()
        }
    }

    private fun createFragmentForTag(tag: String): Fragment = when (tag) {
        TAG_HOME -> HomeFragment()
        TAG_BOOKING -> MyBookingFragment()
        TAG_PROFILE -> ProfileFragment()
        else -> throw IllegalArgumentException("Unknown fragment tag: $tag")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val currentTag = supportFragmentManager.primaryNavigationFragment?.tag ?: TAG_HOME
        outState.putString(KEY_CURRENT_TAG, currentTag)
    }

    fun navigateToHome() {
        bottomNav.selectedItemId = R.id.menu_home
        showFragment(TAG_HOME)
    }
    fun navigateToMyBooking() {
        bottomNav.selectedItemId = R.id.menu_booking
        showFragment(TAG_BOOKING)
    }
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("NAVIGATE_TO_HOME", false) == true) {
            navigateToHome()
        }else if (intent?.getBooleanExtra("NAVIGATE_TO_BOOKING", false) == true) {
            navigateToMyBooking()
        }
    }
}