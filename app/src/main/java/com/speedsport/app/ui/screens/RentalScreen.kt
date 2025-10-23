package com.speedsport.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.speedsport.app.data.rtdb.EquipmentDto
import com.speedsport.app.data.rtdb.RtdbRepo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentalScreen() {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<EquipmentDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        items = RtdbRepo.listEquipment()
        loading = false
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Rent Equipment") }) }) { inner ->
        Column(Modifier.padding(inner).padding(16.dp).fillMaxSize()) {
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            LazyColumn {
                items(items, key = { it.id }) { e ->
                    ElevatedCard(Modifier.padding(bottom = 12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(e.label)
                            Spacer(Modifier.height(4.dp))
                            Text("Stock: ${e.stock} | RM ${e.rentalRateMYR}")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                scope.launch {
                                    if (RtdbRepo.rent(e.id, 1)) items = RtdbRepo.listEquipment()
                                }
                            }) { Text("Rent 1") }
                        }
                    }
                }
            }
        }
    }
}
