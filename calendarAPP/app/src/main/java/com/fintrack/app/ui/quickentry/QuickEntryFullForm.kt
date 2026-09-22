package com.fintrack.app.ui.quickentry

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.WalletRow
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.domain.TransactionCategories

/**
 * Formulario completo de registro rápido en una sola pantalla
 * (ventana IN: botón "+" dentro de la app).
 *
 * Tipo + monto + categoría + nota + cuenta destino en una columna con
 * scroll; la persiana que lo contiene sale desde abajo. La variante por
 * pasos ([QuickEntryForm]) es exclusiva de la ventana OUT del Tile.
 *
 * [onSave] recibe la transacción + billetera elegida (null = Efectivo /
 * regla por defecto) + tarjeta de crédito tageada (null = no es a crédito).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickEntryFullForm(
    cards: List<CreditCardRow> = emptyList(),
    wallets: List<WalletRow> = emptyList(),
    onSave: (TransactionEntity, String?, String?) -> Unit,
    onCancel: () -> Unit,
    onCreateWallet: (name: String, last4: String, kind: String) -> Unit = { _, _, _ -> }
) {
    var type by remember { mutableStateOf("EXPENSE") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(TransactionCategories.defaultFor(false)) }
    var description by remember { mutableStateOf("") }
    // Selección exclusiva: o billetera (débito/efectivo/…) o tarjeta.
    var walletId by remember { mutableStateOf<String?>(null) }
    var cardId by remember { mutableStateOf<String?>(null) }
    // Alta de cuenta inline.
    var showNewAccount by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newLast4 by remember { mutableStateOf("") }
    var newKind by remember { mutableStateOf("Débito") }
    var pendingNewName by remember { mutableStateOf<String?>(null) }

    val isIncome = type == "INCOME"
    val categories = TransactionCategories.forType(isIncome)
    val amountValue = amount.toDoubleOrNull()
    val valid = amountValue != null && amountValue > 0
    val accountTitle = if (isIncome) "Cuenta destino" else "Método de pago"

    // La cuenta recién creada aparece en la lista del padre: seleccionarla.
    LaunchedEffect(wallets, pendingNewName) {
        val pending = pendingNewName ?: return@LaunchedEffect
        wallets.firstOrNull { it.name.trim().equals(pending, ignoreCase = true) }?.let {
            walletId = it.id
            cardId = null
            pendingNewName = null
            showNewAccount = false
            newName = ""
            newLast4 = ""
        }
    }

    Column(
        modifier = Modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            if (isIncome) "Ingreso rápido" else "Gasto rápido",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("EXPENSE" to "Gasto", "INCOME" to "Ingreso").forEach { (t, label) ->
                FilterChip(
                    selected = type == t,
                    onClick = {
                        type = t
                        category = TransactionCategories.defaultFor(t == "INCOME")
                        if (t == "INCOME") cardId = null
                    },
                    label = { Text(label) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Monto") },
            leadingIcon = { Text("$") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text("Categoría", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            categories.forEach { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = { category = cat },
                    label = { Text(cat) },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Nota (opcional)") },
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(accountTitle, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (wallets.isEmpty()) {
                FilterChip(
                    selected = walletId == null,
                    onClick = { walletId = null; cardId = null },
                    label = { Text("Efectivo") }
                )
            } else {
                wallets.forEach { wallet ->
                    FilterChip(
                        selected = walletId == wallet.id ||
                            (walletId == null && cardId == null && wallet.id == "efectivo"),
                        onClick = { walletId = wallet.id; cardId = null },
                        label = { Text(wallet.displayWithKind) }
                    )
                }
            }
            if (!isIncome) {
                cards.forEach { card ->
                    FilterChip(
                        selected = cardId == card.id,
                        onClick = { cardId = card.id; walletId = null },
                        label = { Text(card.displayName) }
                    )
                }
            }
        }
        if (!isIncome && cards.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "El cargo a crédito no resta al balance: se suma a tu próximo pago.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = { showNewAccount = !showNewAccount }) {
            Text(if (showNewAccount) "Ocultar alta" else "+ Nueva cuenta")
        }
        if (showNewAccount) {
            Spacer(modifier = Modifier.height(8.dp))
            if (pendingNewName != null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Nombre de la cuenta") },
                placeholder = { Text("Ej. Nómina BBVA, Vales…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newLast4,
                    onValueChange = { newLast4 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Term.") },
                    placeholder = { Text("1234") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(0.4f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val name = newName.trim()
                        if (name.isNotBlank() && pendingNewName == null) {
                            pendingNewName = name
                            onCreateWallet(name, newLast4, newKind)
                        }
                    },
                    enabled = newName.isNotBlank() && pendingNewName == null,
                    modifier = Modifier.weight(0.6f)
                ) { Text("Añadir") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Tipo de cuenta", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WalletRow.ACCOUNT_KINDS.forEach { kind ->
                    FilterChip(
                        selected = newKind == kind,
                        onClick = { newKind = kind },
                        label = { Text(kind) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) { Text("Cancelar") }
            TextButton(
                onClick = {
                    val value = amount.toDoubleOrNull() ?: return@TextButton
                    if (value <= 0) return@TextButton
                    onSave(
                        TransactionEntity(
                            amount = value,
                            type = type,
                            category = category,
                            description = description,
                            timestamp = System.currentTimeMillis(),
                            source = "MANUAL"
                        ),
                        walletId,
                        if (isIncome) null else cardId
                    )
                },
                enabled = valid && pendingNewName == null
            ) { Text("Guardar") }
        }
    }
}
