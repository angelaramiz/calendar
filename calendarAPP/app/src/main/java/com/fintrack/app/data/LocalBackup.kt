package com.fintrack.app.data

import kotlinx.serialization.Serializable

/**
 * Respaldo de todo lo 100% local (sin columna en el servidor): si el
 * teléfono se pierde o se reinstala, esto es lo que no vuelve solo.
 * Campos null = sección ausente = no se toca al importar.
 */
const val BACKUP_VERSION = 1

@Serializable
data class LocalBackup(
    val version: Int = BACKUP_VERSION,
    val wallets: List<WalletRow>? = null,
    val hiddenWallets: List<String>? = null,
    val walletOverrides: Map<String, String>? = null,
    val cards: List<CreditCardRow>? = null,
    val charges: Map<String, String>? = null,
    val payments: List<CardPayment>? = null,
    val bills: List<ServiceBillRow>? = null,
    val caps: Map<String, Double>? = null,
    val links: Map<String, PatternLink>? = null,
    /** Vínculos registro Inicio ↔ evento programado (txId -> vínculo). */
    val txLinks: Map<String, TxLink>? = null,
    /** Flujo serializado tal cual (MoneyFlow). */
    val flowJson: String? = null,
    val allowedPackages: List<String>? = null,
    /** Planes MSI (§A). */
    val msiPlans: List<MsiPlan>? = null,
    /** Deudas personales (D8). */
    val personDebts: List<PersonDebt>? = null,
    /** Reglas merchant→categoría (C5). */
    val rules: Map<String, String>? = null,
    /** Modo discreto (C10). */
    val discreto: Boolean? = null,
    /** Ancla de ingreso quincenal + apartado ahorro (D6). */
    val allowanceIncome: Double? = null,
    val allowanceSavings: Double? = null,
    /** Último reparto de quincena (D3). */
    val paycheck: PaycheckSnapshot? = null,
    /** Descartes del Vigilante (D5). */
    val dismissedAnomalies: Set<String>? = null,
    /** Rachas por categoría + periodo cerrado (D11). */
    val streaks: Map<String, Int>? = null,
    val streaksLastPeriod: String? = null,
    /** Frecuencia hormiga + semana avisada (D11). */
    val hormigaFreq: String? = null,
    val hormigaLastSent: String? = null,
    /** Borrador del Registro rápido a medias (efímero). */
    val draft: EntryDraft? = null
)

fun encodeBackup(backup: LocalBackup): String =
    PendingOpCodec.json.encodeToString(LocalBackup.serializer(), backup)

/** null si el texto está corrupto, vacío o es de otra versión. */
fun decodeBackup(raw: String?): LocalBackup? {
    if (raw.isNullOrBlank()) return null
    val backup = runCatching {
        PendingOpCodec.json.decodeFromString(LocalBackup.serializer(), raw)
    }.getOrNull() ?: return null
    return backup.takeIf { it.version == BACKUP_VERSION }
}
