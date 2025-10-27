@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.speedsport.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.launch

private const val DB_URL =
    "https://speedsport-edf02-default-rtdb.asia-southeast1.firebasedatabase.app"

private data class EquipmentRow(val name: String, val qty: Int, val rate: Double)

/** status can be null (legacy), "booked", "pending_cancel", "cancelled", "completed" */
private data class BookingRow(
    val id: String,
    val date: String,
    val sport: String,
    val courtId: String,
    val courtName: String,
    val times: List<String>,
    val ratePerHour: Double = 0.0,
    val totalPaid: Double = 0.0,
    val equipment: List<EquipmentRow> = emptyList(),
    val voucherPercent: Int? = null,
    val status: String? = null,
    val cancelNote: String? = null,
    val cancelStatus: String? = null
) {
    val hours: Int get() = times.size
    val courtSubtotal: Double get() = ratePerHour * hours
    val equipmentSubtotal: Double get() = equipment.sumOf { it.rate * it.qty }
    val subtotal: Double get() = courtSubtotal + equipmentSubtotal
    val pointsEarned: Int get() = totalPaid.toInt()
    val isPendingCancel: Boolean get() = status == "pending_cancel" || cancelStatus == "pending"
}

@Composable
fun ScheduleScreen() {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    var bookings by remember { mutableStateOf<List<BookingRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var detailsFor by remember { mutableStateOf<BookingRow?>(null) }
    var requestingCancelFor by remember { mutableStateOf<BookingRow?>(null) }

    LaunchedEffect(uid) {
        if (uid == null) { loading = false; return@LaunchedEffect }
        val db = FirebaseDatabase.getInstance(DB_URL)
        val userIdxRef = db.reference.child("userBookings").child(uid)

        userIdxRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(idxSnap: DataSnapshot) {
                val ids = idxSnap.children.mapNotNull { it.key }
                if (ids.isEmpty()) { bookings = emptyList(); loading = false; return }
                val bookRef = db.reference.child("bookings")
                val acc = mutableListOf<BookingRow>()
                var pending = ids.size
                ids.forEach { id ->
                    bookRef.child(id).get()
                        .addOnSuccessListener { s ->
                            if (s.exists()) {
                                val date = s.child("date").getValue(String::class.java) ?: ""
                                val sport = s.child("sport").getValue(String::class.java) ?: ""
                                val courtId = s.child("courtId").getValue(String::class.java) ?: ""
                                val courtName = s.child("courtName").getValue(String::class.java) ?: courtId
                                val rate = (s.child("ratePerHour").value as? Number)?.toDouble()
                                    ?: s.child("ratePerHour").getValue(String::class.java)?.toDoubleOrNull() ?: 0.0
                                val totalPaid = (s.child("totalPaid").value as? Number)?.toDouble()
                                    ?: s.child("totalPaid").getValue(String::class.java)?.toDoubleOrNull() ?: 0.0
                                val times = s.child("times").children.mapNotNull { it.getValue(String::class.java) }.sorted()
                                val equip = s.child("equipment").children.map { e ->
                                    EquipmentRow(
                                        name = e.child("name").getValue(String::class.java) ?: (e.key ?: ""),
                                        qty = (e.child("qty").value as? Number)?.toInt() ?: 0,
                                        rate = (e.child("rate").value as? Number)?.toDouble()
                                            ?: e.child("rate").getValue(String::class.java)?.toDoubleOrNull() ?: 0.0
                                    )
                                }.filter { it.qty > 0 }
                                val voucherPct = (s.child("voucher").child("percentOff").value as? Number)?.toInt()
                                    ?: s.child("voucher").child("percentOff").getValue(String::class.java)?.toIntOrNull()

                                val status = s.child("status").getValue(String::class.java)
                                val cancelNote = s.child("cancellationRequest").child("note").getValue(String::class.java)
                                val cancelStatus = s.child("cancellationRequest").child("status").getValue(String::class.java)

                                acc += BookingRow(
                                    id = id,
                                    date = date,
                                    sport = sport,
                                    courtId = courtId,
                                    courtName = courtName,
                                    times = times,
                                    ratePerHour = rate,
                                    totalPaid = totalPaid,
                                    equipment = equip,
                                    voucherPercent = voucherPct,
                                    status = status,
                                    cancelNote = cancelNote,
                                    cancelStatus = cancelStatus
                                )
                            } else {
                                userIdxRef.child(id).removeValue()
                            }
                        }
                        .addOnCompleteListener {
                            if (--pending == 0) {
                                bookings = acc.sortedWith(compareBy({ it.date }, { it.times.firstOrNull() ?: "" }))
                                loading = false
                            }
                        }
                }
            }
            override fun onCancelled(error: DatabaseError) { loading = false }
        })
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text("My Schedule", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
                bookings.isEmpty() -> Text("No upcoming bookings.", modifier = Modifier.padding(horizontal = 16.dp))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(bookings, key = { it.id }) { b ->
                        BookingCard(
                            b = b,
                            onRequestCancel = { requestingCancelFor = b },
                            onView = { detailsFor = b }
                        )
                    }
                }
            }
        }
    }

    // Details bottom sheet
    if (detailsFor != null) {
        ModalBottomSheet(onDismissRequest = { detailsFor = null }) {
            val b = detailsFor!!
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Booking details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${b.date}  •  ${b.sport}")
                Text(b.courtName)
                Text("Time: ${spanLabel(b.times)}")
                if (b.isPendingCancel) {
                    AssistChip(onClick = {}, label = { Text("Cancellation pending review") })
                    if (!b.cancelNote.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Your note: ${b.cancelNote}")
                    }
                }
                Divider()
                Text("Payment", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Court (${b.hours}h)")
                    Text("RM ${"%.2f".format(b.courtSubtotal)}")
                }
                if (b.equipment.isNotEmpty()) {
                    b.equipment.forEach {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${it.name} × ${it.qty}")
                            Text("RM ${"%.2f".format(it.rate * it.qty)}")
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal")
                    Text("RM ${"%.2f".format(b.subtotal)}")
                }
                if (b.voucherPercent != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Voucher (${b.voucherPercent}% off)")
                        Text("- RM ${"%.2f".format(b.subtotal * b.voucherPercent / 100.0)}")
                    }
                }
                Divider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", fontWeight = FontWeight.SemiBold)
                    Text("RM ${"%.2f".format(b.totalPaid)}", fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Points earned")
                    Text("+ ${b.pointsEarned}")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Cancel with note dialog
    if (requestingCancelFor != null) {
        CancelBookingDialog(
            booking = requestingCancelFor!!,
            onDismiss = { requestingCancelFor = null },
            onSubmitted = {
                requestingCancelFor = null
                scope.launch { snackbar.showSnackbar("Cancellation request sent. Await admin decision.") }
            }
        )
    }
}

@Composable
private fun BookingCard(
    b: BookingRow,
    onRequestCancel: () -> Unit,
    onView: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${b.date}  •  ${b.sport}", fontWeight = FontWeight.SemiBold)
            Text(b.courtName)
            Text("Time: ${spanLabel(b.times)}")
            Divider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Paid")
                Text("RM ${"%.2f".format(b.totalPaid)}")
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onView) { Text("View details") }
                Spacer(Modifier.width(8.dp))
                if (b.isPendingCancel) {
                    AssistChip(onClick = {}, label = { Text("Cancellation pending review") })
                } else {
                    OutlinedButton(onClick = onRequestCancel) { Text("Cancel") }
                }
            }
        }
    }
}

/* ---------- Helpers ---------- */

private fun spanLabel(starts: List<String>): String {
    if (starts.isEmpty()) return "-"
    val first = starts.minOrNull()!!
    val last = starts.maxOrNull()!!
    return if (starts.size == 1) "$first - ${plusOneHour(first)}" else "$first - ${plusOneHour(last)}"
}

private fun plusOneHour(hm: String): String {
    val (h, m) = hm.split(":").map { it.toInt() }
    val total = h * 60 + m + 60
    val hh = (total / 60) % 24
    val mm = total % 60
    return "%02d:%02d".format(hh, mm)
}

/* ---------- Old hard delete (kept just in case; unused now) ---------- */
private fun cancelBooking(
    b: BookingRow,
    onError: (String?) -> Unit,
    onSuccess: () -> Unit
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return onError("Not logged in")
    val db = FirebaseDatabase.getInstance(DB_URL).reference

    val updates = hashMapOf<String, Any?>(
        "/bookings/${b.id}" to null,
        "/userBookings/$uid/${b.id}" to null
    )
    b.times.sorted().forEach { st ->
        val slotKey = "$st-${plusOneHour(st)}"
        updates["/facilityBookings/${b.courtId}/${b.date}/$slotKey"] = null
    }

    db.updateChildren(updates)
        .addOnSuccessListener { onSuccess() }
        .addOnFailureListener { e -> onError(e.message) }
}

/* ---------- New: Cancel with note dialog ---------- */
@Composable
private fun CancelBookingDialog(
    booking: BookingRow,
    onDismiss: () -> Unit,
    onSubmitted: () -> Unit
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var note by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val db = remember { FirebaseDatabase.getInstance(DB_URL).reference }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Request Cancellation") },
        text = {
            Column {
                Text("Tell admin why you’re cancelling (optional but helpful):")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(400) },
                    placeholder = { Text("E.g., injured, exam clash, transport issue…") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading,
                onClick = {
                    loading = true
                    // Write status + cancellationRequest (do NOT delete booking)
                    val updates = hashMapOf<String, Any?>(
                        "/bookings/${booking.id}/status" to "pending_cancel",
                        "/bookings/${booking.id}/cancellationRequest" to mapOf(
                            "note" to note.trim(),
                            "requestedBy" to uid,
                            "requestedAt" to ServerValue.TIMESTAMP,
                            "status" to "pending",
                            "processedAt" to 0
                        )
                    )
                    db.updateChildren(updates)
                        .addOnSuccessListener {
                            loading = false
                            onSubmitted()
                        }
                        .addOnFailureListener { e ->
                            loading = false
                            scope.launch {
                                // local snackbar would be nicer, but we don't have it here
                            }
                        }
                }
            ) { Text(if (loading) "Submitting..." else "Submit") }
        },
        dismissButton = {
            TextButton(enabled = !loading, onClick = onDismiss) { Text("Close") }
        }
    )
}
