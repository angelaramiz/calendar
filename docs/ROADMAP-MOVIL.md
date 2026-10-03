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

---

## D. Segunda oleada (12 ideas nuevas)

Reutilizan lo que ya existe (cortes, tags, patrones, detector, Dedup) en vez de
pedir datos nuevos. Prioridad y esfuerzo con la misma escala de arriba.

### D1. ¿Con qué tarjeta pago? — P0, ~2 días
Al comprar, la pregunta real es qué tarjeta conviene HOY. Con `cutoffDay` y
`paymentDay` que ya guardas por tarjeta: días de gracia restantes si compras hoy
(`paymentForCutoff` − hoy), MSI activo con promo, y regla de cashback por
categoría (editable por tarjeta, ej. "Nu 2% en gasolina"). Respuesta:
"Compra con Plata: pagas en 47 días; con Didi: en 12". Motor puro
`CardRecommender` + tests con cortes cruzados de mes. Entrada: botón en
QuickEntry ("¿con cuál?") y sección en Cuentas. Brilla más con MSI (A) hecho.

### D2. Estrategia de deudas: bola de nieve vs avalancha — P0, ~3 días
Suma TODO lo que debes (saldos de tarjetas + MSI activos + deudas personales si
existe D8) y calcula tu **fecha de libertad** con los pagos actuales. Simulador:
mismo excedente mensual repartido por menor saldo (nieve, gana rápido) vs mayor
interés (avalancha, paga menos) — con CAT aproximado por tarjeta (dato editable,
default 60%). Motor puro `DebtPlanner` + tests; UI en Presupuesto con tabla
mes a mes y barra hasta la libertad. Es el feature "adulto" que hoy no existe.

### D3. Reparto de quincena — P0, ~2 días
Mitad del país vive de quincena en quincena. Al detectar/registrar la nómina,
ritual de un toque: aparta fijos (renta + servicios del periodo) → deuda
(mínimos + excedente según D2) → ahorro/meta → libre. Propone montos con los
datos reales (servicios, pagos de tarjeta, topes) y registra el reparto como
marcadores del periodo. `PaycheckPlanner` puro + tests con quincena 15/fin.
El hábito financiero más valioso del roadmap.

### D4. Aportaciones a metas — P1, ~2 días
Las metas (Objetivos) hoy dicen si algo es factible, pero no registran progreso.
`aportación(metaId, monto, fecha)` + barra `juntado/meta` + historial + "te
faltan $X (~N quincenas)". Local en `GoalStore` (extiende el modelo, sin DDL).
Cierra el loop con D3 (el apartado de ahorro cae aquí) y con C11.

### D5. Vigilante: duplicados y subidas de precio — P1, ~2 días
Dos anomalías con el mismo motor: (a) mismo comercio + mismo monto en <72h →
"¿te cobraron doble en Liverpool?"; (b) cargo repetido de suscripción con monto
mayor al histórico (Netflix 219→249) → "Netflix subió, ¿la conservas?".
Push informativo (no bloquea nada), lista en Cuentas, descarte con un toque.
Reutiliza transacciones + `SubscriptionDetector`. Tests de ventana 72h y de
tolerancia (misma suscripción con centavos distintos no es subida).

### D6. Límite diario ("hoy puedes gastar $X") — P1, ~1 día
`(ingreso quincenal − fijos del periodo − apartado ahorro) / días restantes`,
recalculado cada mañana y visible en Inicio bajo el balance. Si un gasto te
pasa del día, aviso suave. `DailyAllowance` puro (3 tests). Simple y cambia
conducta más que cualquier reporte.

### D7. Importar CSV del banco + conciliación — P1, ~3 días
BBVA/Banamex/Santander exportan movimientos en CSV/Excel con formatos distintos.
Importar archivo → parser por banco (detector de formato por encabezados) →
vista de conciliación: cada fila se acepta, se vincula a un registro existente
(`OccurrenceLink` ya sabe comparar) o se descarta; `Dedup` evita dobles en
re-imports. Solo lectura del archivo, todo local. El puente entre "app manual"
y "banco real" sin APIs.

### D8. Deudas personales y cuentas divididas — P1, ~3 días
"Le presté $500 a Juan", "la cena $1200 entre 3". `PersonaDebt(quién, monto,
dirección, fecha, parcialidades[])` + `SplitCheck(total, partes)`. Quién te debe
/ a quién debes, abonos parciales, recordatorio de cobro amable a los N días.
Local, con tests de saldos. Compañero natural de Tandas (C2): misma zona,
distinto mecanismo.

### D9. Pregunta a tus datos — P2, ~3 días
"¿Cuánto gasté en tacos en marzo?", "¿cuánto me entró en agosto?" con plantillas
en español sobre tus datos locales (categoría/comercio/mes × suma/promedio/top),
sin LLM ni red: `QueryParser` (regex de intención + entidad) + ejecutor sobre
transacciones. Responde en una tarjeta con la cifra y 3 ejemplos. Ambicioso pero
factible; si una pregunta no matchea, lo dice (cero alucinaciones por diseño).

### D10. Servicios anuales — P1, ~1 día
Hoy los servicios son monthly/bimonthly; fuera quedan predial, verificación,
tenencia, anualidades de tarjeta. Extender `ServiceBillStore.frequency` con
`yearly` (+ fecha fija día/mes) y que `RemindersWorker` lo evalúe. Cambio
pequeño, cierra un hueco real del modelo.

### D11. Fuga hormiga semanal + rachas — P2, ~2 días
Push del lunes: "Oxxo te llevó $340 esta semana (5 visitas)". Y rachas:
"3 quincenas cerrando Comida bajo el tope 🎯" con conteo persistente local.
Engagement barato sobre categorías y topes existentes; todo configurable en
frecuencia (semanal/quincenal/off).

### D12. Reporte mensual exportable — P2, ~2 días
PDF de una página: ingresos/gastos/neto, top categorías, tarjetas (pagado vs
ciclo), servicios, MSI activos y pronóstico. Generación local (Android
`PdfDocument`, sin librerías), botón compartir. Útil para contador, pareja o
tu yo de diciembre. Requiere C1/D4 hechos para que el reporte tenga sustancia.

## Matriz segunda oleada

| Feature | Prioridad | Esfuerzo | Depende de |
|---|---|---|---|
| ¿Con qué tarjeta pago? | P0 | 2 días | mejor con MSI |
| Deudas nieve vs avalancha | P0 | 3 días | MSI + D8 |
| Reparto de quincena | P0 | 2 días | D2 para excedente |
| Aportaciones a metas | P1 | 2 días | nada |
| Vigilante duplicados/subidas | P1 | 2 días | nada |
| Límite diario | P1 | 1 día | nada |
| Importar CSV + conciliar | P1 | 3 días | Dedup existente |
| Deudas personales/divididas | P1 | 3 días | nada |
| Pregunta a tus datos | P2 | 3 días | nada |
| Servicios anuales | P1 | 1 día | nada |
| Fuga hormiga + rachas | P2 | 2 días | nada |
| Reporte PDF | P2 | 2 días | C1 + D4 |

Orden sugerido global actualizado: MSI → Pronóstico de flujo → **¿Con qué
tarjeta? + Reparto de quincena** (usan lo anterior) → Respaldo nube → Deudas
(nieve/avalancha + personales) → Tandas → resto.
