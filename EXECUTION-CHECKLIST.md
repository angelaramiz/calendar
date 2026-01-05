# 🚀 PLAN DE EJECUCIÓN - CHECKLIST MAESTRA

**Fecha de Inicio:** 6 de enero de 2026  
**Proyecto:** Migración a React - Sistema de Planificación Financiera  
**Responsable:** [Tu nombre]  
**Estado:** 🟢 Listo para Iniciar

---

## 🎯 ANTES DE INICIAR (Hoy)

### Pre-Requisitos Críticos
- [ ] He leído el `REACT-MIGRATION-PLAN.md` completo
- [ ] He leído el `MIGRATION-UPDATE-SUMMARY.md`
- [ ] Tengo backup completo de código actual
- [ ] Tengo backup completo de base de datos
- [ ] He confirmado las decisiones tecnológicas (Sección 5 del plan)
- [ ] Stack tecnológico aprobado por el equipo

### Configuración Inicial Necesaria
- [ ] GitHub repo configurado
- [ ] Rama `feat/react-migration` creada
- [ ] Sentry account creado
- [ ] Vercel/Netlify account confirmado
- [ ] `.env.example` documentado con todas las variables
- [ ] GitHub issue tracker creado para progreso
- [ ] GitHub Projects board configurado

### Documentación Preparada
- [ ] ROLLBACK PLAN creado y aprobado
- [ ] Performance baseline medido (actual Lighthouse, bundle size)
- [ ] User communication plan draft listo
- [ ] Team training agenda programada

---

## 📅 SEMANA 1: PREPARACIÓN Y FUNDACIÓN

### Lunes 6/1 - FASE 0: Pre-Setup (2-3 horas)

**Objetivo:** Preparar environment y documentar estado actual

- [ ] **Crear rama git:** `git checkout -b feat/react-migration`
- [ ] **Backup de código:** Copiar carpetas `js/`, `routes/`, `styles/`
- [ ] **Documentar DB:** Snapshot de Supabase schema
- [ ] **Documentar env vars:** Listar todas en `.env.example`
- [ ] **Crear Sentry project:** Setup error tracking
- [ ] **Documentar ROLLBACK PLAN:** Escribir en documento separado
- [ ] **Crear GitHub issue tracker:** Issue #1 = progreso general
- [ ] **Database backup:** Snapshot automático de Supabase
- [ ] **Performance baseline:** 
  - [ ] Medir current Lighthouse score
  - [ ] Medir current bundle size
  - [ ] Documentar baseline en ROLLBACK_PLAN.md

**Entregables:**
- ✅ Backups seguros
- ✅ ROLLBACK_PLAN.md escrito y aprobado
- ✅ GitHub tracking setup
- ✅ Performance baseline documentado

---

### Martes 7/1 - Miércoles 8/1 - FASE 1: Setup (6-8 horas)

**Objetivo:** Crear estructura base de React con CI/CD y testing

#### Setup del Proyecto
- [ ] **Crear proyecto Vite:** `npm create vite@latest calendar-app-react -- --template react-ts`
- [ ] **Instalar dependencias core:**
  ```bash
  npm install react react-dom zustand @supabase/supabase-js
  npm install @tanstack/react-query date-fns zod react-hook-form axios
  ```

#### Configuración de Tooling
- [ ] **TypeScript:**
  - [ ] Actualizar `tsconfig.json`
  - [ ] Enable strict mode
  
- [ ] **Tailwind + Shadcn/ui:**
  - [ ] `npm run setup:tailwind` (Shadcn)
  - [ ] Instalar componentes base
  
- [ ] **ESLint + Prettier:**
  - [ ] Crear `.eslintrc.json`
  - [ ] Crear `.prettierrc.json`
  - [ ] Configurar pre-save formatting
  
- [ ] **Testing:**
  - [ ] `npm install -D vitest @testing-library/react @testing-library/jest-dom`
  - [ ] Crear `vitest.config.ts`
  - [ ] Crear primer test de ejemplo
  
- [ ] **CI/CD:**
  - [ ] Setup GitHub Actions (`.github/workflows/test.yml`)
  - [ ] Configurar Lighthouse CI
  - [ ] Configurar Sentry SDK
  
- [ ] **Pre-commit Hooks:**
  - [ ] `npm install -D husky lint-staged`
  - [ ] Setup husky
  - [ ] Configure lint-staged

#### Estructura de Directorios
- [ ] **Crear estructura:**
  ```
  src/
  ├── features/
  ├── shared/
  ├── App.tsx
  └── main.tsx
  ```

#### Supabase Integration
- [ ] **Crear `supabaseClient.ts`** en `src/shared/services/`
- [ ] **Test Supabase connection**
- [ ] **Documentar RLS policies**

#### First Commit
- [ ] **Commit initial setup:** `git commit -m "feat: initial vite+react+ts setup"`
- [ ] **Push to branch:** `git push origin feat/react-migration`
- [ ] **Verify CI/CD runs:** Check GitHub Actions

**Entregables:**
- ✅ Proyecto Vite funcional
- ✅ `npm run dev` funciona
- ✅ `npm run test` funciona
- ✅ `npm run build` funciona
- ✅ ESLint y Prettier pasando
- ✅ GitHub Actions passing
- ✅ Sentry connected
- ✅ First test passing

**Time Tracking:** _____ horas (Estimado 6-8h)

---

### Jueves 9/1 - Viernes 10/1 - FASE 2: Componentes Shared (8-10 horas)

**Objetivo:** Crear base de componentes reutilizables CON TESTS

#### Componentes Base
- [ ] **Button component + tests**
- [ ] **Input component + tests**
- [ ] **Card component + tests**
- [ ] **Modal component + tests**
- [ ] **Dialog component + tests**
- [ ] **Form components** (Label, etc)
- [ ] **Badge component**

#### Layout Components
- [ ] **Header component + tests**
- [ ] **Navigation component + tests**
- [ ] **Sidebar component + tests**
- [ ] **AppLayout wrapper + tests**
- [ ] **Footer component**

#### Custom Hooks (con tests)
- [ ] **useSupabase()** - wrapper de Supabase client
- [ ] **useCurrency()** - formateo de moneda
- [ ] **useDateFormat()** - formateo de fechas
- [ ] **useNotification()** - toasts (reemplazar SweetAlert2)
- [ ] **useDebounce()** - para búsquedas
- [ ] **useLocalStorage()** - persistencia

#### Utilidades y Tipos
- [ ] **formatting.ts** - funciones de formato
- [ ] **validation.ts** - validaciones comunes
- [ ] **calculations.ts** - cálculos financieros
- [ ] **Tipos TypeScript globales**
- [ ] **Constants:** currencies, frequencies, routes, messages

#### Testing
- [ ] **Tests para cada componente:** > 80% coverage
- [ ] **Tests para cada hook**
- [ ] **Verify coverage:** `npm run test:coverage`

#### Commit Progress
- [ ] **Commit:** `git commit -m "feat: shared components and hooks"`
- [ ] **Push:** `git push origin feat/react-migration`

**Entregables:**
- ✅ Librería de componentes funcional
- ✅ Hooks reutilizables
- ✅ Tests > 80% coverage
- ✅ Sistema de notificaciones setup
- ✅ Tipos TypeScript definidos
- ✅ Constants y utilidades

**Hito 1:** ✅ **SETUP COMPLETADO** - CI/CD funcional, componentes base listos

**Time Tracking:** _____ horas (Estimado 8-10h)

---

## 📅 SEMANA 2: AUTENTICACIÓN Y PATRONES

### Lunes 13/1 - Martes 14/1 - FASE 3: Autenticación (5-7 horas)

**Objetivo:** Migrar login, registro y recuperación CON TESTS COMPLETOS

#### Auth Store (Zustand)
- [ ] **Crear authStore.ts** con state y actions
- [ ] **Crear authService.ts** - llamadas a Supabase Auth
- [ ] **Tests para authStore:** > 85% coverage
- [ ] **Tests para authService**

#### Forms
- [ ] **LoginForm.tsx** + validación Zod + tests
- [ ] **RegisterForm.tsx** + validación Zod + tests
- [ ] **RecoveryForm.tsx** + validación Zod + tests
- [ ] **Form validation tests**

#### Auth Flow
- [ ] **useAuth() hook** + tests
- [ ] **ProtectedRoute component** + tests
- [ ] **Persistent login** (localStorage)
- [ ] **Auth hydration** on app load
- [ ] **Tests de integración Auth** con Supabase

#### Integration with Supabase
- [ ] **Test auth flow end-to-end**
- [ ] **Verify RLS policies activas**
- [ ] **Test logout clears data**

#### Tests
- [ ] **Unit tests:** > 85% coverage
- [ ] **Integration tests:** auth con Supabase
- [ ] **E2E test:** login → dashboard → logout

#### Documentation
- [ ] **Document auth flow** en README
- [ ] **Inline code comments**

#### Commit Progress
- [ ] **Commit:** `git commit -m "feat: auth module with tests"`
- [ ] **Push y verify CI/CD**

**Entregables:**
- ✅ Auth funcional (login/register/recovery)
- ✅ Persistent login working
- ✅ ProtectedRoute implementado
- ✅ Tests > 85% coverage
- ✅ Auth flow documentado

**Time Tracking:** _____ horas (Estimado 5-7h)

---

### Miércoles 15/1 - Jueves 16/1 - FASE 4: Patterns (7-9 horas)

**Objetivo:** Migrar patrones (income/expense) CON TESTS

#### Store y Service
- [ ] **patternsStore.ts** (Zustand)
- [ ] **patternsService.ts** (CRUD con Supabase)
- [ ] **pattern-types.ts** (TypeScript types)
- [ ] **Tests para store y service**

#### Custom Hook
- [ ] **usePatterns() hook**
- [ ] **Integrar TanStack Query** para caching
- [ ] **Tests del hook**

#### UI Components
- [ ] **PatternsManager.tsx** (container)
- [ ] **PatternList.tsx** (lista)
- [ ] **PatternCard.tsx** (card individual)
- [ ] **PatternForm.tsx** (crear/editar)
- [ ] **Search y filters**

#### Tests
- [ ] **Tests para patternsService** (mock Supabase)
- [ ] **Tests para usePatterns hook**
- [ ] **Tests para componentes UI**
- [ ] **E2E test:** crear patrón → editar → eliminar
- [ ] **Coverage > 80%**

#### Validation
- [ ] **Test con datos reales de Supabase**
- [ ] **Verify Zod validation** en forms
- [ ] **Error handling**

#### Commit Progress
- [ ] **Commit:** `git commit -m "feat: patterns module with CRUD"`
- [ ] **Push y verify CI/CD**

**Hito 2:** ✅ **AUTH Y PATRONES COMPLETADOS**

**Entregables:**
- ✅ Patterns CRUD funcional
- ✅ Filtros y búsqueda
- ✅ Tests > 80% coverage
- ✅ Formularios con validación Zod

**Time Tracking:** _____ horas (Estimado 7-9h)

---

## 📅 SEMANA 3: PLANIFICACIÓN Y CALENDARIO

### Lunes 20/1 - Miércoles 22/1 - FASE 5: Planificación (10-14 horas)

**Objetivo:** Migrar planning dashboard CON TESTS EXHAUSTIVOS

**NOTA:** Esta es la fase más crítica - validar exactitud numérica

#### Store y Service
- [ ] **planningStore.ts** (goals, envelopes, expenses)
- [ ] **planningService.ts** (CRUD)
- [ ] **planning-types.ts**
- [ ] **Tests para exactitud numérica**

#### Custom Hooks
- [ ] **usePlanning() hook**
- [ ] **useGoals() hook**
- [ ] **useEnvelopes() hook**
- [ ] **Tests para cada hook:** > 80%

#### UI Components
- [ ] **PlanningDashboard.tsx** (vista principal)
- [ ] **GoalList.tsx + GoalCard.tsx + GoalForm.tsx**
- [ ] **EnvelopeList.tsx + EnvelopeCard.tsx + EnvelopeForm.tsx**
- [ ] **ExpenseSummary.tsx**
- [ ] **AllocationChart.tsx** (usando recharts)
- [ ] **Drag-and-drop para distribución**

#### Gráficos
- [ ] **Install recharts:** `npm install recharts`
- [ ] **Implementar AllocationChart**
- [ ] **Tests para gráficos** (snapshot testing)

#### Tests - EXHAUSTIVOS
- [ ] **Tests para cálculos financieros**
  - [ ] ¿Suma total correcta?
  - [ ] ¿Distribución por categoría correcta?
  - [ ] ¿Porcentajes correctos?
- [ ] **Tests para goals CRUD**
- [ ] **Tests para envelopes CRUD**
- [ ] **Tests de componentes UI**
- [ ] **E2E test:** Flujo completo de planning
- [ ] **Coverage > 80%**
- [ ] **Comparar cálculos con versión vieja**

#### Critical Validation
- [ ] **Create goal, update goal, delete goal**
- [ ] **Create envelope, assign money, verify balance**
- [ ] **Verify calculations match old version**
- [ ] **Test with various currencies**

#### Commit Progress
- [ ] **Commit:** `git commit -m "feat: planning module (goals+envelopes)"`
- [ ] **Push y verify CI/CD**

**Entregables:**
- ✅ Planning dashboard funcional
- ✅ Goals CRUD working
- ✅ Envelopes CRUD working
- ✅ Gráficos funcionan
- ✅ Tests > 80% coverage
- ✅ Exactitud numérica validada

**Time Tracking:** _____ horas (Estimado 10-14h)

---

### Jueves 23/1 - Viernes 24/1 - FASE 6: Calendario (9-11 horas)

**Objetivo:** Migrar calendario y eventos CON TESTS

#### Store y Service
- [ ] **calendarStore.ts**
- [ ] **calendarService.ts** (obtener movimientos por fecha)
- [ ] **event-types.ts**
- [ ] **Tests para date handling**

#### Calendar Library
- [ ] **Install React Big Calendar:** `npm install react-big-calendar`
- [ ] **Setup calendar**

#### Custom Hook
- [ ] **useCalendar() hook**
- [ ] **Tests para hook**

#### UI Components
- [ ] **CalendarView.tsx** (vista principal)
- [ ] **EventModal.tsx** (detalles)
- [ ] **EventList.tsx** (lista de eventos)
- [ ] **DayDetail.tsx** (detalle por día)
- [ ] **Filtros:** por tipo, categoría

#### Tests
- [ ] **Tests para calendarService**
- [ ] **Tests para componentes**
- [ ] **Tests de filtros** (edge cases con fechas)
- [ ] **Tests de navegación** en calendario
- [ ] **Coverage > 75%**

#### Integration
- [ ] **Test con datos reales de Supabase**
- [ ] **Verify movimientos se muestran correctamente**
- [ ] **Test timezone handling**

#### Commit Progress
- [ ] **Commit:** `git commit -m "feat: calendar module with events"`
- [ ] **Push y verify CI/CD**

**Hito 3:** ✅ **PLANNING Y CALENDAR COMPLETADOS**

**Entregables:**
- ✅ Calendario navegable
- ✅ Eventos mostrados correctamente
- ✅ Modal con detalles
- ✅ Filtros funcionales
- ✅ Tests > 75% coverage

**Time Tracking:** _____ horas (Estimado 9-11h)

---

## 📅 SEMANA 4: MÓDULOS COMPLEMENTARIOS

### Lunes 27/1 - Martes 28/1 - FASE 7: Financial Dashboard (8-10 horas)

**Objetivo:** Migrar dashboard financiero

#### Store y Service
- [ ] **financialStore.ts**
- [ ] **financialService.ts**
- [ ] **financial-types.ts**

#### Custom Hook
- [ ] **useFinancial() hook**

#### UI Components
- [ ] **FinancialDashboard.tsx**
- [ ] **BalancePanel.tsx**
- [ ] **StatsPanel.tsx**
- [ ] **TrendChart.tsx** (recharts)
- [ ] **EnginePanel.tsx** (recomendaciones IA)

#### Tests
- [ ] **Tests para cálculos financieros**
- [ ] **Tests para estadísticas**
- [ ] **Tests de componentes**
- [ ] **Coverage > 75%**

#### Commit Progress
- [ ] **Commit:** `git commit -m "feat: financial dashboard"`
- [ ] **Push**

**Entregables:**
- ✅ Dashboard financiero completo
- ✅ Gráficos de tendencias
- ✅ Tests > 75% coverage

**Time Tracking:** _____ horas (Estimado 8-10h)

---

### Miércoles 29/1 - Jueves 30/1 - FASE 8: Wishlist (7-9 horas)

**Objetivo:** Migrar lista de deseos

#### Store, Service, Types
- [ ] **wishlistStore.ts**
- [ ] **wishlistService.ts**
- [ ] **wishlist-types.ts**

#### Custom Hook
- [ ] **useWishlist() hook**

#### UI Components
- [ ] **WishlistDashboard.tsx**
- [ ] **ProductCard.tsx**
- [ ] **ProductForm.tsx**
- [ ] **PriceHistory.tsx**

#### Tests
- [ ] **Tests para CRUD**
- [ ] **Tests para price tracking**
- [ ] **Coverage > 70%**

#### Commit Progress
- [ ] **Commit:** `git commit -m "feat: wishlist module"`

**Entregables:**
- ✅ Wishlist CRUD funcional
- ✅ Price tracking
- ✅ Tests > 70% coverage

**Time Tracking:** _____ horas (Estimado 7-9h)

---

### Viernes 31/1 - FASE 9: Loans & Savings (5-7 horas)

**Objetivo:** Módulos complementarios

#### Loans
- [ ] **loansStore.ts + loansService.ts + loansManager.tsx**
- [ ] **loans-types.ts**
- [ ] **Tests**

#### Savings
- [ ] **savingsStore.ts + savingsService.ts + savingsManager.tsx**
- [ ] **savings-types.ts**
- [ ] **Tests**

#### Commit Progress
- [ ] **Commit:** `git commit -m "feat: loans and savings modules"`

**Hito 4:** ✅ **TODOS LOS MÓDULOS PRESENTES**

**Time Tracking:** _____ horas (Estimado 5-7h)

---

## 📅 SEMANA 5: TESTING INTEGRAL Y DEPLOY

### Lunes 3/2 - Martes 4/2 - FASE 10: Testing Integral (10-12 horas)

**Objetivo:** Testing exhaustivo, performance, a11y

#### E2E Tests (Playwright/Cypress)
- [ ] **Login → crear patrón → crear objetivo → ver calendar**
- [ ] **Crear envelope → asignar dinero → ver dashboard**
- [ ] **Wishlist → agregar → ver price history**
- [ ] **Financial → ver balance → tendencias → recomendaciones**

#### Integration Tests
- [ ] **Auth flow con Supabase**
- [ ] **CRUD para cada tabla**
- [ ] **RLS policies validation**
- [ ] **Concurrency handling**

#### Regression Tests
- [ ] **Comparar output con versión vieja**
- [ ] **Validar exactitud numérica**

#### Performance
- [ ] **Run Lighthouse:** Target > 85
- [ ] **Bundle analysis**
- [ ] **React DevTools profiling**
- [ ] **Optimize componentes** (React.memo, useMemo)
- [ ] **Code splitting y lazy loading**

#### Accessibility
- [ ] **WCAG 2.1 AA compliance**
- [ ] **Keyboard navigation**
- [ ] **Screen reader testing**

#### Coverage Report
- [ ] **Overall coverage > 80%**
- [ ] **Critical path > 85%**

#### Bug Fixes
- [ ] **Fix issues encontrados**
- [ ] **Verify all tests passing**

#### Commit Progress
- [ ] **Commit:** `git commit -m "test: comprehensive testing suite"`

**Entregables:**
- ✅ Coverage > 80%
- ✅ E2E tests passing
- ✅ Lighthouse > 85
- ✅ 0 critical accessibility issues

**Time Tracking:** _____ horas (Estimado 10-12h)

---

### Miércoles 5/2 - Jueves 6/2 - FASE 11: Migration & User Comm (4-6 horas)

**Objetivo:** Transición limpia

#### Data Migration
- [ ] **Verify todos los datos se ven en React**
- [ ] **RLS policies still active**
- [ ] **Data integrity validation**

#### User Communication
- [ ] **Crear mensaje de downtime**
- [ ] **Programar comunicación**
- [ ] **User testing:** Invitar 5-10 usuarios reales
- [ ] **Gather feedback**

#### Documentation
- [ ] **Actualizar README**
- [ ] **Crear CHANGELOG.md**
- [ ] **Document breaking changes**

#### Rollback Test
- [ ] **Practicar rollback procedure**
- [ ] **Verify backup restoration**
- [ ] **Time rollback procedure**

#### Commit Progress
- [ ] **Commit:** `git commit -m "docs: migration communication and changelog"`

**Entregables:**
- ✅ User communication plan ejecutado
- ✅ Data integrity validated
- ✅ Rollback tested y documentado
- ✅ CHANGELOG.md creado

**Time Tracking:** _____ horas (Estimado 4-6h)

---

### Viernes 7/2 - FASE 12: Deployment (5-7 horas)

**Objetivo:** Deploy a producción CON MONITOREO

#### Pre-Deployment
- [ ] **Production build:** `npm run build`
- [ ] **Verify bundle size < 200KB (gzip)**
- [ ] **Deploy to staging**
- [ ] **Smoke testing en staging**

#### Deployment
- [ ] **Deploy a producción** (Vercel/Netlify o servidor)
- [ ] **Verify app loads**
- [ ] **Check Sentry integration**
- [ ] **Verify monitoring alerts activos**

#### Post-Deployment Monitoring
- [ ] **Monitor Sentry errors**
- [ ] **Monitor performance**
- [ ] **Monitor database queries**
- [ ] **Check user feedback**

#### Documentation & Training
- [ ] **SETUP.md** - Development setup
- [ ] **DEPLOYMENT.md** - Release process
- [ ] **ARCHITECTURE.md** - Tech decisions
- [ ] **TROUBLESHOOTING.md** - Common issues
- [ ] **CONTRIBUTING.md** - Team guidelines
- [ ] **API.md** - Stores, hooks, services
- [ ] **INCIDENT_RESPONSE.md** - Error handling

#### Team Training
- [ ] **Support team training**
- [ ] **Developer onboarding docs**
- [ ] **Runbook de incidents**

#### Commit & Close
- [ ] **Final commit:** `git commit -m "docs: deployment and documentation"`
- [ ] **Push to main** (o merge PR)
- [ ] **Create release tag**

**Hito 5:** ✅ **EN VIVO Y MONITOREADO**

**Entregables:**
- ✅ Production deployment exitoso
- ✅ Sentry + monitoring activo
- ✅ Comprehensive documentation
- ✅ Team trained
- ✅ Incident response ready

**Time Tracking:** _____ horas (Estimado 5-7h)

---

## 📊 TRACKING DE PROGRESO

### Daily Standup Template
```
FECHA: ___________

✅ COMPLETADO HOY:
- [item 1]
- [item 2]

🔄 EN PROGRESO:
- [item 1]

🚧 BLOQUEADORES:
- [item 1]

📝 NOTAS:
```

### Métricas a Trackear
- [ ] **Horas invertidas vs estimadas** (por fase)
- [ ] **Tests escritos vs target** (cobertura %)
- [ ] **Bugs encontrados en testing**
- [ ] **Performance metrics** (bundle size, Lighthouse)
- [ ] **Build time**
- [ ] **Number of commits**

---

## 🎯 DEFINICIÓN DE "COMPLETADO"

### Cada Fase es COMPLETADA cuando:
- ✅ Todos los items del checklist marcados
- ✅ Tests pasando (coverage > 70%)
- ✅ No hay errores en console
- ✅ Code reviewed
- ✅ Committed a git
- ✅ CI/CD pipeline passing
- ✅ Documentación inline completa

### Hito es COMPLETADO cuando:
- ✅ Todas las fases del hito completadas
- ✅ Integración entre módulos verified
- ✅ User stories validadas
- ✅ Performance baseline met

---

## 📞 SOPORTE Y ESCALACIÓN

### Si encuentras problemas:

**Problema técnico:**
- 1. Consultar plan original (Sección 8-15)
- 2. Consultar TROUBLESHOOTING.md
- 3. Búsqueda en GitHub issues
- 4. Ask in team chat

**Bloqueador crítico:**
- Contact: [nombre CTO/lead]
- Slack: [canal]
- Decision needed by: [time]

**Rendimiento inferior:**
- Compare vs estimaciones
- Identify bottlenecks
- Ajust timeline si es necesario
- Communicate con stakeholders

---

## ✅ FINALIZACIÓN

Una vez completadas todas las fases:

- [ ] Todos los hitos completados
- [ ] Documentación 100% completada
- [ ] Tests > 80% coverage
- [ ] Production stable
- [ ] Rollback capability verified
- [ ] Team fully trained
- [ ] Post-mortem ejecutado

**PROYECTO COMPLETADO:** ___________

---

**Versión:** 2.0  
**Actualizado:** 5 de enero de 2026  
**Estado:** 🟢 Listo para Ejecución  
**Tiempo Total Estimado:** 86-113 horas  
**Duración:** 4-5 semanas (full-time)

