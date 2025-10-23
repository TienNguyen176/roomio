package com.tdc.nhom6.roomio.model

/**
 * Data models for our hotel booking app
 * 
 * These are just containers for data - like boxes that hold information
 * about hotels, reviews, and deals.
 */

/**
 * HotReviewItem - Represents a hotel review to display in the UI
 * This is what we show in the "Hot Reviews" section
 */
data class HotReviewItem(
    val imageRes: Int,        // Image resource ID (from drawable folder)
    val title: String,        // Hotel name
    val ratingText: String,   // Rating like "4.5 (234)"
    val priceText: String    // Price like "VND 1,500,000"
)

/**
 * DealItem - Represents a hotel deal to display in the UI
 * This is what we show in the "Deals" section
 */
data class DealItem(
    val imageRes: Int,       // Image resource ID (from drawable folder)
    val title: String,       // Hotel name
    val subtitle: String     // Location like "Vung Tau"
)
