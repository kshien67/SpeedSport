package com.speedsport.app.domain

data class EquipmentItem(
    val id: String = "",
    val name: String = "",
    val rate: Double = 0.0,
    val qty: Int = 0,
)

data class BookingDraft(
    val dateIso: String,                // "2025-10-21"
    val sport: String,                  // "BADMINTON"
    val courtId: String,                // RTDB key
    val courtName: String,              // "Badminton Court A"
    val courtRatePerHour: Double,       // e.g., 15.0
    val selectedTimes: List<String>,    // ["10:00"] (each item is a 1h block)
    val equipment: List<EquipmentItem> = emptyList()
) {
    val hours: Int get() = selectedTimes.size
    val courtSubtotal: Double get() = courtRatePerHour * hours
    val equipmentSubtotal: Double get() = equipment.sumOf { it.rate * it.qty }
    fun totalBeforeDiscount(): Double = courtSubtotal + equipmentSubtotal
}
