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

private const val DB_URL =
    "https://speedsport-edf02-default-rtdb.asia-southeast1.firebasedatabase.app"

private data class PointsEvent(
    val id: String,
    val delta: Int,      // + earn, - spend
    val reason: String,
    val ts: Long
)

@Composable
fun PointsScreen(onBack: () -> Unit) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var balance by remember { mutableStateOf(0) }
    var events by remember { mutableStateOf<List<PointsEvent>>(emptyList()) }

    // Live balance
    DisposableEffect(uid) {
        if (uid == null) return@DisposableEffect onDispose { }
        val ref = FirebaseDatabase.getInstance(DB_URL)
            .reference.child("users").child(uid).child("points")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { balance = (s.value as? Number)?.toInt() ?: 0 }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(l)
        onDispose { ref.removeEventListener(l) }
    }

    // Unified history: reads what VoucherScreen & Checkout write
    DisposableEffect(uid) {
        if (uid == null) return@DisposableEffect onDispose { }
        val ref = FirebaseDatabase.getInstance(DB_URL)
            .reference.child("users").child(uid).child("pointsHistory")
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                events = snapshot.children.mapNotNull { n ->
                    val id = n.key ?: return@mapNotNull null
                    PointsEvent(
                        id = id,
                        delta = (n.child("delta").value as? Number)?.toInt() ?: 0,
                        reason = n.child("reason").getValue(String::class.java) ?: "Activity",
                        ts = (n.child("ts").value as? Number)?.toLong() ?: 0L
                    )
                }.sortedByDescending { it.ts }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(l)
        onDispose { ref.removeEventListener(l) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Points") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Current balance", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(2.dp))
                Text("$balance pts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            Divider()
            if (events.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No points history yet")
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(events, key = { it.id }) { PointsRow(it) }
                }
            }
        }
    }
}

@Composable
private fun PointsRow(e: PointsEvent) {
    val sign = if (e.delta >= 0) "+" else ""
    val color = if (e.delta >= 0) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.error
    val df = remember { SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.getDefault()) }
    val dateStr = df.format(Date(if (e.ts == 0L) System.currentTimeMillis() else e.ts))

    ElevatedCard {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(e.reason, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("$sign${e.delta} pts", color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}
