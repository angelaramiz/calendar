package com.fintrack.app.domain

/** Errores donde reintentar después tiene sentido (sesión/red), no errores de datos. */
fun isRecoverableError(e: Throwable): Boolean {
    val msg = (e.message ?: "").lowercase()
    return listOf(
        "jwt", "auth", "401", "403", "unauthor", "forbidden", "token",
        "network", "timeout", "host", "ssl", "socket", "econn", "unreachable",
        "unable to resolve"
    ).any { msg.contains(it) }
}

/**
 * Mensaje para la UI: nunca el texto crudo de la excepción (trae la URL
 * completa con el user_id y espamea el snackbar en cada auto-refresh).
 */
fun friendlyErrorMessage(e: Throwable): String {
    if (isRecoverableError(e)) {
        return "Sin conexión: revisa tu internet. Tus movimientos locales siguen aquí."
    }
    return "Algo falló al cargar. Reintenta."
}
