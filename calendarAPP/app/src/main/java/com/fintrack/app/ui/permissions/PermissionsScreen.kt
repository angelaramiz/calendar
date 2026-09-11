package com.fintrack.app.ui.permissions

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var listenerGranted by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(false) }

    fun refreshState() {
        listenerGranted = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openAppInfo() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permisos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PermissionRow(
                title = "Notificaciones",
                description = "Avisos de nueva versión y confirmación de gastos detectados. FinTrack solo envía avisos de tus finanzas, sin publicidad.",
                granted = notificationsEnabled,
                onGrant = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                    }
                    context.startActivity(intent)
                }
            )

            PermissionRow(
                title = "Acceso a notificaciones",
                description = "Lee solo las notificaciones de tus apps bancarias para registrar cargos y abonos automáticamente. Tus datos nunca salen del teléfono sin tu cuenta.",
                granted = listenerGranted,
                onGrant = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            )

            // Guía de "configuración restringida" (Android 13+: instala por APK)
            if (!listenerGranted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Si Android dice \"A la app se le negó el acceso\"",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Como FinTrack se instaló desde un APK, Android bloquea este permiso hasta que lo autorices:\n\n" +
                                "1. Abre la información de la app (botón de abajo).\n" +
                                "2. Toca el menú ⋮ de arriba a la derecha.\n" +
                                "3. Elige \"Permitir configuración restringida\" y confirma.\n" +
                                "4. Vuelve aquí y toca Otorgar.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = { openAppInfo() }) {
                            Text("Abrir información de la app")
                        }
                    }
                }
            }

            Text(
                "El estado se actualiza solo al volver a esta pantalla.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall)
                Text(
                    if (granted) "Otorgado" else "Pendiente",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (granted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (!granted) {
                Button(onClick = onGrant) { Text("Otorgar") }
            }
        }
    }
}
