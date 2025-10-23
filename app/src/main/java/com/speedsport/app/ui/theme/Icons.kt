package com.speedsport.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable

object AppIcons {
    @Composable fun Home()    { Icon(Icons.Filled.Home,         contentDescription = "Home") }
    @Composable fun Book()    { Icon(Icons.Outlined.CalendarMonth, contentDescription = "Book") }
    @Composable fun Rent()    { Icon(Icons.Outlined.ReceiptLong,   contentDescription = "Rent") } // kept if used anywhere
    @Composable fun Profile() { Icon(Icons.Filled.Person,       contentDescription = "Profile") }
    @Composable fun Shop()    { Icon(Icons.Filled.ShoppingBag,  contentDescription = "Shop") }
}
