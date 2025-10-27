package com.speedsport.app.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.LocalDate as KxDate
import kotlinx.datetime.LocalTime as KxTime
import java.util.UUID

/* =========================
   Core domain enums/models
   ========================= */

enum class FacilityType { BADMINTON, FUTSAL, BASKETBALL, TENNIS }

enum class BookingStatus {
    BOOKED,          // normal state
    PENDING_CANCEL,  // user requested cancellation – waiting for admin
    CANCELLED,       // admin approved cancellation (or forced)
    COMPLETED        // session finished successfully
}

data class Facility(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: FacilityType,
    val hourlyRateMYR: Double
)

/** Extra info stored when a user requests cancellation. */
data class CancellationRequest(
    val note: String = "",
    val requestedBy: String = "",   // uid
    val requestedAt: Long = 0L,     // epoch millis
    val processedAt: Long = 0L,     // set by admin when approve/deny
    val status: String = "pending"  // "pending" | "approved" | "denied"
)

data class Booking(
    val id: String = UUID.randomUUID().toString(),
    val facilityId: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val bookedBy: String,
    val paid: Boolean = false,

    // NEW: admin workflow fields
    val status: BookingStatus = BookingStatus.BOOKED,
    val cancellationRequest: CancellationRequest? = null
)

data class WaitlistEntry(
    val id: String = UUID.randomUUID().toString(),
    val facilityId: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val name: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class Equipment(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val stock: Int,
    val rentalRateMYR: Double
)

/* =========================
   Helpers
   ========================= */

/** yyyy-MM-dd key for RTDB */
fun KxDate.asKey(): String = "%04d-%02d-%02d".format(year, monthNumber, dayOfMonth)

/** Parse "HH:mm" into LocalTime (kotlinx) */
fun parseHm(hhmm: String): KxTime {
    val (h, m) = hhmm.split(":").map { it.toInt() }
    return KxTime(hour = h, minute = m)
}

/** Add minutes to a LocalTime, wrap within the day (no date carry) */
fun KxTime.plusMinutesWrap(minutes: Int): KxTime {
    val total = (hour * 60 + minute + minutes).mod(24 * 60)
    val h = total / 60
    val m = total % 60
    return KxTime(hour = h, minute = m, second = second, nanosecond = nanosecond)
}
