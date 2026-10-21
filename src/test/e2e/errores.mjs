// Mensajes de error: ingreso con clave errada, token viejo guardado, sesión inválida, servidor caído o arrancando,
// y avisos visibles sobre una ventana emergente.
// Uso: BASE=http://localhost:8080 node src/test/e2e/errores.mjs  (app con perfil demo)
import { chromium } from 'playwright';
const BASE = process.env.BASE || 'http://localhost:8080';
const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: Number(process.env.ANCHO || 1280), height: 800 } });
const fallas = [];
const esperar = (cond, msg) => { if (!cond) fallas.push(msg); console.log((cond ? 'OK    ' : 'FALLA ') + msg); };
const textoError = async () => (await p.locator('.login .mensaje-error').innerText().catch(() => '')).trim();
const textoAviso = async () => (await p.locator('#aviso').innerText().catch(() => '')).trim();
const ingresar = async (u, c) => {
  await p.fill('[name=login]', u); await p.fill('[name=contrasena]', c);
  await p.click('form.login button'); await p.waitForTimeout(1200);
};
const VIEJO = { token: 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ2ZW5kZWRvcjEifQ.firma-de-otra-clave', login: 'vendedor1',
  nombre: 'Viejo', permisos: [], expira: '2020-01-01T00:00:00Z' };

// 1. clave errada: mensaje dentro del formulario
await p.goto(BASE + '/#/login');
await ingresar('vendedor1', 'clave-errada');
esperar((await textoError()) === 'Usuario o contraseña incorrectos', 'clave errada: ' + await textoError());

// 2. token viejo guardado (vencido o firmado con otra clave): no bloquea el ingreso
await p.evaluate(v => localStorage.setItem('ferreteria.sesion', JSON.stringify(v)), VIEJO);
await p.reload(); await p.waitForTimeout(500);
await ingresar('vendedor1', 'Demo123*');
esperar(p.url().includes('#/inicio'), 'vendedor1 entra aunque haya un token viejo guardado');

// 3. sesión que el servidor ya no acepta: vuelve al ingreso con aviso
await p.evaluate(v => localStorage.setItem('ferreteria.sesion', JSON.stringify({ ...v, expira: '2999-01-01T00:00:00Z' })), VIEJO);
await p.goto(BASE + '/#/productos'); await p.reload(); await p.waitForTimeout(1500);
const aviso3 = await textoError();
esperar(p.url().includes('#/login') && aviso3.includes('La sesión terminó'), 'sesión inválida vuelve al ingreso: ' + aviso3);

// 4. servidor inalcanzable
await p.route('**/api/auth/login', r => r.abort());
await ingresar('vendedor1', 'Demo123*');
esperar((await textoError()).startsWith('No hay conexión'), 'sin servidor: ' + await textoError());
await p.unroute('**/api/auth/login');

// 5. respuesta vacía de un proxy mientras el servidor arranca
await p.route('**/api/auth/login', r => r.fulfill({ status: 502, body: '' }));
await ingresar('vendedor1', 'Demo123*');
esperar((await textoError()).startsWith('El servidor está arrancando'), '502 sin cuerpo: ' + await textoError());
await p.unroute('**/api/auth/login');

// 6. un aviso de error se ve por encima de una ventana emergente abierta
await ingresar('admin', 'Demo123*');
await p.goto(BASE + '/#/productos'); await p.waitForTimeout(800);
await p.route('**/api/productos', r => r.request().method() === 'POST' ? r.fulfill({ status: 500, body: '' }) : r.continue());
await p.click('text=Nuevo producto'); await p.waitForSelector('dialog.ventana[open]');
await p.evaluate(() => { const f = document.querySelector('dialog.ventana[open] form'); f.noValidate = true; f.requestSubmit(); });
await p.waitForTimeout(800);
const encima = await p.evaluate(() => document.querySelector('#aviso').matches(':popover-open'));
const aviso6 = await textoAviso();
esperar(encima && aviso6.length > 0, 'aviso sobre la ventana: ' + aviso6);
await p.screenshot({ path: process.env.CAPTURA || 'errores.png' });

await b.close();
if (fallas.length) { console.error(fallas.length + ' falla(s)'); process.exit(1); }
console.log('Mensajes de error: todo bien');
