package com.speedsport.app.ui.screens

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.speedsport.app.vm.AuthViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val DB_URL =
    "https://speedsport-edf02-default-rtdb.asia-southeast1.firebasedatabase.app"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegistered: () -> Unit,
    onGoLogin: () -> Unit,
    vm: AuthViewModel = viewModel()
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }              // NEW

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun isValidGmail(addr: String): Boolean {
        val trimmed = addr.trim()
        return Patterns.EMAIL_ADDRESS.matcher(trimmed).matches() &&
                trimmed.lowercase().endsWith("@gmail.com")
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Column(Modifier.padding(inner).padding(16.dp)) {
            Text("Register", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Gmail") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (min 8)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { input -> phone = input.filter { it.isDigit() } },  // digits only
                label = { Text("Phone number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    // --- client-side validation ---
                    when {
                        name.isBlank() -> scope.launch {
                            snackbar.showSnackbar("Please enter your name.")
                        }
                        !isValidGmail(email) -> scope.launch {
                            snackbar.showSnackbar("Please enter a valid Gmail address (example@gmail.com).")
                        }
                        password.length < 8 -> scope.launch {
                            snackbar.showSnackbar("Password must be at least 8 characters.")
                        }
                        phone.isBlank() -> scope.launch {
                            snackbar.showSnackbar("Please enter your phone number.")
                        }
                        else -> {
                            // proceed with register
                            vm.register(
                                name = name.trim(),
                                email = email.trim(),
                                password = password,
                                onError = { msg ->
                                    scope.launch { snackbar.showSnackbar(msg) }
                                },
                                onDone = {
                                    // After Firebase Auth account is created, persist user profile to RTDB
                                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                                    if (uid == null) {
                                        scope.launch { snackbar.showSnackbar("Failed to obtain user id.") }
                                        return@register
                                    }
                                    val ref = FirebaseDatabase.getInstance(DB_URL).reference
                                        .child("users").child(uid)

                                    // Write profile (merge-friendly)
                                    val updates = mapOf(
                                        "name" to name.trim(),
                                        "email" to email.trim(),
                                        "phone" to phone,
                                        "createdAt" to System.currentTimeMillis()
                                    )

                                    // Write profile, then ensure points node exists
                                    ref.updateChildren(updates)
                                        .addOnSuccessListener {
                                            // if points missing, initialize to 0
                                            ref.child("points").get().addOnSuccessListener { snap ->
                                                if (!snap.exists()) {
                                                    ref.child("points").setValue(0)
                                                        .addOnCompleteListener { onRegistered() }
                                                } else onRegistered()
                                            }.addOnFailureListener {
                                                // still navigate even if this read fails
                                                onRegistered()
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            scope.launch {
                                                snackbar.showSnackbar(e.message ?: "Failed to save profile")
                                            }
                                        }
                                }
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create account")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onGoLogin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back to login")
            }
        }
    }
}
