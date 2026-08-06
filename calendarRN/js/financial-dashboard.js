/**
 * financial-dashboard.js
 * Dashboard de Análisis Financiero - Interfaz de Usuario
 * 
 * Componentes visuales para mostrar:
 * - Resumen de salud financiera
 * - Gráficos de distribución
 * - Recomendaciones
 * - Panel de vinculación gastos-ingresos
 */

import { 
    getFullFinancialAnalysis, 
    analyzeIncome, 
    analyzeExpenses,
    suggestExpenseAllocation,
    linkExpenseToIncomes,
    formatCurrency,
    formatDate,
    RECOMMENDED_ALLOCATIONS 
} from './financial-engine.js';

// ============================================================================
// ESTILOS CSS
// ============================================================================

const dashboardStyles = `
<style id="financial-dashboard-styles">
.financial-dashboard {
    background: var(--card-bg, #ffffff);
    border-radius: 16px;
    padding: 24px;
    margin: 20px 0;
    box-shadow: 0 4px 20px rgba(0,0,0,0.08);
}

.fd-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
    padding-bottom: 16px;
    border-bottom: 2px solid var(--border-color, #e0e0e0);
}

.fd-header h2 {
    font-size: 1.5rem;
    color: var(--text-primary, #333);
    display: flex;
    align-items: center;
    gap: 12px;
}

.fd-header h2 i {
    font-size: 1.8rem;
    color: var(--accent-color, #6366f1);
}

.fd-refresh-btn {
    background: var(--accent-color, #6366f1);
    color: white;
    border: none;
    padding: 10px 20px;
    border-radius: 8px;
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 500;
    transition: all 0.3s ease;
}

.fd-refresh-btn:hover {
    background: var(--accent-hover, #4f46e5);
    transform: translateY(-2px);
}

.fd-refresh-btn.loading i {
    animation: spin 1s linear infinite;
}

@keyframes spin {
    100% { transform: rotate(360deg); }
}

/* Health Score */
.fd-health-score {
    display: flex;
    align-items: center;
    gap: 32px;
    background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
    padding: 24px;
    border-radius: 12px;
    margin-bottom: 24px;
}

.fd-health-ring {
    position: relative;
    width: 140px;
    height: 140px;
}

.fd-health-ring svg {
    transform: rotate(-90deg);
    width: 140px;
    height: 140px;
}

.fd-health-ring circle {
    fill: none;
    stroke-width: 12;
}

.fd-health-ring .bg {
    stroke: #e2e8f0;
}

.fd-health-ring .progress {
    stroke-linecap: round;
    transition: stroke-dashoffset 1s ease-out;
}

.fd-health-value {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    text-align: center;
}

.fd-health-value .score {
    font-size: 2.5rem;
    font-weight: 700;
    line-height: 1;
}

.fd-health-value .label {
    font-size: 0.85rem;
    color: #64748b;
    margin-top: 4px;
}

.fd-health-details h3 {
    font-size: 1.25rem;
    color: var(--text-primary, #333);
    margin-bottom: 12px;
}

.fd-health-factors {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.fd-factor {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 12px;
    background: white;
    border-radius: 8px;
    font-size: 0.9rem;
}

.fd-factor .impact {
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 4px;
}

.fd-factor .impact.positive { background: #dcfce7; color: #16a34a; }
.fd-factor .impact.negative { background: #fee2e2; color: #dc2626; }

/* Grid de métricas */
.fd-metrics-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 16px;
    margin-bottom: 24px;
}

.fd-metric-card {
    background: white;
    border-radius: 12px;
    padding: 20px;
    border: 1px solid var(--border-color, #e0e0e0);
    transition: all 0.3s ease;
}

.fd-metric-card:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px rgba(0,0,0,0.1);
}

.fd-metric-card .icon {
    width: 48px;
    height: 48px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 1.5rem;
    margin-bottom: 12px;
}

.fd-metric-card .icon.income { background: #dcfce7; color: #16a34a; }
.fd-metric-card .icon.expense { background: #fee2e2; color: #dc2626; }
.fd-metric-card .icon.balance { background: #dbeafe; color: #2563eb; }
.fd-metric-card .icon.savings { background: #fef3c7; color: #d97706; }

.fd-metric-card .label {
    font-size: 0.85rem;
    color: #64748b;
    margin-bottom: 4px;
}

.fd-metric-card .value {
    font-size: 1.5rem;
    font-weight: 700;
    color: var(--text-primary, #333);
}

.fd-metric-card .trend {
    font-size: 0.85rem;
    margin-top: 8px;
    display: flex;
    align-items: center;
    gap: 4px;
}

.fd-metric-card .trend.up { color: #16a34a; }
.fd-metric-card .trend.down { color: #dc2626; }

/* Distribución 50/30/20 */
.fd-allocation {
    background: white;
    border-radius: 12px;
    padding: 24px;
    border: 1px solid var(--border-color, #e0e0e0);
    margin-bottom: 24px;
}

.fd-allocation h3 {
    font-size: 1.1rem;
    color: var(--text-primary, #333);
    margin-bottom: 20px;
    display: flex;
    align-items: center;
    gap: 8px;
}

.fd-allocation-bars {
    display: flex;
    flex-direction: column;
    gap: 20px;
}

.fd-allocation-item {
    display: grid;
    grid-template-columns: 120px 1fr 100px;
    align-items: center;
    gap: 16px;
}

.fd-allocation-item .category {
    font-weight: 500;
    color: var(--text-primary, #333);
}

.fd-allocation-item .bar-container {
    position: relative;
    height: 24px;
    background: #f1f5f9;
    border-radius: 12px;
    overflow: hidden;
}

.fd-allocation-item .bar {
    position: absolute;
    height: 100%;
    border-radius: 12px;
    transition: width 1s ease-out;
}

.fd-allocation-item .bar.ideal {
    background: #94a3b8;
    opacity: 0.3;
}

.fd-allocation-item .bar.current {
    background: var(--bar-color, #6366f1);
}

.fd-allocation-item .bar.over {
    background: #ef4444;
}

.fd-allocation-item .values {
    text-align: right;
    font-size: 0.9rem;
}

.fd-allocation-item .values .current {
    font-weight: 600;
    color: var(--text-primary, #333);
}

.fd-allocation-item .values .ideal {
    color: #64748b;
}

/* Recomendaciones */
.fd-recommendations {
    margin-bottom: 24px;
}

.fd-recommendations h3 {
    font-size: 1.1rem;
    color: var(--text-primary, #333);
    margin-bottom: 16px;
    display: flex;
    align-items: center;
    gap: 8px;
}

.fd-recommendation-card {
    display: flex;
    gap: 16px;
    padding: 20px;
    background: white;
    border-radius: 12px;
    border-left: 4px solid;
    margin-bottom: 12px;
    transition: all 0.3s ease;
}

.fd-recommendation-card:hover {
    transform: translateX(4px);
}

.fd-recommendation-card.critical { border-color: #ef4444; background: #fef2f2; }
.fd-recommendation-card.high { border-color: #f97316; background: #fff7ed; }
.fd-recommendation-card.medium { border-color: #eab308; background: #fefce8; }
.fd-recommendation-card.low { border-color: #3b82f6; background: #eff6ff; }
.fd-recommendation-card.success { border-color: #22c55e; background: #f0fdf4; }

.fd-recommendation-card .icon {
    font-size: 2rem;
    line-height: 1;
}

.fd-recommendation-card .content {
    flex: 1;
}

.fd-recommendation-card .content h4 {
    font-size: 1rem;
    margin-bottom: 8px;
    color: var(--text-primary, #333);
}

.fd-recommendation-card .content p {
    font-size: 0.9rem;
    color: #64748b;
    margin-bottom: 12px;
}

.fd-recommendation-card .actions {
    list-style: none;
    padding: 0;
    margin: 0;
}

.fd-recommendation-card .actions li {
    font-size: 0.85rem;
    padding: 4px 0;
    padding-left: 20px;
    position: relative;
    color: #475569;
}

.fd-recommendation-card .actions li::before {
    content: '→';
    position: absolute;
    left: 0;
    color: var(--accent-color, #6366f1);
}

/* Proyecciones */
.fd-projections {
    background: white;
    border-radius: 12px;
    padding: 24px;
    border: 1px solid var(--border-color, #e0e0e0);
    margin-bottom: 24px;
}

.fd-projections h3 {
    font-size: 1.1rem;
    color: var(--text-primary, #333);
    margin-bottom: 20px;
}

.fd-projection-tabs {
    display: flex;
    gap: 8px;
    margin-bottom: 20px;
}

.fd-projection-tab {
    padding: 8px 20px;
    border-radius: 8px;
    border: 1px solid var(--border-color, #e0e0e0);
    background: white;
    cursor: pointer;
    font-weight: 500;
    transition: all 0.3s ease;
}

.fd-projection-tab.active {
    background: var(--accent-color, #6366f1);
    color: white;
    border-color: var(--accent-color, #6366f1);
}

.fd-projection-content {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
}

.fd-projection-stat {
    text-align: center;
    padding: 16px;
    background: #f8fafc;
    border-radius: 8px;
}

.fd-projection-stat .label {
    font-size: 0.85rem;
    color: #64748b;
    margin-bottom: 4px;
}

.fd-projection-stat .value {
    font-size: 1.25rem;
    font-weight: 700;
}

.fd-projection-stat .value.positive { color: #16a34a; }
.fd-projection-stat .value.negative { color: #dc2626; }

/* Modal de vinculación */
.fd-link-modal {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0,0,0,0.5);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 10000;
    opacity: 0;
    visibility: hidden;
    transition: all 0.3s ease;
}

.fd-link-modal.active {
    opacity: 1;
    visibility: visible;
}

.fd-link-modal-content {
    background: white;
    border-radius: 16px;
    max-width: 600px;
    width: 90%;
    max-height: 80vh;
    overflow-y: auto;
    padding: 24px;
    transform: scale(0.9);
    transition: transform 0.3s ease;
}

.fd-link-modal.active .fd-link-modal-content {
    transform: scale(1);
}

.fd-link-modal h3 {
    font-size: 1.25rem;
    margin-bottom: 16px;
    display: flex;
    align-items: center;
    gap: 12px;
}

.fd-link-expense-info {
    background: #f8fafc;
    padding: 16px;
    border-radius: 8px;
    margin-bottom: 20px;
}

.fd-link-expense-info .name {
    font-weight: 600;
    font-size: 1.1rem;
}

.fd-link-expense-info .amount {
    color: #dc2626;
    font-size: 1.25rem;
    font-weight: 700;
}

.fd-income-sources {
    display: flex;
    flex-direction: column;
    gap: 12px;
    margin-bottom: 20px;
}

.fd-income-source {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 16px;
    background: #f8fafc;
    border-radius: 8px;
    border: 2px solid transparent;
    cursor: pointer;
    transition: all 0.3s ease;
}

.fd-income-source:hover,
.fd-income-source.selected {
    border-color: var(--accent-color, #6366f1);
    background: #eff6ff;
}

.fd-income-source input[type="checkbox"] {
    width: 20px;
    height: 20px;
    accent-color: var(--accent-color, #6366f1);
}

.fd-income-source .info {
    flex: 1;
}

.fd-income-source .name {
    font-weight: 500;
}

.fd-income-source .monthly {
    font-size: 0.9rem;
    color: #16a34a;
}

.fd-income-source .allocation-input {
    width: 80px;
    padding: 8px;
    border: 1px solid #e0e0e0;
    border-radius: 6px;
    text-align: center;
    font-weight: 500;
}

.fd-link-impact {
    padding: 16px;
    border-radius: 8px;
    margin-bottom: 20px;
}

.fd-link-impact.viable { background: #f0fdf4; border: 1px solid #22c55e; }
.fd-link-impact.caution { background: #fefce8; border: 1px solid #eab308; }
.fd-link-impact.not_recommended { background: #fef2f2; border: 1px solid #ef4444; }

.fd-link-modal-actions {
    display: flex;
    gap: 12px;
    justify-content: flex-end;
}

.fd-link-modal-actions button {
    padding: 12px 24px;
    border-radius: 8px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.3s ease;
}

.fd-link-modal-actions .cancel {
    background: #f1f5f9;
    border: none;
    color: #64748b;
}

.fd-link-modal-actions .confirm {
    background: var(--accent-color, #6366f1);
    border: none;
    color: white;
}

/* Responsive */
@media (max-width: 768px) {
    .fd-health-score {
        flex-direction: column;
        text-align: center;
    }
    
    .fd-allocation-item {
        grid-template-columns: 1fr;
        gap: 8px;
    }
    
    .fd-projection-content {
        grid-template-columns: 1fr;
    }
}
</style>
`;

// ============================================================================
// CLASE PRINCIPAL DEL DASHBOARD
// ============================================================================

class FinancialDashboard {
    constructor(containerId) {
        this.containerId = containerId;
        this.container = null;
        this.analysis = null;
        this.selectedProjection = 0;
        this.initialized = false;
    }
    
    /**
     * Inicializa el dashboard
     */
    async init() {
        this.container = document.getElementById(this.containerId);
        if (!this.container) {
            console.error(`Container #${this.containerId} not found`);
            return false;
        }
        
        // Inyectar estilos
        if (!document.getElementById('financial-dashboard-styles')) {
            document.head.insertAdjacentHTML('beforeend', dashboardStyles);
        }
        
        // Mostrar loading
        this.container.innerHTML = this.renderLoading();
        
        try {
            // Cargar análisis
            this.analysis = await getFullFinancialAnalysis();
            
            // Renderizar dashboard
            this.render();
            
            // Configurar eventos
            this.setupEvents();
            
            this.initialized = true;
            return true;
        } catch (error) {
            console.error('Error initializing financial dashboard:', error);
            this.container.innerHTML = this.renderError(error);
            return false;
        }
    }
    
    /**
     * Renderiza el loading
     */
    renderLoading() {
        return `
            <div class="financial-dashboard">
                <div style="text-align: center; padding: 60px 20px;">
                    <i class="fas fa-chart-line fa-3x" style="color: var(--accent-color); margin-bottom: 20px;"></i>
                    <h3>Analizando tus finanzas...</h3>
                    <p style="color: #64748b;">Esto solo tomará un momento</p>
                </div>
            </div>
        `;
    }
    
    /**
     * Renderiza mensaje de error
     */
    renderError(error) {
        return `
            <div class="financial-dashboard">
                <div style="text-align: center; padding: 60px 20px;">
                    <i class="fas fa-exclamation-triangle fa-3x" style="color: #ef4444; margin-bottom: 20px;"></i>
                    <h3>Error al cargar el análisis</h3>
                    <p style="color: #64748b;">${error.message}</p>
                    <button class="fd-refresh-btn" onclick="window.financialDashboard.init()" style="margin: 20px auto;">
                        <i class="fas fa-sync-alt"></i> Reintentar
                    </button>
                </div>
            </div>
        `;
    }
    
    /**
     * Renderiza el dashboard completo
     */
    render() {
        if (!this.analysis) return;
        
        const { income, expenses, balance, ratios, health, allocations, recommendations, projections, plans } = this.analysis;
        
        this.container.innerHTML = `
            <div class="financial-dashboard">
                <!-- Header -->
                <div class="fd-header">
                    <h2>
                        <i class="fas fa-chart-pie"></i>
                        Análisis Financiero Inteligente
                    </h2>
                    <button class="fd-refresh-btn" id="fd-refresh">
                        <i class="fas fa-sync-alt"></i>
                        Actualizar
                    </button>
                </div>
                
                <!-- Health Score -->
                ${this.renderHealthScore(health)}
                
                <!-- Métricas principales -->
                ${this.renderMetrics(income, expenses, balance, ratios)}
                
                <!-- Distribución 50/30/20 -->
                ${this.renderAllocation(allocations, income.totals.monthly)}
                
                <!-- Recomendaciones -->
                ${this.renderRecommendations(recommendations)}
                
                <!-- Proyecciones -->
                ${this.renderProjections(projections)}
                
                <!-- Metas en riesgo -->
                ${plans.length > 0 ? this.renderPlansAnalysis(plans) : ''}
            </div>
        `;
    }
    
    /**
     * Renderiza el score de salud financiera
     */
    renderHealthScore(health) {
        const circumference = 2 * Math.PI * 54;
        const offset = circumference - (health.score / 100) * circumference;
        
        return `
            <div class="fd-health-score">
                <div class="fd-health-ring">
                    <svg viewBox="0 0 140 140">
                        <circle class="bg" cx="70" cy="70" r="54"/>
                        <circle class="progress" cx="70" cy="70" r="54" 
                                stroke="${health.color}"
                                stroke-dasharray="${circumference}"
                                stroke-dashoffset="${offset}"/>
                    </svg>
                    <div class="fd-health-value">
                        <div class="score" style="color: ${health.color}">${health.score}</div>
                        <div class="label">${health.level}</div>
                    </div>
                </div>
                <div class="fd-health-details">
                    <h3>Salud Financiera</h3>
                    <div class="fd-health-factors">
                        ${health.factors.map(f => `
                            <div class="fd-factor">
                                <span>${f.factor}</span>
                                <span class="impact ${f.impact.startsWith('+') ? 'positive' : 'negative'}">${f.impact}</span>
                            </div>
                        `).join('')}
                    </div>
                </div>
            </div>
        `;
    }
    
    /**
     * Renderiza las métricas principales
     */
    renderMetrics(income, expenses, balance, ratios) {
        return `
            <div class="fd-metrics-grid">
                <div class="fd-metric-card">
                    <div class="icon income"><i class="fas fa-arrow-down"></i></div>
                    <div class="label">Ingresos Mensuales</div>
                    <div class="value">${formatCurrency(income.totals.monthly)}</div>
                    <div class="trend up">
                        <i class="fas fa-chart-line"></i>
                        ${income.sources.count} fuente${income.sources.count !== 1 ? 's' : ''}
                    </div>
                </div>
                
                <div class="fd-metric-card">
                    <div class="icon expense"><i class="fas fa-arrow-up"></i></div>
                    <div class="label">Gastos Mensuales</div>
                    <div class="value">${formatCurrency(expenses.totals.monthly)}</div>
                    <div class="trend ${ratios.expenseToIncome > 100 ? 'down' : 'up'}">
                        <i class="fas fa-percentage"></i>
                        ${ratios.expenseToIncome.toFixed(0)}% del ingreso
                    </div>
                </div>
                
                <div class="fd-metric-card">
                    <div class="icon balance"><i class="fas fa-balance-scale"></i></div>
                    <div class="label">Balance Mensual</div>
                    <div class="value" style="color: ${balance.monthly >= 0 ? '#16a34a' : '#dc2626'}">
                        ${balance.monthly >= 0 ? '+' : ''}${formatCurrency(balance.monthly)}
                    </div>
                    <div class="trend ${balance.monthly >= 0 ? 'up' : 'down'}">
                        <i class="fas fa-${balance.monthly >= 0 ? 'smile' : 'frown'}"></i>
                        ${balance.monthly >= 0 ? 'Superávit' : 'Déficit'}
                    </div>
                </div>
                
                <div class="fd-metric-card">
                    <div class="icon savings"><i class="fas fa-piggy-bank"></i></div>
                    <div class="label">Tasa de Ahorro</div>
                    <div class="value">${ratios.savingsRate.toFixed(1)}%</div>
                    <div class="trend ${ratios.savingsRate >= 20 ? 'up' : 'down'}">
                        <i class="fas fa-${ratios.savingsRate >= 20 ? 'check' : 'exclamation-triangle'}"></i>
                        ${ratios.savingsRate >= 20 ? 'Óptimo' : 'Mejorable'}
                    </div>
                </div>
            </div>
        `;
    }
    
    /**
     * Renderiza la distribución 50/30/20
     */
    renderAllocation(allocations, monthlyIncome) {
        const maxValue = monthlyIncome || 1;
        
        const categories = [
            { 
                key: 'necessities', 
                label: 'Necesidades', 
                color: '#ef4444',
                ideal: RECOMMENDED_ALLOCATIONS.necessities * 100
            },
            { 
                key: 'wants', 
                label: 'Deseos', 
                color: '#f97316',
                ideal: RECOMMENDED_ALLOCATIONS.wants * 100
            },
            { 
                key: 'savings', 
                label: 'Ahorro', 
                color: '#22c55e',
                ideal: RECOMMENDED_ALLOCATIONS.savings * 100
            }
        ];
        
        return `
            <div class="fd-allocation">
                <h3><i class="fas fa-chart-bar"></i> Distribución de Gastos (Regla 50/30/20)</h3>
                <div class="fd-allocation-bars">
                    ${categories.map(cat => {
                        const currentAmount = allocations.current[cat.key] || 0;
                        const idealAmount = allocations.ideal[cat.key] || 0;
                        const currentPercent = (currentAmount / maxValue) * 100;
                        const idealPercent = cat.ideal;
                        const isOver = currentPercent > idealPercent;
                        
                        return `
                            <div class="fd-allocation-item">
                                <div class="category">${cat.label}</div>
                                <div class="bar-container">
                                    <div class="bar ideal" style="width: ${idealPercent}%"></div>
                                    <div class="bar ${isOver ? 'over' : 'current'}" 
                                         style="width: ${Math.min(currentPercent, 100)}%; --bar-color: ${cat.color}">
                                    </div>
                                </div>
                                <div class="values">
                                    <div class="current">${formatCurrency(currentAmount)}</div>
                                    <div class="ideal">Ideal: ${formatCurrency(idealAmount)}</div>
                                </div>
                            </div>
                        `;
                    }).join('')}
                </div>
                
                ${allocations.suggestions.length > 0 ? `
                    <div style="margin-top: 16px; padding-top: 16px; border-top: 1px solid #e0e0e0;">
                        ${allocations.suggestions.map(s => `
                            <div style="display: flex; gap: 8px; align-items: start; margin-bottom: 8px;">
                                <i class="fas fa-${s.type === 'critical' ? 'exclamation-circle' : s.type === 'warning' ? 'exclamation-triangle' : 'info-circle'}" 
                                   style="color: ${s.type === 'critical' ? '#dc2626' : s.type === 'warning' ? '#f97316' : '#3b82f6'}; margin-top: 4px;"></i>
                                <div>
                                    <div style="font-size: 0.9rem; color: var(--text-primary);">${s.message}</div>
                                    <div style="font-size: 0.85rem; color: #64748b;">${s.action}</div>
                                </div>
                            </div>
                        `).join('')}
                    </div>
                ` : ''}
            </div>
        `;
    }
    
    /**
     * Renderiza las recomendaciones
     */
    renderRecommendations(recommendations) {
        return `
            <div class="fd-recommendations">
                <h3><i class="fas fa-lightbulb"></i> Recomendaciones Personalizadas</h3>
                ${recommendations.map(rec => `
                    <div class="fd-recommendation-card ${rec.priority}">
                        <div class="icon">${rec.icon}</div>
                        <div class="content">
                            <h4>${rec.title}</h4>
                            <p>${rec.message}</p>
                            <ul class="actions">
                                ${rec.actions.map(a => `<li>${a}</li>`).join('')}
                            </ul>
                        </div>
                    </div>
                `).join('')}
            </div>
        `;
    }
    
    /**
     * Renderiza las proyecciones
     */
    renderProjections(projections) {
        const selected = projections[this.selectedProjection];
        
        return `
            <div class="fd-projections">
                <h3><i class="fas fa-chart-area"></i> Proyecciones Financieras</h3>
                
                <div class="fd-projection-tabs">
                    ${projections.map((p, i) => `
                        <button class="fd-projection-tab ${i === this.selectedProjection ? 'active' : ''}" 
                                data-index="${i}">
                            ${p.label}
                        </button>
                    `).join('')}
                </div>
                
                <div class="fd-projection-content">
                    <div class="fd-projection-stat">
                        <div class="label">Ingresos proyectados</div>
                        <div class="value positive">${formatCurrency(selected.projected.income)}</div>
                    </div>
                    <div class="fd-projection-stat">
                        <div class="label">Gastos proyectados</div>
                        <div class="value negative">${formatCurrency(selected.projected.expenses)}</div>
                    </div>
                    <div class="fd-projection-stat">
                        <div class="label">Balance acumulado</div>
                        <div class="value ${selected.projected.balance >= 0 ? 'positive' : 'negative'}">
                            ${selected.projected.balance >= 0 ? '+' : ''}${formatCurrency(selected.projected.balance)}
                        </div>
                    </div>
                </div>
                
                ${selected.plansProgress.length > 0 ? `
                    <div style="margin-top: 20px;">
                        <div style="font-weight: 500; margin-bottom: 12px;">Progreso de metas en ${selected.label}:</div>
                        ${selected.plansProgress.map(p => `
                            <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 8px;">
                                <span style="width: 150px; font-size: 0.9rem;">${p.name}</span>
                                <div style="flex: 1; height: 8px; background: #e2e8f0; border-radius: 4px; overflow: hidden;">
                                    <div style="width: ${p.projectedProgress}%; height: 100%; background: linear-gradient(90deg, #6366f1, #8b5cf6); border-radius: 4px;"></div>
                                </div>
                                <span style="width: 50px; text-align: right; font-size: 0.85rem; font-weight: 500;">
                                    ${p.projectedProgress.toFixed(0)}%
                                </span>
                            </div>
                        `).join('')}
                    </div>
                ` : ''}
            </div>
        `;
    }
    
    /**
     * Renderiza análisis de metas
     */
    renderPlansAnalysis(plans) {
        const riskyPlans = plans.filter(p => 
            p.analysis?.feasibility === 'very_difficult' || 
            p.analysis?.feasibility === 'difficult'
        );
        
        if (riskyPlans.length === 0) return '';
        
        return `
            <div class="fd-allocation" style="border-left: 4px solid #f97316;">
                <h3><i class="fas fa-flag"></i> Metas que requieren atención</h3>
                <div style="display: flex; flex-direction: column; gap: 16px;">
                    ${riskyPlans.map(plan => `
                        <div style="padding: 16px; background: #fff7ed; border-radius: 8px;">
                            <div style="display: flex; justify-content: space-between; align-items: start; margin-bottom: 8px;">
                                <div>
                                    <div style="font-weight: 600;">${plan.name}</div>
                                    <div style="font-size: 0.85rem; color: #64748b;">
                                        Meta: ${formatCurrency(plan.target_amount)} | 
                                        Actual: ${formatCurrency(plan.current_amount)}
                                    </div>
                                </div>
                                <span style="background: #fef3c7; color: #d97706; padding: 4px 12px; border-radius: 4px; font-size: 0.8rem; font-weight: 500;">
                                    ${plan.analysis.feasibility === 'very_difficult' ? 'Muy difícil' : 'Difícil'}
                                </span>
                            </div>
                            <div style="font-size: 0.9rem; color: #475569;">
                                <p>Requiere <strong>${formatCurrency(plan.analysis.requiredMonthly)}/mes</strong> 
                                   (${plan.analysis.percentOfIncome.toFixed(1)}% de tus ingresos)</p>
                                <p style="margin-top: 8px;">
                                    💡 <strong>Sugerencia:</strong> Con un aporte cómodo de 
                                    ${formatCurrency(plan.analysis.optimalMonthly)}/mes (15% del ingreso), 
                                    podrías alcanzar esta meta para <strong>${formatDate(plan.analysis.optimalDate)}</strong>
                                </p>
                            </div>
                        </div>
                    `).join('')}
                </div>
            </div>
        `;
    }
    
    /**
     * Configura los event listeners
     */
    setupEvents() {
        // Botón de refresh
        const refreshBtn = document.getElementById('fd-refresh');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => this.refresh());
        }
        
        // Tabs de proyección
        const tabs = this.container.querySelectorAll('.fd-projection-tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', (e) => {
                this.selectedProjection = parseInt(e.target.dataset.index);
                this.render();
                this.setupEvents();
            });
        });
    }
    
    /**
     * Refresca el análisis
     */
    async refresh() {
        const refreshBtn = document.getElementById('fd-refresh');
        if (refreshBtn) {
            refreshBtn.classList.add('loading');
        }
        
        try {
            this.analysis = await getFullFinancialAnalysis();
            this.render();
            this.setupEvents();
        } catch (error) {
            console.error('Error refreshing analysis:', error);
        } finally {
            const btn = document.getElementById('fd-refresh');
            if (btn) {
                btn.classList.remove('loading');
            }
        }
    }
}

// ============================================================================
// MODAL DE VINCULACIÓN INTELIGENTE
// ============================================================================

class ExpenseLinkingModal {
    constructor() {
        this.modalElement = null;
        this.expenseData = null;
        this.suggestions = null;
        this.selectedIncomes = [];
    }
    
    /**
     * Muestra el modal para vincular un gasto
     */
    async show(expenseData) {
        this.expenseData = expenseData;
        this.selectedIncomes = [];
        
        // Obtener sugerencias del motor
        this.suggestions = await suggestExpenseAllocation(expenseData);
        
        this.createModal();
        this.render();
        this.setupEvents();
        
        setTimeout(() => {
            this.modalElement.classList.add('active');
        }, 10);
    }
    
    /**
     * Crea el elemento del modal
     */
    createModal() {
        // Remover modal anterior si existe
        const existing = document.querySelector('.fd-link-modal');
        if (existing) existing.remove();
        
        // Crear nuevo modal
        this.modalElement = document.createElement('div');
        this.modalElement.className = 'fd-link-modal';
        document.body.appendChild(this.modalElement);
    }
    
    /**
     * Renderiza el contenido del modal
     */
    render() {
        const { expense, suggestions, feasibility, warning, impact, optimalAmount } = this.suggestions;
        
        this.modalElement.innerHTML = `
            <div class="fd-link-modal-content">
                <h3>
                    <i class="fas fa-link"></i>
                    Vincular Gasto a Ingresos
                </h3>
                
                <!-- Info del gasto -->
                <div class="fd-link-expense-info">
                    <div class="name">${expense.name}</div>
                    <div class="amount">${formatCurrency(expense.monthlyEquivalent)}/mes</div>
                    <div style="font-size: 0.85rem; color: #64748b; margin-top: 4px;">
                        ${expense.percentOfIncome.toFixed(1)}% de tus ingresos
                    </div>
                </div>
                
                <!-- Impacto -->
                <div class="fd-link-impact ${feasibility}">
                    <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
                        <i class="fas fa-${feasibility === 'viable' ? 'check-circle' : feasibility === 'caution' ? 'exclamation-triangle' : 'times-circle'}"></i>
                        <strong>${impact.recommendation}</strong>
                    </div>
                    <div style="font-size: 0.9rem;">
                        Ahorro actual: ${formatCurrency(impact.currentMonthlySavings)} → 
                        Nuevo: ${formatCurrency(impact.newMonthlySavings)}
                    </div>
                    ${warning ? `<div style="font-size: 0.85rem; color: #dc2626; margin-top: 8px;">${warning}</div>` : ''}
                </div>
                
                <!-- Monto óptimo sugerido -->
                <div style="padding: 12px; background: #eff6ff; border-radius: 8px; margin-bottom: 20px;">
                    <div style="font-size: 0.85rem; color: #3b82f6;">
                        <i class="fas fa-lightbulb"></i> ${optimalAmount.explanation}
                    </div>
                    <div style="font-weight: 600; margin-top: 4px;">
                        Máximo recomendado: ${formatCurrency(optimalAmount.maxRecommended)}/mes
                    </div>
                </div>
                
                <!-- Fuentes de ingreso -->
                <div style="font-weight: 500; margin-bottom: 12px;">Asignar desde:</div>
                <div class="fd-income-sources">
                    ${suggestions.map(inc => `
                        <label class="fd-income-source" data-id="${inc.id}">
                            <input type="checkbox" value="${inc.id}">
                            <div class="info">
                                <div class="name">${inc.name}</div>
                                <div class="monthly">${formatCurrency(inc.monthlyEquivalent)}/mes</div>
                            </div>
                            <input type="number" class="allocation-input" 
                                   placeholder="${formatCurrency(inc.suggestedAllocation)}"
                                   step="100" min="0">
                        </label>
                    `).join('')}
                </div>
                
                <!-- Acciones -->
                <div class="fd-link-modal-actions">
                    <button class="cancel">Cancelar</button>
                    <button class="confirm">
                        <i class="fas fa-link"></i> Vincular
                    </button>
                </div>
            </div>
        `;
    }
    
    /**
     * Configura eventos del modal
     */
    setupEvents() {
        // Cerrar al hacer clic fuera
        this.modalElement.addEventListener('click', (e) => {
            if (e.target === this.modalElement) {
                this.close();
            }
        });
        
        // Botón cancelar
        this.modalElement.querySelector('.cancel').addEventListener('click', () => {
            this.close();
        });
        
        // Checkboxes
        this.modalElement.querySelectorAll('.fd-income-source input[type="checkbox"]').forEach(cb => {
            cb.addEventListener('change', (e) => {
                const source = e.target.closest('.fd-income-source');
                source.classList.toggle('selected', e.target.checked);
            });
        });
        
        // Botón confirmar
        this.modalElement.querySelector('.confirm').addEventListener('click', () => {
            this.handleConfirm();
        });
    }
    
    /**
     * Maneja la confirmación
     */
    async handleConfirm() {
        const allocations = [];
        
        this.modalElement.querySelectorAll('.fd-income-source').forEach(source => {
            const checkbox = source.querySelector('input[type="checkbox"]');
            const amountInput = source.querySelector('.allocation-input');
            
            if (checkbox.checked) {
                allocations.push({
                    income_pattern_id: source.dataset.id,
                    fixed_amount: parseFloat(amountInput.value) || null
                });
            }
        });
        
        if (this.expenseData.id && allocations.length > 0) {
            try {
                await linkExpenseToIncomes(this.expenseData.id, allocations);
                this.close();
                
                // Mostrar notificación de éxito
                if (window.Swal) {
                    Swal.fire({
                        icon: 'success',
                        title: 'Vinculación exitosa',
                        text: 'El gasto ha sido vinculado a las fuentes de ingreso seleccionadas',
                        timer: 2000,
                        showConfirmButton: false
                    });
                }
            } catch (error) {
                console.error('Error linking expense:', error);
            }
        }
        
        this.close();
    }
    
    /**
     * Cierra el modal
     */
    close() {
        this.modalElement.classList.remove('active');
        setTimeout(() => {
            this.modalElement.remove();
        }, 300);
    }
}

// ============================================================================
// EXPORTACIONES E INICIALIZACIÓN
// ============================================================================

// Instancia global del dashboard
let dashboardInstance = null;
let linkingModalInstance = null;

/**
 * Inicializa el dashboard financiero
 */
export async function initFinancialDashboard(containerId = 'financial-dashboard') {
    dashboardInstance = new FinancialDashboard(containerId);
    window.financialDashboard = dashboardInstance;
    return await dashboardInstance.init();
}

/**
 * Muestra el modal de vinculación de gastos
 */
export async function showExpenseLinkingModal(expenseData) {
    if (!linkingModalInstance) {
        linkingModalInstance = new ExpenseLinkingModal();
    }
    await linkingModalInstance.show(expenseData);
}

/**
 * Obtiene la instancia del dashboard
 */
export function getDashboardInstance() {
    return dashboardInstance;
}

export { FinancialDashboard, ExpenseLinkingModal };
