package com.tdc.nhom6.roomio.models


data class HotReviewItem(
    val imageRes: Int,
    val title: String,
    val ratingText: String,
    val priceText: String
)


data class DealItem(
    val imageRes: Int,
    val title: String,
    val subtitle: String
)

// Data classes for search results
enum class SearchResultType {
    HOTEL, DEAL, REVIEW
}

data class SearchResultItem(
    val type: SearchResultType,
    val hotel: Hotel?,
    val deal: Hotel?,
    val review: HotReview?
)