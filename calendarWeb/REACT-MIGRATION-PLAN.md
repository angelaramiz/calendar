# 📋 Plan de Migración a React
**Documento: Planeación, Guía y Planificación para Migración a React**

**Fecha de Creación:** 4 de enero de 2026  
**Proyecto:** Sistema de Planificación Financiera  
**Estado:** Planeación  
**Tiempo Estimado Total:** 40-60 horas (3-4 semanas)

---

## 📑 Tabla de Contenidos

1. [Resumen Ejecutivo](#resumen-ejecutivo)
2. [Estado Actual del Proyecto](#estado-actual-del-proyecto)
3. [Arquitectura Propuesta](#arquitectura-propuesta)
4. [Stack Tecnológico Recomendado](#stack-tecnológico-recomendado)
5. [Plan de Migración por Fases](#plan-de-migración-por-fases)
6. [Módulos Prioritarios](#módulos-prioritarios)
7. [Estructura de Directorios](#estructura-de-directorios)
8. [Detalles Técnicos](#detalles-técnicos)
9. [Consideraciones de Seguridad](#consideraciones-de-seguridad)
10. [Testing y QA](#testing-y-qa)
11. [Deployment](#deployment)
12. [Timeline y Hitos](#timeline-y-hitos)
13. [Checklist de Ejecución](#checklist-de-ejecución)

---

## 1. Resumen Ejecutivo

### ¿Por qué migrar a React?

**Beneficios:**
- ✅ **Componentes Reutilizables:** Reducir código duplicado (actualmente ~5000 líneas de vanilla JS)
- ✅ **State Management Centralizado:** Reemplazar gestión manual con Zustand/Redux
- ✅ **TypeScript:** Type-safety, mejor IDE autocomplete, menos bugs en tiempo de ejecución
- ✅ **Ecosystem Maduro:** Librerías establecidas para UI, routing, testing
- ✅ **Developer Experience:** Hot Module Replacement (HMR), mejor debugging
- ✅ **Performance:** Virtual DOM, memoization automática con React.memo
- ✅ **Team Growth:** Mejor onboarding para nuevos desarrolladores

### Estatus Actual

**Fortalezas Existentes:**
- Arquitectura modular bien separada (patterns.js, planning.js, balance.js)
- Supabase ya implementado con RLS
- Funcionalidad completa: ingresos, gastos, planificación, wishlist, financiero
- Base de datos con schema V2 completo
- Autenticación segura

**Desafíos Actuales:**
- Gestión manual de estado (global state object)
- SweetAlert2 para modales (React Toastify/Dialog sería mejor)
- Renderizado HTML dinámico (propenso a XSS)
- Testing inexistente
- Build process básico (no Vite/Webpack)
- Duplicación de lógica en UI (patterns.js duplica getExpensePatterns)

---

## 2. Estado Actual del Proyecto

### Estructura Actual

```
js/
├── auth/
│   ├── login.js
│   ├── register.js
│   └── recovery.js
├── components/
│   ├── envelope-form.js
│   ├── financial-form.js
│   ├── frequency-toggle.js
│   ├── goal-form.js
│   ├── planned-expense-form.js
│   └── product-wishlist-form.js
├── lib/
│   └── sweetalert2@11.js
├── Services:
│   ├── patterns.js
│   ├── planning.js
│   ├── balance.js
│   ├── movements.js
│   ├── notifications.js
│   ├── financial-engine.js
│   ├── smart-financial-assistant.js
│   ├── product-price-monitor.js
│   ├── product-wishlist.js
│   ├── loans-v2.js
│   ├── plans-v2.js
│   └── supabase-client.js
└── Pages:
    ├── calendar.js
    ├── planning.js
    ├── savings.js
    ├── stats.js
    └── financial-dashboard.js
```

### Líneas de Código por Módulo

| Módulo | Líneas | Complejidad | Prioridad Migración |
|--------|--------|-------------|-------------------|
| planning.js | 800+ | Alta | 🔴 Crítica |
| calendar.js | 700+ | Alta | 🔴 Crítica |
| patterns.js | 250+ | Media | 🔴 Crítica |
| balance.js | 300+ | Media | 🟠 Alta |
| financial-dashboard.js | 600+ | Alta | 🟠 Alta |
| product-wishlist.js | 500+ | Media | 🟡 Media |
| loans-v2.js | 400+ | Media | 🟡 Media |
| smart-financial-assistant.js | 300+ | Alta | 🟡 Media |

---

## 3. Arquitectura Propuesta

### Patrón de Arquitectura: Feature-Sliced Design + Container/Presentational

```
src/
├── features/
│   ├── auth/
│   │   ├── ui/
│   │   │   ├── LoginForm.tsx
│   │   │   ├── RegisterForm.tsx
│   │   │   └── RecoveryForm.tsx
│   │   ├── store/
│   │   │   └── authStore.ts
│   │   ├── services/
│   │   │   └── authService.ts
│   │   └── hooks/
│   │       └── useAuth.ts
│   │
│   ├── planning/
│   │   ├── ui/
│   │   │   ├── PlanningDashboard.tsx
│   │   │   ├── EnvelopeForm.tsx
│   │   │   ├── GoalForm.tsx
│   │   │   └── ExpensePatternList.tsx
│   │   ├── store/
│   │   │   └── planningStore.ts
│   │   ├── services/
│   │   │   └── planningService.ts
│   │   ├── hooks/
│   │   │   ├── usePlanning.ts
│   │   │   ├── useEnvelopes.ts
│   │   │   └── useGoals.ts
│   │   └── types/
│   │       └── planning.types.ts
│   │
│   ├── calendar/
│   │   ├── ui/
│   │   │   ├── CalendarView.tsx
│   │   │   ├── EventModal.tsx
│   │   │   └── EventList.tsx
│   │   ├── store/
│   │   │   └── calendarStore.ts
│   │   ├── services/
│   │   │   └── calendarService.ts
│   │   ├── hooks/
│   │   │   └── useCalendar.ts
│   │   └── types/
│   │       └── calendar.types.ts
│   │
│   ├── patterns/
│   │   ├── ui/
│   │   │   └── PatternsManager.tsx
│   │   ├── store/
│   │   │   └── patternsStore.ts
│   │   ├── services/
│   │   │   └── patternsService.ts
│   │   ├── hooks/
│   │   │   └── usePatterns.ts
│   │   └── types/
│   │       └── patterns.types.ts
│   │
│   ├── wishlist/
│   │   ├── ui/
│   │   │   ├── WishlistDashboard.tsx
│   │   │   ├── ProductCard.tsx
│   │   │   └── ProductForm.tsx
│   │   ├── store/
│   │   │   └── wishlistStore.ts
│   │   ├── services/
│   │   │   └── wishlistService.ts
│   │   ├── hooks/
│   │   │   └── useWishlist.ts
│   │   └── types/
│   │       └── wishlist.types.ts
│   │
│   ├── financial/
│   │   ├── ui/
│   │   │   ├── FinancialDashboard.tsx
│   │   │   ├── StatsPanel.tsx
│   │   │   └── EnginePanel.tsx
│   │   ├── store/
│   │   │   └── financialStore.ts
│   │   ├── services/
│   │   │   └── financialService.ts
│   │   ├── hooks/
│   │   │   └── useFinancial.ts
│   │   └── types/
│   │       └── financial.types.ts
│   │
│   └── notifications/
│       ├── ui/
│       │   ├── Toast.tsx
│       │   └── NotificationCenter.tsx
│       ├── store/
│       │   └── notificationStore.ts
│       └── services/
│           └── notificationService.ts
│
├── shared/
│   ├── components/
│   │   ├── Button.tsx
│   │   ├── Input.tsx
│   │   ├── Modal.tsx
│   │   ├── Card.tsx
│   │   ├── Header.tsx
│   │   └── Navigation.tsx
│   ├── hooks/
│   │   ├── useSupabase.ts
│   │   ├── useCurrency.ts
│   │   ├── useDateFormat.ts
│   │   └── useDebounce.ts
│   ├── services/
│   │   ├── supabaseClient.ts
│   │   ├── apiService.ts
│   │   └── storageService.ts
│   ├── types/
│   │   ├── database.types.ts
│   │   └── common.types.ts
│   ├── utils/
│   │   ├── formatting.ts
│   │   ├── validation.ts
│   │   ├── calculations.ts
│   │   └── dateUtils.ts
│   └── constants/
│       ├── currencies.ts
│       ├── frequencies.ts
│       └── config.ts
│
├── App.tsx
├── App.css
├── main.tsx
└── index.html
```

### Flujo de Datos

```
User Interaction
    ↓
Component (UI)
    ↓
Hook (usePatterns, usePlanning, etc.)
    ↓
Store (Zustand) ← Service Layer
    ↓
Service (patternsService.ts)
    ↓
Supabase Client
    ↓
PostgreSQL + RLS
```

---

## 4. Stack Tecnológico Recomendado

### Core Frontend

| Tecnología | Versión | Propósito | Razón |
|-----------|---------|----------|-------|
| **React** | 18.2+ | Librería UI | Latest features, Suspense, Concurrent |
| **TypeScript** | 5.3+ | Type Safety | Reducir bugs, mejor DX |
| **Vite** | 5+ | Build Tool | Faster builds, HMR, moderno |
| **React Router** | 6.20+ | Routing | Client-side routing |

### Estado y Datos

| Tecnología | Versión | Propósito | Razón |
|-----------|---------|----------|-------|
| **Zustand** | 4.4+ | State Management | Ligero, simple, sin boilerplate |
| **TanStack Query** | 5+ | Data Fetching | Caching, sincronización, refetch |
| **Supabase-js** | 2.38+ | Backend SDK | Ya implementado |

### UI y Estilos

| Tecnología | Versión | Propósito | Razón |
|-----------|---------|----------|-------|
| **Shadcn/ui** | Latest | UI Components | Accesible, customizable, Tailwind |
| **Tailwind CSS** | 3.3+ | Estilos | Utility-first, responsive |
| **Radix UI** | Latest | Headless Components | Basis de shadcn/ui |

### Validación y Formularios

| Tecnología | Versión | Propósito | Razón |
|-----------|---------|----------|-------|
| **React Hook Form** | 7.48+ | Formularios | Performance, integración con Zod |
| **Zod** | 3.22+ | Validación | TypeScript-first, esquemas |

### Desarrollo y Testing

| Tecnología | Versión | Propósito | Razón |
|-----------|---------|----------|-------|
| **Vitest** | 1+ | Unit Testing | Integración con Vite, rápido |
| **React Testing Library** | 14+ | Component Testing | Best practices, user-centric |
| **ESLint** | 8+ | Linting | Code quality |
| **Prettier** | 3+ | Code Formatting | Consistencia |

### Utilidades

| Tecnología | Propósito |
|-----------|----------|
| **date-fns** | Manipulación de fechas |
| **clsx** | Condicionales CSS |
| **axios** | HTTP client (alternativa fetch) |

### package.json Base

```json
{
  "name": "calendar-app-react",
  "type": "module",
  "version": "3.0.0",
  "description": "Sistema de Planificación Financiera - React Edition",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview",
    "lint": "eslint . --ext ts,tsx --report-unused-disable-directives --max-warnings 0",
    "format": "prettier --write \"src/**/*.{ts,tsx,css}\"",
    "test": "vitest",
    "test:ui": "vitest --ui"
  },
  "dependencies": {
    "react": "^18.2.0",
    "react-dom": "^18.2.0",
    "react-router-dom": "^6.20.0",
    "zustand": "^4.4.0",
    "@tanstack/react-query": "^5.0.0",
    "@supabase/supabase-js": "^2.38.0",
    "@radix-ui/react-dialog": "^1.1.0",
    "@radix-ui/react-popover": "^1.0.0",
    "@radix-ui/react-slot": "^2.0.0",
    "class-variance-authority": "^0.7.0",
    "clsx": "^2.0.0",
    "date-fns": "^2.30.0",
    "react-hook-form": "^7.48.0",
    "zod": "^3.22.0"
  },
  "devDependencies": {
    "@types/react": "^18.2.0",
    "@types/react-dom": "^18.2.0",
    "@types/node": "^20.0.0",
    "@typescript-eslint/eslint-plugin": "^6.0.0",
    "@typescript-eslint/parser": "^6.0.0",
    "@vitejs/plugin-react": "^4.2.0",
    "autoprefixer": "^10.4.0",
    "eslint": "^8.54.0",
    "eslint-plugin-react-hooks": "^4.6.0",
    "postcss": "^8.4.0",
    "prettier": "^3.1.0",
    "tailwindcss": "^3.3.0",
    "typescript": "^5.3.0",
    "vite": "^5.0.0",
    "vitest": "^1.0.0",
    "@testing-library/react": "^14.1.0",
    "@testing-library/jest-dom": "^6.1.0"
  }
}
```

---

## 5. Plan de Migración por Fases

### ⏱️ Fase 1: Preparación y Setup (4-6 horas) 🔴 CRÍTICA

**Objetivo:** Crear estructura base de React y migraciones tooling

**Tareas:**
- [ ] Crear nuevo proyecto Vite + React + TypeScript
- [ ] Instalar todas las dependencias del package.json
- [ ] Configurar Tailwind CSS y Shadcn/ui
- [ ] Configurar ESLint y Prettier
- [ ] Crear estructura de directorios (src/features, src/shared, etc.)
- [ ] Migrar variables de entorno a .env.local
- [ ] Configurar Zustand stores básicos
- [ ] Crear supabaseClient.ts reutilizable
- [ ] Configurar React Router con layout base

**Entregables:**
- Proyecto Vite funcional
- Build exitoso
- Dev server corriendo en http://localhost:5173
- Todos los linters pasando
- README actualizado con instrucciones de setup

**Estimado:** 4-6 horas

---

### ⏱️ Fase 2: Componentes Shared y Hooks (6-8 horas) 🔴 CRÍTICA

**Objetivo:** Crear base de componentes reutilizables y custom hooks

**Tareas:**
- [ ] Migrar/crear componentes UI desde Shadcn (Button, Input, Card, Modal, Dialog)
- [ ] Crear Header y Navigation principal
- [ ] Crear Layout wrapper component
- [ ] Crear custom hooks:
  - [ ] `useSupabase()` - wrapper de supabaseClient
  - [ ] `useCurrency()` - formateo de moneda
  - [ ] `useDateFormat()` - formateo de fechas
  - [ ] `useNotification()` - toasts (reemplazar SweetAlert2)
  - [ ] `useDebounce()` - para búsquedas
- [ ] Migrar utilidades (formatting.ts, validation.ts, etc.)
- [ ] Crear tipos TypeScript globales
- [ ] Configurar constants (currencies, frequencies, config)

**Entregables:**
- Librería de componentes funcionando
- Hooks reutilizables
- Sistema de notificaciones sin SweetAlert2
- Todos los tipos TypeScript definidos

**Estimado:** 6-8 horas

---

### ⏱️ Fase 3: Módulo de Autenticación (3-5 horas) 🔴 CRÍTICA

**Objetivo:** Migrar login, registro y recuperación de contraseña

**Tareas:**
- [ ] Crear authStore con Zustand (user, isAuthenticated, login, logout, register)
- [ ] Crear LoginForm.tsx con validación Zod
- [ ] Crear RegisterForm.tsx con validación Zod
- [ ] Crear RecoveryForm.tsx
- [ ] Crear ProtectedRoute wrapper (redirigir si no autenticado)
- [ ] Integrar Supabase Auth
- [ ] Crear authService.ts (login, register, logout, resetPassword)
- [ ] Agregar persistent login (localStorage + hydration)
- [ ] Crear useAuth hook

**Entregables:**
- Login/Register funcionando
- Auth persistente entre refreshes
- ProtectedRoute implementado
- Validación de formularios con mensajes claros

**Estimado:** 3-5 horas

---

### ⏱️ Fase 4: Módulo de Patrones (Patterns) (5-7 horas) 🟠 ALTA

**Objetivo:** Migrar getIncomePatterns y getExpensePatterns a React

**Tareas:**
- [ ] Crear patternsStore con Zustand (patterns, loading, error)
- [ ] Crear patternsService.ts con funciones CRUD
- [ ] Crear usePatternsHook
- [ ] Crear UI components:
  - [ ] PatternsManager.tsx
  - [ ] PatternList.tsx
  - [ ] PatternForm.tsx (income y expense)
  - [ ] PatternCard.tsx
- [ ] Integrar TanStack Query para data fetching
- [ ] Implementar búsqueda y filtros
- [ ] Crear pattern-types.ts con interfaces TypeScript

**Entregables:**
- CRUD de patrones funcionando
- Listado con filtros
- Formulario de creación/edición
- Validación con Zod
- Caching con React Query

**Estimado:** 5-7 horas

---

### ⏱️ Fase 5: Módulo de Planificación (Planning) (8-10 horas) 🟠 ALTA

**Objetivo:** Migrar dashboard de planificación, objetivos, sobres

**Tareas:**
- [ ] Crear planningStore con Zustand
- [ ] Crear planningService.ts
- [ ] Crear usePlanning, useGoals, useEnvelopes hooks
- [ ] Crear UI components:
  - [ ] PlanningDashboard.tsx (vista principal)
  - [ ] GoalList.tsx y GoalCard.tsx
  - [ ] GoalForm.tsx
  - [ ] EnvelopeList.tsx y EnvelopeCard.tsx
  - [ ] EnvelopeForm.tsx
  - [ ] ExpenseSummary.tsx
  - [ ] AllocationChart.tsx
- [ ] Integrar gráficos (opcional: recharts o chart.js)
- [ ] Implementar drag-and-drop para distribución de ingresos (react-beautiful-dnd)
- [ ] Crear planning-types.ts

**Entregables:**
- Dashboard de planificación funcional
- Crear/editar/borrar objetivos
- Crear/editar/borrar sobres
- Visualizar asignaciones
- Gráficos de distribución

**Estimado:** 8-10 horas

---

### ⏱️ Fase 6: Módulo de Calendario (Calendar) (7-9 horas) 🟠 ALTA

**Objetivo:** Migrar vista de calendario y eventos de movimientos

**Tareas:**
- [ ] Crear calendarStore con Zustand
- [ ] Crear calendarService.ts (obtener movimientos por fecha)
- [ ] Usar librería calendar (react-big-calendar o similar)
- [ ] Crear UI components:
  - [ ] CalendarView.tsx (vista principal)
  - [ ] EventModal.tsx
  - [ ] EventList.tsx
  - [ ] DayDetail.tsx
- [ ] Implementar filtros (por tipo, por categoría)
- [ ] Crear event-types.ts

**Entregables:**
- Calendario navegable
- Movimientos por fecha
- Modal con detalles del evento
- Filtros funcionales

**Estimado:** 7-9 horas

---

### ⏱️ Fase 7: Módulo Financial (Dashboard Financiero) (6-8 horas) 🟡 MEDIA

**Objetivo:** Migrar dashboard financiero, stats y motor financiero

**Tareas:**
- [ ] Crear financialStore
- [ ] Crear financialService.ts
- [ ] Crear useFinancial hook
- [ ] Crear UI components:
  - [ ] FinancialDashboard.tsx
  - [ ] BalancePanel.tsx
  - [ ] StatsPanel.tsx
  - [ ] TrendChart.tsx
  - [ ] EnginePanel.tsx
- [ ] Integrar gráficos avanzados
- [ ] Crear financial-types.ts

**Entregables:**
- Dashboard financiero completo
- Gráficos de tendencias
- Estadísticas visuales
- Recomendaciones del motor IA

**Estimado:** 6-8 horas

---

### ⏱️ Fase 8: Módulo Wishlist (5-7 horas) 🟡 MEDIA

**Objetivo:** Migrar gestor de lista de deseos

**Tareas:**
- [ ] Crear wishlistStore
- [ ] Crear wishlistService.ts
- [ ] Crear useWishlist hook
- [ ] Crear UI components:
  - [ ] WishlistDashboard.tsx
  - [ ] ProductCard.tsx
  - [ ] ProductForm.tsx
  - [ ] PriceHistory.tsx
- [ ] Integrar price monitoring
- [ ] Crear wishlist-types.ts

**Entregables:**
- Gestión de productos wishlist
- Gráfico de precios
- Tracking de descuentos
- Contador de días para compra

**Estimado:** 5-7 horas

---

### ⏱️ Fase 9: Módulos Complementarios (Loans, Savings) (4-6 horas) 🟡 MEDIA

**Objetivo:** Migrar préstamos y ahorros

**Tareas:**
- [ ] Crear loansStore y savingsStore
- [ ] Crear loansService.ts y savingsService.ts
- [ ] Crear LoansManager.tsx y SavingsManager.tsx
- [ ] Crear loans-types.ts y savings-types.ts

**Estimado:** 4-6 horas

---

### ⏱️ Fase 10: Testing y Optimización (6-8 horas) 🟠 IMPORTANTE

**Objetivo:** Crear suite de tests y optimizar performance

**Tareas:**
- [ ] Crear tests unitarios para hooks (usePatterns, usePlanning, etc.)
- [ ] Crear tests de componentes UI
- [ ] Crear tests de integración (Supabase queries)
- [ ] Tests e2e críticos (login, crear patrón, crear objetivo)
- [ ] Optimizar componentes (React.memo, useMemo donde sea necesario)
- [ ] Profiling de performance con DevTools
- [ ] Lazy loading de features
- [ ] Bundle analysis

**Entregables:**
- Cobertura mínima 70% de funciones críticas
- Todos los tests pasando
- Bundle size < 200KB (gzip)
- Lighthouse score > 80

**Estimado:** 6-8 horas

---

### ⏱️ Fase 11: Migración de Datos y Cleanup (2-4 horas) 🟡 MEDIA

**Objetivo:** Asegurar transición limpia del proyecto viejo

**Tareas:**
- [ ] Backup de index.html viejo
- [ ] Backup de js/ folder completo
- [ ] Migrar assets (imágenes, fuentes)
- [ ] Configurar redirecciones si es necesario
- [ ] Copiar favicon y manifest
- [ ] Documentar cambios en README

**Estimado:** 2-4 horas

---

### ⏱️ Fase 12: Deployment y Documentación (3-5 horas) 🟠 IMPORTANTE

**Objetivo:** Poner en producción y documentar

**Tareas:**
- [ ] Build production optimizado
- [ ] Configurar CI/CD (GitHub Actions si es público)
- [ ] Deploy a hosting (Vercel, Netlify, o servidor actual)
- [ ] Testing en producción
- [ ] Crear documentación para desarrolladores
- [ ] Crear guía de contribución
- [ ] Crear changelog de migración

**Estimado:** 3-5 horas

---

## 6. Módulos Prioritarios

### 🔴 Fase 1 (Semana 1): Crítica

1. **Setup Inicial** (4-6h)
2. **Autenticación** (3-5h)
3. **Componentes Shared** (6-8h)

**Total Fase 1:** 13-19 horas

### 🟠 Fase 2 (Semana 2-3): Alta Prioridad

1. **Patrones** (5-7h)
2. **Planificación** (8-10h)
3. **Calendario** (7-9h)

**Total Fase 2:** 20-26 horas

### 🟡 Fase 3 (Semana 4): Media Prioridad + Testing

1. **Financial Dashboard** (6-8h)
2. **Wishlist** (5-7h)
3. **Testing & QA** (6-8h)

**Total Fase 3:** 17-23 horas

### 📦 Post-Migración: Optimización y Deploy

1. **Cleanup & Data Migration** (2-4h)
2. **Deployment** (3-5h)
3. **Documentación** (2-3h)

**Total:** 7-12 horas

---

## 7. Estructura de Directorios

### Árbol Completo

```
calendar-app-react/
├── src/
│   ├── features/
│   │   ├── auth/
│   │   │   ├── ui/
│   │   │   │   ├── LoginForm.tsx
│   │   │   │   ├── RegisterForm.tsx
│   │   │   │   └── RecoveryForm.tsx
│   │   │   ├── store/
│   │   │   │   ├── authStore.ts
│   │   │   │   └── authSlice.ts
│   │   │   ├── services/
│   │   │   │   └── authService.ts
│   │   │   ├── hooks/
│   │   │   │   └── useAuth.ts
│   │   │   └── types/
│   │   │       └── auth.types.ts
│   │   │
│   │   ├── planning/
│   │   │   ├── ui/
│   │   │   │   ├── PlanningDashboard.tsx
│   │   │   │   ├── GoalList.tsx
│   │   │   │   ├── GoalCard.tsx
│   │   │   │   ├── GoalForm.tsx
│   │   │   │   ├── EnvelopeList.tsx
│   │   │   │   ├── EnvelopeCard.tsx
│   │   │   │   ├── EnvelopeForm.tsx
│   │   │   │   └── AllocationChart.tsx
│   │   │   ├── store/
│   │   │   │   └── planningStore.ts
│   │   │   ├── services/
│   │   │   │   └── planningService.ts
│   │   │   ├── hooks/
│   │   │   │   ├── usePlanning.ts
│   │   │   │   ├── useGoals.ts
│   │   │   │   └── useEnvelopes.ts
│   │   │   └── types/
│   │   │       └── planning.types.ts
│   │   │
│   │   ├── calendar/
│   │   │   ├── ui/
│   │   │   │   ├── CalendarView.tsx
│   │   │   │   ├── EventModal.tsx
│   │   │   │   ├── EventList.tsx
│   │   │   │   └── DayDetail.tsx
│   │   │   ├── store/
│   │   │   │   └── calendarStore.ts
│   │   │   ├── services/
│   │   │   │   └── calendarService.ts
│   │   │   ├── hooks/
│   │   │   │   └── useCalendar.ts
│   │   │   └── types/
│   │   │       └── calendar.types.ts
│   │   │
│   │   ├── patterns/
│   │   │   ├── ui/
│   │   │   │   ├── PatternsManager.tsx
│   │   │   │   ├── PatternList.tsx
│   │   │   │   ├── PatternCard.tsx
│   │   │   │   └── PatternForm.tsx
│   │   │   ├── store/
│   │   │   │   └── patternsStore.ts
│   │   │   ├── services/
│   │   │   │   └── patternsService.ts
│   │   │   ├── hooks/
│   │   │   │   └── usePatterns.ts
│   │   │   └── types/
│   │   │       └── patterns.types.ts
│   │   │
│   │   ├── wishlist/
│   │   │   ├── ui/
│   │   │   │   ├── WishlistDashboard.tsx
│   │   │   │   ├── ProductCard.tsx
│   │   │   │   ├── ProductForm.tsx
│   │   │   │   └── PriceHistory.tsx
│   │   │   ├── store/
│   │   │   │   └── wishlistStore.ts
│   │   │   ├── services/
│   │   │   │   └── wishlistService.ts
│   │   │   ├── hooks/
│   │   │   │   └── useWishlist.ts
│   │   │   └── types/
│   │   │       └── wishlist.types.ts
│   │   │
│   │   ├── financial/
│   │   │   ├── ui/
│   │   │   │   ├── FinancialDashboard.tsx
│   │   │   │   ├── BalancePanel.tsx
│   │   │   │   ├── StatsPanel.tsx
│   │   │   │   ├── TrendChart.tsx
│   │   │   │   └── EnginePanel.tsx
│   │   │   ├── store/
│   │   │   │   └── financialStore.ts
│   │   │   ├── services/
│   │   │   │   └── financialService.ts
│   │   │   ├── hooks/
│   │   │   │   └── useFinancial.ts
│   │   │   └── types/
│   │   │       └── financial.types.ts
│   │   │
│   │   ├── loans/
│   │   │   ├── ui/
│   │   │   │   └── LoansManager.tsx
│   │   │   ├── store/
│   │   │   │   └── loansStore.ts
│   │   │   ├── services/
│   │   │   │   └── loansService.ts
│   │   │   ├── hooks/
│   │   │   │   └── useLoans.ts
│   │   │   └── types/
│   │   │       └── loans.types.ts
│   │   │
│   │   ├── savings/
│   │   │   ├── ui/
│   │   │   │   └── SavingsManager.tsx
│   │   │   ├── store/
│   │   │   │   └── savingsStore.ts
│   │   │   ├── services/
│   │   │   │   └── savingsService.ts
│   │   │   ├── hooks/
│   │   │   │   └── useSavings.ts
│   │   │   └── types/
│   │   │       └── savings.types.ts
│   │   │
│   │   └── notifications/
│   │       ├── ui/
│   │       │   ├── Toast.tsx
│   │       │   └── NotificationCenter.tsx
│   │       ├── store/
│   │       │   └── notificationStore.ts
│   │       └── services/
│   │           └── notificationService.ts
│   │
│   ├── shared/
│   │   ├── components/
│   │   │   ├── ui/
│   │   │   │   ├── Button.tsx
│   │   │   │   ├── Input.tsx
│   │   │   │   ├── Card.tsx
│   │   │   │   ├── Modal.tsx
│   │   │   │   ├── Dialog.tsx
│   │   │   │   ├── Tabs.tsx
│   │   │   │   ├── Select.tsx
│   │   │   │   ├── Form.tsx
│   │   │   │   ├── Label.tsx
│   │   │   │   ├── Checkbox.tsx
│   │   │   │   ├── Radio.tsx
│   │   │   │   └── Badge.tsx
│   │   │   ├── layout/
│   │   │   │   ├── Header.tsx
│   │   │   │   ├── Navigation.tsx
│   │   │   │   ├── Sidebar.tsx
│   │   │   │   ├── Footer.tsx
│   │   │   │   └── AppLayout.tsx
│   │   │   └── common/
│   │   │       ├── Loading.tsx
│   │   │       ├── EmptyState.tsx
│   │   │       └── ErrorBoundary.tsx
│   │   ├── hooks/
│   │   │   ├── useSupabase.ts
│   │   │   ├── useCurrency.ts
│   │   │   ├── useDateFormat.ts
│   │   │   ├── useNotification.ts
│   │   │   ├── useDebounce.ts
│   │   │   ├── useAsync.ts
│   │   │   ├── useLocalStorage.ts
│   │   │   └── useAuth.ts
│   │   ├── services/
│   │   │   ├── supabaseClient.ts
│   │   │   ├── apiService.ts
│   │   │   └── storageService.ts
│   │   ├── types/
│   │   │   ├── database.types.ts
│   │   │   ├── common.types.ts
│   │   │   └── api.types.ts
│   │   ├── utils/
│   │   │   ├── formatting.ts
│   │   │   ├── validation.ts
│   │   │   ├── calculations.ts
│   │   │   ├── dateUtils.ts
│   │   │   ├── stringUtils.ts
│   │   │   └── arrayUtils.ts
│   │   ├── constants/
│   │   │   ├── currencies.ts
│   │   │   ├── frequencies.ts
│   │   │   ├── config.ts
│   │   │   ├── routes.ts
│   │   │   └── messages.ts
│   │   ├── styles/
│   │   │   ├── globals.css
│   │   │   ├── variables.css
│   │   │   └── animations.css
│   │   └── assets/
│   │       ├── icons/
│   │       ├── images/
│   │       └── fonts/
│   │
│   ├── App.tsx
│   ├── App.css
│   ├── main.tsx
│   ├── index.css
│   └── vite-env.d.ts
│
├── public/
│   ├── favicon.ico
│   ├── manifest.json
│   └── robots.txt
│
├── tests/
│   ├── unit/
│   │   ├── features/
│   │   │   ├── auth/
│   │   │   ├── planning/
│   │   │   └── ...
│   │   └── shared/
│   │       ├── hooks/
│   │       └── utils/
│   ├── integration/
│   │   └── supabase/
│   ├── e2e/
│   │   └── critical-flows.test.ts
│   └── setup.ts
│
├── docs/
│   ├── SETUP.md
│   ├── ARCHITECTURE.md
│   ├── DEVELOPMENT.md
│   ├── TESTING.md
│   ├── DEPLOYMENT.md
│   └── CONTRIBUTING.md
│
├── .env.example
├── .env.local (git-ignored)
├── .eslintrc.json
├── .prettierrc.json
├── tsconfig.json
├── vite.config.ts
├── vitest.config.ts
├── tailwind.config.ts
├── postcss.config.js
├── package.json
├── package-lock.json
├── README.md
└── .gitignore
```

---

## 8. Detalles Técnicos

### 8.1 Configuración de Zustand Store

**Ejemplo: planningStore.ts**

```typescript
import { create } from 'zustand';
import { devtools, persist } from 'zustand/middleware';

export interface Goal {
  id: string;
  userId: string;
  name: string;
  targetAmount: number;
  currentAmount: number;
  deadline: Date;
  active: boolean;
}

export interface PlanningStore {
  // State
  goals: Goal[];
  envelopes: Envelope[];
  expenses: Expense[];
  loading: boolean;
  error: string | null;

  // Actions
  setGoals: (goals: Goal[]) => void;
  addGoal: (goal: Goal) => void;
  updateGoal: (id: string, goal: Partial<Goal>) => void;
  deleteGoal: (id: string) => void;
  
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
}

export const usePlanningStore = create<PlanningStore>()(
  devtools(
    persist(
      (set) => ({
        // Initial state
        goals: [],
        envelopes: [],
        expenses: [],
        loading: false,
        error: null,

        // Actions
        setGoals: (goals) => set({ goals }),
        addGoal: (goal) =>
          set((state) => ({ goals: [...state.goals, goal] })),
        updateGoal: (id, goal) =>
          set((state) => ({
            goals: state.goals.map((g) =>
              g.id === id ? { ...g, ...goal } : g
            ),
          })),
        deleteGoal: (id) =>
          set((state) => ({
            goals: state.goals.filter((g) => g.id !== id),
          })),

        setLoading: (loading) => set({ loading }),
        setError: (error) => set({ error }),
      }),
      {
        name: 'planning-store',
      }
    )
  )
);
```

### 8.2 Custom Hooks Pattern

**Ejemplo: usePatterns.ts**

```typescript
import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { patternsService } from '../services/patternsService';
import { useAuth } from '@/shared/hooks/useAuth';

export const usePatterns = () => {
  const { user } = useAuth();
  const [activeOnly, setActiveOnly] = useState(false);

  const {
    data: expensePatterns = [],
    isLoading: expenseLoading,
    error: expenseError,
    refetch: refetchExpenses,
  } = useQuery({
    queryKey: ['expensePatterns', user?.id, activeOnly],
    queryFn: () =>
      patternsService.getExpensePatterns(user?.id || '', activeOnly),
    enabled: !!user?.id,
  });

  const {
    data: incomePatterns = [],
    isLoading: incomeLoading,
    error: incomeError,
    refetch: refetchIncomes,
  } = useQuery({
    queryKey: ['incomePatterns', user?.id, activeOnly],
    queryFn: () =>
      patternsService.getIncomePatterns(user?.id || '', activeOnly),
    enabled: !!user?.id,
  });

  const createExpense = async (pattern: Omit<ExpensePattern, 'id'>) => {
    await patternsService.createExpensePattern(user?.id || '', pattern);
    refetchExpenses();
  };

  return {
    expensePatterns,
    incomePatterns,
    loading: expenseLoading || incomeLoading,
    error: expenseError || incomeError,
    createExpense,
    refetch: () => {
      refetchExpenses();
      refetchIncomes();
    },
  };
};
```

### 8.3 Service Layer Pattern

**Ejemplo: patternsService.ts**

```typescript
import { supabase } from '@/shared/services/supabaseClient';
import { ExpensePattern, IncomePattern } from '../types/patterns.types';

export const patternsService = {
  async getExpensePatterns(
    userId: string,
    activeOnly = false
  ): Promise<ExpensePattern[]> {
    let query = supabase
      .from('expense_patterns')
      .select('*')
      .eq('user_id', userId)
      .order('name', { ascending: true });

    if (activeOnly) {
      query = query.eq('active', true);
    }

    const { data, error } = await query;
    if (error) throw error;
    return data || [];
  },

  async createExpensePattern(
    userId: string,
    pattern: Omit<ExpensePattern, 'id' | 'userId'>
  ): Promise<ExpensePattern> {
    const { data, error } = await supabase
      .from('expense_patterns')
      .insert({
        ...pattern,
        user_id: userId,
      })
      .select()
      .single();

    if (error) throw error;
    return data;
  },

  // ... más métodos CRUD
};
```

### 8.4 Component Structure

**Ejemplo: PatternsManager.tsx (Container)**

```typescript
import { usePatterns } from '../hooks/usePatterns';
import { PatternList } from './PatternList';
import { PatternForm } from './PatternForm';
import { Loading } from '@/shared/components/common/Loading';

export const PatternsManager: React.FC = () => {
  const {
    expensePatterns,
    incomePatterns,
    loading,
    error,
    createExpense,
    refetch,
  } = usePatterns();

  if (loading) return <Loading />;
  if (error) return <ErrorBoundary error={error} />;

  return (
    <div className="patterns-manager">
      <h1>Gestión de Patrones</h1>
      <PatternForm onSubmit={createExpense} />
      <div className="patterns-grid">
        <section>
          <h2>Ingresos</h2>
          <PatternList patterns={incomePatterns} type="income" />
        </section>
        <section>
          <h2>Gastos</h2>
          <PatternList patterns={expensePatterns} type="expense" />
        </section>
      </div>
    </div>
  );
};
```

---

## 9. Consideraciones de Seguridad

### 9.1 Row Level Security (RLS)

**Mantener todas las políticas actuales:**

```sql
-- Ejemplo que DEBE mantenerse
CREATE POLICY "Users can only see their own patterns"
ON expense_patterns
FOR SELECT
USING (auth.uid() = user_id);

CREATE POLICY "Users can only update their own patterns"
ON expense_patterns
FOR UPDATE
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);
```

### 9.2 Environment Variables

**.env.local (NUNCA commitar)**

```
VITE_SUPABASE_URL=https://your-project.supabase.co
VITE_SUPABASE_ANON_KEY=your-public-anon-key
```

**.env.example (sí commitar)**

```
VITE_SUPABASE_URL=https://your-project.supabase.co
VITE_SUPABASE_ANON_KEY=your-public-anon-key
```

### 9.3 Authentication Flow

```
User Login
  ↓
Supabase Auth
  ↓
JWT Token (stored in localStorage)
  ↓
authStore.setUser(user)
  ↓
useAuth() in components
  ↓
ProtectedRoute checks auth
  ↓
API calls include JWT automatically
```

### 9.4 CORS y CSRF

- Supabase maneja CORS automáticamente
- JWT tokens previenen CSRF attacks
- No enviar credenciales en cookies

---

## 10. Testing y QA

### 10.1 Estrategia de Testing

| Tipo | Herramienta | Cobertura |
|------|-------------|-----------|
| Unit Tests | Vitest + RTL | 70% crítico |
| Integration Tests | Vitest + Supabase | 50% crítico |
| E2E Tests | Playwright o Cypress | Flows críticos |
| Visual Tests | Chromatic (opcional) | Componentes Shared |

### 10.2 Checklist de QA Crítica

**Antes de Release:**

- [ ] Login/Logout funciona
- [ ] Crear/editar/borrar patrones funciona
- [ ] Crear/editar/borrar objetivos funciona
- [ ] Calendario muestra movimientos correctos
- [ ] Cálculos de balance son exactos
- [ ] Datos persisten entre sesiones
- [ ] No hay errores en console
- [ ] Mobile responsive (375px, 768px, 1024px)
- [ ] Performance Lighthouse > 80
- [ ] No hay XSS vulnerabilities

---

## 11. Deployment

### 11.1 Opciones de Hosting

| Opción | Costo | Setup | Recomendación |
|--------|-------|-------|---------------|
| **Vercel** | Free/Pro | ⭐⭐ Muy fácil | ✅ Recomendado |
| **Netlify** | Free/Pro | ⭐⭐ Muy fácil | ✅ Alternativa |
| **GitHub Pages** | Gratis | ⭐⭐⭐ Medio | Si es static |
| **Tu servidor** | Depende | ⭐⭐⭐⭐ Complejo | Si tienes |

### 11.2 CI/CD Pipeline (GitHub Actions)

```yaml
# .github/workflows/deploy.yml
name: Deploy to Vercel

on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '18'
      
      - run: npm ci
      - run: npm run lint
      - run: npm run test
      - run: npm run build
      
      - name: Deploy to Vercel
        uses: vercel/action@master
        with:
          vercel-token: ${{ secrets.VERCEL_TOKEN }}
          vercel-org-id: ${{ secrets.VERCEL_ORG_ID }}
          vercel-project-id: ${{ secrets.VERCEL_PROJECT_ID }}
```

### 11.3 Monitoreo Post-Deployment

- Sentry para error tracking
- LogRocket para session replay
- Posthog para analytics
- Supabase dashboard para DB metrics

---

## 12. Timeline y Hitos

### 📅 Cronograma Estimado (3-4 semanas)

```
Semana 1: Preparación y Bases
├── Lunes-Martes (8h): Setup Vite + React + TypeScript
├── Miércoles (4h): Componentes Shared y Hooks
├── Jueves-Viernes (8h): Auth module completo
└── Hito 1: ✅ Login funcional

Semana 2: Módulos Críticos (I)
├── Lunes-Martes (8h): Patterns module
├── Miércoles-Jueves (10h): Planning module
├── Viernes (6h): Testing básico
└── Hito 2: ✅ Patrones y Planning funcionales

Semana 3: Módulos Críticos (II) + Complementarios
├── Lunes-Martes (8h): Calendar module
├── Miércoles (6h): Financial dashboard
├── Jueves (6h): Wishlist + Loans/Savings
└── Hito 3: ✅ Todos los módulos funcionales

Semana 4: Polish, Testing y Deploy
├── Lunes-Martes (8h): Tests + Bug fixes
├── Miércoles (4h): Performance optimization
├── Jueves (4h): Documentación
├── Viernes (4h): Deploy a producción
└── Hito 4: ✅ En vivo y documentado
```

### 🎯 Hitos Principales

| Hito | Semana | Entregables | Validación |
|------|--------|-------------|-----------|
| **Setup Base** | 1 | Proyecto Vite, React 18, TS, Zustand | `npm run dev` funciona |
| **Auth + UI** | 1 | Login, Register, Componentes Shared | Login exitoso |
| **Patterns & Planning** | 2 | CRUD patrones, dashboard planning | Crear patrón y objetivo |
| **Calendar & Financial** | 3 | Calendario, dashboard financiero | Ver movimientos |
| **Wishlist & Loans** | 3 | Wishlist, préstamos, ahorros | Todos funcionales |
| **Testing & Docs** | 4 | Tests, Docs completa, DevGuide | 70% coverage |
| **Production Ready** | 4 | Deploy, Monitoring, Changelog | En vivo y estable |

---

## 13. Checklist de Ejecución

### Fase 1: Setup

- [ ] Crear nuevo repo (o rama feature)
- [ ] `npm create vite@latest -- --template react-ts`
- [ ] Instalar dependencias core
- [ ] Configurar TypeScript (tsconfig.json)
- [ ] Setup Tailwind CSS
- [ ] Setup Shadcn/ui
- [ ] Configurar ESLint y Prettier
- [ ] Crear estructura de directorios
- [ ] Configurar Zustand
- [ ] Primer commit

### Fase 2: Componentes Base

- [ ] Crear Button, Input, Card, Modal components
- [ ] Crear Header y Navigation
- [ ] Crear AppLayout wrapper
- [ ] Crear custom hooks (useSupabase, useCurrency, etc.)
- [ ] Crear tipos TypeScript globales
- [ ] Crear constants (currencies, frequencies)
- [ ] Crear utilidades (formatting, validation, etc.)

### Fase 3: Autenticación

- [ ] Crear authStore con Zustand
- [ ] Crear authService.ts
- [ ] Migrar LoginForm.tsx
- [ ] Migrar RegisterForm.tsx
- [ ] Migrar RecoveryForm.tsx
- [ ] Crear ProtectedRoute
- [ ] Testing básico de auth
- [ ] Persistencia de sesión

### Fase 4-8: Módulos (repetir para cada uno)

- [ ] Crear store
- [ ] Crear service
- [ ] Crear custom hook
- [ ] Crear UI components
- [ ] Integrar TanStack Query
- [ ] Testing
- [ ] Documentación inline

### Fase 9-12: Finalización

- [ ] Testing suite completa
- [ ] Performance optimization
- [ ] Documentación README, SETUP.md, etc.
- [ ] Cleanup archivos viejos
- [ ] CI/CD setup
- [ ] Deploy a staging
- [ ] Deploy a producción
- [ ] Monitoreo

---

## 📚 Documentación Adicional Requerida

Crear después de setup:

- [ ] **SETUP.md** - Instrucciones de instalación y desarrollo local
- [ ] **ARCHITECTURE.md** - Explicación de Feature-Sliced Design
- [ ] **DEVELOPMENT.md** - Guía para desarrolladores
- [ ] **TESTING.md** - Cómo escribir tests
- [ ] **DEPLOYMENT.md** - Cómo deployar
- [ ] **CONTRIBUTING.md** - Guía de contribución
- [ ] **API.md** - Documentación de store + hooks
- [ ] **TROUBLESHOOTING.md** - Problemas comunes

---

## 🚨 Riesgos y Mitigación

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|-------------|--------|-----------|
| Supabase API changes | Baja | Alta | Monitorear changelogs |
| Performance degradation | Media | Media | Profiling desde día 1 |
| Type errors en runtime | Baja | Media | Strict TypeScript |
| Regresiones en funcionalidad | Alta | Alta | Testing exhaustivo |
| Breaking changes en deps | Baja | Media | Lockfile.lock |
| Scope creep | Media | Alta | Strict checklist |

---

## 💡 Tips para Éxito

1. **Migra módulo por módulo**, no todo a la vez
2. **Mantén el código viejo** hasta que todo funcione
3. **Escribe tests mientras migras**
4. **No optimices prematuramente** - primero funciona, luego optimiza
5. **Documenta mientras avanzas** - no dejes para el final
6. **Usa React DevTools y TypeScript** - son tus mejores amigos
7. **Commits pequeños y frecuentes** - facilita debugging
8. **Haz code reviews** - aunque sea contigo mismo
9. **Monitorea en producción** - Sentry es tu amigo
10. **Comunica progreso** - mantén stakeholders informados

---

## 📞 Próximos Pasos

1. **Revisar este documento** - asegúrate que alineamos en visión
2. **Confirmar stack tecnológico** - ¿Zustand o Redux? ¿Shadcn o MUI?
3. **Crear repositorio** - nueva rama o nuevo repo
4. **Setup inicial** - seguir Fase 1 del plan
5. **Asignar recursos** - cuánta gente, cuánto tiempo
6. **Monitorear progreso** - reviews semanales

---

**Documento Creado:** 4 de enero de 2026  
**Estimado Total:** 40-60 horas  
**Timeline Recomendado:** 3-4 semanas  
**Status:** 🟡 En Preparación - Esperando confirmación

