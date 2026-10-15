// Prueba de humo de la interfaz: entra como admin, abre las 16 pantallas, vende un producto por código de
// barras y abre la factura. Falla si hay errores de JavaScript o respuestas 4xx/5xx de la API.
// Uso: npm i playwright && BASE=http://localhost:8080 node src/test/e2e/humo.mjs  (app con perfil demo)
import { chromium } from 'playwright';
const BASE = process.env.BASE || 'http://localhost:8080';
const b = await chromium.launch();
const p = await b.newPage();
const errores = [];
p.on('pageerror', e => errores.push('PAGE ' + e.message));
p.on('console', m => m.type() === 'error' && errores.push('CONSOLE ' + m.text()));
p.on('response', r => r.url().includes('/api/') && r.status() >= 400 && errores.push(r.status() + ' ' + r.url()));
await p.goto(BASE + '/');
await p.fill('[name=login]', 'admin'); await p.fill('[name=contrasena]', 'Demo123*');
await p.click('form button'); await p.waitForTimeout(1500);
for (const r of ['inicio','vender','ventas','comprar','compras','productos','terceros','reservas','kardex','cierres','reportes','migracion','auditoria','usuarios','integraciones','offline']) {
  await p.goto(BASE + '/#/' + r); await p.waitForTimeout(800);
  const t = await p.locator('#vista').innerText();
  console.log(r.padEnd(10), t.slice(0, 90).replace(/\n/g, ' | '));
}
// venta por código de barras
await p.goto(BASE + '/#/vender'); await p.waitForTimeout(600);
await p.fill('[name=codigo]', '2990000000019'); await p.press('[name=codigo]', 'Enter'); await p.waitForTimeout(800);
await p.click('[data-a=confirmar]'); await p.waitForTimeout(1200);
console.log('VENTA', await p.locator('#resultado').innerText());
await p.click('#resultado a'); await p.waitForTimeout(800);
console.log('FACTURA', (await p.locator('#vista').innerText()).slice(0, 200).replace(/\n/g,' | '));
await p.goto(BASE + '/#/kardex'); await p.waitForTimeout(400); await p.click('[data-a=buscar]'); await p.waitForTimeout(800);
console.log('KARDEX filas', await p.locator('tbody tr').count());
await p.goto(BASE + '/#/auditoria'); await p.waitForTimeout(400); await p.click('[data-a=buscar]'); await p.waitForTimeout(800);
console.log('AUDIT filas', await p.locator('tbody tr').count());
await p.setViewportSize({ width: 390, height: 800 }); await p.goto(BASE + '/#/productos'); await p.waitForTimeout(800);
await p.screenshot({ path: 'humo-movil.png' });
await p.setViewportSize({ width: 1280, height: 800 }); await p.goto(BASE + '/#/vender'); await p.waitForTimeout(800);
await p.screenshot({ path: 'humo-vender.png' });
await b.close();
if (errores.length) { console.error('ERRORES', errores); process.exit(1); }
console.log('Prueba de humo superada');
