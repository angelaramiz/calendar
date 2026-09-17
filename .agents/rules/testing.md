# Reglas de Testing y Verificación — CalendarFinace

1. **Pruebas Unitarias Android (Obligatorias):**
   - Ejecutar desde `calendarAPP/`:
     ```powershell
     .\gradlew.bat :app:testReleaseUnitTest
     ```
   - Las 6 suites principales deben permanecer en estado verde (100% pasando):
     - `PatternExpanderTest` (9/9)
     - `NotificationParserTest` (10/10)
     - `MonthSummaryTest` (7/7)
     - `FlowEngineTest` (13/13)
     - `BudgetPlannerTest` (11/11)
     - `GoalPlannerTest` (14/14)

2. **Verificación de Compilación:**
   - Comprobación rápida:
     ```powershell
     .\gradlew.bat compileReleaseKotlin
     ```
   - Ensamble de release:
     ```powershell
     .\gradlew.bat assembleRelease
     ```

3. **Verificación Web:**
   - Probar cambios localmente iniciando un servidor ligero:
     ```powershell
     cd calendarWeb
     python -m http.server
     ```
   - Verificar en consola del navegador que no existan errores de sintaxis, promesas rechazadas o fallos de red con Supabase.

4. **Regresión Cero:**
   - No dar por terminada ninguna tarea sin verificar que las pruebas unitarias y la compilación pasen sin errores.
