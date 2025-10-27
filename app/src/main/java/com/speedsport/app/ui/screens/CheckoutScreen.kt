@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.speedsport.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ServerValue
import com.speedsport.app.domain.BookingDraft
import kotlinx.coroutines.launch

/* your RTDB url */
private const val DB_URL =
    "https://speedsport-edf02-default-rtdb.asia-southeast1.firebasedatabase.app"

/* ------- small helpers ------- */
private fun plusOneHour(hm: String): String {
    val (h, m) = hm.split(":").map { it.toInt() }
    val total = h * 60 + m + 60
    val hh = (total / 60) % 24
    val mm = total % 60
    return "%02d:%02d".format(hh, mm)
}
private fun selectedTimeLabel(starts: List<String>): String {
    if (starts.isEmpty()) return "-"
    val from = starts.minOrNull()!!
    val to = plusOneHour(starts.maxOrNull()!!)
    return "$from - $to"
}

/* Voucher applied holder */
private data class AppliedVoucher(
    val id: String,
    val code: String,
    val amountOffMYR: Int
)

/* ------- UI ------- */
@Composable
fun CheckoutScreen(
    draft: BookingDraft,
    onBack: () -> Unit,
    onBooked: () -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val uid = FirebaseAuth.getInstance().currentUser?.uid

    var voucherCode by remember { mutableStateOf("") }
    var voucherDiscount by remember { mutableStateOf(0.0) }
    var applying by remember { mutableStateOf(false) }
    var appliedVoucher by remember { mutableStateOf<AppliedVoucher?>(null) }

    val subtotal = draft.totalBeforeDiscount()
    val totalAfterDiscount = (subtotal - voucherDiscount).coerceAtLeast(0.0)
    val pointsEarned = totalAfterDiscount.toInt() // 1 pt per RM1

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checkout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).padding(16.dp)) {
                item {
                    Text("Booking details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    SummaryRow("Date", draft.dateIso)
                    SummaryRow("Sport", draft.sport)
                    SummaryRow("Court", draft.courtName)
                    SummaryRow("Rate", "RM ${"%.2f".format(draft.courtRatePerHour)}/hour")
                    SummaryRow("Time", selectedTimeLabel(draft.selectedTimes))
                    Spacer(Modifier.height(16.dp))
                }
                if (draft.equipment.isNotEmpty()) {
                    item {
                        Text("Equipment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(draft.equipment.filter { it.qty > 0 }) { e ->
                        SummaryRow("${e.name} × ${e.qty}", "RM ${"%.2f".format(e.rate * e.qty)}")
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
                item {
                    Text("Apply Voucher", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = voucherCode,
                            onValueChange = {
                                voucherCode = it.trim().uppercase()
                                // If user edits code again, clear previous apply
                                voucherDiscount = 0.0
                                appliedVoucher = null
                            },
                            label = { Text("Voucher code") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = voucherCode.isNotEmpty() && !applying && uid != null,
                            onClick = {
                                applying = true
                                tryApplyVoucherFirebase(
                                    uid = uid!!,
                                    code = voucherCode,
                                    subtotal = subtotal
                                ) { v, discount, message ->
                                    appliedVoucher = v
                                    voucherDiscount = discount
                                    applying = false
                                    scope.launch { snackbar.showSnackbar(message) }
                                }
                            }
                        ) { Text(if (applying) "..." else "Apply") }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                item {
                    Text("Payment summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    SummaryRow("Court (${draft.hours}h)", "RM ${"%.2f".format(draft.courtSubtotal)}")
                    SummaryRow("Equipment", "RM ${"%.2f".format(draft.equipmentSubtotal)}")
                    SummaryRow("Subtotal", "RM ${"%.2f".format(subtotal)}")
                    SummaryRow("Voucher", "- RM ${"%.2f".format(voucherDiscount)}")
                    Divider(Modifier.padding(vertical = 8.dp))
                    SummaryRowBold("Total", "RM ${"%.2f".format(totalAfterDiscount)}")
                    Spacer(Modifier.height(8.dp))
                    SummaryRow("Points you’ll earn", "+ $pointsEarned")
                }
            }

            Button(
                onClick = {
                    confirmAndWriteBooking(
                        draft = draft,
                        totalPaid = totalAfterDiscount,
                        pointsToAdd = pointsEarned,
                        appliedVoucher = appliedVoucher,
                        onConflict = {
                            scope.launch {
                                snackbar.showSnackbar("One of your time slots was just booked. Please reselect.")
                            }
                        },
                        onError = { err ->
                            scope.launch { snackbar.showSnackbar(err ?: "Booking failed") }
                        },
                        onSuccess = {
                            scope.launch {
                                snackbar.showSnackbar("Booking confirmed!")
                                onBooked()
                            }
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) { Text("Confirm & Book") }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SummaryRowBold(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

/* --- Voucher apply (Firebase) --- */
private fun tryApplyVoucherFirebase(
    uid: String,
    code: String,
    subtotal: Double,
    onResult: (applied: AppliedVoucher?, discount: Double, message: String) -> Unit
) {
    val ref = FirebaseDatabase.getInstance(DB_URL)
        .reference.child("users").child(uid).child("vouchers")

    ref.orderByChild("code").equalTo(code)
        .addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val node = snapshot.children.firstOrNull()
                if (node == null) {
                    onResult(null, 0.0, "Invalid voucher code"); return
                }
                val used = node.child("used").getValue(Boolean::class.java) ?: false
                if (used) {
                    onResult(null, 0.0, "This voucher was already used"); return
                }
                val amount = (node.child("amountOffMYR").value as? Number)?.toInt() ?: 0
                val id = node.key ?: ""
                val discount = amount.coerceAtMost(subtotal.toInt()).toDouble()
                onResult(AppliedVoucher(id = id, code = code, amountOffMYR = amount),
                    discount, "Voucher applied: RM$amount")
            }

            override fun onCancelled(error: DatabaseError) {
                onResult(null, 0.0, error.message)
            }
        })
}

/* --- Booking write + facilityBookings block (with conflict check) --- */
private fun confirmAndWriteBooking(
    draft: BookingDraft,
    totalPaid: Double,
    pointsToAdd: Int,
    appliedVoucher: AppliedVoucher?,
    onConflict: () -> Unit,
    onError: (message: String?) -> Unit,
    onSuccess: () -> Unit
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    if (uid == null) { onError("Not logged in"); return }

    val db = FirebaseDatabase.getInstance(DB_URL).reference
    val bookingId = db.child("bookings").push().key ?: return onError("Failed to allocate booking id")
    val dateKey = draft.dateIso
    val courtId = draft.courtId

    val hours = draft.selectedTimes.sorted()
    val slotKeys = hours.map { s -> "$s-${plusOneHour(s)}" }

    // 1) Read current slots to avoid race
    db.child("facilityBookings").child(courtId).child(dateKey)
        .get()
        .addOnSuccessListener { snap ->
            if (slotKeys.any { snap.hasChild(it) }) {
                onConflict(); return@addOnSuccessListener
            }

            // 2) Prepare write
            val bookingMap = mutableMapOf<String, Any>(
                "userUid" to uid,
                "date" to dateKey,
                "sport" to draft.sport,
                "facilityId" to draft.courtId,
                "facilityName" to draft.courtName,
                "ratePerHour" to draft.courtRatePerHour,
                "times" to hours,
                "equipment" to draft.equipment.associate {
                    it.id to mapOf("name" to it.name, "qty" to it.qty, "rate" to it.rate)
                },
                "totalPaid" to totalPaid,
                "createdAt" to System.currentTimeMillis()
            )

            appliedVoucher?.let { v ->
                bookingMap["voucher"] = mapOf(
                    "code" to v.code,
                    "amountOffMYR" to v.amountOffMYR
                )
            }

            val updates = hashMapOf<String, Any>(
                "/bookings/$bookingId" to bookingMap,
                "/userBookings/$uid/$bookingId" to true,
                "/users/$uid/points" to ServerValue.increment(pointsToAdd.toLong()),
            )
            slotKeys.forEach { k -> updates["/facilityBookings/$courtId/$dateKey/$k"] = bookingId }

            // If voucher used, mark it
            appliedVoucher?.let { v ->
                updates["/users/$uid/vouchers/${v.id}/used"] = true
                updates["/users/$uid/vouchers/${v.id}/usedAt"] = ServerValue.TIMESTAMP
            }

            // 3) Commit
            db.updateChildren(updates)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { e -> onError(e.message) }
        }
        .addOnFailureListener { e -> onError(e.message) }
}
