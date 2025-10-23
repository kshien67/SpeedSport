package com.speedsport.app.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.speedsport.app.domain.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class BookingViewModel : ViewModel() {

    // For snackbars / status messages
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    // Set of taken start times ("HH:mm") for the selected (facility, date)
    private val _taken = MutableStateFlow<Set<String>>(emptySet())
    val taken: StateFlow<Set<String>> = _taken

    /** Refresh the taken start times whenever facility or date changes. */
    fun refreshTaken(facilityId: String, date: LocalDate) {
        _taken.value = if (facilityId.isBlank()) emptySet()
        else Repo.takenTimeStrings(facilityId, date)
    }

    /** Book **start time only** (end is computed inside Repo: start + 60 minutes by default). */
    fun bookStartOnly(
        facilityId: String,
        date: LocalDate,
        start: LocalTime,
        durationMinutes: Int = 60
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        val name = user?.email ?: user?.uid ?: "anonymous"

        viewModelScope.launch {
            val res = Repo.bookStartOnly(
                facilityId = facilityId,
                date = date,
                start = start,
                name = name,
                durationMinutes = durationMinutes
            )
            _message.value = res.fold(
                onSuccess = { "Booked $facilityId at ${Repo.formatHm(start)} on $date" },
                onFailure = { it.message ?: "Failed to book (maybe taken). Added to waitlist if applicable." }
            )
            // After booking (or failure), refresh the taken set so UI updates
            _taken.value = Repo.takenTimeStrings(facilityId, date)
        }
    }

    /** Cancel by bookingId; will also promote waitlist if present (Repo handles this). */
    fun cancel(bookingId: String, facilityId: String, date: LocalDate) {
        viewModelScope.launch {
            val ok = Repo.cancel(bookingId)
            _message.value = if (ok) "Cancelled" else "Booking not found"
            _taken.value = Repo.takenTimeStrings(facilityId, date)
        }
    }

    /** Clear last message after UI has shown it (optional helper). */
    fun clearMessage() { _message.value = null }
}
