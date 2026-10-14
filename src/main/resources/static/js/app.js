// Arranque de la interfaz: sesión, menú según permisos, enrutador por hash y estado de conexión.
import { api, sesion } from './api.js';
import { local, refrescarCatalogo, sincronizar } from './offline.js';
import { VISTAS, avisar, vistaDe } from './vistas.js';

const MENU = [
  ['inicio', 'Inicio', []],
  ['vender', 'Vender', ['VENTA_EMITIR']],
  ['ventas', 'Facturas', ['VENTA_EMITIR', 'REPORTE_CONSULTAR']],
  ['comprar', 'Comprar', ['COMPRA_REGISTRAR']],
  ['productos', 'Productos y existencias', []],
  ['terceros', 'Clientes y proveedores', []],
  ['reservas', 'Reservas y dañados', ['RESERVA_GESTIONAR', 'ESTADO_DANADO']],
  ['kardex', 'Kárdex', ['PRODUCTO_EDITAR', 'REPORTE_CONSULTAR']],
  ['cierres', 'Cierres mensuales', []],
  ['reportes', 'Reportes', ['REPORTE_CONSULTAR']],
  ['migracion', 'Migración Excel', ['MIGRACION_EJECUTAR']],
  ['auditoria', 'Auditoría', ['AUDITORIA_CONSULTAR']],
  ['usuarios', 'Usuarios', ['USUARIO_ADMINISTRAR']],
  ['offline', 'Sin internet', ['VENTA_EMITIR']],
];

const $ = s => document.querySelector(s);

function pintarMenu(actual) {
  $('#menu').innerHTML = MENU.filter(([, , p]) => !p.length || sesion.puede(...p))
    .map(([id, nombre]) => `<a href="#/${id}" class="${id === actual ? 'activo' : ''}">${nombre}</a>`).join('');
}

function pintarEstado() {
  $('#estado-red').textContent = navigator.onLine ? 'En línea' : 'Sin internet';
  const n = local.cola().length;
  $('#pendientes').hidden = n === 0;
  $('#pendientes').textContent = `${n} venta(s) por sincronizar`;
}

async function navegar() {
  const ruta = location.hash.replace('#/', '') || 'inicio';
  const s = sesion.vigente();
  document.body.classList.toggle('sin-sesion', !s);
  $('#btn-salir').hidden = !s;
  $('#usuario').textContent = s ? s.nombre : '';
  if (!s && ruta !== 'login') { location.hash = '#/login'; return; }
  const vista = (s ? vistaDe(ruta) : VISTAS.login) || VISTAS.inicio;
  pintarMenu(ruta);
  if (window.matchMedia('(max-width: 760px)').matches) $('#menu').hidden = true;
  // Elemento nuevo en cada navegación para no acumular manejadores de eventos.
  const anterior = $('#vista'), main = anterior.cloneNode(false);
  anterior.replaceWith(main);
  main.innerHTML = '<p class="suave">Cargando…</p>';
  try {
    await vista(main);
  } catch (e) {
    main.innerHTML = `<p class="error">${e.message}</p>`;
  }
  main.focus();
  pintarEstado();
}

async function intentarSincronizar() {
  if (!navigator.onLine || !sesion.vigente() || !local.cola().length) return;
  try {
    const r = await sincronizar();
    const conflictos = r.filter(x => x.estado === 'CONFLICTO').length;
    avisar(`Sincronizadas ${r.length - conflictos} venta(s)` + (conflictos ? `; ${conflictos} en conflicto para revisión` : ''), !!conflictos);
    await refrescarCatalogo();
  } catch { /* se reintenta en el siguiente ciclo */ }
  pintarEstado();
}

$('#btn-menu').addEventListener('click', () => { $('#menu').hidden = !$('#menu').hidden; });
$('#btn-salir').addEventListener('click', () => { sesion.cerrar(); location.hash = '#/login'; });
window.addEventListener('hashchange', navegar);
window.addEventListener('online', () => { pintarEstado(); intentarSincronizar(); });
window.addEventListener('offline', pintarEstado);
window.addEventListener('cola-cambio', pintarEstado);
setInterval(intentarSincronizar, 60000);
if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(() => {});
export { api };
navegar();
