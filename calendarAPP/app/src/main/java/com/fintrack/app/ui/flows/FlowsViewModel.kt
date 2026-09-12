package com.fintrack.app.ui.flows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrack.app.data.FlowStore
import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.repository.TransactionRepository
import com.fintrack.app.domain.Allocation
import com.fintrack.app.domain.ConditionNode
import com.fintrack.app.domain.ConditionOperator
import com.fintrack.app.domain.EnvelopeNode
import com.fintrack.app.domain.FlowEngine
import com.fintrack.app.domain.FlowNode
import com.fintrack.app.domain.FlowValidationException
import com.fintrack.app.domain.FormulaNode
import com.fintrack.app.domain.IncomeNode
import com.fintrack.app.domain.IncomeSource
import com.fintrack.app.domain.MoneyFlow
import com.fintrack.app.domain.Split
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class FlowsUiState(
    val flowName: String = "",
    val nodes: List<FlowNode> = emptyList(),
    val allocations: List<Allocation> = emptyList(),
    val totalAssigned: Double = 0.0,
    val isLoading: Boolean = false,
    val isRunning: Boolean = false,
    val error: String? = null,
    val needsLogin: Boolean = false,
    val savedMessage: String? = null
)

class FlowsViewModel(
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository,
    private val flowStore: FlowStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(FlowsUiState())
    val uiState: StateFlow<FlowsUiState> = _uiState.asStateFlow()

    private val userId get() = authRepository.currentUserId ?: ""

    init {
        loadFlow()
    }

    fun loadFlow() {
        if (userId.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                needsLogin = true,
                error = "Inicia sesion para usar tus flujos."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, needsLogin = false, error = null)
            try {
                val flow = flowStore.snapshot()
                _uiState.value = _uiState.value.copy(
                    flowName = flow.name,
                    nodes = flow.nodes,
                    allocations = emptyList(),
                    totalAssigned = 0.0,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun runFlow() {
        if (userId.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                needsLogin = true,
                error = "Inicia sesion para ejecutar el flujo."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRunning = true, error = null)
            try {
                val transactions = transactionRepository.getTransactions(userId)
                val allocations = FlowEngine.evaluate(_uiState.value.nodes, transactions)
                _uiState.value = _uiState.value.copy(
                    allocations = allocations,
                    totalAssigned = allocations.sumOf { it.amount },
                    isRunning = false
                )
            } catch (e: FlowValidationException) {
                _uiState.value = _uiState.value.copy(isRunning = false, error = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    error = "No se pudo ejecutar: ${e.message?.take(150)}"
                )
            }
        }
    }

    fun saveFlow() {
        if (userId.isEmpty()) {
            _uiState.value = _uiState.value.copy(needsLogin = true)
            return
        }
        viewModelScope.launch {
            try {
                flowStore.save(MoneyFlow(_uiState.value.flowName, _uiState.value.nodes))
                _uiState.value = _uiState.value.copy(savedMessage = "Flujo guardado.")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "No se pudo guardar: ${e.message?.take(150)}"
                )
            }
        }
    }

    fun resetToSample() {
        val sample = FlowStore.sampleFlow()
        _uiState.value = _uiState.value.copy(
            flowName = sample.name,
            nodes = sample.nodes,
            allocations = emptyList(),
            totalAssigned = 0.0,
            error = null,
            savedMessage = null
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSavedMessage() {
        _uiState.value = _uiState.value.copy(savedMessage = null)
    }

    fun setFlowName(name: String) {
        _uiState.value = _uiState.value.copy(flowName = name, savedMessage = null)
    }

    fun addNode(kind: FlowNodeKind) {
        val node: FlowNode = when (kind) {
            FlowNodeKind.INCOME -> IncomeNode(
                id = newId(),
                label = "Ingreso",
                source = IncomeSource.Fixed(1000.0)
            )
            FlowNodeKind.FORMULA -> FormulaNode(
                id = newId(),
                splits = listOf(Split.Percent(label = "Parte", percent = 50.0))
            )
            FlowNodeKind.CONDITION -> ConditionNode(
                id = newId(),
                operator = ConditionOperator.GREATER_THAN,
                threshold = 500.0,
                trueBranch = listOf(
                    EnvelopeNode(id = newId(), label = "Sobre si", category = "Ahorro")
                ),
                falseBranch = listOf(
                    EnvelopeNode(id = newId(), label = "Sobre no", category = "Otros")
                )
            )
            FlowNodeKind.ENVELOPE -> EnvelopeNode(id = newId(), label = "Sobre", category = "Ahorro")
        }
        _uiState.value = _uiState.value.copy(
            nodes = _uiState.value.nodes + node,
            allocations = emptyList(),
            totalAssigned = 0.0,
            savedMessage = null
        )
    }

    fun removeNode(nodeId: String) {
        _uiState.value = _uiState.value.copy(
            nodes = _uiState.value.nodes.filterNot { it.id == nodeId },
            allocations = emptyList(),
            totalAssigned = 0.0,
            savedMessage = null
        )
    }

    fun updateIncome(nodeId: String, label: String, source: IncomeSource) {
        _uiState.value = _uiState.value.copy(
            nodes = _uiState.value.nodes.map { node ->
                if (node is IncomeNode && node.id == nodeId) {
                    node.copy(label = label, source = source)
                } else {
                    node
                }
            },
            savedMessage = null
        )
    }

    fun updateSplit(nodeId: String, index: Int, split: Split) {
        _uiState.value = _uiState.value.copy(
            nodes = _uiState.value.nodes.map { node ->
                if (node is FormulaNode && node.id == nodeId) {
                    node.copy(splits = node.splits.mapIndexed { i, current ->
                        if (i == index) split else current
                    })
                } else {
                    node
                }
            },
            savedMessage = null
        )
    }

    fun addSplit(nodeId: String) {
        _uiState.value = _uiState.value.copy(
            nodes = _uiState.value.nodes.map { node ->
                if (node is FormulaNode && node.id == nodeId) {
                    node.copy(splits = node.splits + Split.Percent(label = "Parte", percent = 10.0))
                } else {
                    node
                }
            },
            savedMessage = null
        )
    }

    fun removeSplit(nodeId: String, index: Int) {
        _uiState.value = _uiState.value.copy(
            nodes = _uiState.value.nodes.map { node ->
                if (node is FormulaNode && node.id == nodeId) {
                    node.copy(splits = node.splits.filterIndexed { i, _ -> i != index })
                } else {
                    node
                }
            },
            savedMessage = null
        )
    }

    fun updateCondition(nodeId: String, operator: ConditionOperator, threshold: Double) {
        _uiState.value = _uiState.value.copy(
            nodes = _uiState.value.nodes.map { node ->
                if (node is ConditionNode && node.id == nodeId) {
                    node.copy(operator = operator, threshold = threshold)
                } else {
                    node
                }
            },
            savedMessage = null
        )
    }

    fun updateBranchEnvelope(nodeId: String, branchIsTrue: Boolean, index: Int, envelope: EnvelopeNode) {
        _uiState.value = _uiState.value.copy(
            nodes = _uiState.value.nodes.map { node ->
                if (node is ConditionNode && node.id == nodeId) {
                    if (branchIsTrue) {
                        node.copy(trueBranch = node.trueBranch.mapIndexed { i, current ->
                            if (i == index && current is EnvelopeNode) envelope.copy(id = current.id) else current
                        })
                    } else {
                        node.copy(falseBranch = node.falseBranch.mapIndexed { i, current ->
                            if (i == index && current is EnvelopeNode) envelope.copy(id = current.id) else current
                        })
                    }
                } else {
                    node
                }
            },
            savedMessage = null
        )
    }

    fun updateEnvelope(nodeId: String, label: String, category: String?) {
        _uiState.value = _uiState.value.copy(
            nodes = _uiState.value.nodes.map { node ->
                if (node is EnvelopeNode && node.id == nodeId) {
                    node.copy(label = label, category = category?.ifBlank { null })
                } else {
                    node
                }
            },
            savedMessage = null
        )
    }

    private fun newId(): String = UUID.randomUUID().toString().take(8)
}

enum class FlowNodeKind {
    INCOME,
    FORMULA,
    CONDITION,
    ENVELOPE
}
