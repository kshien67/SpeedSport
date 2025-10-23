package com.speedsport.app.ui.theme

import androidx.compose.runtime.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

private const val DB_URL =
    "https://speedsport-edf02-default-rtdb.asia-southeast1.firebasedatabase.app"

/**
 * Reads /users/{uid}/settings/darkMode and keeps it updated in real time.
 * Default is false if not signed in or value missing.
 */
@Composable
fun rememberUserDarkModeSetting(): State<Boolean> {
    var dark by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) return@DisposableEffect onDispose {}

        val ref = FirebaseDatabase.getInstance(DB_URL)
            .reference.child("users").child(uid)
            .child("settings").child("darkMode")

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
