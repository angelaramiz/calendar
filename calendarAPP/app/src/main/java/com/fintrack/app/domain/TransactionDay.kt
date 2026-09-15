package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Día (UTC) de un timestamp: base del "Inicio solo muestra hoy".
 * UTC en ambos sentidos por norma del proyecto (sin off-by-one).
 */
fun Long.toLocalDateUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Transacciones caídas en [day] (comparación por fecha UTC). */
fun List<TransactionEntity>.onDayUtc(day: LocalDate): List<TransactionEntity> =
    filter { it.timestamp.toLocalDateUtc() == day }
