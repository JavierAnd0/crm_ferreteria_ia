// Detalles y formularios en ventana emergente: compras, terceros, usuarios, unidades y precio manual.
// Uso: BASE=http://localhost:8080 node src/test/e2e/ventanas.mjs  (app con perfil demo)
import { chromium } from 'playwright';
const BASE = process.env.BASE || 'http://localhost:8080';
const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: Number(process.env.ANCHO || 1280), height: 800 } });
const errores = [];
p.on('pageerror', e => errores.push('PAGE ' + e.message));
p.on('response', r => r.url().includes('/api/') && r.status() >= 400 && errores.push(r.status() + ' ' + r.url()));
p.on('dialog', d => d.accept(d.message().includes('categoría') ? 'Categoría e2e ' + Date.now() : 'Prueba e2e'));
const ventana = 'dialog.ventana[open]';
const cerrar = async () => { await p.keyboard.press('Escape'); await p.waitForTimeout(200); };
await p.goto(BASE + '/');
await p.fill('[name=login]', 'admin'); await p.fill('[name=contrasena]', 'Demo123*');
await p.click('form button'); await p.waitForTimeout(1500);

// compras registradas → detalle
await p.goto(BASE + '/#/compras'); await p.click('[data-a=ver] >> nth=0'); await p.waitForSelector(`${ventana} .total`);
console.log('COMPRA', (await p.locator(ventana).innerText()).slice(0, 120).replace(/\n/g, ' | ')); await cerrar();

// tercero → edición
await p.goto(BASE + '/#/terceros'); await p.click('[data-a=ver] >> nth=0'); await p.waitForSelector(`${ventana} #tercero`);
const tel = '300' + String(Date.now()).slice(-7);
await p.fill('#tercero [name=telefono]', tel); await p.click('#tercero button'); await p.waitForTimeout(1000);
if (!(await p.locator('tbody').innerText()).includes(tel)) throw new Error('No se actualizó el tercero');
console.log('TERCERO actualizado', tel);

// usuario → edición de roles
await p.goto(BASE + '/#/usuarios'); await p.click('[data-a=ver] >> nth=1'); await p.waitForSelector(`${ventana} #usuario-form`);
console.log('USUARIO', (await p.locator(`${ventana} header`).innerText()));
await p.click('#usuario-form button'); await p.waitForTimeout(800);

// producto → unidad nueva y precio manual
await p.goto(BASE + '/#/productos'); await p.click('[data-a=ver] >> nth=0'); await p.waitForSelector(`${ventana} #unidad`);
await p.fill('#unidad [name=factor]', '12'); await p.click('#unidad button'); await p.waitForTimeout(800);
await p.fill('#precio-manual [name=precio]', '30000'); await p.fill('#precio-manual [name=motivo]', 'Prueba e2e');
await p.click('#precio-manual button'); await p.waitForTimeout(800);
const detalle = await p.locator(ventana).innerText();
if (!detalle.includes('30.000')) throw new Error('No se fijó el precio manual');
console.log('UNIDADES', (await p.locator(`${ventana} .tabla >> nth=0`).innerText()).replace(/\n/g, ' | '));
if (process.env.FOTO) await p.screenshot({ path: process.env.FOTO + '-producto.png', fullPage: true });
await cerrar();

// nuevo producto → crear categoría desde la ventana
await p.click('[data-a=nuevo]'); await p.waitForSelector(`${ventana} #nuevo`);
const antes = await p.locator('#nuevo [name=categoriaId] option').count();
await p.click('#nuevo [aria-label="Nueva categoría"]'); await p.waitForTimeout(800);
if (await p.locator('#nuevo [name=categoriaId] option').count() !== antes + 1) throw new Error('No se creó la categoría');
await cerrar();

// producto → otra presentación del mismo producto
await p.click('[data-a=ver] >> nth=0'); await p.waitForSelector(`${ventana} [data-a=presentacion]`);
const nombre = (await p.locator(`${ventana} header`).innerText()).split(' ')[0];
await p.click('[data-a=presentacion]'); await p.waitForSelector(`${ventana} #nuevo`);
const otro = '78' + String(Date.now()).slice(-11);
await p.fill('#nuevo [name=presentacion]', 'Empaque e2e ' + otro.slice(-5)); await p.fill('#nuevo [name=codigoBarras]', otro);
await p.click('#nuevo button:not([type=button])'); await p.waitForTimeout(1200);
const fila = await p.locator('tbody').innerText();
if (!fila.includes(otro) || !fila.includes(nombre)) throw new Error('No se agregó la presentación');
console.log('PRESENTACION', fila.split('\n')[0].replace(/\t/g, ' '));

// integraciones
await p.goto(BASE + '/#/integraciones'); await p.waitForTimeout(800);
console.log('INTEGRACIONES', (await p.locator('#vista').innerText()).slice(0, 120).replace(/\n/g, ' | '));
await b.close();
if (errores.length) { console.error(errores.join('\n')); process.exit(1); }
console.log('Prueba de ventanas superada');
