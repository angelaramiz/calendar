package com.fintrack.app.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.fintrack.app.data.DiscreteModeStore

private val AMOUNT_REGEX = Regex("""\$[\d.,]+""")

/**
 * Enmascara los montos ($1,234 → $•••) para el modo discreto (C10).
 * Puro y testeable; deja fechas ("5/9") y texto intactos.
 */
fun maskAmounts(text: String): String = AMOUNT_REGEX.replace(text, "\\\$•••")

/**
 * Text que muestra $••• en cada monto si el modo discreto está activo
 * (lee el store con default false). Uso: reemplazar el Text de montos
 * en Inicio, Cuentas y Calendario.
 */
@Composable
fun AmountText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE
) {
    val context = LocalContext.current
    val store = remember { DiscreteModeStore(context) }
    val hidden by store.hidden.collectAsState(initial = false)
    Text(
        text = if (hidden) maskAmounts(text) else text,
        modifier = modifier,
        style = style,
        fontWeight = fontWeight,
        color = color,
        maxLines = maxLines
    )
}
