package com.speedsport.app.vm

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.speedsport.app.data.rtdb.EquipmentDto
import com.speedsport.app.data.rtdb.RtdbRepo
import kotlinx.coroutines.launch

class InventoryViewModel : ViewModel() {
    var items by mutableStateOf<List<EquipmentDto>>(emptyList())
        private set

    fun loadAll() = viewModelScope.launch {
        items = RtdbRepo.listEquipmentBySportOrAll("ALL")
    }

    fun refresh(filterSport: String? = "ALL") = viewModelScope.launch {
        items = RtdbRepo.listEquipmentBySportOrAll(filterSport)
    }
}
