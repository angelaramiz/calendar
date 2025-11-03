# Guía: Web Components (accesibilidad, testing y compatibilidad)

Este documento resume buenas prácticas, recomendaciones y pasos prácticos para usar los Web Components creados en `js/components/` (por ejemplo `frequency-toggle` y `financial-form`). Está pensado para que lo consultes antes de ampliar o integrar más componentes.

## Accesibilidad (A11y)

- Role/keyboard: asegúrate de que elementos interactivos tengan `role` y sean navegables por teclado. Por ejemplo, el header del toggle usa `role="button"` y `tabindex="0"` y responde a `Enter`/`Space`.
- Aria states: usa `aria-expanded` en elementos colapsables y actualízalo cuando cambie el estado.
- Labels: todos los inputs deben tener labels visibles o `aria-label` si son iconos.
- Focus management: al abrir modales, mueve el foco al primer control relevante (SweetAlert2 ya gestiona el foco en la mayoría de casos); al cerrar, devuelve el foco al elemento que lo abrió si es necesario.
- Mensajes de error: mostrar mensajes visibles para validaciones (no confiar solo en `alert()` o `console`). Considera emitir eventos `invalid` con detalle para que el host pueda mostrar mensajes globales.

## Testing

- Unit tests: prueba cada componente aislado. Comandos sugeridos:
  - Usa `@web/test-runner` o `karma` para tests en navegador real.
  - Prueba eventos custom (`save`, `cancel`, `frequency-change`) y la API pública (`.value`, `.setValue(...)`, `.open`).
- E2E / integración: prueba la interacción completa (abrir modal, rellenar formulario, guardar, ver que `addEvent` sea llamado o que el estado del calendario cambie).
- Test manual rápido: `components-demo.html` incluye ejemplos para probar interacciones manualmente.

## Compatibilidad y polyfills

- Navegadores modernos soportan Web Components (Chrome, Edge, Firefox, Safari recientes). Para soportar navegadores antiguos (p. ej. IE11) necesitas el polyfill `@webcomponents/webcomponentsjs`.
- Si tu público objetivo usa navegadores modernos, evita polyfills para reducir peso.
- Para proyectos que usan bundlers, puedes importar `@webcomponents/webcomponentsjs` condicionalmente o cargarlo desde CDN para navegadores sin soporte.

## Estilos y theming

- Shadow DOM encapsula estilos. Si quieres que estilos globales afecten el componente, utiliza CSS Custom Properties (variables) que el host pueda definir.
- Expón partes con `part="name"` y permite que el host use `::part(name)` para styling desde fuera.

## Performance

- Evita instanciar componentes pesados en cada celda si hay muchas celdas (p. ej. 100+). Alternativas:
  - Usar componentes ligeros (badges) que rendericen solo una etiqueta.
  - Crear componentes solo cuando el día tenga eventos.
  - Reutilizar nodos con técnicas de recycling (si haces un refactor más grande).

## Integración con modales (SweetAlert2)

- Inserta el componente dentro de `didOpen` usando `Swal.getHtmlContainer()` y escucha eventos custom.
- Cierra la modal desde el listener (por ejemplo, al recibir `save`) y limpia listeners para evitar fugas.

## Próximos pasos recomendados

1. Añadir pequeñas pruebas unitarias para `frequency-toggle` y `financial-form`.
2. Añadir documentación en README del proyecto con link a `docs/components-guidelines.md` y a `components-demo.html`.
3. Si necesitas compatibilidad IE11, añadir y probar el polyfill oficial.

---
Edición: 2025-11-03 — Autor: implementador automático
