package com.calendarfinance.app.ui.ota

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@Composable
fun OtaUpdateDialog(
    viewModel: OtaUpdateViewModel = koinViewModel(),
    onDismiss: () -> Unit
) {
    val uiState = viewModel.uiState.value
    val context = LocalContext.current
    val update = uiState.updateInfo ?: return

    AlertDialog(
        onDismissRequest = { if (!uiState.isDownloading && !uiState.isInstalling) onDismiss() },
        icon = { Icon(Icons.Default.SystemUpdate, null) },
        title = { Text("Nueva version disponible") },
        text = {
            Column {
                Text("Version ${update.versionName} disponible para descargar.")
                if (uiState.isDownloading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Descargando...")
                    }
                }
                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.downloadAndInstall(context) },
                enabled = !uiState.isDownloading && !uiState.isInstalling
            ) {
                if (uiState.isInstalling) {
                    Text("Instalando...")
                } else {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Descargar e Instalar")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.dismiss()
                    onDismiss()
                },
                enabled = !uiState.isDownloading && !uiState.isInstalling
            ) {
                Text("Despues")
            }
        }
    )
}
