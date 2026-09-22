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
    /** Flujo serializado tal cual (MoneyFlow). */
    val flowJson: String? = null,
    val allowedPackages: List<String>? = null
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
