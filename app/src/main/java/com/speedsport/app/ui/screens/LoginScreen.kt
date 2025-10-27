package com.speedsport.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onGoRegister: () -> Unit,
    onLoggedInAdmin: () -> Unit = onLoggedIn,
    vm: AuthViewModel = viewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            // --- Logo + Tagline (center-ish) ---
            Image(
                painter = painterResource(id = com.speedsport.app.R.drawable.speedsport_logo),
                contentDescription = "SpeedSport Logo",
                modifier = Modifier
                    .size(120.dp)
                    .padding(top = 8.dp, bottom = 8.dp)
            )
            Text(
                "Your speed booking choice",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // --- Title ---
            Text(
                "Login",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(Modifier.height(12.dp))

            // --- Email ---
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // --- Password ---
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // --- Login button ---
            Button(
                onClick = {
                    val e = email.trim()
                    val p = password

                    vm.login(
                        email = e,
                        password = p,
                        onError = {
                            // Always show a friendly, fixed message for incorrect credentials
                            scope.launch {
                                snackbar.showSnackbar("Incorrect credential information. Please try again.")
                            }
                        },
                        onDone = {
                            scope.launch {
                                val auth = FirebaseAuth.getInstance()
                                val uid = auth.currentUser?.uid
                                val isAdmin = try {
                                    if (uid == null) false else {
                                        val snap = FirebaseDatabase.getInstance(DB_URL)
                                            .reference.child("users").child(uid).get().await()
                                        val a  = snap.child("admin").getValue(Boolean::class.java) == true
                                        val ia = snap.child("isAdmin").getValue(Boolean::class.java) == true
                                        a || ia
                                    }
                                } catch (_: Exception) { false }

                                if (isAdmin) onLoggedInAdmin() else onLoggedIn()
                            }
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Login") }

            Spacer(Modifier.height(10.dp))

            // --- Create account button ---
            // (Validation will be enforced in RegisterScreen; you'll send that next)
            OutlinedButton(
                onClick = onGoRegister,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Create account") }

            Spacer(Modifier.height(24.dp))
        }
    }
}
