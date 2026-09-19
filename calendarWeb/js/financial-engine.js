/**
 * financial-engine.js
 * Motor de Análisis Financiero Inteligente
 * 
 * Funcionalidades:
 * - Análisis de relación ingresos/gastos
 * - Cálculo de montos óptimos
 * - Vinculación inteligente de gastos a ingresos
 * - Detección de déficit y superávit
 * - Recomendaciones automáticas
 * - Proyecciones financieras
 */

import { supabase } from './supabase-client.js';
import { getIncomePatterns, getExpensePatterns } from './patterns.js';
import { getPlans } from './plans-v2.js';
import { getConfirmedBalanceSummary, getMonthlyConfirmedBalance, formatCurrencyWhole } from './balance.js';
import { logger } from './logger.js';

// ============================================================================
// CONSTANTES Y CONFIGURACIÓN
// ============================================================================

const RECOMMENDED_ALLOCATIONS = {
    // Regla 50/30/20 adaptada
    necessities: 0.50,      // Necesidades básicas (vivienda, comida, transporte)
    wants: 0.30,            // Deseos (entretenimiento, compras)
    savings: 0.20,          // Ahorro e inversión
    
    // Subcategorías de necesidades
    housing: 0.30,          // Vivienda máximo 30% del ingreso
    food: 0.15,             // Alimentación
    transport: 0.10,        // Transporte
    utilities: 0.05,        // Servicios
    
    // Límites de seguridad
    emergency_fund_months: 3,   // Meses de gastos para fondo de emergencia
    debt_to_income_max: 0.35,   // Máximo ratio deuda/ingreso
};

const CATEGORY_GROUPS = {
    necessities: ['vivienda', 'alquiler', 'renta', 'hipoteca', 'comida', 'alimentación', 
                  'supermercado', 'transporte', 'gasolina', 'servicios', 'luz', 'agua', 
                  'gas', 'internet', 'teléfono', 'salud', 'médico', 'farmacia', 'seguros'],
    wants: ['entretenimiento', 'restaurantes', 'ropa', 'compras', 'suscripciones', 
            'streaming', 'deportes', 'gym', 'hobbies', 'viajes', 'vacaciones'],
    savings: ['ahorro', 'inversión', 'retiro', 'emergencia', 'metas'],
    debt: ['deuda', 'préstamo', 'crédito', 'tarjeta']
};

const TIME_HORIZONS = {
    short: { months: 3, label: 'Corto plazo (0-3 meses)' },
    medium: { months: 12, label: 'Mediano plazo (3-12 meses)' },
    long: { months: 36, label: 'Largo plazo (1-3 años)' }
};

// ============================================================================
// ANÁLISIS DE INGRESOS
// ============================================================================

/**
 * Analiza todos los ingresos del usuario y calcula métricas
 */
export async function analyzeIncome() {
    try {
        const patterns = await getIncomePatterns(true); // Solo activos
        
        const analysis = {
            patterns: [],
            totals: {
                monthly: 0,
                weekly: 0,
                annual: 0
            },
            stability: {
                score: 0,
                level: 'unknown',
                factors: []
            },
            sources: {
                count: patterns.length,
                diversification: 0
            }
        };
        
        // Analizar cada patrón de ingreso
        for (const pattern of patterns) {
            const monthlyAmount = calculateMonthlyEquivalent(pattern.base_amount, pattern.frequency, pattern.interval);
            
            analysis.patterns.push({
                id: pattern.id,
                name: pattern.name,
                category: pattern.category,
                baseAmount: pattern.base_amount,
                frequency: pattern.frequency,
                monthlyEquivalent: monthlyAmount,
                annualEquivalent: monthlyAmount * 12,
                percentOfTotal: 0, // Se calcula después
                stability: assessPatternStability(pattern)
            });
            
            analysis.totals.monthly += monthlyAmount;
        }
        
        // Calcular porcentajes y totales
        analysis.totals.weekly = analysis.totals.monthly / 4.33;
        analysis.totals.annual = analysis.totals.monthly * 12;
        
        // Calcular porcentaje de cada fuente
        analysis.patterns.forEach(p => {
            p.percentOfTotal = analysis.totals.monthly > 0 
                ? (p.monthlyEquivalent / analysis.totals.monthly) * 100 
                : 0;
        });
        
        // Calcular diversificación (índice Herfindahl invertido)
        if (analysis.patterns.length > 0) {
            const herfindahl = analysis.patterns.reduce((sum, p) => 
                sum + Math.pow(p.percentOfTotal / 100, 2), 0);
            analysis.sources.diversification = Math.round((1 - herfindahl) * 100);
        }
        
        // Calcular estabilidad general
        analysis.stability = calculateIncomeStability(analysis.patterns);
        
        return analysis;
    } catch (error) {
        logger.error('Error analyzing income:', error);
        throw error;
    }
}

/**
 * Calcula el equivalente mensual de cualquier frecuencia
 */
function calculateMonthlyEquivalent(amount, frequency, interval = 1) {
    const baseAmount = parseFloat(amount) || 0;
    const freq = frequency?.toLowerCase() || 'monthly';
    const int = parseInt(interval) || 1;
    
    switch (freq) {
        case 'daily':
            return (baseAmount / int) * 30;
        case 'weekly':
            return (baseAmount / int) * 4.33;
        case 'biweekly':
            return (baseAmount / int) * 2.17;
        case 'monthly':
            return baseAmount / int;
        case 'quarterly':
            return baseAmount / (int * 3);
        case 'semiannual':
            return baseAmount / (int * 6);
        case 'annual':
            return baseAmount / (int * 12);
        default:
            return baseAmount;
    }
}

/**
 * Evalúa la estabilidad de un patrón
 */
function assessPatternStability(pattern) {
    let score = 50; // Base
    const factors = [];
    
    // Frecuencia (más frecuente = más estable)
    const freqScores = {
        'daily': 90, 'weekly': 85, 'biweekly': 80, 
        'monthly': 75, 'quarterly': 50, 'semiannual': 40, 'annual': 30
    };
    score = freqScores[pattern.frequency] || 50;
    
    // Si tiene fecha de fin próxima, menos estable
    if (pattern.end_date) {
        const daysUntilEnd = Math.floor((new Date(pattern.end_date) - new Date()) / (1000 * 60 * 60 * 24));
        if (daysUntilEnd < 30) {
            score -= 30;
            factors.push('Termina pronto');
        } else if (daysUntilEnd < 90) {
            score -= 15;
            factors.push('Termina en 3 meses');
        }
    }
    
    // Categoría (empleo fijo más estable que freelance)
    const stableCategories = ['salario', 'nómina', 'pensión', 'renta fija'];
    const unstableCategories = ['freelance', 'comisiones', 'bonos', 'propinas'];
    
    const catLower = (pattern.category || '').toLowerCase();
    if (stableCategories.some(c => catLower.includes(c))) {
        score += 10;
        factors.push('Ingreso estable');
    } else if (unstableCategories.some(c => catLower.includes(c))) {
        score -= 10;
        factors.push('Ingreso variable');
    }
    
    return {
        score: Math.max(0, Math.min(100, score)),
        level: score >= 70 ? 'high' : score >= 40 ? 'medium' : 'low',
        factors
    };
}

/**
 * Calcula estabilidad general de ingresos
 */
function calculateIncomeStability(patterns) {
    if (patterns.length === 0) {
        return { score: 0, level: 'unknown', factors: ['Sin ingresos registrados'] };
    }
    
    // Promedio ponderado por monto
    const totalMonthly = patterns.reduce((sum, p) => sum + p.monthlyEquivalent, 0);
    const weightedScore = patterns.reduce((sum, p) => 
        sum + (p.stability.score * (p.monthlyEquivalent / totalMonthly)), 0);
    
    const factors = [];
    
    if (patterns.length === 1) {
        factors.push('Una sola fuente de ingreso');
    } else if (patterns.length >= 3) {
        factors.push('Múltiples fuentes de ingreso');
    }
    
    // Verificar concentración
    const maxPercent = Math.max(...patterns.map(p => p.percentOfTotal));
    if (maxPercent > 80) {
        factors.push('Alta dependencia de una fuente');
    }
    
    return {
        score: Math.round(weightedScore),
        level: weightedScore >= 70 ? 'high' : weightedScore >= 40 ? 'medium' : 'low',
        factors
    };
}

// ============================================================================
// ANÁLISIS DE GASTOS
// ============================================================================

/**
 * Analiza todos los gastos del usuario
 */
export async function analyzeExpenses() {
    try {
        const patterns = await getExpensePatterns(true);
        
        const analysis = {
            patterns: [],
            totals: {
                monthly: 0,
                weekly: 0,
                annual: 0
            },
            byCategory: {},
            byGroup: {
                necessities: 0,
                wants: 0,
                savings: 0,
                debt: 0,
                other: 0
            },
            alerts: []
        };
        
        // Analizar cada patrón de gasto
        for (const pattern of patterns) {
            const monthlyAmount = calculateMonthlyEquivalent(pattern.base_amount, pattern.frequency, pattern.interval);
            const group = categorizeExpense(pattern.category);
            
            analysis.patterns.push({
                id: pattern.id,
                name: pattern.name,
                category: pattern.category,
                baseAmount: pattern.base_amount,
                frequency: pattern.frequency,
                monthlyEquivalent: monthlyAmount,
                annualEquivalent: monthlyAmount * 12,
                group,
                linkedIncome: pattern.linked_income_id || null
            });
            
            analysis.totals.monthly += monthlyAmount;
            analysis.byGroup[group] += monthlyAmount;
            
            // Agrupar por categoría
            const cat = pattern.category || 'Sin categoría';
            analysis.byCategory[cat] = (analysis.byCategory[cat] || 0) + monthlyAmount;
        }
        
        analysis.totals.weekly = analysis.totals.monthly / 4.33;
        analysis.totals.annual = analysis.totals.monthly * 12;
        
        return analysis;
    } catch (error) {
        logger.error('Error analyzing expenses:', error);
        throw error;
    }
}

/**
 * Categoriza un gasto en grupo (necesidades, deseos, ahorro, deuda)
 */
function categorizeExpense(category) {
    const catLower = (category || '').toLowerCase();
    
    for (const [group, keywords] of Object.entries(CATEGORY_GROUPS)) {
        if (keywords.some(k => catLower.includes(k))) {
            return group;
        }
    }
    
    return 'other';
}

// ============================================================================
// ANÁLISIS COMBINADO Y RECOMENDACIONES
// ============================================================================

/**
 * Realiza un análisis financiero completo
 */
export async function getFullFinancialAnalysis() {
    try {
        const [incomeAnalysis, expenseAnalysis, plans, balanceSummary] = await Promise.all([
            analyzeIncome(),
            analyzeExpenses(),
            getPlans({ status: 'active' }),
            getConfirmedBalanceSummary()
        ]);
        
        const analysis = {
            income: incomeAnalysis,
            expenses: expenseAnalysis,
            plans: analyzePlans(plans, incomeAnalysis),
            balance: {
                monthly: incomeAnalysis.totals.monthly - expenseAnalysis.totals.monthly,
                annual: incomeAnalysis.totals.annual - expenseAnalysis.totals.annual,
                confirmed: balanceSummary
            },
            ratios: calculateFinancialRatios(incomeAnalysis, expenseAnalysis),
            health: null,
            recommendations: [],
            allocations: calculateOptimalAllocations(incomeAnalysis, expenseAnalysis),
            projections: generateProjections(incomeAnalysis, expenseAnalysis, plans)
        };
        
        // Calcular salud financiera general
        analysis.health = calculateFinancialHealth(analysis);
        
        // Generar recomendaciones
        analysis.recommendations = generateRecommendations(analysis);
        
        return analysis;
    } catch (error) {
        logger.error('Error in full financial analysis:', error);
        throw error;
    }
}

/**
 * Analiza los planes/metas
 */
function analyzePlans(plans, incomeAnalysis) {
    const monthlyIncome = incomeAnalysis.totals.monthly;
    
    return plans.map(plan => {
        const remaining = plan.remaining_amount || (plan.target_amount - plan.current_amount);
        const targetDate = plan.target_date ? new Date(plan.target_date) : null;
        const today = new Date();
        
        let monthsRemaining = 12; // Default
        if (targetDate) {
            monthsRemaining = Math.max(1, 
                (targetDate.getFullYear() - today.getFullYear()) * 12 + 
                (targetDate.getMonth() - today.getMonth())
            );
        }
        
        const requiredMonthly = remaining / monthsRemaining;
        const percentOfIncome = monthlyIncome > 0 ? (requiredMonthly / monthlyIncome) * 100 : 0;
        
        // Determinar viabilidad
        let feasibility = 'achievable';
        let feasibilityScore = 100;
        
        if (percentOfIncome > 50) {
            feasibility = 'very_difficult';
            feasibilityScore = 20;
        } else if (percentOfIncome > 30) {
            feasibility = 'difficult';
            feasibilityScore = 50;
        } else if (percentOfIncome > 20) {
            feasibility = 'moderate';
            feasibilityScore = 70;
        }
        
        // Calcular fecha óptima si la actual no es viable
        const optimalMonthly = monthlyIncome * 0.15; // 15% del ingreso
        const optimalMonths = optimalMonthly > 0 ? Math.ceil(remaining / optimalMonthly) : 999;
        const optimalDate = new Date(today);
        optimalDate.setMonth(optimalDate.getMonth() + optimalMonths);
        
        return {
            ...plan,
            analysis: {
                remaining,
                monthsRemaining,
                requiredMonthly,
                percentOfIncome,
                feasibility,
                feasibilityScore,
                optimalMonthly,
                optimalDate: optimalDate.toISOString().split('T')[0],
                horizon: monthsRemaining <= 3 ? 'short' : monthsRemaining <= 12 ? 'medium' : 'long'
            }
        };
    });
}

/**
 * Calcula ratios financieros importantes
 */
function calculateFinancialRatios(income, expenses) {
    const monthlyIncome = income.totals.monthly || 1;
    const monthlyExpenses = expenses.totals.monthly;
    
    return {
        // Ratio de ahorro
        savingsRate: Math.max(0, ((monthlyIncome - monthlyExpenses) / monthlyIncome) * 100),
        
        // Ratio gastos/ingresos
        expenseToIncome: (monthlyExpenses / monthlyIncome) * 100,
        
        // Ratios por grupo
        necessitiesRate: (expenses.byGroup.necessities / monthlyIncome) * 100,
        wantsRate: (expenses.byGroup.wants / monthlyIncome) * 100,
        debtRate: (expenses.byGroup.debt / monthlyIncome) * 100,
        
        // Disponible después de necesidades
        discretionaryIncome: monthlyIncome - expenses.byGroup.necessities,
        
        // Meses de cobertura con ahorro actual
        // (esto necesitaría el ahorro total actual)
        emergencyMonths: 0 // Se calcula con datos adicionales
    };
}

/**
 * Calcula asignaciones óptimas del ingreso
 */
function calculateOptimalAllocations(income, expenses) {
    const monthlyIncome = income.totals.monthly;
    
    // Asignación ideal según regla 50/30/20
    const ideal = {
        necessities: monthlyIncome * RECOMMENDED_ALLOCATIONS.necessities,
        wants: monthlyIncome * RECOMMENDED_ALLOCATIONS.wants,
        savings: monthlyIncome * RECOMMENDED_ALLOCATIONS.savings
    };
    
    // Asignación actual
    const current = {
        necessities: expenses.byGroup.necessities,
        wants: expenses.byGroup.wants,
        savings: Math.max(0, monthlyIncome - expenses.totals.monthly)
    };
    
    // Diferencias
    const diff = {
        necessities: ideal.necessities - current.necessities,
        wants: ideal.wants - current.wants,
        savings: ideal.savings - current.savings
    };
    
    // Sugerencias específicas
    const suggestions = [];
    
    if (current.necessities > ideal.necessities) {
        suggestions.push({
            type: 'warning',
            category: 'necessities',
            message: `Tus gastos esenciales (${formatCurrencyWhole(current.necessities)}) superan el ideal (${formatCurrencyWhole(ideal.necessities)}).`,
            action: 'Considera renegociar contratos o buscar alternativas más económicas.'
        });
    }
    
    if (current.wants > ideal.wants) {
        suggestions.push({
            type: 'info',
            category: 'wants',
            message: `Gastas ${formatCurrencyWhole(current.wants)} en deseos, ${formatCurrencyWhole(current.wants - ideal.wants)} más del ideal.`,
            action: 'Revisa suscripciones y gastos no esenciales que puedas reducir.'
        });
    }
    
    if (current.savings < ideal.savings) {
        suggestions.push({
            type: 'critical',
            category: 'savings',
            message: `Tu ahorro actual (${formatCurrencyWhole(current.savings)}) está ${formatCurrencyWhole(ideal.savings - current.savings)} por debajo del ideal.`,
            action: 'Prioriza aumentar tu tasa de ahorro antes de nuevos gastos.'
        });
    }
    
    return { ideal, current, diff, suggestions };
}

/**
 * Genera proyecciones financieras
 */
function generateProjections(income, expenses, plans) {
    const monthlyBalance = income.totals.monthly - expenses.totals.monthly;
    const projections = [];
    
    // Proyección a 3, 6, 12 meses
    [3, 6, 12].forEach(months => {
        const accumulated = monthlyBalance * months;
        const scenario = {
            months,
            label: months === 3 ? '3 meses' : months === 6 ? '6 meses' : '1 año',
            projected: {
                income: income.totals.monthly * months,
                expenses: expenses.totals.monthly * months,
                balance: accumulated
            },
            plansProgress: plans.map(p => ({
                name: p.name,
                currentProgress: p.progress_percent || 0,
                projectedProgress: Math.min(100, 
                    ((p.current_amount + (p.analysis?.requiredMonthly || 0) * months) / p.target_amount) * 100
                )
            }))
        };
        projections.push(scenario);
    });
    
    return projections;
}

/**
 * Calcula la salud financiera general
 */
function calculateFinancialHealth(analysis) {
    let score = 50; // Base
    const factors = [];
    
    // Factor 1: Ratio de ahorro (máx 25 pts)
    const savingsRate = analysis.ratios.savingsRate;
    if (savingsRate >= 20) {
        score += 25;
        factors.push({ factor: 'Excelente tasa de ahorro', impact: '+25' });
    } else if (savingsRate >= 10) {
        score += 15;
        factors.push({ factor: 'Buena tasa de ahorro', impact: '+15' });
    } else if (savingsRate > 0) {
        score += 5;
        factors.push({ factor: 'Ahorro mínimo', impact: '+5' });
    } else {
        score -= 15;
        factors.push({ factor: 'Sin ahorro o déficit', impact: '-15' });
    }
    
    // Factor 2: Diversificación de ingresos (máx 15 pts)
    const diversification = analysis.income.sources.diversification;
    if (diversification >= 50) {
        score += 15;
        factors.push({ factor: 'Ingresos diversificados', impact: '+15' });
    } else if (diversification >= 25) {
        score += 8;
        factors.push({ factor: 'Ingresos parcialmente diversificados', impact: '+8' });
    }
    
    // Factor 3: Ratio deuda/ingreso (máx -20 pts)
    const debtRate = analysis.ratios.debtRate;
    if (debtRate > 35) {
        score -= 20;
        factors.push({ factor: 'Alto nivel de deuda', impact: '-20' });
    } else if (debtRate > 20) {
        score -= 10;
        factors.push({ factor: 'Deuda moderada', impact: '-10' });
    } else if (debtRate > 0) {
        score -= 5;
        factors.push({ factor: 'Deuda controlada', impact: '-5' });
    }
    
    // Factor 4: Balance positivo (máx 10 pts)
    if (analysis.balance.monthly > 0) {
        score += 10;
        factors.push({ factor: 'Balance mensual positivo', impact: '+10' });
    } else {
        score -= 20;
        factors.push({ factor: 'Balance mensual negativo', impact: '-20' });
    }
    
    // Normalizar score
    score = Math.max(0, Math.min(100, score));
    
    // Determinar nivel
    let level, color;
    if (score >= 80) {
        level = 'Excelente';
        color = '#27ae60';
    } else if (score >= 60) {
        level = 'Buena';
        color = '#2ecc71';
    } else if (score >= 40) {
        level = 'Regular';
        color = '#f39c12';
    } else if (score >= 20) {
        level = 'Necesita atención';
        color = '#e67e22';
    } else {
        level = 'Crítica';
        color = '#e74c3c';
    }
    
    return { score, level, color, factors };
}

/**
 * Genera recomendaciones personalizadas
 */
function generateRecommendations(analysis) {
    const recommendations = [];
    const { income, expenses, balance, ratios, allocations, plans } = analysis;
    
    // Recomendación 1: Balance negativo
    if (balance.monthly < 0) {
        recommendations.push({
            priority: 'critical',
            icon: '🚨',
            title: 'Déficit mensual detectado',
            message: `Tus gastos superan tus ingresos por ${formatCurrencyWhole(Math.abs(balance.monthly))} al mes.`,
            actions: [
                'Revisa y reduce gastos no esenciales',
                'Busca fuentes adicionales de ingreso',
                'Considera renegociar deudas'
            ],
            impact: 'Alto'
        });
    }
    
    // Recomendación 2: Ahorro bajo
    if (ratios.savingsRate < 10 && balance.monthly > 0) {
        recommendations.push({
            priority: 'high',
            icon: '💰',
            title: 'Aumenta tu tasa de ahorro',
            message: `Tu tasa de ahorro es ${ratios.savingsRate.toFixed(1)}%. Se recomienda al menos 20%.`,
            actions: [
                `Intenta ahorrar ${formatCurrencyWhole(income.totals.monthly * 0.2 - (income.totals.monthly - expenses.totals.monthly))} más al mes`,
                'Automatiza transferencias a ahorro',
                'Revisa suscripciones innecesarias'
            ],
            impact: 'Medio'
        });
    }
    
    // Recomendación 3: Alta dependencia de una fuente
    if (income.sources.count === 1) {
        recommendations.push({
            priority: 'medium',
            icon: '📊',
            title: 'Diversifica tus ingresos',
            message: 'Dependes de una sola fuente de ingresos, lo cual es riesgoso.',
            actions: [
                'Considera un trabajo freelance o proyecto paralelo',
                'Explora inversiones que generen ingresos pasivos',
                'Desarrolla habilidades monetizables'
            ],
            impact: 'Largo plazo'
        });
    }
    
    // Recomendación 4: Deuda alta
    if (ratios.debtRate > 35) {
        recommendations.push({
            priority: 'high',
            icon: '💳',
            title: 'Reduce tu nivel de deuda',
            message: `El ${ratios.debtRate.toFixed(1)}% de tus ingresos va a deudas. El máximo recomendado es 35%.`,
            actions: [
                'Usa el método avalancha: paga primero la deuda con mayor interés',
                'Considera consolidar deudas',
                'No adquieras nuevas deudas hasta reducir las actuales'
            ],
            impact: 'Alto'
        });
    }
    
    // Recomendación 5: Planes en riesgo
    const riskyPlans = plans.filter(p => p.analysis?.feasibility === 'very_difficult' || p.analysis?.feasibility === 'difficult');
    if (riskyPlans.length > 0) {
        recommendations.push({
            priority: 'medium',
            icon: '🎯',
            title: 'Revisa tus metas financieras',
            message: `${riskyPlans.length} meta(s) requieren más del 30% de tus ingresos. Considera ajustar fechas.`,
            actions: riskyPlans.map(p => 
                `"${p.name}": Fecha óptima sugerida: ${formatDate(p.analysis.optimalDate)}`
            ),
            impact: 'Medio'
        });
    }
    
    // Recomendación 6: Gastos en deseos excesivos
    if (allocations.current.wants > allocations.ideal.wants * 1.5) {
        recommendations.push({
            priority: 'low',
            icon: '🎭',
            title: 'Gastos discrecionales elevados',
            message: `Gastas ${formatCurrencyWhole(allocations.current.wants)} en deseos. Lo ideal sería ${formatCurrencyWhole(allocations.ideal.wants)}.`,
            actions: [
                'Implementa la regla de 24 horas antes de compras no esenciales',
                'Establece un presupuesto semanal para entretenimiento',
                'Busca alternativas gratuitas de ocio'
            ],
            impact: 'Bajo'
        });
    }
    
    // Recomendación positiva si todo está bien
    if (recommendations.length === 0) {
        recommendations.push({
            priority: 'success',
            icon: '🌟',
            title: '¡Excelente salud financiera!',
            message: 'Tus finanzas están en buen estado. Sigue así y considera estos próximos pasos.',
            actions: [
                'Aumenta tus contribuciones a metas de largo plazo',
                'Considera inversiones para hacer crecer tu patrimonio',
                'Mantén un fondo de emergencia de 3-6 meses'
            ],
            impact: 'Positivo'
        });
    }
    
    return recommendations.sort((a, b) => {
        const priorityOrder = { critical: 0, high: 1, medium: 2, low: 3, success: 4 };
        return priorityOrder[a.priority] - priorityOrder[b.priority];
    });
}

// ============================================================================
// VINCULACIÓN INTELIGENTE GASTOS-INGRESOS
// ============================================================================

/**
 * Sugiere la mejor vinculación de un gasto a fuentes de ingreso
 */
export async function suggestExpenseAllocation(expenseData) {
    const { amount, frequency, category, name } = expenseData;
    const incomeAnalysis = await analyzeIncome();
    
    const monthlyExpense = calculateMonthlyEquivalent(amount, frequency);
    const suggestions = [];
    
    // Ordenar ingresos por disponibilidad (los que tienen menos comprometido primero)
    const availableIncomes = incomeAnalysis.patterns
        .filter(p => p.monthlyEquivalent > 0)
        .map(p => ({
            ...p,
            availablePercent: 100, // TODO: Calcular basado en gastos ya vinculados
            suggestedAllocation: 0
        }));
    
    // Estrategia 1: Dividir proporcionalmente
    const totalIncome = incomeAnalysis.totals.monthly;
    if (totalIncome > 0) {
        availableIncomes.forEach(income => {
            income.suggestedAllocation = (income.monthlyEquivalent / totalIncome) * monthlyExpense;
        });
    }
    
    // Análisis de viabilidad
    const expensePercent = (monthlyExpense / totalIncome) * 100;
    let feasibility = 'viable';
    let warning = null;
    
    if (expensePercent > 50) {
        feasibility = 'not_recommended';
        warning = 'Este gasto representaría más del 50% de tus ingresos. No es recomendable.';
    } else if (expensePercent > 30) {
        feasibility = 'caution';
        warning = 'Este gasto es significativo. Asegúrate de que sea prioritario.';
    }
    
    // Calcular impacto en ahorro
    const currentSavings = totalIncome - (await analyzeExpenses()).totals.monthly;
    const newSavings = currentSavings - monthlyExpense;
    const impactOnSavings = currentSavings > 0 
        ? ((currentSavings - newSavings) / currentSavings) * 100 
        : 100;
    
    return {
        expense: {
            name,
            amount,
            frequency,
            monthlyEquivalent: monthlyExpense,
            percentOfIncome: expensePercent
        },
        suggestions: availableIncomes,
        feasibility,
        warning,
        impact: {
            currentMonthlySavings: currentSavings,
            newMonthlySavings: newSavings,
            savingsReduction: impactOnSavings,
            recommendation: newSavings < 0 
                ? 'No recomendado: generaría déficit'
                : newSavings < totalIncome * 0.1
                    ? 'Precaución: dejaría poco margen de ahorro'
                    : 'Viable dentro de tu presupuesto'
        },
        optimalAmount: calculateOptimalExpenseAmount(totalIncome, category)
    };
}

/**
 * Calcula el monto óptimo para un gasto según categoría
 */
function calculateOptimalExpenseAmount(monthlyIncome, category) {
    const group = categorizeExpense(category);
    
    let maxPercent;
    switch (group) {
        case 'necessities':
            maxPercent = 0.50;
            break;
        case 'wants':
            maxPercent = 0.30;
            break;
        case 'savings':
            maxPercent = 0.20;
            break;
        default:
            maxPercent = 0.10;
    }
    
    // Considerar subcategorías específicas
    const catLower = (category || '').toLowerCase();
    if (catLower.includes('vivienda') || catLower.includes('renta') || catLower.includes('alquiler')) {
        maxPercent = Math.min(maxPercent, 0.30);
    } else if (catLower.includes('transporte')) {
        maxPercent = Math.min(maxPercent, 0.15);
    } else if (catLower.includes('comida') || catLower.includes('alimentación')) {
        maxPercent = Math.min(maxPercent, 0.15);
    }
    
    return {
        maxRecommended: monthlyIncome * maxPercent,
        percentOfIncome: maxPercent * 100,
        group,
        explanation: `Para ${group}, se recomienda no exceder ${(maxPercent * 100).toFixed(0)}% del ingreso`
    };
}

/**
 * Vincula un patrón de gasto a fuentes de ingreso
 */
export async function linkExpenseToIncomes(expensePatternId, allocations) {
    try {
        // allocations = [{ income_pattern_id, percent, fixed_amount }]
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');
        
        // Eliminar vinculaciones anteriores
        await supabase
            .from('expense_income_links')
            .delete()
            .eq('expense_pattern_id', expensePatternId);
        
        // Crear nuevas vinculaciones
        if (allocations && allocations.length > 0) {
            const links = allocations.map(a => ({
                user_id: user.id,
                expense_pattern_id: expensePatternId,
                income_pattern_id: a.income_pattern_id,
                allocation_percent: a.percent || null,
                fixed_amount: a.fixed_amount || null
            }));
            
            const { error } = await supabase
                .from('expense_income_links')
                .insert(links);
            
            if (error) throw error;
        }
        
        return true;
    } catch (error) {
        logger.error('Error linking expense to incomes:', error);
        throw error;
    }
}

// ============================================================================
// UTILIDADES
// ============================================================================

// formatCurrencyWhole() se importa de balance.js (fuente unica).

function formatDate(dateStr) {
    if (!dateStr) return 'Sin fecha';
    return new Date(dateStr).toLocaleDateString('es-MX', {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
    });
}

// ============================================================================
// EXPORTACIONES
// ============================================================================

export {
    calculateMonthlyEquivalent,
    categorizeExpense,
    // formatCurrency se mantiene como alias del compartido (mismo output,
    // 0 decimales) para no romper a financial-dashboard.js.
    formatCurrencyWhole as formatCurrency,
    formatDate,
    RECOMMENDED_ALLOCATIONS,
    CATEGORY_GROUPS,
    TIME_HORIZONS
};
