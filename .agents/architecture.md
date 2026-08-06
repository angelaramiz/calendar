# Arquitectura del Proyecto

## CalendarFinace

Aplicacion de gestion financiera con calendario mensual.

- **Fase**: desarrollo
- **Inicio**: 2025 (estimado)

---

## Estructura General

```
CalendarFinace/
├── calendarWeb/          # App web vanilla JS + Supabase
├── calendarRN/           # Workspace para migracion React Native
├── calendarAPP/          # App Android nativa Kotlin + Compose
├── .codegraph/           # Indice semantico de codigo
└── .agents/              # Memoria y skills IA
```

---

## Stack

| Capa | calendarWeb | calendarRN | calendarAPP |
|------|-------------|------------|-------------|
| **Frontend** | Vanilla JS | Vanilla JS | Kotlin + Compose |
| **UI** | CSS custom | CSS custom | Material 3 |
| **Auth** | Supabase Auth | Supabase Auth | Supabase Auth |
| **DB** | Supabase PG | Supabase PG | Supabase PG |
| **State** | DOM + localStorage | DOM + localStorage | ViewModel + StateFlow |
| **DI** | Manual | Manual | Koin |
| **HTTP** | fetch nativo | fetch nativo | Ktor |
| **Modals** | SweetAlert2 | SweetAlert2 | Compose Dialogs |

---

## Modulos de Negocio

| Modulo | calendarWeb | calendarAPP | Descripcion |
|--------|:-----------:|:-----------:|-------------|
| Auth | x | x | Login, registro, recuperacion |
| Calendario | x | x | Grid mensual con indicadores |
| Movimientos | x | x | CRUD ingresos/gastos |
| Patrones | x | x | Recurrentes (semanal...anual) |
| Balance | x | x | Resumen financiero |
| Savings | x | - | Ahorros con metas |
| Loans | x | - | Prestamos |
| Planning | x | - | Envelopes, metas |
| Wishlist | x | - | Lista de deseos + scraping |
| Price Monitor | x | - | Monitoreo automatico de precios |
| Financial Engine | x | - | Analisis 50/30/20 + salud financiera |
| Smart Assistant | x | - | Analisis de impacto en tiempo real |
| Notificaciones | x | - | Alertas del sistema |

---

## Base de Datos (Supabase)

### Tablas Core
- `users` — Perfiles (FK auth.users)
- `income_patterns` — Patrones de ingreso recurrente
- `expense_patterns` — Patrones de gasto recurrente
- `movements` — Transacciones confirmadas
- `plans` — Metas de ahorro
- `loans` — Prestamos
- `envelopes` — Presupuesto por categoria
- `savings_patterns` — Ahorros programados
- `savings_transactions` — Depositos/retiros

### Vistas
- `confirmed_balance_summary` — Balance general
- `monthly_confirmed_balance` — Balance por mes
- `movements_with_patterns` — Movimientos con patrones
- `plans_with_progress` — Metas con % avance
- `income_pattern_allocations` — Distribucion de ingresos

### Tablas Financial Engine
- `expense_income_links` — Vinculacion gasto-ingreso
- `financial_snapshots` — Snapshots periodicos
- `financial_recommendations` — Recomendaciones
- `expense_categories` — Categorizacion

### Tablas Product Wishlist
- `product_wishlist` — Productos deseados con precios
- `product_price_history` — Historico de precios

---

## API Scraper

- **URL**: `https://calendar-backend-ed6u5g.fly.dev`
- **Stack**: Python Flask + Selenium Chrome headless
- **Endpoints**: `/scrape` (POST), `/debug` (POST)
- **Tiendas**: 18+ (Amazon, MercadoLibre, Liverpool, Walmart, etc.)

---

## Flujo de Datos

```
Usuario → Auth (Supabase) → JWT Token
  ↓
Calendar Screen → Patterns/Movements (PostgREST)
  ↓
Balance View → confirmed_balance_summary
  ↓
Financial Engine → analisis + recomendaciones
```

---

## CodeGraph

- **Estado**: Indexado
- **Archivos**: 76
- **Nodos**: 1,514 (simbolos)
- **Aristas**: 4,883 (relaciones)
- **Comando**: `codegraph explore "<query>"`
