@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.speedsport.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.speedsport.app.domain.BookingDraft
import kotlinx.coroutines.launch

private const val DB_URL =
    "https://speedsport-edf02-default-rtdb.asia-southeast1.firebasedatabase.app"

data class WaitlistItem(
    val requestId: String,
    val courtId: String,
    val courtName: String,
    val sport: String,
    val date: String,            // yyyy-MM-dd
    val slotKey: String,         // "HH:MM-HH:MM"
    val status: String           // "queued" | "held" | "fulfilled" | "canceled"
)

@Composable
fun WaitlistScreen(
    onBack: () -> Unit,
    // We’ll reuse your checkout: set a draft and navigate from AppRoot
    onBookNow: (BookingDraft) -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var list by remember { mutableStateOf<List<WaitlistItem>>(emptyList()) }

    // Load my waitlist items (denormalized from userWaitlist -> facilityWaitlist nodes)
    LaunchedEffect(uid) {
        if (uid == null) return@LaunchedEffect
        val db = FirebaseDatabase.getInstance(DB_URL).reference
        // get keys
        db.child("userWaitlist").child(uid).get().addOnSuccessListener { mapSnap ->
            val reqIds = mapSnap.children.mapNotNull { it.key }
            if (reqIds.isEmpty()) { list = emptyList(); return@addOnSuccessListener }

            // For demo MVP we scan whole facilityWaitlist to find my entries (simple & okay at small scale)
            db.child("facilityWaitlist").get().addOnSuccessListener { root ->
                val acc = mutableListOf<WaitlistItem>()
                for (court in root.children) {
                    val courtId = court.key ?: continue
                    for (date in court.children) {
                        val dateKey = date.key ?: continue
                        for (slot in date.children) {
                            val slotKey = slot.key ?: continue
                            for (req in slot.children) {
                                val rid = req.key ?: continue
                                if (!reqIds.contains(rid)) continue
                                val userId = req.child("userId").getValue(String::class.java)
                                if (userId != uid) continue
                                val status = req.child("status").getValue(String::class.java) ?: "queued"
                                val sport = req.child("sport").getValue(String::class.java) ?: ""
                                val courtName = req.child("courtName").getValue(String::class.java) ?: courtId
                                acc += WaitlistItem(
                                    requestId = rid,
                                    courtId = courtId,
                                    courtName = courtName,
                                    sport = sport,
                                    date = dateKey,
                                    slotKey = slotKey,
                                    status = status
                                )
                            }
                        }
                    }
                }
                list = acc.sortedBy { it.date }
            }.addOnFailureListener {
                scope.launch { snackbar.showSnackbar("Failed to load waitlist") }
            }
        }.addOnFailureListener {
            scope.launch { snackbar.showSnackbar("Failed to load waitlist") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Waitlist") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (list.isEmpty()) {
                item { Text("You have no waitlist requests yet.", modifier = Modifier.padding(8.dp)) }
            }
            items(list, key = { it.requestId }) { item ->
                ElevatedCard {
                    Column(Modifier.padding(12.dp)) {
                        Text("${item.sport} • ${item.courtName}")
                        Text("${item.date} • ${item.slotKey}")
                        val statusText = when (item.status) {
                            "held" -> "Held for you"
                            "fulfilled" -> "Booked"
                            "canceled" -> "Canceled"
                            else -> "Queued"
                        }
                        Text(statusText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // “Book now” enabled only when slot is free (simple MVP) and status == queued or held
                            val slotFreeState = remember { mutableStateOf<Boolean?>(null) }
                            LaunchedEffect(item.courtId, item.date, item.slotKey) {
                                val freeRef = FirebaseDatabase.getInstance(DB_URL)
                                    .reference.child("facilityBookings")
                                    .child(item.courtId).child(item.date).child(item.slotKey)
                                freeRef.get().addOnSuccessListener { s ->
                                    slotFreeState.value = !s.exists()
                                }.addOnFailureListener { slotFreeState.value = null }
                            }

                            val canBookNow = (slotFreeState.value == true) && (item.status == "queued" || item.status == "held")

                            Button(
                                enabled = canBookNow,
                                onClick = {
                                    // Build a 1-hour draft and hand off to checkout
                                    val start = item.slotKey.substringBefore('-')
                                    val draft = BookingDraft(
                                        dateIso = item.date,
                                        sport = item.sport,
                                        courtId = item.courtId,
                                        courtName = item.courtName,
                                        courtRatePerHour = 0.0, // we’ll read actual rate below
                                        selectedTimes = listOf(start),
                                        equipment = emptyList()
                                    )

                                    // Read facility rate first, then pass to onBookNow
                                    val db = FirebaseDatabase.getInstance(DB_URL).reference
                                    db.child("facilities").child(item.courtId).child("ratePerHour").get()
                                        .addOnSuccessListener { r ->
                                            val rate = (r.value as? Number)?.toDouble() ?: 0.0
                                            val finalDraft = draft.copy(courtRatePerHour = rate)
                                            onBookNow(finalDraft)
                                        }.addOnFailureListener {
                                            onBookNow(draft) // fallback; rate will appear as RM0 (user can still confirm)
                                        }
                                }
                            ) { Text("Book now") }

                            OutlinedButton(
                                onClick = {
                                    cancelWaitlist(item) { ok ->
                                        scope.launch {
                                            snackbar.showSnackbar(if (ok) "Removed from waitlist" else "Failed to remove")
                                        }
                                        if (ok) {
                                            list = list.filterNot { it.requestId == item.requestId }
                                        }
                                    }
                                }
                            ) { Text("Cancel") }
                        }
                    }
                }
            }
        }
    }
}

private fun cancelWaitlist(item: WaitlistItem, cb: (Boolean) -> Unit) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return cb(false)
    val db = FirebaseDatabase.getInstance(DB_URL).reference
    val updates = hashMapOf<String, Any?>(
        "/facilityWaitlist/${item.courtId}/${item.date}/${item.slotKey}/${item.requestId}" to null,
        "/userWaitlist/$uid/${item.requestId}" to null
    )
    db.updateChildren(updates)
        .addOnSuccessListener { cb(true) }
        .addOnFailureListener { cb(false) }
}
