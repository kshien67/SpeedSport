@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.speedsport.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import kotlin.math.*

/* ====================== REPORTS ====================== */

private enum class Granularity { DAY, WEEK, MONTH }
private data class Point(val label: String, val xKey: String, val amount: Double)

/** UI order: Date → Daily/Weekly/Monthly → Total & (conditional) Chart */
@Composable
fun ReportScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var granularity by remember { mutableStateOf(Granularity.WEEK) }
    var points by remember { mutableStateOf<List<Point>>(emptyList()) }
    var total by remember { mutableStateOf(0.0) }
    var loading by remember { mutableStateOf(true) }

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
        val y = anchor.year; val m = anchor.monthValue - 1; val d = anchor.dayOfMonth
        DatePickerDialog(context, { _, yy, mm, dd -> anchor = LocalDate.of(yy, mm + 1, dd) }, y, m, d).show()
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Title changed here
        Text("Report", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // 1) Date first
                OutlinedButton(onClick = { openDatePicker() }, modifier = Modifier.fillMaxWidth()) {
                    Text(anchor.toString())
                }

                // 2) Daily / Weekly / Monthly
                PeriodPills(current = granularity, onChange = { granularity = it })

                // 3) Total & (maybe) chart
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Total Sales", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Surface(shape = MaterialTheme.shapes.small, tonalElevation = 1.dp) {
                        Text("RM ${"%.2f".format(total)}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }

                when {
                    loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    // Hide chart if there’s no data OR all values are zero
                    points.isEmpty() || points.all { it.amount <= 0.0 } -> {
                        Text("No sales in this range.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        ScrollableLineChart(
                            data = points.map { it.amount },
                            labels = points.map { it.label },
                            height = 240.dp,
                            minPointSpacing = 64.dp
                        )
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
    val fmtDay = DateTimeFormatter.ofPattern("EEE d MMM")
    val fmtWeek = DateTimeFormatter.ofPattern("d MMM")
    val fmtMonth = DateTimeFormatter.ofPattern("MMM yyyy")
    val wf = WeekFields.ISO

    fun daySeries(): List<LocalDate> = (0..6).map { anchor.minusDays((6 - it).toLong()) }
    fun weekSeries(): List<Pair<LocalDate, LocalDate>> {
        val start = anchor.with(wf.dayOfWeek(), 1)
        return (0..6).map { i -> val s = start.minusWeeks((6 - i).toLong()); s to s.plusDays(6) }
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

/* ===== Pills ===== */
@Composable
private fun PeriodPills(current: Granularity, onChange: (Granularity) -> Unit) {
    val items = listOf(Granularity.DAY to "Daily", Granularity.WEEK to "Weekly", Granularity.MONTH to "Monthly")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (g, label) ->
            val selected = current == g
            if (selected) Button(onClick = {}, shape = MaterialTheme.shapes.large) { Text(label) }
            else OutlinedButton(onClick = { onChange(g) }, shape = MaterialTheme.shapes.large) { Text(label) }
        }
    }
}

/* ================= Scrollable Line Chart with “nice” Y-axis ticks ================= */

@Composable
private fun ScrollableLineChart(
    data: List<Double>,
    labels: List<String>,
    height: Dp,
    minPointSpacing: Dp = 64.dp,
) {
    val scroll = rememberScrollState()
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val axisColor = onSurface.copy(alpha = 0.16f)
    val gridColor = onSurface.copy(alpha = 0.08f)
    val bgColor = surfaceVariant.copy(alpha = 0.20f)

    val rawMax = max(1.0, data.maxOrNull() ?: 1.0)
    val niceMax = niceCeil(rawMax)
    val steps = 4
    val tickStep = niceMax / steps
    val yTicks = (0..steps).map { it * tickStep }

    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    var selected by remember { mutableStateOf<Int?>(null) }

    val computedWidth = if (data.size <= 1) 320.dp else ((data.size - 1) * minPointSpacing.value).dp + 1.dp
    val chartWidthDp = maxOf(320.dp, computedWidth)

    Column {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(56.dp), verticalArrangement = Arrangement.SpaceBetween) {
                yTicks.reversed().forEach { tick ->
                    Text("RM ${"%.0f".format(tick)}", style = MaterialTheme.typography.labelSmall)
                }
            }

            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .horizontalScroll(scroll)
                        .background(bgColor)
                        .height(height)
                        .width(chartWidthDp)
                        .onSizeChanged { size -> chartSize = size }
                        .pointerInput(data) {
                            detectTapGestures(onTap = { o ->
                                selected = nearestIndex(o.x, chartSize.width, data.size)
                            })
                        }
                        .pointerInput(data) {
                            detectDragGestures(onDrag = { change, _ ->
                                selected = nearestIndex(change.position.x, chartSize.width, data.size)
                            })
                        }
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val topPad = 8f
                        val bottomPad = 28f

                        drawLine(color = axisColor, start = Offset(0f, h - bottomPad), end = Offset(w, h - bottomPad), strokeWidth = 2f)
                        yTicks.forEach { v ->
                            val y = (h - bottomPad) - ((v / niceMax) * (h - topPad - bottomPad)).toFloat()
                            drawLine(color = gridColor, start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1f)
                        }

                        if (data.isNotEmpty()) {
                            val stepX = if (data.size == 1) 0f else w / (data.size - 1)
                            val path = Path()
                            data.forEachIndexed { i, v ->
                                val x = stepX * i
                                val y = (h - bottomPad) - ((v / niceMax) * (h - topPad - bottomPad)).toFloat()
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path = path, color = primary, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
                            data.forEachIndexed { i, v ->
                                val x = stepX * i
                                val y = (h - bottomPad) - ((v / niceMax) * (h - topPad - bottomPad)).toFloat()
                                drawCircle(color = primary, radius = 5f, center = Offset(x, y))
                            }

                            selected?.let { idx ->
                                val x = stepX * idx
                                val y = (h - bottomPad) - ((data[idx] / niceMax) * (h - topPad - bottomPad)).toFloat()
                                drawLine(color = axisColor, start = Offset(x, topPad), end = Offset(x, h - bottomPad), strokeWidth = 2f)
                                drawCircle(color = primary, radius = 7f, center = Offset(x, y))
                            }
                        }
                    }
                }

                Row(
                    Modifier
                        .horizontalScroll(scroll)
                        .padding(top = 6.dp)
                        .width(chartWidthDp),
                    horizontalArrangement = Arrangement.spacedBy(minPointSpacing)
                ) {
                    labels.forEach { s -> Text(s, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

/* —— utilities —— */

private fun nearestIndex(x: Float, width: Int, count: Int): Int {
    if (count <= 1 || width <= 0) return 0
    val step = width.toFloat() / (count - 1)
    val raw = (x / step).roundToInt()
    return min(count - 1, max(0, raw))
}

private fun niceCeil(v: Double): Double {
    val exp = floor(log10(max(1e-9, v)))
    val f = v / 10.0.pow(exp)
    val nice = when {
        f <= 1.0 -> 1.0
        f <= 2.0 -> 2.0
        f <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * 10.0.pow(exp)
}
