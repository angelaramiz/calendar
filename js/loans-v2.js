/**
 * loans-v2.js
 * Gestión de préstamos (V2)
 * Compatible con esquema V2:
 * - name, type (given/received), counterparty, original_amount, remaining_amount
 * - loan_date, due_date, status (active/paid/defaulted/cancelled)
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
            .select('*')
            .order('created_at', { ascending: false });

        if (filters.type) {
            query = query.eq('type', filters.type);
        }
        if (filters.status) {
            query = query.eq('status', filters.status);
        }
        if (filters.counterparty) {
            query = query.ilike('counterparty', `%${filters.counterparty}%`);
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
 * Obtiene loans activos
 */
export async function getActiveLoans() {
    return getLoans({ status: 'active' });
}

/**
 * Obtiene un loan por ID
 */
export async function getLoanById(id) {
    try {
        const { data, error } = await supabase
            .from('loans')
            .select('*')
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
 * Crea un nuevo loan
 * @param {Object} loanData - Datos del préstamo
 * @param {string} loanData.name - Nombre/descripción del préstamo
 * @param {string} loanData.type - 'given' (prestado a alguien) o 'received' (recibido de alguien)
 * @param {string} loanData.counterparty - A quién o de quién es el préstamo
 * @param {number} loanData.original_amount - Monto original
 * @param {string} loanData.loan_date - Fecha del préstamo
 * @param {string} [loanData.due_date] - Fecha de vencimiento (opcional)
 * @param {string} [loanData.description] - Descripción adicional
 */
export async function createLoan(loanData) {
    try {
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) throw new Error('Usuario no autenticado');

        // Validar datos del préstamo
        validateLoanData(loanData);

        const loan = {
            user_id: user.id,
            name: loanData.name,
            description: loanData.description || null,
            type: loanData.type, // 'given' o 'received'
            counterparty: loanData.counterparty,
            original_amount: parseFloat(loanData.original_amount),
            remaining_amount: parseFloat(loanData.original_amount), // Inicialmente igual al original
            loan_date: loanData.loan_date,
            due_date: loanData.due_date || null,
            status: 'active'
        };

        const { data, error } = await supabase
            .from('loans')
            .insert([loan])
            .select()
            .single();

        if (error) throw error;

        // Opcionalmente crear el movement de origen
        if (loanData.createOriginMovement !== false) {
            try {
                await createMovement({
                    title: loanData.type === 'given' 
                        ? `Préstamo a ${loanData.counterparty}`
                        : `Préstamo de ${loanData.counterparty}`,
                    description: loanData.description || `Préstamo: $${loanData.original_amount}`,
                    type: loanData.type === 'given' ? 'gasto' : 'ingreso',
                    category: 'Préstamos',
                    date: loanData.loan_date,
                    confirmed_amount: parseFloat(loanData.original_amount),
                    expected_amount: parseFloat(loanData.original_amount),
                    loan_id: data.id,
                    confirmed: true
                });
            } catch (movError) {
                console.warn('No se pudo crear movement de origen:', movError);
            }
        }

        return data;
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

        if (updates.name !== undefined) loan.name = updates.name;
        if (updates.description !== undefined) loan.description = updates.description;
        if (updates.counterparty !== undefined) loan.counterparty = updates.counterparty;
        if (updates.due_date !== undefined) loan.due_date = updates.due_date;
        if (updates.status !== undefined) loan.status = updates.status;
        if (updates.remaining_amount !== undefined) {
            loan.remaining_amount = parseFloat(updates.remaining_amount);
        }

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
 * Elimina un loan
 */
export async function deleteLoan(id) {
    try {
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

        const paymentAmount = parseFloat(paymentData.amount);
        
        // Validar que el pago no exceda el monto restante
        if (paymentAmount > loan.remaining_amount) {
            throw new Error(`El pago ($${paymentAmount}) excede el monto restante ($${loan.remaining_amount})`);
        }

        // Crear movement de pago
        const payment = await createMovement({
            title: paymentData.title || `Pago - ${loan.counterparty}`,
            description: paymentData.description || `Pago de préstamo: ${loan.name}`,
            type: loan.type === 'given' ? 'ingreso' : 'gasto', // given = recibo pago, received = hago pago
            category: 'Préstamos',
            date: paymentData.date || new Date().toISOString().split('T')[0],
            confirmed_amount: paymentAmount,
            expected_amount: paymentData.expected_amount ? parseFloat(paymentData.expected_amount) : paymentAmount,
            loan_id: loanId,
            confirmed: paymentData.confirmed !== false
        });

        // Actualizar remaining_amount del loan
        const newRemaining = loan.remaining_amount - paymentAmount;
        const newStatus = newRemaining <= 0 ? 'paid' : 'active';

        await updateLoan(loanId, {
            remaining_amount: Math.max(0, newRemaining),
            status: newStatus
        });

        return {
            payment,
            loan: {
                ...loan,
                remaining_amount: Math.max(0, newRemaining),
                status: newStatus
            }
        };
    } catch (error) {
        console.error('Error registering loan payment:', error);
        throw error;
    }
}

/**
 * Obtiene los pagos de un préstamo
 */
export async function getLoanPayments(loanId) {
    try {
        const { data, error } = await supabase
            .from('movements')
            .select('*')
            .eq('loan_id', loanId)
            .order('date', { ascending: true });

        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching loan payments:', error);
        throw error;
    }
}

// ============================================================================
// LOAN STATISTICS
// ============================================================================

/**
 * Obtiene resumen de préstamos
 */
export async function getLoansSummary() {
    try {
        const loans = await getLoans();
        
        const summary = {
            given: {
                count: 0,
                total_original: 0,
                total_remaining: 0,
                active: 0
            },
            received: {
                count: 0,
                total_original: 0,
                total_remaining: 0,
                active: 0
            }
        };

        for (const loan of loans) {
            const type = loan.type; // 'given' o 'received'
            summary[type].count++;
            summary[type].total_original += parseFloat(loan.original_amount) || 0;
            summary[type].total_remaining += parseFloat(loan.remaining_amount) || 0;
            if (loan.status === 'active') {
                summary[type].active++;
            }
        }

        // Balance neto: lo que me deben - lo que debo
        summary.net_balance = summary.given.total_remaining - summary.received.total_remaining;

        return summary;
    } catch (error) {
        console.error('Error getting loans summary:', error);
        throw error;
    }
}

/**
 * Obtiene préstamos próximos a vencer
 */
export async function getUpcomingDueLoans(daysAhead = 30) {
    try {
        const today = new Date();
        const futureDate = new Date();
        futureDate.setDate(futureDate.getDate() + daysAhead);

        const { data, error } = await supabase
            .from('loans')
            .select('*')
            .eq('status', 'active')
            .not('due_date', 'is', null)
            .gte('due_date', today.toISOString().split('T')[0])
            .lte('due_date', futureDate.toISOString().split('T')[0])
            .order('due_date', { ascending: true });

        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching upcoming due loans:', error);
        throw error;
    }
}

/**
 * Obtiene préstamos vencidos
 */
export async function getOverdueLoans() {
    try {
        const today = new Date().toISOString().split('T')[0];

        const { data, error } = await supabase
            .from('loans')
            .select('*')
            .eq('status', 'active')
            .not('due_date', 'is', null)
            .lt('due_date', today)
            .order('due_date', { ascending: true });

        if (error) throw error;
        return data || [];
    } catch (error) {
        console.error('Error fetching overdue loans:', error);
        throw error;
    }
}

// ============================================================================
// VALIDATION
// ============================================================================

/**
 * Valida los datos de un loan
 */
function validateLoanData(data) {
    if (!data.name || data.name.trim() === '') {
        throw new Error('El nombre del préstamo es requerido');
    }

    const validTypes = ['given', 'received'];
    if (!validTypes.includes(data.type)) {
        throw new Error(`Tipo inválido. Debe ser: ${validTypes.join(', ')}`);
    }

    if (!data.counterparty || data.counterparty.trim() === '') {
        throw new Error('La contraparte (a quién/de quién) es requerida');
    }

    if (!data.original_amount || parseFloat(data.original_amount) <= 0) {
        throw new Error('El monto debe ser mayor a 0');
    }

    if (!data.loan_date) {
        throw new Error('La fecha del préstamo es requerida');
    }
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Marca un préstamo como pagado completamente
 */
export async function markLoanAsPaid(id) {
    return updateLoan(id, { 
        status: 'paid',
        remaining_amount: 0
    });
}

/**
 * Marca un préstamo como en mora/impago
 */
export async function markLoanAsDefaulted(id) {
    return updateLoan(id, { status: 'defaulted' });
}

/**
 * Cancela un préstamo
 */
export async function cancelLoan(id) {
    return updateLoan(id, { status: 'cancelled' });
}

/**
 * Calcula el progreso de pago de un préstamo (0-100%)
 */
export function calculateLoanProgress(loan) {
    if (!loan || !loan.original_amount) return 0;
    const paid = loan.original_amount - (loan.remaining_amount || 0);
    return Math.min(100, Math.round((paid / loan.original_amount) * 100));
}

/**
 * Obtiene el progreso detallado de un préstamo
 */
export async function getLoanProgress(loanId) {
    try {
        const loan = await getLoanById(loanId);
        if (!loan) throw new Error('Préstamo no encontrado');

        const originalAmount = parseFloat(loan.original_amount) || 0;
        const remainingAmount = parseFloat(loan.remaining_amount) || 0;
        const paid = originalAmount - remainingAmount;
        const progress = originalAmount > 0 ? (paid / originalAmount) * 100 : 0;

        return {
            loan,
            original: originalAmount,
            paid: paid,
            remaining: remainingAmount,
            progress: Math.min(100, progress),
            is_complete: remainingAmount <= 0 || loan.status === 'paid'
        };
    } catch (error) {
        console.error('Error getting loan progress:', error);
        throw error;
    }
}
