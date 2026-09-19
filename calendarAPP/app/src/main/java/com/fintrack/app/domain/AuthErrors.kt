package com.fintrack.app.domain

/**
 * Manejo centralizado de errores de autenticación (Supabase Auth).
 *
 * Convierte los mensajes crudos (casi siempre en inglés) en texto claro en
 * español y decide qué acción de recuperación ofrecer en la UI. Todo puro
 * para poder probarse en JVM sin Android.
 */
enum class AuthAction {
    NONE,
    RESEND_CONFIRMATION,
    GO_TO_LOGIN,
    FORGOT_PASSWORD,
    RETRY
}

private val EMAIL_REGEX =
    Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

/** Qué acción ofrecer según el error crudo. */
fun authActionFor(rawMessage: String): AuthAction {
    val msg = rawMessage.lowercase()
    return when {
        "not confirmed" in msg || "not verified" in msg -> AuthAction.RESEND_CONFIRMATION
        "already registered" in msg || "already exists" in msg ||
            ("user" in msg && "already" in msg) -> AuthAction.GO_TO_LOGIN
        "invalid login credentials" in msg -> AuthAction.FORGOT_PASSWORD
        "network" in msg || "timeout" in msg || "unable to resolve" in msg ||
            "unknownhost" in msg || "connect" in msg || "socket" in msg ||
            "ssl" in msg || ("http" in msg && "fail" in msg) -> AuthAction.RETRY
        "rate" in msg && "limit" in msg || "too many requests" in msg ||
            "429" in msg -> AuthAction.RETRY
        else -> AuthAction.NONE
    }
}

/** Mensaje en español para el error crudo. */
fun friendlyAuthMessage(rawMessage: String): String {
    val msg = rawMessage.lowercase()
    return when {
        "not confirmed" in msg || "not verified" in msg ->
            "Tu correo aún no está confirmado. Revisa tu bandeja o reenvía el correo."
        "already registered" in msg || "already exists" in msg ||
            ("user" in msg && "already" in msg) ->
            "Ese correo ya está registrado. Inicia sesión."
        "invalid login credentials" in msg ->
            "Correo o contraseña incorrectos."
        "weak" in msg || "password" in msg && ("short" in msg || "character" in msg) ||
            "breach" in msg || "leaked" in msg ->
            "La contraseña no cumple los requisitos: usa 8+ caracteres con mayúscula, minúscula, número y símbolo."
        "invalid" in msg && "email" in msg || "validate" in msg && "email" in msg ||
            "malformed" in msg ->
            "Ese correo no es válido. Revísalo e intenta de nuevo."
        "network" in msg || "timeout" in msg || "unable to resolve" in msg ||
            "unknownhost" in msg || "connect" in msg || "socket" in msg ||
            "ssl" in msg || ("http" in msg && "fail" in msg) ->
            "Sin conexión. Revisa tu internet e intenta de nuevo."
        "rate" in msg && "limit" in msg || "too many requests" in msg ||
            "429" in msg ->
            "Demasiados intentos seguidos. Espera unos minutos e intenta de nuevo."
        else -> "No se pudo completar: ${rawMessage.take(120)}"
    }
}
