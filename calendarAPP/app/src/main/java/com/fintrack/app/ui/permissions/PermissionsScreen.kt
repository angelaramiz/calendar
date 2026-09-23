package com.fintrack.app.ui.permissions

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fintrack.app.data.AppFilterStore
import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.service.TransactionNotificationListener
import com.fintrack.app.domain.NotificationParser
import com.fintrack.app.domain.ParseResult
import kotlinx.coroutines.launch

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
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
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

            DetectorDiagnosticsSection(listenerGranted = listenerGranted)

            AppFilterSection()

            TileSection()

            BackupSection()
        }
    }
}

@Composable
private fun DetectorDiagnosticsSection(listenerGranted: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { AppFilterStore(context.applicationContext) }
    val lastDecision by store.lastDecision.collectAsState(initial = null)
    var sessionActive by remember { mutableStateOf<Boolean?>(null) }
    var simPkg by remember { mutableStateOf("com.mercadopago.wallet") }
    var simTitle by remember { mutableStateOf("") }
    var simText by remember { mutableStateOf("") }
    var simResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        scope.launch { store.ensureDefaults() }
        sessionActive = runCatching { AuthRepository().isLoggedIn }.getOrNull()
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Diagnóstico del detector", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Acceso a notificaciones: " + if (listenerGranted) "OTORGADO"
                else "PENDIENTE: otórgalo arriba o no llegará nada.",
                style = MaterialTheme.typography.bodySmall,
                color = if (!listenerGranted) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Ahorro de batería: si tu teléfono mata la app en segundo plano, " +
                    "el detector deja de recibir. Pon FinTrack en \"Sin restricciones\" " +
                    "en Ajustes → Batería.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = {
                // Fuerza al sistema a revincular el servicio (repara el caso
                // "acceso otorgado pero el listener nunca arranca").
                val pm = context.packageManager
                val cn = android.content.ComponentName(
                    context, TransactionNotificationListener::class.java
                )
                pm.setComponentEnabledSetting(
                    cn,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    cn,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
            }) { Text("Reiniciar detector") }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Sesión: " + when (sessionActive) {
                    true -> "activa (puede guardar)"
                    false -> "INACTIVA: sin sesión no se guarda nada. Cierra e inicia sesión."
                    null -> "revisando…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (sessionActive == false) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Última actividad: " + (lastDecision?.let { formatDecision(it) }
                    ?: "sin notificaciones procesadas aún"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Probar una notificación", style = MaterialTheme.typography.labelLarge)
            Text(
                "Copia aquí el título y texto de una notificación para ver si se detecta y por qué.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = simPkg,
                onValueChange = { simPkg = it },
                label = { Text("ID de paquete") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = simTitle,
                onValueChange = { simTitle = it },
                label = { Text("Título") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = simText,
                onValueChange = { simText = it },
                label = { Text("Texto") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                scope.launch {
                    val allowed = runCatching { store.allowedSnapshot() }
                        .getOrDefault(NotificationParser.DEFAULT_PACKAGES)
                    simResult = when (
                        val r = NotificationParser.parse(
                            simPkg.trim(), simTitle, simText, allowed
                        )
                    ) {
                        is ParseResult.Accepted ->
                            "Aceptada: ${r.tx.type} $${r.tx.amount} · ${r.tx.category}" +
                                (r.tx.merchant?.let { " · $it" } ?: "")
                        is ParseResult.Rejected -> "Rechazada: ${r.reason}"
                    }
                }
            }) { Text("Probar") }
            simResult?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Recordatorios diarios", style = MaterialTheme.typography.labelLarge)
            Text(
                "Revisión de vencimientos, pagos y eventos (12:00 y 22:00). " +
                    "Solo avisa a 3, 1 y 0 días: si nada vence pronto, callarse es lo correcto. " +
                    "Pruébalo ahora:",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            var remindResult by remember { mutableStateOf<String?>(null) }
            Button(onClick = {
                scope.launch {
                    remindResult = "Revisando…"
                    remindResult = runCatching {
                        val appContext = context.applicationContext
                        val report = com.fintrack.app.data.service.ReminderCheck.evaluate(appContext)
                        val fired = com.fintrack.app.data.service.ReminderCheck.fire(appContext, report)
                        // Aviso de prueba fijo: verifica el canal sin depender
                        // de ningún vencimiento real.
                        com.fintrack.app.data.service.RemindersNotifier.show(
                            appContext,
                            "prueba-recordatorios",
                            "Notificación de prueba",
                            "Si ves esto, los avisos de FinTrack funcionan en tu teléfono."
                        )
                        com.fintrack.app.data.service.ReminderCheck.describe(report, fired) +
                            " Aviso de prueba enviado."
                    }.getOrElse { e ->
                        "No se pudo lanzar la revisión: ${e.message}"
                    }
                }
            }) { Text("Probar recordatorios") }
            remindResult?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun formatDecision(raw: String): String {
    // "epoch|resultado|paquete|título"
    val parts = raw.split("|", limit = 4)
    if (parts.size < 4) return raw
    val time = runCatching {
        java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(parts[0].toLong()))
    }.getOrNull() ?: ""
    return "$time · ${parts[1]} · ${parts[2]} · ${parts[3]}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppFilterSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { AppFilterStore(context.applicationContext) }
    val allowed by store.allowedPackages.collectAsState(initial = NotificationParser.DEFAULT_PACKAGES)
    val seen by store.seenPackages.collectAsState(initial = emptyList())
    var customPkg by remember { mutableStateOf("") }

    fun shortName(pkg: String): String {
        val parts = pkg.split(".")
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else pkg
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Apps escuchadas", style = MaterialTheme.typography.titleSmall)
            Text(
                "Solo se procesan notificaciones de estas apps. Si tu banco no aparece, agrégalo con su ID de paquete.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            NotificationParser.DEFAULT_PACKAGES.forEach { pkg ->
                val on = allowed.contains(pkg)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(shortName(pkg), style = MaterialTheme.typography.bodyMedium)
                        Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = on,
                        onCheckedChange = { scope.launch { store.setAllowed(pkg, it) } }
                    )
                }
            }

            // Paquetes extra agregados por el usuario (no están en defaults)
            (allowed - NotificationParser.DEFAULT_PACKAGES).forEach { pkg ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(pkg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { store.setAllowed(pkg, false) } }) {
                        Text("Quitar")
                    }
                }
            }

            // Detectadas recientemente: atajo para agregar sin saber el ID
            val candidates = seen.filter { it !in allowed && it != context.packageName }
            if (candidates.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Detectadas recientemente", style = MaterialTheme.typography.labelLarge)
                candidates.forEach { pkg ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pkg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { store.setAllowed(pkg, true) } }) {
                            Text("Agregar")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customPkg,
                    onValueChange = { customPkg = it },
                    label = { Text("ID de paquete") },
                    placeholder = { Text("com.ejemplo.banco") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    scope.launch { store.addCustom(customPkg); customPkg = "" }
                }) { Text("Añadir") }
            }
        }
    }
}

@Composable
private fun TileSection() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Acceso rápido", style = MaterialTheme.typography.titleSmall)
            Text(
                "El botón de Registro rápido vive en el panel de ajustes (donde están " +
                    "Wi-Fi y Bluetooth) y abre el registro flotante sin abrir la app. " +
                    "Para agregarlo: abre el panel completo, toca el lápiz, arrastra " +
                    "\"Registro rápido\" a tus botones y guarda.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun BackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backup = remember { com.fintrack.app.data.BackupManager(context.applicationContext) }
    var importText by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Respaldo local", style = MaterialTheme.typography.titleSmall)
            Text(
                "Billeteras, tarjetas, servicios, topes, flujos y más viven solo " +
                    "en este teléfono. Cópialos y guárdalos fuera antes de cambiar de equipo.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        runCatching { backup.exportJson() }.onSuccess { json ->
                            val clipboard = context.getSystemService(
                                android.content.Context.CLIPBOARD_SERVICE
                            ) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("fintrack-backup", json)
                            )
                            message = "Respaldo copiado (${json.length / 1024} KB). Pégalo en tus notas."
                        }.onFailure {
                            message = "No se pudo generar: ${it.message?.take(120)}"
                        }
                    }
                }) { Text("Copiar respaldo") }
                OutlinedButton(onClick = { showImport = !showImport }) { Text("Restaurar") }
            }
            message?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            if (showImport) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("Pega el respaldo") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch {
                        runCatching { backup.importJson(importText) }.onSuccess { n ->
                            message = if (n == 0) "Respaldo válido pero sin secciones."
                            else "Restauradas $n secciones."
                            importText = ""
                            showImport = false
                        }.onFailure {
                            message = "Respaldo inválido: revisa el texto pegado."
                        }
                    }
                }) { Text("Aplicar") }
            }
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
