package com.speedsport.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.speedsport.app.vm.AuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onGoRegister: () -> Unit,
    vm: AuthViewModel = viewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Column(Modifier.padding(inner).padding(16.dp)) {
            Text("Login", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    vm.login(
                        email = email,
                        password = password,
                        onError = { msg -> scope.launch { snackbar.showSnackbar(msg) } }, // ✅
                        onDone = onLoggedIn
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Login") }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onGoRegister,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Create account") }
        }
    }
}
