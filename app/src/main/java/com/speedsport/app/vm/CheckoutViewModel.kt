package com.speedsport.app.vm

import androidx.lifecycle.ViewModel
import com.speedsport.app.domain.BookingDraft

class CheckoutViewModel : ViewModel() {
    var currentDraft: BookingDraft? = null
}
