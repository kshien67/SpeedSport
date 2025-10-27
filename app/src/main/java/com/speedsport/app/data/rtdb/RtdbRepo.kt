package com.speedsport.app.data.rtdb

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.random.Random
import java.util.UUID

/* =======================
 *  Data models (DTOs)
 * ======================= */

/**
 * IMPORTANT:
 * Keep ONLY ONE boolean for admin here to avoid Firebase's "conflicting getters".
 * We'll still read/write BOTH DB keys ("admin" and legacy "isAdmin") in the repo methods.
 */
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val admin: Boolean = false,   // canonical in the app model
    val points: Int = 0
)

data class FacilityDto(
    val id: String = "",
    val name: String = "",
    val sport: String = "",         // sport (replaces legacy "type")
    val hourlyRateMYR: Double = 0.0
)

data class BookingDto(
    val id: String = "",
    val facilityId: String = "",
    val userUid: String = "",
    val userEmail: String = "",
    val date: String = "",
    val start: String = "",
    val end: String = "",
    val status: String = "booked",
    val cancellationRequest: Map<String, Any?>? = null,
    val sport: String = "",
    val courtName: String? = null,
    val times: List<String>? = null
)

data class EquipmentDto(
    val id: String = "",
    val sport: String = "",         // sport (replaces legacy "type")
    val label: String = "",
    val stock: Int = 0,
    val rentalRate: Double = 0.0
)

data class VoucherDto(
    val id: String = "",
    val amountOffMYR: Double = 0.0,
    val costPoints: Int = 0,
    val active: Boolean = true
)

data class WaitlistRow(
    val requestId: String = "",
    val courtId: String = "",
    val courtName: String = "",
    val date: String = "",
    val slotKey: String = "",
    val userId: String = "",
    val sport: String = "",
    val status: String = "queued"
)

/* =======================
 *  Repo
 * ======================= */

object RtdbRepo {
    private val db = FirebaseDatabase.getInstance().reference
    private val usersRef = db.child("users")
    private val facilitiesRef = db.child("facilities")
    private val bookingsRef = db.child("bookings")
    private val facilityBookingsRef = db.child("facilityBookings")
    private val equipmentRef = db.child("equipment")
    private val sportsRef = db.child("sports")
    private val vouchersRef = db.child("vouchers")
    private val userVouchersRef = db.child("userVouchers")

    /* ---------- Users ---------- */

    suspend fun createUser(profile: UserProfile) {
        // Write both keys so either rules/UI path can read them
        usersRef.child(profile.uid).updateChildren(
            mapOf(
                "uid" to profile.uid,
                "name" to profile.name,
                "email" to profile.email,
                "admin" to profile.admin,       // canonical in model
                "isAdmin" to profile.admin,     // legacy DB key (kept in sync)
                "points" to profile.points
            )
        ).await()
    }

    suspend fun getUser(uid: String): UserProfile? {
        val s = usersRef.child(uid).get().await()
        if (!s.exists()) return null
        val adminCombined =
            (s.child("admin").getValue(Boolean::class.java) == true) ||
                    (s.child("isAdmin").getValue(Boolean::class.java) == true)
        return UserProfile(
            uid = s.child("uid").getValue(String::class.java) ?: uid,
            name = s.child("name").getValue(String::class.java) ?: "",
            email = s.child("email").getValue(String::class.java) ?: "",
            admin = adminCombined,
            points = (s.child("points").value as? Number)?.toInt() ?: 0
        )
    }

    suspend fun isCurrentUserAdmin(): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val s = usersRef.child(uid).get().await()
        val a = s.child("admin").getValue(Boolean::class.java) == true
        val ia = s.child("isAdmin").getValue(Boolean::class.java) == true
        return a || ia
    }

    /* ---------- Facilities (sport) ---------- */

    suspend fun listFacilities(): List<FacilityDto> =
        facilitiesRef.get().await().children.mapNotNull { it.getValue(FacilityDto::class.java) }

    suspend fun listFacilitiesBySport(sport: String): List<FacilityDto> =
        listFacilities().filter { it.sport.equals(sport, ignoreCase = true) }

    suspend fun addFacility(name: String, sport: String, ratePerHour: Double): String {
        val id = facilitiesRef.push().key ?: UUID.randomUUID().toString()
        val data = FacilityDto(
            id = id,
            name = name.trim(),
            sport = sport.trim().uppercase(),
            hourlyRateMYR = ratePerHour
        )
        facilitiesRef.child(id).setValue(data).await()
        return id
    }

    suspend fun updateFacilityNameRate(id: String, name: String, ratePerHour: Double) {
        facilitiesRef.child(id).updateChildren(
            mapOf("name" to name.trim(), "hourlyRateMYR" to ratePerHour)
        ).await()
    }

    suspend fun deleteFacility(id: String) {
        facilitiesRef.child(id).removeValue().await()
    }

    /* ---------- Equipment (sport) ---------- */

    // Tolerant read: prefer "sport"; fall back to legacy "type".
    suspend fun listEquipment(): List<EquipmentDto> {
        val snap = equipmentRef.get().await()
        val out = mutableListOf<EquipmentDto>()
        for (e in snap.children) {
            val id = e.child("id").getValue(String::class.java) ?: (e.key ?: continue)
            val label = e.child("label").getValue(String::class.java) ?: id
            val stock = (e.child("stock").value as? Number)?.toInt() ?: 0
            val rate =
                (e.child("rentalRate").value as? Number)?.toDouble()
                    ?: (e.child("rentalRateMYR").value as? Number)?.toDouble()
                    ?: (e.child("rate").value as? Number)?.toDouble()
                    ?: 0.0
            val sport =
                e.child("sport").getValue(String::class.java)
                    ?: e.child("type").getValue(String::class.java)     // legacy fallback
                    ?: ""
            out += EquipmentDto(id = id, sport = sport, label = label, stock = stock, rentalRate = rate)
        }
        return out
    }

    suspend fun listEquipmentBySportOrAll(filter: String?): List<EquipmentDto> {
        val all = listEquipment()
        return if (filter.isNullOrBlank() || filter.equals("ALL", true)) all
        else all.filter { it.sport.equals(filter, ignoreCase = true) }
    }

    suspend fun addEquipment(sport: String, label: String, rate: Double, stock: Int): String {
        val id = equipmentRef.push().key ?: UUID.randomUUID().toString()
        val payload = mapOf(
            "id" to id,
            "label" to label,
            "stock" to stock,
            "rentalRate" to rate,
            "sport" to sport.trim().uppercase()
        )
        equipmentRef.child(id).setValue(payload).await()
        return id
    }

    suspend fun updateEquipmentFields(id: String, name: String, rate: Double, stock: Int) {
        equipmentRef.child(id).updateChildren(
            mapOf(
                "label" to name,
                "rentalRate" to rate,
                "stock" to stock
            )
        ).await()
    }

    suspend fun deleteEquipment(id: String) {
        equipmentRef.child(id).removeValue().await()
    }

    /* ---------- Sports ---------- */

    suspend fun listSports(): List<String> {
        // Preferred: explicit /sports map { BADMINTON: true, ... }
        val sSnap = sportsRef.get().await()
        val listed = sSnap.children.mapNotNull { it.key }.map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        if (listed.isNotEmpty()) return listed.sorted()

        // Fallback: infer from facilities.sport and equipment.sport (or legacy type)
        val facSnap = facilitiesRef.get().await()
        val fromFacilities = facSnap.children
            .mapNotNull { it.child("sport").getValue(String::class.java) }
        val eqSnap = equipmentRef.get().await()
        val fromEquipment = eqSnap.children
            .mapNotNull { it.child("sport").getValue(String::class.java) ?: it.child("type").getValue(String::class.java) }

        return (fromFacilities + fromEquipment)
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()
            .toList()
            .sorted()
    }

    suspend fun addSport(name: String) {
        val key = name.trim().uppercase()
        if (key.isEmpty()) return
        sportsRef.child(key).setValue(true).await()
    }

    /** Delete sport + ALL its facilities & equipment where sport == key (also matches legacy type). */
    suspend fun deleteSportCascade(name: String) {
        val key = name.trim().uppercase()
        val batch = hashMapOf<String, Any?>("/sports/$key" to null)

        // Facilities with sport == key
        facilitiesRef.get().await().children.forEach { c ->
            val s = c.child("sport").getValue(String::class.java)?.trim()?.uppercase()
            if (s == key) batch["/facilities/${c.key}"] = null
        }

        // Equipment with sport == key (or legacy type == key)
        equipmentRef.get().await().children.forEach { c ->
            val s = (c.child("sport").getValue(String::class.java)
                ?: c.child("type").getValue(String::class.java)) // legacy fallback
                ?.trim()?.uppercase()
            if (s == key) batch["/equipment/${c.key}"] = null
        }

        db.updateChildren(batch).await()
    }

    /* ---------- Vouchers ---------- */

    suspend fun listVouchers(): List<VoucherDto> =
        vouchersRef.get().await().children.mapNotNull { it.getValue(VoucherDto::class.java) }

    suspend fun addVoucher(amountOffMYR: Double, costPoints: Int): String {
        val id = vouchersRef.push().key ?: UUID.randomUUID().toString()
        vouchersRef.child(id).setValue(
            VoucherDto(id = id, amountOffMYR = amountOffMYR, costPoints = costPoints, active = true)
        ).await()
        return id
    }

    suspend fun deleteVoucher(id: String) {
        vouchersRef.child(id).removeValue().await()
    }

    /** Deducts points and gives a unique 9-char code. */
    suspend fun purchaseVoucher(uid: String, voucherId: String): Result<String> {
        val userRef = usersRef.child(uid)
        val voucher = vouchersRef.child(voucherId).get().await().getValue(VoucherDto::class.java)
            ?: return Result.failure(IllegalArgumentException("Voucher not found"))

        val ok = suspendCancellableCoroutine<Boolean> { cont ->
            userRef.child("points").runTransaction(object : Transaction.Handler {
                override fun doTransaction(m: MutableData): Transaction.Result {
                    val current = (m.value as? Number)?.toInt() ?: 0
                    return if (current >= voucher.costPoints) {
                        m.value = current - voucher.costPoints
                        Transaction.success(m)
                    } else Transaction.abort()
                }
                override fun onComplete(e: DatabaseError?, committed: Boolean, s: DataSnapshot?) {
                    cont.resume(committed)
                }
            })
        }
        if (!ok) return Result.failure(IllegalStateException("Not enough points"))

        val code = randomCode9()
        val now = System.currentTimeMillis()
        val owned = mapOf(
            "code" to code,
            "voucherId" to voucherId,
            "amountOffMYR" to voucher.amountOffMYR,
            "acquiredAt" to now,
            "redeemed" to false
        )
        userVouchersRef.child(uid).push().setValue(owned).await()
        usersRef.child(uid).child("pointsHistory").push().setValue(
            mapOf("ts" to now, "delta" to -voucher.costPoints, "reason" to "Bought voucher RM ${voucher.amountOffMYR}")
        ).await()
        return Result.success(code)
    }

    /* ---------- Cancellations (admin) ---------- */

    suspend fun approveCancellationRtdb(
        bookingId: String,
        courtId: String,
        date: String,
        times: List<String>
    ) {
        val updates = hashMapOf<String, Any?>(
            "/bookings/$bookingId/status" to "cancelled",
            "/bookings/$bookingId/cancellationRequest/status" to "approved",
            "/bookings/$bookingId/cancellationRequest/processedAt" to ServerValue.TIMESTAMP
        )
        times.forEach { st ->
            val end = plus1h(st)
            updates["/facilityBookings/$courtId/$date/$st-$end"] = null
        }
        db.updateChildren(updates).await()
    }

    suspend fun denyCancellationRtdb(bookingId: String) {
        db.updateChildren(
            mapOf(
                "/bookings/$bookingId/status" to "booked",
                "/bookings/$bookingId/cancellationRequest/status" to "denied",
                "/bookings/$bookingId/cancellationRequest/processedAt" to ServerValue.TIMESTAMP
            )
        ).await()
    }

    /* ---------- Utils ---------- */

    private fun plus1h(hm: String): String {
        val (h, m) = hm.split(":").map { it.toInt() }
        val t = h * 60 + m + 60
        return "%02d:%02d".format((t / 60) % 24, t % 60)
    }

    private fun randomCode9(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..9).joinToString("") { alphabet[Random.nextInt(alphabet.length)].toString() }
    }
}
