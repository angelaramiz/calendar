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
            <label style="display:block;font-weight:600;margin-bottom:6px;">Título</label>
            <input id="swal-title" class="swal2-input" placeholder="Título del evento">
            
            <label style="display:block;font-weight:600;margin-top:6px;margin-bottom:6px;">Descripción</label>
            <textarea id="swal-desc" class="swal2-textarea" placeholder="Descripción (opcional)" style="height:80px"></textarea>
            
            <label style="display:block;font-weight:600;margin-top:6px;margin-bottom:6px;">Frecuencia</label>
            <select id="swal-frequency" class="swal2-select" style="width:100%;padding:8px;border-radius:6px;border:1px solid #ddd;">
                <option value="">Ninguna</option>
                <option value="semanal">Semanal</option>
                <option value="mensual">Mensual</option>
                <option value="anual">Anual</option>
            </select>
            
            <div style="display:flex;gap:8px;margin-top:8px;">
                <div style="flex:1">
                    <label style="display:block;font-weight:600;margin-bottom:6px;">Intervalo de ciclo</label>
                    <input id="swal-interval" type="number" min="1" value="1" class="swal2-input" style="padding:6px;" />
                    <small style="color:#666">Ej: 2 → cada 2 semanas/meses/años</small>
                </div>
                <div style="width:120px">
                    <label style="display:block;font-weight:600;margin-bottom:6px;">Límite de ciclos</label>
                    <input id="swal-limit" type="number" min="1" value="6" class="swal2-input" style="padding:6px;" />
                    <small style="color:#666">Cantidad de ocurrencias</small>
                </div>
            </div>
        </div>
    `;

    Swal.fire({
        title: `Evento — ${dateISO}`,
        html: modalHTML,
        showCancelButton: true,
        focusConfirm: false,
        confirmButtonText: 'Guardar',
        cancelButtonText: 'Cerrar',
        didOpen: () => {
            const container = Swal.getHtmlContainer();
            attachDeleteHandlers(container);
        },
        preConfirm: () => {
            const title = document.getElementById('swal-title').value.trim();
            const desc = document.getElementById('swal-desc').value.trim();
            const frequency = document.getElementById('swal-frequency').value;
            const interval = parseInt(document.getElementById('swal-interval').value) || 1;
            const limit = parseInt(document.getElementById('swal-limit').value) || 6;

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
            
            return { title, desc, frequency, interval, limit };
        }
    }).then(result => {
        if (result.isConfirmed && result.value) {
            handleEventSave(dateISO, result.value, onUpdate);
        }
    });

    /**
     * Maneja el guardado del evento
     */
    function handleEventSave(dateISO, data, onUpdate) {
        const eventData = {
            title: data.title,
            desc: data.desc,
            frequency: data.frequency || '',
            interval: data.interval || 1,
            limit: data.limit || 6,
            origin: dateISO
        };

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

        Swal.fire({ 
            icon: 'success', 
            title: 'Evento guardado', 
            timer: 1200, 
            showConfirmButton: false 
        });
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
