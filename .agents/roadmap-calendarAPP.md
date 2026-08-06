# Roadmap calendarAPP — Migracion Web → Android Nativo

## Vision

Completar la app Android (Kotlin + Jetpack Compose) para alcanzar paridad funcional con la app web (calendarWeb), priorizando el nucleo financiero y dejando features de e-commerce para una fase posterior.

---

## Fase 1: Pattern Scheduler + Proyecciones (Critico)

> **Sin esto, los patrones recurrentes no sirven para nada en el calendario.**

### 1.1 Recurrence Engine (Port de `recurrence.js`)
- [ ] `RecurrenceEngine.kt` — genera fechas a partir de frecuencia + intervalo + dia
- [ ] Soporte: `daily`, `weekly`, `biweekly`, `monthly`, `yearly`
- [ ] Rango de fechas (`start_date` → `end_date`)

### 1.2 Pattern Scheduler (Port de `pattern-scheduler.js`)
- [ ] `PatternScheduler.kt` — convierte patrones en ocurrencias proyectadas
- [ ] API en `PatternRepository`: `getProjections(userId, yearMonth)`
- [ ] Integrar en `CalendarViewModel` — mezclar proyecciones con movimientos confirmados
- [ ] UI: mostrar proyecciones en gris/tenue vs movimientos confirmados

### 1.3 Confirmar Ocurrencia
- [ ] Boton "Confirmar" en proyeccion → crea movimiento confirmado
- [ ] `MovementRepository.confirmProjection(pattern, date)`

**Estimado:** 4-6 horas

---

## Fase 2: Loans (Prestamos)

> **Port directo de `loans-v2.js` (449 lineas).**

### 2.1 Backend
- [ ] `LoanRepository.kt` — CRUD completo sobre tabla `loans`
- [ ] Soporte: `given` (diste) / `received` (recibiste)
- [ ] Campos: `counterparty`, `original_amount`, `remaining_amount`, `loan_date`, `due_date`, `status`

### 2.2 UI
- [ ] `LoanListScreen.kt` — lista de prestamos activos/pagados
- [ ] `LoanFormScreen.kt` — crear/editar prestamo
- [ ] `LoanViewModel.kt` — estado y operaciones
- [ ] Integrar en `NavGraph.kt` (nueva ruta + boton en menu)
- [ ] Mostrar en `CalendarScreen` si el dia tiene vencimiento de prestamo

**Estimado:** 3-4 horas

---

## Fase 3: Plans / Savings Goals

> **Port directo de `plans-v2.js` (777 lineas).**

### 3.1 Backend
- [ ] `PlanRepository.kt` — CRUD sobre tabla `plans`
- [ ] Campos: `target_amount`, `current_amount`, `target_date`, `status`, `priority`
- [ ] `PlanIncomeSourceRepository.kt` — vincular ingresos a metas

### 3.2 UI
- [ ] `PlanListScreen.kt` — metas activas con barra de progreso
- [ ] `PlanFormScreen.kt` — crear/editar meta
- [ ] `PlanViewModel.kt`
- [ ] Integrar en `NavGraph.kt`

**Estimado:** 3-4 horas

---

## Fase 4: Savings

> **Port directo de `savings.js` (788 lineas).**

### 4.1 Backend
- [ ] `SavingsRepository.kt` — CRUD sobre `savings_patterns`, `savings_transactions`
- [ ] Depositos y retiros con asignacion a patrones de ingreso

### 4.2 UI
- [ ] `SavingsScreen.kt` — cuentas de ahorro con balance
- [ ] `SavingsTransactionScreen.kt` — historial depositos/retiros
- [ ] `SavingsViewModel.kt`

**Estimado:** 3-4 horas

---

## Fase 5: Planning Module

> **Port de `planning.js` + `planning-modals.js` + `planning-incomes.js` (2189 lineas combinadas).**

### 5.1 Envelopes (Apartados / Presupuestos)
- [ ] `EnvelopeRepository.kt` — CRUD sobre `envelopes`
- [ ] `EnvelopeScreen.kt` — lista de envelopes con consumo
- [ ] `EnvelopeFormScreen.kt` — crear/editar envelope

### 5.2 Planned Expenses (Gastos Planeados)
- [ ] `PlannedExpenseRepository.kt`
- [ ] `PlannedExpenseScreen.kt`

### 5.3 Income-to-Goal Assignment
- [ ] Asignar ingresos a metas de ahorro
- [ ] UI para distribuir ingresos entre multiples metas

### 5.4 Planning Dashboard
- [ ] `PlanningDashboardScreen.kt` — tabs: envelopes, goals, planned expenses
- [ ] Consolidacion en una sola pantalla con tabs

**Estimado:** 6-8 horas

---

## Fase 6: Stats Dashboard

> **Port de `stats.js` (184 lineas).**

### 6.1 UI
- [ ] `StatsScreen.kt` — tabs: dia/semana/mes/año
- [ ] Graficas o listas de movimientos por periodo
- [ ] Totales de ingreso/gasto/neto por periodo

**Estimado:** 2-3 horas

---

## Fase 7: Financial Engine + Dashboard

> **Port de `financial-engine.js` (936 lineas) + `financial-dashboard.js` (1316 lineas).**

### 7.1 Engine
- [ ] `FinancialEngine.kt` — calculos:
  - Health score (0-100)
  - 50/30/20 distribution
  - Debt-to-income ratio
  - Emergency fund status
  - Monthly projections
- [ ] Guardar snapshots en `financial_snapshots`

### 7.2 UI
- [ ] `FinancialDashboardScreen.kt` — score circular + graficas + recomendaciones
- [ ] `FinancialViewModel.kt`

**Estimado:** 6-8 horas

---

## Fase 8: Smart Financial Assistant

> **Port de `smart-financial-assistant.js` (1598 lineas).**

### 8.1 Engine
- [ ] `SmartAssistant.kt` — analisis de impacto al crear movimientos/patrones
- [ ] Alertas de riesgo (deficit, sobregiro)
- [ ] Sugerencias de monto optimo

### 8.2 UI
- [ ] Integrar en `MovementFormScreen` y `PatternFormScreen`
- [ ] Panel lateral o card con analisis en tiempo real

**Estimado:** 4-6 horas

---

## Fase 9: Notifications + Polish

### 9.1 Notifications
- [ ] `NotificationRepository.kt` — tabla `alerts`
- [ ] Android push notifications (FCM)
- [ ] Alertas de vencimientos, sobregiros, metas

### 9.2 UI Polish
- [ ] Splash screen
- [ ] Onboarding (3 pasos)
- [ ] Quick-access FAB para añadir movimiento
- [ ] Balance en header del calendario
- [ ] Dark mode toggle

**Estimado:** 4-5 horas

---

## Fase 10: Product Wishlist (Opcional — Baja Prioridad)

> **Port masivo: `product-wishlist.js` (1244) + modals (1476) + price-monitor (324) + form (1626) + scraper (101) + captcha (391) = 5162 lineas.**

### 10.1 Backend
- [ ] `ProductRepository.kt` — CRUD sobre `product_wishlist`
- [ ] Integracion con API scraper (`calendar-backend`)
- [ ] Monitoreo automatico de precios

### 10.2 UI
- [ ] `WishlistScreen.kt` — lista de productos con precio e imagen
- [ ] `ProductFormScreen.kt` — añadir producto via URL
- [ ] Dialogo de CAPTCHA (si aplica)

**Estimado:** 8-12 horas

---

## Resumen

| Fase | Modulo | Horas | Prioridad |
|------|--------|:-----:|:---------:|
| 1 | Pattern Scheduler + Proyecciones | 4-6 | Critica |
| 2 | Loans | 3-4 | Alta |
| 3 | Plans / Goals | 3-4 | Alta |
| 4 | Savings | 3-4 | Alta |
| 5 | Planning (Envelopes + Expenses + Incomes) | 6-8 | Media |
| 6 | Stats Dashboard | 2-3 | Media |
| 7 | Financial Engine + Dashboard | 6-8 | Media |
| 8 | Smart Financial Assistant | 4-6 | Baja |
| 9 | Notifications + Polish | 4-5 | Baja |
| 10 | Product Wishlist | 8-12 | Opcional |
| | **Total** | **43-60** | |

---

## Dependencias entre Fases

```
Fase 1 (Pattern Scheduler) ──► Fase 2 (Loans) ──► Fase 3 (Plans) ──► Fase 4 (Savings)
                                      │                                        │
                                      ▼                                        ▼
                              Fase 5 (Planning) ◄──────────────────────────────┘
                                      │
                                      ▼
                              Fase 6 (Stats)
                                      │
                                      ▼
                              Fase 7 (Financial Engine)
                                      │
                                      ▼
                              Fase 8 (Smart Assistant)
                                      │
                                      ▼
                              Fase 9 (Polish)
                                      │
                                      ▼
                              Fase 10 (Wishlist - opcional)
```

Las fases 2-4 son independientes entre si y pueden hacerse en paralelo si hay multiples desarrolladores.
