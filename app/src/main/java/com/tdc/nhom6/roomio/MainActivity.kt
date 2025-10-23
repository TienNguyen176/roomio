package com.tdc.nhom6.roomio

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tdc.nhom6.roomio.ui.HomeFragment

/**
 * MainActivity - The main screen of our hotel booking app
 * 
 * This is the entry point of our application. It sets up:
 * 1. The main layout with bottom navigation
 * 2. Shows the HomeFragment by default
 * 3. Handles navigation between different screens
 */
class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display for modern Android look
        enableEdgeToEdge()
        
        // Set the main layout
        setContentView(R.layout.activity_main)
        
        // Handle system bars (status bar, navigation bar) padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Show HomeFragment when app starts (only if no saved state)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.nav_host_container, HomeFragment())
                .commit()
        }

        // Set up bottom navigation
        setupBottomNavigation()
    }
    
    
    /**
     * Sets up the bottom navigation bar
     * When user taps on different menu items, it shows different screens
     */
    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> {
                    // Show Home screen
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.nav_host_container, HomeFragment())
                        .commit()
                    true
                }
                else -> false
            }
        }
    }
}