package com.fintrack.app.ui.ocr

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.fintrack.app.data.TicketPhotos
import com.fintrack.app.domain.TicketOcr
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/**
 * OCR de tickets (C9): botón autocontenido. Cámara vía intent del sistema
 * (`TakePicture`, sin permiso de cámara ni librerías de cámara) → ML Kit
 * on-device → [TicketOcr] → [onResult] con monto/comercio/foto.
 *
 * La foto queda en `filesDir/tickets` (ver [TicketPhotos]) y NUNCA viaja:
 * ni al respaldo nube ni a ningún servidor. Sin estados globales.
 *
 * NO se usa dentro de `QuickEntryDialog` (prohibido modificarlo): ver el
 * snippet de integración en el reporte de la misión.
 */
@Composable
fun OcrCaptureButton(
    onResult: (monto: Double?, comercio: String?, foto: File?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var fotoActual: File? by remember { mutableStateOf(null) }
    var leyendo by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val foto = fotoActual
        if (!ok || foto == null || !foto.exists()) {
            error = "No se tomó la foto."
            return@rememberLauncherForActivityResult
        }
        leyendo = true
        error = null
        runCatching {
            val image = InputImage.fromFilePath(context, Uri.fromFile(foto))
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener { visionText ->
                    leyendo = false
                    val parsed = TicketOcr.parse(visionText.text)
                    onResult(parsed.amount, parsed.merchant, foto)
                }
                .addOnFailureListener {
                    leyendo = false
                    error = "No se pudo leer el ticket. Intenta con mejor luz."
                }
        }.onFailure {
            leyendo = false
            error = "No se pudo leer el ticket. Intenta con mejor luz."
        }
    }

    Column(modifier = modifier) {
        Button(
            onClick = {
                error = null
                val foto = TicketPhotos.newFile(context.filesDir)
                fotoActual = foto
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    foto
                )
                launcher.launch(uri)
            },
            enabled = !leyendo,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (leyendo) "Leyendo ticket…" else "Escanear ticket")
        }
        error?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
