@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.speedsport.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val DB_URL =
    "https://speedsport-edf02-default-rtdb.asia-southeast1.firebasedatabase.app"

/* RTDB models */
data class VoucherCatalog(
    val id: String,
    val title: String,          // e.g., "RM10 off"
    val costPoints: Int,
    val amountOffMYR: Int,
    val active: Boolean
)

data class MyVoucher(
    val id: String,
    val title: String,
    val code: String,
    val amountOffMYR: Int,
    val used: Boolean
)

@Composable
fun VoucherScreen(onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var tab by remember { mutableStateOf(0) }
    var balance by remember { mutableStateOf(0) }
    var shop by remember { mutableStateOf<List<VoucherCatalog>>(emptyList()) }
    var mine by remember { mutableStateOf<List<MyVoucher>>(emptyList()) }
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(Unit) {
        if (uid == null) return@LaunchedEffect
        val db = FirebaseDatabase.getInstance(DB_URL).reference

        // Live balance
        db.child("users").child(uid).child("points")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    balance = (s.value as? Number)?.toInt() ?: 0
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        // Shop
        db.child("vouchers").orderByChild("active").equalTo(true)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    shop = s.children.mapNotNull { n ->
                        val id = n.key ?: return@mapNotNull null
                        val amount = (n.child("amountOffMYR").value as? Number)?.toInt() ?: 0
                        val cost = (n.child("costPoints").value as? Number)?.toInt() ?: 0
                        VoucherCatalog(
                            id = id,
                            title = "RM$amount off",
                            costPoints = cost,
                            amountOffMYR = amount,
                            active = n.child("active").getValue(Boolean::class.java) ?: false
                        )
                    }.sortedBy { it.costPoints }
                }
                override fun onCancelled(error: DatabaseError) {
                    scope.launch { snackbar.showSnackbar(error.message) }
                }
            })

        // Mine
        db.child("users").child(uid).child("vouchers")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    mine = s.children.mapNotNull { n ->
                        val id = n.key ?: return@mapNotNull null
                        MyVoucher(
                            id = id,
                            title = n.child("name").getValue(String::class.java) ?: "Voucher",
                            code = n.child("code").getValue(String::class.java) ?: "",
                            amountOffMYR = (n.child("amountOffMYR").value as? Number)?.toInt() ?: 0,
                            used = n.child("used").getValue(Boolean::class.java) ?: false
                        )
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    scope.launch { snackbar.showSnackbar(error.message) }
                }
            })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vouchers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ListItem(
                headlineContent = { Text("Balance") },
                supportingContent = { Text("$balance pts") }
            )
            Divider()

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Shop") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("My vouchers") })
            }

            if (tab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(shop, key = { it.id }) { v ->
                        ListItem(
                            headlineContent = { Text(v.title) },
                            supportingContent = { Text("Cost: ${v.costPoints} pts") },
                            trailingContent = {
                                Button(
                                    enabled = balance >= v.costPoints,
                                    onClick = {
                                        val uidNow = uid ?: return@Button
                                        val db = FirebaseDatabase.getInstance(DB_URL).reference
                                        val code = randomCode9()
                                        val pushId = db.child("users").child(uidNow)
                                            .child("vouchers").push().key ?: return@Button

                                        val updates = hashMapOf<String, Any>(
                                            "/users/$uidNow/points" to ServerValue.increment(-v.costPoints.toLong()),
                                            // write negative points HISTORY (this is what PointsScreen shows)
                                            "/users/$uidNow/pointsHistory/$pushId" to mapOf(
                                                "delta" to -v.costPoints,
                                                "reason" to "Purchased ${v.title}",
                                                "ts" to ServerValue.TIMESTAMP
                                            ),
                                            "/users/$uidNow/vouchers/$pushId" to mapOf(
                                                "name" to v.title,
                                                "code" to code,
                                                "amountOffMYR" to v.amountOffMYR,
                                                "used" to false,
                                                "createdAt" to ServerValue.TIMESTAMP
                                            )
                                        )

                                        db.updateChildren(updates)
                                            .addOnSuccessListener {
                                                // local UI update
                                                mine = mine + MyVoucher(
                                                    id = pushId,
                                                    title = v.title,
                                                    code = code,
                                                    amountOffMYR = v.amountOffMYR,
                                                    used = false
                                                )
                                                scope.launch { snackbar.showSnackbar("Purchased • Code: $code") }
                                            }
                                            .addOnFailureListener { e ->
                                                scope.launch { snackbar.showSnackbar(e.message ?: "Failed") }
                                            }
                                    }
                                ) { Text("Buy") }
                            }
                        )
                        Divider()
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(mine, key = { it.id }) { m ->
                        ListItem(
                            headlineContent = { Text(m.title) },
                            supportingContent = {
                                Column {
                                    Text("Code: ${m.code}")
                                    Text("Discount: RM${m.amountOffMYR}")
                                    if (m.used) Text("Used", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = {
                                    clipboard.setText(AnnotatedString(m.code))
                                    scope.launch { snackbar.showSnackbar("Code copied") }
                                }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy") }
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

private fun randomCode9(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..9).map { chars[Random.nextInt(chars.length)] }.joinToString("")
}
