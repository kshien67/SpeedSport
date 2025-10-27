@file:OptIn(ExperimentalMaterial3Api::class)

package com.speedsport.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.speedsport.app.data.rtdb.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import kotlin.math.max
import kotlin.math.roundToInt

/* ----------------------- In-screen nav ----------------------- */
private sealed class AdminSection(val title: String) {
    data object Menu          : AdminSection("Admin")
    data object Equipment     : AdminSection("Equipment")
    data object Sports        : AdminSection("Sports")
    data object Facilities    : AdminSection("Facilities")
    data object Cancellations : AdminSection("Cancellations")
    data object Vouchers      : AdminSection("Vouchers")
    data object Report        : AdminSection("Report")
}

/* ----------------------- Entry ----------------------- */
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
            AdminSection.Report        -> ReportScreen(Modifier.padding(inner))
        }
    }
}

/* ----------------------- Menu ----------------------- */
@Composable
private fun AdminMenu(
    modifier: Modifier = Modifier,
    onOpen: (AdminSection) -> Unit
) {
    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Tools", style = MaterialTheme.typography.titleMedium)
        val cards = listOf(
            AdminSection.Equipment to "View / filter by sport. Add, edit, delete.",
            AdminSection.Sports to "Add/Delete sport (deletes its facilities & equipment).",
            AdminSection.Facilities to "Per-sport courts. Edit name & rate.",
            AdminSection.Cancellations to "Approve / Reject cancellations.",
            AdminSection.Vouchers to "RM X off + point cost.",
            AdminSection.Report to "Sales report with Day/Week/Month and line chart."
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cards) { (sec, subtitle) ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(sec) }
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(sec.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

/* ----------------------- Small helpers ----------------------- */
private fun cleanDecimal(s: String): String {
    val out = StringBuilder(); var dot = false
    for (c in s) when {
        c in '0'..'9' -> out.append(c)
        c == '.' && !dot -> { out.append('.'); dot = true }
    }
    return out.toString()
}
private fun cleanInt(s: String): String = s.filter { it.isDigit() }

/* =============================== Equipment =============================== */
@Composable
private fun EquipmentScreen(modifier: Modifier = Modifier, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()

    var sports by remember { mutableStateOf<List<String>>(emptyList()) }
    var filter by remember { mutableStateOf<String?>("ALL") }
    var filterMenu by remember { mutableStateOf(false) }

    var items by remember { mutableStateOf<List<EquipmentDto>>(emptyList()) }

    // add-panel state
    var showAdd by remember { mutableStateOf(false) }
    var addName by remember { mutableStateOf("") }
    var addSport by remember { mutableStateOf<String?>(null) }
    var addRate by remember { mutableStateOf("") }
    var addStock by remember { mutableStateOf("") }
    var sportPicker by remember { mutableStateOf(false) }

    suspend fun reload() {
        if (sports.isEmpty()) sports = RtdbRepo.listSports()
        items = RtdbRepo.listEquipmentBySportOrAll(filter)
    }
    LaunchedEffect(Unit, filter) { reload() }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Equipment list", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { filterMenu = true }) {
                        Text(if (filter == "ALL") "Filter: All" else "Filter: $filter")
                    }
                    DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                        DropdownMenuItem(text = { Text("All") }, onClick = {
                            filter = "ALL"; filterMenu = false; scope.launch { reload() }
                        })
                        sports.forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = {
                                filter = s; filterMenu = false; scope.launch { reload() }
                            })
                        }
                    }
                }
                OutlinedButton(onClick = { showAdd = !showAdd }) { Text(if (showAdd) "Close" else "Add") }
            }
        }

        if (showAdd) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Add equipment", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = addName, onValueChange = { addName = it },
                        label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = { sportPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(addSport ?: "Sport")
                    }
                    OutlinedTextField(
                        value = addRate, onValueChange = { addRate = cleanDecimal(it) },
                        label = { Text("Rate (MYR)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = addStock, onValueChange = { addStock = cleanInt(it) },
                        label = { Text("Stock") }, singleLine = true, modifier = Modifier.fillMaxWidth()
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
                                }.onFailure { snackbar.showSnackbar("Failed") }
                            }
                        }) { Text("Save") }
                        OutlinedButton(onClick = {
                            showAdd = false; addName = ""; addSport = null; addRate = ""; addStock = ""
                        }) { Text("Cancel") }
                    }
                }
            }
        }

        if (items.isEmpty()) {
            Text("No equipment found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.id }) { e ->
                    EquipmentRow(
                        e = e,
                        sportLabel = filter?.takeIf { it != "ALL" },
                        onEdit = { newName, newRate, newStock ->
                            scope.launch {
                                runCatching { RtdbRepo.updateEquipmentFields(e.id, newName, newRate, newStock) }
                                    .onSuccess {
                                        items = RtdbRepo.listEquipmentBySportOrAll(filter)
                                        snackbar.showSnackbar("Saved successfully")
                                    }
                                    .onFailure { snackbar.showSnackbar("Update failed") }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                runCatching { RtdbRepo.deleteEquipment(e.id) }
                                    .onSuccess {
                                        items = RtdbRepo.listEquipmentBySportOrAll(filter)
                                        snackbar.showSnackbar("Equipment deleted")
                                    }
                                    .onFailure { snackbar.showSnackbar("Delete failed") }
                            }
                        }
                    )
                }
            }
        }

        if (sportPicker) {
            SportPickerDialog(
                title = "Choose sport",
                options = sports,
                onPick = { addSport = it; sportPicker = false },
                onDismiss = { sportPicker = false }
            )
        }
    }
}

@Composable
private fun EquipmentRow(
    e: EquipmentDto,
    sportLabel: String?,
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
            val subtitle = buildString {
                if (!sportLabel.isNullOrBlank()) append("$sportLabel • ")
                append("RM ${"%.2f".format(e.rentalRate)} • Stock ${e.stock}")
            }
            Text(subtitle)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { edit = true }) { Text("Edit") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDelete) { Text("Delete") }
            }

            if (edit) {
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = rate, onValueChange = { rate = cleanDecimal(it) }, label = { Text("Rate (MYR)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = stock, onValueChange = { stock = cleanInt(it) }, label = { Text("Stock") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            onEdit(name.trim().ifEmpty { e.label }, rate.toDoubleOrNull() ?: e.rentalRate, stock.toIntOrNull() ?: e.stock)
                            edit = false
                        }) { Text("Save") }
                        OutlinedButton(onClick = { edit = false; name = e.label; rate = e.rentalRate.toString(); stock = e.stock.toString() }) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

/* =============================== Sports =============================== */
@Composable
private fun SportsScreen(modifier: Modifier, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<String>>(emptyList()) }
    var newName by remember { mutableStateOf("") }

    suspend fun refresh() { list = RtdbRepo.listSports() }
    LaunchedEffect(Unit) { refresh() }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = {
                val n = newName.trim().ifEmpty { return@Button }
                scope.launch {
                    runCatching { RtdbRepo.addSport(n) }
                        .onSuccess { newName = ""; refresh(); snackbar.showSnackbar("Added $n") }
                        .onFailure { snackbar.showSnackbar("Add failed") }
                }
            }) { Text("Add") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it }) { s ->
                ElevatedCard {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { RtdbRepo.deleteSportCascade(s) }
                                    .onSuccess { refresh(); snackbar.showSnackbar("Deleted $s") }
                                    .onFailure { snackbar.showSnackbar("Delete failed") }
                            }
                        }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

/* =============================== Facilities =============================== */
@Composable
private fun FacilitiesScreen(modifier: Modifier = Modifier, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()

    var sports by remember { mutableStateOf<List<String>>(emptyList()) }
    var filter by remember { mutableStateOf<String?>("ALL") }
    var filterMenu by remember { mutableStateOf(false) }

    var list by remember { mutableStateOf<List<FacilityDto>>(emptyList()) }

    // Add panel
    var showAdd by remember { mutableStateOf(false) }
    var addName by remember { mutableStateOf("") }
    var addRate by remember { mutableStateOf("") }
    var addSport by remember { mutableStateOf<String?>(null) }
    var sportPicker by remember { mutableStateOf(false) }

    suspend fun reload() {
        if (sports.isEmpty()) sports = RtdbRepo.listSports()
        list = if (filter == "ALL") RtdbRepo.listFacilities() else RtdbRepo.listFacilitiesBySport(filter!!)
        if (filter != "ALL") addSport = filter else if (!showAdd) addSport = null
    }
    LaunchedEffect(Unit, filter) { reload() }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Facilities", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { filterMenu = true }) {
                        Text(if (filter == "ALL") "Filter: All" else "Filter: $filter")
                    }
                    DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                        DropdownMenuItem(text = { Text("All") }, onClick = {
                            filter = "ALL"; filterMenu = false; scope.launch { reload() }
                        })
                        sports.forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = {
                                filter = s; filterMenu = false; scope.launch { reload() }
                            })
                        }
                    }
                }
                OutlinedButton(onClick = { showAdd = !showAdd }) { Text(if (showAdd) "Close" else "Add") }
            }
        }

        if (showAdd) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Add a court", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = addName, onValueChange = { addName = it },
                        label = { Text("Court name") }, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = { sportPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(addSport ?: "Sport") }
                    OutlinedTextField(
                        value = addRate, onValueChange = { addRate = cleanDecimal(it) },
                        label = { Text("Rate/hour") }, modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val s = addSport ?: return@Button
                            scope.launch {
                                runCatching { RtdbRepo.addFacility(addName.trim(), s, addRate.toDoubleOrNull() ?: 0.0) }
                                    .onSuccess {
                                        addName = ""; addRate = ""; showAdd = false
                                        reload()
                                        snackbar.showSnackbar("Added")
                                    }
                                    .onFailure { snackbar.showSnackbar("Failed") }
                            }
                        }) { Text("Save") }
                        OutlinedButton(onClick = { showAdd = false; addName = ""; addRate = "" }) { Text("Cancel") }
                    }
                }
            }
        }

        if (list.isEmpty()) {
            Text("No facilities.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { f ->
                    FacilityRowAdmin(
                        f = f,
                        sportLabel = filter?.takeIf { it != "ALL" },
                        onEdit = { newName, newRate ->
                            scope.launch {
                                runCatching { RtdbRepo.updateFacilityNameRate(f.id, newName, newRate) }
                                    .onSuccess { reload(); snackbar.showSnackbar("Saved successfully") }
                                    .onFailure { snackbar.showSnackbar("Update failed") }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                runCatching { RtdbRepo.deleteFacility(f.id) }
                                    .onSuccess { reload(); snackbar.showSnackbar("Facility deleted") }
                                    .onFailure { snackbar.showSnackbar("Delete failed") }
                            }
                        }
                    )
                }
            }
        }

        if (sportPicker) {
            SportPickerDialog(
                title = "Choose sport",
                options = sports,
                onPick = { addSport = it; sportPicker = false },
                onDismiss = { sportPicker = false }
            )
        }
    }
}

@Composable
private fun FacilityRowAdmin(
    f: FacilityDto,
    sportLabel: String?,
    onEdit: (String, Double) -> Unit,
    onDelete: () -> Unit
) {
    var edit by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(f.name) }
    var rate by remember { mutableStateOf(f.hourlyRateMYR.toString()) }

    ElevatedCard {
        Column(Modifier.padding(12.dp)) {
            Text(f.name, fontWeight = FontWeight.SemiBold)
            val subtitle = if (sportLabel.isNullOrBlank())
                "RM ${"%.2f".format(f.hourlyRateMYR)}/h"
            else
                "$sportLabel • RM ${"%.2f".format(f.hourlyRateMYR)}/h"
            Text(subtitle)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { edit = true }) { Text("Edit") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDelete) { Text("Delete") }
            }

            if (edit) {
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = rate, onValueChange = { rate = cleanDecimal(it) }, label = { Text("Rate") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            onEdit(name.trim().ifEmpty { f.name }, rate.toDoubleOrNull() ?: f.hourlyRateMYR)
                            edit = false
                        }) { Text("Save") }
                        OutlinedButton(onClick = { edit = false; name = f.name; rate = f.hourlyRateMYR.toString() }) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

/* =============================== Vouchers =============================== */
@Composable
private fun VouchersScreen(modifier: Modifier = Modifier, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()

    var list by remember { mutableStateOf<List<VoucherDto>>(emptyList()) }

    // add panel
    var showAdd by remember { mutableStateOf(false) }
    var rmOff by remember { mutableStateOf("") }
    var costPts by remember { mutableStateOf("") }

    suspend fun refresh() { list = RtdbRepo.listVouchers() }
    LaunchedEffect(Unit) { refresh() }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Voucher list", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { showAdd = !showAdd }) { Text(if (showAdd) "Close" else "Add") }
        }

        if (showAdd) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Add voucher", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = rmOff, onValueChange = { rmOff = cleanDecimal(it) }, label = { Text("RM off") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = costPts, onValueChange = { costPts = cleanInt(it) }, label = { Text("Cost (points)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val rm = rmOff.toDoubleOrNull() ?: 0.0
                            val pts = costPts.toIntOrNull() ?: 0
                            scope.launch {
                                runCatching { RtdbRepo.addVoucher(rm, pts) }
                                    .onSuccess { rmOff = ""; costPts = ""; showAdd = false; refresh(); snackbar.showSnackbar("Added") }
                                    .onFailure { snackbar.showSnackbar("Add failed") }
                            }
                        }) { Text("Save") }
                        OutlinedButton(onClick = { showAdd = false; rmOff = ""; costPts = "" }) { Text("Cancel") }
                    }
                }
            }
        }

        if (list.isEmpty()) {
            Text("No vouchers yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { v ->
                    VoucherRow(
                        v = v,
                        onEdit = { newRm, newPts ->
                            scope.launch {
                                runCatching {
                                    RtdbRepo.deleteVoucher(v.id)
                                    RtdbRepo.addVoucher(newRm, newPts)
                                }.onSuccess {
                                    refresh()
                                    snackbar.showSnackbar("Saved successfully")
                                }.onFailure { snackbar.showSnackbar("Update failed") }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                runCatching { RtdbRepo.deleteVoucher(v.id) }
                                    .onSuccess { refresh(); snackbar.showSnackbar("Voucher deleted") }
                                    .onFailure { snackbar.showSnackbar("Delete failed") }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoucherRow(
    v: VoucherDto,
    onEdit: (Double, Int) -> Unit,
    onDelete: () -> Unit
) {
    var edit by remember { mutableStateOf(false) }
    var rmOff by remember { mutableStateOf(v.amountOffMYR.toString()) }
    var pts by remember { mutableStateOf(v.costPoints.toString()) }

    ElevatedCard {
        Column(Modifier.padding(12.dp)) {
            Text("RM ${"%.2f".format(v.amountOffMYR)} off", fontWeight = FontWeight.SemiBold)
            Text("${v.costPoints} points")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { edit = true }) { Text("Edit") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDelete) { Text("Delete") }
            }

            if (edit) {
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = rmOff, onValueChange = { rmOff = cleanDecimal(it) }, label = { Text("RM off") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = pts, onValueChange = { pts = cleanInt(it) }, label = { Text("Cost (points)") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            onEdit(rmOff.toDoubleOrNull() ?: v.amountOffMYR, pts.toIntOrNull() ?: v.costPoints)
                            edit = false
                        }) { Text("Save") }
                        OutlinedButton(onClick = {
                            edit = false
                            rmOff = v.amountOffMYR.toString()
                            pts = v.costPoints.toString()
                        }) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

/* ----------------------- Cancellations ----------------------- */
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
        FirebaseDatabase.getInstance().reference.child("bookings")
            .addValueEventListener(object : ValueEventListener {
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
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                    scope.launch {
                                        runCatching { RtdbRepo.denyCancellationRtdb(row.id) }
                                            .onSuccess { snackbar.showSnackbar("Denied") }
                                            .onFailure { snackbar.showSnackbar("Permission denied (deny)") }
                                    }
                                }) { Text("Deny") }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = {
                                    scope.launch {
                                        runCatching { RtdbRepo.approveCancellationRtdb(row.id, row.courtId, row.date, row.times) }
                                            .onSuccess { snackbar.showSnackbar("Approved") }
                                            .onFailure { snackbar.showSnackbar("Permission denied (approve)") }
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

/* ----------------------- Shared UI bits ----------------------- */
@Composable
private fun SportPickerDialog(
    title: String,
    options: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(title) },
        text = {
            if (options.isEmpty()) {
                Text("No sports yet.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(options) { s ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth().clickable { onPick(s) }
                        ) { Text(s, modifier = Modifier.padding(12.dp)) }
                    }
                }
            }
        }
    )
}

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

/* ====================== REPORTS (Day/Week/Month + Line Chart) ====================== */

private enum class Granularity { DAY, WEEK, MONTH }
private data class Point(val label: String, val xKey: String, val amount: Double)

@Composable
private fun ReportScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var granularity by remember { mutableStateOf(Granularity.WEEK) }
    var points by remember { mutableStateOf<List<Point>>(emptyList()) }
    var total by remember { mutableStateOf(0.0) }
    var loading by remember { mutableStateOf(true) }

    // Recompute on changes
    LaunchedEffect(anchor, granularity) {
        loading = true
        FirebaseDatabase.getInstance().reference.child("bookings")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val all = mutableListOf<Pair<LocalDate, Double>>()
                    for (b in s.children) {
                        val dateStr = b.child("date").getValue(String::class.java) ?: continue
                        val d = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: continue

                        val status = b.child("status").getValue(String::class.java).orEmpty()
                        val crStatus = b.child("cancellationRequest/status").getValue(String::class.java).orEmpty()
                        val cancelled = status == "cancelled" || crStatus == "approved"
                        if (cancelled) continue

                        val amount = extractAmountFrom(b)
                        if (amount <= 0.0) continue

                        all += d to amount
                    }
                    val (series, sum) = aggregate(all, anchor, granularity)
                    points = series
                    total = sum
                    loading = false
                }
                override fun onCancelled(error: DatabaseError) {
                    points = emptyList(); total = 0.0; loading = false
                }
            })
    }

    fun openDatePicker() {
        val y = anchor.year
        val m = anchor.monthValue - 1
        val d = anchor.dayOfMonth
        DatePickerDialog(context, { _, yy, mm, dd ->
            anchor = LocalDate.of(yy, mm + 1, dd)
        }, y, m, d).show()
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openDatePicker() }) { Text(anchor.toString()) }
                GranularityTabs(granularity) { granularity = it }
            }
            Text("Total: RM ${"%.2f".format(total)}", style = MaterialTheme.typography.titleMedium)
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Sales", style = MaterialTheme.typography.titleMedium)
                if (loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                } else if (points.isEmpty()) {
                    Text("No sales in this range.", modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LineChart(
                        data = points.map { it.amount },
                        labels = points.map { it.label },
                        height = 220.dp,
                        padding = 12.dp
                    )
                }
            }
        }

        if (points.isNotEmpty()) {
            ElevatedCard {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Breakdown", style = MaterialTheme.typography.titleMedium)
                    points.forEach { p ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.label)
                            Text("RM ${"%.2f".format(p.amount)}", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/* ---- helpers for Report ---- */

private fun extractAmountFrom(b: DataSnapshot): Double {
    fun DataSnapshot.asDoubleOrNull(): Double? =
        getValue(Double::class.java) ?: getValue(Long::class.java)?.toDouble() ?: getValue(String::class.java)?.toDoubleOrNull()

    return b.child("payment/amountMYR").asDoubleOrNull()
        ?: b.child("totalMYR").asDoubleOrNull()
        ?: b.child("total").asDoubleOrNull()
        ?: b.child("amount").asDoubleOrNull()
        ?: 0.0
}

private fun aggregate(
    raw: List<Pair<LocalDate, Double>>,
    anchor: LocalDate,
    g: Granularity
): Pair<List<Point>, Double> {
    val fmtDay = DateTimeFormatter.ofPattern("dd MMM")
    val fmtWeek = DateTimeFormatter.ofPattern("dd MMM")
    val fmtMonth = DateTimeFormatter.ofPattern("MMM yyyy")
    val wf = WeekFields.ISO

    fun daySeries(): List<LocalDate> =
        (0..6).map { anchor.minusDays((6 - it).toLong()) }

    fun weekSeries(): List<Pair<LocalDate, LocalDate>> {
        val start = anchor.with(wf.dayOfWeek(), 1) // Monday
        return (0..6).map { i ->
            val s = start.minusWeeks((6 - i).toLong())
            s to s.plusDays(6)
        }
    }

    fun monthSeries(): List<LocalDate> {
        val first = anchor.withDayOfMonth(1)
        return (0..6).map { first.minusMonths((6 - it).toLong()) }
    }

    return when (g) {
        Granularity.DAY -> {
            val buckets = daySeries()
            val sums = buckets.associateWith { d -> raw.filter { it.first == d }.sumOf { it.second } }
            val pts = buckets.map { d -> Point(d.format(fmtDay), d.toString(), sums[d] ?: 0.0) }
            pts to pts.sumOf { it.amount }
        }
        Granularity.WEEK -> {
            val ranges = weekSeries()
            val pts = ranges.map { (s, e) ->
                val sum = raw.filter { it.first in s..e }.sumOf { it.second }
                Point("${s.format(fmtWeek)}–${e.format(fmtWeek)}", "${s}_$e", sum)
            }
            pts to pts.sumOf { it.amount }
        }
        Granularity.MONTH -> {
            val months = monthSeries()
            val pts = months.map { m ->
                val end = m.plusMonths(1).minusDays(1)
                val sum = raw.filter { it.first in m..end }.sumOf { it.second }
                Point(m.format(fmtMonth), m.toString(), sum)
            }
            pts to pts.sumOf { it.amount }
        }
    }
}

@Composable
private fun GranularityTabs(current: Granularity, onChange: (Granularity) -> Unit) {
    val items = listOf(Granularity.DAY to "Day", Granularity.WEEK to "Week", Granularity.MONTH to "Month")
    var selectedIndex by remember { mutableStateOf(items.indexOfFirst { it.first == current }.coerceAtLeast(0)) }
    LaunchedEffect(current) { selectedIndex = items.indexOfFirst { it.first == current }.coerceAtLeast(0) }

    TabRow(selectedTabIndex = selectedIndex) {
        items.forEachIndexed { i, pair ->
            Tab(
                selected = selectedIndex == i,
                onClick = {
                    selectedIndex = i
                    onChange(pair.first)
                },
                text = { Text(pair.second) }
            )
        }
    }
}

/* ===== Fixed LineChart: capture theme colors outside Canvas (no @Composable reads inside) ===== */
@Composable
private fun LineChart(
    data: List<Double>,
    labels: List<String>,
    height: Dp,
    padding: Dp
) {
    val maxY = max(1.0, data.maxOrNull() ?: 1.0)
    val minY = 0.0
    val steps = 4
    val yTicks = (0..steps).map { i -> (minY + (maxY - minY) * i / steps) }

    // capture colors in composable scope
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val axisColor = onSurface.copy(alpha = 0.2f)
    val gridColor = onSurface.copy(alpha = 0.08f)
    val bgColor = surfaceVariant.copy(alpha = 0.25f)

    Column {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(56.dp), verticalArrangement = Arrangement.SpaceBetween) {
                yTicks.reversed().forEach {
                    Text("RM ${"%.0f".format(it)}", style = MaterialTheme.typography.labelSmall)
                }
            }
            Canvas(
                modifier = Modifier
                    .height(height)
                    .fillMaxWidth()
                    .background(bgColor)
            ) {
                val w = size.width
                val h = size.height
                val leftPad = 0f
                val rightPad = 0f
                val topPad = 8f
                val bottomPad = 24f

                // X axis
                drawLine(
                    color = axisColor,
                    start = Offset(leftPad, h - bottomPad),
                    end = Offset(w - rightPad, h - bottomPad),
                    strokeWidth = 2f
                )
                // Grid
                yTicks.forEach { v ->
                    val y = (h - bottomPad) - ((v - minY) / (maxY - minY) * (h - topPad - bottomPad)).toFloat()
                    drawLine(
                        color = gridColor,
                        start = Offset(leftPad, y),
                        end = Offset(w - rightPad, y),
                        strokeWidth = 1f
                    )
                }
                // Line + points
                if (data.isNotEmpty()) {
                    val stepX = if (data.size == 1) 0f else (w - leftPad - rightPad) / (data.size - 1)
                    val path = Path()
                    data.forEachIndexed { i, v ->
                        val x = leftPad + stepX * i
                        val y = (h - bottomPad) - ((v - minY) / (maxY - minY) * (h - topPad - bottomPad)).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = primary,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                    )
                    data.forEachIndexed { i, v ->
                        val x = leftPad + stepX * i
                        val y = (h - bottomPad) - ((v - minY) / (maxY - minY) * (h - topPad - bottomPad)).toFloat()
                        drawCircle(
                            color = primary,
                            radius = 5f,
                            center = Offset(x, y)
                        )
                    }
                }
            }
        }
        // X labels
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 56.dp, top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val showEvery = max(1, (labels.size / 6.0).roundToInt())
            labels.forEachIndexed { i, s ->
                if (i % showEvery == 0 || i == labels.lastIndex) {
                    Text(s, style = MaterialTheme.typography.labelSmall)
                } else {
                    Spacer(Modifier.width(0.dp))
                }
            }
        }
    }
}
