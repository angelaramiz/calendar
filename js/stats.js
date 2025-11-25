// Módulo de estadísticas básicas
// ADVERTENCIA: Este módulo usa el sistema V1 (events en localStorage)
// TODO: Migrar a V2 usando movements, income_patterns, expense_patterns desde Supabase
import { loadEvents } from './events.js';

function parseISO(dateISO) { return new Date(dateISO + 'T00:00:00'); }
function formatMoney(n) { return (Number(n || 0)).toFixed(2); }

function sumEvent(event, acc) {
  if (!event || (event.type !== 'ingreso' && event.type !== 'gasto')) return acc;
  const isIncome = event.type === 'ingreso';
  const conf = !!event.confirmed;
  const val = conf ? (event.confirmedAmount ?? event.amount ?? 0) : (event.amount ?? 0);
  if (conf) {
    if (isIncome) acc.confirmed.income += Number(val);
    else acc.confirmed.expense += Number(val);
  } else {
    if (isIncome) acc.pending.income += Number(val);
    else acc.pending.expense += Number(val);
  }
  return acc;
}

function emptyAcc() {
  return { confirmed: { income: 0, expense: 0 }, pending: { income: 0, expense: 0 } };
}

export function computeDailyStats(todayISO) {
  console.warn('computeDailyStats usa sistema V1 - los datos pueden estar desactualizados');
  const events = loadEvents();
  const acc = emptyAcc();
  const list = events[todayISO] || [];
  list.forEach(ev => sumEvent(ev, acc));
  const netConfirmed = acc.confirmed.income - acc.confirmed.expense;
  const netPending = acc.pending.income - acc.pending.expense;
  return { acc, netConfirmed, netPending };
}

export function computeWeeklyStatsForMonth(year, monthIndex) {
  // monthIndex: 0..11
  const events = loadEvents();
  const result = []; // [{week:1..6, acc, range:[start,end]}]
  for (let w = 1; w <= 6; w++) {
    const startDay = (w - 1) * 7 + 1;
    const endDay = w * 7;
    const acc = emptyAcc();
    for (let d = startDay; d <= endDay; d++) {
      const date = new Date(year, monthIndex, d);
      if (date.getMonth() !== monthIndex) break;
      const iso = date.toISOString().slice(0, 10);
      const list = events[iso] || [];
      list.forEach(ev => sumEvent(ev, acc));
    }
    // Solo incluir semanas que tocan el mes
    const testDate = new Date(year, monthIndex, startDay);
    if (testDate.getMonth() === monthIndex) {
      const rngStart = new Date(year, monthIndex, startDay);
      const rngEnd = new Date(year, monthIndex, Math.min(endDay, new Date(year, monthIndex + 1, 0).getDate()));
      result.push({ week: w, acc, range: [rngStart, rngEnd] });
    }
  }
  return result;
}

export function computeMonthlyFutureStats(year, monthIndex, todayISO) {
  const events = loadEvents();
  const acc = emptyAcc();
  const first = new Date(year, monthIndex, 1);
  const last = new Date(year, monthIndex + 1, 0);
  const today = parseISO(todayISO);
  for (let d = 1; d <= last.getDate(); d++) {
    const date = new Date(year, monthIndex, d);
    if (date < today) continue; // solo futuro
    const iso = date.toISOString().slice(0, 10);
    const list = events[iso] || [];
    list.forEach(ev => sumEvent(ev, acc));
  }
  return acc;
}

export function computeAnnualStatsGroup(year, groupSize) {
  // groupSize: 2 (bimestral), 3 (trimestral), 6 (semestral), 12 (anual)
  const events = loadEvents();
  const groups = [];
  for (let m = 0; m < 12; m += groupSize) {
    const acc = emptyAcc();
    for (let k = 0; k < groupSize; k++) {
      const month = m + k;
      const days = new Date(year, month + 1, 0).getDate();
      for (let d = 1; d <= days; d++) {
        const date = new Date(year, month, d);
        const iso = date.toISOString().slice(0, 10);
        const list = events[iso] || [];
        list.forEach(ev => sumEvent(ev, acc));
      }
    }
    groups.push({ fromMonth: m + 1, toMonth: Math.min(m + groupSize, 12), acc });
  }
  return groups;
}

export function renderMoney(n) { return `$${formatMoney(n)}`; }