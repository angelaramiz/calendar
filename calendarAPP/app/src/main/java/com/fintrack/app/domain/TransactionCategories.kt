package com.fintrack.app.domain

/**
 * Categorías canónicas por tipo, idénticas a las de [NotificationParser]:
 * cada formulario muestra solo las del tipo elegido (nada de ingresos con
 * categoría "Comida").
 */
object TransactionCategories {

    val EXPENSE = listOf(
        "Comida",
        "Transporte",
        "Efectivo",
        "Servicios",
        "Salud",
        "Ocio",
        "Compras",
        "Vivienda",
        "Educación",
        "Transferencias",
        "Finanzas",
        "Otros"
    )

    val INCOME = listOf(
        "Sueldo",
        "Reembolso",
        "Otros"
    )

    fun forType(isIncome: Boolean): List<String> = if (isIncome) INCOME else EXPENSE

    fun defaultFor(isIncome: Boolean): String = if (isIncome) "Sueldo" else "Comida"
}
