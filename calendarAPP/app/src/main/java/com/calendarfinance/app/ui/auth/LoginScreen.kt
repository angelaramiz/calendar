package com.calendarfinance.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.calendarfinance.app.ui.ota.OtaUpdateDialog
import com.calendarfinance.app.ui.ota.OtaUpdateViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: AuthViewModel = koinViewModel(),
    biometricViewModel: BiometricAuthViewModel = koinViewModel(),
    otaViewModel: OtaUpdateViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val bioState by biometricViewModel.uiState.collectAsState()
    val otaState by otaViewModel.uiState.collectAsState()
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showRecovery by remember { mutableStateOf(false) }
    var recoveryIdentifier by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val activity = LocalContext.current as FragmentActivity

    // Check biometric availability on start
    LaunchedEffect(Unit) {
        try { biometricViewModel.checkBiometricAvailability(activity) } catch (e: Exception) { e.printStackTrace() }
    }

    // Auto-check for OTA updates on start
    LaunchedEffect(Unit) {
        try { otaViewModel.autoCheck(activity) } catch (e: Exception) { e.printStackTrace() }
    }

    // Auto-login with biometric if available and session exists
    LaunchedEffect(bioState.isBiometricEnabled, bioState.hasStoredSession) {
        if (bioState.isBiometricEnabled && bioState.hasStoredSession && bioState.isBiometricAvailable) {
            biometricViewModel.authenticateWithBiometric(activity) { userId, email, username, name ->
                viewModel.loginDirect(userId, email, username, name)
            }
        }
    }

    // Navigate on login success
    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) onLoginSuccess()
    }

    if (showRecovery) {
        RecoveryDialog(
            identifier = recoveryIdentifier,
            onIdentifierChange = { recoveryIdentifier = it },
            onSend = { viewModel.recoverPassword(recoveryIdentifier); showRecovery = false },
            onDismiss = { showRecovery = false }
        )
    }

    // OTA Update Dialog
    if (otaState.updateInfo != null) {
        OtaUpdateDialog(viewModel = otaViewModel, onDismiss = {})
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "CalendarFinance",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tu calendario financiero personal",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // OTA update indicator
        if (otaState.updateInfo != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Nueva version ${otaState.updateInfo!!.versionName} disponible",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it; viewModel.clearError() },
            label = { Text("Email o usuario") },
            leadingIcon = { Icon(Icons.Default.Email, "Email") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; viewModel.clearError() },
            label = { Text("Contrasena") },
            leadingIcon = { Icon(Icons.Default.Lock, "Password") },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle")
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.login(identifier, password) }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = { showRecovery = true }) {
            Text("Olvidaste tu contrasena?")
        }

        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.login(identifier, password) },
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Iniciar Sesion")
            }
        }

        // Biometric button
        if (bioState.hasStoredSession && bioState.isBiometricAvailable && bioState.isBiometricEnabled) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("O", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    biometricViewModel.authenticateWithBiometric(activity) { userId, email, username, name ->
                        viewModel.loginDirect(userId, email, username, name)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = "Biometria",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Iniciar con huella")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onNavigateToRegister,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Crear cuenta")
        }
    }
}

@Composable
private fun RecoveryDialog(
    identifier: String,
    onIdentifierChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recuperar contrasena") },
        text = {
            OutlinedTextField(
                value = identifier,
                onValueChange = onIdentifierChange,
                label = { Text("Email o usuario") },
                singleLine = true
            )
        },
        confirmButton = { TextButton(onClick = onSend) { Text("Enviar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
