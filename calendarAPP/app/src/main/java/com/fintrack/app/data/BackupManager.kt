package com.fintrack.app.data

import android.content.Context
import com.fintrack.app.domain.MoneyFlow

/**
 * Respaldo/restauración de lo 100% local. Exportar genera JSON (para
 * copiar y guardar fuera del teléfono); importar solo toca las secciones
 * presentes en el JSON y devuelve cuántas aplicó.
 */
class BackupManager(private val context: Context) {

    private val appCtx get() = context.applicationContext

    suspend fun export(): LocalBackup {
        val wallets = WalletStore(appCtx)
        val cards = CreditCardStore(appCtx)
        return LocalBackup(
            wallets = wallets.snapshot(),
            hiddenWallets = wallets.hiddenSnapshot(),
            walletOverrides = wallets.overridesSnapshot(),
            cards = cards.cardsSnapshot(),
            charges = cards.chargesSnapshot(),
            payments = cards.paymentsSnapshot(),
            bills = ServiceBillStore(appCtx).snapshot(),
            caps = BudgetCapsStore(appCtx).snapshot(),
            links = PatternLinkStore(appCtx).snapshot(),
            flowJson = PendingOpCodec.json.encodeToString(
                MoneyFlow.serializer(), FlowStore(appCtx).snapshot()
            ),
            allowedPackages = AppFilterStore(appCtx).allowedSnapshot().toList()
        )
    }

    suspend fun exportJson(): String = encodeBackup(export())

    /** Lanza IllegalArgumentException si el texto no es un respaldo válido. */
    suspend fun importJson(raw: String): Int =
        decodeBackup(raw)?.let { import(it) }
            ?: throw IllegalArgumentException("Respaldo inválido")

    suspend fun import(backup: LocalBackup): Int {
        var applied = 0
        backup.wallets?.let { wallets ->
            WalletStore(appCtx).restore(
                wallets = wallets,
                hidden = backup.hiddenWallets ?: emptyList(),
                overrides = backup.walletOverrides ?: emptyMap()
            )
            applied++
        }
        if (backup.cards != null || backup.charges != null || backup.payments != null) {
            val store = CreditCardStore(appCtx)
            store.replaceAll(
                cards = backup.cards ?: store.cardsSnapshot(),
                charges = backup.charges ?: store.chargesSnapshot(),
                payments = backup.payments ?: store.paymentsSnapshot()
            )
            applied++
        }
        backup.bills?.let {
            ServiceBillStore(appCtx).replaceAll(it)
            applied++
        }
        backup.caps?.let { caps ->
            val store = BudgetCapsStore(appCtx)
            store.clear()
            caps.forEach { (category, cap) -> store.setCap(category, cap) }
            applied++
        }
        backup.links?.let { links ->
            val store = PatternLinkStore(appCtx)
            store.clear()
            links.forEach { (patternId, link) ->
                store.setLink(patternId, link.kind, link.cardId)
            }
            applied++
        }
        backup.flowJson?.let { raw ->
            runCatching {
                PendingOpCodec.json.decodeFromString(MoneyFlow.serializer(), raw)
            }.getOrNull()?.let { flow ->
                FlowStore(appCtx).save(flow)
                applied++
            }
        }
        backup.allowedPackages?.let {
            AppFilterStore(appCtx).replaceAllowed(it.toSet())
            applied++
        }
        return applied
    }
}
