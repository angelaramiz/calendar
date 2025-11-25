/**
 * calendar-modals-v2.js
 * Modales para el sistema V2 (confirmar ocurrencias, ver detalles)
 */

import { confirmPatternOccurrence } from './movements.js';
import { getMovementById, updateMovement, deleteMovement } from './movements.js';
import { getLoanById } from './loans-v2.js';
import { getPlanById } from './plans-v2.js';
import { createIncomePattern, createExpensePattern, getIncomePatternById, getExpensePatternById, updateIncomePattern, updateExpensePattern } from './patterns.js';
import { createPlan } from './plans-v2.js';

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
            
            await Swal.fire({
                icon: 'success',
                title: '✅ Confirmado',
                text: 'Evento confirmado exitosamente',
                timer: 1500,
                showConfirmButton: false
            });

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
 * Muestra detalles completos de un préstamo
 */
export async function showLoanDetails(loanId, onUpdated) {
    try {
        const { getLoanById, getLoanProgress } = await import('./loans-v2.js');
        const loan = await getLoanById(loanId);
        if (!loan) {
            throw new Error('Préstamo no encontrado');
        }

        const progress = await getLoanProgress(loanId);
        const kindLabel = loan.kind === 'favor' ? '➡️ Presté dinero' : '⬅️ Me prestaron';
        const paymentPlanLabel = {
            single: '📅 Pago único',
            recurring: '🔄 Pagos recurrentes',
            custom: '📋 Fechas personalizadas'
        }[loan.payment_plan];

        const result = await Swal.fire({
            title: `${kindLabel}: ${loan.person_name}`,
            html: `
                <div style="text-align: left; padding: 10px;">
                    ${loan.description ? `<p style="color: #666; margin-bottom: 15px;">${loan.description}</p>` : ''}
                    
                    <div style="background: #fef3c7; padding: 15px; border-radius: 8px; margin-bottom: 15px;">
                        <div style="margin-bottom: 10px;">
                            <strong>Monto total:</strong> 
                            <span style="color: #f59e0b; font-size: 1.3em;">$${loan.amount}</span>
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
                        
                        <div style="margin-bottom: 10px;">
                            <strong>Plan de pago:</strong> ${paymentPlanLabel}
                        </div>
                        
                        ${loan.payment_plan === 'single' ? `
                            <div style="margin-bottom: 10px;">
                                <strong>Días para recuperar:</strong> ${loan.recovery_days} días
                            </div>
                        ` : ''}
                        
                        ${loan.payment_plan === 'recurring' ? `
                            <div style="margin-bottom: 10px;">
                                <strong>Frecuencia:</strong> ${loan.payment_frequency} cada ${loan.payment_interval}
                            </div>
                            <div style="margin-bottom: 10px;">
                                <strong>Número de pagos:</strong> ${loan.payment_count}
                            </div>
                        ` : ''}
                        
                        <div style="margin-top: 15px; padding-top: 15px; border-top: 1px solid #d97706;">
                            <strong>Pagos registrados:</strong> ${loan.payment_movements?.length || 0}
                        </div>
                    </div>

                    ${loan.origin_movement ? `
                        <div style="background: #f3f4f6; padding: 10px; border-radius: 8px; margin-bottom: 10px;">
                            <strong>Fecha del préstamo:</strong><br/>
                            ${new Date(loan.origin_movement.date).toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
                        </div>
                    ` : ''}
                    
                    ${loan.payment_movements && loan.payment_movements.length > 0 ? `
                        <div style="background: #f3f4f6; padding: 10px; border-radius: 8px;">
                            <strong>Historial de pagos:</strong>
                            <ul style="margin: 10px 0; padding-left: 20px;">
                                ${loan.payment_movements.map(mov => `
                                    <li style="margin-bottom: 5px;">
                                        ${new Date(mov.date).toLocaleDateString('es-ES')} - $${mov.confirmed_amount}
                                    </li>
                                `).join('')}
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
 * Modal para registrar un pago de préstamo
 */
async function showRegisterLoanPaymentDialog(loan, onUpdated) {
    const { registerLoanPayment, getLoanProgress } = await import('./loans-v2.js');
    const progress = await getLoanProgress(loan.id);
    
    const { value: formValues } = await Swal.fire({
        title: `💸 Registrar Pago - ${loan.person_name}`,
        html: `
            <div style="text-align: left; padding: 10px;">
                <div style="background: #f3f4f6; padding: 10px; border-radius: 8px; margin-bottom: 15px;">
                    <p><strong>Tipo:</strong> ${loan.kind === 'favor' ? 'A favor (te deben)' : 'En contra (debes pagar)'}</p>
                    <p><strong>Total:</strong> $${loan.amount}</p>
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
                        ${loan.kind === 'favor' 
                            ? '💰 Se registrará un <strong>ingreso</strong> (dinero que recibes)' 
                            : '💸 Se registrará un <strong>gasto</strong> (dinero que pagas)'}
                    </p>
                </div>
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: 'Registrar Pago',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#10b981',
        width: '550px',
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
            
            return {
                amount: parseFloat(amount),
                date: date,
                description: document.getElementById('payment-description').value.trim() || null
            };
        }
    });
    
    if (formValues) {
        try {
            await registerLoanPayment(loan.id, formValues);
            
            const newProgress = await getLoanProgress(loan.id);
            
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

        const progress = parseFloat(plan.progress_percent) || 0;
        const progressColor = progress >= 100 ? '#10b981' : progress >= 50 ? '#3b82f6' : '#f59e0b';

        const result = await Swal.fire({
            title: `🎯 ${plan.title || 'Sin título'}`,
            html: `
                <div style="text-align: left; padding: 10px;">
                    ${plan.description ? `<p style="color: #666; margin-bottom: 15px;">${plan.description}</p>` : ''}
                    
                    <div style="background: #dbeafe; padding: 15px; border-radius: 8px; margin-bottom: 15px;">
                        <div style="margin-bottom: 15px;">
                            <strong>Objetivo:</strong> 
                            <span style="color: #3b82f6; font-size: 1.3em;">$${plan.target_amount}</span>
                        </div>
                        
                        <div style="margin-bottom: 15px;">
                            <strong>Ahorrado:</strong> 
                            <span style="color: #10b981; font-size: 1.2em;">$${plan.saved_amount || 0}</span>
                        </div>
                        
                        <div style="margin-bottom: 15px;">
                            <div style="background: #e5e7eb; height: 20px; border-radius: 10px; overflow: hidden;">
                                <div style="background: ${progressColor}; height: 100%; width: ${Math.min(progress, 100)}%; transition: width 0.3s;"></div>
                            </div>
                            <p style="text-align: center; margin-top: 5px; font-weight: 600; color: ${progressColor};">
                                ${progress.toFixed(1)}%
                            </p>
                        </div>
                        
                        ${plan.requested_target_date ? `
                            <div style="margin-bottom: 10px;">
                                <strong>Fecha deseada:</strong> ${new Date(plan.requested_target_date).toLocaleDateString('es-ES')}
                            </div>
                        ` : ''}
                        
                        ${plan.suggested_target_date ? `
                            <div style="margin-bottom: 10px;">
                                <strong>Fecha sugerida:</strong> ${new Date(plan.suggested_target_date).toLocaleDateString('es-ES')}
                            </div>
                        ` : ''}
                        
                        ${plan.category ? `
                            <div style="margin-bottom: 10px;">
                                <strong>Categoría:</strong> ${plan.category}
                            </div>
                        ` : ''}
                        
                        <div style="margin-top: 15px; padding-top: 15px; border-top: 1px solid #93c5fd;">
                            <strong>Prioridad:</strong> ${'⭐'.repeat(plan.priority)}
                        </div>
                    </div>

                    ${plan.income_sources && plan.income_sources.length > 0 ? `
                        <div style="background: #f3f4f6; padding: 10px; border-radius: 8px;">
                            <strong>Fuentes de ingreso asignadas:</strong>
                            <ul style="margin: 10px 0; padding-left: 20px;">
                                ${plan.income_sources.map(src => `
                                    <li>
                                        ${src.income_pattern.name}: 
                                        ${src.allocation_type === 'percent' 
                                            ? `${(src.allocation_value * 100).toFixed(0)}%` 
                                            : `$${src.allocation_value}`}
                                    </li>
                                `).join('')}
                            </ul>
                        </div>
                    ` : ''}
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
            // Editar plan
            await showEditPlanDialog(plan, onUpdated);
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
                        Título:
                    </label>
                    <input 
                        id="edit-plan-title" 
                        type="text" 
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        value="${plan.title || ''}"
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
                        value="${plan.requested_target_date || ''}"
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
                        <option value="planned" ${plan.status === 'planned' ? 'selected' : ''}>📋 Planeado</option>
                        <option value="active" ${plan.status === 'active' ? 'selected' : ''}>🚀 Activo</option>
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
            const title = document.getElementById('edit-plan-title').value.trim();
            const amount = document.getElementById('edit-plan-amount').value;
            
            if (!title) {
                Swal.showValidationMessage('El título es requerido');
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
                title,
                target_amount: parseFloat(amount),
                requested_target_date: document.getElementById('edit-plan-target-date').value || null,
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
                title: formValues.title,
                target_amount: formValues.target_amount,
                requested_target_date: formValues.requested_target_date,
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
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: 'Crear',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: type === 'ingreso' ? '#10b981' : '#ef4444',
        cancelButtonColor: '#6b7280',
        focusConfirm: false,
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
            
            return { 
                title: title.trim(),
                description: description.trim(),
                amount: parseFloat(amount), 
                category: category.trim(),
                date 
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

            const movement = await createMovement(movementData);
            
            await Swal.fire({
                icon: 'success',
                title: '✅ Creado',
                text: `${type === 'ingreso' ? 'Ingreso' : 'Gasto'} creado exitosamente`,
                timer: 1500,
                showConfirmButton: false
            });

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
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: 'Crear Patrón',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: isIncome ? '#10b981' : '#ef4444',
        width: '600px',
        preConfirm: () => {
            const name = document.getElementById('pattern-name').value.trim();
            const amount = document.getElementById('pattern-amount').value;
            const frequency = document.getElementById('pattern-frequency').value;
            const interval = document.getElementById('pattern-interval').value;
            const startDate = document.getElementById('pattern-start-date').value;
            const endDate = document.getElementById('pattern-end-date').value;
            
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
                description: document.getElementById('pattern-description').value.trim() || null
            };
        }
    });

    if (formValues) {
        try {
            const pattern = isIncome 
                ? await createIncomePattern(formValues)
                : await createExpensePattern(formValues);
            
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
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: 'Siguiente →',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#3b82f6',
        width: '600px',
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
                requested_target_date: document.getElementById('plan-target-date').value || null,
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
    
    // Construir opciones de ingresos
    const incomeOptionsHTML = incomePatterns.map(income => {
        // Obtener frecuencia en español
        const freqLabel = {
            'weekly': 'Semanal',
            'monthly': 'Mensual',
            'yearly': 'Anual'
        }[income.frequency] || income.frequency;
        
        return `
        <div style="margin-bottom: 12px; padding: 12px; background: #f9fafb; border-radius: 8px; border: 2px solid transparent;" class="income-option" data-income-id="${income.id}">
            <label style="display: flex; align-items: center; cursor: pointer;">
                <input 
                    type="checkbox" 
                    class="income-source-checkbox" 
                    data-income-id="${income.id}"
                    data-income-amount="${income.base_amount}"
                    data-income-frequency="${income.frequency}"
                    data-income-interval="${income.interval || 1}"
                    style="margin-right: 10px; width: 18px; height: 18px;"
                />
                <div style="flex: 1;">
                    <div style="font-weight: 600; color: #1f2937;">${income.name}</div>
                    <div style="font-size: 0.9em; color: #10b981; margin-top: 2px;">
                        💰 $${income.base_amount} · ${freqLabel}
                        ${income.interval > 1 ? ` (cada ${income.interval})` : ''}
                    </div>
                </div>
            </label>
            <div class="allocation-input" style="margin-top: 10px; display: none; padding-left: 28px;">
                <div style="display: flex; align-items: center; gap: 10px;">
                    <label style="font-size: 0.9em; color: #374151; min-width: 120px;">
                        Asignar:
                    </label>
                    <input 
                        type="number" 
                        class="income-allocation-percent" 
                        data-income-id="${income.id}"
                        min="1" 
                        max="100" 
                        value="100"
                        style="width: 70px; padding: 4px 8px; border: 1px solid #d1d5db; border-radius: 4px;"
                    />
                    <span style="color: #6b7280;">% del ingreso</span>
                </div>
                <div style="margin-top: 6px; font-size: 0.85em; color: #6b7280; padding-left: 130px;">
                    = $<span class="calculated-amount">0</span> por pago
                </div>
            </div>
        </div>
        `;
    }).join('');
    
    const { value: formValues } = await Swal.fire({
        title: '💰 Nueva Planeación - Paso 2 de 3',
        html: `
            <div style="text-align: left; padding: 10px;">
                <p style="color: #6b7280; margin-bottom: 15px;">
                    <strong style="color: #1f2937;">Meta:</strong> ${step1Data.title} · <strong>$${step1Data.target_amount}</strong>
                </p>
                
                <p style="color: #374151; margin-bottom: 20px; font-weight: 600;">
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
                    const percent = parseFloat(percentInput.value) || 100;
                    const allocated = (amount * percent / 100);
                    
                    // Convertir a mensual aproximado
                    let monthlyAmount = allocated;
                    if (frequency === 'weekly') {
                        monthlyAmount = (allocated / interval) * 4.33;
                    } else if (frequency === 'yearly') {
                        monthlyAmount = allocated / 12 / interval;
                    } else if (frequency === 'monthly') {
                        monthlyAmount = allocated / interval;
                    }
                    
                    totalPerMonth += monthlyAmount;
                    
                    const option = document.querySelector(`.income-option[data-income-id="${incomeId}"]`);
                    const name = option.querySelector('label div div').textContent;
                    items.push(`• ${name}: $${allocated.toFixed(2)}`);
                });
                
                summaryContent.innerHTML = `
                    ${items.join('<br>')}
                    <div style="margin-top: 10px; padding-top: 10px; border-top: 1px solid #93c5fd; font-weight: 600;">
                        Total aprox. mensual: $${totalPerMonth.toFixed(2)}
                    </div>
                `;
            }
            
            checkboxes.forEach(checkbox => {
                checkbox.addEventListener('change', (e) => {
                    const container = e.target.closest('.income-option');
                    const allocationInput = container.querySelector('.allocation-input');
                    const incomeId = e.target.dataset.incomeId;
                    
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
                
                // Listener para cambios en porcentaje
                const incomeId = checkbox.dataset.incomeId;
                const percentInput = document.querySelector(`.income-allocation-percent[data-income-id="${incomeId}"]`);
                const amountSpan = percentInput.closest('.allocation-input').querySelector('.calculated-amount');
                const baseAmount = parseFloat(checkbox.dataset.incomeAmount);
                
                percentInput.addEventListener('input', () => {
                    const percent = parseFloat(percentInput.value) || 0;
                    const calculated = (baseAmount * percent / 100).toFixed(2);
                    amountSpan.textContent = calculated;
                    updateSummary();
                });
                
                // Calcular inicial
                const percent = parseFloat(percentInput.value) || 100;
                const calculated = (baseAmount * percent / 100).toFixed(2);
                amountSpan.textContent = calculated;
            });
        },
        preConfirm: () => {
            const income_sources = [];
            const checkedBoxes = document.querySelectorAll('.income-source-checkbox:checked');
            
            checkedBoxes.forEach(checkbox => {
                const incomeId = checkbox.dataset.incomeId;
                const amount = parseFloat(checkbox.dataset.incomeAmount);
                const frequency = checkbox.dataset.incomeFrequency;
                const interval = parseInt(checkbox.dataset.incomeInterval) || 1;
                const percentInput = document.querySelector(`.income-allocation-percent[data-income-id="${incomeId}"]`);
                const percentage = parseFloat(percentInput.value) || 100;
                
                income_sources.push({
                    income_pattern_id: incomeId,
                    allocation_type: 'percent',
                    allocation_value: percentage / 100,
                    _metadata: { // Info adicional para cálculos
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
        return await showPlanStep1(step1Data.requested_target_date);
    }
    
    return formValues;
}

/**
 * PASO 3: Cálculo de viabilidad y confirmación
 */
async function showPlanStep3(step2Data) {
    // Calcular viabilidad
    const calculation = calculatePlanViability(step2Data);
    
    const isViable = calculation.canAchieveByRequestedDate;
    const statusColor = isViable ? '#10b981' : '#f59e0b';
    const statusIcon = isViable ? '✅' : '⚠️';
    
    const { value: confirmed } = await Swal.fire({
        title: '📊 Nueva Planeación - Paso 3 de 3',
        html: `
            <div style="text-align: left; padding: 10px;">
                <div style="background: #f3f4f6; padding: 15px; border-radius: 8px; margin-bottom: 20px;">
                    <h3 style="margin: 0 0 10px 0; color: #1f2937;">📋 Resumen de tu planeación:</h3>
                    <div style="color: #374151; line-height: 1.8;">
                        <div><strong>Meta:</strong> ${step2Data.title}</div>
                        <div><strong>Monto objetivo:</strong> $${step2Data.target_amount.toFixed(2)}</div>
                        ${step2Data.requested_target_date ? `<div><strong>Fecha deseada:</strong> ${new Date(step2Data.requested_target_date).toLocaleDateString('es-ES')}</div>` : ''}
                        <div><strong>Prioridad:</strong> ${'⭐'.repeat(step2Data.priority)}</div>
                        ${step2Data.category ? `<div><strong>Categoría:</strong> ${step2Data.category}</div>` : ''}
                    </div>
                </div>
                
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
                        
                        ${!isViable ? `
                            <div style="margin-top: 15px; padding: 10px; background: white; border-radius: 6px; color: #92400e;">
                                <strong>⚠️ Nota:</strong> Con los ingresos seleccionados, la meta se alcanzaría aproximadamente el 
                                <strong>${calculation.suggestedDate.toLocaleDateString('es-ES')}</strong>.
                                ${step2Data.requested_target_date ? `
                                    Esto es después de tu fecha deseada (${new Date(step2Data.requested_target_date).toLocaleDateString('es-ES')}).
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
                                ${step2Data.requested_target_date ? 'antes de la fecha deseada' : 'en el tiempo estimado'}.
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
        confirmButtonText: isViable ? '✅ Crear Planeación' : '📝 Crear de todas formas',
        denyButtonText: '← Ajustar',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: isViable ? '#10b981' : '#f59e0b',
        denyButtonColor: '#6b7280',
        width: '700px'
    });
    
    if (confirmed === false) {
        // Presionó "Ajustar" - volver al paso 2
        return await showPlanStep2(step2Data);
    }
    
    if (!confirmed) {
        return null; // Canceló
    }
    
    // Agregar la fecha sugerida calculada
    return {
        ...step2Data,
        suggested_target_date: calculation.suggestedDate.toISOString().split('T')[0],
        status: 'active'
    };
}

/**
 * Calcula la viabilidad de un plan basado en los ingresos asignados
 */
function calculatePlanViability(planData) {
    if (!planData.income_sources || planData.income_sources.length === 0) {
        return {
            canAchieveByRequestedDate: false,
            monthlyContribution: 0,
            monthsNeeded: 0,
            suggestedDate: new Date(),
            message: 'Sin ingresos asignados'
        };
    }
    
    // Calcular aporte mensual total
    let monthlyContribution = 0;
    
    planData.income_sources.forEach(source => {
        const { base_amount, frequency, interval } = source._metadata;
        const allocated = base_amount * source.allocation_value;
        
        // Convertir a mensual
        let monthly = allocated;
        if (frequency === 'weekly') {
            monthly = (allocated / interval) * 4.33; // 4.33 semanas promedio por mes
        } else if (frequency === 'yearly') {
            monthly = allocated / 12 / interval;
        } else if (frequency === 'monthly') {
            monthly = allocated / interval;
        }
        
        monthlyContribution += monthly;
    });
    
    // Calcular meses necesarios
    const monthsNeeded = monthlyContribution > 0 
        ? Math.ceil(planData.target_amount / monthlyContribution)
        : 999;
    
    // Calcular fecha sugerida
    const suggestedDate = new Date();
    suggestedDate.setMonth(suggestedDate.getMonth() + monthsNeeded);
    
    // Verificar si es viable para la fecha solicitada
    const canAchieveByRequestedDate = planData.requested_target_date
        ? suggestedDate <= new Date(planData.requested_target_date)
        : true;
    
    return {
        canAchieveByRequestedDate,
        monthlyContribution,
        monthsNeeded,
        suggestedDate
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
                            <span style="color: ${typeColor}; font-size: 1.2em;">$${pattern.base_amount}</span>
                        </div>
                        
                        <div style="margin-bottom: 10px;">
                            <strong>Frecuencia:</strong> ${frequencyLabels[pattern.frequency] || pattern.frequency}
                            ${pattern.interval > 1 ? ` (cada ${pattern.interval})` : ''}
                        </div>
                        
                        <div style="margin-bottom: 10px;">
                            <strong>Fecha inicio:</strong> ${new Date(pattern.start_date).toLocaleDateString('es-ES')}
                        </div>
                        
                        ${pattern.end_date ? `
                            <div style="margin-bottom: 10px;">
                                <strong>Fecha límite:</strong> ${new Date(pattern.end_date).toLocaleDateString('es-ES')}
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
                </div>
            `,
            showCancelButton: true,
            confirmButtonText: 'Editar',
            cancelButtonText: 'Cerrar',
            confirmButtonColor: '#3b82f6',
            cancelButtonColor: '#6b7280'
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
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: 'Guardar',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#10b981',
        width: '600px',
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
// MODAL: CREAR PRÉSTAMO
// ============================================================================

/**
 * Modal para crear un nuevo préstamo (a favor / en contra)
 */
async function showCreateLoanDialog(dateISO, onCreated) {
    const { createLoan } = await import('./loans-v2.js');
    
    const { value: formValues } = await Swal.fire({
        title: '💰 Nuevo Préstamo',
        html: `
            <div style="text-align: left; padding: 10px;">
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Tipo de préstamo:
                    </label>
                    <select id="loan-kind" class="swal2-select" style="width: 100%;">
                        <option value="favor">➡️ A favor (Yo presto dinero)</option>
                        <option value="contra">⬅️ En contra (Me prestan dinero)</option>
                    </select>
                    <p style="font-size: 0.85em; color: #666; margin-top: 5px;">
                        <strong>A favor:</strong> Prestas dinero y luego te lo devuelven.<br/>
                        <strong>En contra:</strong> Te prestan dinero y lo debes devolver.
                    </p>
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Persona:
                    </label>
                    <input 
                        id="loan-person" 
                        type="text" 
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="Nombre de la persona"
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
                        Plan de pago:
                    </label>
                    <select id="loan-payment-plan" class="swal2-select" style="width: 100%;">
                        <option value="single">📅 Pago único</option>
                        <option value="recurring">🔄 Pagos recurrentes</option>
                        <option value="custom">📋 Fechas personalizadas</option>
                    </select>
                </div>

                <!-- Opciones para "Pago único" -->
                <div id="single-payment-options" style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Días para recuperar:
                    </label>
                    <input 
                        id="loan-recovery-days" 
                        type="number" 
                        min="1"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="Ej: 30"
                    />
                </div>

                <!-- Opciones para "Pagos recurrentes" -->
                <div id="recurring-payment-options" style="margin-bottom: 15px; display: none;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Frecuencia:
                    </label>
                    <select id="loan-payment-frequency" class="swal2-select" style="width: 100%; margin-bottom: 10px;">
                        <option value="weekly">Semanal</option>
                        <option value="monthly" selected>Mensual</option>
                        <option value="yearly">Anual</option>
                    </select>
                    
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Intervalo:
                    </label>
                    <input 
                        id="loan-payment-interval" 
                        type="number" 
                        min="1"
                        value="1"
                        class="swal2-input" 
                        style="margin: 0; width: 100%; margin-bottom: 10px;"
                        placeholder="Cada cuántos períodos"
                    />
                    
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Número de pagos:
                    </label>
                    <input 
                        id="loan-payment-count" 
                        type="number" 
                        min="1"
                        class="swal2-input" 
                        style="margin: 0; width: 100%;"
                        placeholder="Ej: 12"
                    />
                </div>

                <!-- Opciones para "Fechas personalizadas" -->
                <div id="custom-payment-options" style="margin-bottom: 15px; display: none;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Fechas de pago (una por línea):
                    </label>
                    <textarea 
                        id="loan-custom-dates" 
                        class="swal2-textarea" 
                        style="margin: 0; width: 100%; min-height: 80px;"
                        placeholder="2025-01-15&#10;2025-02-15&#10;2025-03-15"
                    ></textarea>
                    <p style="font-size: 0.85em; color: #666; margin-top: 5px;">
                        Formato: YYYY-MM-DD (una fecha por línea)
                    </p>
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: 600;">
                        Interés (opcional):
                    </label>
                    <div style="display: flex; gap: 10px;">
                        <div style="flex: 1;">
                            <input 
                                id="loan-interest-value" 
                                type="number" 
                                step="0.01"
                                class="swal2-input" 
                                style="margin: 0; width: 100%;"
                                placeholder="Monto fijo"
                            />
                        </div>
                        <div style="flex: 1;">
                            <input 
                                id="loan-interest-percent" 
                                type="number" 
                                step="0.01"
                                min="0"
                                max="100"
                                class="swal2-input" 
                                style="margin: 0; width: 100%;"
                                placeholder="% del monto"
                            />
                        </div>
                    </div>
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
        width: '650px',
        didOpen: () => {
            const paymentPlanSelect = document.getElementById('loan-payment-plan');
            const singleOptions = document.getElementById('single-payment-options');
            const recurringOptions = document.getElementById('recurring-payment-options');
            const customOptions = document.getElementById('custom-payment-options');
            
            // Mostrar/ocultar opciones según el plan de pago
            paymentPlanSelect.addEventListener('change', () => {
                const plan = paymentPlanSelect.value;
                singleOptions.style.display = plan === 'single' ? 'block' : 'none';
                recurringOptions.style.display = plan === 'recurring' ? 'block' : 'none';
                customOptions.style.display = plan === 'custom' ? 'block' : 'none';
            });
        },
        preConfirm: () => {
            const kind = document.getElementById('loan-kind').value;
            const person = document.getElementById('loan-person').value.trim();
            const amount = document.getElementById('loan-amount').value;
            const loanDate = document.getElementById('loan-date').value;
            const paymentPlan = document.getElementById('loan-payment-plan').value;
            
            if (!person) {
                Swal.showValidationMessage('Ingresa el nombre de la persona');
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
                kind,
                person_name: person,
                amount: parseFloat(amount),
                loan_date: loanDate,
                payment_plan: paymentPlan,
                description: document.getElementById('loan-description').value.trim() || null
            };
            
            // Validar y agregar campos según el plan de pago
            if (paymentPlan === 'single') {
                const recoveryDays = document.getElementById('loan-recovery-days').value;
                if (!recoveryDays || parseInt(recoveryDays) <= 0) {
                    Swal.showValidationMessage('Ingresa los días para recuperar');
                    return false;
                }
                loanData.recovery_days = parseInt(recoveryDays);
            } else if (paymentPlan === 'recurring') {
                const paymentCount = document.getElementById('loan-payment-count').value;
                if (!paymentCount || parseInt(paymentCount) <= 0) {
                    Swal.showValidationMessage('Ingresa el número de pagos');
                    return false;
                }
                loanData.payment_frequency = document.getElementById('loan-payment-frequency').value;
                loanData.payment_interval = parseInt(document.getElementById('loan-payment-interval').value) || 1;
                loanData.payment_count = parseInt(paymentCount);
            } else if (paymentPlan === 'custom') {
                const customDatesText = document.getElementById('loan-custom-dates').value.trim();
                if (!customDatesText) {
                    Swal.showValidationMessage('Ingresa al menos una fecha de pago');
                    return false;
                }
                const dates = customDatesText.split('\n').map(d => d.trim()).filter(d => d);
                if (dates.length === 0) {
                    Swal.showValidationMessage('Ingresa al menos una fecha de pago');
                    return false;
                }
                loanData.custom_dates = dates;
            }
            
            // Agregar interés si se especificó
            const interestValue = document.getElementById('loan-interest-value').value;
            const interestPercent = document.getElementById('loan-interest-percent').value;
            
            if (interestValue && parseFloat(interestValue) > 0) {
                loanData.interest_value = parseFloat(interestValue);
            }
            if (interestPercent && parseFloat(interestPercent) > 0) {
                loanData.interest_percent = parseFloat(interestPercent);
            }
            
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
                        <p><strong>Tipo:</strong> ${formValues.kind === 'favor' ? 'A favor (prestaste)' : 'En contra (te prestaron)'}</p>
                        <p><strong>Persona:</strong> ${formValues.person_name}</p>
                        <p><strong>Monto:</strong> $${formValues.amount}</p>
                        <p style="margin-top: 10px; color: #666; font-size: 0.9em;">
                            ${formValues.kind === 'favor' 
                                ? '✅ Se registró un <strong>gasto</strong> en tu calendario (dinero que salió).' 
                                : '✅ Se registró un <strong>ingreso</strong> en tu calendario (dinero que entró).'}
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
