/**
 * Script de migración de localStorage a Supabase
 * EJECUTAR UNA SOLA VEZ en la consola del navegador
 * 
 * Instrucciones:
 * 1. Asegúrate de tener autenticación configurada en Supabase
 * 2. Abre la consola del navegador (F12)
 * 3. Copia y pega TODO este archivo
 * 4. Presiona Enter
 */

(async function migrateToSupabase() {
  console.log('🚀 Iniciando migración a Supabase...');
  
  // Importar cliente de Supabase
  const { createClient } = await import('https://esm.sh/@supabase/supabase-js@2');
  
  // Configurar cliente con credenciales seguras (no hardcodear)
  // Intenta leer desde window.__ENV__ (si cargaste js/config.js) o pide al usuario que las ingrese.
  let SUPABASE_URL = (typeof window !== 'undefined' && window.__ENV__ && window.__ENV__.SUPABASE_URL) || 'https://YOUR-PROJECT-ref.supabase.co';
  let SUPABASE_ANON_KEY = (typeof window !== 'undefined' && window.__ENV__ && window.__ENV__.SUPABASE_ANON_KEY) || 'YOUR_SUPABASE_ANON_KEY';

  if (SUPABASE_URL.includes('YOUR-PROJECT-ref') || SUPABASE_ANON_KEY.includes('YOUR_SUPABASE_ANON_KEY')) {
    console.warn('⚠️ No se detectaron credenciales de Supabase.');
    SUPABASE_URL = prompt('Ingresa tu SUPABASE_URL (ej: https://xxxx.supabase.co):', SUPABASE_URL) || '';
    SUPABASE_ANON_KEY = prompt('Ingresa tu SUPABASE_ANON_KEY (desde Settings > API):', SUPABASE_ANON_KEY) || '';
    if (!SUPABASE_URL || !SUPABASE_ANON_KEY) {
      console.error('❌ Debes proporcionar SUPABASE_URL y SUPABASE_ANON_KEY para continuar.');
      return;
    }
  }

  const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY);
  
  // Verificar autenticación
  const { data: { user }, error: authError } = await supabase.auth.getUser();
  
  if (authError || !user) {
    console.error('❌ No hay usuario autenticado');
    console.log('👉 Primero debes crear un usuario en Supabase:');
    console.log('   1. Ve a Authentication > Users en el dashboard');
    console.log('   2. Click en "Add user" > "Create new user"');
    console.log('   3. Ingresa email y contraseña');
    console.log('   4. Luego ejecuta: await supabase.auth.signInWithPassword({ email: "tu@email.com", password: "tucontraseña" })');
    return;
  }
  
  console.log('✅ Usuario autenticado:', user.email);
  console.log('📝 User ID:', user.id);
  
  // Cargar datos de localStorage
  const oldEvents = JSON.parse(localStorage.getItem('calendarEvents') || '{}');
  const totalDates = Object.keys(oldEvents).length;
  
  if (totalDates === 0) {
    console.log('⚠️ No hay datos en localStorage para migrar');
    return;
  }
  
  console.log(`\n📦 Encontrados ${totalDates} fechas con eventos`);
  
  // Contar total de eventos
  let totalEvents = 0;
  for (const events of Object.values(oldEvents)) {
    totalEvents += events.length;
  }
  console.log(`📊 Total de eventos a migrar: ${totalEvents}`);
  
  // Confirmar migración
  if (!confirm(`¿Migrar ${totalEvents} eventos a Supabase?\n\n✅ Se creará un backup en localStorage antes de continuar.`)) {
    console.log('❌ Migración cancelada');
    return;
  }
  
  // Crear backup
  localStorage.setItem('calendarEvents_backup', JSON.stringify(oldEvents));
  localStorage.setItem('calendarEvents_backup_date', new Date().toISOString());
  console.log('💾 Backup creado en localStorage');
  
  // Contadores
  let migrated = 0;
  let errors = 0;
  let loansCreated = 0;
  let alertsCreated = 0;
  const errorDetails = [];
  
  console.log('\n⏳ Iniciando migración...\n');
  
  // Migrar evento por evento
  for (const [dateISO, eventsArray] of Object.entries(oldEvents)) {
    for (const event of eventsArray) {
      try {
        // Insertar evento principal
        const { data: eventData, error: eventError } = await supabase
          .from('events')
          .insert([{
            user_id: user.id,
            date: dateISO,
            title: event.title,
            type: event.type,
            amount: event.amount || null,
            description: event.description || null,
            category: event.category || null,
            confirmed: event.confirmed || false,
            confirmed_amount: event.confirmedAmount || null,
            archived: event.archived || false,
            is_recurring: event.isRecurring || false,
            frequency: event.frequency || null,
            interval: event.interval || null,
            limit_count: event.limitCount || null,
            is_loan_counterpart: event.isCounterpart || false
          }])
          .select()
          .single();
        
        if (eventError) throw eventError;
        
        // Si tiene préstamo, insertarlo
        if (event.loan) {
          const { error: loanError } = await supabase
            .from('loans')
            .insert([{
              user_id: user.id,
              event_id: eventData.id,
              id: event.loan.loanId || crypto.randomUUID(),
              kind: event.loan.kind,
              amount: event.loan.amount,
              expected_return: event.loan.expectedReturn || null,
              interest_value: event.loan.interestValue || null,
              interest_percent: event.loan.interestPercent || null,
              payment_plan: event.loan.paymentPlan || 'single',
              recovery_days: event.loan.recoveryDays || null,
              payment_frequency: event.loan.paymentFrequency || null,
              payment_count: event.loan.paymentCount || null,
              custom_dates: event.loan.customDates ? JSON.stringify(event.loan.customDates) : null,
              notes: event.loan.notes || null,
              status: 'active'
            }]);
          
          if (loanError) {
            console.warn('⚠️ Error creando préstamo:', loanError.message);
          } else {
            loansCreated++;
          }
        }
        
        // Si tiene alertas personalizadas
        if (event.customAlerts && Array.isArray(event.customAlerts) && event.customAlerts.length > 0) {
          for (const alert of event.customAlerts) {
            const { error: alertError } = await supabase
              .from('alerts')
              .insert([{
                user_id: user.id,
                event_id: eventData.id,
                message: alert.message || 'Recordatorio',
                trigger_days_before: alert.daysBefore || 1,
                priority: alert.priority || 'medium',
                browser_notification: alert.browserNotification || false
              }]);
            
            if (!alertError) {
              alertsCreated++;
            }
          }
        }
        
        migrated++;
        
        // Progreso cada 10 eventos
        if (migrated % 10 === 0) {
          console.log(`⏳ Progreso: ${migrated}/${totalEvents} eventos migrados (${Math.round(migrated/totalEvents*100)}%)`);
        }
        
      } catch (error) {
        console.error(`❌ Error en "${event.title}" (${dateISO}):`, error.message);
        errors++;
        errorDetails.push({
          date: dateISO,
          title: event.title,
          error: error.message
        });
      }
    }
  }
  
  // Resultado final
  console.log('\n' + '='.repeat(60));
  console.log('✅ MIGRACIÓN COMPLETADA');
  console.log('='.repeat(60));
  console.log(`
📊 Resumen:
  ✅ Eventos migrados: ${migrated}
  💰 Préstamos creados: ${loansCreated}
  🔔 Alertas creadas: ${alertsCreated}
  ❌ Errores: ${errors}
  📅 Fechas procesadas: ${totalDates}
  `);
  
  if (errors > 0) {
    console.log('\n❌ Detalles de errores:');
    console.table(errorDetails);
  }
  
  console.log('\n💾 Backup guardado como "calendarEvents_backup" en localStorage');
  
  // Preguntar si quiere limpiar localStorage
  const clearLocal = confirm(
    `✅ Migración exitosa: ${migrated} eventos en Supabase\n\n` +
    `¿Deseas limpiar localStorage original?\n` +
    `(El backup "calendarEvents_backup" se mantendrá)`
  );
  
  if (clearLocal) {
    localStorage.removeItem('calendarEvents');
    console.log('🗑️ localStorage "calendarEvents" eliminado');
    console.log('💾 Backup sigue disponible en "calendarEvents_backup"');
  }
  
  // Verificar datos en Supabase
  console.log('\n🔍 Verificando datos en Supabase...');
  const { count: eventCount } = await supabase
    .from('events')
    .select('*', { count: 'exact', head: true })
    .eq('user_id', user.id);
  
  const { count: loanCount } = await supabase
    .from('loans')
    .select('*', { count: 'exact', head: true })
    .eq('user_id', user.id);
  
  console.log(`✅ Total en Supabase: ${eventCount} eventos, ${loanCount} préstamos`);
  
  alert(
    `✅ Migración completada!\n\n` +
    `${migrated} eventos migrados\n` +
    `${loansCreated} préstamos creados\n` +
    `${alertsCreated} alertas creadas\n\n` +
    `Ahora tus datos están en la nube 🚀`
  );
  
  console.log('\n✨ ¡Listo! Tus datos están ahora en Supabase');
  console.log('👉 Próximo paso: Actualizar database.js para usar Supabase');
  
})();
