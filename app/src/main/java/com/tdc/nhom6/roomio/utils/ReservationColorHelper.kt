package com.tdc.nhom6.roomio.utils

import com.tdc.nhom6.roomio.models.HeaderColor

/**
 * Helper class for determining reservation colors based on payment status.
 */
object ReservationColorHelper {
    
    /**
     * Determines the header color for a reservation based on payment amount.
     * 
     * @param totalPaidAmount The amount that has been paid for the reservation
     * @param totalFinal The total final amount for the reservation
     * @return HeaderColor:
     *   - GREEN if 100% paid (totalPaidAmount >= totalFinal)
     *   - BLUE if 10% paid (deposit) and less than 100%
     *   - YELLOW otherwise (pending payment)
     */
    fun getColorByPaymentStatus(
        totalPaidAmount: Double,
        totalFinal: Double
    ): HeaderColor {
        // Handle edge cases
        if (totalFinal <= 0.0) {
            return HeaderColor.YELLOW
        }
        
        // Calculate payment percentage
        val paymentPercentage = (totalPaidAmount / totalFinal) * 100.0
        
        return when {
            // Fully paid (100% or more)
            paymentPercentage >= 100.0 -> HeaderColor.GREEN
            
            // Deposit paid (10% or more, but less than 100%)
            paymentPercentage >= 10.0 -> HeaderColor.BLUE
            
            // Pending payment (less than 10%)
            else -> HeaderColor.YELLOW
        }
    }
    
    /**
     * Determines the header color with a custom threshold for deposit.
     * 
     * @param totalPaidAmount The amount that has been paid
     * @param totalFinal The total final amount
     * @param depositThreshold The percentage threshold for deposit (default 10.0)
     * @return HeaderColor based on payment status
     */
    fun getColorByPaymentStatus(
        totalPaidAmount: Double,
        totalFinal: Double,
        depositThreshold: Double = 10.0
    ): HeaderColor {
        if (totalFinal <= 0.0) {
            return HeaderColor.YELLOW
        }
        
        val paymentPercentage = (totalPaidAmount / totalFinal) * 100.0
        
        return when {
            paymentPercentage >= 100.0 -> HeaderColor.GREEN
            paymentPercentage >= depositThreshold -> HeaderColor.BLUE
            else -> HeaderColor.YELLOW
        }
    }
}

