package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import java.time.LocalDate
import java.time.ZoneId

/**
 * Balance de hoy para el widget (§C7): misma regla que Inicio
 * (`DashboardViewModel.loadDashboard`) — los cargos con tag de tarjeta no
 * restan al balance del momento (se pagan al corte). Puro y testeado.
 */
object WidgetBalance {

    /**
     * @param cardCharges mapa "tx:<id>" -> cardId (cargos a crédito).
     */
    fun todayBalance(
        transactions: List<TransactionEntity>,
        cardCharges: Map<String, String> = emptyMap(),
        day: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Double {
        val todays = transactions.filter {
            it.timestamp.toLocalDateIn(zone) == day && cardCharges["tx:${it.id}"] == null
        }
        val income = todays.filter { it.kind?.isIncome == true }.sumOf { it.amount }
        val expenses = todays.filter { it.kind?.isIncome != true }.sumOf { it.amount }
        return income - expenses
    }

    /** "−$1,500.00" corto para el widget (montos en español/MX). */
    fun format(balance: Double): String {
        val sign = if (balance < 0) "−" else ""
        return sign + "$" + "%,.2f".format(kotlin.math.abs(balance))
    }
}
