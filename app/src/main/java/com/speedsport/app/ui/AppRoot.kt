package com.speedsport.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.speedsport.app.R
import com.speedsport.app.domain.BookingDraft
import com.speedsport.app.ui.screens.AdminScreen
import com.speedsport.app.ui.screens.BookingScreen
import com.speedsport.app.ui.screens.CheckoutScreen
import com.speedsport.app.ui.screens.EditProfileScreen
import com.speedsport.app.ui.screens.HomeScreen
import com.speedsport.app.ui.screens.LoginScreen
import com.speedsport.app.ui.screens.PointsScreen
import com.speedsport.app.ui.screens.ProfileHomeScreen
import com.speedsport.app.ui.screens.RegisterScreen
import com.speedsport.app.ui.screens.ScheduleScreen
import com.speedsport.app.ui.screens.VoucherScreen
import com.speedsport.app.ui.screens.WaitlistScreen
import com.speedsport.app.ui.theme.AppIcons
import com.speedsport.app.ui.theme.SpeedsportTheme
import com.speedsport.app.ui.theme.rememberUserDarkModeSetting
import com.speedsport.app.vm.CheckoutViewModel

// Bottom bar destinations — Profile replaces Rent
sealed class Dest(val route: String, val label: String, val icon: @Composable () -> Unit) {
    data object Home    : Dest("home",    "Home",    { AppIcons.Home() })
    data object Book    : Dest("book",    "Book",    { AppIcons.Book() })
    data object Profile : Dest("profile", "Profile", { AppIcons.Profile() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val dark by rememberUserDarkModeSetting()

    SpeedsportTheme(darkTheme = dark) {
        val nav = rememberNavController()
        val tabs = listOf(Dest.Home, Dest.Book, Dest.Profile)
        val entry by nav.currentBackStackEntryAsState()
        val currentRoute = entry?.destination?.route

        val isAuthScreen = currentRoute in setOf("login", "register")
        val isTabRoute = currentRoute in setOf(Dest.Home.route, Dest.Book.route, Dest.Profile.route)

        val checkoutVm: CheckoutViewModel = viewModel()

        Scaffold(
            topBar = {
                if (isTabRoute) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.speedsport_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp).padding(end = 8.dp)
                                )
                                Text(text = "SpeedSport — Malaysia")
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!isAuthScreen && isTabRoute) {
                    NavigationBar {
                        val current = entry?.destination
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = current?.hierarchy?.any { it.route == t.route } == true,
                                onClick = {
                                    nav.navigate(t.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { t.icon() },
                                label = { Text(t.label) }
                            )
                        }
                    }
                }
            }
        ) { inner ->
            NavHost(
                navController = nav,
                startDestination = "login",
                modifier = Modifier.padding(inner)
            ) {
                // --- Auth ---
                composable("login") {
                    LoginScreen(
                        onLoggedIn = {
                            nav.navigate(Dest.Home.route) {
                                popUpTo("login") { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onGoRegister = { nav.navigate("register") },
                        onLoggedInAdmin = {
                            nav.navigate("admin") {
                                popUpTo("login") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable("register") {
                    RegisterScreen(
                        onRegistered = {
                            nav.navigate(Dest.Home.route) {
                                popUpTo("login") { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onGoLogin = { nav.popBackStack() }
                    )
                }

                // --- Main tabs ---
                composable(Dest.Home.route) {
                    HomeScreen(
                        onFindCourts = { nav.navigate(Dest.Book.route) },
                        onMyBookings = { nav.navigate("schedule") },
                        onWaitlist   = { nav.navigate("waitlist") },
                        onProfile    = { nav.navigate(Dest.Profile.route) },
                        onSchedule   = { nav.navigate("schedule") },
                        onPoints     = { nav.navigate("points") },
                        onVoucher    = { nav.navigate("voucher") }
                    )
                }

                composable(Dest.Book.route) {
                    BookingScreen(
                        onBack = { /* tab root */ },
                        onProceed = { draft: BookingDraft ->
                            checkoutVm.currentDraft = draft
                            nav.navigate("checkout")
                        }
                    )
                }

                // --- Profile tab and subpages ---
                composable(Dest.Profile.route) {
                    ProfileHomeScreen(
                        onBack = { /* tab root */ },
                        onEditProfile = { nav.navigate("profile/edit") },
                        onGetHelp = { nav.navigate("profile/help") },
                        onLoggedOut = {
                            nav.navigate("login") {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable("profile/edit") {
                    EditProfileScreen(
                        onBack = { nav.popBackStack() },
                        onLoggedOut = {
                            nav.navigate("login") {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable("profile/help") {
                    androidx.compose.material3.Scaffold(
                        topBar = {
                            androidx.compose.material3.TopAppBar(
                                title = { androidx.compose.material3.Text("Get help") },
                                navigationIcon = {
                                    androidx.compose.material3.IconButton(onClick = { nav.popBackStack() }) {
                                        androidx.compose.material3.Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back"
                                        )
                                    }
                                }
                            )
                        }
                    ) { pad ->
                        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Support is coming soon.")
                        }
                    }
                }

                // --- Extra pages (no bottom bar) ---
                composable("schedule") { ScheduleScreen() }
                composable("admin")    { AdminScreen() }
                composable("points")   { PointsScreen(onBack = { nav.popBackStack() }) }
                composable("voucher")  { VoucherScreen(onBack = { nav.popBackStack() }) }

                // --- Checkout (no bottom bar) ---
                composable("checkout") {
                    val draft = checkoutVm.currentDraft
                    if (draft == null) {
                        LaunchedEffect(Unit) { nav.popBackStack() }
                    } else {
                        CheckoutScreen(
                            draft = draft,
                            onBack = { nav.popBackStack() },
                            onBooked = {
                                nav.navigate("schedule") {
                                    popUpTo(Dest.Home.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                                checkoutVm.currentDraft = null
                            }
                        )
                    }
                }

                // --- Waitlist (no bottom bar) ---
                composable("waitlist") {
                    WaitlistScreen(
                        onBack = { nav.popBackStack() },
                        onBookNow = { draft ->
                            checkoutVm.currentDraft = draft
                            nav.navigate("checkout")
                        }
                    )
                }
            }
        }
    }
}
