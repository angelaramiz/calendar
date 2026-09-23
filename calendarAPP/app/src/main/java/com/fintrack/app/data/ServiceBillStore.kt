package com.fintrack.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

private val Context.serviceBillDataStore by preferencesDataStore("service_bills")

/** Pago de servicio: monto estimado, día de vencimiento y frecuencia. */
@Serializable
data class ServiceBillRow(
    val id: String,
    val name: String,
    val estimatedAmount: Double = 0.0,
    /** Día de vencimiento (1-31). */
    val dueDay: Int,
    /** "monthly" o "bimonthly". */
    val frequency: String = "monthly"
)

class ServiceBillStore(private val context: Context) {

    private val billsKey = stringPreferencesKey("service_bills_json")

    private val listSerializer = ListSerializer(ServiceBillRow.serializer())

    val bills: Flow<List<ServiceBillRow>> = context.serviceBillDataStore.data.map { prefs ->
        prefs[billsKey]?.let { raw ->
            runCatching { PendingOpCodec.json.decodeFromString(listSerializer, raw) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun snapshot(): List<ServiceBillRow> = bills.first()

    /** Restaura un respaldo: reemplaza todos los servicios. */
    suspend fun replaceAll(bills: List<ServiceBillRow>) {
        context.serviceBillDataStore.edit { prefs ->
            prefs[billsKey] = PendingOpCodec.json.encodeToString(listSerializer, bills)
        }
    }

    suspend fun upsert(bill: ServiceBillRow) {
        context.serviceBillDataStore.edit { prefs ->
            val current = decode(prefs[billsKey]).toMutableList()
            val index = current.indexOfFirst { it.id == bill.id }
            if (index >= 0) current[index] = bill else current.add(bill)
            prefs[billsKey] = PendingOpCodec.json.encodeToString(listSerializer, current)
        }
    }

    suspend fun delete(id: String) {
        context.serviceBillDataStore.edit { prefs ->
            prefs[billsKey] = PendingOpCodec.json.encodeToString(
                listSerializer, decode(prefs[billsKey]).filterNot { it.id == id }
            )
        }
    }

    private fun decode(raw: String?): List<ServiceBillRow> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { PendingOpCodec.json.decodeFromString(listSerializer, raw) }.getOrNull()
            ?: emptyList()
}

/** Claves ya avisadas ("bill:<id>:<fecha>:<turno>", "card:<id>:<fecha>:<turno>") para no repetir. */
class ReminderStore(private val context: Context) {

    private val remindedKey = stringPreferencesKey("reminded_json")

    suspend fun snapshot(): Set<String> =
        context.serviceBillDataStore.data.map { prefs ->
            PendingOpCodec.decodeStrings(prefs[remindedKey]).keys
        }.first()

    suspend fun markReminded(keys: Set<String>) {
        if (keys.isEmpty()) return
        context.serviceBillDataStore.edit { prefs ->
            val current = PendingOpCodec.decodeStrings(prefs[remindedKey]).toMutableMap()
            keys.forEach { current[it] = "1" }
            // Poda: conserva las últimas 200 para no crecer sin fin.
            val pruned = current.entries.sortedBy { it.key }.takeLast(200)
                .associate { it.key to it.value }
            prefs[remindedKey] = PendingOpCodec.encodeStrings(pruned)
        }
    }
}
