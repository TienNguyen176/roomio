package com.tdc.nhom6.roomio.utils

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

object RecyclerViewUtils {
    /**
     * Configures RecyclerView with common settings to prevent swap behavior issues
     */
    fun configureRecyclerView(recyclerView: RecyclerView, layoutManager: RecyclerView.LayoutManager? = null) {
        recyclerView.layoutManager = layoutManager ?: LinearLayoutManager(recyclerView.context)
        recyclerView.setItemViewCacheSize(20)
        recyclerView.itemAnimator = null
        recyclerView.setNestedScrollingEnabled(false)
        recyclerView.isNestedScrollingEnabled = false
    }
}


