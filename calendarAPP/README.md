# CalendarFinance App

Aplicacion movil nativa Android (Kotlin + Jetpack Compose) para gestion financiera con calendario.

## Requisitos
- Android Studio Hedgehog (2023.1+) o superior
- JDK 17
- Gradle 8.5

## Stack
- **Kotlin** 1.9.22
- **Jetpack Compose** + Material 3
- **Navigation Compose**
- **Supabase** (PostgreSQL + Auth)
- **Koin** (DI)
- **Ktor** (HTTP client)

## Estructura
```
app/src/main/java/com/calendarfinance/app/
├── CalendarFinApp.kt          # Application class (Koin init)
├── MainActivity.kt            # Entry point
├── data/
│   ├── model/                 # Entidades (User, Movement, Pattern, Balance)
│   ├── remote/                # Supabase client
│   └── repository/            # Auth, Movement, Pattern repos
├── di/                        # Koin DI module
├── ui/
│   ├── theme/                 # Material 3 theme
│   ├── navigation/            # NavGraph
│   ├── auth/                  # Login, Register screens
│   ├── calendar/              # Calendar grid + summary
│   ├── movement/              # Movement form
│   ├── pattern/               # Pattern form
│   └── balance/               # Balance report
```

## Funcionalidades
- Auth (login/registro/recuperacion via Supabase)
- Calendario mensual con indicadores de ingreso/gasto
- CRUD de movimientos (ingresos y gastos)
- Patrones recurrentes (semanal, quincenal, mensual, anual)
- Balance financiero (general y mensual)

## Setup
1. Abrir proyecto en Android Studio
2. Sincronizar Gradle
3. Ejecutar en emulador/dispositivo
