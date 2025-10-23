package com.speedsport.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onFindCourts: () -> Unit,
    onMyBookings: () -> Unit,
    onWaitlist: () -> Unit,
    onProfile: () -> Unit,
    onSchedule: () -> Unit,

    // NEW: hook up navigation for these
    onPoints: () -> Unit,
    onVoucher: () -> Unit,

    // Optional: tap on the purple “POINTS” banner
    onTapPointsBanner: () -> Unit = onPoints
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Malaysia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Rounded.ExpandMore, contentDescription = null)
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))
        BannerPlaceholder(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
        )

        Spacer(Modifier.height(12.dp))
        // Light purple “POINTS” banner — make it clickable
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTapPointsBanner() },
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("POINTS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                // The right-side live points number is already bound elsewhere on your screen;
                // you can slot it in here if you have a state.
                Text("586 pts", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Hi there!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(" ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { }) { Icon(Icons.Rounded.Notifications, null) }
        }

        Spacer(Modifier.height(16.dp))
        AccountWidget(
            onPoints = onPoints,
            onVoucher = onVoucher,
            onWaitingList = onWaitlist,
            onSchedule = onSchedule
        )

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun BannerPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
                )
            )
        )
    ) {
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(4) { idx ->
                val active = idx == 1
                Box(
                    Modifier
                        .size(if (active) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (active) Color.White else Color.White.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@Composable
private fun AccountWidget(
    onPoints: () -> Unit,
    onVoucher: () -> Unit,
    onWaitingList: () -> Unit,
    onSchedule: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            // 2x2 grid — each tile is clickable
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniTile("Points", "586", { Icon(Icons.Rounded.Star, null) }, onPoints, Modifier.weight(1f))
                MiniTile("Voucher", "Shop & mine", { Icon(Icons.Rounded.LocalOffer, null) }, onVoucher, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniTile("Waiting", "—", { Icon(Icons.Rounded.Timer, null) }, onWaitingList, Modifier.weight(1f))
                MiniTile("Schedule", "My bookings", { Icon(Icons.Rounded.CalendarMonth, null) }, onSchedule, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniTile(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
