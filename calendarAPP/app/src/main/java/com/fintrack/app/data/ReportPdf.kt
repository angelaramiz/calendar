package com.fintrack.app.data

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.fintrack.app.domain.MonthlyReport
import java.io.File
import java.time.format.TextStyle
import java.util.Locale

/**
 * D12 Render del reporte mensual a PDF de una página (A4, 100% local con
 * `android.graphics.pdf.PdfDocument`, sin librerías nuevas).
 *
 * Letra legible y una sola página por diseño: listas recortadas con fila
 * "+N más" (tarjetas 5, servicios 6, MSI 4, metas 4, top 3). El archivo vive
 * en `cacheDir/reports` y no se persiste nada más (sin stores nuevos, sin
 * sección de respaldo).
 */
object ReportPdf {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40
    private const val MAX_CARDS = 5
    private const val MAX_SERVICES = 6
    private const val MAX_MSI = 4
    private const val MAX_GOALS = 4

    private val Spanish = Locale("es")

    fun monthLabel(model: MonthlyReport.ReportModel): String {
        val name = model.month.month.getDisplayName(TextStyle.FULL, Spanish)
            .replaceFirstChar { it.uppercase() }
        return "$name ${model.month.year}"
    }

    fun money(value: Double): String =
        "$" + "%,.0f".format(value).replace(',', '.')

    /** Renderiza el modelo a un PdfDocument de una página (el llamador lo cierra). */
    fun render(model: MonthlyReport.ReportModel): PdfDocument {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val canvas = page.canvas
        val title = Paint().apply {
            isAntiAlias = true; textSize = 18f; isFakeBoldText = true
        }
        val section = Paint().apply {
            isAntiAlias = true; textSize = 13f; isFakeBoldText = true
        }
        val body = Paint().apply { isAntiAlias = true; textSize = 11f }
        val muted = Paint().apply {
            isAntiAlias = true; textSize = 11f
            color = android.graphics.Color.DKGRAY
        }
        var y = MARGIN.toFloat()
        fun line(paint: Paint, text: String) {
            if (y > PAGE_H - MARGIN) return
            // Recorta por ancho en vez de desbordar la página.
            var shown = text
            while (paint.measureText(shown) > PAGE_W - 2 * MARGIN && shown.length > 4) {
                shown = shown.dropLast(4) + "…"
            }
            canvas.drawText(shown, MARGIN.toFloat(), y, paint)
            y += if (paint === title) 26f else 17f
        }
        fun gap() {
            y += 6f
        }

        line(title, "Reporte mensual — ${monthLabel(model)}")
        line(muted, "FinTrack · generado en tu teléfono, sin subir nada")
        gap()
        line(section, "Resumen")
        line(body, "Ingresos: ${money(model.ingresos)}")
        line(body, "Gastos: ${money(model.gastos)}")
        line(body, "Neto: ${money(model.neto)}")
        model.limiteDiario?.let { line(body, "Límite diario: ${money(it)}") }
        gap()

        line(section, "Top categorías")
        if (model.topCategorias.isEmpty()) {
            line(muted, "Sin gastos este mes.")
        } else {
            model.topCategorias.forEachIndexed { i, top ->
                line(body, "${i + 1}. ${top.nombre}: ${money(top.monto)}")
            }
        }
        gap()

        line(section, "Tarjetas (estado vs ciclo)")
        if (model.tarjetas.isEmpty()) {
            line(muted, "Sin tarjetas.")
        } else {
            model.tarjetas.take(MAX_CARDS).forEach { card ->
                if (card.estadoPagado) {
                    line(body, "${card.nombre}: ✓ pagado (${money(card.pagado)}) · ciclo ${money(card.ciclo)}")
                } else {
                    line(
                        body,
                        "${card.nombre}: a pagar ${money(card.restante)} · ciclo ${money(card.ciclo)}"
                    )
                }
            }
            val rest = model.tarjetas.size - MAX_CARDS
            if (rest > 0) line(muted, "+$rest más")
        }
        gap()

        line(section, "Servicios del mes")
        if (model.servicios.isEmpty()) {
            line(muted, "Sin servicios este mes.")
        } else {
            model.servicios.take(MAX_SERVICES).forEach { service ->
                val marca = if (service.pagado) "✓ " else ""
                line(
                    body,
                    "$marca${service.nombre} vence ${service.vencimiento.dayOfMonth}/" +
                        "${service.vencimiento.monthValue} · ${money(service.monto)}"
                )
            }
            val rest = model.servicios.size - MAX_SERVICES
            if (rest > 0) line(muted, "+$rest más")
        }
        gap()

        line(section, "MSI activos")
        if (model.msi.isEmpty()) {
            line(muted, "Sin planes MSI.")
        } else {
            model.msi.take(MAX_MSI).forEach { msi ->
                val fin = msi.termina?.let { " · termina ${it.monthValue}/${it.year}" } ?: ""
                line(body, "${msi.concepto} ${msi.hechos}/${msi.total} · ${money(msi.parcial)}$fin")
            }
            val rest = model.msi.size - MAX_MSI
            if (rest > 0) line(muted, "+$rest más")
        }
        gap()

        line(section, "Metas")
        if (model.metas.isEmpty()) {
            line(muted, "Sin metas.")
        } else {
            model.metas.take(MAX_GOALS).forEach { goal ->
                val pct = (goal.progreso * 100).toInt()
                line(body, "${goal.nombre} $pct% · ${money(goal.juntado)} de ${money(goal.meta)}")
            }
            val rest = model.metas.size - MAX_GOALS
            if (rest > 0) line(muted, "+$rest más")
        }

        doc.finishPage(page)
        return doc
    }

    /**
     * Genera `reporte-yyyy-MM.pdf` en `cacheDir/reports` (crea el dir).
     * Sobrescribe el del mismo mes: caché efímera para compartir.
     */
    fun writeToCache(context: Context, model: MonthlyReport.ReportModel): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "reporte-${model.month}.pdf")
        val doc = render(model)
        try {
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return file
    }
}
