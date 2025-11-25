/**
 * loans-v2.js
 * Gestión de préstamos con planes de pago (V2)
 */

import { supabase } from './supabase-client.js';
import { createMovement } from './movements.js';

// ============================================================================
// LOANS CRUD
// ============================================================================

/**
 * Obtiene todos los loans del usuario
 */
export async function getLoans(filters = {}) {
    try {
        let query = supabase
            .from('loans')
            .select('*, origin_movement:movements!loans_origin_movement_id_fkey(*)')
            .order('created_at', { ascending: false });

        if (filters.kind) {
            query = query.eq('kind', filters.kind);
        }
        if (filters.payment_plan) {
            query = query.eq('payment_plan', filters.payment_plan);
        }

        const { data, error } = await query;
        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching loans:', error);
        throw error;
    }
}

/**
 * Obtiene un loan por ID con sus movements relacionados
 */
export async function getLoanById(id) {
    try {
        const { data, error } = await supabase
            .from('loans')
            .select(`
                *,
                origin_movement:movements!loans_origin_movement_id_fkey(*),
                payment_movements:movements!movements_loan_id_fkey(*)
            `)
            .eq('id', id)
            .single();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error fetching loan:', error);
        throw error;
    }
}

/**
 * Crea un nuevo loan y su movement de origen automáticamente
 */
export async function createLoan(loanData) {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');

        // Validar datos del préstamo
        validateLoanData(loanData);

        // 1. Crear el movement de origen
        const originMovement = await createMovement({
            title: loanData.kind === 'favor' 
                ? `Préstamo a ${loanData.person_name}`
                : `Préstamo de ${loanData.person_name}`,
            description: loanData.description || `Préstamo: ${loanData.amount}`,
            type: loanData.kind === 'favor' ? 'gasto' : 'ingreso', // favor = dinero sale, contra = dinero entra
            category: 'Préstamos',
            date: loanData.loan_date || new Date().toISOString().split('T')[0],
            confirmed_amount: parseFloat(loanData.amount),
            expected_amount: parseFloat(loanData.amount),
            confirmed: true
        });

        // 2. Crear el loan
        const loan = {
            user_id: user.id,
            amount: parseFloat(loanData.amount),
            person_name: loanData.person_name,
            description: loanData.description || null,
            kind: loanData.kind,
            payment_plan: loanData.payment_plan,
            origin_movement_id: originMovement.id
        };

        // Agregar campos según el tipo de payment_plan
        if (loanData.payment_plan === 'single') {
            loan.recovery_days = parseInt(loanData.recovery_days);
        } else if (loanData.payment_plan === 'recurring') {
            loan.payment_frequency = loanData.payment_frequency;
            loan.payment_interval = parseInt(loanData.payment_interval) || 1;
            loan.payment_count = parseInt(loanData.payment_count);
        } else if (loanData.payment_plan === 'custom') {
            loan.custom_dates = loanData.custom_dates;
        }

        const { data: loanCreated, error } = await supabase
            .from('loans')
            .insert([loan])
            .select()
            .single();

        if (error) {
            // Si falla la creación del loan, eliminar el movement de origen
            await supabase.from('movements').delete().eq('id', originMovement.id);
            throw error;
        }

        return loanCreated;
    } catch (error) {
        console.error('Error creating loan:', error);
        throw error;
    }
}

/**
 * Actualiza un loan existente
 */
export async function updateLoan(id, updates) {
    try {
        const loan = {};

        if (updates.person_name !== undefined) loan.person_name = updates.person_name;
        if (updates.description !== undefined) loan.description = updates.description;
        
        // No permitir cambiar amount, kind o payment_plan después de creado
        // (requeriría lógica compleja de actualización de movements)

        const { data, error } = await supabase
            .from('loans')
            .update(loan)
            .eq('id', id)
            .select()
            .single();

        if (error) throw error;
        return data;
    } catch (error) {
        console.error('Error updating loan:', error);
        throw error;
    }
}

/**
 * Elimina un loan y sus movements relacionados
 */
export async function deleteLoan(id) {
    try {
        // Los movements relacionados se eliminarán automáticamente por CASCADE
        const { error } = await supabase
            .from('loans')
            .delete()
            .eq('id', id);

        if (error) throw error;
        return true;
    } catch (error) {
        console.error('Error deleting loan:', error);
        throw error;
    }
}

// ============================================================================
// LOAN PAYMENTS
// ============================================================================

/**
 * Registra un pago de un préstamo
 */
export async function registerLoanPayment(loanId, paymentData) {
    try {
        const loan = await getLoanById(loanId);
        if (!loan) throw new Error('Préstamo no encontrado');

        // Crear movement de pago
        const payment = await createMovement({
            title: paymentData.title || `Pago - ${loan.person_name}`,
            description: paymentData.description || `Pago de préstamo`,
            type: loan.kind === 'favor' ? 'ingreso' : 'gasto', // favor = recibo pago, contra = pago
            category: 'Préstamos',
            date: paymentData.date,
            confirmed_amount: parseFloat(paymentData.amount),
            expected_amount: parseFloat(paymentData.expected_amount || paymentData.amount),
            loan_id: loanId,
            is_loan_counterpart: true,
            confirmed: true
        });

        return payment;
    } catch (error) {
        console.error('Error registering loan payment:', error);
        throw error;
    }
}

/**
 * Calcula el progreso de pago de un préstamo
 */
export async function getLoanProgress(loanId) {
    try {
        const loan = await getLoanById(loanId);
        if (!loan) throw new Error('Préstamo no encontrado');

        // Sumar todos los payment_movements
        const totalPaid = loan.payment_movements?.reduce((sum, mov) => {
            return sum + parseFloat(mov.confirmed_amount);
        }, 0) || 0;

        const remaining = parseFloat(loan.amount) - totalPaid;
        const progress = (totalPaid / parseFloat(loan.amount)) * 100;

        return {
            total: parseFloat(loan.amount),
            paid: totalPaid,
            remaining: remaining > 0 ? remaining : 0,
            progress: Math.min(progress, 100),
            is_complete: remaining <= 0
        };
    } catch (error) {
        console.error('Error calculating loan progress:', error);
        throw error;
    }
}

/**
 * Genera las fechas de pago esperadas según el payment_plan
 */
export function generatePaymentDates(loan) {
    const dates = [];
    
    if (!loan.origin_movement) {
        return dates;
    }

    const originDate = new Date(loan.origin_movement.date);

    if (loan.payment_plan === 'single') {
        // Un solo pago después de recovery_days
        const paymentDate = new Date(originDate);
        paymentDate.setDate(paymentDate.getDate() + loan.recovery_days);
        dates.push({
            date: paymentDate.toISOString().split('T')[0],
            amount: parseFloat(loan.amount)
        });
    } else if (loan.payment_plan === 'recurring') {
        // Pagos recurrentes
        const amountPerPayment = parseFloat(loan.amount) / loan.payment_count;
        let currentDate = new Date(originDate);

        for (let i = 0; i < loan.payment_count; i++) {
            // Calcular siguiente fecha según frequency
            if (loan.payment_frequency === 'daily') {
                currentDate.setDate(currentDate.getDate() + (loan.payment_interval || 1));
            } else if (loan.payment_frequency === 'weekly') {
                currentDate.setDate(currentDate.getDate() + (7 * (loan.payment_interval || 1)));
            } else if (loan.payment_frequency === 'monthly') {
                currentDate.setMonth(currentDate.getMonth() + (loan.payment_interval || 1));
            } else if (loan.payment_frequency === 'yearly') {
                currentDate.setFullYear(currentDate.getFullYear() + (loan.payment_interval || 1));
            }

            dates.push({
                date: currentDate.toISOString().split('T')[0],
                amount: amountPerPayment
            });
        }
    } else if (loan.payment_plan === 'custom') {
        // Fechas personalizadas desde JSONB
        return loan.custom_dates || [];
    }

    return dates;
}

/**
 * Obtiene los pagos pendientes de un préstamo
 */
export async function getPendingPayments(loanId) {
    try {
        const loan = await getLoanById(loanId);
        if (!loan) throw new Error('Préstamo no encontrado');

        const expectedDates = generatePaymentDates(loan);
        const paidDates = new Set(loan.payment_movements?.map(m => m.date) || []);

        // Filtrar solo las fechas que no han sido pagadas
        const pending = expectedDates.filter(expected => !paidDates.has(expected.date));

        return pending;
    } catch (error) {
        console.error('Error getting pending payments:', error);
        throw error;
    }
}

// ============================================================================
// VALIDACIÓN
// ============================================================================

/**
 * Valida los datos de un loan
 */
function validateLoanData(data) {
    const requiredFields = ['amount', 'person_name', 'kind', 'payment_plan'];
    
    for (const field of requiredFields) {
        if (!data[field]) {
            throw new Error(`Campo requerido: ${field}`);
        }
    }

    if (parseFloat(data.amount) <= 0) {
        throw new Error('El monto debe ser mayor a 0');
    }

    const validKinds = ['favor', 'contra'];
    if (!validKinds.includes(data.kind)) {
        throw new Error(`Tipo de préstamo inválido. Debe ser: ${validKinds.join(', ')}`);
    }

    const validPlans = ['single', 'recurring', 'custom'];
    if (!validPlans.includes(data.payment_plan)) {
        throw new Error(`Plan de pago inválido. Debe ser: ${validPlans.join(', ')}`);
    }

    // Validar campos según payment_plan
    if (data.payment_plan === 'single') {
        if (!data.recovery_days || parseInt(data.recovery_days) <= 0) {
            throw new Error('recovery_days es requerido y debe ser mayor a 0 para payment_plan=single');
        }
    } else if (data.payment_plan === 'recurring') {
        if (!data.payment_frequency) {
            throw new Error('payment_frequency es requerido para payment_plan=recurring');
        }
        if (!data.payment_count || parseInt(data.payment_count) <= 0) {
            throw new Error('payment_count es requerido y debe ser mayor a 0 para payment_plan=recurring');
        }
        const validFrequencies = ['daily', 'weekly', 'monthly', 'yearly'];
        if (!validFrequencies.includes(data.payment_frequency)) {
            throw new Error(`payment_frequency inválido. Debe ser: ${validFrequencies.join(', ')}`);
        }
    } else if (data.payment_plan === 'custom') {
        if (!data.custom_dates || !Array.isArray(data.custom_dates) || data.custom_dates.length === 0) {
            throw new Error('custom_dates es requerido y debe ser un array no vacío para payment_plan=custom');
        }
    }
}

// ============================================================================
// UTILIDADES
// ============================================================================

/**
 * Obtiene estadísticas de préstamos
 */
export async function getLoanStatistics() {
    try {
        const loans = await getLoans();
        
        const stats = {
            total_loans: loans.length,
            favor: { count: 0, total: 0, paid: 0, pending: 0 },
            contra: { count: 0, total: 0, paid: 0, pending: 0 }
        };

        for (const loan of loans) {
            const progress = await getLoanProgress(loan.id);
            const kind = loan.kind;
            
            stats[kind].count += 1;
            stats[kind].total += parseFloat(loan.amount);
            stats[kind].paid += progress.paid;
            stats[kind].pending += progress.remaining;
        }

        return stats;
    } catch (error) {
        console.error('Error calculating loan statistics:', error);
        throw error;
    }
}
