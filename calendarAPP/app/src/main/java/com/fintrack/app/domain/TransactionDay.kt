package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Día (UTC) de un timestamp: solo para conversiones DatePicker↔epoch
 * (norma del proyecto, sin off-by-one). El "hoy" visible usa zona local.
 */
fun Long.toLocalDateUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Transacciones caídas en [day] (comparación por fecha UTC). */
fun List<TransactionEntity>.onDayUtc(day: LocalDate): List<TransactionEntity> =
    filter { it.timestamp.toLocalDateUtc() == day }

/** Día del timestamp en la zona dada (por defecto la del dispositivo). */
fun Long.toLocalDateIn(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/**
 * Transacciones caídas en [day] según [zone]: es el "hoy" que ve el
 * usuario. Con UTC la lista de Inicio se vaciaba a las 18:00-23:59
 * (hora México) porque UTC ya era mañana.
 */
fun List<TransactionEntity>.onDay(
    day: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): List<TransactionEntity> =
    filter { it.timestamp.toLocalDateIn(zone) == day }
