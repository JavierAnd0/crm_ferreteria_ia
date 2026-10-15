// Productos: el detalle abre en ventana emergente y se crea un producto desde el botón «Nuevo producto».
// Uso: BASE=http://localhost:8080 node src/test/e2e/productos.mjs  (app con perfil demo)
import { chromium } from 'playwright';
const BASE = process.env.BASE || 'http://localhost:8080';
const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: Number(process.env.ANCHO || 1280), height: 800 } });
const errores = [];
p.on('pageerror', e => errores.push('PAGE ' + e.message));
p.on('response', r => r.url().includes('/api/') && r.status() >= 400 && errores.push(r.status() + ' ' + r.url()));
await p.goto(BASE + '/');
await p.fill('[name=login]', 'admin'); await p.fill('[name=contrasena]', 'Demo123*');
await p.click('form button'); await p.waitForTimeout(1500);
await p.goto(BASE + '/#/productos'); await p.waitForTimeout(1000);

// detalle en ventana emergente
await p.click('[data-a=ver] >> nth=0'); await p.waitForSelector('dialog.ventana[open] .resumen');
console.log('DETALLE', (await p.locator('dialog.ventana').innerText()).slice(0, 160).replace(/\n/g, ' | '));
if (process.env.FOTO) await p.screenshot({ path: process.env.FOTO + '-detalle.png' });
await p.keyboard.press('Escape'); await p.waitForTimeout(300);
if (await p.locator('dialog.ventana[open]').count()) throw new Error('La ventana no se cerró con Esc');

// nuevo producto
const codigo = '77' + String(Date.now()).slice(-11);
await p.click('[data-a=nuevo]'); await p.waitForSelector('dialog.ventana[open] #nuevo');
await p.fill('#nuevo [name=codigoInterno]', 'E2E-' + codigo.slice(-6));
await p.fill('#nuevo [name=nombre]', 'Tornillo de prueba e2e');
await p.fill('#nuevo [name=presentacion]', 'Caja x 100');
await p.fill('#nuevo [name=codigoBarras]', codigo);
await p.fill('#nuevo [name=stockMinimo]', '5');
if (process.env.FOTO) await p.screenshot({ path: process.env.FOTO + '-nuevo.png' });
await p.click('#nuevo button:not([type=button])'); await p.waitForTimeout(1200);
const filas = await p.locator('tbody tr').allInnerTexts();
console.log('CREADO', filas.join(' / ').replace(/\t/g, ' '));
if (!filas.some(f => f.includes(codigo))) throw new Error('El producto nuevo no aparece en la lista');
await b.close();
if (errores.length) { console.error(errores.join('\n')); process.exit(1); }
console.log('Prueba de productos superada');
