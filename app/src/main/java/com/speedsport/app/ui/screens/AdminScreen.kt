package com.speedsport.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.speedsport.app.data.rtdb.RtdbRepo
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen() {
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Admin") }) }
    ) { inner ->
        Column(
            modifier = Modifier.padding(inner).padding(16.dp)
        ) {
            Button(
                onClick = { scope.launch { RtdbRepo.restockAll(20) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Restock Equipment to 20") }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { scope.launch { RtdbRepo.wipeAllBookings() } },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors()
            ) { Text("Wipe All Bookings") }
        }
    }
}
