package com.fintrack.app.ui.onboarding

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private data class OnboardingStep(
    val title: String,
    val body: String,
    val actionLabel: String?,
    val action: ((android.content.Context) -> Unit)?
)

/**
 * Asistente inicial de 3 pasos (solo la primera vez): activa el registro
 * automático de cargos desde notificaciones bancarias.
 * 1) Avisos, 2) acceso a notificaciones (+ "configuración restringida" por
 * APK), 3) botón flotante "Registro rápido" + pantalla Permisos.
 */
@Composable
fun OnboardingDialog(
    onDone: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    val steps = remember {
        listOf(
            OnboardingStep(
                title = "Paso 1 · Avisos",
                body = "FinTrack te avisa de nuevas versiones y confirma cada " +
                    "gasto detectado. Activa las notificaciones de la app.",
                actionLabel = "Abrir ajustes de avisos",
                action = { ctx ->
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        }
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${ctx.packageName}")
                        }
                    }
                    runCatching { ctx.startActivity(intent) }
                }
            ),
            OnboardingStep(
                title = "Paso 2 · Leer tus bancos",
                body = "Para registrar cargos y abonos sola, la app lee las " +
                    "notificaciones de tus apps bancarias (nada más). Como se " +
                    "instaló por APK, si Android dice \"se le negó el acceso\": " +
                    "info de la app → menú ⋮ → \"Permitir configuración " +
                    "restringida\" → vuelve y otorga.",
                actionLabel = "Otorgar acceso",
                action = { ctx ->
                    runCatching {
                        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                }
            ),
            OnboardingStep(
                title = "Paso 3 · Registro rápido",
                body = "Agrega el botón \"Registro rápido\" al panel de ajustes " +
                    "(Wi-Fi, Bluetooth…): abre el panel completo, toca el lápiz " +
                    "y arrástralo. En Permisos puedes afinar apps, cuentas y respaldo.",
                actionLabel = "Abrir Permisos",
                action = null
            )
        )
    }
    val current = steps[step]
    val isLast = step == steps.lastIndex

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Activa el registro automático",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (step + 1) / steps.size.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(current.title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    current.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                current.actionLabel?.let { label ->
                    Button(
                        onClick = {
                            if (isLast) onOpenPermissions()
                            else current.action?.invoke(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(label) }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDone) { Text("Omitir") }
                    if (isLast) {
                        Button(onClick = onDone) { Text("Empezar") }
                    } else {
                        Button(onClick = { step++ }) { Text("Siguiente") }
                    }
                }
            }
        }
    }
}
