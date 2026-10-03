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

private val Context.personDebtDataStore by preferencesDataStore("person_debts")

/** Abono parcial contra una deuda personal. */
@Serializable
data class PersonDebtAbono(
    val id: String,
    val amount: Double,
    val dateIso: String
)

/**
 * Deuda personal (D8): "Le presté $500 a Juan", "la cena $1200 entre 3".
 * [esDeudaMia] = true → yo debo (a quién le debo); false → me deben.
 */
@Serializable
data class PersonDebt(
    val id: String,
    val nombre: String,
    val monto: Double,
    val esDeudaMia: Boolean,
    val fechaIso: String,
    val abonos: List<PersonDebtAbono> = emptyList()
)

/**
 * Deudas personales y cuentas divididas (local, sin DDL).
 * Saldo vivo = monto − abonos (ver [com.fintrack.app.domain.PersonDebtPlanner]).
 */
class PersonDebtStore(private val context: Context) {

    private val debtsKey = stringPreferencesKey("person_debts_json")

    private val listSerializer = ListSerializer(PersonDebt.serializer())

    val debts: Flow<List<PersonDebt>> = context.personDebtDataStore.data.map { prefs ->
        prefs[debtsKey]?.let { raw ->
            runCatching { PendingOpCodec.json.decodeFromString(listSerializer, raw) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun snapshot(): List<PersonDebt> = debts.first()

    /** Restaura un respaldo: reemplaza todas las deudas personales. */
    suspend fun restore(debts: List<PersonDebt>) {
        context.personDebtDataStore.edit { prefs ->
            prefs[debtsKey] = PendingOpCodec.json.encodeToString(listSerializer, debts)
        }
    }

    suspend fun upsert(debt: PersonDebt) {
        context.personDebtDataStore.edit { prefs ->
            val current = decode(prefs[debtsKey]).toMutableList()
            val index = current.indexOfFirst { it.id == debt.id }
            if (index >= 0) current[index] = debt else current.add(debt)
            prefs[debtsKey] = PendingOpCodec.json.encodeToString(listSerializer, current)
        }
    }

    suspend fun delete(id: String) {
        context.personDebtDataStore.edit { prefs ->
            prefs[debtsKey] = PendingOpCodec.json.encodeToString(
                listSerializer, decode(prefs[debtsKey]).filterNot { it.id == id }
            )
        }
    }

    /** Registra un abono parcial (ignora montos <= 0). */
    suspend fun addAbono(debtId: String, amount: Double, dateIso: String) {
        if (amount <= 0.0) return
        context.personDebtDataStore.edit { prefs ->
            val current = decode(prefs[debtsKey]).map { debt ->
                if (debt.id == debtId) {
                    debt.copy(
                        abonos = debt.abonos + PersonDebtAbono(
                            id = "abono-${System.currentTimeMillis()}",
                            amount = amount,
                            dateIso = dateIso
                        )
                    )
                } else debt
            }
            prefs[debtsKey] = PendingOpCodec.json.encodeToString(listSerializer, current)
        }
    }

    private fun decode(raw: String?): List<PersonDebt> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { PendingOpCodec.json.decodeFromString(listSerializer, raw) }.getOrNull()
            ?: emptyList()
}
