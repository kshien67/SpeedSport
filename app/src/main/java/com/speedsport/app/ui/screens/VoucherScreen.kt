@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.speedsport.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ServerValue
import kotlin.random.Random
import kotlinx.coroutines.launch

private const val DB_URL =
    "https://speedsport-edf02-default-rtdb.asia-southeast1.firebasedatabase.app"

data class VoucherCatalog(val id: String, val name: String, val cost: Int, val percentOff: Int, val desc: String)
data class MyVoucher(val id: String, val name: String, val code: String, val percentOff: Int, val used: Boolean)

@Composable
fun VoucherScreen(onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(0) }
    var balance by remember { mutableStateOf(0) }
    var shop by remember { mutableStateOf<List<VoucherCatalog>>(emptyList()) }
    var mine by remember { mutableStateOf<List<MyVoucher>>(emptyList()) }
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(Unit) {
        if (uid == null) return@LaunchedEffect
        val db = FirebaseDatabase.getInstance(DB_URL).reference
        db.child("users").child(uid).child("points").get().addOnSuccessListener {
            balance = (it.value as? Number)?.toInt() ?: 0
        }
        db.child("voucherCatalog").get().addOnSuccessListener { s ->
            shop = s.children.map {
                VoucherCatalog(
                    id = it.key ?: "",
                    name = it.child("name").getValue(String::class.java) ?: "",
                    cost = (it.child("cost").value as? Number)?.toInt() ?: 0,
                    percentOff = (it.child("percentOff").value as? Number)?.toInt() ?: 0,
                    desc = it.child("desc").getValue(String::class.java) ?: ""
                )
            }
        }
        db.child("users").child(uid).child("vouchers").get().addOnSuccessListener { s ->
            mine = s.children.map {
                MyVoucher(
                    id = it.key ?: "",
                    name = it.child("name").getValue(String::class.java) ?: "",
                    code = it.child("code").getValue(String::class.java) ?: "",
                    percentOff = (it.child("percentOff").value as? Number)?.toInt() ?: 0,
                    used = it.child("used").getValue(Boolean::class.java) ?: false
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vouchers") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ListItem(
                headlineContent = { Text("Balance") },
                supportingContent = { Text("$balance pts") }
            )
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Shop") })
                Tab(tab == 1, onClick = { tab = 1 }, text = { Text("My vouchers") })
            }
            if (tab == 0) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(shop) { v ->
                        ElevatedCard {
                            Column(Modifier.padding(12.dp)) {
                                Text(v.name, style = MaterialTheme.typography.titleMedium)
                                Text("${v.percentOff}% off • Cost ${v.cost} pts")
                                Spacer(Modifier.height(6.dp))
                                Row {
                                    Button(
                                        enabled = balance >= v.cost,
                                        onClick = {
                                            if (uid == null) return@Button
                                            val db = FirebaseDatabase.getInstance(DB_URL).reference
                                            val code = randomCode9()
                                            val myId = db.child("users").child(uid).child("vouchers").push().key ?: return@Button
                                            val updates = hashMapOf<String, Any>(
                                                "/users/$uid/points" to ServerValue.increment(-v.cost.toLong()),
                                                "/users/$uid/pointsHistory/$myId" to mapOf("delta" to -v.cost, "reason" to "Purchased ${v.name}", "ts" to System.currentTimeMillis()),
                                                "/users/$uid/vouchers/$myId" to mapOf(
                                                    "name" to v.name,
                                                    "code" to code,
                                                    "percentOff" to v.percentOff,
                                                    "used" to false
                                                )
                                            )
                                            db.updateChildren(updates)
                                                .addOnSuccessListener {
                                                    scope.launch { snackbar.showSnackbar("Purchased: code $code") }
                                                    balance -= v.cost
                                                    mine = mine + MyVoucher(myId, v.name, code, v.percentOff, false)
                                                }
                                                .addOnFailureListener { e ->
                                                    scope.launch { snackbar.showSnackbar(e.message ?: "Failed") }
                                                }
                                        }
                                    ) { Text("Buy") }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(mine) { m ->
                        ElevatedCard {
                            Column(Modifier.padding(12.dp)) {
                                Text(m.name, style = MaterialTheme.typography.titleMedium)
                                Text("Code: ${m.code}")
                                Text("Discount: ${m.percentOff}%")
                                if (m.used) Text("Used", color = MaterialTheme.colorScheme.error)
                            }
                        }
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
