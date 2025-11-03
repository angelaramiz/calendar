/**
 * Módulo de Modal
 * Gestiona la interfaz de SweetAlert2 para eventos
 */

import { 
    getEventsForDate, 
    addEvent, 
    addRecurringEvents, 
    deleteEvent,
    escapeHTML,
    capitalize,
    loadEvents
} from './events.js';
import { generateRecurringDates } from './recurrence.js';

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
            
            return `
                <div class="existing-event-swal" data-idx="${idx}" style="border:1px solid #eee;padding:8px;border-radius:6px;margin-bottom:8px;background:#fafafa;position:relative;">
                    <strong>${escapeHTML(event.title)} ${freqBadge}</strong>
                    <p style="margin:6px 0 0 0;color:#444">${escapeHTML(event.desc || '')}</p>
                    <button data-idx="${idx}" class="swal-delete" style="position:absolute;right:8px;top:8px;background:#e55353;color:#fff;border:none;padding:4px 6px;border-radius:4px;cursor:pointer;font-size:0.8rem;">
                        Eliminar
                    </button>
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
        
        deleteButtons.forEach(btn => {
            btn.addEventListener('click', function() {
                const idx = Number(this.dataset.idx);
                deleteEvent(dateISO, idx);
                onUpdate([dateISO]);
                
                // Actualizar UI de la modal
                eventsList = getEventsForDate(dateISO);
                updateModalEventsList(container);
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

        const eventsHTML = eventsList.map((event, i) => {
            const freqBadge = event.frequency 
                ? `<span class="freq-badge">${capitalize(event.frequency)}${event.interval && event.interval > 1 ? ' x' + event.interval : ''}</span>` 
                : '';
            
            return `
                <div class="existing-event-swal" data-idx="${i}" style="border:1px solid #eee;padding:8px;border-radius:6px;margin-bottom:8px;background:#fafafa;position:relative;">
                    <strong>${escapeHTML(event.title)} ${freqBadge}</strong>
                    <p style="margin:6px 0 0 0;color:#444">${escapeHTML(event.desc || '')}</p>
                    <button data-idx="${i}" class="swal-delete" style="position:absolute;right:8px;top:8px;background:#e55353;color:#fff;border:none;padding:4px 6px;border-radius:4px;cursor:pointer;font-size:0.8rem;">
                        Eliminar
                    </button>
                </div>
            `;
        }).join('');

        existingContainer.innerHTML = eventsHTML;
        attachDeleteHandlers(container);
    }
}

/**
 * Abre la modal para agregar ingresos o gastos
 * @param {string} dateISO - Fecha en formato 'YYYY-MM-DD'
 * @param {string} type - Tipo de evento: 'ingreso' o 'gasto'
 * @param {Function} onUpdate - Callback para actualizar UI
 */
function openFinancialEventModal(dateISO, type, onUpdate) {
    const isIncome = type === 'ingreso';
    const idSuffix = isIncome ? '-ingreso' : '-gasto';
    const formHTML = isIncome ? renderIncomeForm(idSuffix) : renderExpenseForm(idSuffix);
    const title = isIncome ? `Agregar Ingreso — ${dateISO}` : `Agregar Gasto — ${dateISO}`;
    const iconColor = isIncome ? '#4caf50' : '#e55353';

    Swal.fire({
        title: title,
        html: formHTML,
        showCancelButton: true,
        focusConfirm: false,
        confirmButtonText: 'Guardar',
        confirmButtonColor: iconColor,
        cancelButtonText: 'Cancelar',
        didOpen: () => {
            // Toggle del contenido de frecuencia dentro del modal usando el sufijo único
            const header = document.getElementById(`swal-toggle-header${idSuffix}`);
            const contenido = document.getElementById(`swal-contenido-frecuencia${idSuffix}`);
            const select = document.getElementById(`swal-frequency${idSuffix}`);

            if (!header || !contenido || !select) return;

            // Manejo click del encabezado
            header.addEventListener('click', () => {
                const isVisible = contenido.style.display === 'block';
                contenido.style.display = isVisible ? 'none' : 'block';
                header.textContent = isVisible
                    ? 'Frecuencia de repetición ▼'
                    : 'Frecuencia de repetición ▲';
            });

            // Si el usuario selecciona una frecuencia, mostramos el contenido automáticamente
            select.addEventListener('change', () => {
                if (select.value) {
                    contenido.style.display = 'block';
                    header.textContent = 'Frecuencia de repetición ▲';
                } else {
                    contenido.style.display = 'none';
                    header.textContent = 'Frecuencia de repetición ▼';
                }
            });

            // Estado inicial según la selección (por si hay un valor por defecto)
            if (select.value) {
                contenido.style.display = 'block';
                header.textContent = 'Frecuencia de repetición ▲';
            } else {
                contenido.style.display = 'none';
                header.textContent = 'Frecuencia de repetición ▼';
            }
        },
        preConfirm: () => {
            // Usamos los IDs con sufijo para leer los valores
            const titleEl = document.getElementById(`swal-title${idSuffix}`);
            const descEl = document.getElementById(`swal-desc${idSuffix}`);
            const freqEl = document.getElementById(`swal-frequency${idSuffix}`);
            const intervalEl = document.getElementById(`swal-interval${idSuffix}`);
            const limitEl = document.getElementById(`swal-limit${idSuffix}`);
            // amount solo para ingresos
            const amountEl = document.getElementById(`swal-amount${idSuffix}`);

            const title = titleEl ? titleEl.value.trim() : '';
            const desc = descEl ? descEl.value.trim() : '';
            const frequency = freqEl ? freqEl.value : '';
            const interval = intervalEl ? parseInt(intervalEl.value) || 1 : 1;
            const limit = limitEl ? parseInt(limitEl.value) || 6 : 6;
            const amount = amountEl ? (amountEl.value ? Number(amountEl.value) : null) : null;

            if (!title) {
                Swal.showValidationMessage('El título es obligatorio');
                return false;
            }
            if (frequency && interval < 1) {
                Swal.showValidationMessage('Intervalo debe ser >= 1');
                return false;
            }
            if (frequency && limit < 1) {
                Swal.showValidationMessage('Límite debe ser >= 1');
                return false;
            }
            
            // devolvemos también amount (puede ser null)
            return { title, desc, frequency, interval, limit, amount };
        }
    }).then(result => {
        if (result.isConfirmed && result.value) {
            handleEventSave(dateISO, result.value, onUpdate, type);
        }
    });
}

/**
 * Maneja el guardado del evento financiero
 */
function handleEventSave(dateISO, data, onUpdate, type = 'evento') {
    const eventData = {
        title: data.title,
        desc: data.desc,
        frequency: data.frequency || '',
        interval: data.interval || 1,
        limit: data.limit || 6,
        origin: dateISO,
        type: type // Agregamos el tipo para identificarlo después
    };

    // Si hay amount (solo ingresos en nuestro formulario), lo guardamos como parte del desc o campo adicional.
    if (typeof data.amount === 'number' && !Number.isNaN(data.amount)) {
        eventData.amount = data.amount;
    }

    if (!eventData.frequency) {
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

    const successMsg = type === 'ingreso' ? 'Ingreso guardado' : 
                       type === 'gasto' ? 'Gasto guardado' : 'Evento guardado';

    Swal.fire({ 
        icon: 'success', 
        title: successMsg, 
        timer: 1200, 
        showConfirmButton: false 
    });
}
