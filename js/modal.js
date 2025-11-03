/**
 * Módulo de Modal
 * Gestiona la interfaz de SweetAlert2 para eventos
 */

import { 
    getEventsForDate, 
    addEvent, 
    addRecurringEvents, 
    deleteEvent,
    updateEvent,
    updateFutureOccurrences,
    createLoanCounterpartByLoanId,
    removeLoanCounterpartByLoanId,
    escapeHTML,
    capitalize,
    loadEvents
} from './events.js';
import { generateRecurringDates } from './recurrence.js';
// Web Components used for improved modular UI
import './components/frequency-toggle.js';
import './components/financial-form.js';

/**
 * Genera el formulario HTML para ingresos
 * @param {string} suffix - sufijo para IDs ('-ingreso' or '-gasto')
 * @returns {string} HTML del formulario
 */
function renderIncomeForm(suffix = '-ingreso') {
    return `
        <div style="text-align:left">
            <hr style="margin:10px 0;">
            <label style="display:block;font-weight:600;margin-bottom:6px;">Título del Ingreso</label>
            <input id="swal-title${suffix}" class="swal2-input" placeholder="Ej: Salario, Freelance, etc.">
            
            <label style="display:block;font-weight:600;margin-top:6px;margin-bottom:6px;">Descripción</label>
            <textarea id="swal-desc${suffix}" class="swal2-textarea" placeholder="Descripción (opcional)" style="height:80px"></textarea>
            
            <label style="display:block;font-weight:600;margin-top:6px;margin-bottom:6px;">Monto esperado</label>
            <input id="swal-amount${suffix}" type="number" class="swal2-input" placeholder="Monto esperado" style="padding:6px;" />
            
            <!-- Sección colapsable de frecuencia -->
            <div id="container_Frecuencia${suffix}" style="margin-top:10px;">
                <label id="swal-toggle-header${suffix}" style="cursor: pointer; font-weight:600;">
                    Frecuencia de repetición ▼
                </label>

                <div id="swal-contenido-frecuencia${suffix}" style="display: none; margin-top: 10px;">
                    <select id="swal-frequency${suffix}" class="swal2-select" style="width:100%;padding:8px;border-radius:6px;border:1px solid #ddd;">
                        <option value="">Ninguna</option>
                        <option value="semanal">semanal</option>
                        <option value="mensual">mensual</option>
                        <option value="anual">anual</option>
                    </select>
                    <br><br>

                    <label style="display:block;font-weight:600;margin-bottom:6px;">Intervalo de ciclo</label>
                    <input id="swal-interval${suffix}" type="number" min="1" value="1" class="swal2-input" style="padding:6px;" />
                    <br><br>

                    <label style="display:block;font-weight:600;margin-bottom:6px;">Límite de ciclo</label>
                    <input id="swal-limit${suffix}" type="number" min="1" value="6" class="swal2-input" style="padding:6px;" />
                </div>
            </div>
        </div>
    `;
}

/**
 * Genera el formulario HTML para gastos
 * @param {string} suffix - sufijo para IDs ('-ingreso' or '-gasto')
 * @returns {string} HTML del formulario
 */
function renderExpenseForm(suffix = '-gasto') {
    return `
        <div style="text-align:left">
            <hr style="margin:10px 0;">
            <label style="display:block;font-weight:600;margin-bottom:6px;">Título del Gasto</label>
            <input id="swal-title${suffix}" class="swal2-input" placeholder="Ej: Alquiler, Comida, etc.">
            
            <label style="display:block;font-weight:600;margin-top:6px;margin-bottom:6px;">Descripción</label>
            <textarea id="swal-desc${suffix}" class="swal2-textarea" placeholder="Descripción (opcional)" style="height:80px"></textarea>
            
            <!-- Sección colapsable de frecuencia -->
            <div id="container_Frecuencia${suffix}" style="margin-top:10px;">
                <label id="swal-toggle-header${suffix}" style="cursor: pointer; font-weight:600;">
                    Frecuencia de repetición ▼
                </label>

                <div id="swal-contenido-frecuencia${suffix}" style="display: none; margin-top: 10px;">
                    <select id="swal-frequency${suffix}" class="swal2-select" style="width:100%;padding:8px;border-radius:6px;border:1px solid #ddd;">
                        <option value="">Ninguna</option>
                        <option value="semanal">semanal</option>
                        <option value="mensual">mensual</option>
                        <option value="anual">anual</option>
                    </select>
                    <br><br>

                    <label style="display:block;font-weight:600;margin-bottom:6px;">Intervalo de ciclo</label>
                    <input id="swal-interval${suffix}" type="number" min="1" value="1" class="swal2-input" style="padding:6px;" />
                    <br><br>

                    <label style="display:block;font-weight:600;margin-bottom:6px;">Límite de ciclo</label>
                    <input id="swal-limit${suffix}" type="number" min="1" value="6" class="swal2-input" style="padding:6px;" />
                </div>
            </div>
        </div>
    `;
}

/**
 * Abre la modal de SweetAlert2 para una fecha específica
 * @param {string} dateISO - Fecha en formato 'YYYY-MM-DD'
 * @param {Function} onUpdate - Callback para actualizar UI después de cambios
 */
export function openEventModal(dateISO, onUpdate) {
    let eventsList = getEventsForDate(dateISO);

    /**
     * Renderiza la lista de eventos existentes
     * @returns {string} HTML de la lista
     */
    function renderExistingEvents() {
        if (!eventsList.length) {
            return '<div id="swal-existing-events"><em>No hay eventos.</em></div>';
        }
        
        const eventsHTML = eventsList.map((event, idx) => {
            const freqBadge = event.frequency 
                ? `<span class="freq-badge">${capitalize(event.frequency)}${event.interval && event.interval > 1 ? ' x' + event.interval : ''}</span>` 
                : '';

            if (event.confirmed || event.archived) {
                return `
                <div class="existing-event-swal" data-idx="${idx}" style="border:1px solid #eee;padding:8px;border-radius:6px;margin-bottom:8px;background:#f5f5f5;position:relative;opacity:0.85;">
                    <strong>${escapeHTML(event.title)} ${freqBadge} <span class="freq-badge" style="background:#f4f4f4;color:#888;border-color:#eee;margin-left:8px;">Historial</span></strong>
                    <p style="margin:6px 0 0 0;color:#666">${escapeHTML(event.desc || '')}</p>
                </div>
            `;
            }

            return `
                <div class="existing-event-swal" data-idx="${idx}" style="border:1px solid #eee;padding:8px;border-radius:6px;margin-bottom:8px;background:#fafafa;position:relative;">
                    <strong>${escapeHTML(event.title)} ${freqBadge}</strong>
                    <p style="margin:6px 0 0 0;color:#444">${escapeHTML(event.desc || '')}</p>
                    <div style="position:absolute;right:8px;top:8px;display:flex;gap:6px">
                        <button data-idx="${idx}" class="swal-confirm" style="background:#3498db;color:#fff;border:none;padding:4px 6px;border-radius:4px;cursor:pointer;font-size:0.8rem;">Confirmar</button>
                        <button data-idx="${idx}" class="swal-delete" style="background:#e55353;color:#fff;border:none;padding:4px 6px;border-radius:4px;cursor:pointer;font-size:0.8rem;">Eliminar</button>
                    </div>
                </div>
            `;
        }).join('');
        
        return `<div id="swal-existing-events">${eventsHTML}</div>`;
    }

    const modalHTML = `
    <div style="text-align:left">
            ${renderExistingEvents()}
            <hr style="margin:10px 0;">
            <button id="btn-add-income" style="background:#4caf50;color:#fff;border:none;padding:6px 10px;border-radius:4px;cursor:pointer;font-size:0.9rem;">Agregar ingreso</button>
            <button id="btn-add-expense" style="background:#e55353;color:#fff;border:none;padding:6px 10px;border-radius:4px;cursor:pointer;font-size:0.9rem;">Agregar gasto</button>
            <button style="background:#9e9e9e;color:#fff;border:none;padding:6px 10px;border-radius:4px;cursor:pointer;font-size:0.9rem;" disabled>Agregar recordatorio</button>
            
    </div>`;
    

    Swal.fire({
        title: `Evento — ${dateISO}`,
        html: modalHTML,
        showCancelButton: true,
        showConfirmButton: false,
        focusConfirm: false,
        cancelButtonText: 'Cerrar',
        didOpen: () => {
            const container = Swal.getHtmlContainer();
            attachDeleteHandlers(container);
            attachAddEventHandlers(container, dateISO, onUpdate);
            attachViewHandlers(container, dateISO, onUpdate);
        }
    });

    /**
     * Adjunta handlers para los botones de agregar eventos
     */
    function attachAddEventHandlers(container, dateISO, onUpdate) {
        const btnIncome = container.querySelector('#btn-add-income');
        const btnExpense = container.querySelector('#btn-add-expense');

        if (btnIncome) {
            btnIncome.addEventListener('click', () => {
                openFinancialEventModal(dateISO, 'ingreso', onUpdate);
            });
        }

        if (btnExpense) {
            btnExpense.addEventListener('click', () => {
                openFinancialEventModal(dateISO, 'gasto', onUpdate);
            });
        }
    }

    /**
     * Adjunta handlers para eliminar eventos
     */
    function attachDeleteHandlers(container) {
        const deleteButtons = container.querySelectorAll('.swal-delete');
        const editButtons = container.querySelectorAll('.swal-edit');
        
        deleteButtons.forEach(btn => {
            btn.addEventListener('click', function(e) {
                e.stopPropagation();
                const idx = Number(this.dataset.idx);
                deleteEvent(dateISO, idx);
                onUpdate([dateISO]);
                
                // Actualizar UI de la modal
                eventsList = getEventsForDate(dateISO);
                updateModalEventsList(container);
            });
        });

        const confirmButtons = container.querySelectorAll('.swal-confirm');
        confirmButtons.forEach(btn => {
            btn.addEventListener('click', function(e) {
                e.stopPropagation();
                const idx = Number(this.dataset.idx);
                const ev = eventsList[idx];
                if (!ev) return;
                // abrir modal de confirmación del monto esperado
                Swal.fire({
                    title: 'Confirmar monto',
                    html: `<div style="text-align:left">
                        <label style="display:block;font-weight:600;margin-bottom:6px">Monto</label>
                        <input id="swal-confirm-amount" type="number" class="swal2-input" value="${ev.amount !== undefined && ev.amount !== null ? escapeHTML(String(ev.amount)) : ''}" />
                    </div>`,
                    showCancelButton: true,
                    confirmButtonText: 'Confirmar',
                    cancelButtonText: 'Cancelar',
                    preConfirm: () => {
                        const val = Swal.getPopup().querySelector('#swal-confirm-amount').value;
                        return val;
                    }
                }).then(result => {
                    if (result.isConfirmed) {
                        const confirmedVal = result.value;
                        const updateData = {
                            confirmed: true,
                            archived: true,
                            confirmedAmount: confirmedVal !== undefined && confirmedVal !== null && confirmedVal !== '' ? Number(confirmedVal) : null
                        };
                        updateEvent(dateISO, idx, updateData);
                        // refresh local list and UI
                        eventsList = getEventsForDate(dateISO);
                        onUpdate([dateISO]);
                        updateModalEventsList(container);
                    }
                });
            });
        });

        editButtons.forEach(btn => {
            btn.addEventListener('click', function(e) {
                e.stopPropagation();
                const idx = Number(this.dataset.idx);
                const ev = eventsList[idx];
                if (!ev) return;
                // Abrir modal de edición con los datos existentes
                openFinancialEventModal(dateISO, ev.type || 'evento', onUpdate, {
                    initialData: ev,
                    editIndex: idx,
                    onSaved: () => {
                        // actualizar lista interna y UI de la modal padre
                        eventsList = getEventsForDate(dateISO);
                        updateModalEventsList(container);
                    }
                });
            });
        });
    }

    /**
     * Actualiza la lista de eventos en la modal actual
     */
    function updateModalEventsList(container) {
        const existingContainer = container.querySelector('#swal-existing-events');
        
        if (!eventsList.length) {
            existingContainer.innerHTML = '<em>No hay eventos.</em>';
            return;
        }

        const eventsHTML = eventsList.map((event, idx) => {
            const freqBadge = event.frequency 
                ? `<span class="freq-badge">${capitalize(event.frequency)}${event.interval && event.interval > 1 ? ' x' + event.interval : ''}</span>` 
                : '';
            // if event is already confirmed/archived, show as historial (non-interactive)
            if (event.confirmed || event.archived) {
                return `
                <div class="existing-event-swal" data-idx="${idx}" style="border:1px solid #eee;padding:8px;border-radius:6px;margin-bottom:8px;background:#f5f5f5;position:relative;opacity:0.85;">
                    <strong>${escapeHTML(event.title)} ${freqBadge} <span class="freq-badge" style="background:#f4f4f4;color:#888;border-color:#eee;margin-left:8px;">Historial</span></strong>
                    <p style="margin:6px 0 0 0;color:#666">${escapeHTML(event.desc || '')}</p>
                </div>
            `;
            }

            return `
                <div class="existing-event-swal" data-idx="${idx}" style="border:1px solid #eee;padding:8px;border-radius:6px;margin-bottom:8px;background:#fafafa;position:relative;">
                    <strong>${escapeHTML(event.title)} ${freqBadge}</strong>
                    <p style="margin:6px 0 0 0;color:#444">${escapeHTML(event.desc || '')}</p>
                    <div style="position:absolute;right:8px;top:8px;display:flex;gap:6px">
                      <button data-idx="${idx}" class="swal-confirm" style="background:#3498db;color:#fff;border:none;padding:4px 6px;border-radius:4px;cursor:pointer;font-size:0.8rem;">Confirmar</button>
                      <button data-idx="${idx}" class="swal-edit" style="background:#4caf50;color:#fff;border:none;padding:4px 6px;border-radius:4px;cursor:pointer;font-size:0.8rem;">Editar</button>
                      <button data-idx="${idx}" class="swal-delete" style="background:#e55353;color:#fff;border:none;padding:4px 6px;border-radius:4px;cursor:pointer;font-size:0.8rem;">Eliminar</button>
                    </div>
                </div>
            `;
        }).join('');

        existingContainer.innerHTML = eventsHTML;
        attachDeleteHandlers(container);
        attachViewHandlers(container, dateISO, onUpdate);
    }

    /**
     * Adjunta handlers para abrir la vista detallada al clicar la tarjeta del evento
     */
    function attachViewHandlers(container, dateISO, onUpdate) {
        const cards = container.querySelectorAll('.existing-event-swal');
        cards.forEach(card => {
            card.addEventListener('click', function(e) {
                // evita que botones internos activen la vista
                if (e.target && (e.target.closest('button') || e.target.tagName === 'BUTTON')) return;
                const idx = Number(this.dataset.idx);
                const ev = eventsList[idx];
                if (!ev) return;
                openEventDetailModal(dateISO, idx, ev, onUpdate, container);
            });
        });
    }

    /**
     * Abre modal con la vista completa del evento y opción de editar
     */
    function openEventDetailModal(dateISO, idx, ev, onUpdate, parentContainer) {
        const freqInfo = ev.frequency ? `<div><strong>Frecuencia:</strong> ${escapeHTML(ev.frequency)} (cada ${ev.interval}) — límite ${ev.limit}</div>` : '';
        const amountInfo = ev.amount !== undefined ? `<div><strong>Monto:</strong> ${escapeHTML(String(ev.amount))}</div>` : '';
        const categoryInfo = ev.category ? `<div><strong>Categoría:</strong> ${escapeHTML(ev.category)}</div>` : '';
        const html = `
            <div style="text-align:left">
                <h3 style="margin:0 0 8px 0">${escapeHTML(ev.title)}</h3>
                <p style="margin:0 0 8px 0">${escapeHTML(ev.desc || '')}</p>
                ${amountInfo}
                ${categoryInfo}
                ${freqInfo}
            </div>
        `;

        Swal.fire({
            title: `Evento — ${dateISO}`,
            html,
            showCancelButton: true,
            showConfirmButton: true,
            confirmButtonText: 'Editar',
            cancelButtonText: 'Cerrar',
            focusConfirm: false,
            didOpen: () => {
                // nothing special; actions handled by then()
            }
        }).then(result => {
            if (result.isConfirmed) {
                // abrir editor. pasamos promptApplyToFuture para que el editor pregunte si aplica a futuras
                openFinancialEventModal(dateISO, ev.type || 'evento', onUpdate, { initialData: ev, editIndex: idx, promptApplyToFuture: true, onSaved: () => {
                    // refresh parent modal list
                    eventsList = getEventsForDate(dateISO);
                    updateModalEventsList(parentContainer);
                }});
            }
        });
    }
}

/**
 * Abre la modal para agregar ingresos o gastos
 * @param {string} dateISO - Fecha en formato 'YYYY-MM-DD'
 * @param {string} type - Tipo de evento: 'ingreso' o 'gasto'
 * @param {Function} onUpdate - Callback para actualizar UI
 */
function openFinancialEventModal(dateISO, type, onUpdate, options = {}) {
    const isIncome = type === 'ingreso';
    const title = options.editIndex !== undefined
        ? (isIncome ? `Editar Ingreso — ${dateISO}` : `Editar Gasto — ${dateISO}`)
        : (isIncome ? `Agregar Ingreso — ${dateISO}` : `Agregar Gasto — ${dateISO}`);

    Swal.fire({
        title: title,
        html: '<div id="swal-host"></div>',
        showCancelButton: true,
        showConfirmButton: false,
        cancelButtonText: 'Cancelar',
        didOpen: () => {
            const host = Swal.getHtmlContainer().querySelector('#swal-host');
            if (!host) return;

            // Crear el componente y configurarlo
            const form = document.createElement('financial-form');
            form.setAttribute('type', isIncome ? 'ingreso' : 'gasto');
            // Si hay datos iniciales (modo edición), setearlos
            if (options.initialData) {
                // use setInitial helper exposed by the component
                try { form.setInitial(options.initialData); } catch (e) { /* ignore */ }
            }
            host.appendChild(form);

            // Escuchar eventos emitidos por el componente
            const onSave = (ev) => {
                const data = ev.detail || {};

                // If editing and the original has a frequency and caller requested prompt, ask whether to apply to future occurrences
                const shouldPrompt = options.promptApplyToFuture && options.initialData && options.initialData.frequency;

                if (typeof options.editIndex === 'number' && shouldPrompt) {
                    Swal.fire({
                        title: 'Actualizar ocurrencias',
                        text: '¿Deseas aplicar los cambios también a las futuras ocurrencias de esta serie?',
                        icon: 'question',
                        showCancelButton: true,
                        confirmButtonText: 'Sí, aplicar a futuras',
                        cancelButtonText: 'No, solo esta'
                    }).then(choice => {
                        if (choice.isConfirmed) {
                            // Get existing event to preserve critical data
                            const existingEvent = options.initialData;
                            
                            const updateData = {
                                title: data.title,
                                desc: data.desc,
                                type: type,
                                frequency: data.frequency || '',
                                interval: data.interval || 1,
                                limit: data.limit || 6,
                                origin: existingEvent.origin || dateISO
                            };
                            
                            // Preserve amount
                            if (data.amount !== null && data.amount !== undefined && !isNaN(data.amount)) {
                                updateData.amount = Number(data.amount);
                            } else if (existingEvent.amount !== undefined) {
                                updateData.amount = existingEvent.amount;
                            }
                            
                            // Preserve category (for expenses)
                            if (data.category !== null && data.category !== undefined) {
                                updateData.category = data.category;
                            } else if (existingEvent.category !== undefined) {
                                updateData.category = existingEvent.category;
                            }
                            
                            // apply to future occurrences
                            const updatedDates = updateFutureOccurrences(dateISO, options.editIndex, updateData);
                            onUpdate(updatedDates.length ? updatedDates : [dateISO]);
                        } else {
                            // only update this event
                            handleEventSave(dateISO, data, onUpdate, type, options.editIndex);
                        }

                        if (typeof options.onSaved === 'function') {
                            try { options.onSaved(); } catch (e) { /* ignore */ }
                        }
                        Swal.close();
                    });
                } else {
                    handleEventSave(dateISO, data, onUpdate, type, options.editIndex);
                    if (typeof options.onSaved === 'function') {
                        try { options.onSaved(); } catch (e) { /* ignore */ }
                    }
                    Swal.close();
                }
            };

            const onCancel = () => {
                Swal.close();
            };

            form.addEventListener('save', onSave);
            form.addEventListener('cancel', onCancel);

            // limpiar listeners si la modal se cierra por otros medios
            const cleanup = () => {
                form.removeEventListener('save', onSave);
                form.removeEventListener('cancel', onCancel);
            };

            // SweetAlert2 no tiene un hook de willClose en todos los builds, así que observamos el container
            const observer = new MutationObserver(() => {
                // si el host fue removido, limpiamos
                if (!document.body.contains(host)) {
                    cleanup();
                    observer.disconnect();
                }
            });
            observer.observe(document.body, { childList: true, subtree: true });
        }
    });
}

/**
 * Maneja el guardado del evento financiero
 */
function handleEventSave(dateISO, data, onUpdate, type = 'evento', editIndex) {
    // Get existing event data if editing
    const existingEvent = typeof editIndex === 'number' ? getEventsForDate(dateISO)[editIndex] : null;
    const previousLoanId = existingEvent && existingEvent.loan && existingEvent.loan.loanId ? existingEvent.loan.loanId : null;
    
    const eventData = {
        title: data.title,
        desc: data.desc,
        frequency: data.frequency || '',
        interval: data.interval || 1,
        limit: data.limit || 6,
        origin: dateISO,
        type: type
    };

    // Include loan info if provided for new events
    if (data.loan !== null && data.loan !== undefined) {
        eventData.loan = data.loan;
    }

    // Preserve amount if it exists in data
    if (data.amount !== null && data.amount !== undefined && !isNaN(data.amount)) {
        eventData.amount = Number(data.amount);
    } else if (existingEvent && existingEvent.amount !== undefined) {
        // Keep existing amount if new data doesn't provide one
        eventData.amount = existingEvent.amount;
    }

    // Preserve category if it exists in data (for expenses)
    if (data.category !== null && data.category !== undefined) {
        eventData.category = data.category;
    } else if (existingEvent && existingEvent.category !== undefined) {
        // Keep existing category if new data doesn't provide one
        eventData.category = existingEvent.category;
    }

    // Preserve loan info if provided
    if (data.loan !== null && data.loan !== undefined) {
        eventData.loan = data.loan;
    } else if (existingEvent && existingEvent.loan !== undefined) {
        eventData.loan = existingEvent.loan;
    }

    // Preserve seriesId if editing a recurring event
    if (existingEvent && existingEvent.seriesId) {
        eventData.seriesId = existingEvent.seriesId;
    }
    
    // Preserve occurrenceDate if it exists
    if (existingEvent && existingEvent.occurrenceDate) {
        eventData.occurrenceDate = existingEvent.occurrenceDate;
    }

    if (typeof editIndex === 'number') {
        // Edit existing event
        updateEvent(dateISO, editIndex, eventData);
        onUpdate([dateISO]);
    } else if (!eventData.frequency) {
        // Evento único
        addEvent(dateISO, eventData);
        onUpdate([dateISO]);
    } else {
        // Evento recurrente
        const dates = generateRecurringDates(
            dateISO, 
            eventData.frequency, 
            eventData.interval, 
            eventData.limit
        );
        addRecurringEvents(dates, eventData);
        onUpdate(dates);
    }

    // --- Loan counterpart handling ---
    // If the event has a loan, ensure it has a unique loanId and create counterpart if recoveryDays is set
    if (eventData.loan) {
        // generate loanId if missing
        if (!eventData.loan.loanId) {
            eventData.loan.loanId = `loan-${Date.now().toString(36)}-${Math.floor(Math.random()*10000)}`;
            // persist the assigned loanId for the updated/created event
            if (typeof editIndex === 'number') {
                // re-update to persist loanId
                updateEvent(dateISO, editIndex, { loan: eventData.loan });
            } else if (!eventData.frequency) {
                // unique event: update the just-added event to include loanId
                const arr = getEventsForDate(dateISO);
                const lastIdx = arr.length - 1;
                if (lastIdx >= 0) updateEvent(dateISO, lastIdx, { loan: eventData.loan });
            } else {
                // recurring: we updated via addRecurringEvents which copied eventData, but to be safe, call update on the first occurrence
                const dates = generateRecurringDates(
                    dateISO,
                    eventData.frequency,
                    eventData.interval,
                    eventData.limit
                );
                if (dates && dates.length) {
                    const arr = getEventsForDate(dates[0]);
                    // find the event that matches title/origin
                    for (let i = arr.length - 1; i >= 0; i--) {
                        const ev = arr[i];
                        if (ev && ev.loan && ev.loan.loanId === eventData.loan.loanId) {
                            updateEvent(dates[0], i, { loan: eventData.loan });
                            break;
                        }
                    }
                }
            }
        }

        // create counterpart if recoveryDays is present and > 0
        const recovery = Number(eventData.loan.recoveryDays || 0);
        if (recovery > 0) {
            try {
                createLoanCounterpartByLoanId(eventData.loan.loanId);
            } catch (e) {
                console.error('Error creando contraparte de loan:', e);
            }
        }
    } else if (previousLoanId) {
        // loan was removed in edit: remove any existing counterpart
        try {
            removeLoanCounterpartByLoanId(previousLoanId);
        } catch (e) {
            console.error('Error removiendo contraparte de loan anterior:', e);
        }
    }

    const successMsg = type === 'ingreso' ? 'Ingreso guardado' : 
                       type === 'gasto' ? 'Gasto guardado' : 'Evento guardado';

    Swal.fire({ 
        icon: 'success', 
        title: successMsg, 
        timer: 1200, 
        showConfirmButton: false 
    });
}
