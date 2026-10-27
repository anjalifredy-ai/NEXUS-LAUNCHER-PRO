package com.nexus.launcher.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.launcher.auth.AuthManager
import com.nexus.launcher.ui.theme.NexusColors

@Composable
fun OfflineLoginScreen(
    authManager: AuthManager,
    onLoggedIn: () -> Unit,
    onBack: () -> Unit
) {
    var username by remember { mutableStateOf("Player") }
    var error by remember { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = NexusColors.OnSurface,
        unfocusedTextColor = NexusColors.OnSurface,
        focusedBorderColor = NexusColors.Primary,
        unfocusedBorderColor = NexusColors.OnSurfaceMuted
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusColors.Background)
            .padding(24.dp)
    ) {
        Text("Offline account", color = NexusColors.Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Local play only. Online-mode servers need Microsoft login (coming later).",
            color = NexusColors.OnSurfaceMuted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(16) },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = fieldColors
        )

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = NexusColors.Danger, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (username.isBlank()) {
                    error = "Username required"
                    return@Button
                }
                authManager.loginOffline(username.trim())
                onLoggedIn()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NexusColors.Primary)
        ) {
            Text("Continue offline", color = Color(0xFF052E16))
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back", color = NexusColors.OnSurfaceMuted)
        }
    }
}
