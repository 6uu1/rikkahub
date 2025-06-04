package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel // Changed Hilt import to Koin
import me.rerere.rikkahub.ui.theme.RikkaHubTheme
import kotlin.system.exitProcess


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavSettingsPage(
    viewModel: WebDavViewModel = koinViewModel() // Changed hiltViewModel() to koinViewModel()
) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showRestartDialog by remember { mutableStateOf(false) }

    val backupStatus by viewModel.backupStatus.collectAsState()
    val testConnectionStatus by viewModel.testConnectionStatus.collectAsState()
    val context = LocalContext.current

    var composedStatusText by remember { mutableStateOf("Enter your WebDAV server details.") }

    // Update status text based on ViewModel states
    LaunchedEffect(backupStatus) {
        when (val status = backupStatus) {
            is BackupRestoreStatus.Idle -> { /* Do nothing specific, initial text is fine */ }
            is BackupRestoreStatus.InProgress -> composedStatusText = status.message
            is BackupRestoreStatus.Success -> composedStatusText = status.message
            is BackupRestoreStatus.Error -> composedStatusText = "Error: ${status.message}"
            is BackupRestoreStatus.RestoredNeedRestart -> {
                composedStatusText = status.message
                showRestartDialog = true
            }
        }
    }

    LaunchedEffect(testConnectionStatus) {
        when (val status = testConnectionStatus) {
            is TestConnectionStatus.Idle -> { /* Do nothing specific */ }
            is TestConnectionStatus.Testing -> composedStatusText = "Testing connection..."
            is TestConnectionStatus.Success -> composedStatusText = status.message
            is TestConnectionStatus.Error -> composedStatusText = "Error: ${status.message}"
        }
    }

    val isLoading = backupStatus is BackupRestoreStatus.InProgress || testConnectionStatus is TestConnectionStatus.Testing

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("Restart Required") },
            text = { Text("The restore operation completed successfully. Please restart the app for changes to take effect.") },
            confirmButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    // You can't programmatically restart an Android app easily.
                    // Exit the app, user can reopen.
                    (context as? android.app.Activity)?.finishAffinity()
                    exitProcess(0)
                }) {
                    Text("OK & Exit")
                }
            },
            dismissButton = {
                 TextButton(onClick = { showRestartDialog = false }) {
                    Text("Later")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("WebDAV Settings") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL (e.g., https://example.com/dav)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
            }
            Text(
                text = composedStatusText,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (serverUrl.isNotBlank() && username.isNotBlank()) {
                        viewModel.testWebDavConnection(serverUrl, username, password)
                    } else {
                        composedStatusText = "Server URL and Username cannot be empty."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text("Test Connection")
            }

            Button(
                onClick = {
                     if (serverUrl.isNotBlank() && username.isNotBlank()) {
                        viewModel.backupData(serverUrl, username, password)
                    } else {
                        composedStatusText = "Server URL and Username cannot be empty for backup."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text("Backup Now")
            }

            Button(
                onClick = {
                    if (serverUrl.isNotBlank() && username.isNotBlank()) {
                        viewModel.restoreData(serverUrl, username, password)
                    } else {
                        composedStatusText = "Server URL and Username cannot be empty for restore."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text("Restore from Backup")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WebDavSettingsPagePreview() {
    RikkaHubTheme {
        // Preview won't have a real ViewModel by default,
        // but it's okay for basic layout check.
        WebDavSettingsPage()
    }
}
