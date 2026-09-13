package com.fintrack.app.ui.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.fintrack.app.domain.AuthAction
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onLoggedIn: () -> Unit,
    onNavigateToRecovery: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun launchBiometric() {
        val fragmentActivity = activity ?: return
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(
            fragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.refreshSession()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Sin mensaje: el usuario canceló o falló; sigue el login manual
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear FinTrack")
            .setSubtitle("Usa tu huella para entrar sin contraseña")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(promptInfo)
    }

    if (uiState.loggedIn) {
        LaunchedEffect(Unit) {
            viewModel.consumeLoggedIn()
            onLoggedIn()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (uiState.isLoginMode) "Iniciar sesión" else "Crear cuenta") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("FinTrack", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))

                // Acción de recuperación según el error: ya no es solo texto rojo.
                when (uiState.pendingAction) {
                    AuthAction.RESEND_CONFIRMATION -> {
                        OutlinedButton(
                            onClick = { viewModel.resendConfirmation() },
                            enabled = !uiState.isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Reenviar correo de confirmación") }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    AuthAction.GO_TO_LOGIN -> {
                        OutlinedButton(
                            onClick = { viewModel.goToLogin() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Ir a iniciar sesión") }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    AuthAction.FORGOT_PASSWORD -> {
                        TextButton(onClick = onNavigateToRecovery) {
                            Text("¿Olvidaste tu contraseña? Recupérala aquí")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    AuthAction.RETRY -> {
                        OutlinedButton(
                            onClick = { viewModel.submit(email, password) },
                            enabled = !uiState.isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Reintentar") }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    AuthAction.NONE -> { }
                }
            }

            uiState.info?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (uiState.pendingAction == AuthAction.RESEND_CONFIRMATION) {
                    OutlinedButton(
                        onClick = { viewModel.resendConfirmation() },
                        enabled = !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Reenviar correo") }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Button(
                onClick = { viewModel.submit(email, password) },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (uiState.isLoginMode) "Entrar" else "Registrarme")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = { viewModel.toggleMode() }) {
                Text(
                    if (uiState.isLoginMode) "¿No tienes cuenta? Regístrate"
                    else "¿Ya tienes cuenta? Inicia sesión"
                )
            }

            if (uiState.isLoginMode) {
                TextButton(onClick = onNavigateToRecovery) {
                    Text("¿Olvidaste tu contraseña?")
                }
            }

            if (biometricAvailable && activity != null) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { launchBiometric() },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Entrar con huella")
                }
            }
        }
    }
}
