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
import java.text.SimpleDateFormat
import java.util.*

/** Use your RTDB URL */
private const val DB_URL =
    "https://speedsport-edf02-default-rtdb.asia-southeast1.firebasedatabase.app"

/** UI row model for the points list */
private data class PointsRowUi(
    val createdAt: Long,
    val amount: Int,            // + earn, - spend
    val title: String,          // "Booking" / "Voucher"
    val note: String            // e.g. "Court A — 2025-10-23 (2h)"
)

@Composable
fun PointsScreen(
    onBack: () -> Unit
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    var balance by remember { mutableStateOf(0) }
    var bookingRows by remember { mutableStateOf<List<PointsRowUi>>(emptyList()) }
    var voucherRows by remember { mutableStateOf<List<PointsRowUi>>(emptyList()) }

    // Live points balance
    DisposableEffect(uid) {
        if (uid == null) return@DisposableEffect onDispose { }
        val ref = FirebaseDatabase.getInstance(DB_URL)
            .reference.child("users").child(uid).child("points")

        val listener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                balance = (s.value as? Number)?.toInt() ?: 0
            }
            override fun onCancelled(error: DatabaseError) { /* no-op */ }
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    // Booking history → +points
    DisposableEffect(uid) {
        if (uid == null) return@DisposableEffect onDispose { }
        val bookingsQuery = FirebaseDatabase.getInstance(DB_URL)
            .reference.child("bookings")
            .orderByChild("userUid").equalTo(uid)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val rows = mutableListOf<PointsRowUi>()
                for (b in snapshot.children) {
                    val totalPaid = (b.child("totalPaid").value as? Number)?.toDouble() ?: 0.0
                    val pts = totalPaid.toInt() // 1 RM = 1 pt (floor)
                    val facility = b.child("facilityName").getValue(String::class.java) ?: "Court"
                    val dateIso = b.child("date").getValue(String::class.java) ?: ""
                    val hours = b.child("times").children.count()
                    val createdAt = (b.child("createdAt").value as? Number)?.toLong()
                        ?: System.currentTimeMillis()

                    rows += PointsRowUi(
                        createdAt = createdAt,
                        amount = pts,
                        title = "Booking",
                        note = "$facility — $dateIso (${hours}h)"
                    )
                }
                // newest first
                bookingRows = rows.sortedByDescending { it.createdAt }
            }
            override fun onCancelled(error: DatabaseError) { /* no-op */ }
        }
        bookingsQuery.addValueEventListener(listener)
        onDispose { bookingsQuery.removeEventListener(listener) }
    }

    // Voucher purchases (optional) → -points
    DisposableEffect(uid) {
        if (uid == null) return@DisposableEffect onDispose { }
        val ref = FirebaseDatabase.getInstance(DB_URL)
            .reference.child("users").child(uid).child("voucherPurchases")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val rows = mutableListOf<PointsRowUi>()
                for (c in snapshot.children) {
                    val cost = (c.child("costPoints").value as? Number)?.toInt()
                        ?: continue
                    val title = c.child("title").getValue(String::class.java) ?: "Voucher"
                    val ts = (c.child("createdAt").value as? Number)?.toLong()
                        ?: System.currentTimeMillis()

                    rows += PointsRowUi(
                        createdAt = ts,
                        amount = -cost,
                        title = "Voucher",
                        note = title
                    )
                }
                voucherRows = rows.sortedByDescending { it.createdAt }
            }
            override fun onCancelled(error: DatabaseError) { /* no-op */ }
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    // Combine & sort
    val allRows by remember(bookingRows, voucherRows) {
        mutableStateOf((bookingRows + voucherRows).sortedByDescending { it.createdAt })
    }

    // -------- UI --------
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Points") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Balance
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Current balance", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    "$balance pts",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Divider()

            // History list
            if (allRows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No points history yet")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(allRows) { r ->
                        PointsRow(r)
                    }
                }
            }
        }
    }
}

@Composable
private fun PointsRow(r: PointsRowUi) {
    val sign = if (r.amount >= 0) "+" else ""
    val color = if (r.amount >= 0) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.error
    val df = remember { SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.getDefault()) }
    val dateStr = df.format(Date(r.createdAt))

    ElevatedCard {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (r.note.isNotBlank()) {
                    Text(r.note, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "$sign${r.amount} pts",
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
