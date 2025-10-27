package com.speedsport.app.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.text.NumberFormat
import java.util.Locale

object Repo {
    // Malaysia formatting
    val currencyMY: NumberFormat = NumberFormat.getCurrencyInstance(Locale("ms", "MY"))

    // Seed data
    private val facilities = mutableListOf(
        Facility(name = "Badminton Court A", type = FacilityType.BADMINTON, hourlyRateMYR = 18.0),
        Facility(name = "Futsal Court 1", type = FacilityType.FUTSAL, hourlyRateMYR = 120.0),
        Facility(name = "Basketball Court", type = FacilityType.BASKETBALL, hourlyRateMYR = 60.0)
    )

    private val bookings = mutableListOf<Booking>()
    private val waitlist = mutableListOf<WaitlistEntry>()
    private val equipment = mutableListOf(
        Equipment(label = "Racquet", stock = 10, rentalRateMYR = 5.0),
        Equipment(label = "Shuttlecock (tube)", stock = 20, rentalRateMYR = 8.0),
        Equipment(label = "Futsal Ball", stock = 8, rentalRateMYR = 6.0)
    )

    // ---------- Public queries ----------
    fun listFacilities(): List<Facility> = facilities.toList()
    fun listEquipment(): List<Equipment> = equipment.toList()

    fun listBookings(facilityId: String, date: LocalDate): List<Booking> =
        bookings.filter { it.facilityId == facilityId && it.date == date }

    fun listWaitlist(facilityId: String, date: LocalDate): List<WaitlistEntry> =
        waitlist.filter { it.facilityId == facilityId && it.date == date }

    /** Return taken start times as "HH:mm" strings for a (facility, date). */
    fun takenTimeStrings(facilityId: String, date: LocalDate): Set<String> =
        listBookings(facilityId, date)
            .filter { it.status != BookingStatus.CANCELLED } // cancelled slots are free
            .map { formatHm(it.start) }
            .toSet()

    // ---------- Booking (original, start+end given) ----------
    fun book(
        facilityId: String,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        name: String
    ): Result<Booking> {
        val clash = bookings.any {
            it.facilityId == facilityId && it.date == date &&
                    it.status != BookingStatus.CANCELLED &&           // ignore cancelled bookings
                    !(end <= it.start || start >= it.end)
        }
        return if (!clash) {
            val b = Booking(
                facilityId = facilityId,
                date = date,
                start = start,
                end = end,
                bookedBy = name,
                paid = false,
                status = BookingStatus.BOOKED,
                cancellationRequest = null
            )
            bookings += b
            Result.success(b)
        } else {
            // No slot -> FIFO waitlist
            waitlist += WaitlistEntry(
                facilityId = facilityId,
                date = date,
                start = start,
                end = end,
                name = name
            )
            Result.failure(IllegalStateException("Slot taken. Added to waitlist."))
        }
    }

    /** Convenience: book **start time only** (end = start + durationMinutes). */
    fun bookStartOnly(
        facilityId: String,
        date: LocalDate,
        start: LocalTime,
        name: String,
        durationMinutes: Int = 60
    ): Result<Booking> {
        val end = start.plusMinutesWrap(durationMinutes)
        return book(facilityId, date, start, end, name)
    }

    /* ==========================================================
       New cancellation workflow
       ========================================================== */

    /** User requests to cancel: set status to PENDING_CANCEL and save note. */
    fun requestCancellation(bookingId: String, uidOrName: String, note: String): Boolean {
        val idx = bookings.indexOfFirst { it.id == bookingId }
        if (idx < 0) return false
        val b = bookings[idx]
        if (b.status == BookingStatus.CANCELLED) return false // already cancelled
        // If already pending, just update note
        val newCR = (b.cancellationRequest ?: CancellationRequest())
            .copy(
                note = note.trim(),
                requestedBy = uidOrName,
                requestedAt = System.currentTimeMillis(),
                status = "pending",
                processedAt = 0L
            )

        bookings[idx] = b.copy(
            status = BookingStatus.PENDING_CANCEL,
            cancellationRequest = newCR
        )
        return true
    }

    /** Admin approves: mark CANCELLED and promote first matching waitlist entry. */
    fun approveCancellation(bookingId: String): Boolean {
        val idx = bookings.indexOfFirst { it.id == bookingId }
        if (idx < 0) return false
        val b = bookings[idx]

        // Update booking to CANCELLED + stamp processed time
        val cr = (b.cancellationRequest ?: CancellationRequest()).copy(
            status = "approved",
            processedAt = System.currentTimeMillis()
        )
        bookings[idx] = b.copy(status = BookingStatus.CANCELLED, cancellationRequest = cr)

        // Promote first waitlist candidate that matches the exact slot
        val candidateIdx = waitlist.indexOfFirst {
            it.facilityId == b.facilityId &&
                    it.date == b.date &&
                    it.start == b.start &&
                    it.end == b.end
        }
        if (candidateIdx >= 0) {
            val w = waitlist.removeAt(candidateIdx)
            bookings += Booking(
                facilityId = w.facilityId,
                date = w.date,
                start = w.start,
                end = w.end,
                bookedBy = w.name,
                paid = false,
                status = BookingStatus.BOOKED,
                cancellationRequest = null
            )
        }
        return true
    }

    /** Admin denies: revert back to BOOKED and mark request as denied. */
    fun denyCancellation(bookingId: String): Boolean {
        val idx = bookings.indexOfFirst { it.id == bookingId }
        if (idx < 0) return false
        val b = bookings[idx]
        val cr = (b.cancellationRequest ?: CancellationRequest()).copy(
            status = "denied",
            processedAt = System.currentTimeMillis()
        )
        bookings[idx] = b.copy(status = BookingStatus.BOOKED, cancellationRequest = cr)
        return true
    }

    /** Old cancel() now acts like an admin force-cancel (kept for backward-compat). */
    fun cancel(bookingId: String): Boolean = approveCancellation(bookingId)

    /* ==========================================================
       Equipment & maintenance
       ========================================================== */

    fun rent(equipmentId: String, qty: Int): Boolean {
        val idx = equipment.indexOfFirst { it.id == equipmentId }
        if (idx < 0) return false
        val item = equipment[idx]
        if (qty <= 0 || item.stock < qty) return false
        equipment[idx] = item.copy(stock = item.stock - qty)
        return true
    }

    fun restockAll() { equipment.replaceAll { it.copy(stock = 20) } }
    fun wipeAll() { bookings.clear(); waitlist.clear() }

    // ---------- Time helpers (kotlinx-datetime) ----------
    fun formatHm(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

    fun parseHm(hhmm: String): LocalTime {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return LocalTime(hour = h, minute = m)
    }

    fun LocalTime.plusMinutesWrap(minutes: Int): LocalTime {
        val total = (hour * 60 + minute + minutes).mod(24 * 60)
        val h = total / 60
        val m = total % 60
        return LocalTime(hour = h, minute = m, second = second, nanosecond = nanosecond)
    }
}
