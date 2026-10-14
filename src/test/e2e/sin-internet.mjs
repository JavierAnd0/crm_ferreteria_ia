// Prueba de la venta sin internet en el navegador: pide un bloque de facturas para CAJA-01, corta la red, vende,
// recupera la red y comprueba que la venta se sincroniza. Uso: BASE=http://localhost:8080 node src/test/e2e/sin-internet.mjs
import { chromium } from 'playwright';
const BASE = process.env.BASE || 'http://localhost:8080';
const b = await chromium.launch();
const ctx = await b.newContext(); const p = await ctx.newPage();
const err = []; p.on('pageerror', e => err.push(e.message));
await p.goto(BASE + '/'); await p.fill('[name=login]', 'admin'); await p.fill('[name=contrasena]', 'Demo123*');
await p.click('form button'); await p.waitForTimeout(1200);
await p.goto(BASE + '/#/offline'); await p.waitForTimeout(800);
await p.selectOption('[name=equipo]', { label: 'CAJA-01' }); await p.waitForTimeout(300);
await p.click('[data-a=bloque]'); await p.waitForTimeout(800);
await p.click('[data-a=catalogo]'); await p.waitForTimeout(800);
console.log((await p.locator('#vista .panel p').last().innerText()));
await p.goto(BASE + '/#/vender'); await p.waitForTimeout(800);
await ctx.setOffline(true);
await p.fill('[name=codigo]', '2990000000040'); await p.press('[name=codigo]', 'Enter'); await p.waitForTimeout(800);
await p.click('[data-a=confirmar]'); await p.waitForTimeout(800);
console.log('OFFLINE:', await p.locator('#resultado').innerText(), '|', await p.locator('#pendientes').innerText());
await ctx.setOffline(false);
await p.goto(BASE + '/#/offline'); await p.waitForTimeout(800);
await p.click('[data-a=sync]'); await p.waitForTimeout(1500);
const pendientes = await p.evaluate(() => JSON.parse(localStorage.getItem('ferreteria.cola') || '[]').length);
console.log('Pendientes después de sincronizar:', pendientes);
if (pendientes !== 0) { console.error('La venta no se sincronizó'); process.exit(1); }
await b.close();
if (err.length) { console.error('ERRORES', err); process.exit(1); }
