package com.fintrack.app.ui.report

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.fintrack.app.data.AllowanceAnchorStore
import com.fintrack.app.data.CreditCardStore
import com.fintrack.app.data.MsiStore
import com.fintrack.app.data.ReportPdf
import com.fintrack.app.data.ServiceBillStore
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.repository.MovementRow
import com.fintrack.app.domain.DailyAllowance
import com.fintrack.app.domain.MonthlyReport
import com.fintrack.app.domain.SavingsGoal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private fun formatMoney(value: Double): String =
    "%,.0f".format(value).replace(',', '.')

/**
 * D12 Reporte mensual (vista previa + PDF).
 *
 * Vive como sección embebida al FINAL de Presupuesto (tras Q&A): no tiene
 * ruta propia porque `NavGraph.kt` es intocable en este encargo. Lee los
 * stores existentes directo del contexto (mismo patrón que el límite diario
 * en Inicio): no modifica ningún ViewModel ajeno. Sin stores nuevos, el
 * reporte no persiste nada (el PDF va a `cacheDir/reports` para compartir).
 */
@Composable
fun ReportSection(
    transactions: List<TransactionEntity>,
    movements: List<MovementRow>,
    goals: List<SavingsGoal>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val month = remember { YearMonth.now() }

    val creditStore = remember { CreditCardStore(context) }
    val msiStore = remember { MsiStore(context) }
    val billStore = remember { ServiceBillStore(context) }
    val anchorStore = remember { AllowanceAnchorStore(context) }
    val cards by creditStore.cards.collectAsState(initial = emptyList())
    val charges by creditStore.charges.collectAsState(initial = emptyMap())
    val payments by creditStore.payments.collectAsState(initial = emptyList())
    val msiPlans by msiStore.plans.collectAsState(initial = emptyList())
    val bills by billStore.bills.collectAsState(initial = emptyList())
    val ingreso by anchorStore.biweeklyIncome.collectAsState(initial = null)
    val ahorro by anchorStore.savingsShare.collectAsState(initial = 0.0)

    val model = remember(transactions, movements, cards, charges, payments, bills, msiPlans, goals, ingreso, ahorro) {
        // Límite diario con los motores existentes (D6): servicios del
        // periodo + mínimos de tarjeta vía summarize; sin ancla = null.
        val period = DailyAllowance.periodFor(today)
        val duesPairs = bills.flatMap { bill ->
            com.fintrack.app.domain.ServiceBills.duesInRange(
                bill.dueDay, bill.frequency, period.start, period.end, bill.dueMonth
            ).map { due -> due to bill.estimatedAmount }
        }
        val chargesByCard = cards.associate { card ->
            card.id to MonthlyReport.datedCharges(card, transactions, movements, charges, zone)
        }
        val paymentsByCard = cards.associate { card ->
            card.id to MonthlyReport.paymentsOf(card.id, payments)
        }
        val minimos = DailyAllowance.cardMinimums(
            cards.map {
                DailyAllowance.CardMinInput(it.id, it.cutoffDay, it.paymentDay, it.graceDays)
            },
            chargesByCard,
            paymentsByCard,
            today
        )
        val fijos = DailyAllowance.servicesInPeriod(duesPairs, period) + minimos.values.sum()
        val limite = DailyAllowance.calculate(
            ingreso, fijos, ahorro, DailyAllowance.daysRemaining(today)
        )
        MonthlyReport.build(
            month = month,
            transactions = transactions,
            movements = movements,
            cards = cards,
            charges = charges,
            payments = payments,
            bills = bills,
            msiPlans = msiPlans,
            goals = goals,
            limiteDiario = limite,
            today = today,
            zone = zone
        )
    }

    var pdfFile by remember { mutableStateOf<File?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Una página con tu mes: neto, top 3, tarjetas, servicios, MSI y metas. Se genera en tu teléfono, sin subir nada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Ingresos ${formatMoney(model.ingresos)} · Gastos ${formatMoney(model.gastos)} · " +
                    "Neto ${formatMoney(model.neto)}",
                fontWeight = FontWeight.Bold
            )
            if (model.topCategorias.isEmpty()) {
                Text(
                    "Sin gastos este mes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                model.topCategorias.forEachIndexed { i, top ->
                    Text(
                        "${i + 1}. ${top.nombre}: ${formatMoney(top.monto)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                "Tarjetas: " + if (model.tarjetas.isEmpty()) "sin tarjetas" else
                    model.tarjetas.joinToString { card ->
                        if (card.estadoPagado) "${card.nombre} ✓" else "${card.nombre} ${formatMoney(card.restante)}"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Servicios: " + if (model.servicios.isEmpty()) "sin vencimientos" else
                    "${model.servicios.size} en el mes · " +
                        model.servicios.count { it.pagado } + " pagados",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "MSI: " + if (model.msi.isEmpty()) "sin planes activos" else
                    model.msi.joinToString { "${it.concepto} ${it.hechos}/${it.total}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Metas: " + if (model.metas.isEmpty()) "sin metas" else
                    model.metas.joinToString { "${it.nombre} ${(it.progreso * 100).toInt()}%" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            model.limiteDiario?.let {
                Text(
                    "Límite diario: ${formatMoney(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val file = runCatching {
                                withContext(Dispatchers.IO) {
                                    ReportPdf.writeToCache(context, model)
                                }
                            }.getOrNull()
                            pdfFile = file
                            mensaje = if (file != null) "PDF listo: ${file.name}" else "No se pudo generar el PDF."
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Generar PDF")
                }
                OutlinedButton(
                    onClick = {
                        val file = pdfFile ?: return@OutlinedButton
                        val uri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
                        )
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Compartir reporte"))
                    },
                    enabled = pdfFile != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Compartir")
                }
            }
            mensaje?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
