package com.fintrack.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TransactionEntity(
    val id: String = "",
    val user_id: String = "",
    val amount: Double = 0.0,
    val type: String = "EXPENSE",
    val category: String = "Otros",
    val description: String = "",
    val merchant: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val source: String = "MANUAL"
)
