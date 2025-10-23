package com.speedsport.app.domain

data class PointsTxn(
    val amount: Int = 0,              // + earn, - spend
    val type: String = "",            // "booking", "voucher", ...
    val refId: String? = null,        // bookingId / voucherId
    val note: String? = null,         // human-readable note
    val createdAt: Long = 0L          // epoch millis
)