@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.speedsport.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.delay

private const val DB_URL =
    "https://speedsport-edf02-default-rtdb.asia-southeast1.firebasedatabase.app"

@Composable
fun HomeScreen(
    onFindCourts: () -> Unit,
    onMyBookings: () -> Unit,
    onWaitlist: () -> Unit,
    onProfile: () -> Unit,
    onSchedule: () -> Unit,
    onPoints: () -> Unit,
    onVoucher: () -> Unit,
    onTapPointsBanner: () -> Unit = onPoints,
    bannerImages: List<Int> = listOf(
        com.speedsport.app.R.drawable.sportfact_1,
        com.speedsport.app.R.drawable.sportfact_2,
        com.speedsport.app.R.drawable.sportfact_3
    )
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var points by remember { mutableStateOf(0) }
    var username by remember { mutableStateOf("") }

    // Live points
    DisposableEffect(uid) {
        if (uid == null) return@DisposableEffect onDispose {}
        val ref = FirebaseDatabase.getInstance(DB_URL).reference
            .child("users").child(uid).child("points")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { points = (s.value as? Number)?.toInt() ?: 0 }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(l)
        onDispose { ref.removeEventListener(l) }
    }

    // Live username
    DisposableEffect(uid) {
        if (uid == null) return@DisposableEffect onDispose {}
        val ref = FirebaseDatabase.getInstance(DB_URL).reference
            .child("users").child(uid).child("name")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val raw = s.getValue(String::class.java) ?: ""
                username = raw.trim()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(l)
        onDispose { ref.removeEventListener(l) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Carousel banner (auto-slides every 5s, swipeable)
        BannerCarousel(
            images = bannerImages,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
        )

        Spacer(Modifier.height(12.dp))

        // POINTS banner (tappable)
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
                Text("$points pts", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Greeting row – avatar icon + welcome text
        ListItem(
            leadingContent = {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = "Profile")
                }
            },
            headlineContent = {
                val firstName = remember(username) {
                    username.split(" ").firstOrNull().orEmpty()
                }
                val greet = if (firstName.isNotBlank()) "Welcome back, $firstName"
                else "Welcome back"
                Text(greet, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            },
            trailingContent = {
                IconButton(onClick = { /* notifications */ }) {
                    Icon(Icons.Rounded.Notifications, contentDescription = null)
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        AccountWidget(
            points = points,
            onPoints = onPoints,
            onVoucher = onVoucher,
            onWaitingList = onWaitlist,
            onSchedule = onSchedule
        )

        Spacer(Modifier.height(20.dp))
    }
}

/* ───────────── Banner Carousel ───────────── */

@Composable
private fun BannerCarousel(
    images: List<Int>,
    modifier: Modifier = Modifier,
    autoSlideMillis: Long = 5_000L
) {
    val pageCount = images.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })

    // Auto-slide every 5 seconds
    LaunchedEffect(pageCount) {
        if (pageCount <= 1) return@LaunchedEffect
        while (true) {
            delay(autoSlideMillis)
            val next = (pagerState.currentPage + 1) % pageCount
            pagerState.animateScrollToPage(next)
        }
    }

    Box(modifier) {
        HorizontalPager(state = pagerState, modifier = Modifier.matchParentSize()) { page ->
            Image(
                painter = painterResource(id = images[page]),
                contentDescription = "Banner $page",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Dots indicator
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(pageCount) { idx ->
                val active = idx == pagerState.currentPage
                Box(
                    Modifier
                        .size(if (active) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        )
                )
            }
        }
    }
}

/* ───────────── Account Grid ───────────── */

@Composable
private fun AccountWidget(
    points: Int,
    onPoints: () -> Unit,
    onVoucher: () -> Unit,
    onWaitingList: () -> Unit,
    onSchedule: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniTile("Points", "$points", { Icon(Icons.Rounded.Star, null) }, onPoints, Modifier.weight(1f))
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
