# Roadmap móvil — propuestas de features avanzados

> Solo app móvil (`calendarAPP/`). Web y backend quedan fuera.
> Convenciones del proyecto que aplican a todo lo de aquí: 100% local en
> DataStore salvo donde se indique (sin DDL en Supabase salvo Respaldo en
> nube), lógica pura testeada (TDD, JUnit4), hora local del dispositivo,
> Kotlin UTF-8 sin BOM. Estado actual: v1.0.54.

## Cómo leer este documento

- **P0** = resuelve un dolor real y frecuente. **P1** = alto valor, uso semanal.
  **P2** = pulido / nicho.
- Esfuerzo en días-agent aproximados (código + tests + QA emulador + OTA).

---

## A. Rastreador MSI (meses sin intereses) — P0, ~3 días

### Problema
Comprar a 3/6/12/18/24 MSI es el día a día con tarjetas mexicanas. Hoy la app
acumula el cargo al corte pero no responde: ¿cuánto de mi pago son parciales
MSI?, ¿qué compras siguen activas?, ¿cuándo termino cada plan?, ¿cuánto se
libera de mi pago el mes que entra?

### Propuesta
Planes MSI como capa de **lectura** sobre los cargos tagueados (no duplican
movimientos ni alteran `summarize()`): cada plan proyecta sus cuotas sobre los
cortes de su tarjeta y marca cada cuota como cubierta cuando en ese periodo hay
cargo tagueado ≥ parcial (verificación automática, testeable). La creación es
manual o desde un cargo del ciclo actual ("pasar a MSI").

### UX
- Sección "MSI activos" dentro de cada tarjeta en Cuentas: `Concepto · 4/12 ·
  $850 este corte · termina mar 2027`, con barra de progreso n/N.
- Detalle por plan: tabla de cuotas (corte → parcial → estado cubierta/pendiente),
  botón "Liquidar" (cierra el plan) y "Eliminar".
- Entrada rápida: en la fila de un cargo del ciclo actual, opción
  "Pasar a MSI" → pide N meses → crea el plan.
- El "A pagar" del estado muestra informativo: "incluye $X de MSI (3 planes)".

### Modelo (local, `data/MsiStore.kt`, sin DDL)
```kotlin
MsiPlan(id, cardId, concepto, montoTotal, meses, primerCorteIso,
        liquidado: Boolean = false)
```
- `mensualidad = montoTotal / meses` (redondeo al centavo, ajuste en última cuota).
- `MsiPlanner` puro: `cuotas(plan, cutoffDay, paymentDay, fromHoy)` →
  lista `(corteIso, parcial, numero/total)`; `estadoCuota(corte, cargosTagueados)`
  → cubierta si `sum(cargos en [prev, corte)) >= parcial - 0.01`.
- Cuotas futuras = marcadores de calendario (como vencimientos/servicios, sin
  crear `movements`).

### Reglas y bordes
- Meses permitidos: 3/6/9/12/18/24 (lo que ofrecen los bancos; editable).
- Liquidación anticipada: marca `liquidado`, deja de proyectar.
- Si el cargo original pierde el tag, la cuota queda "pendiente" (no se borra el plan).
- Cambio de tarjeta: no se migra solo (el plan es por tarjeta/corte); se documenta.
- Sin cargos tagueados ese periodo → cuota pendiente + recordatorio (engancha con
  `RemindersWorker` existente).

### Tests
- `mensualidad_exacta_y_ajuste_ultima_cuota`, `cuotas_caen_en_cortes`,
  `cuota_cubierta_con_cargo_tag`, `cuota_pendiente_sin_cargo`,
  `liquidar_cierra_proyeccion`, `caso_didi_12_meses`.

### Métricas de éxito
Usuario con ≥1 MSI ve cada mes qué parte de su pago es MSI y cuándo termina
cada plan, sin registrar nada dos veces.

---

## B. Respaldo en nube cifrada — P0, ~4 días

### Problema
Todo lo valioso vive solo en el teléfono: billeteras, tarjetas, tags, servicios,
topes, vínculos, flujos, metas, planes MSI, sugerencias. Perder o cambiar de
equipo = empezar de cero. El respaldo actual es manual (copiar JSON al
portapapeles) y casi nadie lo hace.

### Propuesta
Subida automática del mismo JSON de `BackupManager` a Supabase, cifrada del
lado del cliente, con restauración al iniciar sesión en un equipo nuevo.
Última escritura gana por `updated_at` + aviso visible si hay conflicto.

### Decisiones de diseño
- **Dónde:** tabla nueva `fintrack_backups(user_id PK, updated_at, schema_v,
  payload)` con `payload` TEXT (JSON cifrado en base64) — o Storage si el JSON
  supera ~200 KB. Una sola fila por usuario (upsert).
- **Cifrado (recomendado):** AES-GCM con llave derivada de la contraseña de
  FinTrack (PBKDF2 + salt por usuario guardado en claro junto al payload).
  La contraseña ya vive en `CredentialStore`, así que no hay PIN nuevo que
  pedir. Alternativa v1 simplificada: solo RLS por `user_id` sin cifrado extra
  (protege contra otros usuarios, no contra acceso admin). No usar Android
  Keystore como única llave: no viaja a otro teléfono.
- **Cuándo sincroniza:** al abrir la app (baja si `updated_at` remoto > local),
  al cerrar/suspender (sube si hubo cambios), y botón manual en
  Respaldo local. Todo detrás de `ensureSession()`; sin sesión no hace nada.
- **Conflictos:** last-write-wins + mensaje "se restauró respaldo del <fecha>".
  `schema_v` permite migrar el JSON entre versiones de la app.
- **Qué NO viaja:** fotos/tickets binarios si algún día existen (solo JSON),
  credenciales (`CredentialStore` nunca sale del equipo).

### Migración SQL (único DDL del roadmap)
```sql
create table if not exists public.fintrack_backups (
  user_id uuid primary key references auth.users(id) on delete cascade,
  updated_at timestamptz not null default now(),
  schema_v int not null default 1,
  payload text not null default ''
);
alter table public.fintrack_backups enable row level security;
create policy "own backup" on public.fintrack_backups
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
```

### Tests
- Codec: `cifrar_descifrar_roundtrip`, `wrong_password_falla`,
  `merge_last_write_wins`, `schema_v_desconocido_no_rompe` (ignora/advierte).
- `CloudBackupPlanner` puro para decidir subir/bajar/nada según timestamps.

### Métricas de éxito
Cambio de equipo + login = tarjetas, topes, servicios y tags de vuelta sin
pegar JSON a mano.

---

## C. Más ideas (desarrolladas)

### C1. Pronóstico de flujo diario — P0, ~3 días
Proyección día por día a 30/60/90 días combinando patrones (`PatternExpander`),
cuotas MSI, servicios y eventos del calendario sobre el saldo actual. Responde
"¿de cuánto ando el 24?" y dispara alerta push si el saldo proyectado cruza un
mínimo ("te quedas en ceros el 24"). Motor puro `CashflowForecast` + tests con
quincena y corte de tarjeta; UI: línea simple en Presupuesto + marcador en
calendario el día crítico. Engancha con `RemindersWorker` (tercer tipo de aviso).

### C2. Control de tandas — P1, ~3 días
Muy mexicano y sin equivalente en la app: `Tanda(numero, montoPorPersona,
integrantes[], orden[], rondaActual, fechaCobro)`. Quién ya dio / a quién le
toca / cuánto falta, con recordatorios de cobro (eres el organizador) y de pago
(te toca dar). Local en DataStore, tests de rotación y morosos. Entrada desde
Cuentas como cuarto grupo (débito/crédito/servicios/tandas).

### C3. Alerta de corte próximo — P1, ~1 día
"Tu Didi corta en 3 días y llevas $X acumulados". Reutiliza `nextCutoff` +
`currentCycle` + `RemindersWorker` (aviso a 3 días). Es el complemento natural
del ciclo actual que ya existe. Casi puro ensamble.

### C4. Velocidad de gasto vs tope — P1, ~2 días
Con `BudgetCapsStore` + cargos del mes: "a este ritmo te pasas de Comida el día
22" (regla de tres por día transcurrido, con banda ±20% para no spamear).
Sección en Presupuesto + aviso semanal opcional. Motor puro testeable.

### C5. Reglas de categorización que aprenden — P1, ~2 días
Memoria local `merchant → categoría` (`CategoryRuleStore`): la primera vez que
corriges "OXXO" a Compras, las siguientes detecciones ya llegan categorizadas.
El parser la consulta al final (antes del `else -> Otros`) y la UI de
detecciones ofrece "siempre así". Tests de precedencia regla-vs-heurística.

### C6. Heatmap de gasto en el calendario — P2, ~2 días
Colorea los días según gasto (verde→rojo por quintil del mes) + comparativa
"este mes vs anterior" por categoría en Presupuesto. 100% lectura sobre datos
existentes; hace visible el ritmo sin abrir reportes.

### C7. Widget de balance + registro — P1, ~3 días
Glance widget: balance de hoy + botón que abre `QuickEntryDialog` (el Tile ya
existe; el widget es su primo visible). Requiere `glance` + receiver; sin
cambios al modelo.

### C8. Registro por voz — P2, ~2 días
"Gasté 200 en tacos" con el reconocedor del sistema → pre-llena QuickEntry
(monto + categoría tentativa) para confirmar con un toque. Sin ML propio, sin
red nuestra; el parser de montos ya existe.

### C9. OCR de tickets — P2, ~4 días
Foto del ticket → texto on-device (ML Kit Text Recognition, gratis, sin red) →
monto + comercio sugeridos en QuickEntry + foto guardada solo local. La foto
NUNCA viaja (ni al respaldo nube: solo el monto/comercio). Permiso de cámara +
almacenamiento interno.

### C10. Modo discreto — P2, ~1 día
Agitar o botón para ocultar montos (`$•••`) al mostrar la app en público.
Toggle en Inicio + persistencia local. Barato y muy pedido en apps de banco.

### C11. Fondo de emergencia con auto-apartado — P2, ~2 días
Regla sobre `FlowEngine`: "de cada ingreso, aparta N% a la meta colchón" con
sugerencia mensual (no mueve dinero real: propone el monto y el usuario lo
aparta en su banco). Cierra el loop Flujos → Metas.

---

## Matriz rápida

| Feature | Prioridad | Esfuerzo | Depende de |
|---|---|---|---|
| MSI | P0 | 3 días | nada nuevo |
| Respaldo nube | P0 | 4 días | nada (1 tabla) |
| Pronóstico flujo | P0 | 3 días | MSI luce mejor con él |
| Tandas | P1 | 3 días | nada |
| Alerta corte | P1 | 1 día | nada |
| Velocidad vs tope | P1 | 2 días | nada |
| Reglas categoría | P1 | 2 días | nada |
| Widget | P1 | 3 días | nada |
| Heatmap | P2 | 2 días | nada |
| Voz | P2 | 2 días | nada |
| OCR tickets | P2 | 4 días | nada |
| Modo discreto | P2 | 1 día | nada |
| Fondo emergencia | P2 | 2 días | Flujos existente |

Orden sugerido de construcción: MSI → Pronóstico de flujo (se alimentan) →
Respaldo nube (asegura todo lo local antes de seguir creciendo) → Tandas →
resto por antojo.
