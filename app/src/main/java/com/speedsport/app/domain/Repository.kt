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
        listBookings(facilityId, date).map { formatHm(it.start) }.toSet()

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
                    !(end <= it.start || start >= it.end)
        }
        return if (!clash) {
            val b = Booking(
                facilityId = facilityId,
                date = date,
                start = start,
                end = end,
                bookedBy = name
            )
            bookings += b
            Result.success(b)
        } else {
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

    fun cancel(bookingId: String): Boolean {
        val idx = bookings.indexOfFirst { it.id == bookingId }
        if (idx < 0) return false
        val cancelled = bookings.removeAt(idx)
        // Promote first waitlist match FIFO
        val candidateIdx = waitlist.indexOfFirst {
            it.facilityId == cancelled.facilityId &&
                    it.date == cancelled.date &&
                    it.start == cancelled.start &&
                    it.end == cancelled.end
        }
        if (candidateIdx >= 0) {
            val w = waitlist.removeAt(candidateIdx)
            bookings += Booking(
                facilityId = w.facilityId,
                date = w.date,
                start = w.start,
                end = w.end,
                bookedBy = w.name,
                paid = false
            )
        }
        return true
    }

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
