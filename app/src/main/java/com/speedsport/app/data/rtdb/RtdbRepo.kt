package com.speedsport.app.data.rtdb

import com.google.firebase.database.*
import com.speedsport.app.domain.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import java.util.UUID

data class UserProfile(val uid: String = "", val name: String = "", val email: String = "", val isAdmin: Boolean = false)
data class FacilityDto(val id: String = "", val name: String = "", val type: String = "", val hourlyRateMYR: Double = 0.0)
data class BookingDto(val id: String = "", val facilityId: String = "", val userUid: String = "", val userEmail: String = "", val date: String = "", val start: String = "", val end: String = "")
data class EquipmentDto(val id: String = "", val label: String = "", val stock: Int = 0, val rentalRateMYR: Double = 0.0)

private fun <T> DataSnapshot.toList(clazz: Class<T>): List<T> = children.mapNotNull { it.getValue(clazz) }

object RtdbRepo {
    private val db = FirebaseDatabase.getInstance().reference
    private val usersRef = db.child("users")
    private val facilitiesRef = db.child("facilities")
    private val bookingsRef = db.child("bookings")
    private val facilityBookingsRef = db.child("facilityBookings")
    private val equipmentRef = db.child("equipment")

    // Users
    suspend fun createUser(profile: UserProfile) { usersRef.child(profile.uid).setValue(profile).await() }
    suspend fun getUser(uid: String): UserProfile? = usersRef.child(uid).get().await().getValue(UserProfile::class.java)

    // Facilities
    suspend fun listFacilities(): List<FacilityDto> = facilitiesRef.get().await().toList(FacilityDto::class.java)

    // Bookings
    suspend fun listBookings(facilityId: String, date: String): List<BookingDto> =
        bookingsRef.orderByChild("facilityId").equalTo(facilityId).get().await().toList(BookingDto::class.java).filter { it.date == date }

    suspend fun listUserBookings(uid: String): List<BookingDto> =
        bookingsRef.orderByChild("userUid").equalTo(uid).get().await().toList(BookingDto::class.java)
            .sortedWith(compareBy({ it.date }, { it.start }))

    suspend fun book(facilityId: String, date: String, start: String, end: String, userUid: String, userEmail: String): Result<BookingDto> {
        val slotKey = "$start-$end"
        val idxRef = facilityBookingsRef.child(facilityId).child(date).child(slotKey)
        val reserved = runReservationTransaction(idxRef)
        if (!reserved) return Result.failure(IllegalStateException("Slot already taken"))

        val b = BookingDto(UUID.randomUUID().toString(), facilityId, userUid, userEmail, date, start, end)
        bookingsRef.child(b.id).setValue(b).await()
        idxRef.setValue(b.id).await()
        return Result.success(b)
    }

    private suspend fun runReservationTransaction(idxRef: DatabaseReference): Boolean =
        suspendCancellableCoroutine { cont ->
            idxRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    return if (currentData.value == null) { currentData.value = "RESERVED"; Transaction.success(currentData) }
                    else Transaction.abort()
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) { cont.resume(committed) }
            })
        }

    suspend fun cancel(bookingId: String): Boolean {
        val snap = bookingsRef.child(bookingId).get().await()
        val b = snap.getValue(BookingDto::class.java) ?: return false
        val slotKey = "${b.start}-${b.end}"
        facilityBookingsRef.child(b.facilityId).child(b.date).child(slotKey).removeValue().await()
        bookingsRef.child(bookingId).removeValue().await()
        return true
    }

    // Equipment
    suspend fun listEquipment(): List<EquipmentDto> = equipmentRef.get().await().toList(EquipmentDto::class.java)
    suspend fun rent(equipmentId: String, qty: Int): Boolean {
        val ref = equipmentRef.child(equipmentId)
        val item = ref.get().await().getValue(EquipmentDto::class.java) ?: return false
        if (qty <= 0 || item.stock < qty) return false
        ref.child("stock").setValue(item.stock - qty).await()
        return true
    }
    suspend fun restockAll(newStock: Int = 20) { equipmentRef.get().await().children.forEach { it.ref.child("stock").setValue(newStock) } }
    suspend fun wipeAllBookings() { facilityBookingsRef.removeValue().await(); bookingsRef.removeValue().await() }
}


