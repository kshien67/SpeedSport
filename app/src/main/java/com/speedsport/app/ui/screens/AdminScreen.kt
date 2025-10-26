@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.speedsport.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.database.*
import com.speedsport.app.data.rtdb.*
import kotlinx.coroutines.launch

private const val DB_URL =
    "https://speedsport-edf02-default-rtdb.asia-southeast1.firebasedatabase.app"

/* ------------ In-screen navigation ------------ */
private sealed class AdminSection(val title: String) {
    data object Menu          : AdminSection("Admin")
    data object Equipment     : AdminSection("Equipment")
    data object Sports        : AdminSection("Sports")
    data object Facilities    : AdminSection("Facilities")
    data object Cancellations : AdminSection("Cancellations")
    data object Vouchers      : AdminSection("Vouchers")
}

/* ============================================================
 * Entry
 * ============================================================ */
@Composable
fun AdminScreen() {
    val snackbar = remember { SnackbarHostState() }
    var section by remember { mutableStateOf<AdminSection>(AdminSection.Menu) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(section.title) },
                navigationIcon = {
                    if (section != AdminSection.Menu) {
                        IconButton(onClick = { section = AdminSection.Menu }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { inner ->
        when (section) {
            AdminSection.Menu          -> AdminMenu(Modifier.padding(inner)) { section = it }
            AdminSection.Equipment     -> EquipmentScreen(Modifier.padding(inner), snackbar)
            AdminSection.Sports        -> SportsScreen(Modifier.padding(inner), snackbar)
            AdminSection.Facilities    -> FacilitiesScreen(Modifier.padding(inner), snackbar)
            AdminSection.Cancellations -> CancellationsScreen(Modifier.padding(inner), snackbar)
            AdminSection.Vouchers      -> VouchersScreen(Modifier.padding(inner), snackbar)
        }
    }
}

/* ============================================================
 * Menu
 * ============================================================ */
@Composable
private fun AdminMenu(
    modifier: Modifier = Modifier,
    onOpen: (AdminSection) -> Unit
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Tools", style = MaterialTheme.typography.titleMedium)
        val items = listOf(
            AdminSection.Equipment,
            AdminSection.Sports,
            AdminSection.Facilities,
            AdminSection.Cancellations,
            AdminSection.Vouchers
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items) { sec ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(sec) }
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                sec.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                when (sec) {
                                    AdminSection.Equipment     -> "View / filter by sport. Add, edit, delete."
                                    AdminSection.Sports        -> "Add/Delete sport (deletes its facilities & equipment)."
                                    AdminSection.Facilities    -> "Per-sport courts. Edit name & rate."
                                    AdminSection.Cancellations -> "Approve / Reject cancellations."
                                    AdminSection.Vouchers      -> "RM X off + point cost."
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

/* ============================================================
 * Helpers
 * ============================================================ */
private fun cleanDecimal(input: String): String {
    val sb = StringBuilder()
    var dot = false
    for (ch in input) {
        when {
            ch in '0'..'9' -> sb.append(ch)
            ch == '.' && !dot -> { sb.append('.'); dot = true }
        }
    }
    return sb.toString()
}
private fun cleanInt(input: String): String = input.filter { it.isDigit() }

/** Live sports list from /sports with a fallback scanning facilities/equipment. */
@Composable
private fun rememberLiveSports(): State<List<String>> {
    val db = remember { FirebaseDatabase.getInstance(DB_URL).reference }
    val state = remember { mutableStateOf<List<String>>(emptyList()) }

    DisposableEffect(Unit) {
        val sportsRef = db.child("sports")
        val listener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val fromSports = s.children.mapNotNull { it.key }
                    .map { it.trim() }.filter { it.isNotEmpty() }.map { it.uppercase() }
                if (fromSports.isNotEmpty()) {
                    state.value = fromSports.sorted()
                } else {
                    db.child("facilities").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(f: DataSnapshot) {
                            val a = f.children.mapNotNull {
                                it.child("sport").getValue(String::class.java)
                                    ?: it.child("type").getValue(String::class.java)
                            }
                            db.child("equipment").addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(e: DataSnapshot) {
                                    val b = e.children.mapNotNull {
                                        it.child("sport").getValue(String::class.java)
                                            ?: it.child("type").getValue(String::class.java)
                                    }
                                    state.value = (a + b)
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .map { it.uppercase() }
                                        .toSet()
                                        .toList()
                                        .sorted()
                                }
                                override fun onCancelled(error: DatabaseError) {}
                            })
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        sportsRef.addValueEventListener(listener)
        onDispose { sportsRef.removeEventListener(listener) }
    }
    return state
}

/** A generic dialog picker that shows a list of sports as big buttons. */
@Composable
private fun SportPickerDialog(
    show: Boolean,
    sports: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose sport") },
        text = {
            if (sports.isEmpty()) {
                Text("No sports yet.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sports.forEach { s ->
                        OutlinedButton(
                            onClick = { onPick(s); onDismiss() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(s) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/* ============================================================
 * Equipment
 * ============================================================ */
@Composable
private fun EquipmentScreen(
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState
) {
    val scope = rememberCoroutineScope()

    val sports by rememberLiveSports()
    var filter by remember { mutableStateOf<String?>("ALL") }
    var showFilterPicker by remember { mutableStateOf(false) }

    var list by remember { mutableStateOf<List<EquipmentDto>>(emptyList()) }
    suspend fun reload() { list = RtdbRepo.listEquipmentBySportOrAll(filter) }
    LaunchedEffect(Unit, filter) { reload() }

    // Add form state
    var showAdd by remember { mutableStateOf(false) }
    var addName by remember { mutableStateOf("") }
    var addSport by remember { mutableStateOf<String?>(null) }
    var addRate by remember { mutableStateOf("") }
    var addStock by remember { mutableStateOf("") }
    var showAddSportPicker by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header: Filter + Add
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Equipment list", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showFilterPicker = true }) {
                    Text(if (filter == "ALL") "Filter: All" else "Filter: $filter")
                }
                OutlinedButton(onClick = { showAdd = !showAdd }) {
                    Text(if (showAdd) "Close" else "Add")
                }
            }
        }

        // Filter picker dialog
        SportPickerDialog(
            show = showFilterPicker,
            sports = listOf("ALL") + sports,
            onPick = { choice ->
                filter = choice
                scope.launch { reload() }
            },
            onDismiss = { showFilterPicker = false }
        )

        // Add form (panel)
        if (showAdd) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Add equipment", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = addName,
                        onValueChange = { addName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Sport picker button (dialog)
                    OutlinedButton(
                        onClick = { showAddSportPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(addSport ?: "Sport")
                    }
                    SportPickerDialog(
                        show = showAddSportPicker,
                        sports = sports,
                        onPick = { addSport = it },
                        onDismiss = { showAddSportPicker = false }
                    )

                    OutlinedTextField(
                        value = addRate,
                        onValueChange = { addRate = cleanDecimal(it) },
                        label = { Text("Rate (MYR)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = addStock,
                        onValueChange = { addStock = cleanInt(it) },
                        label = { Text("Stock") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val s = addSport ?: return@Button
                            scope.launch {
                                runCatching {
                                    RtdbRepo.addEquipment(
                                        sport = s,
                                        label = addName.trim(),
                                        rate = addRate.toDoubleOrNull() ?: 0.0,
                                        stock = addStock.toIntOrNull() ?: 0
                                    )
                                }.onSuccess {
                                    showAdd = false
                                    addName = ""; addSport = null; addRate = ""; addStock = ""
                                    reload()
                                    snackbar.showSnackbar("Added")
                                }.onFailure {
                                    snackbar.showSnackbar("Failed")
                                }
                            }
                        }) { Text("Save") }
                        OutlinedButton(onClick = {
                            showAdd = false; addName = ""; addSport = null; addRate = ""; addStock = ""
                        }) { Text("Cancel") }
                    }
                }
            }
        }

        Divider()

        if (list.isEmpty()) {
            Text("No equipment found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { e ->
                    EquipmentRow(
                        e = e,
                        onEdit = { newName, newRate, newStock ->
                            scope.launch {
                                RtdbRepo.updateEquipmentFields(e.id, newName, newRate, newStock)
                                reload()
                                snackbar.showSnackbar("Saved successfully")
                            }
                        },
                        onDelete = {
                            scope.launch {
                                RtdbRepo.deleteEquipment(e.id)
                                reload()
                                snackbar.showSnackbar("Equipment deleted")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EquipmentRow(
    e: EquipmentDto,
    onEdit: (String, Double, Int) -> Unit,
    onDelete: () -> Unit
) {
    var edit by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(e.label) }
    var rate by remember { mutableStateOf(e.rentalRate.toString()) }
    var stock by remember { mutableStateOf(e.stock.toString()) }

    ElevatedCard {
        Column(Modifier.padding(12.dp)) {
            Text(e.label, fontWeight = FontWeight.SemiBold)
            val sportLabel = if (e.sport.isBlank()) "GLOBAL" else e.sport
            Text("$sportLabel • RM ${"%.2f".format(e.rentalRate)} • Stock ${e.stock}")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { edit = true }) { Text("Edit") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDelete) { Text("Delete") }
            }

            if (edit) {
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rate,
                        onValueChange = { rate = cleanDecimal(it) },
                        label = { Text("Rate (MYR)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = stock,
                        onValueChange = { stock = cleanInt(it) },
                        label = { Text("Stock") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            onEdit(
                                name.trim().ifEmpty { e.label },
                                rate.toDoubleOrNull() ?: e.rentalRate,
                                stock.toIntOrNull() ?: e.stock
                            )
                            edit = false
                        }) { Text("Save") }
                        OutlinedButton(onClick = {
                            edit = false
                            name = e.label
                            rate = e.rentalRate.toString()
                            stock = e.stock.toString()
                        }) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

/* ============================================================
 * Sports
 * ============================================================ */
@Composable
private fun SportsScreen(modifier: Modifier = Modifier, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<String>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { list = RtdbRepo.listSports() }

    Column(
        modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Manage sports", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Sport name") },
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                scope.launch { RtdbRepo.addSport(name); name = ""; list = RtdbRepo.listSports() }
            }) { Text("Add") }
        }
        Divider()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it }) { s ->
                ElevatedCard {
                    Row(
                        Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(s, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = {
                            scope.launch {
                                RtdbRepo.deleteSportCascade(s)
                                list = RtdbRepo.listSports()
                                snackbar.showSnackbar("Deleted $s and its facilities/equipment")
                            }
                        }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

/* ============================================================
 * Facilities
 * ============================================================ */
@Composable
private fun FacilitiesScreen(modifier: Modifier = Modifier, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val sports by rememberLiveSports()

    var selectedSport by remember { mutableStateOf<String?>(null) }
    var showSportPicker by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<FacilityDto>>(emptyList()) }

    fun reload() = scope.launch { selectedSport?.let { list = RtdbRepo.listFacilitiesBySport(it) } }

    Column(
        modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Choose sport", style = MaterialTheme.typography.titleMedium)

        // Sport selector button (dialog)
        OutlinedButton(onClick = { showSportPicker = true }) {
            Text(selectedSport ?: "Select sport")
        }
        SportPickerDialog(
            show = showSportPicker,
            sports = sports,
            onPick = { picked -> selectedSport = picked; reload() },
            onDismiss = { showSportPicker = false }
        )

        if (selectedSport != null) {
            Divider()
            Text("Add a court", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Court name") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = cleanDecimal(it) },
                    label = { Text("Rate/hour") },
                    modifier = Modifier.width(160.dp)
                )
                Button(onClick = {
                    val s = selectedSport ?: return@Button
                    scope.launch {
                        runCatching { RtdbRepo.addFacility(name, s, rate.toDoubleOrNull() ?: 0.0) }
                            .onSuccess { name = ""; rate = ""; reload() }
                            .onFailure { snackbar.showSnackbar("Failed") }
                    }
                }) { Text("Add") }
            }

            Divider()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { f ->
                    FacilityRowAdmin(
                        f = f,
                        onEdit = { newName, newRate ->
                            scope.launch {
                                RtdbRepo.updateFacilityNameRate(f.id, newName, newRate)
                                reload()
                                snackbar.showSnackbar("Saved successfully")
                            }
                        },
                        onDelete = {
                            scope.launch { RtdbRepo.deleteFacility(f.id); reload(); snackbar.showSnackbar("Facility deleted") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FacilityRowAdmin(
    f: FacilityDto,
    onEdit: (String, Double) -> Unit,
    onDelete: () -> Unit
) {
    var edit by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(f.name) }
    var rate by remember { mutableStateOf(f.hourlyRateMYR.toString()) }

    ElevatedCard {
        Column(Modifier.padding(12.dp)) {
            Text(f.name, fontWeight = FontWeight.SemiBold)
            Text("${f.sport} • RM ${"%.2f".format(f.hourlyRateMYR)}/h")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { edit = true }) { Text("Edit") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDelete) { Text("Delete") }
            }

            if (edit) {
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rate,
                        onValueChange = { rate = cleanDecimal(it) },
                        label = { Text("Rate") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            onEdit(name.trim().ifEmpty { f.name }, rate.toDoubleOrNull() ?: f.hourlyRateMYR)
                            edit = false
                        }) { Text("Save") }
                        OutlinedButton(onClick = {
                            edit = false; name = f.name; rate = f.hourlyRateMYR.toString()
                        }) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

/* ============================================================
 * Vouchers
 * ============================================================ */
@Composable
private fun VouchersScreen(modifier: Modifier = Modifier, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<VoucherDto>>(emptyList()) }
    var rmOff by remember { mutableStateOf("") }
    var costPts by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { list = RtdbRepo.listVouchers() }

    Column(
        modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Add voucher", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = rmOff,
                onValueChange = { rmOff = cleanDecimal(it) },
                label = { Text("RM off") },
                modifier = Modifier.width(160.dp)
            )
            OutlinedTextField(
                value = costPts,
                onValueChange = { costPts = cleanInt(it) },
                label = { Text("Cost (points)") },
                modifier = Modifier.width(180.dp)
            )
            Button(onClick = {
                val amount = rmOff.toDoubleOrNull() ?: 0.0
                val cost = costPts.toIntOrNull() ?: 0
                scope.launch {
                    runCatching { RtdbRepo.addVoucher(amount, cost) }
                        .onSuccess {
                            list = RtdbRepo.listVouchers()
                            rmOff = ""; costPts = ""
                            snackbar.showSnackbar("Added")
                        }
                        .onFailure { snackbar.showSnackbar("Add failed") }
                }
            }) { Text("Add") }
        }
        Divider()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { v ->
                ElevatedCard {
                    Row(
                        Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("RM ${"%.2f".format(v.amountOffMYR)} off", fontWeight = FontWeight.SemiBold)
                            Text("${v.costPoints} points")
                        }
                        TextButton(onClick = {
                            scope.launch {
                                RtdbRepo.deleteVoucher(v.id)
                                list = RtdbRepo.listVouchers()
                                snackbar.showSnackbar("Voucher deleted")
                            }
                        }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

/* ============================================================
 * Cancellations
 * ============================================================ */
private data class AdminBookingRow(
    val id: String,
    val date: String,
    val sport: String,
    val courtId: String,
    val courtName: String,
    val times: List<String>,
    val note: String,
    val userEmail: String
)

@Composable
private fun CancellationsScreen(modifier: Modifier = Modifier, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<AdminBookingRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val ref = FirebaseDatabase.getInstance(DB_URL).reference.child("bookings")
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val acc = mutableListOf<AdminBookingRow>()
                for (b in s.children) {
                    val status = b.child("status").getValue(String::class.java).orEmpty()
                    val crStatus = b.child("cancellationRequest/status").getValue(String::class.java).orEmpty()
                    val pendingCancel = status == "pending_cancel" || crStatus == "pending"
                    if (!pendingCancel) continue

                    val id = b.key ?: continue
                    val date = b.child("date").getValue(String::class.java).orEmpty()
                    val sport = b.child("sport").getValue(String::class.java).orEmpty()
                    val courtId = b.child("courtId").getValue(String::class.java).orEmpty()
                    val courtName = b.child("courtName").getValue(String::class.java) ?: courtId
                    val times = b.child("times").children.mapNotNull { it.getValue(String::class.java) }.sorted()
                    val note = b.child("cancellationRequest/note").getValue(String::class.java) ?: ""
                    val userEmail = b.child("userEmail").getValue(String::class.java).orEmpty()

                    acc += AdminBookingRow(id, date, sport, courtId, courtName, times, note, userEmail)
                }
                pending = acc.sortedWith(compareBy({ it.date }, { it.times.firstOrNull() ?: "" }))
                loading = false
            }
            override fun onCancelled(error: DatabaseError) { loading = false }
        })
    }

    Column(modifier.fillMaxSize()) {
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (pending.isEmpty()) {
            Text("No cancellation requests.", modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pending, key = { it.id }) { row ->
                    ElevatedCard {
                        Column(Modifier.padding(12.dp)) {
                            Text("${row.date} • ${row.sport}", fontWeight = FontWeight.SemiBold)
                            Text(row.courtName)
                            Text("Time: ${spanLabel(row.times)}")
                            if (row.note.isNotBlank()) Text("Reason: ${row.note}")
                            Text("Booker: ${row.userEmail}")
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                OutlinedButton(onClick = {
                                    scope.launch { RtdbRepo.denyCancellationRtdb(row.id); snackbar.showSnackbar("Denied") }
                                }) { Text("Deny") }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = {
                                    scope.launch {
                                        RtdbRepo.approveCancellationRtdb(row.id, row.courtId, row.date, row.times)
                                        snackbar.showSnackbar("Approved")
                                    }
                                }) { Text("Approve") }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ============================================================
 * Shared bits
 * ============================================================ */
private fun spanLabel(starts: List<String>): String {
    if (starts.isEmpty()) return "-"
    val first = starts.minOrNull()!!
    val last = starts.maxOrNull()!!
    fun plus(hm: String): String {
        val (h, m) = hm.split(":").map { it.toInt() }
        val t = h * 60 + m + 60
        return "%02d:%02d".format((t / 60) % 24, t % 60)
    }
    return if (starts.size == 1) "$first - ${plus(first)}" else "$first - ${plus(last)}"
}
