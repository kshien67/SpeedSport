package com.speedsport.app.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.speedsport.app.data.rtdb.RtdbRepo
import com.speedsport.app.data.rtdb.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile

    fun loadProfile() = viewModelScope.launch {
        val uid = auth.currentUser?.uid ?: return@launch
        _profile.value = RtdbRepo.getUser(uid)
    }

    fun register(name: String, email: String, password: String, onError: (String)->Unit, onDone: ()->Unit) =
        viewModelScope.launch {
            runCatching {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                auth.currentUser?.updateProfile(userProfileChangeRequest { displayName = name })?.await()
                val uid = result.user?.uid ?: error("No UID")
                RtdbRepo.createUser(UserProfile(uid, name, email, false))
            }.onSuccess { loadProfile(); onDone() }
                .onFailure { onError(it.message ?: "Register failed") }
        }

    fun login(email: String, password: String, onError: (String)->Unit, onDone: ()->Unit) =
        viewModelScope.launch {
            runCatching { FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).await() }
                .onSuccess { loadProfile(); onDone() }
                .onFailure { onError(it.message ?: "Login failed") }
        }

    fun logout() { auth.signOut() }
}
