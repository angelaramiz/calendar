# AGENTS.md — CalendarFinace

Multi-product repo. Production app is `calendarWeb`; Android is being rewritten as FinTrack. `calendarRN/` is a stale migration workspace — ignore it unless asked.

## Layout

- `calendarWeb/` — production static web app (deployed to Render). Entry `calendarWeb/index.html`, logic `calendarWeb/js/`, deploy artifact `calendarWeb/calendarfinance.apk` + `calendarWeb/version.json`.
- `calendarAPP/` — Android rewrite, package `com.fintrack.app` (Kotlin + Compose + Supabase + Koin). Entry `app/src/main/java/com/fintrack/app/MainActivity.kt`, DI `.../di/AppModule.kt`.
- `calendar_backend/` — Flask scraper (Fly.io). Root `index.html`/`js/`/`routes/`/`styles/` are legacy — don't edit.
- No SQL migrations in repo; no Android tests. `.codegraph/` index exists.

## Android (`calendarAPP/`)

Versions are pinned — do not bump without asking: Kotlin 2.4.0, AGP 8.7.3, `compileSdk`/`targetSdk` 35, `minSdk` 26, Java 17, Compose BOM 2024.06, Supabase BOM 3.7.0, Ktor 3.0.3, Koin 3.5.3.

- No KSP/Hilt/Room in this project. KSP has no artifact for Kotlin 2.4.0 — don't re-add it.
- Supabase v3: `auth-kt` exposes `Auth` (not `GoTrue`); inserts must use `buildJsonObject { put(...) }`, never `Map<String, Any>`.
- Wrapper exists (`gradlew.bat` + `gradle/wrapper/`). Run Gradle from `calendarAPP/`:
  `.\gradlew.bat compileReleaseKotlin` (fast check) → `.\gradlew.bat assembleRelease`.
  If Gradle can't find Java, set `JAVA_HOME` to Android Studio's `jbr` dir.
- `lint { checkReleaseBuilds = false }` is intentional — don't "fix" it.
- Release signing reads `calendarAPP/local.properties` (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`; default path `../fintrack.jks`). The only keystore in repo is the stale-named `calendarAPP/calendarfinance.jks`. Never commit `local.properties` or keystores.
- Known issues (updated Sep 2026):
  - `ui/navigation/NavGraph.kt` shares one `DashboardViewModel` per **activity** (`koinViewModel(viewModelStoreOwner = activity)`). Don't scope it to a back-stack entry — tabs/Tile pop entries and `getBackStackEntry()` crashes.
  - Bottom tabs (`FinTrackBottomBar`: Inicio/Calendario/Flujos/Presupuesto) navigate with `popUpTo + launchSingleTop` (helper `navigateToTab` in `NavGraph.kt`).
  - Calendar tab (`ui/calendar/`): patterns expand client-side via `domain/PatternExpander` (no `projections` table — same as web). Frequencies `weekly`/`biweekly`(=14 días)/`monthly`/`yearly`; monthly day-31 drifts like web (31 ene → 28 feb → 28 mar). Backend tables `income_patterns`, `expense_patterns`, `movements` (web-canonical names); confirm creates a `movements` row with the pattern FK (`income_pattern_id`/`expense_pattern_id`) and `confirmed=true`. Month header summary via pure `domain/MonthSummary`.
  - Flows tab (`ui/flows/` + `domain/FlowEngine`): n8n-inspired vertical node chain (income → formula splits → conditions → envelopes). Config persists in DataStore (`data/FlowStore.kt`) as JSON — no new Supabase tables.
  - Budget tab (`ui/budget/` + `domain/BudgetPlanner`): short (month caps per category, 80% alert), medium (3-month projection from patterns + variable average), long (savings-goal feasibility) — all client-side, no new tables.
  - JVM unit tests: `app/src/test/` (JUnit4); run `:app:testReleaseUnitTest --tests "<Clase>"`. `PatternExpanderTest` 9/9, `NotificationParserTest` 10/10, `MonthSummaryTest` 7/7, `FlowEngineTest` 13/13, `BudgetPlannerTest` 11/11 must stay green.
  - No auth screens exist yet: `DashboardViewModel` publishes `needsLogin=true` + message instead of returning silently; UI shows it with a retry button.
  - `data/service/TransactionNotificationListener.kt` delegates to `domain/NotificationParser` (pure, tested) and persists via `TransactionRepository` using the Supabase session (skips silently without session). App allowlist in `data/AppFilterStore.kt` (DataStore, editable in PermissionsScreen; no `QUERY_ALL_PACKAGES` — recent packages auto-record for one-tap add). Keywords use normalized stems (`debitamos`~`debito`); promo exclusions run BEFORE amount parsing; dedup = same app+monto+minuto within 2 min. Each save posts a notification (`DetectionNotifier`, channel `fintrack_detecciones`) with tap-to-open + delete action. Bank package IDs are plausible but **unverified against real bank apps** — confirm on a real device.
  - Kotlin sources must stay **UTF-8 sin BOM**; PowerShell `Set-Content -Encoding UTF8` writes BOM and mangles accents on rewrite. Prefer the `edit` tool; if using PowerShell, write bytes via `[System.IO.File]`.
- DatePicker ↔ epoch conversions must use `ZoneId.of("UTC")` both ways (else off-by-one day).

## Web (`calendarWeb/`)

- `build.sh` (run by Render) copies `calendarWeb/*` to `dist/` and generates `dist/js/config.js` from env vars `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SCRAPER_API_URL`. Never commit real keys; local dev: `cd calendarWeb && python -m http.server`.
- `render.yaml` rewrites all routes to `/index.html`.
- Backend table for the Android rewrite is `fintrack_transactions` (RLS on `user_id`); UI never sets `TransactionEntity.user_id` — repository must fill it.
- Canonical Supabase project is `https://ugtlxnrwfipoctckuvfd.supabase.co` (same URL + key as `release.ps1`; verified working Sep 2026). OTA reads table `app_versions`, row `clave = app_version_calendarfinance`, whose `valor` is **double-encoded JSON** (parse twice).
- OTA flow: `OtaUpdateRepository.checkForUpdate()` compares remote `versionCode` vs `BuildConfig.VERSION_CODE`; `DashboardViewModel` exposes `updateAvailable`; `DashboardScreen` shows dialog; `OtaInstaller` downloads via `DownloadManager` and installs via `FileProvider` (`res/xml/file_paths.xml`, authorities `${applicationId}.fileprovider`). Requires `REQUEST_INSTALL_PACKAGES` + user granting "install unknown apps".
- Permissions UX: `MainActivity` requests `POST_NOTIFICATIONS` at launch (Android 13+); `ui/permissions/PermissionsScreen.kt` (route `permissions`, gear icon in dashboard TopAppBar) deep-links to notification settings and `ACTION_NOTIFICATION_LISTENER_SETTINGS`.

## Release pipeline (`calendarAPP/scripts/release.ps1`)

- Run from repo root: `.\calendarAPP\scripts\release.ps1 [-Version x.y.z] [-SkipBuild]`. Without `-Version` it prompts.
- It auto-bumps `versionCode`/`versionName` in `app/build.gradle.kts`, rebuilds, copies APK to `calendarWeb/calendarfinance.apk`, writes `calendarWeb/version.json`, commits + pushes, triggers Render deploy, then PATCHes Supabase `app_versions` once Render serves the new `versionCode`.
- ALWAYS run `assembleRelease` clean first; the script's retry path is slow.
- WARNING: the script embeds a Supabase key and Render deploy hook in plaintext. Don't print, copy, or rotate them without asking.

## Rules

1. Responde SIEMPRE en español: toda la conversación, explicaciones y respuestas al usuario van en español.
2. `codegraph explore "<query>"` before grep/find on indexed code.
2. Don't invent URLs, endpoints, package names, or table names — only those defined in code/config.
3. Keep Web and Android models consistent (`TransactionEntity` ↔ Supabase columns).
4. Match existing style: Kotlin `PascalCase` classes / `camelCase` functions, JS `kebab-case.js`, DB `snake_case`.
