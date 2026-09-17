# Reglas de Estilo de Código — CalendarFinace

1. **Idioma:** Responde y documenta SIEMPRE en **español**.
2. **Kotlin (Android):**
   - Nombres de clases en `PascalCase`, funciones y variables en `camelCase`.
   - **Codificación:** Guardar SIEMPRE en **UTF-8 sin BOM**.
   - No utilizar KSP, Hilt ni Room (incompatibles con la versión fijada de Kotlin 2.4.0).
3. **Web (calendarWeb):**
   - JavaScript en `kebab-case.js`.
   - Vanilla HTML, CSS y JavaScript moderno sin frameworks pesados ni TailwindCSS salvo petición explícita.
4. **Base de Datos:**
   - Nombres de tablas y columnas estrictamente en `snake_case`.
   - No inventar nombres de tablas o columnas no presentes en el esquema oficial.
5. **Preservación de Código:**
   - Mantener comentarios útiles existentes y no introducir churn innecesario en imports o formateos masivos.
