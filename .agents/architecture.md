# Arquitectura del Proyecto

## CalendarFinace

Aplicacion de gestion financiera con calendario mensual.

- **Fase**: desarrollo activo
- **Inicio**: 2025
- **Deploy**: Render (web) + APK manual (Android)

---

## Stack por Capa

| Capa | calendarWeb | calendarAPP |
|------|-------------|-------------|
| **Lenguaje** | JavaScript ES6 | Kotlin 2.4.0 |
| **UI** | HTML/CSS + SweetAlert2 | Jetpack Compose + Material3 |
| **Auth** | Supabase Auth JS | Supabase Auth Kotlin v3.7 |
| **DB** | Supabase PostgreSQL | Supabase PostgreSQL |
| **State** | DOM + localStorage | ViewModel + StateFlow |
| **DI** | Manual (imports) | Koin 3.5.3 |
| **HTTP** | fetch nativo | Ktor 3.0.3 |
| **Biometria** | WebAuthn API | BiometricPrompt |
| **Deploy** | Render static site | APK (manual) |

---

## Estructura Android (calendarAPP)

```
com.calendarfinance.app/
├── CalendarFinApp.kt          # Application (Koin + global handler)
├── MainActivity.kt            # Entry point (error screen fallback)
├── di/
│   └── AppModule.kt           # Koin: repos + viewmodels
├── data/
│   ├── model/
│   │   ├── Models.kt          # User, Movement, Pattern, Balance, Plan, Loan
│   │   ├── RequestModels.kt   # CreateMovementRequest, CreatePatternRequest
│   │   └── AppVersionInfo.kt  # OTA version info
│   ├── remote/
│   │   ├── SupabaseClientProvider.kt  # Cliente Supabase v3.7
│   │   ├── BiometricHelper.kt         # BiometricPrompt wrapper
│   │   └── SessionManager.kt          # DataStore para sesion
│   └── repository/
│       ├── AuthRepository.kt      # Login/registro/recuperacion
│       ├── MovementRepository.kt  # CRUD movimientos + vistas
│       ├── PatternRepository.kt   # CRUD patrones
│       └── OtaUpdateRepository.kt # OTA check + download + install
└── ui/
    ├── auth/
    │   ├── AuthViewModel.kt
    │   ├── BiometricAuthViewModel.kt
    │   ├── LoginScreen.kt         # Login + biometria + OTA
    │   └── RegisterScreen.kt
    ├── calendar/
    │   ├── CalendarViewModel.kt
    │   └── CalendarScreen.kt      # Grid + resumen + patrones
    ├── movement/
    │   ├── MovementViewModel.kt
    │   └── MovementFormScreen.kt
    ├── pattern/
    │   ├── PatternViewModel.kt
    │   └── PatternFormScreen.kt
    ├── balance/
    │   ├── BalanceViewModel.kt
    │   └── BalanceScreen.kt
    ├── ota/
    │   ├── OtaUpdateViewModel.kt
    │   └── OtaUpdateDialog.kt
    ├── navigation/
    │   └── NavGraph.kt            # Rutas + SafeScreen
    └── theme/
        ├── Color.kt
        └── Theme.kt
```

---

## Flujo de Datos Android

```
Usuario → AuthRepository → Supabase Auth (signInWith)
  ↓
CalendarScreen → MovementRepository → Supabase PostgREST
  ↓
CalendarViewModel (StateFlow) → CalendarScreen (Compose)
  ↓
BalanceView → confirmed_balance_summary (vista SQL)
```

---

## Flujo OTA Android

```
App inicia → OtaUpdateRepository.checkForUpdate()
  ↓ (consultar app_versions en Supabase)
  ↓ versionCode > local?
Dialogo → "Descargar e instalar"
  ↓
OkHttp download → FileProvider URI → Intent install
```

---

## Flujo Biometrico Android

```
PRIMER LOGIN
  email + password → Supabase Auth → OK
  → SessionManager.saveSession(userId, email)
  → BiometricAuthViewModel.saveSessionAndEnableBiometric()

SIGUIENTES LOGINS
  Boton "Huella" → BiometricPrompt → SUCCESS
  → SessionManager.getSession() → userId, email
  → AuthViewModel.loginDirect(userId, email)
```

---

## Tablas Supabase

### Core
- `users` — Perfiles (FK auth.users)
- `income_patterns` — Patrones de ingreso
- `expense_patterns` — Patrones de gasto
- `movements` — Transacciones confirmadas
- `plans` — Metas de ahorro
- `loans` — Prestamos
- `envelopes` — Presupuestos
- `savings_patterns` — Ahorros programados
- `savings_transactions` — Depositos/retiros

### Financial Engine
- `expense_income_links` — Vinculacion gasto-ingreso
- `financial_snapshots` — Snapshots periodicos
- `financial_recommendations` — Recomendaciones

### OTA
- `app_versions` — Version del APK (RLS abierto)

### Vistas
- `confirmed_balance_summary`
- `monthly_confirmed_balance`
- `movements_with_patterns`
- `plans_with_progress`

---

## Configuracion de Build

### calendarAPP/build.gradle.kts
- AGP 8.7.3
- Kotlin 2.4.0
- compileSdk 35, targetSdk 35, minSdk 26
- Compose BOM 2024.06
- Supabase BOM 3.7.0
- isMinifyEnabled = false (temporal)

### calendarAPP/gradle.properties
- JAVA_HOME = Android Studio JBR
- ANDROID_HOME = SDK path
