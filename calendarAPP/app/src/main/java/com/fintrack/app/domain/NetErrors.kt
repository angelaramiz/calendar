package com.fintrack.app.domain

/** Errores donde reintentar después tiene sentido (sesión/red), no errores de datos. */
fun isRecoverableError(e: Exception): Boolean {
    val msg = (e.message ?: "").lowercase()
    return listOf(
        "jwt", "auth", "401", "403", "unauthor", "forbidden", "token",
        "network", "timeout", "host", "ssl", "socket", "econn", "unreachable",
        "unable to resolve"
    ).any { msg.contains(it) }
}
