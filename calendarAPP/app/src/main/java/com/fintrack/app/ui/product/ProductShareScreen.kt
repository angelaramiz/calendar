package com.fintrack.app.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import com.fintrack.app.data.remote.ProductScraperRepository
import com.fintrack.app.domain.ProductScraper
import com.fintrack.app.ui.budget.BudgetViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Planificar compra: pega o recibe un link compartido de Mercado Libre /
 * Amazon, consulta precio y nombre en el buscador y crea la meta de ahorro
 * con el plan contado/crédito ya calculado en Presupuesto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductShareScreen(
    initialUrl: String = "",
    onBack: () -> Unit,
    onGoalCreated: () -> Unit,
    // Mismo BudgetViewModel de la pestaña (alcance actividad): la meta creada
    // aparece seleccionada al volver, igual que el dashboard compartido.
    viewModel: BudgetViewModel = koinViewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    )
) {
    val repository = remember { ProductScraperRepository() }
    val scope = rememberCoroutineScope()
    var urlText by remember(initialUrl) { mutableStateOf(initialUrl) }
    var loading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<ProductScraper.ProductInfo?>(null) }
    var nameText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }

    fun runScrape(raw: String) {
        val url = ProductScraper.extractSharedUrl(raw)
        if (url == null) {
            notice = "Pega un link válido de Mercado Libre o Amazon."
            return
        }
        urlText = url
        loading = true
        notice = null
        info = null
        scope.launch {
            when (val result = repository.scrape(url)) {
                is ProductScraper.QuickResult.Success -> {
                    info = result.info
                    nameText = result.info.name
                    priceText = if (result.info.price > 0.0) {
                        "%,.0f".format(result.info.price).replace(',', '.')
                    } else ""
                    if (result.info.needsManualInput) {
                        notice = "Revisa el precio a mano antes de crear la meta."
                    }
                }
                is ProductScraper.QuickResult.NeedsManual -> {
                    notice = result.message
                    nameText = ""
                    priceText = ""
                }
                is ProductScraper.QuickResult.Error -> {
                    notice = result.message
                }
            }
            loading = false
        }
    }

    // Llega de Compartir: busca solo al entrar.
    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank()) runScrape(initialUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Planificar compra") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Comparte el link desde Mercado Libre o Amazon (o pégalo aquí) " +
                    "y creamos la meta con su precio.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text("Link del producto") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                maxLines = 1
            )
            Button(
                onClick = { runScrape(urlText) },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Buscando…" else "Buscar precio")
            }
            if (loading) {
                CircularProgressIndicator()
            }
            notice?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(it, modifier = Modifier.padding(12.dp))
                }
            }
            info?.let { product ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (product.store.isNotBlank()) {
                                FilterChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text(product.store) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = nameText,
                            onValueChange = { nameText = it },
                            label = { Text("Nombre") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = {
                                priceText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }
                            },
                            label = { Text("Precio") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        val price = priceText.replace(".", "").replace(",", "")
                            .toDoubleOrNull() ?: 0.0
                        Button(
                            onClick = {
                                viewModel.addGoalFromProduct(
                                    nameText,
                                    price,
                                    product.url,
                                    product.store
                                )
                                onGoalCreated()
                            },
                            enabled = nameText.isNotBlank() && price > 0.0,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Crear meta de compra")
                        }
                    }
                }
            }
            // Sin resultado del buscador: alta manual con el link.
            if (!loading && info == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("O crea la meta a mano", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = nameText,
                            onValueChange = { nameText = it },
                            label = { Text("Nombre") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = {
                                priceText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }
                            },
                            label = { Text("Precio") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        val price = priceText.replace(".", "").replace(",", "")
                            .toDoubleOrNull() ?: 0.0
                        Button(
                            onClick = {
                                viewModel.addGoalFromProduct(
                                    nameText,
                                    price,
                                    ProductScraper.extractSharedUrl(urlText).orEmpty(),
                                    ""
                                )
                                onGoalCreated()
                            },
                            enabled = nameText.isNotBlank() && price > 0.0,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Crear meta manual")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Volver")
            }
        }
    }
}
