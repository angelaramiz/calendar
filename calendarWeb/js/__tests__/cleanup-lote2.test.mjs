/**
 * Lote 2 TDD (RED primero):
 * - Una sola fuente para formatCurrency (balance.js).
 * - formatCurrencyWhole compartido (pesos sin centavos).
 * - logger con flag DEBUG.
 *
 * Uso: node --test calendarWeb/js/__tests__/cleanup-lote2.test.mjs
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const JS = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = (f) => readFileSync(path.join(JS, f), 'utf8');

/** Extrae una funcion declarada como `function name(...)` y la evalua. */
function extractFn(src, name) {
  const start = src.indexOf(`function ${name}(`);
  assert.ok(start >= 0, `${name} no encontrada en el archivo`);
  const lineStart = src.lastIndexOf('\n', start) + 1;
  const end = src.indexOf('\n}', start);
  assert.ok(end > start, `cierre no encontrado para ${name}`);
  const body = src.slice(lineStart, end + 2).replace(/^export\s+/, '');
  return new Function(`${body}; return ${name};`)();
}

test('planning.js y product-wishlist.js no definen su propio formatCurrency', () => {
  for (const f of ['planning.js', 'product-wishlist.js']) {
    assert.ok(
      !read(f).includes('function formatCurrency('),
      `${f} aun define su propia copia`
    );
  }
});

test('balance.js exporta formatCurrency y formatCurrencyWhole', () => {
  const src = read('balance.js');
  assert.ok(src.includes('export function formatCurrency('), 'falta formatCurrency');
  assert.ok(src.includes('export function formatCurrencyWhole('), 'falta formatCurrencyWhole');
});

test('canonica: 2 decimales MXN', () => {
  const fmt = extractFn(read('balance.js'), 'formatCurrency');
  assert.equal(fmt(1234.5), '$1,234.50');
  assert.equal(fmt(0), '$0.00');
  assert.equal(fmt(850, 'MXN'), '$850.00');
});

test('whole: sin centavos, con guardia || 0', () => {
  const fmt = extractFn(read('balance.js'), 'formatCurrencyWhole');
  assert.equal(fmt(1234.5), '$1,235');
  assert.equal(fmt(850), '$850');
  assert.equal(fmt(undefined), '$0');
});

test('financial-engine.js y smart-financial-assistant.js usan el compartido', () => {
  for (const f of ['financial-engine.js', 'smart-financial-assistant.js']) {
    const src = read(f);
    assert.ok(
      !src.includes('function formatCurrency('),
      `${f} aun define copia local`
    );
    assert.ok(
      src.includes('formatCurrencyWhole'),
      `${f} no usa el compartido`
    );
  }
});

test('exports publicos siguen resolviendo (alias al compartido)', () => {
  // financial-dashboard.js importa formatCurrency de financial-engine.js:
  // debe seguir existiendo como alias, si no se rompe la carga del modulo.
  const eng = read('financial-engine.js');
  assert.ok(
    eng.includes('formatCurrencyWhole as formatCurrency'),
    'financial-engine debe re-exportar el alias formatCurrency'
  );
  const smart = read('smart-financial-assistant.js');
  assert.ok(
    smart.includes('formatCurrencyWhole as formatMoney'),
    'smart-assistant debe re-exportar el alias formatMoney'
  );
  const dash = read('financial-dashboard.js');
  assert.ok(
    dash.includes('formatCurrency,') && dash.includes("from './financial-engine.js'"),
    'dashboard debe seguir importando formatCurrency de financial-engine'
  );
});

test('logger.js existe, respeta DEBUG y error siempre sale', async () => {
  let mod;
  try {
    mod = await import('../logger.js');
  } catch {
    assert.fail('logger.js no existe o no se puede importar');
  }
  assert.equal(mod.DEBUG, false, 'DEBUG debe venir apagado por defecto');

  const calls = [];
  const origLog = console.log;
  const origError = console.error;
  console.log = (...a) => calls.push(['log', ...a]);
  console.error = (...a) => calls.push(['error', ...a]);
  try {
    mod.logger.debug('x');
    mod.logger.info('x');
    mod.logger.warn('x');
    assert.deepEqual(calls, [], 'con DEBUG=false no debe salir nada');
    mod.logger.error('boom');
    assert.equal(calls.length, 1, 'error siempre debe salir');
    assert.equal(calls[0][0], 'error');
  } finally {
    console.log = origLog;
    console.error = origError;
  }
});
