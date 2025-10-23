@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.speedsport.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.launch

/* ========= CONFIG ========= */
private const val DB_URL =
    "https://speedsport-edf02-default-rtdb.asia-southeast1.firebasedatabase.app"

/* ========= MODEL ========= */
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val phone: String = "",
    val points: Int = 0,
    val admin: Boolean = false
)

/* ========= DB refs ========= */
private fun usersRef() =
    FirebaseDatabase.getInstance(DB_URL).reference.child("users")

/* ========= Profile helpers ========= */

private fun loadProfile(
    onLoaded: (UserProfile?) -> Unit,
    onError: (String) -> Unit = {}
) {
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid
    if (uid == null) { onError("Not logged in"); onLoaded(null); return }

    usersRef().child(uid).get()
        .addOnSuccessListener { snap ->
            val p = UserProfile(
                uid   = uid,
                email = snap.child("email").getValue(String::class.java)
                    ?: auth.currentUser?.email.orEmpty(),
                name  = snap.child("name").getValue(String::class.java) ?: "",
                phone = snap.child("phone").getValue(String::class.java) ?: "",
                points= (snap.child("points").value as? Number)?.toInt() ?: 0,
                admin = snap.child("admin").getValue(Boolean::class.java) ?: false
            )
            onLoaded(p)
        }
        .addOnFailureListener { e -> onError(e.message ?: "Failed to load profile"); onLoaded(null) }
}

private fun saveProfile(
    name: String,
    phone: String,
    onDone: (Boolean, String?) -> Unit
) {
    val user = FirebaseAuth.getInstance().currentUser
    val uid = user?.uid ?: return onDone(false, "Not logged in")

    val updates = mapOf(
        "/users/$uid/uid"       to uid,
        "/users/$uid/email"     to (user.email ?: ""),
        "/users/$uid/name"      to name,
        "/users/$uid/phone"     to phone,
        "/users/$uid/updatedAt" to ServerValue.TIMESTAMP
    )

    FirebaseDatabase.getInstance(DB_URL).reference.updateChildren(updates)
        .addOnSuccessListener { onDone(true, null) }
        .addOnFailureListener { e -> onDone(false, e.message) }
}

/* ========= Dark mode helpers ========= */

@Composable
private fun rememberUserDarkMode(): State<Boolean> {
    var dark by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) return@DisposableEffect onDispose {}
        val ref = usersRef().child(uid).child("settings").child("darkMode")
        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                dark = snapshot.getValue(Boolean::class.java) ?: false
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }
    return remember { derivedStateOf { dark } }
}

private fun setUserDarkMode(newValue: Boolean, onDone: (Boolean) -> Unit) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return onDone(false)
    usersRef().child(uid).child("settings").child("darkMode")
        .setValue(newValue)
        .addOnSuccessListener { onDone(true) }
        .addOnFailureListener { onDone(false) }
}

/* =========================================================================
 *  Profile Home (menu) — Edit Profile, Dark Mode toggle, Get Help, Logout
 * ========================================================================= */

@Composable
fun ProfileHomeScreen(
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onGetHelp: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dark by rememberUserDarkMode()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loadProfile(
            onLoaded = { p -> if (p != null) { name = p.name; email = p.email } },
            onError  = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Hello, ${if (name.isNotBlank()) name else "there"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (email.isNotBlank()) {
                Text(
                    email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            ListItem(
                headlineContent = { Text("Edit Profile") },
                supportingContent = { Text("Name, phone number") },
                leadingContent = { Icon(Icons.Filled.Person, null) },
                trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                modifier = Modifier.fillMaxWidth().clickable { onEditProfile() }
            )

            ListItem(
                headlineContent = { Text("Dark mode") },
                supportingContent = { Text("Apply app-wide dark theme") },
                leadingContent = { Icon(Icons.Filled.Settings, null) },
                trailingContent = {
                    Switch(
                        checked = dark,
                        onCheckedChange = { want ->
                            setUserDarkMode(want) { ok ->
                                if (!ok) scope.launch { snackbar.showSnackbar("Failed to save setting") }
                            }
                        }
                    )
                }
            )

            ListItem(
                headlineContent = { Text("Get help") },
                supportingContent = { Text("FAQs and support (coming soon)") },
                leadingContent = { Icon(Icons.Filled.HelpOutline, null) },
                trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                modifier = Modifier.fillMaxWidth().clickable { onGetHelp() }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Button(
                onClick = {
                    FirebaseAuth.getInstance().signOut()
                    onLoggedOut()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Log out") }
        }
    }
}

/* =========================================================================
 *  Edit Profile screen (form)
 * ========================================================================= */

@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var points by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loadProfile(
            onLoaded = { p ->
                if (p != null) {
                    email = p.email
                    name  = p.name
                    phone = p.phone
                    points = p.points
                }
            },
            onError = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                value = email,
                onValueChange = {},
                label = { Text("Email") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Points: $points", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    saving = true
                    saveProfile(name, phone) { ok, err ->
                        saving = false
                        scope.launch {
                            snackbar.showSnackbar(if (ok) "Profile saved" else err ?: "Save failed")
                        }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (saving) "Saving…" else "Save changes") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Button(
                onClick = {
                    FirebaseAuth.getInstance().signOut()
                    onLoggedOut()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Log out") }
        }
    }
}
