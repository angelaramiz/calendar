package com.fintrack.app.ui.flows

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fintrack.app.domain.ConditionNode
import com.fintrack.app.domain.EnvelopeNode
import com.fintrack.app.domain.FlowNode
import com.fintrack.app.domain.FlowTrace
import com.fintrack.app.domain.FormulaNode
import com.fintrack.app.domain.IncomeNode
import com.fintrack.app.domain.IncomeSource

/**
 * Lienzo estilo n8n: nodos con puertos de entrada/salida unidos por
 * conectores curvos. Tocar un nodo lo selecciona (se edita abajo);
 * al ejecutar, el camino recorrido se ilumina con montos.
 */
@Composable
internal fun FlowCanvas(
    nodes: List<FlowNode>,
    trace: FlowTrace?,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (nodes.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier.fillMaxWidth()
        ) {
            Text(
                "Lienzo vacío: agrega tu primer nodo (normalmente un Ingreso).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }
    val executedIds = trace?.steps?.map { it.nodeId }?.toSet().orEmpty()
    val outputs = trace?.steps?.associate { it.nodeId to it.output }.orEmpty()
    val branches = trace?.steps?.mapNotNull { step ->
        step.branch?.let { step.nodeId to it }
    }?.toMap().orEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        nodes.forEachIndexed { index, node ->
            if (index > 0) {
                val prev = nodes[index - 1]
                FlowEdge(
                    amount = outputs[prev.id],
                    active = prev.id in executedIds
                )
            }
            FlowNodeBox(
                index = index,
                node = node,
                selected = node.id == selectedId,
                executed = node.id in executedIds,
                takenBranch = branches[node.id],
                onClick = { onSelect(node.id) }
            )
        }
        // Puerto final: total asignado cuando hay traza.
        trace?.let {
            FlowEdge(amount = null, active = true)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Resultado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "$${String.format("%.2f", it.totalAssigned)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

/** Conector curvo entre nodos, con el monto que viaja por él. */
@Composable
private fun FlowEdge(amount: Double?, active: Boolean) {
    val edgeColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = if (active) edgeColor else idleColor
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (amount != null) {
            Text(
                "$${String.format("%.0f", amount)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }
        Canvas(modifier = Modifier.width(64.dp).height(26.dp)) {
            val h = size.height / 2f
            val path = Path().apply {
                moveTo(0f, h)
                cubicTo(size.width * 0.35f, h, size.width * 0.65f, h, size.width, h)
            }
            drawPath(path, color = lineColor, style = Stroke(width = 7f))
            drawCircle(lineColor, radius = 9f, center = Offset(0f, h))
            drawCircle(lineColor, radius = 9f, center = Offset(size.width, h))
        }
    }
}

private data class NodeStyle(
    val title: String,
    val icon: ImageVector,
    val accent: Color,
    val subtitle: String
)

@Composable
private fun nodeStyle(node: FlowNode, index: Int): NodeStyle {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    return when (node) {
        is IncomeNode -> {
            val src = when (val s = node.source) {
                is IncomeSource.Fixed -> "Fijo $${String.format("%.0f", s.amount)}"
                is IncomeSource.MonthIncomes -> incomeSourceLabels["MONTH"] ?: ""
                is IncomeSource.CategoryTotal -> s.category.ifBlank { "Categoría" }
                is IncomeSource.RecurringMonth -> incomeSourceLabels["RECURRING"] ?: ""
            }
            NodeStyle(
                "${index + 1} · Ingreso",
                Icons.Default.AccountBalanceWallet,
                primary,
                "${node.label} · $src"
            )
        }
        is FormulaNode -> NodeStyle(
            "${index + 1} · Reparto",
            Icons.AutoMirrored.Filled.CallSplit,
            tertiary,
            node.splits.joinToString(" + ") {
                when (it) {
                    is com.fintrack.app.domain.Split.Percent -> "${it.percent}% ${it.label}"
                    is com.fintrack.app.domain.Split.FixedAmount -> "$${it.amount} ${it.label}"
                }
            }.ifBlank { "Sin repartos" }
        )
        is ConditionNode -> NodeStyle(
            "${index + 1} · Condición",
            Icons.Default.Drafts,
            tertiary,
            "Si monto ${operatorSymbol(node)} ${node.threshold}"
        )
        is EnvelopeNode -> NodeStyle(
            "${index + 1} · Sobre",
            Icons.Default.Savings,
            primary,
            node.label + (node.category?.let { " · $it" } ?: "")
        )
    }
}

private fun operatorSymbol(node: ConditionNode): String = when (node.operator) {
    com.fintrack.app.domain.ConditionOperator.GREATER_THAN -> ">"
    com.fintrack.app.domain.ConditionOperator.LESS_THAN -> "<"
    com.fintrack.app.domain.ConditionOperator.EQUALS -> "="
}

/** Ramas Sí/No de una condición con sus sobres. */
private fun branchLabels(nodes: List<FlowNode>): String =
    nodes.mapNotNull { (it as? EnvelopeNode)?.label?.ifBlank { null } }
        .ifEmpty { listOf("${nodes.size} nodo(s)") }
        .joinToString(", ")

/** Caja de nodo con puertos y resaltado de ejecución/selección. */
@Composable
private fun FlowNodeBox(
    index: Int,
    node: FlowNode,
    selected: Boolean,
    executed: Boolean,
    takenBranch: String?,
    onClick: () -> Unit
) {
    val style = nodeStyle(node, index)
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        executed -> MaterialTheme.colorScheme.tertiary
        else -> Color.Transparent
    }
    Card(
        modifier = Modifier
            .width(168.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            borderColor
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (executed) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = style.accent) {
                        Icon(
                            style.icon,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(6.dp).size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        style.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    style.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (node is ConditionNode) {
                    Spacer(modifier = Modifier.height(6.dp))
                    BranchChip(
                        text = "✓ Sí: ${branchLabels(node.trueBranch)}",
                        highlighted = takenBranch == "si"
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    BranchChip(
                        text = "✗ No: ${branchLabels(node.falseBranch)}",
                        highlighted = takenBranch == "no"
                    )
                }
            }
            // Puertos de entrada/salida.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-5).dp)
                    .size(10.dp)
                    .background(style.accent, CircleShape)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 5.dp)
                    .size(10.dp)
                    .background(style.accent, CircleShape)
            )
        }
    }
}

@Composable
private fun BranchChip(text: String, highlighted: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
