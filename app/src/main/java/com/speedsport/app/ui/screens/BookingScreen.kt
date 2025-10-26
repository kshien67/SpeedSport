@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)


package com.speedsport.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.database.*
import com.speedsport.app.domain.BookingDraft
import com.speedsport.app.domain.EquipmentItem as DraftEquip
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.Instant
import java.time.LocalDate as JLocalDate
import java.time.LocalTime
import java.time.ZoneId
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

/* ----- RTDB URL: must match your Firebase console ----- */
private const val DB_URL =
    "https://speedsport-edf02-default-rtdb.asia-southeast1.firebasedatabase.app"

/**
 * Booking screen that supports:
 *  - Tap start, then tap end (multiple contiguous hours)
 *  - If user taps only once, it's a 1-hour booking
 *  - Equipment selection
 *  - Back arrow (when onBack != null)
 *  - Join waitlist for already-booked slots
 */
@Composable
fun BookingScreen(
    onProceed: (BookingDraft) -> Unit,
    onBack: (() -> Unit)? = null
) {
    // ------------ State ------------
    var selectedDate by remember { mutableStateOf(todayKx()) }
    var selectedSport by rememberSaveable { mutableStateOf("") }
    var selectedCourt by rememberSaveable { mutableStateOf("") }
    var rangeStart by rememberSaveable { mutableStateOf<String?>(null) }
    var rangeEnd by rememberSaveable { mutableStateOf<String?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ------------ Facilities ------------
    val facilities by rememberFacilities()
    val sports = remember(facilities) {
        facilities.map { it.type.trim() }
            .distinctBy { it.lowercase() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }
    val courtsForSport = remember(facilities, selectedSport) {
        if (selectedSport.isBlank()) emptyList() else facilities.filter {
            it.type.equals(selectedSport, ignoreCase = true)
        }
    }
    val courtIds = courtsForSport.map { it.id }

    // ------------ Equipment ------------
    val equipmentState = rememberEquipmentForSport(selectedSport)
    val selectedEquip = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(selectedSport) { selectedEquip.clear() }

    // ------------ Time / bookings ------------
    val allSlots = remember { (8 until 22).map { "%02d:00".format(it) } } // 08:00..21:00 starts
    val dateKey = remember(selectedDate) { selectedDate.asKey() }
    val takenStarts = rememberTakenStarts(selectedCourt, dateKey)

    val isToday = remember(selectedDate) { selectedDate == todayKx() }
    val nowLocal = remember(isToday) { if (isToday) LocalTime.now() else null }

    LaunchedEffect(selectedSport) { selectedCourt = ""; rangeStart = null; rangeEnd = null }
    LaunchedEffect(selectedCourt, selectedDate) { rangeStart = null; rangeEnd = null }

    // ------------ Date Picker (block past) ------------
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = kxLocalDateToMillis(selectedDate),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val pick = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                val today = java.time.LocalDate.now()
                return !pick.isBefore(today)
            }
        }
    )

    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text("Book a Facility") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Date
            item {
                Text("Date: $dateKey", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showDatePicker = true }) { Text("Change date") }
            }

            // Sport
            item {
                Text("Sport", style = MaterialTheme.typography.titleMedium)
                SimpleDropdownField(
                    value = selectedSport,
                    label = if (sports.isEmpty()) "No sports in database" else "Choose sport",
                    options = sports,
                    onSelected = { selectedSport = it },
                    enabled = sports.isNotEmpty()
                )
            }

            // Court
            item {
                Text("Court", style = MaterialTheme.typography.titleMedium)
                SimpleDropdownField(
                    value = selectedCourt,
                    label = when {
                        sports.isEmpty() -> "No sports in database"
                        selectedSport.isBlank() -> "Choose sport first"
                        courtIds.isEmpty() -> "No courts for $selectedSport"
                        else -> "Choose court"
                    },
                    options = courtIds,
                    onSelected = { selectedCourt = it },
                    enabled = courtIds.isNotEmpty()
                )
            }

            // Time range (tap start then end; single tap = 1h on proceed)
            item {
                Text("Time", style = MaterialTheme.typography.titleMedium)
                TimeLegend()

                val bookedColors = FilterChipDefaults.filterChipColors(
                    disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allSlots) { slot ->
                        val isBooked = slot in takenStarts
                        val isPast = isToday && nowLocal?.let { slotIsPast(slot, it) } == true
                        val enabled = !isBooked && !isPast
                        val inSelectedRange = inRange(slot, rangeStart, rangeEnd)

                        FilterChip(
                            selected = inSelectedRange,
                            onClick = {
                                if (enabled) {
                                    handleRangeClick(
                                        clicked = slot,
                                        start = rangeStart,
                                        end = rangeEnd
                                    ) { s, e -> rangeStart = s; rangeEnd = e }
                                }
                            },
                            enabled = enabled,
                            label = { Text(slot) },
                            colors = if (isBooked) bookedColors else FilterChipDefaults.filterChipColors()
                        )
                    }
                }

                // NEW: Booked slots → allow Join Waitlist
                val bookedSlots = remember(takenStarts) { allSlots.filter { it in takenStarts } }
                if (selectedCourt.isNotBlank() && bookedSlots.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Booked slots (join waitlist):", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bookedSlots.forEach { st ->
                            val slotKey = "$st-${plusOneHour(st)}"
                            AssistChip(
                                onClick = {
                                    val courtName = facilities.firstOrNull { it.id == selectedCourt }?.name ?: selectedCourt
                                    joinWaitlist(
                                        sport = selectedSport.ifBlank { "Unknown" },
                                        courtId = selectedCourt,
                                        courtName = courtName,
                                        dateKey = dateKey,
                                        slotKey = slotKey
                                    ) { ok ->
                                        scope.launch {
                                            snackbar.showSnackbar(
                                                if (ok) "Joined waitlist for $slotKey"
                                                else "Failed to join waitlist"
                                            )
                                        }
                                    }
                                },
                                label = { Text(slotKey) }
                            )
                        }
                    }
                }
            }

            // Equipment
            item {
                Text("Equipment", style = MaterialTheme.typography.titleMedium)

                if (selectedSport.isBlank()) {
                    Text("Choose a sport first", style = MaterialTheme.typography.bodyMedium)
                } else {
                    val equipment = equipmentState.value
                    when {
                        equipment == null -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Text("Loading $selectedSport equipment…")
                            }
                        }
                        equipment.isEmpty() -> {
                            Text("No equipment found for $selectedSport")
                        }
                        else -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                equipment.forEach { item ->
                                    EquipmentRow(
                                        item = item,
                                        qty = selectedEquip[item.key] ?: 0,
                                        onInc = {
                                            val cur = selectedEquip[item.key] ?: 0
                                            if (item.stock <= 0 || cur < item.stock) {
                                                selectedEquip[item.key] = cur + 1
                                            }
                                        },
                                        onDec = {
                                            val cur = selectedEquip[item.key] ?: 0
                                            if (cur > 0) selectedEquip[item.key] = cur - 1
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Proceed to checkout
            item {
                Button(
                    onClick = {
                        when {
                            selectedSport.isBlank() ->
                                scope.launch { snackbar.showSnackbar("Choose a sport") }
                            selectedCourt.isBlank() ->
                                scope.launch { snackbar.showSnackbar("Choose a court") }
                            rangeStart == null ->
                                scope.launch { snackbar.showSnackbar("Choose a time") }
                            else -> scope.launch {
                                val from = rangeStart!!
                                val to   = rangeEnd ?: rangeStart!!   // single hour if end not chosen

                                val hours = hoursInRange(from, to)
                                val hasBooked = hours.any { it in takenStarts }
                                val hasPast = isToday && nowLocal?.let { h -> hours.any { slotIsPast(it, h) } } == true

                                if (hasBooked) { snackbar.showSnackbar("Selected range includes booked slot(s)."); return@launch }
                                if (hasPast)   { snackbar.showSnackbar("Selected range includes past time(s).");   return@launch }

                                val court = courtsForSport.firstOrNull { it.id == selectedCourt }
                                val ratePerHour = court?.ratePerHour ?: 0.0
                                val courtName = court?.name ?: selectedCourt

                                // Build draft equipment list from selected quantities
                                val equipDraft: List<DraftEquip> =
                                    (equipmentState.value ?: emptyList()).mapNotNull {
                                        val qty = selectedEquip[it.key] ?: 0
                                        if (qty > 0) DraftEquip(id = it.key, name = it.label, rate = it.rentalRate, qty = qty) else null
                                    }

                                val draft = BookingDraft(
                                    dateIso = dateKey,
                                    sport = selectedSport,
                                    courtId = selectedCourt,
                                    courtName = courtName,
                                    courtRatePerHour = ratePerHour,
                                    selectedTimes = hours,
                                    equipment = equipDraft
                                )

                                onProceed(draft)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Proceed to checkout") }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = millisToKxLocalDate(it)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/* ========================= Helpers & models ========================= */

private data class FacilityRow(
    val id: String,
    val type: String,
    val name: String,
    val ratePerHour: Double = 0.0
)

private data class EquipmentUiItem(
    val key: String,
    val label: String,
    val stock: Int,
    val rentalRate: Double
)

@Composable
private fun rememberFacilities(): State<List<FacilityRow>> {
    var list by remember { mutableStateOf(emptyList<FacilityRow>()) }

    DisposableEffect(Unit) {
        val db = FirebaseDatabase.getInstance(DB_URL)
        val ref = db.reference.child("facilities")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val acc = mutableListOf<FacilityRow>()
                for (child in snapshot.children) {
                    val id = child.key ?: continue
                    val typeFromDb = child.child("type").getValue(String::class.java)
                    val nameFromDb = child.child("name").getValue(String::class.java)
                    val rate = (child.child("ratePerHour").value as? Number)?.toDouble()
                        ?: (child.child("ratePerHour").getValue(String::class.java)?.toDoubleOrNull() ?: 0.0)

                    val type = typeFromDb ?: id.extractSportType() ?: continue
                    val name = nameFromDb ?: id
                    acc += FacilityRow(id = id, type = type, name = name, ratePerHour = rate)
                }
                list = acc
            }
            override fun onCancelled(error: DatabaseError) { list = emptyList() }
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }
    return remember { derivedStateOf { list } }
}

private val sportToEquipmentKeys: Map<String, Set<String>> = mapOf(
    "badminton" to setOf("racquet", "shuttle", "shuttlecock"),
    "pickleball" to setOf("paddle")
)

private val sportLabelTokens: Map<String, List<String>> = mapOf(
    "badminton" to listOf("racquet", "racket", "shuttle", "shuttlecock"),
    "pickleball" to listOf("paddle", "ball")
)

@Composable
private fun rememberEquipmentForSport(selectedSport: String): MutableState<List<EquipmentUiItem>?> {
    val state = remember(selectedSport) { mutableStateOf<List<EquipmentUiItem>?>(null) }

    DisposableEffect(selectedSport) {
        if (selectedSport.isBlank()) {
            state.value = emptyList()
            return@DisposableEffect onDispose { }
        }

        state.value = null // loading
        val sportKey = selectedSport.lowercase()
        val allowedKeys = sportToEquipmentKeys[sportKey].orEmpty().map { it.lowercase() }
        val tokens = sportLabelTokens[sportKey].orEmpty().map { it.lowercase() }

        val db = FirebaseDatabase.getInstance(DB_URL)
        val ref = db.reference.child("equipment")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val acc = mutableListOf<EquipmentUiItem>()
                for (child in snapshot.children) {
                    val key = child.key?.lowercase() ?: continue

                    val rawLabel = child.child("label").getValue(String::class.java) ?: key
                    val labelLower = rawLabel.lowercase()
                    val rentalRate = (child.child("rentalRate").value as? Number)?.toDouble()
                        ?: (child.child("rentalRate").getValue(String::class.java)?.toDoubleOrNull() ?: 0.0)
                    val stock = (child.child("stock").value as? Number)?.toInt() ?: 0

                    val byKey = allowedKeys.isNotEmpty() && key in allowedKeys
                    val byTokens = tokens.any { t -> labelLower.contains(t) }

                    if (byKey || byTokens) {
                        acc += EquipmentUiItem(
                            key = key,
                            label = rawLabel,
                            stock = stock,
                            rentalRate = rentalRate
                        )
                    }
                }
                state.value = acc
            }
            override fun onCancelled(error: DatabaseError) { state.value = emptyList() }
        }

        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    return state
}

@Composable
private fun SimpleDropdownField(
    value: String,
    label: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelected(opt); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun EquipmentRow(
    item: EquipmentUiItem,
    qty: Int,
    onInc: () -> Unit,
    onDec: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(item.label, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Rate: ${item.rentalRate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.stock > 0) {
                    Text("Stock: ${item.stock}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onDec, enabled = qty > 0) { Text("-") }
            Text(qty.toString(), modifier = Modifier.padding(horizontal = 12.dp))
            OutlinedButton(
                onClick = onInc,
                enabled = (item.stock <= 0) || (qty < item.stock)
            ) { Text("+") }
        }
    }
}

/** Legend for time colors */
@Composable
private fun TimeLegend() {
    val blue = MaterialTheme.colorScheme.primaryContainer
    val gray = MaterialTheme.colorScheme.surfaceVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        LegendBox(color = gray, label = "Unavailable")
        LegendBox(color = blue, label = "Booked")
    }
}
@Composable
private fun LegendBox(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(14.dp)
                .background(color, shape = MaterialTheme.shapes.small)
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/** Today as kotlinx LocalDate */
private fun todayKx(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

/** "yyyy-MM-dd" */
private fun LocalDate.asKey(): String =
    "%04d-%02d-%02d".format(year, monthNumber, dayOfMonth)

/** Taken starts from /facilityBookings/{facilityId}/{date}/{HH:MM-HH:MM} */
@Composable
private fun rememberTakenStarts(facilityId: String, dateKey: String): Set<String> {
    var starts by remember(facilityId, dateKey) { mutableStateOf(emptySet<String>()) }

    DisposableEffect(facilityId, dateKey) {
        if (facilityId.isBlank() || dateKey.isBlank()) {
            starts = emptySet()
            return@DisposableEffect onDispose { }
        }
        val db = FirebaseDatabase.getInstance(DB_URL)
        val ref = db.reference.child("facilityBookings").child(facilityId).child(dateKey)

        val listener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                starts = s.children.mapNotNull { it.key?.substringBefore('-') }.toSet()
            }
            override fun onCancelled(error: DatabaseError) { /* no-op */ }
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }
    return starts
}

/** Range helpers */
private fun inRange(slot: String, start: String?, end: String?): Boolean {
    if (start == null) return false
    val s = hmToMinutes(start)
    val t = hmToMinutes(slot)
    val e = if (end == null) s else hmToMinutes(end)
    return t in s..e
}

/** Tap start → (optional) end. Third tap restarts at clicked. */
private fun handleRangeClick(
    clicked: String,
    start: String?,
    end: String?,
    onUpdate: (String?, String?) -> Unit
) {
    when {
        start == null -> onUpdate(clicked, null)
        end == null -> {
            if (hmToMinutes(clicked) < hmToMinutes(start)) onUpdate(clicked, null)
            else onUpdate(start, clicked)
        }
        else -> onUpdate(clicked, null)
    }
}

private fun hoursInRange(start: String, endInclusive: String): List<String> {
    val s = hmToMinutes(start)
    val e = hmToMinutes(endInclusive)
    return (s..e step 60).map { minutesToHm(it) }
}

private fun hmToMinutes(hm: String): Int {
    val (h, m) = hm.split(":").map { it.toInt() }
    return h * 60 + m
}

private fun minutesToHm(min: Int): String =
    "%02d:%02d".format(min / 60, min % 60)

private fun plusOneHour(hm: String): String = minutesToHm(hmToMinutes(hm) + 60)

private fun slotIsPast(slot: String, now: LocalTime): Boolean {
    val (h, m) = slot.split(":").map { it.toInt() }
    val slotTime = LocalTime.of(h, m)
    return !slotTime.isAfter(now)
}

/* ----- Date conversions between java.time and kotlinx ----- */
private fun millisToKxLocalDate(ms: Long): LocalDate {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    return LocalDate(d.year, d.monthValue, d.dayOfMonth)
}
private fun kxLocalDateToMillis(d: LocalDate): Long {
    val j = JLocalDate.of(d.year, d.monthNumber, d.dayOfMonth)
    return j.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/* ----- Small helpers ----- */
private fun String.extractSportType(): String? {
    val lettersOnly = this.takeWhile { it.isLetter() }
    if (lettersOnly.isEmpty()) return null
    return lettersOnly.replaceFirstChar { it.uppercase() }
}

/* ----- Waitlist write (client-only MVP) ----- */
private fun joinWaitlist(
    sport: String,
    courtId: String,
    courtName: String,
    dateKey: String,
    slotKey: String,
    onResult: (Boolean) -> Unit
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return onResult(false)
    val db = FirebaseDatabase.getInstance(DB_URL).reference

    val reqId = db.child("facilityWaitlist").child(courtId).child(dateKey).child(slotKey).push().key
        ?: return onResult(false)

    val data = mapOf(
        "userId" to uid,
        "joinedAt" to ServerValue.TIMESTAMP,
        "status" to "queued",
        "sport" to sport,
        "courtName" to courtName
    )

    val updates = hashMapOf<String, Any>(
        "/facilityWaitlist/$courtId/$dateKey/$slotKey/$reqId" to data,
        "/userWaitlist/$uid/$reqId" to true
    )

    db.updateChildren(updates)
        .addOnSuccessListener { onResult(true) }
        .addOnFailureListener { onResult(false) }
}
