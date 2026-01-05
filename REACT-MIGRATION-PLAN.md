# 📋 Plan de Migración a React
**Documento: Planeación, Guía y Planificación para Migración a React**

**Fecha de Creación:** 4 de enero de 2026  
**Última Actualización:** 5 de enero de 2026  
**Proyecto:** Sistema de Planificación Financiera  
**Estado:** Planeación - Revisado y Mejorado  
**Tiempo Estimado Total:** 80-100 horas (4-5 semanas)  
**Cambios:** Estimaciones aumentadas, testing integrado desde Fase 1, CI/CD y rollback plan incluidos

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
- Mitigación de riesgo: usuarios activos durante migración
- Rollback plan no documentado
- Monitoreo y error tracking no configurado
- CI/CD pipeline no existe

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

### ✅ DECISIONES TECNOLÓGICAS FINALES (Sección 5)

**NOTA IMPORTANTE:** Las decisiones tecnológicas específicas están en la **Sección 5 - Decisiones Tecnológicas Previas** que DEBE completarse ANTES de empezar la Fase 1.

---

## 5. Stack Tecnológico Base Recomendado

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

## 5. Decisiones Tecnológicas Previas

### ⚠️ ANTES DE INICIAR - DECISIONES REQUERIDAS

Estas decisiones DEBEN tomarse antes de empezar cualquier desarrollo:

| Decisión | Opciones | Recomendación | Impacto |
|----------|----------|---------------|--------|
| **State Manager** | Zustand / Redux | ✅ Zustand | Bajo boilerplate, ideal para app finanzas |
| **UI Framework** | Shadcn/ui / MUI / Ant Design | ✅ Shadcn/ui + Tailwind | Customizable, accesible, moderno |
| **Calendar Lib** | React Big Calendar / FullCalendar / TUI | ✅ React Big Calendar | Ligero, flexible, mantenido |
| **Charts Lib** | Recharts / Chart.js / Visx | ✅ Recharts | React-native, componentes, fácil |
| **Hosting** | Vercel / Netlify / Servidor propio | ✅ Vercel | CI/CD automático, preview, monitoreo |
| **Error Tracking** | Sentry / LogRocket / Rollbar | ✅ Sentry | Mejor para apps financieras |
| **Form Lib** | React Hook Form / Formik | ✅ React Hook Form | Performance, Zod integration |
| **Date Lib** | date-fns / dayjs / luxon | ✅ date-fns | Más mantenido, modular |

### ✅ Checklist Pre-Inicio

- [ ] Stack tecnológico confirmado por todo el equipo
- [ ] Backup completo de código actual (js/ y routes/ folders)
- [ ] Base de datos con backups automáticos configurados
- [ ] Variables de entorno documentadas (.env.example)
- [ ] Supabase RLS policies revisadas y documentadas
- [ ] Database schema V2 está completo y validado
- [ ] Equipo capacitado en React, TypeScript, Zustand
- [ ] CI/CD infrastructure lista (GitHub, Vercel/Netlify)
- [ ] Monitoring tools accounts creadas (Sentry, etc.)
- [ ] Communication plan para usuarios durante migración
- [ ] Rollback procedure documentado y testeado
- [ ] Performance baseline medido (actual build size, lighthouse)

---

## 6. Plan de Migración por Fases (REVISADO)

### ⏱️ Fase 0: Pre-Setup (2-3 horas) 🔴 CRÍTICA - ANTES DE TODO

**Objetivo:** Preparar environment y documentar estado actual

**Tareas:**
- [ ] Crear rama `feat/react-migration` en git
- [ ] Backup completo de proyecto actual
- [ ] Documentar URL actual de Supabase y todas las env vars
- [ ] Crear issue de GitHub para tracking de progreso
- [ ] Setup Sentry account para error tracking
- [ ] **Crear documento ROLLBACK PLAN** (ver abajo)
- [ ] Database schema snapshot y documentación
- [ ] Documentar current performance baseline (Lighthouse, bundle size)
- [ ] Crear spreadsheet de tracking de features

**Entregables:**
- Backups seguros
- Documento de rollback plan firmado
- Environment variables documentadas
- Performance baseline establecido
- GitHub issue con checklist de tracking

**Estimado:** 2-3 horas

---

### ⏱️ Fase 1: Preparación y Setup (6-8 horas) 🔴 CRÍTICA

**Objetivo:** Crear estructura base de React con CI/CD desde el inicio

**Tareas:**
- [ ] Crear nuevo proyecto Vite + React + TypeScript
- [ ] Instalar todas las dependencias del package.json
- [ ] Configurar Tailwind CSS y Shadcn/ui
- [ ] Configurar ESLint y Prettier
- [ ] Crear estructura de directorios (src/features, src/shared, etc.)
- [ ] Migrar variables de entorno a .env.local + .env.example
- [ ] Configurar Zustand stores básicos
- [ ] Crear supabaseClient.ts reutilizable
- [ ] Configurar React Router con layout base
- [ ] **Setup Vitest + React Testing Library**
- [ ] **Configurar CI/CD (GitHub Actions o Vercel)**
- [ ] **Setup Sentry para error tracking**
- [ ] **Crear GitHub issue tracker del progreso**
- [ ] **Crear primeros tests de ejemplo**
- [ ] **Setup pre-commit hooks (husky + lint-staged)**

**Entregables:**
- Proyecto Vite funcional
- Build exitoso
- Dev server corriendo en http://localhost:5173
- Todos los linters pasando
- CI/CD pipeline funcionando
- Sentry configurado
- Tests setup working
- README actualizado con instrucciones de setup
- GitHub Actions passing
- Pre-commit hooks configurados

**Estimado:** 6-8 horas

---

### ⏱️ Fase 2: Componentes Shared y Hooks (8-10 horas) 🔴 CRÍTICA

**Objetivo:** Crear base de componentes reutilizables y custom hooks CON TESTS

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
  - [ ] `useLocalStorage()` - persistencia de datos
- [ ] Migrar utilidades (formatting.ts, validation.ts, etc.)
- [ ] Crear tipos TypeScript globales
- [ ] Configurar constants (currencies, frequencies, config)
- [ ] **Escribir tests para cada componente y hook**
- [ ] **Tests coverage > 80% para este módulo**

**Entregables:**
- Librería de componentes funcionando
- Hooks reutilizables
- Sistema de notificaciones sin SweetAlert2
- Todos los tipos TypeScript definidos
- Tests para componentes shared
- Documentación de componentes (Storybook opcional)

**Estimado:** 8-10 horas

---

## 7. Modules Prioritarios (REVISADO)

### 🔴 Fase 0 + Fase 1 (Semana 1): Preparación y Setup

1. **Pre-Setup** (2-3h) - Backups, decisiones, rollback plan
2. **Setup Inicial + CI/CD** (6-8h)
3. **Componentes Shared** (8-10h)

**Total Fases 0-1:** 16-21 horas

### 🔴 Fase 2-3 (Semana 1-2): Crítica

1. **Autenticación con Tests** (5-7h)
2. **Patrones con Tests** (7-9h)

**Total Fases 2-3:** 12-16 horas

### 🟠 Fase 4-6 (Semana 2-3): Alta Prioridad

1. **Planificación con Tests** (10-14h)
2. **Calendario con Tests** (9-11h)
3. **Financial Dashboard** (8-10h)

**Total Fases 4-6:** 27-35 horas

### 🟡 Fase 7-9 (Semana 4): Media Prioridad

1. **Wishlist con Tests** (7-9h)
2. **Loans & Savings con Tests** (5-7h)

**Total Fases 7-9:** 12-16 horas

### 🟠 Fase 10-12 (Semana 4-5): Testing, Optimización y Deploy

1. **Testing Integral & Performance** (10-12h)
2. **Migration & User Communication** (4-6h)
3. **Deployment & Documentation** (5-7h)

**Total Fases 10-12:** 19-25 horas

---

**TOTAL ESTIMADO REVISADO: 86-113 horas (4-5 semanas full-time)**

*Nota: Estimación anterior era 40-60h - incremento por testing integrado desde inicio y gestión de usuarios en producción.*

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

### ⏱️ Fase 3: Módulo de Autenticación (5-7 horas) 🔴 CRÍTICA

**Objetivo:** Migrar login, registro y recuperación de contraseña CON TESTS COMPLETOS

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
- [ ] **Escribir tests de auth store**
- [ ] **Tests de formularios (validación, submit, errors)**
- [ ] **Tests de integración Supabase Auth**
- [ ] **Tests de ProtectedRoute**
- [ ] **Coverage > 85% para auth crítico**

**Entregables:**
- Login/Register funcionando
- Auth persistente entre refreshes
- ProtectedRoute implementado
- Validación de formularios con mensajes claros
- Tests de auth suite completa
- Documentación de auth flow

**Estimado:** 5-7 horas

---

### ⏱️ Fase 4: Módulo de Patrones (Patterns) (7-9 horas) 🟠 ALTA

**Objetivo:** Migrar getIncomePatterns y getExpensePatterns a React CON TESTS

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
- [ ] **Tests para patternsService (mock Supabase)**
- [ ] **Tests para usePatterns hook**
- [ ] **Tests de componentes UI**
- [ ] **Tests e2e: crear patrón, editar, eliminar**
- [ ] **Coverage > 80%**

**Entregables:**
- CRUD de patrones funcionando
- Listado con filtros
- Formulario de creación/edición
- Validación con Zod
- Caching con React Query
- Tests suite for patterns module
- Error handling documentado

**Estimado:** 7-9 horas

---

### ⏱️ Fase 5: Módulo de Planificación (Planning) (10-14 horas) 🟠 ALTA

**Objetivo:** Migrar dashboard de planificación, objetivos, sobres CON TESTS EXHAUSTIVOS

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
- [ ] Integrar gráficos (✅ recharts recomendado)
- [ ] Implementar drag-and-drop para distribución de ingresos (react-beautiful-dnd)
- [ ] Crear planning-types.ts
- [ ] **Tests para cada hook (usePlanning, useGoals, useEnvelopes)**
- [ ] **Tests de cálculos financieros (validar exactitud)**
- [ ] **Tests de componentes UI (especially forms)**
- [ ] **Tests e2e: flujo completo de planning**
- [ ] **Tests de gráficos (snapshot testing)**
- [ ] **Coverage > 80%**
- [ ] **Validar que cálculos matchean versión vieja**

**Entregables:**
- Dashboard de planificación funcional
- Crear/editar/borrar objetivos
- Crear/editar/borrar sobres
- Visualizar asignaciones
- Gráficos de distribución
- Tests suite exhaustivos
- Validación de exactitud numérica
- Documentación de cálculos

**Estimado:** 10-14 horas

---

### ⏱️ Fase 6: Módulo de Calendario (Calendar) (9-11 horas) 🟠 ALTA

**Objetivo:** Migrar vista de calendario y eventos de movimientos CON TESTS

**Tareas:**
- [ ] Crear calendarStore con Zustand
- [ ] Crear calendarService.ts (obtener movimientos por fecha)
- [ ] Usar librería calendar (✅ React Big Calendar recomendado)
- [ ] Crear UI components:
  - [ ] CalendarView.tsx (vista principal)
  - [ ] EventModal.tsx
  - [ ] EventList.tsx
  - [ ] DayDetail.tsx
- [ ] Implementar filtros (por tipo, por categoría)
- [ ] Crear event-types.ts
- [ ] **Tests para calendarService (date handling)**
- [ ] **Tests para componentes de calendario**
- [ ] **Tests de filtros (edge cases con fechas)**
- [ ] **Tests de navegación en calendario**
- [ ] **Coverage > 75%**

**Entregables:**
- Calendario navegable
- Movimientos por fecha
- Modal con detalles del evento
- Filtros funcionales
- Tests de calendario
- Manejo correcto de timezones

**Estimado:** 9-11 horas

---

### ⏱️ Fase 7: Módulo Financial (Dashboard Financiero) (8-10 horas) 🟡 MEDIA

**Objetivo:** Migrar dashboard financiero, stats y motor financiero CON TESTS

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
- [ ] Integrar gráficos avanzados (✅ recharts)
- [ ] Crear financial-types.ts
- [ ] **Tests para cálculos financieros**
- [ ] **Tests para estadísticas**
- [ ] **Tests de componentes de dashboard**
- [ ] **Coverage > 75%**

**Entregables:**
- Dashboard financiero completo
- Gráficos de tendencias
- Estadísticas visuales
- Recomendaciones del motor IA
- Tests de financial module

**Estimado:** 8-10 horas

---

### ⏱️ Fase 8: Módulo Wishlist (7-9 horas) 🟡 MEDIA

**Objetivo:** Migrar gestor de lista de deseos CON TESTS

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
- [ ] **Tests para wishlist CRUD**
- [ ] **Tests de price tracking**
- [ ] **Coverage > 70%**

**Entregables:**
- Gestión de productos wishlist
- Gráfico de precios
- Tracking de descuentos
- Contador de días para compra
- Tests de wishlist

**Estimado:** 7-9 horas

---

### ⏱️ Fase 9: Módulos Complementarios (Loans, Savings) (5-7 horas) 🟡 MEDIA

**Objetivo:** Migrar préstamos y ahorros CON TESTS

**Tareas:**
- [ ] Crear loansStore y savingsStore
- [ ] Crear loansService.ts y savingsService.ts
- [ ] Crear LoansManager.tsx y SavingsManager.tsx
- [ ] Crear loans-types.ts y savings-types.ts
- [ ] **Tests para loans y savings stores**
- [ ] **Coverage > 70%**

**Estimado:** 5-7 horas

---

### ⏱️ Fase 10: Testing Integral y Optimización (10-12 horas) 🟠 IMPORTANTE

**Objetivo:** Testing exhaustivo (ya que muchos tests se hicieron en fases anteriores, ahora enfocamos en integración, e2e, y performance)

**Tareas:**
- [ ] **Crear tests e2e críticos (Playwright/Cypress):**
  - [ ] Flujo completo: login → crear patrón → crear objetivo → ver calendar
  - [ ] Crear envelope → asignar dinero → ver en dashboard
  - [ ] Wishlist: agregar producto → ver price history → recibir alerta
  - [ ] Financial: ver balance → ver tendencias → ver recomendaciones
- [ ] **Tests de integración Supabase:**
  - [ ] Auth flow completo
  - [ ] CRUD operations para cada tabla
  - [ ] RLS policies validation
  - [ ] Concurrency handling
- [ ] **Tests de regresión:** Validar que todo matchea versión vieja
- [ ] **Optimizar componentes:**
  - [ ] React.memo donde sea necesario
  - [ ] useMemo/useCallback para cálculos costosos
  - [ ] Code splitting y lazy loading
  - [ ] Image optimization
- [ ] **Performance profiling:**
  - [ ] Chrome DevTools profiling
  - [ ] Bundle analysis
  - [ ] Lighthouse audit (target > 85)
  - [ ] Render performance
- [ ] **Accessibility testing:**
  - [ ] WCAG 2.1 AA compliance
  - [ ] Keyboard navigation
  - [ ] Screen reader testing

**Entregables:**
- Cobertura total > 80% de funciones críticas
- Todos los tests pasando (unit + integration + e2e)
- Bundle size < 200KB (gzip)
- Lighthouse score > 85
- 0 critical accessibility issues
- Performance regression report

**Estimado:** 10-12 horas

---

### ⏱️ Fase 11: Migración de Datos, User Communication y Cleanup (4-6 horas) 🟠 ALTA

**Objetivo:** Transición limpia del proyecto viejo con plan comunicación usuarios

**Tareas:**
- [ ] Backup de index.html viejo
- [ ] Backup de js/ folder completo
- [ ] Migrar assets (imágenes, fuentes)
- [ ] Validar que Supabase RLS policies siguen activas
- [ ] **Data validation:** Verificar que todos los datos se ven correctamente en React
- [ ] **User communication plan:** Mensajes de mantenimiento, downtime schedule
- [ ] Configurar redirecciones si es necesario
- [ ] Copiar favicon y manifest
- [ ] **Test con usuarios reales (if possible):** 5-10 usuarios
- [ ] Documentar cambios en README y CHANGELOG
- [ ] Database schema versioning

**Entregables:**
- Backups seguros
- User communication plan ejecutado
- Data integrity validation report
- Rollback test completed
- Updated CHANGELOG
- Migration runbook documented

**Estimado:** 4-6 horas

---

### ⏱️ Fase 12: Deployment, Monitoreo y Documentación (5-7 horas) 🟠 IMPORTANTE

**Objetivo:** Poner en producción CON MONITOREO Y DOCUMENTACIÓN COMPLETA

**Tareas:**
- [ ] Build production optimizado
- [ ] Verificar que CI/CD pipeline está completo
- [ ] Deploy a staging environment
- [ ] Smoke testing en staging
- [ ] **Deploy a producción:** Usar blue-green deployment si es posible
- [ ] **Monitoreo en vivo:**
  - [ ] Sentry errors tracking
  - [ ] Performance monitoring
  - [ ] User session monitoring (Supabase logs)
  - [ ] Database query monitoring
  - [ ] Error rate alerts configurados
- [ ] **Rollback procedure test:** Verificar que rollback plan funciona
- [ ] Documentación post-deployment:
  - [ ] SETUP.md (development)
  - [ ] DEPLOYMENT.md (release process)
  - [ ] ARCHITECTURE.md (tech decisions)
  - [ ] TROUBLESHOOTING.md (common issues)
  - [ ] CONTRIBUTING.md (team guidelines)
  - [ ] API.md (stores, hooks, services)
  - [ ] CHANGELOG.md (migration notes)
- [ ] Crear runbook de incidents
- [ ] Training para equipo de support

**Entregables:**
- Production deployment exitoso
- Sentry + monitoring fully operational
- Comprehensive documentation
- Rollback capability verified
- Team training completed
- Post-deployment metrics baseline
- Incident response procedures documented

**Estimado:** 5-7 horas

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

## 8. Plan de Rollback (NUEVO - CRÍTICO)

### 🚨 Escenarios de Rollback

**Trigger Points:** Cuándo regresar a versión vieja inmediatamente

1. **Datos Corruptos:** Si se detecta pérdida de datos en producción
2. **Auth Broken:** Si login/logout no funciona
3. **Critical Bug:** Si aplicación no abre o crashes
4. **Performance Degradation:** Si es > 50% más lenta que versión vieja
5. **User Complaint Rate:** Si > 20% de usuarios reportan problemas en primeras 24h

### Rollback Procedure

**Tiempo estimado:** 15-30 minutos

```
1. Detectar problema (usuario report o monitoring alert)
2. ↓
3. Activar rollback decision (CTO/Lead approval)
4. ↓
5. Si Vercel: Revert to previous deployment
   Si servidor: Switch DNS back a version vieja
6. ↓
7. Verify que users pueden acceder
8. ↓
9. Post-mortem: Documentar qué falló
10. ↓
11. Implementar fix
12. ↓
13. Deploy retry con testing más exhaustivo
```

### Pre-Deployment Checklist para Evitar Rollback

- [ ] **Data Integrity:** Verificar datos en staging matchean producción vieja
- [ ] **User Testing:** 5-10 usuarios reales testean antes de public release
- [ ] **Load Testing:** Simular 2x traffic en staging
- [ ] **Browser Compatibility:** Chrome, Firefox, Safari, Edge
- [ ] **Mobile Testing:** iOS Safari, Android Chrome
- [ ] **VPN Testing:** Verificar app funciona con VPN
- [ ] **Offline Mode:** Verificar app graceful degradation sin internet
- [ ] **Monitoring Alerts:** Todos los alertas activos en Sentry

---

## 9. Detalles Técnicos

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

## 10. Consideraciones de Seguridad

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

## 11. Testing y QA (REVISADO)

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

### 11.1 Estrategia de Testing (REVISADA - TESTS DESDE FASE 1)

| Tipo | Herramienta | Cuándo | Cobertura |
|------|-------------|--------|-----------|
| **Unit Tests** | Vitest + RTL | En cada feature (Fase 1+) | 80%+ crítico |
| **Integration Tests** | Vitest + Supabase | Fases 3-9 | 70%+ servicios |
| **E2E Tests** | Playwright/Cypress | Fase 10 | Flows críticos |
| **Visual/Snapshot** | Jest Snapshots | Con cada componente | Cambios visuales |
| **Accessibility Tests** | axe-core | Fase 10 | WCAG 2.1 AA |
| **Performance Tests** | Lighthouse CI | En cada PR (Fase 1+) | Target > 85 |
| **Load Tests** | k6 o similar | Fase 11 (staging) | 2x traffic |

### 11.2 Testing Best Practices

- **Test behavior, not implementation** - Prueba que el usuario vea lo esperado, no cómo funciona internamente
- **Test user flows** - Login → crear patrón → ver en dashboard
- **Mock Supabase** - No hacer llamadas reales en unit tests
- **Integration tests con DB** - Usar test database separada
- **Snapshot tests** - Solo para componentes UI que no cambian frecuentemente
- **Coverage metrics** - Apuntar a 80%+ para código crítico

### 11.3 Checklist de QA Crítica Pre-Release

**Antes de cualquier release a producción:**

- [ ] Todos los tests pasando (unit + integration + e2e)
- [ ] Coverage > 80% en módulos críticos (auth, patterns, planning)
- [ ] Login/Logout funciona
- [ ] Crear/editar/borrar patrones funciona
- [ ] Crear/editar/borrar objetivos funciona
- [ ] Calendario muestra movimientos correctos
- [ ] Cálculos de balance son exactos vs versión vieja
- [ ] Datos persisten entre sesiones (localStorage)
- [ ] Datos sincronizados en Supabase
- [ ] No hay errores en console
- [ ] Mobile responsive (375px, 768px, 1024px, 1920px)
- [ ] Performance Lighthouse > 85
- [ ] No hay XSS vulnerabilities (OWASP Top 10)
- [ ] RLS policies están activas y validadas
- [ ] Sentry está capturando errores correctamente
- [ ] Monitoreo alertas están configuradas
- [ ] Rollback procedure ha sido testeado
- [ ] User communication está lista

---

## 12. Deployment

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

## 13. Timeline y Hitos (REVISADO - 4-5 SEMANAS)

### 📅 Cronograma Estimado Revisado

```
SEMANA 1: Preparación y Fundación
├── Lunes (2-3h): Fase 0 - Pre-Setup, backups, decisiones
├── Martes-Miércoles (6-8h): Fase 1 - Setup Vite, CI/CD, testing
├── Jueves-Viernes (8-10h): Fase 2 - Componentes shared y hooks
└── Hito 1: ✅ CI/CD funcional, componentes base listos, tests running

SEMANA 2: Autenticación y Patrones
├── Lunes-Martes (5-7h): Fase 3 - Auth module con tests
├── Miércoles-Jueves (7-9h): Fase 4 - Patterns module con tests
├── Viernes (4h): Bug fixes y testing
└── Hito 2: ✅ Auth y Patterns funcionales con cobertura > 80%

SEMANA 3: Planificación y Calendario
├── Lunes-Miércoles (10-14h): Fase 5 - Planning module (exhaustivo)
├── Jueves-Viernes (9-11h): Fase 6 - Calendar module
└── Hito 3: ✅ Planning y Calendar funcionales, tests > 75%

SEMANA 4: Módulos Complementarios
├── Lunes (8-10h): Fase 7 - Financial Dashboard
├── Martes-Miércoles (7-9h): Fase 8 - Wishlist
├── Jueves (5-7h): Fase 9 - Loans & Savings
└── Hito 4: ✅ Todos los módulos presentes y funcionales

SEMANA 5: Testing Integral, Deploy
├── Lunes-Martes (10-12h): Fase 10 - Testing integral + performance
├── Miércoles (4-6h): Fase 11 - Data migration + user comm
├── Jueves-Viernes (5-7h): Fase 12 - Deployment + monitoring
└── Hito 5: ✅ EN VIVO, monitoreado, documentado, rollback testeado
```

**Tiempo Total:** 86-113 horas
**Duración:** 4-5 semanas (si es full-time developer)

### 🎯 Hitos Principales

| Hito | Cuándo | Entregables | Validación |
|------|--------|-------------|-----------|
| **Setup Base** | Fin Sem 1 | Proyecto Vite, React 18, TS, CI/CD, Sentry | `npm run dev` + Tests passing |
| **Auth + Componentes** | Fin Sem 1 | Login, Register, Shared components, tests | Login exitoso + 80% coverage |
| **Auth + Patrones** | Fin Sem 2 | CRUD patrones, auth funcional | Crear/editar/borrar patrones |
| **Planning + Calendar** | Fin Sem 3 | Planning dashboard, calendario, tests | Ver movimientos en calendario |
| **Todos los módulos** | Fin Sem 4 | Wishlist, Financial, Loans, Savings | Todas features presentes |
| **Testing + Optim** | Fin Sem 5 | Tests integral, performance, docs | > 80% coverage, Lighthouse > 85 |
| **Production Ready** | Fin Sem 5 | Deploy, Monitoring, Rollback tested | ✅ En vivo y estable |

---

## 14. Checklist de Ejecución (REVISADO)

### Fase 0: Pre-Setup (CRÍTICA)

- [ ] Crear rama `feat/react-migration`
- [ ] Backup completo de código y base de datos
- [ ] Documentar decisiones tecnológicas
- [ ] Crear ROLLBACK PLAN document
- [ ] Setup Sentry account
- [ ] Crear GitHub issue tracker

### Fase 1: Setup

- [ ] Crear nuevo repo / rama
- [ ] `npm create vite@latest -- --template react-ts`
- [ ] Instalar dependencias core
- [ ] Configurar TypeScript (tsconfig.json)
- [ ] Setup Tailwind CSS
- [ ] Setup Shadcn/ui
- [ ] Configurar ESLint y Prettier
- [ ] Crear estructura de directorios
- [ ] Configurar Zustand
- [ ] **Setup Vitest + RTL**
- [ ] **Configurar GitHub Actions**
- [ ] **Configurar Sentry**
- [ ] **Setup husky + lint-staged**
- [ ] Primer commit

### Fase 2: Componentes Base

- [ ] Crear Button, Input, Card, Modal components
- [ ] Crear Header y Navigation
- [ ] Crear AppLayout wrapper
- [ ] Crear custom hooks (useSupabase, useCurrency, etc.)
- [ ] **Tests para cada componente (80%+ coverage)**
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
- [ ] **Tests de auth (85%+ coverage)**
- [ ] Persistencia de sesión

### Fases 4-9: Módulos (repetir para cada uno)

- [ ] Crear store
- [ ] Crear service
- [ ] Crear custom hook
- [ ] Crear UI components
- [ ] Integrar TanStack Query
- [ ] **Tests (70-80% coverage)**
- [ ] Documentación inline

### Fase 10: Testing Integral

- [ ] Tests e2e críticos
- [ ] Tests de integración Supabase
- [ ] Tests de regresión
- [ ] Performance optimization
- [ ] Profiling y bundle analysis
- [ ] Accessibility testing

### Fase 11: Migration & User Comm

- [ ] Backups seguros
- [ ] **User communication plan**
- [ ] Data integrity validation
- [ ] Rollback test
- [ ] Updated CHANGELOG

### Fase 12: Deployment

- [ ] Build production
- [ ] Deploy a staging
- [ ] Smoke testing
- [ ] **Deploy a producción**
- [ ] **Monitoreo en vivo**
- [ ] **Documentación completa**
- [ ] **Training para team**

---

## 15. Documentación Requerida (REVISADA)

Crear después de cada fase:

### Fase 1
- [ ] **SETUP.md** - Instrucciones de instalación y desarrollo local
- [ ] **README.md actualizado** - Overview del proyecto

### Fase 3-9
- [ ] **ARCHITECTURE.md** - Explicación de Feature-Sliced Design
- [ ] **API.md** - Documentación de stores, hooks, services
- [ ] **CONTRIBUTING.md** - Guía de contribución (code style, PR process)

### Fase 10-12
- [ ] **DEVELOPMENT.md** - Guía para desarrolladores
- [ ] **TESTING.md** - Cómo escribir tests
- [ ] **DEPLOYMENT.md** - Cómo deployar
- [ ] **TROUBLESHOOTING.md** - Problemas comunes y soluciones
- [ ] **CHANGELOG.md** - Migration notes y cambios importantes
- [ ] **INCIDENT_RESPONSE.md** - Cómo manejar errores en producción

---

## 🚨 Riesgos y Mitigación (ACTUALIZADO)

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|-------------|--------|-----------|
| **Supabase API changes** | Baja | Alta | Monitorear changelogs, pin versions |
| **Performance degradation** | Media | Media | Profiling desde Fase 1, Lighthouse CI |
| **Type errors en runtime** | Baja | Media | Strict TypeScript, tests |
| **Regresiones en funcionalidad** | Media | Alta | Testing exhaustivo, regression tests |
| **Breaking changes en deps** | Baja | Media | Lockfile.lock, automated updates con Renovate |
| **Scope creep** | Media | Alta | Strict checklist, feature gates |
| **Data corruption durante migration** | Baja | Crítica | **Backups automáticos, validation tests** |
| **Users stuck in old version** | Baja | Media | **User communication, gradual rollout** |
| **Rollback failure** | Baja | Crítica | **Test rollback procedure pre-deploy** |
| **Performance regression** | Media | Media | **Bundle analysis, Lighthouse CI target** |

---

## 💡 Tips para Éxito (REVISADO)

1. **Migra módulo por módulo**, no todo a la vez ✅
2. **Mantén el código viejo** hasta que todo funcione ✅
3. **Escribe tests mientras migras** (no después) ✅
4. **No optimices prematuramente** - primero funciona, luego optimiza ✅
5. **Documenta mientras avanzas** - no dejes para el final ✅
6. **Usa React DevTools y TypeScript** - son tus mejores amigos ✅
7. **Commits pequeños y frecuentes** - facilita debugging ✅
8. **Haz code reviews** - aunque sea contigo mismo ✅
9. **Monitorea en producción** - Sentry es tu amigo ✅
10. **Comunica progreso** - mantén stakeholders informados ✅
11. **Test rollback procedure antes de deploy** - vital ✅
12. **Mide performance desde el inicio** - Lighthouse CI ✅
13. **User testing antes de release** - 5-10 usuarios reales ✅
14. **Database backups en cada fase** - mejor prevenir que lamentar ✅
15. **Plan comunicación con usuarios** - downtime, cambios UX ✅

---

## 📞 Próximos Pasos INMEDIATOS

### Ahora (5 enero 2026):
1. ✅ **Revisar y aprobar este documento actualizado**
2. ✅ **Confirmar stack tecnológico** (decisiones en sección 5)
3. ✅ **Asignar responsables** para cada fase
4. ✅ **Crear GitHub project board** para tracking
5. ✅ **Crear Sentry + Vercel accounts** si no existen
6. ✅ **Documentar ROLLBACK PLAN** específico para tu setup

### Día 2 (6 enero 2026):
1. **Crear rama git** `feat/react-migration`
2. **Hacer backups** de código y base de datos
3. **Comenzar Fase 0** - Pre-setup y decisiones

### Semana 1:
1. **Completar Fase 0** - Pre-setup
2. **Completar Fase 1** - Setup Vite + CI/CD
3. **Empezar Fase 2** - Componentes shared

---

**Documento Actualizado:** 5 de enero de 2026  
**Estimado Total Revisado:** 86-113 horas (aumentado 115% desde original)  
**Timeline Revisado:** 4-5 semanas (full-time developer)  
**Status:** 🟢 Listo para Ejecución - Esperando aprobación de stack  
**Cambios Principales:**
- ✅ Estimaciones aumentadas (testing integrado desde inicio)
- ✅ CI/CD y monitoring desde Fase 1
- ✅ Rollback plan documentado
- ✅ User communication plan incluido
- ✅ Pre-deployment checklist exhaustivo
- ✅ Decisiones tecnológicas clarificadas pre-inicio

