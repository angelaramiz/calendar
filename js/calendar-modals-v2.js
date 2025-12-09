/**
 * calendar-modals-v2.js
 * Modales para el sistema V2 (confirmar ocurrencias, ver detalles)
 */

import { supabase } from './supabase-client.js';
import { confirmPatternOccurrence } from './movements.js';
import { getMovementById, updateMovement, deleteMovement } from './movements.js';
import { getLoanById } from './loans-v2.js';
import { getPlanById } from './plans-v2.js';
import { createIncomePattern, createExpensePattern, getIncomePatternById, getExpensePatternById, updateIncomePattern, updateExpensePattern, getIncomePatterns, getExpensePatternWithSources, getExpensePatternIncomeSources, replaceExpensePatternIncomeSources, calculateExpensePatternCoverage } from './patterns.js';
import { createPlan } from './plans-v2.js';
import { getConfirmedBalanceSummary, getIncomePatternAllocations, calculateAvailablePercentage, formatCurrency } from './balance.js';
import { getSavingsPatterns, getSavingsSummary, createSavingsDeposit, createSavingsWithdrawal, getSavingsPatternById, createSavingsPattern, getSavingsSuggestionsForIncome, getRemainderSavingsSuggestion } from './savings.js';
import { analyzeNewExpense, analyzeNewPlan, analyzeNewSavings, generateAnalysisPanel, generateQuickInsight, loadFinancialState } from './smart-financial-assistant.js';

// SweetAlert2 está disponible globalmente desde index.html
const Swal = window.Swal;


// ============================================================================
// MODAL: CONFIRMAR PROJECTED EVENT
// ============================================================================

/**
 * Muestra modal para confirmar una ocurrencia proyectada
 */
export async function showConfirmProjectedDialog(projectionData, onConfirmed) {
    // Normalizar campos (puede venir 'name' o 'title', 'expected_amount' o 'amount')
    const title = projectionData.title || projectionData.name;
    const expectedAmount = projectionData.amount || projectionData.expected_amount;
    
    const result = await Swal.fire({
        title: '✅ Confirmar Evento Proyectado',
        html: `
            <div style="text-align: left; padding: 10px;">
                <p><strong>${title}</strong></p>
                <p style="color: #666; margin-top: 8px;">${projectionData.description || ''}</p>
                
                <div style="margin-top: 20px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Monto esperado:
                    </label>
                    <p style="font-size: 1.2em; color: #3b82f6; margin: 0;">
                        $${expectedAmount}
                    </p>
                </div>

                <div style="margin-top: 20px;">
                    <label for="confirmed-amount" style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Monto confirmado:
                    </label>
                    <input 
                        id="confirmed-amount" 
                        type="number" 
                        step="0.01"
                        value="${expectedAmount}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="Ingresa el monto real"
                    />
                </div>

                <div style="margin-top: 20px;">
                    <label for="confirmed-date" style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Fecha:
                    </label>
                    <input 
                        id="confirmed-date" 
                        type="date" 
                        value="${projectionData.date}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                    />
                </div>
            </div>
        `,
        showCancelButton: true,
        showDenyButton: true,
        confirmButtonText: 'Confirmar',
        denyButtonText: 'Ver Patrón',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#10b981',
        denyButtonColor: '#3b82f6',
        cancelButtonColor: '#6b7280',
        focusConfirm: false,
        preConfirm: () => {
            const amount = document.getElementById('confirmed-amount').value;
            const date = document.getElementById('confirmed-date').value;
            
            if (!amount || parseFloat(amount) <= 0) {
                Swal.showValidationMessage('Ingresa un monto válido');
                return false;
            }
            if (!date) {
                Swal.showValidationMessage('Ingresa una fecha válida');
                return false;
            }
            
            return { amount: parseFloat(amount), date };
        }
    });

    console.log('Result from Swal:', result);

    if (result.isConfirmed) {
        const formValues = result.value;
        try {
            // Actualizar fecha si cambió
            const occurrenceData = { ...projectionData, date: formValues.date };
            
            // Confirmar la ocurrencia
            const movement = await confirmPatternOccurrence(occurrenceData, formValues.amount);
            
            // Actualizar el indicador de balance
            if (window.refreshBalanceIndicator) window.refreshBalanceIndicator();
            
            await Swal.fire({
                icon: 'success',
                title: '✅ Confirmado',
                text: 'Evento confirmado exitosamente',
                timer: 1500,
                showConfirmButton: false
            });

            // === AUTOMATIZACIÓN DE AHORRO Y PLANES ===
            // Si es un ingreso, verificar si hay ahorros y planes vinculados
            if (projectionData.pattern_type === 'income' && projectionData.pattern_id) {
                // Primero ahorros
                await promptSavingsAfterIncomeConfirmation(
                    projectionData.pattern_id, 
                    formValues.amount
                );
                
                // Luego planes
                await promptPlanContributionsAfterIncomeConfirmation(
                    projectionData.pattern_id,
                    formValues.amount
                );
            }
            
            // Si es un gasto vinculado a ingreso, verificar si hay sobrante para ahorro
            if (projectionData.pattern_type === 'expense' && projectionData.pattern_id) {
                await promptSavingsFromExpenseSurplus(
                    projectionData.pattern_id,
                    projectionData.expected_amount,
                    formValues.amount
                );
            }

            if (onConfirmed) onConfirmed(movement);
        } catch (error) {
            console.error('Error confirming occurrence:', error);
            await Swal.fire({
                icon: 'error',
                title: 'Error',
                text: error.message || 'No se pudo confirmar el evento'
            });
        }
    } else if (result.isDenied) {
        // Ver/Editar patrón
        console.log('Opening pattern details:', projectionData.pattern_id, projectionData.pattern_type);
        await showPatternDetails(projectionData.pattern_id, projectionData.pattern_type, onConfirmed);
    }
}

// ============================================================================
// AUTOMATIZACIÓN DE AHORRO
// ============================================================================

/**
 * Pregunta al usuario si desea depositar en ahorro después de confirmar un ingreso
 */
async function promptSavingsAfterIncomeConfirmation(incomePatternId, confirmedAmount) {
    try {
        const suggestions = await getSavingsSuggestionsForIncome(incomePatternId, confirmedAmount);
        
        if (!suggestions.hasSuggestions || suggestions.savings.length === 0) {
            return; // No hay ahorros vinculados
        }

        const savingsListHTML = suggestions.savings.map((s, idx) => {
            const progress = s.target_amount 
                ? `<div style="font-size: 0.8em; color: #6b7280;">Meta: $${s.target_amount.toLocaleString('es-MX')} (${((s.current_balance / s.target_amount) * 100).toFixed(1)}%)</div>`
                : '';
            return `
                <div style="display: flex; align-items: center; padding: 10px; background: #f0fdf4; border-radius: 8px; margin-bottom: 8px;">
                    <input type="checkbox" id="savings-check-${idx}" data-savings-id="${s.savings_pattern_id}" data-amount="${s.suggested_amount}" checked style="width: 20px; height: 20px; margin-right: 12px;">
                    <div style="flex: 1;">
                        <div style="font-weight: 600; color: #166534;">🏦 ${s.name}</div>
                        <div style="font-size: 0.9em; color: #15803d;">
                            Sugerido: <strong>$${s.suggested_amount.toLocaleString('es-MX', { minimumFractionDigits: 2 })}</strong>
                        </div>
                        ${progress}
                    </div>
                </div>
            `;
        }).join('');

        const result = await Swal.fire({
            title: '💰 ¿Apartar para Ahorro?',
            html: `
                <div style="text-align: left; padding: 10px;">
                    <p style="margin-bottom: 15px; color: #374151;">
                        Has confirmado un ingreso de <strong style="color: #059669;">$${confirmedAmount.toLocaleString('es-MX', { minimumFractionDigits: 2 })}</strong>
                    </p>
                    <p style="margin-bottom: 15px; color: #6b7280; font-size: 0.9em;">
                        Tienes ahorros vinculados a este ingreso. ¿Deseas depositar automáticamente?
                    </p>
                    
                    <div id="savings-suggestions">
                        ${savingsListHTML}
                    </div>
                    
                    <div style="margin-top: 15px; padding: 10px; background: #eff6ff; border-radius: 8px;">
                        <strong>Total sugerido:</strong> 
                        <span style="color: #2563eb; font-weight: 600;">
                            $${suggestions.totalSuggested.toLocaleString('es-MX', { minimumFractionDigits: 2 })}
                        </span>
                    </div>
                </div>
            `,
            showCancelButton: true,
            confirmButtonText: '✅ Depositar Seleccionados',
            cancelButtonText: 'Omitir',
            confirmButtonColor: '#10b981',
            cancelButtonColor: '#6b7280',
            preConfirm: () => {
                const selected = [];
                suggestions.savings.forEach((s, idx) => {
                    const checkbox = document.getElementById(`savings-check-${idx}`);
                    if (checkbox && checkbox.checked) {
                        selected.push({
                            savings_pattern_id: s.savings_pattern_id,
                            amount: s.suggested_amount,
                            name: s.name
                        });
                    }
                });
                return selected;
            }
        });

        if (result.isConfirmed && result.value && result.value.length > 0) {
            // Crear los depósitos
            let successCount = 0;
            for (const deposit of result.value) {
                try {
                    await createSavingsDeposit(
                        deposit.savings_pattern_id, 
                        deposit.amount, 
                        `Depósito automático desde confirmación de ingreso`
                    );
                    successCount++;
                } catch (e) {
                    console.error(`Error depositing to ${deposit.name}:`, e);
                }
            }

            if (successCount > 0) {
                // Actualizar el indicador de balance
                if (window.refreshBalanceIndicator) window.refreshBalanceIndicator();
                
                await Swal.fire({
                    icon: 'success',
                    title: '🎉 Ahorro Exitoso',
                    text: `Se depositó en ${successCount} cuenta(s) de ahorro`,
                    timer: 2000,
                    showConfirmButton: false
                });
            }
        }
    } catch (error) {
        console.error('Error prompting savings after income:', error);
        // No mostrar error al usuario, es opcional
    }
}

/**
 * Pregunta al usuario si desea apartar el sobrante de un gasto al ahorro
 */
async function promptSavingsFromExpenseSurplus(expensePatternId, expectedAmount, confirmedAmount) {
    try {
        // Verificar si el gasto está vinculado a un ingreso
        const { getExpensePatternIncomeSources } = await import('./patterns.js');
        const incomeSources = await getExpensePatternIncomeSources(expensePatternId);
        
        if (!incomeSources || incomeSources.length === 0) {
            return; // No está vinculado a ningún ingreso
        }

        // Calcular si hay sobrante
        const expected = parseFloat(expectedAmount) || 0;
        const confirmed = parseFloat(confirmedAmount) || 0;
        const surplus = expected - confirmed;
        
        if (surplus <= 0) {
            return; // No hay sobrante
        }

        // Buscar ahorros vinculados al mismo ingreso
        const incomePatternId = incomeSources[0].income_pattern_id;
        const suggestion = await getRemainderSavingsSuggestion(incomePatternId, expected, confirmed);
        
        if (!suggestion.hasSuggestion || !suggestion.savings || suggestion.savings.length === 0) {
            return;
        }

        const result = await Swal.fire({
            title: '💵 ¡Gastaste menos de lo esperado!',
            html: `
                <div style="text-align: left; padding: 10px;">
                    <div style="background: #dcfce7; padding: 12px; border-radius: 8px; margin-bottom: 15px;">
                        <div style="font-size: 0.9em; color: #166534;">Esperado vs Confirmado</div>
                        <div style="display: flex; justify-content: space-between; margin-top: 5px;">
                            <span>Esperado: <strong>$${expected.toLocaleString('es-MX', { minimumFractionDigits: 2 })}</strong></span>
                            <span>Gastado: <strong>$${confirmed.toLocaleString('es-MX', { minimumFractionDigits: 2 })}</strong></span>
                        </div>
                        <div style="margin-top: 8px; font-size: 1.1em; color: #059669; font-weight: 600;">
                            Sobrante: $${surplus.toLocaleString('es-MX', { minimumFractionDigits: 2 })}
                        </div>
                    </div>
                    
                    <p style="color: #374151; margin-bottom: 10px;">
                        ¿Deseas apartar el sobrante a tu ahorro?
                    </p>
                    
                    <select id="surplus-savings-select" class="swal2-select" style="width: 100%;">
                        ${suggestion.savings.map(s => `
                            <option value="${s.savings_pattern_id}">
                                🏦 ${s.name} (Balance: $${s.current_balance.toLocaleString('es-MX')})
                            </option>
                        `).join('')}
                    </select>
                    
                    <div style="margin-top: 15px;">
                        <label style="font-weight: 500;">Monto a apartar:</label>
                        <input 
                            type="number" 
                            id="surplus-amount" 
                            value="${surplus}" 
                            max="${surplus}"
                            step="0.01"
                            class="swal2-input" 
                            style="margin-top: 5px; width: 100%;"
                        >
                    </div>
                </div>
            `,
            showCancelButton: true,
            confirmButtonText: '💰 Apartar al Ahorro',
            cancelButtonText: 'No, gracias',
            confirmButtonColor: '#10b981',
            cancelButtonColor: '#6b7280',
            preConfirm: () => {
                const savingsId = document.getElementById('surplus-savings-select').value;
                const amount = parseFloat(document.getElementById('surplus-amount').value);
                
                if (!savingsId || !amount || amount <= 0) {
                    Swal.showValidationMessage('Selecciona un ahorro y un monto válido');
                    return false;
                }
                
                if (amount > surplus) {
                    Swal.showValidationMessage(`El monto no puede ser mayor al sobrante ($${surplus.toFixed(2)})`);
                    return false;
                }
                
                return { savings_pattern_id: savingsId, amount };
            }
        });

        if (result.isConfirmed && result.value) {
            try {
                await createSavingsDeposit(
                    result.value.savings_pattern_id, 
                    result.value.amount, 
                    'Sobrante de gasto apartado automáticamente'
                );

                // Actualizar el indicador de balance
                if (window.refreshBalanceIndicator) window.refreshBalanceIndicator();

                await Swal.fire({
                    icon: 'success',
                    title: '🎉 ¡Ahorro Exitoso!',
                    text: `Se apartaron $${result.value.amount.toFixed(2)} a tu ahorro`,
                    timer: 2000,
                    showConfirmButton: false
                });
            } catch (error) {
                console.error('Error depositing surplus:', error);
                await Swal.fire({
                    icon: 'error',
                    title: 'Error',
                    text: 'No se pudo realizar el depósito'
                });
            }
        }
    } catch (error) {
        console.error('Error prompting savings from expense surplus:', error);
    }
}

/**
 * Pregunta al usuario si desea contribuir a planes después de confirmar un ingreso
 */
async function promptPlanContributionsAfterIncomeConfirmation(incomePatternId, confirmedAmount) {
    try {
        const { getPlanSuggestionsForIncome, contributeToPlan, recalculatePlanDates } = await import('./plans-v2.js');
        const suggestions = await getPlanSuggestionsForIncome(incomePatternId, confirmedAmount);
        
        if (!suggestions.hasSuggestions || suggestions.plans.length === 0) {
            return; // No hay planes vinculados
        }

        const plansListHTML = suggestions.plans.map((p, idx) => {
            const progressColor = p.progress_percent >= 80 ? '#10b981' : p.progress_percent >= 50 ? '#f59e0b' : '#3b82f6';
            return `
                <div style="display: flex; align-items: center; padding: 12px; background: #eff6ff; border-radius: 8px; margin-bottom: 8px; border-left: 4px solid ${progressColor};">
                    <input type="checkbox" id="plan-check-${idx}" data-plan-id="${p.plan_id}" data-amount="${p.suggested_amount}" checked style="width: 20px; height: 20px; margin-right: 12px;">
                    <div style="flex: 1;">
                        <div style="font-weight: 600; color: #1e40af;">${p.name}</div>
                        <div style="font-size: 0.85em; color: #6b7280;">
                            Progreso: ${p.progress_percent}% · Meta: $${p.target_amount.toLocaleString('es-MX')}
                        </div>
                        <div style="font-size: 0.8em; color: #9ca3af;">
                            📅 Fecha objetivo: ${new Date(p.target_date).toLocaleDateString('es-MX')}
                        </div>
                    </div>
                    <div style="text-align: right;">
                        <input type="number" 
                            id="plan-amount-${idx}" 
                            value="${p.suggested_amount.toFixed(2)}" 
                            step="0.01" 
                            min="0"
                            max="${confirmedAmount}"
                            style="width: 100px; padding: 5px; border: 1px solid #d1d5db; border-radius: 4px; text-align: right;"
                        >
                        <div style="font-size: 0.75em; color: #6b7280; margin-top: 2px;">
                            ${p.allocation_type === 'percent' ? `(${(p.allocation_value * 100).toFixed(0)}%)` : '(fijo)'}
                        </div>
                    </div>
                </div>
            `;
        }).join('');

        const result = await Swal.fire({
            title: '🎯 Contribuir a Planeaciones',
            html: `
                <div style="text-align: left; padding: 10px;">
                    <p style="color: #374151; margin-bottom: 15px;">
                        Este ingreso tiene planes vinculados. ¿Deseas hacer las contribuciones?
                    </p>
                    <div style="background: #dbeafe; padding: 10px; border-radius: 8px; margin-bottom: 15px;">
                        <span style="font-weight: 600; color: #1e40af;">💰 Ingreso confirmado:</span>
                        <span style="color: #059669; font-weight: 600;"> $${confirmedAmount.toLocaleString('es-MX', { minimumFractionDigits: 2 })}</span>
                    </div>
                    <div style="max-height: 300px; overflow-y: auto;">
                        ${plansListHTML}
                    </div>
                </div>
            `,
            showCancelButton: true,
            confirmButtonText: '💰 Contribuir',
            cancelButtonText: 'Omitir',
            confirmButtonColor: '#3b82f6',
            cancelButtonColor: '#6b7280',
            width: '500px',
            preConfirm: () => {
                const contributions = [];
                suggestions.plans.forEach((p, idx) => {
                    const checkbox = document.getElementById(`plan-check-${idx}`);
                    const amountInput = document.getElementById(`plan-amount-${idx}`);
                    
                    if (checkbox && checkbox.checked && amountInput) {
                        const amount = parseFloat(amountInput.value) || 0;
                        if (amount > 0) {
                            contributions.push({
                                plan_id: p.plan_id,
                                plan_name: p.name,
                                amount: amount
                            });
                        }
                    }
                });
                return contributions;
            }
        });

        if (result.isConfirmed && result.value && result.value.length > 0) {
            let successCount = 0;
            let dateChanges = [];
            
            for (const contrib of result.value) {
                try {
                    await contributeToPlan(contrib.plan_id, contrib.amount, `Aporte desde ingreso`);
                    
                    // Recalcular fechas
                    const recalcResult = await recalculatePlanDates(contrib.plan_id, contrib.amount);
                    
                    successCount++;
                    
                    // Verificar si la fecha cambió o se completó
                    if (recalcResult.completed) {
                        dateChanges.push({
                            name: contrib.plan_name,
                            type: 'completed'
                        });
                    } else if (recalcResult.dateChanged) {
                        dateChanges.push({
                            name: contrib.plan_name,
                            type: 'date_changed',
                            old_date: recalcResult.old_target_date,
                            new_date: recalcResult.new_target_date
                        });
                    }
                } catch (err) {
                    console.error(`Error contributing to plan ${contrib.plan_id}:`, err);
                }
            }

            if (successCount > 0) {
                // Actualizar el indicador de balance
                if (window.refreshBalanceIndicator) window.refreshBalanceIndicator();
                
                // Preparar mensaje de éxito
                let htmlMessage = `<p>Se contribuyó a ${successCount} planeación(es)</p>`;
                
                // Agregar info de cambios de fecha
                if (dateChanges.length > 0) {
                    htmlMessage += '<div style="margin-top: 15px; text-align: left;">';
                    dateChanges.forEach(change => {
                        if (change.type === 'completed') {
                            htmlMessage += `
                                <div style="padding: 8px; background: #dcfce7; border-radius: 6px; margin-bottom: 8px;">
                                    <span style="color: #166534;">🎉 <strong>${change.name}</strong>: ¡Meta completada!</span>
                                </div>
                            `;
                        } else if (change.type === 'date_changed') {
                            const oldDate = new Date(change.old_date).toLocaleDateString('es-MX', { day: 'numeric', month: 'short', year: 'numeric' });
                            const newDate = new Date(change.new_date).toLocaleDateString('es-MX', { day: 'numeric', month: 'short', year: 'numeric' });
                            htmlMessage += `
                                <div style="padding: 8px; background: #dbeafe; border-radius: 6px; margin-bottom: 8px;">
                                    <span style="color: #1e40af;">📅 <strong>${change.name}</strong>: ${oldDate} → ${newDate}</span>
                                </div>
                            `;
                        }
                    });
                    htmlMessage += '</div>';
                }
                
                await Swal.fire({
                    icon: 'success',
                    title: '🎯 Contribuciones Exitosas',
                    html: htmlMessage,
                    timer: dateChanges.length > 0 ? undefined : 2500,
                    showConfirmButton: dateChanges.length > 0,
                    confirmButtonText: 'Entendido'
                });
            }
        }
    } catch (error) {
        console.error('Error prompting plan contributions after income:', error);
        // No mostrar error al usuario, es opcional
    }
}

// ============================================================================
// MODAL: VER DETALLES DE MOVEMENT
// ============================================================================

/**
 * Muestra detalles completos de un movement
 */
export async function showMovementDetails(movementId, onUpdated) {
    try {
        const movement = await getMovementById(movementId);
        if (!movement) {
            throw new Error('Movimiento no encontrado');
        }

        const typeLabel = movement.type === 'ingreso' ? '💰 Ingreso' : '💸 Gasto';
        const typeColor = movement.type === 'ingreso' ? '#10b981' : '#ef4444';

        const result = await Swal.fire({
            title: `${typeLabel}: ${movement.title}`,
            html: `
                <div style="text-align: left; padding: 10px;">
                    ${movement.description ? `<p style="color: #666; margin-bottom: 15px;">${movement.description}</p>` : ''}
                    
                    <div style="background: #f3f4f6; padding: 15px; border-radius: 8px; margin-bottom: 15px;">
                        <div style="margin-bottom: 10px;">
                            <strong>Monto:</strong> 
                            <span style="color: ${typeColor}; font-size: 1.2em;">$${movement.confirmed_amount}</span>
                        </div>
                        
                        ${movement.expected_amount && movement.expected_amount !== movement.confirmed_amount ? `
                            <div style="margin-bottom: 10px;">
                                <strong>Esperado:</strong> $${movement.expected_amount}
                                ${movement.difference ? `<span style="color: ${movement.difference > 0 ? '#10b981' : '#ef4444'};">(${movement.difference > 0 ? '+' : ''}${movement.difference})</span>` : ''}
                            </div>
                        ` : ''}
                        
                        <div style="margin-bottom: 10px;">
                            <strong>Fecha:</strong> ${new Date(movement.date).toLocaleDateString('es-ES')}
                        </div>
                        
                        ${movement.category ? `
                            <div style="margin-bottom: 10px;">
                                <strong>Categoría:</strong> ${movement.category}
                            </div>
                        ` : ''}
                        
                        ${movement.income_pattern_name ? `
                            <div style="margin-bottom: 10px;">
                                <strong>Patrón:</strong> ${movement.income_pattern_name} (${movement.income_frequency})
                            </div>
                        ` : ''}
                        
                        ${movement.expense_pattern_name ? `
                            <div style="margin-bottom: 10px;">
                                <strong>Patrón:</strong> ${movement.expense_pattern_name} (${movement.expense_frequency})
                            </div>
                        ` : ''}
                        
                        ${movement.envelope_name ? `
                            <div style="margin-bottom: 10px;">
                                <strong>Sobre:</strong> ${movement.envelope_name}
                            </div>
                        ` : ''}
                    </div>
                </div>
            `,
            showCancelButton: true,
            showDenyButton: true,
            confirmButtonText: 'Editar',
            denyButtonText: 'Eliminar',
            cancelButtonText: 'Cerrar',
            confirmButtonColor: '#3b82f6',
            denyButtonColor: '#ef4444',
            cancelButtonColor: '#6b7280'
        });

        if (result.isConfirmed) {
            // Editar
            await showEditMovementDialog(movement, onUpdated);
        } else if (result.isDenied) {
            // Eliminar
            const confirmDelete = await Swal.fire({
                title: '¿Eliminar movimiento?',
                text: 'Esta acción no se puede deshacer',
                icon: 'warning',
                showCancelButton: true,
                confirmButtonText: 'Sí, eliminar',
                cancelButtonText: 'Cancelar',
                confirmButtonColor: '#ef4444'
            });

            if (confirmDelete.isConfirmed) {
                try {
                    await deleteMovement(movementId, false); // soft delete
                    await Swal.fire({
                        icon: 'success',
                        title: 'Eliminado',
                        text: 'Movimiento archivado',
                        timer: 1500,
                        showConfirmButton: false
                    });
                    if (onUpdated) onUpdated();
                } catch (deleteError) {
                    console.error('Error deleting movement:', deleteError);
                    await Swal.fire({
                        icon: 'error',
                        title: 'Error',
                        text: 'No se pudo eliminar el movimiento: ' + deleteError.message
                    });
                }
            }
        }
    } catch (error) {
        console.error('Error showing movement details:', error);
        await Swal.fire({
            icon: 'error',
            title: 'Error',
            text: error.message || 'No se pudo cargar el movimiento'
        });
    }
}

/**
 * Modal para editar un movement
 */
async function showEditMovementDialog(movement, onUpdated) {
    const { value: formValues } = await Swal.fire({
        title: 'Editar Movimiento',
        html: `
            <div style="text-align: left; padding: 10px;">
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">Título:</label>
                    <input 
                        id="edit-title" 
                        type="text" 
                        value="${movement.title}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">Descripción:</label>
                    <textarea 
                        id="edit-description" 
                        class="swal2-textarea" 
                        style="margin: 0; width: 100%;"
                    >${movement.description || ''}</textarea>
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">Monto:</label>
                    <input 
                        id="edit-amount" 
                        type="number" 
                        step="0.01"
                        value="${movement.confirmed_amount}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">Fecha:</label>
                    <input 
                        id="edit-date" 
                        type="date" 
                        value="${movement.date}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">Categoría:</label>
                    <input 
                        id="edit-category" 
                        type="text" 
                        value="${movement.category || ''}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                    />
                </div>
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: 'Guardar',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#10b981',
        preConfirm: () => {
            return {
                title: document.getElementById('edit-title').value,
                description: document.getElementById('edit-description').value,
                confirmed_amount: parseFloat(document.getElementById('edit-amount').value),
                date: document.getElementById('edit-date').value,
                category: document.getElementById('edit-category').value
            };
        }
    });

    if (formValues) {
        try {
            await updateMovement(movement.id, formValues);
            await Swal.fire({
                icon: 'success',
                title: 'Actualizado',
                text: 'Movimiento actualizado exitosamente',
                timer: 1500,
                showConfirmButton: false
            });
            if (onUpdated) onUpdated();
        } catch (error) {
            console.error('Error updating movement:', error);
            await Swal.fire({
                icon: 'error',
                title: 'Error',
                text: error.message || 'No se pudo actualizar el movimiento'
            });
        }
    }
}

// ============================================================================
// MODAL: VER DETALLES DE LOAN
// ============================================================================

/**
 * Muestra detalles completos de un préstamo (V2)
 */
export async function showLoanDetails(loanId, onUpdated) {
    try {
        const { getLoanById, getLoanProgress, getLoanPayments } = await import('./loans-v2.js');
        const loan = await getLoanById(loanId);
        if (!loan) {
            throw new Error('Préstamo no encontrado');
        }

        const progress = await getLoanProgress(loanId);
        const payments = await getLoanPayments(loanId);
        const typeLabel = loan.type === 'given' ? '➡️ Presté dinero' : '⬅️ Me prestaron';

        const result = await Swal.fire({
            title: `${typeLabel}: ${loan.counterparty}`,
            html: `
                <div style="text-align: left; padding: 10px;">
                    ${loan.description ? `<p style="color: #666; margin-bottom: 15px;">${loan.description}</p>` : ''}
                    
                    <div style="background: #fef3c7; padding: 15px; border-radius: 8px; margin-bottom: 15px;">
                        <div style="margin-bottom: 10px;">
                            <strong>Monto original:</strong> 
                            <span style="color: #f59e0b; font-size: 1.3em;">$${loan.original_amount}</span>
                        </div>
                        
                        <div style="margin-bottom: 10px;">
                            <strong>Pagado:</strong> 
                            <span style="color: #10b981; font-size: 1.2em;">$${progress.paid.toFixed(2)}</span>
                        </div>
                        
                        <div style="margin-bottom: 10px;">
                            <strong>Restante:</strong> 
                            <span style="color: ${progress.remaining > 0 ? '#ef4444' : '#10b981'}; font-size: 1.2em;">$${progress.remaining.toFixed(2)}</span>
                        </div>
                        
                        <div style="margin-bottom: 15px;">
                            <div style="background: #e5e7eb; height: 20px; border-radius: 10px; overflow: hidden;">
                                <div style="background: #10b981; height: 100%; width: ${progress.progress}%; transition: width 0.3s;"></div>
                            </div>
                            <p style="text-align: center; margin-top: 5px; font-weight: 600; color: #10b981;">
                                ${progress.progress.toFixed(1)}% completado
                            </p>
                        </div>
                        
                        ${loan.due_date ? `
                            <div style="margin-bottom: 10px;">
                                <strong>Fecha vencimiento:</strong> ${new Date(loan.due_date).toLocaleDateString('es-ES')}
                            </div>
                        ` : ''}
                        
                        <div style="margin-top: 15px; padding-top: 15px; border-top: 1px solid #d97706;">
                            <strong>Pagos registrados:</strong> ${payments.length || 0}
                        </div>
                    </div>

                    ${loan.loan_date ? `
                        <div style="background: #f3f4f6; padding: 10px; border-radius: 8px; margin-bottom: 10px;">
                            <strong>Fecha del préstamo:</strong><br/>
                            ${new Date(loan.loan_date).toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
                        </div>
                    ` : ''}
                    
                    ${payments && payments.length > 0 ? `
                        <div style="background: #f3f4f6; padding: 10px; border-radius: 8px;">
                            <strong>Historial de pagos:</strong>
                            <ul style="margin: 10px 0; padding-left: 20px;">
                                ${payments.slice(0, 5).map(mov => `
                                    <li style="margin-bottom: 5px;">
                                        ${new Date(mov.date).toLocaleDateString('es-ES')} - $${mov.confirmed_amount || mov.expected_amount}
                                    </li>
                                `).join('')}
                                ${payments.length > 5 ? `<li>...y ${payments.length - 5} más</li>` : ''}
                            </ul>
                        </div>
                    ` : ''}
                </div>
            `,
            showCancelButton: !progress.is_complete,
            showDenyButton: true,
            confirmButtonText: progress.is_complete ? 'Cerrar' : 'Registrar Pago',
            denyButtonText: 'Eliminar',
            cancelButtonText: 'Cerrar',
            confirmButtonColor: '#10b981',
            denyButtonColor: '#ef4444',
            cancelButtonColor: '#6b7280'
        });

        if (result.isConfirmed && !progress.is_complete) {
            // Registrar pago
            await showRegisterLoanPaymentDialog(loan, onUpdated);
        } else if (result.isDenied) {
            // Eliminar préstamo
            const confirmDelete = await Swal.fire({
                title: '¿Eliminar préstamo?',
                text: 'Se eliminarán todos los movimientos relacionados. Esta acción no se puede deshacer.',
                icon: 'warning',
                showCancelButton: true,
                confirmButtonText: 'Sí, eliminar',
                cancelButtonText: 'Cancelar',
                confirmButtonColor: '#ef4444'
            });

            if (confirmDelete.isConfirmed) {
                try {
                    const { deleteLoan } = await import('./loans-v2.js');
                    await deleteLoan(loanId);
                    await Swal.fire({
                        icon: 'success',
                        title: 'Eliminado',
                        text: 'Préstamo eliminado',
                        timer: 1500,
                        showConfirmButton: false
                    });
                    if (onUpdated) onUpdated();
                } catch (deleteError) {
                    console.error('Error deleting loan:', deleteError);
                    await Swal.fire({
                        icon: 'error',
                        title: 'Error',
                        text: 'No se pudo eliminar el préstamo: ' + deleteError.message
                    });
                }
            }
        }
    } catch (error) {
        console.error('Error showing loan details:', error);
        await Swal.fire({
            icon: 'error',
            title: 'Error',
            text: error.message || 'No se pudo cargar el préstamo'
        });
    }
}

/**
 * Modal para registrar un pago de préstamo (V2)
 */
async function showRegisterLoanPaymentDialog(loan, onUpdated) {
    const { registerLoanPayment, getLoanProgress } = await import('./loans-v2.js');
    const progress = await getLoanProgress(loan.id);
    
    // Para préstamos dados (given), cargar patrones de ahorro
    let savingsPatterns = [];
    if (loan.type === 'given') {
        try {
            savingsPatterns = await getSavingsPatterns(true);
        } catch (e) {
            console.error('Error loading savings patterns:', e);
        }
    }
    
    // HTML de destino para préstamos dados
    const destinationHTML = loan.type === 'given' && savingsPatterns.length > 0 ? `
        <div style="margin-top: 15px; padding: 12px; background: #f0fdf4; border-radius: 8px; border: 1px solid #86efac;">
            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #166534;">
                💰 ¿Dónde guardar este dinero?
            </label>
            <div style="display: flex; flex-direction: column; gap: 8px;">
                <label style="display: flex; align-items: center; cursor: pointer;">
                    <input type="radio" name="loan-destination" value="balance" checked style="margin-right: 10px;">
                    <span>📊 Al balance general</span>
                </label>
                <label style="display: flex; align-items: center; cursor: pointer;">
                    <input type="radio" name="loan-destination" value="savings" style="margin-right: 10px;">
                    <span>🏦 A un patrón de ahorro</span>
                </label>
                <div id="loan-savings-container" style="display: none; margin-top: 8px; margin-left: 25px;">
                    <select id="loan-savings-target" class="swal2-select" style="width: 100%;">
                        ${savingsPatterns.map(sp => `
                            <option value="${sp.id}">
                                ${sp.name} (Balance: $${parseFloat(sp.current_balance || 0).toLocaleString('es-MX')})
                            </option>
                        `).join('')}
                    </select>
                </div>
            </div>
        </div>
    ` : '';
    
    const { value: formValues } = await Swal.fire({
        title: `💸 Registrar Pago - ${loan.counterparty}`,
        html: `
            <div style="text-align: left; padding: 10px;">
                <div style="background: #f3f4f6; padding: 10px; border-radius: 8px; margin-bottom: 15px;">
                    <p><strong>Tipo:</strong> ${loan.type === 'given' ? 'A favor (te deben)' : 'En contra (debes pagar)'}</p>
                    <p><strong>Total:</strong> $${loan.original_amount}</p>
                    <p><strong>Restante:</strong> $${progress.remaining.toFixed(2)}</p>
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Monto del pago:
                    </label>
                    <input 
                        id="payment-amount" 
                        type="number" 
                        step="0.01"
                        max="${progress.remaining}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="0.00"
                    />
                    <p style="font-size: 0.85em; color: #666; margin-top: 5px;">
                        Máximo: $${progress.remaining.toFixed(2)}
                    </p>
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Fecha del pago:
                    </label>
                    <input 
                        id="payment-date" 
                        type="date" 
                        value="${new Date().toISOString().split('T')[0]}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Notas (opcional):
                    </label>
                    <textarea 
                        id="payment-description" 
                        class="swal2-textarea" 
                        style="margin: 0; width: 100%; min-height: 60px;"
                        placeholder="Detalles del pago..."
                    ></textarea>
                </div>

                <div style="background: #dbeafe; padding: 10px; border-radius: 8px; font-size: 0.9em;">
                    <p style="margin: 0; color: #1e40af;">
                        ${loan.type === 'given' 
                            ? '💰 Se registrará un <strong>ingreso</strong> (dinero que recibes)' 
                            : '💸 Se registrará un <strong>gasto</strong> (dinero que pagas)'}
                    </p>
                </div>
                
                ${destinationHTML}
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: 'Registrar Pago',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#10b981',
        width: '550px',
        didOpen: () => {
            // Toggle visibilidad del selector de ahorro
            if (loan.type === 'given' && savingsPatterns.length > 0) {
                const radios = document.querySelectorAll('input[name="loan-destination"]');
                const savingsContainer = document.getElementById('loan-savings-container');
                radios.forEach(radio => {
                    radio.addEventListener('change', () => {
                        if (savingsContainer) {
                            savingsContainer.style.display = radio.value === 'savings' ? 'block' : 'none';
                        }
                    });
                });
            }
        },
        preConfirm: () => {
            const amount = document.getElementById('payment-amount').value;
            const date = document.getElementById('payment-date').value;
            
            if (!amount || parseFloat(amount) <= 0) {
                Swal.showValidationMessage('Ingresa un monto válido');
                return false;
            }
            if (parseFloat(amount) > progress.remaining) {
                Swal.showValidationMessage(`El monto no puede ser mayor a $${progress.remaining.toFixed(2)}`);
                return false;
            }
            if (!date) {
                Swal.showValidationMessage('Selecciona la fecha del pago');
                return false;
            }
            
            // Para préstamos dados, obtener destino
            let destination = 'balance';
            let savingsPatternId = null;
            if (loan.type === 'given') {
                const selectedDestination = document.querySelector('input[name="loan-destination"]:checked');
                if (selectedDestination) {
                    destination = selectedDestination.value;
                    if (destination === 'savings') {
                        const savingsSelect = document.getElementById('loan-savings-target');
                        if (savingsSelect) {
                            savingsPatternId = savingsSelect.value;
                        }
                    }
                }
            }
            
            return {
                amount: parseFloat(amount),
                date: date,
                description: document.getElementById('payment-description').value.trim() || null,
                destination,
                savingsPatternId
            };
        }
    });
    
    if (formValues) {
        try {
            await registerLoanPayment(loan.id, formValues);
            
            const newProgress = await getLoanProgress(loan.id);
            
            // Si es préstamo dado y el destino es ahorro, crear depósito
            if (loan.type === 'given' && formValues.destination === 'savings' && formValues.savingsPatternId) {
                try {
                    await createSavingsDeposit(
                        formValues.savingsPatternId,
                        formValues.amount,
                        `Pago de préstamo de ${loan.counterparty}`
                    );
                    
                    await Swal.fire({
                        icon: 'success',
                        title: '✅ Pago + Ahorro',
                        html: `
                            <div style="text-align: left;">
                                <p><strong>Monto pagado:</strong> $${formValues.amount}</p>
                                <p><strong>Depositado al ahorro:</strong> ✅</p>
                                <p><strong>Nuevo saldo del préstamo:</strong> $${newProgress.remaining.toFixed(2)}</p>
                                <p><strong>Progreso:</strong> ${newProgress.progress.toFixed(1)}%</p>
                                ${newProgress.is_complete ? '<p style="color: #10b981; font-weight: 600; margin-top: 10px;">🎉 ¡Préstamo completado!</p>' : ''}
                            </div>
                        `,
                        timer: 3000,
                        showConfirmButton: true
                    });
                } catch (savingsError) {
                    console.error('Error depositing to savings:', savingsError);
                    await Swal.fire({
                        icon: 'warning',
                        title: 'Pago registrado',
                        text: 'Pero hubo un error al depositar al ahorro: ' + savingsError.message
                    });
                }
            } else {
                await Swal.fire({
                    icon: 'success',
                    title: '✅ Pago Registrado',
                    html: `
                        <div style="text-align: left;">
                            <p><strong>Monto pagado:</strong> $${formValues.amount}</p>
                            <p><strong>Nuevo saldo:</strong> $${newProgress.remaining.toFixed(2)}</p>
                            <p><strong>Progreso:</strong> ${newProgress.progress.toFixed(1)}%</p>
                            ${newProgress.is_complete ? '<p style="color: #10b981; font-weight: 600; margin-top: 10px;">🎉 ¡Préstamo completado!</p>' : ''}
                        </div>
                    `,
                    timer: 3000,
                    showConfirmButton: true
                });
            }
            
            // Actualizar el indicador de balance
            if (window.refreshBalanceIndicator) window.refreshBalanceIndicator();
            
            if (onUpdated) onUpdated();
        } catch (error) {
            console.error('Error registering payment:', error);
            await Swal.fire({
                icon: 'error',
                title: 'Error',
                text: error.message || 'No se pudo registrar el pago'
            });
        }
    }
}

// ============================================================================
// MODAL: VER DETALLES DE PLAN
// ============================================================================

/**
 * Muestra detalles completos de un plan/meta
 */
export async function showPlanDetails(planId, onUpdated) {
    try {
        const plan = await getPlanById(planId);
        if (!plan) {
            throw new Error('Plan no encontrado');
        }

        // V2: usar current_amount en lugar de saved_amount, name en lugar de title
        const progress = parseFloat(plan.progress_percent) || 0;
        const savedAmount = parseFloat(plan.current_amount) || 0;
        const targetAmount = parseFloat(plan.target_amount);
        const remainingAmount = targetAmount - savedAmount;
        const progressColor = progress >= 100 ? '#10b981' : progress >= 50 ? '#3b82f6' : '#f59e0b';
        const isCompleted = progress >= 100;

        const result = await Swal.fire({
            title: `🎯 ${plan.name || 'Sin nombre'}`,
            html: `
                <div style="text-align: left; padding: 10px;">
                    ${plan.description ? `<p style="color: #666; margin-bottom: 15px;">${plan.description}</p>` : ''}
                    
                    <div style="background: linear-gradient(135deg, #dbeafe 0%, #e0e7ff 100%); padding: 20px; border-radius: 12px; margin-bottom: 15px;">
                        <!-- Barra de Progreso Visual -->
                        <div style="margin-bottom: 20px;">
                            <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
                                <span style="font-weight: 600; color: #1e40af;">Progreso</span>
                                <span style="font-weight: 700; color: ${progressColor}; font-size: 1.1em;">${progress.toFixed(1)}%</span>
                            </div>
                            <div style="background: #e5e7eb; height: 24px; border-radius: 12px; overflow: hidden; box-shadow: inset 0 2px 4px rgba(0,0,0,0.1);">
                                <div style="background: linear-gradient(90deg, ${progressColor} 0%, ${progress >= 100 ? '#34d399' : progress >= 50 ? '#60a5fa' : '#fbbf24'} 100%); height: 100%; width: ${Math.min(progress, 100)}%; transition: width 0.5s ease-out; border-radius: 12px;"></div>
                            </div>
                        </div>
                        
                        <!-- Montos -->
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-bottom: 15px;">
                            <div style="background: white; padding: 12px; border-radius: 8px; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,0.1);">
                                <div style="font-size: 0.85em; color: #6b7280;">Ahorrado</div>
                                <div style="font-size: 1.4em; font-weight: 700; color: #10b981;">$${savedAmount.toLocaleString('es-MX', {minimumFractionDigits: 2})}</div>
                            </div>
                            <div style="background: white; padding: 12px; border-radius: 8px; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,0.1);">
                                <div style="font-size: 0.85em; color: #6b7280;">Objetivo</div>
                                <div style="font-size: 1.4em; font-weight: 700; color: #3b82f6;">$${targetAmount.toLocaleString('es-MX', {minimumFractionDigits: 2})}</div>
                            </div>
                        </div>
                        
                        <!-- Cantidad Faltante -->
                        ${!isCompleted ? `
                            <div style="background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%); padding: 15px; border-radius: 8px; text-align: center; border: 2px dashed #f59e0b;">
                                <div style="font-size: 0.9em; color: #92400e; margin-bottom: 4px;">💰 Falta por ahorrar</div>
                                <div style="font-size: 1.6em; font-weight: 700; color: #b45309;">$${remainingAmount.toLocaleString('es-MX', {minimumFractionDigits: 2})}</div>
                            </div>
                        ` : `
                            <div style="background: linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%); padding: 15px; border-radius: 8px; text-align: center; border: 2px solid #10b981;">
                                <div style="font-size: 1.2em; color: #065f46;">🎉 ¡Meta alcanzada!</div>
                            </div>
                        `}
                    </div>
                    
                    <!-- Fechas -->
                    <div style="background: #f9fafb; padding: 15px; border-radius: 8px; margin-bottom: 15px;">
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
                            ${plan.target_date ? `
                                <div>
                                    <div style="font-size: 0.8em; color: #6b7280;">📅 Fecha objetivo</div>
                                    <div style="font-weight: 600;">${new Date(plan.target_date).toLocaleDateString('es-ES')}</div>
                                </div>
                            ` : ''}
                        </div>
                        
                        ${plan.category ? `
                            <div style="margin-top: 10px; padding-top: 10px; border-top: 1px solid #e5e7eb;">
                                <span style="font-size: 0.8em; color: #6b7280;">Categoría:</span>
                                <span style="background: #e5e7eb; padding: 2px 8px; border-radius: 4px; font-size: 0.85em;">${plan.category}</span>
                            </div>
                        ` : ''}
                        
                        <div style="margin-top: 10px; padding-top: 10px; border-top: 1px solid #e5e7eb;">
                            <span style="font-size: 0.8em; color: #6b7280;">Prioridad:</span>
                            <span style="color: #f59e0b;">${'⭐'.repeat(plan.priority)}</span>
                        </div>
                    </div>

                    ${plan.income_sources && plan.income_sources.length > 0 ? `
                        <div style="background: #f0fdf4; padding: 12px; border-radius: 8px; border: 1px solid #86efac;">
                            <div style="font-weight: 600; color: #166534; margin-bottom: 8px;">💰 Fuentes de ingreso asignadas:</div>
                            <ul style="margin: 0; padding-left: 20px; color: #15803d;">
                                ${plan.income_sources.filter(src => src.income_pattern).map(src => `
                                    <li style="margin-bottom: 4px;">
                                        ${src.income_pattern?.name || 'Ingreso sin nombre'}: 
                                        <strong>${src.allocation_type === 'percent' 
                                            ? `${(src.allocation_value * 100).toFixed(0)}%` 
                                            : `$${src.allocation_value}`}</strong>
                                    </li>
                                `).join('')}
                            </ul>
                        </div>
                    ` : ''}
                </div>
            `,
            showCancelButton: true,
            showDenyButton: true,
            showCloseButton: !isCompleted,
            confirmButtonText: !isCompleted ? '💵 Agregar aporte' : '✏️ Editar',
            denyButtonText: '🗑️ Eliminar',
            cancelButtonText: 'Cerrar',
            confirmButtonColor: !isCompleted ? '#10b981' : '#3b82f6',
            denyButtonColor: '#ef4444',
            cancelButtonColor: '#6b7280',
            width: '550px'
        });

        if (result.isConfirmed) {
            if (!isCompleted) {
                // Agregar aporte
                await showAddContributionDialog(plan, onUpdated);
            } else {
                // Editar plan
                await showEditPlanDialog(plan, onUpdated);
            }
        } else if (result.isDenied) {
            // Eliminar plan
            const confirmDelete = await Swal.fire({
                title: '¿Eliminar planeación?',
                text: 'Esta acción no se puede deshacer',
                icon: 'warning',
                showCancelButton: true,
                confirmButtonText: 'Sí, eliminar',
                cancelButtonText: 'Cancelar',
                confirmButtonColor: '#ef4444'
            });

            if (confirmDelete.isConfirmed) {
                try {
                    const { deletePlan } = await import('./plans-v2.js');
                    await deletePlan(planId);
                    await Swal.fire({
                        icon: 'success',
                        title: 'Eliminado',
                        text: 'Planeación eliminada',
                        timer: 1500,
                        showConfirmButton: false
                    });
                    if (onUpdated) onUpdated();
                } catch (deleteError) {
                    console.error('Error deleting plan:', deleteError);
                    await Swal.fire({
                        icon: 'error',
                        title: 'Error',
                        text: 'No se pudo eliminar la planeación: ' + deleteError.message
                    });
                }
            }
        }
    } catch (error) {
        console.error('Error showing plan details:', error);
        await Swal.fire({
            icon: 'error',
            title: 'Error',
            text: error.message || 'No se pudo cargar el plan'
        });
    }
}

/**
 * Modal para agregar un aporte manual a una planeación - V2
 */
async function showAddContributionDialog(plan, onUpdated) {
    // V2: usar current_amount en lugar de saved_amount
    const remainingAmount = parseFloat(plan.target_amount) - (parseFloat(plan.current_amount) || 0);
    const today = new Date().toISOString().split('T')[0];
    
    // Obtener balance disponible
    let availableBalance = 0;
    try {
        const balanceSummary = await getConfirmedBalanceSummary();
        availableBalance = balanceSummary.balance || 0;
    } catch (e) {
        console.error('Error getting balance:', e);
    }
    
    const { value: formValues } = await Swal.fire({
        // V2: usar name en lugar de title
        title: `💵 Aporte a: ${plan.name}`,
        html: `
            <div style="text-align: left; padding: 10px;">
                <!-- Info del plan -->
                <div style="background: #f0f9ff; padding: 12px; border-radius: 8px; margin-bottom: 15px;">
                    <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
                        <span style="color: #6b7280;">Progreso actual:</span>
                        <span style="font-weight: 600; color: #3b82f6;">${(parseFloat(plan.progress_percent) || 0).toFixed(1)}%</span>
                    </div>
                    <div style="display: flex; justify-content: space-between;">
                        <span style="color: #6b7280;">Falta:</span>
                        <span style="font-weight: 600; color: #f59e0b;">$${remainingAmount.toLocaleString('es-MX', {minimumFractionDigits: 2})}</span>
                    </div>
                </div>
                
                <!-- Balance disponible -->
                <div style="background: ${availableBalance > 0 ? '#f0fdf4' : '#fef2f2'}; padding: 10px; border-radius: 8px; margin-bottom: 15px; text-align: center;">
                    <span style="color: #6b7280; font-size: 0.9em;">Balance disponible:</span>
                    <span style="font-weight: 700; color: ${availableBalance > 0 ? '#10b981' : '#ef4444'}; font-size: 1.1em; margin-left: 8px;">
                        $${availableBalance.toLocaleString('es-MX', {minimumFractionDigits: 2})}
                    </span>
                </div>
                
                <!-- Monto del aporte -->
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Monto del aporte:
                    </label>
                    <input 
                        id="contribution-amount" 
                        type="number" 
                        step="0.01"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="0.00"
                        max="${remainingAmount}"
                    />
                    <div style="margin-top: 8px; display: flex; gap: 8px; flex-wrap: wrap;">
                        ${availableBalance >= remainingAmount ? `
                            <button type="button" class="quick-amount-btn" data-amount="${remainingAmount}" 
                                style="padding: 6px 12px; background: #10b981; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 0.85em;">
                                🎯 Completar ($${remainingAmount.toLocaleString('es-MX')})
                            </button>
                        ` : ''}
                        ${availableBalance > 0 ? `
                            <button type="button" class="quick-amount-btn" data-amount="${Math.min(availableBalance, remainingAmount)}" 
                                style="padding: 6px 12px; background: #3b82f6; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 0.85em;">
                                💰 Todo el balance ($${Math.min(availableBalance, remainingAmount).toLocaleString('es-MX')})
                            </button>
                        ` : ''}
                    </div>
                </div>
                
                <!-- Descripción -->
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Descripción (opcional):
                    </label>
                    <input 
                        id="contribution-description" 
                        type="text" 
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="Ej: Aporte extra, Bono, etc."
                    />
                </div>
                
                <!-- Fecha -->
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Fecha:
                    </label>
                    <input 
                        id="contribution-date" 
                        type="date" 
                        value="${today}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                    />
                </div>
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: '✅ Agregar aporte',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#10b981',
        width: '450px',
        didOpen: () => {
            // Botones de monto rápido
            const quickBtns = document.querySelectorAll('.quick-amount-btn');
            const amountInput = document.getElementById('contribution-amount');
            quickBtns.forEach(btn => {
                btn.addEventListener('click', () => {
                    amountInput.value = btn.dataset.amount;
                });
            });
        },
        preConfirm: () => {
            const amount = document.getElementById('contribution-amount').value;
            const description = document.getElementById('contribution-description').value;
            const date = document.getElementById('contribution-date').value;
            
            if (!amount || parseFloat(amount) <= 0) {
                Swal.showValidationMessage('Ingresa un monto válido');
                return false;
            }
            
            const amountNum = parseFloat(amount);
            if (amountNum > remainingAmount) {
                Swal.showValidationMessage(`El monto no puede ser mayor a lo que falta: $${remainingAmount.toFixed(2)}`);
                return false;
            }
            
            return {
                amount: amountNum,
                // V2: usar name en lugar de title
                description: description.trim() || `Aporte a ${plan.name}`,
                date: date || today
            };
        }
    });
    
    if (formValues) {
        try {
            const { contributeToPlan, recalculatePlanDates, getPlanById } = await import('./plans-v2.js');
            
            await contributeToPlan(plan.id, formValues.amount, formValues.description, formValues.date);
            
            // Recalcular fechas (pasando la contribución para mejor estimación)
            const updatedPlan = await recalculatePlanDates(plan.id, formValues.amount);
            
            // Verificar si se completó
            const finalPlan = await getPlanById(plan.id);
            const newProgress = parseFloat(finalPlan.progress_percent) || 0;
            
            if (newProgress >= 100) {
                await Swal.fire({
                    icon: 'success',
                    title: '🎉 ¡Meta completada!',
                    html: `
                        <p>¡Felicidades! Has alcanzado tu meta de ahorro.</p>
                        <p style="font-size: 1.5em; color: #10b981; font-weight: 700;">$${parseFloat(finalPlan.target_amount).toLocaleString('es-MX', {minimumFractionDigits: 2})}</p>
                    `,
                    confetti: true
                });
            } else {
                await Swal.fire({
                    icon: 'success',
                    title: '✅ Aporte registrado',
                    html: `
                        <p>+$${formValues.amount.toLocaleString('es-MX', {minimumFractionDigits: 2})}</p>
                        <p style="color: #6b7280; font-size: 0.9em;">
                            Nuevo progreso: <strong style="color: #3b82f6;">${newProgress.toFixed(1)}%</strong>
                        </p>
                    `,
                    timer: 2500,
                    showConfirmButton: false
                });
            }
            
            // Actualizar balance indicator
            if (window.refreshBalanceIndicator) window.refreshBalanceIndicator();
            
            if (onUpdated) onUpdated();
            
        } catch (error) {
            console.error('Error adding contribution:', error);
            await Swal.fire({
                icon: 'error',
                title: 'Error',
                text: 'No se pudo registrar el aporte: ' + error.message
            });
        }
    }
}

/**
 * Modal para editar una planeación existente
 */
async function showEditPlanDialog(plan, onUpdated) {
    // Obtener ingresos disponibles del usuario
    const { getIncomePatterns } = await import('./patterns.js');
    
    let incomePatterns = [];
    try {
        incomePatterns = await getIncomePatterns();
    } catch (error) {
        console.error('Error loading income patterns:', error);
    }
    
    // Obtener los income sources actuales del plan
    const currentSourcesMap = {};
    if (plan.income_sources && plan.income_sources.length > 0) {
        plan.income_sources.forEach(src => {
            currentSourcesMap[src.income_pattern_id] = {
                id: src.id,
                allocation_value: src.allocation_value,
                allocation_type: src.allocation_type
            };
        });
    }
    
    // Construir opciones de ingresos
    const incomeOptionsHTML = incomePatterns.map(income => {
        const isSelected = !!currentSourcesMap[income.id];
        const percentage = isSelected 
            ? (currentSourcesMap[income.id].allocation_value * 100).toFixed(0)
            : 100;
        
        return `
        <div style="margin-bottom: 10px; padding: 10px; background: #f3f4f6; border-radius: 6px;">
            <label style="display: flex; align-items: center; cursor: pointer;">
                <input 
                    type="checkbox" 
                    class="income-source-checkbox" 
                    data-income-id="${income.id}"
                    data-source-id="${currentSourcesMap[income.id]?.id || ''}"
                    ${isSelected ? 'checked' : ''}
                    style="margin-right: 10px;"
                />
                <div style="flex: 1;">
                    <div style="font-weight: 600;">${income.name}</div>
                    <div style="font-size: 0.9em; color: #666;">$${income.base_amount} - ${income.frequency}</div>
                </div>
            </label>
            <div class="allocation-input" style="margin-top: 8px; display: ${isSelected ? 'block' : 'none'};">
                <label style="font-size: 0.9em; color: #666;">Porcentaje a asignar:</label>
                <input 
                    type="number" 
                    class="income-allocation" 
                    data-income-id="${income.id}"
                    min="1" 
                    max="100" 
                    value="${percentage}"
                    style="width: 80px; margin-left: 10px;"
                />
                <span style="margin-left: 5px;">%</span>
            </div>
        </div>
        `;
    }).join('');
    
    const { value: formValues } = await Swal.fire({
        title: '✏️ Editar Planeación',
        html: `
            <div style="text-align: left; padding: 10px;">
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Nombre:
                    </label>
                    <input 
                        id="edit-plan-name" 
                        type="text" 
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        value="${plan.name || ''}"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Monto objetivo:
                    </label>
                    <input 
                        id="edit-plan-amount" 
                        type="number" 
                        step="0.01"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        value="${plan.target_amount}"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Fecha objetivo (opcional):
                    </label>
                    <input 
                        id="edit-plan-target-date" 
                        type="date" 
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        value="${plan.target_date || ''}"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Prioridad:
                    </label>
                    <select id="edit-plan-priority" class="swal2-select" style="width: 100%;">
                        <option value="1" ${plan.priority === 1 ? 'selected' : ''}>🔴 Alta</option>
                        <option value="2" ${plan.priority === 2 ? 'selected' : ''}>🟡 Media-Alta</option>
                        <option value="3" ${plan.priority === 3 ? 'selected' : ''}>🟢 Media</option>
                        <option value="4" ${plan.priority === 4 ? 'selected' : ''}>🔵 Media-Baja</option>
                        <option value="5" ${plan.priority === 5 ? 'selected' : ''}>⚪ Baja</option>
                    </select>
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Estado:
                    </label>
                    <select id="edit-plan-status" class="swal2-select" style="width: 100%;">
                        <option value="active" ${plan.status === 'active' ? 'selected' : ''}>🚀 Activo</option>
                        <option value="paused" ${plan.status === 'paused' ? 'selected' : ''}>⏸️ Pausado</option>
                        <option value="completed" ${plan.status === 'completed' ? 'selected' : ''}>✅ Completado</option>
                        <option value="cancelled" ${plan.status === 'cancelled' ? 'selected' : ''}>❌ Cancelado</option>
                    </select>
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Categoría (opcional):
                    </label>
                    <input 
                        id="edit-plan-category" 
                        type="text" 
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        value="${plan.category || ''}"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Descripción (opcional):
                    </label>
                    <textarea 
                        id="edit-plan-description" 
                        class="swal2-textarea" 
                        style="margin: 0; width: 100%; min-height: 60px;"
                    >${plan.description || ''}</textarea>
                </div>

                ${incomePatterns.length > 0 ? `
                    <div style="margin-top: 20px; padding-top: 20px; border-top: 2px solid #e5e7eb;">
                        <label style="display: block; margin-bottom: 10px; font-weight: 600; color: #374151;">
                            💰 Fuentes de ingreso asignadas:
                        </label>
                        <p style="font-size: 0.9em; color: #6b7280; margin-bottom: 15px;">
                            Modifica los ingresos que usarás para esta planeación.
                        </p>
                        ${incomeOptionsHTML}
                    </div>
                ` : ''}
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: 'Guardar Cambios',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#3b82f6',
        width: '700px',
        didOpen: () => {
            // Mostrar/ocultar input de asignación cuando se marca checkbox
            const checkboxes = document.querySelectorAll('.income-source-checkbox');
            checkboxes.forEach(checkbox => {
                checkbox.addEventListener('change', (e) => {
                    const container = e.target.closest('div[style*="padding: 10px"]');
                    const allocationInput = container.querySelector('.allocation-input');
                    if (allocationInput) {
                        allocationInput.style.display = e.target.checked ? 'block' : 'none';
                    }
                });
            });
        },
        preConfirm: () => {
            // V2: usar edit-plan-name en lugar de edit-plan-title
            const name = document.getElementById('edit-plan-name').value.trim();
            const amount = document.getElementById('edit-plan-amount').value;
            
            if (!name) {
                Swal.showValidationMessage('El nombre es requerido');
                return false;
            }
            if (!amount || parseFloat(amount) <= 0) {
                Swal.showValidationMessage('Ingresa un monto válido');
                return false;
            }
            
            // Capturar ingresos seleccionados
            const income_sources = [];
            const checkedBoxes = document.querySelectorAll('.income-source-checkbox:checked');
            checkedBoxes.forEach(checkbox => {
                const incomeId = checkbox.dataset.incomeId;
                const sourceId = checkbox.dataset.sourceId;
                const allocationInput = document.querySelector(`.income-allocation[data-income-id="${incomeId}"]`);
                const percentage = allocationInput ? parseFloat(allocationInput.value) : 100;
                
                income_sources.push({
                    id: sourceId || null,
                    income_pattern_id: incomeId,
                    allocation_type: 'percent',
                    allocation_value: percentage / 100
                });
            });
            
            return {
                name: name,
                target_amount: parseFloat(amount),
                target_date: document.getElementById('edit-plan-target-date').value || null,
                priority: parseInt(document.getElementById('edit-plan-priority').value),
                status: document.getElementById('edit-plan-status').value,
                category: document.getElementById('edit-plan-category').value.trim() || null,
                description: document.getElementById('edit-plan-description').value.trim() || null,
                income_sources
            };
        }
    });

    if (formValues) {
        try {
            const { updatePlan, assignIncomeSources, removeIncomeSource } = await import('./plans-v2.js');
            
            // 1. Actualizar el plan
            await updatePlan(plan.id, {
                name: formValues.name,
                target_amount: formValues.target_amount,
                target_date: formValues.target_date,
                priority: formValues.priority,
                status: formValues.status,
                category: formValues.category,
                description: formValues.description
            });
            
            // 2. Manejar income sources
            const currentSourceIds = new Set(
                plan.income_sources?.map(s => s.income_pattern_id) || []
            );
            const newSourceIds = new Set(
                formValues.income_sources.map(s => s.income_pattern_id)
            );
            
            // Eliminar sources que ya no están seleccionados
            for (const source of (plan.income_sources || [])) {
                if (!newSourceIds.has(source.income_pattern_id)) {
                    await removeIncomeSource(source.id);
                }
            }
            
            // Agregar nuevos sources (los que no tienen id)
            const newSources = formValues.income_sources.filter(s => !s.id);
            if (newSources.length > 0) {
                await assignIncomeSources(plan.id, newSources);
            }
            
            // Actualizar sources existentes (los que sí tienen id)
            const { updateIncomeSource } = await import('./plans-v2.js');
            for (const source of formValues.income_sources) {
                if (source.id) {
                    await updateIncomeSource(source.id, {
                        allocation_value: source.allocation_value
                    });
                }
            }
            
            await Swal.fire({
                icon: 'success',
                title: '✅ Actualizado',
                text: 'Planeación actualizada exitosamente',
                timer: 1500,
                showConfirmButton: false
            });
            if (onUpdated) onUpdated();
        } catch (error) {
            console.error('Error updating plan:', error);
            await Swal.fire({
                icon: 'error',
                title: 'Error',
                text: error.message || 'No se pudo actualizar la planeación'
            });
        }
    }
}

// ============================================================================
// MODAL: CREAR NUEVO EVENTO/MOVIMIENTO (CON VISTA DE EVENTOS EXISTENTES)
// ============================================================================

/**
 * Muestra modal unificado: eventos existentes + opción para crear nuevo
 */
export async function showCreateEventDialog(dateISO, onCreated) {
    // Obtener eventos existentes de esa fecha
    const { getCalendarDataForMonth } = await import('./pattern-scheduler.js');
    
    const sessionData = localStorage.getItem('calendar_session');
    const userId = sessionData ? JSON.parse(sessionData).userId : null;
    
    if (!userId) {
        await Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'No hay sesión activa'
        });
        return;
    }
    
    const date = new Date(dateISO + 'T00:00:00');
    const year = date.getFullYear();
    const month = date.getMonth();
    const calendarData = await getCalendarDataForMonth(userId, year, month);
    const dayData = calendarData[dateISO] || {};
    
    // Construir lista de eventos existentes
    const existingEvents = [];
    
    console.log('dayData:', dayData); // Debug
    
    // Movimientos confirmados
    if (dayData.confirmed_movements && dayData.confirmed_movements.length > 0) {
        dayData.confirmed_movements.forEach(m => {
            existingEvents.push({
                type: 'movement',
                id: m.id,
                title: m.title,
                amount: m.confirmed_amount,
                category: m.type,
                icon: m.type === 'ingreso' ? '💰' : '💸',
                color: m.type === 'ingreso' ? '#10b981' : '#ef4444',
                onClick: () => showMovementDetails(m.id, onCreated)
            });
        });
    }
    
    // Ingresos proyectados
    if (dayData.projected_incomes && dayData.projected_incomes.length > 0) {
        dayData.projected_incomes.forEach(p => {
            // Solo mostrar si NO tiene movimiento confirmado
            if (!p.has_confirmed_movement) {
                existingEvents.push({
                    type: 'projected',
                    id: p.pattern_id,
                    title: p.name,
                    amount: p.expected_amount,
                    category: 'ingreso',
                    icon: '💵',
                    color: '#10b981',
                    style: 'opacity: 0.7; border-left: 3px dashed #10b981;',
                    subtitle: 'Proyectado',
                    data: p,
                    onClick: () => showConfirmProjectedDialog(p, onCreated)
                });
            }
        });
    }
    
    // Gastos proyectados
    if (dayData.projected_expenses && dayData.projected_expenses.length > 0) {
        dayData.projected_expenses.forEach(p => {
            // Solo mostrar si NO tiene movimiento confirmado
            if (!p.has_confirmed_movement) {
                existingEvents.push({
                    type: 'projected',
                    id: p.pattern_id,
                    title: p.name,
                    amount: p.expected_amount,
                    category: 'gasto',
                    icon: '💸',
                    color: '#ef4444',
                    style: 'opacity: 0.7; border-left: 3px dashed #ef4444;',
                    subtitle: 'Proyectado',
                    data: p,
                    onClick: () => showConfirmProjectedDialog(p, onCreated)
                });
            }
        });
    }
    
    // Planes (target date)
    if (dayData.plan_targets && dayData.plan_targets.length > 0) {
        dayData.plan_targets.forEach(p => {
            existingEvents.push({
                type: 'plan',
                id: p.plan_id,
                title: p.name,
                amount: p.target_amount,
                category: 'plan',
                icon: '🎯',
                color: '#3b82f6',
                subtitle: `Prioridad: ${p.priority}`,
                onClick: () => showPlanDetails(p.plan_id, onCreated)
            });
        });
    }
    
    // Préstamos
    if (dayData.loan_movements && dayData.loan_movements.length > 0) {
        dayData.loan_movements.forEach(l => {
            existingEvents.push({
                type: 'loan',
                id: l.loan_id,
                title: l.title,
                amount: l.confirmed_amount,
                category: 'loan',
                icon: '💰',
                color: '#fbbf24',
                subtitle: 'Préstamo',
                onClick: () => showLoanDetails(l.loan_id, onCreated)
            });
        });
    }
    
    // Construir HTML de eventos existentes
    const eventsListHTML = existingEvents.length > 0 ? `
        <div style="background: #f9fafb; border-radius: 8px; padding: 15px; margin-bottom: 20px; max-height: 300px; overflow-y: auto;">
            <h4 style="margin: 0 0 10px 0; font-size: 0.9em; color: #6b7280;">Eventos del día:</h4>
            ${existingEvents.map(evt => `
                <div 
                    class="event-item" 
                    data-event-id="${evt.id}"
                    style="
                        background: white; 
                        padding: 12px; 
                        margin-bottom: 8px; 
                        border-radius: 6px; 
                        cursor: pointer;
                        border-left: 4px solid ${evt.color};
                        ${evt.style || ''}
                        transition: all 0.2s;
                    "
                    onmouseover="this.style.background='#f3f4f6'"
                    onmouseout="this.style.background='white'"
                >
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <div style="flex: 1;">
                            <div style="font-weight: 600; color: #1f2937; margin-bottom: 2px;">
                                ${evt.icon} ${evt.title}
                            </div>
                            ${evt.subtitle ? `
                                <div style="font-size: 0.8em; color: #9ca3af; margin-bottom: 4px;">
                                    ${evt.subtitle}
                                </div>
                            ` : ''}
                            <div style="font-size: 0.9em; color: #6b7280;">
                                $${evt.amount}
                            </div>
                        </div>
                        <div style="color: #9ca3af; font-size: 1.2em;">›</div>
                    </div>
                </div>
            `).join('')}
        </div>
    ` : '';
    
    // Crear fecha sin problemas de zona horaria
    const [displayYear, displayMonth, displayDay] = dateISO.split('-').map(Number);
    const displayDate = new Date(displayYear, displayMonth - 1, displayDay);
    
    const result = await Swal.fire({
        title: `📅 ${displayDate.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' })}`,
        html: `
            <div style="text-align: left; padding: 10px;">
                ${eventsListHTML}
                
                <div style="border-top: 2px solid #e5e7eb; padding-top: 20px; margin-top: 10px;">
                    <label style="display: block; margin-bottom: 10px; font-weight: 600; color: #374151;">
                        ➕ Crear nuevo:
                    </label>
                    <select id="new-event-type" class="swal2-select" style="width: 100%;">
                        <option value="">-- Selecciona tipo --</option>
                        <option value="movement-income">💵 Ingreso único</option>
                        <option value="movement-expense">💸 Gasto único</option>
                        <option value="pattern-income">🔁 Patrón de ingreso</option>
                        <option value="pattern-expense">🔁 Patrón de gasto</option>
                        <option value="plan">🎯 Planeación</option>
                        <option value="loan">💰 Préstamo</option>
                    </select>
                </div>
            </div>
        `,
        showCancelButton: true,
        showConfirmButton: existingEvents.length === 0, // Solo mostrar "Crear" si no hay eventos
        confirmButtonText: 'Crear',
        cancelButtonText: 'Cerrar',
        confirmButtonColor: '#10b981',
        width: '600px',
        didOpen: () => {
            // Agregar listeners a los eventos existentes
            const eventItems = document.querySelectorAll('.event-item');
            eventItems.forEach((item, index) => {
                item.addEventListener('click', () => {
                    Swal.close();
                    existingEvents[index].onClick();
                });
            });
            
            // Si hay eventos, ocultar botón confirmar y agregar botón "Crear" en el select
            if (existingEvents.length > 0) {
                const select = document.getElementById('new-event-type');
                select.addEventListener('change', async (e) => {
                    const value = e.target.value;
                    if (value) {
                        Swal.close();
                        await handleCreateNew(dateISO, value, onCreated);
                    }
                });
            }
        },
        preConfirm: () => {
            const eventType = document.getElementById('new-event-type').value;
            if (!eventType) {
                Swal.showValidationMessage('Selecciona un tipo de evento');
                return false;
            }
            return eventType;
        }
    });
    
    // Si no hay eventos y se confirma, crear nuevo
    if (result.isConfirmed && result.value) {
        await handleCreateNew(dateISO, result.value, onCreated);
    }
}

/**
 * Maneja la creación de nuevo evento según el tipo
 */
async function handleCreateNew(dateISO, eventType, onCreated) {
    if (eventType === 'movement-income') {
        await showCreateMovementDialog(dateISO, 'ingreso', onCreated);
    } else if (eventType === 'movement-expense') {
        await showCreateMovementDialog(dateISO, 'gasto', onCreated);
    } else if (eventType === 'pattern-income') {
        await showCreatePatternDialog(dateISO, 'income', onCreated);
    } else if (eventType === 'pattern-expense') {
        await showCreatePatternDialog(dateISO, 'expense', onCreated);
    } else if (eventType === 'plan') {
        await showCreatePlanDialog(dateISO, onCreated);
    } else if (eventType === 'loan') {
        await showCreateLoanDialog(dateISO, onCreated);
    }
}

/**
 * Modal para crear un movimiento único
 */
async function showCreateMovementDialog(dateISO, type, onCreated) {
    // Cargar patrones de ahorro y planeaciones disponibles
    let savingsPatterns = [];
    let activePlans = [];
    
    // Para ingresos: cargar destinos posibles
    // Para gastos: cargar orígenes posibles
    try {
        savingsPatterns = await getSavingsPatterns(true);
    } catch (e) {
        console.error('Error loading savings patterns:', e);
    }
    try {
        const { getPlans } = await import('./plans-v2.js');
        const allPlans = await getPlans();
        activePlans = allPlans.filter(p => p.status === 'active' || p.status === 'planned');
    } catch (e) {
        console.error('Error loading plans:', e);
    }
    
    // Obtener balance disponible para gastos
    let availableBalance = 0;
    if (type === 'gasto') {
        try {
            const balanceSummary = await getConfirmedBalanceSummary();
            availableBalance = balanceSummary.balance || 0;
        } catch (e) {
            console.error('Error getting balance:', e);
        }
    }
    
    // HTML de opciones de destino para ingresos
    const destinationHTML = type === 'ingreso' ? `
        <div style="margin-bottom: 15px; padding: 12px; background: #f0fdf4; border-radius: 8px; border: 1px solid #86efac;">
            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #166534;">
                💰 ¿Dónde guardar este ingreso?
            </label>
            <div style="display: flex; flex-direction: column; gap: 8px;">
                <label style="display: flex; align-items: center; cursor: pointer;">
                    <input type="radio" name="income-destination" value="balance" checked style="margin-right: 10px;">
                    <span>📊 Al balance general</span>
                </label>
                ${savingsPatterns.length > 0 ? `
                    <label style="display: flex; align-items: center; cursor: pointer;">
                        <input type="radio" name="income-destination" value="savings" style="margin-right: 10px;">
                        <span>🏦 A un patrón de ahorro</span>
                    </label>
                    <div id="savings-select-container" style="display: none; margin-top: 8px; margin-left: 25px;">
                        <select id="income-savings-target" class="swal2-select" style="width: 100%;">
                            ${savingsPatterns.map(sp => `
                                <option value="${sp.id}">
                                    ${sp.name} (Balance: $${parseFloat(sp.current_balance || 0).toLocaleString('es-MX')})
                                </option>
                            `).join('')}
                        </select>
                    </div>
                ` : ''}
                ${activePlans.length > 0 ? `
                    <label style="display: flex; align-items: center; cursor: pointer;">
                        <input type="radio" name="income-destination" value="plan" style="margin-right: 10px;">
                        <span>🎯 A una planeación/meta</span>
                    </label>
                    <div id="plan-select-container" style="display: none; margin-top: 8px; margin-left: 25px;">
                        <select id="income-plan-target" class="swal2-select" style="width: 100%;">
                            ${activePlans.map(plan => {
                                const progress = parseFloat(plan.progress_percent) || 0;
                                const remaining = parseFloat(plan.remaining_amount) || parseFloat(plan.target_amount);
                                // V2: usar name en lugar de title
                                return `
                                    <option value="${plan.id}">
                                        ${plan.name} (${progress.toFixed(0)}% - Faltan: $${remaining.toLocaleString('es-MX')})
                                    </option>
                                `;
                            }).join('')}
                        </select>
                    </div>
                ` : ''}
            </div>
        </div>
    ` : '';
    
    // HTML de opciones de origen para gastos
    const sourceHTML = type === 'gasto' ? `
        <div style="margin-bottom: 15px; padding: 12px; background: #fef2f2; border-radius: 8px; border: 1px solid #fca5a5;">
            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #991b1b;">
                💸 ¿De dónde descontar este gasto?
            </label>
            <div style="display: flex; flex-direction: column; gap: 8px;">
                <label style="display: flex; align-items: center; cursor: pointer;">
                    <input type="radio" name="expense-source" value="balance" checked style="margin-right: 10px;">
                    <span>📊 Del balance general <span style="color: #6b7280; font-size: 0.85em;">($${availableBalance.toLocaleString('es-MX', {minimumFractionDigits: 2})})</span></span>
                </label>
                ${savingsPatterns.filter(sp => parseFloat(sp.current_balance || 0) > 0).length > 0 ? `
                    <label style="display: flex; align-items: center; cursor: pointer;">
                        <input type="radio" name="expense-source" value="savings" style="margin-right: 10px;">
                        <span>🏦 De un patrón de ahorro</span>
                    </label>
                    <div id="expense-savings-select-container" style="display: none; margin-top: 8px; margin-left: 25px;">
                        <select id="expense-savings-source" class="swal2-select" style="width: 100%;">
                            ${savingsPatterns.filter(sp => parseFloat(sp.current_balance || 0) > 0).map(sp => `
                                <option value="${sp.id}" data-balance="${parseFloat(sp.current_balance || 0)}">
                                    ${sp.name} (Disponible: $${parseFloat(sp.current_balance || 0).toLocaleString('es-MX')})
                                </option>
                            `).join('')}
                        </select>
                    </div>
                ` : ''}
                ${activePlans.filter(p => parseFloat(p.current_amount || 0) > 0).length > 0 ? `
                    <label style="display: flex; align-items: center; cursor: pointer;">
                        <input type="radio" name="expense-source" value="plan" style="margin-right: 10px;">
                        <span>🎯 De una planeación/meta</span>
                    </label>
                    <div id="expense-plan-select-container" style="display: none; margin-top: 8px; margin-left: 25px;">
                        <select id="expense-plan-source" class="swal2-select" style="width: 100%;">
                            ${activePlans.filter(p => parseFloat(p.current_amount || 0) > 0).map(plan => {
                                // V2: usar current_amount en lugar de saved_amount, name en lugar de title
                                const savedAmount = parseFloat(plan.current_amount) || 0;
                                return `
                                    <option value="${plan.id}" data-balance="${savedAmount}">
                                        ${plan.name} (Ahorrado: $${savedAmount.toLocaleString('es-MX')})
                                    </option>
                                `;
                            }).join('')}
                        </select>
                    </div>
                ` : ''}
            </div>
        </div>
    ` : '';
    
    const { value: formValues } = await Swal.fire({
        title: type === 'ingreso' ? '💵 Nuevo Ingreso' : '💸 Nuevo Gasto',
        html: `
            <div style="text-align: left; padding: 10px;">
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Título:
                    </label>
                    <input 
                        id="movement-title" 
                        type="text" 
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="Ej: Pago de renta"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Descripción (opcional):
                    </label>
                    <textarea 
                        id="movement-description" 
                        class="swal2-textarea" 
                        style="margin: 0; width: 100%; height: 60px;"
                        placeholder="Detalles adicionales..."
                    ></textarea>
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Monto:
                    </label>
                    <input 
                        id="movement-amount" 
                        type="number" 
                        step="0.01"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="0.00"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Categoría (opcional):
                    </label>
                    <input 
                        id="movement-category" 
                        type="text" 
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="${type === 'gasto' ? 'Ej: Hogar, Transporte' : 'Ej: Salario, Freelance'}"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Fecha:
                    </label>
                    <input 
                        id="movement-date" 
                        type="date" 
                        value="${dateISO}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                    />
                </div>
                
                ${destinationHTML}
                ${sourceHTML}

                <!-- Panel de Análisis Inteligente -->
                <div id="smart-analysis-panel" class="smart-analysis-panel" style="
                    margin-top: 15px;
                    padding: 15px;
                    border-radius: 12px;
                    background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
                    border: 1px solid #cbd5e1;
                    display: none;
                ">
                    <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 12px;">
                        <span style="font-size: 1.2rem;">🧠</span>
                        <span style="font-weight: 600; color: #1e40af;">Análisis Inteligente</span>
                    </div>
                    <div id="analysis-content" style="font-size: 0.9rem; color: #475569;">
                        Ingresa el monto para ver el análisis...
                    </div>
                </div>
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: 'Crear',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: type === 'ingreso' ? '#10b981' : '#ef4444',
        cancelButtonColor: '#6b7280',
        focusConfirm: false,
        didOpen: async () => {
            // Importar el asistente financiero inteligente
            const { SmartFinancialAssistant, updateAnalysisPanel } = await import('./smart-financial-assistant.js');
            const assistant = new SmartFinancialAssistant();
            
            // Pre-cargar estado financiero
            await assistant.preloadFinancialState();
            
            // Función para actualizar el análisis en tiempo real
            const updateMovementAnalysis = async () => {
                const amount = parseFloat(document.getElementById('movement-amount').value) || 0;
                const category = document.getElementById('movement-category').value;
                
                if (amount > 0) {
                    const analysis = await assistant.getMovementAnalysis(amount, type === 'ingreso' ? 'income' : 'expense', category);
                    updateAnalysisPanel(analysis, 'movement');
                } else {
                    const panel = document.getElementById('smart-analysis-panel');
                    if (panel) panel.style.display = 'none';
                }
            };
            
            // Escuchar cambios en monto y categoría
            document.getElementById('movement-amount').addEventListener('input', updateMovementAnalysis);
            document.getElementById('movement-category').addEventListener('input', updateMovementAnalysis);
            
            // Toggle visibilidad del selector de ahorro y planeación para ingresos
            if (type === 'ingreso') {
                const radios = document.querySelectorAll('input[name="income-destination"]');
                const savingsContainer = document.getElementById('savings-select-container');
                const planContainer = document.getElementById('plan-select-container');
                radios.forEach(radio => {
                    radio.addEventListener('change', () => {
                        if (savingsContainer) {
                            savingsContainer.style.display = radio.value === 'savings' ? 'block' : 'none';
                        }
                        if (planContainer) {
                            planContainer.style.display = radio.value === 'plan' ? 'block' : 'none';
                        }
                    });
                });
            }
            
            // Toggle visibilidad del selector de origen para gastos
            if (type === 'gasto') {
                const radios = document.querySelectorAll('input[name="expense-source"]');
                const savingsContainer = document.getElementById('expense-savings-select-container');
                const planContainer = document.getElementById('expense-plan-select-container');
                radios.forEach(radio => {
                    radio.addEventListener('change', () => {
                        if (savingsContainer) {
                            savingsContainer.style.display = radio.value === 'savings' ? 'block' : 'none';
                        }
                        if (planContainer) {
                            planContainer.style.display = radio.value === 'plan' ? 'block' : 'none';
                        }
                    });
                });
            }
        },
        preConfirm: () => {
            const title = document.getElementById('movement-title').value;
            const description = document.getElementById('movement-description').value;
            const amount = document.getElementById('movement-amount').value;
            const category = document.getElementById('movement-category').value;
            const date = document.getElementById('movement-date').value;
            
            if (!title || title.trim() === '') {
                Swal.showValidationMessage('El título es obligatorio');
                return false;
            }
            if (!amount || parseFloat(amount) <= 0) {
                Swal.showValidationMessage('Ingresa un monto válido');
                return false;
            }
            if (!date) {
                Swal.showValidationMessage('Ingresa una fecha válida');
                return false;
            }
            
            // Para ingresos, obtener el destino
            let destination = 'balance';
            let savingsPatternId = null;
            let planId = null;
            if (type === 'ingreso') {
                const selectedDestination = document.querySelector('input[name="income-destination"]:checked');
                if (selectedDestination) {
                    destination = selectedDestination.value;
                    if (destination === 'savings') {
                        const savingsSelect = document.getElementById('income-savings-target');
                        if (savingsSelect) {
                            savingsPatternId = savingsSelect.value;
                        }
                    } else if (destination === 'plan') {
                        const planSelect = document.getElementById('income-plan-target');
                        if (planSelect) {
                            planId = planSelect.value;
                        }
                    }
                }
            }
            
            // Para gastos, obtener el origen
            let source = 'balance';
            let sourceSavingsPatternId = null;
            let sourcePlanId = null;
            if (type === 'gasto') {
                const selectedSource = document.querySelector('input[name="expense-source"]:checked');
                if (selectedSource) {
                    source = selectedSource.value;
                    if (source === 'savings') {
                        const savingsSelect = document.getElementById('expense-savings-source');
                        if (savingsSelect) {
                            sourceSavingsPatternId = savingsSelect.value;
                            // Validar que haya saldo suficiente
                            const selectedOption = savingsSelect.options[savingsSelect.selectedIndex];
                            const availableSavings = parseFloat(selectedOption.dataset.balance) || 0;
                            if (parseFloat(amount) > availableSavings) {
                                Swal.showValidationMessage(`Saldo insuficiente en el ahorro. Disponible: $${availableSavings.toLocaleString('es-MX')}`);
                                return false;
                            }
                        }
                    } else if (source === 'plan') {
                        const planSelect = document.getElementById('expense-plan-source');
                        if (planSelect) {
                            sourcePlanId = planSelect.value;
                            // Validar que haya saldo suficiente
                            const selectedOption = planSelect.options[planSelect.selectedIndex];
                            const availablePlan = parseFloat(selectedOption.dataset.balance) || 0;
                            if (parseFloat(amount) > availablePlan) {
                                Swal.showValidationMessage(`Saldo insuficiente en la planeación. Disponible: $${availablePlan.toLocaleString('es-MX')}`);
                                return false;
                            }
                        }
                    }
                }
            }
            
            return { 
                title: title.trim(),
                description: description.trim(),
                amount: parseFloat(amount), 
                category: category.trim(),
                date,
                destination,
                savingsPatternId,
                planId,
                source,
                sourceSavingsPatternId,
                sourcePlanId
            };
        }
    });

    if (formValues) {
        try {
            // Importar dinámicamente el módulo de movements
            const { createMovement } = await import('./movements.js');
            
            // Obtener userId del localStorage directamente
            const sessionData = localStorage.getItem('calendar_session');
            if (!sessionData) {
                throw new Error('No hay sesión activa');
            }
            
            const session = JSON.parse(sessionData);
            const userId = session.userId;
            
            if (!userId) {
                throw new Error('No hay usuario autenticado');
            }

            // Crear el movimiento
            const movementData = {
                user_id: userId,
                type: type,
                title: formValues.title,
                description: formValues.description || null,
                confirmed_amount: formValues.amount,
                expected_amount: formValues.amount,
                category: formValues.category || null,
                date: formValues.date,
                status: 'confirmed'
            };
            
            // Si el destino es una planeación, NO crear movement normal
            // contributeToPlan maneja todo internamente para evitar duplicación
            const skipMovementCreation = (type === 'ingreso' && formValues.destination === 'plan' && formValues.planId);
            
            let movement = null;
            if (!skipMovementCreation) {
                movement = await createMovement(movementData);
            }
            
            // Si el ingreso va a ahorro, crear el depósito
            if (type === 'ingreso' && formValues.destination === 'savings' && formValues.savingsPatternId) {
                try {
                    await createSavingsDeposit(
                        formValues.savingsPatternId,
                        formValues.amount,
                        `Depósito desde ingreso: ${formValues.title}`
                    );
                    
                    await Swal.fire({
                        icon: 'success',
                        title: '✅ Ingreso + Ahorro',
                        html: `
                            <p>Ingreso registrado y depositado al ahorro.</p>
                            <p style="color: #059669; font-weight: 600;">+$${formValues.amount.toLocaleString('es-MX', { minimumFractionDigits: 2 })}</p>
                        `,
                        timer: 2000,
                        showConfirmButton: false
                    });
                } catch (savingsError) {
                    console.error('Error depositing to savings:', savingsError);
                    await Swal.fire({
                        icon: 'warning',
                        title: 'Ingreso creado',
                        text: 'Pero hubo un error al depositar al ahorro: ' + savingsError.message
                    });
                }
            } else if (type === 'ingreso' && formValues.destination === 'plan' && formValues.planId) {
                // Si el ingreso va a una planeación, solo actualizar el plan (el movement ya tiene plan_id)
                try {
                    const { contributeToPlan, recalculatePlanDates, getPlanById } = await import('./plans-v2.js');
                    
                    // Agregar contribución al plan
                    await contributeToPlan(formValues.planId, formValues.amount, formValues.title, formValues.date);
                    
                    // Recalcular fecha objetivo pasando la contribución extra
                    const result = await recalculatePlanDates(formValues.planId, formValues.amount);
                    
                    // Preparar mensaje según resultado
                    let htmlMessage = `
                        <p>Ingreso registrado y agregado a la planeación <strong>${result.name}</strong>.</p>
                        <p style="color: #059669; font-weight: 600;">+$${formValues.amount.toLocaleString('es-MX', { minimumFractionDigits: 2 })}</p>
                        <p style="margin-top: 10px;">Progreso: <strong>${result.progress_percent}%</strong></p>
                    `;
                    
                    // Si la meta se completó
                    if (result.completed) {
                        await Swal.fire({
                            icon: 'success',
                            title: '🎉 ¡Meta Completada!',
                            html: `
                                <p>¡Felicidades! Has alcanzado tu meta de ahorro para <strong>${result.name}</strong>.</p>
                                <p style="color: #059669; font-weight: 600;">Meta: $${parseFloat(result.target_amount).toLocaleString('es-MX', { minimumFractionDigits: 2 })}</p>
                            `,
                            confirmButtonText: '¡Genial!'
                        });
                    } else if (result.dateChanged) {
                        // Si la fecha cambió, notificar al usuario
                        const oldDate = new Date(result.old_target_date).toLocaleDateString('es-MX', { year: 'numeric', month: 'long', day: 'numeric' });
                        const newDate = new Date(result.new_target_date).toLocaleDateString('es-MX', { year: 'numeric', month: 'long', day: 'numeric' });
                        
                        htmlMessage += `
                            <div style="margin-top: 15px; padding: 10px; background: #ecfdf5; border-radius: 8px; border-left: 4px solid #10b981;">
                                <p style="margin: 0; color: #065f46;"><strong>📅 Fecha actualizada</strong></p>
                                <p style="margin: 5px 0 0 0; font-size: 0.9em; color: #047857;">
                                    ${oldDate} → <strong>${newDate}</strong>
                                </p>
                                ${result.monthly_contribution > 0 ? `<p style="margin: 5px 0 0 0; font-size: 0.85em; color: #6b7280;">Ahorro mensual estimado: $${result.monthly_contribution.toLocaleString('es-MX', { minimumFractionDigits: 2 })}</p>` : ''}
                            </div>
                        `;
                        
                        await Swal.fire({
                            icon: 'success',
                            title: '✅ Ingreso + Planeación',
                            html: htmlMessage,
                            confirmButtonText: 'Entendido'
                        });
                    } else {
                        await Swal.fire({
                            icon: 'success',
                            title: '✅ Ingreso + Planeación',
                            html: htmlMessage,
                            timer: 3000,
                            showConfirmButton: false
                        });
                    }
                } catch (planError) {
                    console.error('Error adding to plan:', planError);
                    await Swal.fire({
                        icon: 'warning',
                        title: 'Ingreso creado',
                        text: 'Pero hubo un error al agregar a la planeación: ' + planError.message
                    });
                }
            } else if (type === 'gasto' && formValues.source === 'savings' && formValues.sourceSavingsPatternId) {
                // Si el gasto sale del ahorro, crear el retiro
                try {
                    await createSavingsWithdrawal(
                        formValues.sourceSavingsPatternId,
                        formValues.amount,
                        `Retiro para gasto: ${formValues.title}`
                    );
                    
                    await Swal.fire({
                        icon: 'success',
                        title: '✅ Gasto desde Ahorro',
                        html: `
                            <p>Gasto registrado y descontado del ahorro.</p>
                            <p style="color: #ef4444; font-weight: 600;">-$${formValues.amount.toLocaleString('es-MX', { minimumFractionDigits: 2 })}</p>
                        `,
                        timer: 2000,
                        showConfirmButton: false
                    });
                } catch (savingsError) {
                    console.error('Error withdrawing from savings:', savingsError);
                    await Swal.fire({
                        icon: 'warning',
                        title: 'Gasto creado',
                        text: 'Pero hubo un error al retirar del ahorro: ' + savingsError.message
                    });
                }
            } else if (type === 'gasto' && formValues.source === 'plan' && formValues.sourcePlanId) {
                // Si el gasto sale de una planeación, descontar del plan
                try {
                    const { withdrawFromPlan, recalculatePlanDates, getPlanById } = await import('./plans-v2.js');
                    await withdrawFromPlan(formValues.sourcePlanId, formValues.amount, `Retiro para gasto: ${formValues.title}`);
                    
                    // Recalcular fecha objetivo
                    const result = await recalculatePlanDates(formValues.sourcePlanId, 0);
                    
                    let htmlMessage = `
                        <p>Gasto registrado y descontado de <strong>${result.name}</strong>.</p>
                        <p style="color: #ef4444; font-weight: 600;">-$${formValues.amount.toLocaleString('es-MX', { minimumFractionDigits: 2 })}</p>
                        <p style="margin-top: 10px;">Progreso: <strong>${result.progress_percent}%</strong></p>
                    `;
                    
                    if (result.dateChanged) {
                        const oldDate = new Date(result.old_target_date).toLocaleDateString('es-MX', { year: 'numeric', month: 'long', day: 'numeric' });
                        const newDate = new Date(result.new_target_date).toLocaleDateString('es-MX', { year: 'numeric', month: 'long', day: 'numeric' });
                        
                        htmlMessage += `
                            <div style="margin-top: 15px; padding: 10px; background: #fef2f2; border-radius: 8px; border-left: 4px solid #ef4444;">
                                <p style="margin: 0; color: #991b1b;"><strong>📅 Fecha actualizada</strong></p>
                                <p style="margin: 5px 0 0 0; font-size: 0.9em; color: #b91c1c;">
                                    ${oldDate} → <strong>${newDate}</strong>
                                </p>
                            </div>
                        `;
                        
                        await Swal.fire({
                            icon: 'warning',
                            title: '💸 Gasto desde Planeación',
                            html: htmlMessage,
                            confirmButtonText: 'Entendido'
                        });
                    } else {
                        await Swal.fire({
                            icon: 'success',
                            title: '✅ Gasto desde Planeación',
                            html: htmlMessage,
                            timer: 3000,
                            showConfirmButton: false
                        });
                    }
                } catch (planError) {
                    console.error('Error withdrawing from plan:', planError);
                    await Swal.fire({
                        icon: 'warning',
                        title: 'Gasto creado',
                        text: 'Pero hubo un error al descontar de la planeación: ' + planError.message
                    });
                }
            } else {
                await Swal.fire({
                    icon: 'success',
                    title: '✅ Creado',
                    text: `${type === 'ingreso' ? 'Ingreso' : 'Gasto'} creado exitosamente`,
                    timer: 1500,
                    showConfirmButton: false
                });
            }
            
            // Actualizar el indicador de balance
            if (window.refreshBalanceIndicator) window.refreshBalanceIndicator();

            if (onCreated) onCreated(movement);
        } catch (error) {
            console.error('Error creating movement:', error);
            await Swal.fire({
                icon: 'error',
                title: 'Error',
                text: error.message || 'No se pudo crear el movimiento'
            });
        }
    }
}

// ============================================================================
// MODAL: CREAR PATRÓN (INGRESO/GASTO RECURRENTE)
// ============================================================================

/**
 * Modal para crear un patrón de ingreso o gasto recurrente
 */
async function showCreatePatternDialog(startDate, type, onCreated) {
    const isIncome = type === 'income';
    
    // Para gastos, cargar los patrones de ingreso disponibles
    let incomePatterns = [];
    if (!isIncome) {
        try {
            incomePatterns = await getIncomePatterns();
        } catch (e) {
            console.error('Error loading income patterns:', e);
        }
    }
    
    // Generar HTML de fuentes de ingreso solo para gastos
    const incomeSourcesHTML = !isIncome && incomePatterns.length > 0 ? `
        <div style="margin-bottom: 15px; padding: 12px; background: #f0f9ff; border-radius: 8px; border: 1px solid #bae6fd;">
            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #0369a1;">
                💰 Vincular a Fuente de Ingreso (opcional):
            </label>
            <select id="pattern-income-source" class="swal2-select" style="width: 100%; margin-bottom: 8px;">
                <option value="">Sin vincular</option>
                ${incomePatterns.map(ip => `
                    <option value="${ip.id}">${ip.name} - ${formatCurrency(ip.base_amount)}</option>
                `).join('')}
            </select>
            
            <div id="allocation-section" style="display: none; margin-top: 10px;">
                <label style="display: block; margin-bottom: 5px; font-weight: 500; font-size: 0.9em;">
                    Tipo de asignación:
                </label>
                <select id="pattern-allocation-type" class="swal2-select" style="width: 100%; margin-bottom: 8px;">
                    <option value="percent">Porcentaje del ingreso</option>
                    <option value="fixed">Monto fijo</option>
                </select>
                
                <div id="percent-input-section">
                    <label style="display: block; margin-bottom: 5px; font-weight: 500; font-size: 0.9em;">
                        Porcentaje a asignar:
                    </label>
                    <div style="display: flex; align-items: center; gap: 8px;">
                        <input 
                            id="pattern-allocation-percent" 
                            type="number" 
                            min="0" 
                            max="100"
                            step="0.1"
                            value="0"
                            class="swal2-input" 
                            style="margin: 0; flex: 1;"
                        />
                        <span style="font-weight: 600;">%</span>
                    </div>
                    <small id="available-percent-hint" style="color: #059669; font-size: 0.8em;"></small>
                </div>
            </div>
            <small style="color: #6b7280; font-size: 0.8em;">
                Vincular permite calcular qué porcentaje de tus ingresos va a este gasto
            </small>
        </div>
    ` : (!isIncome ? `
        <div style="margin-bottom: 15px; padding: 10px; background: #fef3c7; border-radius: 8px; border: 1px solid #fcd34d;">
            <small style="color: #92400e;">
                💡 Crea primero un patrón de ingreso para poder vincular gastos
            </small>
        </div>
    ` : '');
    
    const { value: formValues } = await Swal.fire({
        title: isIncome ? '🔁 Nuevo Patrón de Ingreso' : '🔁 Nuevo Patrón de Gasto',
        html: `
            <div style="text-align: left; padding: 10px;">
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Nombre:
                    </label>
                    <input 
                        id="pattern-name" 
                        type="text" 
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="Ej: Salario, Renta, Servicios"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Monto:
                    </label>
                    <input 
                        id="pattern-amount" 
                        type="number" 
                        step="0.01"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="0.00"
                    />
                </div>

                ${incomeSourcesHTML}

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Frecuencia:
                    </label>
                    <select id="pattern-frequency" class="swal2-select" style="width: 100%;">
                        <option value="weekly">Semanal</option>
                        <option value="monthly" selected>Mensual</option>
                        <option value="yearly">Anual</option>
                    </select>
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Intervalo:
                    </label>
                    <input 
                        id="pattern-interval" 
                        type="number" 
                        min="1"
                        value="1"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="1 = cada vez, 2 = cada 2 veces"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Fecha de inicio:
                    </label>
                    <input 
                        id="pattern-start-date" 
                        type="date" 
                        value="${startDate}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Fecha límite (opcional):
                    </label>
                    <input 
                        id="pattern-end-date" 
                        type="date" 
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="Dejar vacío para indefinido"
                    />
                    <small style="color: #6b7280; font-size: 0.85em;">
                        Dejar vacío para que continúe indefinidamente
                    </small>
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Categoría (opcional):
                    </label>
                    <input 
                        id="pattern-category" 
                        type="text" 
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="${isIncome ? 'Ej: Trabajo, Inversiones' : 'Ej: Hogar, Transporte'}"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Descripción (opcional):
                    </label>
                    <textarea 
                        id="pattern-description" 
                        class="swal2-textarea" 
                        style="margin: 0; width: 100%; min-height: 60px;"
                        placeholder="Detalles adicionales..."
                    ></textarea>
                </div>
                
                <!-- Panel de Análisis Inteligente (solo para gastos) -->
                ${!isIncome ? `
                <div id="smart-analysis-container" style="margin-top: 16px;">
                    <div style="text-align: center; padding: 20px; color: #9ca3af; font-size: 13px;">
                        <span style="font-size: 20px;">🧠</span><br>
                        Completa el monto y categoría para ver el análisis inteligente
                    </div>
                </div>
                ` : ''}
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: 'Crear Patrón',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: isIncome ? '#10b981' : '#ef4444',
        width: '650px',
        didOpen: async () => {
            // Precargar estado financiero para análisis
            if (!isIncome) {
                await loadFinancialState();
            }
            
            // Setup income source toggle for expense patterns
            if (!isIncome && incomePatterns.length > 0) {
                const incomeSourceSelect = document.getElementById('pattern-income-source');
                const allocationSection = document.getElementById('allocation-section');
                const allocationTypeSelect = document.getElementById('pattern-allocation-type');
                const percentSection = document.getElementById('percent-input-section');
                const percentInput = document.getElementById('pattern-allocation-percent');
                const availableHint = document.getElementById('available-percent-hint');
                const amountInputForAlloc = document.getElementById('pattern-amount');
                
                // Función para calcular y sugerir el porcentaje automáticamente
                const updateSuggestedAllocation = async () => {
                    const selectedId = incomeSourceSelect?.value;
                    const expenseAmount = parseFloat(amountInputForAlloc?.value) || 0;
                    const allocationType = allocationTypeSelect?.value || 'percent';
                    
                    if (!selectedId || expenseAmount <= 0 || !percentInput) return;
                    
                    // Obtener el ingreso seleccionado
                    const selectedIncome = incomePatterns.find(ip => ip.id === selectedId);
                    if (!selectedIncome) return;
                    
                    const incomeAmount = parseFloat(selectedIncome.base_amount) || 0;
                    if (incomeAmount <= 0) return;
                    
                    if (allocationType === 'percent') {
                        // Calcular porcentaje: (gasto / ingreso) * 100
                        const suggestedPercent = (expenseAmount / incomeAmount) * 100;
                        percentInput.value = suggestedPercent.toFixed(1);
                    } else {
                        // Monto fijo = el monto del gasto
                        percentInput.value = expenseAmount.toFixed(2);
                    }
                };
                
                if (incomeSourceSelect && allocationSection) {
                    incomeSourceSelect.addEventListener('change', async () => {
                        const selectedId = incomeSourceSelect.value;
                        if (selectedId) {
                            allocationSection.style.display = 'block';
                            // Get available percentage
                            try {
                                const availability = await calculateAvailablePercentage(selectedId);
                                const percentAvailable = (availability?.percent_available || 0) * 100;
                                if (availableHint) {
                                    availableHint.textContent = `Disponible: ${percentAvailable.toFixed(1)}%`;
                                }
                            } catch (e) {
                                console.error('Error calculating available %:', e);
                                if (availableHint) availableHint.textContent = 'Disponible: 100%';
                            }
                            // Auto-calcular porcentaje sugerido
                            await updateSuggestedAllocation();
                        } else {
                            allocationSection.style.display = 'none';
                        }
                    });
                    
                    // También actualizar cuando cambia el monto del gasto
                    if (amountInputForAlloc) {
                        amountInputForAlloc.addEventListener('input', () => {
                            if (incomeSourceSelect.value) {
                                updateSuggestedAllocation();
                            }
                        });
                    }
                    
                    // Toggle percent vs fixed input
                    if (allocationTypeSelect) {
                        allocationTypeSelect.addEventListener('change', () => {
                            const isPercent = allocationTypeSelect.value === 'percent';
                            if (percentSection) {
                                const label = percentSection.querySelector('label');
                                if (label) {
                                    label.textContent = isPercent ? 'Porcentaje a asignar:' : 'Monto fijo a asignar:';
                                }
                                const percentSpan = percentSection.querySelector('span');
                                if (percentSpan) {
                                    percentSpan.textContent = isPercent ? '%' : '$';
                                }
                            }
                            // Recalcular con el nuevo tipo
                            updateSuggestedAllocation();
                        });
                    }
                }
            }
            
            // ==================== ANÁLISIS INTELIGENTE EN TIEMPO REAL ====================
            if (!isIncome) {
                const amountInput = document.getElementById('pattern-amount');
                const nameInput = document.getElementById('pattern-name');
                const categoryInput = document.getElementById('pattern-category');
                const frequencySelect = document.getElementById('pattern-frequency');
                const analysisContainer = document.getElementById('smart-analysis-container');
                
                // Función para actualizar el análisis
                let analysisTimeout = null;
                const updateAnalysis = async () => {
                    const amount = parseFloat(amountInput?.value) || 0;
                    const name = nameInput?.value?.trim() || '';
                    const category = categoryInput?.value?.trim() || '';
                    const frequency = frequencySelect?.value || 'monthly';
                    
                    if (!analysisContainer) return;
                    
                    // Si no hay monto, mostrar mensaje inicial
                    if (amount <= 0) {
                        analysisContainer.innerHTML = `
                            <div style="text-align: center; padding: 20px; color: #9ca3af; font-size: 13px;">
                                <span style="font-size: 20px;">🧠</span><br>
                                Ingresa un monto para ver el análisis inteligente
                            </div>
                        `;
                        return;
                    }
                    
                    // Mostrar loading
                    analysisContainer.innerHTML = `
                        <div style="text-align: center; padding: 15px; color: #6366f1; font-size: 13px;">
                            <span style="font-size: 18px;">⏳</span> Analizando impacto financiero...
                        </div>
                    `;
                    
                    try {
                        // Obtener análisis
                        const analysis = await analyzeNewExpense({
                            name: name || 'Nuevo gasto',
                            amount,
                            frequency,
                            category: category || name,
                            priority: 3
                        });
                        
                        // Renderizar panel de análisis
                        analysisContainer.innerHTML = generateAnalysisPanel(analysis, 'expense');
                        
                        // Agregar botón para aplicar monto sugerido SOLO si debe reducir
                        if (analysis.optimalAmount && analysis.optimalAmount.showSuggestion && analysis.optimalAmount.suggested < amount) {
                            const suggestBtn = document.createElement('button');
                            suggestBtn.type = 'button';
                            suggestBtn.innerHTML = `💡 Reducir a monto sugerido: ${formatCurrency(analysis.optimalAmount.suggested)}`;
                            suggestBtn.style.cssText = `
                                display: block;
                                width: 100%;
                                margin-top: 8px;
                                padding: 10px;
                                background: #fef3c7;
                                border: 1px solid #f59e0b;
                                border-radius: 8px;
                                color: #92400e;
                                font-weight: 500;
                                cursor: pointer;
                                transition: all 0.2s;
                            `;
                            suggestBtn.onmouseover = () => suggestBtn.style.background = '#fde68a';
                            suggestBtn.onmouseout = () => suggestBtn.style.background = '#fef3c7';
                            suggestBtn.onclick = () => {
                                if (amountInput) {
                                    amountInput.value = analysis.optimalAmount.suggested;
                                    updateAnalysis();
                                }
                            };
                            analysisContainer.appendChild(suggestBtn);
                        }
                    } catch (error) {
                        console.error('Error in smart analysis:', error);
                        analysisContainer.innerHTML = `
                            <div style="text-align: center; padding: 15px; color: #9ca3af; font-size: 13px;">
                                <span style="font-size: 18px;">📊</span><br>
                                No se pudo cargar el análisis
                            </div>
                        `;
                    }
                };
                
                // Debounce para no hacer muchas llamadas
                const debouncedUpdate = () => {
                    if (analysisTimeout) clearTimeout(analysisTimeout);
                    analysisTimeout = setTimeout(updateAnalysis, 500);
                };
                
                // Escuchar cambios en los campos relevantes
                if (amountInput) amountInput.addEventListener('input', debouncedUpdate);
                if (categoryInput) categoryInput.addEventListener('input', debouncedUpdate);
                if (frequencySelect) frequencySelect.addEventListener('change', debouncedUpdate);
                if (nameInput) nameInput.addEventListener('input', debouncedUpdate);
            }
        },
        preConfirm: () => {
            const name = document.getElementById('pattern-name').value.trim();
            const amount = document.getElementById('pattern-amount').value;
            const frequency = document.getElementById('pattern-frequency').value;
            const interval = document.getElementById('pattern-interval').value;
            const startDate = document.getElementById('pattern-start-date').value;
            const endDate = document.getElementById('pattern-end-date').value;
            
            // Income source for expenses
            let incomeSource = null;
            if (!isIncome) {
                const incomeSourceSelect = document.getElementById('pattern-income-source');
                if (incomeSourceSelect && incomeSourceSelect.value) {
                    const allocationType = document.getElementById('pattern-allocation-type')?.value || 'percent';
                    const allocationValueRaw = parseFloat(document.getElementById('pattern-allocation-percent')?.value) || 0;
                    // Convertir porcentaje a decimal si es tipo percent (100% -> 1.0)
                    const allocationValue = allocationType === 'percent' 
                        ? allocationValueRaw / 100 
                        : allocationValueRaw;
                    incomeSource = {
                        income_pattern_id: incomeSourceSelect.value,
                        allocation_type: allocationType,
                        allocation_value: allocationValue
                    };
                }
            }
            
            if (!name) {
                Swal.showValidationMessage('El nombre es requerido');
                return false;
            }
            if (!amount || parseFloat(amount) <= 0) {
                Swal.showValidationMessage('Ingresa un monto válido');
                return false;
            }
            if (!startDate) {
                Swal.showValidationMessage('La fecha de inicio es requerida');
                return false;
            }
            if (endDate && endDate < startDate) {
                Swal.showValidationMessage('La fecha límite debe ser posterior a la fecha de inicio');
                return false;
            }
            
            return {
                name,
                base_amount: parseFloat(amount),
                frequency,
                interval: parseInt(interval) || 1,
                start_date: startDate,
                end_date: endDate || null,
                category: document.getElementById('pattern-category').value.trim() || null,
                description: document.getElementById('pattern-description').value.trim() || null,
                income_source: incomeSource
            };
        }
    });

    if (formValues) {
        try {
            const pattern = isIncome 
                ? await createIncomePattern(formValues)
                : await createExpensePattern(formValues);
            
            // If expense pattern with income source, create the link
            if (!isIncome && formValues.income_source && pattern?.id) {
                try {
                    await replaceExpensePatternIncomeSources(pattern.id, [formValues.income_source]);
                } catch (linkError) {
                    console.error('Error linking income source:', linkError);
                    // Don't fail the whole operation, just warn
                }
            }
            
            await Swal.fire({
                icon: 'success',
                title: '✅ Patrón Creado',
                text: `${isIncome ? 'Ingreso' : 'Gasto'} recurrente creado exitosamente`,
                timer: 1500,
                showConfirmButton: false
            });

            if (onCreated) onCreated(pattern);
        } catch (error) {
            console.error('Error creating pattern:', error);
            await Swal.fire({
                icon: 'error',
                title: 'Error',
                text: error.message || 'No se pudo crear el patrón'
            });
        }
    }
}

// ============================================================================
// MODAL: CREAR PLANEACIÓN
// ============================================================================

/**
 * Modal para crear una planeación (meta o gasto futuro)
 */
async function showCreatePlanDialog(targetDate, onCreated) {
    try {
        // PASO 1: Datos básicos de la planeación
        const step1Data = await showPlanStep1(targetDate);
        if (!step1Data) return; // Usuario canceló
        
        // PASO 2: Selección de ingresos
        const step2Data = await showPlanStep2(step1Data);
        if (!step2Data) return; // Usuario canceló o volvió atrás
        
        // PASO 3: Cálculo de viabilidad y confirmación
        const confirmed = await showPlanStep3(step2Data);
        if (!confirmed) return; // Usuario canceló
        
        // Crear el plan
        const { createPlan } = await import('./plans-v2.js');
        const plan = await createPlan(confirmed);
        
        await Swal.fire({
            icon: 'success',
            title: '✅ Planeación Creada',
            text: 'Tu planeación ha sido creada exitosamente',
            timer: 2000,
            showConfirmButton: false
        });
        
        if (onCreated) onCreated(plan);
        
    } catch (error) {
        console.error('Error creating plan:', error);
        await Swal.fire({
            icon: 'error',
            title: 'Error',
            text: error.message || 'No se pudo crear la planeación'
        });
    }
}

/**
 * PASO 1: Formulario básico de datos de la planeación
 */
async function showPlanStep1(targetDate) {
    // Primero preguntar qué tipo de planeación quiere crear
    const { value: planType } = await Swal.fire({
        title: '🎯 Nueva Planeación',
        html: `
            <div style="text-align: left; padding: 10px;">
                <p style="color: #6b7280; margin-bottom: 20px;">
                    ¿Qué tipo de planeación deseas crear?
                </p>
                
                <div class="plan-type-options" style="display: flex; flex-direction: column; gap: 12px;">
                    <label class="plan-type-card" style="display: flex; align-items: center; padding: 16px; border: 2px solid #e5e7eb; border-radius: 12px; cursor: pointer; transition: all 0.2s;">
                        <input type="radio" name="plan-type" value="traditional" checked style="margin-right: 12px; width: 18px; height: 18px;">
                        <div style="flex: 1;">
                            <div style="font-weight: 600; font-size: 1rem; color: #1f2937;">🎯 Meta de Ahorro</div>
                            <div style="font-size: 0.85rem; color: #6b7280;">Para cualquier objetivo: vacaciones, emergencias, proyectos...</div>
                        </div>
                    </label>
                    
                    <label class="plan-type-card" style="display: flex; align-items: center; padding: 16px; border: 2px solid #e5e7eb; border-radius: 12px; cursor: pointer; transition: all 0.2s;">
                        <input type="radio" name="plan-type" value="product" style="margin-right: 12px; width: 18px; height: 18px;">
                        <div style="flex: 1;">
                            <div style="font-weight: 600; font-size: 1rem; color: #1f2937;">🛒 Producto en Línea</div>
                            <div style="font-size: 0.85rem; color: #6b7280;">Pega el enlace de MercadoLibre, Amazon, etc. y extrae los datos automáticamente</div>
                        </div>
                    </label>
                </div>
            </div>
            <style>
                .plan-type-card:hover { border-color: #3b82f6; background: #f0f9ff; }
                .plan-type-card:has(input:checked) { border-color: #3b82f6; background: #eff6ff; }
            </style>
        `,
        showCancelButton: true,
        confirmButtonText: 'Siguiente →',
        cancelButtonText: 'Cancelar',
        width: '500px',
        preConfirm: () => {
            const selected = document.querySelector('input[name="plan-type"]:checked');
            return selected ? selected.value : 'traditional';
        }
    });

    if (!planType) return null; // Usuario canceló

    // Si eligió producto en línea, abrir el modal de productos
    if (planType === 'product') {
        const productModals = await import('./product-wishlist-modals.js');
        productModals.openAddProductModal();
        return null;
    }

    // Continuar con el formulario tradicional
    const { value: formValues } = await Swal.fire({
        title: '🎯 Nueva Planeación - Paso 1 de 3',
        html: `
            <div style="text-align: left; padding: 10px;">
                <p style="color: #6b7280; margin-bottom: 20px;">
                    Ingresa los datos básicos de tu planeación o meta de ahorro.
                </p>
                
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Título: <span style="color: red;">*</span>
                    </label>
                    <input 
                        id="plan-title" 
                        type="text" 
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="Ej: Operación de ojos, Vacaciones"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Monto objetivo: <span style="color: red;">*</span>
                    </label>
                    <input 
                        id="plan-amount" 
                        type="number" 
                        step="0.01"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="35000.00"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Fecha objetivo deseada:
                    </label>
                    <input 
                        id="plan-target-date" 
                        type="date" 
                        value="${targetDate || ''}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Categoría:
                    </label>
                    <input 
                        id="plan-category" 
                        type="text" 
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="Ej: Salud, Viajes, General"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Prioridad:
                    </label>
                    <select id="plan-priority" class="swal2-select" style="width: 100%;">
                        <option value="5">⭐ Prioridad 5 (Más alta)</option>
                        <option value="4">⭐ Prioridad 4</option>
                        <option value="3" selected>⭐ Prioridad 3 (Media)</option>
                        <option value="2">⭐ Prioridad 2</option>
                        <option value="1">⭐ Prioridad 1 (Más baja)</option>
                    </select>
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Descripción:
                    </label>
                    <textarea 
                        id="plan-description" 
                        class="swal2-textarea" 
                        style="margin: 0; width: 100%; min-height: 60px;"
                        placeholder="Detalles adicionales..."
                    ></textarea>
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: flex; align-items: center; cursor: pointer;">
                        <input 
                            id="plan-auto-reminder" 
                            type="checkbox"
                            style="margin-right: 8px;"
                        />
                        <span>Crear recordatorios automáticos</span>
                    </label>
                </div>

                <!-- Panel de Análisis Inteligente -->
                <div id="smart-analysis-panel" class="smart-analysis-panel" style="
                    margin-top: 20px;
                    padding: 15px;
                    border-radius: 12px;
                    background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
                    border: 1px solid #cbd5e1;
                    display: none;
                ">
                    <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 12px;">
                        <span style="font-size: 1.2rem;">🧠</span>
                        <span style="font-weight: 600; color: #1e40af;">Análisis Inteligente</span>
                    </div>
                    <div id="analysis-content" style="font-size: 0.9rem; color: #475569;">
                        Ingresa el monto objetivo para ver el análisis...
                    </div>
                </div>
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: 'Siguiente →',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#3b82f6',
        width: '600px',
        didOpen: async () => {
            // Importar el asistente financiero inteligente
            const { SmartFinancialAssistant, updateAnalysisPanel } = await import('./smart-financial-assistant.js');
            const assistant = new SmartFinancialAssistant();
            
            // Pre-cargar estado financiero
            await assistant.preloadFinancialState();
            
            // Función para actualizar el análisis de plan en tiempo real
            const updatePlanAnalysis = async () => {
                const amount = parseFloat(document.getElementById('plan-amount').value) || 0;
                const targetDate = document.getElementById('plan-target-date').value;
                const priority = parseInt(document.getElementById('plan-priority').value) || 3;
                const title = document.getElementById('plan-title').value.trim();
                
                if (amount > 0) {
                    const analysis = await assistant.getPlanAnalysis(amount, targetDate, priority, title);
                    updateAnalysisPanel(analysis, 'plan');
                } else {
                    const panel = document.getElementById('smart-analysis-panel');
                    if (panel) panel.style.display = 'none';
                }
            };
            
            // Escuchar cambios en los campos relevantes
            const amountInput = document.getElementById('plan-amount');
            const dateInput = document.getElementById('plan-target-date');
            const prioritySelect = document.getElementById('plan-priority');
            
            if (amountInput) amountInput.addEventListener('input', updatePlanAnalysis);
            if (dateInput) dateInput.addEventListener('change', updatePlanAnalysis);
            if (prioritySelect) prioritySelect.addEventListener('change', updatePlanAnalysis);
        },
        preConfirm: () => {
            const title = document.getElementById('plan-title').value.trim();
            const amount = document.getElementById('plan-amount').value;
            
            if (!title) {
                Swal.showValidationMessage('El título es requerido');
                return false;
            }
            if (!amount || parseFloat(amount) <= 0) {
                Swal.showValidationMessage('Ingresa un monto válido mayor a 0');
                return false;
            }
            
            return {
                title,
                target_amount: parseFloat(amount),
                target_date: document.getElementById('plan-target-date').value || null,
                category: document.getElementById('plan-category').value.trim() || null,
                priority: parseInt(document.getElementById('plan-priority').value),
                description: document.getElementById('plan-description').value.trim() || null,
                auto_create_reminder: document.getElementById('plan-auto-reminder').checked
            };
        }
    });
    
    return formValues;
}

/**
 * PASO 2: Selección de ingresos que financiarán el plan
 */
async function showPlanStep2(step1Data) {
    // Obtener ingresos disponibles
    const { getIncomePatterns } = await import('./patterns.js');
    
    let incomePatterns = [];
    try {
        incomePatterns = await getIncomePatterns();
    } catch (error) {
        console.error('Error loading income patterns:', error);
    }
    
    if (incomePatterns.length === 0) {
        const result = await Swal.fire({
            icon: 'warning',
            title: 'Sin ingresos registrados',
            html: `
                <p>No tienes ingresos recurrentes registrados.</p>
                <p>Puedes continuar sin asignar ingresos (el cálculo de fecha sugerida no estará disponible) o cancelar y crear ingresos primero.</p>
            `,
            showCancelButton: true,
            confirmButtonText: 'Continuar sin ingresos',
            cancelButtonText: 'Cancelar',
            confirmButtonColor: '#f59e0b'
        });
        
        if (result.isConfirmed) {
            return { ...step1Data, income_sources: [] };
        }
        return null;
    }
    
    // Obtener disponibilidad de cada ingreso
    const allocations = await getIncomePatternAllocations();
    const allocationMap = {};
    allocations.forEach(a => {
        allocationMap[a.income_pattern_id] = {
            percentAvailable: parseFloat(a.percent_available) || 1,
            amountAvailable: parseFloat(a.amount_available) || parseFloat(a.base_amount) || 0,
            totalAllocated: parseFloat(a.total_percent_allocated) || 0,
            baseAmount: parseFloat(a.base_amount) || 0,
            toExpenses: parseFloat(a.percent_allocated_to_expenses) || 0,
            toPlans: parseFloat(a.percent_allocated_to_plans) || 0,
            toSavings: parseFloat(a.percent_allocated_to_savings) || 0
        };
    });
    
    // Construir opciones de ingresos con información de disponibilidad
    const incomeOptionsHTML = incomePatterns.map(income => {
        const freqLabel = {
            'weekly': 'Semanal',
            'biweekly': 'Quincenal',
            'monthly': 'Mensual',
            'yearly': 'Anual'
        }[income.frequency] || income.frequency;
        
        // Obtener disponibilidad o asignar valores por defecto
        const allocation = allocationMap[income.id] || {
            percentAvailable: 1,
            amountAvailable: income.base_amount,
            totalAllocated: 0,
            baseAmount: income.base_amount,
            toExpenses: 0,
            toPlans: 0,
            toSavings: 0
        };
        
        const percentAvailableDisplay = (allocation.percentAvailable * 100).toFixed(0);
        const percentAllocatedDisplay = (allocation.totalAllocated * 100).toFixed(0);
        const amountAvailable = allocation.amountAvailable;
        const isFullyAllocated = allocation.percentAvailable <= 0;
        
        // Color basado en disponibilidad
        let availabilityColor = '#10b981'; // Verde
        let availabilityBg = '#d1fae5';
        if (allocation.percentAvailable < 0.3) {
            availabilityColor = '#ef4444'; // Rojo
            availabilityBg = '#fee2e2';
        } else if (allocation.percentAvailable < 0.6) {
            availabilityColor = '#f59e0b'; // Amarillo
            availabilityBg = '#fef3c7';
        }
        
        return `
        <div style="margin-bottom: 12px; padding: 12px; background: ${isFullyAllocated ? '#f3f4f6' : '#f9fafb'}; border-radius: 8px; border: 2px solid transparent; ${isFullyAllocated ? 'opacity: 0.7;' : ''}" class="income-option" data-income-id="${income.id}">
            <label style="display: flex; align-items: center; cursor: pointer;">
                <input 
                    type="checkbox" 
                    class="income-source-checkbox" 
                    data-income-id="${income.id}"
                    data-income-name="${income.name}"
                    data-income-amount="${income.base_amount}"
                    data-income-frequency="${income.frequency}"
                    data-income-interval="${income.interval || 1}"
                    data-max-percent="${percentAvailableDisplay}"
                    data-amount-available="${amountAvailable}"
                    style="margin-right: 10px; width: 18px; height: 18px;"
                    ${isFullyAllocated ? 'disabled' : ''}
                />
                <div style="flex: 1;">
                    <div style="font-weight: 600; color: ${isFullyAllocated ? '#9ca3af' : '#1f2937'};">${income.name}</div>
                    <div style="font-size: 0.9em; color: ${isFullyAllocated ? '#9ca3af' : '#10b981'}; margin-top: 2px;">
                        💰 $${income.base_amount.toLocaleString('es-MX')} · ${freqLabel}
                        ${income.interval > 1 ? ` (cada ${income.interval})` : ''}
                    </div>
                </div>
            </label>
            
            <!-- Barra de asignación actual -->
            <div style="margin-top: 10px; padding-left: 28px;">
                <div style="display: flex; justify-content: space-between; font-size: 0.8em; margin-bottom: 4px;">
                    <span style="color: #6b7280;">Asignado: ${percentAllocatedDisplay}%</span>
                    <span style="color: ${availabilityColor}; font-weight: 600;">Disponible: ${percentAvailableDisplay}%</span>
                </div>
                <div style="height: 8px; background: #e5e7eb; border-radius: 4px; overflow: hidden;">
                    ${allocation.toExpenses > 0 ? `<div style="height: 100%; width: ${allocation.toExpenses * 100}%; background: #ef4444; float: left;" title="Gastos: ${(allocation.toExpenses * 100).toFixed(0)}%"></div>` : ''}
                    ${allocation.toPlans > 0 ? `<div style="height: 100%; width: ${allocation.toPlans * 100}%; background: #3b82f6; float: left;" title="Planes: ${(allocation.toPlans * 100).toFixed(0)}%"></div>` : ''}
                    ${allocation.toSavings > 0 ? `<div style="height: 100%; width: ${allocation.toSavings * 100}%; background: #22c55e; float: left;" title="Ahorros: ${(allocation.toSavings * 100).toFixed(0)}%"></div>` : ''}
                </div>
                <div style="display: flex; gap: 12px; font-size: 0.7em; margin-top: 4px; color: #9ca3af;">
                    ${allocation.toExpenses > 0 ? `<span>🔴 Gastos ${(allocation.toExpenses * 100).toFixed(0)}%</span>` : ''}
                    ${allocation.toPlans > 0 ? `<span>🔵 Planes ${(allocation.toPlans * 100).toFixed(0)}%</span>` : ''}
                    ${allocation.toSavings > 0 ? `<span>🟢 Ahorros ${(allocation.toSavings * 100).toFixed(0)}%</span>` : ''}
                </div>
            </div>
            
            <!-- Input de asignación (oculto inicialmente) -->
            <div class="allocation-input" style="margin-top: 12px; display: none; padding: 12px; background: ${availabilityBg}; border-radius: 6px; margin-left: 28px;">
                <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap;">
                    <label style="font-size: 0.9em; color: #374151; font-weight: 500;">
                        Asignar a esta meta:
                    </label>
                    <div style="display: flex; align-items: center; gap: 5px;">
                        <input 
                            type="range" 
                            class="income-allocation-slider" 
                            data-income-id="${income.id}"
                            min="1" 
                            max="${percentAvailableDisplay}" 
                            value="${percentAvailableDisplay}"
                            style="width: 120px; accent-color: ${availabilityColor};"
                        />
                        <input 
                            type="number" 
                            class="income-allocation-percent" 
                            data-income-id="${income.id}"
                            min="1" 
                            max="${percentAvailableDisplay}" 
                            value="${percentAvailableDisplay}"
                            style="width: 60px; padding: 4px 8px; border: 1px solid #d1d5db; border-radius: 4px; text-align: center;"
                        />
                        <span style="color: #6b7280;">%</span>
                    </div>
                </div>
                <div style="margin-top: 8px; display: flex; justify-content: space-between; align-items: center;">
                    <span style="font-size: 0.85em; color: #374151;">
                        Monto por pago: <strong style="color: ${availabilityColor};">$<span class="calculated-amount">${amountAvailable.toFixed(2)}</span></strong>
                    </span>
                    <span style="font-size: 0.75em; color: #9ca3af;">
                        Máx disponible: ${percentAvailableDisplay}% ($${amountAvailable.toFixed(2)})
                    </span>
                </div>
            </div>
            
            ${isFullyAllocated ? `
                <div style="margin-top: 8px; padding: 8px 12px; background: #fef2f2; border-radius: 6px; margin-left: 28px; font-size: 0.85em; color: #991b1b;">
                    ⚠️ Este ingreso está completamente asignado a otros compromisos
                </div>
            ` : ''}
        </div>
        `;
    }).join('');
    
    const { value: formValues } = await Swal.fire({
        title: '💰 Nueva Planeación - Paso 2 de 3',
        html: `
            <div style="text-align: left; padding: 10px;">
                <p style="color: #6b7280; margin-bottom: 15px;">
                    <strong style="color: #1f2937;">Meta:</strong> ${step1Data.title} · <strong>$${step1Data.target_amount.toLocaleString('es-MX')}</strong>
                </p>
                
                <div style="background: #eff6ff; padding: 10px 12px; border-radius: 8px; margin-bottom: 15px; font-size: 0.85em;">
                    <strong style="color: #1e40af;">ℹ️ Tip:</strong> 
                    <span style="color: #3b82f6;">La barra muestra cómo está distribuido cada ingreso. Solo puedes asignar el porcentaje disponible (sin color).</span>
                </div>
                
                <p style="color: #374151; margin-bottom: 15px; font-weight: 600;">
                    ¿Con qué ingresos quieres financiar esta planeación?
                </p>
                
                <div style="max-height: 400px; overflow-y: auto; margin-bottom: 15px;">
                    ${incomeOptionsHTML}
                </div>
                
                <div id="summary-section" style="display: none; background: #dbeafe; padding: 15px; border-radius: 8px; margin-top: 15px;">
                    <div style="font-weight: 600; color: #1e40af; margin-bottom: 8px;">
                        📊 Resumen de asignación:
                    </div>
                    <div id="summary-content" style="font-size: 0.9em; color: #1e3a8a;">
                    </div>
                </div>
            </div>
        `,
        showCancelButton: true,
        showDenyButton: true,
        confirmButtonText: 'Siguiente →',
        denyButtonText: '← Atrás',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#3b82f6',
        denyButtonColor: '#6b7280',
        width: '700px',
        didOpen: () => {
            const checkboxes = document.querySelectorAll('.income-source-checkbox');
            const summarySection = document.getElementById('summary-section');
            const summaryContent = document.getElementById('summary-content');
            
            function updateSummary() {
                const checked = document.querySelectorAll('.income-source-checkbox:checked');
                if (checked.length === 0) {
                    summarySection.style.display = 'none';
                    return;
                }
                
                summarySection.style.display = 'block';
                let totalPerMonth = 0;
                const items = [];
                
                checked.forEach(cb => {
                    const incomeId = cb.dataset.incomeId;
                    const amount = parseFloat(cb.dataset.incomeAmount);
                    const frequency = cb.dataset.incomeFrequency;
                    const interval = parseInt(cb.dataset.incomeInterval) || 1;
                    const percentInput = document.querySelector(`.income-allocation-percent[data-income-id="${incomeId}"]`);
                    const percent = parseFloat(percentInput.value) || 0;
                    const allocated = (amount * percent / 100);
                    
                    // Convertir a mensual aproximado
                    let monthlyAmount = allocated;
                    if (frequency === 'weekly') {
                        monthlyAmount = (allocated / interval) * 4.33;
                    } else if (frequency === 'biweekly') {
                        monthlyAmount = (allocated / interval) * 2.17;
                    } else if (frequency === 'yearly') {
                        monthlyAmount = allocated / 12 / interval;
                    } else if (frequency === 'monthly') {
                        monthlyAmount = allocated / interval;
                    }
                    
                    totalPerMonth += monthlyAmount;
                    
                    const option = document.querySelector(`.income-option[data-income-id="${incomeId}"]`);
                    const name = option.querySelector('label div div').textContent;
                    items.push(`• ${name}: ${percent}% = $${allocated.toFixed(2)}/pago (~$${monthlyAmount.toFixed(2)}/mes)`);
                });
                
                const monthsToGoal = totalPerMonth > 0 ? Math.ceil(step1Data.target_amount / totalPerMonth) : 0;
                
                summaryContent.innerHTML = `
                    ${items.join('<br>')}
                    <div style="margin-top: 10px; padding-top: 10px; border-top: 1px solid #93c5fd;">
                        <div style="display: flex; justify-content: space-between;">
                            <span>Total aprox. mensual:</span>
                            <strong>$${totalPerMonth.toFixed(2)}</strong>
                        </div>
                        ${monthsToGoal > 0 ? `
                            <div style="display: flex; justify-content: space-between; margin-top: 4px; color: #1e40af;">
                                <span>Tiempo estimado:</span>
                                <strong>~${monthsToGoal} meses</strong>
                            </div>
                        ` : ''}
                    </div>
                `;
            }
            
            checkboxes.forEach(checkbox => {
                const incomeId = checkbox.dataset.incomeId;
                const percentInput = document.querySelector(`.income-allocation-percent[data-income-id="${incomeId}"]`);
                const sliderInput = document.querySelector(`.income-allocation-slider[data-income-id="${incomeId}"]`);
                const amountSpan = percentInput?.closest('.allocation-input')?.querySelector('.calculated-amount');
                const baseAmount = parseFloat(checkbox.dataset.incomeAmount);
                const maxPercent = parseFloat(checkbox.dataset.maxPercent) || 100;
                
                checkbox.addEventListener('change', (e) => {
                    const container = e.target.closest('.income-option');
                    const allocationInput = container.querySelector('.allocation-input');
                    
                    if (e.target.checked) {
                        allocationInput.style.display = 'block';
                        container.style.borderColor = '#3b82f6';
                        container.style.backgroundColor = '#eff6ff';
                    } else {
                        allocationInput.style.display = 'none';
                        container.style.borderColor = 'transparent';
                        container.style.backgroundColor = '#f9fafb';
                    }
                    
                    updateSummary();
                });
                
                // Sincronizar slider y input numérico
                if (sliderInput && percentInput) {
                    sliderInput.addEventListener('input', () => {
                        const val = sliderInput.value;
                        percentInput.value = val;
                        const calculated = (baseAmount * val / 100).toFixed(2);
                        if (amountSpan) amountSpan.textContent = calculated;
                        updateSummary();
                    });
                    
                    percentInput.addEventListener('input', () => {
                        let val = parseFloat(percentInput.value) || 0;
                        // Validar que no exceda el máximo disponible
                        if (val > maxPercent) {
                            val = maxPercent;
                            percentInput.value = val;
                        }
                        sliderInput.value = val;
                        const calculated = (baseAmount * val / 100).toFixed(2);
                        if (amountSpan) amountSpan.textContent = calculated;
                        updateSummary();
                    });
                }
                
                // Calcular valor inicial
                if (percentInput && amountSpan) {
                    const percent = parseFloat(percentInput.value) || maxPercent;
                    const calculated = (baseAmount * percent / 100).toFixed(2);
                    amountSpan.textContent = calculated;
                }
            });
        },
        preConfirm: () => {
            const income_sources = [];
            const checkedBoxes = document.querySelectorAll('.income-source-checkbox:checked');
            
            checkedBoxes.forEach(checkbox => {
                const incomeId = checkbox.dataset.incomeId;
                const incomeName = checkbox.dataset.incomeName;
                const amount = parseFloat(checkbox.dataset.incomeAmount);
                const frequency = checkbox.dataset.incomeFrequency;
                const interval = parseInt(checkbox.dataset.incomeInterval) || 1;
                const maxPercent = parseFloat(checkbox.dataset.maxPercent) || 100;
                const percentInput = document.querySelector(`.income-allocation-percent[data-income-id="${incomeId}"]`);
                let percentage = parseFloat(percentInput.value) || maxPercent;
                
                // Validar que no exceda el máximo
                if (percentage > maxPercent) {
                    percentage = maxPercent;
                }
                
                income_sources.push({
                    income_pattern_id: incomeId,
                    allocation_type: 'percent',
                    allocation_value: percentage / 100,
                    _metadata: {
                        name: incomeName,
                        base_amount: amount,
                        frequency,
                        interval
                    }
                });
            });
            
            return {
                ...step1Data,
                income_sources
            };
        }
    });
    
    // Si presionó "Atrás"
    if (formValues === undefined && !Swal.getHtmlContainer()) {
        return await showPlanStep1(step1Data.target_date);
    }
    
    return formValues;
}

/**
 * PASO 3: Cálculo de viabilidad y confirmación
 */
async function showPlanStep3(step2Data) {
    // Mostrar loading mientras se calcula
    Swal.fire({
        title: 'Analizando viabilidad...',
        html: 'Calculando gastos y ahorros vinculados...',
        allowOutsideClick: false,
        didOpen: () => Swal.showLoading()
    });
    
    // Calcular viabilidad (ahora es async)
    const calculation = await calculatePlanViability(step2Data);
    
    const isViable = calculation.canAchieveByRequestedDate;
    const statusColor = isViable ? '#10b981' : '#f59e0b';
    const statusIcon = isViable ? '✅' : '⚠️';
    
    // Generar HTML del desglose de ingresos si hay análisis
    let incomeBreakdownHTML = '';
    if (calculation.analysis && calculation.analysis.incomeBreakdown && calculation.analysis.incomeBreakdown.length > 0) {
        incomeBreakdownHTML = `
            <div style="margin-top: 15px; padding: 12px; background: #f9fafb; border-radius: 8px;">
                <h4 style="margin: 0 0 10px 0; font-size: 0.95em; color: #374151;">📊 Desglose por ingreso:</h4>
                ${calculation.analysis.incomeBreakdown.map(inc => `
                    <div style="padding: 10px; background: white; border-radius: 6px; margin-bottom: 8px; border-left: 3px solid #3b82f6;">
                        <div style="font-weight: 600; color: #1f2937; margin-bottom: 5px;">
                            ${inc.name} 
                            <span style="font-weight: normal; font-size: 0.8em; color: #6b7280;">
                                (${inc.frequency}, interval: ${inc.interval}, factor: ${inc.monthlyFactor.toFixed(2)})
                            </span>
                        </div>
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 5px; font-size: 0.85em;">
                            <div style="color: #059669;">Bruto mensual: $${inc.grossMonthly.toFixed(2)}</div>
                            <div style="color: #dc2626;">Gastos vinculados: -$${inc.expensesMonthly.toFixed(2)}</div>
                            <div style="color: #f59e0b;">Ahorros vinculados: -$${inc.savingsMonthly.toFixed(2)}</div>
                            <div style="color: #2563eb; font-weight: 600;">Neto disponible: $${inc.netMonthly.toFixed(2)}</div>
                        </div>
                        
                        ${inc.expenseDetails && inc.expenseDetails.length > 0 ? `
                            <div style="margin-top: 8px; padding: 8px; background: #fef2f2; border-radius: 4px; font-size: 0.8em;">
                                <strong style="color: #991b1b;">Gastos cubiertos por este ingreso:</strong>
                                ${inc.expenseDetails.map(e => `
                                    <div style="margin-top: 4px; padding-left: 10px; color: #7f1d1d;">
                                        • ${e.name}: $${e.baseAmount.toFixed(2)}/${e.expenseFrequency} × ${e.coveragePercent.toFixed(0)}% = <strong>$${e.calculatedMonthly.toFixed(2)}/mes</strong>
                                    </div>
                                `).join('')}
                            </div>
                        ` : ''}
                        
                        <div style="margin-top: 8px; padding-top: 8px; border-top: 1px dashed #e5e7eb; font-size: 0.85em;">
                            <span style="color: #6b7280;">Asignado (${inc.allocationPercent.toFixed(0)}% del neto):</span>
                            <strong style="color: #10b981;"> $${inc.contributionToplan.toFixed(2)}/mes</strong>
                        </div>
                    </div>
                `).join('')}
                
                <div style="margin-top: 10px; padding: 10px; background: #eff6ff; border-radius: 6px;">
                    <div style="display: flex; justify-content: space-between; font-size: 0.9em;">
                        <span>Total bruto mensual:</span>
                        <strong style="color: #059669;">$${calculation.analysis.totalMonthlyGross.toFixed(2)}</strong>
                    </div>
                    <div style="display: flex; justify-content: space-between; font-size: 0.9em; margin-top: 3px;">
                        <span>Total neto disponible:</span>
                        <strong style="color: #2563eb;">$${calculation.analysis.totalMonthlyNet.toFixed(2)}</strong>
                    </div>
                    <div style="display: flex; justify-content: space-between; font-size: 0.95em; margin-top: 8px; padding-top: 8px; border-top: 1px solid #bfdbfe;">
                        <span>Aporte mensual a esta meta:</span>
                        <strong style="color: #10b981;">$${calculation.monthlyContribution.toFixed(2)}</strong>
                    </div>
                </div>
            </div>
        `;
    }
    
    // Info del balance actual
    const balanceInfoHTML = calculation.analysis ? `
        <div style="background: #f0fdf4; padding: 12px; border-radius: 8px; margin-bottom: 15px;">
            <div style="display: flex; justify-content: space-between; align-items: center;">
                <span style="color: #166534;">💰 Balance actual:</span>
                <strong style="color: #15803d;">$${calculation.analysis.currentBalance.toFixed(2)}</strong>
            </div>
            ${calculation.analysis.canPayWithBalance ? `
                <div style="margin-top: 8px; padding: 8px; background: white; border-radius: 4px; color: #065f46; font-size: 0.9em;">
                    ✨ Tu balance actual cubre completamente esta meta
                </div>
            ` : ''}
        </div>
    ` : '';
    
    const { value: confirmed } = await Swal.fire({
        title: '📊 Nueva Planeación - Paso 3 de 3',
        html: `
            <div style="text-align: left; padding: 10px; max-height: 70vh; overflow-y: auto;">
                <div style="background: #f3f4f6; padding: 15px; border-radius: 8px; margin-bottom: 20px;">
                    <h3 style="margin: 0 0 10px 0; color: #1f2937;">📋 Resumen de tu planeación:</h3>
                    <div style="color: #374151; line-height: 1.8;">
                        <div><strong>Meta:</strong> ${step2Data.name || step2Data.title}</div>
                        <div><strong>Monto objetivo:</strong> $${step2Data.target_amount.toFixed(2)}</div>
                        ${step2Data.target_date ? `<div><strong>Fecha objetivo:</strong> ${new Date(step2Data.target_date).toLocaleDateString('es-ES')}</div>` : ''}
                        <div><strong>Prioridad:</strong> ${'⭐'.repeat(step2Data.priority)}</div>
                        ${step2Data.category ? `<div><strong>Categoría:</strong> ${step2Data.category}</div>` : ''}
                    </div>
                </div>
                
                ${balanceInfoHTML}
                
                ${step2Data.income_sources && step2Data.income_sources.length > 0 ? `
                    <div style="background: ${isViable ? '#d1fae5' : '#fef3c7'}; padding: 15px; border-radius: 8px; margin-bottom: 20px; border-left: 4px solid ${statusColor};">
                        <h3 style="margin: 0 0 10px 0; color: #1f2937;">
                            ${statusIcon} Análisis de viabilidad:
                        </h3>
                        <div style="color: #374151; line-height: 1.8;">
                            <div><strong>Ingresos asignados:</strong> ${step2Data.income_sources.length}</div>
                            <div><strong>Aporte mensual estimado:</strong> $${calculation.monthlyContribution.toFixed(2)}</div>
                            <div><strong>Tiempo estimado:</strong> ${calculation.monthsNeeded} meses</div>
                            <div><strong>Fecha sugerida:</strong> ${calculation.suggestedDate.toLocaleDateString('es-ES')}</div>
                        </div>
                        
                        ${incomeBreakdownHTML}
                        
                        ${!isViable ? `
                            <div style="margin-top: 15px; padding: 10px; background: white; border-radius: 6px; color: #92400e;">
                                <strong>⚠️ Nota:</strong> Con los ingresos seleccionados, la meta se alcanzaría aproximadamente el 
                                <strong>${calculation.suggestedDate.toLocaleDateString('es-ES')}</strong>.
                                ${step2Data.target_date ? `
                                    Esto es después de tu fecha objetivo (${new Date(step2Data.target_date).toLocaleDateString('es-ES')}).
                                    <br><br>
                                    <strong>Opciones:</strong>
                                    <ul style="margin: 8px 0; padding-left: 20px; text-align: left;">
                                        <li>Aumentar el porcentaje de ingresos asignados</li>
                                        <li>Añadir más fuentes de ingreso</li>
                                        <li>Ajustar la fecha objetivo</li>
                                        <li>Reducir el monto objetivo</li>
                                    </ul>
                                ` : ''}
                            </div>
                        ` : `
                            <div style="margin-top: 15px; padding: 10px; background: white; border-radius: 6px; color: #065f46;">
                                <strong>✅ ¡Excelente!</strong> Con tus ingresos asignados, puedes alcanzar esta meta
                                ${step2Data.target_date ? 'antes de la fecha objetivo' : 'en el tiempo estimado'}.
                            </div>
                        `}
                    </div>
                ` : `
                    <div style="background: #f3f4f6; padding: 15px; border-radius: 8px; margin-bottom: 20px;">
                        <p style="color: #6b7280; margin: 0;">
                            ℹ️ No se asignaron ingresos. La fecha sugerida no se calculará automáticamente.
                        </p>
                    </div>
                `}
                
                <div style="text-align: center; margin-top: 20px;">
                    <p style="color: #6b7280; font-size: 0.9em;">
                        ${step2Data.auto_create_reminder ? '🔔 Se crearán recordatorios automáticos' : ''}
                    </p>
                </div>
            </div>
        `,
        showCancelButton: true,
        showDenyButton: true,
        confirmButtonText: isViable ? '✅ Crear Planeación' : '📝 Crear con mi fecha',
        denyButtonText: '← Ajustar',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: isViable ? '#10b981' : '#f59e0b',
        denyButtonColor: '#6b7280',
        width: '750px',
        footer: !isViable && step2Data.income_sources?.length > 0 ? `
            <button id="use-suggested-date-btn" class="swal2-styled" style="background-color: #10b981; margin-top: 10px;">
                📅 Usar fecha sugerida (${calculation.suggestedDate.toLocaleDateString('es-ES')})
            </button>
        ` : '',
        didOpen: () => {
            const suggestedBtn = document.getElementById('use-suggested-date-btn');
            if (suggestedBtn) {
                suggestedBtn.addEventListener('click', () => {
                    // Guardar la fecha sugerida y resolver con valor especial
                    Swal.getPopup().dataset.useSuggestedDate = 'true';
                    Swal.clickConfirm();
                });
            }
        },
        preConfirm: () => {
            // Verificar si se eligió usar fecha sugerida
            const popup = Swal.getPopup();
            if (popup && popup.dataset.useSuggestedDate === 'true') {
                return { useSuggestedDate: true };
            }
            return { useSuggestedDate: false };
        }
    });
    
    // Verificar si eligió usar la fecha sugerida
    if (confirmed && confirmed.useSuggestedDate) {
        const suggestedDateISO = calculation.suggestedDate.toISOString().split('T')[0];
        return {
            ...step2Data,
            target_date: suggestedDateISO,
            status: 'active'
        };
    }
    
    if (confirmed === false) {
        // Presionó "Ajustar" - volver al paso 2
        return await showPlanStep2(step2Data);
    }
    
    if (!confirmed) {
        return null; // Canceló
    }
    
    // Usar la fecha original del usuario
    return {
        ...step2Data,
        status: 'active'
    };
}

/**
 * Calcula la viabilidad de un plan basado en los ingresos asignados
 * Considera: ingreso bruto, gastos/ahorros vinculados, y balance actual
 */
async function calculatePlanViability(planData) {
    if (!planData.income_sources || planData.income_sources.length === 0) {
        // Sin ingresos asignados, verificar si el balance actual puede cubrir la meta
        const balanceSummary = await getConfirmedBalanceSummary();
        const currentBalance = balanceSummary.balance || 0;
        
        return {
            canAchieveByRequestedDate: false,
            monthlyContribution: 0,
            monthsNeeded: 0,
            suggestedDate: new Date(),
            message: 'Sin ingresos asignados',
            analysis: {
                currentBalance,
                canPayWithBalance: currentBalance >= planData.target_amount,
                remainingAfterBalance: Math.max(0, planData.target_amount - currentBalance)
            }
        };
    }
    
    // Importar funciones necesarias
    const { getSavingsLinkedToIncome, getExpensesLinkedToIncome } = await import('./savings.js');
    
    // Obtener el balance actual
    const balanceSummary = await getConfirmedBalanceSummary();
    const currentBalance = Math.max(0, balanceSummary.balance || 0);
    
    // Calcular aporte mensual total considerando gastos/ahorros vinculados
    let totalMonthlyGross = 0;
    let totalMonthlyNet = 0;
    const incomeAnalysis = [];
    
    for (const source of planData.income_sources) {
        const { base_amount, frequency, interval, name } = source._metadata;
        const incomePatternId = source.income_pattern_id;
        
        // Obtener gastos y ahorros vinculados a este ingreso
        const [linkedExpenses, linkedSavings] = await Promise.all([
            getExpensesLinkedToIncome(incomePatternId),
            getSavingsLinkedToIncome(incomePatternId)
        ]);
        
        // Factor de conversión a mensual según frecuencia del INGRESO
        let monthlyFactor = 1;
        if (frequency === 'weekly') {
            monthlyFactor = 4.33 / interval; // ~4.33 semanas por mes
        } else if (frequency === 'biweekly') {
            monthlyFactor = 2.17 / interval; // ~2.17 quincenas por mes
        } else if (frequency === 'yearly') {
            monthlyFactor = 1 / 12 / interval;
        } else if (frequency === 'monthly') {
            monthlyFactor = 1 / interval;
        }
        
        // Calcular el monto bruto mensual del ingreso
        const monthlyGross = base_amount * monthlyFactor;
        
        // Calcular gastos vinculados
        // IMPORTANTE: El porcentaje de allocation_value representa qué porcentaje del GASTO
        // es cubierto por este ingreso, NO un porcentaje del ingreso.
        // Ejemplo: Si un gasto de $2000 tiene allocation_value=1 (100%), 
        // significa que este ingreso cubre los $2000 completos del gasto.
        // Si allocation_value=0.5 (50%), cubre $1000 del gasto.
        let monthlyExpensesTotal = 0;
        const expenseDetails = [];
        
        for (const expense of linkedExpenses) {
            const allocType = expense.allocation_from_income?.type;
            const allocValue = parseFloat(expense.allocation_from_income?.value) || 0;
            const expenseBaseAmount = parseFloat(expense.base_amount) || 0;
            const expenseFrequency = expense.frequency || 'monthly';
            const expenseInterval = expense.interval || 1;
            
            // Factor de mensualización del GASTO (no del ingreso)
            let expenseMonthlyFactor = 1;
            if (expenseFrequency === 'weekly') {
                expenseMonthlyFactor = 4.33 / expenseInterval;
            } else if (expenseFrequency === 'biweekly') {
                expenseMonthlyFactor = 2.17 / expenseInterval;
            } else if (expenseFrequency === 'yearly') {
                expenseMonthlyFactor = 1 / 12 / expenseInterval;
            } else if (expenseFrequency === 'monthly') {
                expenseMonthlyFactor = 1 / expenseInterval;
            }
            
            let monthlyExpenseAmount = 0;
            
            if (allocType === 'percent') {
                // El porcentaje indica qué parte del GASTO es cubierto por este ingreso
                // Gasto mensual = base_amount_gasto × porcentaje_cobertura × factor_mensual_gasto
                monthlyExpenseAmount = expenseBaseAmount * allocValue * expenseMonthlyFactor;
            } else if (allocType === 'fixed') {
                // Monto fijo que se deduce del ingreso para este gasto
                monthlyExpenseAmount = allocValue * expenseMonthlyFactor;
            } else {
                // Sin tipo definido, usar el base_amount del gasto completo
                monthlyExpenseAmount = expenseBaseAmount * expenseMonthlyFactor;
            }
            
            monthlyExpensesTotal += monthlyExpenseAmount;
            expenseDetails.push({
                name: expense.name || 'Sin nombre',
                allocType: allocType || 'default',
                allocValue,
                baseAmount: expenseBaseAmount,
                expenseFrequency,
                coveragePercent: allocValue * 100,
                calculatedMonthly: monthlyExpenseAmount
            });
        }
        
        const monthlyExpenses = monthlyExpensesTotal;
        
        // Calcular ahorros vinculados POR OCURRENCIA del ingreso
        let savingsPerOccurrence = 0;
        for (const savings of linkedSavings) {
            if (savings.allocation_from_income.type === 'percent') {
                savingsPerOccurrence += base_amount * parseFloat(savings.allocation_from_income.value);
            } else if (savings.allocation_from_income.type === 'fixed') {
                savingsPerOccurrence += parseFloat(savings.allocation_from_income.value);
            } else if (savings.allocation_from_income.type === 'remainder') {
                // El sobrante después de gastos
                savingsPerOccurrence += Math.max(0, base_amount - expensesPerOccurrence);
            }
        }
        // Mensualizar los ahorros
        const monthlySavings = savingsPerOccurrence * monthlyFactor;
        
        // Calcular el monto NETO disponible después de gastos y otros ahorros
        const monthlyNet = monthlyGross - monthlyExpenses - monthlySavings;
        
        // Lo que este ingreso aporta al plan (porcentaje del NETO disponible)
        const allocatedFromNet = Math.max(0, monthlyNet) * source.allocation_value;
        
        totalMonthlyGross += monthlyGross;
        totalMonthlyNet += Math.max(0, monthlyNet);
        
        incomeAnalysis.push({
            name: name || source._metadata.name || 'Ingreso sin nombre',
            grossMonthly: monthlyGross,
            expensesMonthly: monthlyExpenses,
            savingsMonthly: monthlySavings,
            netMonthly: monthlyNet,
            allocationPercent: source.allocation_value * 100,
            contributionToplan: allocatedFromNet,
            // Debug info
            frequency,
            interval,
            monthlyFactor,
            expenseDetails,
            totalLinkedExpenses: linkedExpenses.length
        });
    }
    
    // Calcular aporte mensual total al plan
    let monthlyContribution = 0;
    for (const analysis of incomeAnalysis) {
        monthlyContribution += analysis.contributionToplan;
    }
    
    // Considerar el balance actual para reducir el monto objetivo
    const effectiveTarget = Math.max(0, planData.target_amount - (planData.use_current_balance ? currentBalance : 0));
    
    // Calcular meses necesarios
    const monthsNeeded = monthlyContribution > 0 
        ? Math.ceil(effectiveTarget / monthlyContribution)
        : 999;
    
    // Calcular fecha sugerida
    const suggestedDate = new Date();
    suggestedDate.setMonth(suggestedDate.getMonth() + monthsNeeded);
    
    // V2: Verificar si es viable para la fecha objetivo
    const canAchieveByRequestedDate = planData.target_date
        ? suggestedDate <= new Date(planData.target_date)
        : true;
    
    return {
        canAchieveByRequestedDate,
        monthlyContribution,
        monthsNeeded,
        suggestedDate,
        analysis: {
            currentBalance,
            totalMonthlyGross,
            totalMonthlyNet,
            incomeBreakdown: incomeAnalysis,
            effectiveTarget,
            canPayWithBalance: currentBalance >= planData.target_amount
        }
    };
}

// ============================================================================
// MODAL: VER/EDITAR PATRÓN
// ============================================================================

/**
 * Muestra detalles y permite editar un patrón
 */
export async function showPatternDetails(patternId, patternType, onUpdated) {
    try {
        const isIncome = patternType === 'income';
        const pattern = isIncome 
            ? await getIncomePatternById(patternId)
            : await getExpensePatternById(patternId);
        
        if (!pattern) {
            throw new Error('Patrón no encontrado');
        }

        const typeLabel = isIncome ? '💰 Patrón de Ingreso' : '💸 Patrón de Gasto';
        const typeColor = isIncome ? '#10b981' : '#ef4444';

        const frequencyLabels = {
            'daily': 'Diario',
            'weekly': 'Semanal',
            'biweekly': 'Quincenal',
            'monthly': 'Mensual',
            'yearly': 'Anual'
        };

        const result = await Swal.fire({
            title: `${typeLabel}: ${pattern.name}`,
            html: `
                <div style="text-align: left; padding: 10px;">
                    ${pattern.description ? `<p style="color: #666; margin-bottom: 15px;">${pattern.description}</p>` : ''}
                    
                    <div style="background: #f3f4f6; padding: 15px; border-radius: 8px; margin-bottom: 15px;">
                        <div style="margin-bottom: 10px;">
                            <strong>Monto base:</strong> 
                            <span style="color: ${typeColor}; font-size: 1.2em;">$${parseFloat(pattern.base_amount).toLocaleString('es-MX', { minimumFractionDigits: 2 })}</span>
                        </div>
                        
                        <div style="margin-bottom: 10px;">
                            <strong>Frecuencia:</strong> ${frequencyLabels[pattern.frequency] || pattern.frequency}
                            ${pattern.interval > 1 ? ` (cada ${pattern.interval})` : ''}
                        </div>
                        
                        <div style="margin-bottom: 10px;">
                            <strong>Fecha inicio:</strong> ${new Date(pattern.start_date).toLocaleDateString('es-MX')}
                        </div>
                        
                        ${pattern.end_date ? `
                            <div style="margin-bottom: 10px;">
                                <strong>Fecha límite:</strong> ${new Date(pattern.end_date).toLocaleDateString('es-MX')}
                            </div>
                        ` : '<div style="margin-bottom: 10px;"><strong>Fecha límite:</strong> Indefinido</div>'}
                        
                        ${pattern.category ? `
                            <div style="margin-bottom: 10px;">
                                <strong>Categoría:</strong> ${pattern.category}
                            </div>
                        ` : ''}
                        
                        <div style="margin-bottom: 10px;">
                            <strong>Estado:</strong> 
                            <span style="color: ${pattern.active ? '#10b981' : '#6b7280'};">
                                ${pattern.active ? '✅ Activo' : '❌ Inactivo'}
                            </span>
                        </div>
                    </div>
                    
                    ${!isIncome ? `
                    <div style="margin-top: 15px;">
                        <button 
                            type="button" 
                            id="btn-manage-income-sources"
                            style="width: 100%; padding: 10px; background: #3b82f6; color: white; border: none; border-radius: 8px; cursor: pointer; font-weight: 600;"
                        >
                            💵 Gestionar Fuentes de Ingreso
                        </button>
                    </div>
                    ` : ''}
                    
                    <div style="margin-top: 15px; padding-top: 15px; border-top: 1px solid #e5e7eb;">
                        <button 
                            type="button" 
                            id="btn-delete-pattern"
                            style="width: 100%; padding: 10px; background: #fee2e2; color: #dc2626; border: 1px solid #fecaca; border-radius: 8px; cursor: pointer; font-weight: 600;"
                        >
                            🗑️ Eliminar Patrón
                        </button>
                    </div>
                </div>
            `,
            showCancelButton: true,
            confirmButtonText: '✏️ Editar',
            cancelButtonText: 'Cerrar',
            confirmButtonColor: '#3b82f6',
            cancelButtonColor: '#6b7280',
            didOpen: () => {
                // Botón de gestionar fuentes de ingreso (solo para gastos)
                const btnIncomeSources = document.getElementById('btn-manage-income-sources');
                if (btnIncomeSources) {
                    btnIncomeSources.addEventListener('click', async () => {
                        Swal.close();
                        await showExpensePatternIncomeSourcesDialog(patternId, onUpdated);
                    });
                }
                
                // Botón de eliminar
                const btnDelete = document.getElementById('btn-delete-pattern');
                if (btnDelete) {
                    btnDelete.addEventListener('click', async () => {
                        const confirmDelete = await Swal.fire({
                            title: '⚠️ ¿Eliminar patrón?',
                            html: `
                                <p>Estás a punto de eliminar <strong>${pattern.name}</strong>.</p>
                                <p style="color: #6b7280; font-size: 0.9em; margin-top: 10px;">
                                    Esta acción desactivará el patrón y dejará de generar eventos proyectados.
                                </p>
                                <div style="margin-top: 15px; padding: 10px; background: #fef2f2; border-radius: 8px;">
                                    <label style="display: flex; align-items: center; cursor: pointer;">
                                        <input type="checkbox" id="hard-delete-check" style="margin-right: 8px;">
                                        <span style="color: #991b1b; font-size: 0.85em;">
                                            Eliminar permanentemente (no se puede deshacer)
                                        </span>
                                    </label>
                                </div>
                            `,
                            icon: 'warning',
                            showCancelButton: true,
                            confirmButtonText: 'Sí, eliminar',
                            cancelButtonText: 'Cancelar',
                            confirmButtonColor: '#dc2626',
                            cancelButtonColor: '#6b7280',
                            preConfirm: () => {
                                const hardDelete = document.getElementById('hard-delete-check')?.checked || false;
                                return { hardDelete };
                            }
                        });
                        
                        if (confirmDelete.isConfirmed) {
                            try {
                                const { deleteIncomePattern, deleteExpensePattern } = await import('./patterns.js');
                                
                                if (isIncome) {
                                    await deleteIncomePattern(patternId, confirmDelete.value.hardDelete);
                                } else {
                                    await deleteExpensePattern(patternId, confirmDelete.value.hardDelete);
                                }
                                
                                await Swal.fire({
                                    icon: 'success',
                                    title: 'Patrón eliminado',
                                    text: confirmDelete.value.hardDelete 
                                        ? 'El patrón ha sido eliminado permanentemente' 
                                        : 'El patrón ha sido desactivado',
                                    timer: 2000,
                                    showConfirmButton: false
                                });
                                
                                if (onUpdated) onUpdated();
                            } catch (error) {
                                console.error('Error deleting pattern:', error);
                                await Swal.fire({
                                    icon: 'error',
                                    title: 'Error',
                                    text: 'No se pudo eliminar el patrón: ' + error.message
                                });
                            }
                        }
                    });
                }
            }
        });

        if (result.isConfirmed) {
            await showEditPatternDialog(pattern, patternType, onUpdated);
        }
    } catch (error) {
        console.error('Error showing pattern details:', error);
        await Swal.fire({
            icon: 'error',
            title: 'Error',
            text: error.message || 'No se pudo cargar el patrón'
        });
    }
}

/**
 * Modal para editar un patrón
 */
async function showEditPatternDialog(pattern, patternType, onUpdated) {
    const isIncome = patternType === 'income';
    
    // Para expense patterns, cargar fuentes de ingreso actuales y disponibles
    let currentSources = [];
    let incomePatterns = [];
    if (!isIncome) {
        try {
            currentSources = await getExpensePatternIncomeSources(pattern.id);
            incomePatterns = await getIncomePatterns();
        } catch (e) {
            console.error('Error loading income sources:', e);
        }
    }
    
    // Generar HTML de fuentes actuales
    const currentSourcesHTML = !isIncome ? (() => {
        if (currentSources.length === 0) {
            return `
                <div id="current-sources-section" style="margin-bottom: 15px; padding: 12px; background: #fef3c7; border-radius: 8px; border: 1px solid #fcd34d;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600; color: #92400e;">
                        💰 Sin Fuente de Ingreso Vinculada
                    </label>
                    <small style="color: #92400e;">
                        Puedes vincular este gasto a un ingreso para mejor seguimiento
                    </small>
                </div>
            `;
        }
        
        const sourcesListHTML = currentSources.map(src => {
            const ip = incomePatterns.find(p => p.id === src.income_pattern_id);
            const incomeName = ip?.name || 'Ingreso desconocido';
            const incomeAmount = ip?.base_amount || 0;
            const allocDisplay = src.allocation_type === 'percent' 
                ? `${src.allocation_value}%` 
                : formatCurrency(src.allocation_value);
            return `
                <div style="display: flex; justify-content: space-between; align-items: center; padding: 6px 0; border-bottom: 1px solid #e0f2fe;">
                    <span style="font-weight: 500;">${incomeName}</span>
                    <span style="color: #0369a1; font-weight: 600;">${allocDisplay}</span>
                </div>
            `;
        }).join('');
        
        return `
            <div id="current-sources-section" style="margin-bottom: 15px; padding: 12px; background: #f0f9ff; border-radius: 8px; border: 1px solid #bae6fd;">
                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #0369a1;">
                    💰 Fuentes de Ingreso Vinculadas:
                </label>
                ${sourcesListHTML}
            </div>
        `;
    })() : '';
    
    const { value: formValues } = await Swal.fire({
        title: 'Editar Patrón',
        html: `
            <div style="text-align: left; padding: 10px;">
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">Nombre:</label>
                    <input 
                        id="edit-pattern-name" 
                        type="text" 
                        value="${pattern.name}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">Monto:</label>
                    <input 
                        id="edit-pattern-amount" 
                        type="number" 
                        step="0.01"
                        value="${pattern.base_amount}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                    />
                </div>
                
                ${currentSourcesHTML}

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">Frecuencia:</label>
                    <select id="edit-pattern-frequency" class="swal2-select" style="width: 100%;">
                        <option value="weekly" ${pattern.frequency === 'weekly' ? 'selected' : ''}>Semanal</option>
                        <option value="monthly" ${pattern.frequency === 'monthly' ? 'selected' : ''}>Mensual</option>
                        <option value="yearly" ${pattern.frequency === 'yearly' ? 'selected' : ''}>Anual</option>
                    </select>
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">Intervalo:</label>
                    <input 
                        id="edit-pattern-interval" 
                        type="number" 
                        min="1"
                        value="${pattern.interval}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">Fecha inicio:</label>
                    <input 
                        id="edit-pattern-start-date" 
                        type="date" 
                        value="${pattern.start_date}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">Fecha límite:</label>
                    <input 
                        id="edit-pattern-end-date" 
                        type="date" 
                        value="${pattern.end_date || ''}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                    />
                    <small style="color: #6b7280; font-size: 0.85em;">Dejar vacío para indefinido</small>
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">Categoría:</label>
                    <input 
                        id="edit-pattern-category" 
                        type="text" 
                        value="${pattern.category || ''}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">Descripción:</label>
                    <textarea 
                        id="edit-pattern-description" 
                        class="swal2-textarea" 
                        style="margin: 0; width: 100%; min-height: 60px;"
                    >${pattern.description || ''}</textarea>
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: flex; align-items: center; cursor: pointer;">
                        <input 
                            id="edit-pattern-active" 
                            type="checkbox" 
                            ${pattern.active ? 'checked' : ''}
                            style="margin-right: 8px; width: 20px; height: 20px;"
                        />
                        <span style="font-weight: 600;">Patrón activo</span>
                    </label>
                </div>
                
                ${!isIncome ? `
                <div style="margin-top: 20px; padding-top: 15px; border-top: 1px solid #e5e7eb;">
                    <button 
                        type="button" 
                        id="btn-manage-income-sources"
                        style="width: 100%; padding: 12px; background: #3b82f6; color: white; border: none; border-radius: 8px; cursor: pointer; font-weight: 600; display: flex; align-items: center; justify-content: center; gap: 8px;"
                    >
                        💵 ${currentSources.length > 0 ? 'Modificar' : 'Asignar'} Fuentes de Ingreso
                    </button>
                    <p style="font-size: 0.85em; color: #6b7280; margin-top: 8px; text-align: center;">
                        Asigna de qué ingresos depende este gasto
                    </p>
                </div>
                ` : ''}
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: 'Guardar',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#10b981',
        width: '600px',
        didOpen: () => {
            // Solo para expense patterns: botón de gestionar fuentes de ingreso
            if (!isIncome) {
                const btnManageSources = document.getElementById('btn-manage-income-sources');
                if (btnManageSources) {
                    btnManageSources.addEventListener('click', async () => {
                        Swal.close();
                        await showExpensePatternIncomeSourcesDialog(pattern.id, onUpdated);
                    });
                }
            }
        },
        preConfirm: () => {
            const name = document.getElementById('edit-pattern-name').value.trim();
            const amount = document.getElementById('edit-pattern-amount').value;
            const startDate = document.getElementById('edit-pattern-start-date').value;
            const endDate = document.getElementById('edit-pattern-end-date').value;
            
            if (!name) {
                Swal.showValidationMessage('El nombre es requerido');
                return false;
            }
            if (!amount || parseFloat(amount) <= 0) {
                Swal.showValidationMessage('Ingresa un monto válido');
                return false;
            }
            if (!startDate) {
                Swal.showValidationMessage('La fecha de inicio es requerida');
                return false;
            }
            if (endDate && endDate < startDate) {
                Swal.showValidationMessage('La fecha límite debe ser posterior a la fecha de inicio');
                return false;
            }
            
            return {
                name,
                base_amount: parseFloat(amount),
                frequency: document.getElementById('edit-pattern-frequency').value,
                interval: parseInt(document.getElementById('edit-pattern-interval').value),
                start_date: startDate,
                end_date: endDate || null,
                category: document.getElementById('edit-pattern-category').value.trim() || null,
                description: document.getElementById('edit-pattern-description').value.trim() || null,
                active: document.getElementById('edit-pattern-active').checked
            };
        }
    });

    if (formValues) {
        try {
            if (isIncome) {
                await updateIncomePattern(pattern.id, formValues);
            } else {
                await updateExpensePattern(pattern.id, formValues);
            }
            
            await Swal.fire({
                icon: 'success',
                title: 'Actualizado',
                text: 'Patrón actualizado exitosamente',
                timer: 1500,
                showConfirmButton: false
            });
            
            if (onUpdated) onUpdated();
        } catch (error) {
            console.error('Error updating pattern:', error);
            await Swal.fire({
                icon: 'error',
                title: 'Error',
                text: error.message || 'No se pudo actualizar el patrón'
            });
        }
    }
}

// ============================================================================
// MODAL: GESTIONAR FUENTES DE INGRESO PARA EXPENSE PATTERN
// ============================================================================

/**
 * Modal para asignar/gestionar fuentes de ingreso a un expense_pattern
 */
export async function showExpensePatternIncomeSourcesDialog(expensePatternId, onUpdated) {
    try {
        // Cargar datos del expense pattern con sus sources actuales
        const expensePattern = await getExpensePatternWithSources(expensePatternId);
        const incomePatterns = await getIncomePatterns(true); // Solo activos
        
        if (incomePatterns.length === 0) {
            await Swal.fire({
                icon: 'warning',
                title: 'Sin ingresos disponibles',
                html: `
                    <p>No tienes ingresos recurrentes registrados.</p>
                    <p>Primero debes crear patrones de ingreso para poder asignarlos como fuentes.</p>
                `,
                confirmButtonText: 'Entendido',
                confirmButtonColor: '#f59e0b'
            });
            return;
        }
        
        // Crear mapa de sources actuales
        const currentSourcesMap = {};
        (expensePattern.income_sources || []).forEach(src => {
            currentSourcesMap[src.income_pattern_id] = {
                allocation_type: src.allocation_type,
                allocation_value: src.allocation_value,
                notes: src.notes
            };
        });
        
        // Construir opciones de ingresos
        const incomeOptionsHTML = incomePatterns.map(income => {
            const isSelected = currentSourcesMap[income.id] !== undefined;
            const currentSource = currentSourcesMap[income.id] || {};
            const allocationType = currentSource.allocation_type || 'percent';
            const allocationValue = allocationType === 'percent' 
                ? (currentSource.allocation_value || 0) * 100 
                : currentSource.allocation_value || 0;
            
            const freqLabel = {
                'daily': 'Diario',
                'weekly': 'Semanal',
                'monthly': 'Mensual',
                'yearly': 'Anual'
            }[income.frequency] || income.frequency;
            
            return `
            <div style="margin-bottom: 12px; padding: 12px; background: ${isSelected ? '#dcfce7' : '#f9fafb'}; border-radius: 8px; border: 2px solid ${isSelected ? '#22c55e' : 'transparent'};" class="income-source-option" data-income-id="${income.id}">
                <label style="display: flex; align-items: center; cursor: pointer;">
                    <input 
                        type="checkbox" 
                        class="expense-income-source-checkbox" 
                        data-income-id="${income.id}"
                        data-income-amount="${income.base_amount}"
                        data-income-frequency="${income.frequency}"
                        data-income-interval="${income.interval || 1}"
                        ${isSelected ? 'checked' : ''}
                        style="margin-right: 10px; width: 18px; height: 18px;"
                    />
                    <div style="flex: 1;">
                        <div style="font-weight: 600; color: #1f2937;">${income.name}</div>
                        <div style="font-size: 0.9em; color: #10b981; margin-top: 2px;">
                            💰 $${income.base_amount.toLocaleString()} · ${freqLabel}
                            ${income.interval > 1 ? ` (cada ${income.interval})` : ''}
                        </div>
                    </div>
                </label>
                <div class="allocation-input" style="margin-top: 10px; ${isSelected ? '' : 'display: none;'} padding-left: 28px;">
                    <div style="display: flex; flex-wrap: wrap; align-items: center; gap: 10px;">
                        <select class="allocation-type-select" data-income-id="${income.id}" style="padding: 4px 8px; border: 1px solid #d1d5db; border-radius: 4px;">
                            <option value="percent" ${allocationType === 'percent' ? 'selected' : ''}>Porcentaje</option>
                            <option value="fixed" ${allocationType === 'fixed' ? 'selected' : ''}>Monto fijo</option>
                        </select>
                        <input 
                            type="number" 
                            class="allocation-value-input" 
                            data-income-id="${income.id}"
                            min="0.01" 
                            step="0.01"
                            value="${allocationValue}"
                            style="width: 80px; padding: 4px 8px; border: 1px solid #d1d5db; border-radius: 4px;"
                        />
                        <span class="allocation-unit" data-income-id="${income.id}" style="color: #6b7280;">
                            ${allocationType === 'percent' ? '%' : '$ fijo'}
                        </span>
                    </div>
                    <div style="margin-top: 6px; font-size: 0.85em; color: #6b7280;">
                        = $<span class="calculated-contribution" data-income-id="${income.id}">0</span> por ocurrencia
                    </div>
                    <div style="margin-top: 8px;">
                        <input 
                            type="text" 
                            class="allocation-notes" 
                            data-income-id="${income.id}"
                            placeholder="Notas (opcional)..."
                            value="${currentSource.notes || ''}"
                            style="width: 100%; padding: 4px 8px; border: 1px solid #d1d5db; border-radius: 4px; font-size: 0.9em;"
                        />
                    </div>
                </div>
            </div>
            `;
        }).join('');
        
        // Calcular cobertura actual
        const coverage = await calculateExpensePatternCoverage(expensePatternId);
        
        const { value: formValues } = await Swal.fire({
            title: '💵 Fuentes de Ingreso',
            html: `
                <div style="text-align: left; padding: 10px;">
                    <div style="background: #fef3c7; padding: 12px; border-radius: 8px; margin-bottom: 15px;">
                        <div style="font-weight: 600; color: #92400e; margin-bottom: 5px;">
                            📋 Gasto: ${expensePattern.name}
                        </div>
                        <div style="font-size: 0.9em; color: #b45309;">
                            Monto: $${parseFloat(expensePattern.base_amount).toLocaleString()} · 
                            ${{'daily': 'Diario', 'weekly': 'Semanal', 'monthly': 'Mensual', 'yearly': 'Anual'}[expensePattern.frequency] || expensePattern.frequency}
                        </div>
                    </div>
                    
                    <p style="color: #374151; margin-bottom: 15px; font-weight: 600;">
                        Selecciona los ingresos que cubren este gasto:
                    </p>
                    
                    <div style="max-height: 350px; overflow-y: auto; margin-bottom: 15px;">
                        ${incomeOptionsHTML}
                    </div>
                    
                    <div id="coverage-summary" style="background: #dbeafe; padding: 15px; border-radius: 8px; margin-top: 15px;">
                        <div style="font-weight: 600; color: #1e40af; margin-bottom: 8px;">
                            📊 Cobertura del gasto:
                        </div>
                        <div id="coverage-content" style="font-size: 0.9em; color: #1e3a8a;">
                            <div style="margin-bottom: 8px;">
                                Monto mensual estimado: <strong>$${coverage.expense_amount.toFixed(2)}</strong>
                            </div>
                            <div style="margin-bottom: 8px;">
                                Cubierto: <strong>$${coverage.covered_amount.toFixed(2)}</strong>
                            </div>
                            <div style="margin-top: 10px;">
                                <div style="background: #e5e7eb; border-radius: 4px; height: 20px; overflow: hidden;">
                                    <div id="coverage-bar" style="background: ${coverage.coverage_percent >= 100 ? '#22c55e' : coverage.coverage_percent >= 50 ? '#f59e0b' : '#ef4444'}; height: 100%; width: ${Math.min(coverage.coverage_percent, 100)}%; transition: width 0.3s;"></div>
                                </div>
                                <div style="text-align: center; margin-top: 5px; font-weight: 600;">
                                    <span id="coverage-percent">${coverage.coverage_percent.toFixed(1)}%</span> cubierto
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `,
            showCancelButton: true,
            confirmButtonText: 'Guardar',
            cancelButtonText: 'Cancelar',
            confirmButtonColor: '#10b981',
            width: '700px',
            didOpen: () => {
                const checkboxes = document.querySelectorAll('.expense-income-source-checkbox');
                
                function updateCalculations() {
                    let totalCovered = 0;
                    const expenseMonthlyAmount = coverage.expense_amount;
                    
                    checkboxes.forEach(cb => {
                        const incomeId = cb.dataset.incomeId;
                        const amount = parseFloat(cb.dataset.incomeAmount);
                        const frequency = cb.dataset.incomeFrequency;
                        const interval = parseInt(cb.dataset.incomeInterval) || 1;
                        
                        const typeSelect = document.querySelector(`.allocation-type-select[data-income-id="${incomeId}"]`);
                        const valueInput = document.querySelector(`.allocation-value-input[data-income-id="${incomeId}"]`);
                        const calculatedSpan = document.querySelector(`.calculated-contribution[data-income-id="${incomeId}"]`);
                        
                        if (cb.checked && typeSelect && valueInput) {
                            const allocationType = typeSelect.value;
                            const allocationValue = parseFloat(valueInput.value) || 0;
                            
                            let contribution = 0;
                            if (allocationType === 'percent') {
                                contribution = amount * (allocationValue / 100);
                            } else {
                                contribution = allocationValue;
                            }
                            
                            calculatedSpan.textContent = contribution.toFixed(2);
                            
                            // Convertir a mensual
                            let monthlyContribution = contribution;
                            if (frequency === 'daily') {
                                monthlyContribution = (contribution / interval) * 30;
                            } else if (frequency === 'weekly') {
                                monthlyContribution = (contribution / interval) * 4.33;
                            } else if (frequency === 'monthly') {
                                monthlyContribution = contribution / interval;
                            } else if (frequency === 'yearly') {
                                monthlyContribution = contribution / 12 / interval;
                            }
                            
                            totalCovered += monthlyContribution;
                        }
                    });
                    
                    const coveragePercent = expenseMonthlyAmount > 0 
                        ? (totalCovered / expenseMonthlyAmount) * 100 
                        : 0;
                    
                    document.getElementById('coverage-percent').textContent = coveragePercent.toFixed(1);
                    const bar = document.getElementById('coverage-bar');
                    bar.style.width = `${Math.min(coveragePercent, 100)}%`;
                    bar.style.background = coveragePercent >= 100 ? '#22c55e' : coveragePercent >= 50 ? '#f59e0b' : '#ef4444';
                }
                
                checkboxes.forEach(checkbox => {
                    checkbox.addEventListener('change', (e) => {
                        const container = e.target.closest('.income-source-option');
                        const allocationInput = container.querySelector('.allocation-input');
                        
                        if (e.target.checked) {
                            allocationInput.style.display = 'block';
                            container.style.background = '#dcfce7';
                            container.style.borderColor = '#22c55e';
                        } else {
                            allocationInput.style.display = 'none';
                            container.style.background = '#f9fafb';
                            container.style.borderColor = 'transparent';
                        }
                        
                        updateCalculations();
                    });
                });
                
                // Evento para cambio de tipo de asignación
                document.querySelectorAll('.allocation-type-select').forEach(select => {
                    select.addEventListener('change', (e) => {
                        const incomeId = e.target.dataset.incomeId;
                        const unitSpan = document.querySelector(`.allocation-unit[data-income-id="${incomeId}"]`);
                        const valueInput = document.querySelector(`.allocation-value-input[data-income-id="${incomeId}"]`);
                        
                        if (e.target.value === 'percent') {
                            unitSpan.textContent = '%';
                            if (parseFloat(valueInput.value) > 100) {
                                valueInput.value = '100';
                            }
                        } else {
                            unitSpan.textContent = '$ fijo';
                        }
                        
                        updateCalculations();
                    });
                });
                
                // Evento para cambio de valor
                document.querySelectorAll('.allocation-value-input').forEach(input => {
                    input.addEventListener('input', updateCalculations);
                });
                
                // Cálculo inicial
                updateCalculations();
            },
            preConfirm: () => {
                const sources = [];
                
                document.querySelectorAll('.expense-income-source-checkbox:checked').forEach(cb => {
                    const incomeId = cb.dataset.incomeId;
                    const typeSelect = document.querySelector(`.allocation-type-select[data-income-id="${incomeId}"]`);
                    const valueInput = document.querySelector(`.allocation-value-input[data-income-id="${incomeId}"]`);
                    const notesInput = document.querySelector(`.allocation-notes[data-income-id="${incomeId}"]`);
                    
                    const allocationType = typeSelect.value;
                    let allocationValue = parseFloat(valueInput.value) || 0;
                    
                    if (allocationType === 'percent') {
                        if (allocationValue <= 0 || allocationValue > 100) {
                            Swal.showValidationMessage('El porcentaje debe estar entre 0.01 y 100');
                            return false;
                        }
                        allocationValue = allocationValue / 100; // Convertir a decimal
                    } else {
                        if (allocationValue <= 0) {
                            Swal.showValidationMessage('El monto fijo debe ser mayor a 0');
                            return false;
                        }
                    }
                    
                    sources.push({
                        income_pattern_id: incomeId,
                        allocation_type: allocationType,
                        allocation_value: allocationValue,
                        notes: notesInput?.value?.trim() || null
                    });
                });
                
                return sources;
            }
        });
        
        if (formValues !== undefined) {
            try {
                await replaceExpensePatternIncomeSources(expensePatternId, formValues);
                
                await Swal.fire({
                    icon: 'success',
                    title: '✅ Fuentes actualizadas',
                    text: `Se asignaron ${formValues.length} fuente(s) de ingreso`,
                    timer: 1500,
                    showConfirmButton: false
                });
                
                if (onUpdated) onUpdated();
            } catch (error) {
                console.error('Error updating expense pattern income sources:', error);
                await Swal.fire({
                    icon: 'error',
                    title: 'Error',
                    text: error.message || 'No se pudieron actualizar las fuentes de ingreso'
                });
            }
        }
    } catch (error) {
        console.error('Error in showExpensePatternIncomeSourcesDialog:', error);
        await Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'No se pudo cargar la información del patrón de gasto'
        });
    }
}

// ============================================================================
// MODAL: CREAR PRÉSTAMO
// ============================================================================

/**
 * Modal para crear un nuevo préstamo (dado / recibido) - V2
 */
async function showCreateLoanDialog(dateISO, onCreated) {
    const { createLoan } = await import('./loans-v2.js');
    
    const { value: formValues } = await Swal.fire({
        title: '💰 Nuevo Préstamo',
        html: `
            <div style="text-align: left; padding: 10px;">
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Nombre del préstamo:
                    </label>
                    <input 
                        id="loan-name" 
                        type="text" 
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="Ej: Préstamo a Juan para emergencia"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Tipo de préstamo:
                    </label>
                    <select id="loan-type" class="swal2-select" style="width: 100%;">
                        <option value="given">➡️ Dado (Yo presto dinero)</option>
                        <option value="received">⬅️ Recibido (Me prestan dinero)</option>
                    </select>
                    <p style="font-size: 0.85em; color: #666; margin-top: 5px;">
                        <strong>Dado:</strong> Prestas dinero y luego te lo devuelven.<br/>
                        <strong>Recibido:</strong> Te prestan dinero y lo debes devolver.
                    </p>
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Contraparte (persona/entidad):
                    </label>
                    <input 
                        id="loan-counterparty" 
                        type="text" 
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="Nombre de la persona o entidad"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Monto:
                    </label>
                    <input 
                        id="loan-amount" 
                        type="number" 
                        step="0.01"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="0.00"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Fecha del préstamo:
                    </label>
                    <input 
                        id="loan-date" 
                        type="date" 
                        value="${dateISO}"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Fecha de vencimiento (opcional):
                    </label>
                    <input 
                        id="loan-due-date" 
                        type="date" 
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                    />
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Notas (opcional):
                    </label>
                    <textarea 
                        id="loan-description" 
                        class="swal2-textarea" 
                        style="margin: 0; width: 100%; min-height: 60px;"
                        placeholder="Detalles adicionales..."
                    ></textarea>
                </div>
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: 'Crear Préstamo',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#f59e0b',
        width: '550px',
        preConfirm: () => {
            const name = document.getElementById('loan-name').value.trim();
            const type = document.getElementById('loan-type').value;
            const counterparty = document.getElementById('loan-counterparty').value.trim();
            const amount = document.getElementById('loan-amount').value;
            const loanDate = document.getElementById('loan-date').value;
            const dueDate = document.getElementById('loan-due-date').value;
            const description = document.getElementById('loan-description').value.trim();
            
            if (!name) {
                Swal.showValidationMessage('Ingresa un nombre para el préstamo');
                return false;
            }
            if (!counterparty) {
                Swal.showValidationMessage('Ingresa el nombre de la contraparte');
                return false;
            }
            if (!amount || parseFloat(amount) <= 0) {
                Swal.showValidationMessage('Ingresa un monto válido');
                return false;
            }
            if (!loanDate) {
                Swal.showValidationMessage('Selecciona la fecha del préstamo');
                return false;
            }
            
            const loanData = {
                name,
                type,
                counterparty,
                original_amount: parseFloat(amount),
                remaining_amount: parseFloat(amount), // Al crear, remaining = original
                loan_date: loanDate,
                due_date: dueDate || null,
                description: description || null,
                status: 'active'
            };
            
            return loanData;
        }
    });
    
    
    if (formValues) {
        try {
            const loan = await createLoan(formValues);
            
            await Swal.fire({
                icon: 'success',
                title: '✅ Préstamo Creado',
                html: `
                    <div style="text-align: left;">
                        <p><strong>Tipo:</strong> ${formValues.type === 'given' ? 'Dado (prestaste dinero)' : 'Recibido (te prestaron)'}</p>
                        <p><strong>Contraparte:</strong> ${formValues.counterparty}</p>
                        <p><strong>Monto:</strong> $${formValues.original_amount}</p>
                        <p style="margin-top: 10px; color: #666; font-size: 0.9em;">
                            ${formValues.type === 'given' 
                                ? '✅ Se registró un préstamo <strong>dado</strong> (dinero que prestaste).' 
                                : '✅ Se registró un préstamo <strong>recibido</strong> (dinero que te prestaron).'}
                        </p>
                    </div>
                `,
                timer: 3000,
                showConfirmButton: true
            });
            
            if (onCreated) onCreated(loan);
        } catch (error) {
            console.error('Error creating loan:', error);
            await Swal.fire({
                icon: 'error',
                title: 'Error',
                text: error.message || 'No se pudo crear el préstamo'
            });
        }
    }
}

// ============================================================================
// MODAL: BALANCE DE MOVIMIENTOS CONFIRMADOS (MEJORADO)
// ============================================================================

/**
 * Modal que muestra el balance de movimientos confirmados con información detallada
 */
export async function showBalanceSummaryDialog() {
    try {
        // Obtener datos del usuario
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');
        
        // Obtener datos en paralelo
        const [balance, savings, recentMovements, monthlyData, plansData, categoriesData] = await Promise.all([
            getConfirmedBalanceSummary(),
            getSavingsSummary(),
            // Movimientos recientes (últimos 10 confirmados) - consulta directa a movements
            supabase.from('movements')
                .select('id, date, title, description, type, confirmed_amount, income_pattern_id, expense_pattern_id')
                .eq('user_id', user.id)
                .eq('confirmed', true)
                .eq('archived', false)
                .order('date', { ascending: false })
                .limit(10),
            // Balance del mes actual
            (async () => {
                const today = new Date();
                const startOfMonth = new Date(today.getFullYear(), today.getMonth(), 1).toISOString().slice(0, 10);
                const endOfMonth = new Date(today.getFullYear(), today.getMonth() + 1, 0).toISOString().slice(0, 10);
                const { data } = await supabase.from('movements')
                    .select('type, confirmed_amount')
                    .eq('user_id', user.id)
                    .eq('confirmed', true)
                    .eq('archived', false)
                    .gte('date', startOfMonth)
                    .lte('date', endOfMonth);
                return data || [];
            })(),
            // Planes activos (current_amount en lugar de accumulated)
            supabase.from('plans')
                .select('id, name, current_amount, target_amount, status')
                .eq('user_id', user.id)
                .in('status', ['active', 'paused']),
            // Categorías de gastos del mes actual
            (async () => {
                const today = new Date();
                const startOfMonth = new Date(today.getFullYear(), today.getMonth(), 1).toISOString().slice(0, 10);
                const endOfMonth = new Date(today.getFullYear(), today.getMonth() + 1, 0).toISOString().slice(0, 10);
                const { data } = await supabase.from('movements')
                    .select('category, confirmed_amount')
                    .eq('user_id', user.id)
                    .eq('type', 'gasto')
                    .eq('confirmed', true)
                    .eq('archived', false)
                    .gte('date', startOfMonth)
                    .lte('date', endOfMonth);
                return data || [];
            })()
        ]);
        
        const recent = recentMovements.data || [];
        const plans = plansData.data || [];
        
        // Calcular estadísticas del mes
        const monthStats = monthlyData.reduce((acc, m) => {
            if (m.type === 'ingreso') {
                acc.income += parseFloat(m.confirmed_amount || 0);
            } else {
                acc.expenses += parseFloat(m.confirmed_amount || 0);
            }
            return acc;
        }, { income: 0, expenses: 0 });
        monthStats.balance = monthStats.income - monthStats.expenses;
        
        // Agrupar gastos por categoría
        const categoryTotals = categoriesData.reduce((acc, m) => {
            const cat = m.category || 'Sin categoría';
            acc[cat] = (acc[cat] || 0) + parseFloat(m.confirmed_amount || 0);
            return acc;
        }, {});
        
        // Ordenar categorías por monto
        const sortedCategories = Object.entries(categoryTotals)
            .sort(([, a], [, b]) => b - a)
            .slice(0, 5); // Top 5 categorías
        
        // Calcular total de planes
        const totalPlans = plans.reduce((sum, p) => sum + (p.current_amount || 0), 0);
        const totalPlansTarget = plans.reduce((sum, p) => sum + (p.target_amount || 0), 0);
        
        // Formatear fecha
        const formatDate = (dateStr) => {
            const d = new Date(dateStr + 'T00:00:00');
            return d.toLocaleDateString('es-MX', { day: 'numeric', month: 'short' });
        };
        
        // Nombres de meses
        const monthName = new Date().toLocaleDateString('es-MX', { month: 'long', year: 'numeric' });
        
        // Normalizar balance para evitar -$0.00
        const normalizedBalance = Math.abs(balance.balance) < 0.01 ? 0 : balance.balance;
        const balanceColor = normalizedBalance >= 0 ? '#10b981' : '#ef4444';
        
        await Swal.fire({
            title: '💰 Mi Balance Financiero',
            html: `
                <div class="balance-dialog-content">
                    <!-- Resumen Principal -->
                    <div class="balance-hero">
                        <div class="balance-main-value">
                            <span class="balance-label">Balance Disponible</span>
                            <span class="balance-amount" style="color: ${balanceColor}">
                                ${formatCurrency(normalizedBalance)}
                            </span>
                        </div>
                        <div class="balance-stats-row">
                            <div class="balance-stat income">
                                <span class="stat-icon">📈</span>
                                <span class="stat-value">+${formatCurrency(balance.total_income)}</span>
                                <span class="stat-label">${balance.income_count} ingresos</span>
                            </div>
                            <div class="balance-stat expense">
                                <span class="stat-icon">📉</span>
                                <span class="stat-value">-${formatCurrency(balance.total_expenses)}</span>
                                <span class="stat-label">${balance.expense_count} gastos</span>
                            </div>
                        </div>
                    </div>
                    
                    <!-- Tabs de información -->
                    <div class="balance-tabs">
                        <button class="balance-tab active" data-tab="month">📅 Este mes</button>
                        <button class="balance-tab" data-tab="recent">📋 Recientes</button>
                        <button class="balance-tab" data-tab="categories">📊 Categorías</button>
                        <button class="balance-tab" data-tab="assets">💎 Activos</button>
                    </div>
                    
                    <div class="balance-tab-content">
                        <!-- Tab: Este mes -->
                        <div class="balance-tab-pane active" id="balance-tab-month">
                            <h4 class="section-title">Resumen de ${monthName}</h4>
                            <div class="month-summary">
                                <div class="month-stat">
                                    <span class="month-label">Ingresos del mes</span>
                                    <span class="month-value positive">+${formatCurrency(monthStats.income)}</span>
                                </div>
                                <div class="month-stat">
                                    <span class="month-label">Gastos del mes</span>
                                    <span class="month-value negative">-${formatCurrency(monthStats.expenses)}</span>
                                </div>
                                <div class="month-stat highlight">
                                    <span class="month-label">Balance del mes</span>
                                    <span class="month-value ${Math.abs(monthStats.balance) < 0.01 ? 'positive' : monthStats.balance >= 0 ? 'positive' : 'negative'}">
                                        ${formatCurrency(Math.abs(monthStats.balance) < 0.01 ? 0 : monthStats.balance)}
                                    </span>
                                </div>
                            </div>
                            ${monthStats.income > 0 ? `
                                <div class="spending-rate">
                                    <span>Tasa de gasto</span>
                                    <div class="rate-bar">
                                        <div class="rate-fill" style="width: ${Math.min(100, (monthStats.expenses / monthStats.income) * 100)}%"></div>
                                    </div>
                                    <span class="rate-percent">${((monthStats.expenses / monthStats.income) * 100).toFixed(1)}%</span>
                                </div>
                            ` : ''}
                        </div>
                        
                        <!-- Tab: Movimientos recientes -->
                        <div class="balance-tab-pane" id="balance-tab-recent">
                            <h4 class="section-title">Últimos movimientos</h4>
                            ${recent.length === 0 ? 
                                '<p class="no-data">No hay movimientos recientes</p>' :
                                `<div class="recent-list">
                                    ${recent.map(m => `
                                        <div class="recent-item ${m.type}">
                                            <div class="recent-info">
                                                <span class="recent-desc">${m.title || m.description || m.income_pattern_name || m.expense_pattern_name || 'Sin descripción'}</span>
                                                <span class="recent-date">${formatDate(m.date)}</span>
                                            </div>
                                            <span class="recent-amount ${m.type === 'ingreso' ? 'positive' : 'negative'}">
                                                ${m.type === 'ingreso' ? '+' : '-'}${formatCurrency(m.confirmed_amount)}
                                            </span>
                                        </div>
                                    `).join('')}
                                </div>`
                            }
                        </div>
                        
                        <!-- Tab: Categorías -->
                        <div class="balance-tab-pane" id="balance-tab-categories">
                            <h4 class="section-title">Top gastos por categoría (${monthName})</h4>
                            ${sortedCategories.length === 0 ? 
                                '<p class="no-data">No hay gastos registrados este mes</p>' :
                                `<div class="categories-list">
                                    ${sortedCategories.map(([cat, amount], i) => {
                                        const percent = monthStats.expenses > 0 ? (amount / monthStats.expenses) * 100 : 0;
                                        const colors = ['#ef4444', '#f97316', '#eab308', '#22c55e', '#3b82f6'];
                                        return `
                                            <div class="category-item">
                                                <div class="category-info">
                                                    <span class="category-name">${cat}</span>
                                                    <div class="category-bar">
                                                        <div class="category-fill" style="width: ${percent}%; background: ${colors[i]}"></div>
                                                    </div>
                                                </div>
                                                <div class="category-values">
                                                    <span class="category-amount">${formatCurrency(amount)}</span>
                                                    <span class="category-percent">${percent.toFixed(1)}%</span>
                                                </div>
                                            </div>
                                        `;
                                    }).join('')}
                                </div>`
                            }
                        </div>
                        
                        <!-- Tab: Activos -->
                        <div class="balance-tab-pane" id="balance-tab-assets">
                            <h4 class="section-title">Mis activos financieros</h4>
                            <div class="assets-grid">
                                <div class="asset-card balance-card">
                                    <span class="asset-icon">💵</span>
                                    <span class="asset-label">Balance disponible</span>
                                    <span class="asset-value">${formatCurrency(normalizedBalance)}</span>
                                </div>
                                <div class="asset-card savings-card">
                                    <span class="asset-icon">🐷</span>
                                    <span class="asset-label">Ahorros</span>
                                    <span class="asset-value">${formatCurrency(savings.total_saved)}</span>
                                </div>
                                <div class="asset-card plans-card">
                                    <span class="asset-icon">🎯</span>
                                    <span class="asset-label">En planes</span>
                                    <span class="asset-value">${formatCurrency(totalPlans)}</span>
                                </div>
                                <div class="asset-card total-card">
                                    <span class="asset-icon">💎</span>
                                    <span class="asset-label">Total activos</span>
                                    <span class="asset-value">${formatCurrency(normalizedBalance + savings.total_saved + totalPlans)}</span>
                                </div>
                            </div>
                            
                            ${plans.length > 0 ? `
                                <h4 class="section-title" style="margin-top: 16px;">Planes activos</h4>
                                <div class="plans-mini-list">
                                    ${plans.map(p => {
                                        const progress = p.target_amount > 0 ? Math.min(100, (p.current_amount / p.target_amount) * 100) : 0;
                                        return `
                                            <div class="plan-mini-item">
                                                <div class="plan-mini-info">
                                                    <span class="plan-mini-name">${p.name}</span>
                                                    <div class="plan-mini-progress">
                                                        <div class="plan-mini-bar" style="width: ${progress}%"></div>
                                                    </div>
                                                </div>
                                                <span class="plan-mini-amount">${formatCurrency(p.current_amount)} / ${formatCurrency(p.target_amount)}</span>
                                            </div>
                                        `;
                                    }).join('')}
                                </div>
                            ` : ''}
                        </div>
                    </div>
                    
                    <!-- Nota informativa -->
                    <div class="balance-note">
                        <strong>ℹ️</strong> Este balance solo incluye movimientos <strong>confirmados</strong>.
                    </div>
                </div>
            `,
            showCancelButton: true,
            confirmButtonText: '🐷 Gestionar Ahorros',
            cancelButtonText: 'Cerrar',
            confirmButtonColor: '#22c55e',
            cancelButtonColor: '#6b7280',
            width: '600px',
            customClass: {
                popup: 'balance-summary-popup',
                htmlContainer: 'balance-summary-container'
            },
            didOpen: () => {
                // Manejar tabs
                const tabs = document.querySelectorAll('.balance-tab');
                const panes = document.querySelectorAll('.balance-tab-pane');
                
                tabs.forEach(tab => {
                    tab.addEventListener('click', () => {
                        tabs.forEach(t => t.classList.remove('active'));
                        panes.forEach(p => p.classList.remove('active'));
                        
                        tab.classList.add('active');
                        const targetPane = document.getElementById(`balance-tab-${tab.dataset.tab}`);
                        if (targetPane) targetPane.classList.add('active');
                    });
                });
            }
        }).then(async (result) => {
            if (result.isConfirmed) {
                await showSavingsManagementDialog();
            }
        });
    } catch (error) {
        console.error('Error showing balance summary:', error);
        await Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'No se pudo cargar el resumen de balance'
        });
    }
}

// ============================================================================
// MODAL: GESTIÓN DE AHORROS
// ============================================================================

/**
 * Modal para gestionar patrones de ahorro
 */
export async function showSavingsManagementDialog() {
    try {
        const patterns = await getSavingsPatterns(true);
        const summary = await getSavingsSummary();
        
        let patternsHTML = '';
        if (patterns.length === 0) {
            patternsHTML = `
                <div style="text-align: center; padding: 30px; color: #6b7280;">
                    <div style="font-size: 3em; margin-bottom: 10px;">🐷</div>
                    <p>No tienes patrones de ahorro activos.</p>
                    <p style="font-size: 0.9em;">Crea uno para empezar a ahorrar automáticamente.</p>
                </div>
            `;
        } else {
            patternsHTML = patterns.map(pattern => {
                const progress = pattern.target_amount 
                    ? Math.min((pattern.current_balance / pattern.target_amount) * 100, 100)
                    : null;
                
                const allocationLabel = {
                    'percent': `${(pattern.allocation_value * 100).toFixed(0)}% del sobrante`,
                    'fixed': `$${pattern.allocation_value} fijo`,
                    'remainder': 'Todo el sobrante'
                }[pattern.allocation_type];
                
                return `
                <div style="background: #f9fafb; padding: 15px; border-radius: 8px; margin-bottom: 12px; border-left: 4px solid #22c55e;">
                    <div style="display: flex; justify-content: space-between; align-items: start;">
                        <div>
                            <div style="font-weight: 600; color: #1f2937;">${pattern.name}</div>
                            <div style="font-size: 0.85em; color: #6b7280; margin-top: 2px;">
                                ${allocationLabel} · Prioridad: ${pattern.priority}/10
                            </div>
                        </div>
                        <div style="text-align: right;">
                            <div style="font-size: 1.2em; font-weight: bold; color: #22c55e;">
                                $${parseFloat(pattern.current_balance).toLocaleString('es-MX', { minimumFractionDigits: 2 })}
                            </div>
                            ${pattern.target_amount ? `
                                <div style="font-size: 0.8em; color: #6b7280;">
                                    de $${parseFloat(pattern.target_amount).toLocaleString('es-MX')}
                                </div>
                            ` : ''}
                        </div>
                    </div>
                    ${progress !== null ? `
                        <div style="margin-top: 10px;">
                            <div style="background: #e5e7eb; border-radius: 4px; height: 6px; overflow: hidden;">
                                <div style="background: ${progress >= 100 ? '#22c55e' : '#3b82f6'}; height: 100%; width: ${progress}%;"></div>
                            </div>
                            <div style="font-size: 0.75em; color: #6b7280; text-align: right; margin-top: 2px;">
                                ${progress.toFixed(1)}%
                            </div>
                        </div>
                    ` : ''}
                    <div style="margin-top: 10px; display: flex; gap: 8px;">
                        <button 
                            type="button" 
                            class="btn-savings-deposit" 
                            data-pattern-id="${pattern.id}"
                            style="flex: 1; padding: 6px 12px; background: #22c55e; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 0.85em;"
                        >
                            ➕ Depositar
                        </button>
                        <button 
                            type="button" 
                            class="btn-savings-withdraw" 
                            data-pattern-id="${pattern.id}"
                            data-balance="${pattern.current_balance}"
                            style="flex: 1; padding: 6px 12px; background: #f59e0b; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 0.85em;"
                        >
                            ➖ Retirar
                        </button>
                        <button 
                            type="button" 
                            class="btn-savings-delete" 
                            data-pattern-id="${pattern.id}"
                            data-pattern-name="${pattern.name}"
                            data-balance="${pattern.current_balance}"
                            style="padding: 6px 12px; background: #ef4444; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 0.85em;"
                            title="Eliminar ahorro"
                        >
                            🗑️
                        </button>
                    </div>
                </div>
                `;
            }).join('');
        }
        
        const { value: action } = await Swal.fire({
            title: '🐷 Gestión de Ahorros',
            html: `
                <div style="text-align: left; padding: 10px;">
                    <!-- Total ahorrado -->
                    <div style="background: #dcfce7; padding: 15px; border-radius: 8px; text-align: center; margin-bottom: 20px;">
                        <div style="font-size: 0.9em; color: #166534;">Total Ahorrado</div>
                        <div style="font-size: 2em; font-weight: bold; color: #15803d;">
                            $${summary.total_saved.toLocaleString('es-MX', { minimumFractionDigits: 2 })}
                        </div>
                    </div>
                    
                    <!-- Lista de patrones -->
                    <div style="max-height: 350px; overflow-y: auto;">
                        ${patternsHTML}
                    </div>
                </div>
            `,
            showCancelButton: true,
            showDenyButton: true,
            confirmButtonText: '➕ Nuevo Patrón de Ahorro',
            denyButtonText: '📊 Ver Balance',
            cancelButtonText: 'Cerrar',
            confirmButtonColor: '#22c55e',
            denyButtonColor: '#3b82f6',
            width: '550px',
            didOpen: () => {
                // Eventos para depositar
                document.querySelectorAll('.btn-savings-deposit').forEach(btn => {
                    btn.addEventListener('click', async (e) => {
                        const patternId = e.target.dataset.patternId;
                        Swal.close();
                        await showSavingsDepositDialog(patternId);
                    });
                });
                
                // Eventos para retirar
                document.querySelectorAll('.btn-savings-withdraw').forEach(btn => {
                    btn.addEventListener('click', async (e) => {
                        const patternId = e.target.dataset.patternId;
                        const balance = parseFloat(e.target.dataset.balance);
                        Swal.close();
                        await showSavingsWithdrawalDialog(patternId, balance);
                    });
                });

                // Eventos para eliminar
                document.querySelectorAll('.btn-savings-delete').forEach(btn => {
                    btn.addEventListener('click', async (e) => {
                        const patternId = e.target.dataset.patternId;
                        const patternName = e.target.dataset.patternName;
                        const balance = parseFloat(e.target.dataset.balance) || 0;
                        Swal.close();
                        await showDeleteSavingsDialog(patternId, patternName, balance);
                    });
                });
            }
        });
        
        if (action === true) {
            // Crear nuevo patrón de ahorro
            await showCreateSavingsPatternDialog();
        } else if (action === false) {
            // Volver al balance
            await showBalanceSummaryDialog();
        }
    } catch (error) {
        console.error('Error showing savings management:', error);
        await Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'No se pudo cargar la gestión de ahorros'
        });
    }
}

/**
 * Modal para eliminar un ahorro con opciones sobre qué hacer con el dinero
 */
async function showDeleteSavingsDialog(patternId, patternName, currentBalance) {
    const { deleteSavingsPattern, createSavingsWithdrawal } = await import('./savings.js');
    
    const hasBalance = currentBalance > 0;
    
    const { value: action } = await Swal.fire({
        icon: 'warning',
        title: `🗑️ Eliminar "${patternName}"`,
        html: `
            <div style="text-align: left; padding: 10px;">
                ${hasBalance ? `
                    <div style="background: #fef3c7; padding: 15px; border-radius: 8px; margin-bottom: 15px; border: 1px solid #f59e0b;">
                        <div style="font-weight: 600; color: #92400e; margin-bottom: 8px;">⚠️ Este ahorro tiene dinero</div>
                        <div style="font-size: 1.5em; font-weight: bold; color: #d97706;">
                            $${currentBalance.toLocaleString('es-MX', { minimumFractionDigits: 2 })}
                        </div>
                    </div>
                    <p style="margin-bottom: 15px; color: #374151;">¿Qué deseas hacer con el dinero?</p>
                    
                    <div style="display: flex; flex-direction: column; gap: 10px;">
                        <label style="display: flex; align-items: center; padding: 12px; border: 2px solid #e5e7eb; border-radius: 8px; cursor: pointer;">
                            <input type="radio" name="delete-action" value="transfer" checked style="margin-right: 10px;">
                            <div>
                                <div style="font-weight: 600; color: #059669;">💰 Transferir al balance</div>
                                <div style="font-size: 0.85em; color: #6b7280;">El dinero se añade a tu balance general</div>
                            </div>
                        </label>
                        
                        <label style="display: flex; align-items: center; padding: 12px; border: 2px solid #e5e7eb; border-radius: 8px; cursor: pointer;">
                            <input type="radio" name="delete-action" value="delete" style="margin-right: 10px;">
                            <div>
                                <div style="font-weight: 600; color: #dc2626;">🔥 Eliminar con el dinero</div>
                                <div style="font-size: 0.85em; color: #6b7280;">El dinero se pierde permanentemente</div>
                            </div>
                        </label>
                    </div>
                ` : `
                    <p style="color: #374151;">¿Estás seguro de que deseas eliminar este patrón de ahorro?</p>
                    <p style="font-size: 0.9em; color: #6b7280;">Esta acción no se puede deshacer.</p>
                `}
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: 'Eliminar',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#dc2626',
        width: '450px',
        preConfirm: () => {
            if (hasBalance) {
                const selected = document.querySelector('input[name="delete-action"]:checked');
                return selected ? selected.value : 'transfer';
            }
            return 'delete';
        }
    });
    
    if (!action) {
        await showSavingsManagementDialog();
        return;
    }
    
    try {
        // Si eligió transferir, primero retirar el dinero
        if (action === 'transfer' && currentBalance > 0) {
            await createSavingsWithdrawal(patternId, currentBalance, 'Retiro por eliminación de ahorro');
        }
        
        // Eliminar el patrón
        await deleteSavingsPattern(patternId, true); // hard delete
        
        if (window.refreshBalanceIndicator) window.refreshBalanceIndicator();
        
        const message = action === 'transfer' && currentBalance > 0
            ? `El ahorro fue eliminado y $${currentBalance.toLocaleString('es-MX', { minimumFractionDigits: 2 })} fueron transferidos a tu balance.`
            : 'El ahorro fue eliminado.';
        
        await Swal.fire({
            icon: 'success',
            title: '✅ Eliminado',
            text: message,
            timer: 2500,
            showConfirmButton: false
        });
        
        await showSavingsManagementDialog();
    } catch (error) {
        console.error('Error deleting savings:', error);
        await Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'No se pudo eliminar el ahorro: ' + error.message
        });
        await showSavingsManagementDialog();
    }
}

/**
 * Modal para crear un nuevo patrón de ahorro
 */
export async function showCreateSavingsPatternDialog() {
    try {
        const incomePatterns = await getIncomePatterns(true);
        
        const incomeOptionsHTML = incomePatterns.length > 0 
            ? incomePatterns.map(ip => `
                <label style="display: flex; align-items: center; padding: 8px; background: #f9fafb; border-radius: 4px; margin-bottom: 5px; cursor: pointer;">
                    <input type="checkbox" class="savings-income-source" data-income-id="${ip.id}" style="margin-right: 10px;">
                    <span>${ip.name} - $${ip.base_amount} (${ip.frequency})</span>
                </label>
            `).join('')
            : '<p style="color: #6b7280;">No hay ingresos disponibles para anclar.</p>';
        
        const dayOfWeekOptions = [
            { value: 0, label: 'Domingo' },
            { value: 1, label: 'Lunes' },
            { value: 2, label: 'Martes' },
            { value: 3, label: 'Miércoles' },
            { value: 4, label: 'Jueves' },
            { value: 5, label: 'Viernes' },
            { value: 6, label: 'Sábado' }
        ];
        
        const { value: formValues } = await Swal.fire({
            title: '➕ Nuevo Patrón de Ahorro',
            html: `
                <div style="text-align: left; padding: 10px; max-height: 70vh; overflow-y: auto;">
                    <div style="margin-bottom: 15px;">
                        <label style="display: block; margin-bottom: 5px; font-weight: 600;">Nombre:</label>
                        <input id="savings-name" type="text" class="swal2-input" style="margin: 0; width: 100%;" placeholder="Ej: Fondo de emergencia">
                    </div>
                    
                    <div style="margin-bottom: 15px;">
                        <label style="display: block; margin-bottom: 5px; font-weight: 600;">Tipo de ahorro:</label>
                        <select id="savings-type" class="swal2-select" style="width: 100%;">
                            <option value="percent">Porcentaje del sobrante</option>
                            <option value="fixed">Monto fijo</option>
                            <option value="remainder">Todo el sobrante</option>
                        </select>
                    </div>
                    
                    <div id="savings-value-container" style="margin-bottom: 15px;">
                        <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                            <span id="savings-value-label">Porcentaje (0-100):</span>
                        </label>
                        <input id="savings-value" type="number" step="0.01" class="swal2-input" style="margin: 0; width: 100%;" placeholder="10">
                    </div>
                    
                    <div style="margin-bottom: 15px;">
                        <label style="display: block; margin-bottom: 5px; font-weight: 600;">Meta de ahorro (opcional):</label>
                        <input id="savings-target" type="number" step="0.01" class="swal2-input" style="margin: 0; width: 100%;" placeholder="0.00">
                    </div>
                    
                    <div style="margin-bottom: 15px;">
                        <label style="display: block; margin-bottom: 5px; font-weight: 600;">Prioridad (1-10):</label>
                        <input id="savings-priority" type="number" min="1" max="10" value="5" class="swal2-input" style="margin: 0; width: 100%;">
                    </div>
                    
                    <!-- SECCIÓN: PROGRAMACIÓN -->
                    <div style="margin-bottom: 15px; padding: 12px; background: #f0f9ff; border-radius: 8px; border: 1px solid #bae6fd;">
                        <label style="display: flex; align-items: center; margin-bottom: 10px; cursor: pointer;">
                            <input type="checkbox" id="enable-schedule" style="margin-right: 10px; width: 18px; height: 18px;">
                            <span style="font-weight: 600; color: #0369a1;">📅 Programar depósitos automáticos</span>
                        </label>
                        
                        <div id="schedule-options" style="display: none;">
                            <div style="margin-bottom: 10px;">
                                <label style="display: block; margin-bottom: 5px; font-size: 0.9em;">Frecuencia:</label>
                                <select id="savings-frequency" class="swal2-select" style="width: 100%;">
                                    <option value="weekly">Semanal</option>
                                    <option value="biweekly">Quincenal</option>
                                    <option value="monthly" selected>Mensual</option>
                                    <option value="yearly">Anual</option>
                                </select>
                            </div>
                            
                            <div id="day-of-week-container" style="margin-bottom: 10px; display: none;">
                                <label style="display: block; margin-bottom: 5px; font-size: 0.9em;">Día de la semana:</label>
                                <select id="savings-day-of-week" class="swal2-select" style="width: 100%;">
                                    ${dayOfWeekOptions.map(d => `<option value="${d.value}">${d.label}</option>`).join('')}
                                </select>
                            </div>
                            
                            <div id="day-of-month-container" style="margin-bottom: 10px;">
                                <label style="display: block; margin-bottom: 5px; font-size: 0.9em;">Día del mes:</label>
                                <input id="savings-day-of-month" type="number" min="1" max="31" value="1" class="swal2-input" style="margin: 0; width: 100%;">
                            </div>
                            
                            <div style="margin-bottom: 10px;">
                                <label style="display: block; margin-bottom: 5px; font-size: 0.9em;">Fecha de inicio:</label>
                                <input id="savings-start-date" type="date" value="${new Date().toISOString().split('T')[0]}" class="swal2-input" style="margin: 0; width: 100%;">
                            </div>
                            
                            <div style="margin-bottom: 10px;">
                                <label style="display: block; margin-bottom: 5px; font-size: 0.9em;">Fecha de fin (opcional):</label>
                                <input id="savings-end-date" type="date" class="swal2-input" style="margin: 0; width: 100%;">
                            </div>
                        </div>
                    </div>
                    
                    <!-- SECCIÓN: ANCLAR A INGRESOS -->
                    <div style="margin-bottom: 15px;">
                        <label style="display: block; margin-bottom: 5px; font-weight: 600;">Anclar a ingresos (para sugerencias automáticas):</label>
                        <div style="max-height: 150px; overflow-y: auto; border: 1px solid #e5e7eb; border-radius: 4px; padding: 10px;">
                            ${incomeOptionsHTML}
                        </div>
                        <small style="color: #6b7280;">El ahorro se sugerirá cuando confirmes estos ingresos.</small>
                    </div>

                    <!-- Panel de Análisis Inteligente -->
                    <div id="smart-analysis-panel" class="smart-analysis-panel" style="
                        margin-top: 15px;
                        padding: 15px;
                        border-radius: 12px;
                        background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
                        border: 1px solid #cbd5e1;
                        display: none;
                    ">
                        <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 12px;">
                            <span style="font-size: 1.2rem;">🧠</span>
                            <span style="font-weight: 600; color: #1e40af;">Análisis Inteligente</span>
                        </div>
                        <div id="analysis-content" style="font-size: 0.9rem; color: #475569;">
                            Ingresa el monto para ver el análisis...
                        </div>
                    </div>
                </div>
            `,
            showCancelButton: true,
            confirmButtonText: 'Crear Patrón',
            cancelButtonText: 'Cancelar',
            confirmButtonColor: '#22c55e',
            width: '550px',
            didOpen: async () => {
                // Importar el asistente financiero inteligente
                const { SmartFinancialAssistant, updateAnalysisPanel } = await import('./smart-financial-assistant.js');
                const assistant = new SmartFinancialAssistant();
                
                // Pre-cargar estado financiero
                await assistant.preloadFinancialState();
                
                // Función para actualizar el análisis de ahorro en tiempo real
                const updateSavingsAnalysis = async () => {
                    const type = document.getElementById('savings-type').value;
                    const valueInput = document.getElementById('savings-value').value;
                    const frequency = document.getElementById('enable-schedule').checked 
                        ? document.getElementById('savings-frequency').value 
                        : 'monthly';
                    
                    let amount = 0;
                    if (type === 'fixed') {
                        amount = parseFloat(valueInput) || 0;
                    } else if (type === 'percent') {
                        // Estimar basado en balance disponible
                        const percent = parseFloat(valueInput) || 0;
                        // Usaremos un estimado del ingreso mensual
                        amount = 10000 * (percent / 100); // Placeholder - se ajustará en análisis real
                    }
                    
                    if (amount > 0) {
                        const analysis = await assistant.getSavingsAnalysis(amount, frequency);
                        updateAnalysisPanel(analysis, 'savings');
                    } else if (type !== 'remainder') {
                        const panel = document.getElementById('smart-analysis-panel');
                        if (panel) panel.style.display = 'none';
                    } else {
                        // Para remainder, mostrar panel con info
                        const analysis = await assistant.getSavingsAnalysis(0, frequency);
                        analysis.viability = 'excellent';
                        analysis.reason = 'Guardar el sobrante es una excelente estrategia';
                        updateAnalysisPanel(analysis, 'savings');
                    }
                };

                const typeSelect = document.getElementById('savings-type');
                const valueContainer = document.getElementById('savings-value-container');
                const valueLabel = document.getElementById('savings-value-label');
                const valueInput = document.getElementById('savings-value');
                const enableSchedule = document.getElementById('enable-schedule');
                const scheduleOptions = document.getElementById('schedule-options');
                const frequencySelect = document.getElementById('savings-frequency');
                const dayOfWeekContainer = document.getElementById('day-of-week-container');
                const dayOfMonthContainer = document.getElementById('day-of-month-container');
                
                // Toggle tipo de ahorro
                typeSelect.addEventListener('change', () => {
                    if (typeSelect.value === 'remainder') {
                        valueContainer.style.display = 'none';
                    } else {
                        valueContainer.style.display = 'block';
                        if (typeSelect.value === 'percent') {
                            valueLabel.textContent = 'Porcentaje (0-100):';
                            valueInput.placeholder = '10';
                        } else {
                            valueLabel.textContent = 'Monto fijo:';
                            valueInput.placeholder = '500.00';
                        }
                    }
                    // Actualizar análisis cuando cambia el tipo
                    updateSavingsAnalysis();
                });
                
                // Escuchar cambios en el valor
                valueInput.addEventListener('input', updateSavingsAnalysis);
                
                // Toggle programación
                enableSchedule.addEventListener('change', () => {
                    scheduleOptions.style.display = enableSchedule.checked ? 'block' : 'none';
                    // Actualizar análisis cuando cambia la programación
                    updateSavingsAnalysis();
                });
                
                // Toggle día semana/mes según frecuencia
                frequencySelect.addEventListener('change', () => {
                    if (frequencySelect.value === 'weekly' || frequencySelect.value === 'biweekly') {
                        dayOfWeekContainer.style.display = 'block';
                        dayOfMonthContainer.style.display = 'none';
                    } else {
                        dayOfWeekContainer.style.display = 'none';
                        dayOfMonthContainer.style.display = 'block';
                    }
                    // Actualizar análisis cuando cambia la frecuencia
                    updateSavingsAnalysis();
                });
            },
            preConfirm: () => {
                const name = document.getElementById('savings-name').value.trim();
                const allocationType = document.getElementById('savings-type').value;
                const allocationValue = document.getElementById('savings-value').value;
                const targetAmount = document.getElementById('savings-target').value;
                const priority = document.getElementById('savings-priority').value;
                const enableSchedule = document.getElementById('enable-schedule').checked;
                
                if (!name) {
                    Swal.showValidationMessage('El nombre es requerido');
                    return false;
                }
                
                if (allocationType !== 'remainder' && !allocationValue) {
                    Swal.showValidationMessage('El valor es requerido');
                    return false;
                }
                
                // Obtener ingresos seleccionados
                const selectedIncomes = [];
                document.querySelectorAll('.savings-income-source:checked').forEach(cb => {
                    selectedIncomes.push(cb.dataset.incomeId);
                });
                
                let finalValue = allocationValue;
                if (allocationType === 'percent') {
                    finalValue = parseFloat(allocationValue) / 100; // Convertir a decimal
                }
                
                const data = {
                    name,
                    allocation_type: allocationType,
                    allocation_value: allocationType !== 'remainder' ? finalValue : null,
                    target_amount: targetAmount ? parseFloat(targetAmount) : null,
                    priority: parseInt(priority) || 5,
                    income_sources: selectedIncomes
                };
                
                // Si hay programación habilitada
                if (enableSchedule) {
                    const frequency = document.getElementById('savings-frequency').value;
                    data.frequency = frequency;
                    data.start_date = document.getElementById('savings-start-date').value;
                    const endDate = document.getElementById('savings-end-date').value;
                    if (endDate) data.end_date = endDate;
                    
                    if (frequency === 'weekly' || frequency === 'biweekly') {
                        data.day_of_week = parseInt(document.getElementById('savings-day-of-week').value);
                    } else {
                        data.day_of_month = parseInt(document.getElementById('savings-day-of-month').value);
                    }
                }
                
                return data;
            }
        });
        
        if (formValues) {
            try {
                await createSavingsPattern(formValues);
                
                const msg = formValues.frequency 
                    ? 'El ahorro aparecerá en el calendario según la frecuencia configurada.'
                    : 'El patrón se aplicará cuando confirmes los ingresos vinculados.';
                
                await Swal.fire({
                    icon: 'success',
                    title: '✅ Patrón de Ahorro Creado',
                    text: msg,
                    timer: 2500,
                    showConfirmButton: false
                });
                
                await showSavingsManagementDialog();
            } catch (error) {
                console.error('Error creating savings pattern:', error);
                await Swal.fire({
                    icon: 'error',
                    title: 'Error',
                    text: error.message || 'No se pudo crear el patrón de ahorro'
                });
            }
        }
    } catch (error) {
        console.error('Error showing create savings pattern dialog:', error);
        await Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'No se pudo cargar el formulario'
        });
    }
}

/**
 * Modal para depositar en un patrón de ahorro
 */
async function showSavingsDepositDialog(patternId) {
    try {
        const pattern = await getSavingsPatternById(patternId);
        const balanceSummary = await getConfirmedBalanceSummary();
        const availableBalance = balanceSummary.balance || 0;
        
        const currentBalance = parseFloat(pattern.current_balance) || 0;
        const targetAmount = parseFloat(pattern.target_amount) || 0;
        const remainingToGoal = targetAmount > 0 ? Math.max(0, targetAmount - currentBalance) : 0;
        
        // Mostrar botón de completar meta si: hay meta, hay remanente, y el balance es suficiente
        const showCompleteGoalBtn = targetAmount > 0 && remainingToGoal > 0 && availableBalance >= remainingToGoal;
        
        // Info de meta si existe
        const goalInfoHTML = targetAmount > 0 ? `
            <div style="background: #eff6ff; padding: 12px; border-radius: 8px; margin-bottom: 15px;">
                <div style="font-size: 0.9em; color: #1e40af;">Meta de ahorro</div>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                    <span style="font-size: 1.2em; font-weight: bold; color: #2563eb;">
                        $${targetAmount.toLocaleString('es-MX', { minimumFractionDigits: 2 })}
                    </span>
                    <span style="font-size: 0.9em; color: #6b7280;">
                        Falta: $${remainingToGoal.toLocaleString('es-MX', { minimumFractionDigits: 2 })}
                    </span>
                </div>
                <div style="background: #e5e7eb; height: 8px; border-radius: 4px; margin-top: 8px; overflow: hidden;">
                    <div style="background: #3b82f6; height: 100%; width: ${Math.min((currentBalance / targetAmount) * 100, 100)}%;"></div>
                </div>
            </div>
        ` : '';
        
        // Botón de completar meta
        const completeGoalBtnHTML = showCompleteGoalBtn ? `
            <button type="button" id="complete-goal-btn" style="
                width: 100%; 
                padding: 12px; 
                background: linear-gradient(135deg, #10b981, #059669); 
                color: white; 
                border: none; 
                border-radius: 8px; 
                font-weight: 600; 
                cursor: pointer;
                margin-bottom: 15px;
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 8px;
            ">
                🎯 Completar meta ($${remainingToGoal.toLocaleString('es-MX', { minimumFractionDigits: 2 })})
            </button>
        ` : '';
        
        const { value: formValues } = await Swal.fire({
            title: `➕ Depositar en "${pattern.name}"`,
            html: `
                <div style="text-align: left; padding: 10px;">
                    <div style="background: #dcfce7; padding: 12px; border-radius: 8px; margin-bottom: 15px;">
                        <div style="font-size: 0.9em; color: #166534;">Balance actual del ahorro</div>
                        <div style="font-size: 1.5em; font-weight: bold; color: #15803d;">
                            $${currentBalance.toLocaleString('es-MX', { minimumFractionDigits: 2 })}
                        </div>
                    </div>
                    
                    ${goalInfoHTML}
                    
                    <div style="background: #f3f4f6; padding: 10px; border-radius: 8px; margin-bottom: 15px; font-size: 0.9em;">
                        <span style="color: #6b7280;">Balance disponible:</span>
                        <span style="font-weight: 600; color: ${availableBalance >= 0 ? '#059669' : '#dc2626'};">
                            $${availableBalance.toLocaleString('es-MX', { minimumFractionDigits: 2 })}
                        </span>
                    </div>
                    
                    ${completeGoalBtnHTML}
                    
                    <div style="margin-bottom: 15px;">
                        <label style="display: block; margin-bottom: 5px; font-weight: 600;">Monto a depositar:</label>
                        <input id="deposit-amount" type="number" step="0.01" min="0.01" class="swal2-input" style="margin: 0; width: 100%;" placeholder="0.00">
                    </div>
                    
                    <div style="margin-bottom: 15px;">
                        <label style="display: block; margin-bottom: 5px; font-weight: 600;">Notas (opcional):</label>
                        <input id="deposit-notes" type="text" class="swal2-input" style="margin: 0; width: 100%;" placeholder="Ej: Depósito manual">
                    </div>
                </div>
            `,
            showCancelButton: true,
            confirmButtonText: 'Depositar',
            cancelButtonText: 'Cancelar',
            confirmButtonColor: '#22c55e',
            didOpen: () => {
                // Evento para el botón de completar meta
                const completeBtn = document.getElementById('complete-goal-btn');
                if (completeBtn) {
                    completeBtn.addEventListener('click', () => {
                        document.getElementById('deposit-amount').value = remainingToGoal.toFixed(2);
                    });
                }
            },
            preConfirm: () => {
                const amount = document.getElementById('deposit-amount').value;
                const notes = document.getElementById('deposit-notes').value;
                
                if (!amount || parseFloat(amount) <= 0) {
                    Swal.showValidationMessage('El monto debe ser mayor a 0');
                    return false;
                }
                
                let finalAmount = parseFloat(amount);
                let excessAmount = 0;
                
                // Si hay meta y el monto excede lo necesario, limitar al máximo
                if (targetAmount > 0 && remainingToGoal > 0 && finalAmount > remainingToGoal) {
                    excessAmount = finalAmount - remainingToGoal;
                    finalAmount = remainingToGoal;
                }
                
                return { 
                    amount: finalAmount, 
                    notes: notes.trim() || null,
                    excessAmount: excessAmount 
                };
            }
        });
        
        if (formValues) {
            try {
                const result = await createSavingsDeposit(patternId, formValues.amount, formValues.notes);
                
                // Actualizar el indicador de balance
                if (window.refreshBalanceIndicator) window.refreshBalanceIndicator();
                
                // Verificar si se completó la meta
                const newBalance = result.new_balance;
                const goalCompleted = targetAmount > 0 && newBalance >= targetAmount;
                
                // Mensaje sobre el excedente si lo hay
                const excessMessage = formValues.excessAmount > 0 
                    ? `<p style="color: #059669; font-size: 0.9em;">💡 El excedente de <strong>$${formValues.excessAmount.toFixed(2)}</strong> se mantiene en tu balance.</p>` 
                    : '';
                
                if (goalCompleted) {
                    // Mostrar mensaje de meta completada y preguntar qué hacer
                    await handleGoalCompletion(patternId, pattern.name, newBalance, targetAmount);
                } else {
                    await Swal.fire({
                        icon: 'success',
                        title: '✅ Depósito Realizado',
                        html: `
                            <p>Se depositaron <strong>$${formValues.amount.toFixed(2)}</strong></p>
                            <p>Nuevo balance: <strong>$${newBalance.toFixed(2)}</strong></p>
                            ${targetAmount > 0 ? `<p style="color: #6b7280;">Progreso: ${((newBalance / targetAmount) * 100).toFixed(1)}%</p>` : ''}
                            ${excessMessage}
                        `,
                        timer: formValues.excessAmount > 0 ? 4000 : 2000,
                        showConfirmButton: formValues.excessAmount > 0
                    });
                    
                    await showSavingsManagementDialog();
                }
            } catch (error) {
                console.error('Error creating deposit:', error);
                await Swal.fire({
                    icon: 'error',
                    title: 'Error',
                    text: error.message || 'No se pudo realizar el depósito'
                });
            }
        }
    } catch (error) {
        console.error('Error showing deposit dialog:', error);
        await Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'No se pudo cargar el formulario de depósito'
        });
    }
}

/**
 * Maneja la finalización de una meta de ahorro
 */
async function handleGoalCompletion(patternId, patternName, currentBalance, targetAmount) {
    const { updateSavingsPattern, deleteSavingsPattern, createSavingsWithdrawal } = await import('./savings.js');
    
    const result = await Swal.fire({
        icon: 'success',
        title: '🎉 ¡Meta Completada!',
        html: `
            <div style="text-align: center; padding: 10px;">
                <p style="font-size: 1.1em; margin-bottom: 15px;">
                    Has alcanzado tu meta de ahorro en <strong>"${patternName}"</strong>
                </p>
                <div style="background: #dcfce7; padding: 15px; border-radius: 12px; margin-bottom: 15px;">
                    <div style="font-size: 2em; font-weight: bold; color: #15803d;">
                        $${currentBalance.toLocaleString('es-MX', { minimumFractionDigits: 2 })}
                    </div>
                    <div style="color: #166534; font-size: 0.9em;">de $${targetAmount.toLocaleString('es-MX', { minimumFractionDigits: 2 })}</div>
                </div>
                <p style="color: #374151;">¿Qué deseas hacer con este ahorro?</p>
            </div>
        `,
        showDenyButton: true,
        showCancelButton: true,
        confirmButtonText: '✅ Dar por concluido',
        denyButtonText: '📈 Ampliar meta',
        cancelButtonText: '🔄 Dejarlo así',
        confirmButtonColor: '#10b981',
        denyButtonColor: '#3b82f6',
        cancelButtonColor: '#6b7280'
    });
    
    if (result.isConfirmed) {
        // Dar por concluido: retirar el dinero al balance y eliminar el patrón
        const confirmDelete = await Swal.fire({
            icon: 'warning',
            title: '¿Confirmar conclusión?',
            html: `
                <p>Se retirará <strong>$${currentBalance.toLocaleString('es-MX', { minimumFractionDigits: 2 })}</strong> al balance general.</p>
                <p style="color: #dc2626; font-weight: 500;">El patrón de ahorro será eliminado.</p>
            `,
            showCancelButton: true,
            confirmButtonText: 'Sí, concluir',
            cancelButtonText: 'Cancelar',
            confirmButtonColor: '#dc2626'
        });
        
        if (confirmDelete.isConfirmed) {
            try {
                // Retirar todo el balance al balance general
                if (currentBalance > 0) {
                    await createSavingsWithdrawal(patternId, currentBalance, 'Retiro por conclusión de meta');
                }
                
                // Eliminar el patrón (hard delete)
                await deleteSavingsPattern(patternId, true);
                
                if (window.refreshBalanceIndicator) window.refreshBalanceIndicator();
                
                await Swal.fire({
                    icon: 'success',
                    title: '🎊 ¡Felicidades!',
                    html: `
                        <p>Has completado tu meta de ahorro.</p>
                        <p style="color: #059669; font-weight: 600;">
                            $${currentBalance.toLocaleString('es-MX', { minimumFractionDigits: 2 })} agregados a tu balance.
                        </p>
                    `,
                    timer: 3000,
                    showConfirmButton: true
                });
                
                await showSavingsManagementDialog();
            } catch (error) {
                console.error('Error completing savings goal:', error);
                await Swal.fire({
                    icon: 'error',
                    title: 'Error',
                    text: 'No se pudo completar la operación: ' + error.message
                });
            }
        }
    } else if (result.isDenied) {
        // Ampliar meta
        const { value: newTarget } = await Swal.fire({
            title: '📈 Ampliar Meta',
            html: `
                <div style="text-align: left; padding: 10px;">
                    <p style="margin-bottom: 15px;">Meta actual: <strong>$${targetAmount.toLocaleString('es-MX')}</strong></p>
                    <p style="margin-bottom: 15px;">Balance actual: <strong>$${currentBalance.toLocaleString('es-MX')}</strong></p>
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">Nueva meta:</label>
                    <input id="new-target" type="number" step="0.01" min="${currentBalance + 1}" value="${currentBalance + 1000}" class="swal2-input" style="margin: 0; width: 100%;">
                </div>
            `,
            showCancelButton: true,
            confirmButtonText: 'Actualizar Meta',
            cancelButtonText: 'Cancelar',
            confirmButtonColor: '#3b82f6',
            preConfirm: () => {
                const newTargetValue = document.getElementById('new-target').value;
                if (!newTargetValue || parseFloat(newTargetValue) <= currentBalance) {
                    Swal.showValidationMessage(`La nueva meta debe ser mayor al balance actual ($${currentBalance.toFixed(2)})`);
                    return false;
                }
                return parseFloat(newTargetValue);
            }
        });
        
        if (newTarget) {
            try {
                await updateSavingsPattern(patternId, { target_amount: newTarget });
                
                await Swal.fire({
                    icon: 'success',
                    title: '✅ Meta Actualizada',
                    html: `
                        <p>Nueva meta: <strong>$${newTarget.toLocaleString('es-MX', { minimumFractionDigits: 2 })}</strong></p>
                        <p style="color: #6b7280;">Progreso: ${((currentBalance / newTarget) * 100).toFixed(1)}%</p>
                    `,
                    timer: 2500,
                    showConfirmButton: false
                });
                
                await showSavingsManagementDialog();
            } catch (error) {
                console.error('Error updating target:', error);
                await Swal.fire({
                    icon: 'error',
                    title: 'Error',
                    text: 'No se pudo actualizar la meta: ' + error.message
                });
            }
        }
    } else {
        // Dejarlo así - solo mostrar el diálogo de gestión
        await showSavingsManagementDialog();
    }
}

/**
 * Modal para retirar de un patrón de ahorro
 */
async function showSavingsWithdrawalDialog(patternId, currentBalance) {
    try {
        const pattern = await getSavingsPatternById(patternId);
        
        const { value: formValues } = await Swal.fire({
            title: `➖ Retirar de "${pattern.name}"`,
            html: `
                <div style="text-align: left; padding: 10px;">
                    <div style="background: #fef3c7; padding: 12px; border-radius: 8px; margin-bottom: 15px;">
                        <div style="font-size: 0.9em; color: #92400e;">Balance disponible</div>
                        <div style="font-size: 1.5em; font-weight: bold; color: #b45309;">
                            $${parseFloat(pattern.current_balance).toLocaleString('es-MX', { minimumFractionDigits: 2 })}
                        </div>
                    </div>
                    
                    <div style="margin-bottom: 15px;">
                        <label style="display: block; margin-bottom: 5px; font-weight: 600;">Monto a retirar:</label>
                        <input id="withdraw-amount" type="number" step="0.01" min="0.01" max="${pattern.current_balance}" class="swal2-input" style="margin: 0; width: 100%;" placeholder="0.00">
                    </div>
                    
                    <div style="margin-bottom: 15px;">
                        <label style="display: block; margin-bottom: 5px; font-weight: 600;">Notas (opcional):</label>
                        <input id="withdraw-notes" type="text" class="swal2-input" style="margin: 0; width: 100%;" placeholder="Ej: Retiro para emergencia">
                    </div>
                    
                    <div style="background: #eff6ff; padding: 10px; border-radius: 8px; font-size: 0.85em; color: #1e40af;">
                        ℹ️ El retiro se registrará como un <strong>ingreso</strong> en tus movimientos.
                    </div>
                </div>
            `,
            showCancelButton: true,
            confirmButtonText: 'Retirar',
            cancelButtonText: 'Cancelar',
            confirmButtonColor: '#f59e0b',
            preConfirm: () => {
                const amount = document.getElementById('withdraw-amount').value;
                const notes = document.getElementById('withdraw-notes').value;
                
                if (!amount || parseFloat(amount) <= 0) {
                    Swal.showValidationMessage('El monto debe ser mayor a 0');
                    return false;
                }
                
                if (parseFloat(amount) > parseFloat(pattern.current_balance)) {
                    Swal.showValidationMessage(`El monto no puede exceder el balance disponible ($${pattern.current_balance})`);
                    return false;
                }
                
                return { amount: parseFloat(amount), notes: notes.trim() || null };
            }
        });
        
        if (formValues) {
            try {
                const result = await createSavingsWithdrawal(patternId, formValues.amount, formValues.notes);
                
                // Actualizar el indicador de balance
                if (window.refreshBalanceIndicator) window.refreshBalanceIndicator();
                
                await Swal.fire({
                    icon: 'success',
                    title: '✅ Retiro Realizado',
                    html: `
                        <p>Se retiraron <strong>$${formValues.amount.toFixed(2)}</strong></p>
                        <p>Nuevo balance: <strong>$${result.new_balance.toFixed(2)}</strong></p>
                        <p style="font-size: 0.9em; color: #666; margin-top: 10px;">
                            Se registró un ingreso en tus movimientos.
                        </p>
                    `,
                    timer: 2500,
                    showConfirmButton: true
                });
                
                await showSavingsManagementDialog();
            } catch (error) {
                console.error('Error creating withdrawal:', error);
                await Swal.fire({
                    icon: 'error',
                    title: 'Error',
                    text: error.message || 'No se pudo realizar el retiro'
                });
            }
        }
    } catch (error) {
        console.error('Error showing withdrawal dialog:', error);
        await Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'No se pudo cargar el formulario de retiro'
        });
    }
}
