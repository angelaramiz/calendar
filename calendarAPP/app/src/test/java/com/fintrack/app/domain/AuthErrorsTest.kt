package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthErrorsTest {

    @Test
    fun email_sin_confirmar_ofrece_reenvio() {
        assertEquals(
            AuthAction.RESEND_CONFIRMATION,
            authActionFor("Email not confirmed")
        )
        assertEquals(
            "Tu correo aún no está confirmado. Revisa tu bandeja o reenvía el correo.",
            friendlyAuthMessage("Email not confirmed")
        )
    }

    @Test
    fun email_ya_registrado_lleva_a_login() {
        assertEquals(
            AuthAction.GO_TO_LOGIN,
            authActionFor("User already registered")
        )
        assertEquals(
            "Ese correo ya está registrado. Inicia sesión.",
            friendlyAuthMessage("User already registered")
        )
    }

    @Test
    fun credenciales_invalidas_ofrecen_recuperar() {
        assertEquals(
            AuthAction.FORGOT_PASSWORD,
            authActionFor("Invalid login credentials")
        )
        assertEquals(
            "Correo o contraseña incorrectos.",
            friendlyAuthMessage("Invalid login credentials")
        )
    }

    @Test
    fun password_debil_y_sus_variantes_en_ingles() {
        listOf(
            "Password should be at least 6 characters",
            "Password is too weak",
            "Password breach detected"
        ).forEach {
            assertEquals(
                "La contraseña no cumple los requisitos: usa 8+ caracteres con mayúscula, minúscula, número y símbolo.",
                friendlyAuthMessage(it)
            )
        }
    }

    @Test
    fun email_invalido_en_sus_variantes() {
        listOf(
            "Invalid email address",
            "Unable to validate email address: invalid format",
            "malformed email"
        ).forEach {
            assertEquals(
                "Ese correo no es válido. Revísalo e intenta de nuevo.",
                friendlyAuthMessage(it)
            )
        }
    }

    @Test
    fun fallos_de_red_ofrecen_reintentar() {
        listOf(
            "Unable to resolve host",
            "UnknownHostException: ugtlxnrwfipoctckuvfd.supabase.co",
            "SocketTimeoutException",
            "ConnectException: Failed to connect"
        ).forEach {
            assertEquals(AuthAction.RETRY, authActionFor(it))
            assertEquals(
                "Sin conexión. Revisa tu internet e intenta de nuevo.",
                friendlyAuthMessage(it)
            )
        }
    }

    @Test
    fun error_desconocido_se_muestra_recortado_sin_accion() {
        assertEquals(AuthAction.NONE, authActionFor("Something odd happened"))
        assertEquals(
            "No se pudo completar: Something odd happened",
            friendlyAuthMessage("Something odd happened")
        )
    }

    @Test
    fun validacion_de_formato_de_email() {
        assertTrue(isValidEmail("usuario@correo.com"))
        assertTrue(isValidEmail("  usuario@correo.com  "))
        assertFalse(isValidEmail("usuario@correo"))
        assertFalse(isValidEmail("no-es-correo"))
        assertFalse(isValidEmail(""))
    }
}
