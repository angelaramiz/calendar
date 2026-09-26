/**
 * version-check.js — Avisa cuando hay una versión nueva publicada.
 *
 * build.sh inyecta <meta name="app-version"> en cada HTML y escribe
 * web-version.json en cada deploy. Este script compara ambos cada 5 min
 * (y al volver a la pestaña): si difieren, el navegador tenía caché vieja
 * y se invita a recargar. Sin dependencias (funciona aunque falle un módulo).
 */
(function () {
    var meta = document.querySelector('meta[name="app-version"]');
    var current = meta ? meta.getAttribute('content') : null;
    if (!current) return;

    var PROMPTED_KEY = 'fintrack-prompted-version';

    function base() {
        return location.pathname.indexOf('/routes/') !== -1 ? '../' : '';
    }

    async function check() {
        try {
            var res = await fetch(base() + 'web-version.json?nocache=' + Date.now(), { cache: 'no-store' });
            if (!res.ok) return;
            var data = await res.json();
            if (data && data.version && data.version !== current &&
                sessionStorage.getItem(PROMPTED_KEY) !== data.version) {
                sessionStorage.setItem(PROMPTED_KEY, data.version);
                promptReload();
            }
        } catch (e) {
            /* Sin red o json inaccesible: no hacer nada. */
        }
    }

    function promptReload() {
        if (window.Swal && typeof Swal.fire === 'function') {
            Swal.fire({
                icon: 'info',
                title: 'Nueva versión disponible',
                text: 'Publicamos una actualización. Recarga para usarla.',
                confirmButtonText: 'Recargar ahora',
                allowOutsideClick: false
            }).then(function () { location.reload(); });
        } else if (window.confirm('Hay una nueva versión de FinTrack. ¿Recargar ahora?')) {
            location.reload();
        }
    }

    setTimeout(check, 10000);
    setInterval(check, 5 * 60 * 1000);
    document.addEventListener('visibilitychange', function () {
        if (!document.hidden) check();
    });
})();
