/**
 * smart-financial-assistant.js
 * Asistente Financiero Inteligente en Tiempo Real
 * 
 * Se integra con los formularios de creación para:
 * - Analizar impacto financiero en tiempo real
 * - Sugerir montos óptimos
 * - Recomendar vinculaciones a ingresos
 * - Alertar sobre riesgos
 * - Ajustar prioridades automáticamente
 */

import { supabase } from './supabase-client.js';
import { getIncomePatterns, getExpensePatterns } from './patterns.js';
import { getPlans } from './plans-v2.js';
import { getSavingsPatterns } from './savings.js';

// ============================================================================
// CACHE Y ESTADO GLOBAL
// ============================================================================

let financialState = {
    loaded: false,
    incomes: [],
    expenses: [],
    plans: [],
    savings: [],
    totals: {
        monthlyIncome: 0,
        monthlyExpenses: 0,
        monthlyBalance: 0,
        totalSavings: 0,
        totalPlansTarget: 0,
        totalPlansAccumulated: 0
    },
    allocations: {
        necessities: 0,
        wants: 0,
        savings: 0,
        debt: 0,
        available: 0
    },
    lastUpdate: null
};

// Constantes de la app bancaria
const SMART_LIMITS = {
    // Límites de seguridad
    MIN_EMERGENCY_FUND_MONTHS: 3,
    MAX_SINGLE_EXPENSE_PERCENT: 30,      // Un solo gasto no debe exceder 30% del ingreso
    MAX_TOTAL_COMMITMENTS_PERCENT: 80,   // No comprometer más del 80% del ingreso
    MIN_SAVINGS_RATE: 10,                // Mínimo 10% de ahorro
    IDEAL_SAVINGS_RATE: 20,              // Ideal 20% de ahorro
    
    // Regla 50/30/20
    NECESSITIES_MAX: 50,
    WANTS_MAX: 30,
    SAVINGS_MIN: 20,
    
    // Prioridades
    PRIORITY_WEIGHTS: {
        1: 1.5,   // Urgente - más peso
        2: 1.2,   // Alta
        3: 1.0,   // Media
        4: 0.8,   // Baja
        5: 0.5    // Opcional
    }
};

const CATEGORY_CLASSIFICATION = {
    necessities: [
        'vivienda', 'renta', 'alquiler', 'hipoteca', 'luz', 'agua', 'gas', 
        'electricidad', 'internet', 'teléfono', 'celular', 'transporte',
        'gasolina', 'comida', 'alimentación', 'supermercado', 'salud',
        'médico', 'farmacia', 'seguro', 'servicios', 'educación'
    ],
    wants: [
        'entretenimiento', 'restaurante', 'cine', 'streaming', 'netflix',
        'spotify', 'ropa', 'compras', 'viaje', 'vacaciones', 'hobby',
        'gimnasio', 'deporte', 'suscripción', 'regalo', 'lujo'
    ],
    savings: [
        'ahorro', 'inversión', 'retiro', 'emergencia', 'meta', 'fondo'
    ],
    debt: [
        'deuda', 'préstamo', 'crédito', 'tarjeta', 'financiamiento'
    ]
};

// ============================================================================
// CARGA DE DATOS FINANCIEROS
// ============================================================================

/**
 * Carga todos los datos financieros del usuario
 */
export async function loadFinancialState(forceRefresh = false) {
    // Si ya está cargado y no han pasado 5 minutos, usar cache
    if (financialState.loaded && !forceRefresh) {
        const fiveMinutes = 5 * 60 * 1000;
        if (Date.now() - financialState.lastUpdate < fiveMinutes) {
            return financialState;
        }
    }
    
    try {
        const [incomes, expenses, plans, savings] = await Promise.all([
            getIncomePatterns(true),
            getExpensePatterns(true),
            getPlans({ status: 'active' }),
            getSavingsPatterns(true)
        ]);
        
        financialState.incomes = incomes || [];
        financialState.expenses = expenses || [];
        financialState.plans = plans || [];
        financialState.savings = savings || [];
        
        // Calcular totales
        calculateTotals();
        calculateAllocations();
        
        financialState.loaded = true;
        financialState.lastUpdate = Date.now();
        
        return financialState;
    } catch (error) {
        console.error('Error loading financial state:', error);
        return financialState;
    }
}

/**
 * Calcula los totales mensuales
 */
function calculateTotals() {
    // Ingresos mensuales
    financialState.totals.monthlyIncome = financialState.incomes.reduce((sum, p) => {
        return sum + toMonthlyAmount(p.base_amount, p.frequency, p.interval);
    }, 0);
    
    // Gastos mensuales
    financialState.totals.monthlyExpenses = financialState.expenses.reduce((sum, p) => {
        return sum + toMonthlyAmount(p.base_amount, p.frequency, p.interval);
    }, 0);
    
    // Balance mensual
    financialState.totals.monthlyBalance = 
        financialState.totals.monthlyIncome - financialState.totals.monthlyExpenses;
    
    // Total en ahorros
    financialState.totals.totalSavings = financialState.savings.reduce((sum, s) => {
        return sum + (parseFloat(s.current_balance) || 0);
    }, 0);
    
    // Planes
    financialState.totals.totalPlansTarget = financialState.plans.reduce((sum, p) => {
        return sum + (parseFloat(p.target_amount) || 0);
    }, 0);
    
    financialState.totals.totalPlansAccumulated = financialState.plans.reduce((sum, p) => {
        return sum + (parseFloat(p.current_amount) || 0);
    }, 0);
}

/**
 * Calcula la distribución por categorías
 */
function calculateAllocations() {
    const byGroup = { necessities: 0, wants: 0, savings: 0, debt: 0 };
    
    financialState.expenses.forEach(expense => {
        const group = classifyCategory(expense.category);
        const monthly = toMonthlyAmount(expense.base_amount, expense.frequency, expense.interval);
        byGroup[group] += monthly;
    });
    
    financialState.allocations = {
        ...byGroup,
        available: financialState.totals.monthlyBalance
    };
}

/**
 * Convierte cualquier frecuencia a monto mensual
 */
function toMonthlyAmount(amount, frequency, interval = 1) {
    const base = parseFloat(amount) || 0;
    const int = parseInt(interval) || 1;
    
    switch (frequency?.toLowerCase()) {
        case 'daily': return (base / int) * 30;
        case 'weekly': return (base / int) * 4.33;
        case 'biweekly': return (base / int) * 2.17;
        case 'monthly': return base / int;
        case 'quarterly': return base / (int * 3);
        case 'semiannual': return base / (int * 6);
        case 'annual': case 'yearly': return base / (int * 12);
        case 'once': return base / 12; // Prorratear en 12 meses
        default: return base;
    }
}

/**
 * Clasifica una categoría en grupo (necessities, wants, savings, debt)
 */
function classifyCategory(category) {
    if (!category) return 'wants';
    const cat = category.toLowerCase();
    
    for (const [group, keywords] of Object.entries(CATEGORY_CLASSIFICATION)) {
        if (keywords.some(k => cat.includes(k))) {
            return group;
        }
    }
    return 'wants'; // Default
}

// ============================================================================
// CLASE PRINCIPAL: ASISTENTE FINANCIERO INTELIGENTE
// ============================================================================

/**
 * Clase wrapper para facilitar el uso del asistente en formularios
 */
export class SmartFinancialAssistant {
    constructor() {
        this.initialized = false;
    }
    
    /**
     * Pre-carga el estado financiero
     */
    async preloadFinancialState() {
        await loadFinancialState(true);
        this.initialized = true;
        return financialState;
    }
    
    /**
     * Obtiene análisis para un nuevo gasto
     */
    async getExpenseAnalysis(amount, category, frequency = 'monthly', priority = 3) {
        if (!this.initialized) await this.preloadFinancialState();
        
        const fullAnalysis = await analyzeNewExpense({
            amount,
            category,
            frequency,
            priority
        });
        
        // Simplificar para el panel
        return {
            viability: fullAnalysis.viability?.level || 'caution',
            reason: fullAnalysis.viability?.reason || '',
            currentBalance: financialState.totals.monthlyBalance,
            projectedBalance: financialState.totals.monthlyBalance - toMonthlyAmount(amount, frequency),
            percentOfIncome: fullAnalysis.impact?.percentOfIncome || 0,
            categoryGroup: classifyCategory(category),
            recommendations: fullAnalysis.suggestions?.map(s => s.message || s) || [],
            alerts: fullAnalysis.alerts || []
        };
    }
    
    /**
     * Obtiene análisis para un nuevo plan/meta
     */
    async getPlanAnalysis(targetAmount, targetDate, priority = 3, name = '') {
        if (!this.initialized) await this.preloadFinancialState();
        
        const fullAnalysis = await analyzeNewPlan({
            name,
            targetAmount,
            targetDate,
            priority
        });
        
        const { monthlyIncome, monthlyBalance } = financialState.totals;
        const timeline = fullAnalysis.timeline || {};
        
        // Calcular tiempo estimado basado en ahorro disponible
        const availableForSaving = Math.max(0, monthlyBalance * 0.3); // 30% del balance
        const estimatedMonths = availableForSaving > 0 
            ? Math.ceil(targetAmount / availableForSaving) 
            : 999;
        
        // Determinar viabilidad
        let viability = 'good';
        let reason = '';
        
        if (timeline.requiredMonthly > monthlyBalance) {
            viability = 'not_recommended';
            reason = 'El ahorro mensual requerido excede tu balance disponible';
        } else if (timeline.percentOfBalance > 80) {
            viability = 'caution';
            reason = 'Consumiría la mayor parte de tu balance mensual';
        } else if (timeline.percentOfBalance > 50) {
            viability = 'good';
            reason = 'Meta alcanzable pero requiere disciplina';
        } else {
            viability = 'excellent';
            reason = 'Meta muy alcanzable con tu balance actual';
        }
        
        // Ver si puede cumplir fecha objetivo
        let canMeetDeadline = true;
        let requiredMonthlySaving = 0;
        
        if (targetDate) {
            const months = monthsUntil(targetDate);
            requiredMonthlySaving = targetAmount / months;
            canMeetDeadline = requiredMonthlySaving <= monthlyBalance;
        }
        
        return {
            viability,
            reason,
            estimatedMonths,
            suggestedMonthlySaving: availableForSaving,
            targetDate,
            canMeetDeadline,
            requiredMonthlySaving,
            monthlyImpactPercent: monthlyIncome > 0 
                ? (timeline.requiredMonthly / monthlyIncome) * 100 
                : 0,
            recommendations: fullAnalysis.suggestions?.map(s => s.message || s) || []
        };
    }
    
    /**
     * Obtiene análisis para un nuevo patrón de ahorro
     */
    async getSavingsAnalysis(amount, frequency = 'monthly') {
        if (!this.initialized) await this.preloadFinancialState();
        
        const fullAnalysis = await analyzeNewSavings({
            amount,
            frequency
        });
        
        const { monthlyIncome, monthlyBalance, totalSavings } = financialState.totals;
        const monthlyAmount = toMonthlyAmount(amount, frequency);
        
        // Calcular tasas de ahorro
        const currentSavingsRate = monthlyIncome > 0 
            ? (financialState.allocations.savings / monthlyIncome) * 100 
            : 0;
        const newSavingsRate = monthlyIncome > 0 
            ? ((financialState.allocations.savings + monthlyAmount) / monthlyIncome) * 100 
            : 0;
        
        // Determinar viabilidad
        let viability = 'good';
        let reason = '';
        
        if (monthlyAmount > monthlyBalance) {
            viability = 'not_recommended';
            reason = 'El monto excede tu balance disponible';
        } else if (monthlyAmount > monthlyBalance * 0.5) {
            viability = 'caution';
            reason = 'Consume más de la mitad de tu balance';
        } else if (newSavingsRate >= 20) {
            viability = 'excellent';
            reason = '¡Excelente! Alcanzarás 20% de tasa de ahorro';
        } else {
            viability = 'good';
            reason = 'Buen paso hacia mejor salud financiera';
        }
        
        return {
            viability,
            reason,
            currentSavingsRate,
            newSavingsRate,
            remainingBalance: monthlyBalance - monthlyAmount,
            recommendations: fullAnalysis.suggestions?.map(s => s.message || s) || []
        };
    }
    
    /**
     * Obtiene análisis para un movimiento rápido
     */
    async getMovementAnalysis(amount, type = 'expense', category = '') {
        if (!this.initialized) await this.preloadFinancialState();
        
        const { monthlyBalance } = financialState.totals;
        const numAmount = parseFloat(amount) || 0;
        
        let viability = 'good';
        let reason = '';
        let suggestions = [];
        
        if (type === 'income') {
            viability = 'excellent';
            reason = 'Todo ingreso mejora tu situación financiera';
            suggestions = [
                'Considera destinar parte a ahorro',
                'Revisa si puedes aumentar tu fondo de emergencia'
            ];
        } else {
            if (numAmount > monthlyBalance) {
                viability = 'not_recommended';
                reason = 'Este gasto te dejará en negativo este mes';
            } else if (numAmount > monthlyBalance * 0.5) {
                viability = 'caution';
                reason = 'Gasto significativo, considera si es necesario';
            } else {
                viability = 'good';
                reason = 'Gasto manejable dentro de tu presupuesto';
            }
            
            suggestions = [
                classifyCategory(category) === 'wants' 
                    ? 'Este parece un gasto opcional' 
                    : 'Parece un gasto necesario'
            ];
        }
        
        return {
            viability,
            reason,
            type,
            balanceBefore: monthlyBalance,
            balanceAfter: type === 'income' 
                ? monthlyBalance + numAmount 
                : monthlyBalance - numAmount,
            suggestions
        };
    }
}

// ============================================================================
// ANÁLISIS INTELIGENTE PARA GASTOS
// ============================================================================

/**
 * Analiza un nuevo gasto antes de crearlo
 * @returns Objeto con análisis, sugerencias y alertas
 */
export async function analyzeNewExpense(expenseData) {
    await loadFinancialState();
    
    const { name, amount, frequency, category, priority = 3 } = expenseData;
    const monthlyAmount = toMonthlyAmount(amount, frequency);
    const group = classifyCategory(category);
    
    const analysis = {
        // Datos básicos
        input: { name, amount, frequency, category, priority, monthlyAmount, group },
        
        // Viabilidad
        feasibility: { score: 100, level: 'excellent', issues: [] },
        
        // Impacto
        impact: {},
        
        // Sugerencias
        suggestions: [],
        
        // Alertas
        alerts: [],
        
        // Monto óptimo sugerido
        optimalAmount: null,
        
        // Fuentes de ingreso recomendadas
        recommendedSources: [],
        
        // Comparación con gastos similares
        comparison: null
    };
    
    const { monthlyIncome, monthlyExpenses, monthlyBalance } = financialState.totals;
    
    // ==================== ANÁLISIS DE VIABILIDAD ====================
    
    // 1. ¿El gasto excede el balance disponible?
    if (monthlyAmount > monthlyBalance) {
        analysis.feasibility.score -= 40;
        analysis.feasibility.issues.push('deficit');
        analysis.alerts.push({
            type: 'critical',
            icon: '🚨',
            title: 'Generará déficit mensual',
            message: `Este gasto de ${formatCurrency(monthlyAmount)}/mes excede tu balance disponible de ${formatCurrency(monthlyBalance)}/mes.`,
            suggestion: `Reduce el monto a máximo ${formatCurrency(Math.max(0, monthlyBalance * 0.8))}/mes`
        });
    }
    
    // 2. ¿Es más del 30% del ingreso?
    const percentOfIncome = (monthlyAmount / monthlyIncome) * 100;
    if (percentOfIncome > SMART_LIMITS.MAX_SINGLE_EXPENSE_PERCENT) {
        analysis.feasibility.score -= 25;
        analysis.feasibility.issues.push('too_high');
        analysis.alerts.push({
            type: 'warning',
            icon: '⚠️',
            title: 'Gasto muy elevado',
            message: `Representa el ${percentOfIncome.toFixed(1)}% de tus ingresos. Lo máximo recomendado es ${SMART_LIMITS.MAX_SINGLE_EXPENSE_PERCENT}%.`,
            suggestion: `Monto máximo recomendado: ${formatCurrency(monthlyIncome * 0.3)}/mes`
        });
    }
    
    // 3. ¿Excede el límite de su categoría?
    const currentGroupTotal = financialState.allocations[group] || 0;
    const newGroupTotal = currentGroupTotal + monthlyAmount;
    const groupLimits = {
        necessities: SMART_LIMITS.NECESSITIES_MAX,
        wants: SMART_LIMITS.WANTS_MAX,
        savings: 100,
        debt: 35
    };
    const groupLimit = groupLimits[group] || 30;
    const newGroupPercent = (newGroupTotal / monthlyIncome) * 100;
    
    if (newGroupPercent > groupLimit) {
        analysis.feasibility.score -= 15;
        analysis.feasibility.issues.push('category_exceeded');
        analysis.alerts.push({
            type: 'info',
            icon: '📊',
            title: `Excede límite de ${getGroupName(group)}`,
            message: `Con este gasto, ${getGroupName(group)} sería ${newGroupPercent.toFixed(1)}% del ingreso. El límite ideal es ${groupLimit}%.`,
            suggestion: `Espacio disponible en ${getGroupName(group)}: ${formatCurrency(Math.max(0, (monthlyIncome * groupLimit / 100) - currentGroupTotal))}/mes`
        });
    }
    
    // 4. ¿Compromete demasiado del ingreso total?
    const newTotalExpenses = monthlyExpenses + monthlyAmount;
    const commitmentPercent = (newTotalExpenses / monthlyIncome) * 100;
    if (commitmentPercent > SMART_LIMITS.MAX_TOTAL_COMMITMENTS_PERCENT) {
        analysis.feasibility.score -= 20;
        analysis.feasibility.issues.push('over_committed');
        analysis.alerts.push({
            type: 'warning',
            icon: '💸',
            title: 'Compromiso excesivo',
            message: `Tendrías comprometido el ${commitmentPercent.toFixed(1)}% de tus ingresos. Se recomienda no exceder ${SMART_LIMITS.MAX_TOTAL_COMMITMENTS_PERCENT}%.`
        });
    }
    
    // 5. ¿Afecta la tasa de ahorro?
    const currentSavingsRate = (monthlyBalance / monthlyIncome) * 100;
    const newSavingsRate = ((monthlyBalance - monthlyAmount) / monthlyIncome) * 100;
    if (newSavingsRate < SMART_LIMITS.MIN_SAVINGS_RATE && currentSavingsRate >= SMART_LIMITS.MIN_SAVINGS_RATE) {
        analysis.feasibility.score -= 15;
        analysis.alerts.push({
            type: 'warning',
            icon: '🐷',
            title: 'Reduce tu capacidad de ahorro',
            message: `Tu tasa de ahorro bajaría de ${currentSavingsRate.toFixed(1)}% a ${newSavingsRate.toFixed(1)}%. El mínimo recomendado es ${SMART_LIMITS.MIN_SAVINGS_RATE}%.`
        });
    }
    
    // Determinar nivel de viabilidad
    if (analysis.feasibility.score >= 80) {
        analysis.feasibility.level = 'excellent';
    } else if (analysis.feasibility.score >= 60) {
        analysis.feasibility.level = 'good';
    } else if (analysis.feasibility.score >= 40) {
        analysis.feasibility.level = 'caution';
    } else {
        analysis.feasibility.level = 'not_recommended';
    }
    
    // ==================== CÁLCULO DE IMPACTO ====================
    
    analysis.impact = {
        currentBalance: monthlyBalance,
        newBalance: monthlyBalance - monthlyAmount,
        balanceChange: -monthlyAmount,
        currentSavingsRate: currentSavingsRate,
        newSavingsRate: newSavingsRate,
        savingsRateChange: newSavingsRate - currentSavingsRate,
        percentOfIncome: percentOfIncome,
        categoryBefore: currentGroupTotal,
        categoryAfter: newGroupTotal,
        categoryPercent: newGroupPercent
    };
    
    // ==================== MONTO ÓPTIMO ====================
    
    // Calcular el monto máximo que no cause problemas financieros
    const maxByBalance = Math.max(0, monthlyBalance * 0.7); // 70% del balance disponible
    const maxByCategory = Math.max(0, (monthlyIncome * groupLimit / 100) - currentGroupTotal);
    const maxBySavings = Math.max(0, monthlyBalance - (monthlyIncome * SMART_LIMITS.MIN_SAVINGS_RATE / 100));
    
    const optimalMax = Math.min(maxByBalance, maxByCategory, maxBySavings);
    
    // IMPORTANTE: El monto óptimo solo sugiere REDUCIR si excede el máximo
    // NO sugiere AUMENTAR el gasto (eso no tiene sentido)
    const shouldSuggestReduction = monthlyAmount > optimalMax && optimalMax > 0;
    
    analysis.optimalAmount = {
        suggested: shouldSuggestReduction ? Math.round(optimalMax / 100) * 100 : monthlyAmount,
        max: optimalMax,
        showSuggestion: shouldSuggestReduction, // Solo mostrar si debe reducir
        factors: {
            byBalance: maxByBalance,
            byCategory: maxByCategory,
            bySavings: maxBySavings
        },
        message: shouldSuggestReduction 
            ? `Considera reducir a ${formatCurrency(optimalMax)}/mes para mantener finanzas saludables`
            : `Tu monto de ${formatCurrency(monthlyAmount)}/mes está dentro del rango óptimo`
    };
    
    // ==================== FUENTES DE INGRESO RECOMENDADAS ====================
    
    if (monthlyAmount > 0 && financialState.incomes.length > 0) {
        // Ordenar ingresos por disponibilidad y monto
        const sortedIncomes = [...financialState.incomes]
            .map(inc => {
                const incMonthly = toMonthlyAmount(inc.base_amount, inc.frequency, inc.interval);
                // Calcular cuánto está ya comprometido de este ingreso
                const committed = calculateIncomeCommitment(inc.id);
                return {
                    ...inc,
                    monthlyAmount: incMonthly,
                    committed,
                    available: incMonthly - committed,
                    availablePercent: ((incMonthly - committed) / incMonthly) * 100
                };
            })
            .filter(inc => inc.available > 0)
            .sort((a, b) => b.available - a.available);
        
        // Distribuir el gasto entre fuentes
        let remaining = monthlyAmount;
        analysis.recommendedSources = [];
        
        for (const income of sortedIncomes) {
            if (remaining <= 0) break;
            
            const allocation = Math.min(remaining, income.available * 0.8); // No usar más del 80%
            if (allocation > 0) {
                analysis.recommendedSources.push({
                    id: income.id,
                    name: income.name,
                    totalMonthly: income.monthlyAmount,
                    available: income.available,
                    suggestedAllocation: allocation,
                    percentOfSource: (allocation / income.monthlyAmount) * 100
                });
                remaining -= allocation;
            }
        }
        
        if (remaining > 0) {
            analysis.alerts.push({
                type: 'info',
                icon: '💡',
                title: 'Fuentes insuficientes',
                message: `Faltarían ${formatCurrency(remaining)}/mes por asignar a fuentes de ingreso específicas.`
            });
        }
    }
    
    // ==================== COMPARACIÓN CON GASTOS SIMILARES ====================
    
    const similarExpenses = financialState.expenses.filter(e => 
        classifyCategory(e.category) === group
    );
    
    if (similarExpenses.length > 0) {
        const avgSimilar = similarExpenses.reduce((sum, e) => 
            sum + toMonthlyAmount(e.base_amount, e.frequency, e.interval), 0
        ) / similarExpenses.length;
        
        analysis.comparison = {
            categoryCount: similarExpenses.length,
            categoryAverage: avgSimilar,
            isAboveAverage: monthlyAmount > avgSimilar,
            percentVsAverage: ((monthlyAmount - avgSimilar) / avgSimilar) * 100
        };
        
        if (monthlyAmount > avgSimilar * 2) {
            analysis.suggestions.push({
                icon: '📈',
                text: `Este gasto es ${((monthlyAmount / avgSimilar) * 100 - 100).toFixed(0)}% mayor que tu promedio en ${getGroupName(group)}`
            });
        }
    }
    
    // ==================== SUGERENCIAS INTELIGENTES ====================
    
    // Sugerencia de ajuste de prioridad
    if (group === 'wants' && priority <= 2) {
        analysis.suggestions.push({
            icon: '🎯',
            text: 'Considera bajar la prioridad ya que es un gasto de deseos, no de necesidad'
        });
    }
    
    if (group === 'necessities' && priority >= 4) {
        analysis.suggestions.push({
            icon: '⚡',
            text: 'Considera subir la prioridad ya que es un gasto de necesidad básica'
        });
    }
    
    // Sugerencia de frecuencia
    if (frequency === 'monthly' && monthlyAmount > monthlyIncome * 0.15) {
        analysis.suggestions.push({
            icon: '📅',
            text: 'Podrías dividir este gasto en pagos quincenales para mejor flujo de efectivo'
        });
    }
    
    // Si hay alertas críticas, agregar sugerencia de revisión
    if (analysis.alerts.some(a => a.type === 'critical')) {
        analysis.suggestions.unshift({
            icon: '🔄',
            text: 'Revisa tu presupuesto general antes de agregar este gasto'
        });
    }
    
    return analysis;
}

/**
 * Calcula cuánto está comprometido de un ingreso específico
 */
function calculateIncomeCommitment(incomeId) {
    // Sumar todos los gastos vinculados a este ingreso
    // Por ahora, distribuir proporcionalmente entre todos los ingresos
    const totalIncome = financialState.totals.monthlyIncome;
    const income = financialState.incomes.find(i => i.id === incomeId);
    if (!income || totalIncome === 0) return 0;
    
    const incomeMonthly = toMonthlyAmount(income.base_amount, income.frequency, income.interval);
    const proportion = incomeMonthly / totalIncome;
    
    return financialState.totals.monthlyExpenses * proportion;
}

// ============================================================================
// ANÁLISIS INTELIGENTE PARA PLANES/METAS
// ============================================================================

/**
 * Analiza un nuevo plan/meta antes de crearlo
 */
export async function analyzeNewPlan(planData) {
    await loadFinancialState();
    
    const { name, targetAmount, targetDate, priority = 3, incomeSources = [] } = planData;
    const target = parseFloat(targetAmount) || 0;
    
    const analysis = {
        input: { name, targetAmount: target, targetDate, priority },
        feasibility: { score: 100, level: 'excellent', issues: [] },
        timeline: {},
        suggestions: [],
        alerts: [],
        recommendedSources: [],
        optimalContribution: null
    };
    
    const { monthlyIncome, monthlyBalance } = financialState.totals;
    
    // ==================== ANÁLISIS DE TIMELINE ====================
    
    let monthsAvailable = 12; // Default
    if (targetDate) {
        const today = new Date();
        const target = new Date(targetDate);
        monthsAvailable = Math.max(1, 
            (target.getFullYear() - today.getFullYear()) * 12 + 
            (target.getMonth() - today.getMonth())
        );
    }
    
    const requiredMonthly = target / monthsAvailable;
    const percentOfIncome = (requiredMonthly / monthlyIncome) * 100;
    const percentOfBalance = monthlyBalance > 0 ? (requiredMonthly / monthlyBalance) * 100 : 999;
    
    analysis.timeline = {
        monthsAvailable,
        requiredMonthly,
        percentOfIncome,
        percentOfBalance,
        targetDate: targetDate || null
    };
    
    // ==================== VIABILIDAD ====================
    
    // 1. ¿Requiere más del balance disponible?
    if (requiredMonthly > monthlyBalance) {
        analysis.feasibility.score -= 40;
        analysis.feasibility.issues.push('exceeds_balance');
        analysis.alerts.push({
            type: 'critical',
            icon: '🚨',
            title: 'Meta no alcanzable en el plazo',
            message: `Necesitas ${formatCurrency(requiredMonthly)}/mes pero solo tienes ${formatCurrency(monthlyBalance)}/mes disponible.`,
            suggestion: `Extiende la fecha meta o reduce el objetivo`
        });
    }
    
    // 2. ¿Es más del 50% del balance?
    if (percentOfBalance > 50 && percentOfBalance < 100) {
        analysis.feasibility.score -= 20;
        analysis.alerts.push({
            type: 'warning',
            icon: '⚠️',
            title: 'Meta ambiciosa',
            message: `Consumiría ${percentOfBalance.toFixed(1)}% de tu balance disponible mensual.`
        });
    }
    
    // 3. ¿Afecta otras metas existentes?
    const totalPlansMonthly = financialState.plans.reduce((sum, p) => {
        if (!p.target_date) return sum;
        const months = Math.max(1, monthsUntil(p.target_date));
        const remaining = (p.target_amount || 0) - (p.current_amount || 0);
        return sum + (remaining / months);
    }, 0);
    
    if (totalPlansMonthly + requiredMonthly > monthlyBalance * 0.8) {
        analysis.feasibility.score -= 15;
        analysis.alerts.push({
            type: 'info',
            icon: '🎯',
            title: 'Múltiples metas activas',
            message: `Ya tienes metas que requieren ${formatCurrency(totalPlansMonthly)}/mes. Considera priorizar.`
        });
    }
    
    // Determinar nivel
    if (analysis.feasibility.score >= 80) analysis.feasibility.level = 'excellent';
    else if (analysis.feasibility.score >= 60) analysis.feasibility.level = 'good';
    else if (analysis.feasibility.score >= 40) analysis.feasibility.level = 'caution';
    else analysis.feasibility.level = 'not_recommended';
    
    // ==================== CONTRIBUCIÓN ÓPTIMA ====================
    
    // Calcular cuánto deberías aportar idealmente (15% del balance)
    const idealContribution = monthlyBalance * 0.15;
    const optimalMonths = idealContribution > 0 ? Math.ceil(target / idealContribution) : 999;
    const optimalDate = new Date();
    optimalDate.setMonth(optimalDate.getMonth() + optimalMonths);
    
    // Ajustar por prioridad
    const priorityMultiplier = SMART_LIMITS.PRIORITY_WEIGHTS[priority] || 1;
    const adjustedContribution = idealContribution * priorityMultiplier;
    
    analysis.optimalContribution = {
        ideal: idealContribution,
        adjusted: adjustedContribution,
        priority,
        optimalMonths,
        optimalDate: optimalDate.toISOString().split('T')[0],
        message: targetDate 
            ? requiredMonthly <= adjustedContribution
                ? `✅ Meta alcanzable con ${formatCurrency(requiredMonthly)}/mes`
                : `⚠️ Fecha sugerida: ${formatDate(optimalDate)} (${optimalMonths} meses)`
            : `Sugerimos ${formatCurrency(adjustedContribution)}/mes para alcanzar en ${optimalMonths} meses`
    };
    
    // ==================== FUENTES RECOMENDADAS ====================
    
    if (financialState.incomes.length > 0) {
        // Calcular disponibilidad de cada ingreso
        const availableSources = financialState.incomes.map(inc => {
            const incMonthly = toMonthlyAmount(inc.base_amount, inc.frequency, inc.interval);
            const committed = calculateIncomeCommitment(inc.id);
            const forSavings = (incMonthly - committed) * 0.2; // 20% para ahorro/metas
            
            return {
                id: inc.id,
                name: inc.name,
                monthlyAmount: incMonthly,
                availableForGoals: Math.max(0, forSavings),
                suggestedPercent: 15 * priorityMultiplier // Ajustado por prioridad
            };
        }).filter(s => s.availableForGoals > 0);
        
        analysis.recommendedSources = availableSources;
        
        const totalAvailable = availableSources.reduce((sum, s) => sum + s.availableForGoals, 0);
        if (totalAvailable < requiredMonthly) {
            analysis.suggestions.push({
                icon: '💰',
                text: `Disponible para metas: ${formatCurrency(totalAvailable)}/mes de ${formatCurrency(requiredMonthly)}/mes necesarios`
            });
        }
    }
    
    // ==================== SUGERENCIAS ====================
    
    if (monthsAvailable < 3 && target > monthlyIncome) {
        analysis.suggestions.push({
            icon: '📅',
            text: 'Meta a muy corto plazo. Considera extender la fecha para un ahorro más cómodo'
        });
    }
    
    if (priority >= 4 && percentOfBalance > 30) {
        analysis.suggestions.push({
            icon: '🎯',
            text: 'Si es importante, sube la prioridad para que el sistema le asigne más recursos'
        });
    }
    
    if (financialState.plans.length >= 5) {
        analysis.suggestions.push({
            icon: '📋',
            text: 'Tienes varias metas activas. Enfocarte en menos metas aumenta probabilidad de éxito'
        });
    }
    
    return analysis;
}

// ============================================================================
// ANÁLISIS INTELIGENTE PARA AHORROS
// ============================================================================

/**
 * Analiza una nueva cuenta de ahorro o depósito
 */
export async function analyzeNewSavings(savingsData) {
    await loadFinancialState();
    
    const { name, initialAmount = 0, monthlyContribution = 0, targetAmount, purpose } = savingsData;
    const initial = parseFloat(initialAmount) || 0;
    const monthly = parseFloat(monthlyContribution) || 0;
    const target = parseFloat(targetAmount) || 0;
    
    const analysis = {
        input: { name, initialAmount: initial, monthlyContribution: monthly, targetAmount: target, purpose },
        feasibility: { score: 100, level: 'excellent', issues: [] },
        projections: {},
        suggestions: [],
        alerts: [],
        optimalContribution: null
    };
    
    const { monthlyIncome, monthlyBalance, totalSavings } = financialState.totals;
    const monthlyExpenses = financialState.totals.monthlyExpenses;
    
    // ==================== ANÁLISIS DE FONDO DE EMERGENCIA ====================
    
    const emergencyFundNeeded = monthlyExpenses * SMART_LIMITS.MIN_EMERGENCY_FUND_MONTHS;
    const hasEmergencyFund = totalSavings >= emergencyFundNeeded;
    
    if (!hasEmergencyFund && purpose !== 'emergencia') {
        analysis.alerts.push({
            type: 'info',
            icon: '🛡️',
            title: 'Considera priorizar fondo de emergencia',
            message: `Aún no tienes ${SMART_LIMITS.MIN_EMERGENCY_FUND_MONTHS} meses de gastos ahorrados (${formatCurrency(emergencyFundNeeded)}).`,
            suggestion: 'Se recomienda tener un fondo de emergencia antes de otros ahorros'
        });
    }
    
    // ==================== VIABILIDAD DE CONTRIBUCIÓN ====================
    
    if (monthly > monthlyBalance) {
        analysis.feasibility.score -= 40;
        analysis.feasibility.issues.push('exceeds_balance');
        analysis.alerts.push({
            type: 'critical',
            icon: '🚨',
            title: 'Contribución excede balance',
            message: `No puedes aportar ${formatCurrency(monthly)}/mes con un balance de ${formatCurrency(monthlyBalance)}/mes`
        });
    } else if (monthly > monthlyBalance * 0.5) {
        analysis.feasibility.score -= 15;
        analysis.alerts.push({
            type: 'warning',
            icon: '⚠️',
            title: 'Contribución alta',
            message: `Aportarías ${((monthly / monthlyBalance) * 100).toFixed(0)}% de tu balance disponible`
        });
    }
    
    // Verificar tasa de ahorro resultante
    const currentSavingsRate = (monthlyBalance / monthlyIncome) * 100;
    const totalSavingsCommitment = monthly + getExistingSavingsContributions();
    const newSavingsRate = (totalSavingsCommitment / monthlyIncome) * 100;
    
    if (newSavingsRate > 40) {
        analysis.suggestions.push({
            icon: '💡',
            text: `Ahorrarías ${newSavingsRate.toFixed(0)}% del ingreso. Excelente, pero asegúrate de cubrir necesidades`
        });
    }
    
    // ==================== PROYECCIONES ====================
    
    if (target > 0 && monthly > 0) {
        const monthsToTarget = Math.ceil((target - initial) / monthly);
        const targetDate = new Date();
        targetDate.setMonth(targetDate.getMonth() + monthsToTarget);
        
        analysis.projections = {
            monthsToTarget,
            targetDate: targetDate.toISOString().split('T')[0],
            totalContributed: initial + (monthly * monthsToTarget),
            in3Months: initial + (monthly * 3),
            in6Months: initial + (monthly * 6),
            in12Months: initial + (monthly * 12)
        };
    } else {
        analysis.projections = {
            in3Months: initial + (monthly * 3),
            in6Months: initial + (monthly * 6),
            in12Months: initial + (monthly * 12)
        };
    }
    
    // ==================== CONTRIBUCIÓN ÓPTIMA ====================
    
    // Ideal: 20% del ingreso para ahorro total
    const idealTotalSavings = monthlyIncome * 0.20;
    const currentSavingsContributions = getExistingSavingsContributions();
    const availableForNew = Math.max(0, idealTotalSavings - currentSavingsContributions);
    
    analysis.optimalContribution = {
        ideal: availableForNew,
        minimum: monthlyBalance * 0.1,
        maximum: monthlyBalance * 0.5,
        message: monthly === 0 
            ? `Sugerimos aportar ${formatCurrency(availableForNew)}/mes`
            : monthly <= availableForNew
                ? `✅ Contribución dentro del rango óptimo`
                : `Máximo recomendado: ${formatCurrency(availableForNew)}/mes`
    };
    
    // ==================== SUGERENCIAS ====================
    
    if (purpose === 'emergencia' && target < emergencyFundNeeded) {
        analysis.suggestions.push({
            icon: '🎯',
            text: `Aumenta la meta a ${formatCurrency(emergencyFundNeeded)} (${SMART_LIMITS.MIN_EMERGENCY_FUND_MONTHS} meses de gastos)`
        });
    }
    
    if (initial > 0 && monthly === 0) {
        analysis.suggestions.push({
            icon: '📈',
            text: 'Considera establecer una contribución mensual automática'
        });
    }
    
    if (financialState.savings.length === 0 && purpose !== 'emergencia') {
        analysis.suggestions.unshift({
            icon: '🛡️',
            text: 'Tu primera cuenta de ahorro debería ser un fondo de emergencia'
        });
    }
    
    // Determinar nivel
    if (analysis.feasibility.score >= 80) analysis.feasibility.level = 'excellent';
    else if (analysis.feasibility.score >= 60) analysis.feasibility.level = 'good';
    else if (analysis.feasibility.score >= 40) analysis.feasibility.level = 'caution';
    else analysis.feasibility.level = 'not_recommended';
    
    return analysis;
}

/**
 * Obtiene el total de contribuciones mensuales a ahorros existentes
 */
function getExistingSavingsContributions() {
    return financialState.savings.reduce((sum, s) => {
        return sum + (parseFloat(s.monthly_contribution) || 0);
    }, 0);
}

// ============================================================================
// COMPONENTES UI - PANEL DE ANÁLISIS EN TIEMPO REAL
// ============================================================================

/**
 * Genera el HTML del panel de análisis para insertar en formularios
 */
export function generateAnalysisPanel(analysis, type = 'expense') {
    const { feasibility, alerts, suggestions, impact, optimalAmount, recommendedSources } = analysis;
    
    // Colores según viabilidad
    const colors = {
        excellent: { bg: '#f0fdf4', border: '#22c55e', text: '#166534', icon: '✅' },
        good: { bg: '#eff6ff', border: '#3b82f6', text: '#1e40af', icon: '👍' },
        caution: { bg: '#fefce8', border: '#eab308', text: '#854d0e', icon: '⚠️' },
        not_recommended: { bg: '#fef2f2', border: '#ef4444', text: '#991b1b', icon: '🚫' }
    };
    
    const color = colors[feasibility.level] || colors.caution;
    
    return `
        <div class="smart-analysis-panel" style="
            background: ${color.bg};
            border: 2px solid ${color.border};
            border-radius: 12px;
            padding: 16px;
            margin: 16px 0;
            font-size: 14px;
        ">
            <!-- Encabezado con score -->
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                <div style="display: flex; align-items: center; gap: 8px;">
                    <span style="font-size: 24px;">${color.icon}</span>
                    <div>
                        <div style="font-weight: 600; color: ${color.text};">
                            ${getViabilityLabel(feasibility.level)}
                        </div>
                        <div style="font-size: 12px; color: #6b7280;">
                            Score: ${feasibility.score}/100
                        </div>
                    </div>
                </div>
                ${optimalAmount && optimalAmount.showSuggestion ? `
                    <div style="text-align: right;">
                        <div style="font-size: 11px; color: #dc2626;">⚠️ Sugerido máximo</div>
                        <div style="font-weight: 700; color: #dc2626;">
                            ${formatCurrency(optimalAmount.suggested)}/mes
                        </div>
                    </div>
                ` : `
                    <div style="text-align: right;">
                        <div style="font-size: 11px; color: #059669;">✓ Monto adecuado</div>
                    </div>
                `}
            </div>
            
            <!-- Alertas -->
            ${alerts.length > 0 ? `
                <div style="display: flex; flex-direction: column; gap: 8px; margin-bottom: 12px;">
                    ${alerts.map(alert => `
                        <div style="
                            display: flex;
                            gap: 10px;
                            padding: 10px;
                            background: white;
                            border-radius: 8px;
                            border-left: 3px solid ${alert.type === 'critical' ? '#ef4444' : alert.type === 'warning' ? '#f97316' : '#3b82f6'};
                        ">
                            <span style="font-size: 18px;">${alert.icon}</span>
                            <div>
                                <div style="font-weight: 500; color: #374151;">${alert.title}</div>
                                <div style="font-size: 12px; color: #6b7280;">${alert.message}</div>
                                ${alert.suggestion ? `
                                    <div style="font-size: 11px; color: #059669; margin-top: 4px;">
                                        💡 ${alert.suggestion}
                                    </div>
                                ` : ''}
                            </div>
                        </div>
                    `).join('')}
                </div>
            ` : ''}
            
            <!-- Impacto (para gastos) -->
            ${impact && type === 'expense' ? `
                <div style="
                    display: grid;
                    grid-template-columns: repeat(3, 1fr);
                    gap: 8px;
                    padding: 12px;
                    background: white;
                    border-radius: 8px;
                    margin-bottom: 12px;
                ">
                    <div style="text-align: center;">
                        <div style="font-size: 11px; color: #6b7280;">Balance actual</div>
                        <div style="font-weight: 600; color: #059669;">${formatCurrency(impact.currentBalance)}</div>
                    </div>
                    <div style="text-align: center;">
                        <div style="font-size: 11px; color: #6b7280;">Nuevo balance</div>
                        <div style="font-weight: 600; color: ${impact.newBalance >= 0 ? '#059669' : '#dc2626'};">
                            ${formatCurrency(impact.newBalance)}
                        </div>
                    </div>
                    <div style="text-align: center;">
                        <div style="font-size: 11px; color: #6b7280;">Tasa ahorro</div>
                        <div style="font-weight: 600; color: ${impact.newSavingsRate >= 10 ? '#059669' : '#dc2626'};">
                            ${impact.newSavingsRate.toFixed(1)}%
                        </div>
                    </div>
                </div>
            ` : ''}
            
            <!-- Fuentes recomendadas -->
            ${recommendedSources && recommendedSources.length > 0 ? `
                <div style="margin-bottom: 12px;">
                    <div style="font-size: 12px; font-weight: 500; color: #374151; margin-bottom: 8px;">
                        📊 Distribución sugerida desde ingresos:
                    </div>
                    ${recommendedSources.slice(0, 3).map(src => `
                        <div style="
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            padding: 8px;
                            background: white;
                            border-radius: 6px;
                            margin-bottom: 4px;
                        ">
                            <span style="font-size: 13px;">${src.name}</span>
                            <span style="font-weight: 500; color: #059669;">
                                ${formatCurrency(src.suggestedAllocation)}
                                <span style="font-size: 11px; color: #6b7280;">
                                    (${src.percentOfSource.toFixed(0)}%)
                                </span>
                            </span>
                        </div>
                    `).join('')}
                </div>
            ` : ''}
            
            <!-- Sugerencias -->
            ${suggestions.length > 0 ? `
                <div style="
                    padding: 10px;
                    background: white;
                    border-radius: 8px;
                    border: 1px dashed #d1d5db;
                ">
                    <div style="font-size: 11px; font-weight: 500; color: #6b7280; margin-bottom: 6px;">
                        💡 Sugerencias:
                    </div>
                    ${suggestions.map(s => `
                        <div style="font-size: 12px; color: #374151; padding: 2px 0;">
                            ${s.icon} ${s.text}
                        </div>
                    `).join('')}
                </div>
            ` : ''}
        </div>
    `;
}

/**
 * Genera el HTML compacto para mostrar durante la edición de monto
 */
export function generateQuickInsight(analysis) {
    const { feasibility, optimalAmount, impact } = analysis;
    
    if (!optimalAmount) return '';
    
    const isGood = feasibility.score >= 60;
    
    return `
        <div class="quick-insight" style="
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 8px 12px;
            background: ${isGood ? '#f0fdf4' : '#fef3c7'};
            border-radius: 8px;
            font-size: 13px;
            margin-top: 8px;
        ">
            <span style="font-size: 18px;">${isGood ? '✅' : '⚠️'}</span>
            <div>
                <span style="color: ${isGood ? '#166534' : '#92400e'};">
                    ${optimalAmount.message}
                </span>
                ${impact ? `
                    <span style="color: #6b7280; margin-left: 8px;">
                        | Balance: ${formatCurrency(impact.newBalance)}/mes
                    </span>
                ` : ''}
            </div>
        </div>
    `;
}

// ============================================================================
// UTILIDADES
// ============================================================================

function formatCurrency(amount) {
    return new Intl.NumberFormat('es-MX', {
        style: 'currency',
        currency: 'MXN',
        minimumFractionDigits: 0,
        maximumFractionDigits: 0
    }).format(amount || 0);
}

function formatDate(date) {
    if (!date) return '';
    const d = new Date(date);
    return d.toLocaleDateString('es-MX', { year: 'numeric', month: 'short', day: 'numeric' });
}

function getGroupName(group) {
    const names = {
        necessities: 'Necesidades',
        wants: 'Deseos',
        savings: 'Ahorro',
        debt: 'Deuda'
    };
    return names[group] || group;
}

function getViabilityLabel(level) {
    const labels = {
        excellent: 'Excelente decisión',
        good: 'Buena opción',
        caution: 'Procede con precaución',
        not_recommended: 'No recomendado'
    };
    return labels[level] || level;
}

function monthsUntil(dateStr) {
    if (!dateStr) return 12;
    const target = new Date(dateStr);
    const now = new Date();
    return Math.max(1, 
        (target.getFullYear() - now.getFullYear()) * 12 + 
        (target.getMonth() - now.getMonth())
    );
}

// ============================================================================
// ACTUALIZACIÓN DE PANEL DE ANÁLISIS EN UI
// ============================================================================

/**
 * Actualiza el panel de análisis inteligente en el formulario
 * @param {Object} analysis - Resultado del análisis
 * @param {string} type - Tipo: 'expense', 'plan', 'savings', 'movement'
 */
export function updateAnalysisPanel(analysis, type = 'expense') {
    const panel = document.getElementById('smart-analysis-panel');
    const content = document.getElementById('analysis-content');
    
    if (!panel || !content) {
        console.warn('Panel de análisis no encontrado en el DOM');
        return;
    }
    
    // Mostrar panel
    panel.style.display = 'block';
    
    // Determinar color de fondo según viabilidad
    const viabilityColors = {
        excellent: { bg: 'linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%)', border: '#10b981', icon: '✅' },
        good: { bg: 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)', border: '#3b82f6', icon: '👍' },
        caution: { bg: 'linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%)', border: '#f59e0b', icon: '⚠️' },
        not_recommended: { bg: 'linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%)', border: '#ef4444', icon: '❌' }
    };
    
    const colors = viabilityColors[analysis.viability] || viabilityColors.caution;
    panel.style.background = colors.bg;
    panel.style.borderColor = colors.border;
    
    // Construir HTML según tipo de análisis
    let html = '';
    
    if (type === 'expense') {
        html = buildExpenseAnalysisHTML(analysis, colors);
    } else if (type === 'plan') {
        html = buildPlanAnalysisHTML(analysis, colors);
    } else if (type === 'savings') {
        html = buildSavingsAnalysisHTML(analysis, colors);
    } else if (type === 'movement') {
        html = buildMovementAnalysisHTML(analysis, colors);
    }
    
    content.innerHTML = html;
}

function buildExpenseAnalysisHTML(analysis, colors) {
    return `
        <div style="display: flex; flex-direction: column; gap: 10px;">
            <!-- Indicador de viabilidad -->
            <div style="display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: white; border-radius: 8px; border-left: 4px solid ${colors.border};">
                <span style="font-size: 1.3rem;">${colors.icon}</span>
                <div>
                    <div style="font-weight: 600; color: #1f2937;">${getViabilityLabel(analysis.viability)}</div>
                    <div style="font-size: 0.8rem; color: #6b7280;">${analysis.reason || ''}</div>
                </div>
            </div>
            
            <!-- Impacto en balance -->
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px;">
                <div style="padding: 8px; background: white; border-radius: 8px; text-align: center;">
                    <div style="font-size: 0.75rem; color: #6b7280;">Balance Actual</div>
                    <div style="font-weight: 600; color: ${analysis.currentBalance >= 0 ? '#10b981' : '#ef4444'};">
                        ${formatCurrency(analysis.currentBalance)}
                    </div>
                </div>
                <div style="padding: 8px; background: white; border-radius: 8px; text-align: center;">
                    <div style="font-size: 0.75rem; color: #6b7280;">Balance Proyectado</div>
                    <div style="font-weight: 600; color: ${analysis.projectedBalance >= 0 ? '#10b981' : '#ef4444'};">
                        ${formatCurrency(analysis.projectedBalance)}
                    </div>
                </div>
            </div>
            
            <!-- Porcentaje del ingreso -->
            <div style="padding: 8px 12px; background: white; border-radius: 8px;">
                <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
                    <span style="font-size: 0.8rem; color: #6b7280;">% de tu ingreso mensual</span>
                    <span style="font-weight: 600; color: ${analysis.percentOfIncome > 30 ? '#ef4444' : '#10b981'};">
                        ${analysis.percentOfIncome?.toFixed(1)}%
                    </span>
                </div>
                <div style="height: 6px; background: #e5e7eb; border-radius: 3px; overflow: hidden;">
                    <div style="height: 100%; width: ${Math.min(analysis.percentOfIncome || 0, 100)}%; background: ${analysis.percentOfIncome > 30 ? '#ef4444' : '#10b981'}; border-radius: 3px;"></div>
                </div>
            </div>
            
            ${analysis.recommendations?.length > 0 ? `
                <!-- Recomendaciones -->
                <div style="padding: 8px 12px; background: white; border-radius: 8px;">
                    <div style="font-size: 0.8rem; font-weight: 600; color: #1f2937; margin-bottom: 6px;">💡 Recomendaciones</div>
                    ${analysis.recommendations.slice(0, 2).map(rec => `
                        <div style="font-size: 0.8rem; color: #4b5563; margin-bottom: 4px;">• ${rec}</div>
                    `).join('')}
                </div>
            ` : ''}
        </div>
    `;
}

function buildPlanAnalysisHTML(analysis, colors) {
    return `
        <div style="display: flex; flex-direction: column; gap: 10px;">
            <!-- Indicador de viabilidad -->
            <div style="display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: white; border-radius: 8px; border-left: 4px solid ${colors.border};">
                <span style="font-size: 1.3rem;">${colors.icon}</span>
                <div>
                    <div style="font-weight: 600; color: #1f2937;">${getViabilityLabel(analysis.viability)}</div>
                    <div style="font-size: 0.8rem; color: #6b7280;">${analysis.reason || ''}</div>
                </div>
            </div>
            
            <!-- Tiempo estimado -->
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px;">
                <div style="padding: 8px; background: white; border-radius: 8px; text-align: center;">
                    <div style="font-size: 0.75rem; color: #6b7280;">Tiempo estimado</div>
                    <div style="font-weight: 600; color: #1e40af;">
                        ${analysis.estimatedMonths || '?'} meses
                    </div>
                </div>
                <div style="padding: 8px; background: white; border-radius: 8px; text-align: center;">
                    <div style="font-size: 0.75rem; color: #6b7280;">Ahorro mensual sugerido</div>
                    <div style="font-weight: 600; color: #10b981;">
                        ${formatCurrency(analysis.suggestedMonthlySaving)}
                    </div>
                </div>
            </div>
            
            ${analysis.targetDate ? `
                <!-- Análisis de fecha objetivo -->
                <div style="padding: 8px 12px; background: white; border-radius: 8px;">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <span style="font-size: 0.8rem; color: #6b7280;">Fecha objetivo</span>
                        <span style="font-weight: 600; color: ${analysis.canMeetDeadline ? '#10b981' : '#f59e0b'};">
                            ${analysis.canMeetDeadline ? '✓ Alcanzable' : '⚠ Ajustar'}
                        </span>
                    </div>
                    ${!analysis.canMeetDeadline ? `
                        <div style="font-size: 0.75rem; color: #6b7280; margin-top: 4px;">
                            Para cumplir la fecha necesitas ahorrar ${formatCurrency(analysis.requiredMonthlySaving)}/mes
                        </div>
                    ` : ''}
                </div>
            ` : ''}
            
            <!-- Impacto en finanzas -->
            <div style="padding: 8px 12px; background: white; border-radius: 8px;">
                <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
                    <span style="font-size: 0.8rem; color: #6b7280;">Impacto en balance mensual</span>
                    <span style="font-weight: 600; color: ${analysis.monthlyImpactPercent > 30 ? '#ef4444' : '#10b981'};">
                        -${analysis.monthlyImpactPercent?.toFixed(1)}%
                    </span>
                </div>
                <div style="height: 6px; background: #e5e7eb; border-radius: 3px; overflow: hidden;">
                    <div style="height: 100%; width: ${Math.min(analysis.monthlyImpactPercent || 0, 100)}%; background: ${analysis.monthlyImpactPercent > 30 ? '#ef4444' : '#3b82f6'}; border-radius: 3px;"></div>
                </div>
            </div>
            
            ${analysis.recommendations?.length > 0 ? `
                <!-- Recomendaciones -->
                <div style="padding: 8px 12px; background: white; border-radius: 8px;">
                    <div style="font-size: 0.8rem; font-weight: 600; color: #1f2937; margin-bottom: 6px;">💡 Sugerencias</div>
                    ${analysis.recommendations.slice(0, 2).map(rec => `
                        <div style="font-size: 0.8rem; color: #4b5563; margin-bottom: 4px;">• ${rec}</div>
                    `).join('')}
                </div>
            ` : ''}
        </div>
    `;
}

function buildSavingsAnalysisHTML(analysis, colors) {
    return `
        <div style="display: flex; flex-direction: column; gap: 10px;">
            <!-- Indicador de viabilidad -->
            <div style="display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: white; border-radius: 8px; border-left: 4px solid ${colors.border};">
                <span style="font-size: 1.3rem;">${colors.icon}</span>
                <div>
                    <div style="font-weight: 600; color: #1f2937;">${getViabilityLabel(analysis.viability)}</div>
                    <div style="font-size: 0.8rem; color: #6b7280;">${analysis.reason || ''}</div>
                </div>
            </div>
            
            <!-- Métricas de ahorro -->
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px;">
                <div style="padding: 8px; background: white; border-radius: 8px; text-align: center;">
                    <div style="font-size: 0.75rem; color: #6b7280;">Tasa de ahorro actual</div>
                    <div style="font-weight: 600; color: ${analysis.currentSavingsRate >= 20 ? '#10b981' : '#f59e0b'};">
                        ${analysis.currentSavingsRate?.toFixed(1)}%
                    </div>
                </div>
                <div style="padding: 8px; background: white; border-radius: 8px; text-align: center;">
                    <div style="font-size: 0.75rem; color: #6b7280;">Tasa con este ahorro</div>
                    <div style="font-weight: 600; color: ${analysis.newSavingsRate >= 20 ? '#10b981' : '#f59e0b'};">
                        ${analysis.newSavingsRate?.toFixed(1)}%
                    </div>
                </div>
            </div>
            
            <!-- Balance después -->
            <div style="padding: 8px 12px; background: white; border-radius: 8px;">
                <div style="display: flex; justify-content: space-between;">
                    <span style="font-size: 0.8rem; color: #6b7280;">Balance mensual restante</span>
                    <span style="font-weight: 600; color: ${analysis.remainingBalance >= 0 ? '#10b981' : '#ef4444'};">
                        ${formatCurrency(analysis.remainingBalance)}
                    </span>
                </div>
            </div>
            
            ${analysis.recommendations?.length > 0 ? `
                <div style="padding: 8px 12px; background: white; border-radius: 8px;">
                    <div style="font-size: 0.8rem; font-weight: 600; color: #1f2937; margin-bottom: 6px;">💡 Sugerencias</div>
                    ${analysis.recommendations.slice(0, 2).map(rec => `
                        <div style="font-size: 0.8rem; color: #4b5563; margin-bottom: 4px;">• ${rec}</div>
                    `).join('')}
                </div>
            ` : ''}
        </div>
    `;
}

function buildMovementAnalysisHTML(analysis, colors) {
    const isIncome = analysis.type === 'income';
    
    return `
        <div style="display: flex; flex-direction: column; gap: 10px;">
            <!-- Indicador -->
            <div style="display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: white; border-radius: 8px; border-left: 4px solid ${colors.border};">
                <span style="font-size: 1.3rem;">${isIncome ? '💰' : colors.icon}</span>
                <div>
                    <div style="font-weight: 600; color: #1f2937;">${isIncome ? 'Ingreso registrado' : getViabilityLabel(analysis.viability)}</div>
                    <div style="font-size: 0.8rem; color: #6b7280;">${analysis.reason || ''}</div>
                </div>
            </div>
            
            <!-- Balance -->
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px;">
                <div style="padding: 8px; background: white; border-radius: 8px; text-align: center;">
                    <div style="font-size: 0.75rem; color: #6b7280;">Balance Antes</div>
                    <div style="font-weight: 600; color: #1f2937;">
                        ${formatCurrency(analysis.balanceBefore)}
                    </div>
                </div>
                <div style="padding: 8px; background: white; border-radius: 8px; text-align: center;">
                    <div style="font-size: 0.75rem; color: #6b7280;">Balance Después</div>
                    <div style="font-weight: 600; color: ${analysis.balanceAfter >= 0 ? '#10b981' : '#ef4444'};">
                        ${formatCurrency(analysis.balanceAfter)}
                    </div>
                </div>
            </div>
            
            ${analysis.suggestions?.length > 0 ? `
                <div style="padding: 8px 12px; background: white; border-radius: 8px;">
                    <div style="font-size: 0.8rem; font-weight: 600; color: #1f2937; margin-bottom: 6px;">💡 Sugerencias</div>
                    ${analysis.suggestions.slice(0, 2).map(s => `
                        <div style="font-size: 0.8rem; color: #4b5563; margin-bottom: 4px;">• ${s}</div>
                    `).join('')}
                </div>
            ` : ''}
        </div>
    `;
}

// ============================================================================
// EXPORTACIONES ADICIONALES (funciones sin export en declaración)
// ============================================================================

export {
    toMonthlyAmount,
    classifyCategory,
    formatCurrency as formatMoney,
    SMART_LIMITS,
    CATEGORY_CLASSIFICATION
};
