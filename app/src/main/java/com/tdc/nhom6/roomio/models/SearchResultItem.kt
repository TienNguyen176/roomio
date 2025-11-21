package com.tdc.nhom6.roomio.models

enum class SearchResultType {
    HOTEL, DEAL, REVIEW
}

data class SearchResultItem(
    val type: SearchResultType,
    val hotel: Hotel?,
    val deal: Deal?,
    val review: HotReview?
)
