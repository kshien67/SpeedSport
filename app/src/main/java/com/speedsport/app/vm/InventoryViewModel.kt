package com.speedsport.app.vm

import androidx.lifecycle.ViewModel
import com.speedsport.app.data.rtdb.EquipmentDto
import com.speedsport.app.data.rtdb.RtdbRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InventoryViewModel : ViewModel() {
    suspend fun items(): List<EquipmentDto> =
        withContext(Dispatchers.IO) { RtdbRepo.listEquipment() }

    suspend fun rent(id: String, qty: Int): Boolean =
        withContext(Dispatchers.IO) { RtdbRepo.rent(id, qty) }

    suspend fun restock() =
        withContext(Dispatchers.IO) { RtdbRepo.restockAll() }
}
